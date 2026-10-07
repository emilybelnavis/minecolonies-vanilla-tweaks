package fr.mcvanillatweaks.client.university;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.research.IGlobalResearch;
import com.minecolonies.api.research.IGlobalResearchBranch;
import com.minecolonies.api.research.IGlobalResearchTree;
import com.minecolonies.api.research.ILocalResearch;
import com.minecolonies.api.research.IResearchEffect;
import com.minecolonies.api.research.IResearchRequirement;
import com.minecolonies.api.research.ResearchBranchType;
import com.minecolonies.api.research.util.ResearchState;
import com.minecolonies.core.network.messages.server.colony.building.university.TryResearchMessage;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * A research branch drawn like the vanilla advancements screen: research as advancement frames and boxes on a stone
 * background, joined by the vanilla white lines (gold when only one child can be researched), one column per university
 * level. The canvas is dragged with the mouse. Clicking a research shows its details on the right (effects,
 * requirements, costs as item slots, time) with the button to start, cancel or reset it, which sends the same message
 * as MineColonies' research window.
 * <pre>
 * +----------------------------------------------------------------+
 * | Civilian                                             [Back]    |
 * | +--------------------------------------------+ +-------------+ |
 * | | Level 1 - 1 h     Level 2 - 3 h            | | [#] Name    | |
 * | | [#] Keen   ---+-- [#] Outpost              | | state       | |
 * | |               +-- [#] Hamlet               | | effects...  | |
 * | +--------------------------------------------+ | [Start]     | |
 * +----------------------------------------------------------------+
 * </pre>
 */
public final class ResearchTreeScreen extends Screen
{
    private static final int NODE_W = 122;
    private static final int NODE_H = 26;
    private static final int COLUMN = 158;
    private static final int ROW = 38;
    private static final int MARGIN_X = 12;
    private static final int MARGIN_Y = 26;
    private static final int DETAILS_W = 142;
    private static final int HEADER = 28;
    private static final int TILE = 16;

    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/advancements/backgrounds/stone.png");
    private static final ResourceLocation BOX_OBTAINED = ResourceLocation.withDefaultNamespace("advancements/box_obtained");
    private static final ResourceLocation BOX_UNOBTAINED = ResourceLocation.withDefaultNamespace("advancements/box_unobtained");
    private static final ResourceLocation TASK_OBTAINED = ResourceLocation.withDefaultNamespace("advancements/task_frame_obtained");
    private static final ResourceLocation TASK_UNOBTAINED = ResourceLocation.withDefaultNamespace("advancements/task_frame_unobtained");
    private static final ResourceLocation CHALLENGE_OBTAINED = ResourceLocation.withDefaultNamespace("advancements/challenge_frame_obtained");
    private static final ResourceLocation CHALLENGE_UNOBTAINED = ResourceLocation.withDefaultNamespace("advancements/challenge_frame_unobtained");
    /** MineColonies' highest research level: one research of that level per branch. */
    private static final int MAX_DEPTH = 6;
    /** Zooming out stops when the whole branch fits, and never goes below this. */
    private static final double MIN_ZOOM = 0.3D;
    private static final double ZOOM_STEP = 1.25D;
    private static final int ZOOM_BUTTON = 16;

    /** The zoom is kept for the session, like a map. */
    private static double zoom = 1.0D;

    private record Node(IGlobalResearch research, int column, int row, Research.State state)
    {
        int x()
        {
            return MARGIN_X + column * COLUMN;
        }

        int y()
        {
            return MARGIN_Y + row * ROW;
        }
    }

    private record Edge(Node parent, Node child, boolean exclusive)
    {
    }

    /** The "or" badge on the line between two research of which only one can be chosen, like MineColonies' tree. */
    private record OrMark(Node parent, Node above, Node below)
    {
    }

    private final IBuildingView building;
    private final ResourceLocation branch;
    private final IGlobalResearchTree tree;
    private final Tooltip tooltip = new Tooltip();
    private final ScrollArea details = new ScrollArea();
    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private final List<OrMark> orMarks = new ArrayList<>();
    private int columns;
    private int rows;
    private boolean hasMax;
    @Nullable
    private ResourceLocation selected;
    private boolean scrolled;
    private boolean creativeChecked;
    private double scrollX;
    private double scrollY;
    private boolean dragging;
    private int ticks;

    private int left;
    private int top;
    private int windowW;
    private int windowH;
    private int viewX;
    private int viewY;
    private int viewW;
    private int viewH;
    private Button action;
    private Button zoomOut;
    private Button zoomIn;

    public ResearchTreeScreen(final IBuildingView building, final ResourceLocation branch, final IGlobalResearchTree tree)
    {
        super(MutableComponent.create(tree.getBranchData(branch).getName()));
        this.building = building;
        this.branch = branch;
        this.tree = tree;
    }

    private IGlobalResearchBranch branchData()
    {
        return tree.getBranchData(branch);
    }

    // ------------------------------------------------------------------ layout of the tree

    /** Places the research like MineColonies' tree: children start on their parent's row, siblings go below. */
    private void refresh()
    {
        nodes.clear();
        edges.clear();
        orMarks.clear();
        hasMax = Research.local(building).branchFinishedHighestLevel(branch);
        columns = 0;
        rows = place(new ArrayList<>(tree.getPrimaryResearch(branch)), 0, 0, false, null) + 1;
        if (selected == null || nodes.stream().noneMatch(n -> n.research().getId().equals(selected)))
        {
            selected = defaultSelection();
        }
    }

    private int place(final List<ResourceLocation> ids, final int depth, final int row, final boolean abandoned, @Nullable final Node parent)
    {
        ids.sort(Comparator.comparingInt(id -> {
            final IGlobalResearch research = tree.getResearch(branch, id);
            return research == null ? 0 : research.getSortOrder();
        }));
        int next = row;
        boolean placed = false;
        Node previous = null;
        for (final ResourceLocation id : ids)
        {
            final IGlobalResearch research = tree.getResearch(branch, id);
            if (research == null || !Research.visible(tree, building, research))
            {
                continue;
            }
            if (placed)
            {
                next++;
            }
            placed = true;
            final boolean nodeAbandoned = Research.abandoned(tree, building, research, abandoned);
            final Node node = new Node(research, depth, next, Research.state(tree, building, research, nodeAbandoned, hasMax));
            nodes.add(node);
            columns = Math.max(columns, depth + 1);
            if (parent != null)
            {
                final boolean exclusive = exclusive(parent.research());
                edges.add(new Edge(parent, node, exclusive));
                if (exclusive && previous != null)
                {
                    orMarks.add(new OrMark(parent, previous, node));
                }
            }
            previous = node;
            if (!research.getChildren().isEmpty())
            {
                next = place(new ArrayList<>(research.getChildren()), depth + 1, next, nodeAbandoned, node);
            }
        }
        return next;
    }

    /** MineColonies' "only child": the player picks one of the children, the others are abandoned. */
    private static boolean exclusive(@Nullable final IGlobalResearch parent)
    {
        return parent != null && parent.hasOnlyChild() && parent.getChildren().size() > 1;
    }

    /** Research in progress first, then the first one that can be started, then the first one. */
    @Nullable
    private ResourceLocation defaultSelection()
    {
        for (final Research.State wanted : new Research.State[] {Research.State.IN_PROGRESS, Research.State.AVAILABLE})
        {
            for (final Node node : nodes)
            {
                if (node.state() == wanted)
                {
                    return node.research().getId();
                }
            }
        }
        return nodes.isEmpty() ? null : nodes.get(0).research().getId();
    }

    @Nullable
    private Node selectedNode()
    {
        for (final Node node : nodes)
        {
            if (node.research().getId().equals(selected))
            {
                return node;
            }
        }
        return null;
    }

    private int contentWidth()
    {
        return MARGIN_X + Math.max(1, columns) * COLUMN;
    }

    private int contentHeight()
    {
        return MARGIN_Y + Math.max(1, rows) * ROW + 8;
    }

    /** Scroll is in tree units, so the same research stays in view whatever the zoom. */
    private void clampScroll()
    {
        scrollX = Math.max(0, Math.min(Math.max(0, contentWidth() - viewW / zoom), scrollX));
        scrollY = Math.max(0, Math.min(Math.max(0, contentHeight() - viewH / zoom), scrollY));
    }

    // ------------------------------------------------------------------ zoom

    /** The zoom at which the whole branch is visible, within [MIN_ZOOM, 1]. */
    private double minZoom()
    {
        final double fit = Math.min((double) viewW / contentWidth(), (double) viewH / contentHeight());
        return Math.max(MIN_ZOOM, Math.min(1.0D, fit));
    }

    /** Screen position of the tree's origin; rounded so the text stays sharp. */
    private int originX()
    {
        return viewX - (int) Math.round(scrollX * zoom);
    }

    private int originY()
    {
        return viewY - (int) Math.round(scrollY * zoom);
    }

    private double treeX(final double mouseX)
    {
        return (mouseX - originX()) / zoom;
    }

    private double treeY(final double mouseY)
    {
        return (mouseY - originY()) / zoom;
    }

    /** Zooms by {@code steps} (negative = out), keeping the tree point under (atX, atY) in place. */
    private void zoom(final double steps, final double atX, final double atY)
    {
        final double min = minZoom();
        double next = zoom * Math.pow(ZOOM_STEP, steps);
        // Snap to the ends so the buttons reach exactly 100 % and "whole branch".
        if (next > 0.97D)
        {
            next = 1.0D;
        }
        if (next < min * 1.03D)
        {
            next = min;
        }
        next = Math.max(min, Math.min(1.0D, next));
        if (next == zoom)
        {
            return;
        }
        final double px = treeX(atX);
        final double py = treeY(atY);
        zoom = next;
        scrollX = px - (atX - viewX) / zoom;
        scrollY = py - (atY - viewY) / zoom;
        clampScroll();
        updateZoomButtons();
    }

    private void zoomAtCentre(final double steps)
    {
        zoom(steps, viewX + viewW / 2.0D, viewY + viewH / 2.0D);
    }

    /** Lets the dev preview capture the zoomed-out tree. */
    void zoomOutFully()
    {
        zoomAtCentre(-100);
    }

    private void updateZoomButtons()
    {
        if (zoomOut != null)
        {
            zoomOut.active = zoom > minZoom();
            zoomIn.active = zoom < 1.0D;
        }
    }

    /** Unlike isMouseOver, also true for a disabled button. */
    private static boolean over(final Button button, final double mouseX, final double mouseY)
    {
        return Gui.inside(mouseX, mouseY, button.getX(), button.getY(), button.getWidth(), button.getHeight());
    }

    private boolean overZoomButtons(final double mouseX, final double mouseY)
    {
        return zoomOut != null && (over(zoomOut, mouseX, mouseY) || over(zoomIn, mouseX, mouseY));
    }

    // ------------------------------------------------------------------ screen

    @Override
    protected void init()
    {
        windowW = Math.min(width - 16, 470);
        windowH = Math.min(height - 16, 290);
        left = (width - windowW) / 2;
        top = (height - windowH) / 2;
        viewX = left + 8;
        viewY = top + HEADER;
        viewW = windowW - 16 - DETAILS_W - 6;
        viewH = windowH - HEADER - 8;
        try
        {
            refresh();
            if (!creativeChecked)
            {
                creativeChecked = true;
                completeInCreative();
            }
            zoom = Math.max(minZoom(), zoom);
            if (!scrolled)
            {
                scrolled = true;
                final Node node = selectedNode();
                if (node != null)
                {
                    // Show the column before the selected research too.
                    scrollX = node.x() - COLUMN - MARGIN_X;
                    scrollY = node.y() - viewH / 3.0D / zoom;
                }
            }
            clampScroll();
            addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose()).bounds(left + windowW - 8 - 70, top + 5, 70, 18).build());
            action = addRenderableWidget(Button.builder(Component.empty(), b -> act()).bounds(detailsX(), viewY + viewH - 20, DETAILS_W, 20).build());
            // Drawn over the tree by renderTree, so only registered for input here.
            final int zy = viewY + viewH - 4 - ZOOM_BUTTON;
            final int zx = viewX + viewW - 4 - ZOOM_BUTTON;
            zoomIn = addWidget(Button.builder(Component.literal("+"), b -> zoomAtCentre(1)).bounds(zx, zy, ZOOM_BUTTON, ZOOM_BUTTON).build());
            zoomOut = addWidget(Button.builder(Component.literal("-"), b -> zoomAtCentre(-1)).bounds(zx - 2 - ZOOM_BUTTON, zy, ZOOM_BUTTON, ZOOM_BUTTON).build());
            updateZoomButtons();
            updateAction();
        }
        catch (final LinkageError | RuntimeException e)
        {
            fail(e);
        }
    }

    private int detailsX()
    {
        return viewX + viewW + 6;
    }

    /** MineColonies finishes research at once for creative players when its server allows it. */
    private void completeInCreative()
    {
        if (minecraft == null || minecraft.player == null || !minecraft.player.isCreative() || !Research.creativeCompletion())
        {
            return;
        }
        for (final Node node : nodes)
        {
            final IGlobalResearch research = node.research();
            if (node.state() == Research.State.IN_PROGRESS
                  && Research.progress(building, research) < branchData().getBaseTime(research.getDepth()))
            {
                new TryResearchMessage(building, research.getId(), research.getBranch(), false).sendToServer();
            }
        }
    }

    @Override
    public void tick()
    {
        if (++ticks % 20 == 0)
        {
            try
            {
                refresh();
                updateAction();
                updateZoomButtons();
            }
            catch (final LinkageError | RuntimeException e)
            {
                fail(e);
            }
        }
    }

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        renderTransparentBackground(g);
        Gui.panel(g, left, top, windowW, windowH);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        tooltip.clear();
        try
        {
            renderHeader(g);
            renderTree(g, mouseX, mouseY);
            renderDetails(g, mouseX, mouseY);
            if (!action.active && action.isHovered())
            {
                final List<Component> reason = actionReason();
                if (!reason.isEmpty())
                {
                    tooltip.set(reason);
                }
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            fail(e);
            return;
        }
        tooltip.render(g, font, mouseX, mouseY);
    }

    private void renderHeader(final GuiGraphics g)
    {
        g.drawString(font, Gui.fit(font, getTitle(), windowW - 200), left + 8, top + 7, Gui.LABEL, false);
        final IGlobalResearchBranch data = branchData();
        if (!data.getSubtitle().getKey().isEmpty())
        {
            g.drawString(font, Gui.fit(font, MutableComponent.create(data.getSubtitle()), windowW - 200), left + 8, top + 17, Gui.LABEL_SOFT, false);
        }
        final int running = Research.local(building).getResearchInProgress().size();
        final Component slots = Component.translatable("mcvanillatweaks.university.slots", running, building.getBuildingLevel());
        g.drawString(font, slots, left + windowW - 8 - 70 - 8 - font.width(slots), top + 10, running >= building.getBuildingLevel() ? 0xFFAA0000 : Gui.LABEL_SOFT, false);
    }

    // ------------------------------------------------------------------ tree drawing

    private void renderTree(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        Gui.inset(g, viewX - 1, viewY - 1, viewW + 2, viewH + 2, 0xFF000000);
        // The scissor is in screen coordinates, so it is set before the zoom.
        g.enableScissor(viewX, viewY, viewX + viewW, viewY + viewH);
        g.pose().pushPose();
        g.pose().translate(originX(), originY(), 0);
        g.pose().scale((float) zoom, (float) zoom, 1.0F);
        // Everything below is drawn in tree units; (left, top) to (right, bottom) is the visible part of the tree.
        final int visibleLeft = (int) Math.floor(scrollX);
        final int visibleTop = (int) Math.floor(scrollY);
        final int visibleRight = (int) Math.ceil(scrollX + viewW / zoom) + 1;
        final int visibleBottom = (int) Math.ceil(scrollY + viewH / zoom) + 1;
        final boolean overView = Gui.inside(mouseX, mouseY, viewX, viewY, viewW, viewH) && !overZoomButtons(mouseX, mouseY);
        final double mx = treeX(mouseX);
        final double my = treeY(mouseY);

        // Stone background, scrolling (and shrinking) with the tree like the advancement tabs.
        for (int tx = Math.floorDiv(visibleLeft, TILE) * TILE; tx < visibleRight; tx += TILE)
        {
            for (int ty = Math.floorDiv(visibleTop, TILE) * TILE; ty < visibleBottom; ty += TILE)
            {
                g.blit(BACKGROUND, tx, ty, 0, 0, TILE, TILE, TILE, TILE);
            }
        }

        renderColumns(g, visibleTop, visibleBottom, overView, mx, my);
        final int line = lineWidth();
        for (final Edge edge : edges)
        {
            edge(g, edge, line, true);
        }
        for (final Edge edge : edges)
        {
            edge(g, edge, line, false);
        }
        for (final OrMark mark : orMarks)
        {
            orMark(g, mark, line, overView && !dragging, mx, my);
        }
        for (final Node node : nodes)
        {
            final boolean hovered = overView && !dragging && Gui.inside(mx, my, node.x(), node.y(), NODE_W, NODE_H);
            node(g, node, node.x(), node.y(), hovered, line);
            if (hovered)
            {
                final List<Component> lines = new ArrayList<>(List.of(MutableComponent.create(node.research().getName()), stateLine(node)));
                if (exclusive(tree.getResearch(branch, node.research().getParent())))
                {
                    lines.add(Component.translatable("mcvanillatweaks.university.exclusive").withStyle(ChatFormatting.GOLD));
                }
                lines.add(Component.translatable("mcvanillatweaks.university.click_details").withStyle(ChatFormatting.YELLOW));
                tooltip.set(lines);
            }
        }
        g.pose().popPose();

        // Zoom buttons in the corner, above the research.
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        zoomOut.render(g, mouseX, mouseY, 0);
        zoomIn.render(g, mouseX, mouseY, 0);
        g.pose().popPose();
        g.disableScissor();
        if (over(zoomOut, mouseX, mouseY) || over(zoomIn, mouseX, mouseY))
        {
            tooltip.set(List.of(Component.translatable(over(zoomOut, mouseX, mouseY) ? "mcvanillatweaks.university.zoom_out" : "mcvanillatweaks.university.zoom_in"),
              Component.translatable("mcvanillatweaks.university.zoom_level", Math.round(zoom * 100)).withStyle(ChatFormatting.GRAY),
              Component.translatable("mcvanillatweaks.university.zoom_hint").withStyle(ChatFormatting.GRAY)));
        }
    }

    /** Lines stay at least about one pixel wide on screen when zoomed out. */
    private int lineWidth()
    {
        return Math.max(1, (int) Math.ceil(0.75D / zoom));
    }

    /** "Level n - h hours" over each column; the levels the university has not reached are darkened. */
    private void renderColumns(final GuiGraphics g, final int visibleTop, final int visibleBottom, final boolean overView, final double mouseX,
      final double mouseY)
    {
        final boolean unlockables = branchData().getType() == ResearchBranchType.UNLOCKABLES;
        if (unlockables && branchData().getBaseTime(1) < 1)
        {
            return;
        }
        final int level = building.getBuildingLevel();
        final int max = building.getBuildingMaxLevel();
        for (int depth = 1; depth <= Math.max(columns, 1); depth++)
        {
            final int cx = MARGIN_X + (depth - 1) * COLUMN - 6;
            final boolean reached = unlockables || level >= depth || level == max && !hasMax;
            if (!reached)
            {
                g.fill(cx, visibleTop, cx + COLUMN, visibleBottom, 0x70000000);
            }
            final Component header = Component.translatable("mcvanillatweaks.university.tier", Math.min(depth, max),
              String.format(Locale.ROOT, "%.1f", branchData().getHoursTime(depth)).replace(".0", ""));
            g.drawString(font, header, cx + 6, 8, reached ? Gui.WHITE : Gui.RED, true);
            if (!reached && overView && Gui.inside(mouseX, mouseY, cx + 6, 6, font.width(header), 12))
            {
                tooltip.set(Component.translatable("com.minecolonies.coremod.research.requirement.university.level", Math.min(depth, max)));
            }
        }
    }

    /** The vanilla advancement line: black outline, white (or gold) core {@code width} wide, with an elbow halfway. */
    private static void edge(final GuiGraphics g, final Edge edge, final int width, final boolean outline)
    {
        final int x1 = edge.parent().x() + NODE_W;
        final int y1 = edge.parent().y() + (NODE_H - width + 1) / 2;
        final int x2 = edge.child().x();
        final int y2 = edge.child().y() + (NODE_H - width + 1) / 2;
        final int mid = x1 + (x2 - x1 - width + 1) / 2;
        final int colour = outline ? 0xFF000000 : edge.exclusive() ? 0xFFFFAA00 : 0xFFFFFFFF;
        final int pad = outline ? width : 0;
        g.fill(x1 - pad, y1 - pad, mid + width + pad, y1 + width + pad, colour);
        g.fill(mid - pad, Math.min(y1, y2) - pad, mid + width + pad, Math.max(y1, y2) + width + pad, colour);
        g.fill(mid - pad, y2 - pad, x2 + pad, y2 + width + pad, colour);
    }

    /** A dark badge with a gold "or" on the vertical line, halfway between the two choices. */
    private void orMark(final GuiGraphics g, final OrMark mark, final int line, final boolean hoverable, final double mouseX, final double mouseY)
    {
        final int x1 = mark.parent().x() + NODE_W;
        final int cx = x1 + (mark.below().x() - x1 - line + 1) / 2 + line / 2;
        final int cy = (mark.above().y() + mark.below().y() + NODE_H) / 2;
        final Component text = Component.translatable("com.minecolonies.coremod.research.research.or");
        final int w = font.width(text) + 6;
        final int h = 12;
        final int bx = cx - w / 2;
        final int by = cy - h / 2;
        final boolean hovered = hoverable && Gui.inside(mouseX, mouseY, bx, by, w, h);
        g.fill(bx - 1, by - 1, bx + w + 1, by + h + 1, 0xFF000000);
        g.fill(bx, by, bx + w, by + h, hovered ? 0xFF5A4A1E : 0xFF2E2614);
        g.renderOutline(bx, by, w, h, 0xFFFFAA00);
        g.drawString(font, text, bx + 3, by + 2, Gui.GOLD, true);
        if (hovered)
        {
            tooltip.set(List.of(Component.translatable("mcvanillatweaks.university.exclusive").withStyle(ChatFormatting.GOLD),
              Component.translatable("mcvanillatweaks.university.exclusive_hint", MutableComponent.create(mark.parent().research().getName()))
                .withStyle(ChatFormatting.GRAY)));
        }
    }

    private void node(final GuiGraphics g, final Node node, final int nx, final int ny, final boolean hovered, final int line)
    {
        final Research.State state = node.state();
        final boolean done = state == Research.State.FINISHED;
        final boolean available = state == Research.State.AVAILABLE;
        final boolean challenge = node.research().getDepth() >= MAX_DEPTH;
        // Research that can be started now (costs on hand) gets the "obtained" sprites tinted green: gold times green.
        final boolean gold = done || available;
        if (available)
        {
            g.setColor(0.45F, 1.0F, 0.45F, 1.0F);
        }
        // The box, drawn in two halves of the 200 px vanilla sprite like the advancement titles.
        final ResourceLocation box = gold ? BOX_OBTAINED : BOX_UNOBTAINED;
        final int bw = NODE_W - 13;
        final int half = bw / 2;
        g.blitSprite(box, 200, 26, 0, 0, nx + 13, ny, half, NODE_H);
        g.blitSprite(box, 200, 26, 200 - (bw - half), 0, nx + 13 + half, ny, bw - half, NODE_H);
        g.blitSprite(challenge ? gold ? CHALLENGE_OBTAINED : CHALLENGE_UNOBTAINED : gold ? TASK_OBTAINED : TASK_UNOBTAINED, nx, ny, 26, 26);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        g.renderItem(Research.icon(node.research()), nx + 5, ny + 5);
        if (state.locked())
        {
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(nx, ny, nx + NODE_W, ny + NODE_H, 0x99000000);
            g.pose().popPose();
        }
        final int colour = state.locked() ? 0xFF8A8A8A
                             : state == Research.State.MISSING_COST || state == Research.State.MISSING_REQUIREMENT ? 0xFFC8C8C8 : Gui.WHITE;
        g.pose().pushPose();
        g.pose().translate(0, 0, 210);
        g.drawString(font, Gui.fit(font, MutableComponent.create(node.research().getName()), NODE_W - 34), nx + 30, ny + 9, colour, true);
        if (hovered)
        {
            g.fill(nx + 13, ny + 2, nx + NODE_W - 1, ny + NODE_H - 2, 0x30FFFFFF);
        }
        if (node.research().getId().equals(selected))
        {
            final int x1 = nx - 1 - line;
            final int y1 = ny - 1 - line;
            final int x2 = nx + NODE_W + 1 + line;
            final int y2 = ny + NODE_H + 1 + line;
            g.fill(x1, y1, x2, y1 + line, 0xFFFFFFFF);
            g.fill(x1, y2 - line, x2, y2, 0xFFFFFFFF);
            g.fill(x1, y1 + line, x1 + line, y2 - line, 0xFFFFFFFF);
            g.fill(x2 - line, y1 + line, x2, y2 - line, 0xFFFFFFFF);
        }
        g.pose().popPose();
        if (state == Research.State.IN_PROGRESS)
        {
            Gui.bar(g, nx + 30, ny + NODE_H + 2, NODE_W - 34, Research.progressRatio(tree, node.research(), Research.progress(building, node.research())));
        }
    }

    // ------------------------------------------------------------------ details

    private Component stateLine(final Node node)
    {
        final IGlobalResearch research = node.research();
        return switch (node.state())
        {
            case FINISHED -> Component.translatable("mcvanillatweaks.university.state.finished").withStyle(ChatFormatting.GREEN);
            case IN_PROGRESS -> Component.translatable("mcvanillatweaks.university.state.in_progress",
              Research.timeLeft(tree, research, Research.progress(building, research))).withStyle(ChatFormatting.YELLOW);
            case AVAILABLE -> Component.translatable("mcvanillatweaks.university.state.available").withStyle(ChatFormatting.GREEN);
            case MISSING_COST -> Component.translatable("mcvanillatweaks.university.state.missing_cost").withStyle(ChatFormatting.RED);
            case MISSING_REQUIREMENT -> Component.translatable("mcvanillatweaks.university.state.missing_requirement").withStyle(ChatFormatting.RED);
            case TOO_LOW_UNIVERSITY -> Component.translatable("mcvanillatweaks.university.state.too_low",
              Math.min(research.getDepth(), building.getBuildingMaxLevel())).withStyle(ChatFormatting.RED);
            case MISSING_PARENT -> Component.translatable("mcvanillatweaks.university.state.missing_parent").withStyle(ChatFormatting.GRAY);
            case ABANDONED -> Component.translatable("mcvanillatweaks.university.state.abandoned").withStyle(ChatFormatting.GRAY);
        };
    }

    /** A line of the details panel: wrapped text, a heading, or a row of item slots. */
    private sealed interface Line permits TextLine, SlotsLine
    {
        int height();
    }

    private record TextLine(FormattedCharSequence text, int colour) implements Line
    {
        @Override
        public int height()
        {
            return 10;
        }
    }

    private record SlotsLine(List<ItemStack> stacks, List<Boolean> missing, List<Component> names) implements Line
    {
        @Override
        public int height()
        {
            return 20;
        }
    }

    private void text(final List<Line> lines, final Component text, final int colour, final int width)
    {
        for (final FormattedCharSequence line : font.split(text, width))
        {
            lines.add(new TextLine(line, colour));
        }
    }

    private void gap(final List<Line> lines)
    {
        lines.add(new TextLine(FormattedCharSequence.EMPTY, 0));
    }

    private List<Line> detailLines(final Node node, final int width)
    {
        final IGlobalResearch research = node.research();
        final List<Line> lines = new ArrayList<>();
        text(lines, stateLine(node), Gui.WHITE, width);
        if (!research.getSubtitle().getKey().isEmpty())
        {
            text(lines, MutableComponent.create(research.getSubtitle()).withStyle(ChatFormatting.ITALIC), Gui.LIST_SOFT, width);
        }

        if (!research.getEffects().isEmpty())
        {
            gap(lines);
            text(lines, Component.translatable("mcvanillatweaks.university.effects"), Gui.GOLD, width);
            for (final IResearchEffect effect : research.getEffects())
            {
                text(lines, Component.literal("• ").append(MutableComponent.create(effect.getName())), Gui.WHITE, width);
                if (!effect.getSubtitle().getKey().isEmpty())
                {
                    text(lines, MutableComponent.create(effect.getSubtitle()).withStyle(ChatFormatting.ITALIC), Gui.LIST_SOFT, width);
                }
            }
        }

        final boolean open = node.state() != Research.State.FINISHED && node.state() != Research.State.IN_PROGRESS;
        if (open && !research.getResearchRequirements().isEmpty())
        {
            gap(lines);
            text(lines, Component.translatable("mcvanillatweaks.university.requirements"), Gui.GOLD, width);
            for (final IResearchRequirement requirement : research.getResearchRequirements())
            {
                final boolean met = requirement.isFulfilled(building.getColony());
                text(lines, Component.literal(met ? "✔ " : "✖ ").append(requirement.getDesc().copy()), met ? Gui.GREEN : Gui.RED, width);
            }
        }
        if (open && !research.getCostList().isEmpty())
        {
            gap(lines);
            text(lines, Component.translatable("mcvanillatweaks.university.cost"), Gui.GOLD, width);
            final boolean enough = minecraft != null && minecraft.player != null && research.hasEnoughResources(minecraft.player, building.getPosition());
            final List<ItemStack> stacks = new ArrayList<>();
            final List<Component> names = new ArrayList<>();
            for (final SizedIngredient cost : research.getCostList())
            {
                final ItemStack[] items = cost.ingredient().getItems();
                if (items.length > 0)
                {
                    stacks.add(items[ticks / 20 % items.length].copyWithCount(cost.count()));
                    names.add(Component.literal(cost.count() + " × ").append(items[ticks / 20 % items.length].getHoverName()));
                }
            }
            slots(lines, stacks, names, !enough, width);
        }
        if (open && research.getDepth() > 0 && !research.isInstant() && branchData().getBaseTime(research.getDepth()) > 0)
        {
            gap(lines);
            text(lines, Component.translatable("mcvanillatweaks.university.duration",
              String.format(Locale.ROOT, "%.1f", branchData().getHoursTime(research.getDepth())).replace(".0", "")), Gui.LIST_SOFT, width);
        }

        final IGlobalResearch parent = tree.getResearch(branch, research.getParent());
        if (parent != null && parent.hasOnlyChild() && parent.getChildren().size() > 1)
        {
            gap(lines);
            text(lines, Component.translatable("mcvanillatweaks.university.exclusive"), Gui.GOLD, width);
        }
        if (research.getDepth() >= MAX_DEPTH && branchData().getType() != ResearchBranchType.UNLOCKABLES)
        {
            gap(lines);
            text(lines, Component.translatable("com.minecolonies.coremod.research.limit.onemaxperbranch"), hasMax ? Gui.RED : Gui.LIST_SOFT, width);
        }
        if (research.isImmutable())
        {
            gap(lines);
            text(lines, Component.translatable("com.minecolonies.coremod.research.limit.immutable"), Gui.RED, width);
        }
        if (node.state() == Research.State.FINISHED && resettable(node))
        {
            gap(lines);
            text(lines, Component.translatable("mcvanillatweaks.university.reset_cost"), Gui.GOLD, width);
            final List<ItemStack> stacks = new ArrayList<>();
            final List<Component> names = new ArrayList<>();
            for (final ItemStorage cost : resetCosts())
            {
                stacks.add(cost.getItemStack().copyWithCount(cost.getAmount()));
                names.add(Component.literal(cost.getAmount() + " × ").append(cost.getItemStack().getHoverName()));
            }
            slots(lines, stacks, names, !canPayReset(), width);
        }
        return lines;
    }

    private void slots(final List<Line> lines, final List<ItemStack> stacks, final List<Component> names, final boolean missing, final int width)
    {
        final int perRow = Math.max(1, width / 18);
        for (int i = 0; i < stacks.size(); i += perRow)
        {
            final int end = Math.min(stacks.size(), i + perRow);
            final List<Boolean> flags = new ArrayList<>();
            for (int j = i; j < end; j++)
            {
                flags.add(missing);
            }
            lines.add(new SlotsLine(stacks.subList(i, end), flags, names.subList(i, end)));
        }
    }

    private void renderDetails(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        final int dx = detailsX();
        final int dh = viewH - 24;
        final Node node = selectedNode();
        if (node == null)
        {
            Gui.inset(g, dx, viewY, DETAILS_W, dh);
            return;
        }
        // Title: the research's icon in a slot and its name, above the scrolling details.
        final int textWidth = DETAILS_W - 14 - 6;
        final List<FormattedCharSequence> title = font.split(MutableComponent.create(node.research().getName()), DETAILS_W - 30);
        final int titleH = Math.max(20, title.size() * 10 + 2);
        Gui.slot(g, dx, viewY);
        g.renderItem(Research.icon(node.research()), dx + 1, viewY + 1);
        int ty = viewY + (title.size() == 1 ? 5 : 0);
        for (final FormattedCharSequence line : title)
        {
            g.drawString(font, line, dx + 22, ty, Gui.LABEL, false);
            ty += 10;
        }

        final List<Line> lines = detailLines(node, textWidth);
        int content = 6;
        for (final Line line : lines)
        {
            content += line.height();
        }
        final int listTop = viewY + titleH + 4;
        details.set(dx, listTop, DETAILS_W, viewY + dh - listTop, content, 10);
        details.begin(g);
        int ly = details.listY() + 3;
        for (final Line line : lines)
        {
            if (line instanceof TextLine text)
            {
                g.drawString(font, text.text(), details.listX() + 3, ly, text.colour(), true);
            }
            else if (line instanceof SlotsLine slots)
            {
                for (int i = 0; i < slots.stacks().size(); i++)
                {
                    final int sx = details.listX() + 3 + i * 18;
                    Gui.slot(g, sx, ly);
                    g.renderItem(slots.stacks().get(i), sx + 1, ly + 1);
                    g.renderItemDecorations(font, slots.stacks().get(i), sx + 1, ly + 1);
                    if (slots.missing().get(i))
                    {
                        g.pose().pushPose();
                        g.pose().translate(0, 0, 300);
                        g.fill(sx + 1, ly + 1, sx + 17, ly + 17, 0x50FF0000);
                        g.pose().popPose();
                    }
                    if (details.overRows(mouseX, mouseY) && Gui.inside(mouseX, mouseY, sx, ly, 18, 18))
                    {
                        Gui.slotHighlight(g, sx, ly);
                        tooltip.set(List.of(slots.names().get(i), Component.translatable(slots.missing().get(i)
                                                                                             ? "mcvanillatweaks.university.cost_missing"
                                                                                             : "mcvanillatweaks.university.cost_ok")
                                                                          .withStyle(slots.missing().get(i) ? ChatFormatting.RED : ChatFormatting.GREEN)));
                    }
                }
            }
            ly += line.height();
        }
        details.end(g);
    }

    // ------------------------------------------------------------------ actions

    private enum Action
    {
        START, CANCEL, RESET, NONE
    }

    private Action actionFor(@Nullable final Node node)
    {
        if (node == null)
        {
            return Action.NONE;
        }
        return switch (node.state())
        {
            case IN_PROGRESS -> Action.CANCEL;
            case FINISHED -> resettable(node) ? Action.RESET : Action.NONE;
            default -> Action.START;
        };
    }

    /** MineColonies only offers to undo a finished research that is not fixed and whose children were not started. */
    private boolean resettable(final Node node)
    {
        final IGlobalResearch research = node.research();
        if (research.isImmutable() || research.isAutostart())
        {
            return false;
        }
        for (final ResourceLocation child : research.getChildren())
        {
            final ILocalResearch local = Research.local(building).getResearch(branch, child);
            if (local != null && local.getState() != ResearchState.NOT_STARTED)
            {
                return false;
            }
        }
        return true;
    }

    private List<ItemStorage> resetCosts()
    {
        if (minecraft == null || minecraft.level == null)
        {
            return List.of();
        }
        return tree.getResearchResetCosts(minecraft.level.registryAccess());
    }

    private boolean canPayReset()
    {
        if (minecraft == null || minecraft.player == null)
        {
            return false;
        }
        if (minecraft.player.isCreative())
        {
            return true;
        }
        for (final ItemStorage cost : resetCosts())
        {
            int count = 0;
            for (final ItemStack stack : minecraft.player.getInventory().items)
            {
                if (!stack.isEmpty() && ItemStack.isSameItem(stack, cost.getItemStack()))
                {
                    count += stack.getCount();
                }
            }
            if (count < cost.getAmount())
            {
                return false;
            }
        }
        return true;
    }

    private boolean canStart(final Node node)
    {
        return node.state() == Research.State.AVAILABLE
                 && Research.local(building, node.research()) == null
                 && Research.local(building).getResearchInProgress().size() < building.getBuildingLevel();
    }

    private void updateAction()
    {
        if (action == null)
        {
            return;
        }
        final Node node = selectedNode();
        final Action kind = actionFor(node);
        action.visible = node != null;
        switch (kind)
        {
            case START ->
            {
                action.setMessage(Component.translatable("mcvanillatweaks.university.start"));
                action.active = canStart(node);
            }
            case CANCEL ->
            {
                action.setMessage(Component.translatable("mcvanillatweaks.university.cancel"));
                action.active = true;
            }
            case RESET ->
            {
                action.setMessage(Component.translatable("mcvanillatweaks.university.reset"));
                action.active = canPayReset();
            }
            case NONE ->
            {
                action.setMessage(Component.translatable("mcvanillatweaks.university.state.finished"));
                action.active = false;
            }
        }
    }

    /** Why the action button is disabled. */
    private List<Component> actionReason()
    {
        final Node node = selectedNode();
        if (node == null)
        {
            return List.of();
        }
        return switch (actionFor(node))
        {
            case START ->
            {
                if (node.state() != Research.State.AVAILABLE)
                {
                    yield List.of(stateLine(node));
                }
                if (Research.local(building, node.research()) != null)
                {
                    yield List.of(Component.translatable("mcvanillatweaks.university.waiting").withStyle(ChatFormatting.GRAY));
                }
                yield List.of(Component.translatable("com.minecolonies.coremod.research.research.toomanyinprogress.1").withStyle(ChatFormatting.RED),
                  Component.translatable("mcvanillatweaks.university.slots_hint").withStyle(ChatFormatting.GRAY));
            }
            case RESET -> List.of(Component.translatable("com.minecolonies.coremod.research.research.notenoughresources").withStyle(ChatFormatting.RED));
            case NONE -> node.research().isImmutable()
                           ? List.of(Component.translatable("com.minecolonies.coremod.research.limit.immutable").withStyle(ChatFormatting.RED))
                           : List.of(Component.translatable("mcvanillatweaks.university.children_started").withStyle(ChatFormatting.GRAY));
            default -> List.of();
        };
    }

    /** Sends what MineColonies' research window sends for the same click. */
    private void act()
    {
        final Node node = selectedNode();
        if (node == null)
        {
            return;
        }
        final IGlobalResearch research = node.research();
        switch (actionFor(node))
        {
            case START ->
            {
                if (canStart(node))
                {
                    research.startResearch(Research.local(building));
                    new TryResearchMessage(building, research.getId(), research.getBranch(), false).sendToServer();
                }
            }
            case CANCEL ->
            {
                final ILocalResearch local = Research.local(building, research);
                if (local != null)
                {
                    local.setState(ResearchState.NOT_STARTED);
                }
                new TryResearchMessage(building, research.getId(), research.getBranch(), true).sendToServer();
            }
            case RESET ->
            {
                if (canPayReset())
                {
                    new TryResearchMessage(building, research.getId(), research.getBranch(), true).sendToServer();
                }
            }
            default ->
            {
            }
        }
        refresh();
        updateAction();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        try
        {
            if (super.mouseClicked(mouseX, mouseY, button) || details.mouseClicked(mouseX, mouseY))
            {
                return true;
            }
            if (overZoomButtons(mouseX, mouseY))
            {
                // A disabled zoom button must not select the research under it.
                return true;
            }
            if (button == 0 && Gui.inside(mouseX, mouseY, viewX, viewY, viewW, viewH))
            {
                final double mx = treeX(mouseX);
                final double my = treeY(mouseY);
                for (final Node node : nodes)
                {
                    if (Gui.inside(mx, my, node.x(), node.y(), NODE_W, NODE_H))
                    {
                        Gui.clickSound();
                        selected = node.research().getId();
                        updateAction();
                        return true;
                    }
                }
                dragging = true;
                return true;
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            fail(e);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY)
    {
        if (dragging)
        {
            scrollX -= dragX / zoom;
            scrollY -= dragY / zoom;
            clampScroll();
            return true;
        }
        return details.mouseDragged(mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button)
    {
        dragging = false;
        details.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollXDelta, final double scrollYDelta)
    {
        if (Gui.inside(mouseX, mouseY, viewX, viewY, viewW, viewH))
        {
            if (hasControlDown())
            {
                zoom(Math.signum(scrollYDelta), mouseX, mouseY);
                return true;
            }
            // The same distance on screen whatever the zoom.
            if (hasShiftDown())
            {
                scrollX -= scrollYDelta * 24 / zoom;
            }
            else
            {
                scrollY -= scrollYDelta * 24 / zoom;
                scrollX -= scrollXDelta * 24 / zoom;
            }
            clampScroll();
            return true;
        }
        return details.mouseScrolled(mouseX, mouseY, scrollYDelta) || super.mouseScrolled(mouseX, mouseY, scrollXDelta, scrollYDelta);
    }

    /** "-" and "+" zoom like the corner buttons (typed characters, so it works with any keyboard layout). */
    @Override
    public boolean charTyped(final char codePoint, final int modifiers)
    {
        if (codePoint == '-')
        {
            zoomAtCentre(-1);
            return true;
        }
        if (codePoint == '+' || codePoint == '=')
        {
            zoomAtCentre(1);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    /** Escape and "Back" go back to the university's research page, like MineColonies' window. */
    @Override
    public void onClose()
    {
        try
        {
            Gui.clickSound();
            UniversityScreens.openResearch(building);
        }
        catch (final LinkageError | RuntimeException e)
        {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    private void fail(final Throwable e)
    {
        UniversityScreens.report(e);
        if (minecraft != null && minecraft.screen == this)
        {
            super.onClose();
        }
    }
}
