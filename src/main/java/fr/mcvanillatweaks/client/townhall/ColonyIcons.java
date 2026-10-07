package fr.mcvanillatweaks.client.townhall;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.colonyEvents.descriptions.IBuildingEventDescription;
import com.minecolonies.api.colony.colonyEvents.descriptions.IColonyEventDescription;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Item icons for the town hall lists, so rows read like vanilla screens: huts for buildings, vanilla items for the rest. */
public final class ColonyIcons
{
    private static final ItemStack BUILDING = new ItemStack(Items.BRICKS);
    private static final ItemStack DEFAULT = new ItemStack(Items.PAPER);

    /** MineColonies statistic types (see its "townhall.stats.*" keys) and what pictures them. */
    private static final Map<String, Item> STATS = new HashMap<>();

    static
    {
        STATS.put("animals_butchered", Items.BEEF);
        STATS.put("arrows_fired", Items.ARROW);
        STATS.put("arrows_hit", Items.TARGET);
        STATS.put("birth", Items.EGG);
        STATS.put("blocks_mined", Items.IRON_PICKAXE);
        STATS.put("blocks_placed", Items.COBBLESTONE);
        STATS.put("blocks_placed_detail", Items.COBBLESTONE);
        STATS.put("breeding_attempts", Items.WHEAT);
        STATS.put("build_built", Items.BRICKS);
        STATS.put("build_removed", Items.TNT);
        STATS.put("build_repaired", Items.ANVIL);
        STATS.put("build_upgraded", Items.SCAFFOLDING);
        STATS.put("citizens_healed", Items.GLISTERING_MELON_SLICE);
        STATS.put("citizens_resurrected", Items.TOTEM_OF_UNDYING);
        STATS.put("citizens_visited", Items.BELL);
        STATS.put("crops_harvested", Items.WHEAT);
        STATS.put("death", Items.SKELETON_SKULL);
        STATS.put("deliveries_made", Items.CHEST);
        STATS.put("diseases_treated", Items.HONEY_BOTTLE);
        STATS.put("fish_caught", Items.COD);
        STATS.put("flowers_picked", Items.POPPY);
        STATS.put("food_cooked_detail", Items.COOKED_BEEF);
        STATS.put("food_served", Items.BREAD);
        STATS.put("food_served_detail", Items.BREAD);
        STATS.put("graves_dug", Items.IRON_SHOVEL);
        STATS.put("horses_trained", Items.SADDLE);
        STATS.put("ingredients_harvested", Items.SWEET_BERRIES);
        STATS.put("int_leveled", Items.BOOK);
        STATS.put("item_obtained", Items.CHEST);
        STATS.put("item_used", Items.FLINT_AND_STEEL);
        STATS.put("items_baked_detail", Items.BREAD);
        STATS.put("items_brewed", Items.BREWING_STAND);
        STATS.put("items_collected", Items.HOPPER);
        STATS.put("items_composted", Items.COMPOSTER);
        STATS.put("items_cooked", Items.COOKED_CHICKEN);
        STATS.put("items_crafted", Items.CRAFTING_TABLE);
        STATS.put("items_crafted_detail", Items.CRAFTING_TABLE);
        STATS.put("items_delivered", Items.CHEST);
        STATS.put("items_discovered", Items.SPYGLASS);
        STATS.put("items_enchanted", Items.ENCHANTED_BOOK);
        STATS.put("items_smelted_detail", Items.FURNACE);
        STATS.put("land_tilled", Items.IRON_HOE);
        STATS.put("lessons_given", Items.BOOK);
        STATS.put("levels_gained", Items.EXPERIENCE_BOTTLE);
        STATS.put("milking_attempts", Items.MILK_BUCKET);
        STATS.put("miner_deaths", Items.SKELETON_SKULL);
        STATS.put("mob_killed", Items.IRON_SWORD);
        STATS.put("mobs_killed", Items.IRON_SWORD);
        STATS.put("mounts_readied", Items.SADDLE);
        STATS.put("new_visitors", Items.OAK_DOOR);
        STATS.put("num_diseases_treated", Items.HONEY_BOTTLE);
        STATS.put("ores_broken", Items.IRON_INGOT);
        STATS.put("ores_mined", Items.IRON_ORE);
        STATS.put("patrols_started", Items.SHIELD);
        STATS.put("pickups_made", Items.CHEST_MINECART);
        STATS.put("product_collected", Items.HOPPER);
        STATS.put("research_completed", Items.WRITABLE_BOOK);
        STATS.put("roundups_completed", Items.LEAD);
        STATS.put("trees_cut", Items.OAK_LOG);
        STATS.put("trips_completed", Items.MAP);
        STATS.put("visitors_absconded", Items.LEATHER_BOOTS);
        STATS.put("visitors_recruited", Items.EMERALD);
    }

