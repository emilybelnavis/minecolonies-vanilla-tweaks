package fr.mcvanillatweaks.client.townhall;

import com.google.common.collect.ImmutableList;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.modules.ICommonSettingsModule;
import com.minecolonies.api.colony.colonyEvents.descriptions.IBuildingEventDescription;
import com.minecolonies.api.colony.colonyEvents.descriptions.ICitizenEventDescription;
import com.minecolonies.api.colony.colonyEvents.descriptions.IColonyEventDescription;
import com.minecolonies.api.colony.connections.ColonyConnection;
import com.minecolonies.api.colony.connections.ConnectionEvent;
import com.minecolonies.api.colony.connections.ConnectionEventType;
import com.minecolonies.api.colony.connections.DiplomacyStatus;
import com.minecolonies.api.colony.connections.IColonyConnectionManager;
import com.minecolonies.api.colony.managers.interfaces.IStatisticsManager;
import com.minecolonies.api.colony.managers.interfaces.views.IRegisteredStructureManagerView;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.permissions.ColonyPlayer;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.PermissionEvent;
import com.minecolonies.api.colony.permissions.Rank;
import com.minecolonies.api.colony.workorders.IWorkOrderView;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenHappinessHandler;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenSkillHandler;
import com.minecolonies.api.entity.citizen.happiness.IHappinessModifier;
import com.minecolonies.api.research.IResearchEffectManager;
import com.minecolonies.api.research.IResearchManager;
import com.minecolonies.core.colony.buildings.modules.settings.BoolSetting;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.citizen.DropdownButton;
import fr.mcvanillatweaks.client.citizen.CitizenPreview;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Dev-only (source set "preview", never packaged): after the citizen preview, opens every town hall page with a sample
 * colony and saves run/screenshots/townhall_*.png, then quits.
 */
@EventBusSubscriber(modid = MineColoniesVanillaTweaks.MOD_ID, value = Dist.CLIENT)
public final class TownHallPreview
{
    private static final boolean ENABLED = Boolean.getBoolean("mcvanillatweaks.preview");
    private static final TownHallPage[] PAGES = {
      TownHallPage.ACTIONS, TownHallPage.INFO, TownHallPage.CITIZENS, TownHallPage.HAPPINESS, TownHallPage.STATS, TownHallPage.PERMISSIONS,
      TownHallPage.PERMISSIONS, TownHallPage.PERMISSIONS, TownHallPage.PERMISSIONS, TownHallPage.ALLIANCES, TownHallPage.SETTINGS,
      TownHallPage.ACTIONS, TownHallPage.ALLIANCES, TownHallPage.DASHBOARD};
    /** Permissions section shown at each index of {@link #PAGES} (ignored for the other pages). */
    private static final int[] SECTIONS = {0, 0, 0, 0, 0, 0, 1, 2, 3, 0, 0, 0, 0, 0};
    /** Indexes of {@link #PAGES} captured with the page's first drop-down list open. */
    private static final int DROPDOWN = 11;
    /** Index of {@link #PAGES} captured with connected colonies (the other alliance capture has none). */
    private static final int CONNECTED = 12;

    /** Set once every town hall page was captured. */
    public static boolean finished;
    private static int index = -1;
    private static int step;
    private static boolean capture;
    private static BuildingTownHall.View view;

