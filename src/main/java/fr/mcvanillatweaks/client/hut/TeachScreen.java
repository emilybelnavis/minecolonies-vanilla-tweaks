package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IConcreteDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.crafting.registry.CraftingType;
import com.minecolonies.core.colony.buildings.moduleviews.CraftingModuleView;
import com.minecolonies.core.util.DomumOrnamentumUtils;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.IconButton;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared frame of the recipe-teaching screens, which replace MineColonies' {@code WindowCrafting},
 * {@code WindowFurnaceCrafting} and {@code WindowBrewingstandCrafting} on the same menus. The menus' slots are placed by
 * the server like the vanilla crafting table, furnace and brewing stand (176 x 166, player inventory from (8, 84)), so
 * the menu is drawn {@link #SIDE} px right and {@link #TOP} px down inside a larger frame:
 * <pre>
 * Waiting requests       Teach a recipe                 Recipes: 3 / 10
 * | (o) Bread x4      |  Baker · Crafting table 2x2
 * |     Builder's hut |    [][]  ->  [result]  [1/2]
 * | (o) Cake          |    [][]
 * |                   |  Inventory
 * |                   |  [][][][][][][][][]
 * [Hut's recipes     ]  [Teach: Bread              ]
 * </pre>
 * The left list holds the colony's requests waiting for the player (MineColonies' request button, which needs JEI)
 * that this station can make; a click shows the recipe as ghost items in the empty slots. "Teach" sends MineColonies'
 * {@code AddRemoveRecipeMessage} like its windows, and says why it can't (no room left, recipe already known, nothing
 * placed). "Hut's recipes" closes the menu and opens the recipes page.
 */
abstract class TeachScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M>
{
    static final int SIDE = 150;
    static final int TOP = 30;
    private static final int MENU_WIDTH = 176;
    private static final int MENU_HEIGHT = 166;
    private static final int FRAME_WIDTH = SIDE + MENU_WIDTH;
    private static final int FRAME_HEIGHT = TOP + 190;
    private static final int ROW = 24;
    private static final int BUTTON_HEIGHT = 20;
    private static final int LEARNED_TICKS = 60;

    /** Dev preview: requested items shown instead of the colony's, each with its ghost. */
    @Nullable
    static List<ItemStack> previewRequests;
    /** Dev preview: index of the request shown as ghost items. */
    static int previewSelected = -1;
    /** Dev preview: the ghost of the selected request, there being no level to look recipes up in. */
    @Nullable
    static Ghost previewGhost;

    protected final IBuildingView building;
    protected final CraftingModuleView module;
    private final Tooltip tooltip = new Tooltip();
    private final ScrollArea list = new ScrollArea();
    /** Ghost per requested item, null when this station can't make it ({@link #ghostFor} is slow-ish). */
    private final Map<Item, Ghost> ghosts = new HashMap<>();
    private List<Wanted> requests = List.of();
    @Nullable
    private Wanted selected;
    private Button learn;
    private int frameLeft;
    private int frameTop;
    private int ticks;
    /** Recipe count when "teach" was pressed, to say when the server has added it. */
    private int countBefore = -1;
    private int learnedUntil;

    /** A requested item this station can make. */
    protected record Wanted(ItemStack stack, int count, Component requester, Ghost ghost)
    {
    }

    /**
     * Ingredients of a recipe making a requested item, by menu slot index (several options cycle, like vanilla's recipe
     * book), and what it makes.
     */
    protected record Ghost(Map<Integer, List<ItemStack>> slots, ItemStack result)
    {
    }

    TeachScreen(final M menu, final Inventory inventory, final Component title, final IBuildingView building, final CraftingModuleView module)
    {
        super(menu, inventory, title);
        this.building = building;
        this.module = module;
        imageWidth = MENU_WIDTH;
        imageHeight = MENU_HEIGHT;
    }

    // ------------------------------------------------------------------ what each station provides

    /** Kind of recipe MineColonies checks before learning ({@code canLearn}). */
    protected abstract CraftingType craftingType();

    /** Station name for the header ("Crafting table 3x3"). */
    protected abstract Component stationName();

    /** What the slots make now, empty if nothing. */
    protected abstract ItemStack result();

    /** Ingredients placed, one per stack. */
    protected abstract List<ItemStack> inputs();

    /** Sends MineColonies' message that teaches the recipe in place. */
    protected abstract void send();

    /** Draws the station's slots, arrow and result frame (menu coordinates are relative to leftPos/topPos). */
    protected abstract void renderStation(GuiGraphics g, int mouseX, int mouseY);

