package fr.mcvanillatweaks.client.citizen;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.requestsystem.data.IRequestSystemDeliveryManJobDataStore;
import com.minecolonies.api.colony.workorders.IWorkOrderView;
import com.minecolonies.core.colony.buildings.modules.settings.StringSetting;
import com.minecolonies.core.colony.buildings.moduleviews.BuildingResourcesModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.SettingsModuleView;
import com.minecolonies.core.colony.buildings.utils.BuildingBuilderResource;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.colony.jobs.views.DmanJobView;
import net.minecraft.core.BlockPos;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.requestsystem.data.IRequestSystemCrafterJobDataStore;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.interactionhandling.IInteractionResponseHandler;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.util.constant.CitizenConstants;
import com.minecolonies.core.colony.buildings.moduleviews.CraftingModuleView;
import com.minecolonies.core.colony.jobs.views.CrafterJobView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Why a citizen does not work, or works slowly, in plain words: what MineColonies only shows as an icon over the head
 * (status, blocking and important dialogues) plus what the citizen data says (no job, paused, sick, hungry, homeless,
 * unhappy). Reasons are sorted, the worst first.
 * <p>
 * Crafters (stonemason, sawmill, cook's assistant...) get their own reasons, from {@code AbstractEntityAICrafting}: they
 * only work on the orders in their task queue ({@code CrafterJobView}'s data store, synced to the client), which come
 * from requests their building can resolve ({@code AbstractCraftingRequestResolver}: built hut, an enabled recipe for the
 * item; the warehouse resolver has a higher priority, so an item already in stock is delivered, not crafted). An empty
 * queue explains an idle crafter; with orders, the first one's open child requests are the ingredients it waits for.
 * <p>
 * Builders: without a work order they idle ({@code EntityAIStructureBuilder}); a free order is claimed automatically
 * only by a builder whose hut level is enough ({@code canBuildIgnoringDistance}) and within 100 blocks
 * ({@code WorkOrderBuilding.canBuild}), and never in manual mode. With an order, the resources module tells what is
 * still missing in the hut, and requests held by the player or retrying resolver are what only the player can bring.
 * Couriers: their queue ({@code DmanJobView}); a long one means the warehouse lacks couriers.
 */
public final class CitizenDiagnosis
{
    /** How bad a reason is; ordered from worst. */
    public enum Level
    {
        BLOCKED(ChatFormatting.RED),
        SLOWED(ChatFormatting.GOLD),
        INFO(ChatFormatting.WHITE),
        OK(ChatFormatting.GREEN);

        private final ChatFormatting colour;

        Level(final ChatFormatting colour)
        {
            this.colour = colour;
        }

        public ChatFormatting colour()
        {
            return colour;
        }
    }

    /** One reason: its id (the lang key suffix, "says" for a dialogue), a short text for a row, and what to do. */
    public record Reason(Level level, String id, Component text, Component detail)
    {
    }

    /** Below this (of 60), MineColonies' citizens are starving and work badly. */
    private static final double HUNGRY = CitizenConstants.LOW_SATURATION;
    /** Below this (of 10), a citizen works slower. */
    private static final double UNHAPPY = 4.0D;
    /** A builder only claims free orders within 100 blocks of its hut ({@code WorkOrderBuilding.canBuild}). */
    private static final double MAX_BUILD_DISTANCE_SQ = 100.0D * 100.0D;
    /** From this many queued deliveries, one courier is not enough. */
    private static final int COURIER_BACKLOG = 10;
    /** Request states that still wait for something. */
    private static final Set<RequestState> PENDING = EnumSet.of(RequestState.CREATED, RequestState.REPORTED, RequestState.ASSIGNING,
      RequestState.ASSIGNED, RequestState.IN_PROGRESS, RequestState.FOLLOWUP_IN_PROGRESS);

    private CitizenDiagnosis()
    {
    }

    public static List<Reason> of(final ICitizenDataView citizen)
    {
        final List<Reason> reasons = new ArrayList<>();
        if (citizen.isChild())
        {
            reasons.add(reason(Level.INFO, "child"));
            return reasons;
        }
        if (!CitizenInfo.isEmployed(citizen))
        {
            reasons.add(reason(Level.BLOCKED, "no_job"));
        }
        if (citizen.isPaused())
        {
            reasons.add(reason(Level.BLOCKED, "paused"));
        }
        if (citizen.isSick())
        {
            reasons.add(reason(Level.BLOCKED, "sick"));
        }
        final VisibleCitizenStatus status = citizen.getVisibleStatus();
        if (status == VisibleCitizenStatus.RAIDED)
        {
            reasons.add(reason(Level.BLOCKED, "raided"));
        }
        else if (status == VisibleCitizenStatus.MOURNING)
        {
            reasons.add(reason(Level.SLOWED, "mourning"));
        }
        else if (status == VisibleCitizenStatus.BAD_WEATHER)
        {
            reasons.add(reason(Level.SLOWED, "rain"));
        }
        else if (status == VisibleCitizenStatus.SLEEP)
        {
            reasons.add(reason(Level.INFO, "sleep"));
        }
        else if (status == VisibleCitizenStatus.EAT)
        {
            reasons.add(reason(Level.INFO, "eat"));
        }
        // What the citizen says: blocking dialogues stop the work, important ones slow it.
        try
        {
            final Minecraft mc = Minecraft.getInstance();
            for (final IInteractionResponseHandler interaction : citizen.getOrderedInteractions())
            {
                if (mc.level != null && !interaction.isVisible(mc.level))
                {
                    continue;
                }
                final int priority = interaction.getPriority().getPriority();
                if (priority >= ChatPriority.IMPORTANT.getPriority())
                {
                    final boolean blocking = priority >= ChatPriority.BLOCKING.getPriority();
                    reasons.add(new Reason(blocking ? Level.BLOCKED : Level.SLOWED, "says", interaction.getInquiry(mc.player),
                      Component.translatable(blocking ? "mcvanillatweaks.diagnosis.says_blocking" : "mcvanillatweaks.diagnosis.says_important")));
                }
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            // dialogues only add detail
        }
        if (citizen.getSaturation() < HUNGRY)
        {
            reasons.add(reason(Level.SLOWED, "hungry"));
        }
        if (CitizenInfo.building(citizen, citizen.getHomeBuilding()) == null)
        {
            reasons.add(reason(Level.SLOWED, "no_home"));
        }
        if (citizen.getHappiness() < UNHAPPY)
        {
            reasons.add(reason(Level.SLOWED, "unhappy"));
        }
        if (CitizenInfo.isEmployed(citizen))
        {
            crafter(citizen, reasons);
            builder(citizen, reasons);
            courier(citizen, reasons);
        }
        reasons.sort(Comparator.comparing(Reason::level));
        if (reasons.isEmpty())
        {
            reasons.add(reason(Level.OK, "ok"));
        }
        return reasons;
    }

    /** What a crafter is doing about its orders: none (and why), waiting for ingredients, or working on them. */
    private static void crafter(final ICitizenDataView citizen, final List<Reason> reasons)
    {
        try
        {
            if (!(citizen.getJobView() instanceof CrafterJobView job))
            {
                return;
            }
            final IBuildingView building = CitizenInfo.building(citizen, citizen.getWorkBuilding());
            final IRequestSystemCrafterJobDataStore store = job.getDataStore();
            if (building == null || store == null)
            {
                return;
            }
            final List<IToken<?>> queue = store.getQueue();
            if (queue.isEmpty())
            {
                reasons.add(crafterIdle(building));
                return;
            }
            final IRequestManager manager = citizen.getColony().getRequestManager();
            final IRequest<?> order = manager == null ? null : manager.getRequestForToken(queue.get(0));
            final IRequest<?> missing = order == null ? null : pendingChild(manager, order);
            if (missing != null)
            {
                final Component item = Component.literal(missing.getShortDisplayString().getString().replace("\u00a7f", ""));
                final boolean player = manager.getPlayerResolver().getAllAssignedRequests().contains(missing.getId())
                                         || manager.getRetryingRequestResolver().getAllAssignedRequests().contains(missing.getId());
                reasons.add(new Reason(player ? Level.BLOCKED : Level.SLOWED, "crafter_waiting",
                  Component.translatable("mcvanillatweaks.diagnosis.crafter_waiting", item),
                  Component.translatable(player ? "mcvanillatweaks.diagnosis.crafter_waiting.player" : "mcvanillatweaks.diagnosis.crafter_waiting.detail")));
                return;
            }
            reasons.add(new Reason(Level.INFO, "crafter_orders", Component.translatable("mcvanillatweaks.diagnosis.crafter_orders", queue.size()),
              Component.translatable("mcvanillatweaks.diagnosis.crafter_orders.detail")));
        }
        catch (final LinkageError | RuntimeException e)
        {
            // crafter details only add detail
        }
    }

    /** What a builder is doing: no construction (and why none was taken), missing materials, or building. */
    private static void builder(final ICitizenDataView citizen, final List<Reason> reasons)
    {
        try
        {
            final IBuildingView building = CitizenInfo.building(citizen, citizen.getWorkBuilding());
            if (!(building instanceof BuildingBuilder.View))
            {
                return;
            }
            final BuildingResourcesModuleView resources = building.getModuleViewByType(BuildingResourcesModuleView.class);
            final IColonyView colony = citizen.getColony();
            if (resources == null || colony == null)
            {
                return;
            }
            final IWorkOrderView order = resources.getWorkOrderId() < 0 ? null : colony.getWorkOrder(resources.getWorkOrderId());
            if (order == null)
            {
                reasons.add(builderIdle(building, colony));
                return;
            }
            final Component site = Component.literal(order.getDisplayName().getString().replace("\n", " "));
            // What only the player can bring: requests nobody in the colony can fill.
            final IRequestManager manager = colony.getRequestManager();
            if (manager != null)
            {
                for (final CitizenInfo.RequestNode node : CitizenInfo.openRequests(citizen))
                {
                    final IToken<?> id = node.request().getId();
                    if (manager.getPlayerResolver().getAllAssignedRequests().contains(id) || manager.getRetryingRequestResolver().getAllAssignedRequests().contains(id))
                    {
                        reasons.add(new Reason(Level.BLOCKED, "builder_player", Component.translatable("mcvanillatweaks.diagnosis.builder_player",
                          Component.literal(node.request().getShortDisplayString().getString().replace("\u00a7f", ""))),
                          Component.translatable("mcvanillatweaks.diagnosis.builder_player.detail", site)));
                        return;
                    }
                }
            }
            BuildingBuilderResource first = null;
            int missing = 0;
            for (final BuildingBuilderResource resource : resources.getResources().values())
            {
                if (resource.getAvailable() < resource.getAmount())
                {
                    missing++;
                    if (first == null)
                    {
                        first = resource;
                    }
                }
            }
            if (first != null)
            {
                final Component item = Component.literal((first.getAmount() - first.getAvailable()) + " ").append(first.getItemStack().getHoverName());
                reasons.add(new Reason(Level.SLOWED, "builder_missing",
                  missing > 1 ? Component.translatable("mcvanillatweaks.diagnosis.builder_missing_more", item, missing - 1)
                              : Component.translatable("mcvanillatweaks.diagnosis.builder_missing", item),
                  Component.translatable("mcvanillatweaks.diagnosis.builder_missing.detail", site)));
                return;
            }
            reasons.add(new Reason(Level.INFO, "builder_site", Component.translatable("mcvanillatweaks.diagnosis.builder_site", site),
              Component.translatable("mcvanillatweaks.diagnosis.builder_site.detail")));
        }
        catch (final LinkageError | RuntimeException e)
        {
            // builder details only add detail
        }
    }

    /** Why a builder has no construction: none waiting, one it can't take (level, distance), or manual mode. */
    private static Reason builderIdle(final IBuildingView builder, final IColonyView colony)
    {
        final BlockPos here = builder.getPosition();
        final SettingsModuleView settings = builder.getModuleViewByType(SettingsModuleView.class);
        final StringSetting mode = settings == null ? null : settings.getSetting(BuildingBuilder.MODE);
        final boolean manual = mode != null && BuildingBuilder.MANUAL_SETTING.equals(mode.getValue());
        IWorkOrderView tooLow = null;
        IWorkOrderView tooFar = null;
        for (final IWorkOrderView order : colony.getWorkOrders())
        {
            if (!BlockPos.ZERO.equals(order.getClaimedBy()) || !order.shouldShowIn(builder))
            {
                continue;
            }
            final Component name = Component.literal(order.getDisplayName().getString().replace("\n", " "));
            if (!order.canBuildIgnoringDistance(here, builder.getBuildingLevel()))
            {
                tooLow = tooLow == null ? order : tooLow;
                continue;
            }
            if (manual)
            {
                return new Reason(Level.SLOWED, "builder_manual", Component.translatable("mcvanillatweaks.diagnosis.builder_manual", name),
                  Component.translatable("mcvanillatweaks.diagnosis.builder_manual.detail"));
            }
            if (here.distSqr(order.getLocation()) > MAX_BUILD_DISTANCE_SQ)
            {
                tooFar = tooFar == null ? order : tooFar;
                continue;
            }
            return new Reason(Level.INFO, "builder_next", Component.translatable("mcvanillatweaks.diagnosis.builder_next", name),
              Component.translatable("mcvanillatweaks.diagnosis.builder_next.detail"));
        }
        if (tooFar != null)
        {
            return new Reason(Level.SLOWED, "builder_far", Component.translatable("mcvanillatweaks.diagnosis.builder_far",
              Component.literal(tooFar.getDisplayName().getString().replace("\n", " "))),
              Component.translatable("mcvanillatweaks.diagnosis.builder_far.detail", (int) Math.sqrt(here.distSqr(tooFar.getLocation()))));
        }
        if (tooLow != null)
        {
            return new Reason(Level.SLOWED, "builder_level", Component.translatable("mcvanillatweaks.diagnosis.builder_level",
              Component.literal(tooLow.getDisplayName().getString().replace("\n", " "))),
              Component.translatable("mcvanillatweaks.diagnosis.builder_level.detail", builder.getBuildingLevel()));
        }
        return reason(Level.INFO, "builder_idle");
    }

    /** A courier's deliveries: none, a few (the first one shown), or too many for one courier. */
    private static void courier(final ICitizenDataView citizen, final List<Reason> reasons)
    {
        try
        {
            if (!(citizen.getJobView() instanceof DmanJobView job))
            {
                return;
            }
            final IRequestSystemDeliveryManJobDataStore store = job.getDataStore();
            if (store == null)
            {
                return;
            }
            final List<IToken<?>> queue = store.getQueue();
            if (queue.isEmpty())
            {
                reasons.add(reason(Level.INFO, "courier_idle"));
                return;
            }
            if (queue.size() >= COURIER_BACKLOG)
            {
                reasons.add(new Reason(Level.SLOWED, "courier_busy", Component.translatable("mcvanillatweaks.diagnosis.courier_busy", queue.size()),
                  Component.translatable("mcvanillatweaks.diagnosis.courier_busy.detail")));
                return;
            }
            final IRequestManager manager = citizen.getColony().getRequestManager();
            final IRequest<?> first = manager == null ? null : manager.getRequestForToken(queue.get(0));
            final Component what = first == null ? Component.empty() : Component.literal(first.getShortDisplayString().getString().replace("\u00a7f", ""));
            reasons.add(new Reason(Level.INFO, "courier_work", Component.translatable("mcvanillatweaks.diagnosis.courier_work", queue.size()),
              Component.translatable("mcvanillatweaks.diagnosis.courier_work.detail", what)));
        }
        catch (final LinkageError | RuntimeException e)
        {
            // courier details only add detail
        }
    }

    /**
     * Why a crafter's hut has no order: no recipe, every recipe disabled (MineColonies skips disabled recipes when it
     * looks for one), or simply nobody asked for anything it makes.
     */
    public static Reason crafterIdle(final IBuildingView building)
    {
        int recipes = 0;
        int enabled = 0;
        for (final CraftingModuleView module : building.getModuleViews(CraftingModuleView.class))
        {
            for (final IRecipeStorage recipe : module.getRecipes())
            {
                recipes++;
                enabled += module.isDisabled(recipe) ? 0 : 1;
            }
        }
        if (recipes == 0)
        {
            return reason(Level.SLOWED, "crafter_no_recipe");
        }
        if (enabled == 0)
        {
            return reason(Level.SLOWED, "crafter_disabled");
        }
        return reason(Level.INFO, "crafter_no_order");
    }

    /** The first ingredient request of an order that is not delivered yet, looking down the request tree. */
    @Nullable
    private static IRequest<?> pendingChild(final IRequestManager manager, final IRequest<?> request)
    {
        if (!request.hasChildren())
        {
            return null;
        }
        for (final IToken<?> token : request.getChildren())
        {
            final IRequest<?> child = manager.getRequestForToken(token);
            if (child == null || !PENDING.contains(child.getState()))
            {
                continue;
            }
            final IRequest<?> deeper = pendingChild(manager, child);
            return deeper != null ? deeper : child;
        }
        return null;
    }

    private static Reason reason(final Level level, final String key)
    {
        return new Reason(level, key, Component.translatable("mcvanillatweaks.diagnosis." + key),
          Component.translatable("mcvanillatweaks.diagnosis." + key + ".detail"));
    }

    /** The worst reason, for a single line. */
    public static Reason main(final List<Reason> reasons)
    {
        return reasons.get(0);
    }

    /** Every reason with its explanation, for a tooltip. */
    public static List<Component> tooltip(final List<Reason> reasons)
    {
        final List<Component> lines = new ArrayList<>();
        for (final Reason reason : reasons)
        {
            lines.add(reason.text().copy().withStyle(reason.level().colour()));
            lines.add(reason.detail().copy().withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }
}
