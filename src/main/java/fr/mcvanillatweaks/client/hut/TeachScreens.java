package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.client.gui.containers.WindowBrewingstandCrafting;
import com.minecolonies.core.client.gui.containers.WindowCrafting;
import com.minecolonies.core.client.gui.containers.WindowFurnaceCrafting;
import com.minecolonies.core.colony.buildings.moduleviews.CraftingModuleView;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.config.HudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Swaps MineColonies' recipe-teaching container screens (crafting grid, furnace, brewing stand) for the
 * {@link TeachScreen}s on the same menus, finding the building and crafting module like MineColonies' screens. Follows
 * the hut screen's config switch; if anything is incompatible, MineColonies' screens are used for the rest of the
 * session.
 */
public final class TeachScreens
{
    private static boolean failed;

    private TeachScreens()
    {
    }

    private static boolean enabled()
    {
        try
        {
            return HudConfig.VANILLA_HUT_SCREEN.get();
        }
        catch (final IllegalStateException e)
        {
            return true;
        }
    }

    public static void onScreenOpening(final ScreenEvent.Opening event)
    {
        final Screen screen = event.getNewScreen();
        if (failed || !enabled() || screen == null)
        {
            return;
        }
        final Class<?> type = screen.getClass();
        if (type != WindowCrafting.class && type != WindowFurnaceCrafting.class && type != WindowBrewingstandCrafting.class)
        {
            return;
        }
        try
        {
            final LocalPlayer player = Minecraft.getInstance().player;
            if (player == null)
            {
                return;
            }
            final Screen replacement;
            if (screen instanceof WindowCrafting window)
            {
                final IBuildingView building = building(player, window.getMenu().getPos());
                final CraftingModuleView module = module(building, window.getMenu().getModuleId());
                replacement = module == null ? null : new CraftingTeachScreen(window.getMenu(), player.getInventory(), window.getTitle(), building, module);
            }
            else if (screen instanceof WindowFurnaceCrafting window)
            {
                final IBuildingView building = building(player, window.getMenu().getPos());
                final CraftingModuleView module = module(building, window.getMenu().getModuleId());
                replacement = module == null ? null : new FurnaceTeachScreen(window.getMenu(), player.getInventory(), window.getTitle(), building, module);
            }
            else
            {
                final WindowBrewingstandCrafting window = (WindowBrewingstandCrafting) screen;
                final IBuildingView building = building(player, window.getMenu().getPos());
                final CraftingModuleView module = module(building, window.getMenu().getModuleId());
                replacement = module == null ? null : new BrewingTeachScreen(window.getMenu(), player.getInventory(), window.getTitle(), building, module);
            }
            if (replacement != null)
            {
                event.setNewScreen(replacement);
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            failed = true;
            MineColoniesVanillaTweaks.LOGGER.warn("Vanilla recipe teaching screens unavailable, falling back to MineColonies'", e);
        }
    }

    private static IBuildingView building(final LocalPlayer player, final BlockPos pos)
    {
        return IColonyManager.getInstance().getBuildingView(player.level().dimension(), pos);
    }

    @Nullable
    private static CraftingModuleView module(@Nullable final IBuildingView building, final int moduleId)
    {
        if (building instanceof AbstractBuildingView view && view.getModuleView(moduleId) instanceof CraftingModuleView module)
        {
            return module;
        }
        return null;
    }
}