    /** The recipe making {@code wanted} here, or null if this station can't make it. */
    @Nullable
    protected abstract Ghost ghostFor(ItemStack wanted);

    /** Where a ghost result is drawn (the result slot's top-left, menu coordinates), or null for none. */
    @Nullable
    protected abstract int[] resultPos();

    /** Extra widgets of the station (the crafting table's "other result" button). */
    protected void initStation()
    {
    }

    /** Index of the first player inventory slot in the menu. */
    protected abstract int firstInventorySlot();

    // ------------------------------------------------------------------ screen

    @Override
    protected void init()
    {
        super.init();
        frameLeft = (width - FRAME_WIDTH) / 2;
        frameTop = (height - FRAME_HEIGHT) / 2;
        leftPos = frameLeft + SIDE;
        topPos = frameTop + TOP;
        inventoryLabelY = 73;

        final int buttonY = topPos + MENU_HEIGHT - 2;
        addRenderableWidget(new IconButton(frameLeft + 8, buttonY, SIDE - 16, BUTTON_HEIGHT, HutScreen.moduleIcon(module),
          Component.translatable("mcvanillatweaks.teach.back"), b -> back()));
        learn = addRenderableWidget(Button.builder(Component.empty(), b -> teach()).bounds(leftPos + 8, buttonY, MENU_WIDTH - 16, BUTTON_HEIGHT).build());
        initStation();
        refreshRequests();
        updateLearn();
    }

    private void back()
    {
        if (minecraft != null && minecraft.player != null)
        {
            minecraft.player.closeContainer();
        }
        try
        {
            module.getWindow().open();
        }
        catch (final LinkageError | RuntimeException e)
        {
            MineColoniesVanillaTweaks.LOGGER.debug("Recipes page unavailable", e);
        }
    }

    private void teach()
    {
        if (!canTeach())
        {
            return;
        }
        countBefore = module.getRecipes().size();
        send();
    }

    private boolean canTeach()
    {
        return module.canLearn(craftingType()) && !result().isEmpty() && !alreadyKnown() && ticks >= learnedUntil;
    }

    /** MineColonies refuses a recipe it already has: same result and same ingredients. */
    private boolean alreadyKnown()
    {
        final ItemStack result = result();
        if (result.isEmpty())
        {
            return false;
        }
        final Set<Item> placed = new HashSet<>();
        for (final ItemStack input : inputs())
        {
            placed.add(input.getItem());
        }
        for (final IRecipeStorage recipe : module.getRecipes())
        {
            if (!ItemStack.isSameItemSameComponents(recipe.getPrimaryOutput(), result))
            {
                continue;
            }
            final Set<Item> known = new HashSet<>();
            for (final ItemStorage input : recipe.getCleanedInput())
            {
                known.add(input.getItem());
            }
            if (known.equals(placed))
            {
                return true;
            }
        }
        return false;
    }

    private void updateLearn()
    {
        final int count = module.getRecipes().size();
        final int max = module.getMaxRecipes();
        final ItemStack result = result();
        final Component label;
        Component hint = null;
        if (ticks < learnedUntil)
        {
            label = Component.translatable("mcvanillatweaks.teach.learned").withStyle(ChatFormatting.GREEN);
        }
        else if (!module.canLearn(craftingType()))
        {
            label = Component.translatable("mcvanillatweaks.teach.full", count, max);
            hint = Component.translatable("mcvanillatweaks.teach.full_hint");
        }
        else if (result.isEmpty())
        {
            label = Component.translatable("mcvanillatweaks.teach.place");
        }
        else if (alreadyKnown())
        {
            label = Component.translatable("mcvanillatweaks.teach.known");
        }
        else
        {
            label = Component.translatable("mcvanillatweaks.teach.learn", result.getHoverName());
            hint = Component.translatable("mcvanillatweaks.teach.learn_hint", result.getCount(), result.getHoverName());
        }
        learn.setMessage(label);
        learn.active = canTeach();
        learn.setTooltip(hint == null ? null : net.minecraft.client.gui.components.Tooltip.create(hint));
    }

    @Override
    protected void containerTick()
    {
        ticks++;
        if (countBefore >= 0 && module.getRecipes().size() > countBefore)
        {
            countBefore = -1;
            learnedUntil = ticks + LEARNED_TICKS;
        }
        if (ticks % 40 == 0)
        {
            refreshRequests();
        }
        updateLearn();
    }

    // ------------------------------------------------------------------ requests

