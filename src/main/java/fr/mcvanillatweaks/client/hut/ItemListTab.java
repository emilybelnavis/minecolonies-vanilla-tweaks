package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.buildings.modules.IItemListModuleView;
import com.minecolonies.api.crafting.ItemStorage;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Item list page (MineColonies' filterable lists: fuel, compost, saplings, ores, flowers...): every item the list can
 * hold with a vanilla checkbox, a search field and the reset button. A row click switches the item like MineColonies'
 * On / Off button (through the module, so the florist's level rules and messages still apply). On inverted lists the
 * listed items are the ones switched off, as in MineColonies.
 * <pre>
 * [Search...                         ]  [Reset]
 * 3 of 24 enabled
 * +-----------------------------------------------+
 * | (o) Coal                                 [x]  |
 * | (o) Charcoal                             [ ]  |
 * +-----------------------------------------------+
 * </pre>
 */
final class ItemListTab extends HutTab
{
    private static final int ROW = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final int RESET_WIDTH = 90;
    private static final int BOX = 13;

    private final IItemListModuleView module;
    private final ScrollArea list = new ScrollArea();
    private final List<ItemStorage> all = new ArrayList<>();
    private List<ItemStorage> shown = List.of();
    private String filter = "";
    private int filterTimer;

    ItemListTab(final HutScreen screen, final IItemListModuleView module)
    {
        super(screen);
        this.module = module;
        final Set<ItemStorage> items = module.getAllItems() == null ? null : module.getAllItems().apply(screen.building());
        if (items != null)
        {
            all.addAll(items);
        }
        refresh();
    }

    /** What MineColonies' button shows as "On". */
    private boolean enabled(final ItemStorage item)
    {
        return module.isAllowedItem(item) != module.isInverted();
    }

    // ------------------------------------------------------------------ widgets

    @Override
    protected void init()
    {
        final EditBox search = add(new EditBox(font, x + 1, y + 1, width - RESET_WIDTH - GAP - 2, BUTTON_HEIGHT - 2, Component.translatable("mcvanillatweaks.hut.search")));
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
        final Button reset = add(Button.builder(Component.translatable("mcvanillatweaks.hut.list.reset"), b -> module.clearItems())
                                   .bounds(x + width - RESET_WIDTH, y, RESET_WIDTH, BUTTON_HEIGHT).build());
        reset.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("com.minecolonies.coremod.gui.workerhuts.resetdefault")));
    }

    @Override
    protected void tick()
    {
        if (filterTimer > 0 && --filterTimer == 0)
        {
            refresh();
        }
    }

    /**
     * Filters like MineColonies (name or translation key) and puts the enabled items first; sorted only when the list is
     * opened or searched, so a row doesn't jump away when it is switched.
     */
    private void refresh()
    {
        final String search = filter.toLowerCase(Locale.ROOT).trim();
        final List<ItemStorage> result = new ArrayList<>();
        for (final ItemStorage item : all)
        {
            final ItemStack stack = item.getItemStack();
            if (search.isEmpty() || stack.getDescriptionId().toLowerCase(Locale.ROOT).contains(search)
                  || stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(search))
            {
                result.add(item);
            }
        }
        result.sort(Comparator.<ItemStorage, Boolean>comparing(item -> !enabled(item)).thenComparing(item -> item.getItemStack().getHoverName().getString()));
        shown = result;
    }

    private void toggle(final ItemStorage item)
    {
        if (module.isAllowedItem(item))
        {
            module.removeItem(item);
        }
        else
        {
            module.addItem(item);
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final long on = all.stream().filter(this::enabled).count();
        final Component count = Component.translatable("mcvanillatweaks.hut.list.count", on, all.size());
        g.drawString(font, count, x + 1, y + BUTTON_HEIGHT + 8, Gui.LABEL, false);
        final Component desc = module.getDesc();
        if (desc != null)
        {
            final int descX = x + font.width(count) + 12;
            g.drawString(font, Gui.fit(font, Component.literal("· ").append(desc), x + width - descX), descX, y + BUTTON_HEIGHT + 8, Gui.LABEL_SOFT, false);
        }

        final int top = y + BUTTON_HEIGHT + 22;
        list.set(x, top, width, y + height - top, shown.size() * ROW, ROW);
        list.begin(g);
        if (shown.isEmpty())
        {
            g.drawString(font, Component.translatable(all.isEmpty() ? "mcvanillatweaks.hut.list.empty" : "mcvanillatweaks.hut.search_none"),
              list.listX() + 4, list.visibleTop() + 6, Gui.LIST_SOFT, true);
        }
        for (int i = 0; i < shown.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final ItemStorage item = shown.get(i);
            final ItemStack stack = item.getItemStack();
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            final boolean enabled = enabled(item);
            g.renderItem(stack, list.listX() + 3, rowY + 2);
            final int boxX = list.listX() + list.listWidth() - BOX - 5;
            g.blitSprite(Gui.checkbox(enabled, hovered), boxX, rowY + (ROW - BOX) / 2, BOX, BOX);
            final int textX = list.listX() + 24;
            g.drawString(font, Gui.fit(font, stack.getHoverName(), boxX - 6 - textX), textX, rowY + 6, enabled ? Gui.WHITE : Gui.LIST_SOFT, true);
            if (hovered)
            {
                final List<Component> lines = new ArrayList<>();
                lines.add(stack.getHoverName());
                lines.add(Component.translatable(enabled ? "com.minecolonies.coremod.gui.workerhuts.retrieveon" : "com.minecolonies.coremod.gui.workerhuts.retrieveoff")
                            .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
                lines.add(Component.translatable("mcvanillatweaks.hut.list.click").withStyle(ChatFormatting.GRAY));
                tooltip.setAround(lines, mouseX, rowY, rowY + ROW);
            }
        }
        list.end(g);
    }

    @Override
    protected boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (list.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button != 0 || !list.overRows(mouseX, mouseY))
        {
            return false;
        }
        final int index = (int) Math.floor((mouseY - list.listY()) / ROW);
        if (index < 0 || index >= shown.size())
        {
            return false;
        }
        Gui.clickSound();
        toggle(shown.get(index));
        return true;
    }

    @Override
    protected boolean mouseDragged(final double mouseX, final double mouseY)
    {
        return list.mouseDragged(mouseY);
    }

    @Override
    protected void mouseReleased()
    {
        list.mouseReleased();
    }

    @Override
    protected boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        return list.mouseScrolled(mouseX, mouseY, delta);
    }
}
