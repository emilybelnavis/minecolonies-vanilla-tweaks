package fr.mcvanillatweaks.client.townhall;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHallView;
import com.minecolonies.core.client.gui.modules.RequestTreeWindowModule;
import com.minecolonies.core.client.gui.WindowBannerPicker;
import com.minecolonies.core.client.gui.WindowClipBoard;
import com.minecolonies.core.client.gui.map.WindowColonyMap;
import com.minecolonies.core.client.gui.questlog.WindowQuestLog;
import com.ldtteam.blockui.BOScreen;
import com.minecolonies.core.client.gui.AbstractBuildingWindow;
import com.minecolonies.core.client.gui.townhall.AbstractWindowTownHall;
import com.minecolonies.core.client.gui.townhall.WindowAlliancePage;
import com.minecolonies.core.client.gui.townhall.WindowCitizenPage;
import com.minecolonies.core.client.gui.townhall.WindowInfoPage;
import com.minecolonies.core.client.gui.townhall.WindowMainPage;
import com.minecolonies.core.client.gui.townhall.WindowPermissionsPage;
import com.minecolonies.core.client.gui.townhall.WindowSettings;
import com.minecolonies.core.client.gui.townhall.WindowStatsPage;
import com.minecolonies.core.client.gui.townhall.WindowTownHallMercenary;
import com.minecolonies.core.client.gui.townhall.WindowTownHallNameEntry;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import com.minecolonies.core.network.messages.server.ClickGuiButtonTriggerMessage;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.config.HudConfig;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

/**
 * Replaces MineColonies' town hall windows with the vanilla-style {@link TownHallScreen}, the same way as the citizen
 * screen: MineColonies keeps opening its own windows (right click on the hut, our tabs, the windows it comes back to
 * after a dialog), and each one is swapped for the matching page when it is about to be shown. The MineColonies window
 * stays the source of the page, so its buttons can be used as they are.
 */
public final class TownHallScreens
{
    /** Set after an incompatibility: MineColonies' own windows are used for the rest of the session. */
    private static boolean failed;
    @Nullable
    private static TownHallPage pendingPage;

    private TownHallScreens()
    {
    }