    /**
     * Requests waiting for the player (or retried), with their parents, that ask for items this station can make: the
     * list of MineColonies' request button ({@code WindowSelectRequest}), without Domum Ornamentum blocks.
     */
    private void refreshRequests()
    {
        final List<Wanted> found = new ArrayList<>();
        if (previewRequests != null)
        {
            for (final ItemStack stack : previewRequests)
            {
                final Ghost ghost = found.size() == previewSelected && previewGhost != null ? previewGhost : new Ghost(Map.of(), stack);
                found.add(new Wanted(stack.copyWithCount(1), stack.getCount(), Component.literal("Builder's Hut"), ghost));
            }
            requests = found;
            if (selected == null && previewSelected >= 0 && previewSelected < found.size())
            {
                selected = found.get(previewSelected);
            }
            return;
        }
        try
        {
            final IRequestManager manager = building.getColony().getRequestManager();
            final Set<IToken<?>> tokens = new HashSet<>();
            tokens.addAll(manager.getPlayerResolver().getAllAssignedRequests());
            tokens.addAll(manager.getRetryingRequestResolver().getAllAssignedRequests());
            final Set<IToken<?>> seen = new HashSet<>();
            for (final IToken<?> token : tokens)
            {
                for (IRequest<?> request = manager.getRequestForToken(token); request != null && seen.add(request.getId());
                     request = request.hasParent() ? manager.getRequestForToken(request.getParent()) : null)
                {
                    if (!(request.getRequest() instanceof IConcreteDeliverable deliverable) || !DomumOrnamentumUtils.getRequestedStack(request).isEmpty())
                    {
                        continue;
                    }
                    for (final ItemStack stack : deliverable.getRequestedItems())
                    {
                        if (!ghosts.containsKey(stack.getItem()))
                        {
                            ghosts.put(stack.getItem(), ghostFor(stack));
                        }
                        final Ghost ghost = ghosts.get(stack.getItem());
                        if (ghost != null && found.stream().noneMatch(w -> ItemStack.isSameItem(w.stack(), stack)))
                        {
                            found.add(new Wanted(stack.copyWithCount(1), deliverable.getCount(), requester(manager, request), ghost));
                        }
                    }
                }
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            MineColoniesVanillaTweaks.LOGGER.debug("Teaching screen requests unavailable", e);
        }
        requests = found;
        if (selected != null && found.stream().noneMatch(w -> ItemStack.isSameItem(w.stack(), selected.stack())))
        {
            selected = null;
        }
    }

    private static Component requester(final IRequestManager manager, final IRequest<?> request)
    {
        try
        {
            return request.getRequester().getRequesterDisplayName(manager, request);
        }
        catch (final RuntimeException e)
        {
            return Component.empty();
        }
    }

    /** Ghost items cycle every second, like vanilla's recipe book. */
    protected static ItemStack cycle(final List<ItemStack> options, final int ticks)
    {
        return options.isEmpty() ? ItemStack.EMPTY : options.get((ticks / 20) % options.size());
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY)
    {
        tooltip.clear();
        Gui.panel(g, frameLeft, frameTop, FRAME_WIDTH, FRAME_HEIGHT);
        renderHeader(g);
        renderRequests(g, mouseX, mouseY);
        renderStation(g, mouseX, mouseY);
        renderGhost(g, mouseX, mouseY);
        for (int i = firstInventorySlot(); i < menu.slots.size(); i++)
        {
            final Slot slot = menu.slots.get(i);
            Gui.slot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
    }

    private void renderHeader(final GuiGraphics g)
    {
        final int x = leftPos + 8;
        final Component count = Component.translatable("mcvanillatweaks.teach.count", module.getRecipes().size(), module.getMaxRecipes());
        final int countWidth = font.width(count);
        final int right = leftPos + MENU_WIDTH - 8;
        g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.teach.title"), right - countWidth - 6 - x), x, frameTop + 6, Gui.LABEL, false);
        g.drawString(font, count, right - countWidth, frameTop + 6, module.canLearn(craftingType()) ? Gui.LABEL_SOFT : 0xFFAA0000, false);
        final Component sub = Component.translatable(building.getBuildingDisplayName()).append(" · ").append(stationName());
        g.drawString(font, Gui.fit(font, sub, right - x), x, frameTop + 17, Gui.LABEL_SOFT, false);
    }

    private void renderRequests(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        g.drawString(font, Component.translatable("mcvanillatweaks.teach.requests"), frameLeft + 9, frameTop + 6, Gui.LABEL, false);
        final int top = frameTop + 18;
        list.set(frameLeft + 8, top, SIDE - 12, topPos + MENU_HEIGHT - 8 - top, requests.size() * ROW, ROW);
        list.begin(g);
        if (requests.isEmpty())
        {
            int ty = list.visibleTop() + 6;
            for (final var line : font.split(Component.translatable("mcvanillatweaks.teach.requests_none"), list.listWidth() - 8))
            {
                g.drawString(font, line, list.listX() + 4, ty, Gui.LIST_SOFT, true);
                ty += 10;
            }
        }
        final ItemStack result = result();
        for (int i = 0; i < requests.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final Wanted wanted = requests.get(i);
            final boolean isSelected = wanted == selected;
            if (isSelected)
            {
                g.fill(list.listX(), rowY, list.listX() + list.listWidth(), rowY + ROW, Gui.ROW_SELECTED);
            }
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            g.renderItem(wanted.stack(), list.listX() + 3, rowY + 4);
            final int textX = list.listX() + 23;
            final int textWidth = list.listWidth() - 27;
            final boolean matches = !result.isEmpty() && ItemStack.isSameItem(result, wanted.stack());
            final Component name = wanted.stack().getHoverName();
            g.drawString(font, Gui.fit(font, name, textWidth), textX, rowY + 3, matches ? Gui.GREEN : isSelected ? Gui.YELLOW : Gui.WHITE, true);
            final Component detail = Component.translatable("mcvanillatweaks.teach.amount", wanted.count()).append(" · ").append(wanted.requester());
            g.drawString(font, Gui.fit(font, detail, textWidth), textX, rowY + 13, Gui.LIST_SOFT, true);
            if (hovered)
            {
                final List<Component> lines = new ArrayList<>();
                lines.add(name);
                lines.add(Component.translatable("mcvanillatweaks.teach.requested_by", wanted.requester()).withStyle(ChatFormatting.GRAY));
                if (matches)
                {
                    lines.add(Component.translatable("mcvanillatweaks.teach.matches").withStyle(ChatFormatting.GREEN));
                }
                if (!wanted.ghost().slots().isEmpty())
                {
                    lines.add(Component.translatable(isSelected ? "mcvanillatweaks.teach.hide_hint" : "mcvanillatweaks.teach.show_hint").withStyle(ChatFormatting.YELLOW));
                }
                tooltip.setAround(lines, mouseX, rowY, rowY + ROW);
            }
        }
        list.end(g);
    }

    /** The selected request's recipe, drawn faded in the empty slots like vanilla's recipe book. */
    private void renderGhost(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        if (selected == null)
        {
            return;
        }
        for (final Map.Entry<Integer, List<ItemStack>> entry : selected.ghost().slots().entrySet())
        {
            if (entry.getKey() >= menu.slots.size())
            {
                continue;
            }
            final Slot slot = menu.slots.get(entry.getKey());
            if (slot.hasItem())
            {
                continue;
            }
            final ItemStack stack = cycle(entry.getValue(), ticks);
            ghost(g, stack, leftPos + slot.x, topPos + slot.y);
            if (!stack.isEmpty() && Gui.inside(mouseX, mouseY, leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18))
            {
                tooltip.set(stack, List.of(Component.translatable("mcvanillatweaks.teach.ghost_hint").withStyle(ChatFormatting.YELLOW)));
            }
        }
        final int[] pos = resultPos();
        if (pos != null && result().isEmpty())
        {
            ghost(g, selected.ghost().result(), leftPos + pos[0], topPos + pos[1]);
        }
    }

    private static void ghost(final GuiGraphics g, final ItemStack stack, final int x, final int y)
    {
        if (stack.isEmpty())
        {
            return;
        }
        g.renderFakeItem(stack, x, y);
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(x, y, x + 16, y + 16, 0x808B8B8B);
        g.pose().popPose();
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, Gui.LABEL, false);
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

    /** Adds the tooltip of a hand-drawn element of the station. */
    protected Tooltip tooltip()
    {
        return tooltip;
    }

    protected int ticks()
    {
        return ticks;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (list.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button == 0 && list.overRows(mouseX, mouseY))
        {
            final int index = (int) Math.floor((mouseY - list.listY()) / ROW);
            if (index >= 0 && index < requests.size())
            {
                Gui.clickSound();
                final Wanted wanted = requests.get(index);
                selected = wanted == selected ? null : wanted;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
    protected boolean hasClickedOutside(final double mouseX, final double mouseY, final int left, final int top, final int button)
    {
        return mouseX < frameLeft || mouseY < frameTop || mouseX >= frameLeft + FRAME_WIDTH || mouseY >= frameTop + FRAME_HEIGHT;
    }
}
