package fr.mcvanillatweaks.client.townhall;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.MinimumStack;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.colony.workorders.IWorkOrderView;
import com.minecolonies.core.client.gui.citizen.MainWindowCitizen;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingBuilderView;
import com.google.common.reflect.TypeToken;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.citizen.CitizenDiagnosis;
import fr.mcvanillatweaks.client.citizen.CitizenInfo;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Colony dashboard (no MineColonies window): the colony's key numbers as cards, then everything that needs the player,
 * each line leading where it can be fixed: citizens who are blocked or slowed down ({@link CitizenDiagnosis}, click =
 * citizen screen), requests waiting for the player (the clipboard's: player and retrying resolvers, minimum stocks and
 * async requests left out; click = clipboard), work orders no builder took, and worker huts without a worker (click =
 * the hut). Shown by the town hall's dashboard tab and by the clipboard ({@link ClipboardScreen}).
 * <pre>
 * [totem 12/25] [pick 2] [bed 1] [eye 0] [flesh 1]
 * To sort out
 * | [hut] Alice Martin           Needs a pickaxe |
 * | [hut] Bob Durand                     Hungry |
 * Requests for you (3)
 * | [log] 64 Oak Log                            |
 * </pre>
 */
final class DashboardView
{
    private static final int CARD_HEIGHT = 30;
    private static final int ROW = 20;
    private static final int REFRESH_TICKS = 40;

    /** A line of the list: a heading, or a row that may lead somewhere. */
    private record Entry(@Nullable ItemStack icon, Component text, @Nullable Component right, List<Component> tooltip, @Nullable Runnable click)
    {
        boolean heading()
        {
            return icon == null;
        }
    }

    /** A number at the top, with its explanation. */
    private record Card(ItemStack icon, String value, Component label, int colour)
    {
    }

    private final Minecraft mc = Minecraft.getInstance();
    private final Font font = mc.font;
    private final IColonyView colony;
    /** The town hall's view, which work orders are matched against ({@code shouldShowIn}); null when the colony has none. */
    private final Supplier<IBuildingView> townHall;
    /** Opens the full list of the requests waiting for the player. */
    private final Runnable openRequests;
    private final ScrollArea list = new ScrollArea();
    private List<Entry> entries = List.of();
    private List<Card> cards = List.of();
    private int ticks;
    private int x;
    private int y;
    private int width;
    private int height;

    DashboardView(final IColonyView colony, final Supplier<IBuildingView> townHall, final Runnable openRequests)
    {
        this.colony = colony;
        this.townHall = townHall;
        this.openRequests = openRequests;
        refresh();
    }

    void tick()
    {
        if (++ticks % REFRESH_TICKS == 0)
        {
            refresh();
        }
    }

    // ------------------------------------------------------------------ data

    private interface Section
    {
        void fill(IColonyView colony, List<Entry> list);
    }

    private void refresh()
    {
        final List<Entry> list = new ArrayList<>();
        // Each section on its own: one that fails (missing data) leaves the others.
        for (final Section section : new Section[] {this::citizens, this::requests, this::workOrders, this::emptyHuts})
        {
            final int size = list.size();
            try
            {
                section.fill(colony, list);
            }
            catch (final LinkageError | RuntimeException e)
            {
                list.subList(size, list.size()).clear();
                MineColoniesVanillaTweaks.LOGGER.debug("Dashboard section unavailable", e);
            }
        }
        entries = list;
    }

