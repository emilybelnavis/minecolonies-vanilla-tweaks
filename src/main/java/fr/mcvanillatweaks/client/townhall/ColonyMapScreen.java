package fr.mcvanillatweaks.client.townhall;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHallView;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.client.gui.map.WindowColonyMap;
import com.minecolonies.core.colony.buildings.views.LivingBuildingView;
import com.minecolonies.core.network.messages.client.colony.ColonyListMessage.ColonyInfo;
import com.minecolonies.core.network.messages.server.colony.OpenInventoryMessage;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.citizen.CitizenInfo;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import fr.mcvanillatweaks.client.citizen.VanillaIcons;
import fr.mcvanillatweaks.client.locate.LocateBeams;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Replaces MineColonies' colony map window ({@code WindowColonyMap}) and its prestige ranking window.
 * <ul>
 *   <li>The maps kept in the town hall are drawn like MineColonies does, in a dark frame you drag and zoom.</li>
 *   <li>Huts show their block (bigger with their level), with a coloured frame for houses that still have room, a "!"
 *       for requests only the player can fill, and their range when hovered; citizens are dots (blue with a job, grey
 *       without), with a red mark when sick or hungry; players in the colony show their face.</li>
 *   <li>Zoomed far out, the other colonies of the server replace them (a banner and a name).</li>
 *   <li>A click on a hut or citizen puts a {@link LocateBeams} beam on it and closes the screen, as everywhere else.</li>
 *   <li>"Ranking" swaps the map for the list of colonies by prestige, which was a window of its own.</li>
 * </ul>
 * <pre>
 * [map] Colony map            [ranking] [chest] [back]
 * +------------------------------------------------+
 * |    (map pictures, huts, citizens)              |
 * +------------------------------------------------+
 *  scale 1x   x, z under the cursor
 * </pre>
 */
public final class ColonyMapScreen extends Screen
{
    private static final int MARGIN = 14;
    private static final int MAX_WIDTH = 720;
    private static final int MAX_HEIGHT = 440;
    private static final int HEADER = 28;
    private static final int FOOTER = 18;
    /** Below this zoom the huts and citizens are hidden and the other colonies are shown (MineColonies' 0.3 of its own scale). */
    private static final double FAR_ZOOM = 0.45;
    private static final double MIN_ZOOM = 0.12;
    private static final double MAX_ZOOM = 6;
    private static final double START_ZOOM = 1.5;
    private static final int RANK_ROW = 22;
    /** A press that moves less than this many pixels is a click, not a drag. */
    private static final double CLICK_SLOP = 3;
    private static final int FULL_BRIGHT = 0xF000F0;

    /** Zoom and centre are remembered for the session, so the map reopens where the player left it. */
    private static double lastZoom = START_ZOOM;
    @Nullable
    private static double[] lastCentre;

    private final ITownHallView building;
    private final IColonyView colony;
    private final boolean atTownHall;
    private final Tooltip tooltip = new Tooltip();
    private final ScrollArea ranking = new ScrollArea();

    private boolean showRanking;
    private double zoom = lastZoom;
    private double centreX;
    private double centreZ;
    private boolean hasBounds;
    private int left;
    private int top;
    private int width0;
    private int height0;
    private int viewX;
    private int viewY;
    private int viewW;
    private int viewH;
    private double pressX;
    private double pressY;
    private boolean dragging;
    private boolean pressInside;
    /** What the cursor is over this frame (set while drawing, used by the click). */
    @Nullable
    private ICitizenDataView hoveredCitizen;
    @Nullable
    private IBuildingView hoveredBuilding;

    public ColonyMapScreen(final ITownHallView building, final boolean atTownHall)
    {
        super(Component.translatable("mcvanillatweaks.colonymap.title"));
        this.building = building;
        this.colony = building.getColony();
        this.atTownHall = atTownHall;
        centreOnStart();
    }

    /** Opens on the ranking (used by the dev preview). */
    void showRanking()
    {
        showRanking = true;
    }

