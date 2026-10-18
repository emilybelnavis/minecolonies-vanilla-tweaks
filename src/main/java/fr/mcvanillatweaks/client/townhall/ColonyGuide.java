package fr.mcvanillatweaks.client.townhall;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.KeyBindings;
import fr.mcvanillatweaks.config.HudConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Opens the colony guide ({@link ColonyGuideScreen}) with its key, and says so in the action bar when the player walks
 * into someone else's colony. MineColonies sends a colony's huts to any player standing in its chunks, members or not
 * ({@code ColonyPackageManager} close subscribers), so the guide works for visitors; the message waits until the huts
 * have arrived, and a colony is announced again only after {@link #ANNOUNCE_AGAIN_MS}.
 */
public final class ColonyGuide
{
    private static final int CHECK_TICKS = 20;
    private static final long ANNOUNCE_AGAIN_MS = 10 * 60 * 1000L;
    /** Colony the player stands in, by key, and whether its message is still to show. */
    @Nullable
    private static String inside;
    private static boolean pending;
    private static final Map<String, Long> ANNOUNCED = new HashMap<>();
    private static int ticks;
    private static boolean failed;

    private ColonyGuide()
    {
    }

    public static void onClientTick(final ClientTickEvent.Post event)
    {
        final Minecraft mc = Minecraft.getInstance();
        while (KeyBindings.COLONY_GUIDE.consumeClick())
        {
            if (mc.screen == null && mc.player != null)
            {
                open(mc);
            }
        }
        if (failed || mc.player == null || mc.level == null || ++ticks % CHECK_TICKS != 0)
        {
            return;
        }
        try
        {
            final IColonyView colony = colonyAt(mc);
            final String key = colony == null ? null : key(colony);
            if (key == null ? inside != null : !key.equals(inside))
            {
                inside = key;
                pending = colony != null && !mc.player.getUUID().equals(colony.getPermissions().getOwner());
            }
            if (pending && colony != null && HudConfig.COLONY_GUIDE_MESSAGE.get())
            {
                final int huts = colony.getClientBuildingManager().getBuildings().size();
                if (huts > 0)
                {
                    pending = false;
                    final long now = System.currentTimeMillis();
                    final Long last = ANNOUNCED.get(key);
                    if (last == null || now - last > ANNOUNCE_AGAIN_MS)
                    {
                        ANNOUNCED.put(key, now);
                        mc.player.displayClientMessage(Component.translatable("mcvanillatweaks.guide.enter", colony.getName(),
                          colony.getPermissions().getOwnerName(), huts, KeyBindings.COLONY_GUIDE.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.YELLOW)), true);
                    }
                }
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            failed = true;
            MineColoniesVanillaTweaks.LOGGER.warn("Colony guide message unavailable", e);
        }
    }

    public static void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event)
    {
        inside = null;
        pending = false;
        ANNOUNCED.clear();
    }

    /** The guide of the colony the player stands in, else of the closest colony known in this dimension. */
    private static void open(final Minecraft mc)
    {
        try
        {
            IColonyView colony = colonyAt(mc);
            if (colony == null)
            {
                double best = Double.MAX_VALUE;
                for (final IColonyView view : IColonyManager.getInstance().getColonyViews(mc.level))
                {
                    if (view.getDimension() == mc.level.dimension() && !view.getClientBuildingManager().getBuildings().isEmpty())
                    {
                        final double distance = view.getCenter().distSqr(mc.player.blockPosition());
                        if (distance < best)
                        {
                            best = distance;
                            colony = view;
                        }
                    }
                }
            }
            if (colony == null)
            {
                mc.player.displayClientMessage(Component.translatable("mcvanillatweaks.guide.none"), true);
                return;
            }
            mc.setScreen(new ColonyGuideScreen(colony));
        }
        catch (final LinkageError | RuntimeException e)
        {
            MineColoniesVanillaTweaks.LOGGER.warn("Colony guide unavailable", e);
            mc.player.displayClientMessage(Component.translatable("mcvanillatweaks.guide.none"), true);
        }
    }

    @Nullable
    private static IColonyView colonyAt(final Minecraft mc)
    {
        for (final IColonyView colony : IColonyManager.getInstance().getColonyViews(mc.level))
        {
            if (colony.getDimension() == mc.level.dimension() && colony.isCoordInColony(mc.level, mc.player.blockPosition()))
            {
                return colony;
            }
        }
        return null;
    }

    private static String key(final IColonyView colony)
    {
        return colony.getDimension().location() + "/" + colony.getID();
    }
}