    private static final Map<String, ItemStack> STAT_STACKS = new HashMap<>();
    private static final Map<Item, ItemStack> DETAIL_STACKS = new HashMap<>();
    private static final Map<String, Item> ITEM_NAMES = new HashMap<>();
    private static String namesLanguage = "";

    private ColonyIcons()
    {
    }

    public static ItemStack stat(final String type)
    {
        return STAT_STACKS.computeIfAbsent(type, key -> new ItemStack(STATS.getOrDefault(key, Items.PAPER)));
    }

    /**
     * Icon of a detailed statistic ("food_served_detail;Bread"): MineColonies stores the item's name as the server
     * displayed it, so the name is looked up among the items' names in the game's language and in English (the
     * language of a dedicated server). The restaurant stores the translation key instead ("item.minecraft.bread"),
     * which is looked up too. Unknown names get the statistic's icon.
     */
    public static ItemStack statItem(final String type, final String name)
    {
        final Item item = itemsByName().get(name.trim().toLowerCase(Locale.ROOT));
        return item != null ? DETAIL_STACKS.computeIfAbsent(item, ItemStack::new) : stat(type);
    }

    private static Map<String, Item> itemsByName()
    {
        final Minecraft mc = Minecraft.getInstance();
        final String language = mc.getLanguageManager().getSelected();
        if (ITEM_NAMES.isEmpty() || !language.equals(namesLanguage))
        {
            ITEM_NAMES.clear();
            namesLanguage = language;
            Language english = null;
            try
            {
                english = ClientLanguage.loadFrom(mc.getResourceManager(), List.of("en_us"), false);
            }
            catch (final RuntimeException e)
            {
                // the game's language only
            }
            for (final Item item : BuiltInRegistries.ITEM)
            {
                if (item == Items.AIR)
                {
                    continue;
                }
                ITEM_NAMES.putIfAbsent(item.getDescriptionId().toLowerCase(Locale.ROOT), item);
                if (english != null)
                {
                    ITEM_NAMES.putIfAbsent(english.getOrDefault(item.getDescriptionId()).toLowerCase(Locale.ROOT), item);
                }
                // The game's language wins over English for names both share.
                ITEM_NAMES.put(new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT), item);
            }
        }
        return ITEM_NAMES;
    }

    /** The hut block of a building, bricks for anything else (decorations, unknown positions). */
    static ItemStack hut(@Nullable final IBuildingView building)
    {
        try
        {
            final BuildingEntry type = building == null ? null : building.getBuildingType();
            if (type != null && type.getBuildingBlock() != null)
            {
                return new ItemStack(type.getBuildingBlock());
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            // icon only: fall back to bricks
        }
        return BUILDING;
    }

    public static ItemStack hut(final IColonyView colony, final BlockPos pos)
    {
        try
        {
            return hut(colony.getClientBuildingManager().getBuilding(pos));
        }
        catch (final LinkageError | RuntimeException e)
        {
            return BUILDING;
        }
    }

    /** Colony events: the building's hut, or an item for what happened to the citizen. */
    static ItemStack event(final IColonyView colony, final IColonyEventDescription event)
    {
        if (event instanceof IBuildingEventDescription)
        {
            return hut(colony, event.getEventPos());
        }
        final String name = event.getName() == null ? "" : event.getName().toLowerCase(Locale.ROOT).replace(' ', '_');
        return switch (name)
        {
            case "citizen_born" -> new ItemStack(Items.EGG);
            case "citizen_died" -> new ItemStack(Items.SKELETON_SKULL);
            case "citizen_grew_up" -> new ItemStack(Items.CAKE);
            case "citizen_spawned" -> new ItemStack(Items.OAK_DOOR);
            case "visitor_spawned" -> new ItemStack(Items.EMERALD);
            default -> DEFAULT;
        };
    }
}
