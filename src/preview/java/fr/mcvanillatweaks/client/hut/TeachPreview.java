package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ModCraftingTypes;
import com.minecolonies.api.crafting.registry.CraftingType;
import com.minecolonies.api.inventory.container.ContainerCrafting;
import com.minecolonies.api.inventory.container.ContainerCraftingBrewingstand;
import com.minecolonies.api.inventory.container.ContainerCraftingFurnace;
import com.minecolonies.core.colony.buildings.moduleviews.CraftingModuleView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.decorative.TimberFrameBlock;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.minecolonies.api.util.OptionalPredicate;
import com.minecolonies.core.colony.buildings.moduleviews.DOCraftingModuleView;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dev preview of the {@link TeachScreen}s. Like {@code CutterPreview}, the menus are built on the inventory of a player
 * allocated without its constructor; the crafting menu also reads the player's level, so it gets a client level
 * allocated the same way and marked client side (the menu then skips its server recipe lookup). Requests, the ghost
 * and the brewing result are handed over through the screens' preview fields, there being no colony nor level.
 */
final class TeachPreview
{
    private TeachPreview()
    {
    }

    private static sun.misc.Unsafe unsafe() throws ReflectiveOperationException
    {
        final Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }

    @SuppressWarnings("deprecation")
    private static Player player() throws ReflectiveOperationException
    {
        final sun.misc.Unsafe unsafe = unsafe();
        final Player player = (Player) unsafe.allocateInstance(RemotePlayer.class);
        final ClientLevel level = (ClientLevel) unsafe.allocateInstance(ClientLevel.class);
        unsafe.putBoolean(level, unsafe.objectFieldOffset(Level.class.getDeclaredField("isClientSide")), true);
        final Field levelField = Entity.class.getDeclaredField("level");
        levelField.setAccessible(true);
        levelField.set(player, level);
        return player;
    }

    @SuppressWarnings("unchecked")
    private static CraftingModuleView module(final IBuildingView building) throws ReflectiveOperationException
    {
        final CraftingModuleView module = HutPreview.crafting();
        final Field types = CraftingModuleView.class.getDeclaredField("recipeTypeSet");
        types.setAccessible(true);
        final Set<CraftingType> set = (Set<CraftingType>) types.get(module);
        set.add(ModCraftingTypes.LARGE_CRAFTING.get());
        set.add(ModCraftingTypes.SMELTING.get());
        set.add(ModCraftingTypes.BREWING.get());
        module.setBuildingView(building);
        return module;
    }

    private static Screen wrap(final TeachScreen<?> screen)
    {
        return new Screen(Component.literal("teach preview"))
        {
            @Override
            protected void init()
            {
                screen.init(minecraft, width, height);
            }

            @Override
            public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
            {
                screen.render(g, mouseX, mouseY, partialTick);
            }
        };
    }

    private static void requests(final int selected, final TeachScreen.Ghost ghost)
    {
        TeachScreen.previewRequests = List.of(new ItemStack(Items.BREAD, 12), new ItemStack(Items.TORCH, 64), new ItemStack(Items.CHEST, 2),
          new ItemStack(Items.BAKED_POTATO, 20), new ItemStack(Items.OAK_STAIRS, 32));
        TeachScreen.previewSelected = selected;
        TeachScreen.previewGhost = ghost;
    }

