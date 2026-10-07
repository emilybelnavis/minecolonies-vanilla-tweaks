package fr.mcvanillatweaks.client.buildtool;

import com.ldtteam.blockui.controls.Button;
import com.ldtteam.structurize.Structurize;
import com.ldtteam.structurize.api.Shape;
import com.ldtteam.structurize.api.Utils;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.client.BlueprintHandler;
import com.ldtteam.structurize.client.ModKeyMappings;
import com.ldtteam.structurize.client.gui.AbstractBlueprintManipulationWindow;
import com.ldtteam.structurize.client.gui.WindowShapeTool;
import com.ldtteam.structurize.network.messages.BuildToolPlacementMessage;
import com.ldtteam.structurize.storage.ISurvivalBlueprintHandler;
import com.ldtteam.structurize.storage.SurvivalBlueprintHandlers;
import com.ldtteam.structurize.storage.rendering.RenderingCache;
import com.ldtteam.structurize.storage.rendering.types.BlueprintPreviewData;
import com.mojang.blaze3d.platform.InputConstants;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.citizen.Dropdown;
import fr.mcvanillatweaks.client.citizen.DropdownButton;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.IconButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Replaces Structurize's placement windows that aren't the build tool ({@code AbstractBlueprintManipulationWindow}
 * subclasses: MineColonies' supply camp / ship {@code WindowSupplies}, Structurize's {@code WindowShapeTool}). Like our
 * build tool screen, no dark background, so the preview stays visible, and the same manipulator on the right. Moves,
 * rotation, mirror and cancel press the replaced window's buttons; "place" reproduces its {@code confirmClicked} (the
 * placement choice when there are several) and calls its {@code handlePlacement}. The supplies add "switch style", the
 * shape tool a left panel: shape, sizes, hollow/solid, the two blocks (Structurize's picker, swapped too), undo/redo.
 */
public final class ManipulatorScreen extends Screen
{
    private static final int PANEL_WIDTH = 86;
    private static final int SHAPE_WIDTH = 150;
    private static final int TIP_TICKS = 200;

    private enum Modal
    {
        NONE,
        PLACEMENT,
        SETTINGS
    }

    private record Placement(Component name, BuildToolPlacementMessage.HandlerType type, String id)
    {
    }

    private final AbstractBlueprintManipulationWindow window;
    private final String previewId;
    private final boolean shapes;
    private final Dropdown dropdown = new Dropdown();
    private Modal modal = Modal.NONE;
    private List<Placement> placements = List.of();
    private int ticks;
    private boolean hadBlueprint;

    public ManipulatorScreen(final AbstractBlueprintManipulationWindow window) throws ReflectiveOperationException
    {
        super(Component.empty());
        this.window = window;
        final Field id = AbstractBlueprintManipulationWindow.class.getDeclaredField("bluePrintId");
        id.setAccessible(true);
        this.previewId = (String) id.get(window);
        this.shapes = window instanceof WindowShapeTool;
        // The replaced window's onOpened checks a position was chosen, and fills the shape tool's block icons.
        window.onOpened();
    }

    private BlueprintPreviewData preview()
    {
        return RenderingCache.getOrCreateBlueprintPreviewData(previewId);
    }

    private void press(final String id)
    {
        try
        {
            Button button = window.findPaneOfTypeByID(id, Button.class);
            if (button == null)
            {
                button = new com.ldtteam.blockui.controls.ButtonImage();
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
        MineColoniesVanillaTweaks.LOGGER.warn("Placement screen failed", e);
        BuildToolScreens.report(e);
        onClose();
    }

    // ------------------------------------------------------------------ placing (Structurize's confirmClicked)

    private void confirm()
    {
        final Blueprint blueprint = preview().getBlueprint();
        if (blueprint == null || minecraft == null || minecraft.player == null || minecraft.level == null)
        {
            return;
        }
        final List<ISurvivalBlueprintHandler> handlers = SurvivalBlueprintHandlers.getMatchingHandlers(blueprint, minecraft.level, minecraft.player,
          preview().getPos(), preview().getRotationMirror());
        if (!minecraft.player.isCreative())
        {
            if (handlers.isEmpty())
            {
                Utils.playErrorSound(minecraft.player);
                if (SurvivalBlueprintHandlers.getHandlers().isEmpty())
                {
                    minecraft.player.displayClientMessage(Component.translatable("structurize.gui.no.survival.handler"), false);
                }
                return;
            }
            if (handlers.size() == 1)
            {
                place(BuildToolPlacementMessage.HandlerType.Survival, handlers.get(0).getId());
                return;
            }
        }
        final List<Placement> list = new ArrayList<>();
        if (minecraft.player.isCreative())
        {
            list.add(new Placement(Component.translatable("structurize.gui.buildtool.complete"), BuildToolPlacementMessage.HandlerType.Complete, ""));
            list.add(new Placement(Component.translatable("structurize.gui.buildtool.pretty"), BuildToolPlacementMessage.HandlerType.Pretty, ""));
        }
        for (final ISurvivalBlueprintHandler handler : handlers)
        {
            list.add(new Placement(handler.getDisplayName(), BuildToolPlacementMessage.HandlerType.Survival, handler.getId()));
        }
        placements = list;
        modal = Modal.PLACEMENT;
        rebuildWidgets();
    }

    private void place(final BuildToolPlacementMessage.HandlerType type, final String id)
    {
        try
        {
            final Method method = AbstractBlueprintManipulationWindow.class.getDeclaredMethod("handlePlacement", BuildToolPlacementMessage.HandlerType.class,
              String.class);
            method.setAccessible(true);
            method.invoke(window, type, id);
            if (minecraft != null && minecraft.screen == this)
            {
                modal = Modal.NONE;
                rebuildWidgets();
            }
        }
        catch (final ReflectiveOperationException | LinkageError | RuntimeException e)
        {
            fail(e);
        }
    }

    // ------------------------------------------------------------------ shape tool (its static state)

    private static Object shapeField(final String name) throws ReflectiveOperationException
    {
        final Field field = WindowShapeTool.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private void setShapeField(final String name, final Object value)
    {
        try
        {
            final Field field = WindowShapeTool.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, value);
            final Method gen = WindowShapeTool.class.getDeclaredMethod("genShape");
            gen.setAccessible(true);
            gen.invoke(window);
        }
        catch (final ReflectiveOperationException | LinkageError | RuntimeException e)
        {
            fail(e);
        }
    }

    private int shapeInt(final String name)
    {
        try
        {
            return (Integer) shapeField(name);
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            return 1;
        }
    }

    private Shape shape()
    {
        try
        {
            return (Shape) shapeField("shape");
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            return Shape.CUBE;
        }
    }

    /** Which sizes the shape uses (WindowShapeTool#disableInputIfNecessary): width, length, height, frequency. */
    private boolean[] sizes()
    {
        return switch (shape())
        {
            case SPHERE, HALF_SPHERE, BOWL, PYRAMID, UPSIDE_DOWN_PYRAMID, DIAMOND -> new boolean[] {false, false, true, false};
            case CYLINDER, CONE -> new boolean[] {true, false, true, false};
            case WAVE, WAVE_3D -> new boolean[] {true, true, true, true};
            default -> new boolean[] {true, true, true, false};
        };
    }

    private static final String[] SIZE_FIELDS = {"width", "length", "height", "frequency"};

    // ------------------------------------------------------------------ widgets

    private int panelX()
    {
        return width - PANEL_WIDTH - 6;
    }

    private int panelY()
    {
        return Math.max(4, (height - panelHeight()) / 2);
    }

    private int panelHeight()
    {
        return 18 + 3 * 22 + 6 + (shapes ? 4 : 5) * 22 + 6;
    }

    @Override
    protected void init()
    {
        dropdown.close();
        try
        {
            switch (modal)
            {
                case PLACEMENT -> initPlacement();
                case SETTINGS -> initSettings();
                default -> initMain();
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            fail(e);
        }
    }

    private void initMain()
    {
        final int mx = panelX() + 8;
        final int my = panelY() + 18;
        final boolean active = preview().getBlueprint() != null;
        arrow(mx, my, "↺", "mcvanillatweaks.buildtool.rotate_left", "rotateLeft", active);
        arrow(mx + 23, my, "▲", "mcvanillatweaks.buildtool.forward", "up", active);
        arrow(mx + 46, my, "↻", "mcvanillatweaks.buildtool.rotate_right", "rotateRight", active);
        arrow(mx, my + 22, "◀", "mcvanillatweaks.buildtool.left", "left", active);
        arrow(mx + 23, my + 22, "⇄", "mcvanillatweaks.buildtool.mirror", "mirror", active);
        arrow(mx + 46, my + 22, "▶", "mcvanillatweaks.buildtool.right", "right", active);
        arrow(mx, my + 44, "↓", "mcvanillatweaks.buildtool.down", "minus", active);
        arrow(mx + 23, my + 44, "▼", "mcvanillatweaks.buildtool.back", "down", active);
        arrow(mx + 46, my + 44, "↑", "mcvanillatweaks.buildtool.up", "plus", active);

        final int bw = PANEL_WIDTH - 16;
        int by = my + 72;
        final IconButton place = addRenderableWidget(new IconButton(mx, by, bw, 20, new ItemStack(Items.LIME_DYE),
          Component.translatable("mcvanillatweaks.buildtool.place"), b -> confirm()));
        place.active = active;
        by += 22;
        addRenderableWidget(new IconButton(mx, by, bw, 20, new ItemStack(Items.BARRIER), Component.translatable("gui.cancel"), b -> press("cancel")));
        by += 22;
        if (!shapes)
        {
            addRenderableWidget(new IconButton(mx, by, bw, 20, new ItemStack(Items.PAINTING), Component.translatable("mcvanillatweaks.buildtool.style_button"),
              b -> press("switch")));
            by += 22;
        }
        final int half = (bw - 2) / 2;
        final IconButton settings = addRenderableWidget(new IconButton(mx, by, half, 20, new ItemStack(Items.COMPARATOR), Component.empty(), b -> {
            modal = Modal.SETTINGS;
            rebuildWidgets();
        }));
        settings.setTooltip(Tooltip.create(Component.translatable("mcvanillatweaks.buildtool.settings")));
        final IconButton contents = addRenderableWidget(new IconButton(mx + bw - half, by, half, 20, new ItemStack(Items.PAPER), Component.empty(), b -> {
            final BlueprintPreviewData data = preview();
            if (data.getBlueprint() != null && minecraft != null && minecraft.level != null)
            {
                minecraft.setScreen(new ContentsScreen(this, data.getBlueprint(), minecraft.level,
                  BlueprintHandler.getInstance().getOptionalEntitiesForBlueprint(data)));
            }
        }));
        contents.setTooltip(Tooltip.create(Component.translatable("com.ldtteam.structurize.gui.scantool.showres")));
        contents.active = active;

        if (shapes)
        {
            initShapes();
        }
    }

    private void arrow(final int x, final int y, final String label, final String tooltip, final String button, final boolean active)
    {
        final net.minecraft.client.gui.components.Button arrow = addRenderableWidget(new BuildToolScreen.ArrowButton(x, y, label, b -> press(button)));
        arrow.setTooltip(Tooltip.create(Component.translatable(tooltip)));
        arrow.active = active;
    }

    private int shapeX()
    {
        return 6;
    }

    private int shapeY()
    {
        return Math.max(4, (height - 210) / 2);
    }

    private void initShapes()
    {
        final int x = shapeX() + 8;
        int y = shapeY() + 20;
        final int w = SHAPE_WIDTH - 16;
        addRenderableWidget(new DropdownButton(x, y, w, 18, Component.translatable("structurize.shapetool.shape." + shape().name().toLowerCase(Locale.ROOT)),
          b -> {
              final List<Dropdown.Entry> entries = new ArrayList<>();
              for (final Shape value : Shape.values())
              {
                  entries.add(new Dropdown.Entry(Component.translatable("structurize.shapetool.shape." + value.name().toLowerCase(Locale.ROOT))));
              }
              dropdown.open(b.getX(), b.getY(), b.getWidth(), b.getHeight(), entries, shape().ordinal(), index -> {
                  setShapeField("shape", Shape.values()[index]);
                  rebuildWidgets();
              }, height);
          }));
        y += 24;
        final boolean[] used = sizes();
        for (int i = 0; i < SIZE_FIELDS.length; i++)
        {
            if (!used[i])
            {
                continue;
            }
            final String name = SIZE_FIELDS[i];
            final int value = shapeInt(name);
            addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("-"), b -> {
                setShapeField(name, Math.max(1, shapeInt(name) - (hasShiftDown() ? 10 : 1)));
                rebuildWidgets();
            }).bounds(x + w - 76, y, 18, 18).build());
            final EditBox box = addRenderableWidget(new EditBox(font, x + w - 56, y, 36, 18, Component.empty()));
            box.setFilter(text -> text.matches("\\d{0,3}"));
            box.setValue(String.valueOf(value));
            box.setResponder(text -> {
                if (!text.isEmpty() && Integer.parseInt(text) >= 1 && Integer.parseInt(text) != shapeInt(name))
                {
                    setShapeField(name, Integer.parseInt(text));
                }
            });
            addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("+"), b -> {
                setShapeField(name, shapeInt(name) + (hasShiftDown() ? 10 : 1));
                rebuildWidgets();
            }).bounds(x + w - 18, y, 18, 18).build());
            y += 22;
        }
        y += 4;
        boolean hollow = false;
        try
        {
            hollow = (Boolean) shapeField("hollow");
        }
        catch (final ReflectiveOperationException | RuntimeException ignored)
        {
            // Solid.
        }
        final boolean wasHollow = hollow;
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
          Component.translatable(hollow ? "com.ldtteam.structurize.gui.shapetool.hollow" : "com.ldtteam.structurize.gui.shapetool.solid"), b -> {
              setShapeField("hollow", !wasHollow);
              rebuildWidgets();
          }).bounds(x, y, w, 20).build()).setTooltip(Tooltip.create(Component.translatable("mcvanillatweaks.shape.hollow_hint")));
        y += 24;
        addRenderableWidget(new IconButton(x, y, w, 20, block("mainBlock"), Component.translatable("com.ldtteam.structurize.gui.shapetool.pickmainblock"),
          b -> press("pickMainBlock")));
        y += 22;
        addRenderableWidget(new IconButton(x, y, w, 20, block("secondaryBlock"), Component.translatable("com.ldtteam.structurize.gui.shapetool.pickfillblock"),
          b -> press("pickFillBlock")));
        y += 22;
        if (minecraft != null && minecraft.player != null && minecraft.player.isCreative())
        {
            addRenderableWidget(new IconButton(x, y, w, 20, new ItemStack(Items.CLOCK), Component.translatable("structurize.gui.undoredo"), b -> press("undoredo")));
        }
    }

    private ItemStack block(final String name)
    {
        try
        {
            return shapeField(name) instanceof ItemStack stack ? stack : new ItemStack(Items.GOLD_BLOCK);
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            return new ItemStack(Items.GOLD_BLOCK);
        }
    }

    private void initPlacement()
    {
        final int w = 180;
        final int h = 30 + placements.size() * 22 + 26;
        final int x = (width - w) / 2;
        final int y = (height - h) / 2;
        for (int i = 0; i < placements.size(); i++)
        {
            final Placement placement = placements.get(i);
            addRenderableWidget(net.minecraft.client.gui.components.Button.builder(placement.name(), b -> place(placement.type(), placement.id()))
                                  .bounds(x + 8, y + 24 + i * 22, w - 16, 20).build());
        }
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("gui.cancel"), b -> {
            modal = Modal.NONE;
            rebuildWidgets();
        }).bounds(x + 8, y + h - 26, w - 16, 20).build());
    }

    private void initSettings()
    {
        final List<ModConfigSpec.ConfigValue<?>> settings = BuildToolScreen.previewSettings();
        final int w = 280;
        final int h = 30 + settings.size() * 22 + 26;
        final int x = (width - w) / 2;
        final int y = (height - h) / 2;
        for (int i = 0; i < settings.size(); i++)
        {
            final ModConfigSpec.ConfigValue<?> setting = settings.get(i);
            final int ry = y + 24 + i * 22;
            if (setting.get() instanceof Boolean)
            {
                @SuppressWarnings("unchecked") final ModConfigSpec.ConfigValue<Boolean> bool = (ModConfigSpec.ConfigValue<Boolean>) setting;
                addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable(bool.get() ? "options.on" : "options.off"), b -> {
                    Structurize.getConfig().set(bool, !bool.get());
                    b.setMessage(Component.translatable(bool.get() ? "options.on" : "options.off"));
                }).bounds(x + w - 8 - 50, ry, 50, 20).build());
            }
            else if (setting.get() instanceof Number number)
            {
                final EditBox box = addRenderableWidget(new EditBox(font, x + w - 8 - 49, ry + 1, 48, 18, Component.empty()));
                box.setValue(number.toString());
                box.setFilter(text -> text.matches("-?\\d*\\.?\\d*"));
                box.setResponder(text -> BuildToolScreen.applyNumber(setting, box, text));
            }
        }
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("gui.done"), b -> {
            modal = Modal.NONE;
            rebuildWidgets();
        }).bounds(x + 8, y + h - 26, w - 16, 20).build());
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        // No background: the preview in the world must stay visible.
    }

    @Override
    public void tick()
    {
        ticks++;
        // The supplies' blueprint loads after the window opens: the buttons turn on when it arrives.
        final boolean loaded = preview().getBlueprint() != null;
        if (loaded != hadBlueprint)
        {
            hadBlueprint = loaded;
            if (modal == Modal.NONE)
            {
                rebuildWidgets();
            }
        }
    }

    @Override
    public void render(final GuiGraphics g, final int realMouseX, final int realMouseY, final float partialTick)
    {
        final int mouseX = dropdown.isOpen() ? -1000 : realMouseX;
        final int mouseY = dropdown.isOpen() ? -1000 : realMouseY;
        if (modal == Modal.NONE)
        {
            Gui.panel(g, panelX(), panelY(), PANEL_WIDTH, panelHeight());
            g.drawString(font, Gui.fit(font, title(), PANEL_WIDTH - 12), panelX() + 6, panelY() + 6, Gui.LABEL, false);
            if (shapes)
            {
                Gui.panel(g, shapeX(), shapeY(), SHAPE_WIDTH, 210);
                g.drawString(font, Component.translatable("item.structurize.shapetool"), shapeX() + 8, shapeY() + 7, Gui.LABEL, false);
                final boolean[] used = sizes();
                int y = shapeY() + 44;
                for (int i = 0; i < SIZE_FIELDS.length; i++)
                {
                    if (used[i])
                    {
                        g.drawString(font, Component.translatable("mcvanillatweaks.shape." + SIZE_FIELDS[i]), shapeX() + 8, y + 5, Gui.LABEL, false);
                        y += 22;
                    }
                }
            }
            // The tip, at the top, for ten seconds (or while the blueprint loads).
            final Component tip = preview().getBlueprint() == null ? Component.translatable("mcvanillatweaks.decoration.loading")
                                                                   : ticks < TIP_TICKS ? Component.translatable("structurize.gui.manipulation.info") : null;
            if (tip != null)
            {
                int ty = 8;
                for (final var line : font.split(tip, Math.min(360, width - 2 * PANEL_WIDTH - 40)))
                {
                    g.drawCenteredString(font, line, width / 2, ty, Gui.WHITE);
                    ty += 10;
                }
            }
        }
        else
        {
            final int w = modal == Modal.SETTINGS ? 280 : 180;
            final int count = modal == Modal.SETTINGS ? BuildToolScreen.previewSettings().size() : placements.size();
            final int h = 30 + count * 22 + 26;
            Gui.panel(g, (width - w) / 2, (height - h) / 2, w, h);
            g.drawString(font, Component.translatable(modal == Modal.SETTINGS ? "mcvanillatweaks.buildtool.settings" : "mcvanillatweaks.buildtool.place"),
              (width - w) / 2 + 8, (height - h) / 2 + 8, Gui.LABEL, false);
            if (modal == Modal.SETTINGS)
            {
                final List<ModConfigSpec.ConfigValue<?>> settings = BuildToolScreen.previewSettings();
                for (int i = 0; i < settings.size(); i++)
                {
                    final var spec = settings.get(i).getSpec();
                    final Component label = spec == null || spec.getTranslationKey() == null ? Component.literal(settings.get(i).getPath().getLast())
                                                                                             : Component.translatable(spec.getTranslationKey());
                    g.drawString(font, Gui.fit(font, label, w - 80), (width - w) / 2 + 8, (height - h) / 2 + 30 + i * 22, Gui.LABEL, false);
                }
            }
        }
        for (final var renderable : renderables)
        {
            renderable.render(g, mouseX, mouseY, partialTick);
        }
        dropdown.render(g, font, realMouseX, realMouseY);
    }

    private Component title()
    {
        if (shapes)
        {
            return Component.translatable("item.structurize.shapetool");
        }
        return Component.translatable("mcvanillatweaks.supplies.place");
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (dropdown.mouseClicked(mouseX, mouseY, button))
        {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        return dropdown.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers)
    {
        if (dropdown.keyPressed(key))
        {
            return true;
        }
        if (modal != Modal.NONE && key == GLFW.GLFW_KEY_ESCAPE)
        {
            modal = Modal.NONE;
            rebuildWidgets();
            return true;
        }
        if (modal == Modal.NONE && !(getFocused() instanceof EditBox))
        {
            final InputConstants.Key input = InputConstants.getKey(key, scanCode);
            final String[][] keys = {{"MOVE_FORWARD", "up"}, {"MOVE_BACK", "down"}, {"MOVE_LEFT", "left"}, {"MOVE_RIGHT", "right"}, {"MOVE_UP", "plus"},
              {"MOVE_DOWN", "minus"}, {"ROTATE_CW", "rotateRight"}, {"ROTATE_CCW", "rotateLeft"}, {"MIRROR", "mirror"}};
            for (final String[] entry : keys)
            {
                if (BuildToolScreen.matches(mapping(entry[0]), input))
                {
                    press(entry[1]);
                    return true;
                }
            }
            if (BuildToolScreen.matches(ModKeyMappings.PLACE, input))
            {
                confirm();
                return true;
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static net.neoforged.neoforge.common.util.Lazy<net.minecraft.client.KeyMapping> mapping(final String name)
    {
        try
        {
            return (net.neoforged.neoforge.common.util.Lazy<net.minecraft.client.KeyMapping>) ModKeyMappings.class.getField(name).get(null);
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            return null;
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
