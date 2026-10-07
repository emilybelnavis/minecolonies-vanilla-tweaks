package fr.mcvanillatweaks.client.hut;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.util.BlockInfo;
import com.minecolonies.api.blocks.AbstractBlockMinecoloniesRack;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.research.IGlobalResearch;
import com.minecolonies.api.research.IGlobalResearchTree;
import com.minecolonies.api.research.ILocalResearch;
import com.minecolonies.api.research.IResearchEffect;
import com.minecolonies.api.research.IResearchRequirement;
import com.minecolonies.api.research.ResearchBranchType;
import com.minecolonies.api.research.requirements.BuildingAlternatesResearchRequirement;
import com.minecolonies.api.research.requirements.BuildingResearchRequirement;
import com.minecolonies.api.research.util.ResearchState;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.university.UniversityScreens;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the build options' "what it brings" page shows for the level about to be built. MineColonies does not show any of
 * it before the work order:
 * <ul>
 *     <li>conditions, checked like the server does when the order is placed: a builder's hut of the target level (except
 *     for the builder's hut itself, which upgrades itself, {@code BuildingBuilder.canBeBuiltByBuilder}), and the hut's
 *     research effect ({@code requestUpgrade}: its strength must be above the current level);</li>
 *     <li>what the next level's blueprint adds compared with the current one (beds, storage, furnaces, crafting tables,
 *     brewing stands, size);</li>
 *     <li>research whose building requirement ({@code hasBuilding}, single or summed levels) becomes met;</li>
 *     <li>for the university, the research tier it opens; for the restaurant, the menu size (5 dishes per level) and the
 *     dishes of the new level.</li>
 * </ul>
 */
final class UpgradeInfo
{
    /** A line of the page: a heading, or an icon and a text. */
    record Line(@Nullable ItemStack icon, Component text, int colour, List<Component> tooltip, boolean heading)
    {
        static Line heading(final Component text)
        {
            return new Line(null, text, Gui.YELLOW, List.of(), true);
        }

        static Line of(final ItemStack icon, final Component text, final int colour, final List<Component> tooltip)
        {
            return new Line(icon, text, colour, tooltip, false);
        }
    }

    /** Blocks counted in the blueprints. */
    enum Feature
    {
        BEDS(Items.RED_BED),
        STORAGE(Items.BARREL),
        FURNACES(Items.FURNACE),
        CRAFTING(Items.CRAFTING_TABLE),
        BREWING(Items.BREWING_STAND),
        SIZE(Items.BRICKS);

        private final ItemStack icon;

        Feature(final net.minecraft.world.item.Item icon)
        {
            this.icon = new ItemStack(icon);
        }

        String key()
        {
            return "mcvanillatweaks.build.gain." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Feature counts read by the dev preview instead of blueprints: current level, then target level. */
    @Nullable
    static int[][] previewCounts;

    private final IBuildingView building;
    private final int target;
    private final List<Line> conditions = new ArrayList<>();
    private final List<Line> gains = new ArrayList<>();
    @Nullable
    private int[] currentCounts;
    @Nullable
    private int[] targetCounts;
    private boolean currentLoaded;
    private boolean targetLoaded;

    UpgradeInfo(final IBuildingView building, final int target)
    {
        this.building = building;
        this.target = target;
        try
        {
            builderCondition();
            researchCondition();
            universityGain();
            restaurantGain();
            researchGains();
        }
        catch (final LinkageError | RuntimeException e)
        {
            // Extra information only: the page shows what could be computed.
            fr.mcvanillatweaks.MineColoniesVanillaTweaks.LOGGER.debug("Upgrade information incomplete", e);
        }
        if (previewCounts != null)
        {
            currentCounts = previewCounts[0];
            targetCounts = previewCounts[1];
            currentLoaded = true;
            targetLoaded = true;
        }
        if (building.getBuildingLevel() == 0)
        {
            // Nothing built yet: the whole blueprint is new.
            currentCounts = new int[Feature.values().length];
            currentLoaded = true;
        }
    }

    // ------------------------------------------------------------------ blueprints

    void currentBlueprint(@Nullable final Blueprint blueprint)
    {
        currentCounts = blueprint == null ? null : count(blueprint);
        currentLoaded = true;
    }

    void targetBlueprint(@Nullable final Blueprint blueprint)
    {
        targetCounts = blueprint == null ? null : count(blueprint);
        targetLoaded = true;
    }

    static int[] count(final Blueprint blueprint)
    {
        final int[] counts = new int[Feature.values().length];
        for (final BlockInfo info : blueprint.getBlockInfoAsList())
        {
            final BlockState state = info.getState();
            if (state == null || state.isAir())
            {
                continue;
            }
            final Block block = state.getBlock();
            // Structurize's placeholders (keep what is there, solid, fluid) are not blocks of the hut.
            if ("structurize".equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace()))
            {
                continue;
            }
            counts[Feature.SIZE.ordinal()]++;
            if (block instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD)
            {
                counts[Feature.BEDS.ordinal()]++;
            }
            else if (block instanceof AbstractBlockMinecoloniesRack || block instanceof ChestBlock || block instanceof BarrelBlock)
            {
                counts[Feature.STORAGE.ordinal()]++;
            }
            else if (block instanceof AbstractFurnaceBlock)
            {
                counts[Feature.FURNACES.ordinal()]++;
            }
            else if (block instanceof CraftingTableBlock)
            {
                counts[Feature.CRAFTING.ordinal()]++;
            }
            else if (block instanceof BrewingStandBlock)
            {
                counts[Feature.BREWING.ordinal()]++;
            }
        }
        return counts;
    }

