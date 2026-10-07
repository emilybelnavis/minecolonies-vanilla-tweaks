package fr.mcvanillatweaks.client.domum;

import com.ldtteam.domumornamentum.client.screens.ArchitectsCutterScreen;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.config.HudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Swaps Domum Ornamentum's Architect's Cutter screen for {@link CutterScreen}, on the same menu, when it is about to be
 * shown. If anything is incompatible, Domum's screen is used for the rest of the session.
 */
public final class CutterScreens
{
    /** Set after an incompatibility: Domum's own screen is used for the rest of the session. */
    private static boolean failed;

    private CutterScreens()
    {
    }

    private static boolean enabled()
    {
        try
        {
            return HudConfig.VANILLA_ARCHITECTS_CUTTER.get();
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
        if (failed || !enabled() || screen == null || screen.getClass() != ArchitectsCutterScreen.class)
        {
            return;
        }
        try
        {
            final ArchitectsCutterScreen original = (ArchitectsCutterScreen) screen;
            final Minecraft mc = Minecraft.getInstance();
            if (mc.player != null)
            {
                event.setNewScreen(new CutterScreen(original.getMenu(), mc.player.getInventory(), original.getTitle()));
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            failed = true;
            MineColoniesVanillaTweaks.LOGGER.warn("Vanilla Architect's Cutter screen unavailable, falling back to Domum Ornamentum's", e);
        }
    }
}
