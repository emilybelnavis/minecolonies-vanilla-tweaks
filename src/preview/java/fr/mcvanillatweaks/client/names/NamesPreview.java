package fr.mcvanillatweaks.client.names;

import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.names.NameGenerator;
import fr.mcvanillatweaks.names.NameList;
import fr.mcvanillatweaks.names.NameListsPayload;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Dev preview of {@link NameListsScreen} ({@code hut_names}) and {@link NameListEditScreen} ({@code hut_names_edit*},
 * {@code hut_names_builtin}), {@link IconPickerScreen} ({@code hut_names_icon}),
 * plus log checks of what the preview cannot show: the mixin is applied to MineColonies' CitizenData, the generator
 * avoids taken names, children keep a parent's surname, and the lists survive the trip to the server in parts.
 */
public final class NamesPreview
{
    private static final String SAMPLE = "Exemple";
    private static boolean checked;

    private NamesPreview()
    {
    }

    /** The lists ({@code hut_names}), the sample list selected. */
    public static Screen lists()
    {
        NameListsScreen.preselect(sample().id());
        NameListsScreen.presearch("");
        return new NameListsScreen(new TitleScreen());
    }

    /** The lists filtered by a search that matches names inside lists ({@code hut_names_search}). */
    public static Screen search()
    {
        sample();
        NameListsScreen.presearch("jean");
        return new NameListsScreen(new TitleScreen());
    }

    /**
     * @param custom   the sample list ({@code hut_names_edit*}) or the built-in French one ({@code hut_names_builtin}).
     * @param listTab  the list tab, else the male first names.
     */
    public static Screen edit(final boolean custom, final boolean listTab)
    {
        final NameList sample = sample();
        final NameList french = NameLibrary.get(NameLibrary.PACK_PREFIX + "mcvanillatweaks:french");
        NameListsScreen.presearch("");
        final NameListsScreen parent = new NameListsScreen(new TitleScreen());
        return new NameListEditScreen(parent, custom || french == null ? sample.id() : french.id(), listTab);
    }

    /** The icon picker of the sample list ({@code hut_names_icon}). */
    public static Screen icon()
    {
        final NameList sample = sample();
        final Screen edit = new NameListEditScreen(new NameListsScreen(new TitleScreen()), sample.id(), true);
        return new IconPickerScreen(edit, NameLibrary.item(sample.icon()), item -> {
        });
    }

    private static NameList sample()
    {
        if (!checked)
        {
            checked = true;
            check();
        }
        NameList sample = NameLibrary.all().stream().filter(list -> list.name().equals(SAMPLE)).findFirst().orElse(null);
        if (sample == null)
        {
            sample = NameLibrary.create(SAMPLE, null);
            sample = sample.with(NameList.Column.MALE, List.of("Bob", "Gaston", "Marcel", "Raymond", "Roger"))
              .with(NameList.Column.FEMALE, List.of("Berthe", "Germaine", "Josette", "Odette", "Simone", "Yvette"))
              .with(NameList.Column.SURNAMES, List.of("Lapin", "Le Bris", "Tournesol", "de la Motte"));
            NameLibrary.update(sample);
        }
        if (sample.icon().isEmpty())
        {
            sample = sample.withIcon("minecraft:sunflower");
            NameLibrary.update(sample);
        }
        final NameList french = NameLibrary.get(NameLibrary.PACK_PREFIX + "mcvanillatweaks:french");
        if (french != null)
        {
            NameLibrary.setEnabled(french, true);
        }
        return sample;
    }

