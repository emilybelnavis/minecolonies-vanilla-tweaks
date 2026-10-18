package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.HiringMode;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.mojang.datafixers.util.Pair;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBarracks;
import com.minecolonies.api.colony.buildings.modules.IBuildingModuleView;
import com.minecolonies.api.colony.buildings.modules.settings.ISetting;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.colony.managers.interfaces.ITravellingManager;
import com.minecolonies.api.colony.managers.interfaces.views.IRegisteredStructureManagerView;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IStackBasedTask;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.IDeliverymanRequestable;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.crafting.ModCraftingTypes;
import com.minecolonies.api.crafting.registry.CraftingType;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenSkillHandler;
import com.minecolonies.api.research.IResearchEffectManager;
import com.minecolonies.api.research.IResearchManager;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.colony.buildings.modules.settings.BlockSetting;
import com.minecolonies.core.colony.buildings.modules.settings.BoolSetting;
import com.minecolonies.core.colony.buildings.modules.settings.CrafterRecipeSetting;
import com.minecolonies.core.colony.buildings.modules.settings.GuardTaskSetting;
import com.minecolonies.core.colony.buildings.modules.settings.IntSetting;
import com.minecolonies.core.colony.buildings.modules.settings.SettingKey;
import com.minecolonies.core.colony.buildings.moduleviews.BuildingStatisticsModuleView;
import com.minecolonies.api.colony.workorders.IWorkOrderView;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.core.colony.buildings.modules.settings.StringSetting;
import com.minecolonies.core.colony.buildings.moduleviews.BuildingResourcesModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.CourierAssignmentModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.CombinedHiringLimitModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.EntityListModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.ToolModuleView;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingGuardTower;
import com.minecolonies.core.colony.buildings.modules.settings.GuardPatrolModeSetting;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.core.colony.buildings.moduleviews.WarehouseOptionsModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WarehouseRequestTaskModuleView;
import com.minecolonies.core.colony.jobs.views.DmanJobView;
import com.minecolonies.api.colony.requestsystem.data.IRequestSystemDeliveryManJobDataStore;
import com.minecolonies.core.colony.buildings.moduleviews.CraftingModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.EnchanterStationsModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.GraveyardManagementModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WorkOrderListModuleView;
import com.minecolonies.core.colony.buildings.utils.BuildingBuilderResource;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.colony.buildings.moduleviews.ItemListModuleView;
import com.minecolonies.core.colony.buildings.modules.HomeBuildingModule;
import com.minecolonies.core.colony.buildings.moduleviews.LivingBuildingModuleView;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.colony.buildingextensions.PlantationField;
import com.minecolonies.core.colony.buildings.moduleviews.FieldsModuleView;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingFarmer;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingPlantation;
import com.minecolonies.core.colony.buildings.moduleviews.MinerGuardAssignModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.ExpeditionLogModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.RestaurantMenuModuleView;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import com.minecolonies.core.colony.buildings.moduleviews.MinerLevelManagementModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.MinimumStockModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.RequestTaskModuleView;
import com.minecolonies.core.colony.requestsystem.requests.AbstractRequest;
import com.minecolonies.core.colony.buildings.moduleviews.SettingsModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.university.UniversityPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.resources.language.I18n;
import fr.mcvanillatweaks.client.citizen.DropdownButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Dev preview of the shared hut screen (source set "preview", run with ./gradlew runPreview after the university one):
 * opens the pages of a proxy-mocked farmer's hut (home, workers, settings, statistics, recipes, minimum stock and its
 * item picker, tasks, an item list), the home page of a hut without workers, the builder's pages and the warehouse's
 * (home with couriers, storage, storage searched), saves run/screenshots/hut_*.png, then quits.
 */
@EventBusSubscriber(modid = MineColoniesVanillaTweaks.MOD_ID, value = Dist.CLIENT)
public final class HutPreview
{
    private static final boolean ENABLED = Boolean.getBoolean("mcvanillatweaks.preview");
    private static final String[] NAMES = {"home", "workers", "workers_employed", "settings", "settings_scrolled", "stats", "home_noworkers",
      "recipes", "stock", "stock_add", "tasks", "itemlist", "builder_home", "builder_resources", "builder_workorders", "warehouse_home",
      "warehouse_storage", "warehouse_search", "guard_home", "guard_workers", "guard_patrol", "guard_hostiles",
      "mine_home", "mine_levels", "mine_guards", "house_home", "house_residents", "restaurant_home", "restaurant_menu",
      "farm_home", "farm_fields", "plantation_fields", "build", "build_builders", "build_confirm", "build_unbuilt", "graveyard_graves", "enchanter_gather", "storage", "rename", "info", "postbox", "clipboard", "buildtool", "packs", "postbox_requests", "cutter", "build_materials", "clipboard_dashboard", "clipboard_buildings", "clipboard_citizens", "tasks_crafter_idle", "teach_crafting", "teach_crafting_filled", "teach_furnace", "teach_brewing", "teach_domum", "teach_domum_picker", "colonymap", "colonymap_ranking", "barracks", "rally_banner", "questlog_inprogress", "questlog_available", "questlog_finished", "field", "field_seeds", "plantation_field", "hud", "nether_expedition", "nether_expedition_killed", "nether_expedition_none", "colony_rename", "mercenaries", "dialog_covenant", "dialog_old_colony", "dialog_guide", "connections", "banner", "decoration", "decoration_build", "chest", "select_res", "undo_redo", "config", "config_screens", "config_hud", "config_hud_layout", "config_hud_content", "config_beams", "names", "names_search", "names_edit_list", "names_edit", "names_builtin", "names_icon", "guide", "guide_plan", "multipiston", "clipboard_requests_end"};

    private static int index = -1;
    private static int step;
    private static boolean capture;
    private static final List<Supplier<Screen>> SCREENS = new ArrayList<>();
    private static final List<IBuildingModuleView> MODULES = new ArrayList<>();
    private static final List<IBuildingModuleView> BUILDER_MODULES = new ArrayList<>();
    private static final List<IBuildingModuleView> WAREHOUSE_MODULES = new ArrayList<>();
    private static final List<IBuildingModuleView> GUARD_MODULES = new ArrayList<>();
    private static final List<IBuildingModuleView> MINE_MODULES = new ArrayList<>();
    private static final List<IBuildingModuleView> HOUSE_MODULES = new ArrayList<>();
    private static final List<IBuildingModuleView> RESTAURANT_MODULES = new ArrayList<>();
    private static final List<IBuildingModuleView> PLANTATION_MODULES = new ArrayList<>();
    private static final List<IBuildingModuleView> GRAVEYARD_MODULES = new ArrayList<>();
    private static final List<IBuildingModuleView> ENCHANTER_MODULES = new ArrayList<>();
    private static final List<IBuildingModuleView> NETHER_MODULES = new ArrayList<>();
    private static IBuildingView nether;
    /** The colony's fields and plantation plots. */
    private static final List<IBuildingExtension> FIELDS = new ArrayList<>();
    /** The colony's buildings by position, for the pages that look through the colony (the mine's guard huts). */
    private static final Map<BlockPos, IBuildingView> BUILDINGS = new LinkedHashMap<>();
    /** A crafter's empty task queue (its "no order" explanation). */
    private static IBuildingModuleView crafterIdle;

    private HutPreview()
    {
    }

