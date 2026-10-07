package fr.mcvanillatweaks.client.townhall;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.permissions.ColonyPlayer;
import com.minecolonies.api.colony.permissions.IPermissions;
import com.minecolonies.api.colony.permissions.PermissionEvent;
import com.minecolonies.api.colony.permissions.Rank;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.core.network.messages.PermissionsMessage;
import com.minecolonies.core.network.messages.server.colony.ChangeFreeToInteractBlockMessage;
import com.minecolonies.core.network.messages.server.colony.building.GiveToolMessage;
import fr.mcvanillatweaks.client.citizen.Dropdown;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.ResourceLocationException;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * "Permissions" tab, in four sections chosen with a row of buttons: players (add,
 * rank, remove), ranks (add, remove, type and the on/off state of every action), blocks open to interaction, and
 * permission events. Every change sends the message MineColonies' permissions window sends, with the same local
 * updates and the same "edit permissions" checks.
 */
final class PermissionsTab extends TownHallTab
{
    enum Section
    {
        PLAYERS("mcvanillatweaks.townhall.perm.players"),
        RANKS("mcvanillatweaks.townhall.perm.ranks"),
        BLOCKS("mcvanillatweaks.townhall.perm.blocks"),
        EVENTS("mcvanillatweaks.townhall.perm.events");

        private final String key;

        Section(final String key)
        {
            this.key = key;
        }
    }

    private static final int TAB_HEIGHT = 20;
    private static final int TOP = TAB_HEIGHT + 6;
    private static final int FIELD_HEIGHT = 18;
    private static final int SMALL = 14;

    /** A button drawn in a list, recorded while drawing so clicks hit what is on screen. */
    private record Clickable(int bx, int by, int bw, int bh, Runnable run)
    {
    }

    /** Remembered while the game runs, like the page of MineColonies' window. */
    static Section section = Section.PLAYERS;
    private static int selectedRankId = -1;

    private final ScrollArea list = new ScrollArea();
    private final ScrollArea actions = new ScrollArea();
    private final List<Clickable> clickables = new ArrayList<>();
    private String playerName = "";
    private String rankName = "";
    private String blockName = "";
    private EditBox playerField;
    private int onlineIndex;

    PermissionsTab(final TownHallScreen screen)
    {
        super(screen);
    }

    private IPermissions permissions()
    {
        return colony().getPermissions();
    }

    private boolean canEdit()
    {
        return mc.player != null && permissions().hasPermission(mc.player, Action.EDIT_PERMISSIONS);
    }

    private int bodyY()
    {
        return y + TOP;
    }

    // ------------------------------------------------------------------ widgets

