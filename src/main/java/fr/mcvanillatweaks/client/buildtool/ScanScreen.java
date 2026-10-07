package fr.mcvanillatweaks.client.buildtool;

import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.CheckBox;
import com.ldtteam.blockui.controls.TextField;
import com.ldtteam.structurize.api.ItemStorage;
import com.ldtteam.structurize.client.gui.WindowReplaceBlock;
import com.ldtteam.structurize.client.gui.WindowScan;
import com.ldtteam.structurize.client.gui.util.ItemPositionsStorage;
import com.ldtteam.structurize.network.messages.RemoveBlockMessage;
import com.ldtteam.structurize.network.messages.RemoveEntityMessage;
import com.ldtteam.structurize.storage.rendering.RenderingCache;
import com.ldtteam.structurize.storage.rendering.types.BoxPreviewData;
import com.ldtteam.structurize.util.ScanToolData;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Replaces Structurize's scan tool window ({@code WindowScan}). The replaced window keeps its state and does the work:
 * our fields write into its text fields (name, both corners, the placeholder filler's settings), its private methods
 * are called for the resources ({@code updateResources}), slots (its digit keys) and its buttons are pressed for scan,
 * cancel, undo / redo and fill; its lists ({@code allResources}, {@code entities}) are read to draw ours. Row actions
 * (show in the world, replace through Structurize's replace window, remove, remove entities) send Structurize's own
 * messages, the removals only in creative like Structurize.
 * <pre>
 * Slot: [0][1]...[9]     Name: [house1        ]
 * From: [x][y][z]  To: [x][y][z]
 * [Show resources]   or   [filter] [x] visible [x] hidden
 * | [stone] Stone   124  [see][replace][x] |   | [cow] Cow 2 [x] |
 * [Undo/redo] [Fill placeholders]   [Cancel] [Scan]
 * </pre>
 */
public final class ScanScreen extends Screen
{
    private static final int WIDTH = 380;
    private static final int HEIGHT = 240;
    private static final int ROW = 18;
    private static final String[] CORNERS = {"pos1x", "pos1y", "pos1z", "pos2x", "pos2y", "pos2z"};
    private static final String[] FILL = {"ystretch", "radius", "heightoffset", "blockdist"};

    private final WindowScan window;
    private final ScrollArea resources = new ScrollArea();
    private final ScrollArea entities = new ScrollArea();
    private final Tooltip tooltip = new Tooltip();
    private final boolean creative;
    private boolean filling;
    private List<ItemStorage> sorted = List.of();
    private int left;
    private int top;

    public ScanScreen(final WindowScan window)
    {
        super(Component.translatable("item.structurize.scan_tool"));
        this.window = window;
        this.creative = net.minecraft.client.Minecraft.getInstance().player != null && net.minecraft.client.Minecraft.getInstance().player.isCreative();
        // The replaced window loads its slot and its filter handler when it opens.
        window.onOpened();
        readLists();
    }

    // ------------------------------------------------------------------ the replaced window

    private Object field(final String name) throws ReflectiveOperationException
    {
        final Field field = WindowScan.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(window);
    }

    private void call(final String name)
    {
        try
        {
            final Method method = WindowScan.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(window);
        }
        catch (final ReflectiveOperationException | LinkageError | RuntimeException e)
        {
            fail(e);
        }
    }

    private void press(final String id)
    {
        try
        {
            Button button = window.findPaneOfTypeByID(id, Button.class);
            if (button == null)
            {
                button = new ButtonImage();
                button.setID(id);
            }
            window.onButtonClicked(button);
        }
        catch (final LinkageError | RuntimeException e)
        {
            fail(e);
        }
    }

    private void fail(final Throwable e)
    {
        MineColoniesVanillaTweaks.LOGGER.warn("Scan screen failed", e);
        BuildToolScreens.report(e);
        onClose();
    }

    private String text(final String id)
    {
        final TextField field = window.findPaneOfTypeByID(id, TextField.class);
        return field == null ? "" : field.getText();
    }

    private void setText(final String id, final String value)
    {
        final TextField field = window.findPaneOfTypeByID(id, TextField.class);
        if (field != null)
        {
            field.setText(value);
        }
    }

    private boolean resourcesShown()
    {
        final var pane = window.findPaneByID("showRes");
        return pane != null && !pane.isVisible();
    }

    @SuppressWarnings("unchecked")
    private Map<ItemStorage, ItemPositionsStorage> allResources()
    {
        try
        {
            return (Map<ItemStorage, ItemPositionsStorage>) field("allResources");
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Object2IntMap<EntityType<?>> entityCounts()
    {
        try
        {
            return (Object2IntMap<EntityType<?>>) field("entities");
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            return it.unimi.dsi.fastutil.objects.Object2IntMaps.emptyMap();
        }
    }

    private void readLists()
    {
        final List<ItemStorage> list = new ArrayList<>(allResources().keySet());
        list.sort(Comparator.comparing(storage -> storage.getItemStack().getHoverName().getString()));
        sorted = list;
    }

    private int slot()
    {
        try
        {
            return ((ScanToolData) field("data")).currentSlotId();
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            return 0;
        }
    }

    private void refresh()
    {
        call("updateResources");
        readLists();
    }

    // ------------------------------------------------------------------ widgets

    @Override
    protected void init()
    {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        if (filling)
        {
            initFill();
            return;
        }
        // Slots 0-9: the window's digit keys.
        for (int i = 0; i < 10; i++)
        {
            final int slot = i;
            final net.minecraft.client.gui.components.Button button = addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
              Component.literal(String.valueOf(i)), b -> {
                  window.onUnhandledKeyTyped('0' + slot, 0);
                  readLists();
                  rebuildWidgets();
              }).bounds(left + 40 + i * 17, top + 24, 16, 16).build());
            button.active = slot != slot();
        }
        final EditBox name = addRenderableWidget(new EditBox(font, left + WIDTH - 8 - 140, top + 23, 140, 18, Component.empty()));
        name.setMaxLength(100);
        name.setValue(text("name"));
        name.setResponder(value -> setText("name", value));

        for (int i = 0; i < CORNERS.length; i++)
        {
            final String id = CORNERS[i];
            final int bx = left + 40 + (i % 3) * 50 + (i >= 3 ? 190 : 0);
            final EditBox box = addRenderableWidget(new EditBox(font, bx, top + 46, 46, 16, Component.empty()));
            box.setFilter(value -> value.matches("-?\\d{0,8}"));
            box.setValue(text(id));
            box.setResponder(value -> setText(id, value));
            box.active = creative;
            box.setEditable(creative);
        }

        final int listTop = top + 92;
        if (!resourcesShown())
        {
            addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("com.ldtteam.structurize.gui.scantool.showres"), b -> {
                press("showRes");
                readLists();
                rebuildWidgets();
            }).bounds(left + 8, top + 68, 150, 18).build());
        }
        else
        {
            final EditBox filter = addRenderableWidget(new EditBox(font, left + 8, top + 68, 120, 18, Component.empty()));
            filter.setHint(Component.translatable("mcvanillatweaks.hut.search").withStyle(ChatFormatting.DARK_GRAY));
            filter.setValue(text("filter"));
            filter.setResponder(value -> {
                setText("filter", value);
                try
                {
                    final Field field = WindowScan.class.getDeclaredField("filter");
                    field.setAccessible(true);
                    field.set(window, value);
                }
                catch (final ReflectiveOperationException e)
                {
                    fail(e);
                }
                refresh();
            });
            checkbox(left + 134, "showvisisble", "mcvanillatweaks.scan.visible", "com.ldtteam.structurize.gui.scantool.show_visible");
            checkbox(left + 210, "showhidden", "mcvanillatweaks.scan.hidden", "com.ldtteam.structurize.gui.scantool.show_hidden");
            if (creative)
            {
                addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("com.ldtteam.structurize.gui.scantool.remove_filtered"),
                  b -> {
                      new RemoveBlockMessage(allResources().values().stream().toList()).sendToServer();
                      allResources().clear();
                      readLists();
                  }).bounds(left + WIDTH - 8 - 80, top + 68, 80, 18).build());
            }
        }

        // Bottom buttons.
        final int by = top + HEIGHT - 26;
        int bx = left + 8;
        if (creative)
        {
            addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("structurize.gui.undoredo.undoop")
                                                                                     .append(" / ").append(Component.translatable("structurize.gui.undoredo.redoop")),
              b -> press("undoredo")).bounds(bx, by, 100, 20).build());
            bx += 104;
            addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("mcvanillatweaks.scan.fill"), b -> {
                filling = true;
                rebuildWidgets();
            }).bounds(bx, by, 100, 20).build()).setTooltip(net.minecraft.client.gui.components.Tooltip.create(
              Component.translatable("com.ldtteam.structurize.gui.scantool.fillplacerholder.tooltip")));
        }
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("gui.cancel"), b -> press("cancel"))
                              .bounds(left + WIDTH - 8 - 2 * 70 - 4, by, 70, 20).build());
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("mcvanillatweaks.scan.scan"), b -> press("confirm"))
                              .bounds(left + WIDTH - 8 - 70, by, 70, 20).build());
    }

    private void checkbox(final int x, final String id, final String label, final String tip)
    {
        final CheckBox source = window.findPaneOfTypeByID(id, CheckBox.class);
        final Checkbox box = addRenderableWidget(Checkbox.builder(Component.empty(), font).pos(x, top + 69).selected(source == null || source.isChecked())
                                                   .onValueChange((b, value) -> {
                                                       if (source != null)
                                                       {
                                                           source.setChecked(value);
                                                       }
                                                       refresh();
                                                   }).build());
        box.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(tip)));
    }

    private void initFill()
    {
        for (int i = 0; i < FILL.length; i++)
        {
            final String id = FILL[i];
            final EditBox box = addRenderableWidget(new EditBox(font, left + WIDTH - 8 - 80, top + 40 + i * 24, 80, 18, Component.empty()));
            box.setFilter(value -> value.matches("-?\\d*\\.?\\d*"));
            box.setValue(text(id));
            box.setResponder(value -> setText(id, value));
            box.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
              Component.translatable("com.ldtteam.structurize.gui.scantool.fillplacerholder." + id + ".tooltip")));
        }
        final int bw = (WIDTH - 20) / 2;
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("gui.back"), b -> {
            filling = false;
            rebuildWidgets();
        }).bounds(left + 8, top + HEIGHT - 26, bw, 20).build());
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
          Component.translatable("com.ldtteam.structurize.gui.scantool.fillplacerholder.apply"), b -> press("fill"))
                              .bounds(left + WIDTH - 8 - bw, top + HEIGHT - 26, bw, 20).build());
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void tick()
    {
        try
        {
            window.onUpdate();
        }
        catch (final LinkageError | RuntimeException e)
        {
            fail(e);
        }
    }

    @Override
    public void removed()
    {
        // As BlockUI does when its screen goes: the window saves the corners.
        try
        {
            window.onClosed();
        }
        catch (final LinkageError | RuntimeException e)
        {
            MineColoniesVanillaTweaks.LOGGER.warn("Scan window close failed", e);
        }
    }

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        Gui.panel(g, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        tooltip.clear();
        g.drawString(font, getTitle(), left + 8, top + 9, Gui.LABEL, false);
        if (filling)
        {
            g.drawString(font, Component.translatable("mcvanillatweaks.scan.fill"), left + 8, top + 24, Gui.LABEL, false);
            for (int i = 0; i < FILL.length; i++)
            {
                g.drawString(font, Component.translatable("com.ldtteam.structurize.gui.scantool.fillplacerholder." + FILL[i]), left + 8, top + 45 + i * 24,
                  Gui.LABEL, false);
            }
            int ty = top + 40 + FILL.length * 24 + 6;
            for (final var line : font.split(Component.translatable("com.ldtteam.structurize.gui.scantool.fillplacerholder.tooltip"), WIDTH - 16))
            {
                g.drawString(font, line, left + 8, ty, Gui.LABEL_SOFT, false);
                ty += 10;
            }
            tooltip.render(g, font, mouseX, mouseY);
            return;
        }
        g.drawString(font, Component.translatable("com.ldtteam.structurize.gui.scantool.slot"), left + 8, top + 28, Gui.LABEL, false);
        final Component nameLabel = Component.translatable("com.ldtteam.structurize.gui.scantool.name");
        g.drawString(font, nameLabel, left + WIDTH - 8 - 140 - 4 - font.width(nameLabel), top + 28, Gui.LABEL, false);
        g.drawString(font, Component.translatable("com.ldtteam.structurize.gui.scantool.from"), left + 8, top + 50, Gui.LABEL, false);
        g.drawString(font, Component.translatable("com.ldtteam.structurize.gui.scantool.to"), left + 198, top + 50, Gui.LABEL, false);
        if (resourcesShown())
        {
            g.drawString(font, Component.translatable("mcvanillatweaks.scan.visible"), left + 154, top + 73, Gui.LABEL, false);
            g.drawString(font, Component.translatable("mcvanillatweaks.scan.hidden"), left + 230, top + 73, Gui.LABEL, false);
        }

        final int listTop = top + 92;
        final int listHeight = top + HEIGHT - 32 - listTop;
        final int resourcesWidth = WIDTH - 16 - 130;
        resources.set(left + 8, listTop, resourcesWidth, listHeight, sorted.size() * ROW, ROW);
        resources.begin(g);
        if (!resourcesShown())
        {
            g.drawString(font, Component.translatable("mcvanillatweaks.scan.hint"), resources.listX() + 4, resources.visibleTop() + 6, Gui.LIST_SOFT, true);
        }
        else if (sorted.isEmpty())
        {
            g.drawString(font, Component.translatable("mcvanillatweaks.hut.search_none"), resources.listX() + 4, resources.visibleTop() + 6, Gui.LIST_SOFT, true);
        }
        final int buttons = creative ? 3 : 1;
        for (int i = 0; i < sorted.size(); i++)
        {
            final int rowY = resources.listY() + i * ROW;
            if (rowY + ROW < resources.visibleTop() || rowY > resources.visibleBottom())
            {
                continue;
            }
            final ItemStorage storage = sorted.get(i);
            final boolean hovered = resources.hover(g, mouseX, mouseY, rowY, ROW);
            final ItemStack icon = storage.getItemStack().copyWithCount(1);
            g.renderItem(icon.isEmpty() ? new ItemStack(Items.STRUCTURE_VOID) : icon, resources.listX() + 2, rowY + 1);
            final int bx = resources.listX() + resources.listWidth() - 4 - buttons * 16;
            final String count = String.valueOf(storage.getAmount());
            final int countX = bx - 6 - font.width(count);
            g.drawString(font, Gui.fit(font, storage.getItemStack().getHoverName(), countX - 4 - (resources.listX() + 22)), resources.listX() + 22, rowY + 5,
              Gui.WHITE, true);
            g.drawString(font, count, countX, rowY + 5, Gui.LIST_SOFT, true);
            final String[] labels = creative ? new String[] {"◎", "⇄", "✖"} : new String[] {"◎"};
            final String[] tips = {"com.ldtteam.structurize.gui.scantool.item.tooltipclick", "com.ldtteam.structurize.gui.scantool.replace",
              "com.ldtteam.structurize.gui.scantool.remove"};
            for (int b = 0; b < labels.length; b++)
            {
                final int x = bx + b * 16;
                final boolean over = Gui.inside(mouseX, mouseY, x, rowY + 2, 14, 14);
                g.drawString(font, labels[b], x + 3, rowY + 5, over ? Gui.YELLOW : Gui.LIST_SOFT, true);
                if (over)
                {
                    tooltip.set(Component.translatable(tips[b]));
                }
            }
            if (hovered && tooltip.isEmpty())
            {
                tooltip.set(icon);
            }
        }
        resources.end(g);

        final List<EntityType<?>> types = new ArrayList<>(entityCounts().keySet());
        entities.set(left + 8 + resourcesWidth + 6, listTop, 124, listHeight, types.size() * ROW, ROW);
        entities.begin(g);
        for (int i = 0; i < types.size(); i++)
        {
            final int rowY = entities.listY() + i * ROW;
            if (rowY + ROW < entities.visibleTop() || rowY > entities.visibleBottom())
            {
                continue;
            }
            final EntityType<?> type = types.get(i);
            final boolean hovered = entities.hover(g, mouseX, mouseY, rowY, ROW);
            g.renderItem(entityIcon(type), entities.listX() + 2, rowY + 1);
            final String count = String.valueOf(entityCounts().getInt(type));
            final int end = entities.listX() + entities.listWidth() - 4 - (creative ? 14 : 0);
            g.drawString(font, Gui.fit(font, type.getDescription(), end - 6 - font.width(count) - (entities.listX() + 22)), entities.listX() + 22, rowY + 5,
              Gui.WHITE, true);
            g.drawString(font, count, end - 4 - font.width(count), rowY + 5, Gui.LIST_SOFT, true);
            if (creative)
            {
                final boolean over = Gui.inside(mouseX, mouseY, end, rowY + 2, 12, 14);
                g.drawString(font, "✖", end + 2, rowY + 5, over ? Gui.RED : Gui.LIST_SOFT, true);
                if (over)
                {
                    tooltip.set(Component.translatable("com.ldtteam.structurize.gui.scantool.remove"));
                }
            }
            if (hovered && tooltip.isEmpty())
            {
                tooltip.set(type.getDescription());
            }
        }
        entities.end(g);
        tooltip.render(g, font, mouseX, mouseY);
    }

    private static ItemStack entityIcon(final EntityType<?> type)
    {
        if (type == EntityType.ITEM_FRAME)
        {
            return new ItemStack(Items.ITEM_FRAME);
        }
        if (type == EntityType.GLOW_ITEM_FRAME)
        {
            return new ItemStack(Items.GLOW_ITEM_FRAME);
        }
        if (type == EntityType.MINECART)
        {
            return new ItemStack(Items.MINECART);
        }
        if (type == EntityType.ARMOR_STAND)
        {
            return new ItemStack(Items.ARMOR_STAND);
        }
        if (type == EntityType.PAINTING)
        {
            return new ItemStack(Items.PAINTING);
        }
        final SpawnEggItem egg = SpawnEggItem.byId(type);
        return new ItemStack(egg != null ? egg : Items.NAME_TAG);
    }

    // ------------------------------------------------------------------ input

    @Nullable
    private BlockPos corner(final int first)
    {
        try
        {
            return new BlockPos(Integer.parseInt(text(CORNERS[first])), Integer.parseInt(text(CORNERS[first + 1])), Integer.parseInt(text(CORNERS[first + 2])));
        }
        catch (final NumberFormatException e)
        {
            return null;
        }
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (resources.mouseClicked(mouseX, mouseY) || entities.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button == 0 && !filling && resources.overRows(mouseX, mouseY))
        {
            final int index = (int) ((mouseY - resources.listY()) / ROW);
            if (index >= 0 && index < sorted.size())
            {
                final int rowY = resources.listY() + index * ROW;
                final int buttons = creative ? 3 : 1;
                final int bx = resources.listX() + resources.listWidth() - 4 - buttons * 16;
                final ItemPositionsStorage storage = allResources().get(sorted.get(index));
                for (int b = 0; b < buttons && storage != null; b++)
                {
                    if (Gui.inside(mouseX, mouseY, bx + b * 16, rowY + 2, 14, 14))
                    {
                        Gui.clickSound();
                        switch (b)
                        {
                            case 0 ->
                            {
                                for (final BlockPos pos : storage.positions)
                                {
                                    final BoxPreviewData box = new BoxPreviewData(pos, pos, Optional.empty());
                                    box.setExpireTime(30);
                                    RenderingCache.queue("clickedResource" + pos.toShortString(), box);
                                }
                                window.close();
                            }
                            case 1 -> new WindowReplaceBlock(window, storage).open();
                            default ->
                            {
                                new RemoveBlockMessage(storage).sendToServer();
                                window.removeAllNeededResource(storage.itemStorage.getItemStack());
                                readLists();
                            }
                        }
                        return true;
                    }
                }
            }
        }
        if (button == 0 && !filling && creative && entities.overRows(mouseX, mouseY))
        {
            final List<EntityType<?>> types = new ArrayList<>(entityCounts().keySet());
            final int index = (int) ((mouseY - entities.listY()) / ROW);
            final int end = entities.listX() + entities.listWidth() - 4 - 14;
            final BlockPos from = corner(0);
            final BlockPos to = corner(3);
            if (index >= 0 && index < types.size() && from != null && to != null
                  && Gui.inside(mouseX, mouseY, end, entities.listY() + index * ROW + 2, 12, 14))
            {
                Gui.clickSound();
                new RemoveEntityMessage(from, to, EntityType.getKey(types.get(index))).sendToServer();
                entityCounts().removeInt(types.get(index));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY)
    {
        return resources.mouseDragged(mouseY) || entities.mouseDragged(mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button)
    {
        resources.mouseReleased();
        entities.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        return resources.mouseScrolled(mouseX, mouseY, scrollY) || entities.mouseScrolled(mouseX, mouseY, scrollY)
                 || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers)
    {
        if (filling && key == GLFW.GLFW_KEY_ESCAPE)
        {
            filling = false;
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
