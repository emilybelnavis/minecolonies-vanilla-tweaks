package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.managers.interfaces.IStatisticsManager;
import com.minecolonies.core.colony.buildings.moduleviews.BuildingStatisticsModuleView;
import fr.mcvanillatweaks.client.citizen.Dropdown;
import fr.mcvanillatweaks.client.citizen.DropdownButton;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import fr.mcvanillatweaks.client.townhall.ColonyIcons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Statistics tab (MineColonies' building statistics module): the building's statistics over the chosen period, zero
 * values hidden by default, computed like MineColonies' WindowStatsModule.
 */
final class StatsTab extends HutTab
{
    private static final int ROW = 18;
    private static final int ICON = 22;
    private static final String[] INTERVALS = {
      "com.minecolonies.coremod.gui.interval.yesterday", "com.minecolonies.coremod.gui.interval.lastweek",
      "com.minecolonies.coremod.gui.interval.100days", "com.minecolonies.coremod.gui.interval.alltime"};
    private static final int[] INTERVAL_DAYS = {1, 7, 100, -1};

    private record Stat(ItemStack icon, Component text)
    {
    }

    /** Remembered for the session, like MineColonies' window keeps its choices while it lives. */
    private static int interval;
    private static boolean hideZero = true;

    private final BuildingStatisticsModuleView module;
    private final ScrollArea list = new ScrollArea();
    private DropdownButton intervalButton;
    private List<Stat> stats = List.of();
    private int ticks;

    StatsTab(final HutScreen screen, final BuildingStatisticsModuleView module)
    {
        super(screen);
        this.module = module;
        refresh();
    }

    @Nullable
    private IStatisticsManager manager()
    {
        return module.getBuildingStatisticsManager();
    }

    @Override
    protected void init()
    {
        add(Checkbox.builder(Component.empty(), font).pos(x, y).selected(hideZero).onValueChange((box, value) -> {
            hideZero = value;
            refresh();
        }).build());
        intervalButton = dropdown(x + width - 130, y, 130, Component.translatable(INTERVALS[interval]),
          () -> Arrays.stream(INTERVALS).map(key -> new Dropdown.Entry(Component.translatable(key))).toList(), () -> interval, index -> {
              interval = index;
              intervalButton.setMessage(Component.translatable(INTERVALS[interval]));
              refresh();
          });
    }

    @Override
    protected void tick()
    {
        if (++ticks % 20 == 0)
        {
            refresh();
        }
    }

    private void refresh()
    {
        final IStatisticsManager manager = manager();
        if (manager == null)
        {
            stats = List.of();
            return;
        }
        final int days = INTERVAL_DAYS[interval];
        final int today = building().getColony().getDay();
        final List<Stat> list = new ArrayList<>();
        for (final String id : manager.getStatTypes())
        {
            final int value = days > 0 ? manager.getStatsInPeriod(id, today - days, today) : manager.getStatTotal(id);
            if (hideZero && value <= 0)
            {
                continue;
            }
            // "type;item" ids name what the statistic counted.
            final String[] split = id.split(";", 2);
            final Component text = split.length > 1
                                     ? Component.translatable("com.minecolonies.coremod.gui.townhall.stats." + split[0], value, Component.translatable(split[1]))
                                     : Component.translatable("com.minecolonies.coremod.gui.townhall.stats." + id, value);
            list.add(new Stat(split.length > 1 ? ColonyIcons.statItem(split[0], split[1]) : ColonyIcons.stat(split[0]), text));
        }
        stats = list;
    }

    @Override
    protected void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        g.drawString(font, Component.translatable("mcvanillatweaks.hut.hide_zero"), x + 22, y + 6, Gui.LABEL, false);
        final int top = y + 26;
        list.set(x, top, width, y + height - top, stats.size() * ROW + 2, ROW);
        list.begin(g);
        if (stats.isEmpty())
        {
            g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.hut.no_stats"), list.listWidth() - 8),
              list.listX() + 4, list.visibleTop() + 6, Gui.LIST_SOFT, true);
        }
        for (int i = 0; i < stats.size(); i++)
        {
            final int rowY = list.listY() + 1 + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final Stat stat = stats.get(i);
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            g.renderItem(stat.icon(), list.listX() + 2, rowY + 1);
            final int textWidth = list.listWidth() - ICON - 4;
            g.drawString(font, Gui.fit(font, stat.text(), textWidth), list.listX() + ICON, rowY + 5, Gui.WHITE, true);
            if (hovered && Gui.isCut(font, stat.text(), textWidth))
            {
                tooltip.set(stat.text());
            }
        }
        list.end(g);
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
