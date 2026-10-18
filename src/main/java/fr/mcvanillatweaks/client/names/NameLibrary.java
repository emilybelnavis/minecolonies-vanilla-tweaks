package fr.mcvanillatweaks.client.names;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.names.NameList;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The player's citizen name lists, client side.
 * <ul>
 * <li>Built-in lists: every {@code assets/<namespace>/name_lists/<name>.json} of the loaded resource packs (the mod
 * ships country, history and fantasy ones; a resource pack adds more without code). Read-only, id
 * {@code pack:<ns>:<name>}, shown under {@code mcvanillatweaks.names.list.<name>} for ours, else their "name". Their
 * "category" is one of the {@code assets/<namespace>/name_list_categories/<id>.json} ({"icon": item id, "order"},
 * shown under {@code mcvanillatweaks.names.category.<id>} or their "name"); without one they go under "other", and
 * the player's lists under "mine". A list's optional "icon" (item id) replaces its book on the list screen.</li>
 * <li>Custom lists, and which lists are enabled: config/mcvanillatweaks-names.json, so they outlive worlds and
 * updates.</li>
 * </ul>
 * With nothing enabled, MineColonies keeps naming citizens itself.
 */
public final class NameLibrary
{
    public static final String PACK_PREFIX = "pack:";
    public static final String CUSTOM_PREFIX = "custom:";
    private static final String FOLDER = "name_lists";
    private static final String CATEGORY_FOLDER = "name_list_categories";
    public static final String MINE = "mine";
    public static final String OTHER = "other";
    private static final int MAX_CUSTOM = 100;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final Map<String, NameList> BUILT_IN = new LinkedHashMap<>();
    private static final Map<String, NameList> CUSTOM = new LinkedHashMap<>();
    private static final Set<String> ENABLED = new LinkedHashSet<>();
    private static final Map<String, Category> CATEGORIES = new LinkedHashMap<>();
    /** Category id of each built-in list. */
    private static final Map<String, String> CATEGORY_OF = new LinkedHashMap<>();
    /** Bumped on every change, so the screens and the sync know when to redo their work. */
    private static int revision;

    private NameLibrary()
    {
    }

    public static void registerReloadListener(final RegisterClientReloadListenersEvent event)
    {
        event.registerReloadListener((ResourceManagerReloadListener) NameLibrary::loadBuiltIn);
    }

    /** A group of lists on the list screen. */
    public record Category(String id, String name, ItemStack icon, int order)
    {
        public String displayName()
        {
            return I18n.exists(name) ? I18n.get(name) : name;
        }
    }

    private static void loadBuiltIn(final ResourceManager manager)
    {
        loadCategories(manager);
        BUILT_IN.clear();
        CATEGORY_OF.clear();
        final Map<ResourceLocation, Resource> found = manager.listResources(FOLDER, path -> path.getPath().endsWith(".json"));
        found.keySet().stream().sorted().forEach(location -> {
            final String path = location.getPath().substring(FOLDER.length() + 1, location.getPath().length() - ".json".length());
            final String id = PACK_PREFIX + location.getNamespace() + ":" + path;
            try (Reader reader = found.get(location).openAsReader())
            {
                final JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                final NameList list = NameList.fromJson(json, id);
                BUILT_IN.put(id, list.renamed(id, builtInName(location.getNamespace(), path, list.name())));
                final String category = json.has("category") ? json.get("category").getAsString() : OTHER;
                CATEGORY_OF.put(id, CATEGORIES.containsKey(category) ? category : OTHER);
            }
            catch (final IOException | RuntimeException e)
            {
                MineColoniesVanillaTweaks.LOGGER.warn("Could not read the name list {}", location, e);
            }
        });
        revision++;
    }

    private static void loadCategories(final ResourceManager manager)
    {
        CATEGORIES.clear();
        final Map<ResourceLocation, Resource> found = manager.listResources(CATEGORY_FOLDER, path -> path.getPath().endsWith(".json"));
        found.keySet().stream().sorted().forEach(location -> {
            final String id = location.getPath().substring(CATEGORY_FOLDER.length() + 1, location.getPath().length() - ".json".length());
            try (Reader reader = found.get(location).openAsReader())
            {
                final JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                final Item icon = json.has("icon") ? BuiltInRegistries.ITEM.get(ResourceLocation.parse(json.get("icon").getAsString())) : Items.AIR;
                final String key = "mcvanillatweaks.names.category." + id;
                final String name = json.has("name") ? json.get("name").getAsString() : key;
                CATEGORIES.put(id, new Category(id, location.getNamespace().equals(MineColoniesVanillaTweaks.MOD_ID) ? key : name,
                  new ItemStack(icon == Items.AIR ? Items.BOOK : icon), json.has("order") ? json.get("order").getAsInt() : 100));
            }
            catch (final IOException | RuntimeException e)
            {
                MineColoniesVanillaTweaks.LOGGER.warn("Could not read the name list category {}", location, e);
            }
        });
    }

    /** The category a list is shown under. */
    public static String categoryOf(final NameList list)
    {
        return isCustom(list) ? MINE : CATEGORY_OF.getOrDefault(list.id(), OTHER);
    }

    /** Every category in screen order: the resource packs' ones by "order", then "other", then the player's lists. */
    public static List<Category> categories()
    {
        final List<Category> categories = new ArrayList<>(CATEGORIES.values());
        categories.sort(Comparator.comparingInt(Category::order));
        categories.add(new Category(OTHER, "mcvanillatweaks.names.category.other", new ItemStack(Items.BOOK), Integer.MAX_VALUE - 1));
        categories.add(new Category(MINE, "mcvanillatweaks.names.category.mine", new ItemStack(Items.WRITABLE_BOOK), Integer.MAX_VALUE));
        return categories;
    }

