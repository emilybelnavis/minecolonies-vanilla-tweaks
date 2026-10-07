package fr.mcvanillatweaks.client.hut;

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.placement.AbstractBlueprintIterator;
import com.ldtteam.structurize.placement.BlockPlacementResult;
import com.ldtteam.structurize.placement.StructurePhasePlacementResult;
import com.ldtteam.structurize.placement.StructurePlacer;
import com.ldtteam.structurize.storage.ClientFutureProcessor;
import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.LoadOnlyStructureHandler;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingBuilderView;
import com.minecolonies.core.network.messages.server.colony.building.BuildPickUpMessage;
import com.minecolonies.core.network.messages.server.colony.building.BuildRequestMessage;
import com.minecolonies.core.network.messages.server.colony.building.BuildingSetStyleMessage;
import fr.mcvanillatweaks.client.citizen.Dropdown;
import fr.mcvanillatweaks.client.citizen.DropdownButton;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Replaces MineColonies' build options window ({@code WindowBuildBuilding}), opened by the "build options" button of
 * every hut and of the town hall. It reproduces its logic: the style is the hut's (or its parent's) structure pack, the
 * builder is "any" or one of the colony's builders (closest first, miners left out), the materials are those of the next
 * level's blueprint, read from Structurize like MineColonies does, and the buttons send the same messages
 * ({@code BuildingSetStyleMessage} + {@code BuildRequestMessage} BUILD, or REPAIR at the maximum level; REPAIR; REMOVE;
 * {@code BuildPickUpMessage}). Upgrading asks for confirmation when the hut has a warning for that level, like
 * MineColonies' confirm window; removing asks for a second click, which MineColonies doesn't. Before an upgrade (or the
 * first build), a second tab shows what the level brings and what it needs ({@link UpgradeInfo}); the current level's
 * blueprint is read too, for the comparison.
 * <pre>
 * [hut] Farmer's Hut
 *       Level 2 -> 3
 * Style: Medieval Oak             Builder: [Any builder      v]
 * [ Materials (24)          ] [ What it brings           ]
 * +----------------------------------------------------------+
 * | [log] Oak Log           64 | [glass] Glass            12 |
 * +----------------------------------------------------------+
 * [Back]                         [Repair] [Upgrade] [Remove]
 * </pre>
 */
public final class BuildScreen extends Screen
{
    private static final int WIDTH = 320;
    private static final int HEIGHT = 212;
    private static final int ROW = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ACTION_WIDTH = 76;
    private static final int CONFIRM_TICKS = 60;
    private static final int CONFIRM_WIDTH = 220;
    private static final int TAB_HEIGHT = 18;

    /** The "what it brings" tab is shown rather than the materials (kept for the session). */
    private static boolean showGains = true;

    /** Materials shown by the dev preview instead of reading a blueprint. */
    @Nullable
    static List<ItemStorage> previewResources;

    private enum State
    {
        LOADING, READY, MISSING
    }

    private record Builder(Component name, BlockPos pos)
    {
    }

    private final IBuildingView building;
    private final ScrollArea list = new ScrollArea();
    private final Tooltip tooltip = new Tooltip();
    private final Dropdown dropdown = new Dropdown();
    private final Map<String, ItemStorage> resources = new LinkedHashMap<>();
    private List<ItemStorage> sorted = List.of();
    private final List<Builder> builders = new ArrayList<>();
    private final String style;
    /** What the level to build brings; null at the maximum level. */
    @Nullable
    private final UpgradeInfo upgrade;
    private int builder;
    private State state = State.LOADING;
    /** The blueprint came back: the buttons depend on it. */
    private boolean buttonsDirty;
    private int retry;
    private int ticks;
    /** The remove button was clicked once; a second click before this tick removes the hut. */
    private int removeUntil = -1;
    private boolean confirming;
    private int left;
    private int top;

    @Nullable
    private Button repair;
    @Nullable
    private Button build;
    @Nullable
    private Button remove;
    @Nullable
    private Button pickup;
    @Nullable
    private Button materialsTab;

