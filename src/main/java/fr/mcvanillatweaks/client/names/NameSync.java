package fr.mcvanillatweaks.client.names;

import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.names.NameListsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends the enabled lists to the server ({@link NameListsPayload}) on joining a world (single player included: the
 * integrated server has the channel) and when the list screen closes after a change.
 */
public final class NameSync
{
    /** {@link NameLibrary#revision()} last sent to this server, -1 for none. */
    private static int sent = -1;

    private NameSync()
    {
    }

    public enum State
    {
        /** Not in a world: sent on the next join. */
        OFFLINE,
        /** The server has this mod: new citizens of the player's colonies take names from the lists. */
        APPLIED,
        /** The server lacks this mod: MineColonies keeps naming citizens. */
        UNSUPPORTED
    }

    public static State state()
    {
        final ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null)
        {
            return State.OFFLINE;
        }
        return connection.hasChannel(NameListsPayload.TYPE) ? State.APPLIED : State.UNSUPPORTED;
    }

    public static void onLoggingIn(final ClientPlayerNetworkEvent.LoggingIn event)
    {
        sent = -1;
        sendIfChanged();
    }

    public static void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event)
    {
        sent = -1;
    }

    public static void sendIfChanged()
    {
        if (sent == NameLibrary.revision() || state() != State.APPLIED)
        {
            return;
        }
        try
        {
            for (final NameListsPayload payload : NameListsPayload.split(NameListsPayload.toJson(NameLibrary.enabled())))
            {
                PacketDistributor.sendToServer(payload);
            }
            sent = NameLibrary.revision();
        }
        catch (final LinkageError | RuntimeException e)
        {
            MineColoniesVanillaTweaks.LOGGER.warn("Could not send the citizen name lists", e);
        }
    }
}
