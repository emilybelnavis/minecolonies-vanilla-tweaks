package fr.mcvanillatweaks.client.citizen;

import net.minecraft.client.gui.GuiGraphics;

/**
 * A sunken list area with the creative inventory scrollbar on its right. Rows are drawn by the caller between
 * {@link #begin} and {@link #end}, which clip them to the list.
 */
public final class ScrollArea
{
    private static final int BAR_WIDTH = Gui.SCROLLER_WIDTH + 2;

    private int x;
    private int y;
    private int width;
    private int height;
    private int contentHeight;
    private int step = 12;
    private double scroll;
    private boolean dragging;

    /** @param step pixels scrolled by one wheel notch (one row). */
    public void set(final int x, final int y, final int width, final int height, final int contentHeight, final int step)
    {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.contentHeight = contentHeight;
        this.step = step;
        clamp();
    }

    /** Left of the rows. */
    public int listX()
    {
        return x + 1;
    }

    /** Top of the first row, scroll applied. */
    public int listY()
    {
        return y + 1 - (int) Math.round(scroll);
    }

    public int listWidth()
    {
        return width - BAR_WIDTH - 2;
    }

    public int visibleTop()
    {
        return y + 1;
    }

    public int visibleBottom()
    {
        return y + height - 1;
    }

    private int maxScroll()
    {
        return Math.max(0, contentHeight - (height - 2));
    }

    private void clamp()
    {
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
    }

    public void begin(final GuiGraphics g)
    {
        Gui.inset(g, x, y, width - BAR_WIDTH, height);
        final int max = maxScroll();
        Gui.scrollbar(g, x + width - BAR_WIDTH, y, height, max == 0 ? 0 : (float) (scroll / max), max > 0);
        g.enableScissor(x + 1, y + 1, x + width - BAR_WIDTH - 1, y + height - 1);
    }

    public void end(final GuiGraphics g)
    {
        g.disableScissor();
    }

    /** Whether the mouse is over the visible rows (not the scrollbar). */
    public boolean overRows(final double mouseX, final double mouseY)
    {
        return Gui.inside(mouseX, mouseY, x + 1, y + 1, width - BAR_WIDTH - 2, height - 2);
    }

    /** Highlights the row under the mouse (call between {@link #begin} and {@link #end}, before drawing the row). */
    public boolean hover(final GuiGraphics g, final double mouseX, final double mouseY, final int rowY, final int rowHeight)
    {
        if (!overRows(mouseX, mouseY) || !Gui.inside(mouseX, mouseY, listX(), rowY, listWidth(), rowHeight))
        {
            return false;
        }
        g.fill(listX(), rowY, listX() + listWidth(), rowY + rowHeight, Gui.ROW_HOVER);
        return true;
    }

    public boolean contains(final double mouseX, final double mouseY)
    {
        return Gui.inside(mouseX, mouseY, x, y, width, height);
    }

    public boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        if (!contains(mouseX, mouseY) || maxScroll() == 0)
        {
            return false;
        }
        scroll -= delta * step;
        clamp();
        return true;
    }

    public boolean mouseClicked(final double mouseX, final double mouseY)
    {
        if (maxScroll() > 0 && Gui.inside(mouseX, mouseY, x + width - BAR_WIDTH, y, BAR_WIDTH, height))
        {
            dragging = true;
            drag(mouseY);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(final double mouseY)
    {
        if (dragging)
        {
            drag(mouseY);
            return true;
        }
        return false;
    }

    public void mouseReleased()
    {
        dragging = false;
    }

    private void drag(final double mouseY)
    {
        final double travel = height - 2 - Gui.SCROLLER_HEIGHT;
        final double progress = (mouseY - y - 1 - Gui.SCROLLER_HEIGHT / 2.0D) / travel;
        scroll = Math.max(0, Math.min(1, progress)) * maxScroll();
    }
}
