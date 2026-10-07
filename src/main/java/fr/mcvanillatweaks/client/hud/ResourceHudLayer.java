package fr.mcvanillatweaks.client.hud;

import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.data.HudSnapshot;
import fr.mcvanillatweaks.client.data.ResourceHudTracker;
import fr.mcvanillatweaks.config.HudConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * In-game HUD layer. Reads no MineColonies data: it only draws the latest {@link HudSnapshot} through {@link HudRenderer}.
 */
public final class ResourceHudLayer implements LayeredDraw.Layer
{
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MineColoniesVanillaTweaks.MOD_ID, "resource_hud");

    public static void register(final RegisterGuiLayersEvent event)
    {
        event.registerAboveAll(ID, new ResourceHudLayer());
    }

    @Override
    public void render(final GuiGraphics graphics, final DeltaTracker deltaTracker)
    {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui
              || mc.getDebugOverlay().showDebugScreen()
              || mc.player == null
              || mc.screen instanceof HudLayoutScreen // the editor draws the panels itself
              || !ResourceHudTracker.isVisible())
        {
            return;
        }
        final HudSnapshot snapshot = ResourceHudTracker.snapshot();
        if (snapshot.isEmpty())
        {
            return;
        }

        final float scale = HudConfig.SCALE.get().floatValue();
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        HudRenderer.INSTANCE.draw(graphics, mc.font,
          HudRenderer.INSTANCE.place(mc.font, snapshot, (int) (graphics.guiWidth() / scale), (int) (graphics.guiHeight() / scale)));
        graphics.pose().popPose();
    }
}
