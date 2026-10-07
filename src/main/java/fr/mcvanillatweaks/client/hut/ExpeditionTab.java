package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.client.gui.citizen.MainWindowCitizen;
import com.minecolonies.core.colony.buildings.modules.expedition.ExpeditionLog;
import com.minecolonies.core.colony.buildings.moduleviews.ExpeditionLogModuleView;
import com.minecolonies.core.network.messages.server.colony.building.MarkBuildingDirtyMessage;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import fr.mcvanillatweaks.client.citizen.Vitals;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Expedition log of the nether mine (MineColonies' {@code ExpeditionLogModuleWindow}, unlocked by research): who went,
 * where they are, their health and food when last seen, their equipment, the monsters they beat and the loot they
 * carry (lost when they died). Like MineColonies' window, it asks the server for fresh data when opened and again one
 * second after each update ({@code MarkBuildingDirtyMessage}), but only with the permission the server checks.
 * <pre>
 * +--------------------------------------------------------+
 * | [netherrack] Alice Martin                 Adventuring  |
 * |              [hearts]      [food]                      |
 * +--------------------------------------------------------+
 * Equipment                  Loot: 12 items
 * [][][][][]                 [][][][][][][][]
 * Monsters beaten: 7         [][][][][][][][]
 * +-------------------+      ...
 * | [egg] Ghast   x3  |
 * +-------------------+
 * </pre>
 */
final class ExpeditionTab extends HutTab
{
    private static final int CARD_HEIGHT = 40;
    private static final int MOB_ROW = 18;
    private static final int LEFT_WIDTH = 142;
    private static final int REFRESH_DELAY = 20;

    private final ExpeditionLogModuleView module;
    private final ScrollArea mobs = new ScrollArea();
    private final ItemGrid loot = new ItemGrid();
    private List<ItemStack> lootStacks = List.of();
    private List<Tuple<EntityType<?>, Integer>> mobList = List.of();
    /** Ticks before the next refresh request; 0 = waiting for an update. */
    private int refreshIn = 1;

    ExpeditionTab(final HutScreen screen, final ExpeditionLogModuleView module)
    {
        super(screen);
        this.module = module;
        loot.setCounts(ItemStack::getCount, this::lootLines);
        readLog();
    }

    private ExpeditionLog log()
    {
        return module.getLog();
    }

    private void readLog()
    {
        final List<ItemStack> stacks = new ArrayList<>();
        for (final ItemStorage storage : log().getLoot())
        {
            stacks.add(storage.getItemStack().copyWithCount(Math.max(1, storage.getAmount())));
        }
        lootStacks = List.copyOf(stacks);
        loot.setItems(lootStacks);
        mobList = log().getMobs();
    }

    @Override
    protected void tick()
    {
        if (refreshIn > 0 && --refreshIn == 0)
        {
            final IColonyView colony = building().getColony();
            if (colony != null && mc.player != null && colony.getPermissions().hasPermission(mc.player, Action.MANAGE_HUTS))
            {
                new MarkBuildingDirtyMessage(building()).sendToServer();
            }
        }
        if (module.checkAndResetUpdated())
        {
            refreshIn = REFRESH_DELAY;
            readLog();
        }
    }

    private boolean killed()
    {
        return log().getStatus() == ExpeditionLog.Status.KILLED;
    }

    private List<Component> lootLines(final ItemStack stack)
    {
        return killed() ? List.of(Component.translatable("com.minecolonies.gui.workerhuts.expedition.killed.tip").withStyle(ChatFormatting.RED)) : List.of();
    }

    /** The expedition member among the colony's citizens (the log keeps only their name and entity id). */
    @Nullable
    private ICitizenDataView citizen()
    {
        final IColonyView colony = building().getColony();
        final String name = log().getName();
        if (colony == null || name == null)
        {
            return null;
        }
        for (final ICitizenDataView citizen : colony.getCitizens().values())
        {
            if (name.equals(citizen.getName()))
            {
                return citizen;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ drawing

    private static ItemStack statusIcon(final ExpeditionLog.Status status)
    {
        return new ItemStack(switch (status)
        {
            case NONE -> Items.MAP;
            case STARTING -> Items.FLINT_AND_STEEL;
            case IN_PROGRESS -> Items.NETHERRACK;
            case RETURNING_HOME -> Items.OBSIDIAN;
            case COMPLETED -> Items.FILLED_MAP;
            case KILLED -> Items.SKELETON_SKULL;
        });
    }

    private static int statusColour(final ExpeditionLog.Status status)
    {
        return switch (status)
        {
            case NONE -> Gui.LIST_SOFT;
            case STARTING, IN_PROGRESS, RETURNING_HOME -> Gui.YELLOW;
            case COMPLETED -> Gui.GREEN;
            case KILLED -> Gui.RED;
        };
    }

    @Override
    protected void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final ExpeditionLog log = log();
        final ExpeditionLog.Status status = log.getStatus() == null ? ExpeditionLog.Status.NONE : log.getStatus();
        renderCard(g, mouseX, mouseY, tooltip, log, status);

        final int top = y + CARD_HEIGHT + 8;
        if (status == ExpeditionLog.Status.NONE && log.getName() == null)
        {
            int ty = top + 4;
            for (final var line : font.split(Component.translatable("mcvanillatweaks.hut.expedition.none_hint"), width - 8))
            {
                g.drawString(font, line, x + 4, ty, Gui.LABEL_SOFT, false);
                ty += 10;
            }
            return;
        }

        // Left: equipment, then the monsters beaten.
        heading(g, Component.translatable("mcvanillatweaks.hut.expedition.equipment"), x, top, LEFT_WIDTH);
        final List<ItemStack> equipment = log.getEquipment();
        final int perRow = LEFT_WIDTH / ItemGrid.SLOT;
        final int equipmentRows = Math.max(1, (equipment.size() + perRow - 1) / perRow);
        final int slotsY = top + HEADING;
        if (equipment.isEmpty())
        {
            g.drawString(font, Component.translatable("mcvanillatweaks.hut.expedition.no_equipment"), x + 1, slotsY + 5, Gui.LABEL_SOFT, false);
        }
        for (int i = 0; i < equipment.size(); i++)
        {
            final int sx = x + (i % perRow) * ItemGrid.SLOT;
            final int sy = slotsY + (i / perRow) * ItemGrid.SLOT;
            Gui.slot(g, sx, sy);
            g.renderItem(equipment.get(i), sx + 1, sy + 1);
            g.renderItemDecorations(font, equipment.get(i), sx + 1, sy + 1);
            if (Gui.inside(mouseX, mouseY, sx, sy, ItemGrid.SLOT, ItemGrid.SLOT))
            {
                Gui.slotHighlight(g, sx, sy);
                tooltip.set(equipment.get(i));
            }
        }

        int beaten = 0;
        for (final Tuple<EntityType<?>, Integer> mob : mobList)
        {
            beaten += mob.getB();
        }
        final int mobsTop = slotsY + equipmentRows * ItemGrid.SLOT + 8;
        heading(g, Component.translatable("mcvanillatweaks.hut.expedition.mobs", beaten), x, mobsTop, LEFT_WIDTH);
        final int listTop = mobsTop + HEADING;
        mobs.set(x, listTop, LEFT_WIDTH, y + height - listTop, mobList.size() * MOB_ROW, MOB_ROW);
        mobs.begin(g);
        if (mobList.isEmpty())
        {
            int ty = mobs.visibleTop() + 6;
            for (final var line : font.split(Component.translatable("mcvanillatweaks.hut.expedition.no_mobs"), mobs.listWidth() - 8))
            {
                g.drawString(font, line, mobs.listX() + 4, ty, Gui.LIST_SOFT, true);
                ty += 10;
            }
        }
        for (int i = 0; i < mobList.size(); i++)
        {
            final int rowY = mobs.listY() + i * MOB_ROW;
            if (rowY + MOB_ROW < mobs.visibleTop() || rowY > mobs.visibleBottom())
            {
                continue;
            }
            final EntityType<?> type = mobList.get(i).getA();
            final boolean hovered = mobs.hover(g, mouseX, mouseY, rowY, MOB_ROW);
            final SpawnEggItem egg = SpawnEggItem.byId(type);
            g.renderItem(new ItemStack(egg != null ? egg : Items.SPAWNER), mobs.listX() + 2, rowY + 1);
            final String count = "×" + mobList.get(i).getB();
            final int countWidth = font.width(count);
            final int textX = mobs.listX() + 21;
            final int textWidth = mobs.listX() + mobs.listWidth() - 4 - countWidth - 4 - textX;
            g.drawString(font, Gui.fit(font, type.getDescription(), textWidth), textX, rowY + 5, Gui.WHITE, true);
            g.drawString(font, count, mobs.listX() + mobs.listWidth() - 4 - countWidth, rowY + 5, Gui.LIST_SOFT, true);
            if (hovered && Gui.isCut(font, type.getDescription(), textWidth))
            {
                tooltip.set(type.getDescription());
            }
        }
        mobs.end(g);

        // Right: the loot.
        final int rightX = x + LEFT_WIDTH + 12;
        final int rightWidth = x + width - rightX;
        int items = 0;
        for (final ItemStack stack : lootStacks)
        {
            items += stack.getCount();
        }
        heading(g, Component.translatable(killed() ? "mcvanillatweaks.hut.expedition.loot_lost" : "mcvanillatweaks.hut.expedition.loot", items),
          rightX, top, rightWidth);
        final int columns = ItemGrid.columnsFor(rightWidth);
        final int rows = Math.max(1, (y + height - (top + HEADING)) / ItemGrid.SLOT);
        loot.set(rightX, top + HEADING, columns, rows);
        loot.render(g, font, mouseX, mouseY, tooltip, stack -> false);
        if (lootStacks.isEmpty())
        {
            final Component none = Component.translatable("mcvanillatweaks.hut.expedition.no_loot");
            g.drawString(font, Gui.fit(font, none, columns * ItemGrid.SLOT - 8), rightX + 4, top + HEADING + 5, Gui.LABEL_SOFT, false);
        }
    }

    private void renderCard(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip, final ExpeditionLog log,
      final ExpeditionLog.Status status)
    {
        Gui.inset(g, x, y, width, CARD_HEIGHT);
        final ICitizenDataView citizen = citizen();
        final boolean hovered = Gui.inside(mouseX, mouseY, x, y, width, CARD_HEIGHT);
        if (hovered)
        {
            g.fill(x + 1, y + 1, x + width - 1, y + CARD_HEIGHT - 1, Gui.ROW_HOVER);
        }
        g.renderItem(statusIcon(status), x + 6, y + 12);

        final Component state = Component.translatable("com.minecolonies.gui.workerhuts.expedition." + status.name().toLowerCase(Locale.US));
        final int stateWidth = font.width(state);
        g.drawString(font, state, x + width - 6 - stateWidth, y + 6, statusColour(status), true);

        final int textX = x + 28;
        final Component name = log.getName() == null
                                 ? Component.translatable("mcvanillatweaks.hut.expedition.nobody")
                                 : Component.literal(log.getName());
        g.drawString(font, Gui.fit(font, name, x + width - 6 - stateWidth - 8 - textX), textX, y + 6, log.getName() == null ? Gui.LIST_SOFT : Gui.WHITE, true);

        if (log.getName() != null)
        {
            Vitals.hearts(g, log.getStat(ExpeditionLog.StatType.HEALTH), textX, y + 22);
            Vitals.food(g, log.getStat(ExpeditionLog.StatType.SATURATION), textX + Vitals.WIDTH + 12, y + 22);
        }

        if (hovered)
        {
            final List<Component> lines = new ArrayList<>();
            lines.add(name);
            lines.add(state.copy().withStyle(ChatFormatting.GRAY));
            if (log.getName() != null)
            {
                lines.add(Component.translatable("mcvanillatweaks.hut.expedition.last_seen").withStyle(ChatFormatting.GRAY));
            }
            if (citizen != null)
            {
                lines.add(Component.translatable("mcvanillatweaks.dashboard.open_citizen").withStyle(ChatFormatting.YELLOW));
            }
            tooltip.setAround(lines, mouseX, y, y + CARD_HEIGHT);
        }
    }

    @Override
    protected boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (mobs.mouseClicked(mouseX, mouseY) || loot.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button == 0 && Gui.inside(mouseX, mouseY, x, y, width, CARD_HEIGHT))
        {
            final ICitizenDataView citizen = citizen();
            if (citizen != null)
            {
                Gui.clickSound();
                new MainWindowCitizen(citizen).open();
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean mouseDragged(final double mouseX, final double mouseY)
    {
        return mobs.mouseDragged(mouseY) || loot.mouseDragged(mouseY);
    }

    @Override
    protected void mouseReleased()
    {
        mobs.mouseReleased();
        loot.mouseReleased();
    }

    @Override
    protected boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        return mobs.mouseScrolled(mouseX, mouseY, delta) || loot.mouseScrolled(mouseX, mouseY, delta);
    }
}
