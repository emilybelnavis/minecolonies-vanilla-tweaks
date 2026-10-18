package fr.mcvanillatweaks.names;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A list of citizen names: male and female first names, and surnames. Shared by the client (built-in and custom
 * lists, the editor) and the server (the lists each colony owner sent, see {@link NamePools}).
 * <p>
 * First names are one word (spaces become hyphens) so a citizen's surname is always what follows the first space,
 * which is how children inherit a parent's surname ({@link NameGenerator#surnameOf}).
 * <p>
 * {@code icon} is an item id shown before the list on the client ("" = the default book), unused by the server.
 */
public record NameList(String id, String name, List<String> male, List<String> female, List<String> surnames, String icon)
{
    public static final int MAX_LENGTH = 32;
    public static final int MAX_NAMES = 2000;

    public enum Column
    {
        MALE, FEMALE, SURNAMES;

        public String key()
        {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public NameList(final String id, final String name, final List<String> male, final List<String> female, final List<String> surnames)
    {
        this(id, name, male, female, surnames, "");
    }

    public NameList
    {
        icon = icon == null ? "" : icon;
        male = List.copyOf(male);
        female = List.copyOf(female);
        surnames = List.copyOf(surnames);
    }

    public List<String> get(final Column column)
    {
        return switch (column)
        {
            case MALE -> male;
            case FEMALE -> female;
            case SURNAMES -> surnames;
        };
    }

    public List<String> firstNames(final boolean female)
    {
        return female ? this.female : male;
    }

    public int size()
    {
        return male.size() + female.size() + surnames.size();
    }

    public NameList with(final Column column, final List<String> names)
    {
        return new NameList(id, name,
          column == Column.MALE ? names : male,
          column == Column.FEMALE ? names : female,
          column == Column.SURNAMES ? names : surnames, icon);
    }

    public NameList renamed(final String id, final String name)
    {
        return new NameList(id, name, male, female, surnames, icon);
    }

    public NameList withIcon(final String icon)
    {
        return new NameList(id, name, male, female, surnames, icon);
    }

    /** A name as it would be stored in this column, or null if nothing is left of it. */
    @Nullable
    public static String clean(final Column column, final String raw)
    {
        String name = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        if (column != Column.SURNAMES)
        {
            name = name.replace(' ', '-');
        }
        if (name.length() > MAX_LENGTH)
        {
            name = name.substring(0, MAX_LENGTH).strip();
        }
        return name.isEmpty() ? null : name;
    }

    public JsonObject toJson()
    {
        final JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("name", name);
        if (!icon.isEmpty())
        {
            json.addProperty("icon", icon);
        }
        for (final Column column : Column.values())
        {
            final JsonArray array = new JsonArray();
            get(column).forEach(array::add);
            json.add(column.key(), array);
        }
        return json;
    }

    /** Reads a list, cleaning every name and dropping duplicates (never throws on bad entries). */
    public static NameList fromJson(final JsonObject json, final String defaultId)
    {
        final String id = json.has("id") ? json.get("id").getAsString() : defaultId;
        final String name = json.has("name") && json.get("name").isJsonPrimitive() ? json.get("name").getAsString() : "";
        final String icon = json.has("icon") && json.get("icon").isJsonPrimitive() ? json.get("icon").getAsString().strip() : "";
        return new NameList(id, name, read(json, Column.MALE), read(json, Column.FEMALE), read(json, Column.SURNAMES),
          icon.length() > 100 ? "" : icon);
    }

    private static List<String> read(final JsonObject json, final Column column)
    {
        final Set<String> names = new LinkedHashSet<>();
        if (json.get(column.key()) instanceof JsonArray array)
        {
            for (final JsonElement element : array)
            {
                if (names.size() >= MAX_NAMES)
                {
                    break;
                }
                if (element.isJsonPrimitive())
                {
                    final String name = clean(column, element.getAsString());
                    if (name != null)
                    {
                        names.add(name);
                    }
                }
            }
        }
        return new ArrayList<>(names);
    }
}
