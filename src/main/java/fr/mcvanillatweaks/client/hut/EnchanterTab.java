package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.colony.buildings.moduleviews.EnchanterStationsModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Enchanter's gathering page (MineColonies' enchanter stations module): every hut with workers but the enchanter's,
 * closest first like MineColonies, with a checkbox telling whether the enchanter draws from its workers. A click on a
 * row switches it with the module's own {@code addWorker} / {@code removeWorker}, which send
 * {@code EnchanterWorkerSetMessage}.
 * <pre>
 * Huts drawn from: 2 / 5
 * The enchanter draws experience from the workers of the checked huts.
 * +-----------------------------------------------+
 * | [hut] Farm (level 3)                     [x]  |
 * |       24 m                                    |
 * +-----------------------------------------------+
 * </pre>
 */
final class EnchanterTab extends HutTab
{
    private static final int ROW = 24;
    private static final int BOX = 13;

    private final EnchanterStationsModuleView module;
    private final ScrollArea list = new ScrollArea();
    private List<IBuildingView> huts = List.of();
    private int ticks;

    EnchanterTab(final HutScreen screen, final EnchanterStationsModuleView module)
    {
        super(screen);
        this.module = module;
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

    /** MineColonies' list: huts with a worker module, the enchanter's excepted, by distance. */
    private void refresh()
    {
        final IColonyView colony = building().getColony();
        if (colony == null)
        {
            huts = List.of();
            return;
        }
        final List<IBuildingView> found = new ArrayList<>();
        for (final IBuildingView view : colony.getClientBuildingManager().getBuildings().values())
        {
            if (view instanceof AbstractBuildingView && !view.getModuleViews(WorkerBuildingModuleView.class).isEmpty()
                  && view.getBuildingType() != ModBuildings.enchanter.get())
            {
                found.add(view);
            }
        }
        found.sort(Comparator.comparingLong(view -> BlockPosUtil.getDistance2D(building().getPosition(), view.getPosition())));
        huts = found;
    }

    private boolean selected(final IBuildingView view)
    {
        return module.getBuildingsToGatherFrom().contains(view.getID());
    }

    private void toggle(final IBuildingView view)
    {
        if (selected(view))
        {
            module.removeWorker(view.getID());
        }
        else
        {
            module.addWorker(view.getID());
        }
    }

    private static Component name(final IBuildingView view)
    {
        return view.getCustomName() == null || view.getCustomName().isEmpty()
                 ? Component.translatable(view.getBuildingType().getTranslationKey())
                 : Component.literal(view.getCustomName());
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        int count = 0;
        for (final IBuildingView view : huts)
        {
            if (selected(view))
            {
                count++;
            }
        }
        heading(g, Component.translatable("mcvanillatweaks.hut.enchanter.count", count, huts.size()), x, y + 1, width);
        int ty = y + 14;
        for (final var line : font.split(Component.translatable("mcvanillatweaks.hut.enchanter.hint"), width - 2).stream().limit(2).toList())
        {
            g.drawString(font, line, x + 1, ty, Gui.LABEL_SOFT, false);
            ty += 10;
        }

        final int top = ty + 4;
        list.set(x, top, width, y + height - top, huts.size() * ROW, ROW);
        list.begin(g);
        if (huts.isEmpty())
        {
            g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.hut.enchanter.none"), list.listWidth() - 8), list.listX() + 4,
              list.visibleTop() + 6, Gui.LIST_SOFT, true);
        }
        for (int i = 0; i < huts.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final IBuildingView view = huts.get(i);
            final boolean on = selected(view);
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            g.renderItem(HutScreen.hutIcon(view), list.listX() + 3, rowY + 4);
            final int boxX = list.listX() + list.listWidth() - BOX - 5;
            g.blitSprite(Gui.checkbox(on, hovered), boxX, rowY + (ROW - BOX) / 2, BOX, BOX);
            final int textX = list.listX() + 24;
            final Component title = Component.translatable("mcvanillatweaks.hut.enchanter.hut", name(view), view.getBuildingLevel());
            g.drawString(font, Gui.fit(font, title, boxX - 6 - textX), textX, rowY + 3, on ? Gui.WHITE : Gui.LIST_SOFT, true);
            final Component distance = Component.translatable("com.minecolonies.coremod.gui.blocks.distance", BlockPosUtil.getDistance2D(building().getPosition(), view.getPosition()));
            g.drawString(font, Gui.fit(font, distance, boxX - 6 - textX), textX, rowY + 13, Gui.LIST_SOFT, true);
            if (hovered)
            {
                tooltip.setAround(List.of(title, Component.translatable(on ? "mcvanillatweaks.hut.enchanter.on" : "mcvanillatweaks.hut.enchanter.off")
                                                   .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                  Component.translatable("mcvanillatweaks.hut.enchanter.click").withStyle(ChatFormatting.YELLOW)), mouseX, rowY, rowY + ROW);
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
        Gui.clickSound();
        toggle(huts.get(index));
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
