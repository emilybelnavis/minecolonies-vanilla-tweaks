package fr.mcvanillatweaks.client.townhall;

import com.google.common.reflect.TypeToken;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHallView;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.MinimumStack;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.network.messages.server.ItemSettingMessage;
import com.minecolonies.core.network.messages.server.colony.UpdateRequestStateMessage;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Replaces MineColonies' clipboard window ({@code WindowClipBoard}), with two tabs. The clipboard is linked to a colony
 * by using it on one of its huts (the town hall, usually), so it opens on the colony's dashboard ({@link DashboardView}),
 * readable from anywhere; away from the colony MineColonies stops updating it, so the header says how old it is
 * ({@link ColonyFreshness}). The last two tabs list the colony's huts and citizens to locate them ({@link BuildingsView},
 * {@link ColonyCitizensView}). The second
 * tab is MineColonies' clipboard: the colony's requests that wait for the player
 * (those of the player resolver and of the retrying resolver, shown by their top request), closest requester first,
 * with their sub-requests under them, as MineColonies' clipboard request tree. The checkbox is MineColonies' "!"
 * button: it also shows minimum stock requests and the workers' secondary requests, and is saved on the clipboard with
 * {@code ItemSettingMessage("hideunimportant")}. Top-level requests can be cancelled like in MineColonies.
 * <pre>
 * [beacon][book]
 * [book] Clipboard - Exempleville
 *             4 requests wait for you         [x] Show everything
 * +-----------------------------------------------------------+
 * | (o) 64 Oak Log                                       [x]  |
 * |     Farmer Alice Martin · 24 m                            |
 * +-----------------------------------------------------------+
 * </pre>
 */
public final class ClipboardScreen extends Screen
{
    private static final int WIDTH = 320;
    private static final int HEIGHT = 212;
    private static final int ROW = 24;
    private static final int CANCEL = 14;
    private static final int TAB_WIDTH = 26;
    private static final int TAB_HEIGHT = 32;
    private static final int TAB_STEP = 27;
    private static final int TAB_RISE = 28;
    private static final int CONTENT_Y = 32;
    private static final int SEARCH_WIDTH = 120;

    /** The clipboard's tabs. */
    public enum Page
    {
        DASHBOARD(Items.BEACON, "mcvanillatweaks.townhall.tab.dashboard"),
        // Requests are the book and quill everywhere (the tasks pages).
        REQUESTS(Items.WRITABLE_BOOK, "mcvanillatweaks.clipboard.tab.requests"),
        // Buildings are the bricks everywhere (the town hall's buildings tile); the recovery compass is "locate".
        BUILDINGS(Items.BRICKS,"mcvanillatweaks.clipboard.tab.buildings"),
        // Citizens are the player head everywhere (the town hall's citizens tab).
        CITIZENS(Items.PLAYER_HEAD, "mcvanillatweaks.clipboard.tab.citizens");

        private final ItemStack icon;
        private final String title;

        Page(final Item icon, final String title)
        {
            this.icon = new ItemStack(icon);
            this.title = title;
        }
    }

    private record Row(IRequest<?> request, int depth)
    {
    }

    private final IColonyView colony;
    /** The page the clipboard was last left on, for the session: the clipboard item reopens it. */
    private static Page lastPage = Page.DASHBOARD;
    private final DashboardView dashboard;
    private final BuildingsView buildings;
    private final ColonyCitizensView citizens;
    private String buildingFilter = "";
    private String citizenFilter = "";
    private Page page;
    private final ScrollArea list = new ScrollArea();
    private final Tooltip tooltip = new Tooltip();
    private final Set<IToken<?>> cancelled = new HashSet<>();
    private boolean showAll;
    private List<Row> rows = List.of();
    private int topLevel;
    private int ticks;
    private int left;
    private int top;

    public ClipboardScreen(final IColonyView colony, final boolean showAll, final Page page)
    {
        super(Component.translatable("item.minecolonies.clipboard"));
        this.colony = colony;
        this.showAll = showAll;
        this.page = page;
        lastPage = page;
        dashboard = new DashboardView(colony, () -> townHall(colony), () -> open(Page.REQUESTS));
        buildings = new BuildingsView(colony);
        citizens = new ColonyCitizensView(colony);
        refresh();
    }

