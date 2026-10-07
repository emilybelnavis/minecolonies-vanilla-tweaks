package fr.mcvanillatweaks.client.fixes;

import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.core.client.gui.citizen.AbstractWindowCitizen;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.citizen.LastCitizen;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Runs once on every MineColonies (BlockUI) window, right after MineColonies has filled it (BlockUI calls
 * {@code onOpened} on the window's first tick). Only seen when a vanilla-style screen is disabled or has failed.
 */
public final class BlockUiWindowHooks
{
    private static final Map<BOWindow, Boolean> HANDLED = Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean reported;

    private BlockUiWindowHooks()
    {
    }

    public static void onClientTick(final ClientTickEvent.Post event)
    {
        if (!(Minecraft.getInstance().screen instanceof BOScreen screen))
        {
            return;
        }
        final BOWindow window = screen.getWindow();
        if (window == null || HANDLED.put(window, Boolean.TRUE) != null)
        {
            return;
        }
        try
        {
            fixDeliveryPriority(window);
            if (window instanceof AbstractWindowCitizen citizenWindow)
            {
                // The citizen inventory does not expose its citizen: its side tabs use this one.
                LastCitizen.remember(LastCitizen.citizenOf(citizenWindow));
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            if (!reported)
            {
                reported = true;
                MineColoniesVanillaTweaks.LOGGER.warn("Could not adjust a MineColonies window", e);
            }
        }
    }

    /**
     * Hut main page: MineColonies writes "Pickup Prio.: &lt;value&gt;" in a 95 px wide field. In French
     * ("Prio. de récup.: ") the value wraps to a hidden second line. Widen the field up to the "-" button.
     */
    private static void fixDeliveryPriority(final BOWindow window)
    {
        final Pane value = window.findPaneByID("prioValue");
        final Pane minus = window.findPaneByID("deliveryPrioDown");
        if (!(value instanceof Text text) || minus == null || text.getParent() != minus.getParent())
        {
            return;
        }
        final int left = Math.min(text.getX(), 13);
        final int width = minus.getX() - 3 - left;
        if (width > text.getWidth())
        {
            text.setPosition(left, text.getY());
            text.setSize(width, text.getHeight());
        }
    }
}
