package fr.mcvanillatweaks.client.dialog;

import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.multipiston.TileEntityMultiPiston;
import com.ldtteam.multipiston.network.MultiPistonChangeMessage;
import fr.mcvanillatweaks.client.citizen.Dropdown;
import fr.mcvanillatweaks.client.citizen.DropdownButton;
import fr.mcvanillatweaks.client.citizen.Gui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Replaces MultiPiston's settings window ({@code WindowMultiPiston}, the block MineColonies' builders use for moving
 * doors). MultiPiston is optional: this class is only loaded once {@link DialogScreens} has seen its window, so it may
 * name MultiPiston's classes. Its window names the block's faces by their concrete colour; here each face also says
 * where it points, picking the face already used by the other side swaps them (MultiPiston refuses it with an error),
 * and range (1-10) and speed (1-3, the server's limits) are sliders instead of text fields. "Done" does what the window's
 * "Confirm" does: sets the block entity on the client and sends {@link MultiPistonChangeMessage}.
 * <pre>
 * [block] Multi-Piston
 * Input:   [# Yellow (top)       v]
 * Output:  [# Orange (bottom)    v]
 * The colours are the block's faces.
 * [        Range: 5 blocks       ]
 * [         Speed: Normal        ]
 * [Cancel]                  [Done]
 * </pre>
 */
public final class MultiPistonScreen extends Screen
{
    private static final int WIDTH = 220;
    private static final int HEIGHT = 168;
    private static final int MAX_RANGE = 10;
    private static final int MAX_SPEED = 3;

    /** MultiPiston's face colours, in its own order (the order of its drop-downs). */
    private static final Direction[] FACES = {Direction.UP, Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private final BlockPos pos;
    private final Dropdown dropdown = new Dropdown();
    private Direction input;
    private Direction output;
    private int range;
    private int speed;
    private int left;
    private int top;

    public MultiPistonScreen(final BlockPos pos, final Direction input, final Direction output, final int range, final int speed)
    {
        super(Component.translatable("mcvanillatweaks.multipiston.title"));
        this.pos = pos;
        this.input = input;
        this.output = output;
        this.range = Mth.clamp(range, 1, MAX_RANGE);
        this.speed = Mth.clamp(speed, 1, MAX_SPEED);
    }

    /** The screen for an opened {@code WindowMultiPiston}, or null when its block is gone (the window closes then too). */
    @Nullable
    static Screen of(final BOWindow window) throws ReflectiveOperationException
    {
        final BlockPos pos = (BlockPos) DialogScreens.field(window, "pos");
        final Minecraft mc = Minecraft.getInstance();
        final BlockEntity entity = pos == null || mc.level == null ? null : mc.level.getBlockEntity(pos);
        if (!(entity instanceof TileEntityMultiPiston piston))
        {
            return null;
        }
        return new MultiPistonScreen(pos, piston.getInput(), piston.getOutput(), piston.getRange(), piston.getSpeed());
    }

    @Override
    protected void init()
    {
        dropdown.close();
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        final int labels = Math.max(font.width(Component.translatable("mcvanillatweaks.multipiston.input")),
          font.width(Component.translatable("mcvanillatweaks.multipiston.output")));
        final int bx = left + 9 + labels + 8;
        final int bw = left + WIDTH - 8 - bx;
        addRenderableWidget(new FaceButton(bx, top + 30, bw, input, b -> openFaces(b, input, this::pickInput)));
        addRenderableWidget(new FaceButton(bx, top + 52, bw, output, b -> openFaces(b, output, this::pickOutput)));
        addRenderableWidget(new IntSlider(left + 8, top + 88, WIDTH - 16, 1, MAX_RANGE, range, value -> range = value, MultiPistonScreen::rangeLabel));
        addRenderableWidget(new IntSlider(left + 8, top + 112, WIDTH - 16, 1, MAX_SPEED, speed, value -> speed = value, MultiPistonScreen::speedLabel));
        final int half = (WIDTH - 20) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(left + 8, top + HEIGHT - 28, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> confirm()).bounds(left + WIDTH - 8 - half, top + HEIGHT - 28, half, 20).build());
    }

    private void openFaces(final Button anchor, final Direction current, final IntConsumer onPick)
    {
        final List<Dropdown.Entry> entries = new ArrayList<>();
        int selected = -1;
        for (int i = 0; i < FACES.length; i++)
        {
            entries.add(new Dropdown.Entry(faceName(FACES[i]), swatch(FACES[i])));
            if (FACES[i] == current)
            {
                selected = i;
            }
        }
        dropdown.open(anchor.getX(), anchor.getY(), anchor.getWidth(), anchor.getHeight(), entries, selected, onPick, height);
    }

    /** The two sides can't share a face: taking the other side's face gives it this side's old one. */
    private void pickInput(final int index)
    {
        if (FACES[index] == output)
        {
            output = input;
        }
        input = FACES[index];
        rebuildWidgets();
    }

    private void pickOutput(final int index)
    {
        if (FACES[index] == input)
        {
            input = output;
        }
        output = FACES[index];
        rebuildWidgets();
    }

    private void confirm()
    {
        try
        {
            final Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.getBlockEntity(pos) instanceof TileEntityMultiPiston piston)
            {
                piston.setSpeed(speed);
                piston.setRange(range);
                piston.setOutput(output);
                piston.setInput(input);
            }
            new MultiPistonChangeMessage(pos, input, output, range, speed).sendToServer();
        }
        catch (final LinkageError | RuntimeException e)
        {
            DialogScreens.report(e);
        }
        onClose();
    }

    private static Component faceName(final Direction face)
    {
        return Component.translatable("mcvanillatweaks.multipiston.face." + face.getSerializedName());
    }

    /** The colour of the face's concrete on the block's model. */
    private static int swatch(final Direction face)
    {
        return switch (face)
        {
            case UP -> 0xFFF0AF15;
            case DOWN -> 0xFFE06100;
            case NORTH -> 0xFF2D2F8F;
            case EAST -> 0xFF495B24;
            case SOUTH -> 0xFF8E2020;
            case WEST -> 0xFF64209C;
        };
    }

    private static Component rangeLabel(final int value)
    {
        return Component.translatable(value == 1 ? "mcvanillatweaks.multipiston.range.one" : "mcvanillatweaks.multipiston.range", value);
    }

    private static Component speedLabel(final int value)
    {
        return Component.translatable("mcvanillatweaks.multipiston.speed",
          Component.translatable("mcvanillatweaks.multipiston.speed." + value));
    }

    private static ItemStack icon()
    {
        final ResourceLocation id = ResourceLocation.fromNamespaceAndPath("multipiston", "multipistonblock");
        return BuiltInRegistries.ITEM.containsKey(id) ? new ItemStack(BuiltInRegistries.ITEM.get(id)) : new ItemStack(Items.PISTON);
    }

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        renderTransparentBackground(g);
        Gui.panel(g, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void render(final GuiGraphics g, final int realMouseX, final int realMouseY, final float partialTick)
    {
        final int mouseX = dropdown.isOpen() ? -1000 : realMouseX;
        final int mouseY = dropdown.isOpen() ? -1000 : realMouseY;
        super.render(g, mouseX, mouseY, partialTick);
        g.renderItem(icon(), left + 8, top + 7);
        g.drawString(font, Gui.fit(font, getTitle(), WIDTH - 36), left + 28, top + 11, Gui.LABEL, false);
        g.drawString(font, Component.translatable("mcvanillatweaks.multipiston.input"), left + 9, top + 34, Gui.LABEL, false);
        g.drawString(font, Component.translatable("mcvanillatweaks.multipiston.output"), left + 9, top + 56, Gui.LABEL, false);
        g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.multipiston.colours"), WIDTH - 18), left + 9, top + 74, Gui.LABEL_SOFT, false);
        dropdown.render(g, font, realMouseX, realMouseY);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        return dropdown.mouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        return dropdown.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers)
    {
        return dropdown.keyPressed(key) || super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    /** Drop-down button showing the face's colour square before its name. */
    private static final class FaceButton extends DropdownButton
    {
        private final int colour;

        FaceButton(final int x, final int y, final int width, final Direction face, final OnPress onPress)
        {
            super(x, y, width, 16, faceName(face), onPress);
            this.colour = swatch(face);
        }

        @Override
        public void renderString(final GuiGraphics g, final Font font, final int textColour)
        {
            final int sy = getY() + (getHeight() - 8) / 2;
            g.fill(getX() + 5, sy - 1, getX() + 14, sy + 8, 0xFF000000);
            g.fill(getX() + 6, sy, getX() + 13, sy + 7, colour);
            renderScrollingString(g, font, getMessage(), getX() + 18, getY(), getX() + getWidth() - 14, getY() + getHeight(), textColour);
            Gui.dropdownArrow(g, getX() + getWidth() - 12, getY() + (getHeight() - 4) / 2, textColour);
        }
    }

    /** Vanilla slider over whole numbers, like the game's option sliders. */
    private static final class IntSlider extends AbstractSliderButton
    {
        private final int min;
        private final int max;
        private final IntConsumer onChange;
        private final java.util.function.IntFunction<Component> label;

        IntSlider(final int x, final int y, final int width, final int min, final int max, final int initial, final IntConsumer onChange,
          final java.util.function.IntFunction<Component> label)
        {
            super(x, y, width, 20, Component.empty(), (double) (initial - min) / (max - min));
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            this.label = label;
            updateMessage();
        }

        private int current()
        {
            return min + (int) Math.round(value * (max - min));
        }

        @Override
        protected void updateMessage()
        {
            setMessage(label.apply(current()));
        }

        @Override
        protected void applyValue()
        {
            onChange.accept(current());
        }
    }
}
