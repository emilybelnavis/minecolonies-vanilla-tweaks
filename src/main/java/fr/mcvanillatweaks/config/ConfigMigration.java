package fr.mcvanillatweaks.config;

import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Up to 1.5.0 every option sat at the top of the config file; they now live in sections. NeoForge drops the old
 * top-level entries when it corrects the file, so they are read from the raw file before the config is registered,
 * and written into the sections once it has loaded.
 */
public final class ConfigMigration
{
    private static final String FILE = MineColoniesVanillaTweaks.MOD_ID + "-client.toml";

    /** Old top-level values, by key (raw TOML text), until they are applied. */
    private static Map<String, String> pending = Map.of();

    private ConfigMigration()
    {
    }

    /** Reads the old top-level values; call before registering the config. */
    public static void readOldFile()
    {
        final Path path = FMLPaths.CONFIGDIR.get().resolve(FILE);
        if (!Files.isRegularFile(path))
        {
            return;
        }
        try
        {
            final Map<String, String> values = new LinkedHashMap<>();
            for (final String raw : Files.readAllLines(path, StandardCharsets.UTF_8))
            {
                final String line = raw.strip();
                if (line.startsWith("["))
                {
                    // Sections start: everything after belongs to the new layout.
                    break;
                }
                final int equals = line.indexOf('=');
                if (line.isEmpty() || line.startsWith("#") || equals < 0)
                {
                    continue;
                }
                String value = line.substring(equals + 1).strip();
                final int comment = value.indexOf(" #");
                if (comment >= 0)
                {
                    value = value.substring(0, comment).strip();
                }
                values.put(line.substring(0, equals).strip(), value);
            }
            pending = values;
        }
        catch (final IOException | RuntimeException e)
        {
            MineColoniesVanillaTweaks.LOGGER.warn("Could not read the old settings from {}", path, e);
        }
    }

    public static void onConfigLoading(final ModConfigEvent.Loading event)
    {
        if (pending.isEmpty() || event.getConfig().getSpec() != HudConfig.SPEC)
        {
            return;
        }
        final Map<String, ModConfigSpec.ConfigValue<?>> values = new HashMap<>();
        values.put("vanillaCitizenScreen", HudConfig.VANILLA_CITIZEN_SCREEN);
        values.put("vanillaTownHallScreen", HudConfig.VANILLA_TOWN_HALL_SCREEN);
        values.put("vanillaUniversityScreen", HudConfig.VANILLA_UNIVERSITY_SCREEN);
        values.put("vanillaHutScreen", HudConfig.VANILLA_HUT_SCREEN);
        values.put("vanillaBuildTool", HudConfig.VANILLA_BUILD_TOOL);
        values.put("vanillaArchitectsCutter", HudConfig.VANILLA_ARCHITECTS_CUTTER);
        values.put("vanillaDialogs", HudConfig.VANILLA_DIALOGS);
        values.put("deathBeams", HudConfig.DEATH_BEAMS);
        values.put("position", HudConfig.POSITION);
        values.put("offsetX", HudConfig.OFFSET_X);
        values.put("offsetY", HudConfig.OFFSET_Y);
        values.put("scale", HudConfig.SCALE);
        values.put("showCompleted", HudConfig.SHOW_COMPLETED);
        values.put("showIcons", HudConfig.SHOW_ICONS);
        values.put("showPercentage", HudConfig.SHOW_PERCENTAGE);
        values.put("showConstruction", HudConfig.SHOW_CONSTRUCTION);
        values.put("showHeldScroll", HudConfig.SHOW_HELD_SCROLL);
        values.put("maxLines", HudConfig.MAX_LINES);
        values.put("maxPanels", HudConfig.MAX_PANELS);
        values.put("refreshSeconds", HudConfig.REFRESH_SECONDS);

        int moved = 0;
        for (final Map.Entry<String, String> entry : pending.entrySet())
        {
            final ModConfigSpec.ConfigValue<?> value = values.get(entry.getKey());
            if (value != null && apply(value, entry.getValue()))
            {
                moved++;
            }
        }
        pending = Map.of();
        if (moved > 0)
        {
            HudConfig.SPEC.save();
            MineColoniesVanillaTweaks.LOGGER.info("Moved {} settings into the new sections of {}", moved, FILE);
        }
    }

    private static boolean apply(final ModConfigSpec.ConfigValue<?> value, final String raw)
    {
        try
        {
            final String text = raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"") ? raw.substring(1, raw.length() - 1) : raw;
            if (value instanceof ModConfigSpec.BooleanValue bool && (text.equals("true") || text.equals("false")))
            {
                bool.set(Boolean.parseBoolean(text));
            }
            else if (value instanceof ModConfigSpec.IntValue integer)
            {
                integer.set(Integer.parseInt(text));
            }
            else if (value instanceof ModConfigSpec.DoubleValue number)
            {
                number.set(Double.parseDouble(text));
            }
            else if (value == HudConfig.POSITION)
            {
                HudConfig.POSITION.set(HudConfig.Anchor.valueOf(text.toUpperCase(Locale.ROOT)));
            }
            else
            {
                return false;
            }
            return true;
        }
        catch (final RuntimeException e)
        {
            // An invalid old value keeps the default.
            return false;
        }
    }
}
