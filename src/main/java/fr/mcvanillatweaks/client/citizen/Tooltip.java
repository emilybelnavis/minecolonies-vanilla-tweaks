package fr.mcvanillatweaks.client.citizen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The tooltip to draw on top of everything at the end of the frame: an item or lines of text. */
public final class Tooltip
{
    /** Vanilla tooltips are drawn 12 px right of and above the point they are given, with a 4 px border. */
    private static final int OFFSET = 12;
    private static final int BORDER = 4;

    private ItemStack stack = ItemStack.EMPTY;
    /** Lines added under an item's own tooltip. */
    private List<Component> extra = List.of();
    private List<Component> lines = List.of();
    /** Set: the lines go under the row between these heights (or above it), not at the mouse. */
    private boolean around;
    private int aroundX;
    private int rowTop;
    private int rowBottom;

    public void clear()
    {
        stack = ItemStack.EMPTY;
        extra = List.of();
        lines = List.of();
        around = false;
    }

    public void set(final ItemStack stack)
    {
        clear();
        this.stack = stack == null ? ItemStack.EMPTY : stack;
    }

    /** The item's tooltip followed by {@code extra} lines. */
    public void set(final ItemStack stack, final List<Component> extra)
    {
        set(stack);
        this.extra = extra;
    }

    public void set(final Component line)
    {
        set(List.of(line));
    }

    public void set(final List<Component> lines)
    {
        clear();
        this.lines = lines;
    }

    /** Lines shown under the row (above it when the screen is too short), so they hide nothing on the row itself. */
    public void setAround(final List<Component> lines, final int x, final int top, final int bottom)
    {
        set(lines);
        around = true;
        aroundX = x;
        rowTop = top;
        rowBottom = bottom;
    }

    public boolean isEmpty()
    {
        return stack.isEmpty() && lines.isEmpty();
    }

    public void render(final GuiGraphics g, final Font font, final int mouseX, final int mouseY)
    {
        if (!stack.isEmpty() && !extra.isEmpty())
        {
            final List<Component> all = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
            all.addAll(extra);
            g.renderTooltip(font, all, stack.getTooltipImage(), stack, mouseX, mouseY);
        }
        else if (!stack.isEmpty())
        {
            g.renderTooltip(font, stack, mouseX, mouseY);
        }
        else if (!lines.isEmpty() && around)
        {
            // Same height as vanilla's tooltip: 10 px per line, 2 more after the first.
            final int height = lines.size() * 10 - 2 + (lines.size() > 1 ? 2 : 0);
            int y = rowBottom + 1 + BORDER + OFFSET;
            if (y - OFFSET + height + BORDER > g.guiHeight())
            {
                y = rowTop - 1 - BORDER - height + OFFSET;
            }
            g.renderComponentTooltip(font, lines, aroundX, y);
        }
        else if (!lines.isEmpty())
        {
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }
}
