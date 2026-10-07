package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.connections.ColonyConnection;
import com.minecolonies.api.colony.connections.DiplomacyStatus;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.network.messages.server.colony.TeleportToColonyMessage;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The gatehouse's connected colonies (MineColonies' {@code ConnectionModuleWindow}), shared by its hut page
 * ({@link ConnectionsTab}) and the screen outsiders get ({@link ConnectionsScreen}): direct then indirect connections,
 * each with its distance and diplomacy, and a "travel" button (allied colonies with a known town hall only). Outsiders
 * pay one gold nugget per 125 blocks from the gatehouse, like MineColonies, and need more nuggets than that. A first
 * click arms the button, the second sends {@code TeleportToColonyMessage} (MineColonies asks with a confirmation).
 * <pre>
 * Direct connections:
 * [emerald] Colony B                       [Travel]
 *           240 blocks - Allies
 * </pre>
 */
public final class ConnectionsList
{
    private static final int ROW = 26;
    private static final int TITLE_ROW = 14;
    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BLOCKS_PER_NUGGET = 125;

    private record Row(@Nullable Component title, @Nullable ColonyConnection colony)
    {
    }

    private final IBuildingView building;
    private final boolean external;
    private final ScrollArea list = new ScrollArea();
    private final Font font = Minecraft.getInstance().font;
    private List<Row> rows = List.of();
    /** Colony whose travel button was clicked once (armed), or -1. */
    private int armed = -1;
    private int x;
    private int y;
    private int width;
    private int height;

    public ConnectionsList(final IBuildingView building, final boolean external)
    {
        this.building = building;
        this.external = external;
        refresh();
    }

    public void refresh()
    {
        final List<Row> out = new ArrayList<>();
        final IColonyView colony = building.getColony();
        if (colony != null)
        {
            final var manager = colony.getConnectionManager();
            section(out, "com.minecolonies.core.gui.colonylist.direct.desc", manager.getDirectlyConnectedColonies().values());
            section(out, "com.minecolonies.core.gui.colonylist.indirect.desc", manager.getIndirectlyConnectedColonies().values());
        }
        rows = List.copyOf(out);
    }

    /** A title, then the colonies or a "none" row (both fields null). */
    private static void section(final List<Row> out, final String title, final java.util.Collection<ColonyConnection> colonies)
    {
        out.add(new Row(Component.translatable(title), null));
        for (final ColonyConnection connection : colonies)
        {
            out.add(new Row(null, connection));
        }
        if (colonies.isEmpty())
        {
            out.add(new Row(null, null));
        }
    }

