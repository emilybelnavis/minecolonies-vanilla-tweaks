package fr.mcvanillatweaks.client.townhall;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.core.client.gui.citizen.MainWindowCitizen;
import com.minecolonies.core.network.messages.server.colony.citizen.RecallSingleCitizenMessage;
import fr.mcvanillatweaks.client.citizen.CitizenInfo;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.VanillaIcons;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import fr.mcvanillatweaks.client.citizen.Vitals;
import fr.mcvanillatweaks.client.locate.LocateButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * "Citizens" tab: the colony's citizens (searchable by name or job, like MineColonies' list) and the selected one's
 * portrait, job, health, food and happiness, with MineColonies' "recall" action and a button to open the citizen's own
 * screen.
 */
final class CitizensTab extends TownHallTab
{
    private static final int LIST_WIDTH = 130;
    private static final int SEARCH_HEIGHT = 18;
    private static final int ROW = 14;
    private static final int PORTRAIT_WIDTH = 50;
    private static final int PORTRAIT_HEIGHT = 72;
    private static final int BUTTON_HEIGHT = 20;

    private final ScrollArea area = new ScrollArea();
    private List<ICitizenDataView> citizens = List.of();
    private String filter = "";
    private int selectedId = -1;
    private Button recall;
    private Button open;
    private Button locate;

    CitizensTab(final TownHallScreen screen)
    {
        super(screen);
        refresh();
        if (!citizens.isEmpty())
        {
            selectedId = citizens.get(0).getId();
        }
    }

    @Override
    void init()
    {
        final EditBox search = add(new EditBox(font, x + 1, y + 1, LIST_WIDTH - 2, SEARCH_HEIGHT - 2, Component.translatable("mcvanillatweaks.townhall.search")));
        search.setHint(Component.translatable("mcvanillatweaks.townhall.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(filter);
        search.setResponder(text -> {
            if (!text.equals(filter))
            {
                filter = text;
                refresh();
            }
        });

        final int dx = detailX();
        final int dw = detailWidth();
        locate = add(LocateButton.citizen(dx, y + height - 3 * BUTTON_HEIGHT - 8, dw, this::selected));
        open = add(Button.builder(Component.translatable("mcvanillatweaks.townhall.open_citizen"), b -> openSelected())
                     .bounds(dx, y + height - 2 * BUTTON_HEIGHT - 4, dw, BUTTON_HEIGHT).build());
        recall = add(Button.builder(Component.translatable("mcvanillatweaks.townhall.recall"), b -> recallSelected())
                       .bounds(dx, y + height - BUTTON_HEIGHT, dw, BUTTON_HEIGHT)
                       .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("mcvanillatweaks.townhall.recall.hint")))
                       .build());
        updateButtons();
    }

    @Override
    void tick()
    {
        refresh();
        updateButtons();
    }

    /** Same filter and order as MineColonies' citizen list. */
    private void refresh()
    {
        final String lower = filter.toLowerCase(Locale.US);
        final List<ICitizenDataView> list = new ArrayList<>();
        for (final ICitizenDataView citizen : colony().getCitizens().values())
        {
            if (lower.isEmpty() || citizen.getName().toLowerCase(Locale.US).contains(lower)
                  || citizen.getJobComponent().getString().toLowerCase(Locale.US).contains(lower))
            {
                list.add(citizen);
            }
        }
        list.sort(Comparator.comparing(ICitizenDataView::getName));
        citizens = list;
    }

    @Nullable
    private ICitizenDataView selected()
    {
        return selectedId < 0 ? null : colony().getCitizen(selectedId);
    }

    private void updateButtons()
    {
        final boolean any = selected() != null;
        if (recall != null)
        {
            recall.active = any;
            open.active = any;
            locate.active = any;
        }
    }

    private int detailX()
    {
        return x + LIST_WIDTH + GAP;
    }

