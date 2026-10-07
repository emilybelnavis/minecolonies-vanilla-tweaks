package fr.mcvanillatweaks.client.hut;

import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.modules.IBuildingModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.client.gui.AbstractBuildingWindow;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.minecolonies.core.client.gui.modules.building.DOCraftingWindow;
import com.minecolonies.core.colony.buildings.moduleviews.DOCraftingModuleView;
import com.minecolonies.core.client.gui.WindowHireWorker;
import com.minecolonies.core.client.gui.WindowHutMinPlaceholder;
import com.minecolonies.core.client.gui.huts.WindowHutBuilderModule;
import com.minecolonies.core.client.gui.huts.WindowHutWorkerModulePlaceholder;
import com.minecolonies.core.client.gui.modules.building.EntityListModuleWindow;
import com.minecolonies.core.client.gui.modules.building.ItemListModuleWindow;
import com.minecolonies.core.client.gui.modules.building.ToolModuleWindow;
import com.minecolonies.core.client.gui.modules.building.WindowBuilderResModule;
import com.minecolonies.core.client.gui.modules.building.WorkOrderModuleWindow;
import com.minecolonies.core.client.gui.modules.building.MinimumStockModuleWindow;
import com.minecolonies.core.client.gui.modules.building.SettingsModuleWindow;
import com.minecolonies.core.client.gui.modules.building.SpecialAssignmentModuleWindow;
import com.minecolonies.core.client.gui.modules.building.UniversityModuleWindow;
import com.minecolonies.core.client.gui.modules.building.WarehouseOptionsModuleWindow;
import com.minecolonies.core.client.gui.modules.building.WindowHutRequestTaskModule;
import com.minecolonies.core.client.gui.WindowAssignCitizen;
import com.minecolonies.core.client.gui.WindowBuildBuilding;
import com.minecolonies.core.client.gui.WindowHutAllInventory;
import com.minecolonies.core.client.gui.WindowHutNameEntry;
import com.minecolonies.core.client.gui.WindowPostBoxMain;
import com.minecolonies.core.client.gui.WindowsBarracksSpies;
import com.minecolonies.core.client.gui.WindowBannerRallyGuards;
import com.minecolonies.core.client.gui.WindowPlantationField;
import com.minecolonies.core.client.gui.containers.WindowField;
import com.minecolonies.api.tileentities.AbstractTileEntityPlantationField;
import com.minecolonies.core.tileentities.TileEntityScarecrow;
import com.minecolonies.core.client.gui.huts.WindowBarracksBuilding;
import com.minecolonies.core.client.gui.WindowPostBoxMinStock;
import com.minecolonies.core.client.gui.WindowResourceList;
import com.minecolonies.core.colony.buildings.moduleviews.BuildingResourcesModuleView;
import com.minecolonies.core.client.gui.huts.WindowHutLiving;
import com.minecolonies.core.client.gui.modules.building.EnchanterStationModuleWindow;
import com.minecolonies.core.client.gui.modules.building.FarmFieldsModuleWindow;
import com.minecolonies.core.client.gui.modules.building.ConnectionModuleWindow;
import com.minecolonies.core.client.gui.modules.building.ExpeditionLogModuleWindow;
import com.minecolonies.core.colony.buildings.moduleviews.ColonyConnectionModuleView;
import com.minecolonies.core.client.gui.modules.building.GraveyardManagementWindow;
import com.minecolonies.core.client.gui.modules.building.PlantationFieldsModuleWindow;
import com.minecolonies.core.client.gui.modules.building.RestaurantMenuModuleWindow;
import com.minecolonies.core.client.gui.modules.building.WindowHutMinerModule;
import com.minecolonies.core.colony.buildings.views.LivingBuildingView;
import com.minecolonies.core.client.gui.modules.building.WindowListRecipes;
import com.minecolonies.core.client.gui.modules.building.WindowMineGuardModule;
import com.minecolonies.core.client.gui.modules.building.WindowStatsModule;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.config.HudConfig;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

