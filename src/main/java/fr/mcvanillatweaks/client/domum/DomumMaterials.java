package fr.mcvanillatweaks.client.domum;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.item.interfaces.IDoItem;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * The materials a Domum Ornamentum block is cut from, on one line ("Frame: Oak Planks · Center: White Terracotta"), so the
 * resource scroll says what to put in the Architect's Cutter without hovering the item.
 */
public final class DomumMaterials
{
    private static boolean failed;

    private DomumMaterials()
    {
    }

    /** {@code null} for anything that is not a textured Domum block, or whose materials are not set. */
    @Nullable
    public static Component describe(final ItemStack stack)
    {
        if (failed || stack.isEmpty() || !(stack.getItem() instanceof IDoItem item))
        {
            return null;
        }
        try
        {
            final MaterialTextureData data = MaterialTextureData.readFromItemStack(stack);
            if (data == null || data.isEmpty())
            {
                return null;
            }
            // Materials are keyed by the block's components (their default texture), which come in the order of the
            // cutter's input slots.
            if (!(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof IMateriallyTexturedBlock block))
            {
                return null;
            }
            final Map<ResourceLocation, Block> materials = data.getTexturedComponents();
            final List<ResourceLocation> inputs = item.getInputIds();
            MutableComponent text = null;
            int index = 0;
            for (final IMateriallyTexturedBlockComponent component : block.getComponents())
            {
                final ResourceLocation input = index < inputs.size() ? inputs.get(index) : null;
                index++;
                final Block material = materials.getOrDefault(component.getId(), component.isOptional() ? null : component.getDefault());
                if (material == null)
                {
                    continue;
                }
                final MutableComponent part = block.getComponents().size() == 1 || input == null
                                                ? name(material).copy()
                                                : Component.translatable("mcvanillatweaks.cutter.material_pair", label(input), name(material));
                if (text == null)
                {
                    text = part;
                }
                else
                {
                    text.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY)).append(part);
                }
            }
            return text;
        }
        catch (LinkageError | RuntimeException e)
        {
            failed = true;
            MineColoniesVanillaTweaks.LOGGER.warn("Domum Ornamentum materials unavailable", e);
            return null;
        }
    }

    /**
     * The material's item name: Domum's own blocks (bricks "extra", ...) are only named through their item, their block
     * key has no translation.
     */
    private static Component name(final Block material)
    {
        final ItemStack stack = new ItemStack(material);
        return stack.isEmpty() ? material.getName() : stack.getHoverName();
    }

    private static Component label(final ResourceLocation id)
    {
        final String key = "mcvanillatweaks.cutter.input." + id.getPath();
        return I18n.exists(key) ? Component.translatable(key) : Component.literal(id.getPath());
    }
}
