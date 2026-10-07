package fr.mcvanillatweaks.client.dialog;

import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.views.DropDownList;
import com.minecolonies.api.crafting.ItemStorage;
import net.minecraft.util.Tuple;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import com.minecolonies.core.client.gui.WindowBuildDecoration;
import fr.mcvanillatweaks.client.citizen.Dropdown;
import fr.mcvanillatweaks.client.citizen.DropdownButton;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Replaces MineColonies' decoration build window ({@code WindowBuildDecoration}: a decoration controller's next level or
 * repair, a plantation field's repair): the builder choice and the materials of the blueprint. The replaced window keeps
 * doing the work: its private {@code updateBuilders} fills its builder list once, {@code updateResources} is called each
 * tick until the blueprint is read (then its {@code resources} map is shown), and "build" selects the builder in its
 * drop-down before pressing its button, which sends the work order request.
 * <pre>
 * [block] Build the decoration
 * Builder: [Any builder          v]
 * Materials: 12 kinds
 * +------------------------------+
 * | [log] Oak log            24  |
 * +------------------------------+
 * [Cancel]                 [Build]
 * </pre>
 */
public final class DecorationBuildScreen extends Screen
{
    /** Materials shown in the dev preview, or null. */
    public static List<ItemStack> previewResources;
    /** Builders shown in the dev preview, or null. */
    public static List<String> previewBuilders;

    private static final int WIDTH = 260;
    private static final int HEIGHT = 220;
    private static final int ROW = 18;

    private final AbstractWindowSkeleton window;
    private final Dropdown dropdown = new Dropdown();
    private final ScrollArea list = new ScrollArea();
    private final Tooltip tooltip = new Tooltip();
    private List<String> builders = new ArrayList<>();
    private List<ItemStack> resources = new ArrayList<>();
    private boolean loaded;
    private int builder;
    private net.minecraft.client.gui.components.Button build;
    private int left;
    private int top;

    public DecorationBuildScreen(final AbstractWindowSkeleton window)
    {
        super(Component.translatable("mcvanillatweaks.decoration.build_title"));
        this.window = window;
        if (previewBuilders != null)
        {
            builders = new ArrayList<>(previewBuilders);
            resources = new ArrayList<>(previewResources);
            loaded = true;
            return;
        }
        try
        {
            final Method update = WindowBuildDecoration.class.getDeclaredMethod("updateBuilders");
            update.setAccessible(true);
            update.invoke(window);
            readBuilders();
        }
        catch (final ReflectiveOperationException | LinkageError | RuntimeException e)
        {
            DialogScreens.report(e);
        }
    }

