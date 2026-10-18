package fr.mcvanillatweaks.client.townhall;

import com.minecolonies.api.colony.IColonyView;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The colony guide (key G, {@link ColonyGuide}): where the huts of the colony the player stands in are, any colony,
 * the player's or not. Two tabs: the huts in a list (closest first or by name, search field; {@link BuildingsView}) and
 * a plan seen from above ({@link ColonyPlanView}). A click on a hut puts a beam on it and closes the guide.
 * <pre>
 * [bricks][map]
 * [bricks] Colony Exempleville                 [Sort: distance] [search  ]
 *          Alex's · 34 buildings
 * +----------------------------------------------------------------------+
 * | [hut] Bakery                                     Lvl 2 · 54 m ↗       |
 * +----------------------------------------------------------------------+
 * </pre>
 */
public final class ColonyGuideScreen extends Screen
{
    private static final int MAX_WIDTH = 380;
    private static final int MAX_HEIGHT = 250;
    private static final int TAB_WIDTH = 26;
    private static final int TAB_HEIGHT = 32;
    private static final int TAB_STEP = 27;
    private static final int TAB_RISE = 28;
    private static final int CONTENT_Y = 34;
    private static final int SEARCH_WIDTH = 110;
    private static final int SORT_WIDTH = 82;

    private enum Page
    {
        // Buildings are the bricks everywhere; maps are the filled map (the colony map).
        LIST(Items.BRICKS, "mcvanillatweaks.guide.tab.list"),
        PLAN(Items.FILLED_MAP, "mcvanillatweaks.guide.tab.plan");

        private final ItemStack icon;
        private final String title;

        Page(final Item icon, final String title)
        {
            this.icon = new ItemStack(icon);
            this.title = title;
        }
    }

    /** Tab, sort and search, kept for the session. */
    private static Page lastPage = Page.LIST;
    private static boolean byDistance = true;
    private static String filter = "";

    private final IColonyView colony;
    private final BuildingsView buildings;
    @Nullable
    private ColonyPlanView plan;
    private final Tooltip tooltip = new Tooltip();
    private Page page = lastPage;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;

    public ColonyGuideScreen(final IColonyView colony)
    {
        super(Component.translatable("mcvanillatweaks.guide.title"));
        this.colony = colony;
        buildings = new BuildingsView(colony);
        buildings.setByDistance(byDistance);
        buildings.setFilter(filter);
    }

    /** Opens on the plan (true) or the list (used by the dev preview). */
    public ColonyGuideScreen(final IColonyView colony, final boolean plan)
    {
        this(colony);
        page = plan ? Page.PLAN : Page.LIST;
    }

    @Override
    protected void init()
    {
        panelWidth = Math.min(MAX_WIDTH, width - 20);
        panelHeight = Math.min(MAX_HEIGHT, height - TAB_RISE - 10);
        left = (width - panelWidth) / 2;
        top = Math.max(TAB_RISE, (height - panelHeight - TAB_RISE) / 2 + TAB_RISE);
        if (page == Page.LIST)
        {
            final Component hint = Component.translatable("mcvanillatweaks.townhall.search");
            final EditBox search = addRenderableWidget(new EditBox(font, left + panelWidth - 8 - SEARCH_WIDTH, top + 9, SEARCH_WIDTH, 16, hint));
            search.setHint(hint.copy().withStyle(ChatFormatting.DARK_GRAY));
            search.setValue(filter);
            search.setResponder(text -> {
                filter = text;
                buildings.setFilter(text);
            });
        }
        else if (plan == null)
        {
            plan = new ColonyPlanView(colony);
        }
    }

    private void open(final Page target)
    {
        page = target;
        lastPage = target;
        rebuildWidgets();
    }

    @Override
    public void tick()
    {
        try
        {
            buildings.tick();
        }
        catch (final LinkageError | RuntimeException e)
        {
            TownHallScreens.report(e);
            onClose();
        }
    }

    // ------------------------------------------------------------------ drawing

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
        Gui.panel(g, left, top, panelWidth, panelHeight);
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

    private int sortX()
    {
        return left + panelWidth - 8 - SEARCH_WIDTH - 6 - SORT_WIDTH;
    }

