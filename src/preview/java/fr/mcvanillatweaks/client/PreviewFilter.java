package fr.mcvanillatweaks.client;

import java.util.Arrays;
import java.util.List;

/**
 * Which captures {@code runPreview} takes: all of them, or only those whose file name starts with one of the
 * comma-separated prefixes of {@code -Dmcvanillatweaks.preview.only} ({@code ./gradlew runPreview -Ponly=hut_field,townhall_}).
 * Families with nothing wanted are skipped as a whole, so a filtered run only costs the client's start-up.
 */
public final class PreviewFilter
{
    private static final List<String> ONLY = Arrays.stream(System.getProperty("mcvanillatweaks.preview.only", "").split(","))
                                               .map(String::trim).filter(s -> !s.isEmpty()).toList();

    private PreviewFilter()
    {
    }

    /** Whether the capture saved as {@code name} (without ".png", e.g. "hut_field") is wanted. */
    public static boolean wants(final String name)
    {
        return ONLY.isEmpty() || ONLY.stream().anyMatch(name::startsWith);
    }

    /** Whether any capture of a family ("citizen_", "townhall_", "university_", "hut_") is wanted. */
    public static boolean wantsFamily(final String prefix)
    {
        return ONLY.isEmpty() || ONLY.stream().anyMatch(only -> only.startsWith(prefix) || prefix.startsWith(only));
    }
}
