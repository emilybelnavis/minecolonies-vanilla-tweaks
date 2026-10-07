package fr.mcvanillatweaks.client.townhall;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.quests.FinishedQuest;
import com.minecolonies.api.quests.IQuestInstance;
import com.minecolonies.api.quests.IQuestManager;
import com.minecolonies.api.quests.IQuestObjectiveTemplate;
import com.minecolonies.api.quests.IQuestTemplate;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import fr.mcvanillatweaks.client.locate.LocateBeams;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces MineColonies' quest log window ({@code WindowQuestLog}), with its three pages as top tabs:
 * <ul>
 *   <li>in progress: name, the citizen to see next (MineColonies' {@code getQuestTarget}) and the current objective's
 *       progress text, with the objective number;</li>
 *   <li>available: name and the citizen who offers it;</li>
 *   <li>finished: name and how many times it was done, out of how many it can be.</li>
 * </ul>
 * A row click puts a {@link LocateBeams} beam on its citizen (MineColonies' "track citizen" button highlights them for
 * 30 s) and closes the screen; a second click takes it away.
 * <pre>
 * [clock][emerald][book]
 * [log] Quest log - Exempleville              2 in progress
 * | Name of the quest                           (o) |
 * | Alice Martin                                    |
 * | - Bring 16 bread (4/16)                         |
 * </pre>
 */
public final class QuestLogScreen extends Screen
{
    private static final int WIDTH = 320;
    private static final int HEIGHT = 212;
    private static final int TAB_WIDTH = 26;
    private static final int TAB_HEIGHT = 32;
    private static final int TAB_STEP = 27;
    private static final int TAB_RISE = 28;
    private static final int CONTENT_Y = 32;
    private static final int REFRESH_TICKS = 20;

    /** The quest log's pages. */
    public enum Page
    {
        IN_PROGRESS(Items.CLOCK, "com.minecolonies.coremod.item.questlog.gui.inprogress", 36),
        // An offer, like the villagers' trades.
        AVAILABLE(Items.EMERALD, "com.minecolonies.coremod.item.questlog.gui.available", 24),
        FINISHED(Items.WRITTEN_BOOK, "com.minecolonies.coremod.item.questlog.gui.finished", 24);

        private final ItemStack icon;
        private final String title;
        private final int row;

        Page(final Item icon, final String title, final int row)
        {
            this.icon = new ItemStack(icon);
            this.title = title;
            this.row = row;
        }
    }

    /** One quest row, whatever the page: its name, citizen (null for finished quests) and the line under it. */
    private record Row(Component name, @Nullable ICitizenDataView citizen, @Nullable Component detail, List<Component> extra)
    {
    }

    /** The page last used in the session: the quest log reopens it. */
    private static Page lastPage = Page.IN_PROGRESS;

    private final IColonyView colony;
    private final ScrollArea list = new ScrollArea();
    private final Tooltip tooltip = new Tooltip();
    private Page page;
    private List<Row> rows = List.of();
    private final int[] counts = new int[Page.values().length];
    private int left;
    private int top;
    private int ticks;

    public QuestLogScreen(final IColonyView colony)
    {
        this(colony, lastPage);
    }

    QuestLogScreen(final IColonyView colony, final Page page)
    {
        super(Component.translatable("com.minecolonies.coremod.item.questlog.gui.title"));
        this.colony = colony;
        this.page = page;
        refresh();
    }

    // ------------------------------------------------------------------ data

    @Nullable
    private static IQuestTemplate template(final ResourceLocation id)
    {
        return IQuestManager.GLOBAL_SERVER_QUESTS.get(id);
    }

    private void refresh()
    {
        final IQuestManager manager = colony.getQuestManager();
        if (manager == null)
        {
            rows = List.of();
            return;
        }
        counts[Page.IN_PROGRESS.ordinal()] = manager.getInProgressQuests().size();
        counts[Page.AVAILABLE.ordinal()] = manager.getAvailableQuests().size();
        counts[Page.FINISHED.ordinal()] = manager.getFinishedQuests().size();
        final List<Row> list = new ArrayList<>();
        switch (page)
        {
            case IN_PROGRESS -> {
                for (final IQuestInstance quest : manager.getInProgressQuests())
                {
                    final IQuestTemplate template = template(quest.getId());
                    if (template != null)
                    {
                        list.add(inProgress(quest, template));
                    }
                }
            }
            case AVAILABLE -> {
                for (final IQuestInstance quest : manager.getAvailableQuests())
                {
                    final IQuestTemplate template = template(quest.getId());
                    if (template != null)
                    {
                        final ICitizenDataView giver = colony.getCitizen(quest.getQuestGiverId());
                        list.add(new Row(template.getName(), giver, null,
                          List.of(Component.translatable("mcvanillatweaks.questlog.talk_to", giver == null ? "?" : giver.getName()).withStyle(ChatFormatting.GRAY))));
                    }
                }
            }
            case FINISHED -> {
                for (final FinishedQuest quest : manager.getFinishedQuests())
                {
                    final int max = quest.template().getMaxOccurrence();
                    final Component done = max > 1
                                             ? Component.translatable("com.minecolonies.coremod.item.questlog.gui.completed_multiple", quest.finishedCount(), max)
                                             : Component.translatable("com.minecolonies.coremod.item.questlog.gui.completed");
                    list.add(new Row(quest.template().getName(), null, done, List.of()));
                }
            }
        }
        rows = list;
    }

    private Row inProgress(final IQuestInstance quest, final IQuestTemplate template)
    {
        final ICitizenDataView target = colony.getCitizen(quest.getQuestTarget());
        Component progress = null;
        final List<Component> extra = new ArrayList<>();
        try
        {
            final IQuestObjectiveTemplate objective = template.getObjective(quest.getObjectiveIndex());
            progress = objective.getProgressText(quest, Style.EMPTY.withColor(ChatFormatting.GOLD));
            extra.add(Component.translatable("mcvanillatweaks.questlog.objective", quest.getObjectiveIndex() + 1, template.getObjectiveCount())
                        .withStyle(ChatFormatting.GRAY));
            for (final FormattedText line : font().getSplitter().splitLines(objective.getProgressText(quest, Style.EMPTY.withColor(ChatFormatting.WHITE)), 240,
              Style.EMPTY))
            {
                extra.add(Component.literal(line.getString()));
            }
        }
        catch (final LinkageError | RuntimeException ignored)
        {
            // an objective that can't describe itself only loses its line
        }
        return new Row(template.getName(), target, progress, extra);
    }

    private net.minecraft.client.gui.Font font()
    {
        return net.minecraft.client.Minecraft.getInstance().font;
    }

    // ------------------------------------------------------------------ screen

    @Override
    protected void init()
    {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2 + TAB_RISE / 2;
    }

    @Override
    public void tick()
    {
        if (++ticks % REFRESH_TICKS == 0)
        {
            refresh();
        }
    }

    private void open(final Page target)
    {
        page = target;
        lastPage = target;
        refresh();
    }

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        renderTransparentBackground(g);
        for (final Page other : Page.values())
        {
            if (other != page)
            {
                renderTab(g, other, false);
            }
        }
        Gui.panel(g, left, top, WIDTH, HEIGHT);
    }

    private void renderTab(final GuiGraphics g, final Page tab, final boolean selected)
    {
        final int x = left + TAB_STEP * tab.ordinal();
        final int y = top - TAB_RISE;
        g.blitSprite(ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_" + (selected ? "selected_" : "unselected_") + (tab.ordinal() + 1)),
          x, y, TAB_WIDTH, TAB_HEIGHT);
        g.pose().pushPose();
        g.pose().translate(0, 0, 100);
        g.renderItem(tab.icon, x + 5, y + 9);
        // The count like a stack size, as the citizen screen's requests tab.
        if (counts[tab.ordinal()] > 0)
        {
            g.renderItemDecorations(font, tab.icon, x + 5, y + 9, Integer.toString(counts[tab.ordinal()]));
        }
        g.pose().popPose();
    }

    @Nullable
    private Page tabAt(final double mouseX, final double mouseY)
    {
        for (final Page tab : Page.values())
        {
            if (Gui.inside(mouseX, mouseY, left + TAB_STEP * tab.ordinal(), top - TAB_RISE, TAB_WIDTH, TAB_RISE))
            {
                return tab;
            }
        }
        return null;
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        tooltip.clear();
        try
        {
            renderTab(g, page, true);
            renderContent(g, mouseX, mouseY);
            final Page hoveredTab = tabAt(mouseX, mouseY);
            if (hoveredTab != null)
            {
                tooltip.set(Component.translatable(hoveredTab.title));
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            TownHallScreens.report(e);
            onClose();
            return;
        }
        tooltip.render(g, font, mouseX, mouseY);
    }

    private void renderContent(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        g.renderItem(new ItemStack(Items.WRITABLE_BOOK), left + 8, top + 8);
        final int textWidth = WIDTH - 38;
        g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.questlog.title", colony.getName()), textWidth), left + 30, top + 7, Gui.LABEL, false);
        g.drawString(font, Gui.fit(font, Component.translatable(page.title).append(" : " + rows.size()), textWidth), left + 30, top + 18, Gui.LABEL_SOFT, false);

        final int row = page.row;
        list.set(left + 8, top + CONTENT_Y, WIDTH - 16, HEIGHT - CONTENT_Y - 8, rows.size() * row, row);
        list.begin(g);
        if (rows.isEmpty())
        {
            int ty = list.visibleTop() + 6;
            for (final var line : font.split(Component.translatable("mcvanillatweaks.questlog.none." + page.name().toLowerCase(java.util.Locale.ROOT)), list.listWidth() - 10))
            {
                g.drawString(font, line, list.listX() + 5, ty, Gui.LIST_SOFT, true);
                ty += 10;
            }
        }
        for (int i = 0; i < rows.size(); i++)
        {
            final int rowY = list.listY() + i * row;
            if (rowY + row < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            renderRow(g, rows.get(i), rowY, row, mouseX, mouseY);
        }
        list.end(g);
    }

    private void renderRow(final GuiGraphics g, final Row row, final int rowY, final int height, final int mouseX, final int mouseY)
    {
        final boolean hovered = list.hover(g, mouseX, mouseY, rowY, height);
        final boolean located = row.citizen() != null && LocateBeams.isLocated(row.citizen());
        final int tx = list.listX() + 6;
        int right = list.listX() + list.listWidth() - 4;
        if (row.citizen() != null)
        {
            // The locate button's icon, lit when the beam is on.
            g.renderItem(new ItemStack(located ? Items.RECOVERY_COMPASS : Items.COMPASS), right - 16, rowY + (height - 16) / 2);
            right -= 20;
        }
        final int tw = right - tx;
        g.drawString(font, Gui.fit(font, row.name(), tw), tx, rowY + 3, Gui.WHITE, true);
        int ty = rowY + 13;
        if (row.citizen() != null)
        {
            g.drawString(font, Gui.fit(font, Component.literal(row.citizen().getName()), tw), tx, ty, located ? LocateBeams.CITIZEN_COLOUR : Gui.LIST_SOFT, true);
            ty += 10;
        }
        if (row.detail() != null)
        {
            final Component detail = page == Page.IN_PROGRESS ? Component.literal("- ").append(row.detail()).withStyle(ChatFormatting.GOLD) : row.detail();
            g.drawString(font, Gui.fit(font, detail, tw), tx, ty, Gui.GOLD, true);
        }
        if (hovered)
        {
            final List<Component> lines = new ArrayList<>();
            lines.add(row.name());
            lines.addAll(row.extra());
            if (row.citizen() != null)
            {
                lines.add(Component.translatable(located ? "mcvanillatweaks.clipboard.buildings.click_stop" : "mcvanillatweaks.questlog.click")
                            .withStyle(ChatFormatting.YELLOW));
            }
            tooltip.setAround(lines, mouseX, rowY, rowY + height);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        final Page target = tabAt(mouseX, mouseY);
        if (button == 0 && target != null)
        {
            if (target != page)
            {
                Gui.clickSound();
                open(target);
            }
            return true;
        }
        if (list.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button == 0 && list.overRows(mouseX, mouseY))
        {
            final int index = (int) Math.floor((mouseY - list.listY()) / page.row);
            if (index >= 0 && index < rows.size() && rows.get(index).citizen() != null)
            {
                Gui.clickSound();
                if (LocateBeams.toggle(rows.get(index).citizen()))
                {
                    onClose();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dx, final double dy)
    {
        return list.mouseDragged(mouseY) || super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button)
    {
        list.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        return list.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