    private void citizens(final IColonyView colony, final List<Entry> list)
    {
        int unemployed = 0;
        int homeless = 0;
        int sick = 0;
        int hungry = 0;
        final List<Entry> blocked = new ArrayList<>();
        final List<Entry> slowed = new ArrayList<>();
        for (final ICitizenDataView citizen : colony.getCitizens().values())
        {
            if (citizen.isChild())
            {
                continue;
            }
            unemployed += CitizenInfo.isEmployed(citizen) ? 0 : 1;
            homeless += CitizenInfo.building(citizen, citizen.getHomeBuilding()) == null ? 1 : 0;
            sick += citizen.isSick() ? 1 : 0;
            final List<CitizenDiagnosis.Reason> reasons = CitizenDiagnosis.of(citizen);
            hungry += reasons.stream().anyMatch(r -> r.id().equals("hungry")) ? 1 : 0;
            final CitizenDiagnosis.Reason main = CitizenDiagnosis.main(reasons);
            if (main.level() != CitizenDiagnosis.Level.BLOCKED && main.level() != CitizenDiagnosis.Level.SLOWED)
            {
                continue;
            }
            final List<Component> tooltip = new ArrayList<>();
            tooltip.add(CitizenInfo.isEmployed(citizen) ? CitizenInfo.jobName(citizen).copy().withStyle(ChatFormatting.GRAY)
                          : Component.translatable("mcvanillatweaks.diagnosis.no_job").withStyle(ChatFormatting.GRAY));
            tooltip.addAll(CitizenDiagnosis.tooltip(reasons));
            tooltip.add(Component.translatable("mcvanillatweaks.dashboard.open_citizen").withStyle(ChatFormatting.YELLOW));
            // The citizen's workplace, or the citizen icon when there is none.
            final ItemStack icon = citizen.getWorkBuilding() == null || BlockPos.ZERO.equals(citizen.getWorkBuilding())
                                     ? new ItemStack(Items.PLAYER_HEAD) : ColonyIcons.hut(colony, citizen.getWorkBuilding());
            final Entry entry = new Entry(icon,
              Component.literal(citizen.getName()), main.text().copy().withStyle(main.level().colour()), tooltip,
              () -> new MainWindowCitizen(citizen).open());
            (main.level() == CitizenDiagnosis.Level.BLOCKED ? blocked : slowed).add(entry);
        }
        final int adults = (int) colony.getCitizens().values().stream().filter(c -> !c.isChild()).count();
        cards = List.of(
          new Card(new ItemStack(Items.PLAYER_HEAD), colony.getCitizens().size() + "/" + colony.getCitizenCountLimit(),
            Component.translatable("mcvanillatweaks.dashboard.card.citizens", adults, colony.getCitizens().size() - adults), Gui.WHITE),
          new Card(new ItemStack(Items.IRON_PICKAXE), Integer.toString(unemployed), Component.translatable("mcvanillatweaks.dashboard.card.unemployed"),
            unemployed > 0 ? Gui.GOLD : Gui.WHITE),
          new Card(new ItemStack(Items.RED_BED), Integer.toString(homeless), Component.translatable("mcvanillatweaks.dashboard.card.homeless"),
            homeless > 0 ? Gui.GOLD : Gui.WHITE),
          new Card(new ItemStack(Items.SPIDER_EYE), Integer.toString(sick), Component.translatable("mcvanillatweaks.dashboard.card.sick"),
            sick > 0 ? Gui.RED : Gui.WHITE),
          new Card(new ItemStack(Items.ROTTEN_FLESH), Integer.toString(hungry), Component.translatable("mcvanillatweaks.dashboard.card.hungry"),
            hungry > 0 ? Gui.GOLD : Gui.WHITE));
        if (!blocked.isEmpty() || !slowed.isEmpty())
        {
            list.add(heading(Component.translatable("mcvanillatweaks.dashboard.citizens", blocked.size(), slowed.size())));
            list.addAll(blocked);
            list.addAll(slowed);
        }
    }

