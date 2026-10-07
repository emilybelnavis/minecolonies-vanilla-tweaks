package fr.mcvanillatweaks.client.hud;

import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.PinnedBuilders;
import fr.mcvanillatweaks.client.data.BuilderRef;
import fr.mcvanillatweaks.client.data.HudSnapshot;
import fr.mcvanillatweaks.client.data.ResourceHudTracker;
import fr.mcvanillatweaks.config.HudConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * HUD layout editor (settings key): drag pinned panels with the mouse, right-click to send one back to the default
 * corner, and open the mod settings.
 */
public final class HudLayoutScreen extends Screen
{
    private static final int BUTTON_WIDTH = 110;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 4;

    private static final int OVERLAY = 0x50000000;
    private static final int HOVER_OUTLINE = 0xFFFFFF55;
    private static final int DRAG_OUTLINE = 0xFFFFFFFF;
    private static final int LOCKED_OUTLINE = 0x80AAAAAA;
    private static final int HINT_COLOR = 0xFFFFFFFF;
    private static final int HINT_SECONDARY_COLOR = 0xFFAAAAAA;

    @Nullable
    private final Screen parent;

    private List<HudRenderer.Placed> placed = List.of();

    @Nullable
    private BuilderRef dragging;
    private double grabX;
    private double grabY;

    public HudLayoutScreen(@Nullable final Screen parent)
    {
        super(Component.translatable("screen.mcvanillatweaks.layout.title"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        final int total = BUTTON_WIDTH * 3 + BUTTON_GAP * 2;
        final int x = (width - total) / 2;
        final int y = height - BUTTON_HEIGHT - 8;

        addRenderableWidget(Button.builder(Component.translatable("screen.mcvanillatweaks.layout.settings"),
            button -> minecraft.setScreen(new ConfigurationScreen(MineColoniesVanillaTweaks.container(), this)))
          .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
          .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.mcvanillatweaks.layout.reset_all"),
            button -> {
                PinnedBuilders.resetAllPositions();
                PinnedBuilders.save();
            })
          .bounds(x + BUTTON_WIDTH + BUTTON_GAP, y, BUTTON_WIDTH, BUTTON_HEIGHT)
          .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
          .bounds(x + (BUTTON_WIDTH + BUTTON_GAP) * 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
          .build());
    }

    private static float scale()
    {
        return HudConfig.SCALE.get().floatValue();
    }

    @Override
    public void renderBackground(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick)
    {
        // No blur: the player must see the game behind the panels to place them.
        graphics.fill(0, 0, width, height, OVERLAY);

        final float scale = scale();
        final int hudWidth = (int) (width / scale);
        final int hudHeight = (int) (height / scale);
        final HudSnapshot snapshot = ResourceHudTracker.snapshot();
        placed = snapshot.isEmpty() ? List.of() : HudRenderer.INSTANCE.place(font, snapshot, hudWidth, hudHeight);

        final double hudMouseX = mouseX / scale;
        final double hudMouseY = mouseY / scale;

        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        HudRenderer.INSTANCE.draw(graphics, font, placed);
        for (final HudRenderer.Placed panel : placed)
        {
            final int color;
            if (!panel.layout().pinned())
            {
                color = LOCKED_OUTLINE;
            }
            else if (panel.layout().ref().equals(dragging))
            {
                color = DRAG_OUTLINE;
            }
            else if (dragging == null && panel.contains(hudMouseX, hudMouseY))
            {
                color = HOVER_OUTLINE;
            }
            else
            {
                continue;
            }
            graphics.renderOutline(panel.x() - 1, panel.y() - 1, panel.layout().width() + 2, panel.layout().height() + 2, color);
        }
        graphics.pose().popPose();

        // Instructions, centered in the middle of the screen so they never hide a corner.
        final int centerY = height / 2 - 20;
        graphics.drawCenteredString(font, title, width / 2, centerY, HINT_COLOR);
        graphics.drawCenteredString(font, Component.translatable("screen.mcvanillatweaks.layout.hint.drag"), width / 2, centerY + 14, HINT_SECONDARY_COLOR);
        graphics.drawCenteredString(font, Component.translatable("screen.mcvanillatweaks.layout.hint.reset"), width / 2, centerY + 26, HINT_SECONDARY_COLOR);
        if (ResourceHudTracker.isDisabled())
        {
            graphics.drawCenteredString(font, Component.translatable("screen.mcvanillatweaks.layout.disabled"), width / 2, centerY + 42, 0xFFFF5555);
        }
        else if (placed.isEmpty())
        {
            graphics.drawCenteredString(font, Component.translatable("screen.mcvanillatweaks.layout.empty"), width / 2, centerY + 42, 0xFFFFAA00);
        }
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(graphics, mouseX, mouseY, partialTick);

        if (dragging == null)
        {
            final HudRenderer.Placed hovered = panelAt(mouseX, mouseY);
            if (hovered != null && !hovered.layout().pinned())
            {
                graphics.renderTooltip(font, Component.translatable("screen.mcvanillatweaks.layout.not_pinned"), mouseX, mouseY);
            }
        }
    }

    @Nullable
    private HudRenderer.Placed panelAt(final double mouseX, final double mouseY)
    {
        final float scale = scale();
        // Last drawn on top: search from the end.
        for (int i = placed.size() - 1; i >= 0; i--)
        {
            final HudRenderer.Placed panel = placed.get(i);
            if (panel.contains(mouseX / scale, mouseY / scale))
            {
                return panel;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (super.mouseClicked(mouseX, mouseY, button))
        {
            return true;
        }
        final HudRenderer.Placed panel = panelAt(mouseX, mouseY);
        if (panel == null || !panel.layout().pinned())
        {
            return false;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        {
            PinnedBuilders.resetPosition(panel.layout().ref());
            PinnedBuilders.save();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
        {
            final float scale = scale();
            dragging = panel.layout().ref();
            grabX = mouseX / scale - panel.x();
            grabY = mouseY / scale - panel.y();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY)
    {
        if (dragging == null || button != GLFW.GLFW_MOUSE_BUTTON_LEFT)
        {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        for (final HudRenderer.Placed panel : placed)
        {
            if (panel.layout().ref().equals(dragging))
            {
                final float scale = scale();
                final int hudWidth = (int) (width / scale);
                final int hudHeight = (int) (height / scale);
                final int newX = (int) Math.round(mouseX / scale - grabX);
                final int newY = (int) Math.round(mouseY / scale - grabY);
                PinnedBuilders.setPosition(dragging,
                  HudRenderer.toFraction(newX, panel.layout().width(), hudWidth),
                  HudRenderer.toFraction(newY, panel.layout().height(), hudHeight));
                break;
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button)
    {
        if (dragging != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
        {
            dragging = null;
            PinnedBuilders.save();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose()
    {
        if (dragging != null)
        {
            dragging = null;
            PinnedBuilders.save();
        }
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen()
    {
        // Keep the data flowing while the player arranges the panels.
        return false;
    }
}
