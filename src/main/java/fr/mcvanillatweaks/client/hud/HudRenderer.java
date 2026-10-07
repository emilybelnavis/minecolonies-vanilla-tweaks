package fr.mcvanillatweaks.client.hud;

import com.minecolonies.core.colony.buildings.utils.BuildingBuilderResource.RessourceAvailability;
import fr.mcvanillatweaks.client.PinnedBuilders;
import fr.mcvanillatweaks.client.data.BuilderRef;
import fr.mcvanillatweaks.client.data.HudSnapshot;
import fr.mcvanillatweaks.config.HudConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lays out and draws the {@link HudSnapshot} in a plain vanilla style. Shared by the in-game HUD layer and the layout editor.
 * <p>
 * The layout (texts, truncation, sizes) is computed once per snapshot, i.e. twice per second at most; each frame only
 * positions the cached boxes and draws them. All coordinates are in HUD units (GUI pixels divided by the HUD scale).
 */
public final class HudRenderer
{
    public static final HudRenderer INSTANCE = new HudRenderer();

    private static final int PADDING = 4;
    private static final int PANEL_GAP = 3;
    private static final int TEXT_LINE = 10;
    private static final int ICON_ROW = 18;
    private static final int ICON_SPACE = 18;
    private static final int COLUMN_GAP = 8;
    private static final int MIN_WIDTH = 90;
    private static final int MAX_WIDTH = 200;
    private static final String ELLIPSIS = "…";

    private static final int BACKGROUND = 0xB0101010;
    private static final int BORDER = 0x80606060;
    private static final int SEPARATOR = 0x40FFFFFF;
    private static final int TITLE_COLOR = 0xFFFFFF55;
    private static final int SUBTITLE_COLOR = 0xFFE0E0E0;
    private static final int INFO_COLOR = 0xFFAAAAAA;
    private static final int NAME_COLOR = 0xFFE0E0E0;
    private static final int DONE_COLOR = 0xFF55FF55;
    private static final int BAR_HEIGHT = 3;
    private static final int BAR_TRACK = 0xFF303030;
    private static final int BAR_FILL = 0xFF3FBF3F;

    /** A panel with its computed size and texts. */
    public record PanelLayout(
      BuilderRef ref,
      boolean pinned,
      String title,
      @Nullable String subtitle,
      @Nullable String info,
      int infoColor,
      @Nullable String construction,
      int constructionPercent,
      @Nullable String footer,
      List<RowLayout> rows,
      int width,
      int height)
    {
    }

    public record RowLayout(ItemStack icon, String name, String quantity, int quantityWidth, int color)
    {
    }

    /** A panel placed on screen. */
    public record Placed(PanelLayout layout, int x, int y)
    {
        public boolean contains(final double px, final double py)
        {
            return px >= x && px < x + layout.width() && py >= y && py < y + layout.height();
        }
    }

    private HudSnapshot cachedSnapshot;
    private boolean cachedIcons;
    private boolean cachedPercentage;
    private boolean cachedConstruction;
    private List<PanelLayout> cachedLayout = List.of();

    private HudRenderer()
    {
    }

    // ------------------------------------------------------------------ placement (per frame, cheap)

    /**
     * Places the panels: pinned panels with a custom position go where the player dropped them, the others are stacked
     * in the configured corner.
     */
    public List<Placed> place(final Font font, final HudSnapshot snapshot, final int screenWidth, final int screenHeight)
    {
        final boolean icons = HudConfig.SHOW_ICONS.get();
        final boolean percentage = HudConfig.SHOW_PERCENTAGE.get();
        final boolean construction = HudConfig.SHOW_CONSTRUCTION.get();
        if (snapshot != cachedSnapshot || icons != cachedIcons || percentage != cachedPercentage || construction != cachedConstruction)
        {
            cachedLayout = layout(font, snapshot, icons, percentage, construction);
            cachedSnapshot = snapshot;
            cachedIcons = icons;
            cachedPercentage = percentage;
            cachedConstruction = construction;
        }

        final HudConfig.Anchor anchor = HudConfig.POSITION.get();
        final int offsetX = HudConfig.OFFSET_X.get();
        final int offsetY = HudConfig.OFFSET_Y.get();

        final List<Placed> placed = new ArrayList<>(cachedLayout.size());
        final List<PanelLayout> flow = new ArrayList<>(cachedLayout.size());
        for (final PanelLayout panel : cachedLayout)
        {
            final Optional<PinnedBuilders.Pin> pin = panel.pinned() ? PinnedBuilders.get(panel.ref()) : Optional.empty();
            if (pin.isPresent() && pin.get().hasPosition())
            {
                placed.add(new Placed(panel,
                  fromFraction(pin.get().x(), panel.width(), screenWidth),
                  fromFraction(pin.get().y(), panel.height(), screenHeight)));
            }
            else
            {
                flow.add(panel);
            }
        }

        int flowHeight = 0;
        for (final PanelLayout panel : flow)
        {
            flowHeight += panel.height() + PANEL_GAP;
        }
        flowHeight = Math.max(0, flowHeight - PANEL_GAP);

        int y = anchor.isBottom() ? screenHeight - offsetY - flowHeight : offsetY;
        for (final PanelLayout panel : flow)
        {
            final int x = anchor.isRight() ? screenWidth - offsetX - panel.width() : offsetX;
            placed.add(new Placed(panel, x, y));
            y += panel.height() + PANEL_GAP;
        }
        return placed;
    }

