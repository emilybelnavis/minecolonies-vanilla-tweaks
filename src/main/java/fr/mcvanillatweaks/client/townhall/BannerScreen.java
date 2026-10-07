package fr.mcvanillatweaks.client.townhall;

import com.minecolonies.api.colony.IColonyView;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces MineColonies' colony flag editor ({@code WindowBannerPicker}) with a loom-like screen: the flag as it will
 * look, its layers (the base colour, then up to 6 patterns), the 16 dyes and the patterns drawn with the loom's
 * sprites. Same rules as MineColonies: the base layer only changes colour, picking a pattern on the empty row adds a
 * layer, the town hall level unlocks patterns (the list MineColonies computed is reused), "done" calls
 * {@code setColonyFlag}, and both buttons go back to the town hall window. Supporter-only patterns are left out.
 * <pre>
 * Choose a colour and a pattern...
 * +------+ +----------------+  Colour
 * | flag | | Base: white    |  [dyes x 8]
 * |      | | 1 Red bordure  |  [dyes x 8]
 * |      | | + New layer    |  Pattern
 * +------+ +----------------+  [loom patterns grid]
 * [Cancel]                [Done]
 * </pre>
 */
public final class BannerScreen extends Screen
{
    private static final int WIDTH = 380;
    private static final int HEIGHT = 214;
    private static final int MAX_LAYERS = 7;
    private static final int LAYER_ROW = 18;
    private static final int PREVIEW_WIDTH = 70;
    private static final int LAYERS_WIDTH = 112;
    private static final int CELL = 14;
    private static final int PATTERN_COLUMNS = 10;
    private static final int PATTERN_ROWS = 5;
    private static final ResourceLocation PATTERN = ResourceLocation.withDefaultNamespace("container/loom/pattern");
    private static final ResourceLocation PATTERN_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("container/loom/pattern_highlighted");
    private static final ResourceLocation PATTERN_SELECTED = ResourceLocation.withDefaultNamespace("container/loom/pattern_selected");

    /** Layer selected on opening, for the dev preview. */
    public static int previewActive;

    private final IColonyView colony;
    private final Runnable back;
    private final List<Holder<BannerPattern>> patterns;
    private final List<BannerPatternLayers.Layer> layers;
    private final Holder<BannerPattern> base;
    private final Tooltip tooltip = new Tooltip();
    private ModelPart flag;
    private DyeColor selected;
    private int active;
    private int patternRow;
    private int left;
    private int top;

    /**
     * @param patterns the patterns MineColonies offers at this town hall level.
     * @param base     the base pattern (the first layer's).
     * @param back     reopens the town hall window.
     */
    public BannerScreen(final IColonyView colony, final List<Holder<BannerPattern>> patterns, final Holder<BannerPattern> base, final Runnable back)
    {
        super(Component.translatable("com.minecolonies.coremod.gui.flag.choose"));
        this.colony = colony;
        this.back = back;
        this.patterns = patterns;
        this.layers = new ArrayList<>(colony.getColonyFlag().layers());
        this.base = base;
        if (layers.isEmpty())
        {
            layers.add(new BannerPatternLayers.Layer(base, DyeColor.WHITE));
        }
        this.selected = layers.get(0).color();
        if (previewActive > 0)
        {
            selectLayer(Math.min(previewActive, layers.size()));
        }
    }

