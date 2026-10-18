package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.workorders.IWorkOrderView;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.colony.buildings.modules.settings.StringSetting;
import com.minecolonies.core.colony.buildings.moduleviews.BuildingResourcesModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.SettingsModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WorkOrderListModuleView;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.network.messages.server.colony.WorkOrderChangeMessage;
import com.minecolonies.core.network.messages.server.colony.building.builder.BuilderSelectWorkOrderMessage;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import fr.mcvanillatweaks.client.townhall.ColonyIcons;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Builder's work orders page (MineColonies' work order module): the orders this builder works on and, in manual mode,
 * the free orders it can take, listed and sorted like MineColonies 1.1.1403 (same filters; the order the builder works
 * on now first, outlined in green, then its other orders, then the free ones, each by priority). Each row
 * shows the building's hut, what is done (build, upgrade, repair, removal), the distance and its button: "assign" for a
 * free order in manual mode, "cancel" for this builder's orders (which removes the order, so it asks for a second
 * click). The buttons send MineColonies' messages.
 * <pre>
 * Work orders: 3                    Mode: Manual
 * +-----------------------------------------------+
 * | [hut] Farm                          [Assign]  |
 * |       Upgrade 2 -> 3 · 24 blocks              |
 * +-----------------------------------------------+
 * </pre>
 */
final class WorkOrdersTab extends HutTab
{
    private static final int ROW = 26;
    private static final int BUTTON_HEIGHT = 16;
    private static final int CONFIRM_TICKS = 60;

    private final ScrollArea list = new ScrollArea();
    private List<IWorkOrderView> orders = List.of();
    private boolean manual;
    /** The order the builder works on now (its resources module), or -1. */
    private int current = -1;
    private int ticks;
    /** The order whose cancel button was clicked once, and until when the second click counts. */
    private int confirmId = -1;
    private int confirmUntil;

    WorkOrdersTab(final HutScreen screen, final WorkOrderListModuleView module)
    {
        super(screen);
        refresh();
    }

    @Override
    protected void tick()
    {
        if (++ticks % 20 == 0)
        {
            refresh();
        }
        if (confirmId >= 0 && ticks > confirmUntil)
        {
            confirmId = -1;
        }
    }

    private boolean manualMode()
    {
        final SettingsModuleView settings = building().getModuleViewByType(SettingsModuleView.class);
        final StringSetting mode = settings == null ? null : settings.getSetting(BuildingBuilder.MODE);
        return mode != null && BuildingBuilder.MANUAL_SETTING.equals(mode.getValue());
    }

    /** MineColonies' filter: orders shown here and in range, this builder's ones, plus the free ones in manual mode. */
    private void refresh()
    {
        manual = manualMode();
        final IColonyView colony = building().getColony();
        if (colony == null)
        {
            orders = List.of();
            return;
        }
        final BlockPos here = building().getPosition();
        final BuildingResourcesModuleView resources = building().getModuleViewByType(BuildingResourcesModuleView.class);
        current = resources == null ? -1 : resources.getWorkOrderId();
        final List<IWorkOrderView> result = new ArrayList<>();
        for (final IWorkOrderView order : colony.getWorkOrders())
        {
            final boolean mine = here.equals(order.getClaimedBy());
            final boolean free = BlockPos.ZERO.equals(order.getClaimedBy());
            final boolean shown = order.shouldShowIn(building()) && order.canBuildIgnoringDistance(here, building().getBuildingLevel());
            if (shown && (mine || manual && free))
            {
                result.add(order);
            }
        }
        result.sort(Comparator.comparingInt((IWorkOrderView order) -> order.getID() == current ? 0 : mine(order) ? 1 : 2)
                      .thenComparing(Comparator.comparingInt(IWorkOrderView::getPriority).reversed())
                      .thenComparingInt(IWorkOrderView::getID));
        orders = result;
    }

    private boolean mine(final IWorkOrderView order)
    {
        return building().getPosition().equals(order.getClaimedBy());
    }