    // ------------------------------------------------------------------ conditions

    private void builderCondition()
    {
        if (building.getBuildingType() == ModBuildings.builder.get() || target <= building.getBuildingLevel())
        {
            return;
        }
        int best = 0;
        final IColonyView colony = building.getColony();
        if (colony != null)
        {
            for (final IBuildingView view : colony.getClientBuildingManager().getBuildings().values())
            {
                if (view.getBuildingType() == ModBuildings.builder.get())
                {
                    best = Math.max(best, view.getBuildingLevel());
                }
            }
        }
        final ItemStack icon = new ItemStack(ModBuildings.builder.get().getBuildingBlock());
        if (best >= target)
        {
            conditions.add(Line.of(icon, Component.translatable("mcvanillatweaks.build.cond.builder_ok", target), Gui.GREEN, List.of()));
        }
        else
        {
            conditions.add(Line.of(icon, Component.translatable("mcvanillatweaks.build.cond.builder", target), Gui.RED,
              List.of(Component.translatable(best == 0 ? "mcvanillatweaks.build.cond.builder_none" : "mcvanillatweaks.build.cond.builder_best", best)
                        .withStyle(ChatFormatting.GRAY))));
        }
    }

    /** MineColonies' requestUpgrade: some huts need a research whose effect strength is the level they may reach. */
    private void researchCondition()
    {
        final IColonyView colony = building.getColony();
        if (colony == null || building.getBuildingType().getBuildingBlock() == null)
        {
            return;
        }
        final ResourceLocation effect = colony.getResearchManager().getResearchEffectIdFrom(building.getBuildingType().getBuildingBlock());
        final IGlobalResearchTree tree = UniversityScreens.researchTree();
        if (effect == null || !tree.hasResearchEffect(effect))
        {
            return;
        }
        final double strength = colony.getResearchManager().getResearchEffects().getEffectStrength(effect);
        // The research that allows the target level: the weakest one giving enough strength.
        IGlobalResearch needed = null;
        double neededStrength = Double.MAX_VALUE;
        for (final IGlobalResearch research : tree.getResearchForEffect(effect))
        {
            for (final IResearchEffect researchEffect : research.getEffects())
            {
                if (researchEffect.getId().equals(effect) && researchEffect.getEffect() >= target && researchEffect.getEffect() < neededStrength)
                {
                    needed = research;
                    neededStrength = researchEffect.getEffect();
                }
            }
        }
        final Component name = needed == null ? Component.translatable("mcvanillatweaks.build.cond.research_unknown") : MutableComponent.create(needed.getName());
        final ItemStack icon = needed == null ? new ItemStack(Items.BOOK) : UniversityScreens.researchIcon(needed);
        final List<Component> tooltip = needed == null ? List.of()
                                          : List.of(MutableComponent.create(tree.getBranchData(needed.getBranch()).getName()).withStyle(ChatFormatting.GRAY));
        if (strength >= target)
        {
            conditions.add(Line.of(icon, Component.translatable("mcvanillatweaks.build.cond.research_ok", name), Gui.GREEN, tooltip));
        }
        else
        {
            conditions.add(Line.of(icon, Component.translatable("mcvanillatweaks.build.cond.research", name), Gui.RED, tooltip));
        }
    }

