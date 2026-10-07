package fr.mcvanillatweaks.client.hut;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.IAssignmentModuleView;
import com.minecolonies.api.colony.buildings.modules.IBuildingModuleView;
import com.minecolonies.api.colony.buildings.modules.IEntityListModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.buildings.modules.IItemListModuleView;
import com.minecolonies.api.colony.buildings.modules.IMinimumStockModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.BuildingResourcesModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.BuildingStatisticsModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.CraftingModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.EnchanterStationsModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.FieldsModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.ColonyConnectionModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.ExpeditionLogModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.GraveyardManagementModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.MinerGuardAssignModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.MinerLevelManagementModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.RequestTaskModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.RestaurantMenuModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.SettingsModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.ToolModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.UniversityResearchModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WarehouseOptionsModuleView;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.views.LivingBuildingView;
import com.minecolonies.core.colony.buildings.workerbuildings.PostBox;
import fr.mcvanillatweaks.client.locate.LocateButton;
import net.minecraft.core.registries.BuiltInRegistries;
import com.minecolonies.core.colony.buildings.moduleviews.WorkOrderListModuleView;
import fr.mcvanillatweaks.client.citizen.Dropdown;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import fr.mcvanillatweaks.client.university.UniversityScreens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * Vanilla-style screen shared by every hut, built like the town hall and university ones: creative inventory tabs
 * (home, workers, then the hut's modules in MineColonies' order), the hut's name and level in the header, and one page
 * per tab. Modules without a page here keep their tab, which opens MineColonies' own window.
 * <pre>
 *  [hut][workers][settings][stats]...
 * +------------------------------------------------+
 * | Farmer's Hut                                   |
 * | Level 2 / 5                                    |
 * |  page content                                  |
 * +------------------------------------------------+
 * </pre>
 */
public final class HutScreen extends Screen
{
    static final int WIDTH = 320;
    static final int HEIGHT = 212;
    private static final int CONTENT_X = 8;
    private static final int CONTENT_Y = 32;
    private static final int NAME_Y = 6;
    private static final int IDENTITY_Y = 17;

    private static final int TAB_WIDTH = 26;
    private static final int TAB_HEIGHT = 32;
    private static final int TAB_STEP = 27;
    private static final int TAB_RISE = 28;
    /** Tabs that fit above the panel; the others go below it, like the creative inventory's second row. */
    private static final int TABS_PER_ROW = (WIDTH + 1) / TAB_STEP;

    private final IBuildingView building;
    private final HutPage page;
    @Nullable
    private final BOWindow source;
    private final HutTab tab;
    private final List<HutPage> tabs = new ArrayList<>();
    private final Tooltip tooltip = new Tooltip();
    private final Dropdown dropdown = new Dropdown();
    private int left;
    private int top;

    /**
     * @param source the MineColonies window this screen replaces, used as the parent of the dialogs it opens (they
     *               reopen it when closed, and it is swapped for this screen again); null in the dev preview.
     */
    public HutScreen(final IBuildingView building, final HutPage page, @Nullable final BOWindow source)
    {
        super(Component.translatable(building.getBuildingDisplayName()));
        this.building = building;
        this.page = page;
        this.source = source;
        final HutTab created = createTab(this, page);
        if (created == null)
        {
            throw new IllegalArgumentException("No page for " + page);
        }
        this.tab = created;
    }

    // ------------------------------------------------------------------ pages

    /** Whether this screen has its own page for the module (otherwise its tab opens MineColonies' window). */
    static boolean supports(final IBuildingModuleView module)
    {
        return module instanceof SettingsModuleView || module instanceof BuildingStatisticsModuleView || module instanceof UniversityResearchModuleView
                 || module instanceof CraftingModuleView || module instanceof IMinimumStockModuleView || module instanceof RequestTaskModuleView
                 || module instanceof IItemListModuleView || module instanceof BuildingResourcesModuleView || module instanceof WorkOrderListModuleView
                 || module instanceof WarehouseOptionsModuleView || module instanceof ToolModuleView || module instanceof IEntityListModuleView
                 || module instanceof MinerLevelManagementModuleView || module instanceof MinerGuardAssignModuleView
                 || module instanceof RestaurantMenuModuleView || module instanceof FieldsModuleView
                 || module instanceof GraveyardManagementModuleView || module instanceof EnchanterStationsModuleView
                 || module instanceof ExpeditionLogModuleView || module instanceof ColonyConnectionModuleView;
    }

