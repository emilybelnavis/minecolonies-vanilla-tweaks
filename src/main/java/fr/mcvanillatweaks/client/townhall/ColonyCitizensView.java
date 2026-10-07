package fr.mcvanillatweaks.client.townhall;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.citizen.CitizenInfo;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import fr.mcvanillatweaks.client.locate.LocateBeams;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The clipboard's citizens page: every citizen of the colony by name, with the hut they work in, their job and their
 * distance (their entity when loaded, their last synced position otherwise); search by name or job. A click puts a
 * {@link LocateBeams} beam on the citizen (and closes the clipboard), or takes it away, like {@link BuildingsView}.
 * <pre>
 * | [hut] Alice Martin                  Farmer · 54 m  (o) |
 * </pre>
 */
final class ColonyCitizensView
{
    private static final int ROW = 22;
    private static final int REFRESH_TICKS = 20;

    private record Row(ICitizenDataView citizen, ItemStack icon, Component job)
    {
    }

    private final Minecraft mc = Minecraft.getInstance();
    private final Font font = mc.font;
    private final IColonyView colony;
    private final ScrollArea list = new ScrollArea();
    private List<Row> rows = List.of();
    private String filter = "";
    private int total;
    private int ticks;

    ColonyCitizensView(final IColonyView colony)
    {
        this.colony = colony;
        refresh();
    }

    int total()
    {
        return total;
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
        for (final ICitizenDataView citizen : colony.getCitizens().values())
        {
            try
            {
                count++;
                final Component jobName = CitizenInfo.jobName(citizen);
                final Component job = jobName == null ? Component.literal(String.valueOf(citizen.getJob())) : jobName;
                if (!lower.isEmpty() && !citizen.getName().toLowerCase(Locale.ROOT).contains(lower)
                      && !job.getString().toLowerCase(Locale.ROOT).contains(lower))
                {
                    continue;
                }
                final BlockPos work = citizen.getWorkBuilding();
                // The citizen's workplace, or the citizen icon when there is none.
                list.add(new Row(citizen, work == null || BlockPos.ZERO.equals(work) ? new ItemStack(Items.PLAYER_HEAD)
                                            : ColonyIcons.hut(colony, work), job));
            }
            catch (final LinkageError | RuntimeException e)
            {
                MineColoniesVanillaTweaks.LOGGER.debug("Citizen left out of the clipboard list", e);
            }
        }
        list.sort(Comparator.comparing(row -> row.citizen().getName().toLowerCase(Locale.ROOT)));
        rows = list;
        total = count;
    }

    /** Horizontal distance from the player, or -1 when unknown or in another dimension. */
    private double distance(final ICitizenDataView citizen)
    {
        if (mc.player == null || mc.level == null || mc.level.dimension() != colony.getDimension())
        {
            return -1;
        }
        final Vec3 pos = LocateBeams.position(citizen, 1.0F);
        return pos == null ? -1 : Math.sqrt(Math.pow(pos.x - mc.player.getX(), 2) + Math.pow(pos.z - mc.player.getZ(), 2));
    }

    // ------------------------------------------------------------------ drawing

    void render(final GuiGraphics g, final int x, final int y, final int width, final int height, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        list.set(x, y, width, height, rows.size() * ROW, ROW);
        list.begin(g);
        if (rows.isEmpty())
        {
            g.drawString(font, Gui.fit(font, Component.translatable(filter.isEmpty() ? "mcvanillatweaks.clipboard.citizens.none"
                                                                     : "mcvanillatweaks.clipboard.citizens.no_match"), list.listWidth() - 8),
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
            final ICitizenDataView citizen = row.citizen();
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            final boolean located = LocateBeams.isLocated(citizen);
            g.renderItem(row.icon(), list.listX() + 3, rowY + 3);
            int right = list.listX() + list.listWidth() - 4;
            if (located)
            {
                g.renderItem(new ItemStack(Items.RECOVERY_COMPASS), right - 16, rowY + 3);
            }
            right -= 20;
            final double distance = distance(citizen);
            final Component info = distance < 0 ? row.job()
                                     : row.job().copy().append(Component.translatable("mcvanillatweaks.clipboard.buildings.distance", (int) distance));
            final int textX = list.listX() + 24;
            final int infoWidth = Math.min(font.width(info), (right - textX) / 2);
            g.drawString(font, Gui.fit(font, info, infoWidth), right - infoWidth, rowY + 7, Gui.LIST_SOFT, true);
            g.drawString(font, Gui.fit(font, Component.literal(citizen.getName()), right - infoWidth - 8 - textX), textX, rowY + 7,
              located ? LocateBeams.CITIZEN_COLOUR : Gui.WHITE, true);
            if (hovered)
            {
                final List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(citizen.getName()));
                lines.add(row.job().copy().withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("mcvanillatweaks.clipboard.citizens.work",
                  CitizenInfo.buildingName(citizen, citizen.getWorkBuilding(), "mcvanillatweaks.citizen.no_work")).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("mcvanillatweaks.clipboard.citizens.home",
                  CitizenInfo.buildingName(citizen, citizen.getHomeBuilding(), "mcvanillatweaks.citizen.no_home")).withStyle(ChatFormatting.GRAY));
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
        if (LocateBeams.toggle(rows.get(index).citizen()))
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
