package fr.mcvanillatweaks.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.data.BuilderRef;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Construction sites pinned by the player (pin key while holding a linked Resource Scroll), with their optional
 * scroll name and custom screen position. Stored client-side in config/mcvanillatweaks-pinned.json.
 */
public final class PinnedBuilders
{
    /**
     * A pinned construction site. The position is stored as fractions of the free screen space (0 = left/top edge,
     * 1 = right/bottom edge) so it survives window resizes and HUD scale changes; null means "default corner".
     */
    public static final class Pin
    {
        private final BuilderRef ref;
        @Nullable
        private String label;
        @Nullable
        private Float x;
        @Nullable
        private Float y;

        private Pin(final BuilderRef ref, @Nullable final String label)
        {
            this.ref = ref;
            this.label = label;
        }

        public BuilderRef ref()
        {
            return ref;
        }

        @Nullable
        public String label()
        {
            return label;
        }

        public boolean hasPosition()
        {
            return x != null && y != null;
        }

        public float x()
        {
            return x == null ? 0 : x;
        }

        public float y()
        {
            return y == null ? 0 : y;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<BuilderRef, Pin> PINNED = new LinkedHashMap<>();

    private PinnedBuilders()
    {
    }

    private static Path file()
    {
        return FMLPaths.CONFIGDIR.get().resolve(MineColoniesVanillaTweaks.MOD_ID + "-pinned.json");
    }

    public static List<BuilderRef> refs()
    {
        return List.copyOf(PINNED.keySet());
    }

    public static Optional<Pin> get(final BuilderRef ref)
    {
        return Optional.ofNullable(PINNED.get(ref));
    }

    /**
     * @param label custom name of the scroll, or null.
     * @return true if the builder is now pinned, false if it was unpinned.
     */
    public static boolean toggle(final BuilderRef ref, @Nullable final String label)
    {
        final boolean pinned = PINNED.remove(ref) == null;
        if (pinned)
        {
            PINNED.put(ref, new Pin(ref, label));
        }
        save();
        return pinned;
    }

    /**
     * Keeps the stored scroll name in sync when the player holds a pinned scroll (e.g. after renaming it in an anvil).
     */
    public static void updateLabel(final BuilderRef ref, @Nullable final String label)
    {
        final Pin pin = PINNED.get(ref);
        if (pin != null && !Objects.equals(pin.label, label))
        {
            pin.label = label;
            save();
        }
    }

    /**
     * Moves a panel (not saved until {@link #save()}, so dragging does not write the file every frame).
     */
    public static void setPosition(final BuilderRef ref, final float x, final float y)
    {
        final Pin pin = PINNED.get(ref);
        if (pin != null)
        {
            pin.x = Math.clamp(x, 0F, 1F);
            pin.y = Math.clamp(y, 0F, 1F);
        }
    }

    public static void resetPosition(final BuilderRef ref)
    {
        final Pin pin = PINNED.get(ref);
        if (pin != null)
        {
            pin.x = null;
            pin.y = null;
        }
    }

    public static void resetAllPositions()
    {
        PINNED.values().forEach(pin -> {
            pin.x = null;
            pin.y = null;
        });
    }

    public static void load()
    {
        PINNED.clear();
        final Path path = file();
        if (!Files.exists(path))
        {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
        {
            final JsonArray array = GSON.fromJson(reader, JsonArray.class);
            if (array == null)
            {
                return;
            }
            for (final JsonElement element : array)
            {
                readPin(element).ifPresent(pin -> PINNED.put(pin.ref, pin));
            }
        }
        catch (final IOException | RuntimeException e)
        {
            MineColoniesVanillaTweaks.LOGGER.warn("Could not read {}", path, e);
        }
    }

    private static Optional<Pin> readPin(final JsonElement element)
    {
        // Version 1.0.0 stored plain strings.
        if (element.isJsonPrimitive())
        {
            return BuilderRef.deserialize(element.getAsString()).map(ref -> new Pin(ref, null));
        }
        if (!element.isJsonObject())
        {
            return Optional.empty();
        }
        final JsonObject object = element.getAsJsonObject();
        if (!object.has("builder"))
        {
            return Optional.empty();
        }
        return BuilderRef.deserialize(object.get("builder").getAsString()).map(ref -> {
            final Pin pin = new Pin(ref, object.has("label") ? object.get("label").getAsString() : null);
            if (object.has("x") && object.has("y"))
            {
                pin.x = Math.clamp(object.get("x").getAsFloat(), 0F, 1F);
                pin.y = Math.clamp(object.get("y").getAsFloat(), 0F, 1F);
            }
            return pin;
        });
    }

    public static void save()
    {
        final JsonArray array = new JsonArray();
        for (final Pin pin : PINNED.values())
        {
            final JsonObject object = new JsonObject();
            object.addProperty("builder", pin.ref.serialize());
            if (pin.label != null)
            {
                object.addProperty("label", pin.label);
            }
            if (pin.hasPosition())
            {
                object.addProperty("x", pin.x);
                object.addProperty("y", pin.y);
            }
            array.add(object);
        }

        final Path path = file();
        try
        {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8))
            {
                GSON.toJson(array, writer);
            }
        }
        catch (final IOException e)
        {
            MineColoniesVanillaTweaks.LOGGER.warn("Could not write {}", path, e);
        }
    }
}