    /**
     * Our lists are named by lang key (translated when shown: languages may load after this listener), others by
     * their "name", else the file name.
     */
    private static String builtInName(final String namespace, final String path, final String name)
    {
        if (namespace.equals(MineColoniesVanillaTweaks.MOD_ID))
        {
            return "mcvanillatweaks.names.list." + path;
        }
        return name.isBlank() ? path : name;
    }

    /** Shown name: built-in lists store a lang key or a plain name. */
    public static String displayName(final NameList list)
    {
        return I18n.exists(list.name()) ? I18n.get(list.name()) : list.name();
    }

    public static int revision()
    {
        return revision;
    }

    /** Built-in lists first, by shown name in the game language, then custom ones in creation order. */
    public static List<NameList> all()
    {
        final List<NameList> all = new ArrayList<>(BUILT_IN.values());
        final Collator collator = Collator.getInstance();
        all.sort(Comparator.comparing(NameLibrary::displayName, collator));
        all.addAll(CUSTOM.values());
        return all;
    }

    @Nullable
    public static NameList get(final String id)
    {
        final NameList list = CUSTOM.get(id);
        return list != null ? list : BUILT_IN.get(id);
    }

    /** The item shown before a list: its own icon when it names a known item, else a book (built in) or book and quill. */
    public static ItemStack icon(final NameList list)
    {
        final Item item = item(list.icon());
        return new ItemStack(item != null ? item : isCustom(list) ? Items.WRITABLE_BOOK : Items.BOOK);
    }

    /** The item of an id, or null when it is blank, malformed or unknown (a removed mod). */
    @Nullable
    public static Item item(final String id)
    {
        final ResourceLocation key = id.isBlank() ? null : ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key))
        {
            return null;
        }
        final Item item = BuiltInRegistries.ITEM.get(key);
        return item == Items.AIR ? null : item;
    }

    public static boolean isCustom(final NameList list)
    {
        return list.id().startsWith(CUSTOM_PREFIX);
    }

    public static boolean isEnabled(final NameList list)
    {
        return ENABLED.contains(list.id());
    }

    /** The enabled lists that still exist (a resource pack may have been removed). */
    public static List<NameList> enabled()
    {
        final List<NameList> lists = new ArrayList<>();
        for (final String id : ENABLED)
        {
            final NameList list = get(id);
            if (list != null && list.size() > 0)
            {
                lists.add(list.renamed(list.id(), displayName(list)));
            }
        }
        return lists;
    }

    public static void setEnabled(final NameList list, final boolean enabled)
    {
        if (enabled ? ENABLED.add(list.id()) : ENABLED.remove(list.id()))
        {
            changed();
        }
    }

    public static boolean canCreate()
    {
        return CUSTOM.size() < MAX_CUSTOM;
    }

    /** A new custom list, enabled, copying {@code from} when given. */
    @Nullable
    public static NameList create(final String name, @Nullable final NameList from)
    {
        if (!canCreate())
        {
            return null;
        }
        int number = 1;
        while (CUSTOM.containsKey(CUSTOM_PREFIX + number))
        {
            number++;
        }
        final String id = CUSTOM_PREFIX + number;
        final NameList list = from == null ? new NameList(id, name, List.of(), List.of(), List.of()) : from.renamed(id, name);
        CUSTOM.put(id, list);
        ENABLED.add(id);
        changed();
        return list;
    }

    /** Replaces a custom list by its edited copy (same id). */
    public static void update(final NameList list)
    {
        if (CUSTOM.containsKey(list.id()))
        {
            CUSTOM.put(list.id(), list);
            changed();
        }
    }

    public static void delete(final NameList list)
    {
        if (CUSTOM.remove(list.id()) != null)
        {
            ENABLED.remove(list.id());
            changed();
        }
    }

    private static void changed()
    {
        revision++;
        save();
    }

    private static Path file()
    {
        return FMLPaths.CONFIGDIR.get().resolve(MineColoniesVanillaTweaks.MOD_ID + "-names.json");
    }

    public static void load()
    {
        CUSTOM.clear();
        ENABLED.clear();
        final Path file = file();
        if (!Files.exists(file))
        {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
        {
            final JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.get("lists") instanceof JsonArray lists)
            {
                for (final JsonElement element : lists)
                {
                    if (element instanceof JsonObject object && CUSTOM.size() < MAX_CUSTOM)
                    {
                        final NameList list = NameList.fromJson(object, "");
                        if (list.id().startsWith(CUSTOM_PREFIX) && !CUSTOM.containsKey(list.id()))
                        {
                            CUSTOM.put(list.id(), list);
                        }
                    }
                }
            }
            if (json.get("enabled") instanceof JsonArray enabled)
            {
                enabled.forEach(id -> ENABLED.add(id.getAsString()));
            }
        }
        catch (final IOException | RuntimeException e)
        {
            MineColoniesVanillaTweaks.LOGGER.warn("Could not read {}", file, e);
        }
        revision++;
    }

    private static void save()
    {
        final JsonObject json = new JsonObject();
        final JsonArray enabled = new JsonArray();
        ENABLED.forEach(enabled::add);
        json.add("enabled", enabled);
        final JsonArray lists = new JsonArray();
        CUSTOM.values().forEach(list -> lists.add(list.toJson()));
        json.add("lists", lists);
        try (Writer writer = Files.newBufferedWriter(file(), StandardCharsets.UTF_8))
        {
            GSON.toJson(json, writer);
        }
        catch (final IOException e)
        {
            MineColoniesVanillaTweaks.LOGGER.warn("Could not save {}", file(), e);
        }
    }
}
