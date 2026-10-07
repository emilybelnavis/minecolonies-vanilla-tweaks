package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.network.messages.server.colony.HireSpiesMessage;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.IconButton;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * The barracks' part of the home page, in place of MineColonies' barracks window ({@code WindowBarracksBuilding}) and its
 * spies dialog ({@code WindowsBarracksSpies}):
 * <ul>
 *   <li>its towers and their guards (towers are the huts whose parent is the barracks);</li>
 *   <li>whether a raid is on, and from level 3 where the current raid and the last ones came from, worded like MineColonies
 *       (distance from the barracks: under 50 blocks close, under 100 far, then really far, and the direction);</li>
 *   <li>from level 3, the spies button: 5 gold ingots taken from the player or the barracks' inventory, only during a raid
 *       and once ({@code HireSpiesMessage}, like the dialog's "hire"). It asks for a second click, since it costs gold.</li>
 * </ul>
 */
final class BarracksCard
{
    private static final int SPIES_LEVEL = 3;
    private static final int GOLD_COST = 5;
    private static final int LINE = 11;

    private final Minecraft mc = Minecraft.getInstance();
    private final Font font = mc.font;
    private final IBuildingView barracks;
    private IconButton spies;
    private boolean confirming;

    BarracksCard(final IBuildingView barracks)
    {
        this.barracks = barracks;
    }

    private IColonyView colony()
    {
        return barracks.getColony();
    }

    private boolean spiesUnlocked()
    {
        return barracks.getBuildingLevel() >= SPIES_LEVEL;
    }

    /** The spies button, at the bottom of the card's column; null below level 3, as MineColonies hides it. */
    IconButton createButton(final int x, final int y, final int w, final int h)
    {
        if (!spiesUnlocked())
        {
            spies = null;
            return null;
        }
        spies = new IconButton(x, y, w, h, new ItemStack(Items.GOLD_INGOT), label(), b -> spiesClicked());
        update();
        return spies;
    }

    int buttonSpace()
    {
        return spiesUnlocked() ? 24 : 0;
    }

    private Component label()
    {
        return confirming ? Component.translatable("mcvanillatweaks.hut.barracks.spies_confirm", GOLD_COST)
                          : Component.translatable("mcvanillatweaks.hut.barracks.spies", GOLD_COST);
    }

    void update()
    {
        if (spies == null)
        {
            return;
        }
        final boolean can = reason() == null;
        if (!can)
        {
            confirming = false;
        }
        spies.active = can;
        spies.setMessage(label());
    }

    /** Why the spies can't be hired now, as the dialog's button is disabled; null when they can. */
    private Component reason()
    {
        try
        {
            if (colony().areSpiesEnabled())
            {
                return Component.translatable("mcvanillatweaks.hut.barracks.spies_hired").withStyle(ChatFormatting.GREEN);
            }
            if (!colony().isRaiding())
            {
                return Component.translatable("mcvanillatweaks.hut.barracks.spies_no_raid").withStyle(ChatFormatting.RED);
            }
            final int gold = gold();
            if (gold < GOLD_COST)
            {
                return Component.translatable("mcvanillatweaks.hut.barracks.spies_gold", gold, GOLD_COST).withStyle(ChatFormatting.RED);
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            return Component.translatable("mcvanillatweaks.hut.barracks.spies_no_raid").withStyle(ChatFormatting.RED);
        }
        return null;
    }

    /** Gold ingots in the player's inventory and the barracks' own, which pay for the spies. */
    private int gold()
    {
        int count = 0;
        if (mc.player != null)
        {
            count += InventoryUtils.getItemCountInItemHandler(new InvWrapper(mc.player.getInventory()), Items.GOLD_INGOT);
        }
        if (mc.level != null && mc.level.getBlockEntity(barracks.getID()) instanceof AbstractTileEntityRack rack)
        {
            count += InventoryUtils.getItemCountInItemHandler(rack.getInventory(), Items.GOLD_INGOT);
        }
        return count;
    }

    private void spiesClicked()
    {
        if (reason() != null)
        {
            return;
        }
        if (!confirming)
        {
            confirming = true;
            update();
            return;
        }
        confirming = false;
        new HireSpiesMessage(colony()).sendToServer();
        spies.active = false;
    }

    void spiesTooltip(final Tooltip tooltip, final int mouseX, final int mouseY)
    {
        if (spies == null || !Gui.inside(mouseX, mouseY, spies.getX(), spies.getY(), spies.getWidth(), spies.getHeight()))
        {
            return;
        }
        final List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("com.minecolonies.coremod.gui.workerhuts.spies"));
        for (final FormattedText line : font.getSplitter().splitLines(Component.translatable("com.minecolonies.coremod.gui.barracks.spies.desc"), 220, Style.EMPTY))
        {
            lines.add(Component.literal(line.getString()).withStyle(ChatFormatting.GRAY));
        }
        final Component reason = reason();
        if (reason != null)
        {
            lines.add(reason);
        }
        else if (confirming)
        {
            lines.add(Component.translatable("mcvanillatweaks.hut.barracks.spies_click_again").withStyle(ChatFormatting.YELLOW));
        }
        tooltip.set(lines);
    }

    // ------------------------------------------------------------------ card

    private record Tower(IBuildingView view, int guards)
    {
    }

    private List<Tower> towers()
    {
        final List<Tower> towers = new ArrayList<>();
        for (final IBuildingView view : colony().getClientBuildingManager().getBuildings().values())
        {
            if (view != barracks && barracks.getID().equals(view.getParent()))
            {
                towers.add(new Tower(view, view instanceof AbstractBuildingGuards.View guards ? guards.getGuards().size() : 0));
            }
        }
        return towers;
    }

    /** MineColonies' wording: "quite close in the north-east". */
    private Component where(final BlockPos pos)
    {
        final long distance = BlockPosUtil.getDistance2D(pos, barracks.getPosition());
        final String key = distance < 50 ? "com.minecolonies.coremod.max.schematic.quiteclose"
                             : distance < 100 ? "com.minecolonies.coremod.max.schematic.quitefar" : "com.minecolonies.coremod.max.schematic.reallyfar";
        return Component.translatable(key).append(" ").append(BlockPosUtil.calcDirection(barracks.getPosition(), pos).getLongText());
    }

    void render(final GuiGraphics g, final int x, final int top, final int w, final int h, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        Gui.inset(g, x, top, w, h);
        final int tx = x + 24;
        final int tw = w - 28;
        int ty = top + 5;

        // Towers and guards.
        final List<Tower> towers = towers();
        final int guards = towers.stream().mapToInt(Tower::guards).sum();
        g.renderItem(new ItemStack(Items.SHIELD), x + 4, ty - 1);
        g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.hut.barracks.towers", towers.size()), tw), tx, ty, Gui.WHITE, true);
        g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.hut.barracks.guards", guards), tw), tx, ty + 10, Gui.LIST_SOFT, true);
        if (Gui.inside(mouseX, mouseY, x, ty - 2, w, 22))
        {
            g.fill(x + 1, ty - 3, x + w - 1, ty + 19, Gui.ROW_HOVER);
            final List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("mcvanillatweaks.hut.barracks.towers", towers.size()));
            for (final Tower tower : towers)
            {
                lines.add(Component.translatable("mcvanillatweaks.hut.barracks.tower_line", tower.view().getBuildingLevel(), tower.guards()).withStyle(ChatFormatting.GRAY));
            }
            if (towers.isEmpty())
            {
                lines.add(Component.translatable("mcvanillatweaks.hut.barracks.no_towers").withStyle(ChatFormatting.GRAY));
            }
            tooltip.set(lines);
        }
        ty += 28;

        // Raid.
        final boolean raiding = colony().isRaiding();
        final List<BlockPos> spawns = colony().getLastSpawnPoints();
        g.renderItem(new ItemStack(raiding ? Items.CROSSBOW : Items.BELL), x + 4, ty - 1);
        g.drawString(font, Gui.fit(font, Component.translatable(raiding ? "mcvanillatweaks.hut.barracks.raid" : "mcvanillatweaks.hut.barracks.no_raid"), tw),
          tx, ty, raiding ? Gui.RED : Gui.GREEN, true);
        if (raiding && spiesUnlocked() && !spawns.isEmpty())
        {
            g.drawString(font, Gui.fit(font, where(spawns.get(spawns.size() - 1)), tw), tx, ty + 10, Gui.GOLD, true);
        }
        ty += 28;

        // Where the last raids came from (MineColonies leaves the current raid out of this list).
        g.drawString(font, Gui.fit(font, Component.translatable("com.minecolonies.coremod.gui.workerhuts.barbpoints"), w - 10), x + 5, ty, Gui.WHITE, true);
        ty += LINE + 2;
        if (!spiesUnlocked())
        {
            for (final var line : font.split(Component.translatable("mcvanillatweaks.hut.barracks.level_hint", SPIES_LEVEL), w - 10))
            {
                g.drawString(font, line, x + 5, ty, Gui.LIST_SOFT, true);
                ty += 10;
            }
            return;
        }
        final int count = spawns.size() - (raiding && !spawns.isEmpty() ? 1 : 0);
        if (count <= 0)
        {
            g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.hut.barracks.no_spawns"), w - 10), x + 5, ty, Gui.LIST_SOFT, true);
            return;
        }
        final int maxLines = Math.max(1, (top + h - 4 - ty) / LINE);
        for (int i = 0; i < Math.min(count, maxLines); i++)
        {
            final Component line = Component.literal((i + 1) + " : ").append(where(spawns.get(i)));
            g.drawString(font, Gui.fit(font, line, w - 10), x + 5, ty, Gui.LIST_SOFT, true);
            ty += LINE;
        }
    }
}
