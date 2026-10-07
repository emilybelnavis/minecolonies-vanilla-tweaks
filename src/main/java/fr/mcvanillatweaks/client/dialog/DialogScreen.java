package fr.mcvanillatweaks.client.dialog;

import fr.mcvanillatweaks.client.citizen.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A small vanilla-style dialog: icon and title, wrapped paragraphs (optionally with an item icon on their left), an
 * optional text field between two paragraphs and buttons (one row, or one per row when their labels don't fit). Used
 * for MineColonies' one-shot windows (colony founding, reactivation, confirmations...); a dialog with several steps
 * calls {@link #show} with the next {@link Spec}.
 * <pre>
 * [icon] Title
 * Paragraph text, wrapped...
 * [field                  ]
 * [book] Paragraph with an icon
 * [Cancel]        [Confirm]
 * </pre>
 */
public final class DialogScreen extends Screen
{
    private static final int PADDING = 10;
    private static final int LINE = 10;
    private static final int PARAGRAPH_GAP = 6;
    private static final int ICON_SPACE = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;
    private static final int HEADER = 28;

    /** One paragraph; {@code icon} may be empty. {@code soft} draws it in the secondary label colour. */
    public record Line(ItemStack icon, Component text, boolean soft)
    {
        public static Line of(final Component text)
        {
            return new Line(ItemStack.EMPTY, text, false);
        }

        public static Line soft(final Component text)
        {
            return new Line(ItemStack.EMPTY, text, true);
        }

        public static Line icon(final ItemStack icon, final Component text)
        {
            return new Line(icon, text, false);
        }
    }

    /** A text field drawn after the first {@code afterLines} paragraphs. */
    public record Input(String initial, int maxLength, int afterLines)
    {
    }

    /** A button; {@code run} gets the screen (for the field value or the next step). */
    public record Action(Component label, Consumer<DialogScreen> run)
    {
    }

    /**
     * @param escape what escape does (usually the cancel action), or null to just close.
     */
    public record Spec(ItemStack icon, Component title, List<Line> lines, @Nullable Input input, List<Action> actions, int width,
                       @Nullable Runnable escape)
    {
        public Spec(final ItemStack icon, final Component title, final List<Line> lines, final List<Action> actions, final int width,
          @Nullable final Runnable escape)
        {
            this(icon, title, lines, null, actions, width, escape);
        }
    }

    private Spec spec;
    @Nullable
    private Consumer<DialogScreen> onTick;
    private final List<List<FormattedCharSequence>> wrapped = new ArrayList<>();
    private final List<Integer> lineY = new ArrayList<>();
    @Nullable
    private EditBox field;
    private String value;
    private int panelWidth;
    private int panelHeight;
    private int left;
    private int top;

    public DialogScreen(final Spec spec)
    {
        super(spec.title());
        this.spec = spec;
        this.value = spec.input() == null ? "" : spec.input().initial();
    }

    /** Switches to another step (e.g. a confirmation) without leaving the screen. */
    public void show(final Spec next)
    {
        this.spec = next;
        this.value = next.input() == null ? "" : next.input().initial();
        rebuildWidgets();
    }

    /** Runs every tick (e.g. to follow what the replaced window shows, then {@link #show} the new state). */
    public DialogScreen onTick(final Consumer<DialogScreen> action)
    {
        this.onTick = action;
        return this;
    }

    public Spec spec()
    {
        return spec;
    }

    @Override
    public void tick()
    {
        if (onTick != null)
        {
            onTick.accept(this);
        }
    }

    /** The text field's current value ("" without a field). */
    public String value()
    {
        return value;
    }

    private static int paragraphHeight(final Line line, final List<FormattedCharSequence> split)
    {
        return Math.max(line.icon().isEmpty() ? 0 : 16, split.size() * LINE) + PARAGRAPH_GAP;
    }

    @Override
    protected void init()
    {
        // A dialog taller than the screen gets wider (fewer text lines, buttons two by two) until it fits.
        int tryWidth = Math.min(spec.width(), width - 16);
        while (true)
        {
            layoutFor(tryWidth);
            if (panelHeight <= height - 8 || tryWidth >= Math.min(480, width - 16))
            {
                break;
            }
            tryWidth = Math.min(Math.min(480, width - 16), tryWidth + 60);
        }
    }

    private void layoutFor(final int targetWidth)
    {
        clearWidgets();
        panelWidth = targetWidth;
        final int textWidth = panelWidth - 2 * PADDING;

        // Buttons: one row when every label fits, else a grid of as many columns as fit (one or two).
        final List<Action> actions = spec.actions();
        final int count = Math.max(1, actions.size());
        int columns = count;
        while (columns > 1)
        {
            final int cellWidth = (textWidth - BUTTON_GAP * (columns - 1)) / columns;
            boolean fits = true;
            for (final Action action : actions)
            {
                fits &= font.width(action.label()) + 8 <= cellWidth;
            }
            if (fits)
            {
                break;
            }
            columns = columns > 2 ? 2 : 1;
        }
        final int rowWidth = (textWidth - BUTTON_GAP * (columns - 1)) / columns;
        final int buttonRows = (actions.size() + columns - 1) / columns;
        final int buttonsHeight = Math.max(1, buttonRows) * (BUTTON_HEIGHT + BUTTON_GAP) - BUTTON_GAP;

        // Paragraphs and field, top to bottom.
        wrapped.clear();
        lineY.clear();
        int y = HEADER;
        int fieldY = -1;
        for (int i = 0; i < spec.lines().size(); i++)
        {
            if (spec.input() != null && spec.input().afterLines() == i)
            {
                fieldY = y;
                y += 18 + 14;
            }
            final Line line = spec.lines().get(i);
            final List<FormattedCharSequence> split = font.split(line.text(), textWidth - (line.icon().isEmpty() ? 0 : ICON_SPACE));
            wrapped.add(split);
            lineY.add(y);
            y += paragraphHeight(line, split);
        }
        if (spec.input() != null && fieldY < 0)
        {
            fieldY = y;
            y += 18 + 14;
        }
        panelHeight = y + buttonsHeight + PADDING;
        left = (width - panelWidth) / 2;
        top = Math.max(4, (height - panelHeight) / 2);

        field = null;
        if (spec.input() != null)
        {
            field = addRenderableWidget(new EditBox(font, left + PADDING, top + fieldY, textWidth, 18, spec.title()));
            field.setMaxLength(spec.input().maxLength());
            field.setValue(value.length() > spec.input().maxLength() ? value.substring(0, spec.input().maxLength()) : value);
            field.setResponder(text -> value = text);
            setInitialFocus(field);
        }

        final int buttonsTop = top + panelHeight - PADDING - buttonsHeight;
        for (int i = 0; i < actions.size(); i++)
        {
            final Action action = actions.get(i);
            final int column = i % columns;
            final int row = i / columns;
            // A last button alone on its row takes the whole width.
            final boolean alone = column == 0 && i == actions.size() - 1;
            addRenderableWidget(Button.builder(action.label(), b -> action.run().accept(this))
                                  .bounds(left + PADDING + column * (rowWidth + BUTTON_GAP), buttonsTop + row * (BUTTON_HEIGHT + BUTTON_GAP),
                                    alone ? textWidth : rowWidth, BUTTON_HEIGHT).build());
        }
    }

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        renderTransparentBackground(g);
        Gui.panel(g, left, top, panelWidth, panelHeight);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        int textX = left + PADDING;
        if (!spec.icon().isEmpty())
        {
            g.renderItem(spec.icon(), left + 8, top + 7);
            textX = left + 28;
        }
        g.drawString(font, Gui.fit(font, spec.title(), left + panelWidth - PADDING - textX), textX, top + 11, Gui.LABEL, false);

        for (int i = 0; i < spec.lines().size(); i++)
        {
            final Line line = spec.lines().get(i);
            final List<FormattedCharSequence> split = wrapped.get(i);
            final int ty = top + lineY.get(i);
            int lx = left + PADDING;
            if (!line.icon().isEmpty())
            {
                g.renderItem(line.icon(), lx, ty + Math.max(0, split.size() * LINE - 16) / 2 - 2);
                lx += ICON_SPACE;
            }
            int ly = ty;
            for (final FormattedCharSequence text : split)
            {
                g.drawString(font, text, lx, ly, line.soft() ? Gui.LABEL_SOFT : Gui.LABEL, false);
                ly += LINE;
            }
        }
        if (field != null && spec.input() != null)
        {
            final String count = value.length() + " / " + spec.input().maxLength();
            g.drawString(font, count, left + panelWidth - PADDING - font.width(count), field.getY() + 21, Gui.LABEL_SOFT, false);
        }
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers)
    {
        if (key == GLFW.GLFW_KEY_ESCAPE && spec.escape() != null)
        {
            spec.escape().run();
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && field != null && !spec.actions().isEmpty())
        {
            spec.actions().get(spec.actions().size() - 1).run().accept(this);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
