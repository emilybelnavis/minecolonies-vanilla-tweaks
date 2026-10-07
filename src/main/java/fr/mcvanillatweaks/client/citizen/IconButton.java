package fr.mcvanillatweaks.client.citizen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Vanilla button with an item icon before its label, the pair centred on the button. */
public class IconButton extends Button
{
    private ItemStack icon;

    public IconButton(final int x, final int y, final int width, final int height, final ItemStack icon, final Component message, final OnPress onPress)
    {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.icon = icon;
    }

    public void setIcon(final ItemStack icon)
    {
        this.icon = icon;
    }

    @Override
    public void renderString(final GuiGraphics g, final Font font, final int colour)
    {
        if (getMessage().getString().isEmpty())
        {
            g.renderItem(icon, getX() + (getWidth() - 16) / 2, getY() + (getHeight() - 16) / 2);
            return;
        }
        final int room = getWidth() - 8 - 18;
        final int textWidth = Math.min(font.width(getMessage()), room);
        final int startX = getX() + (getWidth() - 18 - textWidth) / 2;
        g.renderItem(icon, startX, getY() + (getHeight() - 16) / 2);
        renderScrollingString(g, font, getMessage(), startX + 18, getY(), startX + 18 + textWidth, getY() + getHeight(), colour);
    }
}
