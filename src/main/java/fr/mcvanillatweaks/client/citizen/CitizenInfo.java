package fr.mcvanillatweaks.client.citizen;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import com.minecolonies.core.debug.DebugPlayerManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads citizen information the same way MineColonies' own citizen windows do (see {@code AbstractWindowCitizen},
 * {@code CitizenWindowUtils} and {@code RequestWindowCitizen}), from the client-side colony views.
 */
public final class CitizenInfo
{
    /** Citizen levels go up to this value ({@code CitizenConstants.MAX_CITIZEN_LEVEL}). */
    public static final int MAX_SKILL_LEVEL = 99;
    /** {@code CitizenConstants.FULL_SATURATION}. */
    public static final double FULL_SATURATION = 60.0D;
    public static final double MAX_HAPPINESS = 10.0D;

    private CitizenInfo()
    {
    }

    /** The most recent view of the citizen: MineColonies may replace it when the colony is resynchronised. */
    public static ICitizenDataView refresh(final ICitizenDataView citizen)
    {
        final IColonyView colony = citizen.getColony();
        if (colony != null)
        {
            final ICitizenDataView current = colony.getCitizen(citizen.getId());
            if (current != null)
            {
                return current;
            }
        }
        return citizen;
    }

    /** The citizen entity, when it is loaded on the client. */
    @Nullable
    public static LivingEntity entity(final ICitizenDataView citizen)
    {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
        {
            return null;
        }
        final Entity entity = mc.level.getEntity(citizen.getEntityId());
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    @Nullable
    public static IBuildingView building(final ICitizenDataView citizen, @Nullable final BlockPos pos)
    {
        final IColonyView colony = citizen.getColony();
        if (colony == null || pos == null || pos.equals(BlockPos.ZERO))
        {
            return null;
        }
        return colony.getClientBuildingManager().getBuilding(pos);
    }

    /** Same rule as MineColonies' job tab: a work building that is not the library. */
    public static boolean hasJobPage(final ICitizenDataView citizen)
    {
        final IBuildingView building = building(citizen, citizen.getWorkBuilding());
        return building instanceof AbstractBuildingView && building.getBuildingType() != ModBuildings.library.get();
    }

    public static boolean debugEnabled()
    {
        final Minecraft mc = Minecraft.getInstance();
        return mc.player != null && DebugPlayerManager.hasDebugEnabled(mc.player);
    }

    public static boolean isEmployed(final ICitizenDataView citizen)
    {
        final String job = citizen.getJob();
        return job != null && !job.isBlank();
    }

    public static Component jobName(final ICitizenDataView citizen)
    {
        return isEmployed(citizen) ? citizen.getJobComponent() : Component.translatable("mcvanillatweaks.citizen.unemployed");
    }

    /** "Building (level n)", or {@code none} when the citizen has no such building. */
    public static Component buildingName(final ICitizenDataView citizen, @Nullable final BlockPos pos, final String none)
    {
        final IBuildingView building = building(citizen, pos);
        if (building == null)
        {
            return Component.translatable(none);
        }
        return Component.translatable("mcvanillatweaks.citizen.building",
          Component.translatable(building.getBuildingDisplayName()), building.getBuildingLevel());
    }

    public static Component condition(final ICitizenDataView citizen)
    {
        final String key;
        if (citizen.isSick())
        {
            key = "mcvanillatweaks.citizen.status.sick";
        }
        else if (citizen.isChild())
        {
            key = "mcvanillatweaks.citizen.status.child";
        }
        else if (citizen.isPaused())
        {
            key = "mcvanillatweaks.citizen.status.paused";
        }
        else
        {
            key = "mcvanillatweaks.citizen.status.fine";
        }
        return Component.translatable(key);
    }

    public static Component skillName(final Skill skill)
    {
        return Component.translatable("com.minecolonies.coremod.gui.citizen.skills." + skill.name().toLowerCase(Locale.US));
    }

    /** A value with at most one decimal ("8", "8.5"). */
    public static String format(final double value)
    {
        final double rounded = Math.round(value * 10.0D) / 10.0D;
        return rounded == Math.rint(rounded) ? Integer.toString((int) rounded) : String.format(Locale.ROOT, "%.1f", rounded);
    }

    public static int skillLevel(final ICitizenDataView citizen, final Skill skill)
    {
        return citizen.getCitizenSkillHandler().getLevel(skill);
    }

    /** Primary and secondary skill of the citizen's job, as shown on MineColonies' job page. */
    public record JobSkills(Skill primary, Skill secondary)
    {
    }

    @Nullable
    public static JobSkills jobSkills(final ICitizenDataView citizen)
    {
        if (!hasJobPage(citizen) || citizen.getJobView() == null)
        {
            return null;
        }
        final IBuildingView building = building(citizen, citizen.getWorkBuilding());
        if (building == null)
        {
            return null;
        }
        final WorkerBuildingModuleView module = building.getModuleViewMatching(WorkerBuildingModuleView.class,
          m -> m.getJobEntry() == citizen.getJobView().getEntry());
        if (module == null || module.getPrimarySkill() == null || module.getSecondarySkill() == null)
        {
            return null;
        }
        return new JobSkills(module.getPrimarySkill(), module.getSecondarySkill());
    }

    /**
     * Share of the job experience each skill receives, exactly the percentages of MineColonies' job page: primary 100%,
     * its complementary 10% and adverse -10%; secondary 50%, its complementary 5% and adverse -5%.
     */
    public static Map<Skill, List<Integer>> xpShares(@Nullable final JobSkills skills)
    {
        final Map<Skill, List<Integer>> shares = new EnumMap<>(Skill.class);
        if (skills == null)
        {
            return shares;
        }
        add(shares, skills.primary(), 100);
        if (skills.primary().getComplimentary() != null && skills.primary().getAdverse() != null)
        {
            add(shares, skills.primary().getComplimentary(), 10);
            add(shares, skills.primary().getAdverse(), -10);
        }
        add(shares, skills.secondary(), 50);
        if (skills.secondary().getComplimentary() != null && skills.secondary().getAdverse() != null)
        {
            add(shares, skills.secondary().getComplimentary(), 5);
            add(shares, skills.secondary().getAdverse(), -5);
        }
        return shares;
    }

    private static void add(final Map<Skill, List<Integer>> shares, final Skill skill, final int percent)
    {
        shares.computeIfAbsent(skill, s -> new ArrayList<>()).add(percent);
    }

    /** Rows of the citizen inventory, as laid out by MineColonies' inventory menu (3 to 8). */
    public static int inventoryRows(final ICitizenDataView citizen)
    {
        int rows = 3;
        try
        {
            if (citizen.getInventory() != null)
            {
                rows = citizen.getInventory().getSlots() / 9;
            }
        }
        catch (final RuntimeException ignored)
        {
            // keep the default
        }
        return Math.max(3, Math.min(8, rows));
    }

    /** A request and its depth in the request tree (children are the sub-requests created to resolve it). */
    public record RequestNode(IRequest<?> request, int depth)
    {
    }

    /**
     * Open requests of the citizen, as a flattened tree: same source as {@code RequestWindowCitizen} (requests of the
     * citizen and of the building itself) and same traversal as {@code RequestTreeWindowModule}.
     */
    public static List<RequestNode> openRequests(final ICitizenDataView citizen)
    {
        final IColonyView colony = citizen.getColony();
        final IBuildingView building = building(citizen, citizen.getWorkBuilding());
        if (colony == null || building == null)
        {
            return List.of();
        }
        final Map<Integer, Collection<IToken<?>>> byCitizen = building.getOpenRequestsByCitizen();
        final List<RequestNode> nodes = new ArrayList<>();
        for (final int owner : new int[] {citizen.getId(), -1})
        {
            for (final IToken<?> token : byCitizen.getOrDefault(owner, Collections.emptyList()))
            {
                final IRequest<?> request = request(colony, token);
                if (request != null)
                {
                    addTree(colony, request, nodes, 0);
                }
            }
        }
        return nodes;
    }

    private static void addTree(final IColonyView colony, final IRequest<?> request, final List<RequestNode> nodes, final int depth)
    {
        nodes.add(new RequestNode(request, depth));
        if (request.hasChildren() && depth < 16)
        {
            for (final IToken<?> child : request.getChildren())
            {
                final IRequest<?> childRequest = request(colony, child);
                if (childRequest != null)
                {
                    addTree(colony, childRequest, nodes, depth + 1);
                }
            }
        }
    }

    @Nullable
    private static IRequest<?> request(final IColonyView colony, @Nullable final IToken<?> token)
    {
        if (token == null)
        {
            return null;
        }
        try
        {
            return colony.getRequestManager().getRequestForToken(token);
        }
        catch (final IllegalArgumentException e)
        {
            return null;
        }
    }
}
