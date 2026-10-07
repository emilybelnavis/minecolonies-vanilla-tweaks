package fr.mcvanillatweaks.client.townhall;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.connections.ColonyConnection;
import com.minecolonies.api.colony.connections.ConnectionEvent;
import com.minecolonies.api.colony.connections.ConnectionEventType;
import com.minecolonies.api.colony.connections.DiplomacyStatus;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.network.messages.server.colony.TriggerConnectionEventMessage;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * "Alliances" tab.
 * <pre>
 * [# Allies 1] [# Neutral 2] [# Hostile 0]
 * Connected colonies                 Connection events
 * +-------------------------------+  +-----------------+
 * | Direct connections            |  | # Colony B      |
 * | # Colony B         [E] [S]    |  |   asks...  [E]  |
 * |   120 blocks - Neutral        |  |                 |
 * +-------------------------------+  +-----------------+
 * </pre>
 * Diplomacy actions are square item buttons (emerald: ask for an alliance or accept it, sword: start a feud, white
 * banner: back to neutral) sending MineColonies' connection events. Without any connection, the steps to create one.
 */
final class AlliancesTab extends TownHallTab
{
    private static final int TILE_HEIGHT = 36;
    private static final int COLONY_ROW = 26;
    private static final int TITLE_ROW = 14;
    private static final int EVENT_ROW = 26;
    private static final int STEP_ROW = 36;
    private static final int ICON_BUTTON = 18;
    private static final int EVENTS_WIDTH = 124;

    private static final ItemStack ALLIES = new ItemStack(Items.EMERALD);
    private static final ItemStack NEUTRAL = new ItemStack(Items.WHITE_BANNER);
    private static final ItemStack HOSTILE = new ItemStack(Items.IRON_SWORD);
    private static final ItemStack REQUEST = new ItemStack(Items.PAPER);
    private static final ItemStack DISCONNECTED = new ItemStack(Items.BARRIER);

    /** A line of the colony list: a section title or a connected colony. */
    private record Row(@Nullable Component title, @Nullable ColonyConnection colony)
    {
    }

    /** A clickable diplomacy button, recorded while drawing. */
    private record Action(int bx, int by, Runnable run, String buttonId)
    {
    }

    private final ScrollArea colonies = new ScrollArea();
    private final ScrollArea events = new ScrollArea();
    private final List<Action> actions = new ArrayList<>();

    AlliancesTab(final TownHallScreen screen)
    {
        super(screen);
    }

    private boolean hasConnections()
    {
        final IColonyView colony = colony();
        return !colony.getConnectionManager().getConnectionEvents().isEmpty() || !colony.getConnectionManager().getDirectlyConnectedColonies().isEmpty();
    }

    private List<Row> rows()
    {
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(Component.translatable("com.minecolonies.core.gui.colonylist.direct.desc"), null));
        colony().getConnectionManager().getDirectlyConnectedColonies().values().forEach(c -> rows.add(new Row(null, c)));
        final Collection<ColonyConnection> indirect = colony().getConnectionManager().getIndirectlyConnectedColonies().values();
        if (!indirect.isEmpty())
        {
            rows.add(new Row(Component.translatable("com.minecolonies.core.gui.colonylist.indirect.desc"), null));
            indirect.forEach(c -> rows.add(new Row(null, c)));
        }
        return rows;
    }

    private static int rowHeight(final Row row)
    {
        return row.title() != null ? TITLE_ROW : COLONY_ROW;
    }

    private static ItemStack statusIcon(final DiplomacyStatus status)
    {
        return switch (status)
        {
            case ALLIES -> ALLIES;
            case HOSTILE -> HOSTILE;
            default -> NEUTRAL;
        };
    }

    private static ChatFormatting statusColour(final DiplomacyStatus status)
    {
        return switch (status)
        {
            case ALLIES -> ChatFormatting.GREEN;
            case HOSTILE -> ChatFormatting.RED;
            default -> ChatFormatting.GRAY;
        };
    }

    private static ItemStack eventIcon(final ConnectionEventType type)
    {
        return switch (type)
        {
            case ALLY_REQUEST -> REQUEST;
            case ALLY_CONFIRMED -> ALLIES;
            case FEUD_STARTED -> HOSTILE;
            case NEUTRAL_SET -> NEUTRAL;
            default -> DISCONNECTED;
        };
    }

    @Override
    void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        actions.clear();
        renderSummary(g, mouseX, mouseY, tooltip);
        if (!hasConnections())
        {
            renderHowTo(g);
            return;
        }
        renderColonies(g, mouseX, mouseY, tooltip);
        renderEvents(g, mouseX, mouseY, tooltip);
    }

    private int bodyY()
    {
        return y + TILE_HEIGHT + 8;
    }

    // ------------------------------------------------------------------ summary

    /** Number of connected colonies (direct and indirect) by diplomacy status. */
    private void renderSummary(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final int[] counts = new int[DiplomacyStatus.values().length];
        final List<ColonyConnection> all = new ArrayList<>(colony().getConnectionManager().getDirectlyConnectedColonies().values());
        all.addAll(colony().getConnectionManager().getIndirectlyConnectedColonies().values());
        for (final ColonyConnection connection : all)
        {
            counts[connection.diplomacyStatus.ordinal()]++;
        }
        final int tileWidth = (width - 2 * 6) / 3;
        int tx = x;
        for (final DiplomacyStatus status : new DiplomacyStatus[] {DiplomacyStatus.ALLIES, DiplomacyStatus.NEUTRAL, DiplomacyStatus.HOSTILE})
        {
            final int tw = status == DiplomacyStatus.HOSTILE ? x + width - tx : tileWidth;
            final Component label = Component.translatable("mcvanillatweaks.townhall.alliances." + status.name().toLowerCase(java.util.Locale.ROOT));
            Gui.inset(g, tx, y, tw, TILE_HEIGHT);
            if (Gui.inside(mouseX, mouseY, tx, y, tw, TILE_HEIGHT))
            {
                g.fill(tx + 1, y + 1, tx + tw - 1, y + TILE_HEIGHT - 1, Gui.ROW_HOVER);
                tooltip.set(List.of(label.copy().withStyle(statusColour(status)), names(all, status)));
            }
            g.renderItem(statusIcon(status), tx + 6, y + 10);
            final String count = Integer.toString(counts[status.ordinal()]);
            g.pose().pushPose();
            g.pose().translate(tx + tw - 8 - font.width(count) * 2, y + 6, 0);
            g.pose().scale(2.0F, 2.0F, 1.0F);
            g.drawString(font, count, 0, 0, counts[status.ordinal()] == 0 ? Gui.LIST_SOFT : 0xFF000000 | statusColour(status).getColor(), true);
            g.pose().popPose();
            g.drawString(font, Gui.fit(font, label, tw - 34), tx + 26, y + 14, Gui.WHITE, true);
            tx += tw + 6;
        }
    }

    private static Component names(final List<ColonyConnection> all, final DiplomacyStatus status)
    {
        final List<String> names = all.stream().filter(c -> c.diplomacyStatus == status).map(c -> c.name).toList();
        return names.isEmpty()
                 ? Component.translatable("mcvanillatweaks.townhall.alliances.none").withStyle(ChatFormatting.GRAY)
                 : Component.literal(String.join(", ", names)).withStyle(ChatFormatting.GRAY);
    }

    // ------------------------------------------------------------------ how to

    /** The three steps to connect two colonies, each with its item. */
    private void renderHowTo(final GuiGraphics g)
    {
        final int top = bodyY();
        heading(g, Component.translatable("mcvanillatweaks.townhall.alliances.howto"), x, top, width);
        final int listTop = top + HEADING;
        Gui.inset(g, x, listTop, width, y + height - listTop);
        final ItemStack[] icons = {gatehouse(), new ItemStack(Items.WHITE_BANNER), new ItemStack(Items.OAK_SIGN)};
        for (int i = 0; i < icons.length; i++)
        {
            final int rowY = listTop + 4 + i * STEP_ROW;
            Gui.slot(g, x + 6, rowY + (STEP_ROW - 18) / 2 - 2);
            g.renderItem(icons[i], x + 7, rowY + (STEP_ROW - 18) / 2 - 1);
            final List<FormattedCharSequence> lines = font.split(Component.translatable("mcvanillatweaks.townhall.alliances.step" + (i + 1)), width - 40);
            int ly = rowY + (STEP_ROW - lines.size() * 10) / 2 - 2;
            for (final FormattedCharSequence line : lines)
            {
                g.drawString(font, line, x + 32, ly, Gui.WHITE, true);
                ly += 10;
            }
        }
    }

    private static ItemStack gatehouse()
    {
        try
        {
            return new ItemStack(ModBlocks.blockHutGateHouse);
        }
        catch (final LinkageError | RuntimeException e)
        {
            return new ItemStack(Items.OAK_FENCE_GATE);
        }
    }

    // ------------------------------------------------------------------ colonies

    private int coloniesWidth()
    {
        return width - EVENTS_WIDTH - GAP;
    }

    private void renderColonies(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final List<Row> rows = rows();
        int content = 0;
        for (final Row row : rows)
        {
            content += rowHeight(row);
        }
        final int top = bodyY();
        heading(g, Component.translatable("mcvanillatweaks.townhall.connected_colonies"), x, top, coloniesWidth());
        colonies.set(x, top + HEADING, coloniesWidth(), y + height - top - HEADING, content, TITLE_ROW);
        colonies.begin(g);
        int rowY = colonies.listY();
        for (final Row row : rows)
        {
            final int h = rowHeight(row);
            if (rowY + h >= colonies.visibleTop() && rowY <= colonies.visibleBottom())
            {
                if (row.title() != null)
                {
                    g.drawString(font, Gui.fit(font, row.title(), colonies.listWidth() - 8), colonies.listX() + 4, rowY + 4, Gui.GOLD, true);
                }
                else
                {
                    colonies.hover(g, mouseX, mouseY, rowY, h);
                    renderColony(g, row.colony(), rowY, mouseX, mouseY, tooltip);
                }
            }
            rowY += h;
        }
        if (rows.size() == 1)
        {
            g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.townhall.no_connections"), colonies.listWidth() - 16),
              colonies.listX() + 8, colonies.listY() + TITLE_ROW + 3, Gui.LIST_SOFT, true);
        }
        colonies.end(g);
    }

    private void renderColony(final GuiGraphics g, final ColonyConnection connection, final int rowY, final int mouseX, final int mouseY,
      final Tooltip tooltip)
    {
        final boolean over = colonies.overRows(mouseX, mouseY);
        final int right = colonies.listX() + colonies.listWidth() - 3;
        final int by = rowY + (COLONY_ROW - ICON_BUTTON) / 2;
        final int buttons;
        if (connection.diplomacyStatus == DiplomacyStatus.NEUTRAL)
        {
            iconButton(g, right - 2 * ICON_BUTTON - 2, by, ALLIES, "com.minecolonies.core.gui.colonylist.request_ally", over, mouseX, mouseY, tooltip,
              () -> send(ConnectionEventType.ALLY_REQUEST, connection.id), "requestally");
            iconButton(g, right - ICON_BUTTON, by, HOSTILE, "com.minecolonies.core.gui.colonylist.start_feud", over, mouseX, mouseY, tooltip,
              () -> send(ConnectionEventType.FEUD_STARTED, connection.id), "startfeud");
            buttons = 2 * ICON_BUTTON + 6;
        }
        else
        {
            iconButton(g, right - ICON_BUTTON, by, NEUTRAL, "com.minecolonies.core.gui.colonylist.set_neutral", over, mouseX, mouseY, tooltip,
              () -> send(ConnectionEventType.NEUTRAL_SET, connection.id), "setneutral");
            buttons = ICON_BUTTON + 4;
        }

        g.renderItem(statusIcon(connection.diplomacyStatus), colonies.listX() + 4, rowY + 5);
        final int lx = colonies.listX() + 24;
        final int textWidth = right - buttons - lx;
        g.drawString(font, Gui.fit(font, Component.literal(connection.name), textWidth), lx, rowY + 4, Gui.WHITE, true);
        final Component detail = Component.translatable("com.minecolonies.coremod.dist.blocks", (int) BlockPosUtil.dist(connection.pos, colony().getCenter()))
                                   .append(" · ")
                                   .append(Component.translatable("mcvanillatweaks.townhall.alliances.status." + connection.diplomacyStatus.name().toLowerCase(java.util.Locale.ROOT))
                                             .withStyle(statusColour(connection.diplomacyStatus)));
        g.drawString(font, Gui.fit(font, detail, textWidth), lx, rowY + 14, Gui.LIST_SOFT, true);
    }

    // ------------------------------------------------------------------ events

    private void renderEvents(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final int ex = x + coloniesWidth() + GAP;
        final int top = bodyY();
        heading(g, Component.translatable("com.minecolonies.core.gui.connectioneventlist.desc"), ex, top, EVENTS_WIDTH);
        final List<ConnectionEvent> list = colony().getConnectionManager().getConnectionEvents();
        events.set(ex, top + HEADING, EVENTS_WIDTH, y + height - top - HEADING, list.size() * EVENT_ROW, EVENT_ROW);
        events.begin(g);
        if (list.isEmpty())
        {
            g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.townhall.alliances.no_events"), events.listWidth() - 8),
              events.listX() + 4, events.visibleTop() + 6, Gui.LIST_SOFT, true);
        }
        final boolean over = events.overRows(mouseX, mouseY);
        for (int i = 0; i < list.size(); i++)
        {
            final int rowY = events.listY() + i * EVENT_ROW;
            if (rowY + EVENT_ROW < events.visibleTop() || rowY > events.visibleBottom())
            {
                continue;
            }
            // Most recent first, like MineColonies.
            final ConnectionEvent event = list.get(list.size() - 1 - i);
            final boolean hovered = events.hover(g, mouseX, mouseY, rowY, EVENT_ROW);
            final boolean canAccept = event.connectionEventType() == ConnectionEventType.ALLY_REQUEST
                                        && colony().getConnectionManager().getColonyDiplomacyStatus(event.id()) != DiplomacyStatus.ALLIES;
            final int right = events.listX() + events.listWidth() - 3;
            if (canAccept)
            {
                iconButton(g, right - ICON_BUTTON, rowY + (EVENT_ROW - ICON_BUTTON) / 2, ALLIES, "com.minecolonies.core.gui.connectioneventlist.accept_ally",
                  over, mouseX, mouseY, tooltip, () -> send(ConnectionEventType.ALLY_CONFIRMED, event.id()), "acceptally");
            }
            g.renderItem(eventIcon(event.connectionEventType()), events.listX() + 3, rowY + 5);
            final int lx = events.listX() + 22;
            final int textWidth = (canAccept ? right - ICON_BUTTON - 4 : right) - lx;
            final Component name = Component.literal(event.name());
            final Component desc = Component.translatable(event.connectionEventType().translationKey());
            g.drawString(font, Gui.fit(font, name, textWidth), lx, rowY + 4, Gui.WHITE, true);
            g.drawString(font, Gui.fit(font, desc, textWidth), lx, rowY + 14, Gui.LIST_SOFT, true);
            if (hovered && tooltip.isEmpty() && (Gui.isCut(font, desc, textWidth) || Gui.isCut(font, name, textWidth)))
            {
                tooltip.setAround(List.of(name, desc.copy().withStyle(ChatFormatting.GRAY)), mouseX, rowY, rowY + EVENT_ROW);
            }
        }
        events.end(g);
    }

    /** A square vanilla button showing an item; its label is the tooltip. */
    private void iconButton(final GuiGraphics g, final int bx, final int by, final ItemStack icon, final String key, final boolean over, final int mouseX,
      final int mouseY, final Tooltip tooltip, final Runnable run, final String buttonId)
    {
        final boolean hovered = over && Gui.inside(mouseX, mouseY, bx, by, ICON_BUTTON, ICON_BUTTON);
        Gui.button(g, bx, by, ICON_BUTTON, ICON_BUTTON, true, hovered);
        g.renderItem(icon, bx + 1, by + 1);
        if (hovered)
        {
            tooltip.set(Component.translatable(key));
        }
        actions.add(new Action(bx, by, run, buttonId));
    }

    /** The event MineColonies sends for its diplomacy buttons, from this colony to the other one. */
    private void send(final ConnectionEventType type, final int otherColony)
    {
        final IColonyView colony = colony();
        new TriggerConnectionEventMessage(colony, new ConnectionEvent(colony.getID(), colony.getName(), type), otherColony).sendToServer();
    }

    @Override
    boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (colonies.mouseClicked(mouseX, mouseY) || events.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button != 0)
        {
            return false;
        }
        for (final Action action : actions)
        {
            if (Gui.inside(mouseX, mouseY, action.bx(), action.by(), ICON_BUTTON, ICON_BUTTON)
                  && (colonies.overRows(mouseX, mouseY) || events.overRows(mouseX, mouseY)))
            {
                Gui.clickSound();
                screen.trigger(action.buttonId());
                action.run().run();
                return true;
            }
        }
        return false;
    }

    @Override
    boolean mouseDragged(final double mouseX, final double mouseY)
    {
        return colonies.mouseDragged(mouseY) || events.mouseDragged(mouseY);
    }

    @Override
    void mouseReleased()
    {
        colonies.mouseReleased();
        events.mouseReleased();
    }

    @Override
    boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        return colonies.mouseScrolled(mouseX, mouseY, delta) || events.mouseScrolled(mouseX, mouseY, delta);
    }
}
