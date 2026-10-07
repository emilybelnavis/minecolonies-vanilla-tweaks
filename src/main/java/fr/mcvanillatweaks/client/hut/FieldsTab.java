package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.client.render.worldevent.HighlightManager;
import com.minecolonies.core.client.render.worldevent.highlightmanager.TimedBoxRenderData;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.colony.buildingextensions.PlantationField;
import com.minecolonies.core.colony.buildings.moduleviews.FieldsModuleView;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingPlantation;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Fields page of the farmer and the plantation (MineColonies' fields module): the fields this hut works and the free
 * ones, sorted like MineColonies (owned first, nearest first). Each row shows what grows there (the farm field's seed,
 * the plantation field's plant), the distance and direction, the farm field's size and its next step. "Assign" and
 * "free" send MineColonies' messages and only work in manual mode, like MineColonies; a row click outlines the field
 * in the world for a minute and closes the screen.
 * <pre>
 * 2 / 3 fields used                    [x] Assign by hand
 * +------------------------------------------------------+
 * | [wheat] Wheat Seeds                [hoe]    [Free]   |
 * |         Distance: 12m N · 9 x 9                       |
 * +------------------------------------------------------+
 * </pre>
 */
final class FieldsTab extends HutTab
{
    private static final int ROW = 26;
    private static final int BUTTON_HEIGHT = 16;
    private static final String HIGHLIGHT_KEY = "mcvanillatweaks:field";
    private static final int HIGHLIGHT_COLOUR = 0x8055FF55;

    private final FieldsModuleView module;
    private final ScrollArea list = new ScrollArea();
    private List<IBuildingExtension> fields = List.of();
    private int ticks;

    FieldsTab(final HutScreen screen, final FieldsModuleView module)
    {
        super(screen);
        this.module = module;
        refresh();
    }

    @Nullable
    private BuildingPlantation.PlantationFieldsModuleView plantation()
    {
        return module instanceof BuildingPlantation.PlantationFieldsModuleView view ? view : null;
    }

    private void refresh()
    {
        fields = module.getFields();
    }

    @Override
    protected void tick()
    {
        if (++ticks % 20 == 0)
        {
            refresh();
        }
    }

    // ------------------------------------------------------------------ widgets

    @Override
    protected void init()
    {
        add(Checkbox.builder(Component.empty(), font).pos(x + width - 20, y).selected(module.assignFieldManually())
              .onValueChange((box, value) -> {
                  module.setAssignFieldManually(value);
                  refresh();
              }).build());
    }

    // ------------------------------------------------------------------ shared with the home page

    /** What grows on the field: the farm field's seed, the plantation field's plant; empty for a farm field without seed. */
    static ItemStack crop(final IBuildingExtension field)
    {
        if (field instanceof FarmField farm)
        {
            return farm.getSeed();
        }
        if (field instanceof PlantationField plantation && plantation.getModule() != null)
        {
            return new ItemStack(plantation.getModule().getItem());
        }
        return ItemStack.EMPTY;
    }

    /** The field's next step as MineColonies' stage icon tells it, with vanilla items. */
    static ItemStack stageIcon(final FarmField.Stage stage)
    {
        return switch (stage)
        {
            case EMPTY -> new ItemStack(Items.IRON_HOE);
            case HOED -> new ItemStack(Items.WHEAT_SEEDS);
            case PLANTED -> new ItemStack(Items.WHEAT);
        };
    }

    static Component name(final IBuildingExtension field)
    {
        final ItemStack crop = crop(field);
        return crop.isEmpty() ? Component.translatable("mcvanillatweaks.hut.fields.no_seed") : crop.getHoverName();
    }

    /** MineColonies' distance text: "Distance: 12m N", "Above", "Current plantation". */
    static Component distance(final IBuildingExtension field, final IBuildingView building)
    {
        final BlockPosUtil.DirectionResult direction = BlockPosUtil.calcDirection(building.getPosition(), field.getPosition());
        return switch (direction)
        {
            case SAME -> Component.translatable("com.minecolonies.coremod.gui.workerhuts.fields.plantation.direction.exact");
            case UP, DOWN -> direction.getLongText();
            default -> Component.translatable("com.minecolonies.coremod.gui.workerhuts.fields.distance",
              Component.literal(field.getSqDistance(building) + "m"), direction.getShortText());
        };
    }

    // ------------------------------------------------------------------ actions

    private boolean mine(final IBuildingExtension field)
    {
        return field.isTaken() && building().getID().equals(field.getBuildingId());
    }

    private void press(final IBuildingExtension field)
    {
        if (field.isTaken())
        {
            module.freeField(field);
        }
        else
        {
            module.assignField(field);
        }
        refresh();
    }

    /** Why the button is off, or null. */
    @Nullable
    private Component blocked(final IBuildingExtension field)
    {
        if (!module.assignFieldManually())
        {
            return Component.translatable("mcvanillatweaks.hut.fields.auto_hint");
        }
        if (!field.isTaken() && !module.canAssignField(field))
        {
            final MutableComponent warning = module.getFieldWarningTooltip(field);
            return warning == null ? Component.translatable("com.minecolonies.coremod.gui.workerhuts.fields.fieldcount.reached") : warning;
        }
        return null;
    }

    private String label(final IBuildingExtension field)
    {
        return I18n.get(field.isTaken() ? "mcvanillatweaks.hut.fields.free" : "com.minecolonies.coremod.gui.hiring.buttonassign");
    }

    private int buttonWidth(final String label)
    {
        return Math.max(56, font.width(label) + 10);
    }

    /** Outlines the field in the world for a minute, named after its crop, and closes the screen to show it. */
    private void highlight(final IBuildingExtension field)
    {
        HighlightManager.clearHighlightsForKey(HIGHLIGHT_KEY);
        HighlightManager.addHighlight(HIGHLIGHT_KEY, field.getPosition().toString(), new TimedBoxRenderData(field.getPosition()).setDuration(Duration.ofSeconds(60))
                                                                                       .addText(name(field).getString()).setColor(HIGHLIGHT_COLOUR));
        screen.onClose();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        heading(g, Component.translatable("com.minecolonies.coremod.gui.workerhuts.fields.fieldcount", module.getOwnedFields().size(), module.getMaxFieldCount()),
          x, y + 6, width / 2);
        final Component manual = Component.translatable("mcvanillatweaks.hut.fields.manual");
        final int manualX = x + width - 24 - font.width(manual);
        g.drawString(font, manual, manualX, y + 6, Gui.LABEL, false);
        if (Gui.inside(mouseX, mouseY, manualX, y, width - (manualX - x), 20))
        {
            tooltip.set(List.of(Component.translatable("com.minecolonies.coremod.gui.workerhuts.fields.assignmentmode"),
              Component.translatable(module.assignFieldManually() ? "mcvanillatweaks.hut.fields.manual_hint" : "mcvanillatweaks.hut.fields.auto_hint")
                .withStyle(ChatFormatting.GRAY)));
        }
        int top = y + 24;
        final BuildingPlantation.PlantationFieldsModuleView plantation = plantation();
        if (plantation != null)
        {
            g.drawString(font, Component.translatable("com.minecolonies.coremod.gui.workerhuts.fields.plantcount", plantation.getCurrentPlants(),
              plantation.getMaxConcurrentPlants()), x + 1, top, Gui.LABEL_SOFT, false);
            top += 12;
        }
        if (!module.assignFieldManually())
        {
            g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.hut.fields.auto_hint"), width - 2), x + 1, top, Gui.LABEL_SOFT, false);
            top += 12;
        }

        list.set(x, top, width, y + height - top, fields.size() * ROW, ROW);
        list.begin(g);
        if (fields.isEmpty())
        {
            int ey = list.visibleTop() + 6;
            for (final var line : font.split(Component.translatable(plantation != null ? "mcvanillatweaks.hut.fields.none_plantation" : "mcvanillatweaks.hut.fields.none"),
              list.listWidth() - 8))
            {
                g.drawString(font, line, list.listX() + 4, ey, Gui.LIST_SOFT, true);
                ey += 10;
            }
        }
        for (int i = 0; i < fields.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final IBuildingExtension field = fields.get(i);
            final boolean mine = mine(field);
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            if (mine)
            {
                g.fill(list.listX(), rowY, list.listX() + list.listWidth(), rowY + ROW, Gui.ROW_HOVER);
            }
            final ItemStack crop = crop(field);
            g.renderItem(crop.isEmpty() ? new ItemStack(Items.COARSE_DIRT) : crop, list.listX() + 3, rowY + 5);

            final String label = label(field);
            final int bw = buttonWidth(label);
            final int bx = list.listX() + list.listWidth() - 4 - bw;
            final int by = rowY + (ROW - BUTTON_HEIGHT) / 2;
            final Component blocked = blocked(field);
            final boolean overButton = list.overRows(mouseX, mouseY) && Gui.inside(mouseX, mouseY, bx, by, bw, BUTTON_HEIGHT);
            Gui.textButton(g, font, bx, by, bw, BUTTON_HEIGHT, label, blocked == null, overButton && blocked == null);

            int textRight = bx - 6;
            boolean overStage = false;
            if (field instanceof FarmField farm && !farm.getSeed().isEmpty())
            {
                final int sx = textRight - 16;
                g.renderItem(stageIcon(farm.getFieldStage()), sx, rowY + 5);
                overStage = list.overRows(mouseX, mouseY) && Gui.inside(mouseX, mouseY, sx, rowY + 5, 16, 16);
                if (overStage)
                {
                    tooltip.set(List.of(Component.translatable("com.minecolonies.coremod.gui.field.status.current", farm.getFieldStage().getStageText()),
                      Component.translatable("com.minecolonies.coremod.gui.field.status.next", farm.getFieldStage().getNextStage().getStageText())
                        .withStyle(ChatFormatting.GRAY)));
                }
                textRight = sx - 4;
            }

            final int tx = list.listX() + 24;
            g.drawString(font, Gui.fit(font, name(field), textRight - tx), tx, rowY + 3, mine ? Gui.GREEN : crop.isEmpty() ? Gui.LIST_SOFT : Gui.WHITE, true);
            g.drawString(font, Gui.fit(font, detail(field), textRight - tx), tx, rowY + 14, Gui.LIST_SOFT, true);

            if (overButton)
            {
                tooltip.set(blocked != null ? blocked.copy().withStyle(ChatFormatting.RED)
                              : Component.translatable(field.isTaken() ? "mcvanillatweaks.hut.fields.free_hint" : "mcvanillatweaks.hut.fields.assign_hint"));
            }
            else if (hovered && !overStage)
            {
                final List<Component> lines = new ArrayList<>();
                lines.add(name(field));
                lines.add(distance(field, building()).copy().withStyle(ChatFormatting.GRAY));
                lines.add(Component.literal(field.getPosition().toShortString()).withStyle(ChatFormatting.DARK_GRAY));
                if (field instanceof FarmField farm)
                {
                    lines.add(Component.translatable("mcvanillatweaks.hut.fields.size", size(farm, Direction.WEST, Direction.EAST), size(farm, Direction.NORTH, Direction.SOUTH))
                                .withStyle(ChatFormatting.GRAY));
                }
                if (field instanceof PlantationField plantationField)
                {
                    lines.add(Component.translatable("mcvanillatweaks.hut.fields.spots", plantationField.getWorkingPositions().size()).withStyle(ChatFormatting.GRAY));
                }
                lines.add(Component.translatable(mine ? "mcvanillatweaks.hut.fields.mine" : "mcvanillatweaks.hut.fields.free_field")
                            .withStyle(mine ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                lines.add(Component.translatable("mcvanillatweaks.hut.fields.locate").withStyle(ChatFormatting.YELLOW));
                tooltip.setAround(lines, mouseX, rowY, rowY + ROW);
            }
        }
        list.end(g);
    }

    private static int size(final FarmField field, final Direction a, final Direction b)
    {
        return field.getRadius(a) + field.getRadius(b) + 1;
    }

    private Component detail(final IBuildingExtension field)
    {
        final MutableComponent text = distance(field, building()).copy();
        if (field instanceof FarmField farm)
        {
            text.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
              .append(Component.translatable("mcvanillatweaks.hut.fields.size", size(farm, Direction.WEST, Direction.EAST), size(farm, Direction.NORTH, Direction.SOUTH)));
        }
        return text;
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
        if (index < 0 || index >= fields.size())
        {
            return false;
        }
        final IBuildingExtension field = fields.get(index);
        final int bw = buttonWidth(label(field));
        final int bx = list.listX() + list.listWidth() - 4 - bw;
        final int by = list.listY() + index * ROW + (ROW - BUTTON_HEIGHT) / 2;
        if (Gui.inside(mouseX, mouseY, bx, by, bw, BUTTON_HEIGHT))
        {
            if (blocked(field) == null)
            {
                Gui.clickSound();
                press(field);
            }
            return true;
        }
        Gui.clickSound();
        highlight(field);
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
