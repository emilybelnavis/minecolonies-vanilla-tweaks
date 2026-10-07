package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.buildings.modules.IMinimumStockModuleView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.network.messages.server.colony.building.AddMinimumStockToBuildingModuleMessage;
import com.minecolonies.core.network.messages.server.colony.building.RemoveMinimumStockFromBuildingModuleMessage;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.IconButton;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Minimum stock page (MineColonies' minimum stock module): the items the hut keeps in stock, with their number of
 * stacks and a remove button, and an "add" view replacing MineColonies' item picker: a search field, the stack count
 * and every item in a creative-inventory grid. Adding and removing send the same messages as MineColonies.
 * <pre>
 * Minimum stock: 3 items                       [Add]
 * The hut requests these items when ...
 * +-----------------------------------------------+
 * | (o) Oak Log                   4 stacks  [x]   |
 * +-----------------------------------------------+
 * </pre>
 */
final class StockTab extends HutTab
{
    private static final int ROW = 22;
    private static final int BUTTON_HEIGHT = 20;
    private static final int REMOVE = 14;
    private static final int MAX_STACKS = 1000;

    private final IMinimumStockModuleView module;
    private final ScrollArea list = new ScrollArea();
    private final ItemGrid grid = new ItemGrid();

    /** Add view state, kept across rebuilds. */
    private boolean adding;
    private String filter = "";
    private String count = "1";
    @Nullable
    private ItemStack picked;
    private int filterTimer;
    @Nullable
    private List<Entry> allItems;
    private EditBox countBox;
    private Button confirm;

    /** An item of the picker with its lower-case names, computed once. */
    private record Entry(ItemStack stack, String name, String id, boolean inInventory)
    {
    }

    StockTab(final HutScreen screen, final IMinimumStockModuleView module)
    {
        super(screen);
        this.module = module;
    }

    private List<Tuple<ItemStorage, Integer>> stock()
    {
        return module.getStock();
    }

    // ------------------------------------------------------------------ widgets

    @Override
    protected void init()
    {
        if (adding)
        {
            initPicker();
            return;
        }
        final boolean full = module.hasReachedLimit();
        final Component label = Component.translatable(full ? "com.minecolonies.coremod.gui.warehouse.limitreached" : "com.minecolonies.coremod.gui.warehouse.add");
        final int w = Math.max(70, font.width(label) + 34);
        final IconButton add = add(new IconButton(x + width - w, y, w, BUTTON_HEIGHT, new ItemStack(Items.ITEM_FRAME), label, b -> openPicker()));
        add.active = !full;
    }

    private void initPicker()
    {
        final Component countLabel = Component.translatable("com.minecolonies.coremod.gui.scan.select.stack");
        final int countWidth = 40;
        final int searchWidth = width - countWidth - font.width(countLabel) - 2 * GAP - 4;
        final EditBox search = add(new EditBox(font, x + 1, y + 1, searchWidth - 2, BUTTON_HEIGHT - 2, Component.translatable("mcvanillatweaks.hut.search")));
        search.setHint(Component.translatable("mcvanillatweaks.hut.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setMaxLength(50);
        search.setValue(filter);
        search.setResponder(text -> {
            if (!text.equals(filter))
            {
                filter = text;
                filterTimer = 5;
            }
        });
        countBox = add(new EditBox(font, x + width - countWidth + 1, y + 1, countWidth - 2, BUTTON_HEIGHT - 2, countLabel));
        countBox.setMaxLength(4);
        countBox.setFilter(text -> text.matches("\\d*"));
        countBox.setValue(count);
        countBox.setResponder(text -> {
            count = text;
            updateConfirm();
        });

        final int by = y + height - BUTTON_HEIGHT;
        add(Button.builder(Component.translatable("gui.cancel"), b -> closePicker()).bounds(x + width - 2 * 80 - 4, by, 80, BUTTON_HEIGHT).build());
        confirm = add(Button.builder(Component.translatable("com.minecolonies.coremod.gui.warehouse.add"), b -> confirm()).bounds(x + width - 80, by, 80, BUTTON_HEIGHT).build());
        updateConfirm();
        screen.setFocused(search);
        refreshItems();
    }

    private void openPicker()
    {
        if (module.hasReachedLimit())
        {
            return;
        }
        adding = true;
        filter = "";
        count = "1";
        picked = null;
        grid.scrollToTop();
        screen.rebuild();
    }

    private void closePicker()
    {
        adding = false;
        screen.rebuild();
    }

    private int stacks()
    {
        try
        {
            return count.isEmpty() ? 0 : Math.min(MAX_STACKS, Integer.parseInt(count));
        }
        catch (final NumberFormatException e)
        {
            return 0;
        }
    }

    private void updateConfirm()
    {
        if (confirm != null)
        {
            confirm.active = picked != null && stacks() > 0;
        }
    }

    private void confirm()
    {
        if (picked == null || stacks() <= 0)
        {
            return;
        }
        new AddMinimumStockToBuildingModuleMessage(building(), picked, stacks()).sendToServer();
        closePicker();
    }

    private void remove(final int index)
    {
        final List<Tuple<ItemStorage, Integer>> stock = stock();
        if (index < 0 || index >= stock.size())
        {
            return;
        }
        final Tuple<ItemStorage, Integer> entry = stock.remove(index);
        new RemoveMinimumStockFromBuildingModuleMessage(building(), entry.getA().getItemStack(), module.getProducer().getRuntimeID()).sendToServer();
    }

    @Override
    protected void tick()
    {
        if (filterTimer > 0 && --filterTimer == 0)
        {
            refreshItems();
        }
    }

    // ------------------------------------------------------------------ picker items, as WindowSelectRes

    private List<Entry> allItems()
    {
        if (allItems == null)
        {
            final List<ItemStack> stacks = new ArrayList<>();
            if (mc.player != null)
            {
                stacks.addAll(ItemStackUtils.allItemsPlusInventory(mc.player));
            }
            if (stacks.isEmpty())
            {
                // No colony data (dev preview): every registered item.
                BuiltInRegistries.ITEM.forEach(item -> {
                    if (item != Items.AIR)
                    {
                        stacks.add(new ItemStack(item));
                    }
                });
            }
            final List<Entry> entries = new ArrayList<>();
            for (final ItemStack stack : stacks)
            {
                final boolean owned = mc.player != null && mc.player.getInventory().contains(stack);
                entries.add(new Entry(stack, stack.getHoverName().getString().toLowerCase(Locale.ROOT), stack.getDescriptionId().toLowerCase(Locale.ROOT), owned));
            }
            allItems = entries;
        }
        return allItems;
    }

    /** MineColonies' order: items in the player's inventory first, then by name, or by closeness to the search. */
    private void refreshItems()
    {
        final String search = filter.toLowerCase(Locale.ROOT).trim();
        final List<Entry> shown = new ArrayList<>();
        for (final Entry entry : allItems())
        {
            if (search.isEmpty() || entry.name().contains(search) || entry.id().contains(search))
            {
                shown.add(entry);
            }
        }
        final Comparator<Entry> owned = Comparator.comparing(entry -> !entry.inInventory());
        shown.sort(search.isEmpty()
                     ? owned.thenComparing(Entry::name)
                     : owned.thenComparingInt(entry -> StringUtils.getLevenshteinDistance(entry.name(), search)));
        grid.setItems(shown.stream().map(Entry::stack).toList());
        grid.scrollToTop();
    }

    // ------------------------------------------------------------------ drawing

    private Component stackText(final int stacks)
    {
        return Component.translatable("mcvanillatweaks.hut.stock.stacks", stacks);
    }

    @Override
    protected void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        if (adding)
        {
            renderPicker(g, mouseX, mouseY, tooltip);
            return;
        }
        final List<Tuple<ItemStorage, Integer>> stock = stock();
        heading(g, Component.translatable("mcvanillatweaks.hut.stock.count", stock.size()), x, y + 6, width - 110);
        final List<FormattedCharSequence> hint = font.split(Component.translatable("mcvanillatweaks.hut.stock.hint"), width - 2);
        int ty = y + BUTTON_HEIGHT + 6;
        for (final FormattedCharSequence line : hint.subList(0, Math.min(2, hint.size())))
        {
            g.drawString(font, line, x + 1, ty, Gui.LABEL_SOFT, false);
            ty += 10;
        }

        final int top = ty + 4;
        list.set(x, top, width, y + height - top, stock.size() * ROW, ROW);
        list.begin(g);
        if (stock.isEmpty())
        {
            g.drawString(font, Gui.fit(font, Component.translatable("com.minecolonies.coremod.gui.warehouse.stock.empty"), list.listWidth() - 8),
              list.listX() + 4, list.visibleTop() + 6, Gui.LIST_SOFT, true);
        }
        for (int i = 0; i < stock.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final Tuple<ItemStorage, Integer> entry = stock.get(i);
            final ItemStack stack = entry.getA().getItemStack();
            final int stacks = entry.getB();
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            g.renderItem(stack, list.listX() + 3, rowY + 3);

            final int removeX = list.listX() + list.listWidth() - REMOVE - 4;
            final int removeY = rowY + (ROW - REMOVE) / 2;
            final boolean overRemove = list.overRows(mouseX, mouseY) && Gui.inside(mouseX, mouseY, removeX, removeY, REMOVE, REMOVE);
            Gui.textButton(g, font, removeX, removeY, REMOVE, REMOVE, "✖", true, overRemove);

            final Component amount = stackText(stacks);
            final int amountX = removeX - 8 - font.width(amount);
            g.drawString(font, amount, amountX, rowY + 7, Gui.YELLOW, true);
            final int textX = list.listX() + 24;
            g.drawString(font, Gui.fit(font, stack.getHoverName(), amountX - 8 - textX), textX, rowY + 7, Gui.WHITE, true);

            if (overRemove)
            {
                tooltip.set(Component.translatable("mcvanillatweaks.hut.stock.remove"));
            }
            else if (hovered)
            {
                final List<Component> lines = new ArrayList<>();
                lines.add(stack.getHoverName());
                lines.add(Component.translatable("mcvanillatweaks.hut.stock.amount", stacks, stacks * stack.getMaxStackSize()).withStyle(ChatFormatting.GRAY));
                tooltip.setAround(lines, mouseX, rowY, rowY + ROW);
            }
        }
        list.end(g);
    }

    private void renderPicker(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final Component countLabel = Component.translatable("com.minecolonies.coremod.gui.scan.select.stack");
        g.drawString(font, countLabel, x + width - 40 - 4 - font.width(countLabel), y + 6, Gui.LABEL, false);

        final int top = y + BUTTON_HEIGHT + 6;
        final int bottom = y + height - BUTTON_HEIGHT - 6;
        final int columns = ItemGrid.columnsFor(width);
        final int gridX = x + (width - ItemGrid.width(columns)) / 2;
        grid.set(gridX, top, columns, (bottom - top) / ItemGrid.SLOT);
        grid.render(g, font, mouseX, mouseY, tooltip, stack -> picked != null && ItemStack.isSameItemSameComponents(stack, picked));
        if (grid.items().isEmpty())
        {
            g.drawString(font, Component.translatable("mcvanillatweaks.hut.search_none"), gridX + 4, top + 5, Gui.LABEL_SOFT, false);
        }

        // The chosen item, next to the buttons.
        final int by = y + height - BUTTON_HEIGHT;
        Gui.slot(g, x, by + 1);
        if (picked != null)
        {
            g.renderItem(picked, x + 1, by + 2);
            final Component text = Component.translatable("mcvanillatweaks.hut.stock.picked", picked.getHoverName(), stackText(Math.max(0, stacks())));
            g.drawString(font, Gui.fit(font, text, width - 2 * 80 - 4 - 26 - GAP), x + 24, by + 6, Gui.LABEL, false);
            if (Gui.inside(mouseX, mouseY, x, by + 1, 18, 18))
            {
                tooltip.set(picked);
            }
        }
        else
        {
            g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.hut.stock.pick"), width - 2 * 80 - 4 - 26 - GAP), x + 24, by + 6, Gui.LABEL_SOFT, false);
        }
    }

    @Override
    protected boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (adding)
        {
            if (grid.mouseClicked(mouseX, mouseY))
            {
                return true;
            }
            final ItemStack stack = grid.itemAt(mouseX, mouseY);
            if (stack != null && button == 0)
            {
                Gui.clickSound();
                picked = stack;
                updateConfirm();
                return true;
            }
            return false;
        }
        if (list.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button != 0 || !list.overRows(mouseX, mouseY))
        {
            return false;
        }
        final int index = (int) Math.floor((mouseY - list.listY()) / ROW);
        if (index < 0 || index >= stock().size())
        {
            return false;
        }
        final int rowY = list.listY() + index * ROW;
        final int removeX = list.listX() + list.listWidth() - REMOVE - 4;
        if (Gui.inside(mouseX, mouseY, removeX, rowY + (ROW - REMOVE) / 2, REMOVE, REMOVE))
        {
            Gui.clickSound();
            remove(index);
            return true;
        }
        return false;
    }

    /** In the add view, escape goes back to the list and enter adds the chosen item. */
    @Override
    protected boolean keyPressed(final int key)
    {
        if (!adding)
        {
            return false;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE)
        {
            closePicker();
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && confirm != null && confirm.active)
        {
            confirm();
            return true;
        }
        return false;
    }

    @Override
    protected boolean mouseDragged(final double mouseX, final double mouseY)
    {
        return adding ? grid.mouseDragged(mouseY) : list.mouseDragged(mouseY);
    }

    @Override
    protected void mouseReleased()
    {
        grid.mouseReleased();
        list.mouseReleased();
    }

    @Override
    protected boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        return adding ? grid.mouseScrolled(mouseX, mouseY, delta) : list.mouseScrolled(mouseX, mouseY, delta);
    }

    // ------------------------------------------------------------------ preview

    /** Opens the add view (dev preview). */
    void previewPicker(final String search, @Nullable final ItemStack item)
    {
        openPicker();
        filter = search;
        picked = item;
        screen.rebuild();
    }
}
