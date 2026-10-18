package fr.mcvanillatweaks.client.names;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Picks the item shown before a name list, in the game's menu style: title, search field (item names and ids, accents
 * ignored), every item in a grid inside a vanilla list (hover = name, click = chosen), "default icon" and cancel at the
 * bottom. The current icon is framed.
 */
final class IconPickerScreen extends Screen
{
    private static final int CELL = 20;
    private static final int HEADER = 48;
    private static final int FOOTER = 36;

    private final Screen parent;
    @Nullable
    private final Item current;
    /** Receives the chosen item, or null for the default icon. */
    private final Consumer<Item> onPick;
    private final List<Choice> all = new ArrayList<>();
    private String query = "";
    private Grid grid;

    private record Choice(Item item, ItemStack stack, String folded)
    {
    }

    IconPickerScreen(final Screen parent, @Nullable final Item current, final Consumer<Item> onPick)
    {
        super(Component.translatable("mcvanillatweaks.names.icon.title"));
        this.parent = parent;
        this.current = current;
        this.onPick = onPick;
    }

    @Override
    protected void init()
    {
        if (all.isEmpty())
        {
            final FeatureFlagSet flags = minecraft.level != null ? minecraft.level.enabledFeatures() : FeatureFlags.DEFAULT_FLAGS;
            for (final Item item : BuiltInRegistries.ITEM)
            {
                if (item != Items.AIR && item.isEnabled(flags))
                {
                    final ItemStack stack = new ItemStack(item);
                    all.add(new Choice(item, stack, NameListsScreen.fold(stack.getHoverName().getString() + " " + BuiltInRegistries.ITEM.getKey(item))));
                }
            }
        }
        final EditBox search = addRenderableWidget(new EditBox(font, width / 2 - 100, 20, 200, 20, Component.translatable("mcvanillatweaks.names.icon.search")));
        search.setHint(Component.translatable("mcvanillatweaks.names.icon.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(text -> {
            query = text;
            grid.refill();
        });
        grid = addRenderableWidget(new Grid(minecraft, width, height - HEADER - FOOTER, HEADER));
        addRenderableWidget(Button.builder(Component.translatable("mcvanillatweaks.names.icon.default"), b -> pick(null))
          .bounds(width / 2 - 154, height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
          .bounds(width / 2 + 4, height - 28, 150, 20).build());
        grid.refill();
        setInitialFocus(search);
    }

    private void pick(@Nullable final Item item)
    {
        onPick.accept(item);
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose()
    {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 7, 0xFFFFFFFF);
    }

    /** The items, a row of cells per list entry, as many columns as fit (up to 16). */
    private final class Grid extends ObjectSelectionList<Line>
    {
        private final int columns;

        private Grid(final Minecraft minecraft, final int width, final int height, final int y)
        {
            super(minecraft, width, height, y, CELL);
            columns = Math.max(6, Math.min(16, (width - 40) / CELL));
        }

        private void refill()
        {
            final String folded = NameListsScreen.fold(query.strip());
            clearEntries();
            Line line = null;
            for (final Choice choice : all)
            {
                if (!folded.isEmpty() && !choice.folded().contains(folded))
                {
                    continue;
                }
                if (line == null || line.choices.size() == columns)
                {
                    line = new Line();
                    addEntry(line);
                }
                line.choices.add(choice);
            }
            setScrollAmount(0);
        }

        @Override
        public int getRowWidth()
        {
            return columns * CELL;
        }

        @Override
        public void renderWidget(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
        {
            super.renderWidget(g, mouseX, mouseY, partialTick);
            if (children().isEmpty())
            {
                g.drawCenteredString(font, Component.translatable("mcvanillatweaks.names.icon.none"), getX() + getWidth() / 2, getY() + 20, 0xFFA0A0A0);
            }
        }
    }

    private final class Line extends ObjectSelectionList.Entry<Line>
    {
        private final List<Choice> choices = new ArrayList<>();

        @Nullable
        private Choice at(final double mouseX)
        {
            final int index = (int) Math.floor((mouseX - grid.getRowLeft()) / CELL);
            return index >= 0 && index < choices.size() ? choices.get(index) : null;
        }

        @Override
        public void render(final GuiGraphics g, final int index, final int top, final int left, final int width, final int height,
          final int mouseX, final int mouseY, final boolean hovered, final float partialTick)
        {
            final Choice over = hovered ? at(mouseX) : null;
            for (int i = 0; i < choices.size(); i++)
            {
                final Choice choice = choices.get(i);
                final int x = left + i * CELL;
                final int y = top + (height - 16) / 2 - 2;
                if (choice.item() == current)
                {
                    g.renderOutline(x, y, CELL, CELL, 0xFFFFFFFF);
                }
                if (choice == over)
                {
                    g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x60FFFFFF);
                }
                g.renderItem(choice.stack(), x + 2, y + 2);
            }
            if (over != null)
            {
                setTooltipForNextRenderPass(over.stack().getHoverName());
            }
        }

        @Override
        public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
        {
            final Choice choice = at(mouseX);
            if (choice != null)
            {
                pick(choice.item());
                return true;
            }
            return false;
        }

        @Override
        public Component getNarration()
        {
            return Component.empty();
        }
    }
}
