package fr.mcvanillatweaks.client.domum;

import com.ldtteam.domumornamentum.container.ArchitectsCutterContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;

/**
 * Dev preview of {@link CutterScreen}: a container screen needs a player, which the title screen has not, so the menu is
 * built on an inventory of a player allocated without its constructor, and the screen is drawn by a plain wrapper
 * screen (a container screen's tick needs {@code Minecraft.player}). A group and a variant are selected, with materials
 * and a result in place.
 */
public final class CutterPreview
{
    private CutterPreview()
    {
    }

    public static Screen screen()
    {
        try
        {
            final Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            final sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
            final Player player = (Player) unsafe.allocateInstance(RemotePlayer.class);
            final Inventory inventory = new Inventory(player);
            final ArchitectsCutterContainer menu = new ArchitectsCutterContainer(0, inventory);
            final CutterScreen cutter = new CutterScreen(menu, inventory, Component.literal("Architect's Cutter"));
            cutter.previewSelect(player, 2, 3);
            // Materials and a result, set without the menu's recipe lookup (it needs a level).
            if (menu.inputInventory instanceof SimpleContainer container)
            {
                container.getItems().set(0, new ItemStack(Items.SPRUCE_PLANKS, 12));
                if (container.getContainerSize() > 1)
                {
                    container.getItems().set(1, new ItemStack(Items.STRIPPED_OAK_LOG, 7));
                }
            }
            if (menu.getCurrentVariant() != null)
            {
                menu.outputInventorySlot.set(menu.getCurrentVariant().copy());
            }
            return new Screen(Component.literal("cutter preview"))
            {
                @Override
                protected void init()
                {
                    cutter.init(minecraft, width, height);
                }

                @Override
                public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
                {
                    cutter.render(g, mouseX, mouseY, partialTick);
                }
            };
        }
        catch (final ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }
}
