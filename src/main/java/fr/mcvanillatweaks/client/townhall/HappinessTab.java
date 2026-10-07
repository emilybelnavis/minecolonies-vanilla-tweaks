package fr.mcvanillatweaks.client.townhall;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.entity.citizen.happiness.IHappinessModifier;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import fr.mcvanillatweaks.client.citizen.VanillaIcons;
import fr.mcvanillatweaks.client.citizen.Vitals;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Happiness" tab. Left: the colony's overall happiness (large value and smileys) and how many citizens are happy,
 * satisfied, unsatisfied or unhappy (hovering a mood lists them). Right: each happiness modifier with its average factor
 * over the citizens (sum of the factors divided by the number of citizens, like MineColonies' citizens page) and how
 * many citizens it affects. The modifiers that change happiness come first, most negative first.
 * <pre>
 * Overall happiness        Happiness modifiers
 * +------------------+     +------------------------------+
 * | 8.6 / 10         |     | :( Guards in the colony      |
 * | :) :) :) :) :)   |     |    3 citizens affected  x0.48 |
 * +------------------+     | ...                          |
 * Citizens' mood           |                              |
 * | :) Happy      4  |     |                              |
 * | ...              |     |                              |
 * </pre>
 */
final class HappinessTab extends TownHallTab
{
    private static final int ROW = 24;
    private static final int MOOD_ROW = 20;
    private static final int LEFT_WIDTH = 104;
    private static final int CARD_HEIGHT = 44;
    private static final int REFRESH_TICKS = 20;


    /** Citizen moods (our grouping of the 0-10 happiness), best first: a citizen gets the first whose minimum it reaches. */
    private enum Mood
    {
        HAPPY(7.5D, VanillaIcons.Mood.HAPPY, ChatFormatting.GREEN),
        SATISFIED(5.0D, VanillaIcons.Mood.SATISFIED, ChatFormatting.YELLOW),
        UNSATISFIED(2.5D, VanillaIcons.Mood.UNSATISFIED, ChatFormatting.GOLD),
        UNHAPPY(Double.NEGATIVE_INFINITY, VanillaIcons.Mood.UNHAPPY, ChatFormatting.RED);

        final double min;
        final VanillaIcons.Mood icon;
        final ChatFormatting colour;

        Mood(final double min, final VanillaIcons.Mood icon, final ChatFormatting colour)
        {
            this.min = min;
            this.icon = icon;
            this.colour = colour;
        }

        static Mood of(final double happiness)
        {
            for (final Mood mood : values())
            {
                if (happiness >= mood.min)
                {
                    return mood;
                }
            }
            return UNHAPPY;
        }

        Component label()
        {
            return Component.translatable("mcvanillatweaks.townhall.mood." + name().toLowerCase(Locale.ROOT));
        }
    }

    private record Modifier(String id, double factor, int affected)
    {
    }

    private final ScrollArea area = new ScrollArea();
    private final Map<Mood, List<ICitizenDataView>> moods = new EnumMap<>(Mood.class);
    private List<Modifier> modifiers = List.of();
    private int ticks;

    HappinessTab(final TownHallScreen screen)
    {
        super(screen);
        refresh();
    }

    @Override
    void tick()
    {
        if (++ticks % REFRESH_TICKS == 0)
        {
            refresh();
        }
    }

    private void refresh()
    {
        final Map<String, Double> sums = new HashMap<>();
        final Map<String, Integer> affected = new HashMap<>();
        final int count = colony().getCitizens().size();
        moods.clear();
        for (final Mood mood : Mood.values())
        {
            moods.put(mood, new ArrayList<>());
        }
        for (final ICitizenDataView citizen : colony().getCitizens().values())
        {
            moods.get(Mood.of(citizen.getHappiness())).add(citizen);
            for (final String id : citizen.getHappinessHandler().getModifiers())
            {
                final IHappinessModifier modifier = citizen.getHappinessHandler().getModifier(id);
                if (modifier != null)
                {
                    final double factor = modifier.getFactor(null);
                    sums.merge(id, factor, Double::sum);
                    affected.merge(id, factor == 1.0D ? 0 : 1, Integer::sum);
                }
            }
        }
        for (final List<ICitizenDataView> list : moods.values())
        {
            list.sort(Comparator.comparingDouble(ICitizenDataView::getHappiness));
        }
        final List<Modifier> list = new ArrayList<>();
        for (final Map.Entry<String, Double> entry : sums.entrySet())
        {
            list.add(new Modifier(entry.getKey(), count == 0 ? 1.0D : entry.getValue() / count, affected.getOrDefault(entry.getKey(), 0)));
        }
        list.sort(Comparator.comparing((Modifier m) -> m.factor() == 1.0D).thenComparingDouble(Modifier::factor));
        modifiers = list;
    }