    private boolean overSort(final double mouseX, final double mouseY)
    {
        return page == Page.LIST && Gui.inside(mouseX, mouseY, sortX(), top + 8, SORT_WIDTH, 18);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        tooltip.clear();
        try
        {
            renderTab(g, page, true);
            renderHeader(g, mouseX, mouseY);
            final int x = left + 8;
            final int y = top + CONTENT_Y;
            final int w = panelWidth - 16;
            final int h = panelHeight - CONTENT_Y - 8;
            if (page == Page.LIST)
            {
                buildings.render(g, x, y, w, h, mouseX, mouseY, tooltip);
            }
            else if (plan != null)
            {
                plan.render(g, x, y, w, h, mouseX, mouseY, tooltip);
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

    private void renderHeader(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        g.renderItem(page.icon, left + 8, top + 8);
        final int textRight = page == Page.LIST ? sortX() - 6 : left + panelWidth - 8;
        final int textWidth = textRight - (left + 30);
        g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.guide.colony", colony.getName()), textWidth), left + 30, top + 7,
          Gui.LABEL, false);
        Component detail = Component.translatable("mcvanillatweaks.guide.detail", owner(), buildings.total());
        final boolean live = ColonyFreshness.isLive(colony);
        if (!live)
        {
            detail = detail.copy().append(Component.translatable("mcvanillatweaks.guide.stale", ClipboardScreen.age(colony)));
        }
        g.drawString(font, Gui.fit(font, detail, textWidth), left + 30, top + 18, Gui.LABEL_SOFT, false);
        if (!live && Gui.inside(mouseX, mouseY, left + 30, top + 17, Math.min(textWidth, font.width(detail)), 10))
        {
            tooltip.set(List.of(detail, Component.translatable("mcvanillatweaks.clipboard.stale_hint").withStyle(ChatFormatting.GRAY)));
        }
        if (page == Page.LIST)
        {
            final boolean over = overSort(mouseX, mouseY);
            final Component sort = Component.translatable(buildings.byDistance() ? "mcvanillatweaks.guide.sort.distance" : "mcvanillatweaks.guide.sort.name");
            Gui.textButton(g, font, sortX(), top + 8, SORT_WIDTH, 18, font.substrByWidth(sort, SORT_WIDTH - 8).getString(), true, over);
            if (over)
            {
                tooltip.set(List.of(sort, Component.translatable("mcvanillatweaks.guide.sort.hint").withStyle(ChatFormatting.GRAY)));
            }
        }
    }

    private Component owner()
    {
        try
        {
            final String name = colony.getPermissions().getOwnerName();
            return Component.literal(name == null || name.isBlank() ? "?" : name);
        }
        catch (final LinkageError | RuntimeException e)
        {
            return Component.literal("?");
        }
    }

    // ------------------------------------------------------------------ input

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
        if (button == 0 && overSort(mouseX, mouseY))
        {
            Gui.clickSound();
            byDistance = !buildings.byDistance();
            buildings.setByDistance(byDistance);
            return true;
        }
        try
        {
            if (super.mouseClicked(mouseX, mouseY, button))
            {
                return true;
            }
            return page == Page.LIST ? buildings.mouseClicked(mouseX, mouseY, button) : plan != null && plan.mouseClicked(mouseX, mouseY, button);
        }
        catch (final LinkageError | RuntimeException e)
        {
            TownHallScreens.report(e);
            onClose();
            return true;
        }
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY)
    {
        final boolean used = page == Page.LIST ? buildings.mouseDragged(mouseY) : plan != null && plan.mouseDragged(mouseX, mouseY, dragX, dragY);
        return used || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button)
    {
        buildings.mouseReleased();
        if (plan != null && plan.mouseReleased())
        {
            // A beam was added: close so the player sees it.
            minecraft.setScreen(null);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        final boolean used = page == Page.LIST ? buildings.mouseScrolled(mouseX, mouseY, scrollY) : plan != null && plan.mouseScrolled(mouseX, mouseY, scrollY);
        return used || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void removed()
    {
        if (plan != null)
        {
            plan.close();
            plan = null;
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
