package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.IGraveData;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.client.render.worldevent.HighlightManager;
import com.minecolonies.core.client.render.worldevent.highlightmanager.TimedBoxRenderData;
import com.minecolonies.core.colony.buildings.moduleviews.GraveyardManagementModuleView;
import com.minecolonies.core.tileentities.TileEntityGrave;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;

/**
 * Graveyard page (MineColonies' graveyard module): on the left the graves the undertaker has to bring back, with the
 * citizen's name read from the grave like MineColonies (graves that disappeared are dropped every tick, as it does),
 * their distance and direction from the graveyard; a click outlines the grave in the world. On the right the citizens
 * buried here, with how many of the graveyard's plots are filled (MineColonies 1.1.1403 and later).
 * <pre>
 * Graves to collect: 2           Buried here: 3
 * +--------------------------+  +--------------------+
 * | [skull] Alice Martin     |  | [poppy] Bruno Petit|
 * |         24 m, north-east |  | [poppy] ...        |
 * +--------------------------+  +--------------------+
 * </pre>
 */
final class GraveyardTab extends HutTab
{
    private static final int GRAVE_ROW = 26;
    private static final int NAME_ROW = 18;
    private static final String HIGHLIGHT_KEY = "mcvanillatweaks:grave";
    private static final int HIGHLIGHT_COLOUR = 0x80AAAAAA;

    private final GraveyardManagementModuleView module;
    private final ScrollArea graves = new ScrollArea();
    private final ScrollArea buried = new ScrollArea();

    GraveyardTab(final HutScreen screen, final GraveyardManagementModuleView module)
    {
        super(screen);
        this.module = module;
    }

    @Override
    protected void tick()
    {
        // MineColonies' window forgets the graves that are gone (collected, or dug by the player).
        final IColonyView colony = building().getColony();
        if (colony != null && colony.getWorld() != null)
        {
            module.cleanGraves();
        }
    }

    @Nullable
    private IGraveData grave(final BlockPos pos)
    {
        final IColonyView colony = building().getColony();
        if (colony == null || colony.getWorld() == null)
        {
            return null;
        }
        final BlockEntity entity = colony.getWorld().getBlockEntity(pos);
        return entity instanceof TileEntityGrave grave ? grave.getGraveData() : null;
    }

    private Component graveName(final BlockPos pos)
    {
        final IGraveData data = grave(pos);
        return data == null || data.getCitizenName() == null || data.getCitizenName().isEmpty()
                 ? Component.translatable("mcvanillatweaks.hut.graveyard.unknown")
                 : Component.literal(data.getCitizenName());
    }

    private Component where(final BlockPos pos)
    {
        final int distance = (int) Math.sqrt(BlockPosUtil.getDistanceSquared(pos, building().getPosition()));
        return Component.translatable("mcvanillatweaks.hut.graveyard.where", distance, BlockPosUtil.calcDirection(building().getPosition(), pos).getLongText());
    }

    private void highlight(final BlockPos pos)
    {
        HighlightManager.clearHighlightsForKey(HIGHLIGHT_KEY);
        HighlightManager.addHighlight(HIGHLIGHT_KEY, pos.toString(), new TimedBoxRenderData(pos).setDuration(Duration.ofSeconds(60))
                                                                       .addText(graveName(pos).getString()).setColor(HIGHLIGHT_COLOUR));
        screen.onClose();
    }

    // ------------------------------------------------------------------ drawing

    private int splitX()
    {
        return x + width * 3 / 5;
    }

    /** Filled and total grave plots, or null before MineColonies 1.1.1403 (no such count). */
    @Nullable
    private int[] plots()
    {
        try
        {
            return new int[] {module.getFilledGravePlotCount(), module.getTotalGravePlotCount()};
        }
        catch (final LinkageError e)
        {
            return null;
        }
    }

    @Override
    protected void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final List<BlockPos> positions = module.getGraves();
        final List<String> names = module.getRestingCitizen();
        final int leftWidth = splitX() - x - 4;
        final int rightX = splitX() + 4;
        final int rightWidth = x + width - rightX;
        heading(g, Component.translatable("mcvanillatweaks.hut.graveyard.graves", positions.size()), x, y + 1, leftWidth);
        final int[] plots = plots();
        final Component buriedHeading = plots == null || plots[1] <= 0 ? Component.translatable("mcvanillatweaks.hut.graveyard.buried", names.size())
                                          : Component.translatable("mcvanillatweaks.hut.graveyard.buried_plots", plots[0], plots[1]);
        heading(g, buriedHeading, rightX, y + 1, rightWidth);
        if (plots != null && plots[1] > 0 && Gui.inside(mouseX, mouseY, rightX, y, rightWidth, 11))
        {
            tooltip.set(List.of(buriedHeading, Component.translatable("com.minecolonies.coremod.gui.workerhuts.graveyard.plotsfilled", plots[0], plots[1])
                                                 .withStyle(ChatFormatting.GRAY)));
        }

