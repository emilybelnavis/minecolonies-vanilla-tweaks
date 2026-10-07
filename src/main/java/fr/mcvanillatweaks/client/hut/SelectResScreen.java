package fr.mcvanillatweaks.client.hut;

import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.TextField;
import com.ldtteam.structurize.client.gui.WindowSelectRes;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Replaces Structurize's item picker ({@code WindowSelectRes}, and {@code WindowReplaceBlock} which extends it: the shape
 * tool's blocks, the scan tool's "replace"): search, the creative-style grid (items the player carries first, like
 * Structurize), the count or percentage field when the window has one, and a "done" button when it wants a second
 * confirmation. The choice goes to the window's own {@code onSelectResource} (after copying our count into its field,
 * which the replace window reads), so its callback and its return to the previous window are Structurize's.
 * <pre>
 * [icon] Select the main block
 * [stone] Stone  ->  [granite] Granite
 * Search: [__________]   Percentage: [100]
 * [grid of items                     ]
 * [Cancel]                     [Done]
 * </pre>
 */
public final class SelectResScreen extends Screen
{
    private static final int WIDTH = 300;
    private static final int HEIGHT = 230;

    private final WindowSelectRes window;
    private final List<ItemStack> all;
    private final boolean secondaryConfirm;
    @Nullable
    private final Component countLabel;
    private final ItemStack previous;
    private final ItemGrid grid = new ItemGrid();
    private final Tooltip tooltip = new Tooltip();
    private List<FormattedCharSequence> description = List.of();
    private String filter = "";
    private String count;
    private ItemStack chosen = ItemStack.EMPTY;
    private int left;
    private int top;

    @SuppressWarnings("unchecked")
    public SelectResScreen(final WindowSelectRes window) throws ReflectiveOperationException
    {
        super(title(window));
        this.window = window;
        this.all = (List<ItemStack>) field(window, "allItems");
        this.secondaryConfirm = (Boolean) field(window, "secondaryConfirm");
        this.countLabel = field(window, "selectCountText") instanceof Component label ? label : null;
        final ItemIcon from = window.findPaneOfTypeByID(WindowSelectRes.RESOURCE_ICON_FROM, ItemIcon.class);
        this.previous = from != null && from.isVisible() && from.getItem() != null ? from.getItem() : ItemStack.EMPTY;
        final TextField countField = window.findPaneOfTypeByID(WindowSelectRes.COUNT, TextField.class);
        this.count = countField == null ? "1" : countField.getText();
        refresh();
    }

    /** The window's description; the shape tool's English one has our translation. */
    private static Component title(final WindowSelectRes window) throws ReflectiveOperationException
    {
        final Component desc = field(window, "desc") instanceof Component c ? c : Component.empty();
        return desc.getString().equals("Select the main block") ? Component.translatable("mcvanillatweaks.select.block") : desc;
    }

