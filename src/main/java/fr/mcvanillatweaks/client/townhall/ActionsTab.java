package fr.mcvanillatweaks.client.townhall;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.core.client.gui.WindowBuildBuilding;
import com.minecolonies.core.network.messages.server.colony.TeamColonyColorChangeMessage;
import com.minecolonies.core.network.messages.server.colony.building.BuildRequestMessage;
import fr.mcvanillatweaks.client.citizen.Dropdown;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.IconButton;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import fr.mcvanillatweaks.client.citizen.VanillaIcons;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Home tab (MineColonies' town hall main page).
 * <pre>
 * Appearance            Colony
 * +-----------+         [Citizens 6/25] [Buildings 12] [Day 42]
 * |  banner   |         Management
 * +-----------+         [# Build options       ]
 * [Edit the flag]       [# Town map            ]
 * [Colour: White v]     [# Hire mercenaries    ]
 * [Style: ...    ]      [# Rename the colony   ]
 * </pre>
 * Buttons that open a MineColonies dialog press the button of the replaced window, so the dialog comes back to the
 * town hall exactly as before; the team colour is a drop-down list sending MineColonies' message. MineColonies'
 * supporter-only choices (textures, name styles, Patreon) are left out, at the user's request.
 */
final class ActionsTab extends TownHallTab
{
    private static final int BUTTON_HEIGHT = 20;
    private static final int STEP = 24;
    private static final int LEFT_WIDTH = 100;
    private static final int CARD_HEIGHT = 76;
    private static final int TILE_HEIGHT = 36;
    /** MineColonies disables the mercenaries for this long after hiring them (two days). */
    private static final long MERCENARY_COOLDOWN = 48000L;
    private static final List<ChatFormatting> COLOURS = Arrays.stream(ChatFormatting.values()).filter(ChatFormatting::isColor).toList();

    private static final ItemStack CITIZENS = new ItemStack(Items.PLAYER_HEAD);
    private static final ItemStack BUILDINGS = new ItemStack(Items.BRICKS);
    private static final ItemStack DAY = new ItemStack(Items.CLOCK);

    private IconButton build;
    private Button mercenaries;
    private Button colour;
    private Button style;
    private Component styleLabel = Component.empty();
    private ItemStack banner = ItemStack.EMPTY;
    private BannerPatternLayers bannerLayers;

    ActionsTab(final TownHallScreen screen)
    {
        super(screen);
    }

    private int rightX()
    {
        return x + LEFT_WIDTH + GAP;
    }

    private int rightWidth()
    {
        return width - LEFT_WIDTH - GAP;
    }

    @Override
    void init()
    {
        final int cardBottom = y + HEADING + CARD_HEIGHT;
        add(new IconButton(x, cardBottom + 4, LEFT_WIDTH, BUTTON_HEIGHT, new ItemStack(Items.WHITE_BANNER), Component.translatable("mcvanillatweaks.townhall.flag"),
          b -> screen.clickSource("bannerPicker")));
        colour = dropdown(x, cardBottom + 4 + STEP, LEFT_WIDTH, Component.empty(), ActionsTab::colourEntries,
          () -> COLOURS.indexOf(colony().getTeamColonyColor()), index -> new TeamColonyColorChangeMessage(index, view()).sendToServer());
        style = add(Button.builder(Component.empty(), b -> screen.clickSource("colonyStylePicker"))
                      .bounds(x, cardBottom + 4 + 2 * STEP, LEFT_WIDTH, BUTTON_HEIGHT).build());

        final int rx = rightX();
        final int rw = rightWidth();
        final int top = y + HEADING + TILE_HEIGHT + 8 + HEADING;
        build = add(new IconButton(rx, top, rw, BUTTON_HEIGHT, ColonyIcons.hut(view()), buildLabel(), b -> buildClicked()));
        add(new IconButton(rx, top + STEP, rw, BUTTON_HEIGHT, new ItemStack(Items.FILLED_MAP), Component.translatable("com.minecolonies.coremod.gui.townHall.map"),
          b -> screen.clickSource("map")));
        mercenaries = add(new IconButton(rx, top + 2 * STEP, rw, BUTTON_HEIGHT, new ItemStack(Items.IRON_SWORD),
          Component.translatable("com.minecolonies.coremod.gui.townhall.mercenary"), b -> screen.clickSource("mercenaries")));
        add(new IconButton(rx, top + 3 * STEP, rw, BUTTON_HEIGHT, new ItemStack(Items.NAME_TAG), Component.translatable("mcvanillatweaks.townhall.rename"),
          b -> screen.clickSource("rename")));
        update();
    }

    @Override
    void tick()
    {
        update();
    }

    /** Labels and states follow the colony, like MineColonies' window does on every update. */
    private void update()
    {
        final IColonyView colony = colony();
        build.setMessage(buildLabel());

        final long used = colony.getMercenaryUseTime();
        mercenaries.active = used == 0L || colony.getWorld() == null || colony.getWorld().getGameTime() - used >= MERCENARY_COOLDOWN;

        final ChatFormatting teamColour = colony.getTeamColonyColor();
        colour.setMessage(Component.translatable("mcvanillatweaks.townhall.colour",
          colourName(teamColour).withStyle(teamColour == ChatFormatting.WHITE ? ChatFormatting.WHITE : teamColour)));
        // "Style: <pack>" is too wide for most pack names: the button then shows the pack alone, the full label on hover.
        styleLabel = Component.translatable("mcvanillatweaks.townhall.style", colony.getStructurePack());
        style.setMessage(font.width(styleLabel) <= LEFT_WIDTH - 8 ? styleLabel : Component.literal(colony.getStructurePack()));
    }

    /** The colony's flag as a banner item, rebuilt when the flag changes. */
    private ItemStack banner()
    {
        final BannerPatternLayers layers = colony().getColonyFlag();
        if (banner.isEmpty() || layers != bannerLayers)
        {
            bannerLayers = layers;
            banner = new ItemStack(Items.WHITE_BANNER);
            if (layers != null)
            {
                banner.set(DataComponents.BANNER_PATTERNS, layers);
            }
        }
        return banner;
    }

    /** The team colours in MineColonies' list order (its message sends the index), each with its swatch. */
    private static List<Dropdown.Entry> colourEntries()
    {
        return COLOURS.stream().map(c -> new Dropdown.Entry(colourName(c), 0xFF000000 | c.getColor())).toList();
    }

    private static MutableComponent colourName(final ChatFormatting colour)
    {
        return Component.translatable("mcvanillatweaks.color." + colour.getName().toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ building

    /** Same label as MineColonies' build button for the hut's current state. */
    private Component buildLabel()
    {
        return Component.translatable(buildKey());
    }

    private String buildKey()
    {
        if (view().isBuilding())
        {
            return view().getBuildingLevel() == 0 ? "com.minecolonies.coremod.gui.workerhuts.cancelbuild" : "com.minecolonies.coremod.gui.workerhuts.cancelupgrade";
        }
        if (view().isRepairing())
        {
            return "com.minecolonies.coremod.gui.workerhuts.cancelrepair";
        }
        if (view().isDeconstructing())
        {
            return "com.minecolonies.coremod.gui.workerhuts.canceldeconstruction";
        }
        return "com.minecolonies.coremod.gui.workerhuts.buildrepair";
    }

    /** What MineColonies' build button does: cancel the current order, or open the build options. */
    private void buildClicked()
    {
        screen.trigger("build");
        switch (buildKey())
        {
            case "com.minecolonies.coremod.gui.workerhuts.cancelbuild", "com.minecolonies.coremod.gui.workerhuts.cancelupgrade" ->
              new BuildRequestMessage(view(), BuildRequestMessage.Mode.BUILD, BlockPos.ZERO).sendToServer();
            case "com.minecolonies.coremod.gui.workerhuts.cancelrepair" ->
              new BuildRequestMessage(view(), BuildRequestMessage.Mode.REPAIR, BlockPos.ZERO).sendToServer();
            case "com.minecolonies.coremod.gui.workerhuts.canceldeconstruction" ->
              new BuildRequestMessage(view(), BuildRequestMessage.Mode.REMOVE, BlockPos.ZERO).sendToServer();
            default -> new WindowBuildBuilding(colony(), view()).open();
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        heading(g, Component.translatable("mcvanillatweaks.townhall.appearance"), x, y, LEFT_WIDTH);
        final int cardY = y + HEADING;
        Gui.inset(g, x, cardY, LEFT_WIDTH, CARD_HEIGHT);
        // The banner item, four times its size, centred in the card.
        VanillaIcons.item(g, banner(), x + (LEFT_WIDTH - 64) / 2, cardY + (CARD_HEIGHT - 64) / 2, 64);
        if (Gui.inside(mouseX, mouseY, x, cardY, LEFT_WIDTH, CARD_HEIGHT))
        {
            tooltip.set(Component.translatable("mcvanillatweaks.townhall.home.flag", colony().getName()));
        }

        if (style.isHovered() && style.getMessage() != styleLabel)
        {
            tooltip.set(styleLabel);
        }

        final int rx = rightX();
        final int rw = rightWidth();
        heading(g, Component.translatable("mcvanillatweaks.townhall.home.colony"), rx, y, rw);
        final IColonyView colony = colony();
        final int citizens = colony.getCitizens().size();
        final int tileWidth = (rw - 2 * 6) / 3;
        final int tileY = y + HEADING;
        tile(g, rx, tileY, tileWidth, CITIZENS, citizens + "/" + Math.max(citizens, colony.getCitizenCountLimit()),
          Component.translatable("mcvanillatweaks.townhall.home.citizens"), mouseX, mouseY, tooltip,
          Component.translatable("mcvanillatweaks.townhall.home.citizens_hint"));
        tile(g, rx + tileWidth + 6, tileY, tileWidth, BUILDINGS, Integer.toString(buildingCount()),
          Component.translatable("mcvanillatweaks.townhall.home.buildings"), mouseX, mouseY, tooltip,
          Component.translatable("mcvanillatweaks.townhall.home.buildings_hint"));
        tile(g, rx + 2 * (tileWidth + 6), tileY, rw - 2 * (tileWidth + 6), DAY, Integer.toString(colony.getDay()),
          Component.translatable("mcvanillatweaks.townhall.home.day"), mouseX, mouseY, tooltip,
          Component.translatable("mcvanillatweaks.townhall.home.day_hint"));

        heading(g, Component.translatable("mcvanillatweaks.townhall.management"), rx, tileY + TILE_HEIGHT + 8, rw);
    }

    private int buildingCount()
    {
        try
        {
            return colony().getClientBuildingManager().getBuildings().size();
        }
        catch (final RuntimeException e)
        {
            return 0;
        }
    }

    /** A small card: icon and value on the first line, the label centred under them. */
    private void tile(final GuiGraphics g, final int tx, final int ty, final int tw, final ItemStack icon, final String value, final Component label,
      final int mouseX, final int mouseY, final Tooltip tooltip, final Component hint)
    {
        Gui.inset(g, tx, ty, tw, TILE_HEIGHT);
        if (Gui.inside(mouseX, mouseY, tx, ty, tw, TILE_HEIGHT))
        {
            g.fill(tx + 1, ty + 1, tx + tw - 1, ty + TILE_HEIGHT - 1, Gui.ROW_HOVER);
            tooltip.set(List.of(label, hint.copy().withStyle(ChatFormatting.GRAY)));
        }
        g.renderItem(icon, tx + 4, ty + 3);
        g.drawString(font, value, tx + tw - 5 - font.width(value), ty + 7, Gui.WHITE, true);
        g.drawString(font, Gui.fit(font, label, tw - 6), tx + Math.max(3, (tw - font.width(label)) / 2), ty + TILE_HEIGHT - 12, Gui.LIST_SOFT, true);
    }
}
