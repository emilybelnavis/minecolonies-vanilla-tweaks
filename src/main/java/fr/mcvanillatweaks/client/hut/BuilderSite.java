package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.workorders.IWorkOrderView;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.core.colony.buildings.moduleviews.BuildingResourcesModuleView;
import com.minecolonies.core.colony.buildings.utils.BuildingBuilderResource;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.network.messages.server.colony.building.MarkBuildingDirtyMessage;
import fr.mcvanillatweaks.client.data.BuilderRef;
import fr.mcvanillatweaks.client.data.ResourceHudTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What a builder is working on, computed like MineColonies' resource list (WindowBuilderResModule and the Resource
 * Scroll): the resources of its current work order, as copies with the player's inventory and the deliveries on their
 * way added, sorted with MineColonies' own comparator. Shared by the builder's home card and its resources page.
 */
final class BuilderSite
{
    private static final BuildingBuilderResource.ResourceComparator COMPARATOR = new BuildingBuilderResource.ResourceComparator();

    final BuildingResourcesModuleView module;
    @Nullable
    final IWorkOrderView workOrder;
    final List<BuildingBuilderResource> resources;
    /** Share of the resources already in the hut, or -1 without resources. */
    final int supplied;
    /** Share of the resources used on the site (MineColonies' "used"), or -1 without resources. */
    final int used;

    private BuilderSite(final BuildingResourcesModuleView module, @Nullable final IWorkOrderView workOrder, final List<BuildingBuilderResource> resources,
      final int supplied, final int used)
    {
        this.module = module;
        this.workOrder = workOrder;
        this.resources = resources;
        this.supplied = supplied;
        this.used = used;
    }

    @Nullable
    static BuildingResourcesModuleView module(final IBuildingView building)
    {
        return building.getModuleViewByType(BuildingResourcesModuleView.class);
    }

    static BuilderSite of(final IBuildingView building, final BuildingResourcesModuleView module)
    {
        final IColonyView colony = building.getColony();
        final IWorkOrderView workOrder = colony == null || module.getWorkOrderId() < 0 ? null : colony.getWorkOrder(module.getWorkOrderId());
        final LocalPlayer player = Minecraft.getInstance().player;
        final List<Delivery> deliveries = colony != null && building instanceof BuildingBuilder.View builder
                                            ? ResourceHudTracker.collectDeliveries(builder, colony)
                                            : List.of();
        final List<BuildingBuilderResource> resources = new ArrayList<>();
        double supplied = 0;
        double total = 0;
        for (final BuildingBuilderResource original : module.getResources().values())
        {
            final BuildingBuilderResource copy = new BuildingBuilderResource(original.getItemStack(), original.getAmount(), original.getAvailable());
            if (player != null)
            {
                copy.setPlayerAmount(player.isCreative()
                                       ? original.getAmount()
                                       : InventoryUtils.getItemCountInItemHandler(new InvWrapper(player.getInventory()),
                                         stack -> !ItemStackUtils.isEmpty(stack) && ItemStackUtils.compareItemStacksIgnoreStackSize(stack, original.getItemStack())));
            }
            int inDelivery = 0;
            for (final Delivery delivery : deliveries)
            {
                if (ItemStackUtils.compareItemStacksIgnoreStackSize(original.getItemStack(), delivery.getStack(), false, true))
                {
                    inDelivery += delivery.getStack().getCount();
                }
            }
            copy.setAmountInDelivery(inDelivery);
            supplied += Math.min(original.getAvailable(), original.getAmount());
            total += original.getAmount();
            resources.add(copy);
        }
        resources.sort(COMPARATOR);
        return new BuilderSite(module, workOrder, resources, total > 0 ? (int) (supplied / total * 100.0D) : -1, total > 0 ? module.getProgress() : -1);
    }

    /** The work order's name on one line, as MineColonies shows it. */
    @Nullable
    Component name()
    {
        return workOrder == null ? null : Component.literal(workOrder.getDisplayName().getString().replace("\n", " "));
    }

    /**
     * Asks the server for fresh resources, like MineColonies' window when it opens. Only sent with the permission the
     * server checks, so it never posts "permission denied" in the chat.
     */
    static void refresh(final IBuildingView building)
    {
        final LocalPlayer player = Minecraft.getInstance().player;
        final IColonyView colony = building.getColony();
        if (player != null && colony != null && colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS))
        {
            new MarkBuildingDirtyMessage(building).sendToServer();
        }
    }

    @Nullable
    static BuilderRef ref(final IBuildingView building)
    {
        final IColonyView colony = building.getColony();
        return colony == null || colony.getDimension() == null ? null : new BuilderRef(colony.getDimension(), colony.getID(), building.getPosition());
    }
}
