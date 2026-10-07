package fr.mcvanillatweaks.client.townhall;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.colonyEvents.descriptions.IBuildingEventDescription;
import com.minecolonies.api.colony.colonyEvents.descriptions.ICitizenEventDescription;
import com.minecolonies.api.colony.colonyEvents.descriptions.IColonyEventDescription;
import com.minecolonies.api.colony.workorders.IWorkOrderView;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingBuilderView;
import com.minecolonies.core.colony.eventhooks.citizenEvents.CitizenDiedEvent;
import com.minecolonies.core.network.messages.server.colony.WorkOrderChangeMessage;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * "Information" tab: the colony's events on the left, the work orders on the right. Work orders are sorted and
 * reprioritised exactly like MineColonies' list (highest priority first, moving one sets it just above or below its
 * neighbour); the up / down / delete buttons appear on the hovered order.
 */
final class InfoTab extends TownHallTab
{
    private static final int EVENT_ROW = 24;
    private static final int ORDER_ROW = 24;
    /** Room for the 16 px item icon on the left of the rows. */
    private static final int ICON = 23;
    private static final int BUTTON = 14;
    private static final int EVENTS_WIDTH = 150;

    private final ScrollArea events = new ScrollArea();
    private final ScrollArea orders = new ScrollArea();
    private List<IWorkOrderView> workOrders = List.of();

    InfoTab(final TownHallScreen screen)
    {
        super(screen);
        refresh();
    }

    @Override
    void tick()
    {
        refresh();
    }

    /** Same filter and order as MineColonies' work order list. */
    private void refresh()
    {
        final List<IWorkOrderView> list = new ArrayList<>();
        for (final IWorkOrderView order : colony().getWorkOrders())
        {
            if (order.shouldShowIn(view()))
            {
                list.add(order);
            }
        }
        list.sort(Comparator.comparing(IWorkOrderView::getPriority, Comparator.reverseOrder()));
        workOrders = list;
    }

    private int ordersX()
    {
        return x + EVENTS_WIDTH + GAP;
    }

    private int ordersWidth()
    {
        return width - EVENTS_WIDTH - GAP;
    }

    private void updateAreas()
    {
        events.set(x, y + HEADING, EVENTS_WIDTH, height - HEADING, view().getColonyEvents().size() * EVENT_ROW, EVENT_ROW);
        orders.set(ordersX(), y + HEADING, ordersWidth(), height - HEADING, workOrders.size() * ORDER_ROW, ORDER_ROW);
    }

