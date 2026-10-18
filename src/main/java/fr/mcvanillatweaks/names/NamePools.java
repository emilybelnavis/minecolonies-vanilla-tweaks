package fr.mcvanillatweaks.names;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side: the enabled name lists each player sent ({@link NameListsPayload}), saved with the world
 * (data/mcvanillatweaks_citizen_names.dat) so a colony keeps its owner's lists while the owner is offline and after a
 * restart. A player who never sent lists (no mod on their client) has none: MineColonies names their citizens.
 */
public final class NamePools extends SavedData
{
    private static final String ID = "mcvanillatweaks_citizen_names";

    private final Map<UUID, List<NameList>> pools = new HashMap<>();

    public static NamePools get(final MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(NamePools::new, NamePools::load), ID);
    }

    public List<NameList> lists(final UUID player)
    {
        return pools.getOrDefault(player, List.of());
    }

    public void set(final UUID player, final List<NameList> lists)
    {
        if (lists.isEmpty())
        {
            if (pools.remove(player) != null)
            {
                setDirty();
            }
            return;
        }
        pools.put(player, List.copyOf(lists));
        setDirty();
    }

    private static NamePools load(final CompoundTag tag, final HolderLookup.Provider provider)
    {
        final NamePools data = new NamePools();
        for (final Tag entry : tag.getList("players", Tag.TAG_COMPOUND))
        {
            final CompoundTag player = (CompoundTag) entry;
            final List<NameList> lists = new ArrayList<>();
            for (final Tag listEntry : player.getList("lists", Tag.TAG_COMPOUND))
            {
                final CompoundTag list = (CompoundTag) listEntry;
                lists.add(new NameList(list.getString("id"), list.getString("name"), strings(list, NameList.Column.MALE),
                  strings(list, NameList.Column.FEMALE), strings(list, NameList.Column.SURNAMES)));
            }
            if (player.hasUUID("player") && !lists.isEmpty())
            {
                data.pools.put(player.getUUID("player"), lists);
            }
        }
        return data;
    }

    private static List<String> strings(final CompoundTag tag, final NameList.Column column)
    {
        final List<String> names = new ArrayList<>();
        for (final Tag name : tag.getList(column.key(), Tag.TAG_STRING))
        {
            names.add(name.getAsString());
        }
        return names;
    }

    @Override
    public CompoundTag save(final CompoundTag tag, final HolderLookup.Provider provider)
    {
        final ListTag players = new ListTag();
        pools.forEach((uuid, lists) -> {
            final CompoundTag player = new CompoundTag();
            player.putUUID("player", uuid);
            final ListTag listTags = new ListTag();
            for (final NameList list : lists)
            {
                final CompoundTag listTag = new CompoundTag();
                listTag.putString("id", list.id());
                listTag.putString("name", list.name());
                for (final NameList.Column column : NameList.Column.values())
                {
                    final ListTag names = new ListTag();
                    list.get(column).forEach(name -> names.add(StringTag.valueOf(name)));
                    listTag.put(column.key(), names);
                }
                listTags.add(listTag);
            }
            player.put("lists", listTags);
            players.add(player);
        });
        tag.put("players", players);
        return tag;
    }
}