    /** The clipboard's requests: what the player has to bring. */
    private void requests(final IColonyView colony, final List<Entry> list)
    {
        final IRequestManager manager = colony.getRequestManager();
        if (manager == null)
        {
            return;
        }
        final Set<IToken<?>> async = new HashSet<>();
        for (final ICitizenDataView citizen : colony.getCitizens().values())
        {
            if (citizen.getJobView() != null)
            {
                async.addAll(citizen.getJobView().getAsyncRequests());
            }
        }
        final Set<IToken<?>> tokens = new HashSet<>();
        tokens.addAll(manager.getPlayerResolver().getAllAssignedRequests());
        tokens.addAll(manager.getRetryingRequestResolver().getAllAssignedRequests());
        final List<IRequest<?>> requests = new ArrayList<>();
        for (final IToken<?> token : tokens)
        {
            IRequest<?> request = manager.getRequestForToken(token);
            while (request != null && request.hasParent())
            {
                request = manager.getRequestForToken(request.getParent());
            }
            if (request != null && !request.getType().equals(TypeToken.of(MinimumStack.class)) && !async.contains(request.getId()) && !requests.contains(request))
            {
                requests.add(request);
            }
        }
        if (requests.isEmpty())
        {
            return;
        }
        list.add(heading(Component.translatable("mcvanillatweaks.dashboard.requests", requests.size())));
        for (final IRequest<?> request : requests)
        {
            final List<ItemStack> stacks = request.getDisplayStacks();
            final Component text = Component.literal(request.getShortDisplayString().getString().replace("§f", ""));
            final Component requester = request.getRequester().getRequesterDisplayName(manager, request);
            list.add(new Entry(stacks.isEmpty() ? new ItemStack(Items.PAPER) : stacks.get(0), text, requester.copy().withStyle(ChatFormatting.GRAY),
              List.of(text, requester.copy().withStyle(ChatFormatting.GRAY), Component.translatable("mcvanillatweaks.dashboard.open_clipboard").withStyle(ChatFormatting.YELLOW)),
              openRequests));
        }
    }

    /** Work orders no builder took. */
    private void workOrders(final IColonyView colony, final List<Entry> list)
    {
        final IBuildingView hall = townHall.get();
        if (hall == null)
        {
            return;
        }
        final List<Entry> rows = new ArrayList<>();
        for (final IWorkOrderView order : colony.getWorkOrders())
        {
            if (!order.shouldShowIn(hall) || isClaimed(colony, order))
            {
                continue;
            }
            final String[] parts = order.getDisplayName().getString().split("\n", 2);
            rows.add(new Entry(ColonyIcons.hut(colony, order.getLocation()), Component.literal(parts[0]),
              Component.literal(parts.length > 1 ? parts[1] : "").withStyle(ChatFormatting.GRAY),
              List.of(order.getDisplayName(), Component.translatable("mcvanillatweaks.dashboard.unclaimed_hint").withStyle(ChatFormatting.GRAY)), null));
        }
        if (!rows.isEmpty())
        {
            list.add(heading(Component.translatable("mcvanillatweaks.dashboard.orders", rows.size())));
            list.addAll(rows);
        }
    }

    private static boolean isClaimed(final IColonyView colony, final IWorkOrderView order)
    {
        final BlockPos claimedBy = order.getClaimedBy();
        return claimedBy != null && colony.getClientBuildingManager().getBuilding(claimedBy) instanceof AbstractBuildingBuilderView;
    }

    /** Built worker huts nobody works in. */
    private void emptyHuts(final IColonyView colony, final List<Entry> list)
    {
        final List<Entry> rows = new ArrayList<>();
        for (final IBuildingView building : colony.getClientBuildingManager().getBuildings().values())
        {
            if (building.getBuildingLevel() <= 0)
            {
                continue;
            }
            final List<WorkerBuildingModuleView> modules = building.getModuleViews(WorkerBuildingModuleView.class);
            if (modules.isEmpty() || modules.stream().anyMatch(module -> !module.getAssignedCitizens().isEmpty()))
            {
                continue;
            }
            final Component name = Component.translatable(building.getBuildingDisplayName());
            rows.add(new Entry(ColonyIcons.hut(building), name, Component.translatable("mcvanillatweaks.hut.level_short", building.getBuildingLevel())
                                                                .withStyle(ChatFormatting.GRAY),
              List.of(name, Component.translatable("mcvanillatweaks.dashboard.empty_hut_hint").withStyle(ChatFormatting.GRAY),
                Component.translatable("mcvanillatweaks.dashboard.open_hut").withStyle(ChatFormatting.YELLOW)),
              () -> building.openGui(false)));
        }
        if (!rows.isEmpty())
        {
            list.add(heading(Component.translatable("mcvanillatweaks.dashboard.empty_huts", rows.size())));
            list.addAll(rows);
        }
    }

