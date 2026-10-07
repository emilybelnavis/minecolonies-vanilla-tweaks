package fr.mcvanillatweaks.client.buildtool;

import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.client.gui.AbstractBlueprintManipulationWindow;
import com.ldtteam.structurize.client.gui.WindowExtendedBuildTool;
import com.ldtteam.structurize.client.gui.WindowSwitchPack;
import com.ldtteam.structurize.storage.StructurePackMeta;
import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.core.client.gui.WindowSuggestBuildTool;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.config.HudConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.reflect.Field;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Replaces Structurize's build tool windows when they are about to be shown: {@code WindowExtendedBuildTool} (only
 * without a selection callback, since the callback expects Structurize's window: MineColonies' hut suggestion and
 * schematic analyzer keep theirs) becomes {@link BuildToolScreen}, {@code WindowSwitchPack} becomes {@link PackScreen}, and
 * MineColonies' {@code WindowSuggestBuildTool} (a hut block placed by hand) becomes {@link SuggestBuildToolScreen}.
 * Structurize's window was already created, so its constructor has set the preview position as usual.
 */
public final class BuildToolScreens
{
    /** Set after an incompatibility: Structurize's own windows are used for the rest of the session. */
    private static boolean failed;

    private BuildToolScreens()
    {
    }

    private static boolean enabled()
    {
        try
        {
            return HudConfig.VANILLA_BUILD_TOOL.get();
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
        if (failed || !enabled() || !(screen instanceof BOScreen boScreen))
        {
            return;
        }
        final BOWindow window = boScreen.getWindow();
        try
        {
            if (window.getClass() == WindowExtendedBuildTool.class)
            {
                final Object callback = field(WindowExtendedBuildTool.class, "selectionCallback", window);
                // Structurize's window asks for a style first when none is selected.
                if (StructurePacks.getSelectedPack() == null)
                {
                    event.setNewScreen(new PackScreen(null, pack -> true));
                    return;
                }
                final int groundstyle = (Integer) field(AbstractBlueprintManipulationWindow.class, "groundstyle", window);
                @SuppressWarnings("unchecked") final Predicate<Blueprint> available = (Predicate<Blueprint>) field(WindowExtendedBuildTool.class,
                  "availableBlueprintPredicate", window);
                final HolderLookup.Provider provider = (HolderLookup.Provider) field(WindowExtendedBuildTool.class, "provider", window);
                final Text tip = window.findPaneOfTypeByID("tip", Text.class);
                final BuildToolScreen tool = new BuildToolScreen(groundstyle, available, provider, tip != null && tip.isVisible());
                if (callback != null)
                {
                    @SuppressWarnings("unchecked") final java.util.function.BiConsumer<WindowExtendedBuildTool, Blueprint> selection =
                      (java.util.function.BiConsumer<WindowExtendedBuildTool, Blueprint>) callback;
                    tool.selecting(selection, (WindowExtendedBuildTool) window);
                }
                event.setNewScreen(tool);
            }
            else if (window.getClass() == com.minecolonies.core.client.gui.WindowSupplies.class
                       || window.getClass() == com.ldtteam.structurize.client.gui.WindowShapeTool.class)
            {
                // Supply camp / ship placement, shape tool.
                event.setNewScreen(new ManipulatorScreen((AbstractBlueprintManipulationWindow) window));
            }
            else if (window instanceof com.ldtteam.structurize.client.gui.WindowSelectRes select
                       && (window.getClass() == com.ldtteam.structurize.client.gui.WindowSelectRes.class
                             || window.getClass() == com.ldtteam.structurize.client.gui.WindowReplaceBlock.class))
            {
                event.setNewScreen(new fr.mcvanillatweaks.client.hut.SelectResScreen(select));
            }
            else if (window.getClass() == com.ldtteam.structurize.client.gui.WindowScan.class)
            {
                event.setNewScreen(new ScanScreen((com.ldtteam.structurize.client.gui.WindowScan) window));
            }
            else if (window.getClass() == com.ldtteam.structurize.client.gui.WindowTagTool.class)
            {
                event.setNewScreen(new TagToolScreen((com.ldtteam.structurize.client.gui.WindowTagTool) window));
            }
            else if (window.getClass() == com.ldtteam.structurize.client.gui.WindowUndoRedo.class)
            {
                event.setNewScreen(new UndoRedoScreen());
            }
            else if (window.getClass() == WindowSuggestBuildTool.class)
            {
                event.setNewScreen(new SuggestBuildToolScreen((BlockPos) field(WindowSuggestBuildTool.class, "pos", window),
                  (BlockState) field(WindowSuggestBuildTool.class, "building", window), (ItemStack) field(WindowSuggestBuildTool.class, "stack", window)));
            }
            else if (window.getClass() == WindowSwitchPack.class)
            {
                @SuppressWarnings("unchecked") final Supplier<BOWindow> previous = (Supplier<BOWindow>) field(WindowSwitchPack.class, "prevWindow", window);
                @SuppressWarnings("unchecked") final Predicate<StructurePackMeta> filter = (Predicate<StructurePackMeta>) field(WindowSwitchPack.class, "packPredicate",
                  window);
                event.setNewScreen(new PackScreen(previous, filter == null ? pack -> true : filter));
            }
        }
        catch (final LinkageError | RuntimeException | ReflectiveOperationException e)
        {
            report(e);
        }
    }

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
            MineColoniesVanillaTweaks.LOGGER.warn("Vanilla build tool unavailable, falling back to Structurize's windows", e);
        }
    }
}
