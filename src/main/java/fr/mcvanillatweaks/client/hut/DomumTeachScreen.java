package fr.mcvanillatweaks.client.hut;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.block.MateriallyTexturedBlockManager;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.recipe.ModRecipeTypes;
import com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipe;
import com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipeInput;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.crafting.RecipeStorage;
import com.minecolonies.api.util.OptionalPredicate;
import com.minecolonies.core.colony.buildings.moduleviews.DOCraftingModuleView;
import com.minecolonies.core.network.messages.server.colony.building.worker.AddRemoveRecipeMessage;
import com.minecolonies.core.util.DomumOrnamentumUtils;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.IconButton;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import fr.mcvanillatweaks.client.domum.DomumMaterials;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Replaces MineColonies' {@code DOCraftingWindow}: teaching an Architect's Cutter recipe to a Domum Ornamentum crafter
 * (sawmill, stonemason...). Same frame as the {@link TeachScreen}s:
 * <pre>
 * Waiting requests       Teach a recipe                 Recipes: 3 / 10
 * | (o) Oak shingle x32|  Sawmill · Architect's Cutter
 * |     Frame: Oak ... |  [slot] Material 1: Oak Planks
 * |                    |  [slot] Material 2: (click to choose)
 * |                    |  Blocks it makes (12)
 * |                    |  [][][][][][][][]
 * [Hut's recipes      ]  [Teach: Oak shingle            ]
 * </pre>
 * A material slot opens a picker (search + grid) of the items MineColonies offers for that slot (its
 * {@code WindowSelectRes} list); materials the building doesn't work with (its ingredient validator) are marked. The
 * left list holds the colony's Domum block requests this building can make (MineColonies' request button); a click
 * puts their materials in place. "Teach" builds the same multi-output recipe as MineColonies: the first block made of
 * these materials as result, the others as alternatives.
 */
final class DomumTeachScreen extends Screen
{
    private static final int SIDE = TeachScreen.SIDE;
    private static final int TOP = TeachScreen.TOP;
    private static final int MENU_WIDTH = 176;
    private static final int FRAME_WIDTH = SIDE + MENU_WIDTH;
    private static final int FRAME_HEIGHT = TOP + 190;
    private static final int ROW = 24;
    private static final int MATERIAL_ROW = 22;
    private static final int BUTTON_HEIGHT = 20;
    private static final int COLUMNS = 8;
    private static final int LEARNED_TICKS = 60;

    /** Dev preview: materials in place and the blocks they make, there being no level. */
    @Nullable
    static List<ItemStack> previewMaterials;
    @Nullable
    static List<ItemStack> previewResults;
    /** Dev preview: the picker open on this slot, with this search. */
    static int previewPicker = -1;
    /** Dev preview: the picker's items and the requests, there being no colony. */
    @Nullable
    static List<ItemStack> previewCandidates;
    @Nullable
    static List<ItemStack> previewRequests;

    private final IBuildingView building;
    private final DOCraftingModuleView module;
    private final SimpleContainer inputs;
    private final Tooltip tooltip = new Tooltip();
    private final ScrollArea list = new ScrollArea();
    private final ItemGrid resultGrid = new ItemGrid();
    private final ItemGrid pickerGrid = new ItemGrid();
    private List<Wanted> requests = List.of();
    private List<ItemStack> results = List.of();
    /** Material slot being chosen, or -1. */
    private int picking = -1;
    private String filter = "";
    private List<ItemStack> candidates = List.of();
    private Button learn;
    private int frameLeft;
    private int frameTop;
    private int left;
    private int ticks;
    private int countBefore = -1;
    private int learnedUntil;

    private record Wanted(ItemStack stack, int count, Component requester)
    {
    }

    DomumTeachScreen(final DOCraftingModuleView module)
    {
        super(Component.translatable("mcvanillatweaks.teach.title"));
        this.module = module;
        this.building = module.getBuildingView();
        this.inputs = new SimpleContainer(MateriallyTexturedBlockManager.getInstance().getMaxTexturableComponentCount());
        if (previewMaterials != null)
        {
            for (int i = 0; i < previewMaterials.size() && i < inputs.getContainerSize(); i++)
            {
                inputs.setItem(i, previewMaterials.get(i));
            }
        }
        picking = previewPicker;
        updateResults();
        refreshRequests();
    }