    @Nullable
    private static HutTab createTab(final HutScreen screen, final HutPage page)
    {
        return switch (page.kind())
        {
            case HOME -> screen.building() instanceof PostBox.View postBox ? new PostBoxTab(screen, postBox) : new HomeTab(screen);
            case STORAGE -> new WarehouseTab(screen, null);
            case WORKERS -> screen.building() instanceof LivingBuildingView home ? new ResidentsTab(screen, home) : new WorkersTab(screen);
            case MODULE ->
            {
                final IBuildingModuleView module = page.module();
                if (module instanceof SettingsModuleView settings)
                {
                    yield new SettingsTab(screen, settings);
                }
                if (module instanceof BuildingStatisticsModuleView stats)
                {
                    yield new StatsTab(screen, stats);
                }
                if (module instanceof UniversityResearchModuleView)
                {
                    yield UniversityScreens.researchTab(screen);
                }
                if (module instanceof CraftingModuleView crafting)
                {
                    yield new RecipesTab(screen, crafting);
                }
                if (module instanceof IMinimumStockModuleView stock)
                {
                    yield new StockTab(screen, stock);
                }
                if (module instanceof RequestTaskModuleView tasks)
                {
                    yield new TasksTab(screen, tasks);
                }
                if (module instanceof IItemListModuleView items)
                {
                    yield new ItemListTab(screen, items);
                }
                if (module instanceof BuildingResourcesModuleView resources)
                {
                    yield new ResourcesTab(screen, resources);
                }
                if (module instanceof WorkOrderListModuleView orders)
                {
                    yield new WorkOrdersTab(screen, orders);
                }
                if (module instanceof WarehouseOptionsModuleView warehouse)
                {
                    yield new WarehouseTab(screen, warehouse);
                }
                if (module instanceof ToolModuleView tool)
                {
                    yield new ToolTab(screen, tool);
                }
                if (module instanceof IEntityListModuleView entities)
                {
                    yield new EntityListTab(screen, entities);
                }
                if (module instanceof MinerLevelManagementModuleView levels)
                {
                    yield new MinerLevelsTab(screen, levels);
                }
                if (module instanceof MinerGuardAssignModuleView)
                {
                    yield new MineGuardsTab(screen);
                }
                if (module instanceof RestaurantMenuModuleView menu)
                {
                    yield new RestaurantMenuTab(screen, menu);
                }
                if (module instanceof FieldsModuleView fields)
                {
                    yield new FieldsTab(screen, fields);
                }
                if (module instanceof GraveyardManagementModuleView graveyard)
                {
                    yield new GraveyardTab(screen, graveyard);
                }
                if (module instanceof EnchanterStationsModuleView stations)
                {
                    yield new EnchanterTab(screen, stations);
                }
                if (module instanceof ExpeditionLogModuleView expedition)
                {
                    yield new ExpeditionTab(screen, expedition);
                }
                if (module instanceof ColonyConnectionModuleView)
                {
                    yield new ConnectionsTab(screen);
                }
                yield null;
            }
        };
    }

    /**
     * Home, workers when the hut employs someone, then the module tabs MineColonies shows. Assignment modules with a page
     * (the warehouse's couriers) are left out: their list is on the home page and their hiring on the workers page.
     */
    private void collectTabs()
    {
        tabs.clear();
        tabs.add(HutPage.HOME);
        // Houses get their residents page in the workers' place.
        final boolean workers = !WorkersTab.modules(building).isEmpty() || building instanceof LivingBuildingView;
        if (workers)
        {
            tabs.add(HutPage.WORKERS);
        }
        final List<IBuildingModuleView> modules = building.getAllModuleViews();
        if (modules != null)
        {
            for (final IBuildingModuleView module : modules)
            {
                if (module.isPageVisible() && !(workers && module instanceof IAssignmentModuleView))
                {
                    tabs.add(HutPage.module(module));
                }
            }
        }
        if (tabs.stream().noneMatch(page::sameAs))
        {
            tabs.add(page);
        }
    }

    private ItemStack icon(final HutPage target)
    {
        return switch (target.kind())
        {
            case HOME -> hutIcon(building);
            case STORAGE -> new ItemStack(Items.BARREL);
            case WORKERS -> new ItemStack(building instanceof LivingBuildingView ? Items.RED_BED : Items.IRON_PICKAXE);
            case MODULE -> moduleIcon(target.module());
        };
    }

