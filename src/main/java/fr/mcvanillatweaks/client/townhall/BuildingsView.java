package fr.mcvanillatweaks.client.townhall;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import fr.mcvanillatweaks.client.locate.LocateBeams;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The huts of a colony (the clipboard's huts page, the colony guide's list): every hut by name (same types together)
 * or closest first, with its level, distance and direction; a click puts a {@link LocateBeams} beam on it (and closes
 * the screen so the player sees it), or takes it away.
 * <pre>
 * | [hut] Guard Tower                  Lvl 2 · 54 m ↗  (o) |
 * | [hut] Guard Tower                  Lvl 1 · 120 m ←     |
 * </pre>
 */
final class BuildingsView
{
    private static final int ROW = 22;
    private static final int REFRESH_TICKS = 40;

    private record Row(IBuildingView building, Component name, ItemStack icon, String sortKey)
    {
    }

    private final Minecraft mc = Minecraft.getInstance();
    private final Font font = mc.font;
    private final IColonyView colony;
    private final ScrollArea list = new ScrollArea();
    private List<Row> rows = List.of();
    private String filter = "";
    private boolean byDistance;
    private int total;
    private int ticks;

    BuildingsView(final IColonyView colony)
    {
        this.colony = colony;
        refresh();
    }

    int total()
    {
        return total;
    }

    boolean byDistance()
    {
        return byDistance;
    }

    /** Closest first, or by name (huts of a type together). */
    void setByDistance(final boolean byDistance)
    {
        if (byDistance != this.byDistance)
        {
            this.byDistance = byDistance;
            refresh();
        }
    }

    void setFilter(final String filter)
    {
        if (!filter.equals(this.filter))
        {
            this.filter = filter;
            refresh();
        }
    }

    void tick()
    {
        if (++ticks % REFRESH_TICKS == 0)
        {
            refresh();
        }
    }

    private void refresh()
    {
        final String lower = filter.toLowerCase(Locale.ROOT);
        final List<Row> list = new ArrayList<>();
        int count = 0;
        for (final IBuildingView building : colony.getClientBuildingManager().getBuildings().values())
        {
            try
            {
                final Component name = LocateBeams.name(building);
                count++;
                final String text = name.getString();
                if (!lower.isEmpty() && !text.toLowerCase(Locale.ROOT).contains(lower))
                {
                    continue;
                }
                list.add(new Row(building, name, ColonyIcons.hut(building), text.toLowerCase(Locale.ROOT)));
            }
            catch (final LinkageError | RuntimeException e)
            {
                MineColoniesVanillaTweaks.LOGGER.debug("Hut left out of the clipboard list", e);
            }
        }
        if (byDistance)
        {
            list.sort(Comparator.comparingDouble((Row row) -> {
                final double distance = distance(row.building());
                return distance < 0 ? Double.MAX_VALUE : distance;
            }).thenComparing(Row::sortKey));
        }
        else
        {
            list.sort(Comparator.comparing(Row::sortKey).thenComparingDouble(row -> distance(row.building())));
        }
        rows = list;
        total = count;
    }

    /** Horizontal distance from the player, or -1 in another dimension. */
    private double distance(final IBuildingView building)
    {
        if (mc.player == null || mc.level == null || mc.level.dimension() != colony.getDimension())
        {
            return -1;
        }
        final Vec3 pos = Vec3.atBottomCenterOf(building.getID());
        return Math.sqrt(Math.pow(pos.x - mc.player.getX(), 2) + Math.pow(pos.z - mc.player.getZ(), 2));
    }

    // ------------------------------------------------------------------ drawing

    void render(final GuiGraphics g, final int x, final int y, final int width, final int height, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        list.set(x, y, width, height, rows.size() * ROW, ROW);
        list.begin(g);
        if (rows.isEmpty())
        {
            g.drawString(font, Gui.fit(font, Component.translatable(filter.isEmpty() ? "mcvanillatweaks.clipboard.buildings.none"
                                                                     : "mcvanillatweaks.clipboard.buildings.no_match"), list.listWidth() - 8),
              list.listX() + 4, list.visibleTop() + 6, Gui.LIST_SOFT, true);
        }
        for (int i = 0; i < rows.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final Row row = rows.get(i);
            final IBuildingView building = row.building();
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            final boolean located = LocateBeams.isLocated(building);
            g.renderItem(row.icon(), list.listX() + 3, rowY + 3);
            int right = list.listX() + list.listWidth() - 4;
            if (located)
            {
                g.renderItem(new ItemStack(Items.RECOVERY_COMPASS), right - 16, rowY + 3);
            }
            right -= 20;
            final double distance = distance(building);
            final Component level = building.getBuildingLevel() > 0
                                      ? Component.translatable("mcvanillatweaks.hut.level_short", building.getBuildingLevel())
                                      : Component.translatable("mcvanillatweaks.clipboard.buildings.unbuilt");
            final Component info = distance < 0 ? level
                                     : level.copy().append(Component.translatable("mcvanillatweaks.clipboard.buildings.distance", (int) distance))
                                         .append(" " + LocateBeams.arrow(Vec3.atBottomCenterOf(building.getID())));
            final int infoWidth = font.width(info);
            g.drawString(font, info, right - infoWidth, rowY + 7, Gui.LIST_SOFT, true);
            final int textX = list.listX() + 24;
            g.drawString(font, Gui.fit(font, row.name(), right - infoWidth - 8 - textX), textX, rowY + 7, located ? Gui.GOLD : Gui.WHITE, true);
            if (hovered)
            {
                final List<Component> lines = new ArrayList<>();
                lines.add(row.name());
                lines.add(Component.translatable("mcvanillatweaks.hut.level", building.getBuildingLevel(), building.getBuildingMaxLevel())
                            .withStyle(ChatFormatting.GRAY));
                lines.add(Component.literal(building.getID().toShortString()).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable(located ? "mcvanillatweaks.clipboard.buildings.click_stop" : "mcvanillatweaks.clipboard.buildings.click")
                            .withStyle(ChatFormatting.YELLOW));
                tooltip.setAround(lines, mouseX, rowY, rowY + ROW);
            }
        }
        list.end(g);
    }

    // ------------------------------------------------------------------ input

    boolean mouseClicked(final double mouseX, final double mouseY, final int button)
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
        if (index < 0 || index >= rows.size())
        {
            return false;
        }
        Gui.clickSound();
        if (LocateBeams.toggle(rows.get(index).building()))
        {
            mc.setScreen(null);
        }
        return true;
    }

    boolean mouseDragged(final double mouseY)
    {
        return list.mouseDragged(mouseY);
    }

    void mouseReleased()
    {
        list.mouseReleased();
    }

    boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        return list.mouseScrolled(mouseX, mouseY, delta);
    }
}