    private int detailWidth()
    {
        return width - LIST_WIDTH - GAP;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        area.set(x, y + SEARCH_HEIGHT + 4, LIST_WIDTH, height - SEARCH_HEIGHT - 4, citizens.size() * ROW, ROW);
        area.begin(g);
        if (citizens.isEmpty())
        {
            final Component none = Component.translatable("mcvanillatweaks.townhall.no_citizens");
            g.drawString(font, Gui.fit(font, none, area.listWidth() - 8), area.listX() + 4, area.visibleTop() + 6, Gui.LIST_SOFT, true);
        }
        for (int i = 0; i < citizens.size(); i++)
        {
            final int rowY = area.listY() + i * ROW;
            if (rowY + ROW < area.visibleTop() || rowY > area.visibleBottom())
            {
                continue;
            }
            final ICitizenDataView citizen = citizens.get(i);
            final boolean isSelected = citizen.getId() == selectedId;
            final boolean hovered = area.overRows(mouseX, mouseY) && Gui.inside(mouseX, mouseY, area.listX(), rowY, area.listWidth(), ROW);
            if (isSelected)
            {
                g.fill(area.listX(), rowY, area.listX() + area.listWidth(), rowY + ROW, Gui.ROW_SELECTED);
            }
            else if (hovered)
            {
                g.fill(area.listX(), rowY, area.listX() + area.listWidth(), rowY + ROW, Gui.ROW_HOVER);
            }
            final Component name = Component.literal(citizen.getName());
            g.drawString(font, Gui.fit(font, name, area.listWidth() - 8), area.listX() + 4, rowY + 3, isSelected ? Gui.YELLOW : Gui.WHITE, true);
            if (hovered)
            {
                tooltip.setAround(citizenTooltip(citizen), mouseX, rowY, rowY + ROW);
            }
        }
        area.end(g);

        final ICitizenDataView citizen = selected();
        if (citizen != null)
        {
            renderDetail(g, citizen, mouseX, mouseY, tooltip);
        }
    }