    private int rightX()
    {
        return x + LEFT_WIDTH + GAP;
    }

    @Override
    void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        renderSummary(g, mouseX, mouseY, tooltip);
        renderMoods(g, mouseX, mouseY, tooltip);
        renderModifiers(g, mouseX, mouseY, tooltip);
    }

    /** Large overall value and the smileys, in a card. */
    private void renderSummary(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final double happiness = colony().getOverallHappiness();
        heading(g, Component.translatable("mcvanillatweaks.townhall.overall_happiness"), x, y, LEFT_WIDTH);
        final int cy = y + HEADING;
        Gui.inset(g, x, cy, LEFT_WIDTH, CARD_HEIGHT);
        final String value = TownHallScreen.format(happiness);
        g.pose().pushPose();
        g.pose().translate(x + 8, cy + 6, 0);
        g.pose().scale(2.0F, 2.0F, 1.0F);
        g.drawString(font, value, 0, 0, colour(Mood.of(happiness).colour), true);
        g.pose().popPose();
        g.drawString(font, "/ 10", x + 8 + font.width(value) * 2 + 4, cy + 13, Gui.LIST_SOFT, true);
        Vitals.happiness(g, happiness, x + 8, cy + CARD_HEIGHT - 15, LEFT_WIDTH - 16);
        if (Gui.inside(mouseX, mouseY, x, cy, LEFT_WIDTH, CARD_HEIGHT))
        {
            tooltip.set(List.of(Component.translatable("mcvanillatweaks.townhall.overall_happiness"),
              Component.translatable("mcvanillatweaks.townhall.happiness_hint").withStyle(ChatFormatting.GRAY)));
        }
    }

    /** One row per mood with its number of citizens; hovering a row lists them, least happy first. */
    private void renderMoods(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final int top = y + HEADING + CARD_HEIGHT + 8;
        heading(g, Component.translatable("mcvanillatweaks.townhall.moods"), x, top, LEFT_WIDTH);
        final int listTop = top + HEADING;
        final int listHeight = y + height - listTop;
        Gui.inset(g, x, listTop, LEFT_WIDTH, listHeight);
        final int step = Math.min(MOOD_ROW + 2, (listHeight - 2) / Mood.values().length);
        for (final Mood mood : Mood.values())
        {
            final int rowY = listTop + 1 + mood.ordinal() * step;
            final List<ICitizenDataView> citizens = moods.getOrDefault(mood, List.of());
            if (Gui.inside(mouseX, mouseY, x + 1, rowY, LEFT_WIDTH - 2, MOOD_ROW))
            {
                g.fill(x + 1, rowY, x + LEFT_WIDTH - 1, rowY + MOOD_ROW, Gui.ROW_HOVER);
                tooltip.set(moodTooltip(mood, citizens));
            }
            VanillaIcons.mood(g, mood.icon, x + 3, rowY + 2, 16);
            final String count = Integer.toString(citizens.size());
            final int countX = x + LEFT_WIDTH - 5 - font.width(count);
            final boolean none = citizens.isEmpty();
            g.drawString(font, Gui.fit(font, mood.label(), countX - x - 28), x + 23, rowY + 6, none ? Gui.LIST_SOFT : Gui.WHITE, true);
            g.drawString(font, count, countX, rowY + 6, none ? Gui.LIST_SOFT : colour(mood.colour), true);
        }
    }

    private static List<Component> moodTooltip(final Mood mood, final List<ICitizenDataView> citizens)
    {
        final List<Component> lines = new ArrayList<>();
        lines.add(mood.label().copy().withStyle(mood.colour));
        if (citizens.isEmpty())
        {
            lines.add(Component.translatable("mcvanillatweaks.townhall.mood.none").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        final int shown = Math.min(citizens.size(), 12);
        for (int i = 0; i < shown; i++)
        {
            final ICitizenDataView citizen = citizens.get(i);
            lines.add(Component.literal(citizen.getName() + "  ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(TownHallScreen.format(citizen.getHappiness())).withStyle(ChatFormatting.WHITE)));
        }
        if (citizens.size() > shown)
        {
            lines.add(Component.translatable("mcvanillatweaks.townhall.mood.more", citizens.size() - shown).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    /** Modifiers: the name on the first line, the citizens affected and the average factor on the second. */
    private void renderModifiers(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final int rx = rightX();
        final int rw = x + width - rx;
        heading(g, Component.translatable("com.minecolonies.coremod.gui.happiness.happinessmodifier"), rx, y, rw);
        area.set(rx, y + HEADING, rw, height - HEADING, modifiers.size() * ROW, ROW);
        area.begin(g);
        for (int i = 0; i < modifiers.size(); i++)
        {
            final int rowY = area.listY() + i * ROW;
            if (rowY + ROW < area.visibleTop() || rowY > area.visibleBottom())
            {
                continue;
            }
            final Modifier modifier = modifiers.get(i);
            final boolean neutral = modifier.factor() == 1.0D;
            final boolean hovered = area.hover(g, mouseX, mouseY, rowY, ROW);
            VanillaIcons.mood(g, VanillaIcons.Mood.ofFactor(modifier.factor()), area.listX() + 3, rowY + 4, 16);
            final int tx = area.listX() + 24;
            final int right = area.listX() + area.listWidth() - 4;
            final Component name = Component.translatable("com.minecolonies.coremod.gui.townhall.happiness." + modifier.id());
            g.drawString(font, Gui.fit(font, name, right - tx), tx, rowY + 3, neutral ? Gui.LIST_SOFT : Gui.WHITE, true);
            final String factor = "×" + String.format(Locale.ROOT, "%.2f", modifier.factor());
            final int factorX = right - font.width(factor);
            final Component detail = modifier.affected() == 0
                                       ? Component.translatable("mcvanillatweaks.townhall.no_effect")
                                       : Component.translatable("mcvanillatweaks.townhall.affected", modifier.affected());
            g.drawString(font, Gui.fit(font, detail, factorX - tx - 4), tx, rowY + 13, Gui.LIST_SOFT, true);
            g.drawString(font, factor, factorX, rowY + 13, neutral ? Gui.LIST_SOFT : factorColour(modifier.factor()), true);
            if (hovered)
            {
                tooltip.setAround(tooltip(modifier, name), mouseX, rowY, rowY + ROW);
            }
        }
        area.end(g);
    }

    private static int colour(final ChatFormatting colour)
    {
        return 0xFF000000 | colour.getColor();
    }

    private static int factorColour(final double factor)
    {
        return factor > 1.0D ? Gui.GREEN : factor > 0.75D ? Gui.YELLOW : Gui.RED;
    }


    private static List<Component> tooltip(final Modifier modifier, final Component name)
    {
        final double factor = modifier.factor();
        final Component quality;
        if (factor > 1.0D)
        {
            quality = Component.translatable("com.minecolonies.coremod.gui.happiness.positive").withStyle(ChatFormatting.GREEN);
        }
        else if (factor == 1.0D)
        {
            quality = Component.translatable("com.minecolonies.coremod.gui.happiness.neutral").withStyle(ChatFormatting.GRAY);
        }
        else if (factor > 0.75D)
        {
            quality = Component.translatable("com.minecolonies.coremod.gui.happiness.slightlynegative").withStyle(ChatFormatting.YELLOW);
        }
        else
        {
            quality = Component.translatable("com.minecolonies.coremod.gui.happiness.negative").withStyle(ChatFormatting.RED);
        }
        return List.of(name, quality,
          Component.translatable("com.minecolonies.coremod.gui.townhall.happiness.desc." + modifier.id()).withStyle(ChatFormatting.GRAY),
          Component.translatable("mcvanillatweaks.townhall.average_factor", String.format(Locale.ROOT, "%.2f", factor)).withStyle(ChatFormatting.GRAY));
    }

    @Override
    boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        return area.mouseClicked(mouseX, mouseY);
    }

    @Override
    boolean mouseDragged(final double mouseX, final double mouseY)
    {
        return area.mouseDragged(mouseY);
    }

    @Override
    void mouseReleased()
    {
        area.mouseReleased();
    }

    @Override
    boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        return area.mouseScrolled(mouseX, mouseY, delta);
    }
}