    @Override
    protected void init()
    {
        flag = minecraft.getEntityModels().bakeLayer(ModelLayers.BANNER).getChild("flag");
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        final int bw = (WIDTH - 22) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> back.run()).bounds(left + 8, top + HEIGHT - 28, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> done()).bounds(left + WIDTH - 8 - bw, top + HEIGHT - 28, bw, 20).build());
    }

    private void done()
    {
        try
        {
            final BannerPatternLayers.Builder builder = new BannerPatternLayers.Builder();
            for (final BannerPatternLayers.Layer layer : layers)
            {
                builder.add(layer);
            }
            colony.setColonyFlag(builder.build());
        }
        catch (final LinkageError | RuntimeException e)
        {
            TownHallScreens.report(e);
        }
        back.run();
    }

    // ------------------------------------------------------------------ editing (MineColonies' rules)

    private void selectLayer(final int layer)
    {
        active = layer;
        if (layer >= layers.size())
        {
            selected = layers.get(0).color() == DyeColor.BLACK ? DyeColor.WHITE : DyeColor.BLACK;
        }
        else
        {
            selected = layers.get(layer).color();
        }
    }

    private void setLayer(@Nullable Holder<BannerPattern> pattern, final DyeColor color)
    {
        if (pattern == null)
        {
            if (active == layers.size())
            {
                return;
            }
            pattern = active == 0 ? base : layers.get(active).pattern();
        }
        if (active == layers.size())
        {
            layers.add(new BannerPatternLayers.Layer(pattern, color));
        }
        else
        {
            layers.set(active, new BannerPatternLayers.Layer(pattern, color));
        }
    }

    // ------------------------------------------------------------------ geometry

    private int layersX()
    {
        return left + 8 + PREVIEW_WIDTH + 6;
    }

    private int bodyTop()
    {
        return top + 30;
    }

    private int rightX()
    {
        return layersX() + LAYERS_WIDTH + 10;
    }

    private int paletteY()
    {
        return bodyTop() + 12;
    }

    private int patternsY()
    {
        return paletteY() + 2 * 18 + 16;
    }

    private int layerRows()
    {
        return Math.min(MAX_LAYERS, layers.size() + 1);
    }

    private int maxPatternRow()
    {
        return Math.max(0, (patterns.size() + PATTERN_COLUMNS - 1) / PATTERN_COLUMNS - PATTERN_ROWS);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        renderTransparentBackground(g);
        Gui.panel(g, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        tooltip.clear();
        g.drawString(font, Gui.fit(font, getTitle(), WIDTH - 16), left + 8, top + 10, Gui.LABEL, false);

        // The flag.
        final int bodyHeight = HEIGHT - 30 - 36;
        Gui.inset(g, left + 8, bodyTop(), PREVIEW_WIDTH, bodyHeight);
        final int flagHeight = Math.min(bodyHeight - 16, 110);
        final int flagWidth = flagHeight / 2;
        drawFlag(g, layers, left + 8 + (PREVIEW_WIDTH - flagWidth) / 2, bodyTop() + (bodyHeight - flagHeight) / 2, flagHeight);

        renderLayers(g, mouseX, mouseY, bodyHeight);
        renderPalette(g, mouseX, mouseY);
        renderPatterns(g, mouseX, mouseY);
        tooltip.render(g, font, mouseX, mouseY);
    }

    private void renderLayers(final GuiGraphics g, final int mouseX, final int mouseY, final int bodyHeight)
    {
        final int lx = layersX();
        Gui.inset(g, lx, bodyTop(), LAYERS_WIDTH, bodyHeight);
        for (int i = 0; i < layerRows(); i++)
        {
            final int ry = bodyTop() + 2 + i * LAYER_ROW;
            final boolean hovered = Gui.inside(mouseX, mouseY, lx + 1, ry, LAYERS_WIDTH - 2, LAYER_ROW);
            if (i == active)
            {
                g.fill(lx + 1, ry, lx + LAYERS_WIDTH - 1, ry + LAYER_ROW, Gui.ROW_SELECTED);
            }
            else if (hovered)
            {
                g.fill(lx + 1, ry, lx + LAYERS_WIDTH - 1, ry + LAYER_ROW, Gui.ROW_HOVER);
            }
            final Component label;
            if (i < layers.size())
            {
                final BannerPatternLayers.Layer layer = layers.get(i);
                g.renderItem(new ItemStack(DyeItem.byColor(layer.color())), lx + 3, ry + 1);
                label = i == 0
                          ? Component.translatable("com.minecolonies.coremod.gui.flag.base_layer").append(" : ")
                              .append(Component.translatable("color.minecraft." + layer.color().getName()))
                          : Component.translatable(layer.pattern().value().translationKey() + "." + layer.color().getName());
            }
            else
            {
                label = Component.translatable("mcvanillatweaks.banner.new_layer");
            }
            final int textWidth = LAYERS_WIDTH - 24 - (i > 0 && i < layers.size() ? 14 : 0);
            g.drawString(font, Gui.fit(font, label, textWidth), lx + 22, ry + 5, i < layers.size() ? Gui.WHITE : Gui.LIST_SOFT, true);
            if (i > 0 && i < layers.size())
            {
                // Remove this layer.
                final int bx = lx + LAYERS_WIDTH - 14;
                final boolean overRemove = Gui.inside(mouseX, mouseY, bx - 2, ry + 3, 12, 12);
                g.drawString(font, "✖", bx, ry + 5, overRemove ? Gui.RED : Gui.LIST_SOFT, true);
                if (overRemove)
                {
                    tooltip.set(Component.translatable("mcvanillatweaks.banner.remove"));
                }
            }
            if (hovered && Gui.isCut(font, label, textWidth))
            {
                tooltip.set(label);
            }
        }
    }

    private void renderPalette(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        g.drawString(font, Component.translatable("mcvanillatweaks.banner.colour"), rightX() + 1, bodyTop(), Gui.LABEL, false);
        final DyeColor[] colours = DyeColor.values();
        for (int i = 0; i < colours.length; i++)
        {
            final int sx = rightX() + (i % 8) * 18;
            final int sy = paletteY() + (i / 8) * 18;
            Gui.slot(g, sx, sy);
            if (colours[i] == selected)
            {
                g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF55FF55);
                g.fill(sx + 2, sy + 2, sx + 16, sy + 16, 0xFF8B8B8B);
            }
            g.renderItem(new ItemStack(DyeItem.byColor(colours[i])), sx + 1, sy + 1);
            if (Gui.inside(mouseX, mouseY, sx, sy, 18, 18))
            {
                Gui.slotHighlight(g, sx, sy);
                tooltip.set(Component.translatable("color.minecraft." + colours[i].getName()));
            }
        }
    }

    private void renderPatterns(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        g.drawString(font, Component.translatable("mcvanillatweaks.banner.pattern"), rightX() + 1, patternsY() - 12, Gui.LABEL, false);
        final boolean enabled = active > 0;
        if (!enabled)
        {
            int ty = patternsY() + 4;
            for (final var line : font.split(Component.translatable("mcvanillatweaks.banner.base_hint"), PATTERN_COLUMNS * CELL + 4))
            {
                g.drawString(font, line, rightX() + 1, ty, Gui.LABEL_SOFT, false);
                ty += 10;
            }
            return;
        }
        Lighting.setupForFlatItems();
        for (int row = 0; row < PATTERN_ROWS; row++)
        {
            for (int column = 0; column < PATTERN_COLUMNS; column++)
            {
                final int index = (patternRow + row) * PATTERN_COLUMNS + column;
                if (index >= patterns.size())
                {
                    continue;
                }
                final int cx = rightX() + column * CELL;
                final int cy = patternsY() + row * CELL;
                final boolean hovered = Gui.inside(mouseX, mouseY, cx, cy, CELL, CELL);
                final boolean current = active < layers.size() && layers.get(active).pattern().equals(patterns.get(index));
                g.blitSprite(current ? PATTERN_SELECTED : hovered ? PATTERN_HIGHLIGHTED : PATTERN, cx, cy, CELL, CELL);
                renderPattern(g, patterns.get(index), cx, cy);
                if (hovered)
                {
                    tooltip.set(List.of(Component.translatable(patterns.get(index).value().translationKey() + "." + selected.getName()),
                      Component.translatable(active == layers.size() ? "mcvanillatweaks.banner.add" : "mcvanillatweaks.banner.replace")
                        .withStyle(ChatFormatting.YELLOW)));
                }
            }
        }
        Lighting.setupFor3DItems();
        final int max = maxPatternRow();
        Gui.scrollbar(g, rightX() + PATTERN_COLUMNS * CELL + 4, patternsY(), PATTERN_ROWS * CELL, max == 0 ? 0 : (float) patternRow / max, max > 0);
    }

    /** One pattern in a loom cell, white on grey (vanilla LoomScreen#renderPattern). */
    private void renderPattern(final GuiGraphics g, final Holder<BannerPattern> pattern, final int x, final int y)
    {
        final PoseStack pose = new PoseStack();
        pose.pushPose();
        pose.translate(x + 0.5F, y + 16, 0.0F);
        pose.scale(6.0F, -6.0F, 1.0F);
        pose.translate(0.5F, 0.5F, 0.0F);
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.scale(0.6666667F, -0.6666667F, -0.6666667F);
        flag.xRot = 0.0F;
        flag.y = -32.0F;
        BannerRenderer.renderPatterns(pose, g.bufferSource(), 15728880, OverlayTexture.NO_OVERLAY, flag, ModelBakery.BANNER_BASE, true, DyeColor.GRAY,
          new BannerPatternLayers.Builder().add(pattern, DyeColor.WHITE).build());
        pose.popPose();
        g.flush();
    }

    /** The whole flag with its top-left corner at (x, y), {@code height} pixels tall (vanilla's loom result maths). */
    private void drawFlag(final GuiGraphics g, final List<BannerPatternLayers.Layer> list, final int x, final int y, final int height)
    {
        if (list.isEmpty())
        {
            return;
        }
        final float k = height * 0.6F;
        Lighting.setupForFlatItems();
        g.pose().pushPose();
        g.pose().translate(x - k / 12.0F, y + 11.0F * k / 6.0F, 0.0F);
        g.pose().scale(k, k, 1.0F);
        g.pose().translate(0.5F, -0.5F, 0.5F);
        g.pose().scale(0.6666667F, 0.6666667F, -0.6666667F);
        flag.xRot = 0.0F;
        flag.y = -32.0F;
        final BannerPatternLayers.Builder builder = new BannerPatternLayers.Builder();
        for (int i = 1; i < list.size(); i++)
        {
            builder.add(list.get(i));
        }
        BannerRenderer.renderPatterns(g.pose(), g.bufferSource(), 15728880, OverlayTexture.NO_OVERLAY, flag, ModelBakery.BANNER_BASE, true,
          list.get(0).color(), builder.build());
        g.pose().popPose();
        g.flush();
        Lighting.setupFor3DItems();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (button == 0)
        {
            // Layers.
            final int lx = layersX();
            for (int i = 0; i < layerRows(); i++)
            {
                final int ry = bodyTop() + 2 + i * LAYER_ROW;
                if (i > 0 && i < layers.size() && Gui.inside(mouseX, mouseY, lx + LAYERS_WIDTH - 16, ry + 3, 12, 12))
                {
                    Gui.clickSound();
                    layers.remove(i);
                    selectLayer(Math.min(active, layers.size()));
                    return true;
                }
                if (Gui.inside(mouseX, mouseY, lx + 1, ry, LAYERS_WIDTH - 2, LAYER_ROW))
                {
                    Gui.clickSound();
                    selectLayer(i);
                    return true;
                }
            }
            // Dyes.
            final DyeColor[] colours = DyeColor.values();
            for (int i = 0; i < colours.length; i++)
            {
                if (Gui.inside(mouseX, mouseY, rightX() + (i % 8) * 18, paletteY() + (i / 8) * 18, 18, 18))
                {
                    Gui.clickSound();
                    selected = colours[i];
                    setLayer(null, selected);
                    return true;
                }
            }
            // Patterns.
            if (active > 0 && Gui.inside(mouseX, mouseY, rightX(), patternsY(), PATTERN_COLUMNS * CELL, PATTERN_ROWS * CELL))
            {
                final int index = (patternRow + (int) ((mouseY - patternsY()) / CELL)) * PATTERN_COLUMNS + (int) ((mouseX - rightX()) / CELL);
                if (index >= 0 && index < patterns.size())
                {
                    Gui.clickSound();
                    setLayer(patterns.get(index), selected);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        if (active > 0 && Gui.inside(mouseX, mouseY, rightX(), patternsY(), PATTERN_COLUMNS * CELL + 20, PATTERN_ROWS * CELL))
        {
            patternRow = Math.max(0, Math.min(maxPatternRow(), patternRow - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers)
    {
        if (key == GLFW.GLFW_KEY_ESCAPE)
        {
            back.run();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
