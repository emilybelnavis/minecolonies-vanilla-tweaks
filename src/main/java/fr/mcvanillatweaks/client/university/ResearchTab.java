package fr.mcvanillatweaks.client.university;

import com.minecolonies.api.research.IGlobalResearch;
import com.minecolonies.api.research.IGlobalResearchBranch;
import com.minecolonies.api.research.IGlobalResearchTree;
import com.minecolonies.api.research.ILocalResearch;
import com.minecolonies.api.research.IResearchRequirement;
import com.minecolonies.core.network.messages.server.colony.OpenInventoryMessage;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.IconButton;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import fr.mcvanillatweaks.client.hut.HutScreen;
import fr.mcvanillatweaks.client.hut.HutTab;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Research tab (MineColonies' university research page): the research branches on the left (click: the branch's tree),
 * the research in progress over all branches on the right with their progress and time left.
 */
final class ResearchTab extends HutTab
{
    private static final int BRANCH_ROW = 26;
    private static final int RUNNING_ROW = 34;
    private static final int LEFT_WIDTH = 140;

    /** A branch and, when its first research is hidden behind requirements, what unlocks it. */
    private record Branch(ResourceLocation id, List<MutableComponent> lock)
    {
        boolean locked()
        {
            return !lock.isEmpty();
        }
    }

    private final ScrollArea branches = new ScrollArea();
    private final ScrollArea running = new ScrollArea();
    private List<Branch> branchList = List.of();
    private final List<Runnable> clicks = new ArrayList<>();
    private final List<int[]> clickAreas = new ArrayList<>();
    private int ticks;

    private final IGlobalResearchTree tree;

    ResearchTab(final HutScreen screen, final IGlobalResearchTree tree)
    {
        super(screen);
        this.tree = tree;
        refresh();
    }

    private IGlobalResearchTree tree()
    {
        return tree;
    }

    private void openTree(final ResourceLocation branch)
    {
        Gui.clickSound();
        UniversityScreens.openTree(building(), branch, tree);
    }

    @Override
    protected void tick()
    {
        if (++ticks % 20 == 0)
        {
            refresh();
        }
    }

    /** The branches MineColonies lists, in its order: hidden branches only once unlocked. */
    private void refresh()
    {
        final List<ResourceLocation> ids = new ArrayList<>(tree().getBranches());
        ids.sort(Comparator.comparingInt(id -> tree().getBranchData(id).getSortOrder()));
        final List<Branch> list = new ArrayList<>();
        for (final ResourceLocation id : ids)
        {
            final List<MutableComponent> lock = lock(id);
            if (lock.isEmpty() || !tree().getBranchData(id).getHidden())
            {
                list.add(new Branch(id, lock));
            }
        }
        branchList = list;
    }

    /** MineColonies' getHidingRequirementDesc: empty when a first research of the branch can be seen. */
    private List<MutableComponent> lock(final ResourceLocation branch)
    {
        final List<MutableComponent> lines = new ArrayList<>();
        for (final ResourceLocation primaryId : tree().getPrimaryResearch(branch))
        {
            final IGlobalResearch primary = tree().getResearch(branch, primaryId);
            if (!primary.isHidden() || tree().isResearchRequirementsFulfilled(primary.getResearchRequirements(), building().getColony()))
            {
                return List.of();
            }
            if (!lines.isEmpty())
            {
                lines.add(Component.translatable("mcvanillatweaks.university.or").withStyle(ChatFormatting.BLUE));
            }
            for (final IResearchRequirement requirement : primary.getResearchRequirements())
            {
                lines.add(Component.literal("- ").append(requirement.getDesc().copy()
                  .withStyle(requirement.isFulfilled(building().getColony()) ? ChatFormatting.AQUA : ChatFormatting.RED)));
            }
        }
        return lines;
    }

    private int rightX()
    {
        return x + LEFT_WIDTH + GAP;
    }

    @Override
    protected void init()
    {
        add(new IconButton(x, y + height - 20, LEFT_WIDTH, 20, new ItemStack(Items.CHEST), Component.translatable("mcvanillatweaks.university.inventory"),
          b -> new OpenInventoryMessage(building()).sendToServer()));
    }