    /** Job and skills, as in MineColonies' list tooltip. */
    private static List<Component> citizenTooltip(final ICitizenDataView citizen)
    {
        final List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(citizen.getName()));
        lines.add(CitizenInfo.jobName(citizen).copy().withStyle(ChatFormatting.GRAY));
        for (final Skill skill : Skill.values())
        {
            lines.add(CitizenInfo.skillName(skill).copy().append(" : " + CitizenInfo.skillLevel(citizen, skill)).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    private void renderDetail(final GuiGraphics g, final ICitizenDataView citizen, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final int dx = detailX();
        final LivingEntity entity = CitizenInfo.entity(citizen);
        Gui.inset(g, dx, y, PORTRAIT_WIDTH, PORTRAIT_HEIGHT, 0xFF000000);
        if (entity != null)
        {
            portrait(g, entity, dx, mouseX, mouseY);
        }
        else
        {
            final Component away = Component.translatable("mcvanillatweaks.citizen.away");
            g.drawString(font, away, dx + (PORTRAIT_WIDTH - font.width(away)) / 2, y + 32, Gui.LIST_SOFT, true);
        }

        final int tx = dx + PORTRAIT_WIDTH + 6;
        final int tw = detailWidth() - PORTRAIT_WIDTH - 6;
        final Component name = Component.literal(citizen.getName());
        g.drawString(font, Gui.fit(font, name, tw - 13), tx, y + 2, Gui.LABEL, false);
        Gui.gender(g, tx + Math.min(font.width(name), tw - 13) + 3, y + 1, citizen.isFemale() ? 'f' : 'm');
        g.drawString(font, Gui.fit(font, CitizenInfo.jobName(citizen), tw), tx, y + 13, Gui.LABEL_SOFT, false);

        final int vitalsY = y + 30;
        Vitals.hearts(g, citizen, tx, vitalsY);
        Vitals.food(g, citizen, tx, vitalsY + 11);
        Vitals.happiness(g, citizen.getHappiness(), tx, vitalsY + 22);
        if (Gui.inside(mouseX, mouseY, tx, vitalsY, Vitals.WIDTH, 9))
        {
            tooltip.set(Component.translatable("mcvanillatweaks.citizen.health_value", format(citizen.getHealth()), format(citizen.getMaxHealth())));
        }
        else if (Gui.inside(mouseX, mouseY, tx, vitalsY + 11, Vitals.WIDTH, 9))
        {
            tooltip.set(Component.translatable("mcvanillatweaks.citizen.saturation_value", format(citizen.getSaturation()),
              format(CitizenInfo.FULL_SATURATION)));
        }
        else if (Gui.inside(mouseX, mouseY, tx, vitalsY + 22, Vitals.WIDTH, 9))
        {
            tooltip.set(Component.translatable("mcvanillatweaks.citizen.happiness_value", format(citizen.getHappiness())));
        }

        final VisibleCitizenStatus status = citizen.getVisibleStatus();
        final Component activity = status == null ? Component.translatable("mcvanillatweaks.citizen.activity.idle") : Component.translatable(status.getTranslationKey());
        final int activityY = y + PORTRAIT_HEIGHT - 9;
        int ax = tx;
        final net.minecraft.world.item.ItemStack statusIcon = VanillaIcons.status(status);
        if (!statusIcon.isEmpty())
        {
            VanillaIcons.item(g, statusIcon, ax, activityY - 1, 10);
            ax += 12;
        }
        g.drawString(font, Gui.fit(font, activity, tw - (ax - tx)), ax, activityY, Gui.LABEL, false);

        final Component home = CitizenInfo.buildingName(citizen, citizen.getHomeBuilding(), "mcvanillatweaks.citizen.no_home");
        final Component work = CitizenInfo.buildingName(citizen, citizen.getWorkBuilding(), "mcvanillatweaks.citizen.no_work");
        final int infoY = y + PORTRAIT_HEIGHT + 8;
        line(g, Component.translatable("mcvanillatweaks.citizen.row.work"), work, dx, infoY, mouseX, mouseY, tooltip);
        line(g, Component.translatable("mcvanillatweaks.citizen.row.home"), home, dx, infoY + 12, mouseX, mouseY, tooltip);
    }

    /** Label, then the value in the soft colour, cut with a tooltip. */
    private void line(final GuiGraphics g, final Component label, final Component value, final int lx, final int ly, final int mouseX,
      final int mouseY, final Tooltip tooltip)
    {
        g.drawString(font, label, lx, ly, Gui.LABEL, false);
        final int vx = lx + font.width(label) + 6;
        final int room = detailX() + detailWidth() - vx;
        g.drawString(font, Gui.fit(font, value, room), vx, ly, Gui.LABEL_SOFT, false);
        if (Gui.isCut(font, value, room) && Gui.inside(mouseX, mouseY, lx, ly - 1, detailWidth(), 10))
        {
            tooltip.set(List.of(label, value));
        }
    }

    private void portrait(final GuiGraphics g, final LivingEntity entity, final int px, final int mouseX, final int mouseY)
    {
        // Hide the name tag MineColonies' renderer would draw above the head.
        final boolean hideGui = mc.options.hideGui;
        mc.options.hideGui = true;
        try
        {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, px + 1, y + 1, px + PORTRAIT_WIDTH - 1, y + PORTRAIT_HEIGHT - 1, 30, 0.0625F,
              mouseX, mouseY, entity);
        }
        finally
        {
            mc.options.hideGui = hideGui;
        }
    }

    private static String format(final double value)
    {
        return TownHallScreen.format(value);
    }

    // ------------------------------------------------------------------ input

    private void recallSelected()
    {
        final ICitizenDataView citizen = selected();
        if (citizen != null)
        {
            // Same message and button id as MineColonies' "recall" button.
            screen.trigger("recallone");
            new RecallSingleCitizenMessage(view(), citizen.getId()).sendToServer();
        }
    }

    private void openSelected()
    {
        final ICitizenDataView citizen = selected();
        if (citizen != null)
        {
            new MainWindowCitizen(citizen).open();
        }
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
        for (int i = 0; i < citizens.size(); i++)
        {
            final int rowY = area.listY() + i * ROW;
            if (Gui.inside(mouseX, mouseY, area.listX(), rowY, area.listWidth(), ROW))
            {
                Gui.clickSound();
                screen.trigger("name");
                selectedId = citizens.get(i).getId();
                updateButtons();
                return true;
            }
        }
        return false;
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
