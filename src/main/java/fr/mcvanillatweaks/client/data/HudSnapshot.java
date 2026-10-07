package fr.mcvanillatweaks.client.data;

import com.minecolonies.core.colony.buildings.utils.BuildingBuilderResource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Immutable data produced by {@link ResourceHudTracker} a few times per second. The HUD layer only draws it.
 */
public record HudSnapshot(List<Panel> panels)
{
    public static final HudSnapshot EMPTY = new HudSnapshot(List.of());

    public boolean isEmpty()
    {
        return panels.isEmpty();
    }

    public enum State
    {
        /** Resources are known and listed. */
        OK,
        /** The builder has no construction site in progress. */
        NO_WORK_ORDER,
        /** The colony/building view is not synced to this client (e.g. too far from the colony). */
        UNAVAILABLE
    }

    /**
     * One construction site.
     *
     * @param ref             builder this panel belongs to.
     * @param pinned          true if pinned (and therefore movable), false for the scroll held in hand.
     * @param title           scroll name if renamed, otherwise the builder's worker name, otherwise the hut name.
     * @param subtitle        construction name, or null.
     * @param suppliedPercent "Supplied" value of the Resource Scroll, or -1 when the scroll would not show it.
     * @param usedPercent     "Used" value of the Resource Scroll, or -1 when the scroll would not show it.
     * @param hiddenLines     resources not listed because of the line limit.
     */
    public record Panel(
      BuilderRef ref,
      boolean pinned,
      String title,
      @Nullable String subtitle,
      State state,
      int suppliedPercent,
      int usedPercent,
      List<Line> lines,
      int hiddenLines)
    {
    }

    /**
     * One resource line.
     *
     * @param available what the builder's hut holds.
     * @param carried   what the player carries.
     */
    public record Line(ItemStack icon, String name, int available, int carried, int needed, BuildingBuilderResource.RessourceAvailability status)
    {
    }
}