    @Nullable
    private static IBuildingView townHall(final IColonyView colony)
    {
        for (final IBuildingView building : colony.getClientBuildingManager().getBuildings().values())
        {
            if (building instanceof ITownHallView)
            {
                return building;
            }
        }
        return null;
    }

    /** The page the clipboard item opens on: the last one used. */
    public static Page lastPage()
    {
        return lastPage;
    }

    private void open(final Page target)
    {
        page = target;
        lastPage = target;
        refresh();
        rebuildWidgets();
    }

    // ------------------------------------------------------------------ MineColonies' request list

    private void refresh()
    {
        final List<Row> list = new ArrayList<>();
        final List<IRequest<?>> roots = openRequests();
        for (final IRequest<?> request : roots)
        {
            if (!cancelled.contains(request.getId()))
            {
                addTree(request, list, 0);
            }
        }
        rows = list;
        topLevel = (int) list.stream().filter(row -> row.depth() == 0).count();
    }

    private List<IRequest<?>> openRequests()
    {
        final IRequestManager manager = colony.getRequestManager();
        final List<IRequest<?>> requests = new ArrayList<>();
        if (manager == null)
        {
            return requests;
        }
        final List<IToken<?>> async = new ArrayList<>();
        for (final ICitizenDataView citizen : colony.getCitizens().values())
        {
            if (citizen.getJobView() != null)
            {
                async.addAll(citizen.getJobView().getAsyncRequests());
            }
        }
        try
        {
            final Set<IToken<?>> tokens = new HashSet<>();
            tokens.addAll(manager.getPlayerResolver().getAllAssignedRequests());
            tokens.addAll(manager.getRetryingRequestResolver().getAllAssignedRequests());
            for (final IToken<?> token : tokens)
            {
                IRequest<?> request = manager.getRequestForToken(token);
                while (request != null && request.hasParent())
                {
                    request = manager.getRequestForToken(request.getParent());
                }
                if (request != null && (showAll || !request.getType().equals(TypeToken.of(MinimumStack.class))) && !requests.contains(request))
                {
                    requests.add(request);
                }
            }
            if (!showAll)
            {
                requests.removeIf(request -> async.contains(request.getId()));
            }
            final BlockPos player = minecraft == null || minecraft.player == null ? BlockPos.ZERO : minecraft.player.blockPosition();
            requests.sort(Comparator.<IRequest<?>>comparingDouble(request -> request.getRequester().getLocation().getInDimensionLocation().distSqr(player))
                            .thenComparingInt(request -> request.getId().hashCode()));
        }
        catch (final RuntimeException e)
        {
            MineColoniesVanillaTweaks.LOGGER.debug("Clipboard requests unavailable", e);
            return List.of();
        }
        return requests;
    }

    private void addTree(final IRequest<?> request, final List<Row> list, final int depth)
    {
        list.add(new Row(request, depth));
        if (!request.hasChildren())
        {
            return;
        }
        for (final IToken<?> token : request.getChildren())
        {
            final IRequest<?> child = colony.getRequestManager().getRequestForToken(token);
            if (child != null)
            {
                addTree(child, list, depth + 1);
            }
        }
    }

    private void toggle(final boolean value)
    {
        showAll = value;
        new ItemSettingMessage("hideunimportant", showAll ? 1 : 0).sendToServer();
        refresh();
    }

    private void cancel(final IRequest<?> request)
    {
        new UpdateRequestStateMessage(colony, request.getId(), RequestState.CANCELLED, null).sendToServer();
        cancelled.add(request.getId());
        refresh();
    }

    private Component requester(final IRequest<?> request)
    {
        try
        {
            return request.getRequester().getRequesterDisplayName(colony.getRequestManager(), request);
        }
        catch (final RuntimeException e)
        {
            return Component.empty();
        }
    }

    private int distance(final IRequest<?> request)
    {
        if (minecraft == null || minecraft.player == null)
        {
            return 0;
        }
        return (int) Math.sqrt(request.getRequester().getLocation().getInDimensionLocation().distSqr(minecraft.player.blockPosition()));
    }

