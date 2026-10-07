package fr.mcvanillatweaks.client.data;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.items.component.BuildingId;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.core.items.ItemResourceScroll;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Identifies a builder hut the same way a Resource Scroll does: colony (id + dimension) and hut position.
 */
public record BuilderRef(ResourceKey<Level> dimension, int colonyId, BlockPos pos)
{
    private static final String SEPARATOR = "|";

    /**
     * Reads the builder a Resource Scroll is linked to, from the data components MineColonies stores on it.
     */
    public static Optional<BuilderRef> fromScroll(final ItemStack stack)
    {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemResourceScroll))
        {
            return Optional.empty();
        }

        final ColonyId colonyId = ColonyId.readFromItemStack(stack);
        final BuildingId buildingId = BuildingId.readFromItemStack(stack);
        if (!colonyId.hasColonyId() || !buildingId.hasId() || colonyId.dimension() == null)
        {
            return Optional.empty();
        }
        return Optional.of(new BuilderRef(colonyId.dimension(), colonyId.id(), buildingId.id().immutable()));
    }

    /**
     * Resolves the client-side building view, exactly like {@link BuildingId#readBuildingViewFromItemStack}.
     */
    @Nullable
    public IBuildingView resolve()
    {
        final IColonyView colony = colonyView();
        return colony == null ? null : colony.getClientBuildingManager().getBuilding(pos);
    }

    @Nullable
    public IColonyView colonyView()
    {
        return IColonyManager.getInstance().getColonyView(colonyId, dimension);
    }

    public String serialize()
    {
        return String.join(SEPARATOR,
          dimension.location().toString(),
          Integer.toString(colonyId),
          Integer.toString(pos.getX()),
          Integer.toString(pos.getY()),
          Integer.toString(pos.getZ()));
    }

    public static Optional<BuilderRef> deserialize(final String value)
    {
        final String[] parts = value.split("\\" + SEPARATOR);
        if (parts.length != 5)
        {
            return Optional.empty();
        }
        try
        {
            final ResourceLocation dim = ResourceLocation.parse(parts[0]);
            return Optional.of(new BuilderRef(
              ResourceKey.create(Registries.DIMENSION, dim),
              Integer.parseInt(parts[1]),
              new BlockPos(Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), Integer.parseInt(parts[4]))));
        }
        catch (final RuntimeException e)
        {
            return Optional.empty();
        }
    }
}
