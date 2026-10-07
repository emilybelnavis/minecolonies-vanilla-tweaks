package fr.mcvanillatweaks.client.citizen;

import com.minecolonies.api.colony.ICitizenDataView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Content of one tab of {@link CitizenScreen}, drawn in the content area of the frame. */
abstract class PageView
{
    protected final CitizenScreen screen;
    protected final Minecraft mc = Minecraft.getInstance();
    protected final Font font = mc.font;
    /** Content area, absolute screen coordinates (updated by {@link #layout}). */
    protected int x;
    protected int y;
    protected int width;
    protected int height;

    PageView(final CitizenScreen screen)
    {
        this.screen = screen;
    }

    protected ICitizenDataView citizen()
    {
        return screen.frame().citizen();
    }

    void layout(final int x, final int y, final int width, final int height)
    {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    void tick()
    {
    }

    /** Draws the page and fills {@code tooltip} with what is under the mouse. */
    abstract void render(GuiGraphics g, int mouseX, int mouseY, Tooltip tooltip);

    boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        return false;
    }

    boolean mouseDragged(final double mouseX, final double mouseY)
    {
        return false;
    }

    void mouseReleased()
    {
    }

    boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        return false;
    }

    /** Section title on the panel, vanilla container label style. */
    protected void heading(final GuiGraphics g, final net.minecraft.network.chat.Component text, final int atY)
    {
        g.drawString(font, text, x + 1, atY, Gui.LABEL, false);
    }
}