    /** Converts a stored fraction of the free space into a coordinate that keeps the panel fully on screen. */
    public static int fromFraction(final float fraction, final int size, final int screenSize)
    {
        return Math.round(fraction * Math.max(0, screenSize - size));
    }

    /** Inverse of {@link #fromFraction}. */
    public static float toFraction(final int position, final int size, final int screenSize)
    {
        final int free = screenSize - size;
        return free <= 0 ? 0F : Math.clamp((float) position / free, 0F, 1F);
    }

    // ------------------------------------------------------------------ drawing

    public void draw(final GuiGraphics graphics, final Font font, final List<Placed> placed)
    {
        final boolean icons = HudConfig.SHOW_ICONS.get();
        for (final Placed panel : placed)
        {
            drawPanel(graphics, font, panel.layout(), panel.x(), panel.y(), icons);
        }
    }

    private static void drawPanel(final GuiGraphics graphics, final Font font, final PanelLayout panel, final int x, final int y, final boolean icons)
    {
        // Flat, square, semi-transparent box with a 1px frame.
        graphics.fill(x, y, x + panel.width(), y + panel.height(), BACKGROUND);
        graphics.renderOutline(x, y, panel.width(), panel.height(), BORDER);

        int cy = y + PADDING;
        graphics.drawString(font, panel.title(), x + PADDING, cy, TITLE_COLOR, true);
        cy += TEXT_LINE;

        if (panel.subtitle() != null)
        {
            graphics.drawString(font, panel.subtitle(), x + PADDING, cy, SUBTITLE_COLOR, true);
            cy += TEXT_LINE;
        }

        if (panel.info() != null)
        {
            graphics.drawString(font, panel.info(), x + PADDING, cy, panel.infoColor(), true);
            cy += TEXT_LINE;
        }

        if (panel.construction() != null)
        {
            graphics.drawString(font, panel.construction(), x + PADDING, cy, SUBTITLE_COLOR, true);
            cy += TEXT_LINE;
            final int barLeft = x + PADDING;
            final int barRight = x + panel.width() - PADDING;
            graphics.fill(barLeft, cy, barRight, cy + BAR_HEIGHT, BAR_TRACK);
            graphics.fill(barLeft, cy, barLeft + (barRight - barLeft) * panel.constructionPercent() / 100, cy + BAR_HEIGHT,
              panel.constructionPercent() >= 100 ? DONE_COLOR : BAR_FILL);
            cy += BAR_HEIGHT + 3;
        }

        if (!panel.rows().isEmpty())
        {
            graphics.fill(x + PADDING, cy, x + panel.width() - PADDING, cy + 1, SEPARATOR);
            cy += 2;
        }

        final int rowHeight = icons ? ICON_ROW : TEXT_LINE;
        final int textOffset = icons ? (ICON_ROW - 8) / 2 : 0;
        for (final RowLayout row : panel.rows())
        {
            int tx = x + PADDING;
            if (icons)
            {
                graphics.renderItem(row.icon(), tx, cy + 1);
                tx += ICON_SPACE;
            }
            graphics.drawString(font, row.name(), tx, cy + textOffset, NAME_COLOR, true);
            graphics.drawString(font, row.quantity(), x + panel.width() - PADDING - row.quantityWidth(), cy + textOffset, row.color(), true);
            cy += rowHeight;
        }

        if (panel.footer() != null)
        {
            graphics.drawString(font, panel.footer(), x + PADDING, cy + 1, INFO_COLOR, true);
        }
    }

    // ------------------------------------------------------------------ layout (once per snapshot)

    private static List<PanelLayout> layout(final Font font, final HudSnapshot snapshot, final boolean icons, final boolean percentage, final boolean construction)
    {
        final List<PanelLayout> layouts = new ArrayList<>(snapshot.panels().size());
        for (final HudSnapshot.Panel panel : snapshot.panels())
        {
            layouts.add(layoutPanel(font, panel, icons, percentage, construction));
        }
        return List.copyOf(layouts);
    }