    /** Empty 3x3 grid with the bread request shown as ghost wheat, or a filled grid with two matching recipes. */
    static Screen crafting(final IBuildingView building, final boolean filled)
    {
        try
        {
            final Player player = player();
            final Inventory inventory = new Inventory(player);
            inventory.setItem(0, new ItemStack(Items.OAK_PLANKS, 40));
            inventory.setItem(1, new ItemStack(Items.WHEAT, 17));
            inventory.setItem(9, new ItemStack(Items.STICK, 9));
            final ContainerCrafting menu = new ContainerCrafting(0, inventory, true, BlockPos.ZERO, 0);
            if (filled)
            {
                requests(-1, null);
                for (final int slot : new int[] {0, 1, 3, 4})
                {
                    menu.craftMatrix.setItem(slot, new ItemStack(Items.OAK_PLANKS));
                }
                menu.craftResult.setItem(0, new ItemStack(Items.CRAFTING_TABLE));
                final Field switchable = ContainerCrafting.class.getDeclaredField("switchableSlot");
                switchable.setAccessible(true);
                ((DataSlot) switchable.get(menu)).set(2);
            }
            else
            {
                final List<ItemStack> wheat = List.of(new ItemStack(Items.WHEAT));
                requests(0, new TeachScreen.Ghost(Map.of(4, wheat, 5, wheat, 6, wheat), new ItemStack(Items.BREAD)));
            }
            return wrap(new CraftingTeachScreen(menu, inventory, Component.literal("Crafting"), building, module(building)));
        }
        catch (final ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    static Screen furnace(final IBuildingView building)
    {
        try
        {
            final Inventory inventory = new Inventory(player());
            inventory.setItem(0, new ItemStack(Items.POTATO, 23));
            final ContainerCraftingFurnace menu = new ContainerCraftingFurnace(0, inventory, BlockPos.ZERO, 0);
            menu.slots.get(0).set(new ItemStack(Items.POTATO));
            menu.slots.get(1).set(new ItemStack(Items.BAKED_POTATO));
            requests(-1, null);
            return wrap(new FurnaceTeachScreen(menu, inventory, Component.literal("Furnace"), building, module(building)));
        }
        catch (final ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    /** Domum teaching: two materials in place and the blocks they make, or the picker of the second material. */
    @SuppressWarnings("unchecked")
    static Screen domum(final IBuildingView building, final boolean picker)
    {
        try
        {
            final DOCraftingModuleView module = new DOCraftingModuleView(
              () -> OptionalPredicate.of(stack -> stack.is(ItemTags.PLANKS) || stack.is(ItemTags.LOGS) || stack.is(Items.WHITE_TERRACOTTA)));
            for (final String[] value : new String[][] {{"id", "domum"}})
            {
                final Field field = CraftingModuleView.class.getDeclaredField(value[0]);
                field.setAccessible(true);
                field.set(module, value[1]);
            }
            final Field max = CraftingModuleView.class.getDeclaredField("maxRecipes");
            max.setAccessible(true);
            max.set(module, 10);
            final Field types = CraftingModuleView.class.getDeclaredField("recipeTypeSet");
            types.setAccessible(true);
            ((Set<CraftingType>) types.get(module)).add(ModCraftingTypes.ARCHITECTS_CUTTER.get());
            final Field recipes = CraftingModuleView.class.getDeclaredField("recipes");
            recipes.setAccessible(true);
            ((List<Object>) recipes.get(module)).addAll(HutPreview.crafting().getRecipes());
            module.setBuildingView(building);

            final List<ItemStack> frames = new ArrayList<>();
            for (final TimberFrameBlock frame : ModBlocks.getInstance().getTimberFrames())
            {
                frames.add(textured(frame, Blocks.OAK_PLANKS, Blocks.WHITE_TERRACOTTA));
                if (frames.size() >= 13)
                {
                    break;
                }
            }
            DomumTeachScreen.previewMaterials = List.of(new ItemStack(Items.OAK_PLANKS), picker ? ItemStack.EMPTY : new ItemStack(Items.WHITE_TERRACOTTA));
            DomumTeachScreen.previewResults = picker ? List.of() : frames;
            final List<TimberFrameBlock> all = ModBlocks.getInstance().getTimberFrames();
            final ItemStack wanted = textured(all.get(2), Blocks.DARK_OAK_PLANKS, Blocks.WHITE_TERRACOTTA);
            wanted.setCount(32);
            DomumTeachScreen.previewRequests = List.of(wanted, frames.get(0).copyWithCount(16));
            DomumTeachScreen.previewPicker = picker ? 1 : -1;
            DomumTeachScreen.previewCandidates = List.of(new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.SPRUCE_PLANKS), new ItemStack(Items.BIRCH_PLANKS),
              new ItemStack(Items.DARK_OAK_PLANKS), new ItemStack(Items.OAK_LOG), new ItemStack(Items.STRIPPED_OAK_LOG), new ItemStack(Items.WHITE_TERRACOTTA),
              new ItemStack(Items.STONE), new ItemStack(Items.COBBLESTONE), new ItemStack(Items.BRICKS), new ItemStack(Items.SANDSTONE), new ItemStack(Items.DEEPSLATE),
              new ItemStack(Items.STONE_BRICKS), new ItemStack(Items.ANDESITE), new ItemStack(Items.GRANITE), new ItemStack(Items.DIORITE));
            return new DomumTeachScreen(module);
        }
        catch (final ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static ItemStack textured(final TimberFrameBlock frame, final Block first, final Block second)
    {
        final Map<ResourceLocation, Block> textures = new LinkedHashMap<>();
        final Block[] skins = {first, second};
        int skin = 0;
        for (final IMateriallyTexturedBlockComponent component : frame.getComponents())
        {
            textures.put(component.getId(), skins[Math.min(skin++, skins.length - 1)]);
        }
        final ItemStack stack = new ItemStack(frame);
        new MaterialTextureData(textures).writeToItemStack(stack);
        return stack;
    }

    static Screen brewing(final IBuildingView building)
    {
        try
        {
            final Inventory inventory = new Inventory(player());
            inventory.setItem(0, new ItemStack(Items.SUGAR, 8));
            final ContainerCraftingBrewingstand menu = new ContainerCraftingBrewingstand(0, inventory, BlockPos.ZERO, 0);
            // The menu's handler checks the level's potion brewing; set the stacks on the slots' handler directly.
            final ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
            final Field handlerField = ContainerCraftingBrewingstand.class.getDeclaredField("brewingStandInventory");
            handlerField.setAccessible(true);
            final Object handler = handlerField.get(menu);
            for (final Field field : handler.getClass().getDeclaredFields())
            {
                field.setAccessible(true);
                if (field.getName().equals("potion"))
                {
                    field.set(handler, awkward);
                }
                else if (field.getName().equals("ingredient"))
                {
                    field.set(handler, new ItemStack(Items.SUGAR));
                }
            }
            BrewingTeachScreen.previewResult = PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS).copyWithCount(3);
            requests(-1, null);
            return wrap(new BrewingTeachScreen(menu, inventory, Component.literal("Brewing"), building, module(building)));
        }
        catch (final ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }
}