    public static ItemStack hutIcon(final IBuildingView building)
    {
        try
        {
            return new ItemStack(building.getBuildingType().getBuildingBlock());
        }
        catch (final RuntimeException e)
        {
            return new ItemStack(Items.BRICKS);
        }
    }

    /** A vanilla item for each kind of MineColonies module, matched by name so unknown modules still get one. */
    static ItemStack moduleIcon(@Nullable final IBuildingModuleView module)
    {
        if (module == null)
        {
            return new ItemStack(Items.PAPER);
        }
        final Item byId = moduleIconById(module);
        if (byId != null)
        {
            return new ItemStack(byId);
        }
        final String name = module.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        final Item item;
        if (name.contains("settings"))
        {
            item = Items.COMPARATOR;
        }
        else if (name.contains("statistic"))
        {
            item = Items.CLOCK;
        }
        else if (name.contains("enchanterstations"))
        {
            // The huts whose workers the enchanter draws experience from.
            item = Items.EXPERIENCE_BOTTLE;
        }
        else if (name.contains("university") || name.contains("enchanter"))
        {
            item = Items.ENCHANTING_TABLE;
        }
        else if (name.contains("minimumstock"))
        {
            // Not the barrel: that one stands for the storage (all the hut's containers).
            item = Items.ITEM_FRAME;
        }
        else if (name.contains("requesttask") || name.contains("workorder"))
        {
            item = Items.WRITABLE_BOOK;
        }
        else if (name.contains("docrafting"))
        {
            item = Items.SMITHING_TABLE;
        }
        else if (name.contains("crafting"))
        {
            item = Items.CRAFTING_TABLE;
        }
        else if (name.contains("flower"))
        {
            item = Items.POPPY;
        }
        else if (name.contains("itemlist"))
        {
            item = Items.HOPPER;
        }
        else if (name.contains("entitylist"))
        {
            item = Items.ZOMBIE_HEAD;
        }
        else if (name.contains("tool"))
        {
            item = Items.IRON_AXE;
        }
        else if (name.contains("farmer"))
        {
            item = Items.WHEAT;
        }
        else if (name.contains("plantation"))
        {
            item = Items.SUGAR_CANE;
        }
        else if (name.contains("restaurant"))
        {
            item = Items.COOKED_BEEF;
        }
        else if (name.contains("resources"))
        {
            item = Items.PAPER;
        }
        else if (name.contains("minerlevel"))
        {
            // Not the pickaxe: that one is the workers tab.
            item = Items.LADDER;
        }
        else if (name.contains("guard"))
        {
            item = Items.SHIELD;
        }
        else if (name.contains("warehouse"))
        {
            // Same icon as the "storage" button of every hut: the contents of all the containers (the chest is the hut's own inventory).
            item = Items.BARREL;
        }
        else if (name.contains("courier"))
        {
            item = Items.MINECART;
        }
        else if (name.contains("graveyard"))
        {
            item = Items.SKELETON_SKULL;
        }
        else if (name.contains("expedition"))
        {
            item = Items.FILLED_MAP;
        }
        else if (name.contains("connection"))
        {
            item = Items.COMPASS;
        }
        else
        {
            item = Items.BOOK;
        }
        return new ItemStack(item);
    }

    /** Item lists and recipe lists share a class: their id tells them apart; tool pages go by their scepter. */
    @Nullable
    @SuppressWarnings("deprecation")
    private static Item moduleIconById(final IBuildingModuleView module)
    {
        final String id;
        if (module instanceof ToolModuleView tool)
        {
            final String path = BuiltInRegistries.ITEM.getKey(tool.getTool()).getPath();
            return path.contains("guard") ? Items.COMPASS : path.contains("beekeeper") ? Items.BEEHIVE : Items.IRON_AXE;
        }
        if (module instanceof IItemListModuleView list)
        {
            id = list.getId();
        }
        else if (module instanceof CraftingModuleView crafting)
        {
            id = crafting.getId();
        }
        else
        {
            return null;
        }
        if (id == null)
        {
            return null;
        }
        return switch (id)
        {
            case "fuel" -> Items.COAL;
            case "compostables" -> Items.COMPOSTER;
            case "saplings" -> Items.OAK_SAPLING;
            case "ores" -> Items.RAW_IRON;
            case "flowers" -> Items.POPPY;
            case "crafting" -> Items.CRAFTING_TABLE;
            case "smelting" -> Items.FURNACE;
            case "brewing" -> Items.BREWING_STAND;
            case "domum" -> Items.STONECUTTER;
            case "custom" -> Items.SMITHING_TABLE;
            default -> null;
        };
    }

