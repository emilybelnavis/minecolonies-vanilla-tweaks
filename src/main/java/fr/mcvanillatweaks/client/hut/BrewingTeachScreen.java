package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.crafting.ModCraftingTypes;
import com.minecolonies.api.crafting.registry.CraftingType;
import com.minecolonies.api.inventory.container.ContainerCraftingBrewingstand;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.core.colony.buildings.moduleviews.CraftingModuleView;
import com.minecolonies.core.network.messages.server.colony.building.worker.AddRemoveRecipeMessage;
import fr.mcvanillatweaks.client.citizen.Gui;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Teaching a brewing recipe ({@code WindowBrewingstandCrafting}): bottle slots 0-2 (one shared bottle) and ingredient
 * slot 3, like the brewing stand without its fuel. The stand has no result slot: what the bottle and the ingredient
 * brew is shown here (x3, like MineColonies' recipe), computed like MineColonies with the level's potion brewing.
 */
final class BrewingTeachScreen extends TeachScreen<ContainerCraftingBrewingstand>
{
    private static final ResourceLocation BREWING_STAND = ResourceLocation.withDefaultNamespace("textures/gui/container/brewing_stand.png");
    private static final ResourceLocation CRAFTING_TABLE = ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");
    private static final int RESULT_X = 151;
    private static final int RESULT_Y = 35;

    /** Dev preview: the result to show, there being no level to brew with. */
    @Nullable
    static ItemStack previewResult;

    BrewingTeachScreen(final ContainerCraftingBrewingstand menu, final Inventory inventory, final Component title, final IBuildingView building,
      final CraftingModuleView module)
    {
        super(menu, inventory, title, building, module);
    }

    @Override
    protected CraftingType craftingType()
    {
        return ModCraftingTypes.BREWING.get();
    }

    @Override
    protected Component stationName()
    {
        return Component.translatable("mcvanillatweaks.teach.station.brewing");
    }

    private ItemStack potion()
    {
        return menu.getSlot(0).getItem();
    }

    private ItemStack ingredient()
    {
        return menu.getSlot(3).getItem();
    }

    @Override
    protected ItemStack result()
    {
        if (previewResult != null)
        {
            return previewResult;
        }
        final Level level = minecraft == null ? null : minecraft.level;
        final ItemStack potion = potion();
        final ItemStack ingredient = ingredient();
        if (level == null || potion.isEmpty() || ingredient.isEmpty())
        {
            return ItemStack.EMPTY;
        }
        final ItemStack mixed = level.potionBrewing().mix(ingredient, potion);
        return ItemStackUtils.isEmpty(mixed) || mixed == potion ? ItemStack.EMPTY : mixed.copyWithCount(3);
    }

    @Override
    protected List<ItemStack> inputs()
    {
        final List<ItemStack> list = new ArrayList<>();
        if (!potion().isEmpty())
        {
            list.add(potion());
        }
        if (!ingredient().isEmpty())
        {
            list.add(ingredient());
        }
        return list;
    }

    @Override
    protected void send()
    {
        final ItemStack primaryOutput = result();
        if (!primaryOutput.isEmpty())
        {
            final List<ItemStorage> input = new ArrayList<>();
            input.add(new ItemStorage(potion(), 3, false));
            input.add(new ItemStorage(ingredient()));
            new AddRemoveRecipeMessage(building, input, 1, primaryOutput.copyWithCount(3), false, Blocks.BREWING_STAND, module.getProducer().getRuntimeID())
              .sendToServer();
        }
    }

    @Override
    protected int firstInventorySlot()
    {
        return 4;
    }

    @Override
    protected int[] resultPos()
    {
        return new int[] {RESULT_X, RESULT_Y};
    }

    @Override
    protected void renderStation(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        // The brewing stand's bottles, ingredient slot, tube and bubbles, without its fuel slot.
        g.blit(BREWING_STAND, leftPos + 54, topPos + 14, 54, 14, 68, 64, 256, 256);
        g.blit(CRAFTING_TABLE, leftPos + RESULT_X - 30, topPos + RESULT_Y, 90, 35, 22, 15, 256, 256);
        g.blit(CRAFTING_TABLE, leftPos + RESULT_X - 5, topPos + RESULT_Y - 5, 119, 30, 26, 26, 256, 256);
        final ItemStack result = result();
        if (!result.isEmpty())
        {
            g.renderItem(result, leftPos + RESULT_X, topPos + RESULT_Y);
            g.renderItemDecorations(font, result, leftPos + RESULT_X, topPos + RESULT_Y);
            if (Gui.inside(mouseX, mouseY, leftPos + RESULT_X - 1, topPos + RESULT_Y - 1, 18, 18))
            {
                tooltip().set(result);
            }
        }
    }

    /**
     * The mix brewing the requested potion: the bottle it is brewed from in the bottle slots, its ingredient in the
     * ingredient slot. Vanilla keeps its mixes private, so they are read by reflection (no ghost otherwise).
     */
    @Override
    @Nullable
    protected Ghost ghostFor(final ItemStack wanted)
    {
        if (!(wanted.getItem() instanceof PotionItem))
        {
            return null;
        }
        final Map<Integer, List<ItemStack>> slots = new HashMap<>();
        final Level level = minecraft == null ? null : minecraft.level;
        final Optional<Holder<Potion>> target = Optional.ofNullable(wanted.get(DataComponents.POTION_CONTENTS)).flatMap(PotionContents::potion);
        if (level != null && target.isPresent())
        {
            try
            {
                final Field field = PotionBrewing.class.getDeclaredField("potionMixes");
                field.setAccessible(true);
                for (final Object mix : (List<?>) field.get(level.potionBrewing()))
                {
                    final Method to = mix.getClass().getDeclaredMethod("to");
                    to.setAccessible(true);
                    if (!target.get().equals(to.invoke(mix)))
                    {
                        continue;
                    }
                    final Method from = mix.getClass().getDeclaredMethod("from");
                    final Method ingredient = mix.getClass().getDeclaredMethod("ingredient");
                    from.setAccessible(true);
                    ingredient.setAccessible(true);
                    @SuppressWarnings("unchecked") final Holder<Potion> base = (Holder<Potion>) from.invoke(mix);
                    final ItemStack bottle = PotionContents.createItemStack(wanted.getItem(), base);
                    for (int i = 0; i < 3; i++)
                    {
                        slots.put(i, List.of(bottle));
                    }
                    CraftingTeachScreen.put(slots, 3, (Ingredient) ingredient.invoke(mix));
                    break;
                }
            }
            catch (final ReflectiveOperationException | RuntimeException e)
            {
                slots.clear();
            }
        }
        return new Ghost(slots, wanted.copyWithCount(3));
    }
}
