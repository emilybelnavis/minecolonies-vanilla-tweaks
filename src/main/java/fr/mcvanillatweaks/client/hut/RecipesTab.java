package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.core.colony.buildings.moduleviews.CraftingModuleView;
import com.minecolonies.core.network.messages.server.colony.building.worker.AddRemoveRecipeMessage;
import com.minecolonies.core.network.messages.server.colony.building.worker.ChangeRecipePriorityMessage;
import com.minecolonies.core.network.messages.server.colony.building.worker.ToggleRecipeMessage;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.IconButton;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Recipes page (MineColonies' recipe list, one per crafting module of the hut): the recipes in priority order on the
 * left, the selected one on the right drawn like the crafting table (3x3 grid, arrow, result slot) with its station or
 * tool and the actions of MineColonies' rows (move up / down, enable / disable, remove). "Teach a recipe" opens the
 * same crafting menu as MineColonies. Every action calls the same module method and sends the same message.
 * <pre>
 * Active recipes: 4 / 10                 [Teach a recipe]
 * +---------------------+   [][][]
 * | (o) Bread          |   [][][]  ->  [  ]
 * | (o) Cake           |   [][][]
 * | ...                |   Station: Furnace
 * +---------------------+   [Up] [Down] [Disable] [Remove]
 * </pre>
 */
final class RecipesTab extends HutTab
{
    private static final ResourceLocation CRAFTING_TABLE = ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");
    private static final int ROW = 20;
    private static final int LIST_WIDTH = 150;
    private static final int TOP_BAR = 20;
    private static final int BUTTON_HEIGHT = 20;

    /** The selected recipe, remembered per module while the game runs (the page is rebuilt on every action). */
    private static int lastModule = -1;
    private static int lastSelected;

    private final CraftingModuleView module;
    private final ScrollArea list = new ScrollArea();
    private int selected;
    private int lifeCount;
    private Button upButton;
    private Button downButton;
    private Button toggleButton;
    private Button removeButton;

    RecipesTab(final HutScreen screen, final CraftingModuleView module)
    {
        super(screen);
        this.module = module;
        final int id = runtimeId();
        selected = id == lastModule ? lastSelected : 0;
        lastModule = id;
    }

    private int runtimeId()
    {
        return module.getProducer() == null ? -1 : module.getProducer().getRuntimeID();
    }

    private List<IRecipeStorage> recipes()
    {
        return module.getRecipes();
    }

    @Nullable
    private IRecipeStorage selectedRecipe()
    {
        final List<IRecipeStorage> recipes = recipes();
        if (recipes.isEmpty())
        {
            return null;
        }
        selected = Math.max(0, Math.min(selected, recipes.size() - 1));
        return recipes.get(selected);
    }

    private void select(final int index)
    {
        selected = index;
        lastSelected = index;
        update();
    }

    // ------------------------------------------------------------------ widgets

    private int detailX()
    {
        return x + LIST_WIDTH + GAP;
    }

    private int detailWidth()
    {
        return x + width - detailX();
    }

    @Override
    protected void init()
    {
        if (module.isRecipeAlterationAllowed())
        {
            final Component teach = Component.translatable("com.minecolonies.coremod.gui.workerhuts.crafting");
            final int teachWidth = font.width(teach) + 34;
            add(new IconButton(x + width - teachWidth, y, teachWidth, BUTTON_HEIGHT, new ItemStack(Items.CRAFTING_TABLE), teach, b -> module.openCraftingGUI()));
        }
        final int by = y + height - BUTTON_HEIGHT;
        final int half = (detailWidth() - 4) / 2;
        upButton = add(Button.builder(Component.literal("↑ ").append(Component.translatable("mcvanillatweaks.hut.recipe.up")), b -> move(true))
                         .bounds(detailX(), by - BUTTON_HEIGHT - 4, half, BUTTON_HEIGHT).build());
        downButton = add(Button.builder(Component.literal("↓ ").append(Component.translatable("mcvanillatweaks.hut.recipe.down")), b -> move(false))
                           .bounds(detailX() + half + 4, by - BUTTON_HEIGHT - 4, detailWidth() - half - 4, BUTTON_HEIGHT).build());
        toggleButton = add(Button.builder(Component.empty(), b -> toggle()).bounds(detailX(), by, half, BUTTON_HEIGHT).build());
        removeButton = add(Button.builder(Component.translatable("com.minecolonies.coremod.gui.recipe.remove"), b -> remove())
                             .bounds(detailX() + half + 4, by, detailWidth() - half - 4, BUTTON_HEIGHT).build());
        update();
    }

    @Override
    protected void tick()
    {
        // MineColonies' row icons cycle through a recipe's outputs, and stop while shift is held.
        if (!Screen.hasShiftDown())
        {
            lifeCount++;
        }
        update();
    }

    /** Button states, following MineColonies' row rules. */
    private void update()
    {
        if (upButton == null)
        {
            return;
        }
        final IRecipeStorage recipe = selectedRecipe();
        final boolean alter = module.isRecipeAlterationAllowed();
        upButton.visible = downButton.visible = toggleButton.visible = recipe != null;
        removeButton.visible = recipe != null && alter;
        if (recipe == null)
        {
            return;
        }
        upButton.active = selected > 0;
        downButton.active = selected < recipes().size() - 1;
        final boolean disabled = module.isDisabled(recipe);
        toggleButton.setMessage(Component.translatable(disabled ? "com.minecolonies.coremod.gui.recipe.enable" : "com.minecolonies.coremod.gui.recipe.disable"));
        // A disabled recipe can be enabled again if it is built in or if there is room.
        toggleButton.active = !disabled || recipe.getRecipeSource() != null || module.getActiveRecipes() < module.getMaxRecipes();
        // Built-in recipes are disabled rather than removed, unless ctrl is held (MineColonies' reset).
        removeButton.active = recipe.getRecipeSource() == null || Screen.hasControlDown();
    }

    // ------------------------------------------------------------------ actions, as WindowListRecipes

    private void move(final boolean up)
    {
        final IRecipeStorage recipe = selectedRecipe();
        if (recipe == null)
        {
            return;
        }
        // Shift moves to the top or the bottom, like MineColonies.
        final boolean full = Screen.hasShiftDown();
        final int row = selected;
        module.switchOrder(row, up ? row - 1 : row + 1, full);
        new ChangeRecipePriorityMessage(building(), row, up, runtimeId(), full).sendToServer();
        select(recipes().indexOf(recipe));
    }

    private void toggle()
    {
        if (selectedRecipe() == null)
        {
            return;
        }
        module.toggle(selected);
        new ToggleRecipeMessage(building(), selected, runtimeId()).sendToServer();
        update();
    }

    private void remove()
    {
        final IRecipeStorage recipe = selectedRecipe();
        if (recipe == null)
        {
            return;
        }
        module.removeRecipe(selected);
        new AddRemoveRecipeMessage(building(), true, recipe, runtimeId()).sendToServer();
        select(Math.max(0, selected - 1));
    }

    // ------------------------------------------------------------------ recipe data

    /** The output shown for a recipe: MineColonies cycles through the outputs of its recipe type. */
    private ItemStack output(final IRecipeStorage recipe)
    {
        List<ItemStack> outputs = null;
        try
        {
            outputs = recipe.getRecipeType() == null ? null : recipe.getRecipeType().getOutputDisplayStacks();
        }
        catch (final RuntimeException ignored)
        {
            // falls back to the primary output
        }
        if (outputs == null || outputs.isEmpty())
        {
            return recipe.getPrimaryOutput();
        }
        return outputs.get(lifeCount / 30 % outputs.size());
    }

    private static ItemStack withCount(final ItemStorage storage)
    {
        return storage.getItemStack().copyWithCount(storage.getAmount());
    }

    /** The 3x3 grid as MineColonies fills it: inputs in order, four inputs as a 2x2 square. */
    private static ItemStack[] grid(final IRecipeStorage recipe)
    {
        final ItemStack[] grid = new ItemStack[9];
        Arrays.fill(grid, ItemStack.EMPTY);
        final List<ItemStorage> input = recipe.getInput();
        if (input.size() == 4)
        {
            final int[] square = {0, 1, 3, 4};
            for (int i = 0; i < 4; i++)
            {
                grid[square[i]] = withCount(input.get(i));
            }
        }
        else
        {
            for (int i = 0; i < Math.min(9, input.size()); i++)
            {
                grid[i] = withCount(input.get(i));
            }
        }
        return grid;
    }

    @Nullable
    private static Component tool(final IRecipeStorage recipe)
    {
        final EquipmentTypeEntry tool = recipe.getRequiredTool();
        return tool == null || tool == ModEquipmentTypes.none.get() ? null : tool.getDisplayName();
    }

    @Nullable
    private static Block station(final IRecipeStorage recipe)
    {
        final Block block = recipe.getIntermediate();
        return block == null || block == Blocks.AIR ? null : block;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final List<IRecipeStorage> recipes = recipes();
        if (module.isRecipeAlterationAllowed())
        {
            final Component status = Component.translatable("mcvanillatweaks.hut.recipe.active", module.getActiveRecipes(), module.getMaxRecipes());
            g.drawString(font, status, x + 1, y + 6, Gui.LABEL, false);
            if (Gui.inside(mouseX, mouseY, x, y, font.width(status) + 2, BUTTON_HEIGHT))
            {
                tooltip.set(List.of(status, Component.translatable("mcvanillatweaks.hut.recipe.active_hint").withStyle(ChatFormatting.GRAY)));
            }
        }
        else
        {
            heading(g, Component.translatable("mcvanillatweaks.hut.recipe.count", recipes.size()), x, y + 6, width);
        }

        final int top = y + TOP_BAR + 6;
        list.set(x, top, LIST_WIDTH, y + height - top, recipes.size() * ROW, ROW);
        list.begin(g);
        if (recipes.isEmpty())
        {
            final List<FormattedCharSequence> lines = font.split(Component.translatable("mcvanillatweaks.hut.recipe.none"), list.listWidth() - 8);
            for (int i = 0; i < lines.size(); i++)
            {
                g.drawString(font, lines.get(i), list.listX() + 4, list.visibleTop() + 6 + i * 10, Gui.LIST_SOFT, true);
            }
        }
        for (int i = 0; i < recipes.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final IRecipeStorage recipe = recipes.get(i);
            if (i == selected)
            {
                g.fill(list.listX(), rowY, list.listX() + list.listWidth(), rowY + ROW, Gui.ROW_SELECTED);
            }
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            final boolean disabled = module.isDisabled(recipe);
            final ItemStack out = output(recipe);
            g.renderItem(out, list.listX() + 2, rowY + 2);
            final Component name = out.getHoverName();
            final int textX = list.listX() + 21;
            final int textWidth = list.listX() + list.listWidth() - 3 - textX;
            g.drawString(font, Gui.fit(font, name, textWidth), textX, rowY + 6, disabled ? Gui.LIST_SOFT : Gui.WHITE, true);
            if (disabled)
            {
                // Struck through, like an unavailable entry.
                final int lineWidth = Math.min(font.width(name), textWidth);
                g.fill(textX, rowY + 9, textX + lineWidth, rowY + 10, 0xFFAAAAAA);
            }
            if (hovered)
            {
                final List<Component> lines = new ArrayList<>();
                lines.add(name);
                lines.add(Component.translatable("mcvanillatweaks.hut.recipe.priority", i + 1).withStyle(ChatFormatting.GRAY));
                if (disabled)
                {
                    lines.add(Component.translatable("mcvanillatweaks.hut.recipe.disabled").withStyle(ChatFormatting.RED));
                }
                if (recipe.getRecipeSource() != null)
                {
                    lines.add(Component.translatable("mcvanillatweaks.hut.recipe.builtin").withStyle(ChatFormatting.GRAY));
                }
                tooltip.setAround(lines, mouseX, rowY, rowY + ROW);
            }
        }
        list.end(g);

        final IRecipeStorage recipe = selectedRecipe();
        if (recipe != null)
        {
            renderDetail(g, recipe, top, mouseX, mouseY, tooltip);
        }
    }

    /** The selected recipe as the crafting table shows one. */
    private void renderDetail(final GuiGraphics g, final IRecipeStorage recipe, final int top, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final int gridWidth = 54 + 8 + 22 + 8 + 26;
        final int gx = detailX() + (detailWidth() - gridWidth) / 2;
        final int gy = top + 2;
        final ItemStack[] grid = grid(recipe);
        for (int i = 0; i < 9; i++)
        {
            final int sx = gx + (i % 3) * 18;
            final int sy = gy + (i / 3) * 18;
            slot(g, sx, sy, grid[i], mouseX, mouseY, tooltip);
        }
        // Vanilla arrow and result slot, read from the crafting table texture.
        final int ax = gx + 54 + 8;
        g.blit(CRAFTING_TABLE, ax, gy + 19, 90, 35, 22, 15, 256, 256);
        final int rx = ax + 22 + 8;
        final int ry = gy + 14;
        g.blit(CRAFTING_TABLE, rx, ry, 119, 30, 26, 26, 256, 256);
        final ItemStack out = output(recipe);
        g.renderItem(out, rx + 5, ry + 5);
        g.renderItemDecorations(font, out, rx + 5, ry + 5);
        if (Gui.inside(mouseX, mouseY, rx, ry, 26, 26))
        {
            g.fillGradient(rx + 5, ry + 5, rx + 21, ry + 21, 0x80FFFFFF, 0x80FFFFFF);
            tooltip.set(out);
        }

        // Station or tool, then the state, then the extra outputs.
        int ty = gy + 54 + 4;
        final Block station = station(recipe);
        final Component tool = tool(recipe);
        if (tool != null)
        {
            ty = info(g, Component.translatable("mcvanillatweaks.hut.recipe.tool", tool), ty);
        }
        else if (station != null)
        {
            ty = info(g, Component.translatable("mcvanillatweaks.hut.recipe.station", station.getName()), ty);
        }
        if (module.isDisabled(recipe))
        {
            ty = info(g, Component.translatable("mcvanillatweaks.hut.recipe.disabled").withStyle(ChatFormatting.DARK_RED), ty);
        }
        final List<ItemStack> extra = new ArrayList<>(recipe.getSecondaryOutputs() == null ? List.of() : recipe.getSecondaryOutputs());
        if (!extra.isEmpty() && ty + 18 <= upButton.getY() - 2)
        {
            final Component label = Component.translatable("mcvanillatweaks.hut.recipe.byproducts");
            g.drawString(font, label, detailX() + 1, ty + 5, Gui.LABEL_SOFT, false);
            int sx = detailX() + font.width(label) + 4;
            for (final ItemStack stack : extra)
            {
                if (sx + 18 > detailX() + detailWidth())
                {
                    break;
                }
                slot(g, sx, ty, stack, mouseX, mouseY, tooltip);
                sx += 18;
            }
        }

        if (removeButton.visible && removeButton.isHovered() && !removeButton.active)
        {
            tooltip.set(Component.translatable("com.minecolonies.coremod.gui.workerhuts.removebuiltin", Component.translatable("key.keyboard.left.control")));
        }
        if (upButton.isHovered() || downButton.isHovered())
        {
            tooltip.set(Component.translatable("mcvanillatweaks.hut.recipe.move_hint").withStyle(ChatFormatting.GRAY));
        }
        if (toggleButton.isHovered() && !toggleButton.active)
        {
            tooltip.set(Component.translatable("com.minecolonies.coremod.gui.warehouse.limitreached"));
        }
    }

    private int info(final GuiGraphics g, final Component text, final int ty)
    {
        g.drawString(font, Gui.fit(font, text, detailWidth() - 2), detailX() + 1, ty, Gui.LABEL, false);
        return ty + 12;
    }

    private void slot(final GuiGraphics g, final int sx, final int sy, final ItemStack stack, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        Gui.slot(g, sx, sy);
        Gui.slotItem(g, font, sx, sy, stack, null);
        if (Gui.inside(mouseX, mouseY, sx, sy, 18, 18))
        {
            Gui.slotHighlight(g, sx, sy);
            if (!stack.isEmpty())
            {
                tooltip.set(stack);
            }
        }
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
        if (index < 0 || index >= recipes().size())
        {
            return false;
        }
        if (index != selected)
        {
            Gui.clickSound();
            select(index);
        }
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
