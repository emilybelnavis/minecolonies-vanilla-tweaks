package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.client.render.worldevent.HighlightManager;
import com.minecolonies.core.client.render.worldevent.highlightmanager.TimedBoxRenderData;
import com.minecolonies.core.colony.buildings.moduleviews.WarehouseOptionsModuleView;
import com.minecolonies.core.network.messages.server.colony.building.warehouse.SortBuildingMessage;
import com.minecolonies.core.network.messages.server.colony.building.warehouse.UpgradeWarehouseMessage;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.IconButton;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Storage page of the warehouse (MineColonies' warehouse options window, its "settings" tab): what the racks hold, read
 * from the racks the client has loaded (MineColonies sends their whole inventory to the client), on the right; on the
 * left the fill rate, the storage upgrade (an emerald block per level, 3 levels, at the hut's maximum level) and the
 * sort button (level 3). Upgrade and sort send MineColonies' messages, with the same conditions as its window. A click
 * on an item outlines the racks holding it in the world, like the "locate" button of MineColonies' all-inventory window.
 * <pre>
 * Storage                     Contents (87)
 * +----------------------+   [Search...              ]
 * | [chest] 24 racks     |   [][][][][][][][]  |
 * | 12 345 items · 87    |   [][][][][][][][]  |
 * | Slots     312 / 648  |   ...
 * | [=======-------]     |
 * +----------------------+
 * Rack upgrade
 * | [emerald] block 1/3  |
 * [ Upgrade            ]
 * [ Sort the warehouse ]
 * </pre>
 */
final class WarehouseTab extends HutTab
{
    /** MineColonies' limits: BuildingWareHouse.MAX_STORAGE_UPGRADE and the level its sort button needs. */
    private static final int MAX_UPGRADE = 3;
    private static final int SORT_LEVEL = 3;
    /** Each upgrade gives every rack one more row. */
    private static final int SLOTS_PER_UPGRADE = 9;
    private static final int LEFT_WIDTH = 132;
    private static final int BUTTON_HEIGHT = 20;
    private static final int STATS_HEIGHT = 54;
    private static final int UPGRADE_HEIGHT = 28;
    /** MineColonies' highlight group for located items, so a new search replaces its boxes. */
    private static final String HIGHLIGHT_KEY = "inventoryHighlight";

    /** Contents and fill of the hut's containers. */
    record Storage(int containers, int unloaded, int slots, int usedSlots, List<ItemStack> items, Map<ItemStack, Integer> counts,
                   Map<ItemStack, Integer> places)
    {
        long total()
        {
            long total = 0;
            for (final int count : counts.values())
            {
                total += count;
            }
            return total;
        }
    }

    /** Set by the dev preview, which has no world to read racks from. */
    @Nullable
    static Storage previewStorage;

    /** Null for the storage page of the other huts: no rack options, and the hut block's own inventory counts. */
    @Nullable
    private final WarehouseOptionsModuleView module;
    private final ItemGrid grid = new ItemGrid();
    private Storage storage;
    private String filter = "";
    /** Like MineColonies' window: one upgrade per opening, the module view is only updated by the next sync. */
    private boolean lockUpgrade;
    @Nullable
    private IconButton upgrade;
    @Nullable
    private IconButton sort;
    private int ticks;

    WarehouseTab(final HutScreen screen, @Nullable final WarehouseOptionsModuleView module)
    {
        super(screen);
        this.module = module;
        storage = scan(screen.building(), module == null);
        // MineColonies' window asks for a fresh copy of the hut when it opens.
        BuilderSite.refresh(screen.building());
    }

    // ------------------------------------------------------------------ racks

    static Storage scan(final IBuildingView building)
    {
        return scan(building, false);
    }

    /** The hut's containers, plus the hut block itself for the all-inventory view, like MineColonies' window. */
    private static Set<BlockPos> containers(final IBuildingView building, final boolean withHut)
    {
        final Set<BlockPos> containers = new LinkedHashSet<>(building.getContainers());
        if (withHut)
        {
            containers.add(building.getPosition());
        }
        return containers;
    }

    static Storage scan(final IBuildingView building, final boolean withHut)
    {
        if (previewStorage != null)
        {
            return previewStorage;
        }
        final ClientLevel level = Minecraft.getInstance().level;
        int containers = 0;
        int unloaded = 0;
        int slots = 0;
        int used = 0;
        final Map<Key, ItemStack> stacks = new HashMap<>();
        final Map<ItemStack, Integer> counts = new IdentityHashMap<>();
        final Map<ItemStack, Integer> places = new IdentityHashMap<>();
        for (final BlockPos pos : containers(building, withHut))
        {
            final BlockEntity entity = level != null && level.isLoaded(pos) ? level.getBlockEntity(pos) : null;
            if (!(entity instanceof AbstractTileEntityRack rack))
            {
                unloaded++;
                continue;
            }
            containers++;
            final IItemHandler inventory = rack.getInventory();
            slots += inventory.getSlots();
            final Set<ItemStack> here = Collections.newSetFromMap(new IdentityHashMap<>());
            for (int slot = 0; slot < inventory.getSlots(); slot++)
            {
                final ItemStack stack = inventory.getStackInSlot(slot);
                if (stack.isEmpty())
                {
                    continue;
                }
                used++;
                final ItemStack shown = stacks.computeIfAbsent(new Key(stack), key -> stack.copyWithCount(1));
                counts.merge(shown, stack.getCount(), Integer::sum);
                here.add(shown);
            }
            for (final ItemStack shown : here)
            {
                places.merge(shown, 1, Integer::sum);
            }
        }
        return new Storage(containers, unloaded, slots, used, new ArrayList<>(stacks.values()), counts, places);
    }

    /** Items told apart like stacks: same item, same components. */
    private record Key(ItemStack stack)
    {
        @Override
        public boolean equals(final Object other)
        {
            return other instanceof Key key && ItemStack.isSameItemSameComponents(stack, key.stack);
        }

        @Override
        public int hashCode()
        {
            return ItemStack.hashItemAndComponents(stack);
        }
    }

    private int count(final ItemStack stack)
    {
        return storage.counts().getOrDefault(stack, 0);
    }

    /** Most plentiful first, then by name; the search matches the name or the item id. */
    private void refreshGrid()
    {
        final String search = filter.toLowerCase(Locale.ROOT).trim();
        final List<ItemStack> shown = new ArrayList<>();
        for (final ItemStack stack : storage.items())
        {
            if (search.isEmpty() || stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(search)
                  || stack.getItem().builtInRegistryHolder().key().location().toString().contains(search))
            {
                shown.add(stack);
            }
        }
        shown.sort(Comparator.comparingInt(this::count).reversed().thenComparing(stack -> stack.getHoverName().getString()));
        grid.setItems(shown);
        grid.setCounts(this::count, stack -> List.of(
          Component.translatable("mcvanillatweaks.hut.warehouse.places", storage.places().getOrDefault(stack, 0)).withStyle(ChatFormatting.GRAY),
          Component.translatable("mcvanillatweaks.hut.warehouse.locate").withStyle(ChatFormatting.YELLOW)));
    }

    // ------------------------------------------------------------------ widgets

    private int rightX()
    {
        return x + LEFT_WIDTH + GAP;
    }

    private int rightWidth()
    {
        return width - LEFT_WIDTH - GAP;
    }

    private int upgradeTop()
    {
        return y + HEADING + STATS_HEIGHT + 10 + HEADING;
    }

    @Override
    protected void init()
    {
        if (module != null)
        {
            initRackOptions();
        }
        final EditBox search = add(new EditBox(font, rightX() + 1, y + HEADING + 1, rightWidth() - 2, 16, Component.translatable("mcvanillatweaks.hut.search")));
        search.setHint(Component.translatable("mcvanillatweaks.hut.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setMaxLength(50);
        search.setValue(filter);
        search.setResponder(text -> {
            if (!text.equals(filter))
            {
                filter = text;
                refreshGrid();
                grid.scrollToTop();
            }
        });
        refreshGrid();
        update();
    }

    private void initRackOptions()
    {
        final int by = upgradeTop() + UPGRADE_HEIGHT + 6;
        upgrade = add(new IconButton(x, by, LEFT_WIDTH, BUTTON_HEIGHT, new ItemStack(Items.EMERALD_BLOCK),
          Component.translatable("mcvanillatweaks.hut.warehouse.upgrade"), b -> upgrade()));
        sort = add(new IconButton(x, by + BUTTON_HEIGHT + 4, LEFT_WIDTH, BUTTON_HEIGHT, new ItemStack(Items.HOPPER),
          Component.translatable("mcvanillatweaks.hut.warehouse.sort"), b -> sort()));
    }

    @Override
    protected void tick()
    {
        if (++ticks % 20 == 0)
        {
            storage = scan(building(), module == null);
            refreshGrid();
        }
        update();
    }

    private void update()
    {
        if (upgrade == null || sort == null)
        {
            return;
        }
        upgrade.active = upgradeState() == UpgradeState.AVAILABLE;
        sort.active = building().getBuildingLevel() >= SORT_LEVEL;
    }

    private enum UpgradeState
    {
        LOW_LEVEL, DONE, GIVEN, MISSING, AVAILABLE
    }

    /** MineColonies' rules: at the hut's maximum level, under 3 upgrades, one emerald block in the inventory (or creative). */
    private UpgradeState upgradeState()
    {
        if (building().getBuildingLevel() < building().getBuildingMaxLevel())
        {
            return UpgradeState.LOW_LEVEL;
        }
        if (module == null || module.getStorageUpgradeLevel() >= MAX_UPGRADE)
        {
            return UpgradeState.DONE;
        }
        if (lockUpgrade)
        {
            return UpgradeState.GIVEN;
        }
        return emeraldBlocks() > 0 ? UpgradeState.AVAILABLE : UpgradeState.MISSING;
    }

    private int emeraldBlocks()
    {
        final Player player = mc.player;
        if (player == null)
        {
            return 0;
        }
        return player.isCreative() ? 1 : player.getInventory().countItem(Items.EMERALD_BLOCK);
    }

    private void upgrade()
    {
        if (module == null || upgradeState() != UpgradeState.AVAILABLE)
        {
            return;
        }
        new UpgradeWarehouseMessage(building()).sendToServer();
        module.incrementStorageUpgrade();
        lockUpgrade = true;
        update();
    }

    private void sort()
    {
        if (building().getBuildingLevel() >= SORT_LEVEL && mc.player != null)
        {
            new SortBuildingMessage(building()).sendToServer();
            MessageUtils.format("com.minecolonies.coremod.gui.warehouse.sorted").sendTo(mc.player);
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        renderStats(g, mouseX, mouseY, tooltip);
        if (module == null || upgrade == null || sort == null)
        {
            // Other huts: what the storage holds, explained under the card.
            int ty = y + HEADING + STATS_HEIGHT + 8;
            for (final var line : font.split(Component.translatable("mcvanillatweaks.hut.storage.hint"), LEFT_WIDTH - 2))
            {
                g.drawString(font, line, x + 1, ty, Gui.LABEL_SOFT, false);
                ty += 10;
            }
            renderContents(g, mouseX, mouseY, tooltip);
            return;
        }
        renderUpgrade(g, mouseX, mouseY, tooltip);
        if (sort.isHovered())
        {
            final List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("mcvanillatweaks.hut.warehouse.sort"));
            lines.add(Component.translatable("mcvanillatweaks.hut.warehouse.sort_hint").withStyle(ChatFormatting.GRAY));
            if (!sort.active)
            {
                lines.add(Component.translatable("mcvanillatweaks.hut.warehouse.needs_level", SORT_LEVEL).withStyle(ChatFormatting.RED));
            }
            tooltip.set(lines);
        }
        renderContents(g, mouseX, mouseY, tooltip);
    }

    private void renderStats(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        heading(g, Component.translatable("mcvanillatweaks.hut.warehouse.storage"), x, y, LEFT_WIDTH);
        final int top = y + HEADING;
        final int w = LEFT_WIDTH;
        Gui.inset(g, x, top, w, STATS_HEIGHT);
        final boolean hovered = Gui.inside(mouseX, mouseY, x, top, w, STATS_HEIGHT);
        if (hovered)
        {
            g.fill(x + 1, top + 1, x + w - 1, top + STATS_HEIGHT - 1, Gui.ROW_HOVER);
        }
        g.renderItem(new ItemStack(Items.BARREL), x + 4, top + 4);
        g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.hut.warehouse.containers", storage.containers()), w - 28), x + 24, top + 4, Gui.WHITE, true);
        g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.hut.warehouse.items", ItemGrid.grouped(storage.total())), w - 28),
          x + 24, top + 14, Gui.LIST_SOFT, true);
        final float fill = fill(storage);
        renderFill(g, font, storage, x, top + 29, w);
        if (hovered)
        {
            final List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("mcvanillatweaks.hut.warehouse.fill", Math.round(fill * 100)));
            lines.add(Component.translatable("mcvanillatweaks.hut.warehouse.free", ItemGrid.grouped(storage.slots() - storage.usedSlots())).withStyle(ChatFormatting.GRAY));
            if (storage.unloaded() > 0)
            {
                lines.add(Component.translatable("mcvanillatweaks.hut.warehouse.unloaded", storage.unloaded()).withStyle(ChatFormatting.GOLD));
            }
            tooltip.set(lines);
        }
    }

    static float fill(final Storage storage)
    {
        return storage.slots() == 0 ? 0 : (float) storage.usedSlots() / storage.slots();
    }

    /**
     * Two lines inside an inset of width {@code w}: used / total slots, then the villager experience bar with the fill
     * rate beside it (grey, gold from 75 %, red from 90 %).
     */
    static void renderFill(final GuiGraphics g, final Font font, final Storage storage, final int left, final int top, final int w)
    {
        final float fill = fill(storage);
        final int colour = fill >= 0.9F ? Gui.RED : fill >= 0.75F ? Gui.GOLD : Gui.LIST_SOFT;
        final String slots = ItemGrid.grouped(storage.usedSlots()) + " / " + ItemGrid.grouped(storage.slots());
        g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.hut.warehouse.slots"), w - 14 - font.width(slots)), left + 5, top, Gui.WHITE, true);
        g.drawString(font, slots, left + w - 5 - font.width(slots), top, Gui.LIST_SOFT, true);
        final String percent = Math.round(fill * 100) + " %";
        final int barWidth = Math.min(Gui.BAR_WIDTH, w - 16 - font.width(percent));
        Gui.bar(g, left + 5, top + 13, barWidth, fill);
        g.drawString(font, percent, left + w - 5 - font.width(percent), top + 11, colour, true);
    }

    private void renderUpgrade(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final int top = upgradeTop();
        heading(g, Component.translatable("mcvanillatweaks.hut.warehouse.capacity"), x, top - HEADING, LEFT_WIDTH);
        final int w = LEFT_WIDTH;
        Gui.inset(g, x, top, w, UPGRADE_HEIGHT);
        final boolean hovered = Gui.inside(mouseX, mouseY, x, top, w, UPGRADE_HEIGHT);
        if (hovered)
        {
            g.fill(x + 1, top + 1, x + w - 1, top + UPGRADE_HEIGHT - 1, Gui.ROW_HOVER);
        }
        final UpgradeState state = upgradeState();
        g.renderItem(new ItemStack(Items.EMERALD_BLOCK), x + 4, top + 6);
        final int colour = switch (state)
        {
            case AVAILABLE -> Gui.GREEN;
            case MISSING -> Gui.RED;
            default -> Gui.WHITE;
        };
        g.drawString(font, Gui.fit(font, Items.EMERALD_BLOCK.getDescription(), w - 28), x + 24, top + 5, colour, true);
        final Component progress = Component.translatable("mcvanillatweaks.hut.warehouse.upgrades", module.getStorageUpgradeLevel(), MAX_UPGRADE);
        g.drawString(font, Gui.fit(font, progress, w - 28), x + 24, top + 16, Gui.LIST_SOFT, true);
        if (hovered || upgrade.isHovered())
        {
            final List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("com.minecolonies.coremod.gui.warehouse.upgrade"));
            lines.add(Component.translatable("mcvanillatweaks.hut.warehouse.upgrade_hint", SLOTS_PER_UPGRADE).withStyle(ChatFormatting.GRAY));
            switch (state)
            {
                case LOW_LEVEL ->
                  lines.add(Component.translatable("mcvanillatweaks.hut.warehouse.needs_level", building().getBuildingMaxLevel()).withStyle(ChatFormatting.RED));
                case DONE -> lines.add(Component.translatable("mcvanillatweaks.hut.warehouse.upgrade_done").withStyle(ChatFormatting.GREEN));
                case GIVEN -> lines.add(Component.translatable("mcvanillatweaks.hut.warehouse.upgrade_given").withStyle(ChatFormatting.GREEN));
                case MISSING -> lines.add(Component.translatable("mcvanillatweaks.hut.warehouse.upgrade_missing").withStyle(ChatFormatting.RED));
                case AVAILABLE -> lines.add(Component.translatable("mcvanillatweaks.hut.warehouse.upgrade_ready", emeraldBlocks()).withStyle(ChatFormatting.GREEN));
            }
            tooltip.set(lines);
        }
    }

    private void renderContents(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final int rx = rightX();
        final int rw = rightWidth();
        heading(g, Component.translatable("mcvanillatweaks.hut.warehouse.contents", storage.items().size()), rx, y, rw);
        final int top = y + HEADING + 22;
        final int columns = ItemGrid.columnsFor(rw);
        final int gridX = rx + (rw - ItemGrid.width(columns)) / 2;
        grid.set(gridX, top, columns, (y + height - top) / ItemGrid.SLOT);
        grid.render(g, font, mouseX, mouseY, tooltip, stack -> false);
        if (grid.items().isEmpty())
        {
            final Component empty = storage.items().isEmpty()
                                      ? Component.translatable(module == null ? "mcvanillatweaks.hut.storage.empty" : "mcvanillatweaks.hut.warehouse.empty")
                                      : Component.translatable("mcvanillatweaks.hut.search_none");
            g.drawString(font, Gui.fit(font, empty, rw - 8), gridX + 4, top + 5, Gui.LABEL_SOFT, false);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (grid.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        final ItemStack stack = button == 0 ? grid.itemAt(mouseX, mouseY) : null;
        if (stack == null)
        {
            return false;
        }
        Gui.clickSound();
        locate(stack);
        return true;
    }

    /**
     * MineColonies' "locate" (its all-inventory window): every container holding the item is outlined in the world for
     * a minute with the amount it holds, from red (a few) to green (32 or more), and the screen closes to show them.
     */
    private void locate(final ItemStack item)
    {
        final ClientLevel level = mc.level;
        if (level == null || mc.player == null)
        {
            return;
        }
        HighlightManager.clearHighlightsForKey(HIGHLIGHT_KEY);
        for (final BlockPos pos : containers(building(), module == null))
        {
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof AbstractTileEntityRack rack))
            {
                continue;
            }
            int count = 0;
            final IItemHandler inventory = rack.getInventory();
            for (int slot = 0; slot < inventory.getSlots(); slot++)
            {
                final ItemStack stack = inventory.getStackInSlot(slot);
                if (ItemStack.isSameItemSameComponents(stack, item))
                {
                    count += stack.getCount();
                }
            }
            if (count > 0)
            {
                final int colour = 0x80000000 | Mth.clamp((int) (255.0F * (2.0F - count / 32.0F)), 0, 255) << 16 | Mth.clamp(255 * count / 32, 0, 255) << 8;
                HighlightManager.addHighlight(HIGHLIGHT_KEY, pos.toString(),
                  new TimedBoxRenderData(pos).setDuration(Duration.ofSeconds(60)).addText(Integer.toString(count)).setColor(colour));
            }
        }
        MessageUtils.format("com.minecolonies.coremod.locating").sendTo(mc.player);
        screen.onClose();
    }

    @Override
    protected boolean mouseDragged(final double mouseX, final double mouseY)
    {
        return grid.mouseDragged(mouseY);
    }

    @Override
    protected void mouseReleased()
    {
        grid.mouseReleased();
    }

    @Override
    protected boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        return grid.mouseScrolled(mouseX, mouseY, delta);
    }
}