    /** Why "assign" is off, as MineColonies' tooltip; null when the order can be assigned. */
    @Nullable
    private String blocked(final IWorkOrderView order)
    {
        if (!order.canBuildIgnoringDistance(building().getPosition(), building().getBuildingLevel()))
        {
            return "tile.blockhut.cannotbuild";
        }
        if (!BlockPos.ZERO.equals(order.getClaimedBy()))
        {
            return "tile.blockhut.alreadyclaimed";
        }
        if (building().getAllAssignedCitizens().isEmpty())
        {
            return "tile.blockhut.noworkerassigned";
        }
        return null;
    }

    /** The row's button, or null (free orders outside manual mode). */
    @Nullable
    private String buttonLabel(final IWorkOrderView order)
    {
        if (mine(order))
        {
            return I18n.get(confirmId == order.getID() ? "mcvanillatweaks.hut.wo.confirm" : "com.minecolonies.coremod.gui.builder.cancel");
        }
        return manual ? I18n.get("com.minecolonies.coremod.gui.builder.select") : null;
    }

    private int buttonWidth(final String label)
    {
        return Math.max(50, font.width(label) + 10);
    }

    private void press(final IWorkOrderView order)
    {
        if (mine(order))
        {
            if (confirmId != order.getID())
            {
                confirmId = order.getID();
                confirmUntil = ticks + CONFIRM_TICKS;
                return;
            }
            confirmId = -1;
            new WorkOrderChangeMessage(building(), order.getID(), true, 0).sendToServer();
        }
        else if (manual && blocked(order) == null)
        {
            new BuilderSelectWorkOrderMessage(building(), order.getID()).sendToServer();
        }
    }

    // ------------------------------------------------------------------ text

    private static Component type(final IWorkOrderView order)
    {
        final WorkOrderType type = order.getWorkOrderType();
        if (type == null)
        {
            return Component.empty();
        }
        if (type == WorkOrderType.UPGRADE)
        {
            return Component.translatable("mcvanillatweaks.hut.wo.type.upgrade", order.getCurrentLevel(), order.getTargetLevel());
        }
        return Component.translatable("mcvanillatweaks.hut.wo.type." + type.name().toLowerCase(Locale.ROOT));
    }

