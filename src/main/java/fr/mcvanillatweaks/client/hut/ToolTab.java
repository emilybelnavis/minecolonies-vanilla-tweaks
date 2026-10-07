package fr.mcvanillatweaks.client.hut;

import com.minecolonies.core.client.render.worldevent.HighlightManager;
import com.minecolonies.core.client.render.worldevent.highlightmanager.TimedBoxRenderData;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.modules.settings.GuardFollowModeSetting;
import com.minecolonies.core.colony.buildings.modules.settings.GuardPatrolModeSetting;
import com.minecolonies.core.colony.buildings.modules.settings.GuardTaskSetting;
import com.minecolonies.core.colony.buildings.moduleviews.SettingsModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.ToolModuleView;
import com.minecolonies.core.network.messages.server.colony.building.GiveToolMessage;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.IconButton;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import fr.mcvanillatweaks.client.citizen.VanillaIcons;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Tool page (MineColonies' tool window: a description and a button giving the hut's scepter). Guard huts make it their
 * patrol page: the scepter on the left, the guards' current mission and the patrol points set with the scepter on the
 * right (a click outlines a point in the world, like MineColonies' locate boxes). Other huts (forester, beekeeper)
 * get the scepter card alone.
 * <pre>
 * Guard scepter              Mission
 * +-------------------+     | [shield] Guard          |
 * |     [scepter]     |     Patrol points (3)
 * | description...    |     | 1  120, 64, -30   24 m |
 * +-------------------+     | 2  ...                  |
 * [ Get the tool      ]     [ Show in the world      ]
 * </pre>
 */
final class ToolTab extends HutTab
{
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW = 20;
    private static final int MISSION_HEIGHT = 28;
    /** Width of the scepter card when the hut has no patrol data. */
    private static final int CARD_WIDTH = 200;
    private static final String HIGHLIGHT_KEY = "mcvanillatweaks:patrol";
    private static final int HIGHLIGHT_COLOUR = 0x804499FF;

    private final ToolModuleView module;
    @Nullable
    private final AbstractBuildingGuards.View guards;
    private final ScrollArea list = new ScrollArea();
    private List<BlockPos> points = List.of();
    private IconButton show;

    ToolTab(final HutScreen screen, final ToolModuleView module)
    {
        super(screen);
        this.module = module;
        guards = screen.building() instanceof AbstractBuildingGuards.View view ? view : null;
        refresh();
    }

    private void refresh()
    {
        points = guards == null ? List.of() : guards.getPatrolTargets();
    }

    private ItemStack tool()
    {
        return new ItemStack(module.getTool());
    }

    // ------------------------------------------------------------------ layout

    private int cardX()
    {
        return guards == null ? x + (width - CARD_WIDTH) / 2 : x;
    }

    private int cardWidth()
    {
        return guards == null ? CARD_WIDTH : (width - GAP) / 2;
    }

    private int rightX()
    {
        return x + cardWidth() + GAP;
    }

    private int listTop()
    {
        return y + HEADING + MISSION_HEIGHT + 10 + HEADING;
    }

    private int buttonY()
    {
        return y + height - BUTTON_HEIGHT;
    }

    @Override
    protected void init()
    {
        add(new IconButton(cardX(), buttonY(), cardWidth(), BUTTON_HEIGHT, tool(), Component.translatable("mcvanillatweaks.hut.tool.give"),
          b -> new GiveToolMessage(building(), module.getTool()).sendToServer()));
        if (guards != null)
        {
            show = add(new IconButton(rightX(), buttonY(), width - cardWidth() - GAP, BUTTON_HEIGHT, new ItemStack(Items.ENDER_EYE),
              Component.translatable("mcvanillatweaks.hut.patrol.show"), b -> highlight(points)));
            show.active = !points.isEmpty();
        }
    }

    @Override
    protected void tick()
    {
        refresh();
        if (show != null)
        {
            show.active = !points.isEmpty();
        }
    }

    /** Outlines the points in the world for a minute, numbered like the list, and closes the screen to show them. */
    private void highlight(final List<BlockPos> targets)
    {
        if (targets.isEmpty())
        {
            return;
        }
        HighlightManager.clearHighlightsForKey(HIGHLIGHT_KEY);
        for (final BlockPos pos : targets)
        {
            HighlightManager.addHighlight(HIGHLIGHT_KEY, pos.toString(), new TimedBoxRenderData(pos).setDuration(Duration.ofSeconds(60))
                                                                           .addText(Integer.toString(points.indexOf(pos) + 1)).setColor(HIGHLIGHT_COLOUR));
        }
        screen.onClose();
    }

    // ------------------------------------------------------------------ guard mission

    @Nullable
    private SettingsModuleView guardSettings()
    {
        for (final SettingsModuleView settings : building().getModuleViews(SettingsModuleView.class))
        {
            if (settings.getSetting(AbstractBuildingGuards.GUARD_TASK) != null)
            {
                return settings;
            }
        }
        return null;
    }

    @Nullable
    private String task()
    {
        final SettingsModuleView settings = guardSettings();
        final GuardTaskSetting task = settings == null ? null : settings.getSetting(AbstractBuildingGuards.GUARD_TASK);
        return task == null ? null : task.getValue();
    }

    private static ItemStack taskIcon(@Nullable final String task)
    {
        if (GuardTaskSetting.GUARD.equals(task))
        {
            return new ItemStack(Items.SHIELD);
        }
        if (GuardTaskSetting.FOLLOW.equals(task))
        {
            return new ItemStack(Items.LEAD);
        }
        if (GuardTaskSetting.PATROL_MINE.equals(task))
        {
            return new ItemStack(Items.IRON_PICKAXE);
        }
        return new ItemStack(Items.COMPASS);
    }

    /** The detail MineColonies' settings give for the task: patrol mode, follow mode, guarded mine. */
    private Component taskDetail(@Nullable final String task)
    {
        final SettingsModuleView settings = guardSettings();
        if (GuardTaskSetting.PATROL.equals(task) && settings != null)
        {
            final GuardPatrolModeSetting mode = settings.getSetting(AbstractBuildingGuards.PATROL_MODE);
            if (mode != null)
            {
                return Component.translatable("mcvanillatweaks.hut.patrol.mode", Component.translatable(mode.getValue()));
            }
        }
        if (GuardTaskSetting.FOLLOW.equals(task) && settings != null)
        {
            final GuardFollowModeSetting mode = settings.getSetting(AbstractBuildingGuards.FOLLOW_MODE);
            if (mode != null)
            {
                return Component.translatable(mode.getValue());
            }
        }
        if (GuardTaskSetting.PATROL_MINE.equals(task) && guards != null)
        {
            return guards.getMinePos() == null
                     ? Component.translatable("mcvanillatweaks.hut.patrol.no_mine")
                     : Component.translatable("mcvanillatweaks.hut.patrol.mine", guards.getMinePos().toShortString());
        }
        if (GuardTaskSetting.GUARD.equals(task))
        {
            return Component.translatable("mcvanillatweaks.hut.patrol.guard_spot");
        }
        return Component.translatable("mcvanillatweaks.hut.patrol.range", guards == null ? 0 : guards.getRange());
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        renderCard(g);
        if (guards != null)
        {
            renderMission(g, mouseX, mouseY, tooltip);
            renderPoints(g, mouseX, mouseY, tooltip);
        }
    }

    private void renderCard(final GuiGraphics g)
    {
        final int cx = cardX();
        final int w = cardWidth();
        final ItemStack tool = tool();
        heading(g, tool.getHoverName(), cx, y, w);
        final int top = y + HEADING;
        final int h = buttonY() - 6 - top;
        Gui.inset(g, cx, top, w, h);
        VanillaIcons.item(g, tool, cx + w / 2 - 16, top + 8, 32);
        final String key = "com.minecolonies.coremod.gui.tooldesc." + BuiltInRegistries.ITEM.getKey(module.getTool()).getPath();
        int ty = top + 48;
        for (final FormattedCharSequence line : font.split(Component.translatable(key), w - 12))
        {
            if (ty + 9 > top + h)
            {
                break;
            }
            g.drawString(font, line, cx + 6, ty, Gui.LIST_SOFT, true);
            ty += 10;
        }
    }

    private void renderMission(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final int rx = rightX();
        final int w = width - cardWidth() - GAP;
        heading(g, Component.translatable("mcvanillatweaks.hut.patrol.mission"), rx, y, w);
        final int top = y + HEADING;
        Gui.inset(g, rx, top, w, MISSION_HEIGHT);
        final boolean hovered = Gui.inside(mouseX, mouseY, rx, top, w, MISSION_HEIGHT);
        if (hovered)
        {
            g.fill(rx + 1, top + 1, rx + w - 1, top + MISSION_HEIGHT - 1, Gui.ROW_HOVER);
        }
        final String task = task();
        g.renderItem(taskIcon(task), rx + 5, top + 6);
        final Component name = task == null ? Component.translatable("mcvanillatweaks.hut.patrol.no_task") : Component.translatable(task);
        g.drawString(font, Gui.fit(font, name, w - 30), rx + 25, top + 5, Gui.WHITE, true);
        final Component detail = taskDetail(task);
        g.drawString(font, Gui.fit(font, detail, w - 30), rx + 25, top + 16, Gui.LIST_SOFT, true);
        if (hovered)
        {
            tooltip.set(List.of(name, detail.copy().withStyle(ChatFormatting.GRAY),
              Component.translatable("mcvanillatweaks.hut.patrol.mission_hint").withStyle(ChatFormatting.GRAY)));
        }
    }

    private void renderPoints(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final int rx = rightX();
        final int w = width - cardWidth() - GAP;
        final int top = listTop();
        heading(g, Component.translatable("mcvanillatweaks.hut.patrol.points", points.size()), rx, top - HEADING, w);
        list.set(rx, top, w, buttonY() - 6 - top, points.size() * ROW, ROW);
        list.begin(g);
        if (points.isEmpty())
        {
            int ty = list.visibleTop() + 6;
            for (final FormattedCharSequence line : font.split(Component.translatable("mcvanillatweaks.hut.patrol.no_points"), list.listWidth() - 8))
            {
                g.drawString(font, line, list.listX() + 4, ty, Gui.LIST_SOFT, true);
                ty += 10;
            }
        }
        final BlockPos hut = building().getPosition();
        for (int i = 0; i < points.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final BlockPos pos = points.get(i);
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            final String number = Integer.toString(i + 1);
            g.drawString(font, number, list.listX() + 12 - font.width(number) / 2, rowY + 6, Gui.YELLOW, true);
            final String distance = Component.translatable("mcvanillatweaks.hut.patrol.distance", (int) Math.sqrt(pos.distSqr(hut))).getString();
            final int dx = list.listX() + list.listWidth() - 4 - font.width(distance);
            g.drawString(font, distance, dx, rowY + 6, Gui.LIST_SOFT, true);
            g.drawString(font, Gui.fit(font, Component.literal(pos.toShortString()), dx - 6 - (list.listX() + 24)), list.listX() + 24, rowY + 6, Gui.WHITE, true);
            if (hovered)
            {
                tooltip.setAround(List.of(Component.translatable("mcvanillatweaks.hut.patrol.point", i + 1),
                  Component.literal(pos.toShortString()).withStyle(ChatFormatting.GRAY),
                  Component.translatable("mcvanillatweaks.hut.patrol.locate").withStyle(ChatFormatting.YELLOW)), mouseX, rowY, rowY + ROW);
            }
        }
        list.end(g);
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (guards == null)
        {
            return false;
        }
        if (list.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button != 0 || !list.overRows(mouseX, mouseY))
        {
            return false;
        }
        final int index = (int) Math.floor((mouseY - list.listY()) / ROW);
        if (index < 0 || index >= points.size())
        {
            return false;
        }
        Gui.clickSound();
        highlight(List.of(points.get(index)));
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
