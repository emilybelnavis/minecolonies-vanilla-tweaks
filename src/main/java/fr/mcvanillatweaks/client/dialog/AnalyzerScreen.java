package fr.mcvanillatweaks.client.dialog;

import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.views.Box;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import com.minecolonies.core.client.gui.WindowSchematicAnalyzer;
import com.minecolonies.core.util.SchemAnalyzerUtil.SchematicAnalyzationResult;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Replaces MineColonies' schematic analyzer ({@code WindowSchematicAnalyzer}): two blueprints side by side, each with its
 * complexity score, block kinds, size, huts it contains and the blocks it needs (most costly first). The selections are
 * the window's statics; the arrows and "select" press the window's own buttons of that side (it finds the side from the
 * button's parent box), "select" opening the build tool as a chooser, which comes back here.
 */
public final class AnalyzerScreen extends Screen
{
    private static final int WIDTH = 400;
    private static final int HEIGHT = 230;
    private static final int ROW = 18;

    private final AbstractWindowSkeleton window;
    private final ScrollArea[] lists = {new ScrollArea(), new ScrollArea()};
    private final Tooltip tooltip = new Tooltip();
    private int left;
    private int top;

    public AnalyzerScreen(final AbstractWindowSkeleton window)
    {
        super(Component.translatable("item.minecolonies.scan_analyzer"));
        this.window = window;
    }

    @Nullable
    private static SchematicAnalyzationResult selected(final boolean right)
    {
        try
        {
            final Field field = WindowSchematicAnalyzer.class.getDeclaredField(right ? "selectedRight" : "selectedLeft");
            field.setAccessible(true);
            return (SchematicAnalyzationResult) field.get(null);
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            return null;
        }
    }

    private void press(final boolean right, final String id)
    {
        final Box box = window.findPaneOfTypeByID(right ? "right" : "left", Box.class);
        final Button button = box == null ? null : box.findPaneOfTypeByID(id, Button.class);
        if (button != null)
        {
            window.onButtonClicked(button);
            rebuildWidgets();
        }
    }

    private int columnWidth()
    {
        return (WIDTH - 16 - 8) / 2;
    }

    private int columnX(final boolean right)
    {
        return left + 8 + (right ? columnWidth() + 8 : 0);
    }

    @Override
    protected void init()
    {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        for (final boolean right : new boolean[] {false, true})
        {
            final int x = columnX(right);
            final int w = columnWidth();
            addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("◀"), b -> press(right, "selectleft"))
                                  .bounds(x, top + 24, 20, 20).build());
            addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("▶"), b -> press(right, "selectright"))
                                  .bounds(x + w - 20, top + 24, 20, 20).build());
            addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("com.minecolonies.coremod.gui.analyzer.select"),
              b -> press(right, "selectschematic")).bounds(x, top + 48, w, 20).build());
        }
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("gui.done"), b -> {
            final Button cancel = window.findPaneOfTypeByID("cancel", Button.class);
            if (cancel != null)
            {
                window.onButtonClicked(cancel);
            }
            else
            {
                onClose();
            }
        }).bounds(left + 8, top + HEIGHT - 26, WIDTH - 16, 20).build());
    }

    @Override
    public void removed()
    {
        // The analyzer's box highlight goes with the window.
        try
        {
            window.onClosed();
        }
        catch (final LinkageError | RuntimeException ignored)
        {
            // Only the highlight.
        }
    }

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        renderTransparentBackground(g);
        Gui.panel(g, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        tooltip.clear();
        g.drawString(font, getTitle(), left + 8, top + 9, Gui.LABEL, false);
        for (final boolean right : new boolean[] {false, true})
        {
            renderSide(g, mouseX, mouseY, right);
        }
        tooltip.render(g, font, mouseX, mouseY);
    }

    private static Component name(final SchematicAnalyzationResult result)
    {
        String name = result.blueprint.getName();
        if (result.blueprint.getFileName() != null && result.blueprint.getFilePath() != null)
        {
            final String[] split = result.blueprint.getFileName().split("/");
            name = result.blueprint.getFilePath().toString().replace("blueprints/minecolonies/", "") + "/" + split[split.length - 1];
        }
        return Component.literal(name.replace(".blueprint", "").replace('\\', '/'));
    }

    private void renderSide(final GuiGraphics g, final int mouseX, final int mouseY, final boolean right)
    {
        final int x = columnX(right);
        final int w = columnWidth();
        final SchematicAnalyzationResult result = selected(right);
        final Component name = result == null ? Component.translatable("mcvanillatweaks.analyzer.none") : name(result);
        g.drawCenteredString(font, Gui.fit(font, name, w - 48), x + w / 2, top + 30, Gui.LABEL);
        if (Gui.inside(mouseX, mouseY, x + 22, top + 24, w - 44, 20) && result != null)
        {
            tooltip.set(name);
        }
        if (result == null)
        {
            return;
        }
        int ty = top + 74;
        g.drawString(font, Component.translatable("com.minecolonies.coremod.gui.analyzer.complexity",
          Component.literal(String.valueOf(result.costScore)).withStyle(ChatFormatting.DARK_RED)), x + 1, ty, Gui.LABEL, false);
        ty += 11;
        g.drawString(font, Component.translatable("com.minecolonies.coremod.gui.analyzer.blockcounts",
          Component.literal(String.valueOf(result.differentBlocks.size())).withStyle(ChatFormatting.DARK_BLUE)), x + 1, ty, Gui.LABEL, false);
        ty += 11;
        g.drawString(font, Component.translatable("com.minecolonies.coremod.gui.analyzer.size",
          Component.literal(result.blueprint.getSizeX() + " × " + result.blueprint.getSizeY() + " × " + result.blueprint.getSizeZ())), x + 1, ty, Gui.LABEL,
          false);
        ty += 11;
        g.drawString(font, Component.translatable("com.minecolonies.coremod.gui.analyzer.buildings",
          Component.literal(String.valueOf(result.containedBuildings))), x + 1, ty, Gui.LABEL, false);
        ty += 14;

        final List<ItemStorage> blocks = new ArrayList<>(result.differentBlocks);
        blocks.sort(Comparator.<ItemStorage>comparingInt(storage -> storage.getAmount() * storage.getItemStack().getCount()).reversed());
        final ScrollArea list = lists[right ? 1 : 0];
        list.set(x, ty, w, top + HEIGHT - 32 - ty, blocks.size() * ROW, ROW);
        list.begin(g);
        for (int i = 0; i < blocks.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final ItemStorage storage = blocks.get(i);
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            final ItemStack icon = storage.getItemStack().copyWithCount(1);
            g.renderItem(icon, list.listX() + 2, rowY + 1);
            final String count = String.valueOf(storage.getAmount());
            final int countX = list.listX() + list.listWidth() - 4 - font.width(count);
            g.drawString(font, Gui.fit(font, icon.getHoverName(), countX - 6 - (list.listX() + 22)), list.listX() + 22, rowY + 5, Gui.WHITE, true);
            g.drawString(font, count, countX, rowY + 5, Gui.YELLOW, true);
            if (hovered)
            {
                tooltip.set(icon, List.of(Component.translatable("com.minecolonies.coremod.gui.analyzer.score", storage.getItemStack().getCount(),
                  storage.getItemStack().getCount() * storage.getAmount()).withStyle(ChatFormatting.GRAY)));
            }
        }
        list.end(g);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        return lists[0].mouseClicked(mouseX, mouseY) || lists[1].mouseClicked(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY)
    {
        return lists[0].mouseDragged(mouseY) || lists[1].mouseDragged(mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button)
    {
        lists[0].mouseReleased();
        lists[1].mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        return lists[0].mouseScrolled(mouseX, mouseY, scrollY) || lists[1].mouseScrolled(mouseX, mouseY, scrollY)
                 || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