    // ------------------------------------------------------------------ gains

    private void universityGain()
    {
        if (building.getBuildingType() != ModBuildings.university.get())
        {
            return;
        }
        final IGlobalResearchTree tree = UniversityScreens.researchTree();
        int count = 0;
        for (final IGlobalResearch research : allResearch(tree))
        {
            if (research.getDepth() == target && tree.getBranchData(research.getBranch()).getType() != ResearchBranchType.UNLOCKABLES && !research.isHidden())
            {
                count++;
            }
        }
        if (count > 0)
        {
            gains.add(Line.of(new ItemStack(Items.BOOKSHELF), Component.translatable("mcvanillatweaks.build.gain.tier", target, count), Gui.WHITE, List.of()));
        }
    }

    private void restaurantGain()
    {
        if (building.getBuildingType() != ModBuildings.cook.get())
        {
            return;
        }
        gains.add(Line.of(new ItemStack(Items.BOWL), Component.translatable("mcvanillatweaks.build.gain.menu", target * 5, 5), Gui.WHITE, List.of()));
        final Set<net.minecraft.world.item.Item> before = new HashSet<>();
        if (building.getBuildingLevel() > 0)
        {
            for (final ItemStorage storage : IColonyManager.getInstance().getCompatibilityManager().getEdibles(building.getBuildingLevel() - 1))
            {
                before.add(storage.getItem());
            }
        }
        final List<Component> names = new ArrayList<>();
        ItemStack first = ItemStack.EMPTY;
        for (final ItemStorage storage : IColonyManager.getInstance().getCompatibilityManager().getEdibles(target - 1))
        {
            if (!before.contains(storage.getItem()))
            {
                names.add(storage.getItemStack().getHoverName().copy().withStyle(ChatFormatting.GRAY));
                if (first.isEmpty())
                {
                    first = storage.getItemStack().copyWithCount(1);
                }
            }
        }
        if (!names.isEmpty())
        {
            gains.add(Line.of(first, Component.translatable("mcvanillatweaks.build.gain.dishes", names.size()), Gui.WHITE,
              names.size() > 20 ? List.copyOf(names.subList(0, 20)) : names));
        }
    }

    /** Research whose requirement on this kind of hut is met once it reaches the target level. */
    private void researchGains()
    {
        final IColonyView colony = building.getColony();
        if (colony == null)
        {
            return;
        }
        final ResourceLocation type = building.getBuildingType().getRegistryName();
        final IGlobalResearchTree tree = UniversityScreens.researchTree();
        final Map<ResourceLocation, IGlobalResearch> unlocked = new LinkedHashMap<>();
        for (final IGlobalResearch research : allResearch(tree))
        {
            final ILocalResearch local = colony.getResearchManager().getResearchTree().getResearch(research.getBranch(), research.getId());
            if (local != null && local.getState() == ResearchState.FINISHED)
            {
                continue;
            }
            for (final IResearchRequirement requirement : research.getResearchRequirements())
            {
                if (requirement instanceof BuildingResearchRequirement single && single.getBuilding().equals(type)
                      && !hasBuilding(colony, type, single.getBuildingLevel(), isSingle(single), false)
                      && hasBuilding(colony, type, single.getBuildingLevel(), isSingle(single), true))
                {
                    unlocked.put(research.getId(), research);
                }
                else if (requirement instanceof BuildingAlternatesResearchRequirement alternates && alternates.getBuildings().contains(type)
                           && !anyBuilding(colony, alternates, false) && anyBuilding(colony, alternates, true))
                {
                    unlocked.put(research.getId(), research);
                }
            }
        }
        for (final IGlobalResearch research : unlocked.values())
        {
            final List<Component> tooltip = new ArrayList<>();
            tooltip.add(MutableComponent.create(tree.getBranchData(research.getBranch()).getName()).withStyle(ChatFormatting.GRAY));
            for (final IResearchRequirement requirement : research.getResearchRequirements())
            {
                tooltip.add(requirement.getDesc().copy().withStyle(ChatFormatting.GRAY));
            }
            gains.add(Line.of(UniversityScreens.researchIcon(research),
              Component.translatable("mcvanillatweaks.build.gain.research", MutableComponent.create(research.getName())), Gui.WHITE, tooltip));
        }
    }

