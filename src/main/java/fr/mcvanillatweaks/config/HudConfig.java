package fr.mcvanillatweaks.config;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

import java.util.Locale;

/**
 * Client configuration (config/mcvanillatweaks-client.toml, editable in game with K or from the Mods menu), in
 * sections that NeoForge's configuration screen shows as sub-menus: screens, resource HUD (layout, content), beams.
 * {@link ConfigMigration} carries over the values of the flat file written by 1.5.0 and earlier.
 */
public final class HudConfig
{
    public enum Anchor implements TranslatableEnum
    {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT;

        public boolean isRight()
        {
            return this == TOP_RIGHT || this == BOTTOM_RIGHT;
        }

        public boolean isBottom()
        {
            return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
        }

        /** Shown by the settings screen instead of the constant name. */
        @Override
        public Component getTranslatedName()
        {
            return Component.translatable(KEY + "position." + name().toLowerCase(Locale.ROOT));
        }
    }

    /** What a click on a builder's "finished" message does ({@code CompletionMessages}). */
    public enum CompletionClick implements TranslatableEnum
    {
        BOTH,
        BEAM,
        POSITION,
        OFF;

        public boolean beam()
        {
            return this == BOTH || this == BEAM;
        }

        public boolean position()
        {
            return this == BOTH || this == POSITION;
        }

        @Override
        public Component getTranslatedName()
        {
            return Component.translatable(KEY + "completionClick." + name().toLowerCase(Locale.ROOT));
        }
    }

    private static final String KEY = "mcvanillatweaks.configuration.";

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.EnumValue<Anchor>  POSITION;
    public static final ModConfigSpec.IntValue           OFFSET_X;
    public static final ModConfigSpec.IntValue           OFFSET_Y;
    public static final ModConfigSpec.DoubleValue        SCALE;
    public static final ModConfigSpec.BooleanValue       SHOW_COMPLETED;
    public static final ModConfigSpec.BooleanValue       SHOW_ICONS;
    public static final ModConfigSpec.BooleanValue       SHOW_PERCENTAGE;
    public static final ModConfigSpec.BooleanValue       SHOW_CONSTRUCTION;
    public static final ModConfigSpec.BooleanValue       SHOW_HELD_SCROLL;
    public static final ModConfigSpec.IntValue           MAX_LINES;
    public static final ModConfigSpec.IntValue           MAX_PANELS;
    public static final ModConfigSpec.IntValue           REFRESH_SECONDS;
    public static final ModConfigSpec.BooleanValue       VANILLA_CITIZEN_SCREEN;
    public static final ModConfigSpec.BooleanValue       VANILLA_TOWN_HALL_SCREEN;
    public static final ModConfigSpec.BooleanValue       VANILLA_UNIVERSITY_SCREEN;
    public static final ModConfigSpec.BooleanValue       VANILLA_HUT_SCREEN;
    public static final ModConfigSpec.BooleanValue       VANILLA_BUILD_TOOL;
    public static final ModConfigSpec.BooleanValue       VANILLA_ARCHITECTS_CUTTER;
    public static final ModConfigSpec.BooleanValue       VANILLA_DIALOGS;
    public static final ModConfigSpec.BooleanValue       DEATH_BEAMS;
    public static final ModConfigSpec.EnumValue<CompletionClick> COMPLETION_CLICK;
    public static final ModConfigSpec.BooleanValue       COLONY_GUIDE_MESSAGE;

