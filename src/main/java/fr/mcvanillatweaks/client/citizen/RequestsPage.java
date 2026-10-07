package fr.mcvanillatweaks.client.citizen;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IStackBasedTask;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.client.gui.citizen.RequestWindowCitizen;
import com.minecolonies.core.client.gui.modules.IWindowModule;
import com.minecolonies.core.client.gui.modules.RequestTreeWindowModule;
import com.minecolonies.core.colony.requestsystem.requests.AbstractRequest;
import com.minecolonies.core.colony.requestsystem.requests.StandardRequests;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * "Requests" tab: the citizen's open requests as a tree (sub-requests indented), each with an item slot, its title and
 * its requester. Clicking a row opens its details; the fulfill / cancel buttons of MineColonies' request list appear
 * on the hovered row, so the text gets the whole width the rest of the time. The actions go through the request tree
 * module of the {@link RequestWindowCitizen} this page replaces, so they behave exactly like MineColonies' own buttons
 * (the details window even comes back to this page when closed).
 */
final class RequestsPage extends PageView
{
    private static final int ROW = 24;
    private static final int SLOT_Y = 3;
    private static final int BUTTON = 14;
    private static final int REFRESH_TICKS = 20;
    /** Detail windows already opened automatically, so closing one does not reopen it. */
    private static final Map<RequestWindowCitizen, Boolean> AUTO_OPENED = Collections.synchronizedMap(new WeakHashMap<>());

    @Nullable
    private final RequestWindowCitizen window;
    @Nullable
    private final RequestTreeWindowModule module;
    private final ScrollArea area = new ScrollArea();
    private List<CitizenInfo.RequestNode> nodes = List.of();
    /** Requests just fulfilled/cancelled, hidden until the server confirms. */
    private final Set<IToken<?>> handled = new HashSet<>();
    private int ticks;
    private int lifeCount;

    RequestsPage(final CitizenScreen screen, @Nullable final RequestWindowCitizen window)
    {
        super(screen);
        this.window = window;
        this.module = window == null ? null : moduleOf(window);
        refresh();
        openRequestedDetails();
    }

    @Nullable
    private static RequestTreeWindowModule moduleOf(final RequestWindowCitizen window)
    {
        for (final IWindowModule module : window.getModules())
        {
            if (module instanceof RequestTreeWindowModule tree)
            {
                return tree;
            }
        }
        return null;
    }

    /** A request interaction ("see the request") opens the window on one request: show its details, once. */
    private void openRequestedDetails()
    {
        if (window == null || module == null || AUTO_OPENED.put(window, Boolean.TRUE) != null)
        {
            return;
        }
        try
        {
            final Field field = RequestWindowCitizen.class.getDeclaredField("autoOpenRequest");
            field.setAccessible(true);
            if (field.get(window) instanceof IRequest<?> request)
            {
                module.openDetails(request);
            }
        }
        catch (final ReflectiveOperationException | RuntimeException ignored)
        {
            // the list is still shown
        }
    }

    @Override
    void tick()
    {
        if (!Screen.hasShiftDown())
        {
            lifeCount++;
        }
        if (++ticks % REFRESH_TICKS == 0)
        {
            refresh();
        }
    }

    private void refresh()
    {
        if (module != null)
        {
            module.refreshOpenRequests();
        }
        final List<CitizenInfo.RequestNode> all = CitizenInfo.openRequests(citizen());
        final Set<IToken<?>> open = new HashSet<>();
        final List<CitizenInfo.RequestNode> shown = new ArrayList<>();
        for (final CitizenInfo.RequestNode node : all)
        {
            open.add(node.request().getId());
            if (!handled.contains(node.request().getId()))
            {
                shown.add(node);
            }
        }
        handled.retainAll(open);
        nodes = shown;
    }

    @Override
    void layout(final int x, final int y, final int width, final int height)
    {
        super.layout(x, y, width, height);
        updateArea();
    }

    private void updateArea()
    {
        area.set(x, y, width, height, nodes.size() * ROW, ROW);
    }

    private int fulfillX()
    {
        return area.listX() + area.listWidth() - 2 * (BUTTON + 1);
    }

    private int cancelX()
    {
        return fulfillX() + BUTTON + 1;
    }

