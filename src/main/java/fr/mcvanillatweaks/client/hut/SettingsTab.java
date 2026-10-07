package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.buildings.modules.settings.ISetting;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.research.util.ResearchConstants;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.settings.BeekeeperCollectionSetting;
import com.minecolonies.core.colony.buildings.modules.settings.BlockSetting;
import com.minecolonies.core.colony.buildings.modules.settings.BoolSetting;
import com.minecolonies.core.colony.buildings.modules.settings.BuilderModeSetting;
import com.minecolonies.core.colony.buildings.modules.settings.GuardPatrolModeSetting;
import com.minecolonies.core.colony.buildings.modules.settings.GuardTaskSetting;
import com.minecolonies.core.colony.buildings.modules.settings.IntSetting;
import com.minecolonies.core.colony.buildings.modules.settings.RecipeSetting;
import com.minecolonies.core.colony.buildings.modules.settings.StringSetting;
import com.minecolonies.core.colony.buildings.moduleviews.CraftingModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.SettingsModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.ToolModuleView;
import com.minecolonies.core.network.messages.server.colony.building.TriggerSettingMessage;
import fr.mcvanillatweaks.client.citizen.Dropdown;
import fr.mcvanillatweaks.client.citizen.DropdownButton;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.IconButton;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Settings page (MineColonies' settings module, shared by most huts): one row per setting, its name on the left and a
 * vanilla control on the right, like the game's options screens. On/off settings are buttons, settings with a list of
 * values are drop-downs, numbers are text fields and recipe choices list the hut's recipes. Every change goes through
 * the same module method or message as MineColonies' buttons. Setting types without a control here (block choice,
 * settings added by other mods) get a button that opens MineColonies' settings window.
 */
final class SettingsTab extends HutTab
{
    private static final int ROW = 26;
    private static final int CONTROL_WIDTH = 140;
    private static final int CONTROL_HEIGHT = 20;
    private static final int SIDE_BUTTON = 20;
    private static final int BAR = Gui.SCROLLER_WIDTH + 2;
    private static final String BEEKEEPER_BOTH = "com.minecolonies.core.apiary.setting.both";

    /** A row: its setting and the hover text of its control. */
    private record Row(ISettingKey<?> key, ISetting<?> setting, AbstractWidget control, Supplier<List<Component>> hint)
    {
    }

    private final SettingsModuleView module;
    private final List<Row> rows = new ArrayList<>();
    private List<ISettingKey<?>> keys = List.of();
    /** First visible row. */
    private int first;

    SettingsTab(final HutScreen screen, final SettingsModuleView module)
    {
        super(screen);
        this.module = module;
    }

    private int visibleRows()
    {
        return Math.max(1, (height + ROW - CONTROL_HEIGHT) / ROW);
    }

    private boolean scrolls()
    {
        return keys.size() > visibleRows();
    }

    private int rowY(final int index)
    {
        return y + (index - first) * ROW;
    }

    private int controlX()
    {
        return x + width - CONTROL_WIDTH - (scrolls() ? BAR + 4 : 0);
    }

    // ------------------------------------------------------------------ widgets

    @Override
    protected void init()
    {
        rows.clear();
        keys = new ArrayList<ISettingKey<?>>(module.getSettingsToShow());
        first = Math.max(0, Math.min(first, keys.size() - visibleRows()));
        for (int i = first; i < Math.min(keys.size(), first + visibleRows()); i++)
        {
            final ISettingKey<?> key = keys.get(i);
            final ISetting<?> setting = setting(key);
            if (setting != null)
            {
                rows.add(row(key, setting, controlX(), rowY(i)));
            }
        }
        update();
    }

