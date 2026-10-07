package fr.mcvanillatweaks.client.hut;

import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * A grid of vanilla slots with the creative inventory scrollbar on its right, scrolled by rows, like the creative
 * inventory's item tab. The caller gives the items and asks which one was clicked.
 */
final class ItemGrid
{
    static final int SLOT = 18;
    private static final int BAR = Gui.SCROLLER_WIDTH + 2;

    private List<ItemStack> items = List.of();
    /** Amounts drawn like stack sizes (and given in the tooltip), or null for plain items. */
    @Nullable
    private ToIntFunction<ItemStack> counts;
    /** Lines added under an item's tooltip, after its amount when counted (e.g. what a click does). */
    @Nullable
    private Function<ItemStack, List<Component>> extraLines;
    private int x;
    private int y;
    private int columns = 1;
    private int rows = 1;
    private int firstRow;
    private boolean dragging;

    void setItems(final List<ItemStack> items)
    {
        this.items = items;
        clamp();
    }

    void setCounts(@Nullable final ToIntFunction<ItemStack> counts, @Nullable final Function<ItemStack, List<Component>> extraLines)
    {
        this.counts = counts;
        this.extraLines = extraLines;
    }

    List<ItemStack> items()
    {
        return items;
    }

    /** Places the grid; the scrollbar sits {@code 4} px right of the last column. */
    void set(final int x, final int y, final int columns, final int rows)
    {
        this.x = x;
        this.y = y;
        this.columns = Math.max(1, columns);
        this.rows = Math.max(1, rows);
        clamp();
    }

    /** Width taken by the grid and its scrollbar. */
    static int width(final int columns)
    {
        return columns * SLOT + 4 + BAR;
    }

    static int columnsFor(final int width)
    {
        return Math.max(1, (width - 4 - BAR) / SLOT);
    }

    private int totalRows()
    {
        return (items.size() + columns - 1) / columns;
    }

    private int maxRow()
    {
        return Math.max(0, totalRows() - rows);
    }

    private void clamp()
    {
        firstRow = Math.max(0, Math.min(firstRow, maxRow()));
    }

    void scrollToTop()
    {
        firstRow = 0;
    }

    /** Draws the slots; {@code highlighted} items get a selection frame. */
    void render(final GuiGraphics g, final Font font, final int mouseX, final int mouseY, final Tooltip tooltip, final Predicate<ItemStack> highlighted)
    {
        for (int row = 0; row < rows; row++)
        {
            for (int column = 0; column < columns; column++)
            {
                final int sx = x + column * SLOT;
                final int sy = y + row * SLOT;
                Gui.slot(g, sx, sy);
                final int index = (firstRow + row) * columns + column;
                if (index >= items.size())
                {
                    continue;
                }
                final ItemStack stack = items.get(index);
                if (highlighted.test(stack))
                {
                    g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF55FF55);
                    g.fill(sx + 2, sy + 2, sx + 16, sy + 16, 0xFF8B8B8B);
                }
                g.renderItem(stack, sx + 1, sy + 1);
                final int count = counts == null ? 1 : counts.applyAsInt(stack);
                if (count != 1)
                {
                    drawCount(g, font, stack, sx, sy, count);
                }
                if (Gui.inside(mouseX, mouseY, sx, sy, SLOT, SLOT))
                {
                    Gui.slotHighlight(g, sx, sy);
                    if (counts == null)
                    {
                        if (extraLines != null)
                        {
                            tooltip.set(stack, extraLines.apply(stack));
                        }
                        else
                        {
                            tooltip.set(stack);
                        }
                    }
                    else
                    {
                        final List<Component> lines = new ArrayList<>();
                        lines.add(Component.translatable("mcvanillatweaks.hut.amount", grouped(count)).withStyle(ChatFormatting.GRAY));
                        if (extraLines != null)
                        {
                            lines.addAll(extraLines.apply(stack));
                        }
                        tooltip.set(stack, lines);
                    }
                }
            }
        }
        final int max = maxRow();
        Gui.scrollbar(g, x + columns * SLOT + 4, y, rows * SLOT, max == 0 ? 0 : (float) firstRow / max, max > 0);
    }

    /**
     * Up to 99 the vanilla stack size; above, three digits would run into the next slot, so the amount is shortened and
     * drawn at half size in the slot's corner, like storage mods do.
     */
    private static void drawCount(final GuiGraphics g, final Font font, final ItemStack stack, final int sx, final int sy, final int count)
    {
        if (count < 100)
        {
            g.renderItemDecorations(font, stack, sx + 1, sy + 1, Integer.toString(count));
            return;
        }
        g.renderItemDecorations(font, stack, sx + 1, sy + 1, "");
        final String text = compact(count);
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.pose().scale(0.5F, 0.5F, 1);
        g.drawString(font, text, (sx + 17) * 2 - font.width(text), (sy + 17) * 2 - 7, Gui.WHITE, true);
        g.pose().popPose();
    }

    /** 12 345: thousands grouped with a plain space (the game font has no narrow no-break space). */
    static String grouped(final long count)
    {
        return String.format(Locale.ROOT, "%,d", count).replace(',', ' ');
    }

    /** Amounts above a slot's width are shortened: 999, 1.2k, 12k, 1.5M. */
    static String compact(final int count)
    {
        if (count < 1000)
        {
            return Integer.toString(count);
        }
        if (count < 10_000)
        {
            return String.format(Locale.ROOT, "%.1fk", count / 1000.0D).replace(".0k", "k");
        }
        if (count < 1_000_000)
        {
            return count / 1000 + "k";
        }
        return String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0D).replace(".0M", "M");
    }

    /** Top-left of the slot showing the {@code index}-th item, or null when scrolled out of view. */
    @Nullable
    int[] slotPos(final int index)
    {
        final int row = index / columns - firstRow;
        if (row < 0 || row >= rows)
        {
            return null;
        }
        return new int[] {x + index % columns * SLOT, y + row * SLOT};
    }

    @Nullable
    ItemStack itemAt(final double mouseX, final double mouseY)
    {
        if (!Gui.inside(mouseX, mouseY, x, y, columns * SLOT, rows * SLOT))
        {
            return null;
        }
        final int column = (int) ((mouseX - x) / SLOT);
        final int row = (int) ((mouseY - y) / SLOT);
        final int index = (firstRow + row) * columns + column;
        return index < items.size() ? items.get(index) : null;
    }

    private boolean overBar(final double mouseX, final double mouseY)
    {
        return Gui.inside(mouseX, mouseY, x + columns * SLOT + 4, y, BAR, rows * SLOT);
    }

    boolean mouseClicked(final double mouseX, final double mouseY)
    {
        if (maxRow() > 0 && overBar(mouseX, mouseY))
        {
            dragging = true;
            drag(mouseY);
            return true;
        }
        return false;
    }

    boolean mouseDragged(final double mouseY)
    {
        if (dragging)
        {
            drag(mouseY);
            return true;
        }
        return false;
    }

    void mouseReleased()
    {
        dragging = false;
    }

    boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        if (!Gui.inside(mouseX, mouseY, x, y, columns * SLOT + 4 + BAR, rows * SLOT) || maxRow() == 0)
        {
            return false;
        }
        firstRow -= (int) Math.signum(delta);
        clamp();
        return true;
    }

    private void drag(final double mouseY)
    {
        final double travel = rows * SLOT - 2 - Gui.SCROLLER_HEIGHT;
        final double progress = (mouseY - y - 1 - Gui.SCROLLER_HEIGHT / 2.0D) / travel;
        firstRow = (int) Math.round(Math.max(0, Math.min(1, progress)) * maxRow());
    }
}