/**
 * Replaces the hut windows every MineColonies building shares with {@link HutScreen}, like the town hall: MineColonies
 * keeps opening its own windows (right click on the hut, side tabs, the windows it comes back to after a dialog) and
 * each one is swapped for the matching page when it is about to be shown. Swapped are the home page of huts (with and
 * without workers, and the builder's once its guide is read), the hiring window, and the settings, statistics,
 * university research, recipe, minimum stock, task, item list, builder resources, work order, warehouse, tool (scepter)
 * and entity list (the guards' hostiles), graveyard and enchanter gathering modules (the
 * couriers' assignment window goes to the home page, which lists them). The build options window (also the town
 * hall's) becomes {@link BuildScreen}, the rename window {@link RenameScreen}, the all-inventory window the storage page, the resource scroll's window the builder's
 * resources page. The barracks' home
 * page and spies dialog become the home page, with {@link BarracksCard}. Windows a building customises otherwise and the
 * town hall are left to MineColonies.
 */
public final class HutScreens
{
    /** Module windows that have a page here, matched exactly: a module with its own window subclass keeps it. */
    private static final Set<Class<?>> MODULE_WINDOWS = Set.of(SettingsModuleWindow.class, WindowStatsModule.class, UniversityModuleWindow.class,
      WindowListRecipes.class, MinimumStockModuleWindow.class, WindowHutRequestTaskModule.class, ItemListModuleWindow.class, WindowBuilderResModule.class,
      WorkOrderModuleWindow.class, WarehouseOptionsModuleWindow.class, ToolModuleWindow.class, EntityListModuleWindow.class,
      WindowHutMinerModule.class, WindowMineGuardModule.class, RestaurantMenuModuleWindow.class,
      FarmFieldsModuleWindow.class, PlantationFieldsModuleWindow.class, GraveyardManagementWindow.class, EnchanterStationModuleWindow.class,
      ExpeditionLogModuleWindow.class,
      WindowPostBoxMinStock.class);
    /** MineColonies shows its guide instead of the builder's hut until the player has read it. */
    private static final ResourceLocation BUILDER_GUIDE = ResourceLocation.fromNamespaceAndPath("minecolonies", "minecolonies/check_out_guide");

    /** Set after an incompatibility: MineColonies' own windows are used for the rest of the session. */
    private static boolean failed;
    /** A MineColonies window opened on purpose, shown as is. */
    @Nullable
    private static BOWindow original;

    private HutScreens()
    {
    }

    private static boolean configEnabled(final boolean university)
    {
        try
        {
            return (university ? HudConfig.VANILLA_UNIVERSITY_SCREEN : HudConfig.VANILLA_HUT_SCREEN).get();
        }
        catch (final IllegalStateException e)
        {
            // config not loaded yet
            return true;
        }
    }

