package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.colony.buildings.moduleviews.MinerLevelManagementModuleView;
import com.minecolonies.core.network.messages.server.colony.building.miner.MinerRepairLevelMessage;
import com.minecolonies.core.network.messages.server.colony.building.miner.MinerSetLevelMessage;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import fr.mcvanillatweaks.client.citizen.VanillaIcons;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Miner's levels page (MineColonies' level management module): a card with the level the miner works on, then every
 * level of the shaft, top to bottom, with its depth and built nodes. "Mine here" makes a level the current one and
 * "repair" queues a repair of its shaft, with MineColonies' messages (the repair also posts its chat message). Repair
 * is off while a repair order exists for the level, and right after a click, until the server sends the order.
 * <pre>
 * +-----------------------------------------------+
 * | [pick] Current level: 03                      |
 * |        Y 45 · 12 nodes          5 levels      |
 * +-----------------------------------------------+
 * Levels
 * +-----------------------------------------------+
 * | [stone] Level 01           [Mine here][Repair]|
 * |         Y 61 · 8 nodes                        |
 * +-----------------------------------------------+
 * </pre>
 */
final class MinerLevelsTab extends HutTab
{
    private static final int CARD_HEIGHT = 34;
    private static final int ROW = 26;
    private static final int BUTTON_HEIGHT = 16;
    private static final String REPAIR = "com.minecolonies.coremod.gui.workerhuts.minerrepairnode";

    private final MinerLevelManagementModuleView module;
    private final ScrollArea list = new ScrollArea();
    /** Levels whose repair was asked during this opening: MineColonies only sends the order on its next update. */
    private final Set<Integer> repairAsked = new HashSet<>();

    MinerLevelsTab(final HutScreen screen, final MinerLevelManagementModuleView module)
    {
        super(screen);
        this.module = module;
    }

    private List<Tuple<Integer, Integer>> levels()
    {
        return module.levelsInfo == null ? List.of() : module.levelsInfo;
    }

    // ------------------------------------------------------------------ shared with the home page

    /** Two-digit level number, as MineColonies writes it. */
    static String number(final int index)
    {
        return String.format("%02d", index + 1);
    }

    /** The level's height as MineColonies shows it ("depth": the floor's Y + 1), and its built nodes. */
    static Component detail(final Tuple<Integer, Integer> level)
    {
        return Component.translatable("mcvanillatweaks.hut.miner.depth", level.getB() + 1)
                 .append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                 .append(Component.translatable("mcvanillatweaks.hut.miner.nodes", level.getA()));
    }

    /** Stone above Y 0, deepslate below, like the rock the level is dug in. */
    static ItemStack rock(final Tuple<Integer, Integer> level)
    {
        return new ItemStack(level.getB() < 0 ? Items.DEEPSLATE : Items.STONE);
    }

    /**
     * Current level card (white text, on an inset): the miner's level, its depth and nodes, and the number of levels.
     * Also drawn on the home page.
     */
    static void renderCurrent(final GuiGraphics g, final Font font, final MinerLevelManagementModuleView module, final int cx, final int cy, final int w)
    {
        final List<Tuple<Integer, Integer>> levels = module.levelsInfo == null ? List.of() : module.levelsInfo;
        VanillaIcons.item(g, new ItemStack(Items.LADDER), cx + 5, cy + 5, 16);
        final int tx = cx + 26;
        final int tw = w - 31;
        if (levels.isEmpty())
        {
            int ty = cy + 6;
            for (final var line : font.split(Component.translatable("com.minecolonies.coremod.gui.workerhuts.minernolevels"), tw))
            {
                g.drawString(font, line, tx, ty, Gui.LIST_SOFT, true);
                ty += 10;
            }
            return;
        }
        final boolean valid = module.current >= 0 && module.current < levels.size();
        final Component title = valid
                                  ? Component.translatable("mcvanillatweaks.hut.miner.current", number(module.current))
                                  : Component.translatable("mcvanillatweaks.hut.miner.no_current");
        g.drawString(font, Gui.fit(font, title, tw), tx, cy + 6, Gui.WHITE, true);
        final Component count = Component.translatable("mcvanillatweaks.hut.miner.level_count", levels.size());
        final int countW = font.width(count);
        if (valid)
        {
            g.drawString(font, Gui.fit(font, detail(levels.get(module.current)), tw - countW - 8), tx, cy + 18, Gui.LIST_SOFT, true);
        }
        g.drawString(font, count, cx + w - countW - 6, cy + 18, Gui.LIST_SOFT, true);
    }

    // ------------------------------------------------------------------ actions

    private boolean repairBlocked(final int index)
    {
        return repairAsked.contains(index) || module.doesWorkOrderExist(index);
    }

    private void mine(final int index)
    {
        if (index == module.current || index < 0 || index >= levels().size())
        {
            return;
        }
        module.current = index;
        new MinerSetLevelMessage(building(), index).sendToServer();
    }

    private void repair(final int index)
    {
        repairAsked.add(index);
        new MinerRepairLevelMessage(building(), index).sendToServer();
        if (mc.player != null)
        {
            MessageUtils.format("com.minecolonies.coremod.gui.workerhuts.miner.repair.enqueued").sendTo(new Player[] {mc.player});
        }
    }

    // ------------------------------------------------------------------ row buttons

    private String mineLabel(final int index)
    {
        return I18n.get(index == module.current ? "mcvanillatweaks.hut.miner.mining" : "com.minecolonies.coremod.gui.workerhuts.minerminenode");
    }

