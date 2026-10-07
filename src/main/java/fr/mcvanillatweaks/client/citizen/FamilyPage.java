package fr.mcvanillatweaks.client.citizen;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * "Family" tab: parents, partner, siblings and children, as in MineColonies' family page. Relatives living in the
 * colony can be clicked to open their own family page. MineColonies only keeps the parents' names, so a parent is
 * looked up by name among the colony's citizens.
 */
final class FamilyPage extends PageView
{
    private static final int ROW = 13;
    /** Space above each group title but the first. */
    private static final int GROUP_GAP = 5;

    /** A line of the list: a section title, a relative, or a placeholder. */
    private record Line(Component text, boolean title, @Nullable ICitizenDataView relative)
    {
    }

    private final ScrollArea area = new ScrollArea();
    private List<Line> lines = List.of();
    private int ticks;

    FamilyPage(final CitizenScreen screen)
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
        final ICitizenDataView citizen = citizen();
        final IColonyView colony = citizen.getColony();
        final List<Line> list = new ArrayList<>();

        title(list, "com.minecolonies.coremod.gui.citizen.family.parents");
        final String parentA = citizen.getParents() == null ? "" : citizen.getParents().getA();
        final String parentB = citizen.getParents() == null ? "" : citizen.getParents().getB();
        list.add(new Line(parent(parentA), false, byName(colony, parentA)));
        list.add(new Line(parent(parentB), false, byName(colony, parentB)));

        title(list, "com.minecolonies.coremod.gui.citizen.family.partner");
        final Integer partner = citizen.getPartner();
        final ICitizenDataView partnerView = partner == null || colony == null ? null : colony.getCitizen(partner);
        if (partnerView == null)
        {
            none(list);
        }
        else
        {
            list.add(new Line(Component.literal(partnerView.getName()), false, partnerView));
        }

        relatives(list, colony, "com.minecolonies.coremod.gui.citizen.family.siblings", citizen.getSiblings());
        relatives(list, colony, "com.minecolonies.coremod.gui.citizen.family.children", citizen.getChildren());
        lines = list;
    }

    @Nullable
    private static ICitizenDataView byName(@Nullable final IColonyView colony, @Nullable final String name)
    {
        if (colony == null || name == null || name.isEmpty())
        {
            return null;
        }
        for (final ICitizenDataView other : colony.getCitizens().values())
        {
            if (name.equals(other.getName()))
            {
                return other;
            }
        }
        return null;
    }

    private static Component parent(@Nullable final String name)
    {
        return name == null || name.isEmpty() ? Component.translatable("com.minecolonies.coremod.gui.citizen.family.unknown")
                 : Component.literal(name);
    }

    private static void title(final List<Line> list, final String key)
    {
        list.add(new Line(Component.translatable(key), true, null));
    }

    private static void none(final List<Line> list)
    {
        list.add(new Line(Component.translatable("mcvanillatweaks.citizen.family.none"), false, null));
    }

    private static void relatives(final List<Line> list, @Nullable final IColonyView colony, final String key, @Nullable final List<Integer> ids)
    {
        title(list, key);
        if (ids == null || ids.isEmpty())
        {
            none(list);
            return;
        }
        for (final Integer id : ids)
        {
            final ICitizenDataView relative = colony == null || id == null ? null : colony.getCitizen(id);
            list.add(relative == null
                       ? new Line(Component.translatable("com.minecolonies.coremod.gui.citizen.family.unknown"), false, null)
                       : new Line(Component.literal(relative.getName()), false, relative));
        }
    }

    @Override
    void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        area.set(x, y, width, height, lineTop(lines.size()) + 3, ROW);
        area.begin(g);
        for (int i = 0; i < lines.size(); i++)
        {
            final int rowY = area.listY() + lineTop(i);
            if (rowY + ROW < area.visibleTop() || rowY > area.visibleBottom())
            {
                continue;
            }
            final Line line = lines.get(i);
            if (line.title())
            {
                g.drawString(font, line.text(), area.listX() + 4, rowY + 3, Gui.GOLD, true);
                continue;
            }
            final boolean hovered = line.relative() != null && area.overRows(mouseX, mouseY)
                                      && Gui.inside(mouseX, mouseY, area.listX(), rowY, area.listWidth(), ROW);
            if (hovered)
            {
                g.fill(area.listX(), rowY, area.listX() + area.listWidth(), rowY + ROW, Gui.ROW_HOVER);
                tooltip.set(Component.translatable("mcvanillatweaks.citizen.family.open", line.text()).withStyle(ChatFormatting.GRAY));
            }
            final int colour = line.relative() == null ? Gui.LIST_SOFT : hovered ? Gui.YELLOW : Gui.WHITE;
            g.drawString(font, Gui.fit(font, line.text(), area.listWidth() - 16), area.listX() + 12, rowY + 3, colour, true);
        }
        area.end(g);
    }

    @Override
    boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (area.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button != 0 || !area.overRows(mouseX, mouseY))
        {
            return false;
        }
        for (int i = 0; i < lines.size(); i++)
        {
            final int rowY = area.listY() + lineTop(i);
            final Line line = lines.get(i);
            if (line.relative() != null && Gui.inside(mouseX, mouseY, area.listX(), rowY, area.listWidth(), ROW))
            {
                Gui.clickSound();
                CitizenScreens.openOther(line.relative(), CitizenPage.FAMILY);
                return true;
            }
        }
        return false;
    }

    /** Top of line {@code index} in the list (or the list height for {@code lines.size()}). */
    private int lineTop(final int index)
    {
        int top = 1;
        for (int i = 0; i < index; i++)
        {
            top += ROW;
            if (i + 1 < lines.size() && lines.get(i + 1).title())
            {
                top += GROUP_GAP;
            }
        }
        return top;
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
