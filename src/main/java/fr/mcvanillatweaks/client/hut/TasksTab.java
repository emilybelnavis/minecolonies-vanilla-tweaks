package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IStackBasedTask;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.IDeliverymanRequestable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.buildings.moduleviews.CrafterRequestTaskModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.RequestTaskModuleView;
import fr.mcvanillatweaks.client.citizen.CitizenDiagnosis;
import com.minecolonies.core.colony.requestsystem.requests.AbstractRequest;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import fr.mcvanillatweaks.client.citizen.VanillaIcons;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tasks page (MineColonies' task list of crafters, couriers and the warehouse): the queue of the hut's workers, in
 * order, each row showing the task's item in a slot, what is done with it, who asked for it (and for whom, when the
 * request comes from another one) and, for deliveries, its priority. Tasks in progress are green, like MineColonies.
 * <pre>
 * 5 tasks · 1 in progress
 * +-----------------------------------------------------+
 * | [16x] Crafting: Bread                   Priority: 3 |
 * |       Baker -> Restaurant                           |
 * +-----------------------------------------------------+
 * </pre>
 */
final class TasksTab extends HutTab
{
    private static final int ROW = 26;
    private static final int SLOT_Y = 4;

    /** A task with what its row shows. */
    private record Task(IRequest<?> request, @Nullable IRequest<?> parent)
    {
    }

    private final RequestTaskModuleView module;
    private final ScrollArea list = new ScrollArea();
    private List<Task> tasks = List.of();
    private int ticks;
    private int lifeCount;

    TasksTab(final HutScreen screen, final RequestTaskModuleView module)
    {
        super(screen);
        this.module = module;
        refresh();
    }

    @Override
    protected void tick()
    {
        lifeCount++;
        if (++ticks % 20 == 0)
        {
            refresh();
        }
    }

    @Nullable
    private IRequestManager requests()
    {
        final IColonyView colony = building().getColony();
        return colony == null ? null : colony.getRequestManager();
    }

    /** MineColonies' list: tasks whose request still exists, with the furthest parent from another requester. */
    private void refresh()
    {
        final IRequestManager manager = requests();
        final List<IToken<?>> tokens = module.getTasks();
        if (manager == null || tokens == null)
        {
            tasks = List.of();
            return;
        }
        final List<Task> result = new ArrayList<>();
        for (final IToken<?> token : new ArrayList<>(tokens))
        {
            final IRequest<?> request = manager.getRequestForToken(token);
            if (request != null)
            {
                result.add(new Task(request, parent(manager, request)));
            }
        }
        tasks = result;
    }

    @Nullable
    private static IRequest<?> parent(final IRequestManager manager, final IRequest<?> request)
    {
        if (request.getParent() == null)
        {
            return null;
        }
        IRequest<?> parent = manager.getRequestForToken(request.getParent());
        while (parent != null && parent.getParent() != null && parent.getRequester().getLocation().equals(request.getRequester().getLocation()))
        {
            final IRequest<?> next = manager.getRequestForToken(parent.getParent());
            if (next == null)
            {
                break;
            }
            parent = next;
        }
        return parent;
    }

    // ------------------------------------------------------------------ text, as WindowHutRequestTaskModule

    private static Component title(final IRequest<?> request)
    {
        if (request instanceof IStackBasedTask task)
        {
            return task.getDisplayPrefix().copy().append(task.getTaskStack().getHoverName());
        }
        return Component.literal(request.getShortDisplayString().getString().replace("§f", ""));
    }

    private Component requester(final IRequestManager manager, final Task task)
    {
        final MutableComponent text = task.request().getRequester().getRequesterDisplayName(manager, task.request()).copy();
        if (task.parent() != null)
        {
            text.append(" → ").append(task.parent().getRequester().getRequesterDisplayName(manager, task.parent()));
        }
        return text;
    }

    @Nullable
    private static Component priority(final IRequest<?> request)
    {
        return request.getRequest() instanceof IDeliverymanRequestable delivery
                 ? Component.translatable("mcvanillatweaks.hut.tasks.priority", delivery.getPriority())
                 : null;
    }

