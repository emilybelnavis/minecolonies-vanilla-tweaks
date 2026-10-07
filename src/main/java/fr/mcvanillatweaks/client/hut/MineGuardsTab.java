package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.modules.settings.GuardTaskSetting;
import com.minecolonies.core.colony.buildings.moduleviews.SettingsModuleView;
import com.minecolonies.core.network.messages.server.colony.building.guard.GuardSetMinePosMessage;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Mine guards page (MineColonies' mine guard module): the guard huts whose mission is "patrol the mine", one row per
 * hut since MineColonies assigns a whole hut to a mine, with its guards and where it patrols. "Assign" sends the hut to
 * this mine, up to MineColonies' limit for the mine's level (1, 1, 2, 2, 3), "unassign" frees it; a hut already guarding
 * another mine can't be taken. A click on the rest of a row opens the guard hut.
 * <pre>
 * Assigned guard huts: 1 / 2
 * Guard huts on the "patrol the mine" mission show up here.
 * +-----------------------------------------------+
 * | [tower] Guard Tower (level 2)       [Assign]  |
 * |         Steve, Alex · free                    |
 * +-----------------------------------------------+
 * </pre>
 */
final class MineGuardsTab extends HutTab
{
    private static final int ROW = 26;
    private static final int BUTTON_HEIGHT = 16;

    private record Hut(AbstractBuildingGuards.View view, List<ICitizenDataView> guards)
    {
    }

    private final ScrollArea list = new ScrollArea();
    private List<Hut> huts = List.of();
    private int assigned;
    private int ticks;

    MineGuardsTab(final HutScreen screen)
    {
        super(screen);
        refresh();
    }

    @Override
    protected void tick()
    {
        if (++ticks % 20 == 0)
        {
            refresh();
        }
    }

    private static boolean minePatrol(final IBuildingView view)
    {
        final SettingsModuleView settings = view.getModuleViewByType(SettingsModuleView.class);
        final GuardTaskSetting task = settings == null ? null : settings.getSetting(AbstractBuildingGuards.GUARD_TASK);
        return task != null && GuardTaskSetting.PATROL_MINE.equals(task.getValue());
    }

    /** MineColonies' list: the colony's guard huts on the mine mission, and how many already guard this mine. */
    private void refresh()
    {
        final IColonyView colony = building().getColony();
        final List<Hut> result = new ArrayList<>();
        int count = 0;
        if (colony != null)
        {
            for (final IBuildingView view : colony.getClientBuildingManager().getBuildings().values())
            {
                if (!(view instanceof AbstractBuildingGuards.View guards) || !minePatrol(view))
                {
                    continue;
                }
                if (here(guards))
                {
                    count++;
                }
                final List<ICitizenDataView> citizens = new ArrayList<>();
                for (final Integer id : guards.getGuards())
                {
                    final ICitizenDataView citizen = colony.getCitizen(id);
                    if (citizen != null)
                    {
                        citizens.add(citizen);
                    }
                }
                result.add(new Hut(guards, citizens));
            }
        }
        // This mine's huts first, then the free ones, then those of other mines, nearest first.
        final BlockPos pos = building().getPosition();
        result.sort(Comparator.<Hut>comparingInt(h -> here(h.view()) ? 0 : h.view().getMinePos() == null ? 1 : 2)
                      .thenComparingDouble(h -> h.view().getPosition().distSqr(pos)));
        huts = result;
        assigned = count;
    }

    private int maxGuards()
    {
        return maxGuards(building().getBuildingLevel());
    }

    /** MineColonies' limit of guard huts per mine, by the mine's level. */
    static int maxGuards(final int level)
    {
        return switch (level)
        {
            case 1, 2 -> 1;
            case 3, 4 -> 2;
            case 5 -> 3;
            default -> 0;
        };
    }

    private boolean here(final AbstractBuildingGuards.View view)
    {
        return building().getPosition().equals(view.getMinePos());
    }

    private boolean elsewhere(final AbstractBuildingGuards.View view)
    {
        return view.getMinePos() != null && !here(view);
    }

    private boolean canPress(final AbstractBuildingGuards.View view)
    {
        return here(view) || view.getMinePos() == null && assigned < maxGuards();
    }

    private void press(final AbstractBuildingGuards.View view)
    {
        if (here(view))
        {
            new GuardSetMinePosMessage(view).sendToServer();
            view.setMinePos(null);
        }
        else if (canPress(view))
        {
            new GuardSetMinePosMessage(view, building().getPosition()).sendToServer();
            view.setMinePos(building().getPosition());
        }
        refresh();
    }

    // ------------------------------------------------------------------ text

    private static String label(final boolean here)
    {
        return I18n.get(here ? "com.minecolonies.coremod.gui.hiring.buttonunassign" : "com.minecolonies.coremod.gui.hiring.buttonassign");
    }

    private int buttonWidth(final String label)
    {
        return Math.max(60, font.width(label) + 10);
    }

    private static Component name(final AbstractBuildingGuards.View view)
    {
        return Component.translatable("mcvanillatweaks.hut.mine_guards.hut", Component.translatable(view.getBuildingDisplayName()), view.getBuildingLevel());
    }

    private static Component guardNames(final Hut hut)
    {
        if (hut.guards().isEmpty())
        {
            return Component.translatable("mcvanillatweaks.hut.mine_guards.no_guard");
        }
        final List<String> names = new ArrayList<>();
        for (final ICitizenDataView guard : hut.guards())
        {
            names.add(guard.getName());
        }
        return Component.literal(String.join(", ", names));
    }

    private Component state(final AbstractBuildingGuards.View view)
    {
        if (here(view))
        {
            return Component.translatable("mcvanillatweaks.hut.mine_guards.here").withStyle(ChatFormatting.GREEN);
        }
        if (elsewhere(view))
        {
            return Component.translatable("mcvanillatweaks.hut.mine_guards.elsewhere").withStyle(ChatFormatting.GOLD);
        }
        return Component.translatable("mcvanillatweaks.hut.mine_guards.free");
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final int max = maxGuards();
        heading(g, Component.translatable("mcvanillatweaks.hut.mine_guards.count", assigned, max), x, y + 1, width);
        final var hint = font.split(Component.translatable("mcvanillatweaks.hut.mine_guards.hint"), width - 2);
        int ty = y + 14;
        for (final var line : hint.subList(0, Math.min(2, hint.size())))
        {
            g.drawString(font, line, x + 1, ty, Gui.LABEL_SOFT, false);
            ty += 10;
        }

        final int top = ty + 4;
        list.set(x, top, width, y + height - top, huts.size() * ROW, ROW);
        list.begin(g);
        if (huts.isEmpty())
        {
            int ey = list.visibleTop() + 6;
            for (final var line : font.split(Component.translatable("com.minecolonies.coremod.gui.workerhuts.miner.noguards"), list.listWidth() - 8))
            {
                g.drawString(font, line, list.listX() + 4, ey, Gui.LIST_SOFT, true);
                ey += 10;
            }
        }
        final IColonyView colony = building().getColony();
        for (int i = 0; i < huts.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final Hut hut = huts.get(i);
            final AbstractBuildingGuards.View view = hut.view();
            final boolean here = here(view);
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            if (here)
            {
                g.fill(list.listX(), rowY, list.listX() + list.listWidth(), rowY + ROW, Gui.ROW_HOVER);
            }
            g.renderItem(HutScreen.hutIcon(view), list.listX() + 3, rowY + 5);

            final String label = label(here);
            final int bw = buttonWidth(label);
            final int bx = list.listX() + list.listWidth() - 4 - bw;
            final int by = rowY + (ROW - BUTTON_HEIGHT) / 2;
            final boolean active = canPress(view);
            final boolean overButton = list.overRows(mouseX, mouseY) && Gui.inside(mouseX, mouseY, bx, by, bw, BUTTON_HEIGHT);
            Gui.textButton(g, font, bx, by, bw, BUTTON_HEIGHT, label, active, overButton && active);

            final int textX = list.listX() + 24;
            final int textRight = bx - 6;
            g.drawString(font, Gui.fit(font, name(view), textRight - textX), textX, rowY + 3, here ? Gui.GREEN : Gui.WHITE, true);
            final MutableComponent detail = guardNames(hut).copy()
                                              .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                                              .append(state(view));
            g.drawString(font, Gui.fit(font, detail, textRight - textX), textX, rowY + 14, Gui.LIST_SOFT, true);

            if (overButton)
            {
                if (here)
                {
                    tooltip.set(Component.translatable("mcvanillatweaks.hut.mine_guards.unassign_hint"));
                }
                else if (elsewhere(view))
                {
                    tooltip.set(Component.translatable("mcvanillatweaks.hut.mine_guards.elsewhere_hint", view.getMinePos().toShortString()));
                }
                else if (!active)
                {
                    tooltip.set(Component.translatable("mcvanillatweaks.hut.mine_guards.full_hint", max));
                }
                else
                {
                    tooltip.set(Component.translatable("mcvanillatweaks.hut.mine_guards.assign_hint"));
                }
            }
            else if (hovered)
            {
                final List<Component> lines = new ArrayList<>();
                lines.add(name(view));
                for (final ICitizenDataView guard : hut.guards())
                {
                    lines.add(Component.literal("- " + guard.getName()).withStyle(ChatFormatting.GRAY));
                }
                if (hut.guards().isEmpty())
                {
                    lines.add(Component.translatable("mcvanillatweaks.hut.mine_guards.no_guard").withStyle(ChatFormatting.GRAY));
                }
                lines.add(state(view).copy());
                if (elsewhere(view))
                {
                    lines.add(Component.literal(view.getMinePos().toShortString()).withStyle(ChatFormatting.DARK_GRAY));
                }
                lines.add(Component.translatable("com.minecolonies.coremod.gui.blocks.distance",
                  (int) Math.sqrt(view.getPosition().distSqr(building().getPosition()))).withStyle(ChatFormatting.GRAY));
                if (colony != null)
                {
                    lines.add(Component.translatable("mcvanillatweaks.hut.mine_guards.open").withStyle(ChatFormatting.YELLOW));
                }
                tooltip.setAround(lines, mouseX, rowY, rowY + ROW);
            }
        }
        list.end(g);
    }

    @Override
    protected boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (list.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button != 0 || !list.overRows(mouseX, mouseY))
        {
            return false;
        }
        final int index = (int) Math.floor((mouseY - list.listY()) / ROW);
        if (index < 0 || index >= huts.size())
        {
            return false;
        }
        final AbstractBuildingGuards.View view = huts.get(index).view();
        final String label = label(here(view));
        final int bw = buttonWidth(label);
        final int bx = list.listX() + list.listWidth() - 4 - bw;
        final int by = list.listY() + index * ROW + (ROW - BUTTON_HEIGHT) / 2;
        if (Gui.inside(mouseX, mouseY, bx, by, bw, BUTTON_HEIGHT))
        {
            if (canPress(view))
            {
                Gui.clickSound();
                press(view);
            }
            return true;
        }
        Gui.clickSound();
        view.getWindow().open();
        return true;
    }

    @Override
    protected boolean mouseDragged(final double mouseX, final double mouseY)
    {
        return list.mouseDragged(mouseY);
    }

    @Override
    protected void mouseReleased()
    {
        list.mouseReleased();
    }

    @Override
    protected boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        return list.mouseScrolled(mouseX, mouseY, delta);
    }

    /** Guard huts sent to this mine (home page card). */
    static int assignedHuts(final IBuildingView mine)
    {
        int count = 0;
        if (mine.getColony() != null)
        {
            for (final IBuildingView view : mine.getColony().getClientBuildingManager().getBuildings().values())
            {
                if (view instanceof AbstractBuildingGuards.View guards && minePatrol(view) && mine.getPosition().equals(guards.getMinePos()))
                {
                    count++;
                }
            }
        }
        return count;
    }
}