        final int top = y + HEADING + 2;
        graves.set(x, top, leftWidth, y + height - top, positions.size() * GRAVE_ROW, GRAVE_ROW);
        graves.begin(g);
        if (positions.isEmpty())
        {
            empty(g, graves, "mcvanillatweaks.hut.graveyard.no_graves");
        }
        final ItemStack skull = new ItemStack(Items.SKELETON_SKULL);
        for (int i = 0; i < positions.size(); i++)
        {
            final int rowY = graves.listY() + i * GRAVE_ROW;
            if (rowY + GRAVE_ROW < graves.visibleTop() || rowY > graves.visibleBottom())
            {
                continue;
            }
            final BlockPos pos = positions.get(i);
            final boolean hovered = graves.hover(g, mouseX, mouseY, rowY, GRAVE_ROW);
            g.renderItem(skull, graves.listX() + 3, rowY + 5);
            final int textX = graves.listX() + 24;
            final int textWidth = graves.listX() + graves.listWidth() - 4 - textX;
            g.drawString(font, Gui.fit(font, graveName(pos), textWidth), textX, rowY + 3, Gui.WHITE, true);
            g.drawString(font, Gui.fit(font, where(pos), textWidth), textX, rowY + 14, Gui.LIST_SOFT, true);
            if (hovered)
            {
                final IGraveData data = grave(pos);
                final List<Component> lines = new java.util.ArrayList<>();
                lines.add(graveName(pos));
                if (data != null && data.getCitizenJobName() != null && !data.getCitizenJobName().isEmpty())
                {
                    lines.add(Component.translatable(data.getCitizenJobName()).withStyle(ChatFormatting.GRAY));
                }
                lines.add(where(pos).copy().withStyle(ChatFormatting.GRAY));
                lines.add(Component.literal(pos.toShortString()).withStyle(ChatFormatting.DARK_GRAY));
                lines.add(Component.translatable("mcvanillatweaks.hut.graveyard.locate").withStyle(ChatFormatting.YELLOW));
                tooltip.setAround(lines, mouseX, rowY, rowY + GRAVE_ROW);
            }
        }
        graves.end(g);

        buried.set(rightX, top, rightWidth, y + height - top, names.size() * NAME_ROW, NAME_ROW);
        buried.begin(g);
        if (names.isEmpty())
        {
            empty(g, buried, "mcvanillatweaks.hut.graveyard.nobody");
        }
        final ItemStack flower = new ItemStack(Items.POPPY);
        for (int i = 0; i < names.size(); i++)
        {
            final int rowY = buried.listY() + i * NAME_ROW;
            if (rowY + NAME_ROW < buried.visibleTop() || rowY > buried.visibleBottom())
            {
                continue;
            }
            final boolean hovered = buried.hover(g, mouseX, mouseY, rowY, NAME_ROW);
            g.renderItem(flower, buried.listX() + 2, rowY + 1);
            final Component name = Component.literal(names.get(i));
            final int textWidth = buried.listWidth() - 26;
            g.drawString(font, Gui.fit(font, name, textWidth), buried.listX() + 22, rowY + 5, Gui.WHITE, true);
            if (hovered && Gui.isCut(font, name, textWidth))
            {
                tooltip.set(name);
            }
        }
        buried.end(g);
    }

    private void empty(final GuiGraphics g, final ScrollArea list, final String key)
    {
        int ty = list.visibleTop() + 6;
        for (final var line : font.split(Component.translatable(key), list.listWidth() - 8))
        {
            g.drawString(font, line, list.listX() + 4, ty, Gui.LIST_SOFT, true);
            ty += 10;
        }
    }

    @Override
    protected boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (graves.mouseClicked(mouseX, mouseY) || buried.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button != 0 || !graves.overRows(mouseX, mouseY))
        {
            return false;
        }
        final int index = (int) Math.floor((mouseY - graves.listY()) / GRAVE_ROW);
        if (index < 0 || index >= module.getGraves().size())
        {
            return false;
        }
        Gui.clickSound();
        highlight(module.getGraves().get(index));
        return true;
    }

    @Override
    protected boolean mouseDragged(final double mouseX, final double mouseY)
    {
        return graves.mouseDragged(mouseY) || buried.mouseDragged(mouseY);
    }

    @Override
    protected void mouseReleased()
    {
        graves.mouseReleased();
        buried.mouseReleased();
    }

    @Override
    protected boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        return graves.mouseScrolled(mouseX, mouseY, delta) || buried.mouseScrolled(mouseX, mouseY, delta);
    }
}
