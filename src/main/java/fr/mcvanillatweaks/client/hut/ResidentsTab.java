package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.HiringMode;
import com.minecolonies.api.colony.buildings.modules.settings.ISetting;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.client.gui.citizen.MainWindowCitizen;
import com.minecolonies.core.colony.buildings.modules.settings.BoolSetting;
import com.minecolonies.core.colony.buildings.views.LivingBuildingView;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import com.minecolonies.core.network.messages.server.colony.building.home.AssignUnassignMessage;
import fr.mcvanillatweaks.client.citizen.Dropdown;
import fr.mcvanillatweaks.client.citizen.DropdownButton;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import fr.mcvanillatweaks.client.citizen.VanillaIcons;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Residents page of a house or tavern (MineColonies' citizen assignment window): the hiring mode on top, then one list
 * with the residents first ("unassign") and the citizens who could move in ("assign"), filtered and sorted like
 * MineColonies (homeless first, then by distance from their work to this house). Each row tells how far the citizen's
 * work would be, in green when closer than from their current home, and red past 300 blocks. The buttons are off, as in
 * MineColonies, while the colony or the house assigns homes by itself, and for travelling residents.
 * <pre>
 * Residents: 2 / 4                     Housing: [Manual     v]
 * +------------------------------------------------------------+
 * | (o) Alice · Farmer                              [Unassign] |
 * |     Work 12 blocks away                                     |
 * | (o) Denis · Unemployed                            [Assign] |
 * |     Homeless                                                |
 * +------------------------------------------------------------+
 * </pre>
 */
final class ResidentsTab extends HutTab
{
    private static final int ROW = 26;
    private static final int BUTTON_HEIGHT = 16;
    private static final int CONTROL_HEIGHT = 20;
    private static final int MODE_WIDTH = 120;
    private static final double FAR = 300.0D;

    private final LivingBuildingView home;
    private final ScrollArea list = new ScrollArea();
    private List<ICitizenDataView> residents = List.of();
    private List<ICitizenDataView> candidates = List.of();
    private DropdownButton modeButton;
    private int ticks;

    ResidentsTab(final HutScreen screen, final LivingBuildingView home)
    {
        super(screen);
        this.home = home;
        refresh();
    }

    // ------------------------------------------------------------------ widgets

    @Override
    protected void init()
    {
        modeButton = dropdown(x + width - MODE_WIDTH, y, MODE_WIDTH, modeName(home.getHiringMode()), () -> {
            final List<Dropdown.Entry> entries = new ArrayList<>();
            for (final HiringMode mode : HiringMode.values())
            {
                entries.add(new Dropdown.Entry(modeName(mode)));
            }
            return entries;
        }, () -> home.getHiringMode() == null ? 0 : home.getHiringMode().ordinal(), index -> {
            home.setHiringMode(HiringMode.values()[index]);
            modeButton.setMessage(modeName(home.getHiringMode()));
        });
    }

    private static Component modeName(@Nullable final HiringMode mode)
    {
        return Component.translatable("mcvanillatweaks.hut.hiring." + (mode == null ? HiringMode.DEFAULT : mode).name().toLowerCase(Locale.ROOT));
    }

    @Override
    protected void tick()
    {
        if (++ticks % 20 == 0)
        {
            refresh();
        }
    }

    // ------------------------------------------------------------------ citizens, as WindowAssignCitizen

    private void refresh()
    {
        final IColonyView colony = home.getColony();
        if (colony == null)
        {
            residents = List.of();
            candidates = List.of();
            return;
        }
        final List<ICitizenDataView> living = new ArrayList<>();
        for (final int id : home.getResidents())
        {
            final ICitizenDataView citizen = colony.getCitizen(id);
            if (citizen != null)
            {
                living.add(citizen);
            }
        }
        residents = living;
        final BlockPos here = home.getPosition();
        candidates = colony.getCitizens().values().stream()
                       .filter(c -> (!Objects.equals(c.getHomeBuilding(), c.getWorkBuilding()) || c.getHomeBuilding() == null) && !here.equals(c.getHomeBuilding()))
                       .sorted(Comparator.<ICitizenDataView>comparingInt(c -> c.getHomeBuilding() == null ? 0 : 1).thenComparingLong(c -> {
                           if (c.getWorkBuilding() == null)
                           {
                               return c.getHomeBuilding() == null ? 0L : Integer.MAX_VALUE;
                           }
                           return (long) (int) BlockPosUtil.getDistance(c.getWorkBuilding(), here);
                       }))
                       .toList();
    }

    private int size()
    {
        return residents.size() + candidates.size();
    }

    private ICitizenDataView citizen(final int index)
    {
        return index < residents.size() ? residents.get(index) : candidates.get(index - residents.size());
    }

    /** MineColonies only lets the player choose while neither the colony nor the house assigns homes by itself. */
    private boolean locked()
    {
        final HiringMode mode = home.getHiringMode();
        return (autoHousing() || mode != HiringMode.DEFAULT) && mode != HiringMode.MANUAL;
    }

    private boolean autoHousing()
    {
        try
        {
            final ISetting<?> setting = home.getColony().getSettings().getSetting(BuildingTownHall.AUTO_HOUSING_MODE);
            return setting instanceof BoolSetting bool && bool.getValue();
        }
        catch (final RuntimeException e)
        {
            return false;
        }
    }

    private boolean travelling(final ICitizenDataView citizen)
    {
        try
        {
            return home.getColony().getTravellingManager().isTravelling(citizen.getId());
        }
        catch (final RuntimeException e)
        {
            return false;
        }
    }

    /** Why the row's button is off, or null when it can be pressed. */
    @Nullable
    private String blocked(final ICitizenDataView citizen, final boolean resident)
    {
        if (locked())
        {
            return "com.minecolonies.coremod.gui.home.hire.warning";
        }
        if (resident)
        {
            return travelling(citizen) ? "com.minecolonies.coremod.gui.home.travelling" : null;
        }
        return residents.size() >= home.getMax() ? "mcvanillatweaks.hut.residents.full" : null;
    }

    private void assign(final ICitizenDataView citizen)
    {
        if (home.getResidents().size() < home.getMax())
        {
            home.addResident(citizen.getId());
            citizen.setHomeBuilding(home.getPosition());
            new AssignUnassignMessage(home, true, citizen.getId(), null).sendToServer();
            refresh();
        }
    }

    private void unassign(final ICitizenDataView citizen)
    {
        home.removeResident(citizen.getId());
        citizen.setHomeBuilding(null);
        new AssignUnassignMessage(home, false, citizen.getId(), null).sendToServer();
        refresh();
    }

    // ------------------------------------------------------------------ text

    private static Component job(final ICitizenDataView citizen)
    {
        if (citizen.getJobView() != null)
        {
            return Component.translatable(citizen.getJobView().getEntry().getTranslationKey());
        }
        return citizen.getJob() == null || citizen.getJob().isEmpty()
                 ? Component.translatable("com.minecolonies.coremod.gui.townhall.citizens.unemployed")
                 : Component.translatable(citizen.getJob());
    }

    /** Distance from the citizen's work to this house, coloured like MineColonies; empty without work. */
    private Component workHere(final ICitizenDataView citizen, final boolean resident)
    {
        final BlockPos work = citizen.getWorkBuilding();
        if (work == null)
        {
            return Component.empty();
        }
        final int distance = (int) BlockPosUtil.getDistance(work, home.getPosition());
        final MutableComponent text = Component.translatable("mcvanillatweaks.hut.residents.work", distance);
        if (distance > FAR)
        {
            return text.withStyle(ChatFormatting.RED);
        }
        if (!resident && citizen.getHomeBuilding() != null && distance < (int) BlockPosUtil.getDistance(work, citizen.getHomeBuilding()))
        {
            return text.withStyle(ChatFormatting.GREEN);
        }
        return text;
    }

    /** Where a candidate lives now. */
    private Component currentHome(final ICitizenDataView citizen)
    {
        final BlockPos current = citizen.getHomeBuilding();
        if (current == null)
        {
            return Component.translatable("com.minecolonies.coremod.gui.home.homeless").withStyle(ChatFormatting.GOLD);
        }
        if (citizen.getWorkBuilding() == null)
        {
            return Component.translatable("mcvanillatweaks.hut.residents.housed");
        }
        final int distance = (int) BlockPosUtil.getDistance(citizen.getWorkBuilding(), current);
        final MutableComponent text = Component.translatable("mcvanillatweaks.hut.residents.current", distance);
        return distance > FAR ? text.withStyle(ChatFormatting.RED) : text;
    }

    private Component detail(final ICitizenDataView citizen, final boolean resident)
    {
        final Component work = workHere(citizen, resident);
        if (resident)
        {
            return work.getString().isEmpty() ? Component.translatable("com.minecolonies.coremod.gui.home.liveshere") : work;
        }
        final MutableComponent text = Component.empty().append(currentHome(citizen));
        if (!work.getString().isEmpty())
        {
            text.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(Component.translatable("mcvanillatweaks.hut.residents.here")
                                                                                              .append(" ").append(work));
        }
        return text;
    }

    private String label(final boolean resident)
    {
        return I18n.get(resident ? "com.minecolonies.coremod.gui.hiring.buttonunassign" : "com.minecolonies.coremod.gui.hiring.buttonassign");
    }

    private int buttonWidth(final String label)
    {
        return Math.max(60, font.width(label) + 10);
    }

    // ------------------------------------------------------------------ drawing

    private int listTop()
    {
        return y + CONTROL_HEIGHT + (locked() ? 26 : 8);
    }

    @Override
    protected void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        heading(g, Component.translatable("mcvanillatweaks.hut.residents", residents.size(), home.getMax()), x, y + 6, width - MODE_WIDTH - 8);
        final Component housing = Component.translatable("mcvanillatweaks.hut.residents.mode");
        g.drawString(font, housing, x + width - MODE_WIDTH - 4 - font.width(housing), y + 6, Gui.LABEL, false);
        if (modeButton != null && modeButton.isHovered())
        {
            tooltip.set(List.of(Component.translatable("com.minecolonies.coremod.gui.hiringmode." + home.getHiringMode().name().toLowerCase(Locale.US)),
              Component.translatable("mcvanillatweaks.hut.residents.mode_hint").withStyle(ChatFormatting.GRAY)));
        }
        if (locked())
        {
            g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.hut.residents.locked"), width - 2), x + 1, y + CONTROL_HEIGHT + 6,
              Gui.LABEL_SOFT, false);
        }

        final int top = listTop();
        list.set(x, top, width, y + height - top, size() * ROW, ROW);
        list.begin(g);
        if (size() == 0)
        {
            g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.hut.no_candidates"), list.listWidth() - 8),
              list.listX() + 4, list.visibleTop() + 6, Gui.LIST_SOFT, true);
        }
        for (int i = 0; i < size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final ICitizenDataView citizen = citizen(i);
            final boolean resident = i < residents.size();
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            if (resident)
            {
                g.fill(list.listX(), rowY, list.listX() + list.listWidth(), rowY + ROW, Gui.ROW_HOVER);
            }
            VanillaIcons.item(g, VanillaIcons.status(citizen.getVisibleStatus()), list.listX() + 3, rowY + 5, 16);

            final String label = label(resident);
            final int bw = buttonWidth(label);
            final int bx = list.listX() + list.listWidth() - 4 - bw;
            final int by = rowY + (ROW - BUTTON_HEIGHT) / 2;
            final String blocked = blocked(citizen, resident);
            final boolean overButton = list.overRows(mouseX, mouseY) && Gui.inside(mouseX, mouseY, bx, by, bw, BUTTON_HEIGHT);
            Gui.textButton(g, font, bx, by, bw, BUTTON_HEIGHT, label, blocked == null, overButton && blocked == null);

            final int tx = list.listX() + 23;
            final int tw = bx - 6 - tx;
            final Component name = Component.literal(citizen.getName()).append(Component.literal(" · ").append(job(citizen)).withStyle(ChatFormatting.GRAY));
            g.drawString(font, Gui.fit(font, name, tw), tx, rowY + 3, resident ? Gui.GREEN : Gui.WHITE, true);
            g.drawString(font, Gui.fit(font, detail(citizen, resident), tw), tx, rowY + 14, Gui.LIST_SOFT, true);

            if (overButton)
            {
                tooltip.set(Component.translatable(blocked != null ? blocked
                                                     : resident ? "mcvanillatweaks.hut.residents.unassign_hint" : "mcvanillatweaks.hut.residents.assign_hint"));
            }
            else if (hovered)
            {
                final List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(citizen.getName()));
                lines.add(job(citizen).copy().withStyle(ChatFormatting.GRAY));
                if (!resident)
                {
                    lines.add(currentHome(citizen).copy().withStyle(s -> s.getColor() == null ? s.withColor(ChatFormatting.GRAY) : s));
                }
                final Component work = workHere(citizen, resident);
                if (!work.getString().isEmpty())
                {
                    lines.add(Component.translatable("mcvanillatweaks.hut.residents.here").append(" ").append(work).withStyle(ChatFormatting.GRAY));
                }
                lines.add(Component.translatable("mcvanillatweaks.hut.open_citizen").withStyle(ChatFormatting.YELLOW));
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
        if (index < 0 || index >= size())
        {
            return false;
        }
        final ICitizenDataView citizen = citizen(index);
        final boolean resident = index < residents.size();
        final int bw = buttonWidth(label(resident));
        final int bx = list.listX() + list.listWidth() - 4 - bw;
        final int by = list.listY() + index * ROW + (ROW - BUTTON_HEIGHT) / 2;
        if (Gui.inside(mouseX, mouseY, bx, by, bw, BUTTON_HEIGHT))
        {
            if (blocked(citizen, resident) == null)
            {
                Gui.clickSound();
                if (resident)
                {
                    unassign(citizen);
                }
                else
                {
                    assign(citizen);
                }
            }
            return true;
        }
        Gui.clickSound();
        new MainWindowCitizen(citizen).open();
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
}