    public void layout(final int x, final int y, final int width, final int height)
    {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    private int rowHeight(final Row row)
    {
        return row.colony() == null ? TITLE_ROW : ROW;
    }

    private int cost(final ColonyConnection connection)
    {
        return external ? (int) BlockPosUtil.dist(connection.pos, building.getPosition()) / BLOCKS_PER_NUGGET : 0;
    }

    private static int nuggets()
    {
        final var player = Minecraft.getInstance().player;
        return player == null ? 0 : InventoryUtils.getItemCountInItemHandler(new InvWrapper(player.getInventory()), Items.GOLD_NUGGET);
    }

    /** Why the colony can't be visited, or null. */
    @Nullable
    private Component blocked(final ColonyConnection connection)
    {
        if (connection.diplomacyStatus != DiplomacyStatus.ALLIES || BlockPos.ZERO.equals(connection.pos))
        {
            return Component.translatable("com.minecolonies.core.gui.colonylist.travel.tooltip");
        }
        if (external && nuggets() <= cost(connection))
        {
            return Component.translatable("com.ldtteam.gatehouse.travel.cost");
        }
        return null;
    }

    private static ItemStack diplomacyIcon(final DiplomacyStatus status)
    {
        return new ItemStack(switch (status)
        {
            case ALLIES -> Items.EMERALD;
            case NEUTRAL -> Items.WHITE_BANNER;
            case HOSTILE -> Items.IRON_SWORD;
        });
    }

    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        int total = 0;
        for (final Row row : rows)
        {
            total += rowHeight(row);
        }
        list.set(x, y, width, height, total, ROW);
        list.begin(g);
        int rowY = list.listY();
        boolean any = false;
        for (int i = 0; i < rows.size(); i++)
        {
            final Row row = rows.get(i);
            final int h = rowHeight(row);
            if (rowY + h < list.visibleTop() || rowY > list.visibleBottom())
            {
                rowY += h;
                any |= row.colony() != null;
                continue;
            }
            if (row.title() != null)
            {
                g.drawString(font, Gui.fit(font, row.title(), list.listWidth() - 8), list.listX() + 4, rowY + 4, Gui.LIST_SOFT, true);
                rowY += h;
                continue;
            }
            if (row.colony() == null)
            {
                g.drawString(font, Component.translatable("mcvanillatweaks.hut.connections.none"), list.listX() + 12, rowY + 3, Gui.LIST_SOFT, true);
                rowY += h;
                continue;
            }
            any = true;
            final ColonyConnection connection = row.colony();
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, h);
            g.renderItem(diplomacyIcon(connection.diplomacyStatus), list.listX() + 3, rowY + 5);
            final int textX = list.listX() + 24;
            final int buttonX = list.listX() + list.listWidth() - 4 - BUTTON_WIDTH;
            final int textWidth = buttonX - 6 - textX;
            g.drawString(font, Gui.fit(font, Component.literal(connection.name), textWidth), textX, rowY + 3, Gui.WHITE, true);
            final int distance = (int) BlockPosUtil.dist(connection.pos, building.getColony().getCenter());
            final Component detail = Component.translatable("com.minecolonies.coremod.dist.blocks", distance).append(" - ")
                                       .append(Component.translatable(connection.diplomacyStatus.translationKey()));
            g.drawString(font, Gui.fit(font, detail, textWidth), textX, rowY + 14, Gui.LIST_SOFT, true);

            final Component why = blocked(connection);
            final int by = rowY + (h - BUTTON_HEIGHT) / 2;
            final boolean overButton = Gui.inside(mouseX, mouseY, buttonX, by, BUTTON_WIDTH, BUTTON_HEIGHT);
            final Component label = Component.translatable(armed == connection.id ? "mcvanillatweaks.hut.connections.confirm"
                                                             : "com.minecolonies.core.gui.colonylist.travel");
            Gui.textButton(g, font, buttonX, by, BUTTON_WIDTH, BUTTON_HEIGHT, label.getString(), why == null, overButton && why == null);
            if (overButton)
            {
                final List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable("com.minecolonies.core.gui.colonylist.travel.really", connection.name));
                if (external)
                {
                    lines.add(Component.translatable("mcvanillatweaks.hut.connections.cost", cost(connection), nuggets()).withStyle(ChatFormatting.GOLD));
                }
                if (why != null)
                {
                    lines.add(why.copy().withStyle(ChatFormatting.RED));
                }
                else
                {
                    lines.add(Component.translatable(armed == connection.id ? "mcvanillatweaks.hut.connections.click_again"
                                                       : "mcvanillatweaks.hut.connections.click").withStyle(ChatFormatting.YELLOW));
                }
                tooltip.set(lines);
            }
            else if (hovered && Gui.isCut(font, Component.literal(connection.name), textWidth))
            {
                tooltip.set(Component.literal(connection.name));
            }
            rowY += h;
        }
        list.end(g);
        if (!any)
        {
            // Nothing at all: MineColonies' message.
            int ty = list.visibleTop() + TITLE_ROW * 2 + 12;
            for (final var line : font.split(Component.translatable("com.minecolonies.core.gui.connectioneventlist.noconnections"), list.listWidth() - 16))
            {
                g.drawString(font, line, list.listX() + 8, ty, Gui.LIST_SOFT, true);
                ty += 10;
            }
        }
    }

    /** @return true when the click was used; {@code close} is run after a travel request. */
    public boolean mouseClicked(final double mouseX, final double mouseY, final Runnable close)
    {
        if (list.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (!list.overRows(mouseX, mouseY))
        {
            return false;
        }
        int rowY = list.listY();
        for (final Row row : rows)
        {
            final int h = rowHeight(row);
            if (row.colony() != null)
            {
                final int buttonX = list.listX() + list.listWidth() - 4 - BUTTON_WIDTH;
                final int by = rowY + (h - BUTTON_HEIGHT) / 2;
                if (Gui.inside(mouseX, mouseY, buttonX, by, BUTTON_WIDTH, BUTTON_HEIGHT))
                {
                    final ColonyConnection connection = row.colony();
                    if (blocked(connection) != null)
                    {
                        return true;
                    }
                    Gui.clickSound();
                    if (armed != connection.id)
                    {
                        armed = connection.id;
                        return true;
                    }
                    final Minecraft mc = Minecraft.getInstance();
                    if (mc.level != null)
                    {
                        new TeleportToColonyMessage(mc.level.dimension(), connection.id, connection.pos, building.getColony().getID(), cost(connection))
                          .sendToServer();
                    }
                    close.run();
                    return true;
                }
            }
            rowY += h;
        }
        armed = -1;
        return false;
    }

    public boolean mouseDragged(final double mouseY)
    {
        return list.mouseDragged(mouseY);
    }

    public void mouseReleased()
    {
        list.mouseReleased();
    }

    public boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        return list.mouseScrolled(mouseX, mouseY, delta);
    }
}
