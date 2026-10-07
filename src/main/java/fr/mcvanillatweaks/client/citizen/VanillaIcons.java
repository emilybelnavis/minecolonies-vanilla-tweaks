package fr.mcvanillatweaks.client.citizen;

import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Minecraft pictures for what MineColonies draws with its own icons: items for skills and citizen activities, vanilla
 * status effect icons for moods. The user wants every picture of the screens to be a Minecraft one.
 */
public final class VanillaIcons
{
    /** A mood, from a happiness modifier factor or a citizen's happiness. */
    public enum Mood
    {
        HAPPY(MobEffects.HERO_OF_THE_VILLAGE),
        SATISFIED(MobEffects.LUCK),
        UNSATISFIED(MobEffects.UNLUCK),
        UNHAPPY(MobEffects.BAD_OMEN);

        private final Holder<MobEffect> effect;

        Mood(final Holder<MobEffect> effect)
        {
            this.effect = effect;
        }

        /** MineColonies' thresholds for a happiness modifier factor. */
        public static Mood ofFactor(final double factor)
        {
            if (factor > 1.0D)
            {
                return HAPPY;
            }
            if (factor == 1.0D)
            {
                return SATISFIED;
            }
            return factor > 0.75D ? UNSATISFIED : UNHAPPY;
        }
    }

    private static final Map<Skill, ItemStack> SKILLS = new EnumMap<>(Skill.class);
    /** Activities by the end of their translation key ("com.minecolonies.gui.visiblestatus.<name>"). */
    private static final Map<String, Item> STATUSES = new HashMap<>();
    private static final Map<String, ItemStack> STATUS_STACKS = new HashMap<>();

    static
    {
        SKILLS.put(Skill.Athletics, new ItemStack(Items.LEATHER_BOOTS));
        SKILLS.put(Skill.Dexterity, new ItemStack(Items.SHEARS));
        SKILLS.put(Skill.Strength, new ItemStack(Items.ANVIL));
        SKILLS.put(Skill.Agility, new ItemStack(Items.FEATHER));
        SKILLS.put(Skill.Stamina, new ItemStack(Items.COOKED_BEEF));
        SKILLS.put(Skill.Mana, new ItemStack(Items.LAPIS_LAZULI));
        SKILLS.put(Skill.Adaptability, new ItemStack(Items.COMPASS));
        SKILLS.put(Skill.Focus, new ItemStack(Items.SPYGLASS));
        SKILLS.put(Skill.Creativity, new ItemStack(Items.PAINTING));
        SKILLS.put(Skill.Knowledge, new ItemStack(Items.BOOK));
        SKILLS.put(Skill.Intelligence, new ItemStack(Items.ENCHANTED_BOOK));

        STATUSES.put("archer_combat", Items.BOW);
        STATUSES.put("archer_uni", Items.TARGET);
        STATUSES.put("baker", Items.BREAD);
        STATUSES.put("burying", Items.IRON_SHOVEL);
        STATUSES.put("chickenherder", Items.EGG);
        STATUSES.put("composter", Items.COMPOSTER);
        STATUSES.put("cook", Items.COOKED_BEEF);
        STATUSES.put("cowboy", Items.MILK_BUCKET);
        STATUSES.put("crusher", Items.GRAVEL);
        STATUSES.put("delivery", Items.CHEST);
        STATUSES.put("digging", Items.IRON_SHOVEL);
        STATUSES.put("eat", Items.BREAD);
        STATUSES.put("emptying", Items.HOPPER);
        STATUSES.put("farmer", Items.WHEAT);
        STATUSES.put("florist", Items.POPPY);
        STATUSES.put("idle", Items.OAK_DOOR);
        STATUSES.put("knight_combat", Items.IRON_SWORD);
        STATUSES.put("lumberjack_search", Items.IRON_AXE);
        STATUSES.put("miner", Items.IRON_PICKAXE);
        STATUSES.put("mourn", Items.WITHER_ROSE);
        STATUSES.put("raid", Items.CROSSBOW);
        STATUSES.put("rain", Items.WATER_BUCKET);
        STATUSES.put("sawmill", Items.OAK_PLANKS);
        STATUSES.put("sick", Items.FERMENTED_SPIDER_EYE);
        STATUSES.put("sleep", Items.RED_BED);
        STATUSES.put("smelting", Items.FURNACE);
        STATUSES.put("stablemaster", Items.SADDLE);
        STATUSES.put("teacher_student", Items.BOOK);
        STATUSES.put("working", Items.IRON_PICKAXE);
    }

    private VanillaIcons()
    {
    }

    public static ItemStack skill(final Skill skill)
    {
        return SKILLS.getOrDefault(skill, ItemStack.EMPTY);
    }

    /** The item picturing a citizen activity; a clock for activities added after this list was written. */
    public static ItemStack status(@Nullable final VisibleCitizenStatus status)
    {
        if (status == null)
        {
            return ItemStack.EMPTY;
        }
        final String key = status.getTranslationKey() == null ? "" : status.getTranslationKey();
        final String name = key.substring(key.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return STATUS_STACKS.computeIfAbsent(name, n -> new ItemStack(STATUSES.getOrDefault(n, Items.CLOCK)));
    }

    /** What a request without items shows, guessed from MineColonies' icon name. */
    public static ItemStack request(final ResourceLocation icon)
    {
        final String path = icon.getPath();
        if (path.contains("food"))
        {
            return new ItemStack(Items.BREAD);
        }
        if (path.contains("armor"))
        {
            return new ItemStack(Items.IRON_CHESTPLATE);
        }
        if (path.contains("weapon") || path.contains("sword"))
        {
            return new ItemStack(Items.IRON_SWORD);
        }
        if (path.contains("tool") || path.contains("pick"))
        {
            return new ItemStack(Items.IRON_PICKAXE);
        }
        return new ItemStack(Items.CHEST);
    }

    /** An item drawn at {@code size} pixels (16 is the vanilla size) with its top-left corner at (x, y). */
    public static void item(final GuiGraphics g, final ItemStack stack, final int x, final int y, final int size)
    {
        if (stack.isEmpty())
        {
            return;
        }
        if (size == 16)
        {
            g.renderItem(stack, x, y);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(size / 16.0F, size / 16.0F, 1.0F);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    /** The vanilla status effect icon of a mood, scaled to {@code size}. */
    public static void mood(final GuiGraphics g, final Mood mood, final int x, final int y, final int size)
    {
        g.blit(x, y, 0, size, size, Minecraft.getInstance().getMobEffectTextures().get(mood.effect));
    }
}
