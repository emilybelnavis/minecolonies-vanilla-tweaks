package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.crafting.ModCraftingTypes;
import com.minecolonies.api.crafting.registry.CraftingType;
import com.minecolonies.api.inventory.container.ContainerCraftingFurnace;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.core.colony.buildings.moduleviews.CraftingModuleView;
import com.minecolonies.core.network.messages.server.colony.building.worker.AddRemoveRecipeMessage;
import fr.mcvanillatweaks.client.citizen.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Teaching a smelting recipe ({@code WindowFurnaceCrafting}): input slot 0 at (56, 17) and result slot 1 at (116, 35),
 * like the furnace, without its fuel slot (MineColonies' furnace users bring their own fuel).
 */
final class FurnaceTeachScreen extends TeachScreen<ContainerCraftingFurnace>
{
    private static final ResourceLocation FURNACE = ResourceLocation.withDefaultNamespace("textures/gui/container/furnace.png");
    private static final ResourceLocation LIT = ResourceLocation.withDefaultNamespace("container/furnace/lit_progress");
    private static final ResourceLocation BURN = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");

    FurnaceTeachScreen(final ContainerCraftingFurnace menu, final Inventory inventory, final Component title, final IBuildingView building,
      final CraftingModuleView module)
    {
        super(menu, inventory, title, building, module);
    }

    @Override
    protected CraftingType craftingType()
    {
        return ModCraftingTypes.SMELTING.get();
    }

    @Override
    protected Component stationName()
    {
        return Component.translatable("mcvanillatweaks.teach.station.furnace");
    }

    @Override
    protected ItemStack result()
    {
        return menu.slots.get(1).getItem();
    }

    @Override
    protected List<ItemStack> inputs()
    {
        final ItemStack input = menu.slots.get(0).getItem();
        return input.isEmpty() ? List.of() : List.of(input);
    }

    @Override
    protected void send()
    {
        final ItemStack primaryOutput = menu.slots.get(1).getItem().copy();
        if (!ItemStackUtils.isEmpty(primaryOutput))
        {
            new AddRemoveRecipeMessage(building, List.of(new ItemStorage(menu.slots.get(0).getItem())), 1, primaryOutput, false, Blocks.FURNACE,
              module.getProducer().getRuntimeID()).sendToServer();
        }
    }

    @Override
    protected int firstInventorySlot()
    {
        return 2;
    }

    @Override
    protected int[] resultPos()
    {
        final Slot slot = menu.slots.get(1);
        return new int[] {slot.x, slot.y};
    }

    @Override
    protected void renderStation(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        final Slot input = menu.slots.get(0);
        Gui.slot(g, leftPos + input.x - 1, topPos + input.y - 1);
        // Vanilla flame, arrow and result frame, from the furnace texture; the flame is lit, the arrow full once there is a result.
        g.blit(FURNACE, leftPos + 56, topPos + 36, 56, 36, 14, 14, 256, 256);
        g.blitSprite(LIT, leftPos + 56, topPos + 36, 14, 14);
        g.blit(FURNACE, leftPos + 79, topPos + 34, 79, 34, 24, 17, 256, 256);
        if (!result().isEmpty())
        {
            g.blitSprite(BURN, leftPos + 79, topPos + 34, 24, 16);
        }
        g.blit(FURNACE, leftPos + 111, topPos + 30, 111, 30, 26, 26, 256, 256);
    }

    @Override
    @Nullable
    protected Ghost ghostFor(final ItemStack wanted)
    {
        final Level level = minecraft == null ? null : minecraft.level;
        if (level == null)
        {
            return null;
        }
        for (final RecipeHolder<SmeltingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING))
        {
            final ItemStack result = holder.value().getResultItem(level.registryAccess());
            if (ItemStack.isSameItem(result, wanted) && !holder.value().getIngredients().isEmpty())
            {
                final Map<Integer, List<ItemStack>> slots = new HashMap<>();
                CraftingTeachScreen.put(slots, 0, holder.value().getIngredients().get(0));
                if (!slots.isEmpty())
                {
                    return new Ghost(slots, result.copy());
                }
            }
        }
        return null;
    }
}