    static
    {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Vanilla-style screens replacing MineColonies' windows. Turn one off to get MineColonies' own windows back.")
          .translation(KEY + "screens")
          .push("screens");
        VANILLA_CITIZEN_SCREEN = builder
                                   .comment("Replace MineColonies' citizen windows with the vanilla-style citizen screen (tabs, portrait, equipment, inventory).")
                                   .translation(KEY + "vanillaCitizenScreen")
                                   .define("vanillaCitizenScreen", true);
        VANILLA_TOWN_HALL_SCREEN = builder
                                     .comment("Replace MineColonies' town hall windows with the vanilla-style town hall screen.")
                                     .translation(KEY + "vanillaTownHallScreen")
                                     .define("vanillaTownHallScreen", true);
        VANILLA_HUT_SCREEN = builder
                               .comment("Replace the hut windows every MineColonies building shares (home page, workers, settings, statistics) with a vanilla-style screen.")
                               .translation(KEY + "vanillaHutScreen")
                               .define("vanillaHutScreen", true);
        VANILLA_UNIVERSITY_SCREEN = builder
                                      .comment("Replace MineColonies' university windows (hut page, research branches, research trees) with vanilla-style screens.")
                                      .translation(KEY + "vanillaUniversityScreen")
                                      .define("vanillaUniversityScreen", true);
        VANILLA_BUILD_TOOL = builder
                               .comment("Replace the build tool windows (blueprint browser, style choice) with vanilla-style screens.")
                               .translation(KEY + "vanillaBuildTool")
                               .define("vanillaBuildTool", true);
        VANILLA_ARCHITECTS_CUTTER = builder
                                      .comment("Replace Domum Ornamentum's Architect's Cutter screen with a larger vanilla-style screen (named groups, variant grid).")
                                      .translation(KEY + "vanillaArchitectsCutter")
                                      .define("vanillaArchitectsCutter", true);
        VANILLA_DIALOGS = builder
                            .comment("Replace MineColonies' other windows (colony founding, confirmations, abandoned huts, supply camp, scan tools, decorations, graves...) with vanilla-style screens.")
                            .translation(KEY + "vanillaDialogs")
                            .define("vanillaDialogs", true);
        builder.pop();

        builder.comment("The resource HUD: panels of the pinned builders (J pins the held Resource Scroll, P arranges the panels, H shows or hides them).")
          .translation(KEY + "hud")
          .push("hud");

        builder.comment("Where the panels are drawn and how big.")
          .translation(KEY + "hud.layout")
          .push("layout");
        POSITION = builder
                     .comment("Default screen corner of the HUD. Pinned panels can be moved anywhere with the layout editor (P).")
                     .translation(KEY + "position")
                     .defineEnum("position", Anchor.TOP_LEFT);
        OFFSET_X = builder
                     .comment("Horizontal distance from the screen edge, in GUI pixels.")
                     .translation(KEY + "offsetX")
                     .defineInRange("offsetX", 4, 0, 1000);
        OFFSET_Y = builder
                     .comment("Vertical distance from the screen edge, in GUI pixels.")
                     .translation(KEY + "offsetY")
                     .defineInRange("offsetY", 4, 0, 1000);
        SCALE = builder
                  .comment("HUD size multiplier.")
                  .translation(KEY + "scale")
                  .defineInRange("scale", 1.0D, 0.5D, 2.0D);
        builder.pop();

        builder.comment("What each panel shows.")
          .translation(KEY + "hud.content")
          .push("content");
        SHOW_HELD_SCROLL = builder
                             .comment("Also show the construction site of the Resource Scroll held in hand, even if it is not pinned.")
                             .translation(KEY + "showHeldScroll")
                             .define("showHeldScroll", true);
        SHOW_CONSTRUCTION = builder
                              .comment("Show how far the construction is (share of the blueprint's materials already placed, the Resource Scroll's 'Used' value) with a progress bar.")
                              .translation(KEY + "showConstruction")
                              .define("showConstruction", true);
        SHOW_PERCENTAGE = builder
                            .comment("Show the 'Supplied % / Used %' line, same values as the Resource Scroll.")
                            .translation(KEY + "showPercentage")
                            .define("showPercentage", true);
        SHOW_COMPLETED = builder
                           .comment("Also list resources the builder already has in sufficient quantity.")
                           .translation(KEY + "showCompleted")
                           .define("showCompleted", true);
        SHOW_ICONS = builder
                       .comment("Draw the item icons.")
                       .translation(KEY + "showIcons")
                       .define("showIcons", true);
        MAX_PANELS = builder
                       .comment("Maximum number of construction sites displayed at the same time.")
                       .translation(KEY + "maxPanels")
                       .defineInRange("maxPanels", 3, 1, 10);
        MAX_LINES = builder
                      .comment("Maximum number of resource lines per construction site.")
                      .translation(KEY + "maxLines")
                      .defineInRange("maxLines", 8, 1, 64);
        REFRESH_SECONDS = builder
                            .comment("Seconds between two data refresh requests sent to the server for each displayed site (same request as opening the Resource Scroll).")
                            .translation(KEY + "refreshSeconds")
                            .defineInRange("refreshSeconds", 5, 2, 60);
        builder.pop();
        builder.pop();

        builder.comment("Beacon beams drawn in the world.")
          .translation(KEY + "beams")
          .push("beams");
        DEATH_BEAMS = builder
                        .comment("Show a red beacon beam above the grave of each dead citizen, until the grave is collected.")
                        .translation(KEY + "deathBeams")
                        .define("deathBeams", true);
        COMPLETION_CLICK = builder
                             .comment("What a click on a builder's \"building finished\" chat message does: BOTH (beacon beam and coordinates), BEAM, POSITION (coordinates in the chat) or OFF.")
                             .translation(KEY + "completionClick")
                             .defineEnum("completionClick", CompletionClick.BOTH);
        COLONY_GUIDE_MESSAGE = builder
                                 .comment("Say in the action bar, when entering someone else's colony, how many buildings it has and which key opens the colony guide.")
                                 .translation(KEY + "colonyGuideMessage")
                                 .define("colonyGuideMessage", true);
        builder.pop();

        SPEC = builder.build();
    }

    private HudConfig()
    {
    }
}