    private Component detail(final IWorkOrderView order)
    {
        final MutableComponent text = type(order).copy();
        text.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
          .append(Component.translatable("com.minecolonies.coremod.gui.blocks.distance", BlockPosUtil.getDistance2D(order.getLocation(), building().getPosition())));
        if (order.getID() == current)
        {
            text.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
              .append(Component.translatable("mcvanillatweaks.hut.wo.current").withStyle(ChatFormatting.GREEN));
        }
        else if (mine(order))
        {
            text.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
              .append(Component.translatable("mcvanillatweaks.hut.wo.mine").withStyle(ChatFormatting.GREEN));
        }
        return text;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        heading(g, Component.translatable("mcvanillatweaks.hut.wo.count", orders.size()), x, y + 1, width / 2);
        final Component mode = Component.translatable("mcvanillatweaks.hut.wo.mode",
          Component.translatable(manual ? "com.minecolonies.core.builder.setting.manual" : "com.minecolonies.core.builder.setting.automatic"));
        g.drawString(font, mode, x + width - font.width(mode) - 1, y + 1, Gui.LABEL, false);
        if (Gui.inside(mouseX, mouseY, x + width - font.width(mode) - 2, y - 1, font.width(mode) + 2, 11))
        {
            tooltip.set(List.of(mode, Component.translatable(manual ? "mcvanillatweaks.hut.wo.manual_hint" : "mcvanillatweaks.hut.wo.auto_hint")
                                        .withStyle(ChatFormatting.GRAY)));
        }
        final var hint = font.split(Component.translatable(manual ? "mcvanillatweaks.hut.wo.manual_hint" : "mcvanillatweaks.hut.wo.auto_hint"), width - 2);
        int ty = y + 14;
        for (final var line : hint.subList(0, Math.min(2, hint.size())))
        {
            g.drawString(font, line, x + 1, ty, Gui.LABEL_SOFT, false);
            ty += 10;
        }

        final int top = ty + 4;
        list.set(x, top, width, y + height - top, orders.size() * ROW, ROW);
        list.begin(g);
        if (orders.isEmpty())
        {
            g.drawString(font, Gui.fit(font, Component.translatable("com.minecolonies.coremod.gui.townhall.workorders.empty"), list.listWidth() - 8),
              list.listX() + 4, list.visibleTop() + 6, Gui.LIST_SOFT, true);
        }
        final IColonyView colony = building().getColony();
        for (int i = 0; i < orders.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final IWorkOrderView order = orders.get(i);
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            if (mine(order))
            {
                g.fill(list.listX(), rowY, list.listX() + list.listWidth(), rowY + ROW, Gui.ROW_HOVER);
            }
            if (order.getID() == current)
            {
                // MineColonies outlines the current order in green.
                g.renderOutline(list.listX(), rowY, list.listWidth(), ROW, 0xFF00AA00);
            }
            if (colony != null)
            {
                g.renderItem(ColonyIcons.hut(colony, order.getLocation()), list.listX() + 3, rowY + 5);
            }

            final String label = buttonLabel(order);
            int textRight = list.listX() + list.listWidth() - 4;
            boolean overButton = false;
            if (label != null)
            {
                final int bw = buttonWidth(label);
                final int bx = textRight - bw;
                final int by = rowY + (ROW - BUTTON_HEIGHT) / 2;
                final boolean active = mine(order) || blocked(order) == null;
                overButton = list.overRows(mouseX, mouseY) && Gui.inside(mouseX, mouseY, bx, by, bw, BUTTON_HEIGHT);
                Gui.textButton(g, font, bx, by, bw, BUTTON_HEIGHT, label, active, overButton && active);
                textRight = bx - 6;
                if (overButton)
                {
                    if (mine(order))
                    {
                        tooltip.set(Component.translatable(confirmId == order.getID() ? "mcvanillatweaks.hut.wo.confirm_hint" : "mcvanillatweaks.hut.wo.cancel_hint"));
                    }
                    else if (!active)
                    {
                        tooltip.set(Component.translatable(blocked(order)));
                    }
                }
            }
            final int textX = list.listX() + 24;
            final Component name = order.getDisplayName() == null ? Component.empty() : Component.literal(order.getDisplayName().getString().replace("\n", " "));
            g.drawString(font, Gui.fit(font, name, textRight - textX), textX, rowY + 3, Gui.WHITE, true);
            g.drawString(font, Gui.fit(font, detail(order), textRight - textX), textX, rowY + 14, Gui.LIST_SOFT, true);
            if (hovered && !overButton)
            {
                final List<Component> lines = new ArrayList<>();
                lines.add(name);
                lines.add(detail(order).copy().withStyle(ChatFormatting.GRAY));
                lines.add(Component.literal(order.getLocation().toShortString()).withStyle(ChatFormatting.DARK_GRAY));
                lines.add(Component.translatable("mcvanillatweaks.hut.wo.priority", order.getPriority()).withStyle(ChatFormatting.GRAY));
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
        if (index < 0 || index >= orders.size())
        {
            return false;
        }
        final IWorkOrderView order = orders.get(index);
        final String label = buttonLabel(order);
        if (label == null)
        {
            return false;
        }
        final int bw = buttonWidth(label);
        final int bx = list.listX() + list.listWidth() - 4 - bw;
        final int rowY = list.listY() + index * ROW;
        if (Gui.inside(mouseX, mouseY, bx, rowY + (ROW - BUTTON_HEIGHT) / 2, bw, BUTTON_HEIGHT) && (mine(order) || blocked(order) == null))
        {
            Gui.clickSound();
            press(order);
            return true;
        }
        return false;
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