    private static boolean isSingle(final BuildingResearchRequirement requirement)
    {
        try
        {
            final java.lang.reflect.Field field = BuildingResearchRequirement.class.getDeclaredField("singleBuilding");
            field.setAccessible(true);
            return field.getBoolean(requirement);
        }
        catch (final ReflectiveOperationException e)
        {
            return true;
        }
    }

    private boolean anyBuilding(final IColonyView colony, final BuildingAlternatesResearchRequirement requirement, final boolean upgraded)
    {
        for (final ResourceLocation id : requirement.getBuildings())
        {
            if (hasBuilding(colony, id, requirement.getBuildingLevel(), false, upgraded))
            {
                return true;
            }
        }
        return false;
    }

    /** MineColonies' {@code hasBuilding}, with this hut at the target level when {@code upgraded}. */
    private boolean hasBuilding(final IColonyView colony, final ResourceLocation id, final int level, final boolean single, final boolean upgraded)
    {
        int sum = 0;
        for (final IBuildingView view : colony.getClientBuildingManager().getBuildings().values())
        {
            if (!view.getBuildingType().getRegistryName().equals(id))
            {
                continue;
            }
            final int viewLevel = upgraded && view.getPosition().equals(building.getPosition()) ? target : view.getBuildingLevel();
            if (single ? viewLevel >= level : (sum += viewLevel) >= level)
            {
                return true;
            }
        }
        return false;
    }

    private static List<IGlobalResearch> allResearch(final IGlobalResearchTree tree)
    {
        final List<IGlobalResearch> list = new ArrayList<>();
        for (final ResourceLocation branch : tree.getBranches())
        {
            final Deque<ResourceLocation> queue = new ArrayDeque<>(tree.getPrimaryResearch(branch));
            final Set<ResourceLocation> seen = new HashSet<>();
            while (!queue.isEmpty())
            {
                final ResourceLocation id = queue.poll();
                if (!seen.add(id))
                {
                    continue;
                }
                final IGlobalResearch research = tree.getResearch(branch, id);
                if (research != null)
                {
                    list.add(research);
                    queue.addAll(research.getChildren());
                }
            }
        }
        return list;
    }

    // ------------------------------------------------------------------ page

    boolean hasBlockers()
    {
        return conditions.stream().anyMatch(line -> line.colour() == Gui.RED);
    }

    List<Line> lines()
    {
        final List<Line> lines = new ArrayList<>();
        if (!conditions.isEmpty())
        {
            lines.add(Line.heading(Component.translatable("mcvanillatweaks.build.cond.title")));
            lines.addAll(conditions);
        }
        lines.add(Line.heading(Component.translatable("mcvanillatweaks.build.gain.title", target)));
        final int before = lines.size();
        if (!currentLoaded || !targetLoaded)
        {
            lines.add(Line.of(new ItemStack(Items.MAP), Component.translatable("mcvanillatweaks.build.gain.loading"), Gui.LIST_SOFT, List.of()));
        }
        else if (currentCounts != null && targetCounts != null)
        {
            for (final Feature feature : Feature.values())
            {
                final int from = currentCounts[feature.ordinal()];
                final int to = targetCounts[feature.ordinal()];
                if (to != from)
                {
                    final String diff = (to > from ? "+" : "") + (to - from);
                    lines.add(Line.of(feature.icon, Component.translatable(feature.key(), diff, from, to), to > from ? Gui.WHITE : Gui.LIST_SOFT, List.of()));
                }
            }
        }
        lines.addAll(gains);
        if (lines.size() == before)
        {
            lines.add(Line.of(new ItemStack(Items.PAPER), Component.translatable("mcvanillatweaks.build.gain.none"), Gui.LIST_SOFT, List.of()));
        }
        return lines;
    }
}