    private static void check()
    {
        try
        {
            final Class<?> data = Class.forName("com.minecolonies.core.colony.CitizenData");
            final boolean applied = Arrays.stream(data.getDeclaredMethods()).anyMatch(method -> method.getName().contains("mcvanillatweaks$newName"))
              && Arrays.stream(data.getDeclaredMethods()).anyMatch(method -> method.getName().contains("mcvanillatweaks$childName"));
            MineColoniesVanillaTweaks.LOGGER.info("[names check] mixin applied to CitizenData: {}", applied);
            // Not about names, but the other server side mixin (CookMeals) needs a colony too, so it is checked here.
            final Class<?> eat = Class.forName("com.minecolonies.core.entity.ai.minimal.EntityAIEatTask");
            MineColoniesVanillaTweaks.LOGGER.info("[cook check] mixin applied to EntityAIEatTask: {}",
              Arrays.stream(eat.getDeclaredMethods()).anyMatch(method -> method.getName().contains("mcvanillatweaks$feedCook")));

            final NameList a = new NameList("a", "A", List.of("Jean", "Paul"), List.of("Anne"), List.of("Dupont"));
            final NameList b = new NameList("b", "B", List.of("Ken"), List.of(), List.of());
            final Random random = new Random(1);
            final Set<String> taken = new HashSet<>(Set.of("Jean Dupont"));
            final StringBuilder drawn = new StringBuilder();
            for (int i = 0; i < 6; i++)
            {
                final String name = NameGenerator.newName(random, false, List.of(a, b), List.of("Native"), List.of("Nativeson"), taken);
                drawn.append(name).append(" | ");
                taken.add(name);
            }
            MineColoniesVanillaTweaks.LOGGER.info("[names check] male draws (Jean Dupont taken): {}", drawn);
            MineColoniesVanillaTweaks.LOGGER.info("[names check] female from lists without surnames: {}",
              NameGenerator.newName(random, true, List.of(b), List.of("Nativa"), List.of("Nativeson"), Set.of()));
            MineColoniesVanillaTweaks.LOGGER.info("[names check] child of 'Jean de la Fontaine' and 'Anne M. Dupont': {}",
              NameGenerator.childName(random, true, List.of(a), "Jean de la Fontaine", "Anne M. Dupont", Set.of()));
            MineColoniesVanillaTweaks.LOGGER.info("[names check] no lists: {}", NameGenerator.newName(random, true, List.of(), List.of("X"), List.of("Y"), Set.of()));

            final StringBuilder big = new StringBuilder();
            for (int i = 0; i < 30000; i++)
            {
                big.append(i % 7 == 0 ? "é" : "x");
            }
            final String json = NameListsPayload.toJson(List.of(a.with(NameList.Column.SURNAMES, List.of(big.toString().substring(0, 32), "Été"))));
            final List<NameListsPayload> parts = NameListsPayload.split(json + big);
            final StringBuilder joined = new StringBuilder();
            parts.forEach(part -> joined.append(part.chunk()));
            MineColoniesVanillaTweaks.LOGGER.info("[names check] split into {} parts, joined back intact: {}", parts.size(),
              joined.toString().equals(json + big));

            // A hand-written file: French keys, comments, a column as one string, an icon; then a broken file.
            final Path folder = Files.createTempDirectory("mcvt-names");
            final Path good = folder.resolve("Gaulois.json");
            Files.writeString(good, "﻿" + """
              {
                // mes noms
                titre: "Gaulois",
                "icône": "minecraft:shield",
                "Prénoms masculins": ["Astérix", "Obélix"],
                "femmes": "Falbala, Bonemine",
                "noms": ["Le Gaulois"],
              }""", StandardCharsets.UTF_8);
            final Path bad = folder.resolve("cassé.json");
            Files.writeString(bad, """
              {
                "male": ["A" "B"]
                "female": [
              """, StandardCharsets.UTF_8);
            final NameFiles.Result result = NameFiles.importFiles(List.of(good, bad));
            MineColoniesVanillaTweaks.LOGGER.info("[names check] import: {} | problems: {}",
              result.imported().stream().map(list -> list.name() + " " + list.icon() + " " + list.male() + list.female() + list.surnames()).toList(),
              result.problems().stream().map(Component::getString).toList());
            result.imported().forEach(NameLibrary::delete);
            Files.delete(good);
            Files.delete(bad);
            Files.delete(folder);
        }
        catch (final ReflectiveOperationException | IOException | RuntimeException e)
        {
            MineColoniesVanillaTweaks.LOGGER.error("[names check] failed", e);
        }
    }
}
