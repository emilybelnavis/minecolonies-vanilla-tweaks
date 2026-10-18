package fr.mcvanillatweaks.client;

import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.names.NameListsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * The mod's settings (key K, and Mods > Config): NeoForge's config screen, whose first page gets one more line opening
 * the citizen name lists, which are not config values.
 */
public final class ModSettings
{
    private ModSettings()
    {
    }

    public static Screen create(final ModContainer container, @Nullable final Screen parent)
    {
        return new ConfigurationScreen(container, parent, Section::new);
    }

    public static void open(@Nullable final Screen parent)
    {
        Minecraft.getInstance().setScreen(create(MineColoniesVanillaTweaks.container(), parent));
    }

    /** The top page of the config (sub-sections are plain NeoForge pages). */
    private static final class Section extends ConfigurationScreen.ConfigurationSectionScreen
    {
        private Section(final Screen parent, final ModConfig.Type type, final ModConfig config, final Component title)
        {
            super(parent, type, config, title);
        }

        @Override
        protected Collection<? extends Element> createSyntheticValues()
        {
            final Button button = Button.builder(Component.translatable("mcvanillatweaks.names.config.button"),
              b -> Minecraft.getInstance().setScreen(new NameListsScreen(this))).width(Button.DEFAULT_WIDTH).build();
            return List.of(new Element(Component.translatable("mcvanillatweaks.names.config"),
              Component.translatable("mcvanillatweaks.names.config.tooltip"), button, false));
        }
    }
}
