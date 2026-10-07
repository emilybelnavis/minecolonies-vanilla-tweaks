package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.ICitizen;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildingextensions.modules.IBuildingExtensionModule;
import com.minecolonies.api.colony.buildingextensions.plantation.IPlantationModule;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.api.tileentities.AbstractTileEntityPlantationField;
import com.minecolonies.core.client.gui.WindowBuildDecoration;
import com.minecolonies.core.network.messages.server.PlantationFieldBuildRequestMessage;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Replaces MineColonies' plantation field window ({@code WindowPlantationField}): each plant the field grows (one per
 * field type of the block, read from its plantation module like MineColonies) with who works it (the citizen of the
 * plantation that took that part of the field) or "free", and the repair button, which opens MineColonies' decoration
 * build window with {@code PlantationFieldBuildRequestMessage} like MineColonies.
 */
public final class PlantationFieldScreen extends Screen
{
    private static final int WIDTH = 260;
    private static final int HEIGHT = 190;
    private static final int ROW = 24;

    private record Plant(ItemStack item, BuildingExtensionRegistries.BuildingExtensionEntry type)
    {
    }

    private final AbstractTileEntityPlantationField tile;
    @Nullable
    private final IColonyView colonyOverride;
    private final List<Plant> plants = new ArrayList<>();
    private final Tooltip tooltip = new Tooltip();
    private final ScrollArea list = new ScrollArea();
    private Button repair;
    private int left;
    private int top;

    public PlantationFieldScreen(final AbstractTileEntityPlantationField tile, @Nullable final IColonyView colonyOverride)
    {
        super(Component.translatable("block.minecolonies.blockhutplantationfield"));
        this.tile = tile;
        this.colonyOverride = colonyOverride;
        for (final BuildingExtensionRegistries.BuildingExtensionEntry type : tile.getPlantationFieldTypes())
        {
            for (final Function<IBuildingExtension, IBuildingExtensionModule> producer : type.getExtensionModuleProducers())
            {
                if (producer.apply(null) instanceof IPlantationModule module)
                {
                    plants.add(new Plant(new ItemStack(module.getItem()), type));
                }
            }
        }
    }

    @Nullable
    private IColonyView colony()
    {
        if (colonyOverride != null)
        {
            return colonyOverride;
        }
        try
        {
            return tile.getCurrentColony() instanceof IColonyView view ? view : null;
        }
        catch (final RuntimeException e)
        {
            return null;
        }
    }

    /** Who works this part of the field: the first citizen of the plantation that took it; null when free or unknown. */
    @Nullable
    private Component worker(final IColonyView colony, final Plant plant)
    {
        final List<IBuildingExtension> extensions = colony.getClientBuildingManager().getBuildingExtensions(
          extension -> extension.getBuildingExtensionType().equals(plant.type()) && extension.getPosition().equals(tile.getBlockPos()));
        if (extensions.isEmpty() || !extensions.get(0).isTaken())
        {
            return null;
        }
        final IBuildingView building = colony.getClientBuildingManager().getBuilding(extensions.get(0).getBuildingId());
        if (building == null)
        {
            // Taken by a plantation the client doesn't know (another colony's view not loaded).
            return Component.translatable("com.minecolonies.building.plantation");
        }
        final Integer id = building.getAllAssignedCitizens().stream().findFirst().orElse(null);
        final ICitizen citizen = id == null ? null : colony.getCitizen(id);
        return citizen == null ? Component.translatable(building.getBuildingDisplayName()) : Component.literal(citizen.getName());
    }

    @Override
    protected void init()
    {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        final int bw = (WIDTH - 24) / 2;
        repair = addRenderableWidget(Button.builder(Component.translatable("com.minecolonies.coremod.gui.workerhuts.plantationfields.repair"), b -> repair())
                                       .bounds(left + 8, top + HEIGHT - 28, bw, 20).build());
        repair.visible = colony() != null;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + WIDTH - 8 - bw, top + HEIGHT - 28, bw, 20).build());
    }

    private void repair()
    {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
        {
            return;
        }
        new WindowBuildDecoration(tile.getBlockPos(), tile.getPackName(), tile.getBlueprintPath(), tile.getRotationMirror(),
          builder -> new PlantationFieldBuildRequestMessage(WorkOrderType.REPAIR, tile.getBlockPos(), tile.getPackName(), tile.getBlueprintPath(),
            mc.level.dimension(), tile.getRotationMirror(), builder)).open();
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
        tooltip.clear();
        super.render(g, mouseX, mouseY, partialTick);
        try
        {
            renderContent(g, mouseX, mouseY);
        }
        catch (final LinkageError | RuntimeException e)
        {
            HutScreens.report(e);
            onClose();
            return;
        }
        tooltip.render(g, font, mouseX, mouseY);
    }

    private void renderContent(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        g.renderItem(new ItemStack(tile.getBlockState().getBlock().asItem()), left + 8, top + 7);
        g.drawString(font, Gui.fit(font, title, WIDTH - 36), left + 28, top + 11, Gui.LABEL, false);
        final IColonyView colony = colony();
        repair.visible = colony != null;
        if (colony == null)
        {
            int ty = top + 34;
            for (final var line : font.split(Component.translatable("com.minecolonies.coremod.gui.workerhuts.fields.notincolony"), WIDTH - 18))
            {
                g.drawString(font, line, left + 9, ty, Gui.LABEL, false);
                ty += 10;
            }
            return;
        }
        g.drawString(font, Component.translatable("com.minecolonies.coremod.gui.workerhuts.plantationfields.plants"), left + 9, top + 30, Gui.LABEL, false);
        final int listTop = top + 42;
        list.set(left + 8, listTop, WIDTH - 16, top + HEIGHT - 36 - listTop, plants.size() * ROW, ROW);
        list.begin(g);
        for (int i = 0; i < plants.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final Plant plant = plants.get(i);
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            Gui.slot(g, list.listX() + 3, rowY + 3);
            g.renderItem(plant.item(), list.listX() + 4, rowY + 4);
            final int tx = list.listX() + 26;
            final int tw = list.listWidth() - 30;
            g.drawString(font, Gui.fit(font, plant.item().getHoverName(), tw), tx, rowY + 3, Gui.WHITE, true);
            final Component worker = worker(colony, plant);
            g.drawString(font, Gui.fit(font, worker == null ? Component.translatable("mcvanillatweaks.plantation.free")
                                                            : Component.translatable("mcvanillatweaks.plantation.worked_by", worker), tw), tx, rowY + 13,
              worker == null ? Gui.GOLD : Gui.LIST_SOFT, true);
            if (hovered)
            {
                tooltip.set(plant.item(), worker == null ? List.of(Component.translatable("mcvanillatweaks.plantation.free_hint").withStyle(ChatFormatting.GRAY)) : List.of());
            }
        }
        list.end(g);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        return list.mouseClicked(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dx, final double dy)
    {
        return list.mouseDragged(mouseY) || super.mouseDragged(mouseX, mouseY, button, dx, dy);
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
    public boolean isPauseScreen()
    {
        return false;
    }
}
