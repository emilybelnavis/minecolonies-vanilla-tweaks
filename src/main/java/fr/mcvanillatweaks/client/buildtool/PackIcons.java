package fr.mcvanillatweaks.client.buildtool;

import com.ldtteam.structurize.storage.StructurePackMeta;
import com.mojang.blaze3d.platform.NativeImage;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The style packs' own preview pictures (their {@code icon} file, part of the pack, read from its folder like
 * Structurize does), loaded once as dynamic textures.
 */
final class PackIcons
{
    /** Pack name to texture; null when the pack has no readable icon. */
    private static final Map<String, ResourceLocation> ICONS = new HashMap<>();

    private PackIcons()
    {
    }

    @Nullable
    static ResourceLocation icon(final StructurePackMeta pack)
    {
        if (ICONS.containsKey(pack.getName()))
        {
            return ICONS.get(pack.getName());
        }
        ResourceLocation location = null;
        try
        {
            if (!pack.getIconPath().isEmpty())
            {
                final Path path = pack.getPath().resolve(pack.getIconPath());
                try (InputStream in = Files.newInputStream(path))
                {
                    final NativeImage image = NativeImage.read(in);
                    final String id = Integer.toHexString(pack.getName().hashCode()) + "_" + Math.abs(pack.getName().toLowerCase(Locale.ROOT).hashCode() % 1000);
                    location = ResourceLocation.fromNamespaceAndPath(MineColoniesVanillaTweaks.MOD_ID, "dynamic/pack_" + id);
                    Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
                }
            }
        }
        catch (final Exception e)
        {
            MineColoniesVanillaTweaks.LOGGER.debug("No icon for style pack {}", pack.getName(), e);
            location = null;
        }
        ICONS.put(pack.getName(), location);
        return location;
    }
}
