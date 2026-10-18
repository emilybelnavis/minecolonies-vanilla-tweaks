package fr.mcvanillatweaks.fixes;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.api.items.ModTags;
import com.minecolonies.api.util.FoodUtils;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.tileentities.TileEntityRack;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.Set;

/**
 * Server side fix for the restaurant's cook going hungry (MineColonies 1.1.1403). Nobody serves the cook: he only eats
 * from his inventory and his restaurant's racks, and only what his house level allows. When nothing qualifies,
 * MineColonies' {@code EntityAIEatTask.goToHut} gives up, but his citizen AI keeps choosing EATING (its last state)
 * while he is hungry, so he stops cooking and starves next to his raw food. Called from {@code EntityAIEatTaskMixin}
 * at that point: the cook eats the best cooked food he can reach even if his house wants better (his food happiness
 * still counts it), then goes back to work; with nothing cooked at all, he cooks and MineColonies sends him eating
 * again later.
 */
public final class CookMeals
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** CitizenData's full saturation, where MineColonies' own meals stop too. */
    private static final double FULL = 60.0;

    /** Items eaten in one meal at most (a stack). */
    private static final int MAX_BITES = 64;

    private static boolean failed;

    private CookMeals()
    {
    }

    public static void onNothingToEat(final EntityCitizen citizen)
    {
        if (failed)
        {
            return;
        }
        try
        {
            final ICitizenData data = citizen.getCitizenData();
            if (data == null || !(data.getWorkBuilding() instanceof BuildingCook restaurant))
            {
                return;
            }
            final Set<ItemStorage> menu = restaurant.getModule(BuildingModules.RESTAURANT_MENU).getMenu();
            final InventoryCitizen inventory = citizen.getInventoryCitizen();
            int slot = bestSlot(inventory, data, menu);
            if (slot < 0)
            {
                final ItemStorage stored = bestInRestaurant(restaurant, data, menu);
                if (stored != null)
                {
                    final double value = Math.max(1.0, FoodUtils.getFoodValue(stored.getItemStack(), citizen));
                    final int count = (int) Math.ceil(Math.max(1.0, (FULL - data.getSaturation()) / value));
                    InventoryUtils.transferItemStackIntoNextBestSlotInItemHandler(restaurant, stored, count, inventory);
                    slot = bestSlot(inventory, data, menu);
                }
            }
            if (slot >= 0)
            {
                eat(citizen, data, inventory.getStackInSlot(slot));
            }
            // Back to work either way, instead of MineColonies retrying the same empty meal forever.
            data.setJustAte(true);
        }
        catch (LinkageError | RuntimeException e)
        {
            failed = true;
            LOGGER.warn("Cook meal fix unavailable, MineColonies feeds the cook", e);
        }
    }

    private static void eat(final EntityCitizen citizen, final ICitizenData data, final ItemStack food)
    {
        final ItemStack shown = food.copyWithCount(1);
        citizen.setItemInHand(InteractionHand.MAIN_HAND, shown);
        citizen.swing(InteractionHand.MAIN_HAND);
        citizen.playSound(SoundEvents.GENERIC_EAT, 1.0F, 1.0F);
        for (int bite = 0; bite < MAX_BITES && !food.isEmpty() && data.getSaturation() < FULL; bite++)
        {
            final int before = food.getCount();
            ItemStackUtils.consumeFood(food, citizen, null);
            if (food.getCount() >= before)
            {
                break;
            }
        }
        citizen.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        data.getCitizenFoodHandler().addLastEaten(shown.getItem());
    }

    /** The cook's best food slot (see {@link #score}), -1 when he carries nothing he could eat. */
    private static int bestSlot(final InventoryCitizen inventory, final ICitizenData data, final Set<ItemStorage> menu)
    {
        int best = -1;
        int bestScore = -1;
        for (int i = 0; i < inventory.getSlots(); i++)
        {
            final int score = score(inventory.getStackInSlot(i), data, menu);
            if (score > bestScore)
            {
                best = i;
                bestScore = score;
            }
        }
        return best;
    }

    /** Same, among the restaurant's racks (the hut block included), like MineColonies' {@code checkForFoodInBuilding}. */
    private static ItemStorage bestInRestaurant(final BuildingCook restaurant, final ICitizenData data, final Set<ItemStorage> menu)
    {
        final Level level = restaurant.getColony().getWorld();
        ItemStorage best = null;
        int bestScore = -1;
        for (final BlockPos pos : restaurant.getContainers())
        {
            if (WorldUtil.isBlockLoaded(level, pos) && level.getBlockEntity(pos) instanceof TileEntityRack rack)
            {
                for (final ItemStorage storage : rack.getAllContent().keySet())
                {
                    final int score = score(storage.getItemStack(), data, menu);
                    if (score > bestScore)
                    {
                        best = storage;
                        bestScore = score;
                    }
                }
            }
        }
        return best == null ? null : new ItemStorage(best.getItemStack().copy());
    }

    /**
     * -1 for what the cook can't eat at all (raw, poisonous, MineColonies crops), else: food his house accepts first,
     * then food on the menu, then the most nourishing.
     */
    private static int score(final ItemStack stack, final ICitizenData data, final Set<ItemStorage> menu)
    {
        if (stack.isEmpty() || !FoodUtils.EDIBLE.test(stack) || stack.is(ModTags.poisonous_food) || !FoodUtils.canEatLevel(stack, 0))
        {
            return -1;
        }
        final FoodProperties food = stack.getFoodProperties(null);
        final int nutrition = food == null ? 0 : Math.min(food.nutrition(), 99);
        final boolean housed = FoodUtils.canEat(stack, data.getHomeBuilding(), data.getWorkBuilding());
        final boolean onMenu = menu.contains(new ItemStorage(stack));
        return (housed ? 200 : 0) + (onMenu ? 100 : 0) + nutrition;
    }
}