    public BuildScreen(final IBuildingView building)
    {
        super(Component.translatable("com.minecolonies.coremod.gui.workerhuts.buildrepair"));
        this.building = building;
        this.style = parentStyle();
        this.upgrade = building.getBuildingLevel() == 0 || canBeUpgraded() ? new UpgradeInfo(building, targetLevel()) : null;
        collectBuilders();
        if (previewResources != null)
        {
            for (final ItemStorage storage : previewResources)
            {
                resources.put(storage.getItemStack().getDescriptionId() + resources.size(), storage);
            }
            sortResources();
            state = State.READY;
        }
        else
        {
            loadResources();
            loadCurrentLevel();
        }
    }

    // ------------------------------------------------------------------ MineColonies' logic

    /** A child building (a tower of the barracks...) is built in its parent's style. */
    private String parentStyle()
    {
        final IColonyView colony = building.getColony();
        if (!BlockPos.ZERO.equals(building.getParent()) && colony != null && colony.getClientBuildingManager().getBuilding(building.getParent()) != null)
        {
            return colony.getClientBuildingManager().getBuilding(building.getParent()).getStructurePack();
        }
        return building.getStructurePack();
    }

    @Nullable
    private IBuildingView parent()
    {
        final IColonyView colony = building.getColony();
        return colony == null ? null : colony.getClientBuildingManager().getBuilding(building.getParent());
    }

    /** Same rule as MineColonies: below the maximum, and not above a parent that can still be upgraded itself. */
    private boolean canBeUpgraded()
    {
        final IBuildingView parent = parent();
        return building.getBuildingLevel() < building.getBuildingMaxLevel()
                 && (parent == null || building.getBuildingLevel() < parent.getBuildingLevel() || parent.getBuildingLevel() >= parent.getBuildingMaxLevel());
    }

    private int targetLevel()
    {
        return canBeUpgraded() ? building.getBuildingLevel() + 1 : building.getBuildingLevel();
    }

    private void collectBuilders()
    {
        builders.clear();
        builders.add(new Builder(Component.translatable("mcvanillatweaks.build.any_builder"), BlockPos.ZERO));
        final IColonyView colony = building.getColony();
        if (colony == null)
        {
            return;
        }
        final List<AbstractBuildingBuilderView> found = new ArrayList<>();
        for (final IBuildingView view : colony.getClientBuildingManager().getBuildings().values())
        {
            if (view instanceof AbstractBuildingBuilderView builderView && !builderView.getWorkerName().isEmpty()
                  && view.getBuildingType() != ModBuildings.miner.get())
            {
                found.add(builderView);
            }
        }
        found.sort(Comparator.comparingDouble(view -> view.getPosition().distSqr(building.getPosition())));
        for (final AbstractBuildingBuilderView view : found)
        {
            final int distance = (int) Math.round(Math.sqrt(view.getPosition().distSqr(building.getPosition())));
            builders.add(new Builder(Component.translatable("mcvanillatweaks.build.builder_entry", view.getWorkerName(), view.getBuildingLevel(),
              Component.translatable("com.minecolonies.coremod.gui.blocks.distance", distance)), view.getPosition()));
        }
    }

    /** Reads the blueprint of the level to build, like MineColonies' window (every block of it, nothing skipped). */
    private void loadResources()
    {
        retry = 20;
        final Level level = minecraftLevel();
        String name = building.getStructurePath().replace(".blueprint", "");
        if (level == null || name.isEmpty())
        {
            return;
        }
        name = name.substring(0, name.length() - 1) + targetLevel() + ".blueprint";
        ClientFutureProcessor.queueBlueprint(new ClientFutureProcessor.BlueprintProcessingData(
          StructurePacks.getBlueprintFuture(style, name, level.registryAccess()), blueprint -> {
            try
            {
                resources.clear();
                if (upgrade != null)
                {
                    upgrade.targetBlueprint(blueprint);
                }
                if (blueprint == null)
                {
                    state = State.MISSING;
                    sortResources();
                    buttonsDirty = true;
                    return;
                }
                blueprint.setRotationMirror(building.getRotationMirror(), level);
                final StructurePlacer placer = new StructurePlacer(new LoadOnlyStructureHandler(level, building.getPosition(), blueprint, RotationMirror.NONE));
                BlockPos progress = AbstractBlueprintIterator.NULL_POS;
                StructurePhasePlacementResult result;
                do
                {
                    result = placer.executeStructureStep(level, null, progress, StructurePlacer.Operation.GET_RES_REQUIREMENTS,
                      () -> placer.getIterator().increment((info, pos, handler) -> false), true);
                    progress = result.getIteratorPos();
                    for (final ItemStack stack : result.getBlockResult().getRequiredItems())
                    {
                        addResource(stack, stack.getCount());
                    }
                }
                while (result.getBlockResult().getResult() != BlockPlacementResult.Result.FINISHED);
                state = State.READY;
                sortResources();
                buttonsDirty = true;
            }
            catch (final LinkageError | RuntimeException e)
            {
                HutScreens.report(e);
            }
        }));
    }

