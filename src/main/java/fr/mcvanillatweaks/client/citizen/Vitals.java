package fr.mcvanillatweaks.client.citizen;

import com.minecolonies.api.colony.ICitizenDataView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Health, food and happiness rows of ten 9x9 icons (8 px apart), drawn like the vanilla HUD. Shared by the citizen and
 * town hall screens.
 */
public final class Vitals
{
    /** Width of a row of ten icons. */
    public static final int WIDTH = 81;

    private static final ResourceLocation HEART_CONTAINER = ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation[] HEART_FULL = {
      ResourceLocation.withDefaultNamespace("hud/heart/full"), ResourceLocation.withDefaultNamespace("hud/heart/absorbing_full")};
    private static final ResourceLocation[] HEART_HALF = {
      ResourceLocation.withDefaultNamespace("hud/heart/half"), ResourceLocation.withDefaultNamespace("hud/heart/absorbing_half")};
    /** Third and fourth tiers (health above 40): the vanilla poisoned and frozen hearts, green and blue like MineColonies' ones. */
    private static final ResourceLocation[] EXTRA_FULL = {
      ResourceLocation.withDefaultNamespace("hud/heart/poisoned_full"), ResourceLocation.withDefaultNamespace("hud/heart/frozen_full")};
    private static final ResourceLocation[] EXTRA_HALF = {
      ResourceLocation.withDefaultNamespace("hud/heart/poisoned_half"), ResourceLocation.withDefaultNamespace("hud/heart/frozen_half")};
    private static final ResourceLocation FOOD_EMPTY = ResourceLocation.withDefaultNamespace("hud/food_empty");
    private static final ResourceLocation FOOD_HALF = ResourceLocation.withDefaultNamespace("hud/food_half");
    private static final ResourceLocation FOOD_FULL = ResourceLocation.withDefaultNamespace("hud/food_full");

    private Vitals()
    {
    }

    /**
     * Ten hearts, each tier covering 20 health: red, golden, green then blue (vanilla sprites), the highest tier
     * filling from the left (same result as MineColonies' citizen window).
     */
    public static void hearts(final GuiGraphics g, final ICitizenDataView citizen, final int x, final int y)
    {
        hearts(g, citizen.getHealth(), x, y);
    }

    /** {@link #hearts(GuiGraphics, ICitizenDataView, int, int)} for a health value read elsewhere (e.g. an expedition log). */
    public static void hearts(final GuiGraphics g, final double citizenHealth, final int x, final int y)
    {
        final int health = Math.max(0, (int) citizenHealth);
        final int layer = Math.min(health / 20, 4);
        final int rest = layer >= 4 ? 0 : health % 20;
        for (int i = 0; i < 10; i++)
        {
            final int hx = x + i * 8;
            g.blitSprite(HEART_CONTAINER, hx, y, 9, 9);
            if (i < rest / 2)
            {
                heart(g, hx, y, layer + 1, false);
            }
            else
            {
                heart(g, hx, y, layer, false);
                if (i == rest / 2 && rest % 2 == 1)
                {
                    heart(g, hx, y, layer + 1, true);
                }
            }
        }
    }

    /** @param tier 1 red, 2 golden, 3 green, 4 blue (0: nothing). */
    private static void heart(final GuiGraphics g, final int x, final int y, final int tier, final boolean half)
    {
        if (tier <= 0)
        {
            return;
        }
        if (tier <= 2)
        {
            g.blitSprite(half ? HEART_HALF[tier - 1] : HEART_FULL[tier - 1], x, y, 9, 9);
        }
        else
        {
            g.blitSprite(half ? EXTRA_HALF[tier - 3] : EXTRA_FULL[tier - 3], x, y, 9, 9);
        }
    }

    /** Ten drumsticks for MineColonies' 0-60 saturation (6 per drumstick, like its citizen window). */
    public static void food(final GuiGraphics g, final ICitizenDataView citizen, final int x, final int y)
    {
        food(g, citizen.getSaturation(), x, y);
    }

    /** {@link #food(GuiGraphics, ICitizenDataView, int, int)} for a saturation read elsewhere (e.g. an expedition log). */
    public static void food(final GuiGraphics g, final double saturation, final int x, final int y)
    {
        final double units = Math.max(0, saturation) / 6.0D;
        final int full = (int) units;
        final boolean half = units - full > 0;
        for (int i = 0; i < 10; i++)
        {
            final int fx = x + i * 8;
            g.blitSprite(FOOD_EMPTY, fx, y, 9, 9);
            if (i < full)
            {
                g.blitSprite(FOOD_FULL, fx, y, 9, 9);
            }
            else if (i == full && half)
            {
                g.blitSprite(FOOD_HALF, fx, y, 9, 9);
            }
        }
    }

    /** A 0-10 happiness as the villager experience bar, the width of a row of icons and centred in its 9 px height. */
    public static void happiness(final GuiGraphics g, final double happiness, final int x, final int y)
    {
        happiness(g, happiness, x, y, WIDTH);
    }

    public static void happiness(final GuiGraphics g, final double happiness, final int x, final int y, final int width)
    {
        Gui.bar(g, x, y + 2, width, (float) (Math.max(0, Math.min(10, happiness)) / 10.0D));
    }
}
