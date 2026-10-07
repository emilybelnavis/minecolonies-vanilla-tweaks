package fr.mcvanillatweaks.client.dialog;

import fr.mcvanillatweaks.client.citizen.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * A vanilla chest look for MineColonies' chest-like container screens (hut inventory {@code WindowBuildingInventory},
 * grave {@code WindowGrave}, rack {@code WindowRack}), which draw MineColonies' own textures. The menu and its slot
 * positions come from the server, so the frame is sized from the slots (whatever the number of rows or columns) and a
 * vanilla slot is drawn under each one; the labels sit where vanilla puts them.
 */
public final class ChestLikeScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T>
{
    private static final int MARGIN = 7;

    private int playerTop = -1;

    public ChestLikeScreen(final T menu, final Inventory inventory, final Component title)
    {
        super(menu, inventory, title);
        int maxX = 0;
        int maxY = 0;
        for (final Slot slot : menu.slots)
        {
            maxX = Math.max(maxX, slot.x + 16);
            maxY = Math.max(maxY, slot.y + 16);
            if (slot.container instanceof Inventory && (playerTop < 0 || slot.y < playerTop))
            {
                playerTop = slot.y;
            }
        }
        imageWidth = Math.max(176, maxX + 1 + MARGIN);
        imageHeight = maxY + 1 + MARGIN;
        titleLabelY = 6;
        inventoryLabelY = playerTop < 0 ? imageHeight : playerTop - 12;
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY)
    {
        Gui.panel(g, leftPos, topPos, imageWidth, imageHeight);
        for (final Slot slot : menu.slots)
        {
            Gui.slot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        g.drawString(font, title, titleLabelX, titleLabelY, Gui.LABEL, false);
        if (playerTop >= 0)
        {
            g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Gui.LABEL, false);
        }
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
