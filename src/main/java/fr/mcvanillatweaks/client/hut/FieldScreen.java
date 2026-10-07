package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.ICitizen;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.items.ModTags;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.items.ItemCrop;
import com.minecolonies.core.network.messages.server.colony.building.fields.FarmFieldPlotResizeMessage;
import com.minecolonies.core.network.messages.server.colony.building.fields.FarmFieldUpdateSeedMessage;
import com.minecolonies.core.tileentities.TileEntityScarecrow;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Replaces MineColonies' farm field window ({@code WindowField}, right click on the scarecrow):
 * <ul>
 *   <li>the seed in a slot, "choose" opens a picker in the screen (MineColonies opens Structurize's item list) with the
 *       same candidates: seeds, crop blocks, and MineColonies' crops that grow in this biome; a click sends
 *       {@code FarmFieldUpdateSeedMessage} like MineColonies;</li>
 *   <li>the farmer working it (first citizen of the hut that took the field), the biome and its crop climate;</li>
 *   <li>the size as a plan seen from above, with - / + per direction instead of MineColonies' cycling buttons. Same rule:
 *       1 to 20 blocks a side and 20 in all, sent with {@code FarmFieldPlotResizeMessage} and set on the client block.</li>
 * </ul>
 */
public final class FieldScreen extends Screen
{
    private static final int WIDTH = 320;
    private static final int HEIGHT = 216;
    private static final int TOTAL = 20;
    private static final int MAX_SIDE = 20;
    private static final int PLAN = 96;
    private static final int SMALL = 14;

    /** Seeds shown by the dev preview, whose compatibility manager is empty at the title screen. */
    static List<ItemStack> previewCandidates;

    private final TileEntityScarecrow scarecrow;
    @Nullable
    private final IColonyView colonyOverride;
    private final Tooltip tooltip = new Tooltip();
    private final ItemGrid grid = new ItemGrid();
    private final Map<Direction, Button[]> sizeButtons = new EnumMap<>(Direction.class);
    /** Per side: centre x and y of its number, then the hover box (x, y, w, h) of its controls. */
    private final Map<Direction, int[]> sizeLabels = new EnumMap<>(Direction.class);
    @Nullable
    private FarmField field;
    private boolean picking;
    private List<ItemStack> candidates;
    private String filter = "";
    private EditBox search;
    private int left;
    private int top;

    public FieldScreen(final TileEntityScarecrow scarecrow, @Nullable final IColonyView colonyOverride)
    {
        super(Component.translatable("block.minecolonies.blockhutfield"));
        this.scarecrow = scarecrow;
        this.colonyOverride = colonyOverride;
    }

    // ------------------------------------------------------------------ data

    @Nullable
    private IColonyView colony()
    {
        if (colonyOverride != null)
        {
            return colonyOverride;
        }
        try
        {
            return scarecrow.getCurrentColony() instanceof IColonyView view ? view : null;
        }
        catch (final RuntimeException e)
        {
            return null;
        }
    }

    @Nullable
    private FarmField field()
    {
        if (field == null)
        {
            final IColonyView colony = colony();
            if (colony != null)
            {
                final List<IBuildingExtension> fields = colony.getClientBuildingManager().getBuildingExtensions(
                  other -> other.getBuildingExtensionType().equals(BuildingExtensionRegistries.farmField.get()) && other.getPosition().equals(scarecrow.getBlockPos()));
                if (!fields.isEmpty() && fields.get(0) instanceof FarmField found)
                {
                    field = found;
                }
            }
        }
        return field;
    }

    private int size(final Direction direction)
    {
        return scarecrow.getFieldSize()[direction.get2DDataValue()];
    }

    private int total()
    {
        int sum = 0;
        for (final int side : scarecrow.getFieldSize())
        {
            sum += side;
        }
        return sum;
    }

    @Nullable
    private Holder<Biome> biome()
    {
        return minecraft == null || minecraft.level == null ? null : minecraft.level.getBiome(scarecrow.getBlockPos());
    }

    /** The farmer: first citizen of the hut that took the field, as MineColonies shows it. */
    private Component farmer()
    {
        final IColonyView colony = colony();
        final FarmField farm = field();
        if (colony != null && farm != null && farm.isTaken())
        {
            final IBuildingView building = colony.getClientBuildingManager().getBuilding(farm.getBuildingId());
            if (building != null)
            {
                final Integer id = building.getAllAssignedCitizens().stream().findFirst().orElse(null);
                final ICitizen citizen = id == null ? null : colony.getCitizen(id);
                if (citizen != null)
                {
                    return Component.translatable("com.minecolonies.coremod.gui.field.assignedfarmer", citizen.getName());
                }
            }
        }
        return Component.translatable("com.minecolonies.coremod.gui.field.noassignedfarmer");
    }

    private List<ItemStack> candidates()
    {
        if (candidates == null && previewCandidates != null)
        {
            candidates = previewCandidates;
        }
        if (candidates == null)
        {
            final Holder<Biome> biome = biome();
            candidates = IColonyManager.getInstance().getCompatibilityManager().getListOfMatchingItems(stack -> stack.is(Tags.Items.SEEDS)
              || stack.getItem() instanceof BlockItem item && item.getBlock() instanceof CropBlock
              || stack.getItem() instanceof ItemCrop crop && (biome == null || crop.canBePlantedIn(biome)));
        }
        return candidates;
    }

    private void applyFilter()
    {
        final String lower = filter.toLowerCase(Locale.ROOT);
        final List<ItemStack> list = new ArrayList<>();
        for (final ItemStack stack : candidates())
        {
            if (lower.isEmpty() || stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(lower))
            {
                list.add(stack);
            }
        }
        grid.setItems(list);
        grid.scrollToTop();
    }

    /** Opens on the seed picker (used by the dev preview). */
    FieldScreen picking()
    {
        picking = true;
        return this;
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init()
    {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        sizeButtons.clear();
        sizeLabels.clear();
        if (picking)
        {
            search = addRenderableWidget(new EditBox(font, left + 9, top + 28, WIDTH - 18, 16, Component.translatable("mcvanillatweaks.townhall.search")));
            search.setHint(Component.translatable("mcvanillatweaks.townhall.search").withStyle(ChatFormatting.DARK_GRAY));
            search.setValue(filter);
            search.setResponder(text -> {
                filter = text;
                applyFilter();
            });
            setInitialFocus(search);
            final int columns = ItemGrid.columnsFor(WIDTH - 16);
            grid.set(left + 8, top + 50, columns, (HEIGHT - 50 - 36) / 18);
            applyFilter();
            addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> setPicking(false)).bounds(left + WIDTH - 108, top + HEIGHT - 28, 100, 20).build());
            return;
        }
        final IColonyView colony = colony();
        if (colony != null)
        {
            addRenderableWidget(Button.builder(Component.translatable("mcvanillatweaks.field.choose"), b -> setPicking(true))
                                  .bounds(left + 8, top + 76, 124, 20).build());
        }
        // The plan: north on top, like a map.
        final int cx = planX() + PLAN / 2;
        final int cy = planY() + PLAN / 2;
        sizeControls(Direction.NORTH, cx - SMALL - 12, planY() - SMALL - 3, false);
        sizeControls(Direction.SOUTH, cx - SMALL - 12, planY() + PLAN + 3, false);
        sizeControls(Direction.WEST, planX() - SMALL - 6, cy - SMALL - 7, true);
        sizeControls(Direction.EAST, planX() + PLAN + 6, cy - SMALL - 7, true);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + 8, top + HEIGHT - 28, 124, 20).build());
        updateButtons();
    }

    private int planX()
    {
        return left + 150 + (WIDTH - 158 - PLAN) / 2;
    }

    private int planY()
    {
        return top + 58;
    }

    /** "- 5 +" above and under the plan, "+ 5 -" stacked on its sides (+ on top), starting at (x, y). */
    private void sizeControls(final Direction direction, final int x, final int y, final boolean vertical)
    {
        final Button minus;
        final Button plus;
        if (vertical)
        {
            plus = addRenderableWidget(Button.builder(Component.literal("+"), b -> resize(direction, 1)).bounds(x, y, SMALL, SMALL).build());
            minus = addRenderableWidget(Button.builder(Component.literal("-"), b -> resize(direction, -1)).bounds(x, y + SMALL + 14, SMALL, SMALL).build());
            sizeLabels.put(direction, new int[] {x + SMALL / 2, y + SMALL + 3, x, y, SMALL, 2 * SMALL + 14});
        }
        else
        {
            minus = addRenderableWidget(Button.builder(Component.literal("-"), b -> resize(direction, -1)).bounds(x, y, SMALL, SMALL).build());
            plus = addRenderableWidget(Button.builder(Component.literal("+"), b -> resize(direction, 1)).bounds(x + SMALL + 24, y, SMALL, SMALL).build());
            sizeLabels.put(direction, new int[] {x + SMALL + 12, y + 3, x, y, 2 * SMALL + 24, SMALL});
        }
        sizeButtons.put(direction, new Button[] {minus, plus});
    }

    private void updateButtons()
    {
        final int total = total();
        for (final Map.Entry<Direction, Button[]> entry : sizeButtons.entrySet())
        {
            final int side = size(entry.getKey());
            entry.getValue()[0].active = side > 1;
            entry.getValue()[1].active = side < MAX_SIDE && total < TOTAL;
        }
    }

    private void resize(final Direction direction, final int delta)
    {
        final int value = size(direction) + delta;
        if (value < 1 || value > MAX_SIDE || delta > 0 && total() >= TOTAL)
        {
            return;
        }
        try
        {
            scarecrow.setFieldSize(direction, value);
        }
        catch (final RuntimeException e)
        {
            // the block keeps its size until the server's update
        }
        new FarmFieldPlotResizeMessage(value, direction, scarecrow.getBlockPos()).sendToServer();
        updateButtons();
    }

    private void setPicking(final boolean picking)
    {
        this.picking = picking;
        rebuildWidgets();
    }

    private void setSeed(final ItemStack stack)
    {
        final IColonyView colony = colony();
        final FarmField farm = field();
        if (colony != null && farm != null)
        {
            new FarmFieldUpdateSeedMessage(colony, stack, farm.getPosition()).sendToServer();
            farm.setSeed(stack);
        }
        setPicking(false);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        renderTransparentBackground(g);
        Gui.panel(g, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        tooltip.clear();
        super.render(g, mouseX, mouseY, partialTick);
        g.renderItem(new ItemStack(scarecrow.getBlockState().getBlock().asItem()), left + 8, top + 7);
        try
        {
            if (picking)
            {
                g.drawString(font, Gui.fit(font, Component.translatable("com.minecolonies.coremod.gui.field.selectseed"), WIDTH - 36), left + 28, top + 11, Gui.LABEL, false);
                final FarmField farm = field();
                final ItemStack current = farm == null ? ItemStack.EMPTY : farm.getSeed();
                grid.render(g, font, mouseX, mouseY, tooltip, stack -> ItemStack.isSameItem(stack, current));
                if (grid.items().isEmpty())
                {
                    g.drawString(font, Component.translatable("mcvanillatweaks.field.no_seed_match"), left + 9, top + 56, Gui.LABEL_SOFT, false);
                }
            }
            else
            {
                renderMain(g, mouseX, mouseY);
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            HutScreens.report(e);
            onClose();
            return;
        }
        tooltip.render(g, font, mouseX, mouseY);
    }

    private void renderMain(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        g.drawString(font, Gui.fit(font, title, WIDTH - 36), left + 28, top + 11, Gui.LABEL, false);
        final IColonyView colony = colony();
        final int lw = 124;
        final int lx = left + 8;
        if (colony == null)
        {
            int ty = top + 32;
            for (final var line : font.split(Component.translatable("com.minecolonies.coremod.gui.workerhuts.fields.notincolony"), lw))
            {
                g.drawString(font, line, lx, ty, Gui.LABEL, false);
                ty += 10;
            }
        }
        else
        {
            // Seed.
            g.drawString(font, Component.translatable("mcvanillatweaks.field.seed"), lx, top + 30, Gui.LABEL, false);
            final FarmField farm = field();
            final ItemStack seed = farm == null ? ItemStack.EMPTY : farm.getSeed();
            Gui.slot(g, lx, top + 42);
            if (!seed.isEmpty())
            {
                g.renderItem(seed, lx + 1, top + 43);
                if (Gui.inside(mouseX, mouseY, lx, top + 42, 18, 18))
                {
                    tooltip.set(seed);
                }
            }
            final Component seedName = seed.isEmpty() ? Component.translatable("mcvanillatweaks.hut.fields.no_seed") : seed.getHoverName();
            g.drawString(font, Gui.fit(font, seedName, lw - 24), lx + 23, top + 47, seed.isEmpty() ? Gui.LABEL_SOFT : Gui.LABEL, false);
            // Farmer.
            g.drawString(font, Gui.fit(font, farmer(), lw), lx, top + 108, Gui.LABEL, false);
        }
        // Biome and its crop climates.
        final Holder<Biome> biome = biome();
        if (biome != null)
        {
            final ResourceLocation id = biome.unwrapKey().map(key -> key.location()).orElse(null);
            if (id != null)
            {
                final String key = "biome." + id.getNamespace() + "." + id.getPath();
                final Component name = Component.translatable("com.minecolonies.core.biome")
                                         .append(I18n.exists(key) ? Component.translatable(key) : Component.literal(id.getPath()));
                g.drawString(font, Gui.fit(font, name, lw), lx, top + 124, Gui.LABEL_SOFT, false);
            }
            final MutableComponent climate = Component.empty();
            for (final TagKey<Biome> tag : ModTags.cropBiomeTags)
            {
                if (biome.is(tag))
                {
                    if (!climate.getSiblings().isEmpty())
                    {
                        climate.append(", ");
                    }
                    climate.append(Component.translatable("com.minecolonies.core.tag.crop.biome." + tag.location().getPath()));
                }
            }
            if (!climate.getSiblings().isEmpty())
            {
                final var lines = font.split(climate, lw);
                for (int i = 0; i < Math.min(3, lines.size()); i++)
                {
                    g.drawString(font, lines.get(i), lx, top + 136 + 10 * i, Gui.LABEL_SOFT, false);
                }
            }
        }
        renderPlan(g, mouseX, mouseY);
    }

    /** The field from above: farmland scaled to fit, the scarecrow in its cell, and the size of each side. */
    private void renderPlan(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        final int px = planX();
        final int py = planY();
        g.drawString(font, Component.translatable("mcvanillatweaks.field.size"), left + 150, top + 30, Gui.LABEL, false);
        Gui.inset(g, px, py, PLAN, PLAN);
        final int north = size(Direction.NORTH);
        final int south = size(Direction.SOUTH);
        final int west = size(Direction.WEST);
        final int east = size(Direction.EAST);
        final int blocksW = west + east + 1;
        final int blocksH = north + south + 1;
        final int cell = Math.max(1, Math.min((PLAN - 8) / blocksW, (PLAN - 8) / blocksH));
        final int fw = cell * blocksW;
        final int fh = cell * blocksH;
        final int fx = px + (PLAN - fw) / 2;
        final int fy = py + (PLAN - fh) / 2;
        g.fill(fx, fy, fx + fw, fy + fh, 0xFF5B3A1E);
        // Rows of crops every other line, like a planted field.
        for (int row = 0; row < blocksH; row++)
        {
            if (row % 2 == 0)
            {
                g.fill(fx, fy + row * cell, fx + fw, fy + row * cell + Math.max(1, cell / 2), 0xFF6E9A2E);
            }
        }
        final int sx = fx + west * cell;
        final int sy = fy + north * cell;
        g.renderOutline(fx, fy, fw, fh, 0xFF2E1D0F);
        final int icon = Math.max(8, Math.min(16, cell));
        fr.mcvanillatweaks.client.citizen.VanillaIcons.item(g, new ItemStack(scarecrow.getBlockState().getBlock().asItem()), sx + (cell - icon) / 2, sy + (cell - icon) / 2, icon);
        g.drawString(font, "N", px + 3, py + 3, Gui.WHITE, true);

        for (final Map.Entry<Direction, int[]> entry : sizeLabels.entrySet())
        {
            final int[] box = entry.getValue();
            final String value = Integer.toString(size(entry.getKey()));
            g.drawString(font, value, box[0] - font.width(value) / 2 + 1, box[1], Gui.LABEL, false);
            if (Gui.inside(mouseX, mouseY, box[2], box[3], box[4], box[5]))
            {
                tooltip.set(List.of(Component.translatable("com.minecolonies.coremod.gui.field." + entry.getKey().getSerializedName()),
                  Component.translatable(relativeKey(entry.getKey())).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
                  Component.translatable("mcvanillatweaks.field.size_hint", TOTAL).withStyle(ChatFormatting.GRAY)));
            }
        }
        final Component dims = Component.translatable("mcvanillatweaks.field.dimensions", blocksW, blocksH);
        g.drawString(font, dims, px + PLAN / 2 - font.width(dims) / 2, py + PLAN + SMALL + 8, Gui.LABEL_SOFT, false);
        final Component left = Component.translatable("mcvanillatweaks.field.left", TOTAL - total());
        g.drawString(font, left, px + PLAN / 2 - font.width(left) / 2, py + PLAN + SMALL + 19, Gui.LABEL_SOFT, false);
    }

    /** MineColonies' "to your left / opposite / ..." for a side, from where the player looks. */
    private String relativeKey(final Direction direction)
    {
        if (minecraft == null || minecraft.player == null)
        {
            return "com.minecolonies.coremod.gui.field.near";
        }
        final Direction[] looks = Direction.orderedByNearest(minecraft.player);
        final Direction facing = looks[0].getAxis() == Direction.Axis.Y ? looks[1] : looks[0];
        return switch (facing.getOpposite().get2DDataValue() - direction.get2DDataValue())
        {
            case -3, 1 -> "com.minecolonies.coremod.gui.field.to_right";
            case -2, 2 -> "com.minecolonies.coremod.gui.field.opposite";
            case -1, 3 -> "com.minecolonies.coremod.gui.field.to_left";
            default -> "com.minecolonies.coremod.gui.field.near";
        };
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (picking)
        {
            if (grid.mouseClicked(mouseX, mouseY))
            {
                return true;
            }
            final ItemStack stack = grid.itemAt(mouseX, mouseY);
            if (button == 0 && stack != null && !stack.isEmpty())
            {
                Gui.clickSound();
                setSeed(stack.copyWithCount(1));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dx, final double dy)
    {
        return picking && grid.mouseDragged(mouseY) || super.mouseDragged(mouseX, mouseY, button, dx, dy);
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
        return picking && grid.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers)
    {
        if (picking && key == GLFW.GLFW_KEY_ESCAPE)
        {
            setPicking(false);
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
