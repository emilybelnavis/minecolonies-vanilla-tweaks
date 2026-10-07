package fr.mcvanillatweaks.client.data;

import com.google.common.collect.ImmutableList;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.colony.workorders.IWorkOrderView;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.core.colony.buildings.moduleviews.BuildingResourcesModuleView;
import com.minecolonies.core.colony.buildings.utils.BuildingBuilderResource;
import com.minecolonies.core.colony.buildings.utils.BuildingBuilderResource.RessourceAvailability;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.network.messages.server.colony.building.MarkBuildingDirtyMessage;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.KeyBindings;
import fr.mcvanillatweaks.client.PinnedBuilders;
import fr.mcvanillatweaks.client.hud.HudLayoutScreen;
import fr.mcvanillatweaks.config.HudConfig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Collects the Resource Scroll data of the tracked builders and turns it into an immutable {@link HudSnapshot}.
 * <p>
 * Runs on the client tick, never per frame. The data is read exactly like MineColonies'
 * {@code WindowResourceList#pullResourcesFromHut} does, but on copies of the resources so the shared building view is never mutated.
 */
public final class ResourceHudTracker
{
    /** Snapshot rebuild period (10 ticks = twice per second). */
    private static final int SNAPSHOT_INTERVAL_TICKS = 10;

    /** Same sort order as the Resource Scroll (missing resources first). */
    private static final BuildingBuilderResource.ResourceComparator COMPARATOR = new BuildingBuilderResource.ResourceComparator(
      RessourceAvailability.NOT_NEEDED,
      RessourceAvailability.HAVE_ENOUGH,
      RessourceAvailability.IN_DELIVERY,
      RessourceAvailability.NEED_MORE,
      RessourceAvailability.DONT_HAVE);

    private static final Map<BuilderRef, Long> LAST_REFRESH_REQUEST = new HashMap<>();

    private static HudSnapshot snapshot = HudSnapshot.EMPTY;
    private static boolean visible = true;
    private static boolean disabled;
    private static int ticks;

    /** A linked Resource Scroll held by the player. */
    private record HeldScroll(BuilderRef ref, @Nullable String label)
    {
    }

    private ResourceHudTracker()
    {
    }

    public static HudSnapshot snapshot()
    {
        return snapshot;
    }

    public static boolean isVisible()
    {
        return visible && !disabled;
    }

    public static boolean isDisabled()
    {
        return disabled;
    }

    public static void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event)
    {
        snapshot = HudSnapshot.EMPTY;
        LAST_REFRESH_REQUEST.clear();
    }

    public static void onClientTick(final ClientTickEvent.Post event)
    {
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        if (player == null || mc.level == null)
        {
            snapshot = HudSnapshot.EMPTY;
            return;
        }

        final boolean forceRebuild = handleKeys(mc, player);

        // The layout editor always shows the panels, even when the HUD is hidden with the toggle key.
        final boolean editing = mc.screen instanceof HudLayoutScreen;
        if (disabled || !(visible || editing))
        {
            return;
        }

        if (++ticks >= SNAPSHOT_INTERVAL_TICKS || forceRebuild)
        {
            ticks = 0;
            try
            {
                snapshot = buildSnapshot(player);
            }
            catch (final LinkageError | RuntimeException e)
            {
                disable(e);
            }
        }
    }

    // ------------------------------------------------------------------ keys

    private static boolean handleKeys(final Minecraft mc, final LocalPlayer player)
    {
        boolean changed = false;
        while (KeyBindings.TOGGLE_HUD.consumeClick())
        {
            visible = !visible;
            player.displayClientMessage(Component.translatable(visible ? "hud.mcvanillatweaks.shown" : "hud.mcvanillatweaks.hidden"), true);
            changed = true;
        }
        while (KeyBindings.PIN_SCROLL.consumeClick())
        {
            if (disabled)
            {
                continue;
            }
            try
            {
                togglePin(player);
                changed = true;
            }
            catch (final LinkageError | RuntimeException e)
            {
                disable(e);
            }
        }
        while (KeyBindings.OPEN_SETTINGS.consumeClick())
        {
            if (mc.screen == null)
            {
                mc.setScreen(new ConfigurationScreen(MineColoniesVanillaTweaks.container(), null));
            }
        }
        while (KeyBindings.HUD_LAYOUT.consumeClick())
        {
            if (mc.screen == null)
            {
                mc.setScreen(new HudLayoutScreen(null));
                changed = true;
            }
        }
        return changed;
    }

    private static void togglePin(final LocalPlayer player)
    {
        final Optional<HeldScroll> held = heldScroll(player);
        if (held.isEmpty())
        {
            player.displayClientMessage(Component.translatable("hud.mcvanillatweaks.pin.no_scroll"), true);
            return;
        }
        final BuilderRef ref = held.get().ref();
        final boolean pinned = PinnedBuilders.toggle(ref, held.get().label());
        final IBuildingView view = ref.resolve();
        final String name = view instanceof BuildingBuilder.View builder
                              ? panelTitle(held.get().label(), builder)
                              : fallbackTitle(held.get().label(), ref);
        player.displayClientMessage(Component.translatable(pinned ? "hud.mcvanillatweaks.pin.added" : "hud.mcvanillatweaks.pin.removed", name), true);
    }

    private static Optional<HeldScroll> heldScroll(final LocalPlayer player)
    {
        for (final InteractionHand hand : InteractionHand.values())
        {
            final ItemStack stack = player.getItemInHand(hand);
            final Optional<BuilderRef> ref = BuilderRef.fromScroll(stack);
            if (ref.isPresent())
            {
                final Component customName = stack.get(DataComponents.CUSTOM_NAME);
                return Optional.of(new HeldScroll(ref.get(), customName == null ? null : customName.getString()));
            }
        }
        return Optional.empty();
    }

    private static void disable(final Throwable error)
    {
        disabled = true;
        snapshot = HudSnapshot.EMPTY;
        MineColoniesVanillaTweaks.LOGGER.error("Resource HUD disabled: incompatible MineColonies version or unexpected data.", error);
    }

    // ------------------------------------------------------------------ snapshot

    private static HudSnapshot buildSnapshot(final LocalPlayer player)
    {
        final List<BuilderRef> targets = new ArrayList<>(PinnedBuilders.refs());
        final Map<BuilderRef, String> heldLabels = new HashMap<>();

        final Optional<HeldScroll> held = heldScroll(player);
        if (held.isPresent())
        {
            final BuilderRef ref = held.get().ref();
            if (targets.contains(ref))
            {
                // Keep the pinned entry's name in sync with the scroll (anvil renaming).
                PinnedBuilders.updateLabel(ref, held.get().label());
            }
            else if (HudConfig.SHOW_HELD_SCROLL.get())
            {
                targets.add(0, ref);
                heldLabels.put(ref, held.get().label());
            }
        }

        final int maxPanels = HudConfig.MAX_PANELS.get();
        if (targets.size() > maxPanels)
        {
            targets.subList(maxPanels, targets.size()).clear();
        }

        LAST_REFRESH_REQUEST.keySet().retainAll(targets);
        if (targets.isEmpty())
        {
            return HudSnapshot.EMPTY;
        }

        final List<HudSnapshot.Panel> panels = new ArrayList<>(targets.size());
        for (final BuilderRef ref : targets)
        {
            final Optional<PinnedBuilders.Pin> pin = PinnedBuilders.get(ref);
            final boolean pinned = pin.isPresent();
            final String label = pinned ? pin.get().label() : heldLabels.get(ref);

            final IBuildingView view = ref.resolve();
            if (view instanceof BuildingBuilder.View builder)
            {
                requestRefresh(ref, builder, player);
                panels.add(buildPanel(ref, pinned, label, builder, player));
            }
            else
            {
                panels.add(new HudSnapshot.Panel(ref, pinned, fallbackTitle(label, ref), null, HudSnapshot.State.UNAVAILABLE, -1, -1, List.of(), 0));
            }
        }
        return new HudSnapshot(List.copyOf(panels));
    }

    /**
     * Asks the server to resync the builder view, like the Resource Scroll does when it opens.
     * Only sent when the player has the permission the server checks, so it never produces a "permission denied" chat message.
     */
    private static void requestRefresh(final BuilderRef ref, final BuildingBuilder.View builder, final LocalPlayer player)
    {
        final long now = Util.getMillis();
        final Long last = LAST_REFRESH_REQUEST.get(ref);
        if (last != null && now - last < HudConfig.REFRESH_SECONDS.get() * 1000L)
        {
            return;
        }
        LAST_REFRESH_REQUEST.put(ref, now);

        final IColonyView colony = builder.getColony();
        if (colony != null && colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS))
        {
            new MarkBuildingDirtyMessage(builder).sendToServer();
        }
    }

    private static HudSnapshot.Panel buildPanel(
      final BuilderRef ref,
      final boolean pinned,
      @Nullable final String label,
      final BuildingBuilder.View builder,
      final LocalPlayer player)
    {
        final String title = panelTitle(label, builder);
        final BuildingResourcesModuleView module = builder.getModuleViewByType(BuildingResourcesModuleView.class);
        final IColonyView colony = builder.getColony();
        if (module == null || colony == null)
        {
            return new HudSnapshot.Panel(ref, pinned, title, null, HudSnapshot.State.UNAVAILABLE, -1, -1, List.of(), 0);
        }

        final IWorkOrderView workOrder = colony.getWorkOrder(module.getWorkOrderId());
        final String subtitle = workOrder == null ? null : workOrder.getDisplayName().getString().replace("\n", " ");
        final Collection<BuildingBuilderResource> source = module.getResources().values();
        if (source.isEmpty())
        {
            final HudSnapshot.State state = workOrder == null ? HudSnapshot.State.NO_WORK_ORDER : HudSnapshot.State.OK;
            return new HudSnapshot.Panel(ref, pinned, title, subtitle, state, -1, -1, List.of(), 0);
        }

        // --- Same computation as WindowResourceList#pullResourcesFromHut, on copies.
        final List<Delivery> deliveries = collectDeliveries(builder, colony);
        final InvWrapper playerInventory = new InvWrapper(player.getInventory());
        final boolean creative = player.isCreative();

        final List<BuildingBuilderResource> resources = new ArrayList<>(source.size());
        double supplied = 0;
        double total = 0;
        for (final BuildingBuilderResource original : source)
        {
            final BuildingBuilderResource copy = new BuildingBuilderResource(original.getItemStack(), original.getAmount(), original.getAvailable());

            final int playerAmount = creative
                                       ? original.getAmount()
                                       : InventoryUtils.getItemCountInItemHandler(playerInventory,
                                         stack -> !ItemStackUtils.isEmpty(stack) && ItemStackUtils.compareItemStacksIgnoreStackSize(original.getItemStack(), stack));
            copy.setPlayerAmount(playerAmount);

            int inDelivery = 0;
            for (final Delivery delivery : deliveries)
            {
                if (ItemStackUtils.compareItemStacksIgnoreStackSize(original.getItemStack(), delivery.getStack(), false, true))
                {
                    inDelivery += delivery.getStack().getCount();
                }
            }
            copy.setAmountInDelivery(inDelivery);

            supplied += Math.min(original.getAvailable(), original.getAmount());
            total += original.getAmount();
            resources.add(copy);
        }
        resources.sort(COMPARATOR);

        final int suppliedPercent = total > 0 ? (int) (supplied / total * 100.0D) : -1;
        final int usedPercent = total > 0 ? module.getProgress() : -1;

        // --- Lines for the HUD.
        final boolean showCompleted = HudConfig.SHOW_COMPLETED.get();
        final int maxLines = HudConfig.MAX_LINES.get();
        final List<HudSnapshot.Line> lines = new ArrayList<>(Math.min(maxLines, resources.size()));
        int hidden = 0;
        for (final BuildingBuilderResource resource : resources)
        {
            final RessourceAvailability status = resource.getAvailabilityStatus();
            if (!showCompleted && status == RessourceAvailability.NOT_NEEDED)
            {
                continue;
            }
            if (lines.size() >= maxLines)
            {
                hidden++;
                continue;
            }
            final ItemStack icon = resource.getItemStack().copyWithCount(1);
            lines.add(new HudSnapshot.Line(icon, resource.getName(), resource.getAvailable(), resource.getPlayerAmount(), resource.getAmount(), status));
        }

        return new HudSnapshot.Panel(ref, pinned, title, subtitle, HudSnapshot.State.OK, suppliedPercent, usedPercent, List.copyOf(lines), hidden);
    }

    /**
     * Header: scroll name if the scroll was renamed, otherwise the builder's worker name (as in the scroll's tooltip),
     * otherwise the hut name.
     */
    private static String panelTitle(@Nullable final String label, final BuildingBuilder.View builder)
    {
        if (label != null && !label.isBlank())
        {
            return label;
        }
        final String worker = builder.getWorkerName();
        if (worker != null && !worker.isBlank())
        {
            return worker.trim();
        }
        return Component.translatable(builder.getBuildingDisplayName()).getString();
    }

    private static String fallbackTitle(@Nullable final String label, final BuilderRef ref)
    {
        if (label != null && !label.isBlank())
        {
            return label;
        }
        return Component.translatable("hud.mcvanillatweaks.builder_at", ref.pos().toShortString()).getString();
    }

    /**
     * Deliveries heading to the builder, collected like WindowResourceList#addDeliveryRequestsToList.
     */
    public static List<Delivery> collectDeliveries(final BuildingBuilder.View builder, final IColonyView colony)
    {
        final List<Delivery> deliveries = new ArrayList<>();
        final IRequestManager requestManager = colony.getRequestManager();
        if (requestManager == null)
        {
            return deliveries;
        }
        for (final Collection<IToken<?>> tokens : builder.getOpenRequestsByCitizen().values())
        {
            addDeliveries(deliveries, requestManager, builder.getID(), ImmutableList.copyOf(tokens));
        }
        return deliveries;
    }

    private static void addDeliveries(final List<Delivery> out, final IRequestManager requestManager, final BlockPos target, final Collection<IToken<?>> tokens)
    {
        for (final IToken<?> token : tokens)
        {
            final IRequest<?> request;
            try
            {
                request = requestManager.getRequestForToken(token);
            }
            catch (final IllegalArgumentException e)
            {
                continue;
            }
            if (request == null)
            {
                continue;
            }
            if (request.getRequest() instanceof Delivery delivery
                  && delivery.getTarget() != null
                  && target.equals(delivery.getTarget().getInDimensionLocation()))
            {
                out.add(delivery);
            }
            if (request.hasChildren())
            {
                addDeliveries(out, requestManager, target, request.getChildren());
            }
        }
    }
}
