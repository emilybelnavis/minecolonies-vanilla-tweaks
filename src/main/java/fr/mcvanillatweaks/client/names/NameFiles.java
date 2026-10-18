package fr.mcvanillatweaks.client.names;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.names.NameList;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Name lists to and from .json files: "export" writes one list ({"name", "icon", "male", "female", "surnames"}),
 * "import" reads files written that way or by hand. Import is forgiving, since players write these files themselves:
 * comments, missing quotes (Gson's lenient mode) and trailing commas are accepted, keys may be French or English and a column may be one
 * string of comma-separated names; a file may hold one list, an array of lists or {"lists": [...]} (the mod's own
 * config file).
 * <p>
 * The files are chosen with the system's dialog (LWJGL's TinyFileDialogs, shipped with the game), opened on its own
 * thread so the game keeps drawing; the result comes back on the render thread.
 */
final class NameFiles
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final long MAX_BYTES = 2_000_000L;
    private static final Pattern LINE = Pattern.compile("line (\\d+)");
    private static final Pattern TRAILING_COMMA = Pattern.compile(",(\\s*[}\\]])");
    /** Accepted keys, folded (no accents, case or separators), for each column; "prenoms" alone fills both genders. */
    private static final Map<String, List<NameList.Column>> COLUMN_KEYS = Map.ofEntries(
      Map.entry("male", List.of(NameList.Column.MALE)),
      Map.entry("males", List.of(NameList.Column.MALE)),
      Map.entry("men", List.of(NameList.Column.MALE)),
      Map.entry("boys", List.of(NameList.Column.MALE)),
      Map.entry("malefirstnames", List.of(NameList.Column.MALE)),
      Map.entry("masculin", List.of(NameList.Column.MALE)),
      Map.entry("masculins", List.of(NameList.Column.MALE)),
      Map.entry("hommes", List.of(NameList.Column.MALE)),
      Map.entry("garcons", List.of(NameList.Column.MALE)),
      Map.entry("prenomsmasculins", List.of(NameList.Column.MALE)),
      Map.entry("female", List.of(NameList.Column.FEMALE)),
      Map.entry("females", List.of(NameList.Column.FEMALE)),
      Map.entry("women", List.of(NameList.Column.FEMALE)),
      Map.entry("girls", List.of(NameList.Column.FEMALE)),
      Map.entry("femalefirstnames", List.of(NameList.Column.FEMALE)),
      Map.entry("feminin", List.of(NameList.Column.FEMALE)),
      Map.entry("feminins", List.of(NameList.Column.FEMALE)),
      Map.entry("femmes", List.of(NameList.Column.FEMALE)),
      Map.entry("filles", List.of(NameList.Column.FEMALE)),
      Map.entry("prenomsfeminins", List.of(NameList.Column.FEMALE)),
      Map.entry("firstnames", List.of(NameList.Column.MALE, NameList.Column.FEMALE)),
      Map.entry("prenoms", List.of(NameList.Column.MALE, NameList.Column.FEMALE)),
      Map.entry("surnames", List.of(NameList.Column.SURNAMES)),
      Map.entry("surname", List.of(NameList.Column.SURNAMES)),
      Map.entry("lastnames", List.of(NameList.Column.SURNAMES)),
      Map.entry("familynames", List.of(NameList.Column.SURNAMES)),
      Map.entry("noms", List.of(NameList.Column.SURNAMES)),
      Map.entry("nom", List.of(NameList.Column.SURNAMES)),
      Map.entry("nomsdefamille", List.of(NameList.Column.SURNAMES)),
      Map.entry("nomdefamille", List.of(NameList.Column.SURNAMES)));
    private static final List<String> NAME_KEYS = List.of("name", "title", "titre", "nom", "listname", "nomdelaliste");
    private static final List<String> ICON_KEYS = List.of("icon", "icone", "item", "objet");

    /** A dialog is open: a second click on import / export does nothing until it closes. */
    private static volatile boolean dialogOpen;

    private NameFiles()
    {
    }

    /** What an import did: the new lists and one line per file (or list) that could not be read. */
    record Result(List<NameList> imported, List<Component> problems)
    {
    }

    // ---- Dialogs ----

    /** Lets the player pick .json files, imports them and hands the result to {@code done} (nothing when cancelled). */
    static void chooseAndImport(final Consumer<Result> done)
    {
        final String title = Component.translatable("mcvanillatweaks.names.import.dialog").getString();
        dialog(() -> {
            try (MemoryStack stack = MemoryStack.stackPush())
            {
                final String chosen = TinyFileDialogs.tinyfd_openFileDialog(title, startFolder() + File.separator, jsonFilter(stack), "JSON", true);
                if (chosen == null)
                {
                    return null;
                }
                final List<Path> paths = new ArrayList<>();
                for (final String path : chosen.split("\\|"))
                {
                    if (!path.isBlank())
                    {
                        paths.add(Path.of(path));
                    }
                }
                return () -> done.accept(importFiles(paths));
            }
        }, () -> done.accept(new Result(List.of(), List.of(Component.translatable("mcvanillatweaks.names.import.no_dialog")))));
    }

    /** Lets the player choose where to save {@code list} and hands the message to show to {@code done}. */
    static void chooseAndExport(final NameList list, final Consumer<Component> done)
    {
        final String title = Component.translatable("mcvanillatweaks.names.export.dialog").getString();
        final String name = NameLibrary.displayName(list);
        final JsonObject json = exportJson(list, name);
        dialog(() -> {
            try (MemoryStack stack = MemoryStack.stackPush())
            {
                final String chosen = TinyFileDialogs.tinyfd_saveFileDialog(title, startFolder().resolve(fileName(name)).toString(), jsonFilter(stack), "JSON");
                if (chosen == null)
                {
                    return null;
                }
                final Path path = Path.of(chosen.toLowerCase(Locale.ROOT).endsWith(".json") ? chosen : chosen + ".json");
                final Component message = write(json, path);
                return () -> done.accept(message);
            }
        }, () -> done.accept(Component.translatable("mcvanillatweaks.names.export.no_dialog")));
    }

    /**
     * Runs {@code dialog} on its own thread; what it returns runs on the render thread. {@code unavailable} runs
     * when the system has no dialog (no TinyFileDialogs, or it failed).
     */
    private static void dialog(final Supplier<Runnable> dialog, final Runnable unavailable)
    {
        if (dialogOpen)
        {
            return;
        }
        dialogOpen = true;
        final Minecraft minecraft = Minecraft.getInstance();
        final Thread thread = new Thread(() -> {
            Runnable then;
            try
            {
                then = dialog.get();
            }
            catch (final LinkageError | RuntimeException e)
            {
                MineColoniesVanillaTweaks.LOGGER.warn("File dialog unavailable", e);
                then = unavailable;
            }
            dialogOpen = false;
            if (then != null)
            {
                minecraft.execute(then);
            }
        }, "mcvanillatweaks name list file dialog");
        thread.setDaemon(true);
        thread.start();
    }

    private static PointerBuffer jsonFilter(final MemoryStack stack)
    {
        final PointerBuffer filter = stack.mallocPointer(1);
        filter.put(stack.UTF8("*.json"));
        filter.flip();
        return filter;
    }

    /** The Downloads folder when there is one, else the home folder. */
    private static Path startFolder()
    {
        final Path home = Path.of(System.getProperty("user.home"));
        final Path downloads = home.resolve("Downloads");
        return Files.isDirectory(downloads) ? downloads : home;
    }

    private static String fileName(final String name)
    {
        final String clean = name.replaceAll("[\\\\/:*?\"<>|]", "_").strip();
        return (clean.isEmpty() ? "names" : clean) + ".json";
    }

    // ---- Export ----

    private static JsonObject exportJson(final NameList list, final String name)
    {
        final JsonObject json = new JsonObject();
        json.addProperty("name", name);
        if (!list.icon().isEmpty())
        {
            json.addProperty("icon", list.icon());
        }
        for (final NameList.Column column : NameList.Column.values())
        {
            final JsonArray array = new JsonArray();
            list.get(column).forEach(array::add);
            json.add(column.key(), array);
        }
        return json;
    }

    private static Component write(final JsonObject json, final Path path)
    {
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8))
        {
            GSON.toJson(json, writer);
            return Component.translatable("mcvanillatweaks.names.export.done", path.getFileName().toString());
        }
        catch (final IOException | RuntimeException e)
        {
            MineColoniesVanillaTweaks.LOGGER.warn("Could not export a name list to {}", path, e);
            return Component.translatable("mcvanillatweaks.names.export.failed", path.getFileName().toString());
        }
    }

    // ---- Import ----

    /** Reads every file into new custom lists (enabled, like a new list). Runs on the render thread. */
    static Result importFiles(final List<Path> paths)
    {
        final List<NameList> imported = new ArrayList<>();
        final List<Component> problems = new ArrayList<>();
        for (final Path path : paths)
        {
            final String file = path.getFileName() == null ? path.toString() : path.getFileName().toString();
            final JsonElement root;
            try
            {
                if (Files.size(path) > MAX_BYTES)
                {
                    problems.add(Component.translatable("mcvanillatweaks.names.import.too_big", file));
                    continue;
                }
                String text = Files.readString(path, StandardCharsets.UTF_8);
                if (text.startsWith("﻿"))
                {
                    text = text.substring(1);
                }
                // A comma left after the last entry is the usual hand-writing slip: Gson rejects it even when lenient.
                root = JsonParser.parseString(TRAILING_COMMA.matcher(text).replaceAll("$1"));
            }
            catch (final IOException e)
            {
                problems.add(Component.translatable("mcvanillatweaks.names.import.unreadable", file));
                continue;
            }
            catch (final RuntimeException e)
            {
                final Matcher line = LINE.matcher(String.valueOf(e.getMessage()));
                problems.add(line.find()
                  ? Component.translatable("mcvanillatweaks.names.import.invalid.line", file, line.group(1))
                  : Component.translatable("mcvanillatweaks.names.import.invalid", file));
                continue;
            }
            final List<JsonObject> objects = listObjects(root);
            final String stem = file.toLowerCase(Locale.ROOT).endsWith(".json") ? file.substring(0, file.length() - 5) : file;
            int found = 0;
            for (final JsonObject object : objects)
            {
                final NameList list = read(object);
                if (list.size() == 0)
                {
                    continue;
                }
                found++;
                if (!NameLibrary.canCreate())
                {
                    problems.add(Component.translatable("mcvanillatweaks.names.import.too_many"));
                    return new Result(imported, problems);
                }
                final String base = list.name().isBlank() ? (objects.size() > 1 ? stem + " " + found : stem) : list.name();
                final NameList created = NameLibrary.create(NameListsScreen.uniqueName(base.length() > NameList.MAX_LENGTH
                  ? base.substring(0, NameList.MAX_LENGTH).strip() : base), list);
                if (created != null)
                {
                    imported.add(created);
                }
            }
            if (found == 0)
            {
                problems.add(Component.translatable("mcvanillatweaks.names.import.empty", file));
            }
        }
        return new Result(imported, problems);
    }

    /** The list objects of a file: one list, an array of lists, or {"lists": [...]}. */
    private static List<JsonObject> listObjects(final JsonElement root)
    {
        final List<JsonObject> objects = new ArrayList<>();
        JsonElement lists = root;
        if (root instanceof JsonObject object && object.get("lists") instanceof JsonArray array)
        {
            lists = array;
        }
        if (lists instanceof JsonArray array)
        {
            array.forEach(element -> {
                if (element instanceof JsonObject object)
                {
                    objects.add(object);
                }
            });
        }
        else if (lists instanceof JsonObject object)
        {
            objects.add(object);
        }
        return objects;
    }

    /** One list, whatever the keys are called; unknown keys are ignored. */
    private static NameList read(final JsonObject object)
    {
        final JsonObject clean = new JsonObject();
        final Map<NameList.Column, JsonArray> columns = new EnumMap<>(NameList.Column.class);
        for (final NameList.Column column : NameList.Column.values())
        {
            columns.put(column, new JsonArray());
        }
        for (final Map.Entry<String, JsonElement> entry : object.entrySet())
        {
            final String key = NameListsScreen.fold(entry.getKey()).replaceAll("[^a-z]", "");
            final JsonElement value = entry.getValue();
            final List<NameList.Column> targets = value.isJsonArray() || !NAME_KEYS.contains(key) ? COLUMN_KEYS.get(key) : null;
            if (targets != null)
            {
                for (final String name : strings(value))
                {
                    targets.forEach(column -> columns.get(column).add(name));
                }
            }
            else if (value.isJsonPrimitive() && NAME_KEYS.contains(key) && !clean.has("name"))
            {
                clean.addProperty("name", value.getAsString().strip());
            }
            else if (value.isJsonPrimitive() && ICON_KEYS.contains(key))
            {
                final String icon = value.getAsString().strip().toLowerCase(Locale.ROOT);
                if (NameLibrary.item(icon) != null)
                {
                    clean.addProperty("icon", icon);
                }
            }
        }
        columns.forEach((column, array) -> clean.add(column.key(), array));
        return NameList.fromJson(clean, "");
    }

    /** The names of a column: an array of strings, or one string separated by commas, semicolons or new lines. */
    private static List<String> strings(@Nullable final JsonElement value)
    {
        final List<String> names = new ArrayList<>();
        if (value instanceof JsonArray array)
        {
            array.forEach(element -> {
                if (element.isJsonPrimitive())
                {
                    names.add(element.getAsString());
                }
            });
        }
        else if (value != null && value.isJsonPrimitive())
        {
            names.addAll(List.of(value.getAsString().split("[,;\\n]")));
        }
        return names;
    }
}
