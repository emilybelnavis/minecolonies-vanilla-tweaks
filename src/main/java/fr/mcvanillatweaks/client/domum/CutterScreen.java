package fr.mcvanillatweaks.client.domum;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.container.ArchitectsCutterContainer;
import com.ldtteam.domumornamentum.item.interfaces.IDoItem;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Replaces Domum Ornamentum's Architect's Cutter screen. The menu is unchanged (its slot positions come from the server:
 * material slots at (96, 66 + 20 i), result at (183, 77), player inventory from (40, 120)), so it is drawn
 * {@link #TOP} px lower and {@link #SIDE} px to the right inside a larger frame. Domum shows the groups and variants as
 * two rows of ten unnamed icons; here the groups are a named list on the left and the variants a grid of several rows,
 * drawn with the materials in place once they make a block, like Domum. A click sends the same menu button ids as
 * Domum (group index, then group count + variant index), and the last group and variant are reselected on opening.
 * <pre>
 * Groups           Architect's Cutter               Shingles
 * | [] Vanilla  |  [][][][][][][][][][][]  ^
 * | [] Timber.. |  [][][][][][][][][][][]  |
 * | [] Shingles |  [][][][][][][][][][][]  v
 * | ...         |  Place the materials for: Oak Shingle
 * |             |  Main material  [slot]  ->  [result]
 * |             |  Support        [slot]
 * |             |      Inventory
 * </pre>
 */
public final class CutterScreen extends AbstractContainerScreen<ArchitectsCutterContainer>
{
    static final int SIDE = 126;
    static final int TOP = 22;
    private static final int MENU_WIDTH = 242;
    private static final int MENU_HEIGHT = 202;
    private static final int FRAME_WIDTH = SIDE + MENU_WIDTH;
    private static final int FRAME_HEIGHT = TOP + MENU_HEIGHT;
    private static final int GROUP_ROW = 18;
    private static final int SLOT = 18;
    private static final int COLUMNS = 11;
    private static final int ROWS = 3;
    private static final ResourceLocation CRAFTING_TABLE = ResourceLocation.withDefaultNamespace("textures/gui/container/crafting_table.png");

    /** Last choices, kept for the session like Domum's screen (menu button ids). */
    private static int groupIndexCache;
    private static int variantIndexCache = -1;

    private final ScrollArea groupList = new ScrollArea();
    private final Tooltip tooltip = new Tooltip();
    private int frameLeft;
    private int frameTop;
    private int variantRow;
    private boolean draggingScroller;
    /** Variants as drawn (with the materials in place), recomputed when the group or the materials change. */
    private List<ItemStack> shownVariants = List.of();
    @Nullable
    private String shownKey;

    public CutterScreen(final ArchitectsCutterContainer menu, final Inventory inventory, final Component title)
    {
        super(menu, inventory, title);
        imageWidth = MENU_WIDTH;
        imageHeight = MENU_HEIGHT;
    }

    // ------------------------------------------------------------------ Domum's data

    private static List<ResourceLocation> groups()
    {
        return new ArrayList<>(ModBlocks.getInstance().getOrComputeItemGroups().keySet());
    }

    private List<ItemStack> variants()
    {
        final ResourceLocation group = menu.getCurrentGroup();
        if (group == null)
        {
            return List.of();
        }
        final List<ItemStack> list = ModBlocks.getInstance().getOrComputeItemGroups().get(group);
        return list == null ? List.of() : list;
    }

    /** Domum's group names exist in English only: ours first, Domum's otherwise. */
    static Component groupName(final ResourceLocation group)
    {
        final String ours = "mcvanillatweaks.cutter.group." + group.getPath();
        return I18n.exists(ours) ? Component.translatable(ours) : Component.translatable("cuttergroup." + group.getNamespace() + "." + group.getPath());
    }

    /** Name of the i-th material slot for the chosen variant, or null when the variant uses fewer materials. */
    @Nullable
    private Component inputName(final int index)
    {
        final ItemStack variant = menu.getCurrentVariant();
        if (variant == null || !(variant.getItem() instanceof IDoItem item))
        {
            return null;
        }
        final List<ResourceLocation> ids = item.getInputIds();
        if (index >= ids.size())
        {
            return null;
        }
        final ResourceLocation id = ids.get(index);
        final String ours = "mcvanillatweaks.cutter.input." + id.getPath();
        return I18n.exists(ours) ? Component.translatable(ours)
                 : Component.translatable(id.getNamespace() + ".desc." + id.getPath(), Component.translatable("domum_ornamentum.desc.material", ""));
    }

    /** Material slots the chosen variant uses (all of them before a variant is chosen). */
    private int usedInputs()
    {
        final ItemStack variant = menu.getCurrentVariant();
        if (variant != null && variant.getItem() instanceof BlockItem item && item.getBlock() instanceof IMateriallyTexturedBlock block)
        {
            return block.getComponents().size();
        }
        return inputSlots();
    }

    private int inputSlots()
    {
        return menu.inputInventory.getContainerSize();
    }

    /** Domum shows the variants made of the materials in place once they give a result. */
    private List<ItemStack> shownVariants()
    {
        final List<ItemStack> variants = variants();
        final boolean texturize = menu.outputInventorySlot.hasItem();
        final StringBuilder key = new StringBuilder(String.valueOf(menu.getCurrentGroup())).append(texturize);
        if (texturize)
        {
            for (int i = 0; i < inputSlots(); i++)
            {
                key.append('|').append(menu.inputInventory.getItem(i).getItem());
            }
        }
        if (!key.toString().equals(shownKey))
        {
            shownKey = key.toString();
            if (!texturize)
            {
                shownVariants = variants;
            }
            else
            {
                final List<ItemStack> list = new ArrayList<>();
                for (final ItemStack variant : variants)
                {
                    list.add(texturized(variant));
                }
                shownVariants = list;
            }
        }
        return shownVariants;
    }

    private ItemStack texturized(final ItemStack variant)
    {
        final ItemStack copy = variant.copy();
        if (copy.getItem() instanceof BlockItem item && item.getBlock() instanceof IMateriallyTexturedBlock block)
        {
            final MaterialTextureData.Builder builder = MaterialTextureData.builder();
            int i = 0;
            for (final IMateriallyTexturedBlockComponent component : block.getComponents())
            {
                if (i < inputSlots() && menu.inputInventory.getItem(i).getItem() instanceof BlockItem material)
                {
                    builder.setComponent(component.getId(), material.getBlock());
                }
                i++;
            }
            builder.writeToItemStack(copy);
        }
        return copy;
    }

    private boolean isSelected(final ItemStack variant)
    {
        final ItemStack current = menu.getCurrentVariant();
        return current != null && (current == variant || ItemStack.isSameItemSameComponents(current, variant));
    }

    /** Domum's menu buttons, sent like its screen does. */
    private void press(final int id)
    {
        if (minecraft == null || minecraft.player == null || minecraft.gameMode == null)
        {
            return;
        }
        if (menu.clickMenuButton(minecraft.player, id))
        {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void selectGroup(final int index)
    {
        minecraftSound();
        groupIndexCache = index;
        variantRow = 0;
        press(index);
        // Domum keeps the previous group's variant selected; the first one of the new group is clearer.
        variantIndexCache = groups().size();
        press(variantIndexCache);
    }

    private void selectVariant(final int index)
    {
        minecraftSound();
        variantIndexCache = index + groups().size();
        press(variantIndexCache);
    }

    private static void minecraftSound()
    {
        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
    }

    /** Dev preview: selects a group and a variant without a server. */
    void previewSelect(final Player player, final int group, final int variant)
    {
        menu.clickMenuButton(player, group);
        menu.clickMenuButton(player, groups().size() + variant);
    }

    // ------------------------------------------------------------------ screen

    @Override
    protected void init()
    {
        super.init();
        frameLeft = (width - FRAME_WIDTH) / 2;
        frameTop = (height - FRAME_HEIGHT) / 2;
        leftPos = frameLeft + SIDE;
        topPos = frameTop + TOP;
    }

    @Override
    protected void containerTick()
    {
        // Domum reselects the last group and variant on opening.
        final int groupCount = groups().size();
        if (groupCount == 0)
        {
            return;
        }
        if (menu.getCurrentGroup() == null)
        {
            press(Math.min(groupIndexCache, groupCount - 1));
        }
        else if (menu.getCurrentVariant() == null)
        {
            if (variantIndexCache < groupCount || variantIndexCache - groupCount >= variants().size())
            {
                variantIndexCache = groupCount;
            }
            press(variantIndexCache);
        }
    }

    private int gridX()
    {
        return leftPos + 8;
    }

    private int gridY()
    {
        return frameTop + 16;
    }

    private int maxVariantRow()
    {
        return Math.max(0, (variants().size() + COLUMNS - 1) / COLUMNS - ROWS);
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY)
    {
        tooltip.clear();
        Gui.panel(g, frameLeft, frameTop, FRAME_WIDTH, FRAME_HEIGHT);
        renderGroups(g, mouseX, mouseY);
        renderVariants(g, mouseX, mouseY);
        renderCraft(g, mouseX, mouseY);
        // Player inventory slots.
        for (int i = inputSlots() + 1; i < menu.slots.size(); i++)
        {
            final Slot slot = menu.slots.get(i);
            Gui.slot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
    }

    private void renderGroups(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        g.drawString(font, Component.translatable("mcvanillatweaks.cutter.groups"), frameLeft + 9, frameTop + 6, Gui.LABEL, false);
        final List<ResourceLocation> groups = groups();
        final Map<ResourceLocation, List<ItemStack>> items = ModBlocks.getInstance().getOrComputeItemGroups();
        final int top = frameTop + 18;
        groupList.set(frameLeft + 8, top, SIDE - 12, frameTop + FRAME_HEIGHT - 8 - top, groups.size() * GROUP_ROW, GROUP_ROW);
        groupList.begin(g);
        for (int i = 0; i < groups.size(); i++)
        {
            final int rowY = groupList.listY() + i * GROUP_ROW;
            if (rowY + GROUP_ROW < groupList.visibleTop() || rowY > groupList.visibleBottom())
            {
                continue;
            }
            final ResourceLocation group = groups.get(i);
            final boolean selected = group.equals(menu.getCurrentGroup());
            if (selected)
            {
                g.fill(groupList.listX(), rowY, groupList.listX() + groupList.listWidth(), rowY + GROUP_ROW, Gui.ROW_SELECTED);
            }
            final boolean hovered = groupList.hover(g, mouseX, mouseY, rowY, GROUP_ROW);
            final List<ItemStack> list = items.get(group);
            if (list != null && !list.isEmpty())
            {
                g.renderItem(list.get(0), groupList.listX() + 2, rowY + 1);
            }
            final Component name = groupName(group);
            final int textWidth = groupList.listWidth() - 24;
            g.drawString(font, Gui.fit(font, name, textWidth), groupList.listX() + 21, rowY + 5, selected ? Gui.YELLOW : Gui.WHITE, true);
            if (hovered)
            {
                final List<Component> lines = new ArrayList<>();
                lines.add(name);
                lines.add(Component.translatable("mcvanillatweaks.cutter.group_count", list == null ? 0 : list.size()).withStyle(ChatFormatting.GRAY));
                tooltip.set(lines);
            }
        }
        groupList.end(g);
    }

    private void renderVariants(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        g.drawString(font, title, leftPos + 8, frameTop + 6, Gui.LABEL, false);
        final ResourceLocation group = menu.getCurrentGroup();
        if (group != null)
        {
            final Component name = groupName(group);
            final int w = Math.min(font.width(name), 90);
            g.drawString(font, Gui.fit(font, name, 90), leftPos + MENU_WIDTH - 8 - w, frameTop + 6, Gui.LABEL_SOFT, false);
        }
        final List<ItemStack> variants = shownVariants();
        final List<ItemStack> original = variants();
        variantRow = Math.min(variantRow, maxVariantRow());
        for (int row = 0; row < ROWS; row++)
        {
            for (int column = 0; column < COLUMNS; column++)
            {
                final int sx = gridX() + column * SLOT;
                final int sy = gridY() + row * SLOT;
                Gui.slot(g, sx, sy);
                final int index = (variantRow + row) * COLUMNS + column;
                if (index >= variants.size() || index >= original.size())
                {
                    continue;
                }
                if (isSelected(original.get(index)))
                {
                    g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF55FF55);
                    g.fill(sx + 2, sy + 2, sx + 16, sy + 16, 0xFF8B8B8B);
                }
                g.renderItem(variants.get(index), sx + 1, sy + 1);
                if (Gui.inside(mouseX, mouseY, sx, sy, SLOT, SLOT))
                {
                    Gui.slotHighlight(g, sx, sy);
                    tooltip.set(variants.get(index));
                }
            }
        }
        final int max = maxVariantRow();
        Gui.scrollbar(g, scrollerX(), gridY(), ROWS * SLOT, max == 0 ? 0 : (float) variantRow / max, max > 0);
    }

    private int scrollerX()
    {
        return gridX() + COLUMNS * SLOT + 4;
    }

    private void renderCraft(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        // What the slots are for.
        final ItemStack variant = menu.getCurrentVariant();
        final Component status;
        if (menu.outputInventorySlot.hasItem())
        {
            status = Component.translatable("mcvanillatweaks.cutter.result", menu.outputInventorySlot.getItem().getHoverName());
        }
        else if (variant != null)
        {
            status = Component.translatable("mcvanillatweaks.cutter.place", variant.getHoverName());
        }
        else
        {
            status = Component.translatable("mcvanillatweaks.cutter.pick");
        }
        g.drawString(font, Gui.fit(font, status, MENU_WIDTH - 16), leftPos + 8, gridY() + ROWS * SLOT + 5, Gui.LABEL, false);

        final int used = usedInputs();
        for (int i = 0; i < inputSlots(); i++)
        {
            final Slot slot = menu.slots.get(i);
            final int sx = leftPos + slot.x - 1;
            final int sy = topPos + slot.y - 1;
            Gui.slot(g, sx, sy);
            if (i >= used)
            {
                // Not used by this variant.
                g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x60000000);
                continue;
            }
            final Component name = inputName(i);
            if (name != null)
            {
                // Right-aligned against its slot.
                final int room = slot.x - 1 - 12;
                final int w = Math.min(font.width(name), room);
                g.drawString(font, Gui.fit(font, name, room), sx - 4 - w, sy + 5, Gui.LABEL, false);
                if (Gui.isCut(font, name, room) && Gui.inside(mouseX, mouseY, leftPos + 8, sy, room, SLOT) )
                {
                    tooltip.set(name);
                }
            }
        }
        // Vanilla arrow and result slot, read from the crafting table texture.
        final Slot output = menu.outputInventorySlot;
        g.blit(CRAFTING_TABLE, leftPos + output.x - 5 - 30, topPos + output.y, 90, 35, 22, 15, 256, 256);
        g.blit(CRAFTING_TABLE, leftPos + output.x - 5, topPos + output.y - 5, 119, 30, 26, 26, 256, 256);
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        g.drawString(font, playerInventoryTitle, 40, 108, Gui.LABEL, false);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        if (hoveredSlot != null && hoveredSlot.hasItem())
        {
            renderTooltip(g, mouseX, mouseY);
            return;
        }
        if (menu.getCarried().isEmpty())
        {
            tooltip.render(g, font, mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        draggingScroller = false;
        if (groupList.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button == 0 && groupList.overRows(mouseX, mouseY))
        {
            final int index = (int) Math.floor((mouseY - groupList.listY()) / GROUP_ROW);
            if (index >= 0 && index < groups().size())
            {
                selectGroup(index);
                return true;
            }
        }
        if (button == 0 && Gui.inside(mouseX, mouseY, gridX(), gridY(), COLUMNS * SLOT, ROWS * SLOT))
        {
            final int index = (variantRow + (int) ((mouseY - gridY()) / SLOT)) * COLUMNS + (int) ((mouseX - gridX()) / SLOT);
            if (index >= 0 && index < variants().size())
            {
                selectVariant(index);
            }
            return true;
        }
        if (button == 0 && maxVariantRow() > 0 && Gui.inside(mouseX, mouseY, scrollerX(), gridY(), Gui.SCROLLER_WIDTH + 2, ROWS * SLOT))
        {
            draggingScroller = true;
            scrollTo(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void scrollTo(final double mouseY)
    {
        final double travel = ROWS * SLOT - 2 - Gui.SCROLLER_HEIGHT;
        final double progress = (mouseY - gridY() - 1 - Gui.SCROLLER_HEIGHT / 2.0) / travel;
        variantRow = (int) Math.round(Math.max(0, Math.min(1, progress)) * maxVariantRow());
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY)
    {
        if (draggingScroller)
        {
            scrollTo(mouseY);
            return true;
        }
        return groupList.mouseDragged(mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button)
    {
        draggingScroller = false;
        groupList.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        if (groupList.mouseScrolled(mouseX, mouseY, scrollY))
        {
            return true;
        }
        if (Gui.inside(mouseX, mouseY, gridX(), gridY(), COLUMNS * SLOT + 4 + Gui.SCROLLER_WIDTH + 2, ROWS * SLOT))
        {
            variantRow = Math.max(0, Math.min(maxVariantRow(), variantRow - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected boolean hasClickedOutside(final double mouseX, final double mouseY, final int left, final int top, final int button)
    {
        return mouseX < frameLeft || mouseY < frameTop || mouseX >= frameLeft + FRAME_WIDTH || mouseY >= frameTop + FRAME_HEIGHT;
    }
}
