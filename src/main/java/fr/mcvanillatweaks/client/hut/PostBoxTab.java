package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.resolver.player.IPlayerRequestResolver;
import com.minecolonies.api.colony.requestsystem.resolver.retrying.IRetryingRequestResolver;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import com.minecolonies.core.network.messages.server.colony.OpenInventoryMessage;
import com.minecolonies.core.network.messages.server.colony.UpdateRequestStateMessage;
import com.minecolonies.core.network.messages.server.colony.building.postbox.PostBoxRequestMessage;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.IconButton;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Post box page (MineColonies' {@code WindowPostBoxMain}, the post box's own main window): every item in a creative
 * grid (MineColonies' list, the player's items included) with a search that also matches enchantments, then the chosen
 * item, the amount (MineColonies asks for a stack when the field is empty, 1000 at most) and "deliver what is available"
 * before sending {@code PostBoxRequestMessage}. Under the form, a summary of the open requests and a button to their own
 * view, which takes the whole page: the post box's open requests and their sub-requests, with MineColonies' cancel
 * ({@code UpdateRequestStateMessage}, top-level requests only). Each row says who handles it, the resolver MineColonies'
 * request details show: the player resolver (and the retrying one, which also waits for the player) in gold, since the
 * player has to bring the items.
 * <pre>
 * [Search...         ]   Request                 [< New request]   Open requests: 2
 * [][][][][][][][]  |    [slot] Oak Log          | (o) 64 Oak Log                       [x] |
 * [][][][][][][][]  |    Amount [64 ] [x] Partial|     By: Warehouse                        |
 * ...                    [Request]   [Inventory] | (o) 16 Diamond                       [x] |
 *                        Open: 2                 |     You have to bring it                 |
 *                        1 to bring yourself
 *                        [Open requests]
 * </pre>
 */
final class PostBoxTab extends HutTab
{
    private static final int LEFT_WIDTH = 168;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW = 24;
    private static final int CANCEL = 14;
    private static final int MAX_AMOUNT = 1000;
    /** Gold darkened for the light panel. */
    private static final int GOLD_ON_PANEL = 0xFFB86E00;

    /** An item of the grid with its lower-case names, computed once. */
    private record Entry(ItemStack stack, String name, String id, boolean inInventory)
    {
    }

    /** A request with who handles it; {@code player} when the player has to bring the items. */
    private record Row(IRequest<?> request, int depth, Component resolver, boolean player)
    {
    }

    /** Set by the dev preview: open requests without a request system (their resolvers come from the preview's manager). */
    @Nullable
    static List<IRequest<?>> previewRequests;

    private final AbstractBuildingView postBox;
    private final ItemGrid grid = new ItemGrid();
    private final ScrollArea requests = new ScrollArea();
    @Nullable
    private List<Entry> allItems;
    private List<Row> rows = List.of();
    /** Requests just cancelled, hidden until the server confirms. */
    private final Set<IToken<?>> cancelled = new HashSet<>();

    /** State kept across rebuilds. */
    private String filter = "";
    private String amount = "";
    private boolean partial;
    @Nullable
    private ItemStack picked;
    private int filterTimer;
    private int ticks;
    /** The open requests' own view instead of the request form. */
    private boolean showRequests;

    @Nullable
    private IconButton request;
    @Nullable
    private EditBox amountBox;

    PostBoxTab(final HutScreen screen, final AbstractBuildingView postBox)
    {
        super(screen);
        this.postBox = postBox;
        refreshRequests();
    }

    // ------------------------------------------------------------------ widgets

    private int rightX()
    {
        return x + LEFT_WIDTH + GAP;
    }

    private int rightWidth()
    {
        return width - LEFT_WIDTH - GAP;
    }

