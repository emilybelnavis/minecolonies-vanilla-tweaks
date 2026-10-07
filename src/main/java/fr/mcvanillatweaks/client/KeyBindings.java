package fr.mcvanillatweaks.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Configurable keys (Options > Controls > Key Binds > MineColonies Vanilla Tweaks).
 */
public final class KeyBindings
{
    private static final String CATEGORY = "key.categories.mcvanillatweaks";

    public static final KeyMapping TOGGLE_HUD = new KeyMapping(
      "key.mcvanillatweaks.toggle_hud", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);

    public static final KeyMapping PIN_SCROLL = new KeyMapping(
      "key.mcvanillatweaks.pin_scroll", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);

    /** Opens the mod settings. */
    public static final KeyMapping OPEN_SETTINGS = new KeyMapping(
      "key.mcvanillatweaks.open_settings", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    /** Opens the HUD layout editor. */
    public static final KeyMapping HUD_LAYOUT = new KeyMapping(
      "key.mcvanillatweaks.hud_layout", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, CATEGORY);

    private KeyBindings()
    {
    }

    public static void register(final RegisterKeyMappingsEvent event)
    {
        event.register(TOGGLE_HUD);
        event.register(PIN_SCROLL);
        event.register(OPEN_SETTINGS);
        event.register(HUD_LAYOUT);
    }
}