    private int buttonWidth(final String label)
    {
        return Math.max(48, font.width(label) + 10);
    }

    /** Button bounds in a row: [0] = repair (right), [1] = mine here; each {x, width}. */
    private int[][] buttons(final int index)
    {
        final int repairW = buttonWidth(I18n.get(REPAIR));
        final int repairX = list.listX() + list.listWidth() - 4 - repairW;
        final int mineW = buttonWidth(mineLabel(index));
        return new int[][] {{repairX, repairW}, {repairX - 4 - mineW, mineW}};
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        Gui.inset(g, x, y, width, CARD_HEIGHT);
        renderCurrent(g, font, module, x, y, width);
        if (Gui.inside(mouseX, mouseY, x, y, width, CARD_HEIGHT))
        {
            tooltip.set(List.of(Component.translatable("mcvanillatweaks.hut.miner.current_title"),
              Component.translatable("mcvanillatweaks.hut.miner.current_hint").withStyle(ChatFormatting.GRAY)));
        }

        final List<Tuple<Integer, Integer>> levels = levels();
        final int headY = y + CARD_HEIGHT + 8;
        heading(g, Component.translatable("com.minecolonies.coremod.gui.miner.levels"), x, headY, width);
        final int top = headY + HEADING;
        list.set(x, top, width, y + height - top, levels.size() * ROW, ROW);
        list.begin(g);
        if (levels.isEmpty())
        {
            g.drawString(font, Gui.fit(font, Component.translatable("com.minecolonies.coremod.gui.workerhuts.minernolevels"), list.listWidth() - 8),
              list.listX() + 4, list.visibleTop() + 6, Gui.LIST_SOFT, true);
        }
        for (int i = 0; i < levels.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final Tuple<Integer, Integer> level = levels.get(i);
            final boolean current = i == module.current;
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            if (current)
            {
                g.fill(list.listX(), rowY, list.listX() + list.listWidth(), rowY + ROW, Gui.ROW_HOVER);
            }
            g.renderItem(rock(level), list.listX() + 3, rowY + 5);

            final int[][] bounds = buttons(i);
            final int by = rowY + (ROW - BUTTON_HEIGHT) / 2;
            final boolean overRows = list.overRows(mouseX, mouseY);
            final boolean overRepair = overRows && Gui.inside(mouseX, mouseY, bounds[0][0], by, bounds[0][1], BUTTON_HEIGHT);
            final boolean overMine = overRows && Gui.inside(mouseX, mouseY, bounds[1][0], by, bounds[1][1], BUTTON_HEIGHT);
            final boolean canRepair = !repairBlocked(i);
            Gui.textButton(g, font, bounds[0][0], by, bounds[0][1], BUTTON_HEIGHT, I18n.get(REPAIR), canRepair, overRepair && canRepair);
            Gui.textButton(g, font, bounds[1][0], by, bounds[1][1], BUTTON_HEIGHT, mineLabel(i), !current, overMine && !current);

            final int textX = list.listX() + 24;
            final int textRight = bounds[1][0] - 6;
            final Component name = Component.translatable("mcvanillatweaks.hut.miner.level", number(i));
            g.drawString(font, Gui.fit(font, name, textRight - textX), textX, rowY + 3, current ? Gui.GREEN : Gui.WHITE, true);
            g.drawString(font, Gui.fit(font, detail(level), textRight - textX), textX, rowY + 14, Gui.LIST_SOFT, true);

            if (overRepair)
            {
                tooltip.set(Component.translatable(canRepair ? "mcvanillatweaks.hut.miner.repair_hint" : "mcvanillatweaks.hut.miner.repair_queued"));
            }
            else if (overMine)
            {
                tooltip.set(Component.translatable(current ? "mcvanillatweaks.hut.miner.mining_hint" : "mcvanillatweaks.hut.miner.mine_hint"));
            }
            else if (hovered)
            {
                final List<Component> lines = new ArrayList<>();
                lines.add(name);
                lines.add(Component.translatable("mcvanillatweaks.hut.miner.depth", level.getB() + 1).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("mcvanillatweaks.hut.miner.nodes", level.getA()).withStyle(ChatFormatting.GRAY));
                if (current)
                {
                    lines.add(Component.translatable("mcvanillatweaks.hut.miner.mining_hint").withStyle(ChatFormatting.GREEN));
                }
                if (module.doesWorkOrderExist(i))
                {
                    lines.add(Component.translatable("mcvanillatweaks.hut.miner.repair_queued").withStyle(ChatFormatting.GOLD));
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
        if (index < 0 || index >= levels().size())
        {
            return false;
        }
        final int[][] bounds = buttons(index);
        final int by = list.listY() + index * ROW + (ROW - BUTTON_HEIGHT) / 2;
        if (Gui.inside(mouseX, mouseY, bounds[0][0], by, bounds[0][1], BUTTON_HEIGHT) && !repairBlocked(index))
        {
            Gui.clickSound();
            repair(index);
            return true;
        }
        if (Gui.inside(mouseX, mouseY, bounds[1][0], by, bounds[1][1], BUTTON_HEIGHT) && index != module.current)
        {
            Gui.clickSound();
            mine(index);
            return true;
        }
        return false;
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

    /** The miner's level module, when MineColonies shows its page (not while a quarrier works from the hut). */
    @Nullable
    static MinerLevelManagementModuleView module(final IBuildingView building)
    {
        final MinerLevelManagementModuleView module = building.getModuleViewByType(MinerLevelManagementModuleView.class);
        return module != null && module.isPageVisible() ? module : null;
    }
}