    @Override
    protected void init()
    {
        if (showRequests)
        {
            add(Button.builder(Component.translatable("mcvanillatweaks.postbox.back"), b -> {
                showRequests = false;
                screen.rebuild();
            }).bounds(x, y, 120, BUTTON_HEIGHT).build());
            request = null;
            amountBox = null;
            return;
        }
        final EditBox search = add(new EditBox(font, x + 1, y + 1, LEFT_WIDTH - 2, 16, Component.translatable("mcvanillatweaks.hut.search")));
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

        final int rx = rightX();
        final int rw = rightWidth();
        amountBox = add(new EditBox(font, rx + rw - 41, y + HEADING + 26, 40, 16, Component.translatable("mcvanillatweaks.postbox.amount")));
        amountBox.setMaxLength(4);
        amountBox.setFilter(text -> text.matches("\\d*"));
        amountBox.setValue(amount);
        updateAmountHint();
        amountBox.setResponder(text -> {
            amount = text;
            updateRequest();
        });
        add(Checkbox.builder(Component.empty(), font).pos(rx, y + HEADING + 46).selected(partial).onValueChange((box, value) -> partial = value).build());

        final int by = y + HEADING + 68;
        request = add(new IconButton(rx, by, rw - 24, BUTTON_HEIGHT, new ItemStack(Items.PAPER), Component.translatable("mcvanillatweaks.postbox.request"),
          b -> requestClicked()));
        // The post box's own inventory, as an icon: the chest is the hut's inventory everywhere.
        add(new IconButton(rx + rw - 22, by, 22, BUTTON_HEIGHT, new ItemStack(Items.CHEST), Component.empty(),
          b -> new OpenInventoryMessage(building()).sendToServer()))
          .setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("mcvanillatweaks.hut.inventory")));
        // The writable book is the requests' icon everywhere.
        add(new IconButton(rx, y + height - BUTTON_HEIGHT, rw, BUTTON_HEIGHT, new ItemStack(Items.WRITABLE_BOOK), Component.translatable("mcvanillatweaks.postbox.show"),
          b -> {
              showRequests = true;
              screen.rebuild();
          }));
        updateRequest();
        if (allItems == null)
        {
            refreshItems();
        }
    }

    /** The field shows MineColonies' default amount, one stack of the chosen item. */
    private void updateAmountHint()
    {
        if (amountBox != null)
        {
            amountBox.setHint(Component.literal(picked == null ? "64" : Integer.toString(picked.getMaxStackSize())).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private int amount()
    {
        if (picked == null)
        {
            return 0;
        }
        if (amount.isEmpty())
        {
            // MineColonies' default: one stack.
            return picked.getMaxStackSize();
        }
        try
        {
            return Math.min(MAX_AMOUNT, Integer.parseInt(amount));
        }
        catch (final NumberFormatException e)
        {
            return 0;
        }
    }

    private void updateRequest()
    {
        if (request != null)
        {
            request.active = picked != null && amount() > 0;
            // The button shows what it asks for.
            request.setIcon(picked != null ? picked : new ItemStack(Items.PAPER));
        }
    }

    private void requestClicked()
    {
        if (picked == null || amount() <= 0)
        {
            return;
        }
        new PostBoxRequestMessage(building(), picked.copy(), amount(), partial).sendToServer();
        ticks = 80;
    }

    @Override
    protected void tick()
    {
        if (filterTimer > 0 && --filterTimer == 0)
        {
            refreshItems();
        }
        // MineColonies refreshes its request list every 5 seconds; sooner after a request.
        if (++ticks >= 100)
        {
            ticks = 0;
            refreshRequests();
        }
    }

    // ------------------------------------------------------------------ items, as MineColonies' list

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

    /** MineColonies' filter (name, id, or an enchantment of a book); the player's items first, then by name or closeness. */
    private void refreshItems()
    {
        final String search = filter.toLowerCase(Locale.ROOT).trim();
        final List<Entry> shown = new ArrayList<>();
        for (final Entry entry : allItems())
        {
            if (search.isEmpty() || entry.name().contains(search) || entry.id().contains(search) || enchantmentMatches(entry.stack(), search))
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

    private static boolean enchantmentMatches(final ItemStack stack, final String search)
    {
        if (!(stack.getItem() instanceof EnchantedBookItem))
        {
            return false;
        }
        for (final var entry : stack.getTagEnchantments().entrySet())
        {
            if (entry.getKey().getRegisteredName().contains(search))
            {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ requests, as MineColonies' request tree

    private void refreshRequests()
    {
        final List<Row> list = new ArrayList<>();
        final IColonyView colony = building().getColony();
        try
        {
            for (final IRequest<?> request : previewRequests != null ? previewRequests : postBox.getOpenRequestsOfBuilding())
            {
                if (!cancelled.contains(request.getId()))
                {
                    addTree(colony, request, list, 0);
                }
            }
        }
        catch (final RuntimeException e)
        {
            // The request system has no data yet.
        }
        rows = list;
    }

    private static void addTree(@Nullable final IColonyView colony, final IRequest<?> request, final List<Row> list, final int depth)
    {
        IRequestResolver<?> resolver = null;
        Component name = Component.translatable("mcvanillatweaks.postbox.by_none");
        if (colony != null)
        {
            try
            {
                resolver = colony.getRequestManager().getResolverForRequest(request.getId());
                if (resolver != null)
                {
                    name = resolver.getRequesterDisplayName(colony.getRequestManager(), request);
                }
            }
            catch (final RuntimeException e)
            {
                // Not assigned yet.
            }
        }
        if (resolver instanceof IPlayerRequestResolver)
        {
            list.add(new Row(request, depth, Component.translatable("mcvanillatweaks.postbox.by_player"), true));
        }
        else if (resolver instanceof IRetryingRequestResolver)
        {
            list.add(new Row(request, depth, Component.translatable("mcvanillatweaks.postbox.by_retrying"), true));
        }
        else
        {
            list.add(new Row(request, depth, Component.translatable("mcvanillatweaks.postbox.by", name), false));
        }
        if (colony == null || !request.hasChildren())
        {
            return;
        }
        for (final IToken<?> token : request.getChildren())
        {
            final IRequest<?> child = colony.getRequestManager().getRequestForToken(token);
            if (child != null)
            {
                addTree(colony, child, list, depth + 1);
            }
        }
    }

    private void cancel(final IRequest<?> request)
    {
        final IColonyView colony = building().getColony();
        if (colony == null)
        {
            return;
        }
        new UpdateRequestStateMessage(colony, request.getId(), RequestState.CANCELLED, null).sendToServer();
        postBox.onRequestedRequestCancelled(colony.getRequestManager(), request);
        cancelled.add(request.getId());
        refreshRequests();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        if (showRequests)
        {
            renderRequests(g, mouseX, mouseY, tooltip);
        }
        else
        {
            renderForm(g, mouseX, mouseY, tooltip);
        }
    }

    private void renderForm(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final int top = y + 22;
        final int columns = ItemGrid.columnsFor(LEFT_WIDTH);
        grid.set(x, top, columns, (y + height - top) / ItemGrid.SLOT);
        grid.render(g, font, mouseX, mouseY, tooltip, stack -> picked != null && ItemStack.isSameItemSameComponents(stack, picked));
        if (grid.items().isEmpty())
        {
            g.drawString(font, Component.translatable("mcvanillatweaks.hut.search_none"), x + 4, top + 5, Gui.LABEL_SOFT, false);
        }

        final int rx = rightX();
        final int rw = rightWidth();
        heading(g, Component.translatable("mcvanillatweaks.postbox.title"), rx, y + 1, rw);
        // The chosen item.
        final int cy = y + HEADING + 2;
        Gui.slot(g, rx, cy);
        if (picked != null)
        {
            g.renderItem(picked, rx + 1, cy + 1);
            g.drawString(font, Gui.fit(font, picked.getHoverName(), rw - 24), rx + 22, cy + 5, Gui.LABEL, false);
            if (Gui.inside(mouseX, mouseY, rx, cy, 18, 18))
            {
                tooltip.set(picked);
            }
        }
        else
        {
            g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.postbox.pick"), rw - 24), rx + 22, cy + 5, Gui.LABEL_SOFT, false);
        }
        g.drawString(font, Component.translatable("mcvanillatweaks.postbox.amount"), rx + 1, y + HEADING + 30, Gui.LABEL, false);
        final int partialY = y + HEADING + 46;
        final Component partialLabel = Component.translatable("mcvanillatweaks.postbox.partial");
        g.drawString(font, Gui.fit(font, partialLabel, rw - 24), rx + 24, partialY + 6, Gui.LABEL, false);
        if (Gui.inside(mouseX, mouseY, rx, partialY, rw, 20))
        {
            tooltip.set(List.of(partialLabel, Component.translatable("mcvanillatweaks.postbox.partial_hint").withStyle(ChatFormatting.GRAY)));
        }
        if (request != null && request.isHovered() && picked != null)
        {
            tooltip.set(Component.translatable("mcvanillatweaks.postbox.request_hint", amount(), picked.getHoverName()));
        }

        // Summary of the open requests; the button under it opens their view.
        final int summaryTop = y + HEADING + 68 + BUTTON_HEIGHT + 8;
        heading(g, Component.translatable("mcvanillatweaks.postbox.open", openCount()), rx, summaryTop, rw);
        final long toBring = rows.stream().filter(Row::player).count();
        final Component summary = rows.isEmpty() ? Component.translatable("mcvanillatweaks.postbox.none")
                                    : toBring > 0 ? Component.translatable("mcvanillatweaks.postbox.to_bring", toBring)
                                    : Component.translatable("mcvanillatweaks.postbox.all_handled");
        int sy = summaryTop + HEADING + 2;
        for (final var line : font.split(summary, rw - 2))
        {
            if (sy + 9 > y + height - BUTTON_HEIGHT - 2)
            {
                break;
            }
            g.drawString(font, line, rx + 1, sy, toBring > 0 ? GOLD_ON_PANEL : Gui.LABEL_SOFT, false);
            sy += 10;
        }
    }

    /** Dev preview: the open requests' view. */
    void previewShowRequests()
    {
        showRequests = true;
        screen.rebuild();
    }

    private long openCount()
    {
        return rows.stream().filter(row -> row.depth() == 0).count();
    }

    /** The open requests over the whole page. */
    private void renderRequests(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        g.drawString(font, Component.translatable("mcvanillatweaks.postbox.open", openCount()), x + 128, y + 6, Gui.LABEL, false);
        final int boxTop = y + BUTTON_HEIGHT + 6;
        requests.set(x, boxTop, width, y + height - boxTop, rows.size() * ROW, ROW);
        requests.begin(g);
        if (rows.isEmpty())
        {
            g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.postbox.none"), requests.listWidth() - 8), requests.listX() + 4,
              requests.visibleTop() + 5, Gui.LIST_SOFT, true);
        }
        for (int i = 0; i < rows.size(); i++)
        {
            final int rowY = requests.listY() + i * ROW;
            if (rowY + ROW < requests.visibleTop() || rowY > requests.visibleBottom())
            {
                continue;
            }
            final Row row = rows.get(i);
            final boolean hovered = requests.hover(g, mouseX, mouseY, rowY, ROW);
            final int indent = Math.min(3, row.depth()) * 8;
            final List<ItemStack> stacks = row.request().getDisplayStacks();
            final ItemStack icon = stacks.isEmpty() ? ItemStack.EMPTY : stacks.get((int) (mc.level == null ? 0 : mc.level.getGameTime() / 30 % stacks.size()));
            g.renderItem(icon, requests.listX() + 2 + indent, rowY + 4);
            int textRight = requests.listX() + requests.listWidth() - 3;
            boolean overCancel = false;
            if (row.depth() == 0)
            {
                final int cx = textRight - CANCEL;
                final int cyy = rowY + (ROW - CANCEL) / 2;
                overCancel = requests.overRows(mouseX, mouseY) && Gui.inside(mouseX, mouseY, cx, cyy, CANCEL, CANCEL);
                Gui.textButton(g, font, cx, cyy, CANCEL, CANCEL, "✖", true, overCancel);
                textRight = cx - 4;
            }
            final int textX = requests.listX() + 21 + indent;
            final Component text = Component.literal(row.request().getShortDisplayString().getString().replace("§f", ""));
            g.drawString(font, Gui.fit(font, text, textRight - textX), textX, rowY + 3, row.depth() == 0 ? Gui.WHITE : Gui.LIST_SOFT, true);
            g.drawString(font, Gui.fit(font, row.resolver(), textRight - textX), textX, rowY + 13, row.player() ? Gui.GOLD : Gui.LIST_SOFT, true);
            if (overCancel)
            {
                tooltip.set(Component.translatable("com.minecolonies.coremod.gui.citizen.requestscancel"));
            }
            else if (hovered)
            {
                final List<Component> lines = new ArrayList<>();
                lines.add(text);
                lines.add(Component.literal(row.request().getLongDisplayString().getString().replace("§f", "")).withStyle(ChatFormatting.GRAY));
                lines.add(row.resolver().copy().withStyle(row.player() ? ChatFormatting.GOLD : ChatFormatting.GRAY));
                if (row.player())
                {
                    lines.add(Component.translatable("mcvanillatweaks.postbox.by_player_hint").withStyle(ChatFormatting.GRAY));
                }
                lines.addAll(row.request().getResolverToolTip(building().getColony()));
                tooltip.setAround(lines, mouseX, rowY, rowY + ROW);
            }
        }
        requests.end(g);
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (showRequests ? requests.mouseClicked(mouseX, mouseY) : grid.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button != 0)
        {
            return false;
        }
        final ItemStack stack = showRequests ? null : grid.itemAt(mouseX, mouseY);
        if (stack != null)
        {
            Gui.clickSound();
            picked = stack.copyWithCount(1);
            updateAmountHint();
            updateRequest();
            return true;
        }
        if (showRequests && requests.overRows(mouseX, mouseY))
        {
            final int index = (int) Math.floor((mouseY - requests.listY()) / ROW);
            if (index >= 0 && index < rows.size() && rows.get(index).depth() == 0)
            {
                final int rowY = requests.listY() + index * ROW;
                final int cx = requests.listX() + requests.listWidth() - 3 - CANCEL;
                if (Gui.inside(mouseX, mouseY, cx, rowY + (ROW - CANCEL) / 2, CANCEL, CANCEL))
                {
                    Gui.clickSound();
                    cancel(rows.get(index).request());
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected boolean mouseDragged(final double mouseX, final double mouseY)
    {
        return showRequests ? requests.mouseDragged(mouseY) : grid.mouseDragged(mouseY);
    }

    @Override
    protected void mouseReleased()
    {
        grid.mouseReleased();
        requests.mouseReleased();
    }

    @Override
    protected boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        return showRequests ? requests.mouseScrolled(mouseX, mouseY, delta) : grid.mouseScrolled(mouseX, mouseY, delta);
    }
}