    @Override
    protected void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        clicks.clear();
        clickAreas.clear();
        renderBranches(g, mouseX, mouseY, tooltip);
        renderRunning(g, mouseX, mouseY, tooltip);
    }

    private void renderBranches(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        heading(g, Component.translatable("mcvanillatweaks.university.branches"), x, y, LEFT_WIDTH);
        final int top = y + HEADING;
        branches.set(x, top, LEFT_WIDTH, y + height - 24 - top, branchList.size() * BRANCH_ROW, BRANCH_ROW);
        branches.begin(g);
        for (int i = 0; i < branchList.size(); i++)
        {
            final int rowY = branches.listY() + i * BRANCH_ROW;
            if (rowY + BRANCH_ROW < branches.visibleTop() || rowY > branches.visibleBottom())
            {
                continue;
            }
            final Branch branch = branchList.get(i);
            final IGlobalResearchBranch data = tree().getBranchData(branch.id());
            final boolean hovered = branches.hover(g, mouseX, mouseY, rowY, BRANCH_ROW);
            g.renderItem(Research.branchIcon(branch.id()), branches.listX() + 3, rowY + 5);
            final int tx = branches.listX() + 23;
            final int tw = branches.listWidth() - 27;
            final Component name = MutableComponent.create(data.getName());
            if (branch.locked())
            {
                g.drawString(font, Gui.fit(font, name, tw), tx, rowY + 4, Gui.LIST_SOFT, true);
                g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.university.locked"), tw), tx, rowY + 14, Gui.RED, true);
            }
            else
            {
                g.drawString(font, Gui.fit(font, name, tw), tx, rowY + 4, Gui.WHITE, true);
                g.drawString(font, Gui.fit(font, progressLine(branch.id()), tw), tx, rowY + 14, Gui.LIST_SOFT, true);
                if (branches.overRows(mouseX, mouseY))
                {
                    clicks.add(() -> openTree(branch.id()));
                    clickAreas.add(new int[] {branches.listX(), rowY, branches.listWidth(), BRANCH_ROW});
                }
            }
            if (hovered)
            {
                final List<Component> lines = new ArrayList<>();
                lines.add(name);
                if (!data.getSubtitle().getKey().isEmpty())
                {
                    lines.add(MutableComponent.create(data.getSubtitle()).withStyle(ChatFormatting.GRAY));
                }
                if (branch.locked())
                {
                    lines.addAll(branch.lock());
                }
                else
                {
                    lines.add(Component.translatable("mcvanillatweaks.university.open_tree").withStyle(ChatFormatting.YELLOW));
                }
                tooltip.setAround(lines, mouseX, rowY, rowY + BRANCH_ROW);
            }
        }
        branches.end(g);
    }

    /** "3 / 12 done": finished research of the branch over all its research. */
    private Component progressLine(final ResourceLocation branch)
    {
        final int[] counts = new int[2];
        count(branch, tree().getPrimaryResearch(branch), counts);
        return Component.translatable("mcvanillatweaks.university.branch_progress", counts[0], counts[1]);
    }

    private void count(final ResourceLocation branch, final List<ResourceLocation> ids, final int[] counts)
    {
        for (final ResourceLocation id : ids)
        {
            final IGlobalResearch research = tree().getResearch(branch, id);
            if (research == null || !Research.visible(tree(), building(), research))
            {
                continue;
            }
            counts[1]++;
            if (Research.local(building()).isComplete(id))
            {
                counts[0]++;
            }
            count(branch, research.getChildren(), counts);
        }
    }

    private void renderRunning(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final List<ILocalResearch> list = new ArrayList<>(Research.local(building()).getResearchInProgress());
        final int rx = rightX();
        final int rw = x + width - rx;
        heading(g, Component.translatable("mcvanillatweaks.university.running", list.size(), building().getBuildingLevel()), rx, y, rw);
        final int top = y + HEADING;
        running.set(rx, top, rw, y + height - top, list.size() * RUNNING_ROW, RUNNING_ROW);
        running.begin(g);
        if (list.isEmpty())
        {
            final int tw = running.listWidth() - 8;
            int ly = running.visibleTop() + 6;
            g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.university.none_running"), tw), running.listX() + 4, ly, Gui.LIST_SOFT, true);
            for (final var line : font.split(Component.translatable("mcvanillatweaks.university.none_running_hint"), tw))
            {
                ly += 12;
                g.drawString(font, line, running.listX() + 4, ly, Gui.LIST_SOFT, true);
            }
        }
        for (int i = 0; i < list.size(); i++)
        {
            final int rowY = running.listY() + i * RUNNING_ROW;
            if (rowY + RUNNING_ROW < running.visibleTop() || rowY > running.visibleBottom())
            {
                continue;
            }
            final ILocalResearch local = list.get(i);
            final IGlobalResearch research = tree().getResearch(local.getBranch(), local.getId());
            if (research == null)
            {
                continue;
            }
            final boolean hovered = running.hover(g, mouseX, mouseY, rowY, RUNNING_ROW);
            g.renderItem(Research.icon(research), running.listX() + 3, rowY + 4);
            final int tx = running.listX() + 23;
            final int tw = running.listWidth() - 27;
            final Component name = MutableComponent.create(research.getName());
            g.drawString(font, Gui.fit(font, name, tw), tx, rowY + 3, Gui.WHITE, true);
            final String time = Research.timeLeft(tree(), research, local.getProgress());
            final int barWidth = tw - font.width(time) - 6;
            Gui.bar(g, tx, rowY + 16, barWidth, Research.progressRatio(tree(), research, local.getProgress()));
            g.drawString(font, time, tx + barWidth + 5, rowY + 14, Gui.LIST_SOFT, true);
            g.drawString(font, Gui.fit(font, MutableComponent.create(tree().getBranchData(local.getBranch()).getName()), tw), tx, rowY + 23, Gui.LIST_SOFT, true);
            if (running.overRows(mouseX, mouseY))
            {
                clicks.add(() -> openTree(local.getBranch()));
                clickAreas.add(new int[] {running.listX(), rowY, running.listWidth(), RUNNING_ROW});
            }
            if (hovered)
            {
                tooltip.setAround(List.of(name, Component.translatable("mcvanillatweaks.university.time_left", time).withStyle(ChatFormatting.GRAY),
                  Component.translatable("mcvanillatweaks.university.open_tree").withStyle(ChatFormatting.YELLOW)), mouseX, rowY, rowY + RUNNING_ROW);
            }
        }
        running.end(g);
    }

    @Override
    protected boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (branches.mouseClicked(mouseX, mouseY) || running.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button != 0)
        {
            return false;
        }
        for (int i = 0; i < clickAreas.size(); i++)
        {
            final int[] area = clickAreas.get(i);
            if (Gui.inside(mouseX, mouseY, area[0], area[1], area[2], area[3]))
            {
                clicks.get(i).run();
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean mouseDragged(final double mouseX, final double mouseY)
    {
        return branches.mouseDragged(mouseY) || running.mouseDragged(mouseY);
    }

    @Override
    protected void mouseReleased()
    {
        branches.mouseReleased();
        running.mouseReleased();
    }

    @Override
    protected boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        return branches.mouseScrolled(mouseX, mouseY, delta) || running.mouseScrolled(mouseX, mouseY, delta);
    }
}