    @Override
    void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        updateArea();
        area.begin(g);
        if (nodes.isEmpty())
        {
            final Component none = Component.translatable("mcvanillatweaks.citizen.requests.none");
            g.drawString(font, none, area.listX() + (area.listWidth() - font.width(none)) / 2, area.visibleTop() + 20, Gui.LIST_SOFT, true);
        }
        final IColonyView colony = citizen().getColony();
        for (int i = 0; i < nodes.size(); i++)
        {
            final int rowY = area.listY() + i * ROW;
            if (rowY + ROW < area.visibleTop() || rowY > area.visibleBottom())
            {
                continue;
            }
            renderRow(g, colony, nodes.get(i), rowY, mouseX, mouseY, tooltip);
        }
        area.end(g);
    }

    private void renderRow(final GuiGraphics g, final IColonyView colony, final CitizenInfo.RequestNode node, final int rowY,
      final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final IRequest<?> request = node.request();
        final int indent = Math.min(node.depth(), 4) * 6;
        final int left = area.listX() + indent;
        final boolean visible = area.overRows(mouseX, mouseY);
        final boolean hovered = visible && Gui.inside(mouseX, mouseY, area.listX(), rowY, area.listWidth(), ROW);
        if (hovered)
        {
            g.fill(area.listX(), rowY, area.listX() + area.listWidth(), rowY + ROW, Gui.ROW_HOVER);
        }
        if (node.depth() > 0)
        {
            // tree branch
            g.fill(left - 4, rowY, left - 3, rowY + ROW / 2, 0xFF6A6A6A);
            g.fill(left - 4, rowY + ROW / 2, left - 1, rowY + ROW / 2 + 1, 0xFF6A6A6A);
        }

        Gui.slot(g, left + 1, rowY + SLOT_Y);
        final List<ItemStack> stacks = request.getDisplayStacks();
        ItemStack shown = ItemStack.EMPTY;
        if (request instanceof IStackBasedTask task)
        {
            shown = task.getTaskStack().copy();
            shown.setCount(Math.max(1, task.getDisplayCount()));
        }
        else if (!stacks.isEmpty())
        {
            shown = stacks.get(lifeCount / 30 % stacks.size());
        }
        final boolean overSlot = visible && Gui.inside(mouseX, mouseY, left + 1, rowY + SLOT_Y, 18, 18);
        if (!shown.isEmpty())
        {
            Gui.slotItem(g, font, left + 1, rowY + SLOT_Y, shown, null);
        }
        else if (!AbstractRequest.MISSING.equals(request.getDisplayIcon()))
        {
            g.renderItem(VanillaIcons.request(request.getDisplayIcon()), left + 2, rowY + SLOT_Y + 1);
        }
        if (overSlot)
        {
            Gui.slotHighlight(g, left + 1, rowY + SLOT_Y);
            if (!shown.isEmpty())
            {
                tooltip.set(shown);
            }
            else
            {
                tooltip.set(new ArrayList<Component>(request.getResolverToolTip(colony)));
            }
        }

        final Component title = title(request, stacks);
        final Component requester = request.getRequester().getRequesterDisplayName(colony.getRequestManager(), request);
        // Clear of the slot's stack size, which sits on the requester line.
        final int textX = left + 24;
        final int textWidth = (hovered ? fulfillX() - 2 : area.listX() + area.listWidth() - 2) - textX;
        g.drawString(font, Gui.fit(font, title, textWidth), textX, rowY + 3, Gui.WHITE, true);
        g.drawString(font, Gui.fit(font, requester, textWidth), textX, rowY + 14, Gui.LIST_SOFT, true);
        if (hovered && !overSlot)
        {
            // Under the row, not at the mouse: the fulfill / cancel buttons stay visible.
            tooltip.setAround(rowTooltip(colony, request, title, requester), mouseX, rowY, rowY + ROW);
        }

        if (hovered)
        {
            final int buttonY = rowY + (ROW - BUTTON) / 2;
            final boolean canFulfill = module != null && module.isFulfillable(request);
            final boolean canCancel = module != null && module.isCancellable(request);
            button(g, fulfillX(), buttonY, "✔", canFulfill, mouseX, mouseY, tooltip, "com.minecolonies.coremod.gui.citizen.requestsfulfill");
            button(g, cancelX(), buttonY, "✖", canCancel, mouseX, mouseY, tooltip, "com.minecolonies.coremod.gui.citizen.requestscancel");
        }
    }

    /** Full title and requester, state, MineColonies' resolver lines, and what a click does. */
    private List<Component> rowTooltip(final IColonyView colony, final IRequest<?> request, final Component title, final Component requester)
    {
        final List<Component> lines = new ArrayList<>();
        lines.add(title);
        lines.add(requester.copy().withStyle(ChatFormatting.GRAY));
        if (request.getState() != null)
        {
            lines.add(Component.translatable("mcvanillatweaks.citizen.request_state",
              Component.translatable("mcvanillatweaks.citizen.request_state." + request.getState().name().toLowerCase(Locale.ROOT)))
              .withStyle(ChatFormatting.GRAY));
        }
        lines.addAll(request.getResolverToolTip(colony));
        if (module != null)
        {
            lines.add(Component.translatable("mcvanillatweaks.citizen.requests.click_details").withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    private void button(final GuiGraphics g, final int bx, final int by, final String label, final boolean active,
      final int mouseX, final int mouseY, final Tooltip tooltip, final String tooltipKey)
    {
        final boolean hovered = Gui.inside(mouseX, mouseY, bx, by, BUTTON, BUTTON);
        Gui.textButton(g, font, bx, by, BUTTON, BUTTON, label, active, hovered && active);
        if (hovered)
        {
            tooltip.set(Component.translatable(tooltipKey));
        }
    }

    /** Same text as MineColonies' request list. */
    private Component title(final IRequest<?> request, final List<ItemStack> stacks)
    {
        if (request instanceof IStackBasedTask task)
        {
            return task.getDisplayPrefix().copy().append(task.getTaskStack().getHoverName());
        }
        if (request instanceof StandardRequests.ItemTagRequest && !stacks.isEmpty())
        {
            return stacks.get(lifeCount / 30 % stacks.size()).getHoverName();
        }
        return Component.literal(request.getShortDisplayString().getString().replace("§f", ""));
    }

    @Override
    boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (area.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button != 0 || module == null || !area.overRows(mouseX, mouseY))
        {
            return false;
        }
        for (int i = 0; i < nodes.size(); i++)
        {
            final int rowY = area.listY() + i * ROW;
            if (!Gui.inside(mouseX, mouseY, area.listX(), rowY, area.listWidth(), ROW))
            {
                continue;
            }
            final int buttonY = rowY + (ROW - BUTTON) / 2;
            final IRequest<?> request = nodes.get(i).request();
            if (Gui.inside(mouseX, mouseY, fulfillX(), buttonY, BUTTON, BUTTON) && module.isFulfillable(request)
                  && module instanceof RequestTreeWindowModule.IRequestTreeSupportsFulfill fulfill)
            {
                Gui.clickSound();
                // MineColonies' "fulfill" advancement listens to this exact button id and window.
                CitizenScreens.trigger("fulfill", RequestWindowCitizen.WINDOW_ID);
                fulfill.onFulfill(request);
                handled.add(request.getId());
                refresh();
                return true;
            }
            if (Gui.inside(mouseX, mouseY, cancelX(), buttonY, BUTTON, BUTTON) && module.isCancellable(request))
            {
                Gui.clickSound();
                CitizenScreens.trigger("cancel", RequestWindowCitizen.WINDOW_ID);
                module.cancel(request);
                handled.add(request.getId());
                refresh();
                return true;
            }
            if (Gui.inside(mouseX, mouseY, fulfillX(), buttonY, 2 * BUTTON + 1, BUTTON))
            {
                // a disabled button
                return true;
            }
            Gui.clickSound();
            // Same button id as MineColonies' "details" button.
            CitizenScreens.trigger("detail", RequestWindowCitizen.WINDOW_ID);
            module.openDetails(request);
            return true;
        }
        return false;
    }

    @Override
    boolean mouseDragged(final double mouseX, final double mouseY)
    {
        return area.mouseDragged(mouseY);
    }

    @Override
    void mouseReleased()
    {
        area.mouseReleased();
    }

    @Override
    boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        return area.mouseScrolled(mouseX, mouseY, delta);
    }
}