    private TownHallPreview()
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
        if (index == -1 && CitizenPreview.finished && !fr.mcvanillatweaks.client.PreviewFilter.wantsFamily("townhall_"))
        {
            index = Integer.MIN_VALUE;
            finished = true;
            return;
        }
        if (index == -1 && CitizenPreview.finished)
        {
            MineColoniesVanillaTweaks.LOGGER.info("Town hall preview: starting");
            view = sampleView();
            index = 0;
            step = 0;
        }
        if (index < 0)
        {
            return;
        }
        if (index >= PAGES.length)
        {
            index = Integer.MIN_VALUE;
            // The university preview takes over and quits.
            finished = true;
            return;
        }
        step++;
        if (step == 1)
        {
            PermissionsTab.section = PermissionsTab.Section.values()[SECTIONS[index]];
            mc.setScreen(new TownHallScreen(view, PAGES[index], null));
        }
        else if (step == 10 && index == DROPDOWN && mc.screen != null)
        {
            mc.screen.children().stream().filter(DropdownButton.class::isInstance).map(DropdownButton.class::cast).findFirst()
              .ifPresent(DropdownButton::onPress);
        }
        else if (step == 20)
        {
            capture = true;
        }
    }

    @SubscribeEvent
    public static void onScreenRender(final ScreenEvent.Render.Post event)
    {
        if (!capture || !(event.getScreen() instanceof TownHallScreen))
        {
            return;
        }
        capture = false;
        final Minecraft mc = Minecraft.getInstance();
        final String suffix = index == DROPDOWN ? "_dropdown" : index == CONNECTED ? "_connected"
                                : PAGES[index] == TownHallPage.PERMISSIONS ? "_" + PermissionsTab.section.name().toLowerCase(Locale.ROOT) : "";
        Screenshot.grab(mc.gameDirectory, "townhall_" + PAGES[index].name().toLowerCase(Locale.ROOT) + suffix + ".png", mc.getMainRenderTarget(),
          message -> MineColoniesVanillaTweaks.LOGGER.info("Town hall preview: {}", message.getString()));
        index++;
        step = 0;
    }

    // ------------------------------------------------------------------ sample data

    private static BuildingTownHall.View sampleView()
    {
        final Map<Integer, ICitizenDataView> citizens = new LinkedHashMap<>();
        final String[][] people = {
          {"Clara B. Martin", "com.minecolonies.job.cook"}, {"Hugo L. Bernard", "com.minecolonies.job.builder"},
          {"Lina M. Martin", "com.minecolonies.job.builder"}, {"Paul R. Petit", "com.minecolonies.job.farmer"},
          {"Emma J. Roux", ""}, {"Noah T. Bernard", "com.minecolonies.job.deliveryman"}};
        for (int i = 0; i < people.length; i++)
        {
            citizens.put(i + 1, citizen(i + 1, people[i][0], people[i][1]));
        }
        final List<IWorkOrderView> orders = List.of(
          workOrder(1, "Amélioration de Hôtel de Ville\n2 > 3", 3), workOrder(2, "Amélioration de Cimetière\n1 > 2", 2),
          workOrder(3, "Construction de Boulangerie\n0 > 1", 1));
        final IStatisticsManager stats = proxy(IStatisticsManager.class, (method, args) -> switch (method.getName())
        {
            case "getStatTypes" -> new LinkedHashSet<>(List.of("trees_cut", "ores_mined", "land_tilled", "items_crafted", "citizens_healed", "death",
              "crops_harvested", "build_upgraded", "birth", "blocks_placed"));
            case "getStatTotal", "getStatsInPeriod" -> Math.abs(((String) args[0]).hashCode() % 40);
            default -> null;
        });
        final IResearchEffectManager effects = proxy(IResearchEffectManager.class, (method, args) -> null);
        final IResearchManager research = proxy(IResearchManager.class, (method, args) ->
          method.getName().equals("getResearchEffects") ? effects : null);
        final IColonyConnectionManager connections = proxy(IColonyConnectionManager.class, (method, args) -> switch (method.getName())
        {
            case "getDirectlyConnectedColonies" -> index == CONNECTED ? new TreeMap<>(Map.of(
              2, new ColonyConnection(2, "Valombre", new BlockPos(640, 70, 120), DiplomacyStatus.ALLIES),
              3, new ColonyConnection(3, "Port-Sable", new BlockPos(-300, 64, 900), DiplomacyStatus.NEUTRAL))) : new TreeMap<>();
            case "getIndirectlyConnectedColonies" -> index == CONNECTED ? new TreeMap<>(Map.of(
              4, new ColonyConnection(4, "Fort Corbeau", new BlockPos(1500, 80, -400), DiplomacyStatus.HOSTILE))) : new TreeMap<>();
            case "getConnectionEvents" -> index == CONNECTED ? List.of(
              new ConnectionEvent(2, "Valombre", ConnectionEventType.ALLY_CONFIRMED),
              new ConnectionEvent(3, "Port-Sable", ConnectionEventType.ALLY_REQUEST)) : List.of();
            case "getColonyDiplomacyStatus" -> DiplomacyStatus.NEUTRAL;
            default -> null;
        });
        final Map<Object, BoolSetting> settings = new java.util.HashMap<>();
        final ICommonSettingsModule settingsModule = proxy(ICommonSettingsModule.class, (method, args) ->
          method.getName().equals("getSetting") ? settings.computeIfAbsent(args[0], key -> new BoolSetting(settings.size() % 2 == 0)) : null);
        final IRegisteredStructureManagerView buildings = proxy(IRegisteredStructureManagerView.class, (method, args) ->
          method.getName().equals("getBuildings") ? Map.of() : null);
        final Rank owner = new Rank(0, "Owner", true);
        final Rank officer = new Rank(1, "Officer", true);
        final Rank friend = new Rank(2, "Friend", true);
        final Rank neutral = new Rank(3, "Neutral", true);
        final Rank hostile = new Rank(4, "Hostile", true);
        final Rank builders = new Rank(5, "Builders", false);
        final Map<Integer, Rank> ranks = new LinkedHashMap<>();
        for (final Rank rank : List.of(owner, officer, friend, neutral, hostile, builders))
        {
            ranks.put(rank.getId(), rank);
        }
        officer.setColonyManager(true);
        hostile.setHostile(true);
        final Map<UUID, ColonyPlayer> players = new LinkedHashMap<>();
        players.put(new UUID(0, 0), new ColonyPlayer(new UUID(0, 0), "Joueur1", owner));
        players.put(new UUID(0, 1), new ColonyPlayer(new UUID(0, 1), "Joueur2", officer));
        players.put(new UUID(0, 2), new ColonyPlayer(new UUID(0, 2), "Steve", builders));
        final IPermissions permissions = proxy(IPermissions.class, (method, args) -> switch (method.getName())
        {
            case "getOwner" -> new UUID(0, 0);
            case "getRanks" -> ranks;
            case "getRankOwner" -> owner;
            case "getRankOfficer" -> officer;
            case "getRank" -> owner;
            case "getPlayers" -> players;
            case "hasPermission" -> args[0] instanceof Rank rank ? (rank.getId() + ((Action) args[1]).ordinal()) % 3 != 0 : true;
            case "canAlterPermission" -> true;
            default -> null;
        });

        final IColonyView colony = proxy(IColonyView.class, (method, args) -> switch (method.getName())
        {
            case "getName" -> "Exempleville";
            case "getID" -> 1;
            case "getCitizens" -> citizens;
            case "getCitizen" -> citizens.get((Integer) args[0]);
            case "getCitizenCountLimit" -> 25;
            case "getOverallHappiness" -> 8.6D;
            case "getTeamColonyColor" -> ChatFormatting.WHITE;
            case "getStructurePack" -> "Ancient Athens";
            case "getTextureStyleId", "getNameStyle" -> "default";
            case "getNameFileIds" -> List.of("default");
            case "getPermissions" -> permissions;
            case "getWorkOrders" -> orders;
            case "getClientBuildingManager" -> buildings;
            case "getStatisticsManager" -> stats;
            case "getDay" -> 40;
            case "getResearchManager" -> research;
            case "getConnectionManager" -> connections;
            case "getSettings" -> settingsModule;
            case "getCenter" -> BlockPos.ZERO;
            case "getPlayers" -> players;
            case "getFreeBlocks" -> List.of(Blocks.OAK_DOOR, Blocks.LEVER);
            case "getFreePositions" -> List.of(new BlockPos(150, 68, -120));
            default -> null;
        });

        final BuildingTownHall.View sample = new BuildingTownHall.View(colony, BlockPos.ZERO)
        {
            @Override
            public String getBuildingDisplayName()
            {
                return "com.minecolonies.building.townhall";
            }

            @Override
            public int getBuildingLevel()
            {
                return 2;
            }
        };
        try
        {
            final Field field = BuildingTownHall.View.class.getDeclaredField("colonyEvents");
            field.setAccessible(true);
            field.set(sample, ImmutableList.<IColonyEventDescription>of(
              buildingEvent("Building Upgraded", "com.minecolonies.building.university", 2),
              citizenEvent("Citizen Grew Up", "Emma J. Roux"),
              buildingEvent("Building Built", "com.minecolonies.building.graveyard", 1),
              citizenEvent("Citizen Born", "Emma J. Roux")));
        }
        catch (final ReflectiveOperationException e)
        {
            MineColoniesVanillaTweaks.LOGGER.warn("Town hall preview: no sample events", e);
        }
        sample.getPermissionEvents().add(new PermissionEvent(new UUID(0, 3), "Alex", Action.OPEN_CONTAINER, new BlockPos(160, 70, -150)));
        sample.getPermissionEvents().add(new PermissionEvent(null, "[Minecraft]", Action.BREAK_BLOCKS, new BlockPos(161, 70, -151)));
        return sample;
    }

    private static IWorkOrderView workOrder(final int id, final String name, final int priority)
    {
        final int[] current = {priority};
        return proxy(IWorkOrderView.class, (method, args) -> switch (method.getName())
        {
            case "getID" -> id;
            case "getPriority" -> current[0];
            case "setPriority" ->
            {
                current[0] = (Integer) args[0];
                yield null;
            }
            case "getDisplayName" -> Component.literal(name);
            case "shouldShowIn" -> true;
            default -> null;
        });
    }

    private static IColonyEventDescription buildingEvent(final String name, final String building, final int level)
    {
        return proxy(IBuildingEventDescription.class, (method, args) -> switch (method.getName())
        {
            case "getName" -> name;
            case "getBuildingName" -> building;
            case "getLevel" -> level;
            case "getEventPos" -> new BlockPos(164, 70, -184);
            default -> null;
        });
    }

    private static IColonyEventDescription citizenEvent(final String name, final String citizen)
    {
        return proxy(ICitizenEventDescription.class, (method, args) -> switch (method.getName())
        {
            case "getName" -> name;
            case "getCitizenName" -> citizen;
            case "getEventPos" -> new BlockPos(145, 67, -103);
            default -> null;
        });
    }

    private static final Map<String, Double> MODIFIERS = Map.of(
      "social", 0.8D, "saturation", 1.5D, "security", 0.5D, "homelessness", 1.0D, "unemployment", 1.0D, "health", 1.0D, "idleatjob", 1.0D);

    private static ICitizenDataView citizen(final int id, final String name, final String job)
    {
        final ICitizenSkillHandler skills = proxy(ICitizenSkillHandler.class, (method, args) ->
          method.getName().equals("getLevel") ? 1 + (((Skill) args[0]).ordinal() * 9 + id * 5) % 23 : null);
        final ICitizenHappinessHandler happiness = proxy(ICitizenHappinessHandler.class, (method, args) -> switch (method.getName())
        {
            case "getModifiers" -> List.copyOf(MODIFIERS.keySet());
            case "getModifier" -> proxy(IHappinessModifier.class, (m, a) -> switch (m.getName())
            {
                case "getId" -> args[0];
                case "getFactor" -> MODIFIERS.get((String) args[0]) * (id % 2 == 0 ? 1.0D : 0.9D);
                default -> null;
            });
            default -> null;
        });
        return proxy(ICitizenDataView.class, (method, args) -> switch (method.getName())
        {
            case "getName" -> name;
            case "getId" -> id;
            case "getEntityId", "getColonyId" -> -1;
            case "isFemale" -> id % 2 == 0;
            case "getHealth" -> 14.0D + id;
            case "getMaxHealth" -> 20.0D;
            case "getSaturation" -> 20.0D + id * 4;
            case "getHappiness" -> id == 3 ? 2.0D : 4.5D + id * 0.5D;
            case "getJob" -> job;
            case "getJobComponent" -> job.isEmpty() ? Component.empty() : Component.translatable(job);
            case "getWorkBuilding", "getHomeBuilding", "getPosition", "getStatusPosition" -> BlockPos.ZERO;
            case "getVisibleStatus" -> VisibleCitizenStatus.WORKING;
            case "getCitizenSkillHandler" -> skills;
            case "getHappinessHandler" -> happiness;
            default -> null;
        });
    }

    private interface Handler
    {
        Object handle(Method method, Object[] args);
    }

    /** A proxy answering from {@code handler}, with zero / false for unhandled primitive results. */
    @SuppressWarnings("unchecked")
    private static <T> T proxy(final Class<T> type, final Handler handler)
    {
        return (T) Proxy.newProxyInstance(TownHallPreview.class.getClassLoader(), new Class<?>[] {type}, (self, method, args) -> {
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
            final Object result = handler.handle(method, args);
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
