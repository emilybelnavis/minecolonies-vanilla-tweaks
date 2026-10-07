package fr.mcvanillatweaks.client.citizen;

import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenHappinessHandler;
import com.minecolonies.api.entity.citizen.happiness.IHappinessModifier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * "Happiness" tab: overall happiness and every happiness modifier with MineColonies' own icons and descriptions. The
 * modifiers that currently change happiness come first (most negative first); the neutral ones, which MineColonies'
 * window hid, are listed after them, greyed out.
 */
final class HappinessPage extends PageView
{
    private static final int ROW = 20;


    private record Modifier(String id, double factor)
    {
    }

    private final ScrollArea area = new ScrollArea();
    private List<Modifier> modifiers = List.of();
    private int ticks;

    HappinessPage(final CitizenScreen screen)
    {
        super(screen);
        refresh();
    }

    @Override
    void tick()
    {
        if (++ticks % 20 == 0)
        {
            refresh();
        }
    }

    private void refresh()
    {
        final ICitizenHappinessHandler handler = citizen().getHappinessHandler();
        final List<Modifier> list = new ArrayList<>();
        for (final String id : handler.getModifiers())
        {
            final IHappinessModifier modifier = handler.getModifier(id);
            if (modifier != null)
            {
                list.add(new Modifier(id, modifier.getFactor(null)));
            }
        }
        list.sort(Comparator.comparing((Modifier m) -> m.factor() == 1.0D).thenComparingDouble(Modifier::factor));
        modifiers = list;
    }

    private int listTop()
    {
        return y + 36;
    }

    @Override
    void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final double happiness = citizen().getHappiness();
        heading(g, Component.translatable("mcvanillatweaks.citizen.happiness_overall"), y);
        final String value = CitizenFrame.format(happiness) + " / 10";
        g.drawString(font, value, x + width - 1 - font.width(value), y, Gui.LABEL, false);
        final int barX = x + (width - Gui.BAR_WIDTH) / 2;
        Gui.bar(g, barX, y + 13, (float) (happiness / CitizenInfo.MAX_HAPPINESS));
        if (Gui.inside(mouseX, mouseY, barX, y + 12, Gui.BAR_WIDTH, Gui.BAR_HEIGHT + 2))
        {
            tooltip.set(Component.translatable("mcvanillatweaks.citizen.happiness_value", CitizenFrame.format(happiness)));
        }

        heading(g, Component.translatable("com.minecolonies.coremod.gui.happiness.happinessmodifier"), listTop() - 11);
        area.set(x, listTop(), width, y + height - listTop(), modifiers.size() * ROW, ROW);
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
            VanillaIcons.mood(g, VanillaIcons.Mood.ofFactor(modifier.factor()), area.listX() + 3, rowY + 2, 16);
            final Component name = Component.translatable("com.minecolonies.coremod.gui.townhall.happiness." + modifier.id());
            final int textWidth = area.listWidth() - 27;
            if (neutral)
            {
                g.drawString(font, Gui.fit(font, name, textWidth), area.listX() + 24, rowY + 6, Gui.LIST_SOFT, true);
            }
            else
            {
                g.drawString(font, Gui.fit(font, name, textWidth), area.listX() + 24, rowY + 6, Gui.WHITE, true);
            }
            if (area.overRows(mouseX, mouseY) && Gui.inside(mouseX, mouseY, area.listX(), rowY, area.listWidth(), ROW))
            {
                g.fill(area.listX(), rowY, area.listX() + area.listWidth(), rowY + ROW, Gui.ROW_HOVER);
                tooltip.set(tooltip(modifier, name));
            }
        }
        area.end(g);
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
          Component.translatable("mcvanillatweaks.citizen.happiness_factor", String.format(java.util.Locale.ROOT, "%.2f", factor))
            .withStyle(ChatFormatting.GRAY));
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
