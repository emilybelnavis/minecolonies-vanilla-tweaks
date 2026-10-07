package fr.mcvanillatweaks.client.buildtool;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.data.registries.VanillaRegistries;

/** Dev preview of the build tool screens (no world: an empty browser, and the style packs Structurize found). */
public final class BuildToolPreview
{
    private BuildToolPreview()
    {
    }

    public static Screen buildTool()
    {
        return new BuildToolScreen(1, null, VanillaRegistries.createLookup(), true);
    }

    public static Screen packs()
    {
        return new PackScreen(null, pack -> true);
    }
}