    private static Entry heading(final Component text)
    {
        return new Entry(null, text, null, List.of(), null);
    }

    // ------------------------------------------------------------------ drawing

    void render(final GuiGraphics g, final int x, final int y, final int width, final int height, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        renderCards(g, mouseX, mouseY, tooltip);
        final int top = y + CARD_HEIGHT + 8;
        list.set(x, top, width, y + height - top, entries.size() * ROW, ROW);
        list.begin(g);
        if (entries.isEmpty())
        {
            g.renderItem(new ItemStack(Items.SUNFLOWER), list.listX() + 3, list.visibleTop() + 2);
            g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.dashboard.all_good"), list.listWidth() - 28), list.listX() + 23,
              list.visibleTop() + 6, Gui.GREEN, true);
        }
        for (int i = 0; i < entries.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final Entry entry = entries.get(i);
            if (entry.heading())
            {
                g.drawString(font, Gui.fit(font, entry.text(), list.listWidth() - 8), list.listX() + 4, rowY + 7, Gui.YELLOW, true);
                continue;
            }
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            g.renderItem(entry.icon(), list.listX() + 3, rowY + 2);
            int right = list.listX() + list.listWidth() - 4;
            if (entry.right() != null)
            {
                final int w = Math.min(font.width(entry.right()), list.listWidth() / 2 - 8);
                g.drawString(font, Gui.fit(font, entry.right(), w), right - w, rowY + 6, Gui.WHITE, true);
                right -= w + 6;
            }
            final int tx = list.listX() + 23;
            g.drawString(font, Gui.fit(font, entry.text(), right - tx), tx, rowY + 6, Gui.WHITE, true);
            if (hovered && !entry.tooltip().isEmpty())
            {
                tooltip.setAround(entry.tooltip(), mouseX, rowY, rowY + ROW);
            }
        }
        list.end(g);
    }

    private void renderCards(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        if (cards.isEmpty())
        {
            return;
        }
        final int gap = 4;
        final int w = (width - gap * (cards.size() - 1)) / cards.size();
        for (int i = 0; i < cards.size(); i++)
        {
            final Card card = cards.get(i);
            final int cx = x + i * (w + gap);
            Gui.inset(g, cx, y, w, CARD_HEIGHT);
            final boolean hovered = Gui.inside(mouseX, mouseY, cx, y, w, CARD_HEIGHT);
            if (hovered)
            {
                g.fill(cx + 1, y + 1, cx + w - 1, y + CARD_HEIGHT - 1, Gui.ROW_HOVER);
                tooltip.set(card.label());
            }
            g.renderItem(card.icon(), cx + 4, y + (CARD_HEIGHT - 16) / 2);
            g.drawString(font, Gui.fit(font, Component.literal(card.value()), w - 26), cx + 23, y + (CARD_HEIGHT - 8) / 2, card.colour(), true);
        }
    }

    // ------------------------------------------------------------------ input

    boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (list.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button != 0 || !list.overRows(mouseX, mouseY))
        {
            return false;
        }
        final int index = (int) Math.floor((mouseY - list.listY()) / ROW);
        if (index >= 0 && index < entries.size() && entries.get(index).click() != null)
        {
            Gui.clickSound();
            entries.get(index).click().run();
            return true;
        }
        return false;
    }

    boolean mouseDragged(final double mouseX, final double mouseY)
    {
        return list.mouseDragged(mouseY);
    }

    void mouseReleased()
    {
        list.mouseReleased();
    }

    boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        return list.mouseScrolled(mouseX, mouseY, delta);
    }
}