    @SuppressWarnings("unchecked")
    private void readBuilders() throws ReflectiveOperationException
    {
        final List<String> names = new ArrayList<>();
        for (final Object entry : (List<Object>) DialogScreens.field(window, "builders"))
        {
            final Tuple<String, BlockPos> builderEntry = (Tuple<String, BlockPos>) entry;
            names.add(BlockPos.ZERO.equals(builderEntry.getB()) ? Component.translatable("mcvanillatweaks.decoration.any_builder").getString() : builderEntry.getA());
        }
        builders = names;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void tick()
    {
        if (loaded || window == null)
        {
            return;
        }
        try
        {
            final Method update = WindowBuildDecoration.class.getDeclaredMethod("updateResources");
            update.setAccessible(true);
            update.invoke(window);
            if (DialogScreens.field(window, "blueprintFuture") == null)
            {
                final List<ItemStack> stacks = new ArrayList<>();
                for (final ItemStorage storage : ((Map<String, ItemStorage>) DialogScreens.field(window, "resources")).values())
                {
                    stacks.add(storage.getItemStack().copyWithCount(Math.max(1, storage.getAmount())));
                }
                stacks.sort(Comparator.comparingInt(ItemStack::getCount).reversed());
                resources = stacks;
                loaded = true;
                rebuildWidgets();
            }
        }
        catch (final ReflectiveOperationException | LinkageError | RuntimeException e)
        {
            DialogScreens.report(e);
            onClose();
        }
    }

    @Override
    protected void init()
    {
        dropdown.close();
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        final Component label = Component.translatable("mcvanillatweaks.decoration.builder");
        final int bx = left + 8 + font.width(label) + 6;
        if (!builders.isEmpty())
        {
            addRenderableWidget(new DropdownButton(bx, top + 28, left + WIDTH - 8 - bx, 16, Component.literal(builders.get(builder)), this::openBuilders));
        }
        final int bw = (WIDTH - 20) / 2;
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("gui.cancel"), b -> DialogScreens.press(window, "cancel"))
                              .bounds(left + 8, top + HEIGHT - 28, bw, 20).build());
        build = addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("com.minecolonies.coremod.gui.workerhuts.build"),
          b -> confirm()).bounds(left + WIDTH - 8 - bw, top + HEIGHT - 28, bw, 20).build());
        build.active = loaded;
    }

    private void openBuilders(final net.minecraft.client.gui.components.Button anchor)
    {
        final List<Dropdown.Entry> entries = new ArrayList<>();
        for (final String name : builders)
        {
            entries.add(new Dropdown.Entry(Component.literal(name)));
        }
        dropdown.open(anchor.getX(), anchor.getY(), anchor.getWidth(), anchor.getHeight(), entries, builder, index -> {
            builder = index;
            rebuildWidgets();
        }, height);
    }

    private void confirm()
    {
        if (window == null)
        {
            return;
        }
        try
        {
            final DropDownList drop = window.findPaneOfTypeByID("worker", DropDownList.class);
            if (drop != null)
            {
                drop.setSelectedIndex(builder);
            }
            final Button button = window.findPaneOfTypeByID("build", Button.class);
            if (button != null)
            {
                window.onButtonClicked(button);
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            DialogScreens.report(e);
            onClose();
        }
    }

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        renderTransparentBackground(g);
        Gui.panel(g, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void render(final GuiGraphics g, final int realMouseX, final int realMouseY, final float partialTick)
    {
        final int mouseX = dropdown.isOpen() ? -1000 : realMouseX;
        final int mouseY = dropdown.isOpen() ? -1000 : realMouseY;
        super.render(g, mouseX, mouseY, partialTick);
        tooltip.clear();
        g.renderItem(new ItemStack(Items.STRUCTURE_BLOCK), left + 8, top + 7);
        g.drawString(font, Gui.fit(font, getTitle(), WIDTH - 36), left + 28, top + 11, Gui.LABEL, false);
        g.drawString(font, Component.translatable("mcvanillatweaks.decoration.builder"), left + 9, top + 32, Gui.LABEL, false);

        final int listTop = top + 64;
        g.drawString(font, loaded ? Component.translatable("mcvanillatweaks.decoration.materials", resources.size())
                                  : Component.translatable("mcvanillatweaks.decoration.loading"), left + 9, listTop - 12, Gui.LABEL, false);
        list.set(left + 8, listTop, WIDTH - 16, top + HEIGHT - 34 - listTop, resources.size() * ROW, ROW);
        list.begin(g);
        for (int i = 0; i < resources.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final ItemStack stack = resources.get(i);
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            g.renderItem(stack, list.listX() + 2, rowY + 1);
            final String count = String.valueOf(stack.getCount());
            final int countX = list.listX() + list.listWidth() - 4 - font.width(count);
            g.drawString(font, Gui.fit(font, stack.getHoverName(), countX - 6 - (list.listX() + 22)), list.listX() + 22, rowY + 5, Gui.WHITE, true);
            g.drawString(font, count, countX, rowY + 5, Gui.LIST_SOFT, true);
            if (hovered)
            {
                tooltip.set(stack);
            }
        }
        list.end(g);
        dropdown.render(g, font, realMouseX, realMouseY);
        tooltip.render(g, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (dropdown.mouseClicked(mouseX, mouseY, button))
        {
            return true;
        }
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
        return dropdown.mouseScrolled(mouseX, mouseY, scrollY) || list.mouseScrolled(mouseX, mouseY, scrollY)
                 || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers)
    {
        return dropdown.keyPressed(key) || super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