    private static PanelLayout layoutPanel(
      final Font font,
      final HudSnapshot.Panel panel,
      final boolean icons,
      final boolean percentage,
      final boolean construction)
    {
        // The Resource Scroll's "used" value is the share of the blueprint's materials already placed: the construction progress.
        final boolean showConstruction = construction && panel.state() == HudSnapshot.State.OK && panel.usedPercent() >= 0;
        final int constructionPercent = showConstruction ? Math.clamp(panel.usedPercent(), 0, 100) : 0;
        final String constructionText = showConstruction
                                          ? Component.translatable("hud.mcvanillatweaks.construction", constructionPercent).getString()
                                          : null;

        // Line under the construction name: percentages or a state message.
        String info = null;
        int infoColor = INFO_COLOR;
        switch (panel.state())
        {
            case UNAVAILABLE -> info = Component.translatable("hud.mcvanillatweaks.unavailable").getString();
            case NO_WORK_ORDER -> info = Component.translatable("hud.mcvanillatweaks.no_work_order").getString();
            case OK ->
            {
                if (panel.lines().isEmpty() && panel.hiddenLines() == 0 && panel.suppliedPercent() >= 0)
                {
                    info = Component.translatable("hud.mcvanillatweaks.all_supplied").getString();
                    infoColor = DONE_COLOR;
                }
                else if (percentage && panel.suppliedPercent() >= 0)
                {
                    info = showConstruction
                             ? Component.translatable("hud.mcvanillatweaks.supplied", panel.suppliedPercent()).getString()
                             : Component.translatable("hud.mcvanillatweaks.progress", panel.suppliedPercent(), panel.usedPercent()).getString();
                }
            }
        }
        final String subtitle = panel.subtitle() == null || panel.subtitle().isBlank() || panel.subtitle().equals(panel.title()) ? null : panel.subtitle();
        final String footer = panel.hiddenLines() > 0 ? Component.translatable("hud.mcvanillatweaks.more", panel.hiddenLines()).getString() : null;

        // Width: fit the content, within bounds.
        final int iconSpace = icons ? ICON_SPACE : 0;
        int contentWidth = font.width(panel.title());
        for (final String text : new String[] {subtitle, info, constructionText, footer})
        {
            if (text != null)
            {
                contentWidth = Math.max(contentWidth, font.width(text));
            }
        }
        final List<String> quantities = new ArrayList<>(panel.lines().size());
        for (final HudSnapshot.Line line : panel.lines())
        {
            final String quantity = quantity(line);
            quantities.add(quantity);
            contentWidth = Math.max(contentWidth, iconSpace + font.width(line.name()) + COLUMN_GAP + font.width(quantity));
        }
        final int width = Math.clamp(contentWidth + PADDING * 2, MIN_WIDTH, MAX_WIDTH);
        final int innerWidth = width - PADDING * 2;

        final List<RowLayout> rows = new ArrayList<>(panel.lines().size());
        for (int i = 0; i < panel.lines().size(); i++)
        {
            final HudSnapshot.Line line = panel.lines().get(i);
            final String quantity = quantities.get(i);
            final int quantityWidth = font.width(quantity);
            final String name = truncate(font, line.name(), innerWidth - iconSpace - COLUMN_GAP - quantityWidth);
            rows.add(new RowLayout(line.icon(), name, quantity, quantityWidth, statusColor(line.status())));
        }

        int height = PADDING * 2 + TEXT_LINE;
        if (subtitle != null)
        {
            height += TEXT_LINE;
        }
        if (info != null)
        {
            height += TEXT_LINE;
        }
        if (constructionText != null)
        {
            height += TEXT_LINE + BAR_HEIGHT + 3;
        }
        if (!rows.isEmpty())
        {
            height += 2 + rows.size() * (icons ? ICON_ROW : TEXT_LINE);
        }
        if (footer != null)
        {
            height += TEXT_LINE;
        }

        return new PanelLayout(
          panel.ref(),
          panel.pinned(),
          truncate(font, panel.title(), innerWidth),
          subtitle == null ? null : truncate(font, subtitle, innerWidth),
          info == null ? null : truncate(font, info, innerWidth),
          infoColor,
          constructionText == null ? null : truncate(font, constructionText, innerWidth),
          constructionPercent,
          footer,
          List.copyOf(rows),
          width,
          height);
    }

    /**
     * "in the hut / needed", with what the player carries in brackets while it still has to be handed over, so picking
     * blocks up shows on the line ("0 (+5) / 105") and not only through its colour.
     */
    private static String quantity(final HudSnapshot.Line line)
    {
        final boolean handOver = line.carried() > 0
                                   && (line.status() == RessourceAvailability.NEED_MORE || line.status() == RessourceAvailability.HAVE_ENOUGH);
        return handOver ? line.available() + " (+" + line.carried() + ") / " + line.needed() : line.available() + " / " + line.needed();
    }

    private static String truncate(final Font font, final String text, final int maxWidth)
    {
        if (font.width(text) <= maxWidth)
        {
            return text;
        }
        final int available = Math.max(0, maxWidth - font.width(ELLIPSIS));
        return font.plainSubstrByWidth(text, available) + ELLIPSIS;
    }

    private static int statusColor(final RessourceAvailability status)
    {
        return switch (status)
        {
            case DONT_HAVE -> 0xFFFF5555;   // red: missing, nobody has it
            case NEED_MORE -> 0xFFFFAA00;   // gold: the player carries some, not enough
            case IN_DELIVERY -> 0xFFFFFF55; // yellow: on its way to the builder
            case HAVE_ENOUGH -> 0xFF55FF55; // green: the player carries enough
            case NOT_NEEDED -> 0xFFAAAAAA;  // gray: the builder already has it
        };
    }
}