    private Component tabTitle(final HutPage target)
    {
        return switch (target.kind())
        {
            case HOME -> Component.translatable("mcvanillatweaks.hut.tab.home");
            case STORAGE -> Component.translatable("mcvanillatweaks.hut.all_inventory");
            case WORKERS -> Component.translatable(building instanceof LivingBuildingView ? "mcvanillatweaks.hut.tab.residents" : "mcvanillatweaks.hut.tab.workers");
            case MODULE ->
            {
                if (target.module() instanceof WarehouseOptionsModuleView)
                {
                    // MineColonies calls it "Settings"; the page is about the racks.
                    yield Component.translatable("mcvanillatweaks.hut.warehouse.storage");
                }
                if (target.module() instanceof GraveyardManagementModuleView)
                {
                    // MineColonies gives it the enchanter's title.
                    yield Component.translatable("mcvanillatweaks.hut.graveyard.tab");
                }
                if (target.module() instanceof EnchanterStationsModuleView)
                {
                    yield Component.translatable("mcvanillatweaks.hut.enchanter.tab");
                }
                if (target.module() instanceof ToolModuleView && building instanceof AbstractBuildingGuards.View)
                {
                    // The guards' scepter page also shows their mission and patrol points.
                    yield Component.translatable("mcvanillatweaks.hut.patrol.tab");
                }
                final Component desc = target.module() == null ? null : target.module().getDesc();
                yield desc == null || desc.getString().isEmpty() ? Component.translatable("mcvanillatweaks.hut.tab.module") : desc;
            }
        };
    }

    // ------------------------------------------------------------------ access for the pages

    public IBuildingView building()
    {
        return building;
    }

    /** The MineColonies window this screen replaced, or a fresh main window of the hut. */
    public BOWindow parentWindow()
    {
        return source != null ? source : building.getWindow();
    }

    <T extends AbstractWidget> T addPageWidget(final T widget)
    {
        return addRenderableWidget(widget);
    }

    void openDropdown(final int ax, final int ay, final int aw, final int ah, final List<Dropdown.Entry> entries, final int selected, final IntConsumer onPick)
    {
        dropdown.open(ax, ay, aw, ah, entries, selected, onPick, height);
    }

    /** The page's content (dev preview). */
    HutTab tab()
    {
        return tab;
    }

    void rebuild()
    {
        rebuildWidgets();
    }

    /** Opens another page through MineColonies' own window, which is then swapped for this screen. */
    public void open(final HutPage target)
    {
        Gui.clickSound();
        HutScreens.open(building, target);
    }

    // ------------------------------------------------------------------ screen

    @Override
    protected void init()
    {
        dropdown.close();
        left = (width - WIDTH) / 2;
        top = Math.max(TAB_RISE, (height - HEIGHT - TAB_RISE) / 2 + TAB_RISE);
        try
        {
            collectTabs();
            tab.layout(left + CONTENT_X, top + CONTENT_Y, WIDTH - 2 * CONTENT_X, HEIGHT - CONTENT_Y - 8);
            tab.init();
            addRenderableWidget(LocateButton.occupants(left + WIDTH - 8 - LocateButton.SIZE, top + 5, building));
        }
        catch (final LinkageError | RuntimeException e)
        {
            fail(e);
        }
    }

