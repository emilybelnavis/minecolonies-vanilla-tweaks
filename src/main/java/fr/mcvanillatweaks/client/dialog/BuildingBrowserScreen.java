package fr.mcvanillatweaks.client.dialog;

import com.ldtteam.structurize.storage.StructurePackMeta;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import com.minecolonies.core.client.gui.WindowBuildingBrowser;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Replaces MineColonies' building browser ({@code WindowBuildingBrowser}: every blueprint, in every style, that has a
 * given hut or block). The replaced window searches in the background as usual (its {@code onOpened} starts it, its
 * {@code onUpdate} collects it, its {@code onClosed} cancels it); this screen shows the progress, then the list: style,
 * path, size, levels, green for blueprints that only contain the block, purple for the ones hidden in survival (shown
 * to creative players only, like MineColonies).
 */
public final class BuildingBrowserScreen extends Screen
{
    private static final int WIDTH = 340;
    private static final int HEIGHT = 230;
    private static final int ROW = 22;

    /** One blueprint, read from MineColonies' private record. */
    private record Entry(String pack, String path, Set<Integer> levels, BlockPos size, boolean parent, boolean invisible)
    {
    }

    private final AbstractWindowSkeleton window;
    private final Block block;
    private final ScrollArea list = new ScrollArea();
    private final Tooltip tooltip = new Tooltip();
    private List<Entry> entries;
    private int left;
    private int top;

    public BuildingBrowserScreen(final AbstractWindowSkeleton window) throws ReflectiveOperationException
    {
        super(Component.empty());
        this.window = window;
        this.block = (Block) DialogScreens.field(window, "block");
        window.onOpened();
    }

    @Override
    public void tick()
    {
        if (entries != null)
        {
            return;
        }
        try
        {
            window.onUpdate();
            if (DialogScreens.field(window, "futureBuildings") == null && DialogScreens.field(window, "buildings") instanceof List<?> list)
            {
                final boolean creative = minecraft != null && minecraft.player != null && minecraft.player.isCreative();
                final List<Entry> read = new ArrayList<>();
                for (final Object info : list)
                {
                    final Entry entry = entry(info);
                    if (creative || !entry.invisible())
                    {
                        read.add(entry);
                    }
                }
                entries = read;
            }
        }
        catch (final ReflectiveOperationException | LinkageError | RuntimeException e)
        {
            DialogScreens.report(e);
            onClose();
        }
    }

    @SuppressWarnings("unchecked")
    private static Entry entry(final Object info) throws ReflectiveOperationException
    {
        final Object[] values = new Object[6];
        final RecordComponent[] components = info.getClass().getRecordComponents();
        for (int i = 0; i < components.length && i < values.length; i++)
        {
            final var accessor = components[i].getAccessor();
            accessor.setAccessible(true);
            values[i] = accessor.invoke(info);
        }
        return new Entry(((StructurePackMeta) values[0]).getName(), (String) values[1], (Set<Integer>) values[2], (BlockPos) values[3], (Boolean) values[4],
          (Boolean) values[5]);
    }

    private float progress()
    {
        try
        {
            final int done = ((AtomicInteger) DialogScreens.field(window, "currentProgress")).get();
            final Field total = WindowBuildingBrowser.class.getDeclaredField("totalProgress");
            total.setAccessible(true);
            final int all = total.getInt(window);
            return all <= 0 ? 0 : Math.min(1F, (float) done / all);
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            return 0;
        }
    }

    @Override
    protected void init()
    {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + 8, top + HEIGHT - 26, WIDTH - 16, 20).build());
    }

    @Override
    public void removed()
    {
        try
        {
            window.onClosed();
        }
        catch (final LinkageError | RuntimeException ignored)
        {
            // The search goes on in the background until it ends.
        }
    }

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        renderTransparentBackground(g);
        Gui.panel(g, left, top, WIDTH, HEIGHT);
    }

    private static String levels(final Set<Integer> levels)
    {
        final List<Integer> list = new ArrayList<>(levels);
        if (list.size() == 1)
        {
            return String.valueOf(list.get(0));
        }
        final int min = list.get(0);
        final int max = list.get(list.size() - 1);
        if (list.size() == max - min + 1)
        {
            return min + "-" + max;
        }
        return String.join(",", list.stream().map(String::valueOf).toList());
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        tooltip.clear();
        g.renderItem(new ItemStack(block), left + 8, top + 7);
        final Component name = block instanceof AbstractBlockHut<?> hut ? hut.getBlueprintDisplayName() : block.getName();
        g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.browser.title", name), WIDTH - 36), left + 28, top + 11, Gui.LABEL, false);
        final int listTop = top + 30;
        if (entries == null)
        {
            g.drawString(font, Component.translatable("com.minecolonies.command.gui.browsebuilding.loading"), left + 9, listTop + 4, Gui.LABEL, false);
            Gui.bar(g, left + 8, listTop + 16, WIDTH - 16, progress());
            return;
        }
        g.drawString(font, Component.translatable("mcvanillatweaks.browser.count", entries.size()), left + WIDTH - 9
          - font.width(Component.translatable("mcvanillatweaks.browser.count", entries.size())), top + 11, Gui.LABEL_SOFT, false);
        list.set(left + 8, listTop, WIDTH - 16, top + HEIGHT - 32 - listTop, entries.size() * ROW, ROW);
        list.begin(g);
        if (entries.isEmpty())
        {
            g.drawString(font, Component.translatable("mcvanillatweaks.browser.none"), list.listX() + 4, list.visibleTop() + 6, Gui.LIST_SOFT, true);
        }
        for (int i = 0; i < entries.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final Entry entry = entries.get(i);
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            final int colour = entry.invisible() ? 0xFFFF55FF : entry.parent() ? Gui.GREEN : Gui.WHITE;
            final String size = entry.size().getX() + "×" + entry.size().getY() + "×" + entry.size().getZ();
            final int sizeX = list.listX() + list.listWidth() - 4 - font.width(size);
            final String[] parts = entry.path().split("/");
            g.drawString(font, Gui.fit(font, Component.literal(parts[parts.length - 1]), sizeX - 6 - (list.listX() + 4)), list.listX() + 4, rowY + 2, colour,
              true);
            g.drawString(font, size, sizeX, rowY + 2, Gui.LIST_SOFT, true);
            final Component second = entry.levels().isEmpty()
                                       ? Component.literal(entry.pack())
                                       : Component.literal(entry.pack() + " · ").append(Component.translatable("mcvanillatweaks.browser.levels", levels(entry.levels())));
            g.drawString(font, Gui.fit(font, second, list.listWidth() - 8), list.listX() + 4, rowY + 12, Gui.LIST_SOFT, true);
            if (hovered)
            {
                final List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(entry.path()));
                lines.add(Component.literal(entry.pack()).withStyle(ChatFormatting.GRAY));
                if (entry.parent())
                {
                    lines.add(Component.translatable("mcvanillatweaks.browser.contains").withStyle(ChatFormatting.GREEN));
                }
                if (entry.invisible())
                {
                    lines.add(Component.translatable("mcvanillatweaks.buildtool.creative").withStyle(ChatFormatting.LIGHT_PURPLE));
                }
                tooltip.set(lines);
            }
        }
        list.end(g);
        tooltip.render(g, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        return list.mouseClicked(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY)
    {
        return list.mouseDragged(mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button)
    {
        list.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        return list.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