    public static void onScreenOpening(final ScreenEvent.Opening event)
    {
        final Screen screen = event.getNewScreen();
        if (failed || !(screen instanceof BOScreen boScreen))
        {
            return;
        }
        final BOWindow window = boScreen.getWindow();
        if (window == original)
        {
            original = null;
            return;
        }
        try
        {
            if (window.getClass() == WindowHutNameEntry.class)
            {
                if (field(WindowHutNameEntry.class, "building", window) instanceof IBuildingView view && handles(view))
                {
                    event.setNewScreen(new RenameScreen(view));
                }
                return;
            }
            if (window.getClass() == WindowField.class)
            {
                // The farm field's scarecrow (a block in the world): follows the hut screen setting.
                if (field(WindowField.class, "tileEntityScarecrow", window) instanceof TileEntityScarecrow scarecrow && configEnabled(false))
                {
                    event.setNewScreen(new FieldScreen(scarecrow, null));
                }
                return;
            }
            if (window.getClass() == WindowPlantationField.class)
            {
                if (field(WindowPlantationField.class, "tileEntityPlantationField", window) instanceof AbstractTileEntityPlantationField tile && configEnabled(false))
                {
                    event.setNewScreen(new PlantationFieldScreen(tile, null));
                }
                return;
            }
            if (window.getClass() == WindowBannerRallyGuards.class)
            {
                // The guards' rally banner (an item, not a hut): follows the hut screen setting.
                if (field(WindowBannerRallyGuards.class, "banner", window) instanceof ItemStack banner && configEnabled(false))
                {
                    event.setNewScreen(new RallyBannerScreen(banner));
                }
                return;
            }
            if (window.getClass() == DOCraftingWindow.class)
            {
                // Teaching an Architect's Cutter recipe, opened by the recipes page of Domum Ornamentum crafters.
                if (field(AbstractModuleWindow.class, "moduleView", window) instanceof DOCraftingModuleView module && handles(module.getBuildingView()))
                {
                    event.setNewScreen(new DomumTeachScreen(module));
                }
                return;
            }
            if (window.getClass() == WindowResourceList.class)
            {
                // The resource scroll: the builder's resources page, with the warehouse snapshot the scroll carries.
                if (field(WindowResourceList.class, "builder", window) instanceof IBuildingView view && handles(view)
                      && view.getModuleViewByType(BuildingResourcesModuleView.class) instanceof BuildingResourcesModuleView resources)
                {
                    final Object snapshot = field(WindowResourceList.class, "warehouseSnapshot", window);
                    @SuppressWarnings("unchecked") final Map<String, Integer> map = snapshot instanceof Map<?, ?> m ? (Map<String, Integer>) m : null;
                    ResourcesTab.pendingSnapshot = map;
                    // Opened from the scroll: no "give" button, the player has to go to the hut.
                    ResourcesTab.pendingFromScroll = true;
                    event.setNewScreen(new HutScreen(view, HutPage.module(resources), window));
                }
                return;
            }
            if (window.getClass() == WindowBuildBuilding.class)
            {
                // Build options, of huts and of the town hall.
                if (field(WindowBuildBuilding.class, "building", window) instanceof IBuildingView view && view.getColony() != null && configEnabled(false))
                {
                    event.setNewScreen(new BuildScreen(view));
                }
                return;
            }
            final IBuildingView building;
            final HutPage page;
            if (window.getClass() == WindowPostBoxMain.class)
            {
                // The post box's own main window: its request page is the home page here.
                building = field(WindowPostBoxMain.class, "postBoxView", window) instanceof IBuildingView view ? view : null;
                page = HutPage.HOME;
            }
            else if (window.getClass() == WindowsBarracksSpies.class)
            {
                // The barracks' spies dialog: its button is on the barracks' home page.
                building = field(WindowsBarracksSpies.class, "buildingView", window) instanceof IBuildingView view ? view : null;
                page = HutPage.HOME;
            }
            else if (window instanceof WindowHireWorker hire)
            {
                building = field(WindowHireWorker.class, "building", hire) instanceof IBuildingView view ? view : null;
                page = HutPage.WORKERS;
            }
            else if (window.getClass() == WindowHutAllInventory.class)
            {
                // Every container of the hut: the storage page (the warehouse's own page, without its rack options).
                building = field(WindowHutAllInventory.class, "building", window) instanceof IBuildingView view ? view : null;
                page = HutPage.STORAGE;
            }
            else if (window.getClass() == WindowAssignCitizen.class)
            {
                // A house's residents: the workers' page of houses.
                building = field(WindowAssignCitizen.class, "building", window) instanceof IBuildingView view ? view : null;
                page = HutPage.WORKERS;
            }
            else if (window instanceof AbstractBuildingWindow<?> buildingWindow)
            {
                building = field(AbstractBuildingWindow.class, "buildingView", buildingWindow) instanceof IBuildingView view ? view : null;
                if (window.getClass() == ConnectionModuleWindow.class && building != null)
                {
                    // The gatehouse's connected colonies: its module page, or a screen of its own for outsiders.
                    if (Boolean.TRUE.equals(field(ConnectionModuleWindow.class, "externalPlayer", window)))
                    {
                        if (configEnabled(false))
                        {
                            event.setNewScreen(new ConnectionsScreen(building));
                        }
                        return;
                    }
                    if (building.getModuleViewByType(ColonyConnectionModuleView.class) instanceof ColonyConnectionModuleView connections && handles(building))
                    {
                        event.setNewScreen(new HutScreen(building, HutPage.module(connections), window));
                    }
                    return;
                }
                // The assignment window (the warehouse's couriers) only lists the assigned citizens: the home page does.
                if (window.getClass() == WindowHutWorkerModulePlaceholder.class || window.getClass() == WindowHutMinPlaceholder.class
                      || window.getClass() == WindowHutBuilderModule.class && guideRead() || window.getClass() == SpecialAssignmentModuleWindow.class
                      || window.getClass() == WindowHutLiving.class || window.getClass() == WindowBarracksBuilding.class)
                {
                    page = HutPage.HOME;
                }
                else if (window instanceof AbstractModuleWindow<?> moduleWindow && MODULE_WINDOWS.contains(window.getClass())
                           && field(AbstractModuleWindow.class, "moduleView", moduleWindow) instanceof IBuildingModuleView module
                           && HutScreen.supports(module))
                {
                    page = HutPage.module(module);
                }
                else
                {
                    return;
                }
            }
            else
            {
                return;
            }
            if (!handles(building))
            {
                return;
            }
            event.setNewScreen(new HutScreen(building, page, window));
        }
        catch (final LinkageError | RuntimeException | ReflectiveOperationException e)
        {
            report(e);
        }
    }