    // ------------------------------------------------------------------ MineColonies' logic

    private int inputCount()
    {
        int count = 0;
        for (int i = 0; i < inputs.getContainerSize(); i++)
        {
            if (!inputs.getItem(i).isEmpty())
            {
                count++;
            }
        }
        return count;
    }

    /** The blocks the cutter makes from these materials, keeping those using exactly as many materials (updateStockList). */
    private void updateResults()
    {
        if (previewResults != null)
        {
            results = previewResults;
            resultGrid.setItems(results);
            return;
        }
        final List<ItemStack> found = new ArrayList<>();
        final Level level = minecraft().level;
        if (level != null && !inputs.isEmpty())
        {
            final ArchitectsCutterRecipeInput input = new ArchitectsCutterRecipeInput(inputs);
            final int count = inputCount();
            for (final RecipeHolder<ArchitectsCutterRecipe> recipe : level.getRecipeManager().getRecipesFor(ModRecipeTypes.ARCHITECTS_CUTTER.get(), input, level))
            {
                final ItemStack result = recipe.value().assemble(input, level.registryAccess()).copy();
                final IMateriallyTexturedBlock block = DomumOrnamentumUtils.getBlock(result);
                if (block != null && block.getComponents().size() == count)
                {
                    found.add(result);
                }
            }
        }
        results = found;
        resultGrid.setItems(results);
        resultGrid.scrollToTop();
    }

    private net.minecraft.client.Minecraft minecraft()
    {
        return net.minecraft.client.Minecraft.getInstance();
    }

    private List<ItemStorage> recipeInputs()
    {
        final List<ItemStorage> list = new ArrayList<>();
        for (int i = 0; i < inputs.getContainerSize(); i++)
        {
            final ItemStack stack = inputs.getItem(i).copy();
            if (!stack.isEmpty())
            {
                stack.setCount(1);
                list.add(new ItemStorage(stack));
            }
        }
        return list;
    }

    /** MineColonies' addRecipe: the first block as result, the others as alternatives, a 3x3 multi-output recipe. */
    private void teach()
    {
        if (!canTeach())
        {
            return;
        }
        final IRecipeStorage storage = RecipeStorage.builder()
                                         .withInputs(recipeInputs())
                                         .withPrimaryOutput(results.get(0).copy())
                                         .withAlternateOutputs(results.subList(1, results.size()).stream().map(ItemStack::copy).toList())
                                         .withGridSize(3)
                                         .withRecipeType(com.minecolonies.api.crafting.ModRecipeTypes.MULTI_OUTPUT_ID)
                                         .build();
        countBefore = module.getRecipes().size();
        new AddRemoveRecipeMessage(building, false, storage, module.getProducer().getRuntimeID()).sendToServer();
    }

    private boolean canTeach()
    {
        return module.canLearn(com.minecolonies.api.crafting.ModCraftingTypes.ARCHITECTS_CUTTER.get()) && !results.isEmpty() && !alreadyKnown()
                 && ticks >= learnedUntil;
    }

