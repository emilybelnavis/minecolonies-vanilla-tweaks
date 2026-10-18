package fr.mcvanillatweaks.names;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client to server: the player's enabled name lists, as JSON ({"lists": [...]}, see {@link NameList#toJson}), cut in
 * parts since a serverbound payload is limited to 32 KiB. The server keeps the last complete copy in {@link NamePools}.
 * <p>
 * The channel is optional: players and servers without this mod still connect, and the client only sends when the
 * server has the channel.
 */
public record NameListsPayload(int part, int total, String chunk) implements CustomPacketPayload
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Characters per part: at most 3 bytes each in UTF-8, well under the 32767-byte limit. */
    public static final int CHUNK = 8000;
    private static final int MAX_PARTS = 64;
    private static final int MAX_LISTS = 100;

    public static final Type<NameListsPayload> TYPE =
      new Type<>(ResourceLocation.fromNamespaceAndPath("mcvanillatweaks", "citizen_names"));
    public static final StreamCodec<ByteBuf, NameListsPayload> CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, NameListsPayload::part,
      ByteBufCodecs.VAR_INT, NameListsPayload::total,
      ByteBufCodecs.stringUtf8(CHUNK), NameListsPayload::chunk,
      NameListsPayload::new);

    /** Parts received so far, per player. */
    private static final Map<UUID, StringBuilder> PENDING = new HashMap<>();

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void register(final RegisterPayloadHandlersEvent event)
    {
        event.registrar("1").optional().playToServer(TYPE, CODEC, NameListsPayload::handle);
    }

    /** The JSON cut into payloads, never splitting a surrogate pair. */
    public static List<NameListsPayload> split(final String json)
    {
        final List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < json.length() || chunks.isEmpty())
        {
            int end = Math.min(json.length(), start + CHUNK);
            if (end < json.length() && Character.isHighSurrogate(json.charAt(end - 1)))
            {
                end--;
            }
            chunks.add(json.substring(start, end));
            start = end;
        }
        final List<NameListsPayload> payloads = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++)
        {
            payloads.add(new NameListsPayload(i, chunks.size(), chunks.get(i)));
        }
        return payloads;
    }

    public static String toJson(final List<NameList> lists)
    {
        final JsonArray array = new JsonArray();
        lists.forEach(list -> array.add(list.toJson()));
        final JsonObject json = new JsonObject();
        json.add("lists", array);
        return json.toString();
    }

    private static void handle(final NameListsPayload payload, final IPayloadContext context)
    {
        if (!(context.player() instanceof ServerPlayer player))
        {
            return;
        }
        final UUID id = player.getUUID();
        if (payload.total < 1 || payload.total > MAX_PARTS || payload.part < 0 || payload.part >= payload.total)
        {
            PENDING.remove(id);
            return;
        }
        if (payload.part == 0)
        {
            PENDING.put(id, new StringBuilder());
        }
        final StringBuilder json = PENDING.get(id);
        if (json == null)
        {
            return;
        }
        json.append(payload.chunk);
        if (payload.part < payload.total - 1)
        {
            return;
        }
        PENDING.remove(id);
        try
        {
            final List<NameList> lists = new ArrayList<>();
            final JsonElement root = JsonParser.parseString(json.toString());
            if (root.getAsJsonObject().get("lists") instanceof JsonArray array)
            {
                for (final JsonElement element : array)
                {
                    if (element instanceof JsonObject object && lists.size() < MAX_LISTS)
                    {
                        final NameList list = NameList.fromJson(object, "list" + lists.size());
                        if (list.size() > 0)
                        {
                            lists.add(list);
                        }
                    }
                }
            }
            NamePools.get(player.server).set(id, lists);
        }
        catch (final RuntimeException e)
        {
            LOGGER.warn("Could not read the citizen name lists sent by {}", player.getGameProfile().getName(), e);
        }
    }
}
