package fr.mcvanillatweaks.client.buildtool;

import com.ldtteam.structurize.client.gui.WindowUndoRedo;
import com.ldtteam.structurize.network.messages.UndoRedoMessage;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Replaces Structurize's undo / redo window ({@code WindowUndoRedo}, creative only): the recent operations the server
 * sends back ({@code WindowUndoRedo.lastOperations}, filled after the window opened), each with "undo" and, unless it
 * already is an undo, "redo" ({@code UndoRedoMessage}, then the screen closes like Structurize's).
 */
public final class UndoRedoScreen extends Screen
{
    private static final int WIDTH = 280;
    private static final int HEIGHT = 200;
    private static final int ROW = 22;
    private static final int BUTTON = 50;

    private final ScrollArea list = new ScrollArea();
    private final Tooltip tooltip = new Tooltip();
    private int left;
    private int top;

    public UndoRedoScreen()
    {
        super(Component.translatable("structurize.gui.undoredo"));
    }

    @Override
    protected void init()
    {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + 8, top + HEIGHT - 28, WIDTH - 16, 20).build());
    }

    private static List<Tuple<String, Integer>> operations()
    {
        return WindowUndoRedo.lastOperations;
    }

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        renderTransparentBackground(g);
        Gui.panel(g, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        tooltip.clear();
        g.renderItem(new ItemStack(Items.CLOCK), left + 8, top + 7);
        g.drawString(font, getTitle(), left + 28, top + 11, Gui.LABEL, false);
        int ty = top + 28;
        for (final var line : font.split(Component.translatable("structurize.gui.undoredo.desc"), WIDTH - 16))
        {
            g.drawString(font, line, left + 8, ty, Gui.LABEL_SOFT, false);
            ty += 10;
        }
        final List<Tuple<String, Integer>> operations = operations();
        final int listTop = ty + 4;
        list.set(left + 8, listTop, WIDTH - 16, top + HEIGHT - 34 - listTop, operations.size() * ROW, ROW);
        list.begin(g);
        if (operations.isEmpty())
        {
            g.drawString(font, Component.translatable("mcvanillatweaks.undoredo.none"), list.listX() + 4, list.visibleTop() + 6, Gui.LIST_SOFT, true);
        }
        for (int i = 0; i < operations.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            list.hover(g, mouseX, mouseY, rowY, ROW);
            final String name = operations.get(i).getA();
            final boolean canRedo = name.indexOf("undo") != 0;
            final int undoX = list.listX() + list.listWidth() - 4 - (canRedo ? 2 * BUTTON + 2 : BUTTON);
            g.drawString(font, Gui.fit(font, Component.literal(name), undoX - 6 - (list.listX() + 4)), list.listX() + 4, rowY + 7, Gui.WHITE, true);
            final boolean overUndo = Gui.inside(mouseX, mouseY, undoX, rowY + 2, BUTTON, 18);
            Gui.textButton(g, font, undoX, rowY + 2, BUTTON, 18, Component.translatable("structurize.gui.undoredo.undoop").getString(), true, overUndo);
            if (canRedo)
            {
                final boolean overRedo = Gui.inside(mouseX, mouseY, undoX + BUTTON + 2, rowY + 2, BUTTON, 18);
                Gui.textButton(g, font, undoX + BUTTON + 2, rowY + 2, BUTTON, 18, Component.translatable("structurize.gui.undoredo.redoop").getString(), true,
                  overRedo);
            }
        }
        list.end(g);
        tooltip.render(g, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (list.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button == 0 && list.overRows(mouseX, mouseY))
        {
            final List<Tuple<String, Integer>> operations = operations();
            final int index = (int) Math.floor((mouseY - list.listY()) / ROW);
            if (index >= 0 && index < operations.size())
            {
                final int rowY = list.listY() + index * ROW;
                final boolean canRedo = operations.get(index).getA().indexOf("undo") != 0;
                final int undoX = list.listX() + list.listWidth() - 4 - (canRedo ? 2 * BUTTON + 2 : BUTTON);
                final boolean undo = Gui.inside(mouseX, mouseY, undoX, rowY + 2, BUTTON, 18);
                final boolean redo = canRedo && Gui.inside(mouseX, mouseY, undoX + BUTTON + 2, rowY + 2, BUTTON, 18);
                if (undo || redo)
                {
                    Gui.clickSound();
                    new UndoRedoMessage(operations.get(index).getB(), undo).sendToServer();
                    onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY)
    {
        return list.mouseDragged(mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button)
    {
        list.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        return list.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
