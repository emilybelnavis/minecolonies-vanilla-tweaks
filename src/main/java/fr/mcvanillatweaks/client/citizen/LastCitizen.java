package fr.mcvanillatweaks.client.citizen;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.core.client.gui.citizen.AbstractWindowCitizen;
import com.minecolonies.core.client.gui.citizen.MainWindowCitizen;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

/**
 * The citizen of MineColonies' citizen windows: read from the window, and remembered for the windows that do not
 * expose it (the citizen inventory).
 */
public final class LastCitizen
{
    /** Last citizen shown, used when a page does not expose its citizen. */
    @Nullable
    private static ICitizenDataView lastCitizen;
    @Nullable
    private static Field citizenField;
    private static boolean citizenFieldResolved;

    private LastCitizen()
    {
    }

    @Nullable
    public static ICitizenDataView get()
    {
        return lastCitizen;
    }

    public static void remember(@Nullable final ICitizenDataView citizen)
    {
        if (citizen != null)
        {
            lastCitizen = citizen;
        }
    }

    /** The window's citizen (its private {@code citizen} field), or the last one remembered. */
    @Nullable
    public static ICitizenDataView citizenOf(final AbstractWindowCitizen window)
    {
        if (window instanceof MainWindowCitizen main)
        {
            return main.getCitizen();
        }
        if (!citizenFieldResolved)
        {
            citizenFieldResolved = true;
            try
            {
                final Field field = AbstractWindowCitizen.class.getDeclaredField("citizen");
                field.setAccessible(true);
                citizenField = field;
            }
            catch (final ReflectiveOperationException | RuntimeException e)
            {
                citizenField = null;
            }
        }
        if (citizenField != null)
        {
            try
            {
                return (ICitizenDataView) citizenField.get(window);
            }
            catch (final ReflectiveOperationException | RuntimeException e)
            {
                citizenField = null;
            }
        }
        return lastCitizen;
    }
}