    /** The blueprint of the level already built, to compare with the next one. */
    private void loadCurrentLevel()
    {
        final Level level = minecraftLevel();
        final String path = building.getStructurePath().replace(".blueprint", "");
        if (upgrade == null || building.getBuildingLevel() == 0 || level == null || path.isEmpty())
        {
            return;
        }
        final String name = path.substring(0, path.length() - 1) + building.getBuildingLevel() + ".blueprint";
        ClientFutureProcessor.queueBlueprint(new ClientFutureProcessor.BlueprintProcessingData(
          StructurePacks.getBlueprintFuture(style, name, level.registryAccess()), blueprint -> {
            try
            {
                upgrade.currentBlueprint(blueprint);
            }
            catch (final LinkageError | RuntimeException e)
            {
                upgrade.currentBlueprint(null);
            }
        }));
    }

    private boolean gainsShown()
    {
        return showGains && upgrade != null;
    }

    @Nullable
    private Level minecraftLevel()
    {
        return net.minecraft.client.Minecraft.getInstance().level;
    }

    private void addResource(@Nullable final ItemStack stack, final int amount)
    {
        if (ItemStackUtils.isEmpty(stack) || amount == 0)
        {
            return;
        }
        final String key = stack.getDescriptionId() + "-" + stack.getComponentsPatch().hashCode();
        final ItemStorage existing = resources.get(key);
        if (existing == null)
        {
            final ItemStorage storage = new ItemStorage(stack);
            storage.setAmount(amount);
            resources.put(key, storage);
        }
        else
        {
            existing.setAmount(existing.getAmount() + amount);
        }
    }

    /** Most needed first, then by name. */
    private void sortResources()
    {
        final List<ItemStorage> copy = new ArrayList<>(resources.values());
        copy.sort(Comparator.comparingInt(ItemStorage::getAmount).reversed().thenComparing(s -> s.getItemStack().getHoverName().getString()));
        sorted = copy;
    }

    private BlockPos chosenBuilder()
    {
        return builders.get(Math.min(builder, builders.size() - 1)).pos();
    }

    private String warning()
    {
        final String key = building.getHoverWarningForLevel();
        return key == null ? "" : key;
    }

    private void buildClicked()
    {
        if (!warning().isEmpty())
        {
            confirming = true;
            rebuildWidgets();
            return;
        }
        confirmBuild();
    }

    private void confirmBuild()
    {
        new BuildingSetStyleMessage(building, style).sendToServer();
        final BuildRequestMessage.Mode mode = building.getBuildingLevel() == building.getBuildingMaxLevel() ? BuildRequestMessage.Mode.REPAIR : BuildRequestMessage.Mode.BUILD;
        new BuildRequestMessage(building, mode, chosenBuilder()).sendToServer();
        back();
    }

    private void repairClicked()
    {
        new BuildRequestMessage(building, BuildRequestMessage.Mode.REPAIR, chosenBuilder()).sendToServer();
        back();
    }

    private void removeClicked()
    {
        if (removeUntil < ticks)
        {
            removeUntil = ticks + CONFIRM_TICKS;
            updateButtons();
            return;
        }
        new BuildRequestMessage(building, BuildRequestMessage.Mode.REMOVE, chosenBuilder()).sendToServer();
        back();
    }