    @Override
    void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        updateAreas();
        heading(g, Component.translatable("com.minecolonies.coremod.gui.townhall.events"), x, y, EVENTS_WIDTH);
        heading(g, Component.translatable("com.minecolonies.coremod.gui.townhall.workorders"), ordersX(), y, ordersWidth());
        renderEvents(g, mouseX, mouseY, tooltip);
        renderOrders(g, mouseX, mouseY, tooltip);
    }

    // ------------------------------------------------------------------ events

    private void renderEvents(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final List<IColonyEventDescription> list = view().getColonyEvents();
        events.begin(g);
        if (list.isEmpty())
        {
            empty(g, events, Component.translatable("mcvanillatweaks.townhall.no_events"));
        }
        for (int i = 0; i < list.size(); i++)
        {
            final int rowY = events.listY() + i * EVENT_ROW;
            if (rowY + EVENT_ROW < events.visibleTop() || rowY > events.visibleBottom())
            {
                continue;
            }
            final IColonyEventDescription event = list.get(i);
            final Component title = eventTitle(event);
            final Component subject = eventSubject(event);
            final int textX = events.listX() + ICON;
            final int textWidth = events.listWidth() - ICON - 4;
            final boolean hovered = events.overRows(mouseX, mouseY) && Gui.inside(mouseX, mouseY, events.listX(), rowY, events.listWidth(), EVENT_ROW);
            if (hovered)
            {
                g.fill(events.listX(), rowY, events.listX() + events.listWidth(), rowY + EVENT_ROW, Gui.ROW_HOVER);
                final List<Component> lines = new ArrayList<>(List.of(title, subject.copy().withStyle(ChatFormatting.GRAY)));
                if (event instanceof CitizenDiedEvent died && died.getDeathCause() != null && !died.getDeathCause().isEmpty())
                {
                    lines.add(Component.literal(died.getDeathCause()).withStyle(ChatFormatting.GRAY));
                }
                lines.add(position(event.getEventPos()).withStyle(ChatFormatting.GRAY));
                tooltip.setAround(lines, mouseX, rowY, rowY + EVENT_ROW);
            }
            g.renderItem(ColonyIcons.event(colony(), event), events.listX() + 3, rowY + 4);
            g.drawString(font, Gui.fit(font, title, textWidth), textX, rowY + 3, Gui.WHITE, true);
            g.drawString(font, Gui.fit(font, subject, textWidth), textX, rowY + 14, Gui.LIST_SOFT, true);
        }
        events.end(g);
    }

    /** MineColonies only has English names for its events: known ones are translated. */
    private static Component eventTitle(final IColonyEventDescription event)
    {
        final String name = event.getName() == null ? "" : event.getName();
        final String key = "mcvanillatweaks.colony_event." + name.toLowerCase(Locale.ROOT).replace(' ', '_');
        return I18n.exists(key) ? Component.translatable(key) : Component.literal(name);
    }

    private static Component eventSubject(final IColonyEventDescription event)
    {
        if (event instanceof ICitizenEventDescription citizen)
        {
            return Component.literal(citizen.getCitizenName());
        }
        if (event instanceof IBuildingEventDescription building)
        {
            return Component.translatable(building.getBuildingName()).append(" " + building.getLevel());
        }
        return Component.empty();
    }

    private static net.minecraft.network.chat.MutableComponent position(final BlockPos pos)
    {
        return Component.translatable("mcvanillatweaks.townhall.position", pos.getX(), pos.getY(), pos.getZ());
    }

    // ------------------------------------------------------------------ work orders

    private int upX()
    {
        return orders.listX() + orders.listWidth() - 3 * (BUTTON + 1);
    }

    private int downX()
    {
        return upX() + BUTTON + 1;
    }

    private int deleteX()
    {
        return downX() + BUTTON + 1;
    }

    private void renderOrders(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        orders.begin(g);
        if (workOrders.isEmpty())
        {
            empty(g, orders, Component.translatable("mcvanillatweaks.townhall.no_work_orders"));
        }
        for (int i = 0; i < workOrders.size(); i++)
        {
            final int rowY = orders.listY() + i * ORDER_ROW;
            if (rowY + ORDER_ROW < orders.visibleTop() || rowY > orders.visibleBottom())
            {
                continue;
            }
            final IWorkOrderView order = workOrders.get(i);
            final boolean hovered = orders.overRows(mouseX, mouseY) && Gui.inside(mouseX, mouseY, orders.listX(), rowY, orders.listWidth(), ORDER_ROW);
            // MineColonies' name is "building\nlevels": the building on the first line, the levels and the builder on the second.
            final String[] parts = order.getDisplayName().getString().split("\n", 2);
            final Component work = Component.literal(parts[0]);
            final String builder = builderName(order);
            final Component assignee = builder.isEmpty() ? Component.translatable("mcvanillatweaks.townhall.unassigned") : Component.literal(builder);
            final Component detail = parts.length > 1 ? Component.literal(parts[1] + " · ").append(assignee) : assignee;
            final int textRight = hovered ? upX() - 2 : orders.listX() + orders.listWidth() - 2;
            final int textX = orders.listX() + ICON;
            final int textWidth = textRight - textX;
            if (hovered)
            {
                g.fill(orders.listX(), rowY, orders.listX() + orders.listWidth(), rowY + ORDER_ROW, Gui.ROW_HOVER);
            }
            g.renderItem(ColonyIcons.hut(colony(), order.getLocation()), orders.listX() + 3, rowY + 4);
            g.drawString(font, Gui.fit(font, work, textWidth), textX, rowY + 3, Gui.WHITE, true);
            g.drawString(font, Gui.fit(font, detail, textWidth), textX, rowY + 14, Gui.LIST_SOFT, true);
            if (!hovered)
            {
                continue;
            }
            final int buttonY = rowY + (ORDER_ROW - BUTTON) / 2;
            button(g, upX(), buttonY, "↑", i > 0, mouseX, mouseY, tooltip, "mcvanillatweaks.townhall.priority_up");
            button(g, downX(), buttonY, "↓", i < workOrders.size() - 1, mouseX, mouseY, tooltip, "mcvanillatweaks.townhall.priority_down");
            button(g, deleteX(), buttonY, "✖", true, mouseX, mouseY, tooltip, "mcvanillatweaks.townhall.delete_order");
            if (tooltip.isEmpty())
            {
                tooltip.setAround(List.of(order.getDisplayName(), assignee.copy().withStyle(ChatFormatting.GRAY),
                  Component.translatable("mcvanillatweaks.townhall.priority", order.getPriority()).withStyle(ChatFormatting.GRAY)), mouseX, rowY,
                  rowY + ORDER_ROW);
            }
        }
        orders.end(g);
    }

    /** The builder working on the order, found like MineColonies does (its hut is the order's claimer). */
    private String builderName(final IWorkOrderView order)
    {
        final BlockPos claimedBy = order.getClaimedBy();
        if (claimedBy == null)
        {
            return "";
        }
        for (final IBuildingView building : colony().getClientBuildingManager().getBuildings().values())
        {
            if (building.getPosition().equals(claimedBy) && building instanceof AbstractBuildingBuilderView builder)
            {
                return builder.getWorkerName() == null ? "" : builder.getWorkerName();
            }
        }
        return "";
    }

    private void button(final GuiGraphics g, final int bx, final int by, final String label, final boolean active, final int mouseX,
      final int mouseY, final Tooltip tooltip, final String tooltipKey)
    {
        final boolean hovered = Gui.inside(mouseX, mouseY, bx, by, BUTTON, BUTTON);
        Gui.textButton(g, font, bx, by, BUTTON, BUTTON, label, active, hovered && active);
        if (hovered && active)
        {
            tooltip.set(Component.translatable(tooltipKey));
        }
    }

    private void empty(final GuiGraphics g, final ScrollArea area, final Component text)
    {
        final int w = area.listWidth() - 8;
        g.drawString(font, Gui.fit(font, text, w), area.listX() + 4 + Math.max(0, (w - font.width(text)) / 2), area.visibleTop() + 20,
          Gui.LIST_SOFT, true);
    }

    // ------------------------------------------------------------------ input

    @Override
    boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (events.mouseClicked(mouseX, mouseY) || orders.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button != 0 || !orders.overRows(mouseX, mouseY))
        {
            return false;
        }
        for (int i = 0; i < workOrders.size(); i++)
        {
            final int rowY = orders.listY() + i * ORDER_ROW;
            final int buttonY = rowY + (ORDER_ROW - BUTTON) / 2;
            final IWorkOrderView order = workOrders.get(i);
            if (Gui.inside(mouseX, mouseY, upX(), buttonY, BUTTON, BUTTON) && i > 0)
            {
                // MineColonies' "plus": just above the order before it.
                setPriority(order, workOrders.get(i - 1).getPriority() + 1, "plus");
                return true;
            }
            if (Gui.inside(mouseX, mouseY, downX(), buttonY, BUTTON, BUTTON) && i < workOrders.size() - 1)
            {
                setPriority(order, workOrders.get(i + 1).getPriority() - 1, "minus");
                return true;
            }
            if (Gui.inside(mouseX, mouseY, deleteX(), buttonY, BUTTON, BUTTON))
            {
                Gui.clickSound();
                screen.trigger("delete");
                new WorkOrderChangeMessage(view(), order.getID(), true, 0).sendToServer();
                final List<IWorkOrderView> list = new ArrayList<>(workOrders);
                list.remove(order);
                workOrders = list;
                return true;
            }
        }
        return false;
    }

    private void setPriority(final IWorkOrderView order, final int priority, final String buttonId)
    {
        Gui.clickSound();
        screen.trigger(buttonId);
        order.setPriority(priority);
        new WorkOrderChangeMessage(view(), order.getID(), false, priority).sendToServer();
        refresh();
    }

    @Override
    boolean mouseDragged(final double mouseX, final double mouseY)
    {
        return events.mouseDragged(mouseY) || orders.mouseDragged(mouseY);
    }

    @Override
    void mouseReleased()
    {
        events.mouseReleased();
        orders.mouseReleased();
    }

    @Override
    boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        return events.mouseScrolled(mouseX, mouseY, delta) || orders.mouseScrolled(mouseX, mouseY, delta);
    }
}
