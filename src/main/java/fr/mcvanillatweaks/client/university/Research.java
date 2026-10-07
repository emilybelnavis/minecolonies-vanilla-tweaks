package fr.mcvanillatweaks.client.university;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.research.IGlobalResearch;
import com.minecolonies.api.research.IGlobalResearchTree;
import com.minecolonies.api.research.ILocalResearch;
import com.minecolonies.api.research.ILocalResearchTree;
import com.minecolonies.api.research.IResearchEffect;
import com.minecolonies.api.research.ResearchBranchType;
import com.minecolonies.api.research.util.ResearchConstants;
import com.minecolonies.api.research.util.ResearchState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * What the university screens need to know about a research, computed like MineColonies' research tree window: its
 * state for the player, its icon and the time left.
 */
final class Research
{
    /** MineColonies' research button states (its TOO_MANY_PROGRESS and LOCKED are never used). */
    enum State
    {
        AVAILABLE,
        IN_PROGRESS,
        FINISHED,
        ABANDONED,
        MISSING_PARENT,
        MISSING_REQUIREMENT,
        MISSING_COST,
        TOO_LOW_UNIVERSITY;

        boolean locked()
        {
            return this == ABANDONED || this == MISSING_PARENT || this == TOO_LOW_UNIVERSITY;
        }
    }

    /** One research progress unit lasts this long; MineColonies shows hours of 144 units. */
    private static final double UNITS_PER_HOUR = 144.0D;

    private static final Map<ResourceLocation, ItemStack> ICONS = new HashMap<>();

    /** Set by the dev preview, which has no player: research with costs counts as affordable. */
    static boolean previewAffordable;

    private Research()
    {
    }

    static ILocalResearchTree local(final IBuildingView building)
    {
        return building.getColony().getResearchManager().getResearchTree();
    }

    @Nullable
    static ILocalResearch local(final IBuildingView building, final IGlobalResearch research)
    {
        return local(building).getResearch(research.getBranch(), research.getId());
    }

    static ResearchState localState(final IBuildingView building, final IGlobalResearch research)
    {
        final ILocalResearch local = local(building, research);
        return local == null ? ResearchState.NOT_STARTED : local.getState();
    }

    static int progress(final IBuildingView building, final IGlobalResearch research)
    {
        final ILocalResearch local = local(building, research);
        return local == null ? 0 : local.getProgress();
    }

    /** Whether MineColonies' tree shows this research (hidden ones appear once their requirements are met). */
    static boolean visible(final IGlobalResearchTree tree, final IBuildingView building, final IGlobalResearch research)
    {
        if (research.isHidden() && !tree.isResearchRequirementsFulfilled(research.getResearchRequirements(), building.getColony()))
        {
            return false;
        }
        // With "workers always work in rain", the rain research is pointless and hidden unless already started.
        try
        {
            if (IMinecoloniesAPI.getInstance().getConfig().getServer().workersAlwaysWorkInRain.get()
                  && research.getEffects().size() == 1
                  && research.getEffects().get(0).getId().equals(ResearchConstants.WORKING_IN_RAIN)
                  && local(building, research) == null)
            {
                return false;
            }
        }
        catch (final RuntimeException e)
        {
            // server configuration not received
        }
        return true;
    }