    @Override
    void init()
    {
        final boolean edit = canEdit();
        final int fieldWidth = width - 2 * 64;
        switch (section)
        {
            case PLAYERS ->
            {
                playerField = field(x, fieldWidth, playerName, "mcvanillatweaks.townhall.perm.player_hint", text -> playerName = text, edit);
                add(Button.builder(Component.translatable("com.minecolonies.coremod.gui.townhall.add"), b -> addPlayer())
                      .bounds(x + fieldWidth + 4, bodyY(), 60, 20).build()).active = edit;
                add(Button.builder(Component.translatable("mcvanillatweaks.townhall.perm.online"), b -> pickOnlinePlayer())
                      .bounds(x + fieldWidth + 68, bodyY(), 60, 20)
                      .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("mcvanillatweaks.townhall.perm.online.hint")))
                      .build()).active = edit;
                if (!edit)
                {
                    playerField.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                      Component.translatable("com.minecolonies.coremod.gui.townhall.player_permission_error")));
                }
            }
            case RANKS ->
            {
                final int left = rankColumnWidth();
                final EditBox rankField = field(x, left - 24, rankName, "mcvanillatweaks.townhall.perm.rank_hint", text -> rankName = text, edit);
                rankField.setY(y + height - 44);
                if (!edit)
                {
                    rankField.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                      Component.translatable("com.minecolonies.core.gui.townhall.rank_permission_error")));
                }
                add(Button.builder(Component.literal("+"), b -> addRank()).bounds(x + left - 20, y + height - 45, 20, 20)
                      .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("com.minecolonies.coremod.gui.townhall.addrank")))
                      .build()).active = edit;
                final Rank rank = selectedRank();
                add(Button.builder(Component.translatable("com.minecolonies.coremod.gui.townhall.removerank"), b -> removeRank())
                      .bounds(x, y + height - 20, left, 20).build()).active = rank != null && !rank.isInitial();
                dropdown(x + left + GAP, bodyY(), width - left - GAP, rankTypeLabel(rank),
                  () -> java.util.Arrays.stream(RANK_TYPES).map(key -> new Dropdown.Entry(Component.translatable(key))).toList(),
                  () -> rank == null ? -1 : rankType(rank), this::pickRankType).active = rank != null && !rank.equals(permissions().getRankOwner());
            }
            case BLOCKS ->
            {
                field(x, fieldWidth, blockName, "mcvanillatweaks.townhall.perm.block_hint", text -> blockName = text, edit);
                add(Button.builder(Component.translatable("com.minecolonies.coremod.gui.townhall.add"), b -> addBlock())
                      .bounds(x + fieldWidth + 4, bodyY(), 60, 20).build()).active = edit;
                add(Button.builder(Component.translatable("mcvanillatweaks.townhall.perm.tool"), b -> giveTool())
                      .bounds(x + fieldWidth + 68, bodyY(), 60, 20)
                      .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("mcvanillatweaks.townhall.perm.tool.hint")))
                      .build()).active = edit;
            }
            default ->
            {
            }
        }
    }

    private EditBox field(final int fx, final int fw, final String value, final String hint, final java.util.function.Consumer<String> responder,
      final boolean editable)
    {
        final EditBox box = add(new EditBox(font, fx + 1, bodyY() + 1, fw - 2, FIELD_HEIGHT, Component.translatable(hint)));
        box.setMaxLength(128);
        box.setHint(Component.translatable(hint).withStyle(ChatFormatting.DARK_GRAY));
        box.setValue(value);
        box.setResponder(responder);
        box.setEditable(editable);
        return box;
    }

    private int rankColumnWidth()
    {
        return 110;
    }

    // ------------------------------------------------------------------ actions (same messages as MineColonies' window)

    private void addPlayer()
    {
        if (!playerName.isBlank())
        {
            screen.trigger("addPlayer");
            new PermissionsMessage.AddPlayer(colony(), playerName.trim()).sendToServer();
            playerName = "";
            screen.rebuild();
        }
    }

    /** Fills the name with the next online player who is not in the colony yet (MineColonies' player picker). */
    private void pickOnlinePlayer()
    {
        if (mc.player == null)
        {
            return;
        }
        final List<String> names = new ArrayList<>();
        for (final PlayerInfo info : mc.player.connection.getOnlinePlayers())
        {
            if (!colony().getPlayers().containsKey(info.getProfile().getId()))
            {
                names.add(info.getProfile().getName());
            }
        }
        if (names.isEmpty())
        {
            return;
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        playerName = names.get(onlineIndex++ % names.size());
        playerField.setValue(playerName);
    }

    /** The ranks a player can be given, as a drop-down list under the row's rank button. */
    private void pickPlayerRank(final ColonyPlayer player, final int bx, final int by, final int bw)
    {
        final List<Rank> ranks = assignableRanks();
        screen.openDropdown(bx, by, bw, SMALL, ranks.stream().map(rank -> new Dropdown.Entry(Component.literal(rank.getName()))).toList(),
          ranks.indexOf(player.getRank()), index -> {
              final Rank next = ranks.get(index);
              player.setRank(next);
              new PermissionsMessage.ChangePlayerRank(colony(), player.getID(), next).sendToServer();
          });
    }

    private void removePlayer(final ColonyPlayer player)
    {
        if (!player.getRank().equals(permissions().getRankOwner()))
        {
            screen.trigger("removePlayer");
            new PermissionsMessage.RemovePlayer(colony(), player.getID()).sendToServer();
        }
    }

    /** Every rank but the owner's, which nobody can be given. */
    private List<Rank> assignableRanks()
    {
        final List<Rank> ranks = new ArrayList<>();
        for (final Rank rank : permissions().getRanks().values())
        {
            if (!rank.equals(permissions().getRankOwner()))
            {
                ranks.add(rank);
            }
        }
        return ranks;
    }

    @Nullable
    private Rank selectedRank()
    {
        final Rank rank = selectedRankId < 0 ? null : permissions().getRanks().get(selectedRankId);
        return rank != null ? rank : permissions().getRankOfficer();
    }

    private void selectRank(final Rank rank)
    {
        selectedRankId = rank.getId();
        screen.trigger("rankButton");
        screen.rebuild();
    }

    private void addRank()
    {
        final String name = rankName.trim();
        final boolean taken = assignableRanks().stream().anyMatch(rank -> rank.getName().equals(name));
        if (name.isEmpty() || taken)
        {
            return;
        }
        screen.trigger("buttonAddRank");
        new PermissionsMessage.AddRank(colony(), name).sendToServer();
        rankName = "";
        screen.rebuild();
    }

    private void removeRank()
    {
        final Rank rank = selectedRank();
        if (rank == null || rank.isInitial())
        {
            return;
        }
        screen.trigger("removeRank");
        new PermissionsMessage.RemoveRank(colony(), rank).sendToServer();
        permissions().removeRank(rank);
        selectedRankId = permissions().getRankOfficer().getId();
        screen.rebuild();
    }

    /** MineColonies' rank types: 0 colony manager, 1 hostile, 2 none. */
    private static int rankType(final Rank rank)
    {
        return rank.isColonyManager() ? 0 : rank.isHostile() ? 1 : 2;
    }

    private static final String[] RANK_TYPES = {
      "com.minecolonies.coremod.gui.townhall.ranktype.colonymanager", "com.minecolonies.coremod.gui.townhall.ranktype.hostile",
      "com.minecolonies.coremod.gui.townhall.ranktype.none"};

    private static Component rankTypeLabel(@Nullable final Rank rank)
    {
        return Component.translatable("mcvanillatweaks.townhall.perm.rank_type",
          rank == null ? Component.empty() : Component.translatable(RANK_TYPES[rankType(rank)]));
    }

    private void pickRankType(final int next)
    {
        final Rank rank = selectedRank();
        if (rank == null)
        {
            return;
        }
        new PermissionsMessage.EditRankType(colony(), rank, next).sendToServer();
        // Shown at once; the server sends the colony back anyway.
        rank.setColonyManager(next == 0);
        rank.setHostile(next == 1);
        screen.rebuild();
    }

    private void toggleAction(final Rank rank, final Action action)
    {
        if (mc.player == null)
        {
            return;
        }
        final IPermissions permissions = permissions();
        final boolean enable = !permissions.hasPermission(rank, action);
        screen.trigger("trigger");
        if (permissions.alterPermission(permissions.getRank(mc.player), rank, action, enable))
        {
            new PermissionsMessage.Permission(colony(), enable, rank, action).sendToServer();
        }
    }

    /** A block id ("minecraft:oak_door") or a position ("x y z"), like MineColonies' field. */
    private void addBlock()
    {
        final IColonyView colony = colony();
        final String input = blockName.trim();
        try
        {
            final Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(input));
            if (!block.defaultBlockState().isAir())
            {
                colony.addFreeBlock(block);
                new ChangeFreeToInteractBlockMessage(colony, block, ChangeFreeToInteractBlockMessage.MessageType.ADD_BLOCK).sendToServer();
            }
        }
        catch (final ResourceLocationException ignored)
        {
            // not a block id
        }
        final BlockPos pos = com.minecolonies.api.util.BlockPosUtil.getBlockPosOfString(input);
        if (pos != null)
        {
            new ChangeFreeToInteractBlockMessage(colony, pos, ChangeFreeToInteractBlockMessage.MessageType.ADD_BLOCK).sendToServer();
            colony.addFreePosition(pos);
        }
        screen.trigger("addBlock");
        blockName = "";
        screen.rebuild();
    }

    private void removeFreeBlock(final Block block)
    {
        screen.trigger("removeBlock");
        new ChangeFreeToInteractBlockMessage(colony(), block, ChangeFreeToInteractBlockMessage.MessageType.REMOVE_BLOCK).sendToServer();
        colony().removeFreeBlock(block);
    }

    private void removeFreePosition(final BlockPos pos)
    {
        screen.trigger("removeBlock");
        new ChangeFreeToInteractBlockMessage(colony(), pos, ChangeFreeToInteractBlockMessage.MessageType.REMOVE_BLOCK).sendToServer();
        colony().removeFreePosition(pos);
    }

    private void giveTool()
    {
        screen.trigger("blockTool");
        new GiveToolMessage(view(), ModItems.permTool).sendToServer();
    }

    private void addEventPlayer(final PermissionEvent event)
    {
        screen.trigger("addfakeplayer");
        new PermissionsMessage.AddPlayerOrFakePlayer(colony(), event.getName(), event.getId()).sendToServer();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        clickables.clear();
        renderSections(g, mouseX, mouseY);
        switch (section)
        {
            case PLAYERS -> renderPlayers(g, mouseX, mouseY, tooltip);
            case RANKS -> renderRanks(g, mouseX, mouseY, tooltip);
            case BLOCKS -> renderBlocks(g, mouseX, mouseY, tooltip);
            case EVENTS -> renderEvents(g, mouseX, mouseY, tooltip);
        }
    }

    private int sectionWidth()
    {
        return width / Section.values().length;
    }

    private void renderSections(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        final int sw = sectionWidth();
        for (final Section s : Section.values())
        {
            final int sx = x + s.ordinal() * sw;
            final boolean hovered = Gui.inside(mouseX, mouseY, sx, y, sw, TAB_HEIGHT);
            final boolean selected = s == section;
            // Vanilla buttons; the open section keeps the highlighted frame and a yellow label.
            Gui.button(g, sx + 1, y, sw - 2, TAB_HEIGHT, true, selected || hovered);
            final Component label = Component.translatable(s.key);
            g.drawString(font, Gui.fit(font, label, sw - 8), sx + Math.max(4, (sw - font.width(label) + 1) / 2), y + 6,
              selected ? Gui.YELLOW : Gui.WHITE, true);
        }
    }

    /** A small hand-drawn button in a list row. */
    private void rowButton(final GuiGraphics g, final int bx, final int by, final int bw, final Component label, final boolean active,
      final boolean over, final int mouseX, final int mouseY, final Tooltip tooltip, @Nullable final Component hint, final Runnable run)
    {
        final boolean hovered = over && Gui.inside(mouseX, mouseY, bx, by, bw, SMALL);
        Gui.button(g, bx, by, bw, SMALL, active, hovered && active);
        g.drawString(font, Gui.fit(font, label, bw - 4), bx + Math.max(2, (bw - font.width(label) + 1) / 2), by + 3,
          active ? Gui.WHITE : Gui.DISABLED, true);
        if (hovered && hint != null)
        {
            tooltip.set(hint);
        }
        if (active)
        {
            clickables.add(new Clickable(bx, by, bw, SMALL, run));
        }
    }

    private boolean visible(final ScrollArea area, final int rowY, final int rowHeight)
    {
        return rowY + rowHeight >= area.visibleTop() && rowY <= area.visibleBottom();
    }

    private void empty(final GuiGraphics g, final ScrollArea area, final Component text)
    {
        g.drawString(font, Gui.fit(font, text, area.listWidth() - 8), area.listX() + 4, area.visibleTop() + 6, Gui.LIST_SOFT, true);
    }

    // ---- players

    private static final int PLAYER_ROW = 20;

    private void renderPlayers(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final List<ColonyPlayer> players = new ArrayList<>(colony().getPlayers().values());
        players.sort(Comparator.comparing(ColonyPlayer::getRank, Rank::compareTo));
        final int top = bodyY() + 24;
        list.set(x, top, width, y + height - top, players.size() * PLAYER_ROW, PLAYER_ROW);
        list.begin(g);
        final boolean over = list.overRows(mouseX, mouseY);
        final boolean edit = canEdit();
        final int rankWidth = 100;
        final int removeX = list.listX() + list.listWidth() - SMALL - 3;
        final int rankX = removeX - 4 - rankWidth;
        for (int i = 0; i < players.size(); i++)
        {
            final int rowY = list.listY() + i * PLAYER_ROW;
            if (!visible(list, rowY, PLAYER_ROW))
            {
                continue;
            }
            final ColonyPlayer player = players.get(i);
            list.hover(g, mouseX, mouseY, rowY, PLAYER_ROW);
            final boolean owner = player.getRank().equals(permissions().getRankOwner());
            g.drawString(font, Gui.fit(font, Component.literal(player.getName()), rankX - list.listX() - 10), list.listX() + 4, rowY + 6, Gui.WHITE, true);
            final int by = rowY + (PLAYER_ROW - SMALL) / 2;
            if (owner)
            {
                final Component rank = Component.literal(player.getRank().getName());
                g.drawString(font, rank, rankX + (rankWidth - font.width(rank)) / 2, by + 3, Gui.LIST_SOFT, true);
            }
            else
            {
                rowButton(g, rankX, by, rankWidth, Component.literal(player.getRank().getName()), true, over, mouseX, mouseY, tooltip,
                  null, () -> pickPlayerRank(player, rankX, by, rankWidth));
                Gui.dropdownArrow(g, rankX + rankWidth - 11, by + (SMALL - 4) / 2, Gui.WHITE);
            }
            rowButton(g, removeX, by, SMALL, Component.literal("✖"), !owner && edit, over, mouseX, mouseY, tooltip,
              Component.translatable("mcvanillatweaks.townhall.perm.remove_player"), () -> removePlayer(player));
        }
        list.end(g);
    }

    // ---- ranks

    private static final int RANK_ROW = 14;
    private static final int ACTION_ROW = 18;

    private void renderRanks(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final List<Rank> ranks = new ArrayList<>(permissions().getRanks().values());
        final Rank selected = selectedRank();
        final int left = rankColumnWidth();
        final int listBottom = y + height - 48;
        list.set(x, bodyY(), left, listBottom - bodyY(), ranks.size() * RANK_ROW, RANK_ROW);
        list.begin(g);
        for (int i = 0; i < ranks.size(); i++)
        {
            final int rowY = list.listY() + i * RANK_ROW;
            if (!visible(list, rowY, RANK_ROW))
            {
                continue;
            }
            final Rank rank = ranks.get(i);
            final boolean isSelected = rank.equals(selected);
            final boolean hovered = list.overRows(mouseX, mouseY) && Gui.inside(mouseX, mouseY, list.listX(), rowY, list.listWidth(), RANK_ROW);
            if (isSelected || hovered)
            {
                g.fill(list.listX(), rowY, list.listX() + list.listWidth(), rowY + RANK_ROW, isSelected ? Gui.ROW_SELECTED : Gui.ROW_HOVER);
            }
            g.drawString(font, Gui.fit(font, Component.literal(rank.getName()), list.listWidth() - 8), list.listX() + 4, rowY + 3,
              isSelected ? Gui.YELLOW : Gui.WHITE, true);
            if (!isSelected && list.overRows(mouseX, mouseY))
            {
                clickables.add(new Clickable(list.listX(), rowY, list.listWidth(), RANK_ROW, () -> selectRank(rank)));
            }
        }
        list.end(g);

        if (selected == null)
        {
            return;
        }
        final int ax = x + left + GAP;
        final int aw = width - left - GAP;
        final int top = bodyY() + 24;
        final Action[] all = Action.values();
        actions.set(ax, top, aw, y + height - top, all.length * ACTION_ROW, ACTION_ROW);
        actions.begin(g);
        final boolean over = actions.overRows(mouseX, mouseY);
        final Rank myRank = mc.player == null ? null : permissions().getRank(mc.player);
        final int bw = 64;
        final int bx = actions.listX() + actions.listWidth() - bw - 3;
        for (int i = 0; i < all.length; i++)
        {
            final int rowY = actions.listY() + i * ACTION_ROW;
            if (!visible(actions, rowY, ACTION_ROW))
            {
                continue;
            }
            final Action action = all[i];
            actions.hover(g, mouseX, mouseY, rowY, ACTION_ROW);
            final Component name = Component.translatable("com.minecolonies.coremod.permission." + action.toString().toLowerCase(Locale.US));
            final int textWidth = bx - actions.listX() - 8;
            g.drawString(font, Gui.fit(font, name, textWidth), actions.listX() + 4, rowY + 5, Gui.WHITE, true);
            if (over && Gui.isCut(font, name, textWidth) && Gui.inside(mouseX, mouseY, actions.listX(), rowY, textWidth, ACTION_ROW))
            {
                tooltip.set(name);
            }
            final boolean on = permissions().hasPermission(selected, action);
            final boolean alterable = myRank != null && permissions().canAlterPermission(myRank, selected, action);
            rowButton(g, bx, rowY + (ACTION_ROW - SMALL) / 2, bw,
              Component.translatable(on ? "com.minecolonies.coremod.gui.workerhuts.retrieveon" : "com.minecolonies.coremod.gui.workerhuts.retrieveoff")
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED),
              alterable, over, mouseX, mouseY, tooltip, null, () -> toggleAction(selected, action));
        }
        actions.end(g);
    }

    // ---- free blocks

    private static final int BLOCK_ROW = 24;

    private void renderBlocks(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final IColonyView colony = colony();
        final List<Block> blocks = colony.getFreeBlocks();
        final List<BlockPos> positions = colony.getFreePositions();
        final int count = blocks.size() + positions.size();
        final int top = bodyY() + 24;
        heading(g, Component.translatable("com.minecolonies.coremod.gui.townhall.freeblocks"), x, top, width);
        final int listTop = top + HEADING;
        list.set(x, listTop, width, y + height - listTop, count * BLOCK_ROW, BLOCK_ROW);
        list.begin(g);
        if (count == 0)
        {
            empty(g, list, Component.translatable("mcvanillatweaks.townhall.perm.no_blocks"));
        }
        final boolean over = list.overRows(mouseX, mouseY);
        final boolean edit = canEdit();
        final int removeX = list.listX() + list.listWidth() - SMALL - 3;
        for (int i = 0; i < count; i++)
        {
            final int rowY = list.listY() + i * BLOCK_ROW;
            if (!visible(list, rowY, BLOCK_ROW))
            {
                continue;
            }
            list.hover(g, mouseX, mouseY, rowY, BLOCK_ROW);
            final Component first;
            final Component second;
            final Runnable remove;
            if (i < blocks.size())
            {
                final Block block = blocks.get(i);
                first = Component.literal(BuiltInRegistries.BLOCK.getKey(block).toString());
                second = block.getName();
                remove = () -> removeFreeBlock(block);
            }
            else
            {
                final BlockPos pos = positions.get(i - blocks.size());
                first = Component.translatable("mcvanillatweaks.townhall.position", pos.getX(), pos.getY(), pos.getZ());
                final boolean loaded = colony.getWorld() != null && colony.getWorld().isLoaded(pos);
                final BlockState state = loaded ? colony.getWorld().getBlockState(pos) : null;
                second = state == null ? Component.empty() : state.getBlock().getName();
                remove = () -> removeFreePosition(pos);
            }
            final int textWidth = removeX - list.listX() - 8;
            g.drawString(font, Gui.fit(font, first, textWidth), list.listX() + 4, rowY + 3, Gui.WHITE, true);
            g.drawString(font, Gui.fit(font, second, textWidth), list.listX() + 4, rowY + 14, Gui.LIST_SOFT, true);
            rowButton(g, removeX, rowY + (BLOCK_ROW - SMALL) / 2, SMALL, Component.literal("✖"), edit, over, mouseX, mouseY, tooltip,
              Component.translatable("mcvanillatweaks.townhall.perm.remove_block"), remove);
        }
        list.end(g);
    }

    // ---- events

    private static final int EVENT_ROW = 24;

    private void renderEvents(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        // Most recent first, like MineColonies.
        final List<PermissionEvent> events = new ArrayList<>(view().getPermissionEvents());
        java.util.Collections.reverse(events);
        list.set(x, bodyY(), width, y + height - bodyY(), events.size() * EVENT_ROW, EVENT_ROW);
        list.begin(g);
        if (events.isEmpty())
        {
            empty(g, list, Component.translatable("mcvanillatweaks.townhall.perm.no_events"));
        }
        final boolean over = list.overRows(mouseX, mouseY);
        final int bw = 60;
        final int bx = list.listX() + list.listWidth() - bw - 3;
        for (int i = 0; i < events.size(); i++)
        {
            final int rowY = list.listY() + i * EVENT_ROW;
            if (!visible(list, rowY, EVENT_ROW))
            {
                continue;
            }
            final PermissionEvent event = events.get(i);
            list.hover(g, mouseX, mouseY, rowY, EVENT_ROW);
            final MutableComponent name = Component.literal(event.getName());
            if (event.getId() == null)
            {
                name.append(Component.translatable("mcvanillatweaks.townhall.perm.fake").withStyle(ChatFormatting.GRAY));
            }
            final Component action = Component.translatable("com.minecolonies.coremod.permission." + event.getAction().toString().toLowerCase(Locale.US));
            final BlockPos pos = event.getPosition();
            final Component detail = action.copy().append(" · ")
                                       .append(Component.translatable("mcvanillatweaks.townhall.position", pos.getX(), pos.getY(), pos.getZ()));
            final int textWidth = bx - list.listX() - 8;
            g.drawString(font, Gui.fit(font, name, textWidth), list.listX() + 4, rowY + 3, Gui.WHITE, true);
            g.drawString(font, Gui.fit(font, detail, textWidth), list.listX() + 4, rowY + 14, Gui.LIST_SOFT, true);
            if (over && Gui.isCut(font, detail, textWidth) && Gui.inside(mouseX, mouseY, list.listX(), rowY, textWidth, EVENT_ROW))
            {
                tooltip.setAround(List.of(name, detail), mouseX, rowY, rowY + EVENT_ROW);
            }
            if (event.getId() != null)
            {
                rowButton(g, bx, rowY + (EVENT_ROW - SMALL) / 2, bw, Component.translatable("com.minecolonies.coremod.gui.townhall.add"), canEdit(), over,
                  mouseX, mouseY, tooltip, Component.translatable("mcvanillatweaks.townhall.perm.add_event_player"), () -> addEventPlayer(event));
            }
        }
        list.end(g);
    }

    // ------------------------------------------------------------------ input

    @Override
    boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        final int sw = sectionWidth();
        for (final Section s : Section.values())
        {
            if (Gui.inside(mouseX, mouseY, x + s.ordinal() * sw, y, sw, TAB_HEIGHT))
            {
                if (s != section && button == 0)
                {
                    Gui.clickSound();
                    section = s;
                    screen.rebuild();
                }
                return true;
            }
        }
        if (list.mouseClicked(mouseX, mouseY) || section == Section.RANKS && actions.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button != 0)
        {
            return false;
        }
        for (final Clickable clickable : List.copyOf(clickables))
        {
            if (Gui.inside(mouseX, mouseY, clickable.bx(), clickable.by(), clickable.bw(), clickable.bh()))
            {
                Gui.clickSound();
                clickable.run().run();
                return true;
            }
        }
        return false;
    }

    @Override
    boolean mouseDragged(final double mouseX, final double mouseY)
    {
        return list.mouseDragged(mouseY) || actions.mouseDragged(mouseY);
    }

    @Override
    void mouseReleased()
    {
        list.mouseReleased();
        actions.mouseReleased();
    }

    @Override
    boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        return list.mouseScrolled(mouseX, mouseY, delta) || section == Section.RANKS && actions.mouseScrolled(mouseX, mouseY, delta);
    }
}
