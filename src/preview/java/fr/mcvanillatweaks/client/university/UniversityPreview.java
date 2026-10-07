package fr.mcvanillatweaks.client.university;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.modules.IBuildingModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.research.IGlobalResearch;
import com.minecolonies.api.research.IGlobalResearchBranch;
import com.minecolonies.api.research.IGlobalResearchTree;
import com.minecolonies.api.research.ILocalResearch;
import com.minecolonies.api.research.ILocalResearchTree;
import com.minecolonies.api.research.IResearchEffect;
import com.minecolonies.api.research.IResearchManager;
import com.minecolonies.api.research.ResearchBranchType;
import com.minecolonies.api.research.util.ResearchState;
import com.minecolonies.core.colony.buildings.moduleviews.BuildingStatisticsModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.UniversityResearchModuleView;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.hut.HutPage;
import fr.mcvanillatweaks.client.hut.HutScreen;
import fr.mcvanillatweaks.client.townhall.TownHallPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Dev preview of the university screens (source set "preview", run with ./gradlew runPreview after the town hall one):
 * opens each tab and a research tree with a proxy-mocked university and a sample of MineColonies' civilian research,
 * saves run/screenshots/university_*.png, then sets {@link #finished} for the hut preview.
 */
@EventBusSubscriber(modid = MineColoniesVanillaTweaks.MOD_ID, value = Dist.CLIENT)
public final class UniversityPreview
{
    private static final boolean ENABLED = Boolean.getBoolean("mcvanillatweaks.preview");
    private static final String[] NAMES = {"home", "research", "stats", "tree", "tree_zoomed"};

    /** Set once the university screenshots are saved: the hut preview starts. */
    public static boolean finished;

    private static int index = -1;
    private static int step;
    private static boolean capture;
    private static IBuildingView building;
    private static IGlobalResearchTree tree;
    private static final List<IBuildingModuleView> modules = new ArrayList<>();
    private static final List<Supplier<Screen>> SCREENS = new ArrayList<>();

    private UniversityPreview()
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
        if (index == -1 && TownHallPreview.finished && !fr.mcvanillatweaks.client.PreviewFilter.wantsFamily("university_"))
        {
            index = Integer.MIN_VALUE;
            finished = true;
            return;
        }
        if (index == -1 && TownHallPreview.finished)
        {
            MineColoniesVanillaTweaks.LOGGER.info("University preview: starting");
            Research.previewAffordable = true;
            sample();
            SCREENS.add(() -> new HutScreen(building, HutPage.HOME, null));
            SCREENS.add(() -> new HutScreen(building, HutPage.module(modules.get(0)), null));
            SCREENS.add(() -> new HutScreen(building, HutPage.module(modules.get(1)), null));
            SCREENS.add(() -> new ResearchTreeScreen(building, CIVILIAN, tree));
            SCREENS.add(() -> new ResearchTreeScreen(building, CIVILIAN, tree));
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
            finished = true;
            return;
        }
        step++;
        if (step == 1)
        {
            mc.setScreen(SCREENS.get(index).get());
        }
        else if (step == 2 && "tree_zoomed".equals(NAMES[index]) && mc.screen instanceof ResearchTreeScreen screen)
        {
            screen.zoomOutFully();
        }
        else if (step == 20)
        {
            capture = true;
        }
    }

    @SubscribeEvent
    public static void onScreenRender(final ScreenEvent.Render.Post event)
    {
        if (!capture || !(event.getScreen() instanceof HutScreen || event.getScreen() instanceof ResearchTreeScreen))
        {
            return;
        }
        capture = false;
        final Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "university_" + NAMES[index] + ".png", mc.getMainRenderTarget(),
          message -> MineColoniesVanillaTweaks.LOGGER.info("University preview: {}", message.getString()));
        index++;
        step = 0;
    }

    // ------------------------------------------------------------------ sample data

    private static final ResourceLocation CIVILIAN = ResourceLocation.fromNamespaceAndPath("minecolonies", "civilian");
    private static final ResourceLocation COMBAT = ResourceLocation.fromNamespaceAndPath("minecolonies", "combat");
    private static final ResourceLocation TECHNOLOGY = ResourceLocation.fromNamespaceAndPath("minecolonies", "technology");

    /** id -> {parent, depth}, a part of MineColonies' civilian branch. */
    private static final String[][] RESEARCH = {
      {"keen", "", "1"}, {"stamina", "", "1"}, {"firstaid", "", "1"}, {"higherlearning", "", "1"},
      {"diligent", "keen", "2"}, {"haste", "keen", "2"}, {"outpost", "keen", "2"},
      {"bandaid", "stamina", "2"}, {"resistance", "stamina", "2"},
      {"gourmand", "firstaid", "2"}, {"circus", "firstaid", "2"},
      {"morebooks", "higherlearning", "2"}, {"nurture", "higherlearning", "2"},
      {"studious", "diligent", "3"}, {"nimble", "haste", "3"}, {"hamlet", "outpost", "3"}, {"healingcream", "bandaid", "3"},
      {"agile", "nimble", "4"}};
    private static final List<String> FINISHED = List.of("keen", "stamina", "outpost");
    private static final List<String> RUNNING = List.of("diligent", "firstaid");

    private static ResourceLocation id(final String name)
    {
        return name.isEmpty() ? null : ResourceLocation.fromNamespaceAndPath("minecolonies", "civilian/" + name);
    }

    private static void sample()
    {
        final Map<ResourceLocation, IGlobalResearch> research = new LinkedHashMap<>();
        final Map<ResourceLocation, List<ResourceLocation>> children = new LinkedHashMap<>();
        for (final String[] entry : RESEARCH)
        {
            children.computeIfAbsent(id(entry[0]), k -> new ArrayList<>());
            if (!entry[1].isEmpty())
            {
                children.computeIfAbsent(id(entry[1]), k -> new ArrayList<>()).add(id(entry[0]));
            }
        }
        int order = 0;
        for (final String[] entry : RESEARCH)
        {
            final ResourceLocation id = id(entry[0]);
            final int depth = Integer.parseInt(entry[2]);
            final int sort = order++;
            final IResearchEffect effect = proxy(IResearchEffect.class, (m, a) -> switch (m.getName())
            {
                case "getName" -> new TranslatableContents("com.minecolonies.research.effects." + entry[0] + ".description", null, new Object[] {"10", "20"});
                case "getSubtitle" -> new TranslatableContents("", null, new Object[0]);
                case "getId" -> ResourceLocation.fromNamespaceAndPath("minecolonies", "effects/" + entry[0]);
                default -> null;
            });
            research.put(id, proxy(IGlobalResearch.class, (m, a) -> switch (m.getName())
            {
                case "getId" -> id;
                case "getBranch" -> CIVILIAN;
                case "getParent" -> id(entry[1]);
                case "getDepth" -> depth;
                case "getSortOrder" -> sort;
                case "getName" -> new TranslatableContents("com.minecolonies.research.civilian." + entry[0] + ".name", null, new Object[0]);
                case "getSubtitle" -> new TranslatableContents("com.minecolonies.research.civilian." + entry[0] + ".subtitle", null, new Object[0]);
                case "getChildren" -> children.getOrDefault(id, List.of());
                case "getEffects" -> List.of(effect);
                case "getResearchRequirements", "getCostList" -> List.of();
                case "hasOnlyChild" -> entry[0].equals("firstaid");
                case "hasResearchedChild" -> false;
                case "hasEnoughResources" -> !entry[0].equals("higherlearning");
                default -> null;
            }));
        }

        final Map<ResourceLocation, ILocalResearch> locals = new LinkedHashMap<>();
        for (final String name : List.of("keen", "stamina", "outpost", "diligent", "firstaid"))
        {
            final ResourceLocation id = id(name);
            final ResearchState state = FINISHED.contains(name) ? ResearchState.FINISHED : ResearchState.IN_PROGRESS;
            locals.put(id, proxy(ILocalResearch.class, (m, a) -> switch (m.getName())
            {
                case "getId" -> id;
                case "getBranch" -> CIVILIAN;
                case "getState" -> state;
                case "getProgress" -> name.equals("diligent") ? 200 : 60;
                case "getDepth" -> research.get(id).getDepth();
                default -> null;
            }));
        }
        final ILocalResearchTree localTree = proxy(ILocalResearchTree.class, (m, a) -> switch (m.getName())
        {
            case "getResearch" -> locals.get((ResourceLocation) a[1]);
            case "getResearchInProgress" -> locals.values().stream().filter(l -> l.getState() == ResearchState.IN_PROGRESS).toList();
            case "hasCompletedResearch" -> a[0] == null || FINISHED.contains(((ResourceLocation) a[0]).getPath().replace("civilian/", ""));
            case "isComplete" -> FINISHED.contains(((ResourceLocation) a[0]).getPath().replace("civilian/", ""));
            default -> null;
        });
        final IResearchManager manager = proxy(IResearchManager.class, (m, a) -> m.getName().equals("getResearchTree") ? localTree : null);
        final IColonyView colony = proxy(IColonyView.class, (m, a) -> switch (m.getName())
        {
            case "getResearchManager" -> manager;
            case "getName" -> "Exempleville";
            case "getDay" -> 40;
            default -> null;
        });
        building = proxy(IBuildingView.class, (m, a) -> switch (m.getName())
        {
            case "getColony" -> colony;
            case "getBuildingDisplayName" -> "com.minecolonies.building.university";
            case "getBuildingType" -> ModBuildings.university.get();
            case "getBuildingLevel" -> 2;
            case "getBuildingMaxLevel" -> 5;
            case "getBuildingDmPrio" -> 4;
            case "allowsAssignment" -> true;
            case "getPosition", "getID" -> BlockPos.ZERO;
            case "getAllModuleViews" -> modules;
            case "getModuleViews" -> modules.stream().filter(((Class<?>) a[0])::isInstance).toList();
            default -> null;
        });
        modules.add(new UniversityResearchModuleView().setBuildingView(building).setColonyView(colony));
        modules.add(new BuildingStatisticsModuleView().setBuildingView(building).setColonyView(colony));

        final Map<ResourceLocation, IGlobalResearchBranch> branches = new LinkedHashMap<>();
        int sortOrder = 0;
        for (final ResourceLocation branch : List.of(CIVILIAN, COMBAT, TECHNOLOGY))
        {
            final int sort = sortOrder++;
            branches.put(branch, proxy(IGlobalResearchBranch.class, (m, a) -> switch (m.getName())
            {
                case "getName" -> new TranslatableContents("com.minecolonies.research." + branch.getPath() + ".name", null, new Object[0]);
                case "getSubtitle" -> new TranslatableContents("", null, new Object[0]);
                case "getBaseTime" -> (Integer) a[0] * 144 * 2;
                case "getHoursTime" -> (double) ((Integer) a[0] * 2);
                case "getSortOrder" -> sort;
                case "getType" -> ResearchBranchType.DEFAULT;
                default -> null;
            }));
        }
        tree = proxy(IGlobalResearchTree.class, (m, a) -> switch (m.getName())
        {
            case "getBranches" -> new ArrayList<>(branches.keySet());
            case "getBranchData" -> branches.get((ResourceLocation) a[0]);
            case "getPrimaryResearch" -> CIVILIAN.equals(a[0])
                                           ? new ArrayList<>(research.values().stream().filter(r -> r.getDepth() == 1).map(IGlobalResearch::getId).toList())
                                           : new ArrayList<ResourceLocation>();
            case "getResearch" -> a.length == 2 ? research.get((ResourceLocation) a[1]) : research.get((ResourceLocation) a[0]);
            case "isResearchRequirementsFulfilled" -> true;
            case "getResearchResetCosts" -> List.of();
            default -> null;
        });
        UniversityScreens.treeOverride = tree;
    }

    private interface Handler
    {
        Object handle(Method method, Object[] args);
    }

    /** A proxy answering from {@code handler}, with zero / false for unhandled primitive results. */
    @SuppressWarnings("unchecked")
    private static <T> T proxy(final Class<T> type, final Handler handler)
    {
        return (T) Proxy.newProxyInstance(UniversityPreview.class.getClassLoader(), new Class<?>[] {type}, (self, method, args) -> {
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
