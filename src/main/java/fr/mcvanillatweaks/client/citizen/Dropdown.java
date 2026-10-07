package fr.mcvanillatweaks.client.citizen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Drop-down list opened under (or above) a control, like MineColonies' drop-downs, in the dark list style. A screen owns
 * one, draws it after everything else and gives it the input first: while it is open, a click outside only closes it.
 */
public final class Dropdown
{
    /** One choice; {@code swatch} is an ARGB colour square drawn before the text, 0 for none. */
    public record Entry(Component text, int swatch)
    {
        public Entry(final Component text)
        {
            this(text, 0);
        }
    }

    private static final int ROW = 13;
    private static final int MAX_ROWS = 9;
    private static final int BORDER = 0xFF000000;
    private static final int FRAME = 0xFFA0A0A0;

    private List<Entry> entries = List.of();
    private int selected;
    private IntConsumer onPick;
    private boolean open;
    private int x;
    private int y;
    private int width;
    private int rows;
    private int first;

    /**
     * Opens the list against a control.
     *
     * @param selected     index shown as current (-1 for none).
     * @param screenHeight to open above the control when there is no room below.
     */
    public void open(final int anchorX, final int anchorY, final int anchorWidth, final int anchorHeight, final List<Entry> entries, final int selected,
      final IntConsumer onPick, final int screenHeight)
    {
        if (entries.isEmpty())
        {
            return;
        }
        this.entries = List.copyOf(entries);
        this.selected = selected;
        this.onPick = onPick;
        this.rows = Math.min(MAX_ROWS, entries.size());
        this.width = anchorWidth;
        this.x = anchorX;
        final int h = height();
        this.y = anchorY + anchorHeight + h <= screenHeight - 2 ? anchorY + anchorHeight - 1 : Math.max(2, anchorY - h + 1);
        this.first = Math.max(0, Math.min(selected - rows / 2, entries.size() - rows));
        this.open = true;
        Gui.clickSound();
    }

    public boolean isOpen()
    {
        return open;
    }

    public void close()
    {
        open = false;
    }

    private int height()
    {
        return rows * ROW + 4;
    }

    private boolean scrolls()
    {
        return entries.size() > rows;
    }

    public boolean isOver(final double mouseX, final double mouseY)
    {
        return open && Gui.inside(mouseX, mouseY, x, y, width, height());
    }

    private int rowAt(final double mouseX, final double mouseY)
    {
        if (!Gui.inside(mouseX, mouseY, x + 2, y + 2, width - 4, rows * ROW))
        {
            return -1;
        }
        final int index = first + (int) ((mouseY - y - 2) / ROW);
        return index < entries.size() ? index : -1;
    }

    public void render(final GuiGraphics g, final Font font, final int mouseX, final int mouseY)
    {
        if (!open)
        {
            return;
        }
        final int h = height();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(x, y, x + width, y + h, BORDER);
        g.fill(x + 1, y + 1, x + width - 1, y + h - 1, FRAME);
        g.fill(x + 2, y + 2, x + width - 2, y + h - 2, Gui.LIST_BODY);

        final int hovered = rowAt(mouseX, mouseY);
        final int textRight = x + width - (scrolls() ? 8 : 5);
        for (int r = 0; r < rows; r++)
        {
            final int index = first + r;
            final int rowY = y + 2 + r * ROW;
            if (index == hovered)
            {
                g.fill(x + 2, rowY, x + width - 2, rowY + ROW, Gui.ROW_HOVER);
            }
            final Entry entry = entries.get(index);
            int tx = x + 5;
            if (entry.swatch() != 0)
            {
                g.fill(tx, rowY + 2, tx + 9, rowY + 11, BORDER);
                g.fill(tx + 1, rowY + 3, tx + 8, rowY + 10, entry.swatch());
                tx += 13;
            }
            final int colour = index == selected ? Gui.YELLOW : Gui.WHITE;
            g.drawString(font, Gui.fit(font, entry.text(), textRight - tx), tx, rowY + 3, colour, true);
        }

        if (scrolls())
        {
            final int track = rows * ROW - 2;
            final int thumb = Math.max(8, track * rows / entries.size());
            final int offset = (track - thumb) * first / (entries.size() - rows);
            g.fill(x + width - 5, y + 3 + offset, x + width - 3, y + 3 + offset + thumb, FRAME);
        }
        g.pose().popPose();
    }

    /** Always consumes the click while open: picks the row under the mouse, or just closes. */
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (!open)
        {
            return false;
        }
        final int index = button == 0 ? rowAt(mouseX, mouseY) : -1;
        if (index >= 0)
        {
            open = false;
            Gui.clickSound();
            if (index != selected)
            {
                onPick.accept(index);
            }
            return true;
        }
        if (!isOver(mouseX, mouseY))
        {
            open = false;
        }
        return true;
    }

    public boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        if (!open)
        {
            return false;
        }
        if (scrolls())
        {
            first = Math.max(0, Math.min(entries.size() - rows, first - (int) Math.signum(delta)));
        }
        return true;
    }

    /** Escape closes the list instead of the screen. */
    public boolean keyPressed(final int key)
    {
        if (open && key == GLFW.GLFW_KEY_ESCAPE)
        {
            open = false;
            return true;
        }
        return false;
    }
}
