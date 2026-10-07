package fr.mcvanillatweaks.client.citizen;

import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.core.client.gui.WindowInteraction;
import com.minecolonies.core.client.gui.citizen.AbstractWindowCitizen;
import com.minecolonies.core.client.gui.citizen.FamilyWindowCitizen;
import com.minecolonies.core.client.gui.citizen.HappinessWindowCitizen;
import com.minecolonies.core.client.gui.citizen.JobWindowCitizen;
import com.minecolonies.core.client.gui.citizen.MainWindowCitizen;
import com.minecolonies.core.client.gui.citizen.RequestWindowCitizen;
import com.minecolonies.core.client.gui.containers.WindowCitizenInventory;
import com.minecolonies.core.debug.gui.DebugWindowCitizen;
import com.minecolonies.core.network.messages.server.ClickGuiButtonTriggerMessage;
import com.minecolonies.core.network.messages.server.colony.OpenInventoryMessage;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.config.HudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

/**
 * Replaces MineColonies' citizen windows with the vanilla-style citizen screen.
 * <p>
 * MineColonies keeps opening its own windows exactly as before (right click on a citizen, the
 * buttons of our tabs...). When one of them is about to be shown ({@link ScreenEvent.Opening}), it is swapped for the
 * matching page of {@link CitizenScreen}; the citizen inventory menu is shown by {@link CitizenInventoryScreen}. The
 * MineColonies window object is kept as the source of the page, so everything MineColonies does when creating it (GUI
 * advancement triggers, request tree module) still happens. A citizen's dialogue ({@code WindowInteraction}) becomes
 * {@link InteractionScreen}. If anything is incompatible, the original window is shown.
 */
public final class CitizenScreens
{
    /** Set after an incompatibility: MineColonies' own windows are used for the rest of the session. */
    private static boolean failed;

    /** Page asked by one of our tabs when two pages share the same MineColonies window. */
    @Nullable
    private static CitizenPage pendingPage;
    private static int pendingCitizen = Integer.MIN_VALUE;

    private CitizenScreens()
    {
    }

    public static boolean enabled()
    {
        if (failed)
        {
            return false;
        }
        try
        {
            return HudConfig.VANILLA_CITIZEN_SCREEN.get();
        }
        catch (final IllegalStateException e)
        {
            // config not loaded yet
            return true;
        }
    }

    public static void onScreenOpening(final ScreenEvent.Opening event)
    {
        final Screen screen = event.getNewScreen();
        if (!enabled() || screen instanceof CitizenScreen || screen instanceof CitizenInventoryScreen)
        {
            return;
        }
        try
        {
            if (screen instanceof BOScreen boScreen && boScreen.getWindow().getClass() == WindowInteraction.class)
            {
                // A citizen's dialogue, opened by right clicking them.
                final Field field = WindowInteraction.class.getDeclaredField("citizen");
                field.setAccessible(true);
                if (field.get(boScreen.getWindow()) instanceof ICitizenDataView citizen && citizen.getColony() != null)
                {
                    if (InteractionScreen.hasSomethingToSay(citizen, Minecraft.getInstance().level))
                    {
                        event.setNewScreen(new InteractionScreen(citizen, boScreen.getWindow()));
                    }
                    else
                    {
                        // MineColonies' window closes at once and opens its parent, the citizen's page.
                        final MainWindowCitizen main = new MainWindowCitizen(citizen);
                        LastCitizen.remember(citizen);
                        event.setNewScreen(new CitizenScreen(citizen, CitizenPage.OVERVIEW, main));
                    }
                }
            }
            else if (screen instanceof BOScreen boScreen && boScreen.getWindow() instanceof AbstractWindowCitizen window)
            {
                final ICitizenDataView citizen = citizenOf(window);
                if (citizen != null && citizen.getColony() != null)
                {
                    LastCitizen.remember(citizen);
                    event.setNewScreen(new CitizenScreen(citizen, pageOf(window, citizen), window));
                }
            }
            else if (screen instanceof WindowCitizenInventory inventory && inventory.getCitizenData() instanceof ICitizenDataView citizen)
            {
                LastCitizen.remember(citizen);
                event.setNewScreen(new CitizenInventoryScreen(inventory, citizen));
            }
        }
        catch (final LinkageError | RuntimeException | ReflectiveOperationException e)
        {
            report(e);
        }
    }

