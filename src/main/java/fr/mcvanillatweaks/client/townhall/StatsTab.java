package fr.mcvanillatweaks.client.townhall;

import com.minecolonies.api.MinecoloniesAPIProxy;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.research.util.ResearchConstants;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.colony.buildings.moduleviews.CombinedHiringLimitModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import fr.mcvanillatweaks.client.citizen.Dropdown;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * "Statistics" tab: population (citizens against the colony's limit, workers per job, unemployed, children) on the left,
 * the colony's statistics over the chosen period on the right. Everything is computed like MineColonies' statistics
 * page.
 */
final class StatsTab extends TownHallTab
{
    private static final int ROW = 18;
    /** Room for the 16 px item icon on the left of the rows. */
    private static final int ICON = 22;
    private static final int REFRESH_TICKS = 20;
    /** MineColonies' periods: translation key and number of days (-1: since the colony was founded). */
    private static final String[] INTERVALS = {
      "com.minecolonies.coremod.gui.interval.yesterday", "com.minecolonies.coremod.gui.interval.lastweek",
      "com.minecolonies.coremod.gui.interval.100days", "com.minecolonies.coremod.gui.interval.alltime"};
    private static final int[] INTERVAL_DAYS = {1, 7, 100, -1};

    /** One line of the population list: an icon (empty for none), a label and a count on the right. */
    private record Line(ItemStack icon, Component label, String count)
    {
    }

    private record Stat(ItemStack icon, Component text)
    {
    }

    private static final ItemStack UNEMPLOYED = new ItemStack(Items.WOODEN_HOE);
    private static final ItemStack CHILDREN = new ItemStack(Items.EGG);

    private final ScrollArea population = new ScrollArea();
    private final ScrollArea stats = new ScrollArea();
    private List<Line> lines = List.of();
    private List<Stat> statLines = List.of();
    private Component total = Component.empty();
    private int totalColour = Gui.GREEN;
    private List<Component> totalTooltip = List.of();
    /** Remembered for the session, like MineColonies' drop-down keeps its choice while the window lives. */
    private static int interval;
    private Button intervalButton;
    private int ticks;

    StatsTab(final TownHallScreen screen)
    {
        super(screen);
        refresh();
    }

    private int columnWidth()
    {
        return (width - GAP) / 2;
    }

    private int rightX()
    {
        return x + columnWidth() + GAP;
    }

    @Override
    void init()
    {
        intervalButton = dropdown(rightX(), y + HEADING, columnWidth(), Component.translatable(INTERVALS[interval]),
          () -> java.util.Arrays.stream(INTERVALS).map(key -> new Dropdown.Entry(Component.translatable(key))).toList(), () -> interval, this::pickInterval);
    }

    private void pickInterval(final int index)
    {
        interval = index;
        intervalButton.setMessage(Component.translatable(INTERVALS[interval]));
        screen.trigger("intervals");
        refresh();
    }

    @Override
    void tick()
    {
        if (++ticks % REFRESH_TICKS == 0)
        {
            refresh();
        }
    }

    private void refresh()
    {
        refreshPopulation();
        refreshStats();
    }

    private void refreshPopulation()
    {
        final IColonyView colony = colony();
        final int citizens = colony.getCitizens().size();
        final int configMax = configMax(colony);
        int cap = configMax;
        if (MinecoloniesAPIProxy.getInstance().getGlobalResearchTree().hasResearchEffect(ResearchConstants.CITIZEN_CAP))
        {
            final int max = Math.max(25, (int) colony.getResearchManager().getResearchEffects().getEffectStrength(ResearchConstants.CITIZEN_CAP));
            cap = Math.min(max, configMax);
        }
        final List<Component> hover = new ArrayList<>();
        if (citizens < cap * 0.9D && citizens < colony.getCitizenCountLimit() * 0.9D)
        {
            total = Component.translatable("com.minecolonies.coremod.gui.townhall.population.totalcitizens.count", citizens,
              Math.max(citizens, colony.getCitizenCountLimit()));
            totalColour = Gui.GREEN;
        }
        else if (citizens < cap)
        {
            total = Component.translatable("com.minecolonies.coremod.gui.townhall.population.totalcitizens.count", citizens,
              Math.max(citizens, colony.getCitizenCountLimit()));
            hover.add(Component.translatable("com.minecolonies.coremod.gui.townhall.population.totalcitizens.houselimited", colony.getName()));
            totalColour = Gui.GOLD;
        }
        else
        {
            hover.add(Component.translatable(cap < configMax
                                               ? "com.minecolonies.coremod.gui.townhall.population.totalcitizens.researchlimited"
                                               : "com.minecolonies.coremod.gui.townhall.population.totalcitizens.configlimited", colony.getName()));
            total = Component.translatable("com.minecolonies.coremod.gui.townhall.population.totalcitizens.count", citizens, cap);
            totalColour = Gui.RED;
        }
        totalTooltip = hover;

        // Workers per job, as MineColonies counts them (combined hiring limits share their places).
        final Map<String, int[]> jobs = new TreeMap<>();
        final Map<String, ItemStack> huts = new HashMap<>();
        for (final IBuildingView building : colony.getClientBuildingManager().getBuildings().values())
        {
            if (!(building instanceof AbstractBuildingView))
            {
                continue;
            }
            for (final WorkerBuildingModuleView module : building.getModuleViews(WorkerBuildingModuleView.class))
            {
                int max = module.getMaxInhabitants();
                if (module instanceof CombinedHiringLimitModuleView)
                {
                    int assigned = 0;
                    for (final WorkerBuildingModuleView combined : building.getModuleViews(WorkerBuildingModuleView.class))
                    {
                        assigned += combined.getAssignedCitizens().size();
                    }
                    max = max - assigned + module.getAssignedCitizens().size();
                }
                final String job = module.getJobDisplayName().toLowerCase(Locale.US);
                huts.putIfAbsent(job, ColonyIcons.hut(building));
                final int[] counts = jobs.computeIfAbsent(job, key -> new int[2]);
                counts[0] += module.getAssignedCitizens().size();
                counts[1] += max;
            }
        }
        int unemployed = 0;
        int children = 0;
        for (final ICitizenDataView citizen : colony.getCitizens().values())
        {
            if (citizen.isChild())
            {
                children++;
            }
            else if (citizen.getJobView() == null)
            {
                unemployed++;
            }
        }
        final List<Line> list = new ArrayList<>();
        for (final Map.Entry<String, int[]> entry : jobs.entrySet())
        {
            final String job = Component.translatable(entry.getKey()).getString();
            final String label = job.isEmpty() ? job : job.substring(0, 1).toUpperCase(Locale.ROOT) + job.substring(1);
            list.add(new Line(huts.getOrDefault(entry.getKey(), ItemStack.EMPTY), Component.literal(label), entry.getValue()[0] + "/" + entry.getValue()[1]));
        }
        list.add(new Line(UNEMPLOYED, Component.translatable("mcvanillatweaks.townhall.unemployed"), Integer.toString(unemployed)));
        list.add(new Line(CHILDREN, Component.translatable("mcvanillatweaks.townhall.children"), Integer.toString(children)));
        lines = list;
    }

    private static int configMax(final IColonyView colony)
    {
        try
        {
            return MineColonies.getConfig().getServer().maxCitizenPerColony.get();
        }
        catch (final IllegalStateException | NullPointerException e)
        {
            // server configuration not received
            return Math.max(colony.getCitizens().size(), colony.getCitizenCountLimit());
        }
    }

    private void refreshStats()
    {
        final IColonyView colony = colony();
        final List<Stat> list = new ArrayList<>();
        final int days = INTERVAL_DAYS[interval];
        for (final String type : colony.getStatisticsManager().getStatTypes())
        {
            final int value = days > 0
                                ? colony.getStatisticsManager().getStatsInPeriod(type, colony.getDay() - days, colony.getDay())
                                : colony.getStatisticsManager().getStatTotal(type);
            list.add(new Stat(ColonyIcons.stat(type), Component.translatable("com.minecolonies.coremod.gui.townhall.stats." + type, value)));
        }
        statLines = list;
    }

    @Override
    void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final int cw = columnWidth();
        heading(g, Component.translatable("com.minecolonies.coremod.gui.townhall.population.totalcitizens"), x, y, cw - 40);
        g.drawString(font, total, x + cw - 1 - font.width(total), y, darker(totalColour), false);
        if (!totalTooltip.isEmpty() && Gui.inside(mouseX, mouseY, x, y - 1, cw, 10))
        {
            tooltip.set(totalTooltip);
        }

        final int listTop = y + HEADING;
        population.set(x, listTop, cw, y + height - listTop, lines.size() * ROW + 2, ROW);
        population.begin(g);
        for (int i = 0; i < lines.size(); i++)
        {
            final int rowY = population.listY() + 1 + i * ROW;
            if (rowY + ROW < population.visibleTop() || rowY > population.visibleBottom())
            {
                continue;
            }
            population.hover(g, mouseX, mouseY, rowY, ROW);
            final Line line = lines.get(i);
            final boolean summary = i >= lines.size() - 2;
            final int countX = population.listX() + population.listWidth() - 4 - font.width(line.count());
            g.renderItem(line.icon(), population.listX() + 2, rowY + 1);
            g.drawString(font, Gui.fit(font, line.label(), countX - population.listX() - ICON - 6), population.listX() + ICON, rowY + 5,
              summary ? Gui.LIST_SOFT : Gui.WHITE, true);
            g.drawString(font, line.count(), countX, rowY + 5, summary ? Gui.LIST_SOFT : Gui.WHITE, true);
        }
        population.end(g);

        heading(g, Component.translatable("mcvanillatweaks.townhall.statistics"), rightX(), y, cw);
        final int statsTop = y + HEADING + 24;
        stats.set(rightX(), statsTop, cw, y + height - statsTop, statLines.size() * ROW + 2, ROW);
        stats.begin(g);
        for (int i = 0; i < statLines.size(); i++)
        {
            final int rowY = stats.listY() + 1 + i * ROW;
            if (rowY + ROW < stats.visibleTop() || rowY > stats.visibleBottom())
            {
                continue;
            }
            stats.hover(g, mouseX, mouseY, rowY, ROW);
            final Stat stat = statLines.get(i);
            final Component text = stat.text();
            final int textWidth = stats.listWidth() - ICON - 4;
            g.renderItem(stat.icon(), stats.listX() + 2, rowY + 1);
            g.drawString(font, Gui.fit(font, text, textWidth), stats.listX() + ICON, rowY + 5, Gui.WHITE, true);
            if (Gui.isCut(font, text, textWidth) && stats.overRows(mouseX, mouseY)
                  && Gui.inside(mouseX, mouseY, stats.listX(), rowY, stats.listWidth(), ROW))
            {
                tooltip.set(text);
            }
        }
        stats.end(g);
    }

    /** MineColonies' green / orange / red, dark enough to read on the light panel. */
    private static int darker(final int colour)
    {
        return colour == Gui.GREEN ? 0xFF00AA00 : colour == Gui.GOLD ? 0xFFCC7700 : 0xFFAA0000;
    }

    @Override
    boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        return population.mouseClicked(mouseX, mouseY) || stats.mouseClicked(mouseX, mouseY);
    }

    @Override
    boolean mouseDragged(final double mouseX, final double mouseY)
    {
        return population.mouseDragged(mouseY) || stats.mouseDragged(mouseY);
    }

    @Override
    void mouseReleased()
    {
        population.mouseReleased();
        stats.mouseReleased();
    }

    @Override
    boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        return population.mouseScrolled(mouseX, mouseY, delta) || stats.mouseScrolled(mouseX, mouseY, delta);
    }
}
