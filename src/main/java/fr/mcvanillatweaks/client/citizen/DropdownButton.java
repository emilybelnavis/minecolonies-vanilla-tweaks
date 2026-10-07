package fr.mcvanillatweaks.client.citizen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Vanilla button with a drop-down arrow on its right; its label stays clear of the arrow. */
public class DropdownButton extends Button
{
    public DropdownButton(final int x, final int y, final int width, final int height, final Component message, final OnPress onPress)
    {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderString(final GuiGraphics g, final Font font, final int colour)
    {
        renderScrollingString(g, font, getMessage(), getX() + 4, getY(), getX() + getWidth() - 14, getY() + getHeight(), colour);
        Gui.dropdownArrow(g, getX() + getWidth() - 12, getY() + (getHeight() - 4) / 2, colour);
    }
}