    /** Same check as WindowHutBuilderModule: the guide advancement is done. */
    private static boolean guideRead()
    {
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null)
        {
            return false;
        }
        final AdvancementHolder guide = player.connection.getAdvancements().get(BUILDER_GUIDE);
        if (guide == null)
        {
            return false;
        }
        // The progress map is private in vanilla (MineColonies widens it with an access transformer).
        try
        {
            return field(ClientAdvancements.class, "progress", player.connection.getAdvancements()) instanceof Map<?, ?> map
                     && map.get(guide) instanceof AdvancementProgress progress && progress.isDone();
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            return false;
        }
    }

    /** Every hut but the town hall, which has its own screen; the university follows its own setting. */
    private static boolean handles(@Nullable final IBuildingView building)
    {
        if (building == null || building.getColony() == null || building.getBuildingType() == ModBuildings.townHall.get())
        {
            return false;
        }
        return configEnabled(building.getBuildingType() == ModBuildings.university.get());
    }

    @Nullable
    private static Object field(final Class<?> owner, final String name, final Object instance) throws ReflectiveOperationException
    {
        final Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    static void report(final Throwable e)
    {
        if (!failed)
        {
            failed = true;
            MineColoniesVanillaTweaks.LOGGER.warn("Vanilla hut screen unavailable, falling back to MineColonies' windows", e);
        }
    }

    /**
     * Opens a page through MineColonies' own window, which is then swapped for {@link HutScreen} (its open logic still
     * runs). Pages without a screen here open MineColonies' window.
     */
    public static void open(final IBuildingView building, final HutPage page)
    {
        switch (page.kind())
        {
            case HOME -> building.getWindow().open();
            case STORAGE -> new WindowHutAllInventory(building, building.getWindow()).open();
            case WORKERS ->
            {
                if (building instanceof LivingBuildingView home)
                {
                    new WindowAssignCitizen(building.getColony(), home).open();
                }
                else
                {
                    new WindowHireWorker(building.getColony(), building.getPosition()).open();
                }
            }
            case MODULE ->
            {
                if (page.module() != null)
                {
                    page.module().getWindow().open();
                }
            }
        }
    }

    /** Opens a MineColonies window without swapping it (for what the vanilla pages don't cover). */
    static void openOriginal(final BOWindow window)
    {
        original = window;
        window.open();
    }
}