    private Row row(final ISettingKey<?> key, final ISetting<?> setting, final int cx, final int cy)
    {
        if (setting instanceof BoolSetting bool)
        {
            final Button button = add(Button.builder(onOff(bool), b -> {
                module.trigger(key);
                b.setMessage(onOff(bool));
            }).bounds(cx, cy, CONTROL_WIDTH, CONTROL_HEIGHT).build());
            return new Row(key, setting, button, () -> hint(key, setting));
        }
        if (setting instanceof StringSetting string)
        {
            int width = CONTROL_WIDTH;
            if (setting instanceof GuardTaskSetting task && needsPositions(task))
            {
                width -= SIDE_BUTTON + 2;
                final IconButton positions = add(new IconButton(cx, cy, SIDE_BUTTON, CONTROL_HEIGHT, new ItemStack(Items.FILLED_MAP), Component.empty(), b -> openGuardTool()));
                positions.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("mcvanillatweaks.hut.guard_positions")));
            }
            final DropdownButton dropdown = dropdown(cx + CONTROL_WIDTH - width, cy, width, valueText(string, string.getValue()), () -> choices(string),
              () -> allowedValues(string).indexOf(string.getValue()), index -> pick(key, string, allowedValues(string).get(index)));
            return new Row(key, setting, dropdown, () -> setting instanceof GuardTaskSetting && GuardTaskSetting.PATROL_MINE.equals(string.getValue())
                                                           ? List.of(minePatrolHint())
                                                           : hint(key, setting));
        }
        if (setting instanceof IntSetting number)
        {
            final EditBox box = add(new EditBox(font, cx + 1, cy + 1, CONTROL_WIDTH - 2, CONTROL_HEIGHT - 2, Component.empty()));
            box.setMaxLength(9);
            box.setFilter(text -> text.matches("\\d*"));
            box.setValue(String.valueOf(number.getValue()));
            box.setResponder(text -> {
                // MineColonies' field: empty means 0 and is not sent, anything else is sent at once.
                if (text.isEmpty())
                {
                    number.setValue(0);
                    return;
                }
                try
                {
                    final int value = Integer.parseInt(text);
                    // The field is also refreshed from the server: don't send that value back.
                    if (value != number.getValue())
                    {
                        number.setValue(value);
                        module.trigger(key);
                    }
                }
                catch (final NumberFormatException ignored)
                {
                    // out of range: kept as typed, like MineColonies
                }
            });
            return new Row(key, setting, box, () -> hint(key, setting));
        }
        if (setting instanceof RecipeSetting recipe)
        {
            final DropdownButton dropdown = dropdown(cx, cy, CONTROL_WIDTH, recipeText(recipe), () -> {
                final List<Dropdown.Entry> entries = new ArrayList<>();
                for (final IRecipeStorage storage : recipes())
                {
                    entries.add(new Dropdown.Entry(storage.getPrimaryOutput().getHoverName()));
                }
                return entries;
            }, () -> recipeIndex(recipe), index -> {
                final List<IRecipeStorage> list = recipes();
                if (index < list.size())
                {
                    recipe.set(list.get(index));
                    module.trigger(key);
                    screen.rebuild();
                }
            });
            return new Row(key, setting, dropdown, () -> hint(key, setting));
        }
        // Block choice and unknown types: MineColonies' own window.
        final ItemStack icon = setting instanceof BlockSetting block && block.getValue() != null ? new ItemStack(block.getValue()) : new ItemStack(Items.WRITABLE_BOOK);
        final IconButton button = add(new IconButton(cx, cy, CONTROL_WIDTH, CONTROL_HEIGHT, icon, Component.translatable("mcvanillatweaks.hut.setting_edit"),
          b -> HutScreens.openOriginal(module.getWindow())));
        return new Row(key, setting, button, () -> {
            final List<Component> lines = new ArrayList<>(hint(key, setting));
            lines.add(Component.translatable("mcvanillatweaks.hut.setting_original").withStyle(ChatFormatting.GRAY));
            return lines;
        });
    }

    @Override
    protected void tick()
    {
        if (!keys.equals(module.getSettingsToShow()))
        {
            screen.rebuild();
            return;
        }
        update();
    }

    /** Values change when the server answers or another player edits them. */
    private void update()
    {
        for (final Row row : rows)
        {
            final ISetting<?> setting = setting(row.key());
            if (setting == null)
            {
                continue;
            }
            row.control().active = setting.isActive(module);
            if (setting instanceof BoolSetting bool)
            {
                row.control().setMessage(onOff(bool));
            }
            else if (setting instanceof StringSetting string)
            {
                row.control().setMessage(valueText(string, string.getValue()));
            }
            else if (setting instanceof IntSetting number && row.control() instanceof EditBox box && !box.isFocused()
                       && !box.getValue().equals(String.valueOf(number.getValue())))
            {
                box.setValue(String.valueOf(number.getValue()));
            }
            else if (setting instanceof RecipeSetting recipe)
            {
                row.control().setMessage(recipeText(recipe));
            }
        }
    }

    // ------------------------------------------------------------------ values

    private static Component onOff(final BoolSetting setting)
    {
        return Component.translatable(setting.getValue() ? "com.minecolonies.coremod.gui.workerhuts.retrieveon" : "com.minecolonies.coremod.gui.workerhuts.retrieveoff");
    }

    private static Component valueText(final StringSetting setting, final String value)
    {
        if (setting instanceof BuilderModeSetting)
        {
            return Component.translatable("com.ldtteam.structurize.iterators." + value);
        }
        return Component.translatable(value);
    }

    /** The values the setting can take, in MineColonies' order (some need a research). */
    private List<String> allowedValues(final StringSetting setting)
    {
        final List<String> values = new ArrayList<>();
        final List<String> all = setting.getSettings();
        for (int i = 0; i < all.size(); i++)
        {
            final boolean allowed = setting instanceof BeekeeperCollectionSetting
                                      ? !BEEKEEPER_BOTH.equals(all.get(i)) || research(ResearchConstants.BEEKEEP_2)
                                      : setting.isIndexAllowed(i);
            if (allowed)
            {
                values.add(all.get(i));
            }
        }
        return values;
    }

    private boolean research(final net.minecraft.resources.ResourceLocation effect)
    {
        return building().getColony().getResearchManager().getResearchEffects().getEffectStrength(effect) > 0;
    }

    private List<Dropdown.Entry> choices(final StringSetting setting)
    {
        final List<Dropdown.Entry> entries = new ArrayList<>();
        for (final String value : allowedValues(setting))
        {
            entries.add(new Dropdown.Entry(valueText(setting, value)));
        }
        return entries;
    }

    /**
     * Sets the chosen value and sends it, like MineColonies' settings module does after a click (it sends the whole
     * setting, the server keeps the value it receives).
     */
    private void pick(final ISettingKey<?> key, final StringSetting setting, final String value)
    {
        if (!setting.isActive(module) || value.equals(setting.getValue()))
        {
            return;
        }
        setting.set(value);
        new TriggerSettingMessage(module.getColony(), key, setting, module.getProducer().getRuntimeID(), building().getPosition()).sendToServer();
        screen.rebuild();
    }

    /** MineColonies cycles through the recipes of the hut's first crafting module. */
    private List<IRecipeStorage> recipes()
    {
        final CraftingModuleView crafting = building().getModuleViewByType(CraftingModuleView.class);
        return crafting == null ? List.of() : crafting.getRecipes();
    }

    private int recipeIndex(final RecipeSetting setting)
    {
        final List<IRecipeStorage> list = recipes();
        for (int i = 0; i < list.size(); i++)
        {
            if (list.get(i).getToken().equals(setting.getValue()))
            {
                return i;
            }
        }
        return -1;
    }

    private Component recipeText(final RecipeSetting setting)
    {
        final int index = recipeIndex(setting);
        final List<IRecipeStorage> list = recipes();
        if (index >= 0)
        {
            return list.get(index).getPrimaryOutput().getHoverName();
        }
        return list.isEmpty() ? Component.empty() : list.get(0).getPrimaryOutput().getHoverName();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Nullable
    private ISetting<?> setting(final ISettingKey<?> key)
    {
        return (ISetting<?>) module.getSetting((ISettingKey) key);
    }

    /** The same hover text as MineColonies' setting controls (none, or one line). */
    private List<Component> hint(final ISettingKey<?> key, final ISetting<?> setting)
    {
        final String tooltipKey = "com.minecolonies.coremod.setting.tooltip." + key.getUniqueId();
        final boolean active = setting.isActive(module);
        if (!active && setting.getInactiveReason() != null)
        {
            return List.of(setting.getInactiveReason());
        }
        if (I18n.exists(tooltipKey))
        {
            return List.of(Component.translatable(tooltipKey));
        }
        final Component text = active ? setting.getToolTipText() : null;
        return text == null ? List.of() : List.of(text);
    }

    // ------------------------------------------------------------------ guard towers

    /** MineColonies shows its "set positions" button for guarding a spot and for manual patrols. */
    private boolean needsPositions(final GuardTaskSetting task)
    {
        if (building().getModuleView(BuildingModules.GUARD_TOOL) == null)
        {
            return false;
        }
        if (GuardTaskSetting.GUARD.equals(task.getValue()))
        {
            return true;
        }
        if (GuardTaskSetting.PATROL.equals(task.getValue()))
        {
            final GuardPatrolModeSetting mode = module.getSetting(AbstractBuildingGuards.PATROL_MODE);
            return mode != null && GuardPatrolModeSetting.MANUAL.equals(mode.getValue());
        }
        return false;
    }

    private void openGuardTool()
    {
        final ToolModuleView tool = building().getModuleView(BuildingModules.GUARD_TOOL);
        if (tool != null)
        {
            tool.getWindow().open();
        }
    }

    private Component minePatrolHint()
    {
        if (building() instanceof AbstractBuildingGuards.View guards && guards.getMinePos() != null)
        {
            return Component.translatable("com.minecolonies.coremod.gui.worherhuts.patrollingmine", guards.getMinePos().toShortString());
        }
        return Component.translatable("com.minecolonies.coremod.job.guard.assignmine");
    }

    // ------------------------------------------------------------------ drawing

    private static Component label(final ISettingKey<?> key)
    {
        return Component.literal(Component.translatable("com.minecolonies.coremod.setting." + key.getUniqueId()).getString().replaceAll("\\s*:\\s*$", ""));
    }

    @Override
    protected void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        if (keys.isEmpty())
        {
            heading(g, Component.translatable("mcvanillatweaks.hut.no_settings"), x, y + 6, width);
            return;
        }
        final int labelWidth = controlX() - GAP - x;
        for (int i = first; i < Math.min(keys.size(), first + visibleRows()); i++)
        {
            final int ry = rowY(i);
            if (i > first)
            {
                // Thin separator, the light and dark lines of the vanilla panel.
                g.fill(x, ry - 4, controlX() + CONTROL_WIDTH, ry - 3, 0xFF8B8B8B);
                g.fill(x, ry - 3, controlX() + CONTROL_WIDTH, ry - 2, 0xFFFFFFFF);
            }
            final Component label = label(keys.get(i));
            g.drawString(font, Gui.fit(font, label, labelWidth), x + 1, ry + (CONTROL_HEIGHT - 8) / 2, Gui.LABEL, false);
            if (Gui.inside(mouseX, mouseY, x, ry, labelWidth, CONTROL_HEIGHT))
            {
                final List<Component> lines = new ArrayList<>();
                lines.add(label);
                final Row row = rowOf(keys.get(i));
                if (row != null)
                {
                    for (final Component line : row.hint().get())
                    {
                        lines.add(line.copy().withStyle(ChatFormatting.GRAY));
                    }
                }
                if (lines.size() > 1 || Gui.isCut(font, label, labelWidth))
                {
                    tooltip.set(lines);
                }
            }
        }
        for (final Row row : rows)
        {
            if (row.control().isHovered() && !row.hint().get().isEmpty())
            {
                tooltip.set(row.hint().get());
            }
        }
        if (scrolls())
        {
            final int max = keys.size() - visibleRows();
            Gui.scrollbar(g, x + width - BAR, y, visibleRows() * ROW - (ROW - CONTROL_HEIGHT), (float) first / max, true);
        }
    }

    @Nullable
    private Row rowOf(final ISettingKey<?> key)
    {
        for (final Row row : rows)
        {
            if (row.key().equals(key))
            {
                return row;
            }
        }
        return null;
    }

    @Override
    protected boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        if (!scrolls() || !Gui.inside(mouseX, mouseY, x, y, width, height))
        {
            return false;
        }
        final int next = Math.max(0, Math.min(keys.size() - visibleRows(), first - (int) Math.signum(delta)));
        if (next != first)
        {
            first = next;
            screen.rebuild();
        }
        return true;
    }

    @Override
    protected boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        final int barHeight = visibleRows() * ROW - (ROW - CONTROL_HEIGHT);
        if (!scrolls() || !Gui.inside(mouseX, mouseY, x + width - BAR, y, BAR, barHeight))
        {
            return false;
        }
        // A click on the track jumps there, like dragging the creative scroller.
        final int max = keys.size() - visibleRows();
        first = Math.max(0, Math.min(max, (int) Math.round((mouseY - y) / barHeight * max)));
        screen.rebuild();
        return true;
    }
}