    // ------------------------------------------------------------------ screen

    @Override
    protected void init()
    {
        left = (width - WIDTH) / 2;
        top = Math.max(TAB_RISE, (height - HEIGHT - TAB_RISE) / 2 + TAB_RISE);
        if (page == Page.REQUESTS)
        {
            addRenderableWidget(Checkbox.builder(Component.empty(), font).pos(left + WIDTH - 8 - 17, top + 9).selected(showAll)
                                  .onValueChange((box, value) -> toggle(value)).build());
        }
        else if (page == Page.BUILDINGS || page == Page.CITIZENS)
        {
            final boolean huts = page == Page.BUILDINGS;
            final Component hint = Component.translatable("mcvanillatweaks.townhall.search");
            final EditBox search = addRenderableWidget(new EditBox(font, left + WIDTH - 8 - SEARCH_WIDTH, top + 9, SEARCH_WIDTH, 16, hint));
            search.setHint(hint.copy().withStyle(ChatFormatting.DARK_GRAY));
            search.setValue(huts ? buildingFilter : citizenFilter);
            search.setResponder(text -> {
                if (huts)
                {
                    buildingFilter = text;
                    buildings.setFilter(text);
                }
                else
                {
                    citizenFilter = text;
                    citizens.setFilter(text);
                }
            });
        }
    }