    @Override
    public void tick()
    {
        try
        {
            tab.tick();
        }
        catch (final LinkageError | RuntimeException e)
        {
            fail(e);
        }
    }

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        renderTransparentBackground(g);
        for (int i = 0; i < tabs.size(); i++)
        {
            if (!tabs.get(i).sameAs(page))
            {
                renderTab(g, i, false);
            }
        }
        Gui.panel(g, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void render(final GuiGraphics g, final int realMouseX, final int realMouseY, final float partialTick)
    {
        // An open drop-down is modal: nothing under it reacts to the mouse.
        final int mouseX = dropdown.isOpen() ? -1000 : realMouseX;
        final int mouseY = dropdown.isOpen() ? -1000 : realMouseY;
        super.render(g, mouseX, mouseY, partialTick);
        tooltip.clear();
        try
        {
            for (int i = 0; i < tabs.size(); i++)
            {
                if (tabs.get(i).sameAs(page))
                {
                    renderTab(g, i, true);
                }
            }
            renderHeader(g, mouseX, mouseY);
            tab.render(g, mouseX, mouseY, tooltip);
            final int hovered = tabAt(mouseX, mouseY);
            if (hovered >= 0)
            {
                final HutPage target = tabs.get(hovered);
                if (target.kind() == HutPage.Kind.MODULE && target.module() != null && !supports(target.module()))
                {
                    tooltip.set(List.of(tabTitle(target), Component.translatable("mcvanillatweaks.hut.tab.original").withStyle(ChatFormatting.GRAY)));
                }
                else
                {
                    tooltip.set(tabTitle(target));
                }
            }
            dropdown.render(g, font, realMouseX, realMouseY);
        }
        catch (final LinkageError | RuntimeException e)
        {
            fail(e);
            return;
        }
        tooltip.render(g, font, mouseX, mouseY);
    }

    private void renderHeader(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        final Component name = Component.translatable(building.getBuildingDisplayName());
        g.drawString(font, Gui.fit(font, name, 200), left + 8, top + NAME_Y, Gui.LABEL, false);
        final Component level = Component.translatable("mcvanillatweaks.hut.level", building.getBuildingLevel(), building.getBuildingMaxLevel());
        g.drawString(font, Gui.fit(font, level, 200), left + 8, top + IDENTITY_Y, Gui.LABEL_SOFT, false);
        UniversityScreens.renderHeaderInfo(g, font, building, left + WIDTH - 8 - LocateButton.SIZE - 6, top + NAME_Y, mouseX, mouseY, tooltip);
    }

    private int tabX(final int index)
    {
        return left + TAB_STEP * (index % TABS_PER_ROW);
    }

    private boolean bottomRow(final int index)
    {
        return index >= TABS_PER_ROW;
    }

    private void renderTab(final GuiGraphics g, final int index, final boolean selected)
    {
        final int x = tabX(index);
        final boolean bottom = bottomRow(index);
        final int y = bottom ? top + HEIGHT - 4 : top - TAB_RISE;
        // Sprite 1 joins the panel's left edge; the others are the plain middle tab.
        final int sprite = index % TABS_PER_ROW == 0 ? 1 : 2;
        final String row = bottom ? "tab_bottom_" : "tab_top_";
        g.blitSprite(ResourceLocation.withDefaultNamespace("container/creative_inventory/" + row + (selected ? "selected_" : "unselected_") + sprite),
          x, y, TAB_WIDTH, TAB_HEIGHT);
        g.pose().pushPose();
        g.pose().translate(0, 0, 100);
        g.renderItem(icon(tabs.get(index)), x + 5, y + (bottom ? 7 : 9));
        g.pose().popPose();
    }

    private int tabAt(final double mouseX, final double mouseY)
    {
        for (int i = 0; i < tabs.size(); i++)
        {
            final int y = bottomRow(i) ? top + HEIGHT : top - TAB_RISE;
            if (Gui.inside(mouseX, mouseY, tabX(i), y, TAB_WIDTH, TAB_RISE))
            {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        try
        {
            if (dropdown.mouseClicked(mouseX, mouseY, button))
            {
                return true;
            }
            final int target = tabAt(mouseX, mouseY);
            if (target >= 0)
            {
                if (!tabs.get(target).sameAs(page) && button == 0)
                {
                    open(tabs.get(target));
                }
                return true;
            }
            if (super.mouseClicked(mouseX, mouseY, button))
            {
                return true;
            }
            return tab.mouseClicked(mouseX, mouseY, button);
        }
        catch (final LinkageError | RuntimeException e)
        {
            fail(e);
            return true;
        }
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY)
    {
        return tab.mouseDragged(mouseX, mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button)
    {
        tab.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        if (dropdown.mouseScrolled(mouseX, mouseY, scrollY))
        {
            return true;
        }
        return tab.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers)
    {
        return dropdown.keyPressed(key) || tab.keyPressed(key) || super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    /** Something in MineColonies changed: fall back to its own windows. */
    void fail(final Throwable e)
    {
        HutScreens.report(e);
        if (minecraft != null && minecraft.screen == this)
        {
            onClose();
        }
    }
}