    private void centreOnStart()
    {
        final List<ITownHallView.MapEntry> maps = building.getMapDataList();
        if (maps.isEmpty())
        {
            return;
        }
        hasBounds = true;
        if (lastCentre != null)
        {
            centreX = lastCentre[0];
            centreZ = lastCentre[1];
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        final BlockPos player = mc.player == null ? BlockPos.ZERO : mc.player.blockPosition();
        if (mc.player != null && covered(maps, player))
        {
            centreX = player.getX();
            centreZ = player.getZ();
            return;
        }
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (final ITownHallView.MapEntry entry : maps)
        {
            final MapItemSavedData data = entry.mapData();
            final int half = 64 << data.scale;
            minX = Math.min(minX, data.centerX - half);
            maxX = Math.max(maxX, data.centerX + half);
            minZ = Math.min(minZ, data.centerZ - half);
            maxZ = Math.max(maxZ, data.centerZ + half);
        }
        centreX = (minX + maxX) / 2;
        centreZ = (minZ + maxZ) / 2;
    }

    private static boolean covered(final List<ITownHallView.MapEntry> maps, final BlockPos pos)
    {
        for (final ITownHallView.MapEntry entry : maps)
        {
            final MapItemSavedData data = entry.mapData();
            final int half = 64 << data.scale;
            if (Math.abs(pos.getX() - data.centerX) <= half && Math.abs(pos.getZ() - data.centerZ) <= half)
            {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init()
    {
        width0 = Math.min(MAX_WIDTH, width - 2 * MARGIN);
        height0 = Math.min(MAX_HEIGHT, height - 2 * MARGIN);
        left = (width - width0) / 2;
        top = (height - height0) / 2;
        viewX = left + 8;
        viewY = top + HEADER;
        viewW = width0 - 16;
        viewH = height0 - HEADER - FOOTER - 6;
    }

    private int buttonRight(final int index)
    {
        return left + width0 - 8 - index * 66;
    }

    private boolean overButton(final double mouseX, final double mouseY, final int index)
    {
        return Gui.inside(mouseX, mouseY, buttonRight(index) - 62, top + 6, 62, 18);
    }

    private boolean inventoryShown()
    {
        return atTownHall;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        renderTransparentBackground(g);
        Gui.panel(g, left, top, width0, height0);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        renderBackground(g, mouseX, mouseY, partialTick);
        tooltip.clear();
        hoveredCitizen = null;
        hoveredBuilding = null;
        g.renderItem(new ItemStack(Items.FILLED_MAP), left + 8, top + 7);
        g.drawString(font, Gui.fit(font, title, width0 - 250), left + 28, top + 11, Gui.LABEL, false);
        drawButtons(g, mouseX, mouseY);
        if (showRanking)
        {
            renderRanking(g, mouseX, mouseY);
        }
        else
        {
            renderMap(g, mouseX, mouseY, partialTick);
        }
        tooltip.render(g, font, mouseX, mouseY);
    }

    private void drawButtons(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        int index = 0;
        final Component back = Component.translatable(atTownHall ? "gui.back" : "gui.done");
        Gui.textButton(g, font, buttonRight(index) - 62, top + 6, 62, 18, back.getString(), true, overButton(mouseX, mouseY, index));
        index++;
        if (inventoryShown())
        {
            Gui.button(g, buttonRight(index) - 62, top + 6, 62, 18, true, overButton(mouseX, mouseY, index));
            g.renderItem(new ItemStack(Items.CHEST), buttonRight(index) - 62 + 23, top + 6 + 1);
            if (overButton(mouseX, mouseY, index))
            {
                tooltip.set(Component.translatable("mcvanillatweaks.colonymap.inventory"));
            }
            index++;
        }
        final Component rank = Component.translatable(showRanking ? "mcvanillatweaks.colonymap.show_map" : "mcvanillatweaks.colonymap.show_ranking");
        Gui.textButton(g, font, buttonRight(index) - 62, top + 6, 62, 18, shortText(rank, 56), true, overButton(mouseX, mouseY, index));
        if (overButton(mouseX, mouseY, index) && Gui.isCut(font, rank, 56))
        {
            tooltip.set(rank);
        }
    }

    private String shortText(final Component text, final int width)
    {
        return font.substrByWidth(text, width).getString();
    }

    // ---- map

    private double toScreenX(final double worldX)
    {
        return viewX + viewW / 2.0 + (worldX - centreX) * zoom;
    }

    private double toScreenY(final double worldZ)
    {
        return viewY + viewH / 2.0 + (worldZ - centreZ) * zoom;
    }

    private void renderMap(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        Gui.inset(g, viewX - 1, viewY - 1, viewW + 2, viewH + 2, 0xFF101010);
        g.enableScissor(viewX, viewY, viewX + viewW, viewY + viewH);
        if (!hasBounds)
        {
            final Component warning = Component.translatable("mcvanillatweaks.colonymap.no_maps");
            final var lines = font.split(warning, viewW - 40);
            for (int i = 0; i < lines.size(); i++)
            {
                final int w = font.width(lines.get(i));
                g.drawString(font, lines.get(i), viewX + (viewW - w) / 2, viewY + viewH / 2 - lines.size() * 5 + i * 11, Gui.WHITE, true);
            }
            g.disableScissor();
            return;
        }
        drawMaps(g);
        final boolean inView = Gui.inside(mouseX, mouseY, viewX, viewY, viewW, viewH);
        final int mx = inView ? mouseX : -1000;
        final int my = inView ? mouseY : -1000;
        if (zoom < FAR_ZOOM)
        {
            drawColonies(g, mx, my);
        }
        else
        {
            drawBuildings(g, mx, my);
            drawCitizens(g, mx, my, partialTick);
        }
        drawPlayers(g, mx, my);
        g.disableScissor();
        drawFooter(g, mouseX, mouseY);
    }

    private void drawMaps(final GuiGraphics g)
    {
        final PoseStack pose = g.pose();
        for (final ITownHallView.MapEntry entry : building.getMapDataList())
        {
            final MapItemSavedData data = entry.mapData();
            final int blocks = 128 << data.scale;
            final double x0 = toScreenX(data.centerX - blocks / 2.0);
            final double y0 = toScreenY(data.centerZ - blocks / 2.0);
            final double size = blocks * zoom;
            if (x0 > viewX + viewW || y0 > viewY + viewH || x0 + size < viewX || y0 + size < viewY)
            {
                continue;
            }
            pose.pushPose();
            pose.translate((float) x0, (float) y0, 1);
            final float scale = (float) (size / 128.0);
            pose.scale(scale, scale, 1);
            try
            {
                Minecraft.getInstance().gameRenderer.getMapRenderer().render(pose, g.bufferSource(), entry.mapId(), data, true, FULL_BRIGHT);
            }
            catch (final RuntimeException e)
            {
                MineColoniesVanillaTweaks.LOGGER.debug("Map picture not drawn", e);
            }
            g.flush();
            pose.popPose();
        }
    }

    private void drawBuildings(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        final List<IBuildingView> buildings = new ArrayList<>(colony.getClientBuildingManager().getBuildings().values());
        // Small huts last, so they stay above large ones.
        buildings.sort(Comparator.comparingInt(IBuildingView::getBuildingLevel));
        IBuildingView rangeOf = null;
        for (final IBuildingView view : buildings)
        {
            try
            {
                final BlockPos pos = view.getID();
                final int size = 11 + view.getBuildingLevel();
                final int x = (int) Math.round(toScreenX(pos.getX() + 0.5)) - size / 2;
                final int y = (int) Math.round(toScreenY(pos.getZ() + 0.5)) - size / 2;
                if (x + size < viewX || y + size < viewY || x > viewX + viewW || y > viewY + viewH)
                {
                    continue;
                }
                final boolean hovered = Gui.inside(mouseX, mouseY, x, y, size, size);
                if (view instanceof LivingBuildingView home && home.getResidents().size() < view.getBuildingLevel() && view.getRange() == 0)
                {
                    final double full = Math.max(0, Math.min(1, (double) home.getResidents().size() / view.getBuildingLevel()));
                    final int red = full <= 0.5 ? (int) (255 * (full / 0.5)) : 255;
                    final int green = full <= 0.5 ? 255 : (int) (255 * (1 - (full - 0.5) / 0.5));
                    final int colour = 0xFF000000 | (red << 16) | (green << 8) | 33;
                    g.renderOutline(x - 1, y - 1, size + 2, size + 2, colour);
                }
                if (LocateBeams.isLocated(view))
                {
                    g.renderOutline(x - 2, y - 2, size + 4, size + 4, LocateBeams.BUILDING_COLOUR);
                }
                if (hovered)
                {
                    g.fill(x, y, x + size, y + size, 0x50FFFFFF);
                }
                VanillaIcons.item(g, ColonyIcons.hut(view), x, y, size);
                final List<IRequest<?>> requests = playerRequests(view);
                if (!requests.isEmpty())
                {
                    g.drawString(font, "!", x + size - 3, y - 4, Gui.GOLD, true);
                }
                if (hovered)
                {
                    hoveredBuilding = view;
                    rangeOf = view;
                    tooltip.set(buildingTooltip(view, requests));
                }
            }
            catch (final LinkageError | RuntimeException e)
            {
                MineColoniesVanillaTweaks.LOGGER.debug("Hut left out of the map", e);
            }
        }
        if (rangeOf != null && rangeOf.getRange() != 0)
        {
            final BlockPos pos = rangeOf.getPosition();
            final int range = rangeOf.getRange();
            final int x1 = (int) Math.round(toScreenX(pos.getX() - range));
            final int y1 = (int) Math.round(toScreenY(pos.getZ() - range));
            final int x2 = (int) Math.round(toScreenX(pos.getX() + range + 1));
            final int y2 = (int) Math.round(toScreenY(pos.getZ() + range + 1));
            g.renderOutline(x1, y1, x2 - x1, y2 - y1, 0xFFFF5555);
        }
    }

    private List<Component> buildingTooltip(final IBuildingView view, final List<IRequest<?>> requests)
    {
        final List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(view.getBuildingDisplayName()).append(Component.literal(" " + view.getBuildingLevel()).withStyle(ChatFormatting.GRAY)));
        lines.add(Component.translatable("mcvanillatweaks.colonymap.coordinates", view.getID().getX(), view.getID().getZ()).withStyle(ChatFormatting.GRAY));
        final int residents = view instanceof LivingBuildingView home ? home.getResidents().size() : view.getAllAssignedCitizens().size();
        lines.add(Component.translatable("mcvanillatweaks.colonymap.citizens", residents).withStyle(ChatFormatting.GRAY));
        for (final int id : view.getAllAssignedCitizens())
        {
            final ICitizenDataView citizen = colony.getCitizen(id);
            if (citizen != null)
            {
                lines.add(Component.literal("  " + citizen.getName()).withStyle(ChatFormatting.WHITE));
            }
        }
        if (!requests.isEmpty())
        {
            lines.add(Component.translatable("mcvanillatweaks.colonymap.requests", requests.size()).withStyle(ChatFormatting.GOLD));
            for (int i = 0; i < Math.min(5, requests.size()); i++)
            {
                try
                {
                    lines.add(Component.literal("  ").append(requests.get(i).getShortDisplayString()).withStyle(ChatFormatting.YELLOW));
                }
                catch (final RuntimeException ignored)
                {
                    // a request that cannot be described is left out
                }
            }
        }
        lines.add(Component.translatable(LocateBeams.isLocated(view) ? "mcvanillatweaks.clipboard.buildings.click_stop" : "mcvanillatweaks.clipboard.buildings.click")
                    .withStyle(ChatFormatting.YELLOW));
        return lines;
    }

    /** The requests of this hut only the player can fill, as MineColonies' map lists them. */
    private List<IRequest<?>> playerRequests(final IBuildingView view)
    {
        final List<IRequest<?>> found = new ArrayList<>();
        try
        {
            final IRequestManager manager = colony.getRequestManager();
            if (manager == null)
            {
                return found;
            }
            for (final IToken<?> token : new HashSet<>(manager.getPlayerResolver().getAllAssignedRequests()))
            {
                final IRequest<?> request = manager.getRequestForToken(token);
                if (request != null && request.getRequester().getLocation().getInDimensionLocation().equals(view.getPosition()))
                {
                    found.add(request);
                }
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            return List.of();
        }
        return found;
    }

    private void drawCitizens(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        for (final ICitizenDataView citizen : colony.getCitizens().values())
        {
            try
            {
                final Vec3 pos = LocateBeams.position(citizen, partialTick);
                if (pos == null)
                {
                    continue;
                }
                final boolean job = !citizen.getJob().isEmpty();
                final int size = job ? 6 : 4;
                final int x = (int) Math.round(toScreenX(pos.x)) - size / 2;
                final int y = (int) Math.round(toScreenY(pos.z)) - size / 2;
                if (x + size < viewX || y + size < viewY || x > viewX + viewW || y > viewY + viewH)
                {
                    continue;
                }
                final boolean located = LocateBeams.isLocated(citizen);
                final boolean hovered = Gui.inside(mouseX, mouseY, x - 2, y - 2, size + 4, size + 4);
                g.fill(x - 1, y - 1, x + size + 1, y + size + 1, located ? LocateBeams.CITIZEN_COLOUR : 0xFF000000);
                g.fill(x, y, x + size, y + size, hovered ? Gui.WHITE : job ? 0xFF55AAFF : 0xFFAAAAAA);
                if (citizen.isSick())
                {
                    g.fill(x + size - 1, y - 3, x + size + 2, y, Gui.RED);
                }
                else if (citizen.getSaturation() < 6.0)
                {
                    g.fill(x + size - 1, y - 3, x + size + 2, y, Gui.GOLD);
                }
                if (hovered)
                {
                    hoveredCitizen = citizen;
                    hoveredBuilding = null;
                    tooltip.set(citizenTooltip(citizen, located));
                }
            }
            catch (final LinkageError | RuntimeException e)
            {
                MineColoniesVanillaTweaks.LOGGER.debug("Citizen left out of the map", e);
            }
        }
    }

    private List<Component> citizenTooltip(final ICitizenDataView citizen, final boolean located)
    {
        final List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(citizen.getName()));
        final Component job = CitizenInfo.jobName(citizen);
        if (job != null)
        {
            lines.add(job.copy().withStyle(ChatFormatting.GRAY));
        }
        if (citizen.isSick())
        {
            lines.add(Component.translatable("mcvanillatweaks.colonymap.sick").withStyle(ChatFormatting.RED));
        }
        else if (citizen.getSaturation() < 6.0)
        {
            lines.add(Component.translatable("mcvanillatweaks.colonymap.hungry").withStyle(ChatFormatting.GOLD));
        }
        try
        {
            if (citizen.hasBlockingInteractions() && !citizen.getOrderedInteractions().isEmpty())
            {
                lines.add(citizen.getOrderedInteractions().get(0).getInquiry().copy().withStyle(ChatFormatting.RED));
            }
        }
        catch (final LinkageError | RuntimeException ignored)
        {
            // no dialogue to show
        }
        lines.add(Component.translatable(located ? "mcvanillatweaks.clipboard.buildings.click_stop" : "mcvanillatweaks.clipboard.buildings.click")
                    .withStyle(ChatFormatting.YELLOW));
        return lines;
    }

    private void drawPlayers(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
        {
            return;
        }
        for (final Player player : mc.level.players())
        {
            if (!(player instanceof AbstractClientPlayer client) || !colony.isCoordInColony(mc.level, player.blockPosition()))
            {
                continue;
            }
            final int x = (int) Math.round(toScreenX(player.getX())) - 5;
            final int y = (int) Math.round(toScreenY(player.getZ())) - 5;
            if (x + 10 < viewX || y + 10 < viewY || x > viewX + viewW || y > viewY + viewH)
            {
                continue;
            }
            g.renderOutline(x - 1, y - 1, 12, 12, player == mc.player ? Gui.WHITE : 0xFF000000);
            PlayerFaceRenderer.draw(g, client.getSkin().texture(), x, y, 10);
            if (Gui.inside(mouseX, mouseY, x, y, 10, 10))
            {
                tooltip.set(player.getDisplayName());
            }
        }
    }

    private void drawColonies(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        for (final ColonyInfo info : WindowColonyMap.colonies)
        {
            final BlockPos centre = info.getCenter();
            final int size = info.getCitizencount() < 25 ? 12 : info.getCitizencount() < 75 ? 16 : 20;
            final int x = (int) Math.round(toScreenX(centre.getX())) - size / 2;
            final int y = (int) Math.round(toScreenY(centre.getZ())) - size / 2;
            if (x + size < viewX || y + size < viewY || x > viewX + viewW || y > viewY + viewH)
            {
                continue;
            }
            VanillaIcons.item(g, new ItemStack(Items.BELL), x, y, size);
            final Component name = Component.literal(info.getName());
            g.drawString(font, name, x + size / 2 - font.width(name) / 2, y + size + 1, Gui.WHITE, true);
            if (Gui.inside(mouseX, mouseY, x, y, size, size))
            {
                tooltip.set(List.of(name, Component.translatable("mcvanillatweaks.colonymap.owner", info.getOwner()).withStyle(ChatFormatting.GRAY),
                  Component.translatable("mcvanillatweaks.colonymap.coordinates", centre.getX(), centre.getZ()).withStyle(ChatFormatting.GRAY),
                  Component.translatable("mcvanillatweaks.colonymap.citizens", info.getCitizencount()).withStyle(ChatFormatting.GRAY)));
            }
        }
    }

    private void drawFooter(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        final int y = viewY + viewH + 7;
        final String scale = zoom >= 1 ? String.format("%.1fx", zoom) : String.format("1/%.0fx", 1 / zoom);
        g.drawString(font, Component.translatable("mcvanillatweaks.colonymap.scale", scale), viewX, y, Gui.LABEL_SOFT, false);
        if (Gui.inside(mouseX, mouseY, viewX, viewY, viewW, viewH))
        {
            final Component pos = Component.translatable("mcvanillatweaks.colonymap.coordinates", (int) Math.floor((mouseX - viewX - viewW / 2.0) / zoom + centreX),
              (int) Math.floor((mouseY - viewY - viewH / 2.0) / zoom + centreZ));
            g.drawString(font, pos, viewX + viewW - font.width(pos), y, Gui.LABEL_SOFT, false);
        }
        else
        {
            final Component hint = Component.translatable("mcvanillatweaks.colonymap.hint");
            g.drawString(font, Gui.fit(font, hint, viewW - 80), viewX + 70, y, Gui.LABEL_SOFT, false);
        }
    }

    // ---- ranking

    private void renderRanking(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        final List<ColonyInfo> colonies = new ArrayList<>(WindowColonyMap.colonies);
        // MineColonies sorts the other way round, which puts the smallest colony first.
        colonies.sort(Comparator.comparingInt(ColonyInfo::getPrestige).reversed());
        Gui.inset(g, viewX - 1, viewY - 1, viewW + 2, viewH + 2);
        ranking.set(viewX, viewY, viewW, viewH, colonies.size() * RANK_ROW, RANK_ROW);
        ranking.begin(g);
        if (colonies.isEmpty())
        {
            g.drawString(font, Component.translatable("mcvanillatweaks.colonymap.ranking.none"), ranking.listX() + 6, ranking.visibleTop() + 7, Gui.LIST_SOFT, true);
        }
        for (int i = 0; i < colonies.size(); i++)
        {
            final int rowY = ranking.listY() + i * RANK_ROW;
            if (rowY + RANK_ROW < ranking.visibleTop() || rowY > ranking.visibleBottom())
            {
                continue;
            }
            final ColonyInfo info = colonies.get(i);
            final boolean mine = info.getId() == colony.getID();
            ranking.hover(g, mouseX, mouseY, rowY, RANK_ROW);
            final String rank = "#" + (i + 1);
            g.drawString(font, rank, ranking.listX() + 6, rowY + 7, i < 3 ? Gui.GOLD : Gui.LIST_SOFT, true);
            VanillaIcons.item(g, new ItemStack(Items.BELL), ranking.listX() + 34, rowY + 3, 16);
            final int right = ranking.listX() + ranking.listWidth() - 8;
            final String prestige = String.valueOf(info.getPrestige());
            g.drawString(font, prestige, right - font.width(prestige), rowY + 7, Gui.YELLOW, true);
            final Component citizens = Component.translatable("mcvanillatweaks.colonymap.citizens", info.getCitizencount());
            final int citizensX = right - 64 - font.width(citizens);
            g.drawString(font, citizens, citizensX, rowY + 7, Gui.LIST_SOFT, true);
            final Component name = Component.literal(info.getName() + "  ").append(Component.literal(info.getOwner()).withStyle(ChatFormatting.GRAY));
            g.drawString(font, Gui.fit(font, name, citizensX - (ranking.listX() + 58) - 8), ranking.listX() + 58, rowY + 7, mine ? Gui.GREEN : Gui.WHITE, true);
        }
        ranking.end(g);
        final Component head = Component.translatable("mcvanillatweaks.colonymap.ranking.hint");
        g.drawString(font, Gui.fit(font, head, viewW), viewX, viewY + viewH + 7, Gui.LABEL_SOFT, false);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (button == 0)
        {
            if (overButton(mouseX, mouseY, 0))
            {
                Gui.clickSound();
                onClose();
                return true;
            }
            int index = 1;
            if (inventoryShown())
            {
                if (overButton(mouseX, mouseY, index))
                {
                    Gui.clickSound();
                    new OpenInventoryMessage(building).sendToServer();
                    return true;
                }
                index++;
            }
            if (overButton(mouseX, mouseY, index))
            {
                Gui.clickSound();
                showRanking = !showRanking;
                return true;
            }
            if (showRanking && ranking.mouseClicked(mouseX, mouseY))
            {
                return true;
            }
            pressX = mouseX;
            pressY = mouseY;
            dragging = false;
            pressInside = !showRanking && hasBounds && Gui.inside(mouseX, mouseY, viewX, viewY, viewW, viewH);
            if (pressInside)
            {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dx, final double dy)
    {
        if (showRanking && ranking.mouseDragged(mouseY))
        {
            return true;
        }
        if (button == 0 && pressInside)
        {
            if (!dragging && Math.hypot(mouseX - pressX, mouseY - pressY) > CLICK_SLOP)
            {
                dragging = true;
            }
            if (dragging)
            {
                centreX -= dx / zoom;
                centreZ -= dy / zoom;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button)
    {
        ranking.mouseReleased();
        if (button == 0 && pressInside)
        {
            pressInside = false;
            if (!dragging)
            {
                clickMap();
            }
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void clickMap()
    {
        boolean closes = false;
        if (hoveredCitizen != null)
        {
            Gui.clickSound();
            closes = LocateBeams.toggle(hoveredCitizen);
        }
        else if (hoveredBuilding != null)
        {
            Gui.clickSound();
            closes = LocateBeams.toggle(hoveredBuilding);
        }
        if (closes)
        {
            minecraft.setScreen(null);
        }
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        if (showRanking)
        {
            return ranking.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (hasBounds && Gui.inside(mouseX, mouseY, viewX, viewY, viewW, viewH))
        {
            // Zoom around the cursor: the block under it stays under it.
            final double worldX = (mouseX - viewX - viewW / 2.0) / zoom + centreX;
            final double worldZ = (mouseY - viewY - viewH / 2.0) / zoom + centreZ;
            zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * Math.pow(1.2, scrollY)));
            centreX = worldX - (mouseX - viewX - viewW / 2.0) / zoom;
            centreZ = worldZ - (mouseY - viewY - viewH / 2.0) / zoom;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose()
    {
        if (atTownHall)
        {
            building.openGui(false);
        }
        else
        {
            super.onClose();
        }
    }

    @Override
    public void removed()
    {
        lastZoom = zoom;
        if (hasBounds)
        {
            lastCentre = new double[] {centreX, centreZ};
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
