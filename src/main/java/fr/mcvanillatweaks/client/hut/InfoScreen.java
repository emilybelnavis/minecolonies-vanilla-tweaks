package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces MineColonies' hut guide ({@code WindowInfo}): its chapters ({@code com.minecolonies.coremod.info.<hut>.<i>},
 * titled by {@code .name}) listed on the left, the chosen one's text on the right, instead of one book page each.
 * "Back" returns to the hut like MineColonies' button.
 * <pre>
 * [hut] Farmer's Hut - guide
 * +-------------+ +--------------------------------+
 * | Introduction| | The farmer tends the fields... |
 * | Fields      | |                                |
 * +-------------+ +--------------------------------+
 * [Back]
 * </pre>
 */
public final class InfoScreen extends Screen
{
    private static final int WIDTH = 320;
    private static final int HEIGHT = 212;
    private static final int LIST_WIDTH = 104;
    private static final int ROW = 18;
    private static final int LINE = 10;

    private final IBuildingView building;
    private final List<String> chapters = new ArrayList<>();
    private final ScrollArea list = new ScrollArea();
    private final ScrollArea text = new ScrollArea();
    private final Tooltip tooltip = new Tooltip();
    private int selected;
    private int left;
    private int top;

    public InfoScreen(final IBuildingView building)
    {
        super(Component.translatable("mcvanillatweaks.hut.info"));
        this.building = building;
        final String prefix = prefix(building);
        for (int i = 0; I18n.exists(prefix + i); i++)
        {
            chapters.add(prefix + i);
        }
    }

    /** MineColonies' key prefix for a hut's guide. */
    static String prefix(final IBuildingView building)
    {
        return "com.minecolonies.coremod.info." + building.getBuildingType().getTranslationKey().replace("com.minecolonies.building.", "") + ".";
    }

    @Override
    protected void init()
    {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> building.openGui(false))
                              .bounds(left + 8, top + HEIGHT - 28, 70, 20).build());
    }

    private List<FormattedCharSequence> lines(final int width)
    {
        if (chapters.isEmpty())
        {
            return List.of();
        }
        return font.split(Component.translatable(chapters.get(selected)), width);
    }

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
        g.renderItem(HutScreen.hutIcon(building), left + 8, top + 7);
        final Component title = Component.translatable("mcvanillatweaks.hut.info.title", Component.translatable(building.getBuildingDisplayName()));
        g.drawString(font, Gui.fit(font, title, WIDTH - 36), left + 28, top + 11, Gui.LABEL, false);

        final int areaTop = top + 30;
        final int areaHeight = HEIGHT - 30 - 34;
        list.set(left + 8, areaTop, LIST_WIDTH, areaHeight, chapters.size() * ROW, ROW);
        list.begin(g);
        for (int i = 0; i < chapters.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (i == selected)
            {
                g.fill(list.listX(), rowY, list.listX() + list.listWidth(), rowY + ROW, Gui.ROW_SELECTED);
            }
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            final Component name = Component.translatable(chapters.get(i) + ".name");
            g.drawString(font, Gui.fit(font, name, list.listWidth() - 8), list.listX() + 4, rowY + 5, i == selected ? Gui.YELLOW : Gui.WHITE, true);
            if (hovered && Gui.isCut(font, name, list.listWidth() - 8))
            {
                tooltip.set(name);
            }
        }
        list.end(g);

        final int textX = left + 8 + LIST_WIDTH + 6;
        final int textWidth = left + WIDTH - 8 - textX;
        final List<FormattedCharSequence> lines = lines(textWidth - 22);
        text.set(textX, areaTop, textWidth, areaHeight, lines.size() * LINE + 10, LINE * 3);
        text.begin(g);
        int ty = text.listY() + 5;
        for (final FormattedCharSequence line : lines)
        {
            g.drawString(font, line, text.listX() + 5, ty, Gui.WHITE, true);
            ty += LINE;
        }
        text.end(g);

        if (chapters.size() > 1)
        {
            final String page = (selected + 1) + " / " + chapters.size();
            g.drawString(font, page, left + WIDTH - 8 - font.width(page), top + HEIGHT - 22, Gui.LABEL_SOFT, false);
        }
        tooltip.render(g, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (list.mouseClicked(mouseX, mouseY) || text.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button == 0 && list.overRows(mouseX, mouseY))
        {
            final int index = (int) Math.floor((mouseY - list.listY()) / ROW);
            if (index >= 0 && index < chapters.size() && index != selected)
            {
                Gui.clickSound();
                selected = index;
                // A new chapter starts at its top.
                text.set(0, 0, 0, 0, 0, LINE);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY)
    {
        return list.mouseDragged(mouseY) || text.mouseDragged(mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button)
    {
        list.mouseReleased();
        text.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        return list.mouseScrolled(mouseX, mouseY, scrollY) || text.mouseScrolled(mouseX, mouseY, scrollY)
                 || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
