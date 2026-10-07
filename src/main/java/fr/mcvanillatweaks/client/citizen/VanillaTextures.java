package fr.mcvanillatweaks.client.citizen;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * Draws vanilla Java Edition GUI pieces (panels, side tabs, gender symbols) procedurally from the vanilla colour
 * scheme (light grey body, white/dark bevel, black rounded outline), so no Mojang file is shipped.
 */
public final class VanillaTextures
{
    private static final int K = 0xFF000000; // outline
    private static final int W = 0xFFFFFFFF; // light bevel
    private static final int C = 0xFFC6C6C6; // panel body
    private static final int D = 0xFF555555; // dark bevel

    /** Size of the panel corner pattern. */
    public static final int CORNER = 7;

    // Corner patterns of the vanilla container frame ('.' = transparent).
    private static final String[] TOP_LEFT = {"..KKKKK", ".KWWWWW", "KWWWWWW", "KWWWCCC", "KWWCCCC", "KWWCCCC", "KWWCCCC"};
    private static final String[] TOP_RIGHT = {"KKKK...", "WWWWK..", "WWWWCK.", "CCCCDDK", "CCCCDDK", "CCCCDDK", "CCCCDDK"};
    private static final String[] BOTTOM_LEFT = {"KWWCCCC", "KWWCCCC", "KWWCCCC", "KWWCCCC", ".KCDDDD", "..KDDDD", "...KKKK"};
    private static final String[] BOTTOM_RIGHT = {"CCCCDDK", "CCCCDDK", "CCCCDDK", "CCCDDDK", "DDDDDDK", "DDDDDK.", "KKKKK.."};
    private static final String TOP_EDGE = "KWWCCCC";    // top to bottom
    private static final String BOTTOM_EDGE = "CCCCDDK"; // top to bottom
    private static final String LEFT_EDGE = "KWWCCCC";   // left to right
    private static final String RIGHT_EDGE = "CCCCDDK";  // left to right

    private VanillaTextures()
    {
    }

    // ------------------------------------------------------------------ colours

    private static int colour(final char c, final boolean dim)
    {
        return switch (c)
        {
            case 'K' -> K;
            case 'W' -> dim ? 0xFFD6D6D6 : W;
            case 'C' -> dim ? 0xFFA9A9A9 : C;
            case 'D' -> dim ? 0xFF4A4A4A : D;
            default -> 0;
        };
    }

    /** Pixel of a vanilla panel of size w x h (w, h >= 2 * CORNER). */
    private static int panelPixel(final int x, final int y, final int w, final int h)
    {
        return panelPixel(x, y, w, h, false);
    }

    /** @param dim darker palette, used for unselected tabs. */
    private static int panelPixel(final int x, final int y, final int w, final int h, final boolean dim)
    {
        final boolean left = x < CORNER;
        final boolean right = x >= w - CORNER;
        final boolean top = y < CORNER;
        final boolean bottom = y >= h - CORNER;
        if (top && left)
        {
            return colour(TOP_LEFT[y].charAt(x), dim);
        }
        if (top && right)
        {
            return colour(TOP_RIGHT[y].charAt(x - (w - CORNER)), dim);
        }
        if (bottom && left)
        {
            return colour(BOTTOM_LEFT[y - (h - CORNER)].charAt(x), dim);
        }
        if (bottom && right)
        {
            return colour(BOTTOM_RIGHT[y - (h - CORNER)].charAt(x - (w - CORNER)), dim);
        }
        if (top)
        {
            return colour(TOP_EDGE.charAt(y), dim);
        }
        if (bottom)
        {
            return colour(BOTTOM_EDGE.charAt(y - (h - CORNER)), dim);
        }
        if (left)
        {
            return colour(LEFT_EDGE.charAt(x), dim);
        }
        if (right)
        {
            return colour(RIGHT_EDGE.charAt(x - (w - CORNER)), dim);
        }
        return dim ? colour('C', true) : C;
    }

    // ------------------------------------------------------------------ images

    public static NativeImage panel(final int width, final int height)
    {
        final int w = Math.max(width, CORNER * 2);
        final int h = Math.max(height, CORNER * 2);
        final NativeImage image = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                set(image, x, y, panelPixel(x, y, w, h));
            }
        }
        return image;
    }

    /**
     * Side tab: a panel whose edge facing the window is open, so it merges with the window frame it overlaps.
     *
     * @param left true for a tab on the left side of a window (open on its right).
     */
    public static NativeImage tab(final int width, final int height, final boolean left)
    {
        return tab(width, height, left, false);
    }

    /**
     * @param dim darker palette for an unselected tab.
     */
    public static NativeImage tab(final int width, final int height, final boolean left, final boolean dim)
    {
        final int w = Math.max(width, CORNER + 1);
        final int h = Math.max(height, CORNER * 2);
        final int full = w + CORNER;
        final NativeImage image = new NativeImage(w, h, true);
        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                set(image, x, y, panelPixel(left ? x : x + CORNER, y, full, h, dim));
            }
        }
        return image;
    }


    private static final String[] MALE = {
      ".....####",
      "......###",
      ".....#.##",
      "....#...#",
      ".###.....",
      "#...#....",
      "#...#....",
      "#...#....",
      ".###....."};
    private static final String[] FEMALE = {
      "...###...",
      "..#...#..",
      "..#...#..",
      "..#...#..",
      "...###...",
      "....#....",
      "..#####..",
      "....#....",
      "....#...."};
    private static final String[] UNKNOWN = {
      "...###...",
      "..#...#..",
      "......#..",
      ".....#...",
      "....#....",
      "....#....",
      ".........",
      "....#....",
      "........."};

    /**
     * Gender symbol (replaces MineColonies' red wax seals), drawn pixel-art style with a text-like shadow.
     *
     * @param gender 'm', 'f' or anything else for unknown.
     */
    public static NativeImage gender(final int width, final int height, final char gender)
    {
        final String[] pattern = gender == 'm' ? MALE : gender == 'f' ? FEMALE : UNKNOWN;
        final int colour = gender == 'm' ? 0xFF5B8CFF : gender == 'f' ? 0xFFFF6FCF : 0xFFAAAAAA;
        final int w = Math.max(width, 10);
        final int h = Math.max(height, 10);
        final int scale = Math.max(1, Math.min((w - 1) / 9, (h - 1) / 9));
        final int ox = (w - 9 * scale - 1) / 2;
        final int oy = (h - 9 * scale - 1) / 2;
        final NativeImage image = new NativeImage(w, h, true);
        for (int pass = 0; pass < 2; pass++)
        {
            final int shift = pass == 0 ? 1 : 0;
            final int c = pass == 0 ? 0xFF3F3F3F : colour;
            for (int y = 0; y < 9; y++)
            {
                for (int x = 0; x < 9; x++)
                {
                    if (pattern[y].charAt(x) != '#')
                    {
                        continue;
                    }
                    for (int dy = 0; dy < scale; dy++)
                    {
                        for (int dx = 0; dx < scale; dx++)
                        {
                            safeSet(image, ox + x * scale + dx + shift, oy + y * scale + dy + shift, c);
                        }
                    }
                }
            }
        }
        return image;
    }

    // ------------------------------------------------------------------ helpers

    /** NativeImage stores ABGR: convert from ARGB. */
    private static void set(final NativeImage image, final int x, final int y, final int argb)
    {
        final int a = argb >>> 24;
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        final int b = argb & 0xFF;
        image.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
    }

    private static void safeSet(final NativeImage image, final int x, final int y, final int argb)
    {
        if (x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight())
        {
            set(image, x, y, argb);
        }
    }
}
