package fr.mcvanillatweaks.client.townhall;

import com.minecolonies.api.colony.buildings.modules.ICommonSettingsModule;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingsModuleView;
import com.minecolonies.core.colony.buildings.modules.settings.BoolSetting;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * "Settings" tab: the colony's five switches, each a label and a vanilla on/off button like the game's options screens.
 * A click toggles the setting through the colony's settings module, exactly what MineColonies' buttons do.
 */
final class SettingsTab extends TownHallTab
{
    private static final int ROW = 28;
    private static final int BUTTON_WIDTH = 90;
    private static final int BUTTON_HEIGHT = 20;

    /** Setting and label, in MineColonies' order. */
    private record Entry(ISettingKey<BoolSetting> key, String label)
    {
    }

    private static final List<Entry> ENTRIES = List.of(
      new Entry(BuildingTownHall.MOVE_IN, "com.minecolonies.coremod.gui.townhall.movein"),
      new Entry(BuildingTownHall.AUTO_HIRING_MODE, "com.minecolonies.coremod.gui.townhall.jobhiring"),
      new Entry(BuildingTownHall.AUTO_HOUSING_MODE, "com.minecolonies.coremod.gui.townhall.housing"),
      new Entry(BuildingTownHall.ENTER_LEAVE_MESSAGES, "com.minecolonies.coremod.gui.townhall.entermsgs"),
      new Entry(BuildingTownHall.CONSTRUCTION_TAPE, "com.minecolonies.coremod.gui.townhall.tape"));

    private final List<Button> buttons = new ArrayList<>();

    SettingsTab(final TownHallScreen screen)
    {
        super(screen);
    }

    private int rowY(final int index)
    {
        return y + index * ROW;
    }

    @Override
    void init()
    {
        buttons.clear();
        for (int i = 0; i < ENTRIES.size(); i++)
        {
            final Entry entry = ENTRIES.get(i);
            buttons.add(add(Button.builder(Component.empty(), b -> toggle(entry))
                              .bounds(x + width - BUTTON_WIDTH, rowY(i) + (ROW - BUTTON_HEIGHT) / 2 - 2, BUTTON_WIDTH, BUTTON_HEIGHT).build()));
        }
        update();
    }

    @Override
    void tick()
    {
        update();
    }

    private void update()
    {
        final ICommonSettingsModule settings = colony().getSettings();
        for (int i = 0; i < ENTRIES.size(); i++)
        {
            final Entry entry = ENTRIES.get(i);
            final Button button = buttons.get(i);
            final BoolSetting setting = settings.getSetting(entry.key());
            if (setting == null)
            {
                button.visible = false;
                continue;
            }
            button.visible = true;
            button.setMessage(Component.translatable(setting.getValue()
                                                       ? "com.minecolonies.coremod.gui.workerhuts.retrieveon"
                                                       : "com.minecolonies.coremod.gui.workerhuts.retrieveoff"));
            final boolean active = !(settings instanceof ISettingsModuleView view) || setting.isActive(view);
            button.active = active;
            final Component hint = hint(entry.key(), setting, active);
            button.setTooltip(hint == null ? null : net.minecraft.client.gui.components.Tooltip.create(hint));
        }
    }

    /** The same hover text as MineColonies' setting buttons. */
    @Nullable
    private static Component hint(final ISettingKey<?> key, final BoolSetting setting, final boolean active)
    {
        final String tooltipKey = "com.minecolonies.coremod.setting.tooltip." + key.getUniqueId();
        if (!active && setting.getInactiveReason() != null)
        {
            return setting.getInactiveReason();
        }
        if (I18n.exists(tooltipKey))
        {
            return Component.translatable(tooltipKey);
        }
        return active ? setting.getToolTipText() : null;
    }

    private void toggle(final Entry entry)
    {
        colony().getSettings().trigger(entry.key());
        update();
    }

    @Override
    void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final int labelWidth = width - BUTTON_WIDTH - GAP;
        for (int i = 0; i < ENTRIES.size(); i++)
        {
            final int ry = rowY(i);
            if (i > 0)
            {
                // Thin separator, the light and dark lines of the vanilla panel.
                g.fill(x, ry - 3, x + width, ry - 2, 0xFF8B8B8B);
                g.fill(x, ry - 2, x + width, ry - 1, 0xFFFFFFFF);
            }
            final Component label = Component.literal(Component.translatable(ENTRIES.get(i).label()).getString().replaceAll("\\s*:\\s*$", ""));
            g.drawString(font, Gui.fit(font, label, labelWidth), x + 1, ry + (ROW - 8) / 2 - 2, Gui.LABEL, false);
            if (Gui.isCut(font, label, labelWidth) && Gui.inside(mouseX, mouseY, x, ry, labelWidth, ROW - 4))
            {
                tooltip.set(label);
            }
        }
    }
}
