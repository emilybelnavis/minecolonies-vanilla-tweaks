package fr.mcvanillatweaks.client.university;

import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.research.IGlobalResearchTree;
import com.minecolonies.core.client.gui.WindowResearchTree;
import com.minecolonies.core.colony.buildings.moduleviews.UniversityResearchModuleView;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import fr.mcvanillatweaks.client.hut.HutPage;
import fr.mcvanillatweaks.client.hut.HutScreen;
import fr.mcvanillatweaks.client.hut.HutScreens;
import fr.mcvanillatweaks.client.hut.HutTab;
import fr.mcvanillatweaks.config.HudConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;

/**
 * The university's own parts of the vanilla-style screens. Its hut windows (home, research, statistics...) are swapped
 * for the shared {@link HutScreen}, which asks this class for the research page and the research slots of the
 * header; this class swaps MineColonies' research tree windows for {@link ResearchTreeScreen}.
 */
public final class UniversityScreens
{
    /** Set after an incompatibility: MineColonies' own windows are used for the rest of the session. */
    private static boolean failed;
    /** Research tree to use instead of MineColonies' one (the dev preview's sample tree). */
    @Nullable
    static IGlobalResearchTree treeOverride;

    private UniversityScreens()
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
            return HudConfig.VANILLA_UNIVERSITY_SCREEN.get();
        }
        catch (final IllegalStateException e)
        {
            // config not loaded yet
            return true;
        }
    }

    /** A research's icon, as the university screens show it (also used by the build options' upgrade page). */
    public static net.minecraft.world.item.ItemStack researchIcon(final com.minecolonies.api.research.IGlobalResearch research)
    {
        return Research.icon(research);
    }

    /** The global research tree (or the preview's sample). */
    public static IGlobalResearchTree researchTree()
    {
        return tree();
    }

    static IGlobalResearchTree tree()
    {
        return treeOverride != null ? treeOverride : IGlobalResearchTree.getInstance();
    }

    public static void onScreenOpening(final ScreenEvent.Opening event)
    {
        final Screen screen = event.getNewScreen();
        if (!enabled() || !(screen instanceof BOScreen boScreen))
        {
            return;
        }
        final BOWindow window = boScreen.getWindow();
        try
        {
            if (window instanceof WindowResearchTree researchTree)
            {
                final IBuildingView building = field(WindowResearchTree.class, "building", researchTree) instanceof IBuildingView view ? view : null;
                final ResourceLocation branch = field(WindowResearchTree.class, "branch", researchTree) instanceof ResourceLocation id ? id : null;
                if (isUniversity(building) && branch != null)
                {
                    event.setNewScreen(new ResearchTreeScreen(building, branch, tree()));
                }
            }
        }
        catch (final LinkageError | RuntimeException | ReflectiveOperationException e)
        {
            report(e);
        }
    }

    private static boolean isUniversity(@Nullable final IBuildingView building)
    {
        return building != null && building.getColony() != null && building.getBuildingType() == ModBuildings.university.get();
    }

    @Nullable
    private static Object field(final Class<?> owner, final String name, final Object instance) throws ReflectiveOperationException
    {
        final Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(instance);
    }

    static void report(final Throwable e)
    {
        if (!failed)
        {
            failed = true;
            MineColoniesVanillaTweaks.LOGGER.warn("Vanilla research tree unavailable, falling back to MineColonies' windows", e);
        }
    }

    // ------------------------------------------------------------------ parts of the hut screen

    /** The research page of the hut screen. */
    public static HutTab researchTab(final HutScreen screen)
    {
        return new ResearchTab(screen, tree());
    }

    /** "Research: 1 / 2" at the right of the header, for the university only. */
    public static void renderHeaderInfo(final GuiGraphics g, final Font font, final IBuildingView building, final int right, final int y,
      final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        if (!isUniversity(building))
        {
            return;
        }
        final int running = Research.local(building).getResearchInProgress().size();
        final int slots = building.getBuildingLevel();
        final Component research = Component.translatable("mcvanillatweaks.university.slots", running, slots);
        g.drawString(font, research, right - font.width(research), y, running >= slots ? 0xFFAA0000 : Gui.LABEL_SOFT, false);
        if (Gui.inside(mouseX, mouseY, right - font.width(research), y - 1, font.width(research), 10))
        {
            tooltip.set(List.of(research, Component.translatable("mcvanillatweaks.university.slots_hint").withStyle(ChatFormatting.GRAY)));
        }
    }

    /** Back to the university's research page, through MineColonies' window like its tabs. */
    static void openResearch(final IBuildingView building)
    {
        final UniversityResearchModuleView module = building.getModuleViews(UniversityResearchModuleView.class).stream().findFirst().orElse(null);
        HutScreens.open(building, module == null ? HutPage.HOME : HutPage.module(module));
    }

    /** Opens a branch's research tree. */
    static void openTree(final IBuildingView building, final ResourceLocation branch, final IGlobalResearchTree tree)
    {
        Minecraft.getInstance().setScreen(new ResearchTreeScreen(building, branch, tree));
    }
}