    private boolean alreadyKnown()
    {
        if (results.isEmpty())
        {
            return false;
        }
        final Set<Item> placed = new HashSet<>();
        for (final ItemStorage input : recipeInputs())
        {
            placed.add(input.getItem());
        }
        for (final IRecipeStorage recipe : module.getRecipes())
        {
            if (!ItemStack.isSameItemSameComponents(recipe.getPrimaryOutput(), results.get(0)))
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

    /** True / false when the building's validator decides, empty when it has no opinion. */
    private Optional<Boolean> worked(final ItemStack material)
    {
        try
        {
            final OptionalPredicate<ItemStack> validator = module.getIngredientValidator();
            return validator == null ? Optional.empty() : validator.test(material);
        }
        catch (final RuntimeException e)
        {
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------ requests

    /** Domum blocks waiting for the player that this building works with (MineColonies' matchingRequest). */
    private void refreshRequests()
    {
        final List<Wanted> found = new ArrayList<>();
        if (previewRequests != null)
        {
            for (final ItemStack stack : previewRequests)
            {
                found.add(new Wanted(stack.copyWithCount(1), stack.getCount(), Component.literal("Builder's Hut")));
            }
            requests = found;
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
                    final ItemStack stack = DomumOrnamentumUtils.getRequestedStack(request);
                    if (stack.isEmpty() || !matches(stack) || found.stream().anyMatch(w -> ItemStack.isSameItemSameComponents(w.stack(), stack)))
                    {
                        continue;
                    }
                    final int count = request.getRequest() instanceof IDeliverable deliverable ? deliverable.getCount() : stack.getCount();
                    found.add(new Wanted(stack.copyWithCount(1), count, requester(manager, request)));
                }
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            MineColoniesVanillaTweaks.LOGGER.debug("Domum teaching requests unavailable", e);
        }
        requests = found;
    }

    private boolean matches(final ItemStack stack)
    {
        final MaterialTextureData data = MaterialTextureData.readFromItemStack(stack);
        if (data.isEmpty())
        {
            return false;
        }
        for (final Block block : data.getTexturedComponents().values())
        {
            if (worked(new ItemStack(block)).orElse(false))
            {
                return true;
            }
        }
        return false;
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

    /** MineColonies' reopenWithRequest: the requested block's materials, in its components' order. */
    private void use(final Wanted wanted)
    {
        final IMateriallyTexturedBlock block = DomumOrnamentumUtils.getBlock(wanted.stack());
        final MaterialTextureData data = MaterialTextureData.readFromItemStack(wanted.stack());
        if (block == null || data.isEmpty())
        {
            return;
        }
        int slot = 0;
        for (final IMateriallyTexturedBlockComponent component : block.getComponents())
        {
            if (slot < inputs.getContainerSize())
            {
                inputs.setItem(slot++, new ItemStack(data.getTexturedComponents().getOrDefault(component.getId(), Blocks.AIR)));
            }
        }
        while (slot < inputs.getContainerSize())
        {
            inputs.setItem(slot++, ItemStack.EMPTY);
        }
        picking = -1;
        rebuild();
        updateResults();
    }

    // ------------------------------------------------------------------ picker

    /** MineColonies' list for a slot (every known item holding a material for it), worked materials first. */
    private void openPicker(final int slot)
    {
        picking = slot;
        filter = "";
        rebuild();
    }

    private void updateCandidates()
    {
        final List<ItemStack> all = previewCandidates != null ? previewCandidates : slotItems(picking);
        if (all == null)
        {
            candidates = List.of();
            pickerGrid.setItems(candidates);
            return;
        }
        final String search = filter.toLowerCase(Locale.ROOT).trim();
        final List<ItemStack> shown = new ArrayList<>();
        for (final ItemStack stack : all)
        {
            if (search.isEmpty() || stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(search))
            {
                shown.add(stack);
            }
        }
        shown.sort(Comparator.comparingInt((ItemStack stack) -> worked(stack).orElse(true) ? 0 : 1)
                     .thenComparing(stack -> stack.getHoverName().getString()));
        candidates = shown;
        pickerGrid.setItems(candidates);
        pickerGrid.scrollToTop();
    }

    @Nullable
    private static List<ItemStack> slotItems(final int slot)
    {
        try
        {
            return IColonyManager.getInstance().getCompatibilityManager()
                     .getListOfMatchingItems(stack -> MateriallyTexturedBlockManager.getInstance().doesItemStackContainsMaterialForSlot(slot, stack));
        }
        catch (final LinkageError | RuntimeException e)
        {
            MineColoniesVanillaTweaks.LOGGER.debug("Domum materials list unavailable", e);
            return null;
        }
    }

    private void pick(final ItemStack stack)
    {
        Gui.clickSound();
        inputs.setItem(picking, stack.copyWithCount(1));
        picking = -1;
        rebuild();
        updateResults();
    }

    private void rebuild()
    {
        if (minecraft != null)
        {
            rebuildWidgets();
        }
    }

    // ------------------------------------------------------------------ screen

    @Override
    protected void init()
    {
        frameLeft = (width - FRAME_WIDTH) / 2;
        frameTop = (height - FRAME_HEIGHT) / 2;
        left = frameLeft + SIDE;
        final int buttonY = frameTop + TOP + 164;
        addRenderableWidget(new IconButton(frameLeft + 8, buttonY, SIDE - 16, BUTTON_HEIGHT, HutScreen.moduleIcon(module),
          Component.translatable("mcvanillatweaks.teach.back"), b -> back()));
        if (picking >= 0)
        {
            final EditBox search = addRenderableWidget(new EditBox(font, left + 9, frameTop + TOP + 15, MENU_WIDTH - 18, 16,
              Component.translatable("mcvanillatweaks.hut.search")));
            search.setHint(Component.translatable("mcvanillatweaks.hut.search").withStyle(ChatFormatting.DARK_GRAY));
            search.setMaxLength(50);
            search.setValue(filter);
            search.setResponder(text -> {
                filter = text;
                updateCandidates();
            });
            setInitialFocus(search);
            addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> {
                picking = -1;
                rebuild();
            }).bounds(left + 8, buttonY, MENU_WIDTH - 16, BUTTON_HEIGHT).build());
            updateCandidates();
            pickerGrid.set(left + 8, frameTop + TOP + 36, COLUMNS, 7);
            pickerGrid.setCounts(null, stack -> worked(stack).orElse(true)
                                                  ? List.of(Component.translatable("mcvanillatweaks.teach.domum.pick_hint").withStyle(ChatFormatting.YELLOW))
                                                  : List.of(Component.translatable("mcvanillatweaks.teach.domum.not_worked").withStyle(ChatFormatting.RED)));
            return;
        }
        learn = addRenderableWidget(Button.builder(Component.empty(), b -> teach()).bounds(left + 8, buttonY, MENU_WIDTH - 16, BUTTON_HEIGHT).build());
        resultGrid.set(left + 8, resultsTop() + 12, COLUMNS, 4);
        resultGrid.setCounts(ItemStack::getCount, null);
        updateLearn();
    }

    private int materialsTop()
    {
        return frameTop + TOP + 4;
    }

    private int resultsTop()
    {
        return materialsTop() + inputs.getContainerSize() * MATERIAL_ROW + 4;
    }

    private void back()
    {
        try
        {
            module.getWindow().open();
        }
        catch (final LinkageError | RuntimeException e)
        {
            MineColoniesVanillaTweaks.LOGGER.debug("Recipes page unavailable", e);
            onClose();
        }
    }

    private void updateLearn()
    {
        if (learn == null)
        {
            return;
        }
        final Component label;
        Component hint = null;
        if (ticks < learnedUntil)
        {
            label = Component.translatable("mcvanillatweaks.teach.learned").withStyle(ChatFormatting.GREEN);
        }
        else if (!module.canLearn(com.minecolonies.api.crafting.ModCraftingTypes.ARCHITECTS_CUTTER.get()))
        {
            label = Component.translatable("mcvanillatweaks.teach.full", module.getRecipes().size(), module.getMaxRecipes());
            hint = Component.translatable("mcvanillatweaks.teach.full_hint");
        }
        else if (results.isEmpty())
        {
            label = Component.translatable(inputs.isEmpty() ? "mcvanillatweaks.teach.domum.choose" : "mcvanillatweaks.teach.domum.nothing");
        }
        else if (alreadyKnown())
        {
            label = Component.translatable("mcvanillatweaks.teach.known");
        }
        else
        {
            label = Component.translatable("mcvanillatweaks.teach.learn", results.get(0).getHoverName());
            hint = Component.translatable("mcvanillatweaks.teach.domum.learn_hint", results.size());
        }
        learn.setMessage(label);
        learn.active = canTeach();
        learn.setTooltip(hint == null ? null : net.minecraft.client.gui.components.Tooltip.create(hint));
    }

    @Override
    public void tick()
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

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        tooltip.clear();
        renderBackground(g, mouseX, mouseY, partialTick);
        Gui.panel(g, frameLeft, frameTop, FRAME_WIDTH, FRAME_HEIGHT);
        renderHeader(g);
        renderRequests(g, mouseX, mouseY);
        if (picking >= 0)
        {
            renderPicker(g, mouseX, mouseY);
        }
        else
        {
            renderMaterials(g, mouseX, mouseY);
            renderResults(g, mouseX, mouseY);
        }
        for (final var child : renderables)
        {
            child.render(g, mouseX, mouseY, partialTick);
        }
        tooltip.render(g, font, mouseX, mouseY);
    }

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        renderTransparentBackground(g);
    }