    private ItemStack shown(final IRequest<?> request)
    {
        if (request instanceof IStackBasedTask task)
        {
            final ItemStack stack = task.getTaskStack().copy();
            stack.setCount(Math.max(1, task.getDisplayCount()));
            return stack;
        }
        final List<ItemStack> stacks = request.getDisplayStacks();
        return stacks.isEmpty() ? ItemStack.EMPTY : stacks.get(lifeCount / 30 % stacks.size());
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final IRequestManager manager = requests();
        final long running = tasks.stream().filter(task -> task.request().getState() == RequestState.IN_PROGRESS).count();
        heading(g, Component.translatable("mcvanillatweaks.hut.tasks.count", tasks.size(), running), x, y + 4, width);

        final int top = y + 18;
        list.set(x, top, width, y + height - top, tasks.size() * ROW, ROW);
        list.begin(g);
        if (tasks.isEmpty() || manager == null)
        {
            if (module instanceof CrafterRequestTaskModuleView)
            {
                // Crafters only work on orders: say why there are none, and how to get some.
                final CitizenDiagnosis.Reason reason = CitizenDiagnosis.crafterIdle(building());
                int lineY = list.visibleTop() + 6;
                g.drawString(font, Gui.fit(font, reason.text(), list.listWidth() - 8), list.listX() + 4, lineY, Gui.YELLOW, true);
                lineY += 14;
                for (final FormattedCharSequence line : font.split(reason.detail(), list.listWidth() - 8))
                {
                    g.drawString(font, line, list.listX() + 4, lineY, Gui.LIST_SOFT, true);
                    lineY += 11;
                }
            }
            else
            {
                g.drawString(font, Gui.fit(font, Component.translatable("com.minecolonies.coremod.gui.workerhuts.crafter.tasks.empty"), list.listWidth() - 8),
                  list.listX() + 4, list.visibleTop() + 6, Gui.LIST_SOFT, true);
            }
            list.end(g);
            return;
        }
        for (int i = 0; i < tasks.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            renderRow(g, manager, tasks.get(i), i, rowY, mouseX, mouseY, tooltip);
        }
        list.end(g);
    }

    private void renderRow(final GuiGraphics g, final IRequestManager manager, final Task task, final int index, final int rowY,
      final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final IRequest<?> request = task.request();
        final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
        final boolean visible = list.overRows(mouseX, mouseY);
        final int left = list.listX() + 2;
        Gui.slot(g, left, rowY + SLOT_Y);
        final ItemStack stack = shown(request);
        if (!stack.isEmpty())
        {
            Gui.slotItem(g, font, left, rowY + SLOT_Y, stack, null);
        }
        else if (!AbstractRequest.MISSING.equals(request.getDisplayIcon()))
        {
            g.renderItem(VanillaIcons.request(request.getDisplayIcon()), left + 1, rowY + SLOT_Y + 1);
        }
        final boolean overSlot = visible && Gui.inside(mouseX, mouseY, left, rowY + SLOT_Y, 18, 18);
        if (overSlot)
        {
            Gui.slotHighlight(g, left, rowY + SLOT_Y);
        }

        final boolean running = request.getState() == RequestState.IN_PROGRESS;
        final Component priority = priority(request);
        final int right = list.listX() + list.listWidth() - 4;
        if (priority != null)
        {
            g.drawString(font, priority, right - font.width(priority), rowY + 3, Gui.LIST_SOFT, true);
        }
        final int textX = left + 23;
        final int titleWidth = (priority == null ? right : right - font.width(priority) - 6) - textX;
        final Component title = title(request);
        final Component requester = requester(manager, task);
        // The first task is the one being worked on: numbered rows show the order.
        g.drawString(font, Gui.fit(font, Component.literal((index + 1) + ". ").withStyle(ChatFormatting.GRAY).append(title), titleWidth), textX, rowY + 3,
          running ? Gui.GREEN : Gui.WHITE, true);
        g.drawString(font, Gui.fit(font, requester, right - textX), textX, rowY + 14, Gui.LIST_SOFT, true);

        if (overSlot && !stack.isEmpty())
        {
            tooltip.set(stack);
        }
        else if (hovered)
        {
            tooltip.setAround(rowTooltip(task, title, requester, priority), mouseX, rowY, rowY + ROW);
        }
    }

    /** Full texts, positions of the requesters (MineColonies' tooltip), state and resolver lines. */
    private List<Component> rowTooltip(final Task task, final Component title, final Component requester, @Nullable final Component priority)
    {
        final IRequest<?> request = task.request();
        final List<Component> lines = new ArrayList<>();
        lines.add(title);
        lines.add(requester.copy().withStyle(ChatFormatting.GRAY));
        String positions = request.getRequester().getLocation().getInDimensionLocation().toShortString();
        if (task.parent() != null)
        {
            positions += " → " + task.parent().getRequester().getLocation().getInDimensionLocation().toShortString();
        }
        lines.add(Component.literal(positions).withStyle(ChatFormatting.DARK_GRAY));
        if (request.getState() != null)
        {
            lines.add(Component.translatable("mcvanillatweaks.citizen.request_state",
              Component.translatable("mcvanillatweaks.citizen.request_state." + request.getState().name().toLowerCase(Locale.ROOT)))
              .withStyle(request.getState() == RequestState.IN_PROGRESS ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        if (priority != null)
        {
            lines.add(priority.copy().withStyle(ChatFormatting.GRAY));
        }
        final IColonyView colony = building().getColony();
        if (colony != null)
        {
            lines.addAll(request.getResolverToolTip(colony));
        }
        return lines;
    }

    @Override
    protected boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        return list.mouseClicked(mouseX, mouseY);
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
