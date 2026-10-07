package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.crafting.ModCraftingTypes;
import com.minecolonies.api.crafting.registry.CraftingType;
import com.minecolonies.api.inventory.container.ContainerCrafting;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.core.colony.buildings.moduleviews.CraftingModuleView;
import com.minecolonies.core.network.messages.server.SwitchRecipeCraftingTeachingMessage;
import com.minecolonies.core.network.messages.server.colony.building.worker.AddRemoveRecipeMessage;
import fr.mcvanillatweaks.client.citizen.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Teaching a crafting recipe ({@code WindowCrafting}): a 2x2 or 3x3 grid (slots 1.., at (30, 17) like the crafting
 * table), result slot 0 at (124, 35). When several recipes match the grid, a button cycles them with MineColonies'
 * {@code SwitchRecipeCraftingTeachingMessage}; "teach" sends every grid slot, empty ones included, like MineColonies.
 */
final class CraftingTeachScreen extends TeachScreen<ContainerCrafting>
{
    private static final ResourceLocation CRAFTING_TABLE = ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");

    private Button switchButton;

    CraftingTeachScreen(final ContainerCrafting menu, final Inventory inventory, final Component title, final IBuildingView building, final CraftingModuleView module)
    {
        super(menu, inventory, title, building, module);
    }

    private int size()
    {
        return menu.isComplete() ? 3 : 2;
    }

    @Override
    protected CraftingType craftingType()
    {
        // MineColonies checks the small grid even for the large one.
        return ModCraftingTypes.SMALL_CRAFTING.get();
    }

    @Override
    protected Component stationName()
    {
        return Component.translatable(menu.isComplete() ? "mcvanillatweaks.teach.station.crafting_large" : "mcvanillatweaks.teach.station.crafting_small");
    }

    @Override
    protected ItemStack result()
    {
        return menu.craftResult.getItem(0);
    }

    @Override
    protected List<ItemStack> inputs()
    {
        final List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < size() * size(); i++)
        {
            if (!menu.craftMatrix.getItem(i).isEmpty())
            {
                list.add(menu.craftMatrix.getItem(i));
            }
        }
        return list;
    }

    @Override
    protected void send()
    {
        final List<ItemStorage> input = new LinkedList<>();
        for (int i = 0; i < size() * size(); i++)
        {
            final ItemStack copy = menu.craftMatrix.getItem(i).copy();
            ItemStackUtils.setSize(copy, 1);
            input.add(new ItemStorage(copy));
        }
        final ItemStack primaryOutput = menu.craftResult.getItem(0).copy();
        if (!ItemStackUtils.isEmpty(primaryOutput))
        {
            new AddRemoveRecipeMessage(building, input, size(), primaryOutput, menu.getRemainingItems(), false, module.getProducer().getRuntimeID()).sendToServer();
        }
    }

    @Override
    protected void initStation()
    {
        switchButton = addRenderableWidget(Button.builder(Component.literal("»"), b -> new SwitchRecipeCraftingTeachingMessage().sendToServer())
                                             .bounds(leftPos + 149, topPos + 36, 22, 14).build());
        updateSwitch();
    }

    @Override
    protected void containerTick()
    {
        super.containerTick();
        updateSwitch();
    }

    /** Shown when several recipes match the grid, labelled with which one is shown ("2/3") when the menu's data allows. */
    private void updateSwitch()
    {
        switchButton.visible = menu.canSwitchRecipes();
        if (!switchButton.visible)
        {
            return;
        }
        final int count = dataSlot("switchableSlot");
        final int index = dataSlot("recipeIndexSlot");
        if (count > 1 && index >= 0)
        {
            final int shown = index % count + 1;
            switchButton.setMessage(Component.literal(shown + "/" + count));
            switchButton.setTooltip(Tooltip.create(Component.translatable("mcvanillatweaks.teach.switch", shown, count)));
        }
        else
        {
            switchButton.setTooltip(Tooltip.create(Component.translatable("mcvanillatweaks.teach.switch_any")));
        }
    }

    private int dataSlot(final String name)
    {
        try
        {
            final Field field = ContainerCrafting.class.getDeclaredField(name);
            field.setAccessible(true);
            return ((DataSlot) field.get(menu)).get();
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            return -1;
        }
    }

    @Override
    protected int firstInventorySlot()
    {
        return 1 + size() * size();
    }

    @Override
    protected int[] resultPos()
    {
        final Slot slot = menu.slots.get(0);
        return new int[] {slot.x, slot.y};
    }

    @Override
    protected void renderStation(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        for (int i = 1; i < firstInventorySlot(); i++)
        {
            final Slot slot = menu.slots.get(i);
            Gui.slot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
        // Vanilla arrow and result frame, from the crafting table texture.
        final Slot output = menu.slots.get(0);
        g.blit(CRAFTING_TABLE, leftPos + 90, topPos + 35, 90, 35, 22, 15, 256, 256);
        g.blit(CRAFTING_TABLE, leftPos + output.x - 5, topPos + output.y - 5, 119, 30, 26, 26, 256, 256);
    }

    /** The first crafting recipe making the item that fits this grid, placed like the vanilla recipe book places it. */
    @Override
    @Nullable
    protected Ghost ghostFor(final ItemStack wanted)
    {
        final Level level = minecraft == null ? null : minecraft.level;
        if (level == null)
        {
            return null;
        }
        final int size = size();
        for (final RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING))
        {
            final CraftingRecipe recipe = holder.value();
            if (recipe.isSpecial() || !recipe.canCraftInDimensions(size, size))
            {
                continue;
            }
            final ItemStack result = recipe.getResultItem(level.registryAccess());
            if (!ItemStack.isSameItem(result, wanted))
            {
                continue;
            }
            final Map<Integer, List<ItemStack>> slots = new HashMap<>();
            final List<Ingredient> ingredients = recipe.getIngredients();
            if (recipe instanceof ShapedRecipe shaped)
            {
                for (int row = 0; row < shaped.getHeight(); row++)
                {
                    for (int column = 0; column < shaped.getWidth(); column++)
                    {
                        put(slots, 1 + row * size + column, ingredients.get(row * shaped.getWidth() + column));
                    }
                }
            }
            else
            {
                for (int i = 0; i < ingredients.size() && i < size * size; i++)
                {
                    put(slots, 1 + i, ingredients.get(i));
                }
            }
            if (!slots.isEmpty())
            {
                return new Ghost(slots, result.copy());
            }
        }
        return null;
    }

    static void put(final Map<Integer, List<ItemStack>> slots, final int slot, final Ingredient ingredient)
    {
        if (ingredient.isEmpty())
        {
            return;
        }
        final List<ItemStack> options = Arrays.stream(ingredient.getItems()).filter(stack -> !stack.isEmpty()).toList();
        if (!options.isEmpty())
        {
            slots.put(slot, options);
        }
    }
}