    private static Object field(final Object owner, final String name) throws ReflectiveOperationException
    {
        final Field field = WindowSelectRes.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private void refresh()
    {
        final String lower = filter.toLowerCase(Locale.US);
        final List<ItemStack> shown = new ArrayList<>();
        for (final ItemStack stack : all)
        {
            if (lower.isEmpty() || stack.getDescriptionId().toLowerCase(Locale.US).contains(lower)
                  || stack.getHoverName().getString().toLowerCase(Locale.US).contains(lower))
            {
                shown.add(stack);
            }
        }
        final var player = net.minecraft.client.Minecraft.getInstance().player;
        final Comparator<ItemStack> carried = Comparator.comparing((ItemStack s) -> player != null && player.getInventory().contains(s)).reversed();
        shown.sort(lower.isEmpty()
                     ? carried.thenComparing(s -> s.getHoverName().getString())
                     : carried.thenComparingInt(s -> StringUtils.getLevenshteinDistance(s.getHoverName().getString(), filter)));
        grid.setItems(shown);
        grid.scrollToTop();
    }

    @Override
    protected void init()
    {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        description = font.split(getTitle(), WIDTH - 36);
        final int rowY = top + 28 + Math.max(0, description.size() - 1) * 10 + 24;
        final EditBox search = addRenderableWidget(new EditBox(font, left + 8, rowY, countLabel == null ? WIDTH - 16 : WIDTH - 16 - 110, 18, Component.empty()));
        search.setHint(Component.translatable("mcvanillatweaks.hut.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(filter);
        search.setResponder(text -> {
            if (!text.equals(filter))
            {
                filter = text;
                refresh();
            }
        });
        setInitialFocus(search);
        if (countLabel != null)
        {
            final EditBox countBox = addRenderableWidget(new EditBox(font, left + WIDTH - 8 - 40, rowY, 40, 18, countLabel));
            countBox.setFilter(text -> text.matches("\\d{0,4}"));
            countBox.setValue(count);
            countBox.setResponder(text -> count = text);
        }
        final int gridTop = rowY + 24;
        final int columns = ItemGrid.columnsFor(WIDTH - 16);
        grid.set(left + 8 + (WIDTH - 16 - ItemGrid.width(columns)) / 2, gridTop, columns, (top + HEIGHT - 32 - gridTop) / ItemGrid.SLOT);

        final int bw = (WIDTH - 20) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> cancel())
                              .bounds(left + 8, top + HEIGHT - 26, secondaryConfirm ? bw : WIDTH - 16, 20).build());
        if (secondaryConfirm)
        {
            final Button done = addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> select(chosen))
                                                      .bounds(left + WIDTH - 8 - bw, top + HEIGHT - 26, bw, 20).build());
            done.active = !chosen.isEmpty() || chosenAir;
        }
    }

    /** Structurize lets "air" be picked for a replacement. */
    private boolean chosenAir;

    private void select(final ItemStack stack)
    {
        try
        {
            final TextField countField = window.findPaneOfTypeByID(WindowSelectRes.COUNT, TextField.class);
            if (countField != null)
            {
                countField.setText(count.isEmpty() ? "1" : count);
            }
            int amount = 1;
            if (countLabel != null)
            {
                try
                {
                    amount = Integer.parseInt(count);
                }
                catch (final NumberFormatException e)
                {
                    amount = 1;
                }
            }
            window.onSelectResource(stack, amount);
        }
        catch (final LinkageError | RuntimeException e)
        {
            MineColoniesVanillaTweaks.LOGGER.warn("Item picker failed", e);
            onClose();
        }
    }

    private void cancel()
    {
        final com.ldtteam.blockui.controls.Button button = window.findPaneOfTypeByID("cancel", com.ldtteam.blockui.controls.Button.class);
        if (button != null)
        {
            window.onButtonClicked(button);
        }
        else
        {
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
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        tooltip.clear();
        g.renderItem(previous.isEmpty() ? new ItemStack(Items.PAPER) : previous, left + 8, top + 7);
        int ty = top + 11;
        for (final FormattedCharSequence line : description)
        {
            g.drawString(font, line, left + 28, ty, Gui.LABEL, false);
            ty += 10;
        }
        // From -> to.
        final int rowY = top + 28 + Math.max(0, description.size() - 1) * 10;
        int x = left + 8;
        if (!previous.isEmpty())
        {
            g.renderItem(previous, x, rowY);
            x += 20;
            g.drawString(font, Gui.fit(font, previous.getHoverName(), 100), x, rowY + 4, Gui.LABEL, false);
            x = left + 134;
            g.drawString(font, "→", x, rowY + 4, Gui.LABEL, false);
            x += 14;
        }
        if (secondaryConfirm)
        {
            if (!chosen.isEmpty())
            {
                g.renderItem(chosen, x, rowY);
            }
            g.drawString(font, Gui.fit(font, chosen.isEmpty() ? Component.translatable(chosenAir ? "block.minecraft.air" : "mcvanillatweaks.select.none")
                                                             : chosen.getHoverName(), left + WIDTH - 8 - (x + 20)), x + 20, rowY + 4, Gui.LABEL, false);
        }
        if (countLabel != null)
        {
            final int labelX = left + WIDTH - 8 - 40 - 4 - font.width(countLabel);
            g.drawString(font, countLabel, Math.max(left + WIDTH - 110, labelX), rowY + 29, Gui.LABEL_SOFT, false);
        }
        grid.render(g, font, mouseX, mouseY, tooltip, stack -> secondaryConfirm && (chosenAir ? stack.is(Items.AIR) : !chosen.isEmpty() && ItemStack.isSameItemSameComponents(stack, chosen)));
        tooltip.render(g, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (grid.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        final ItemStack stack = grid.itemAt(mouseX, mouseY);
        if (button == 0 && stack != null)
        {
            Gui.clickSound();
            if (secondaryConfirm)
            {
                chosen = stack;
                chosenAir = stack.is(Items.AIR);
                rebuildWidgets();
            }
            else
            {
                select(stack);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY)
    {
        return grid.mouseDragged(mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button)
    {
        grid.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        return grid.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers)
    {
        if (key == GLFW.GLFW_KEY_ESCAPE)
        {
            cancel();
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
