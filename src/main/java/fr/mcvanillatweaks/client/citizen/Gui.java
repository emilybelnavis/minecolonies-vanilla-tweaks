package fr.mcvanillatweaks.client.citizen;

import com.mojang.blaze3d.platform.NativeImage;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Drawing helpers for the citizen screens. Everything is either a vanilla GUI sprite (read from the game at runtime) or
 * drawn procedurally with the vanilla colour scheme, so no Mojang or MineColonies file is shipped.
 */
public final class Gui
{
    /*
     * Text rule, so every screen reads the same way:
     * - on the light panel, LABEL / LABEL_SOFT without shadow (vanilla container labels);
     * - inside the dark lists (inset, ScrollArea), WHITE / LIST_SOFT or a chat colour, always with shadow (vanilla menu lists).
     * Never mix a shadowed and an unshadowed colour on the same background.
     */
    /** Vanilla container label colour. */
    public static final int LABEL = 0xFF404040;
    /** Secondary text on the light panel (vanilla dark grey). */
    public static final int LABEL_SOFT = 0xFF555555;
    public static final int WHITE = 0xFFFFFFFF;
    /** Secondary text inside the dark lists (vanilla grey). */
    public static final int LIST_SOFT = 0xFFAAAAAA;
    /** Inactive button label, as vanilla. */
    public static final int DISABLED = Gui.DISABLED;
    public static final int GOLD = 0xFFFFAA00;
    public static final int YELLOW = 0xFFFFFF55;
    public static final int GREEN = 0xFF55FF55;
    public static final int RED = 0xFFFF5555;

    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_BODY = 0xFF8B8B8B;
    /** Body of the text lists: dark, so light shadowed text and chat colours stay readable. */
    public static final int LIST_BODY = 0xFF404040;
    /** Row hover and selection over {@link #LIST_BODY}. */
    public static final int ROW_HOVER = 0x28FFFFFF;
    public static final int ROW_SELECTED = 0x40FFFFFF;

    private static final ResourceLocation SLOT = ResourceLocation.withDefaultNamespace("container/slot");
    private static final ResourceLocation BUTTON = ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation BUTTON_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/button_highlighted");
    private static final ResourceLocation BUTTON_DISABLED = ResourceLocation.withDefaultNamespace("widget/button_disabled");
    private static final ResourceLocation SCROLLER = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller");
    private static final ResourceLocation SCROLLER_DISABLED = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller_disabled");
    private static final ResourceLocation BAR_BACKGROUND = ResourceLocation.withDefaultNamespace("container/villager/experience_bar_background");
    private static final ResourceLocation BAR_CURRENT = ResourceLocation.withDefaultNamespace("container/villager/experience_bar_current");

    public static final int SCROLLER_WIDTH = 12;
    public static final int SCROLLER_HEIGHT = 15;
    public static final int BAR_WIDTH = 102;
    public static final int BAR_HEIGHT = 5;

    private static final Map<Long, ResourceLocation> PANELS = new HashMap<>();
    private static final Map<Character, ResourceLocation> GENDERS = new HashMap<>();

    private Gui()
    {
    }

    /** Vanilla container frame (rounded black outline, white/dark bevel, light grey body). */
    public static void panel(final GuiGraphics g, final int x, final int y, final int w, final int h)
    {
        final ResourceLocation texture = PANELS.computeIfAbsent(((long) w << 32) | h,
          key -> register("citizen_panel_" + w + "x" + h, VanillaTextures.panel(w, h)));
        g.blit(texture, x, y, 0, 0, w, h, w, h);
    }

    /** Gender symbol, drawn pixel-art style ('m', 'f'). */
    public static void gender(final GuiGraphics g, final int x, final int y, final char gender)
    {
        final ResourceLocation texture = GENDERS.computeIfAbsent(gender,
          key -> register("citizen_gender_" + (gender == 'f' ? "female" : "male"), VanillaTextures.gender(10, 10, gender)));
        g.blit(texture, x, y, 0, 0, 10, 10, 10, 10);
    }

