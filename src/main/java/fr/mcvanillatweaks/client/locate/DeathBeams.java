package fr.mcvanillatweaks.client.locate;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.IGraveData;
import com.minecolonies.api.tileentities.AbstractTileEntityGrave;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.config.HudConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A red {@link LocateBeams} beam above each citizen's grave. MineColonies syncs the colony's graves to its members
 * (its grave manager, what JourneyMap's death points read), so a new grave is a death: it gets a beam and a message.
 * Graves already there when the colony is first seen in the session get a beam too, silently. A beam goes away when
 * the grave does (collected by the undertaker or dug up), when the player reaches it, or after an hour; each grave gets
 * one beam per session.
 * <p>
 * The name comes from the grave block when its chunk is loaded on the client, otherwise from the citizen who left the
 * colony's list since the last scan.
 */
public final class DeathBeams
{
    private static final int SCAN_TICKS = 20;

    /** Graves already given a beam this session, per colony key. */
    private static final Set<String> SEEN = new HashSet<>();
    /** Colonies scanned once: their later graves are new deaths. */
    private static final Set<String> KNOWN_COLONIES = new HashSet<>();
    /** Citizen names at the last scan, per colony key, to name a grave whose block is not loaded. */
    private static final Map<String, Map<Integer, String>> CITIZENS = new HashMap<>();
    private static boolean failed;
    private static int ticks;

    private DeathBeams()
    {
    }

    private static boolean enabled()
    {
        try
        {
            return !failed && HudConfig.DEATH_BEAMS.get();
        }
        catch (final IllegalStateException e)
        {
            return !failed;
        }
    }

    public static void onClientTick(final ClientTickEvent.Post event)
    {
        final Minecraft mc = Minecraft.getInstance();
        if (++ticks % SCAN_TICKS != 0 || mc.level == null || mc.player == null || !enabled())
        {
            return;
        }
        try
        {
            for (final IColonyView colony : IColonyManager.getInstance().getColonyViews(mc.level))
            {
                scan(mc, colony);
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            failed = true;
            MineColoniesVanillaTweaks.LOGGER.warn("Death beams unavailable", e);
        }
    }

    private static void scan(final Minecraft mc, final IColonyView colony)
    {
        if (colony.getGraveManager() == null)
        {
            return;
        }
        final String colonyKey = colony.getDimension().location() + "/" + colony.getID();
        final boolean known = KNOWN_COLONIES.contains(colonyKey);
        final Map<Integer, String> before = CITIZENS.getOrDefault(colonyKey, Map.of());
        final Map<Integer, String> now = new HashMap<>();
        for (final ICitizenDataView citizen : colony.getCitizens().values())
        {
            now.put(citizen.getId(), citizen.getName());
        }
        final List<String> gone = new ArrayList<>();
        for (final Map.Entry<Integer, String> entry : before.entrySet())
        {
            if (!now.containsKey(entry.getKey()))
            {
                gone.add(entry.getValue());
            }
        }
        CITIZENS.put(colonyKey, now);

        final List<BlockPos> fresh = new ArrayList<>();
        for (final BlockPos pos : colony.getGraveManager().getGraves().keySet())
        {
            if (SEEN.add(colonyKey + "/" + pos.asLong()))
            {
                fresh.add(pos);
            }
        }
        for (final BlockPos pos : fresh)
        {
            String name = graveName(mc, pos);
            if (name == null && known && gone.size() == 1 && fresh.size() == 1)
            {
                name = gone.get(0);
            }
            final Component who = name == null ? Component.translatable("mcvanillatweaks.death.someone") : Component.literal(name);
            LocateBeams.addGrave(colony, pos, Component.translatable("mcvanillatweaks.death.label", who), known
              ? Component.translatable("mcvanillatweaks.death.added", who).withStyle(ChatFormatting.RED) : null);
        }
        KNOWN_COLONIES.add(colonyKey);
    }

    @Nullable
    private static String graveName(final Minecraft mc, final BlockPos pos)
    {
        if (mc.level != null && mc.level.isLoaded(pos) && mc.level.getBlockEntity(pos) instanceof AbstractTileEntityGrave grave)
        {
            final IGraveData data = grave.getGraveData();
            if (data != null && data.getCitizenName() != null && !data.getCitizenName().isEmpty())
            {
                return data.getCitizenName();
            }
        }
        return null;
    }

    public static void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event)
    {
        SEEN.clear();
        KNOWN_COLONIES.clear();
        CITIZENS.clear();
    }
}
