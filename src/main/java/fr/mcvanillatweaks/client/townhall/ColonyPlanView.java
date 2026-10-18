package fr.mcvanillatweaks.client.townhall;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.mojang.blaze3d.platform.NativeImage;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import fr.mcvanillatweaks.client.citizen.VanillaIcons;
import fr.mcvanillatweaks.client.locate.LocateBeams;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A plan of a colony seen from above, north up: the ground drawn like a vanilla map from the chunks this client has
 * loaded (darker where it has none), each hut's block on it (bigger with its level), and the players standing in the
 * colony as their face. Drag to move, scroll to zoom; a click on a hut puts a {@link LocateBeams} beam on it. Needs no
 * map item, so it works in any colony the player visits (the colony guide, {@link ColonyGuideScreen}).
 */
final class ColonyPlanView
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(MineColoniesVanillaTweaks.MOD_ID, "colony_plan");
    /** Blocks around the huts, so the outer ones are not at the edge. */
    private static final int MARGIN = 32;
    private static final int MAX_SIZE = 768;
    private static final int UNLOADED = 0xFF1E1E1E;
    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 8;
    private static final double CLICK_SLOP = 3;

    private final Minecraft mc = Minecraft.getInstance();
    private final Font font = mc.font;
    private final IColonyView colony;
    /** World coordinates of the texture's top left block, and its size. */
    private int originX;
    private int originZ;
    private int sizeX;
    private int sizeZ;
    private boolean textured;
    private boolean fitted;
    private double zoom = 1;
    private double centreX;
    private double centreZ;
    private int viewX;
    private int viewY;
    private int viewW;
    private int viewH;
    private double pressX;
    private double pressY;
    private boolean pressInside;
    private boolean dragging;
    @Nullable
    private IBuildingView hovered;

    ColonyPlanView(final IColonyView colony)
    {
        this.colony = colony;
        bounds();
        draw();
    }

    // ------------------------------------------------------------------ the ground

    /** The area to draw: the huts with a margin (the colony's centre when it has none), at most {@link #MAX_SIZE}. */
    private void bounds()
    {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (final IBuildingView building : colony.getClientBuildingManager().getBuildings().values())
        {
            final BlockPos pos = building.getID();
            minX = Math.min(minX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        if (minX == Integer.MAX_VALUE)
        {
            final BlockPos centre = colony.getCenter();
            minX = maxX = centre.getX();
            minZ = maxZ = centre.getZ();
        }
        minX -= MARGIN;
        minZ -= MARGIN;
        maxX += MARGIN;
        maxZ += MARGIN;
        final int middleX = (minX + maxX) / 2;
        final int middleZ = (minZ + maxZ) / 2;
        sizeX = Math.min(MAX_SIZE, maxX - minX + 1);
        sizeZ = Math.min(MAX_SIZE, maxZ - minZ + 1);
        originX = middleX - sizeX / 2;
        originZ = middleZ - sizeZ / 2;
        centreX = middleX;
        centreZ = middleZ;
    }

    /** Colours each column from its top block, shaded by the height step to the north, like a vanilla map. */
    private void draw()
    {
        final ClientLevel level = mc.level;
        final NativeImage image = new NativeImage(NativeImage.Format.RGBA, sizeX, sizeZ, false);
        final boolean sameWorld = level != null && level.dimension() == colony.getDimension();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final int[] previous = new int[sizeX];
        for (int dz = -1; dz < sizeZ; dz++)
        {
            for (int dx = 0; dx < sizeX; dx++)
            {
                final int x = originX + dx;
                final int z = originZ + dz;
                if (!sameWorld || !level.hasChunk(x >> 4, z >> 4))
                {
                    previous[dx] = Integer.MIN_VALUE;
                    if (dz >= 0)
                    {
                        image.setPixelRGBA(dx, dz, UNLOADED);
                    }
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                BlockState state = level.getBlockState(pos.set(x, y, z));
                MapColor colour = state.getMapColor(level, pos);
                // Glass and the like have no map colour: look under them.
                for (int i = 0; i < 8 && colour == MapColor.NONE && y > level.getMinBuildHeight(); i++)
                {
                    y--;
                    state = level.getBlockState(pos.set(x, y, z));
                    colour = state.getMapColor(level, pos);
                }
                if (dz >= 0)
                {
                    final int north = previous[dx];
                    final MapColor.Brightness brightness = north == Integer.MIN_VALUE || north == y || colour == MapColor.WATER
                                                             ? MapColor.Brightness.NORMAL : y > north ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
                    image.setPixelRGBA(dx, dz, colour == MapColor.NONE ? UNLOADED : colour.calculateRGBColor(brightness));
                }
                previous[dx] = y;
            }
        }
        mc.getTextureManager().register(TEXTURE, new DynamicTexture(image));
        textured = true;
    }

    /** Frees the picture (the screen closed). */
    void close()
    {
        if (textured)
        {
            mc.getTextureManager().release(TEXTURE);
            textured = false;
        }
    }

    // ------------------------------------------------------------------ drawing

    private double toScreenX(final double worldX)
    {
        return viewX + viewW / 2.0 + (worldX - centreX) * zoom;
    }

    private double toScreenY(final double worldZ)
    {
        return viewY + viewH / 2.0 + (worldZ - centreZ) * zoom;
    }

    void render(final GuiGraphics g, final int x, final int y, final int width, final int height, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        viewX = x + 1;
        viewY = y + 1;
        viewW = width - 2;
        viewH = height - 2 - 14;
        if (!fitted)
        {
            // The whole colony in view to start with.
            fitted = true;
            zoom = Math.max(MIN_ZOOM, Math.min(4, Math.min((double) viewW / sizeX, (double) viewH / sizeZ)));
        }
        Gui.inset(g, x, y, width, height - 14, 0xFF101010);
        g.enableScissor(viewX, viewY, viewX + viewW, viewY + viewH);
        if (textured)
        {
            g.pose().pushPose();
            g.pose().translate((float) toScreenX(originX), (float) toScreenY(originZ), 0);
            g.pose().scale((float) zoom, (float) zoom, 1);
            g.blit(TEXTURE, 0, 0, 0.0F, 0.0F, sizeX, sizeZ, sizeX, sizeZ);
            g.pose().popPose();
        }
        final boolean inView = Gui.inside(mouseX, mouseY, viewX, viewY, viewW, viewH);
        hovered = null;
        drawBuildings(g, inView ? mouseX : -1000, inView ? mouseY : -1000, tooltip);
        drawPlayers(g, inView ? mouseX : -1000, inView ? mouseY : -1000, tooltip);
        g.drawString(font, "N ↑", viewX + 5, viewY + 5, Gui.WHITE, true);
        g.disableScissor();
        final int footerY = y + height - 10;
        if (inView)
        {
            final Component pos = Component.translatable("mcvanillatweaks.colonymap.coordinates", (int) Math.floor((mouseX - viewX - viewW / 2.0) / zoom + centreX),
              (int) Math.floor((mouseY - viewY - viewH / 2.0) / zoom + centreZ));
            g.drawString(font, pos, x + width - font.width(pos), footerY, Gui.LABEL_SOFT, false);
        }
        else
        {
            g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.guide.plan.hint"), width), x, footerY, Gui.LABEL_SOFT, false);
        }
    }

    private void drawBuildings(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final List<IBuildingView> buildings = new ArrayList<>(colony.getClientBuildingManager().getBuildings().values());
        // Small huts last, so they stay above large ones.
        buildings.sort(Comparator.comparingInt(IBuildingView::getBuildingLevel).reversed());
        for (final IBuildingView view : buildings)
        {
            try
            {
                final BlockPos pos = view.getID();
                final int size = 10 + view.getBuildingLevel();
                final int x = (int) Math.round(toScreenX(pos.getX() + 0.5)) - size / 2;
                final int y = (int) Math.round(toScreenY(pos.getZ() + 0.5)) - size / 2;
                if (x + size < viewX || y + size < viewY || x > viewX + viewW || y > viewY + viewH)
                {
                    continue;
                }
                final boolean over = Gui.inside(mouseX, mouseY, x, y, size, size);
                g.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0x90000000);
                if (LocateBeams.isLocated(view))
                {
                    g.renderOutline(x - 2, y - 2, size + 4, size + 4, LocateBeams.BUILDING_COLOUR);
                }
                if (over)
                {
                    g.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0x70FFFFFF);
                    hovered = view;
                }
                VanillaIcons.item(g, ColonyIcons.hut(view), x, y, size);
                if (over)
                {
                    tooltip.set(tooltip(view));
                }
            }
            catch (final LinkageError | RuntimeException e)
            {
                MineColoniesVanillaTweaks.LOGGER.debug("Hut left out of the plan", e);
            }
        }
    }

    private List<Component> tooltip(final IBuildingView view)
    {
        final List<Component> lines = new ArrayList<>();
        lines.add(LocateBeams.name(view));
        lines.add(Component.translatable("mcvanillatweaks.hut.level", view.getBuildingLevel(), view.getBuildingMaxLevel()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("mcvanillatweaks.colonymap.coordinates", view.getID().getX(), view.getID().getZ()).withStyle(ChatFormatting.GRAY));
        if (mc.player != null && mc.level != null && mc.level.dimension() == colony.getDimension())
        {
            final double dx = view.getID().getX() + 0.5 - mc.player.getX();
            final double dz = view.getID().getZ() + 0.5 - mc.player.getZ();
            lines.add(Component.translatable("mcvanillatweaks.guide.distance", (int) Math.sqrt(dx * dx + dz * dz)).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable(LocateBeams.isLocated(view) ? "mcvanillatweaks.clipboard.buildings.click_stop" : "mcvanillatweaks.clipboard.buildings.click")
                    .withStyle(ChatFormatting.YELLOW));
        return lines;
    }

    private void drawPlayers(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        if (mc.level == null || mc.level.dimension() != colony.getDimension())
        {
            return;
        }
        for (final Player player : mc.level.players())
        {
            if (!(player instanceof AbstractClientPlayer client) || (player != mc.player && !colony.isCoordInColony(mc.level, player.blockPosition())))
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
                tooltip.set(player == mc.player ? Component.translatable("mcvanillatweaks.guide.plan.you") : player.getDisplayName());
            }
        }
    }

    // ------------------------------------------------------------------ input

    boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (button != 0 || !Gui.inside(mouseX, mouseY, viewX, viewY, viewW, viewH))
        {
            return false;
        }
        pressX = mouseX;
        pressY = mouseY;
        pressInside = true;
        dragging = false;
        return true;
    }

    boolean mouseDragged(final double mouseX, final double mouseY, final double dx, final double dy)
    {
        if (!pressInside)
        {
            return false;
        }
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

    /** Ends a press; a click (no drag) on a hut toggles its beam. Returns true when a beam was added. */
    boolean mouseReleased()
    {
        final boolean click = pressInside && !dragging;
        pressInside = false;
        dragging = false;
        if (click && hovered != null)
        {
            Gui.clickSound();
            return LocateBeams.toggle(hovered);
        }
        return false;
    }

    boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        if (!Gui.inside(mouseX, mouseY, viewX, viewY, viewW, viewH))
        {
            return false;
        }
        // Zoom around the cursor: the block under it stays under it.
        final double worldX = (mouseX - viewX - viewW / 2.0) / zoom + centreX;
        final double worldZ = (mouseY - viewY - viewH / 2.0) / zoom + centreZ;
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * Math.pow(1.2, delta)));
        centreX = worldX - (mouseX - viewX - viewW / 2.0) / zoom;
        centreZ = worldZ - (mouseY - viewY - viewH / 2.0) / zoom;
        return true;
    }
}