    static void report(final Throwable e)
    {
        if (!failed)
        {
            failed = true;
            MineColoniesVanillaTweaks.LOGGER.warn("Vanilla citizen screen unavailable, falling back to MineColonies' windows", e);
        }
    }

    @Nullable
    private static ICitizenDataView citizenOf(final AbstractWindowCitizen window)
    {
        return LastCitizen.citizenOf(window);
    }

    private static CitizenPage pageOf(final AbstractWindowCitizen window, final ICitizenDataView citizen)
    {
        final CitizenPage pending = pendingPage;
        final boolean samePerson = pendingCitizen == citizen.getId();
        pendingPage = null;
        pendingCitizen = Integer.MIN_VALUE;
        if (pending != null && samePerson)
        {
            return pending;
        }
        if (window instanceof JobWindowCitizen)
        {
            return CitizenPage.SKILLS;
        }
        if (window instanceof RequestWindowCitizen)
        {
            return CitizenPage.REQUESTS;
        }
        if (window instanceof HappinessWindowCitizen)
        {
            return CitizenPage.HAPPINESS;
        }
        if (window instanceof FamilyWindowCitizen)
        {
            return CitizenPage.FAMILY;
        }
        return CitizenPage.OVERVIEW;
    }

    /**
     * Opens a page the way MineColonies' own tab buttons do (the window is then swapped for our screen).
     *
     * @param sourceWindow the MineColonies window currently shown, for the click trigger; null from the inventory.
     */
    static void open(final ICitizenDataView citizen, final CitizenPage page, @Nullable final BOWindow sourceWindow)
    {
        if (sourceWindow != null)
        {
            trigger(page.buttonId(), sourceWindow.getXmlResourceLocation());
        }
        switch (page)
        {
            case OVERVIEW -> openAs(citizen, CitizenPage.OVERVIEW, () -> new MainWindowCitizen(citizen).open());
            case SKILLS ->
            {
                if (CitizenInfo.hasJobPage(citizen))
                {
                    new JobWindowCitizen(citizen).open();
                }
                else
                {
                    openAs(citizen, CitizenPage.SKILLS, () -> new MainWindowCitizen(citizen).open());
                }
            }
            case INVENTORY -> new OpenInventoryMessage(citizen.getColony(), citizen.getName(), citizen.getEntityId()).sendToServer();
            case REQUESTS -> new RequestWindowCitizen(citizen).open();
            case HAPPINESS -> new HappinessWindowCitizen(citizen).open();
            case FAMILY -> new FamilyWindowCitizen(citizen).open();
            case DEBUG -> new DebugWindowCitizen(citizen).open();
        }
    }

    /** Opens the given page of another citizen (family members). */
    static void openOther(final ICitizenDataView citizen, final CitizenPage page)
    {
        switch (page)
        {
            case FAMILY -> new FamilyWindowCitizen(citizen).open();
            default -> openAs(citizen, CitizenPage.OVERVIEW, () -> new MainWindowCitizen(citizen).open());
        }
    }

    private static void openAs(final ICitizenDataView citizen, final CitizenPage page, final Runnable opener)
    {
        pendingPage = page;
        pendingCitizen = citizen.getId();
        opener.run();
    }

    /** Same message MineColonies sends when one of its buttons is clicked (used by its GUI advancements). */
    static void trigger(final String buttonId, @Nullable final ResourceLocation window)
    {
        if (window != null)
        {
            new ClickGuiButtonTriggerMessage(buttonId, window).sendToServer();
        }
    }
}
