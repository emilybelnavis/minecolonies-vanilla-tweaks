package fr.mcvanillatweaks;

import com.mojang.logging.LogUtils;
import fr.mcvanillatweaks.client.KeyBindings;
import fr.mcvanillatweaks.client.ModSettings;
import fr.mcvanillatweaks.client.PinnedBuilders;
import fr.mcvanillatweaks.client.citizen.CitizenScreens;
import fr.mcvanillatweaks.client.townhall.ColonyFreshness;
import fr.mcvanillatweaks.client.townhall.TownHallScreens;
import fr.mcvanillatweaks.client.hut.HutScreens;
import fr.mcvanillatweaks.client.hut.TeachScreens;
import fr.mcvanillatweaks.client.buildtool.BuildToolScreens;
import fr.mcvanillatweaks.client.domum.CutterScreens;
import fr.mcvanillatweaks.client.university.UniversityScreens;
import fr.mcvanillatweaks.client.data.ResourceHudTracker;
import fr.mcvanillatweaks.client.fixes.BlockUiWindowHooks;
import fr.mcvanillatweaks.client.fixes.CitizenInventoryTabs;
import fr.mcvanillatweaks.client.hud.ResourceHudLayer;
import fr.mcvanillatweaks.client.locate.LocateBeams;
import fr.mcvanillatweaks.client.names.NameLibrary;
import fr.mcvanillatweaks.client.names.NameSync;
import fr.mcvanillatweaks.config.ConfigMigration;
import fr.mcvanillatweaks.config.HudConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * MineColonies Vanilla Tweaks, client entry point (the one for both sides is {@link MineColoniesVanillaTweaksCommon}).
 * Vanilla-style screens for MineColonies' windows, a HUD of the builders' resources, "locate" beams and the editor of
 * the citizen name lists.
 */
@Mod(value = MineColoniesVanillaTweaks.MOD_ID, dist = Dist.CLIENT)
public final class MineColoniesVanillaTweaks
{
    public static final String MOD_ID = "mcvanillatweaks";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static ModContainer modContainer;

    public MineColoniesVanillaTweaks(final IEventBus modBus, final ModContainer container)
    {
        modContainer = container;
        // Before registering: NeoForge drops the old top-level entries when it loads the new sections.
        ConfigMigration.readOldFile();
        container.registerConfig(ModConfig.Type.CLIENT, HudConfig.SPEC);
        modBus.addListener(ConfigMigration::onConfigLoading);
        // NeoForge's generic config screen (Mods > MineColonies Vanilla Tweaks > Config).
        container.registerExtensionPoint(IConfigScreenFactory.class, ModSettings::create);

        modBus.addListener(KeyBindings::register);
        modBus.addListener(ResourceHudLayer::register);
        modBus.addListener(LocateBeams::registerLayer);
        modBus.addListener(NameLibrary::registerReloadListener);

        NeoForge.EVENT_BUS.addListener(ResourceHudTracker::onClientTick);
        NeoForge.EVENT_BUS.addListener(ResourceHudTracker::onLoggingOut);
        // Fixes for long-standing MineColonies UI bugs, for its own windows.
        NeoForge.EVENT_BUS.addListener(BlockUiWindowHooks::onClientTick);
        NeoForge.EVENT_BUS.addListener(CitizenInventoryTabs::onScreenInit);
        // Vanilla-style citizen screen, replacing MineColonies' citizen windows as they open.
        NeoForge.EVENT_BUS.addListener(CitizenScreens::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(TownHallScreens::onScreenOpening);
        // When the player was last in each colony (the clipboard's dashboard says how old its data is).
        NeoForge.EVENT_BUS.addListener(ColonyFreshness::onClientTick);
        NeoForge.EVENT_BUS.addListener(ColonyFreshness::onLoggingOut);
        // Colony guide (key G): the huts of the colony the player stands in, and a message on entering someone else's.
        NeoForge.EVENT_BUS.addListener(fr.mcvanillatweaks.client.townhall.ColonyGuide::onClientTick);
        NeoForge.EVENT_BUS.addListener(fr.mcvanillatweaks.client.townhall.ColonyGuide::onLoggingOut);
        // "Locate" beams above citizens and huts.
        NeoForge.EVENT_BUS.addListener(LocateBeams::onClientTick);
        NeoForge.EVENT_BUS.addListener(fr.mcvanillatweaks.client.locate.DeathBeams::onClientTick);
        NeoForge.EVENT_BUS.addListener(fr.mcvanillatweaks.client.locate.DeathBeams::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(LocateBeams::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(LocateBeams::onRenderLevel);
        // Builders' "building finished" messages: a click beams the hut and/or gives its coordinates.
        NeoForge.EVENT_BUS.addListener(fr.mcvanillatweaks.client.locate.CompletionMessages::onChatReceived);
        NeoForge.EVENT_BUS.addListener(fr.mcvanillatweaks.client.locate.CompletionMessages::registerCommands);
        NeoForge.EVENT_BUS.addListener(UniversityScreens::onScreenOpening);
        // Every other hut: home page, workers, settings, statistics (and the university's research page).
        NeoForge.EVENT_BUS.addListener(HutScreens::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(BuildToolScreens::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(CutterScreens::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(TeachScreens::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(fr.mcvanillatweaks.client.dialog.DialogScreens::onScreenOpening);

        // Citizen name lists: sent to the server (single player included) on joining a world.
        NeoForge.EVENT_BUS.addListener(NameSync::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(NameSync::onLoggingOut);

        PinnedBuilders.load();
        NameLibrary.load();
    }

    public static ModContainer container()
    {
        return modContainer;
    }
}