    @SubscribeEvent
    public static void onClientTick(final ClientTickEvent.Post event)
    {
        final Minecraft mc = Minecraft.getInstance();
        if (!ENABLED || capture)
        {
            return;
        }
        if (index == -1 && UniversityPreview.finished)
        {
            MineColoniesVanillaTweaks.LOGGER.info("Hut preview: starting");
            try
            {
                sample();
            }
            catch (final ReflectiveOperationException e)
            {
                throw new IllegalStateException(e);
            }
            final IBuildingView sampleFarm = farm;
            SCREENS.add(() -> new HutScreen(sampleFarm, HutPage.HOME, null));
            SCREENS.add(() -> new HutScreen(sampleFarm, HutPage.WORKERS, null));
            SCREENS.add(() -> new HutScreen(sampleFarm, HutPage.WORKERS, null));
            SCREENS.add(() -> new HutScreen(sampleFarm, HutPage.module(MODULES.get(1)), null));
            SCREENS.add(() -> new HutScreen(sampleFarm, HutPage.module(MODULES.get(1)), null));
            SCREENS.add(() -> new HutScreen(sampleFarm, HutPage.module(MODULES.get(2)), null));
            SCREENS.add(() -> new HutScreen(postBox, HutPage.HOME, null));
            SCREENS.add(() -> new HutScreen(sampleFarm, HutPage.module(MODULES.get(4)), null));
            SCREENS.add(() -> new HutScreen(sampleFarm, HutPage.module(MODULES.get(3)), null));
            SCREENS.add(() -> new HutScreen(sampleFarm, HutPage.module(MODULES.get(3)), null));
            SCREENS.add(() -> new HutScreen(sampleFarm, HutPage.module(MODULES.get(5)), null));
            SCREENS.add(() -> new HutScreen(sampleFarm, HutPage.module(MODULES.get(6)), null));
            final IBuildingView sampleBuilder = builder;
            SCREENS.add(() -> new HutScreen(sampleBuilder, HutPage.HOME, null));
            SCREENS.add(() -> new HutScreen(sampleBuilder, HutPage.module(BUILDER_MODULES.get(1)), null));
            SCREENS.add(() -> new HutScreen(sampleBuilder, HutPage.module(BUILDER_MODULES.get(2)), null));
            final IBuildingView sampleWarehouse = warehouse;
            SCREENS.add(() -> new HutScreen(sampleWarehouse, HutPage.HOME, null));
            SCREENS.add(() -> new HutScreen(sampleWarehouse, HutPage.module(WAREHOUSE_MODULES.get(1)), null));
            SCREENS.add(() -> new HutScreen(sampleWarehouse, HutPage.module(WAREHOUSE_MODULES.get(1)), null));
            final IBuildingView sampleTower = guardTower;
            SCREENS.add(() -> new HutScreen(sampleTower, HutPage.HOME, null));
            SCREENS.add(() -> new HutScreen(sampleTower, HutPage.WORKERS, null));
            SCREENS.add(() -> new HutScreen(sampleTower, HutPage.module(GUARD_MODULES.get(2)), null));
            SCREENS.add(() -> new HutScreen(sampleTower, HutPage.module(GUARD_MODULES.get(3)), null));
            final IBuildingView sampleMine = mine;
            SCREENS.add(() -> new HutScreen(sampleMine, HutPage.HOME, null));
            SCREENS.add(() -> new HutScreen(sampleMine, HutPage.module(MINE_MODULES.get(1)), null));
            SCREENS.add(() -> new HutScreen(sampleMine, HutPage.module(MINE_MODULES.get(2)), null));
            final IBuildingView sampleHouse = house;
            SCREENS.add(() -> new HutScreen(sampleHouse, HutPage.HOME, null));
            SCREENS.add(() -> new HutScreen(sampleHouse, HutPage.WORKERS, null));
            final IBuildingView sampleRestaurant = restaurant;
            SCREENS.add(() -> new HutScreen(sampleRestaurant, HutPage.HOME, null));
            SCREENS.add(() -> new HutScreen(sampleRestaurant, HutPage.module(RESTAURANT_MODULES.get(1)), null));
            SCREENS.add(() -> new HutScreen(sampleFarm, HutPage.HOME, null));
            SCREENS.add(() -> new HutScreen(sampleFarm, HutPage.module(MODULES.get(MODULES.size() - 1)), null));
            final IBuildingView samplePlantation = plantation;
            SCREENS.add(() -> new HutScreen(samplePlantation, HutPage.module(PLANTATION_MODULES.get(1)), null));
            final IBuildingView upgrade = buildSample(farm, 2, "com.minecolonies.core.gui.residence.warning.2");
            final IBuildingView unbuilt = buildSample(farm, 0, "");
            SCREENS.add(() -> new BuildScreen(upgrade));
            SCREENS.add(() -> new BuildScreen(upgrade));
            SCREENS.add(() -> new BuildScreen(upgrade));
            SCREENS.add(() -> new BuildScreen(unbuilt));
            final IBuildingView sampleGraveyard = graveyard;
            SCREENS.add(() -> new HutScreen(sampleGraveyard, HutPage.module(GRAVEYARD_MODULES.get(0)), null));
            final IBuildingView sampleEnchanter = enchanter;
            SCREENS.add(() -> new HutScreen(sampleEnchanter, HutPage.module(ENCHANTER_MODULES.get(0)), null));
            SCREENS.add(() -> new HutScreen(sampleFarm, HutPage.STORAGE, null));
            SCREENS.add(() -> new RenameScreen(sampleFarm));
            SCREENS.add(() -> new InfoScreen(sampleFarm));
            final IBuildingView samplePostBox = realPostBox;
            SCREENS.add(() -> new HutScreen(samplePostBox, HutPage.HOME, null));
            final IColonyView sampleColony = farm.getColony();
            SCREENS.add(() -> new fr.mcvanillatweaks.client.townhall.ClipboardScreen(sampleColony, false,
              fr.mcvanillatweaks.client.townhall.ClipboardScreen.Page.REQUESTS));
            SCREENS.add(fr.mcvanillatweaks.client.buildtool.BuildToolPreview::buildTool);
            SCREENS.add(fr.mcvanillatweaks.client.buildtool.BuildToolPreview::packs);
            SCREENS.add(() -> new HutScreen(samplePostBox, HutPage.HOME, null));
            SCREENS.add(fr.mcvanillatweaks.client.domum.CutterPreview::screen);
            SCREENS.add(() -> new BuildScreen(upgrade));
            SCREENS.add(() -> new fr.mcvanillatweaks.client.townhall.ClipboardScreen(sampleColony, false,
              fr.mcvanillatweaks.client.townhall.ClipboardScreen.Page.DASHBOARD));
            SCREENS.add(() -> new fr.mcvanillatweaks.client.townhall.ClipboardScreen(sampleColony, false,
              fr.mcvanillatweaks.client.townhall.ClipboardScreen.Page.BUILDINGS));
            SCREENS.add(() -> new fr.mcvanillatweaks.client.townhall.ClipboardScreen(sampleColony, false,
              fr.mcvanillatweaks.client.townhall.ClipboardScreen.Page.CITIZENS));
            SCREENS.add(() -> new HutScreen(sampleFarm, HutPage.module(crafterIdle), null));
            SCREENS.add(() -> TeachPreview.crafting(sampleFarm, false));
            SCREENS.add(() -> TeachPreview.crafting(sampleFarm, true));
            SCREENS.add(() -> TeachPreview.furnace(sampleFarm));
            SCREENS.add(() -> TeachPreview.brewing(sampleFarm));
            SCREENS.add(() -> TeachPreview.domum(sampleFarm, false));
            SCREENS.add(() -> TeachPreview.domum(sampleFarm, true));
            SCREENS.add(() -> fr.mcvanillatweaks.client.townhall.ColonyMapPreview.map(sampleColony));
            SCREENS.add(() -> fr.mcvanillatweaks.client.townhall.ColonyMapPreview.ranking(sampleColony));
            final IBuildingView sampleBarracks = barracks;
            SCREENS.add(() -> new HutScreen(sampleBarracks, HutPage.HOME, null));
            SCREENS.add(HutPreview::rallyBanner);
            SCREENS.add(() -> fr.mcvanillatweaks.client.townhall.QuestLogPreview.screen(sampleColony, "IN_PROGRESS"));
            SCREENS.add(() -> fr.mcvanillatweaks.client.townhall.QuestLogPreview.screen(sampleColony, "AVAILABLE"));
            SCREENS.add(() -> fr.mcvanillatweaks.client.townhall.QuestLogPreview.screen(sampleColony, "FINISHED"));
            SCREENS.add(() -> fieldScreen(sampleColony, false));
            SCREENS.add(() -> fieldScreen(sampleColony, true));
            SCREENS.add(() -> plantationFieldScreen(sampleColony));
            SCREENS.add(fr.mcvanillatweaks.client.hud.HudPreview::screen);
            final IBuildingView sampleNether = nether;
            SCREENS.add(() -> new HutScreen(sampleNether, HutPage.module(NETHER_MODULES.get(0)), null));
            SCREENS.add(() -> {
                ((ExpeditionLogModuleView) NETHER_MODULES.get(0)).getLog().setKilled();
                return new HutScreen(sampleNether, HutPage.module(NETHER_MODULES.get(0)), null);
            });
            SCREENS.add(() -> {
                ((ExpeditionLogModuleView) NETHER_MODULES.get(0)).getLog().reset();
                return new HutScreen(sampleNether, HutPage.module(NETHER_MODULES.get(0)), null);
            });
            SCREENS.add(() -> new fr.mcvanillatweaks.client.townhall.ColonyRenameScreen(sampleColony));
            SCREENS.add(() -> new fr.mcvanillatweaks.client.townhall.MercenaryScreen(sampleColony));
            SCREENS.add(fr.mcvanillatweaks.client.dialog.DialogPreview::covenant);
            SCREENS.add(fr.mcvanillatweaks.client.dialog.DialogPreview::oldColony);
            SCREENS.add(fr.mcvanillatweaks.client.dialog.DialogPreview::guide);
            SCREENS.add(fr.mcvanillatweaks.client.dialog.DialogPreview::connections);
            SCREENS.add(fr.mcvanillatweaks.client.dialog.DialogPreview::banner);
            SCREENS.add(fr.mcvanillatweaks.client.dialog.DialogPreview::decorationController);
            SCREENS.add(fr.mcvanillatweaks.client.dialog.DialogPreview::decorationBuild);
            SCREENS.add(fr.mcvanillatweaks.client.dialog.DialogPreview::chest);
            SCREENS.add(fr.mcvanillatweaks.client.dialog.DialogPreview::selectRes);
            SCREENS.add(fr.mcvanillatweaks.client.dialog.DialogPreview::undoRedo);
            fr.mcvanillatweaks.client.locate.CompletionPreview.check();
            // The settings: NeoForge opens the only config file at once; variants open its sections at steps 3 and 6.
            for (int i = 0; i < 6; i++)
            {
                SCREENS.add(() -> fr.mcvanillatweaks.client.ModSettings.create(MineColoniesVanillaTweaks.container(), new net.minecraft.client.gui.screens.TitleScreen()));
            }
            SCREENS.add(fr.mcvanillatweaks.client.names.NamesPreview::lists);
            SCREENS.add(fr.mcvanillatweaks.client.names.NamesPreview::search);
            SCREENS.add(() -> fr.mcvanillatweaks.client.names.NamesPreview.edit(true, true));
            SCREENS.add(() -> fr.mcvanillatweaks.client.names.NamesPreview.edit(true, false));
            SCREENS.add(() -> fr.mcvanillatweaks.client.names.NamesPreview.edit(false, false));
            SCREENS.add(fr.mcvanillatweaks.client.names.NamesPreview::icon);
            SCREENS.add(() -> new fr.mcvanillatweaks.client.townhall.ColonyGuideScreen(sampleColony, false));
            SCREENS.add(() -> new fr.mcvanillatweaks.client.townhall.ColonyGuideScreen(sampleColony, true));
            SCREENS.add(() -> new fr.mcvanillatweaks.client.dialog.MultiPistonScreen(net.minecraft.core.BlockPos.ZERO,
              net.minecraft.core.Direction.UP, net.minecraft.core.Direction.DOWN, 5, 2));
            SCREENS.add(() -> new fr.mcvanillatweaks.client.townhall.ClipboardScreen(sampleColony, false,
              fr.mcvanillatweaks.client.townhall.ClipboardScreen.Page.REQUESTS));
            index = 0;
            step = 0;
        }
        if (index < 0)
        {
            return;
        }
        if (index >= SCREENS.size())
        {
            index = Integer.MIN_VALUE;
            mc.stop();
            return;
        }
        // A filtered run skips unwanted captures (the throwaway last one is always kept).
        while (step == 0 && index < SCREENS.size() - 1 && !fr.mcvanillatweaks.client.PreviewFilter.wants("hut_" + NAMES[index]))
        {
            index++;
        }
        step++;
        if (step == 1)
        {
            mc.setScreen(SCREENS.get(index).get());
        }
        else if (step == 3 && mc.screen instanceof BuildScreen build)
        {
            // Variants: the builders' drop-down open, the upgrade confirmation.
            if ("build_materials".equals(NAMES[index]))
            {
                final String materials = I18n.get("mcvanillatweaks.build.tab.materials");
                build.children().stream().filter(child -> child instanceof Button button && button.getMessage().getString().startsWith(materials)).findFirst()
                  .ifPresent(button -> ((Button) button).onPress());
            }
            else if ("build_builders".equals(NAMES[index]))
            {
                build.children().stream().filter(DropdownButton.class::isInstance).findFirst().ifPresent(button -> ((DropdownButton) button).onPress());
            }
            else if ("build_confirm".equals(NAMES[index]))
            {
                final String upgrade = I18n.get("com.minecolonies.coremod.gui.workerhuts.upgrade");
                build.children().stream().filter(child -> child instanceof Button button && button.getMessage().getString().equals(upgrade)).findFirst()
                  .ifPresent(button -> ((Button) button).onPress());
            }
        }
        else if (step == 3 && mc.screen instanceof HutScreen screen)
        {
            // Variants: the employed citizens shown, the settings list scrolled down.
            if ("workers_employed".equals(NAMES[index]))
            {
                screen.children().stream().filter(Checkbox.class::isInstance).findFirst().ifPresent(box -> ((Checkbox) box).onPress());
            }
            else if ("stock_add".equals(NAMES[index]) && screen.tab() instanceof StockTab stock)
            {
                stock.previewPicker("log", new ItemStack(Items.OAK_LOG));
            }
            else if ("warehouse_search".equals(NAMES[index]))
            {
                screen.children().stream().filter(EditBox.class::isInstance).findFirst().ifPresent(box -> ((EditBox) box).setValue("oak"));
            }
            else if ("postbox_requests".equals(NAMES[index]) && screen.tab() instanceof PostBoxTab postBoxTab)
            {
                postBoxTab.previewShowRequests();
            }
            else if ("settings_scrolled".equals(NAMES[index]))
            {
                for (int i = 0; i < 5; i++)
                {
                    screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, -1);
                }
            }
        }
        else if ((step == 3 || step == 6) && NAMES[index].startsWith("config_"))
        {
            // Section buttons, in order: screens, HUD, beams; inside the HUD: layout, content.
            final int[] path = switch (NAMES[index])
            {
                case "config_screens" -> new int[] {0};
                case "config_hud" -> new int[] {1};
                case "config_hud_layout" -> new int[] {1, 0};
                case "config_hud_content" -> new int[] {1, 1};
                case "config_beams" -> new int[] {2};
                default -> new int[0];
            };
            final int depth = step == 3 ? 0 : 1;
            if (depth < path.length && mc.screen != null)
            {
                final List<Button> open = new ArrayList<>();
                sectionButtons(mc.screen, open);
                if (path[depth] < open.size())
                {
                    open.get(path[depth]).onPress();
                }
            }
        }
        else if (step == 20)
        {
            capture = true;
        }
    }

    @SubscribeEvent
    public static void onScreenRender(final ScreenEvent.Render.Post event)
    {
        if (!capture || index < 0 || index >= NAMES.length)
        {
            return;
        }
        capture = false;
        final Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "hut_" + NAMES[index] + ".png", mc.getMainRenderTarget(),
          message -> MineColoniesVanillaTweaks.LOGGER.info("Hut preview: {}", message.getString()));
        index++;
        step = 0;
    }

    /** The "Open..." buttons of a NeoForge config section screen, in screen order. */
    private static void sectionButtons(final net.minecraft.client.gui.components.events.ContainerEventHandler parent, final List<Button> out)
    {
        final String open = I18n.get("mcvanillatweaks.configuration.screens.button");
        for (final net.minecraft.client.gui.components.events.GuiEventListener child : parent.children())
        {
            if (child instanceof Button button && button.getMessage().getString().startsWith(open))
            {
                out.add(button);
            }
            else if (child instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container)
            {
                sectionButtons(container, out);
            }
        }
    }

    // ------------------------------------------------------------------ sample data

    private static IBuildingView farm;
    private static IBuildingView postBox;
    private static IBuildingView builder;
    private static IBuildingView warehouse;
    private static IBuildingView guardTower;
    private static IBuildingView mine;
    private static IBuildingView house;
    private static IBuildingView restaurant;
    private static IBuildingView plantation;
    private static IBuildingView graveyard;
    private static IBuildingView realPostBox;
    private static IBuildingView enchanter;
    private static IBuildingView barracks;
    private static final List<Pair<BlockPos, AbstractBuildingGuards.View>> RALLY = new ArrayList<>();
    /** Every skill at 10, for the sample couriers and guards. */
    private static final ICitizenSkillHandler FLAT_SKILLS = proxy(ICitizenSkillHandler.class, (m, a) -> m.getName().equals("getLevel") ? 10 : null);

    private static final BlockPos FARM_POS = new BlockPos(100, 64, 100);

    /** id, name, job (empty: unemployed), works at the farm, home distance (-1: homeless), paused, skill seed. */
    private static final Object[][] CITIZENS = {
      {1, "Alice Martin", "com.minecolonies.job.farmer", true, 12, false, 3},
      {2, "Bruno Petit", "com.minecolonies.job.farmer", true, 0, true, 7},
      {3, "Chloé Durand", "", false, 30, false, 11},
      {4, "Denis Moreau", "", false, -1, false, 5},
      {5, "Emma Laurent", "", false, 75, false, 9},
      {6, "Félix Girard", "com.minecolonies.job.builder", false, 20, false, 2},
      {7, "Gaëlle Roux", "com.minecolonies.job.fisherman", false, 55, false, 13}};

    private static void sample() throws ReflectiveOperationException
    {
        final IResearchEffectManager effects = proxy(IResearchEffectManager.class, (m, a) -> null);
        final IResearchManager research = proxy(IResearchManager.class, (m, a) -> m.getName().equals("getResearchEffects") ? effects : null);
        final ITravellingManager travelling = proxy(ITravellingManager.class, (m, a) -> null);
        final Map<Integer, ICitizenDataView> citizens = new LinkedHashMap<>();
        final IRegisteredStructureManagerView buildings = proxy(IRegisteredStructureManagerView.class, (m, a) -> switch (m.getName())
        {
            case "getBuildings" -> BUILDINGS;
            case "getBuilding" -> a.length == 1 ? BUILDINGS.get(a[0]) : null;
            case "getBuildingExtensions" -> FIELDS.stream().filter(field -> matches(a[0], field)).toList();
            default -> null;
        });
        final Map<IToken<?>, IRequest<?>> requests = new LinkedHashMap<>();
        final Map<IToken<?>, Object> resolvers = new LinkedHashMap<>();
        final IRequestManager requestManager = proxy(IRequestManager.class, (m, a) -> switch (m.getName())
        {
            case "getRequestForToken" -> requests.get(a[0]);
            case "getResolverForRequest" -> resolvers.get(a[0]);
            default -> null;
        });
        final IColonyView colony = proxy(IColonyView.class, (m, a) -> switch (m.getName())
        {
            case "getCitizens" -> citizens;
            case "getCitizen" -> citizens.get((Integer) a[0]);
            case "getResearchManager" -> research;
            case "getTravellingManager" -> travelling;
            case "getClientBuildingManager" -> buildings;
            case "getRequestManager" -> requestManager;
            case "getWorkOrders" -> WORK_ORDERS;
            case "getWorkOrder" -> WORK_ORDERS.stream().filter(order -> order.getID() == (Integer) a[0]).findFirst().orElse(null);
            case "getDimension" -> Level.OVERWORLD;
            case "getID" -> 1;
            case "getName" -> "Exempleville";
            case "getDay" -> 40;
            case "getCitizenCountLimit" -> 25;
            case "isCoordInColony" -> false;
            case "isRaiding" -> true;
            case "getLastSpawnPoints" -> List.of(FARM_POS.offset(-140, 0, 30), FARM_POS.offset(40, 0, -70), FARM_POS.offset(20, 0, 25));
            case "getQuestManager" -> fr.mcvanillatweaks.client.townhall.QuestLogPreview.manager();
            default -> null;
        });
        for (final Object[] entry : CITIZENS)
        {
            final int id = (Integer) entry[0];
            final int seed = (Integer) entry[6];
            final int distance = (Integer) entry[4];
            final boolean paused = (Boolean) entry[5];
            final BlockPos work = (Boolean) entry[3] ? FARM_POS : ((String) entry[2]).isEmpty() ? null : new BlockPos(0, 64, 0);
            final BlockPos home = distance < 0 ? null : distance == 0 ? FARM_POS : FARM_POS.offset(distance, 0, 0);
            final ICitizenSkillHandler skills = proxy(ICitizenSkillHandler.class, (m, a) -> m.getName().equals("getLevel")
                                                                                            ? 3 + (((Skill) a[0]).ordinal() * 7 + seed * 5) % 28
                                                                                            : null);
            final boolean[] pausedState = {paused};
            citizens.put(id, proxy(ICitizenDataView.class, (m, a) -> switch (m.getName())
            {
                case "getId" -> id;
                case "getName" -> entry[1];
                case "getJob" -> entry[2];
                case "getWorkBuilding" -> work;
                case "getHomeBuilding" -> home;
                case "isPaused" -> pausedState[0];
                case "setPaused" ->
                {
                    pausedState[0] = (Boolean) a[0];
                    yield null;
                }
                case "getHappiness" -> 6.5D + seed % 4;
                case "getCitizenSkillHandler" -> skills;
                case "getColony" -> colony;
                default -> null;
            }));
        }

        farm = building(colony, ModBuildings.farmer.get(), "com.minecolonies.building.farmer", 3, MODULES);
        final WorkerBuildingModuleView worker = new WorkerBuildingModuleView();
        set(WorkerBuildingModuleView.class, worker, "hiringMode", HiringMode.DEFAULT);
        set(WorkerBuildingModuleView.class, worker, "maxInhabitants", 3);
        set(WorkerBuildingModuleView.class, worker, "jobEntry", ModJobs.farmer.get());
        set(WorkerBuildingModuleView.class, worker, "primary", Skill.Stamina);
        set(WorkerBuildingModuleView.class, worker, "secondary", Skill.Athletics);
        @SuppressWarnings("unchecked") final Set<Integer> ids = (Set<Integer>) get(WorkerBuildingModuleView.class, worker, "workerIDs");
        ids.add(1);
        ids.add(2);
        MODULES.add(worker.setBuildingView(farm).setColonyView(colony));

        final SettingsModuleView settings = new SettingsModuleView();
        @SuppressWarnings("unchecked") final Map<ISettingKey<?>, ISetting<?>> map = (Map<ISettingKey<?>, ISetting<?>>) get(SettingsModuleView.class, settings, "settings");
        map.put(key(BoolSetting.class, "breeding"), new BoolSetting(true));
        map.put(key(BoolSetting.class, "fertilize"), new BoolSetting(false));
        map.put(key(GuardTaskSetting.class, "guardtask"), new GuardTaskSetting());
        map.put(key(CrafterRecipeSetting.class, "recipemode"), new CrafterRecipeSetting());
        map.put(key(IntSetting.class, "dailylimit"), new IntSetting(64));
        map.put(key(BlockSetting.class, "fillblock"), new BlockSetting((BlockItem) Items.DIRT));
        map.put(key(BoolSetting.class, "replant"), new BoolSetting(true));
        map.put(key(IntSetting.class, "maxdepth"), new IntSetting(12));
        MODULES.add(settings.setBuildingView(farm).setColonyView(colony));
        MODULES.add(new BuildingStatisticsModuleView().setBuildingView(farm).setColonyView(colony));
        final MinimumStockModuleView stock = new MinimumStockModuleView();
        @SuppressWarnings("unchecked") final List<Tuple<ItemStorage, Integer>> stockList = (List<Tuple<ItemStorage, Integer>>) get(MinimumStockModuleView.class, stock, "minimumStock");
        stockList.add(new Tuple<>(new ItemStorage(new ItemStack(Items.OAK_LOG)), 4));
        stockList.add(new Tuple<>(new ItemStorage(new ItemStack(Items.WHEAT_SEEDS)), 1));
        stockList.add(new Tuple<>(new ItemStorage(new ItemStack(Items.BONE_MEAL)), 2));
        MODULES.add(stock.setBuildingView(farm).setColonyView(colony));
        MODULES.add(crafting().setBuildingView(farm).setColonyView(colony));
        final List<IToken<?>> tokens = tasks(requests);
        MODULES.add(new RequestTaskModuleView()
        {
            @Override
            public List<IToken<?>> getTasks()
            {
                return tokens;
            }
        }.setBuildingView(farm).setColonyView(colony));
        final ItemListModuleView fuel = new ItemListModuleView("fuel", Component.translatable("com.minecolonies.coremod.request.burnable"), false,
          view -> Set.of(new ItemStorage(new ItemStack(Items.COAL)), new ItemStorage(new ItemStack(Items.CHARCOAL)), new ItemStorage(new ItemStack(Items.OAK_PLANKS)),
            new ItemStorage(new ItemStack(Items.BLAZE_ROD)), new ItemStorage(new ItemStack(Items.LAVA_BUCKET)), new ItemStorage(new ItemStack(Items.COAL_BLOCK)),
            new ItemStorage(new ItemStack(Items.STICK)), new ItemStorage(new ItemStack(Items.DRIED_KELP_BLOCK))));
        @SuppressWarnings("unchecked") final List<ItemStorage> allowed = (List<ItemStorage>) get(ItemListModuleView.class, fuel, "listsOfItems");
        allowed.add(new ItemStorage(new ItemStack(Items.COAL)));
        allowed.add(new ItemStorage(new ItemStack(Items.CHARCOAL)));
        MODULES.add(fuel.setBuildingView(farm).setColonyView(colony));
        // A crafter's task queue with no order, for the "why is it idle" explanation.
        crafterIdle = new com.minecolonies.core.colony.buildings.moduleviews.CrafterRequestTaskModuleView()
        {
            @Override
            public List<IToken<?>> getTasks()
            {
                return List.of();
            }
        }.setBuildingView(farm).setColonyView(colony);
        MODULES.add(crafterIdle);

        postBox = building(colony, ModBuildings.postBox.get(), "com.minecolonies.building.postbox", 1, List.of());
        builderSample(colony);
        warehouseSample(colony, citizens);
        guardSample(colony, citizens);
        mineSample(colony, citizens);
        houseSample(colony);
        barracksSample(colony);
        restaurantSample(colony);
        fieldsSample(colony);
        buildersSample(colony);
        graveyardSample(colony);
        netherSample(colony);
        final com.minecolonies.core.colony.buildings.workerbuildings.PostBox.View box = new com.minecolonies.core.colony.buildings.workerbuildings.PostBox.View(colony,
          FARM_POS.offset(5, 0, 5));
        set(AbstractBuildingView.class, box, "buildingType", ModBuildings.postBox.get());
        realPostBox = box;
        postBoxRequests(resolvers);
        // Build options' "what it brings": beds, storage, furnaces, crafting tables, brewing stands, size.
        UpgradeInfo.previewCounts = new int[][] {{1, 3, 0, 1, 0, 820}, {2, 5, 1, 1, 0, 1240}};
    }

    /** Post box requests: one the warehouse delivers, one the player has to bring, one waiting for a retry. */
    private static void postBoxRequests(final Map<IToken<?>, Object> resolvers)
    {
        final Object warehouse = proxy(com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver.class,
          (m, a) -> m.getName().equals("getRequesterDisplayName") ? Component.translatable("com.minecolonies.coremod.buildings.warehouse.name") : null);
        final Object player = proxy(com.minecolonies.api.colony.requestsystem.resolver.player.IPlayerRequestResolver.class, (m, a) -> null);
        final Object retrying = proxy(com.minecolonies.api.colony.requestsystem.resolver.retrying.IRetryingRequestResolver.class, (m, a) -> null);
        final Object[][] samples = {
          {new ItemStack(Items.OAK_LOG), "64 Oak Log", warehouse},
          {new ItemStack(Items.DIAMOND), "16 Diamond", player},
          {new ItemStack(Items.BLAZE_ROD), "8 Blaze Rod", retrying}};
        final List<IRequest<?>> list = new ArrayList<>();
        for (final Object[] sample : samples)
        {
            final IToken<?> token = proxy(IToken.class, (m, a) -> null);
            resolvers.put(token, sample[2]);
            list.add(proxy(IRequest.class, (m, a) -> switch (m.getName())
            {
                case "getId" -> token;
                case "getDisplayStacks" -> List.of(sample[0]);
                case "getShortDisplayString", "getLongDisplayString" -> Component.literal((String) sample[1]);
                case "getResolverToolTip" -> List.of();
                case "hasChildren" -> false;
                default -> null;
            }));
        }
        PostBoxTab.previewRequests = list;
    }

    /** A nether mine whose miner is on an expedition: equipped, three kinds of monsters beaten, some loot. */
    @SuppressWarnings("unchecked")
    private static void netherSample(final IColonyView colony) throws ReflectiveOperationException
    {
        nether = building(colony, ModBuildings.netherWorker.get(), "com.minecolonies.building.netherworker", 3, NETHER_MODULES);
        final ExpeditionLogModuleView module = new ExpeditionLogModuleView();
        set(ExpeditionLogModuleView.class, module, "unlocked", true);
        final com.minecolonies.core.colony.buildings.modules.expedition.ExpeditionLog log = module.getLog();
        log.setStatus(com.minecolonies.core.colony.buildings.modules.expedition.ExpeditionLog.Status.IN_PROGRESS);
        set(com.minecolonies.core.colony.buildings.modules.expedition.ExpeditionLog.class, log, "name", "Hugo Lefèvre");
        final java.lang.reflect.Field stats = com.minecolonies.core.colony.buildings.modules.expedition.ExpeditionLog.class.getDeclaredField("stats");
        stats.setAccessible(true);
        final Map<Object, Double> values = (Map<Object, Double>) stats.get(log);
        values.put(com.minecolonies.core.colony.buildings.modules.expedition.ExpeditionLog.StatType.HEALTH, 13.0D);
        values.put(com.minecolonies.core.colony.buildings.modules.expedition.ExpeditionLog.StatType.SATURATION, 33.0D);
        log.setEquipment(List.of(new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.CHAINMAIL_LEGGINGS),
          new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_PICKAXE)));
        for (int i = 0; i < 7; i++)
        {
            log.addMob(net.minecraft.world.entity.EntityType.ZOMBIFIED_PIGLIN);
        }
        for (int i = 0; i < 3; i++)
        {
            log.addMob(net.minecraft.world.entity.EntityType.MAGMA_CUBE);
        }
        log.addMob(net.minecraft.world.entity.EntityType.GHAST);
        log.addLoot(List.of(new ItemStack(Items.NETHERRACK, 64), new ItemStack(Items.NETHERRACK, 64), new ItemStack(Items.NETHERRACK, 20),
          new ItemStack(Items.QUARTZ, 23), new ItemStack(Items.GLOWSTONE_DUST, 12), new ItemStack(Items.GOLD_NUGGET, 31), new ItemStack(Items.MAGMA_CREAM, 4),
          new ItemStack(Items.GHAST_TEAR, 1), new ItemStack(Items.SOUL_SAND, 40), new ItemStack(Items.NETHER_WART, 9), new ItemStack(Items.BLACKSTONE, 32)));
        NETHER_MODULES.add(module.setBuildingView(nether).setColonyView(colony));
    }

    /** Two graves to collect (their area isn't loaded here) and three citizens buried; an enchanter drawing from one guard tower. */
    private static void graveyardSample(final IColonyView colony) throws ReflectiveOperationException
    {
        graveyard = building(colony, ModBuildings.graveyard.get(), "com.minecolonies.building.graveyard", 2, GRAVEYARD_MODULES);
        final GraveyardManagementModuleView graves = new GraveyardManagementModuleView();
        set(GraveyardManagementModuleView.class, graves, "graves", new ArrayList<>(List.of(FARM_POS.offset(30, 0, -20), FARM_POS.offset(-60, 2, 5))));
        set(GraveyardManagementModuleView.class, graves, "restingCitizen", new ArrayList<>(List.of("Bruno Petit", "Chloé Durand", "Gaëlle Roux")));
        GRAVEYARD_MODULES.add(graves.setBuildingView(graveyard).setColonyView(colony));

        enchanter = building(colony, ModBuildings.enchanter.get(), "com.minecolonies.building.enchanter", 3, ENCHANTER_MODULES);
        final EnchanterStationsModuleView stations = new EnchanterStationsModuleView();
        stations.getBuildingsToGatherFrom().add(new BlockPos(112, 64, 96));
        ENCHANTER_MODULES.add(stations.setBuildingView(enchanter).setColonyView(colony));
    }

    /**
     * Fields: the farm works two of its three (manual mode), a fourth field without seed is free; a plantation works a
     * sugar cane plot of two (1 / 2 plants), a cactus plot is free.
     */
    private static void fieldsSample(final IColonyView colony) throws ReflectiveOperationException
    {
        final Object[][] farmFields = {{Items.WHEAT_SEEDS, FarmField.Stage.PLANTED, 4, true}, {Items.CARROT, FarmField.Stage.HOED, 3, true},
          {Items.POTATO, FarmField.Stage.EMPTY, 4, false}, {null, FarmField.Stage.EMPTY, 2, false}};
        int offset = 8;
        for (final Object[] entry : farmFields)
        {
            final FarmField field = new FarmField(BuildingExtensionRegistries.farmField.get(), FARM_POS.offset(offset, 0, -offset / 2));
            offset += 9;
            if (entry[0] != null)
            {
                field.setSeed(new ItemStack((Item) entry[0]));
            }
            field.setFieldStage((FarmField.Stage) entry[1]);
            for (final Direction direction : Direction.Plane.HORIZONTAL)
            {
                field.setRadius(direction, (Integer) entry[2]);
            }
            if ((Boolean) entry[3])
            {
                field.setBuilding(FARM_POS);
            }
            FIELDS.add(field);
        }
        final BuildingFarmer.FarmerFieldsModuleView farmFieldsView = new BuildingFarmer.FarmerFieldsModuleView();
        set(FieldsModuleView.class, farmFieldsView, "shouldAssignFieldManually", true);
        set(FieldsModuleView.class, farmFieldsView, "maxFieldCount", 3);
        MODULES.add(farmFieldsView.setBuildingView(farm).setColonyView(colony));

        final BlockPos plantationPos = FARM_POS.offset(0, 0, 40);
        plantation = building(colony, ModBuildings.plantation.get(), "com.minecolonies.building.plantation", 2, PLANTATION_MODULES);
        final WorkerBuildingModuleView worker = new WorkerBuildingModuleView();
        set(WorkerBuildingModuleView.class, worker, "hiringMode", HiringMode.DEFAULT);
        set(WorkerBuildingModuleView.class, worker, "maxInhabitants", 1);
        set(WorkerBuildingModuleView.class, worker, "jobEntry", ModJobs.planter.get());
        set(WorkerBuildingModuleView.class, worker, "primary", Skill.Agility);
        set(WorkerBuildingModuleView.class, worker, "secondary", Skill.Dexterity);
        PLANTATION_MODULES.add(worker.setBuildingView(plantation).setColonyView(colony));
        final PlantationField cane = PlantationField.create(BuildingExtensionRegistries.plantationSugarCaneField.get(), plantationPos.offset(6, 0, 3));
        cane.setBuilding(FARM_POS);
        cane.setWorkingPositions(List.of(plantationPos, plantationPos.east(), plantationPos.east(2), plantationPos.east(3)));
        FIELDS.add(cane);
        final PlantationField cactus = PlantationField.create(BuildingExtensionRegistries.plantationCactusField.get(), plantationPos.offset(-12, 0, 8));
        cactus.setWorkingPositions(List.of(plantationPos, plantationPos.west()));
        FIELDS.add(cactus);
        final PlantationField bamboo = PlantationField.create(BuildingExtensionRegistries.plantationBambooField.get(), plantationPos.offset(20, 0, -10));
        FIELDS.add(bamboo);
        final BuildingPlantation.PlantationFieldsModuleView plots = new BuildingPlantation.PlantationFieldsModuleView();
        set(FieldsModuleView.class, plots, "shouldAssignFieldManually", false);
        set(FieldsModuleView.class, plots, "maxFieldCount", 3);
        set(BuildingPlantation.PlantationFieldsModuleView.class, plots, "maxConcurrentPlants", 2);
        PLANTATION_MODULES.add(plots.setBuildingView(plantation).setColonyView(colony));
        PLANTATION_MODULES.add(new BuildingStatisticsModuleView().setBuildingView(plantation).setColonyView(colony));
    }

    /** A level 4 barracks (a real view: the home page tests for it) with three towers, and a rally banner over them. */
    private static void barracksSample(final IColonyView colony) throws ReflectiveOperationException
    {
        final BlockPos pos = FARM_POS.offset(-30, 0, 10);
        final BuildingBarracks.View view = new BuildingBarracks.View(colony, pos)
        {
            @Override
            public String getBuildingDisplayName()
            {
                return "com.minecolonies.building.barracks";
            }

            @Override
            public int getBuildingLevel()
            {
                return 4;
            }

            @Override
            public int getBuildingMaxLevel()
            {
                return 5;
            }

            @Override
            public List<IBuildingModuleView> getAllModuleViews()
            {
                return List.of();
            }

            @Override
            public <T extends IBuildingModuleView> List<T> getModuleViews(final Class<T> type)
            {
                return List.of();
            }

            @Override
            public <T extends IBuildingModuleView> T getModuleViewByType(final Class<T> type)
            {
                return null;
            }
        };
        set(AbstractBuildingView.class, view, "buildingType", ModBuildings.barracks.get());
        barracks = view;
        BUILDINGS.put(pos, view);
        for (int i = 0; i < 3; i++)
        {
            final BlockPos towerPos = pos.offset(8 * (i - 1), 0, 9);
            final int level = 2 + i;
            final AbstractBuildingGuards.View tower = new AbstractBuildingGuards.View(colony, towerPos)
            {
                @Override
                public String getBuildingDisplayName()
                {
                    return "com.minecolonies.building.barrackstower";
                }

                @Override
                public int getBuildingLevel()
                {
                    return level;
                }

                @Override
                public BlockPos getParent()
                {
                    return pos;
                }
            };
            set(AbstractBuildingView.class, tower, "buildingType", ModBuildings.barracksTower.get());
            @SuppressWarnings("unchecked") final List<Integer> ids = (List<Integer>) get(AbstractBuildingGuards.View.class, tower, "guards");
            ids.addAll(i == 0 ? List.of(1) : List.of(1, 6));
            BUILDINGS.put(towerPos, tower);
            RALLY.add(Pair.of(towerPos, tower));
        }
        RALLY.add(Pair.of(pos.offset(60, 0, -40), null));
    }

    /** The wheat field's scarecrow (taken by the farm), with the seed picker as a variant. */
    private static Screen fieldScreen(final IColonyView colony, final boolean picking)
    {
        final com.minecolonies.core.tileentities.TileEntityScarecrow scarecrow = new com.minecolonies.core.tileentities.TileEntityScarecrow(
          FARM_POS.offset(8, 0, -4), com.minecolonies.api.blocks.ModBlocks.blockScarecrow.defaultBlockState());
        scarecrow.setFieldSize(Direction.NORTH, 3);
        scarecrow.setFieldSize(Direction.SOUTH, 6);
        scarecrow.setFieldSize(Direction.WEST, 4);
        scarecrow.setFieldSize(Direction.EAST, 4);
        FieldScreen.previewCandidates = List.of(new ItemStack(Items.WHEAT_SEEDS), new ItemStack(Items.BEETROOT_SEEDS), new ItemStack(Items.CARROT),
          new ItemStack(Items.POTATO), new ItemStack(Items.MELON_SEEDS), new ItemStack(Items.PUMPKIN_SEEDS), new ItemStack(Items.TORCHFLOWER_SEEDS),
          new ItemStack(Items.PITCHER_POD));
        final FieldScreen screen = new FieldScreen(scarecrow, colony);
        return picking ? screen.picking() : screen;
    }

    /** A plantation field block growing sugar cane (taken by the plantation) and cactus (free). */
    private static Screen plantationFieldScreen(final IColonyView colony)
    {
        final com.minecolonies.core.tileentities.TileEntityPlantationField tile = new com.minecolonies.core.tileentities.TileEntityPlantationField(
          FARM_POS.offset(0, 0, 40).offset(6, 0, 3), com.minecolonies.api.blocks.ModBlocks.blockPlantationField.defaultBlockState());
        try
        {
            set(com.minecolonies.core.tileentities.TileEntityPlantationField.class, tile, "plantationFieldTypes",
              new java.util.LinkedHashSet<>(List.of(BuildingExtensionRegistries.plantationSugarCaneField.get(), BuildingExtensionRegistries.plantationCactusField.get())));
        }
        catch (final ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
        return new PlantationFieldScreen(tile, colony);
    }

    private static Screen rallyBanner()
    {
        RallyBannerScreen.previewTowers = RALLY;
        return new RallyBannerScreen(new ItemStack(net.minecraft.world.item.Items.RED_BANNER));
    }

    /** A level 3 house (a real view: the pages test for LivingBuildingView) in manual mode, with 2 of 4 beds taken. */
    private static void houseSample(final IColonyView colony) throws ReflectiveOperationException
    {
        final HomeBuildingModule.View home = new HomeBuildingModule.View(colony, FARM_POS.offset(12, 0, 0))
        {
            @Override
            public String getBuildingDisplayName()
            {
                return "com.minecolonies.building.residence";
            }

            @Override
            public int getBuildingLevel()
            {
                return 3;
            }

            @Override
            public int getBuildingMaxLevel()
            {
                return 5;
            }

            @Override
            public List<IBuildingModuleView> getAllModuleViews()
            {
                return HOUSE_MODULES;
            }

            @Override
            public <T extends IBuildingModuleView> List<T> getModuleViews(final Class<T> type)
            {
                return HOUSE_MODULES.stream().filter(type::isInstance).map(type::cast).toList();
            }

            @Override
            public <T extends IBuildingModuleView> T getModuleViewByType(final Class<T> type)
            {
                return getModuleViews(type).stream().findFirst().orElse(null);
            }
        };
        house = home;
        set(AbstractBuildingView.class, home, "buildingType", ModBuildings.home.get());
        final LivingBuildingModuleView living = new LivingBuildingModuleView();
        set(LivingBuildingModuleView.class, living, "hiringMode", HiringMode.MANUAL);
        set(LivingBuildingModuleView.class, living, "maxInhabitants", 4);
        living.add(1);
        living.add(6);
        HOUSE_MODULES.add(living.setBuildingView(home).setColonyView(colony));
        HOUSE_MODULES.add(new BuildingStatisticsModuleView().setBuildingView(home).setColonyView(colony));
    }

    /** A level 3 restaurant (15 dishes at most) with three customers and a menu of vanilla and MineColonies dishes. */
    private static void restaurantSample(final IColonyView colony) throws ReflectiveOperationException
    {
        final BuildingCook.View cook = new BuildingCook.View(colony, FARM_POS.offset(-20, 0, 0))
        {
            @Override
            public String getBuildingDisplayName()
            {
                return "com.minecolonies.building.cook";
            }

            @Override
            public int getBuildingLevel()
            {
                return 3;
            }

            @Override
            public int getBuildingMaxLevel()
            {
                return 5;
            }

            @Override
            public List<IBuildingModuleView> getAllModuleViews()
            {
                return RESTAURANT_MODULES;
            }

            @Override
            public <T extends IBuildingModuleView> List<T> getModuleViews(final Class<T> type)
            {
                return RESTAURANT_MODULES.stream().filter(type::isInstance).map(type::cast).toList();
            }

            @Override
            public <T extends IBuildingModuleView> T getModuleViewByType(final Class<T> type)
            {
                return getModuleViews(type).stream().findFirst().orElse(null);
            }
        };
        restaurant = cook;
        set(AbstractBuildingView.class, cook, "buildingType", ModBuildings.cook.get());
        set(BuildingCook.View.class, cook, "customerList", new it.unimi.dsi.fastutil.ints.IntOpenHashSet(new int[] {1, 2, 3}));
        final WorkerBuildingModuleView worker = new WorkerBuildingModuleView();
        set(WorkerBuildingModuleView.class, worker, "hiringMode", HiringMode.DEFAULT);
        set(WorkerBuildingModuleView.class, worker, "maxInhabitants", 1);
        set(WorkerBuildingModuleView.class, worker, "jobEntry", ModJobs.cook.get());
        set(WorkerBuildingModuleView.class, worker, "primary", Skill.Adaptability);
        set(WorkerBuildingModuleView.class, worker, "secondary", Skill.Knowledge);
        RESTAURANT_MODULES.add(worker.setBuildingView(cook).setColonyView(colony));
        final RestaurantMenuModuleView menu = new RestaurantMenuModuleView();
        for (final Item item : List.of(Items.BREAD, Items.COOKED_BEEF, ModItems.pottage, ModItems.cheddar_cheese, Items.BAKED_POTATO))
        {
            menu.getMenu().add(new ItemStorage(new ItemStack(item)));
        }
        RESTAURANT_MODULES.add(menu.setBuildingView(cook).setColonyView(colony));
        RESTAURANT_MODULES.add(new BuildingStatisticsModuleView().setBuildingView(cook).setColonyView(colony));
        RestaurantMenuTab.previewEdibles = List.of(Items.BREAD, Items.COOKED_BEEF, Items.BAKED_POTATO, Items.COOKED_CHICKEN, Items.COOKED_PORKCHOP,
          Items.COOKED_COD, Items.COOKED_SALMON, Items.PUMPKIN_PIE, Items.MUSHROOM_STEW, Items.BEETROOT_SOUP, Items.RABBIT_STEW, Items.APPLE, Items.CARROT,
          Items.GOLDEN_CARROT, Items.COOKIE, ModItems.pottage, ModItems.cheddar_cheese, ModItems.lamb_stew, ModItems.sushi_roll, ModItems.pasta_tomato,
          ModItems.manchet_bread, ModItems.hand_pie, ModItems.muffin, ModItems.rice_ball).stream().map(item -> new ItemStorage(new ItemStack(item))).toList();
        final Map<ItemStack, Double> ingredients = new LinkedHashMap<>();
        ingredients.put(new ItemStack(Items.WHEAT), 3.0D);
        ingredients.put(new ItemStack(Items.MILK_BUCKET), 1.0D);
        ingredients.put(new ItemStack(Items.POTATO), 2.0D);
        ingredients.put(new ItemStack(Items.CARROT), 1.0D);
        ingredients.put(new ItemStack(Items.BEEF), 1.0D);
        RestaurantMenuTab.previewIngredients = ingredients;
    }

    /**
     * A level 3 mine (2 guard huts at most) with a miner working on its third level, five levels down to deepslate, and
     * four guard huts on the mine mission: one guarding this mine, two free (one without guards), one at another mine.
     */
    private static void mineSample(final IColonyView colony, final Map<Integer, ICitizenDataView> citizens) throws ReflectiveOperationException
    {
        mine = building(colony, ModBuildings.miner.get(), "com.minecolonies.building.miner", 3, MINE_MODULES);
        citizens.put(12, simpleCitizen(colony, 12, "Marc Dubois", "com.minecolonies.job.miner"));
        final WorkerBuildingModuleView worker = new WorkerBuildingModuleView();
        set(WorkerBuildingModuleView.class, worker, "hiringMode", HiringMode.DEFAULT);
        set(WorkerBuildingModuleView.class, worker, "maxInhabitants", 1);
        set(WorkerBuildingModuleView.class, worker, "jobEntry", ModJobs.miner.get());
        set(WorkerBuildingModuleView.class, worker, "primary", Skill.Strength);
        set(WorkerBuildingModuleView.class, worker, "secondary", Skill.Stamina);
        @SuppressWarnings("unchecked") final Set<Integer> ids = (Set<Integer>) get(WorkerBuildingModuleView.class, worker, "workerIDs");
        ids.add(12);
        MINE_MODULES.add(worker.setBuildingView(mine).setColonyView(colony));

        final MinerLevelManagementModuleView levels = new MinerLevelManagementModuleView();
        levels.levelsInfo = new ArrayList<>(List.of(new net.minecraft.util.Tuple<>(14, 60), new net.minecraft.util.Tuple<>(22, 45), new net.minecraft.util.Tuple<>(9, 30),
          new net.minecraft.util.Tuple<>(3, 14), new net.minecraft.util.Tuple<>(0, -6)));
        levels.current = 2;
        MINE_MODULES.add(levels.setBuildingView(mine).setColonyView(colony));
        MINE_MODULES.add(new MinerGuardAssignModuleView().setBuildingView(mine).setColonyView(colony));
        MINE_MODULES.add(new BuildingStatisticsModuleView().setBuildingView(mine).setColonyView(colony));

        citizens.put(13, simpleCitizen(colony, 13, "Nina Garnier", "com.minecolonies.job.knight"));
        citizens.put(14, simpleCitizen(colony, 14, "Oscar Blanc", "com.minecolonies.job.ranger"));
        citizens.put(15, simpleCitizen(colony, 15, "Paul Henry", "com.minecolonies.job.knight"));
        citizens.put(16, simpleCitizen(colony, 16, "Rose Caron", "com.minecolonies.job.ranger"));
        mineTower(colony, new BlockPos(112, 64, 96), 2, List.of(13, 14), FARM_POS);
        mineTower(colony, new BlockPos(80, 64, 130), 1, List.of(15), null);
        mineTower(colony, new BlockPos(150, 64, 70), 3, List.of(16), new BlockPos(200, 50, 40));
        mineTower(colony, new BlockPos(96, 64, 60), 1, List.of(), null);
    }

    private static ICitizenDataView simpleCitizen(final IColonyView colony, final int id, final String name, final String job)
    {
        return proxy(ICitizenDataView.class, (m, a) -> switch (m.getName())
        {
            case "getId" -> id;
            case "getName" -> name;
            case "getJob" -> job;
            case "getHappiness" -> 7.0D;
            case "getCitizenSkillHandler" -> FLAT_SKILLS;
            case "getColony" -> colony;
            default -> null;
        });
    }

    /** A guard tower on the "patrol the mine" mission, registered in the colony's buildings. */
    private static void mineTower(final IColonyView colony, final BlockPos pos, final int level, final List<Integer> guards, final BlockPos minePos)
      throws ReflectiveOperationException
    {
        final List<IBuildingModuleView> modules = new ArrayList<>();
        final BuildingGuardTower.View tower = new BuildingGuardTower.View(colony, pos)
        {
            @Override
            public String getBuildingDisplayName()
            {
                return "com.minecolonies.building.guardtower";
            }

            @Override
            public int getBuildingLevel()
            {
                return level;
            }

            @Override
            public <T extends IBuildingModuleView> List<T> getModuleViews(final Class<T> type)
            {
                return modules.stream().filter(type::isInstance).map(type::cast).toList();
            }

            @Override
            public <T extends IBuildingModuleView> T getModuleViewByType(final Class<T> type)
            {
                return getModuleViews(type).stream().findFirst().orElse(null);
            }
        };
        set(AbstractBuildingView.class, tower, "buildingType", ModBuildings.guardTower.get());
        @SuppressWarnings("unchecked") final List<Integer> ids = (List<Integer>) get(AbstractBuildingGuards.View.class, tower, "guards");
        ids.addAll(guards);
        tower.setMinePos(minePos);
        final SettingsModuleView settings = new SettingsModuleView();
        @SuppressWarnings("unchecked") final Map<ISettingKey<?>, ISetting<?>> map = (Map<ISettingKey<?>, ISetting<?>>) get(SettingsModuleView.class, settings, "settings");
        final GuardTaskSetting task = new GuardTaskSetting(GuardTaskSetting.PATROL, GuardTaskSetting.GUARD, GuardTaskSetting.FOLLOW, GuardTaskSetting.PATROL_MINE);
        task.set(GuardTaskSetting.PATROL_MINE);
        map.put(AbstractBuildingGuards.GUARD_TASK, task);
        modules.add(settings.setBuildingView(tower).setColonyView(colony));
        // A worker module, so the towers count as huts with workers (the enchanter's page lists them).
        modules.add(new WorkerBuildingModuleView().setBuildingView(tower).setColonyView(colony));
        BUILDINGS.put(pos, tower);
    }

    /** A level 3 guard tower (a real view: the pages test for AbstractBuildingGuards.View) with a knight, a ranger and 3 patrol points. */
    private static void guardSample(final IColonyView colony, final Map<Integer, ICitizenDataView> citizens) throws ReflectiveOperationException
    {
        final BuildingGuardTower.View tower = new BuildingGuardTower.View(colony, FARM_POS)
        {
            @Override
            public String getBuildingDisplayName()
            {
                return "com.minecolonies.building.guardtower";
            }

            @Override
            public int getBuildingLevel()
            {
                return 3;
            }

            @Override
            public int getBuildingMaxLevel()
            {
                return 5;
            }

            @Override
            public int getClaimRadius()
            {
                return 3;
            }

            @Override
            public List<IBuildingModuleView> getAllModuleViews()
            {
                return GUARD_MODULES;
            }

            @Override
            public <T extends IBuildingModuleView> List<T> getModuleViews(final Class<T> type)
            {
                return GUARD_MODULES.stream().filter(type::isInstance).map(type::cast).toList();
            }

            @Override
            public <T extends IBuildingModuleView> T getModuleViewByType(final Class<T> type)
            {
                return getModuleViews(type).stream().findFirst().orElse(null);
            }
        };
        guardTower = tower;
        set(AbstractBuildingView.class, tower, "buildingType", ModBuildings.guardTower.get());
        set(AbstractBuildingGuards.View.class, tower, "patrolTargets",
          new ArrayList<>(List.of(new BlockPos(124, 66, 92), new BlockPos(140, 64, 130), new BlockPos(88, 70, 150))));

        final Object[][] guards = {{10, "Jules Mercier", "com.minecolonies.job.knight", ModJobs.knight.get()}, {11, "Léa Fontaine", "com.minecolonies.job.ranger", ModJobs.archer.get()}};
        for (final Object[] guard : guards)
        {
            final int id = (Integer) guard[0];
            citizens.put(id, proxy(ICitizenDataView.class, (m, a) -> switch (m.getName())
            {
                case "getId" -> id;
                case "getName" -> guard[1];
                case "getJob" -> guard[2];
                case "getWorkBuilding" -> FARM_POS;
                case "getHomeBuilding" -> FARM_POS;
                case "getHappiness" -> 7.5D;
                case "getCitizenSkillHandler" -> FLAT_SKILLS;
                case "getColony" -> colony;
                default -> null;
            }));
            final CombinedHiringLimitModuleView work = new CombinedHiringLimitModuleView();
            set(WorkerBuildingModuleView.class, work, "hiringMode", HiringMode.DEFAULT);
            set(WorkerBuildingModuleView.class, work, "maxInhabitants", 1);
            set(WorkerBuildingModuleView.class, work, "jobEntry", guard[3]);
            set(WorkerBuildingModuleView.class, work, "primary", Skill.Adaptability);
            set(WorkerBuildingModuleView.class, work, "secondary", Skill.Stamina);
            @SuppressWarnings("unchecked") final Set<Integer> ids = (Set<Integer>) get(WorkerBuildingModuleView.class, work, "workerIDs");
            ids.add(id);
            GUARD_MODULES.add(work.setBuildingView(tower).setColonyView(colony));
        }

        GUARD_MODULES.add(new ToolModuleView(ModItems.scepterGuard).setBuildingView(tower).setColonyView(colony));
        final EntityListModuleView hostiles = new EntityListModuleView("hostiles", Component.translatable("com.minecolonies.coremod.gui.workerhuts.hostilelist"), true);
        @SuppressWarnings("unchecked") final List<ResourceLocation> ignored = (List<ResourceLocation>) get(EntityListModuleView.class, hostiles, "listOfEntities");
        ignored.add(ResourceLocation.withDefaultNamespace("creeper"));
        ignored.add(ResourceLocation.withDefaultNamespace("enderman"));
        GUARD_MODULES.add(hostiles.setBuildingView(tower).setColonyView(colony));
        EntityListTab.previewMonsters = List.of("zombie", "skeleton", "creeper", "spider", "enderman", "witch", "pillager", "vindicator", "drowned", "husk",
          "stray", "phantom", "slime", "cave_spider", "ravager", "evoker", "blaze", "ghast").stream().map(ResourceLocation::withDefaultNamespace).toList();

        final SettingsModuleView settings = new SettingsModuleView();
        @SuppressWarnings("unchecked") final Map<ISettingKey<?>, ISetting<?>> map = (Map<ISettingKey<?>, ISetting<?>>) get(SettingsModuleView.class, settings, "settings");
        map.put(AbstractBuildingGuards.GUARD_TASK, new GuardTaskSetting(GuardTaskSetting.PATROL, GuardTaskSetting.GUARD, GuardTaskSetting.FOLLOW, GuardTaskSetting.PATROL_MINE));
        map.put(AbstractBuildingGuards.PATROL_MODE, new GuardPatrolModeSetting(List.of(GuardPatrolModeSetting.AUTO, GuardPatrolModeSetting.MANUAL), 1));
        map.put(AbstractBuildingGuards.RETREAT, new BoolSetting(true));
        GUARD_MODULES.add(settings.setBuildingView(tower).setColonyView(colony));
        GUARD_MODULES.add(new MinimumStockModuleView().setBuildingView(tower).setColonyView(colony));
        GUARD_MODULES.add(new BuildingStatisticsModuleView().setBuildingView(tower).setColonyView(colony));
    }

    /** A level 5 warehouse with two couriers (one busy), one rack upgrade done, and racks of building materials. */
    private static void warehouseSample(final IColonyView colony, final Map<Integer, ICitizenDataView> citizens) throws ReflectiveOperationException
    {
        warehouse = building(colony, ModBuildings.wareHouse.get(), "com.minecolonies.building.warehouse", 5, WAREHOUSE_MODULES);
        final Object[][] couriers = {{8, "Hugo Lefèvre", 3}, {9, "Inès Bonnet", 0}};
        final CourierAssignmentModuleView assignment = new CourierAssignmentModuleView();
        set(CourierAssignmentModuleView.class, assignment, "hiringMode", HiringMode.DEFAULT);
        @SuppressWarnings("unchecked") final Set<Integer> ids = (Set<Integer>) get(CourierAssignmentModuleView.class, assignment, "workerIDs");
        for (final Object[] courier : couriers)
        {
            final int id = (Integer) courier[0];
            final LinkedList<IToken<?>> queue = new LinkedList<>();
            for (int i = 0; i < (Integer) courier[2]; i++)
            {
                queue.add(proxy(IToken.class, (m, a) -> null));
            }
            final IRequestSystemDeliveryManJobDataStore store = proxy(IRequestSystemDeliveryManJobDataStore.class, (m, a) -> m.getName().equals("getQueue") ? queue : null);
            final DmanJobView job = new DmanJobView(colony, null)
            {
                @Override
                public IRequestSystemDeliveryManJobDataStore getDataStore()
                {
                    return store;
                }
            };
            final ICitizenDataView citizen = proxy(ICitizenDataView.class, (m, a) -> switch (m.getName())
            {
                case "getId" -> id;
                case "getName" -> courier[1];
                case "getJob" -> "com.minecolonies.job.deliveryman";
                case "getJobView" -> job;
                case "getWorkBuilding" -> new BlockPos(0, 64, 0);
                case "getHappiness" -> 8.0D;
                case "getCitizenSkillHandler" -> FLAT_SKILLS;
                case "getColony" -> colony;
                default -> null;
            });
            citizens.put(id, citizen);
            ids.add(id);
        }
        WAREHOUSE_MODULES.add(assignment.setBuildingView(warehouse).setColonyView(colony));
        final WarehouseOptionsModuleView options = new WarehouseOptionsModuleView();
        set(WarehouseOptionsModuleView.class, options, "storageUpgrade", 1);
        WAREHOUSE_MODULES.add(options.setBuildingView(warehouse).setColonyView(colony));
        WAREHOUSE_MODULES.add(new MinimumStockModuleView().setBuildingView(warehouse).setColonyView(colony));
        WAREHOUSE_MODULES.add(new WarehouseRequestTaskModuleView().setBuildingView(warehouse).setColonyView(colony));

        final Object[][] contents = {
          {Items.COBBLESTONE, 2304}, {Items.OAK_LOG, 412}, {Items.OAK_PLANKS, 960}, {Items.DIRT, 1180}, {Items.STONE_BRICKS, 640}, {Items.SAND, 256},
          {Items.GLASS, 190}, {Items.IRON_INGOT, 87}, {Items.COAL, 345}, {Items.WHEAT, 128}, {Items.BREAD, 64}, {Items.TORCH, 150}, {Items.OAK_STAIRS, 72},
          {Items.SPRUCE_LOG, 260}, {Items.SPRUCE_PLANKS, 300}, {Items.BIRCH_LOG, 48}, {Items.GRAVEL, 220}, {Items.CLAY_BALL, 96}, {Items.BRICKS, 120},
          {Items.WHITE_WOOL, 40}, {Items.STRING, 33}, {Items.BONE_MEAL, 58}, {Items.WHEAT_SEEDS, 210}, {Items.POTATO, 140}, {Items.CARROT, 90},
          {Items.COOKED_BEEF, 24}, {Items.LEATHER, 17}, {Items.FEATHER, 41}, {Items.FLINT, 12}, {Items.GOLD_INGOT, 9}, {Items.REDSTONE, 75},
          {Items.OAK_DOOR, 6}, {Items.GLASS_PANE, 112}, {Items.LADDER, 30}, {Items.CHEST, 4}, {Items.IRON_PICKAXE, 1}, {Items.STONE_AXE, 3},
          {Items.OAK_SAPLING, 27}, {Items.APPLE, 15}, {Items.EMERALD, 2}, {Items.OAK_SLAB, 88}, {Items.COBBLESTONE_WALL, 36}};
        final List<ItemStack> items = new ArrayList<>();
        final Map<ItemStack, Integer> counts = new IdentityHashMap<>();
        int used = 0;
        for (final Object[] content : contents)
        {
            final ItemStack stack = new ItemStack((Item) content[0]);
            items.add(stack);
            counts.put(stack, (Integer) content[1]);
            used += ((Integer) content[1] + stack.getMaxStackSize() - 1) / stack.getMaxStackSize();
        }
        final Map<ItemStack, Integer> places = new IdentityHashMap<>();
        counts.forEach((stack, count) -> places.put(stack, 1 + count / 500));
        WarehouseTab.previewStorage = new WarehouseTab.Storage(24, 2, 24 * 36, used, items, counts, places);
    }

    private static final List<IWorkOrderView> WORK_ORDERS = new ArrayList<>();

    /** A level 2 builder in manual mode, upgrading the town hall, with two free work orders. */
    private static void builderSample(final IColonyView colony) throws ReflectiveOperationException
    {
        builder = building(colony, ModBuildings.builder.get(), "com.minecolonies.building.builder", 2, BUILDER_MODULES);
        final WorkerBuildingModuleView worker = new WorkerBuildingModuleView();
        set(WorkerBuildingModuleView.class, worker, "hiringMode", HiringMode.DEFAULT);
        set(WorkerBuildingModuleView.class, worker, "maxInhabitants", 1);
        set(WorkerBuildingModuleView.class, worker, "jobEntry", ModJobs.builder.get());
        @SuppressWarnings("unchecked") final Set<Integer> ids = (Set<Integer>) get(WorkerBuildingModuleView.class, worker, "workerIDs");
        ids.add(6);
        BUILDER_MODULES.add(worker.setBuildingView(builder).setColonyView(colony));

        final BuildingResourcesModuleView resources = new BuildingResourcesModuleView();
        @SuppressWarnings("unchecked") final Map<String, BuildingBuilderResource> map = (Map<String, BuildingBuilderResource>) get(BuildingResourcesModuleView.class, resources, "resources");
        final Object[][] needs = {
          {Items.OAK_PLANKS, 128, 64}, {Items.COBBLESTONE, 96, 96}, {Items.GLASS_PANE, 24, 0}, {Items.OAK_STAIRS, 40, 12},
          {Items.TORCH, 16, 16}, {Items.WHITE_WOOL, 8, 2}, {Items.OAK_DOOR, 2, 0}};
        for (final Object[] need : needs)
        {
            map.put(need[0].toString(), new BuildingBuilderResource(new ItemStack((Item) need[0]), (Integer) need[1], (Integer) need[2]));
        }
        // A Domum Ornamentum timber frame, whose row shows the materials to cut it from.
        final com.ldtteam.domumornamentum.block.decorative.TimberFrameBlock timber = com.ldtteam.domumornamentum.block.ModBlocks.getInstance().getTimberFrames().get(0);
        final Map<net.minecraft.resources.ResourceLocation, net.minecraft.world.level.block.Block> textures = new java.util.LinkedHashMap<>();
        final net.minecraft.world.level.block.Block[] skins = {net.minecraft.world.level.block.Blocks.DARK_OAK_PLANKS, net.minecraft.world.level.block.Blocks.WHITE_TERRACOTTA};
        int skin = 0;
        for (final com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent component : timber.getComponents())
        {
            textures.put(component.getId(), skins[Math.min(skin++, skins.length - 1)]);
        }
        final ItemStack frame = new ItemStack(timber);
        new com.ldtteam.domumornamentum.client.model.data.MaterialTextureData(textures).writeToItemStack(frame);
        map.put("timber_frame", new BuildingBuilderResource(frame, 32, 4));
        set(BuildingResourcesModuleView.class, resources, "workOrderId", 3);
        set(BuildingResourcesModuleView.class, resources, "progress", 0.7D);
        set(BuildingResourcesModuleView.class, resources, "totalStages", 4);
        set(BuildingResourcesModuleView.class, resources, "finishedStages", 2);
        BUILDER_MODULES.add(resources.setBuildingView(builder).setColonyView(colony));
        BUILDER_MODULES.add(new WorkOrderListModuleView().setBuildingView(builder).setColonyView(colony));

        final SettingsModuleView settings = new SettingsModuleView();
        @SuppressWarnings("unchecked") final Map<ISettingKey<?>, ISetting<?>> settingMap = (Map<ISettingKey<?>, ISetting<?>>) get(SettingsModuleView.class, settings, "settings");
        settingMap.put(BuildingBuilder.MODE, new StringSetting(List.of(BuildingBuilder.MANUAL_SETTING, "com.minecolonies.core.builder.setting.automatic"), 0));
        BUILDER_MODULES.add(settings.setBuildingView(builder).setColonyView(colony));

        WORK_ORDERS.add(workOrder(3, "Hôtel de ville", WorkOrderType.UPGRADE, 1, 2, new BlockPos(130, 64, 100), FARM_POS, 3));
        WORK_ORDERS.add(workOrder(4, "Ferme", WorkOrderType.BUILD, 0, 1, new BlockPos(60, 64, 140), BlockPos.ZERO, 2));
        WORK_ORDERS.add(workOrder(5, "Maison", WorkOrderType.REPAIR, 2, 2, new BlockPos(90, 64, 40), BlockPos.ZERO, 1));
    }

    private static IWorkOrderView workOrder(final int id, final String name, final WorkOrderType type, final int current, final int target, final BlockPos location,
      final BlockPos claimedBy, final int priority)
    {
        return proxy(IWorkOrderView.class, (m, a) -> switch (m.getName())
        {
            case "getID" -> id;
            case "getDisplayName" -> Component.literal(name);
            case "getWorkOrderType" -> type;
            case "getCurrentLevel" -> current;
            case "getTargetLevel" -> target;
            case "getLocation" -> location;
            case "getClaimedBy" -> claimedBy;
            case "getPriority" -> priority;
            case "shouldShowIn", "canBuildIgnoringDistance" -> true;
            default -> null;
        });
    }

    /** A crafter's recipes: 3x3, 2x2 with a by-product, smelting, a disabled built-in one. */
    static CraftingModuleView crafting() throws ReflectiveOperationException
    {
        final CraftingModuleView crafting = new CraftingModuleView();
        set(CraftingModuleView.class, crafting, "id", "crafting");
        set(CraftingModuleView.class, crafting, "isVisible", true);
        set(CraftingModuleView.class, crafting, "activeRecipes", 3);
        set(CraftingModuleView.class, crafting, "maxRecipes", 10);
        @SuppressWarnings("unchecked") final Set<CraftingType> types = (Set<CraftingType>) get(CraftingModuleView.class, crafting, "recipeTypeSet");
        types.add(ModCraftingTypes.SMALL_CRAFTING.get());
        @SuppressWarnings("unchecked") final List<IRecipeStorage> recipes = (List<IRecipeStorage>) get(CraftingModuleView.class, crafting, "recipes");
        recipes.add(recipe(new ItemStack(Items.BREAD), List.of(new ItemStorage(new ItemStack(Items.WHEAT), 3)), null, List.of(), false));
        recipes.add(recipe(new ItemStack(Items.CAKE), List.of(new ItemStorage(new ItemStack(Items.MILK_BUCKET), 3), new ItemStorage(new ItemStack(Items.SUGAR), 2),
          new ItemStorage(new ItemStack(Items.EGG), 1), new ItemStorage(new ItemStack(Items.WHEAT), 3)), null, List.of(new ItemStack(Items.BUCKET, 3)), false));
        recipes.add(recipe(new ItemStack(Items.BAKED_POTATO), List.of(new ItemStorage(new ItemStack(Items.POTATO), 1)), Blocks.FURNACE, List.of(), false));
        final IRecipeStorage cookie = recipe(new ItemStack(Items.COOKIE, 8), List.of(new ItemStorage(new ItemStack(Items.WHEAT), 2),
          new ItemStorage(new ItemStack(Items.COCOA_BEANS), 1)), null, List.of(), true);
        recipes.add(cookie);
        recipes.add(recipe(new ItemStack(Items.PUMPKIN_PIE), List.of(new ItemStorage(new ItemStack(Items.PUMPKIN), 1), new ItemStorage(new ItemStack(Items.SUGAR), 1),
          new ItemStorage(new ItemStack(Items.EGG), 1)), null, List.of(), false));
        @SuppressWarnings("unchecked") final List<IRecipeStorage> disabled = (List<IRecipeStorage>) get(CraftingModuleView.class, crafting, "disabledRecipes");
        disabled.add(cookie);
        return crafting;
    }

    private static IRecipeStorage recipe(final ItemStack output, final List<ItemStorage> input, final Block station, final List<ItemStack> extra, final boolean builtIn)
    {
        return proxy(IRecipeStorage.class, (m, a) -> switch (m.getName())
        {
            case "getPrimaryOutput" -> output;
            case "getInput", "getCleanedInput" -> input;
            case "getIntermediate" -> station == null ? Blocks.AIR : station;
            case "getSecondaryOutputs" -> extra;
            case "getRecipeSource" -> builtIn ? ResourceLocation.withDefaultNamespace("cookie") : null;
            default -> null;
        });
    }

    /** A baker's queue: tasks for the restaurant (one in progress, under one order), one for the hut, a delivery. */
    private static List<IToken<?>> tasks(final Map<IToken<?>, IRequest<?>> requests)
    {
        final ILocation bakery = proxy(ILocation.class, (m, a) -> m.getName().equals("getInDimensionLocation") ? FARM_POS : null);
        final ILocation restaurant = proxy(ILocation.class, (m, a) -> m.getName().equals("getInDimensionLocation") ? new BlockPos(140, 64, 90) : null);
        final IRequester baker = proxy(IRequester.class, (m, a) -> switch (m.getName())
        {
            case "getLocation" -> bakery;
            case "getRequesterDisplayName" -> Component.literal("Alice Martin");
            default -> null;
        });
        final IRequester cook = proxy(IRequester.class, (m, a) -> switch (m.getName())
        {
            case "getLocation" -> restaurant;
            case "getRequesterDisplayName" -> Component.literal("Restaurant");
            default -> null;
        });
        final IToken<?> order = proxy(IToken.class, (m, a) -> null);
        requests.put(order, proxy(IRequest.class, (m, a) -> switch (m.getName())
        {
            case "getRequester" -> cook;
            case "getState" -> RequestState.IN_PROGRESS;
            default -> null;
        }));
        final List<IToken<?>> tokens = new ArrayList<>();
        final Object[][] samples = {
          {new ItemStack(Items.BREAD), 16, "Crafting: ", RequestState.IN_PROGRESS, baker, order},
          {new ItemStack(Items.CAKE), 2, "Crafting: ", RequestState.ASSIGNED, baker, order},
          {new ItemStack(Items.COOKIE), 32, "Crafting: ", RequestState.ASSIGNED, baker, null},
          {new ItemStack(Items.WHEAT), 64, "Delivery: ", RequestState.ASSIGNED, cook, null}};
        for (final Object[] sample : samples)
        {
            final IToken<?> token = proxy(IToken.class, (m, a) -> null);
            final IDeliverymanRequestable delivery = proxy(IDeliverymanRequestable.class, (m, a) -> m.getName().equals("getPriority") ? 4 : null);
            final boolean isDelivery = sample[1].equals(64);
            requests.put(token, proxy(new Class<?>[] {IRequest.class, IStackBasedTask.class}, (m, a) -> switch (m.getName())
            {
                case "getTaskStack" -> sample[0];
                case "getDisplayCount" -> sample[1];
                case "getDisplayPrefix" -> Component.literal((String) sample[2]);
                case "getState" -> sample[3];
                case "getRequester" -> sample[4];
                case "getParent" -> sample[5];
                case "getRequest" -> isDelivery ? delivery : null;
                case "getDisplayStacks", "getResolverToolTip" -> List.of();
                case "getDisplayIcon" -> AbstractRequest.MISSING;
                default -> null;
            }));
            tokens.add(token);
        }
        return tokens;
    }

    /**
     * The build options of the farm: level 2 (or not built), the "Medieval Oak" style, a warning to confirm, a builder
     * nearby and one further away, and a blueprint's worth of materials (the preview has no structure packs).
     */
    private static IBuildingView buildSample(final IBuildingView base, final int level, final String warning)
    {
        BuildScreen.previewResources = List.of(storage(Items.OAK_PLANKS, 212), storage(Items.COBBLESTONE, 148), storage(Items.OAK_LOG, 64),
          storage(Items.GLASS_PANE, 24), storage(Items.OAK_STAIRS, 38), storage(Items.OAK_SLAB, 20), storage(Items.TORCH, 8), storage(Items.OAK_DOOR, 2),
          storage(Items.CHEST, 1), storage(Items.COMPOSTER, 2), storage(Items.DIRT, 30), storage(Items.WATER_BUCKET, 2), storage(Items.OAK_FENCE, 16),
          storage(Items.HAY_BLOCK, 4), storage(Items.LANTERN, 3), storage(Items.WHITE_WOOL, 6), storage(Items.STONE_BRICKS, 40), storage(Items.LADDER, 5));
        return proxy(IBuildingView.class, (m, a) -> switch (m.getName())
        {
            case "getBuildingLevel" -> level;
            case "getStructurePack" -> "Medieval Oak";
            case "getParent" -> BlockPos.ZERO;
            case "getHoverWarningForLevel" -> warning;
            case "isDeconstructed" -> false;
            default ->
            {
                try
                {
                    yield m.invoke(base, a);
                }
                catch (final ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            }
        });
    }

    private static ItemStorage storage(final net.minecraft.world.item.Item item, final int amount)
    {
        final ItemStorage storage = new ItemStorage(new ItemStack(item));
        storage.setAmount(amount);
        return storage;
    }

    /** Two builders' huts of the colony, for the build options' drop-down. */
    private static void buildersSample(final IColonyView colony) throws ReflectiveOperationException
    {
        final Object[][] builders = {{"Félix Girard", 3, FARM_POS.offset(24, 0, 10)}, {"Hugo Lambert", 1, FARM_POS.offset(-90, 0, 40)}};
        for (final Object[] entry : builders)
        {
            final BuildingBuilder.View view = new BuildingBuilder.View(colony, (BlockPos) entry[2]);
            set(com.minecolonies.core.colony.buildings.views.AbstractBuildingBuilderView.class, view, "workerName", entry[0]);
            set(AbstractBuildingView.class, view, "buildingLevel", entry[1]);
            BUILDINGS.put((BlockPos) entry[2], view);
        }
    }

    private static IBuildingView building(final IColonyView colony, final BuildingEntry type, final String name, final int level, final List<IBuildingModuleView> modules)
    {
        return proxy(IBuildingView.class, (m, a) -> switch (m.getName())
        {
            case "getColony" -> colony;
            case "getBuildingDisplayName" -> name;
            case "getBuildingType" -> type;
            case "getBuildingLevel" -> level;
            case "getBuildingMaxLevel" -> 5;
            case "getBuildingDmPrio" -> 5;
            case "allowsAssignment" -> true;
            case "getPosition", "getID" -> FARM_POS;
            case "getAllModuleViews" -> modules;
            case "getModuleViews" -> modules.stream().filter(((Class<?>) a[0])::isInstance).toList();
            case "getModuleViewByType" -> modules.stream().filter(((Class<?>) a[0])::isInstance).findFirst().orElse(null);
            case "getAllAssignedCitizens" -> Set.of(6);
            case "getModuleViewMatching" -> modules.stream().filter(((Class<?>) a[0])::isInstance).filter(v -> matches(a[1], v)).findFirst().orElse(null);
            default -> null;
        });
    }

    @SuppressWarnings("unchecked")
    private static boolean matches(final Object predicate, final Object value)
    {
        return ((Predicate<Object>) predicate).test(value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ISettingKey<?> key(final Class<? extends ISetting> type, final String id)
    {
        return new SettingKey(type, ResourceLocation.fromNamespaceAndPath("minecolonies", id));
    }

    private static void set(final Class<?> owner, final Object instance, final String name, final Object value) throws ReflectiveOperationException
    {
        final Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static Object get(final Class<?> owner, final Object instance, final String name) throws ReflectiveOperationException
    {
        final Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    private interface Handler
    {
        Object handle(Method method, Object[] args);
    }

    private static <T> T proxy(final Class<T> type, final Handler handler)
    {
        return proxy(new Class<?>[] {type}, handler);
    }

    /** A proxy answering from {@code handler}, with zero / false for unhandled primitive results. */
    @SuppressWarnings("unchecked")
    private static <T> T proxy(final Class<?>[] types, final Handler handler)
    {
        final Class<?> type = types[0];
        return (T) Proxy.newProxyInstance(HutPreview.class.getClassLoader(), types, (self, method, args) -> {
            switch (method.getName())
            {
                case "toString":
                    return type.getSimpleName() + " sample";
                case "hashCode":
                    return System.identityHashCode(self);
                case "equals":
                    return self == args[0];
                default:
                    break;
            }
            final Object result = handler.handle(method, args == null ? new Object[0] : args);
            if (result != null || !method.getReturnType().isPrimitive())
            {
                return result;
            }
            final Class<?> r = method.getReturnType();
            if (r == boolean.class)
            {
                return false;
            }
            if (r == void.class)
            {
                return null;
            }
            if (r == long.class)
            {
                return 0L;
            }
            if (r == double.class)
            {
                return 0.0D;
            }
            if (r == float.class)
            {
                return 0.0F;
            }
            return 0;
        });
    }
}