    private void pickupClicked()
    {
        new BuildPickUpMessage(building).sendToServer();
        onClose();
    }

    /** MineColonies' cancel: back to the hut's window (swapped for the vanilla one). */
    private void back()
    {
        building.openGui(false);
    }

    // ------------------------------------------------------------------ screen

    @Override
    protected void init()
    {
        dropdown.close();
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        try
        {
            if (confirming)
            {
                initConfirm();
                return;
            }
            final int bx0 = left + 8 + font.width(Component.translatable("mcvanillatweaks.build.builder")) + 6;
            addRenderableWidget(new DropdownButton(bx0, top + 32, left + WIDTH - 8 - bx0, 16, builders.get(builder).name(), b -> openBuilders(b)));

            if (upgrade != null)
            {
                // Two tabs: the active one is the disabled button.
                final int tw = (WIDTH - 16 - 4) / 2;
                final Button materials = materialsTab = addRenderableWidget(Button.builder(Component.translatable("mcvanillatweaks.build.tab.materials"), b -> {
                    showGains = false;
                    rebuildWidgets();
                }).bounds(left + 8, top + 52, tw, TAB_HEIGHT).build());
                final MutableComponent gainsLabel = Component.translatable("mcvanillatweaks.build.tab.gains");
                if (upgrade.hasBlockers())
                {
                    gainsLabel.append(Component.literal(" !").withStyle(ChatFormatting.RED));
                }
                final Button gains = addRenderableWidget(Button.builder(gainsLabel, b -> {
                    showGains = true;
                    rebuildWidgets();
                }).bounds(left + 8 + tw + 4, top + 52, tw, TAB_HEIGHT).build());
                materials.active = gainsShown();
                gains.active = !gainsShown();
            }
            else
            {
                materialsTab = null;
            }
            final int by = top + HEIGHT - 8 - BUTTON_HEIGHT;
            addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> back()).bounds(left + 8, by, 60, BUTTON_HEIGHT).build());
            int bx = left + WIDTH - 8 - ACTION_WIDTH;
            remove = addRenderableWidget(Button.builder(Component.translatable("com.minecolonies.coremod.gui.workerhuts.deconstruct"), b -> removeClicked())
                                           .bounds(bx, by, ACTION_WIDTH, BUTTON_HEIGHT).build());
            pickup = addRenderableWidget(Button.builder(Component.translatable("mcvanillatweaks.build.pickup"), b -> pickupClicked())
                                           .bounds(bx, by, ACTION_WIDTH, BUTTON_HEIGHT).build());
            bx -= ACTION_WIDTH + 4;
            build = addRenderableWidget(Button.builder(Component.empty(), b -> buildClicked()).bounds(bx, by, ACTION_WIDTH, BUTTON_HEIGHT).build());
            bx -= ACTION_WIDTH + 4;
            repair = addRenderableWidget(Button.builder(Component.empty(), b -> repairClicked()).bounds(bx, by, ACTION_WIDTH, BUTTON_HEIGHT).build());
            updateButtons();
        }
        catch (final LinkageError | RuntimeException e)
        {
            fail(e);
        }
    }

    private void initConfirm()
    {
        final int h = confirmHeight();
        final int cx = (width - CONFIRM_WIDTH) / 2;
        final int by = (height - h) / 2 + h - 8 - BUTTON_HEIGHT;
        final int bw = (CONFIRM_WIDTH - 24) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> {
            confirming = false;
            rebuildWidgets();
        }).bounds(cx + 8, by, bw, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable(buildKey()), b -> confirmBuild()).bounds(cx + CONFIRM_WIDTH - 8 - bw, by, bw, BUTTON_HEIGHT).build());
    }

    private List<FormattedCharSequence> confirmLines()
    {
        return font.split(Component.translatable(warning()), CONFIRM_WIDTH - 16);
    }

    private int confirmHeight()
    {
        final int title = font.split(Component.translatable("com.minecolonies.core.gui.build.confirm.title"), CONFIRM_WIDTH - 16).size();
        return 8 + title * 10 + 6 + confirmLines().size() * 10 + 10 + BUTTON_HEIGHT + 8;
    }

    /** "Build" for a hut not built yet, "upgrade" otherwise, as MineColonies labels the button. */
    private String buildKey()
    {
        return building.getBuildingLevel() == 0 ? "mcvanillatweaks.build.build" : "com.minecolonies.coremod.gui.workerhuts.upgrade";
    }

    /** MineColonies' button rules: a hut not built (or removed) can be picked up instead of removed, a missing blueprint leaves only the pickup. */
    private void updateButtons()
    {
        if (materialsTab != null)
        {
            materialsTab.setMessage(state == State.READY ? Component.translatable("mcvanillatweaks.build.tab.materials_count", sorted.size())
                                      : Component.translatable("mcvanillatweaks.build.tab.materials"));
        }
        if (build == null || repair == null || remove == null || pickup == null)
        {
            return;
        }
        final boolean unbuilt = building.getBuildingLevel() == 0;
        build.setMessage(Component.translatable(buildKey()));
        build.visible = (unbuilt || canBeUpgraded()) && state != State.MISSING;
        build.setTooltip(warning().isEmpty() ? null : net.minecraft.client.gui.components.Tooltip.create(Component.translatable(warning())));
        repair.setMessage(Component.translatable(building.isDeconstructed() ? "mcvanillatweaks.build.build" : "com.minecolonies.coremod.gui.workerhuts.repair"));
        repair.visible = !unbuilt && state != State.MISSING;
        repair.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("mcvanillatweaks.build.repair_hint")));
        final boolean canPickup = unbuilt || building.isDeconstructed() || state == State.MISSING;
        pickup.visible = canPickup;
        pickup.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("mcvanillatweaks.build.pickup_hint")));
        remove.visible = !canPickup;
        final boolean armed = removeUntil >= ticks;
        remove.setMessage(armed ? Component.translatable("mcvanillatweaks.hut.wo.confirm").withStyle(ChatFormatting.RED)
                            : Component.translatable("com.minecolonies.coremod.gui.workerhuts.deconstruct"));
        remove.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(armed ? "mcvanillatweaks.build.remove_confirm" : "mcvanillatweaks.build.remove_hint")));
    }

    private void openBuilders(final Button anchor)
    {
        final List<Dropdown.Entry> entries = new ArrayList<>();
        for (final Builder entry : builders)
        {
            entries.add(new Dropdown.Entry(entry.name()));
        }
        dropdown.open(anchor.getX(), anchor.getY(), anchor.getWidth(), anchor.getHeight(), entries, builder, index -> {
            builder = index;
            rebuildWidgets();
        }, height);
    }

    @Override
    public void tick()
    {
        ticks++;
        if (removeUntil >= 0 && removeUntil + 1 == ticks)
        {
            removeUntil = -1;
            updateButtons();
        }
        // MineColonies tries again every second while nothing came back.
        if (previewResources == null && resources.isEmpty() && retry > 0 && --retry == 0)
        {
            try
            {
                loadResources();
            }
            catch (final LinkageError | RuntimeException e)
            {
                fail(e);
            }
        }
        if (buttonsDirty)
        {
            buttonsDirty = false;
            updateButtons();
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
        if (confirming)
        {
            renderConfirm(g, realMouseX, realMouseY, partialTick);
            return;
        }
        final int mouseX = dropdown.isOpen() ? -1000 : realMouseX;
        final int mouseY = dropdown.isOpen() ? -1000 : realMouseY;
        super.render(g, mouseX, mouseY, partialTick);
        tooltip.clear();
        try
        {
            renderHeader(g, mouseX, mouseY);
            renderResources(g, mouseX, mouseY);
            dropdown.render(g, font, realMouseX, realMouseY);
        }
        catch (final LinkageError | RuntimeException e)
        {
            fail(e);
            return;
        }
        tooltip.render(g, font, mouseX, mouseY);
    }

    private Component levelLine()
    {
        final int level = building.getBuildingLevel();
        if (level == 0)
        {
            return Component.translatable("mcvanillatweaks.build.level.unbuilt");
        }
        if (canBeUpgraded())
        {
            return Component.translatable("mcvanillatweaks.build.level.upgrade", level, level + 1);
        }
        if (level >= building.getBuildingMaxLevel())
        {
            return Component.translatable("mcvanillatweaks.build.level.max", level);
        }
        final IBuildingView parent = parent();
        return parent == null ? Component.translatable("mcvanillatweaks.hut.level", level, building.getBuildingMaxLevel())
                 : Component.translatable("mcvanillatweaks.build.level.parent", level, Component.translatable(parent.getBuildingDisplayName()), parent.getBuildingLevel() + 1);
    }

    private void renderHeader(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        g.renderItem(HutScreen.hutIcon(building), left + 8, top + 8);
        final Component name = Component.translatable(building.getBuildingDisplayName());
        g.drawString(font, Gui.fit(font, name, WIDTH - 40), left + 30, top + 7, Gui.LABEL, false);

        // Style on the right of the level line.
        final Component styleLine = Component.translatable("mcvanillatweaks.build.style", style == null || style.isEmpty() ? "-" : style);
        final int styleWidth = Math.min(font.width(styleLine), 130);
        final int styleX = left + WIDTH - 8 - styleWidth;
        g.drawString(font, Gui.fit(font, styleLine, styleWidth), styleX, top + 18, Gui.LABEL_SOFT, false);
        if (Gui.inside(mouseX, mouseY, styleX - 1, top + 16, styleWidth + 2, 12))
        {
            tooltip.set(List.of(styleLine, Component.translatable("mcvanillatweaks.build.style_hint").withStyle(ChatFormatting.GRAY)));
        }

        final Component level = levelLine();
        final int levelWidth = styleX - 8 - (left + 30);
        g.drawString(font, Gui.fit(font, level, levelWidth), left + 30, top + 18, Gui.LABEL_SOFT, false);
        if (Gui.isCut(font, level, levelWidth) && Gui.inside(mouseX, mouseY, left + 30, top + 17, levelWidth, 10))
        {
            tooltip.set(level);
        }
        g.drawString(font, Component.translatable("mcvanillatweaks.build.builder"), left + 8, top + 36, Gui.LABEL, false);
    }

    private void renderResources(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        int total = 0;
        for (final ItemStorage storage : sorted)
        {
            total += storage.getAmount();
        }
        final Component heading = state == State.READY
                                    ? Component.translatable("mcvanillatweaks.build.materials", targetLevel(), sorted.size(), total)
                                    : Component.translatable("mcvanillatweaks.build.materials_title", targetLevel());
        if (upgrade == null)
        {
            g.drawString(font, Gui.fit(font, heading, WIDTH - 16), left + 8, top + 56, Gui.LABEL, false);
        }
        else if (!gainsShown() && Gui.inside(mouseX, mouseY, left + 8, top + 52, (WIDTH - 20) / 2, TAB_HEIGHT))
        {
            tooltip.set(heading);
        }
        if (gainsShown())
        {
            renderGains(g, mouseX, mouseY);
            return;
        }

        final int listTop = upgrade == null ? top + 68 : top + 52 + TAB_HEIGHT + 4;
        final int listBottom = top + HEIGHT - 8 - BUTTON_HEIGHT - 6;
        final int rows = (sorted.size() + 1) / 2;
        list.set(left + 8, listTop, WIDTH - 16, listBottom - listTop, rows * ROW, ROW);
        list.begin(g);
        if (sorted.isEmpty())
        {
            final String key = switch (state)
            {
                case LOADING -> "mcvanillatweaks.build.loading";
                case MISSING -> "mcvanillatweaks.build.missing";
                case READY -> "mcvanillatweaks.build.nothing";
            };
            int ty = list.visibleTop() + 6;
            for (final FormattedCharSequence line : font.split(Component.translatable(key), list.listWidth() - 8))
            {
                g.drawString(font, line, list.listX() + 4, ty, Gui.LIST_SOFT, true);
                ty += 10;
            }
        }
        final int cell = list.listWidth() / 2;
        for (int i = 0; i < sorted.size(); i++)
        {
            final int rowY = list.listY() + (i / 2) * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final int cx = list.listX() + (i % 2) * cell;
            final ItemStorage storage = sorted.get(i);
            final boolean hovered = list.overRows(mouseX, mouseY) && Gui.inside(mouseX, mouseY, cx, rowY, cell, ROW);
            if (hovered)
            {
                g.fill(cx, rowY, cx + cell, rowY + ROW, Gui.ROW_HOVER);
            }
            final ItemStack icon = storage.getItemStack().copyWithCount(1);
            g.renderItem(icon, cx + 3, rowY + 2);
            final String amount = Integer.toString(storage.getAmount());
            final int amountX = cx + cell - 5 - font.width(amount);
            g.drawString(font, amount, amountX, rowY + 6, Gui.WHITE, true);
            g.drawString(font, Gui.fit(font, icon.getHoverName(), amountX - cx - 28), cx + 23, rowY + 6, Gui.WHITE, true);
            if (hovered)
            {
                tooltip.set(icon, List.of(Component.translatable("mcvanillatweaks.build.needed", storage.getAmount()).withStyle(ChatFormatting.GRAY)));
            }
        }
        list.end(g);
    }

    /** The "what it brings" tab: conditions, then what the level adds. */
    private void renderGains(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        final List<UpgradeInfo.Line> lines = upgrade.lines();
        final int listTop = top + 52 + TAB_HEIGHT + 4;
        final int listBottom = top + HEIGHT - 8 - BUTTON_HEIGHT - 6;
        list.set(left + 8, listTop, WIDTH - 16, listBottom - listTop, lines.size() * ROW, ROW);
        list.begin(g);
        for (int i = 0; i < lines.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final UpgradeInfo.Line line = lines.get(i);
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            int tx = list.listX() + 4;
            if (line.icon() != null && !line.icon().isEmpty())
            {
                g.renderItem(line.icon(), list.listX() + 3, rowY + 2);
                tx = list.listX() + 23;
            }
            final int room = list.listX() + list.listWidth() - 4 - tx;
            g.drawString(font, Gui.fit(font, line.text(), room), tx, rowY + 6, line.colour(), true);
            if (hovered && (Gui.isCut(font, line.text(), room) || !line.tooltip().isEmpty()))
            {
                final List<Component> tip = new ArrayList<>();
                tip.add(line.text());
                tip.addAll(line.tooltip());
                tooltip.set(tip);
            }
        }
        list.end(g);
    }

    private void renderConfirm(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        renderTransparentBackground(g);
        Gui.panel(g, left, top, WIDTH, HEIGHT);
        g.fill(left, top, left + WIDTH, top + HEIGHT, 0x80000000);
        final int h = confirmHeight();
        final int cx = (width - CONFIRM_WIDTH) / 2;
        final int cy = (height - h) / 2;
        Gui.panel(g, cx, cy, CONFIRM_WIDTH, h);
        int ty = cy + 8;
        for (final FormattedCharSequence line : font.split(Component.translatable("com.minecolonies.core.gui.build.confirm.title"), CONFIRM_WIDTH - 16))
        {
            g.drawString(font, line, cx + 8, ty, Gui.LABEL, false);
            ty += 10;
        }
        ty += 6;
        for (final FormattedCharSequence line : confirmLines())
        {
            g.drawString(font, line, cx + 8, ty, Gui.LABEL_SOFT, false);
            ty += 10;
        }
        for (final var renderable : renderables)
        {
            renderable.render(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        try
        {
            if (dropdown.mouseClicked(mouseX, mouseY, button))
            {
                return true;
            }
            return list.mouseClicked(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
        }
        catch (final LinkageError | RuntimeException e)
        {
            fail(e);
            return true;
        }
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
        if (confirming && key == GLFW.GLFW_KEY_ESCAPE)
        {
            confirming = false;
            rebuildWidgets();
            return true;
        }
        return dropdown.keyPressed(key) || super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    private void fail(final Throwable e)
    {
        HutScreens.report(e);
        if (minecraft != null && minecraft.screen == this)
        {
            onClose();
        }
    }
}