    @Override
    public void tick()
    {
        try
        {
            dashboard.tick();
            buildings.tick();
            citizens.tick();
        }
        catch (final LinkageError | RuntimeException e)
        {
            TownHallScreens.report(e);
            onClose();
            return;
        }
        // MineColonies refreshes its list every 5 seconds.
        if (++ticks % 100 == 0)
        {
            refresh();
        }
    }

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        renderTransparentBackground(g);
        for (final Page other : Page.values())
        {
            if (other != page)
            {
                renderTab(g, other, false);
            }
        }
        Gui.panel(g, left, top, WIDTH, HEIGHT);
    }

    private void renderTab(final GuiGraphics g, final Page tab, final boolean selected)
    {
        final int x = left + TAB_STEP * tab.ordinal();
        final int y = top - TAB_RISE;
        g.blitSprite(ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_" + (selected ? "selected_" : "unselected_") + (tab.ordinal() + 1)),
          x, y, TAB_WIDTH, TAB_HEIGHT);
        g.pose().pushPose();
        g.pose().translate(0, 0, 100);
        g.renderItem(tab.icon, x + 5, y + 9);
        g.pose().popPose();
    }

    @Nullable
    private Page tabAt(final double mouseX, final double mouseY)
    {
        for (final Page tab : Page.values())
        {
            if (Gui.inside(mouseX, mouseY, left + TAB_STEP * tab.ordinal(), top - TAB_RISE, TAB_WIDTH, TAB_RISE))
            {
                return tab;
            }
        }
        return null;
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        tooltip.clear();
        try
        {
            renderTab(g, page, true);
            if (page == Page.DASHBOARD)
            {
                renderDashboard(g, mouseX, mouseY);
            }
            else if (page == Page.BUILDINGS || page == Page.CITIZENS)
            {
                renderLocateList(g, mouseX, mouseY);
            }
            else
            {
                renderContent(g, mouseX, mouseY);
            }
            final Page hoveredTab = tabAt(mouseX, mouseY);
            if (hoveredTab != null)
            {
                tooltip.set(Component.translatable(hoveredTab.title));
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            TownHallScreens.report(e);
            onClose();
            return;
        }
        tooltip.render(g, font, mouseX, mouseY);
    }

    private void renderDashboard(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        g.renderItem(page.icon, left + 8, top + 8);
        final int textWidth = WIDTH - 8 - 30;
        g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.clipboard.title", colony.getName()), textWidth), left + 30, top + 7,
          Gui.LABEL, false);
        final boolean live = ColonyFreshness.isLive(colony);
        final Component freshness = live ? Component.translatable("mcvanillatweaks.clipboard.live")
                                      : Component.translatable("mcvanillatweaks.clipboard.stale", age(colony));
        g.drawString(font, Gui.fit(font, freshness, textWidth), left + 30, top + 18, Gui.LABEL_SOFT, false);
        if (Gui.inside(mouseX, mouseY, left + 30, top + 17, Math.min(textWidth, font.width(freshness)), 10))
        {
            tooltip.set(List.of(freshness, Component.translatable(live ? "mcvanillatweaks.clipboard.live_hint" : "mcvanillatweaks.clipboard.stale_hint")
                                             .withStyle(ChatFormatting.GRAY)));
        }
        dashboard.render(g, left + 8, top + CONTENT_Y, WIDTH - 16, HEIGHT - CONTENT_Y - 8, mouseX, mouseY, tooltip);
    }

    private void renderLocateList(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        final boolean huts = page == Page.BUILDINGS;
        g.renderItem(page.icon, left + 8, top + 8);
        final int textWidth = WIDTH - 8 - SEARCH_WIDTH - 8 - 30;
        g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.clipboard.title", colony.getName()), textWidth), left + 30, top + 7,
          Gui.LABEL, false);
        final Component count = huts ? Component.translatable("mcvanillatweaks.clipboard.buildings.count", buildings.total())
                                  : Component.translatable("mcvanillatweaks.clipboard.citizens.count", citizens.total());
        g.drawString(font, Gui.fit(font, count, textWidth), left + 30, top + 18, Gui.LABEL_SOFT, false);
        if (huts)
        {
            buildings.render(g, left + 8, top + CONTENT_Y, WIDTH - 16, HEIGHT - CONTENT_Y - 8, mouseX, mouseY, tooltip);
        }
        else
        {
            citizens.render(g, left + 8, top + CONTENT_Y, WIDTH - 16, HEIGHT - CONTENT_Y - 8, mouseX, mouseY, tooltip);
        }
    }

    /** How long ago the player left the colony, as MineColonies stopped updating it then. */
    private static Component age(final IColonyView colony)
    {
        final Long since = ColonyFreshness.sinceInside(colony);
        if (since == null)
        {
            return Component.translatable("mcvanillatweaks.clipboard.age.login");
        }
        final long minutes = since / 60_000L;
        if (minutes < 1)
        {
            return Component.translatable("mcvanillatweaks.clipboard.age.now");
        }
        return minutes < 60 ? Component.translatable("mcvanillatweaks.clipboard.age.minutes", minutes)
                 : Component.translatable("mcvanillatweaks.clipboard.age.hours", minutes / 60);
    }

    private void renderContent(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        g.renderItem(page.icon, left + 8, top + 8);
        final Component title = Component.translatable("mcvanillatweaks.clipboard.title", colony.getName());
        final Component showLabel = Component.translatable("mcvanillatweaks.clipboard.show_all");
        final int showX = left + WIDTH - 8 - 17 - 4 - font.width(showLabel);
        g.drawString(font, Gui.fit(font, title, showX - 8 - (left + 30)), left + 30, top + 7, Gui.LABEL, false);
        g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.clipboard.count", topLevel), showX - 8 - (left + 30)), left + 30, top + 18,
          Gui.LABEL_SOFT, false);
        g.drawString(font, showLabel, showX, top + 13, Gui.LABEL, false);
        if (Gui.inside(mouseX, mouseY, showX - 2, top + 8, left + WIDTH - 8 - showX + 2, 20))
        {
            tooltip.set(List.of(showLabel, Component.translatable("mcvanillatweaks.clipboard.show_all_hint").withStyle(ChatFormatting.GRAY)));
        }

        final int listTop = top + CONTENT_Y;
        final int listBottom = top + HEIGHT - 8;
        list.set(left + 8, listTop, WIDTH - 16, listBottom - listTop, rows.size() * ROW, ROW);
        list.begin(g);
        if (rows.isEmpty())
        {
            g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.clipboard.none"), list.listWidth() - 8), list.listX() + 4,
              list.visibleTop() + 6, Gui.LIST_SOFT, true);
        }
        final long time = minecraft == null || minecraft.level == null ? 0 : minecraft.level.getGameTime();
        for (int i = 0; i < rows.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final Row row = rows.get(i);
            final IRequest<?> request = row.request();
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            final int indent = Math.min(4, row.depth()) * 10;
            final List<ItemStack> stacks = request.getDisplayStacks();
            if (!stacks.isEmpty())
            {
                g.renderItem(stacks.get((int) (time / 30 % stacks.size())), list.listX() + 3 + indent, rowY + 4);
            }
            int textRight = list.listX() + list.listWidth() - 4;
            boolean overCancel = false;
            if (row.depth() == 0)
            {
                final int cx = textRight - CANCEL;
                final int cy = rowY + (ROW - CANCEL) / 2;
                overCancel = list.overRows(mouseX, mouseY) && Gui.inside(mouseX, mouseY, cx, cy, CANCEL, CANCEL);
                Gui.textButton(g, font, cx, cy, CANCEL, CANCEL, "✖", true, overCancel);
                textRight = cx - 6;
            }
            final int textX = list.listX() + 24 + indent;
            final Component text = Component.literal(request.getShortDisplayString().getString().replace("§f", ""));
            g.drawString(font, Gui.fit(font, text, textRight - textX), textX, rowY + 3, row.depth() == 0 ? Gui.WHITE : Gui.LIST_SOFT, true);
            final Component who = Component.translatable("mcvanillatweaks.clipboard.who", requester(request), distance(request));
            g.drawString(font, Gui.fit(font, who, textRight - textX), textX, rowY + 13, Gui.LIST_SOFT, true);
            if (overCancel)
            {
                tooltip.set(Component.translatable("com.minecolonies.coremod.gui.citizen.requestscancel"));
            }
            else if (hovered)
            {
                final List<Component> lines = new ArrayList<>();
                lines.add(text);
                lines.add(Component.literal(request.getLongDisplayString().getString().replace("§f", "")).withStyle(ChatFormatting.GRAY));
                lines.add(who.copy().withStyle(ChatFormatting.GRAY));
                lines.add(Component.literal(request.getRequester().getLocation().getInDimensionLocation().toShortString()).withStyle(ChatFormatting.DARK_GRAY));
                tooltip.setAround(lines, mouseX, rowY, rowY + ROW);
            }
        }
        list.end(g);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        final Page target = tabAt(mouseX, mouseY);
        if (target != null)
        {
            if (target != page && button == 0)
            {
                Gui.clickSound();
                open(target);
            }
            return true;
        }
        if (page != Page.REQUESTS)
        {
            try
            {
                if (super.mouseClicked(mouseX, mouseY, button))
                {
                    return true;
                }
                return switch (page)
                {
                    case DASHBOARD -> dashboard.mouseClicked(mouseX, mouseY, button);
                    case BUILDINGS -> buildings.mouseClicked(mouseX, mouseY, button);
                    default -> citizens.mouseClicked(mouseX, mouseY, button);
                };
            }
            catch (final LinkageError | RuntimeException e)
            {
                TownHallScreens.report(e);
                onClose();
                return true;
            }
        }
        if (list.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button == 0 && list.overRows(mouseX, mouseY))
        {
            final int index = (int) Math.floor((mouseY - list.listY()) / ROW);
            if (index >= 0 && index < rows.size() && rows.get(index).depth() == 0)
            {
                final int rowY = list.listY() + index * ROW;
                final int cx = list.listX() + list.listWidth() - 4 - CANCEL;
                if (Gui.inside(mouseX, mouseY, cx, rowY + (ROW - CANCEL) / 2, CANCEL, CANCEL))
                {
                    Gui.clickSound();
                    cancel(rows.get(index).request());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY)
    {
        if (page == Page.DASHBOARD)
        {
            return dashboard.mouseDragged(mouseX, mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        if (page == Page.BUILDINGS)
        {
            return buildings.mouseDragged(mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        if (page == Page.CITIZENS)
        {
            return citizens.mouseDragged(mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        return list.mouseDragged(mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button)
    {
        list.mouseReleased();
        dashboard.mouseReleased();
        buildings.mouseReleased();
        citizens.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        if (page == Page.DASHBOARD)
        {
            return dashboard.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (page == Page.BUILDINGS)
        {
            return buildings.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (page == Page.CITIZENS)
        {
            return citizens.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return list.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