    private void renderHeader(final GuiGraphics g)
    {
        final int x = left + 8;
        final int right = left + MENU_WIDTH - 8;
        final Component count = Component.translatable("mcvanillatweaks.teach.count", module.getRecipes().size(), module.getMaxRecipes());
        final int countWidth = font.width(count);
        g.drawString(font, Gui.fit(font, title, right - countWidth - 6 - x), x, frameTop + 6, Gui.LABEL, false);
        g.drawString(font, count, right - countWidth, frameTop + 6,
          module.canLearn(com.minecolonies.api.crafting.ModCraftingTypes.ARCHITECTS_CUTTER.get()) ? Gui.LABEL_SOFT : 0xFFAA0000, false);
        final Component sub = Component.translatable(building.getBuildingDisplayName()).append(" · ").append(Component.translatable("mcvanillatweaks.teach.station.cutter"));
        g.drawString(font, Gui.fit(font, sub, right - x), x, frameTop + 17, Gui.LABEL_SOFT, false);
    }

    private void renderRequests(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        g.drawString(font, Component.translatable("mcvanillatweaks.teach.requests"), frameLeft + 9, frameTop + 6, Gui.LABEL, false);
        final int top = frameTop + 18;
        list.set(frameLeft + 8, top, SIDE - 12, frameTop + TOP + 158 - top, requests.size() * ROW, ROW);
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
        for (int i = 0; i < requests.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final Wanted wanted = requests.get(i);
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            g.renderItem(wanted.stack(), list.listX() + 3, rowY + 4);
            final int textX = list.listX() + 23;
            final int textWidth = list.listWidth() - 27;
            final boolean inPlace = results.stream().anyMatch(r -> ItemStack.isSameItemSameComponents(r.copyWithCount(1), wanted.stack()));
            g.drawString(font, Gui.fit(font, wanted.stack().getHoverName(), textWidth), textX, rowY + 3, inPlace ? Gui.GREEN : Gui.WHITE, true);
            final Component materials = DomumMaterials.describe(wanted.stack());
            final Component detail = materials != null ? materials : Component.translatable("mcvanillatweaks.teach.amount", wanted.count());
            g.drawString(font, Gui.fit(font, detail, textWidth), textX, rowY + 13, Gui.LIST_SOFT, true);
            if (hovered)
            {
                final List<Component> lines = new ArrayList<>();
                lines.add(wanted.stack().getHoverName());
                lines.add(Component.translatable("mcvanillatweaks.teach.amount", wanted.count()).withStyle(ChatFormatting.GRAY));
                if (materials != null)
                {
                    lines.add(materials.copy().withStyle(ChatFormatting.GRAY));
                }
                lines.add(Component.translatable("mcvanillatweaks.teach.requested_by", wanted.requester()).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable(inPlace ? "mcvanillatweaks.teach.domum.in_place" : "mcvanillatweaks.teach.domum.use_hint")
                            .withStyle(inPlace ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
                tooltip.setAround(lines, mouseX, rowY, rowY + ROW);
            }
        }
        list.end(g);
    }

    private void renderMaterials(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        for (int i = 0; i < inputs.getContainerSize(); i++)
        {
            final int sx = left + 8;
            final int sy = materialsTop() + i * MATERIAL_ROW;
            Gui.slot(g, sx, sy);
            final ItemStack stack = inputs.getItem(i);
            final boolean over = Gui.inside(mouseX, mouseY, sx, sy, MENU_WIDTH - 16, 18);
            final Component label;
            final int colour;
            if (stack.isEmpty())
            {
                label = Component.translatable("mcvanillatweaks.teach.domum.material_empty", i + 1);
                colour = Gui.LABEL_SOFT;
            }
            else
            {
                g.renderItem(stack, sx + 1, sy + 1);
                label = Component.translatable("mcvanillatweaks.teach.domum.material", i + 1, stack.getHoverName());
                colour = worked(stack).orElse(true) ? Gui.LABEL : 0xFFAA0000;
            }
            if (over)
            {
                Gui.slotHighlight(g, sx, sy);
                final List<Component> lines = new ArrayList<>();
                if (!stack.isEmpty())
                {
                    lines.add(stack.getHoverName());
                    if (!worked(stack).orElse(true))
                    {
                        lines.add(Component.translatable("mcvanillatweaks.teach.domum.not_worked").withStyle(ChatFormatting.RED));
                    }
                }
                lines.add(Component.translatable("mcvanillatweaks.teach.domum.slot_hint").withStyle(ChatFormatting.YELLOW));
                if (!stack.isEmpty())
                {
                    lines.add(Component.translatable("mcvanillatweaks.teach.domum.clear_hint").withStyle(ChatFormatting.GRAY));
                }
                tooltip.set(lines);
            }
            g.drawString(font, Gui.fit(font, label, MENU_WIDTH - 16 - 24), sx + 23, sy + 5, colour, false);
        }
    }

    private void renderResults(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        final Component heading = results.isEmpty()
                                    ? Component.translatable("mcvanillatweaks.teach.domum.results_none")
                                    : Component.translatable("mcvanillatweaks.teach.domum.results", results.size());
        g.drawString(font, Gui.fit(font, heading, MENU_WIDTH - 16), left + 9, resultsTop() + 1, Gui.LABEL, false);
        resultGrid.render(g, font, mouseX, mouseY, tooltip, stack -> !results.isEmpty() && stack == results.get(0));
    }

    private void renderPicker(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        g.drawString(font, Component.translatable("mcvanillatweaks.teach.domum.pick", picking + 1), left + 9, frameTop + TOP + 4, Gui.LABEL, false);
        pickerGrid.render(g, font, mouseX, mouseY, tooltip, stack -> false);
        // Materials the building doesn't work with are dimmed.
        final List<ItemStack> items = pickerGrid.items();
        if (candidates.isEmpty())
        {
            g.drawString(font, Component.translatable("mcvanillatweaks.hut.search_none"), left + 12, frameTop + TOP + 41, Gui.LABEL_SOFT, false);
        }
        for (int i = 0; i < items.size(); i++)
        {
            if (!worked(items.get(i)).orElse(true))
            {
                final int[] pos = pickerGrid.slotPos(i);
                if (pos != null)
                {
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 300);
                    g.fill(pos[0] + 1, pos[1] + 1, pos[0] + 17, pos[1] + 17, 0x90000000);
                    g.pose().popPose();
                }
            }
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (super.mouseClicked(mouseX, mouseY, button))
        {
            return true;
        }
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
                use(requests.get(index));
                return true;
            }
        }
        if (picking >= 0)
        {
            if (pickerGrid.mouseClicked(mouseX, mouseY))
            {
                return true;
            }
            final ItemStack stack = pickerGrid.itemAt(mouseX, mouseY);
            if (button == 0 && stack != null && !stack.isEmpty())
            {
                pick(stack);
                return true;
            }
            return false;
        }
        for (int i = 0; i < inputs.getContainerSize(); i++)
        {
            if (Gui.inside(mouseX, mouseY, left + 8, materialsTop() + i * MATERIAL_ROW, MENU_WIDTH - 16, 18))
            {
                Gui.clickSound();
                if (button == 1)
                {
                    inputs.setItem(i, ItemStack.EMPTY);
                    updateResults();
                }
                else
                {
                    openPicker(i);
                }
                return true;
            }
        }
        return resultGrid.mouseClicked(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY)
    {
        if (list.mouseDragged(mouseY))
        {
            return true;
        }
        if (picking >= 0 ? pickerGrid.mouseDragged(mouseY) : resultGrid.mouseDragged(mouseY))
        {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button)
    {
        list.mouseReleased();
        pickerGrid.mouseReleased();
        resultGrid.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        if (list.mouseScrolled(mouseX, mouseY, scrollY))
        {
            return true;
        }
        if (picking >= 0 ? pickerGrid.mouseScrolled(mouseX, mouseY, scrollY) : resultGrid.mouseScrolled(mouseX, mouseY, scrollY))
        {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers)
    {
        if (picking >= 0 && key == GLFW.GLFW_KEY_ESCAPE)
        {
            picking = -1;
            rebuild();
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