    /** MineColonies' getResearchButtonState. */
    static State state(final IGlobalResearchTree tree, final IBuildingView building, final IGlobalResearch research, final boolean abandoned,
      final boolean hasMax)
    {
        final ILocalResearchTree local = local(building);
        final ResearchState localState = localState(building, research);
        final boolean parentResearched = local.hasCompletedResearch(research.getParent());
        final ResearchBranchType type = tree.getBranchData(research.getBranch()).getType();
        if (abandoned)
        {
            return State.ABANDONED;
        }
        if (!parentResearched)
        {
            return State.MISSING_PARENT;
        }
        if (localState == ResearchState.FINISHED)
        {
            return State.FINISHED;
        }
        if (localState == ResearchState.IN_PROGRESS)
        {
            return State.IN_PROGRESS;
        }
        final int level = building.getBuildingLevel();
        final int max = building.getBuildingMaxLevel();
        if (research.getDepth() > level && (research.getDepth() <= max || hasMax || level != max) && type != ResearchBranchType.UNLOCKABLES)
        {
            return State.TOO_LOW_UNIVERSITY;
        }
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isCreative())
        {
            return State.AVAILABLE;
        }
        if (!tree.isResearchRequirementsFulfilled(research.getResearchRequirements(), building.getColony()))
        {
            return State.MISSING_REQUIREMENT;
        }
        return previewAffordable || mc.player != null && research.hasEnoughResources(mc.player, building.getPosition()) ? State.AVAILABLE : State.MISSING_COST;
    }

    /** MineColonies' rule: a not-started research is abandoned when its parent allows one child and another child was taken. */
    static boolean abandoned(final IGlobalResearchTree tree, final IBuildingView building, final IGlobalResearch research, final boolean parentAbandoned)
    {
        if (parentAbandoned)
        {
            return true;
        }
        final ResearchState state = localState(building, research);
        if (research.getDepth() == 1 || state == ResearchState.FINISHED || state == ResearchState.IN_PROGRESS)
        {
            return false;
        }
        final IGlobalResearch parent = tree.getResearch(research.getBranch(), research.getParent());
        return parent != null && parent.hasOnlyChild() && parent.hasResearchedChild(local(building));
    }

    static boolean creativeCompletion()
    {
        try
        {
            return IMinecoloniesAPI.getInstance().getConfig().getServer().researchCreativeCompletion.get();
        }
        catch (final RuntimeException e)
        {
            return false;
        }
    }

    /** Share of the research done, from 0 to 1, like MineColonies' progress bars. */
    static float progressRatio(final IGlobalResearchTree tree, final IGlobalResearch research, final int progress)
    {
        final int base = tree.getBranchData(research.getBranch()).getBaseTime(research.getDepth());
        return base <= 0 ? 1.0F : Math.min(1.0F, (progress + 1) / (float) base);
    }

    /** Time left as MineColonies shows it ("h:mm", rounded up to the quarter hour). */
    static String timeLeft(final IGlobalResearchTree tree, final IGlobalResearch research, final int progress)
    {
        final Minecraft mc = Minecraft.getInstance();
        final boolean creative = mc.player != null && mc.player.isCreative() && creativeCompletion();
        final double toGo = research.isInstant() || creative ? 0.0D : tree.getBranchData(research.getBranch()).getBaseTime(research.getDepth()) - progress;
        final int hours = (int) (toGo / UNITS_PER_HOUR);
        final int increments = (int) Math.ceil(toGo % UNITS_PER_HOUR / 36.0D);
        return increments == 4 ? String.format(Locale.ROOT, "%d:%02d", hours + 1, 0) : String.format(Locale.ROOT, "%d:%02d", hours, increments * 15);
    }

    // ------------------------------------------------------------------ icons

    /**
     * The research's icon: MineColonies' research files name one, but the client does not receive it, so it is read
     * from the file of the mod that provides the research when there is one. Texture icons (MineColonies pictures) and
     * missing ones are replaced by a Minecraft item picturing the research.
     */
    static ItemStack icon(final IGlobalResearch research)
    {
        return ICONS.computeIfAbsent(research.getId(), id -> readIcon(research).orElseGet(() -> fallback(research)));
    }

    private static Optional<ItemStack> readIcon(final IGlobalResearch research)
    {
        try
        {
            final ResourceLocation id = research.getId();
            final var file = ModList.get().getModFileById(id.getNamespace());
            if (file == null)
            {
                return Optional.empty();
            }
            final Path path = file.getFile().findResource(("data/" + id.getNamespace() + "/researches/" + id.getPath() + ".json").split("/"));
            if (!Files.exists(path))
            {
                return Optional.empty();
            }
            try (Reader reader = Files.newBufferedReader(path))
            {
                final JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (!json.has("icon"))
                {
                    return Optional.empty();
                }
                final String icon = json.get("icon").getAsString();
                if (icon.contains("/"))
                {
                    return Optional.of(texture(icon));
                }
                // "namespace:item" or "namespace:item:count"
                final String[] parts = icon.split(":");
                final ResourceLocation itemId = ResourceLocation.tryParse(parts.length >= 2 ? parts[0] + ":" + parts[1] : icon);
                final Item item = itemId == null ? Items.AIR : BuiltInRegistries.ITEM.get(itemId);
                return item == Items.AIR ? Optional.empty() : Optional.of(new ItemStack(item));
            }
        }
        catch (final Exception | LinkageError e)
        {
            return Optional.empty();
        }
    }

    /** MineColonies' research textures ("hp3.png", "speed1.png"...) as Minecraft items. */
    private static ItemStack texture(final String icon)
    {
        final String name = icon.substring(icon.lastIndexOf('/') + 1);
        if (name.startsWith("hp"))
        {
            return new ItemStack(Items.GOLDEN_APPLE);
        }
        if (name.startsWith("speed"))
        {
            return new ItemStack(Items.SUGAR);
        }
        if (name.startsWith("regeneration"))
        {
            return new ItemStack(Items.GHAST_TEAR);
        }
        if (name.startsWith("saturation"))
        {
            return new ItemStack(Items.COOKED_BEEF);
        }
        if (name.startsWith("xpgain"))
        {
            return new ItemStack(Items.EXPERIENCE_BOTTLE);
        }
        if (name.startsWith("happy"))
        {
            return new ItemStack(Items.CAKE);
        }
        if (name.startsWith("childgrowth"))
        {
            return new ItemStack(Items.EGG);
        }
        return new ItemStack(Items.BOOK);
    }

    /** The first item it costs, or its branch's item. */
    private static ItemStack fallback(final IGlobalResearch research)
    {
        for (final SizedIngredient cost : research.getCostList())
        {
            final ItemStack[] items = cost.ingredient().getItems();
            if (items.length > 0)
            {
                return items[0].copyWithCount(1);
            }
        }
        for (final IResearchEffect effect : research.getEffects())
        {
            final String path = effect.getId().getPath();
            if (path.contains("armor") || path.contains("leather"))
            {
                return new ItemStack(Items.IRON_CHESTPLATE);
            }
        }
        return branchIcon(research.getBranch());
    }

    static ItemStack branchIcon(final ResourceLocation branch)
    {
        final String path = branch.getPath();
        if (path.contains("civil"))
        {
            return new ItemStack(Items.BREAD);
        }
        if (path.contains("combat"))
        {
            return new ItemStack(Items.IRON_SWORD);
        }
        if (path.contains("tech"))
        {
            return new ItemStack(Items.REDSTONE);
        }
        if (path.contains("unlock"))
        {
            return new ItemStack(Items.TRIPWIRE_HOOK);
        }
        return new ItemStack(Items.BOOK);
    }
}
