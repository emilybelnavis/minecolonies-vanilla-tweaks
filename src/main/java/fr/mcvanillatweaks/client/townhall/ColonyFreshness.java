package fr.mcvanillatweaks.client.townhall;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * How fresh this client's copy of a colony is. MineColonies only keeps colony views up to date for the players standing
 * in the colony's chunks ({@code ColonyPackageManager}'s close subscribers, the same test as
 * {@link IColonyView#isCoordInColony}); elsewhere the client keeps the copy it had when the player left (or the one sent
 * at login to the colony's members). This remembers, per colony, when the player was last inside.
 */
public final class ColonyFreshness
{
    private static final int CHECK_TICKS = 20;
    private static final Map<String, Long> LAST_INSIDE = new HashMap<>();
    private static int ticks;
    private static boolean failed;

    private ColonyFreshness()
    {
    }

    public static void onClientTick(final ClientTickEvent.Post event)
    {
        final Minecraft mc = Minecraft.getInstance();
        if (failed || mc.player == null || mc.level == null || ++ticks % CHECK_TICKS != 0)
        {
            return;
        }
        try
        {
            for (final IColonyView colony : IColonyManager.getInstance().getColonyViews(mc.level))
            {
                if (colony.getDimension() == mc.level.dimension() && colony.isCoordInColony(mc.level, mc.player.blockPosition()))
                {
                    LAST_INSIDE.put(key(colony), System.currentTimeMillis());
                }
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            failed = true;
            MineColoniesVanillaTweaks.LOGGER.warn("Colony freshness unavailable", e);
        }
    }

    public static void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event)
    {
        LAST_INSIDE.clear();
    }

    /** Whether the player stands in the colony, so MineColonies keeps its data up to date. */
    static boolean isLive(final IColonyView colony)
    {
        final Minecraft mc = Minecraft.getInstance();
        try
        {
            return mc.player != null && mc.level != null && colony.getDimension() == mc.level.dimension()
                     && colony.isCoordInColony(mc.level, mc.player.blockPosition());
        }
        catch (final LinkageError | RuntimeException e)
        {
            return false;
        }
    }

    /** Milliseconds since the player was last in the colony, or null if not since login. */
    @Nullable
    static Long sinceInside(final IColonyView colony)
    {
        final Long last = LAST_INSIDE.get(key(colony));
        return last == null ? null : System.currentTimeMillis() - last;
    }

    private static String key(final IColonyView colony)
    {
        return colony.getDimension().location() + "/" + colony.getID();
    }
}
