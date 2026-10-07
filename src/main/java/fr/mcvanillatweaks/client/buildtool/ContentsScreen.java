package fr.mcvanillatweaks.client.buildtool;

import com.ldtteam.common.util.BlockToItemHelper;
import com.ldtteam.structurize.api.ItemStackUtils;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a blueprint holds (Structurize's {@code WindowBlockGetterContents}, opened from the build tool and the placement
 * tools): its blocks, what their containers hold, its entities and what they carry, counted like Structurize (same
 * helpers), in four tabs. Structurize opens its window as a layer, which can't be swapped, so our screens open this one;
 * "back" returns to the screen that opened it.
 */
public final class ContentsScreen extends Screen
{
    private static final int WIDTH = 280;
    private static final int HEIGHT = 220;
    private static final int ROW = 18;

    private final Screen back;
    private final List<List<ItemStack>> lists = new ArrayList<>();
    private final ScrollArea list = new ScrollArea();
    private final Tooltip tooltip = new Tooltip();
    private int tab;
    private int left;
    private int top;

    private static final String[] TABS = {"structurize.gui.content.blocks", "structurize.gui.content.blocks_content", "structurize.gui.content.entities",
      "structurize.gui.content.entities_content"};

    public ContentsScreen(final Screen back, final Blueprint blueprint, final Level level, final Collection<Entity> entities)
    {
        this(back, blueprint, level, new BlockPos(blueprint.getMinX(), blueprint.getMinBuildHeight(), blueprint.getMinZ()),
          new BlockPos(blueprint.getMaxX() - 1, blueprint.getMaxBuildHeight() - 1, blueprint.getMaxZ() - 1), entities);
    }

    public ContentsScreen(final Screen back, final BlockGetter getter, @Nullable final Level level, final BlockPos start, final BlockPos end,
      final Collection<Entity> entities)
    {
        super(Component.translatable("com.ldtteam.structurize.gui.scantool.showres"));
        this.back = back;
        final Map<Item, ItemStack> blocks = new LinkedHashMap<>();
        final Map<Item, ItemStack> blockContents = new LinkedHashMap<>();
        final Map<Item, ItemStack> entityItems = new LinkedHashMap<>();
        final Map<Item, ItemStack> entityContents = new LinkedHashMap<>();
        for (final BlockPos pos : BlockPos.betweenClosed(start, end))
        {
            final BlockState state = getter.getBlockState(pos);
            final BlockEntity blockEntity = getter.getBlockEntity(pos);
            add(blocks, BlockToItemHelper.getItemStack(state, blockEntity, Minecraft.getInstance().player));
            if (blockEntity != null && blockEntity.getLevel() == null && level != null)
            {
                ItemStackUtils.ITEM_HANDLER_FAKE_LEVEL.get(level).withFakeLevelContext(state, blockEntity, level,
                  fake -> ItemStackUtils.getItemHandlersFromProvider(blockEntity, pos, state)
                            .forEach(handler -> ItemStackUtils.deepExtractItemHandler(handler, stack -> add(blockContents, stack))));
            }
            else if (blockEntity != null)
            {
                ItemStackUtils.getItemHandlersFromProvider(blockEntity, pos, state)
                  .forEach(handler -> ItemStackUtils.deepExtractItemHandler(handler, stack -> add(blockContents, stack)));
            }
        }
        for (final Entity entity : entities)
        {
            add(entityItems, ItemStackUtils.getEntitySpawningItem(entity));
            ItemStackUtils.getItemStacksOfEntity(entity).forEach(stack -> add(entityContents, stack));
        }
        for (final Map<Item, ItemStack> map : List.of(blocks, blockContents, entityItems, entityContents))
        {
            final List<ItemStack> sorted = new ArrayList<>(map.values());
            sorted.sort(Comparator.comparing(stack -> stack.getHoverName().getString()));
            lists.add(sorted);
        }
    }

    private static void add(final Map<Item, ItemStack> map, @Nullable final ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
        {
            return;
        }
        final ItemStack entry = map.computeIfAbsent(stack.getItem(), item -> stack.copyWithCount(0));
        entry.setCount(entry.getCount() + Math.max(1, stack.getCount()));
    }

    @Override
    protected void init()
    {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        final int tw = (WIDTH - 16 - 6) / 4;
        for (int i = 0; i < TABS.length; i++)
        {
            final int index = i;
            final Button button = addRenderableWidget(Button.builder(Component.translatable(TABS[i]), b -> {
                tab = index;
                rebuildWidgets();
            }).bounds(left + 8 + i * (tw + 2), top + 24, tw, 20).build());
            button.active = tab != i;
            button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
              Component.translatable(TABS[i]).append(" : " + lists.get(i).size())));
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> minecraft.setScreen(back))
                              .bounds(left + 8, top + HEIGHT - 28, WIDTH - 16, 20).build());
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
        g.drawString(font, getTitle(), left + 8, top + 10, Gui.LABEL, false);
        final List<ItemStack> items = lists.get(tab);
        final int listTop = top + 50;
        list.set(left + 8, listTop, WIDTH - 16, top + HEIGHT - 34 - listTop, items.size() * ROW, ROW);
        list.begin(g);
        if (items.isEmpty())
        {
            g.drawString(font, Component.translatable("mcvanillatweaks.contents.empty"), list.listX() + 4, list.visibleTop() + 6, Gui.LIST_SOFT, true);
        }
        for (int i = 0; i < items.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final ItemStack stack = items.get(i);
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            g.renderItem(stack, list.listX() + 2, rowY + 1);
            final String count = String.valueOf(stack.getCount());
            final int countX = list.listX() + list.listWidth() - 4 - font.width(count);
            g.drawString(font, Gui.fit(font, stack.getHoverName(), countX - 6 - (list.listX() + 22)), list.listX() + 22, rowY + 5, Gui.WHITE, true);
            g.drawString(font, count, countX, rowY + 5, Gui.LIST_SOFT, true);
            if (hovered)
            {
                tooltip.set(stack.copyWithCount(1));
            }
        }
        list.end(g);
        tooltip.render(g, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        return list.mouseClicked(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
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
    public void onClose()
    {
        minecraft.setScreen(back);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