    private static ResourceLocation register(final String name, final NativeImage image)
    {
        final ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MineColoniesVanillaTweaks.MOD_ID, "dynamic/" + name);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        return id;
    }

    /** Sunken dark area holding text rows (see the text rule above). */
    public static void inset(final GuiGraphics g, final int x, final int y, final int w, final int h)
    {
        inset(g, x, y, w, h, LIST_BODY);
    }

    /** Sunken area with a custom body colour (black for the portrait, like the vanilla inventory). */
    public static void inset(final GuiGraphics g, final int x, final int y, final int w, final int h, final int body)
    {
        g.fill(x, y, x + w, y + h, body);
        g.fill(x, y, x + w - 1, y + 1, SLOT_DARK);
        g.fill(x, y, x + 1, y + h - 1, SLOT_DARK);
        g.fill(x + 1, y + h - 1, x + w, y + h, WHITE);
        g.fill(x + w - 1, y + 1, x + w, y + h, WHITE);
    }

    /** Vanilla 18x18 slot background. */
    public static void slot(final GuiGraphics g, final int x, final int y)
    {
        g.blitSprite(SLOT, x, y, 18, 18);
    }

    /** Item (with count and durability) inside an 18x18 slot drawn at (x, y); the icon when empty, if any. */
    public static void slotItem(final GuiGraphics g, final Font font, final int x, final int y, final ItemStack stack, final ResourceLocation emptyIcon)
    {
        if (!stack.isEmpty())
        {
            g.renderItem(stack, x + 1, y + 1);
            g.renderItemDecorations(font, stack, x + 1, y + 1);
        }
        else if (emptyIcon != null)
        {
            final TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(emptyIcon);
            g.blit(x + 1, y + 1, 0, 16, 16, sprite);
        }
    }

    /** Hover highlight of a slot, same as vanilla. */
    public static void slotHighlight(final GuiGraphics g, final int x, final int y)
    {
        g.fillGradient(x + 1, y + 1, x + 17, y + 17, 0x80FFFFFF, 0x80FFFFFF);
    }

    /** Vanilla button background (nine-sliced by the game from its own sprite). */
    public static void button(final GuiGraphics g, final int x, final int y, final int w, final int h, final boolean active, final boolean hovered)
    {
        g.blitSprite(!active ? BUTTON_DISABLED : hovered ? BUTTON_HIGHLIGHTED : BUTTON, x, y, w, h);
    }

    /** Small vanilla button with a centred label. */
    public static void textButton(final GuiGraphics g, final Font font, final int x, final int y, final int w, final int h, final String label,
      final boolean active, final boolean hovered)
    {
        button(g, x, y, w, h, active, hovered);
        final int colour = active ? WHITE : DISABLED;
        g.drawString(font, label, x + (w - font.width(label) + 1) / 2, y + (h - 8) / 2, colour, true);
    }

    /** Small downward arrow (7 x 4) of the drop-down controls, with a text-like shadow. */
    public static void dropdownArrow(final GuiGraphics g, final int x, final int y, final int colour)
    {
        final int shadow = (colour & 0xFC000000) | ((colour & 0xFCFCFC) >> 2);
        for (int pass = 0; pass < 2; pass++)
        {
            final int o = pass == 0 ? 1 : 0;
            for (int row = 0; row < 4; row++)
            {
                g.fill(x + row + o, y + row + o, x + 7 - row + o, y + row + 1 + o, pass == 0 ? shadow : colour);
            }
        }
    }

    /** Creative inventory scrollbar: sunken track and the vanilla scroller. */
    public static void scrollbar(final GuiGraphics g, final int x, final int y, final int h, final float progress, final boolean enabled)
    {
        inset(g, x, y, SCROLLER_WIDTH + 2, h, SLOT_BODY);
        final int travel = h - 2 - SCROLLER_HEIGHT;
        final int offset = enabled ? Math.round(travel * Math.max(0, Math.min(1, progress))) : 0;
        g.blitSprite(enabled ? SCROLLER : SCROLLER_DISABLED, x + 1, y + 1 + offset, SCROLLER_WIDTH, SCROLLER_HEIGHT);
    }

    /** Vanilla checkbox sprite (13 x 13), for checkboxes drawn in list rows. */
    public static ResourceLocation checkbox(final boolean selected, final boolean highlighted)
    {
        return ResourceLocation.withDefaultNamespace("widget/checkbox" + (selected ? "_selected" : "") + (highlighted ? "_highlighted" : ""));
    }

    /** Villager trade experience bar (102 x 5), filled to {@code progress} (0..1). */
    public static void bar(final GuiGraphics g, final int x, final int y, final float progress)
    {
        g.blitSprite(BAR_BACKGROUND, x, y, BAR_WIDTH, BAR_HEIGHT);
        final int filled = Math.round(BAR_WIDTH * Math.max(0, Math.min(1, progress)));
        if (filled > 0)
        {
            g.blitSprite(BAR_CURRENT, BAR_WIDTH, BAR_HEIGHT, 0, 0, x, y, filled, BAR_HEIGHT);
        }
    }

    /** Shorter villager experience bar: the left of the vanilla bar and its right end, filled to {@code progress}. */
    public static void bar(final GuiGraphics g, final int x, final int y, final int width, final float progress)
    {
        g.blitSprite(BAR_BACKGROUND, BAR_WIDTH, BAR_HEIGHT, 0, 0, x, y, width - 1, BAR_HEIGHT);
        g.blitSprite(BAR_BACKGROUND, BAR_WIDTH, BAR_HEIGHT, BAR_WIDTH - 1, 0, x + width - 1, y, 1, BAR_HEIGHT);
        final int filled = Math.round(width * Math.max(0, Math.min(1, progress)));
        if (filled > 0)
        {
            g.blitSprite(BAR_CURRENT, BAR_WIDTH, BAR_HEIGHT, 0, 0, x, y, filled, BAR_HEIGHT);
        }
    }

    /** Whole texture scaled into a w x h box (for MineColonies icons of any resolution). */
    public static void icon(final GuiGraphics g, final ResourceLocation texture, final int x, final int y, final int w, final int h)
    {
        g.blit(texture, x, y, w, h, 0, 0, 1, 1, 1, 1);
    }

    /** Text cut to {@code width} with an ellipsis. */
    public static FormattedCharSequence fit(final Font font, final Component text, final int width)
    {
        if (font.width(text) <= width)
        {
            return text.getVisualOrderText();
        }
        final FormattedText cut = font.substrByWidth(text, Math.max(0, width - font.width("…")));
        return net.minecraft.locale.Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of("…")));
    }

    public static boolean isCut(final Font font, final Component text, final int width)
    {
        return font.width(text) > width;
    }

    public static boolean inside(final double mouseX, final double mouseY, final int x, final int y, final int w, final int h)
    {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    public static void clickSound()
    {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