    public static boolean enabled()
    {
        if (failed)
        {
            return false;
        }
        try
        {
            return HudConfig.VANILLA_TOWN_HALL_SCREEN.get();
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
        if (enabled() && screen != null && screen.getClass() == WindowBannerPicker.class)
        {
            openBanner(event, (WindowBannerPicker) screen);
            return;
        }
        if (enabled() && screen instanceof BOScreen clipboard && clipboard.getWindow().getClass() == WindowClipBoard.class)
        {
            openClipboard(event, (WindowClipBoard) clipboard.getWindow());
            return;
        }
        if (enabled() && screen instanceof BOScreen log && log.getWindow().getClass() == WindowQuestLog.class)
        {
            openQuestLog(event, (WindowQuestLog) log.getWindow());
            return;
        }
        if (enabled() && screen instanceof BOScreen map && map.getWindow().getClass() == WindowColonyMap.class)
        {
            openMap(event, (WindowColonyMap) map.getWindow());
            return;
        }
        if (enabled() && screen instanceof BOScreen dialog
              && (dialog.getWindow().getClass() == WindowTownHallNameEntry.class || dialog.getWindow().getClass() == WindowTownHallMercenary.class))
        {
            openDialog(event, dialog.getWindow());
            return;
        }
        if (!enabled() || !(screen instanceof BOScreen boScreen) || !(boScreen.getWindow() instanceof AbstractWindowTownHall window))
        {
            return;
        }
        try
        {
            final BuildingTownHall.View view = viewOf(window);
            if (view != null && view.getColony() != null)
            {
                event.setNewScreen(new TownHallScreen(view, pageOf(window), window));
            }
        }
        catch (final LinkageError | RuntimeException | ReflectiveOperationException e)
        {
            report(e);
        }
    }

    /** The quest log keeps its colony only in its page containers (private {@code moduleMap}, each with a {@code colonyView}). */
    private static void openQuestLog(final ScreenEvent.Opening event, final WindowQuestLog window)
    {
        try
        {
            final Field mapField = WindowQuestLog.class.getDeclaredField("moduleMap");
            mapField.setAccessible(true);
            if (mapField.get(window) instanceof java.util.Map<?, ?> modules && !modules.isEmpty())
            {
                final Object container = modules.values().iterator().next();
                final Field colonyField = container.getClass().getDeclaredField("colonyView");
                colonyField.setAccessible(true);
                if (colonyField.get(container) instanceof IColonyView colony)
                {
                    event.setNewScreen(new QuestLogScreen(colony));
                }
            }
        }
        catch (final LinkageError | RuntimeException | ReflectiveOperationException e)
        {
            report(e);
        }
    }

    /**
     * The flag editor (already a plain screen, but without a panel): its colony, the town hall window to go back to and
     * the patterns the town hall level allows, minus the supporter-only ones when locked.
     */
    @SuppressWarnings("unchecked")
    private static void openBanner(final ScreenEvent.Opening event, final WindowBannerPicker picker)
    {
        try
        {
            final Field colonyField = WindowBannerPicker.class.getDeclaredField("colony");
            colonyField.setAccessible(true);
            final Field windowField = WindowBannerPicker.class.getDeclaredField("window");
            windowField.setAccessible(true);
            final Field patternsField = WindowBannerPicker.class.getDeclaredField("patterns");
            patternsField.setAccessible(true);
            final Field unlockedField = WindowBannerPicker.class.getDeclaredField("isFeatureUnlocked");
            unlockedField.setAccessible(true);
            if (colonyField.get(picker) instanceof IColonyView colony && windowField.get(picker) instanceof AbstractWindowTownHall window
                  && patternsField.get(picker) instanceof java.util.List<?> list)
            {
                final boolean unlocked = unlockedField.get(picker) instanceof java.util.concurrent.atomic.AtomicBoolean flag && flag.get();
                final java.util.List<net.minecraft.core.Holder<net.minecraft.world.level.block.entity.BannerPattern>> patterns = new java.util.ArrayList<>();
                for (final Object entry : list)
                {
                    final var pattern = (net.minecraft.core.Holder<net.minecraft.world.level.block.entity.BannerPattern>) entry;
                    final boolean supporter = pattern.unwrapKey().map(key -> "minecolonies".equals(key.location().getNamespace())).orElse(false);
                    if (unlocked || !supporter)
                    {
                        patterns.add(pattern);
                    }
                }
                event.setNewScreen(new BannerScreen(colony, patterns, colony.getWorld().registryAccess()
                  .registryOrThrow(net.minecraft.core.registries.Registries.BANNER_PATTERN)
                  .getHolderOrThrow(net.minecraft.world.level.block.entity.BannerPatterns.BASE), window::open));
            }
        }
        catch (final LinkageError | RuntimeException | ReflectiveOperationException e)
        {
            report(e);
        }
    }

    /** The colony rename and mercenary dialogs keep their colony in a private {@code colony} field. */
    private static void openDialog(final ScreenEvent.Opening event, final Object window)
    {
        try
        {
            final Field colonyField = window.getClass().getDeclaredField("colony");
            colonyField.setAccessible(true);
            if (colonyField.get(window) instanceof IColonyView colony)
            {
                event.setNewScreen(window instanceof WindowTownHallMercenary ? new MercenaryScreen(colony) : new ColonyRenameScreen(colony));
            }
        }
        catch (final LinkageError | RuntimeException | ReflectiveOperationException e)
        {
            report(e);
        }
    }

    /** The map window keeps its town hall and where it was opened from in private fields. */
    private static void openMap(final ScreenEvent.Opening event, final WindowColonyMap window)
    {
        try
        {
            final Field buildingField = WindowColonyMap.class.getDeclaredField("building");
            buildingField.setAccessible(true);
            final Field atTownHallField = WindowColonyMap.class.getDeclaredField("atTownHall");
            atTownHallField.setAccessible(true);
            if (buildingField.get(window) instanceof ITownHallView view && view.getColony() != null)
            {
                event.setNewScreen(new ColonyMapScreen(view, atTownHallField.getBoolean(window)));
            }
        }
        catch (final LinkageError | RuntimeException | ReflectiveOperationException e)
        {
            report(e);
        }
    }

    /** The clipboard's colony is only kept by its request list module (protected {@code colony}). */
    private static void openClipboard(final ScreenEvent.Opening event, final WindowClipBoard window)
    {
        try
        {
            final Field moduleField = WindowClipBoard.class.getDeclaredField("requestTreeWindowModule");
            moduleField.setAccessible(true);
            final Object module = moduleField.get(window);
            final Field colonyField = RequestTreeWindowModule.class.getDeclaredField("colony");
            colonyField.setAccessible(true);
            final Field showField = WindowClipBoard.class.getDeclaredField("showImportant");
            showField.setAccessible(true);
            if (module != null && colonyField.get(module) instanceof IColonyView colony)
            {
                event.setNewScreen(new ClipboardScreen(colony, showField.getBoolean(window), ClipboardScreen.lastPage()));
            }
        }
        catch (final LinkageError | RuntimeException | ReflectiveOperationException e)
        {
            report(e);
        }
    }

    static void report(final Throwable e)
    {
        if (!failed)
        {
            failed = true;
            MineColoniesVanillaTweaks.LOGGER.warn("Vanilla town hall screen unavailable, falling back to MineColonies' windows", e);
        }
    }

    @Nullable
    private static BuildingTownHall.View viewOf(final AbstractWindowTownHall window) throws ReflectiveOperationException
    {
        final Field field = AbstractBuildingWindow.class.getDeclaredField("buildingView");
        field.setAccessible(true);
        return field.get(window) instanceof BuildingTownHall.View view ? view : null;
    }

    private static TownHallPage pageOf(final AbstractWindowTownHall window)
    {
        final TownHallPage pending = pendingPage;
        pendingPage = null;
        if (window instanceof WindowCitizenPage)
        {
            return pending == TownHallPage.HAPPINESS ? TownHallPage.HAPPINESS : TownHallPage.CITIZENS;
        }
        if (window instanceof WindowPermissionsPage)
        {
            return TownHallPage.PERMISSIONS;
        }
        if (window instanceof WindowInfoPage)
        {
            return TownHallPage.INFO;
        }
        if (window instanceof WindowStatsPage)
        {
            return TownHallPage.STATS;
        }
        if (window instanceof WindowAlliancePage)
        {
            return TownHallPage.ALLIANCES;
        }
        if (window instanceof WindowSettings)
        {
            return TownHallPage.SETTINGS;
        }
        return pending == TownHallPage.DASHBOARD ? TownHallPage.DASHBOARD : TownHallPage.ACTIONS;
    }

    /** Opens a page the way MineColonies' own tab buttons do (the window is then swapped for our screen). */
    static void open(final BuildingTownHall.View view, final TownHallPage page, @Nullable final AbstractWindowTownHall source)
    {
        if (source != null)
        {
            new ClickGuiButtonTriggerMessage(page.buttonId(), source.getXmlResourceLocation()).sendToServer();
        }
        pendingPage = page;
        switch (page)
        {
            // The dashboard has no MineColonies window: its main page is swapped for it.
            case ACTIONS, DASHBOARD -> new WindowMainPage(view).open();
            case INFO -> new WindowInfoPage(view).open();
            case CITIZENS, HAPPINESS -> new WindowCitizenPage(view).open();
            case STATS -> new WindowStatsPage(view).open();
            case PERMISSIONS -> new WindowPermissionsPage(view).open();
            case ALLIANCES -> new WindowAlliancePage(view).open();
            case SETTINGS -> new WindowSettings(view).open();
        }
    }
}
