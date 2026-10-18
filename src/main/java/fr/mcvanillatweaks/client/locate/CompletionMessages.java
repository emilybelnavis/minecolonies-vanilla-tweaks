package fr.mcvanillatweaks.client.locate;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.config.HudConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Makes a builder's "building finished" chat message clickable. MineColonies sends it as
 * {@code entity.builder.message<build|repair|removal>complete} (args: work order name, side of the colony) with a
 * hover {@code message.positiondist} holding the hut's coordinates; this reads them from the hover and gives the whole
 * message a click that runs the client command {@code /mcvanillatweaks locate x y z}. That command, depending on
 * {@link HudConfig#COMPLETION_CLICK}, toggles a {@link LocateBeams} beam above the hut and/or writes the coordinates,
 * distance and direction in the chat. Purely client side: works on any server.
 */
public final class CompletionMessages
{
    private static final Set<String> KEYS = Set.of("entity.builder.messagebuildcomplete", "entity.builder.messagerepaircomplete",
      "entity.builder.messageremovalcomplete");
    private static final String POSITION_KEY = "message.positiondist";
    private static final String COMMAND = "mcvanillatweaks";
    /** Name of the work order at each position seen in a message, for the beam's label (the last 64). */
    private static final Map<Long, Component> LABELS = new LinkedHashMap<>()
    {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<Long, Component> eldest)
        {
            return size() > 64;
        }
    };
    private static boolean failed;

    private CompletionMessages()
    {
    }

    private record Found(BlockPos pos, Component label, Component positionText)
    {
    }

    // ---- The message ----

    public static void onChatReceived(final ClientChatReceivedEvent.System event)
    {
        if (failed || event.isOverlay() || HudConfig.COMPLETION_CLICK.get() == HudConfig.CompletionClick.OFF)
        {
            return;
        }
        try
        {
            final Found found = find(event.getMessage());
            if (found == null)
            {
                return;
            }
            LABELS.put(found.pos().asLong(), found.label());
            final Component hover = Component.empty().append(found.positionText()).append("\n")
              .append(Component.translatable("mcvanillatweaks.completion.hint." + HudConfig.COMPLETION_CLICK.get().name().toLowerCase(Locale.ROOT))
                .withStyle(ChatFormatting.GRAY));
            final MutableComponent message = rebuild(event.getMessage(), hover);
            Style style = message.getStyle().withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
              "/" + COMMAND + " locate " + found.pos().getX() + " " + found.pos().getY() + " " + found.pos().getZ()));
            if (style.getHoverEvent() == null)
            {
                style = style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
            }
            message.setStyle(style);
            event.setMessage(message);
        }
        catch (final LinkageError | RuntimeException e)
        {
            failed = true;
            MineColoniesVanillaTweaks.LOGGER.warn("Clickable building messages unavailable", e);
        }
    }

    /** The finished-work part of the message, with the hut's position from its hover, or null for other messages. */
    @Nullable
    private static Found find(final Component component)
    {
        if (component.getContents() instanceof TranslatableContents contents && KEYS.contains(contents.getKey())
              && component.getStyle().getHoverEvent() != null)
        {
            final Component position = component.getStyle().getHoverEvent().getValue(HoverEvent.Action.SHOW_TEXT);
            if (position != null && position.getContents() instanceof TranslatableContents positionContents
                  && positionContents.getKey().equals(POSITION_KEY) && positionContents.getArgs().length >= 3)
            {
                final Object[] args = positionContents.getArgs();
                final Integer x = number(args[0]);
                final Integer y = number(args[1]);
                final Integer z = number(args[2]);
                if (x != null && y != null && z != null)
                {
                    final Object name = contents.getArgs().length > 0 ? contents.getArgs()[0] : null;
                    final Component label = name instanceof Component text ? text : Component.literal(name == null ? x + " " + y + " " + z : String.valueOf(name));
                    return new Found(new BlockPos(x, y, z), label, position);
                }
            }
        }
        for (final Component sibling : component.getSiblings())
        {
            final Found found = find(sibling);
            if (found != null)
            {
                return found;
            }
        }
        return null;
    }

    @Nullable
    private static Integer number(final Object arg)
    {
        if (arg instanceof Number number)
        {
            return number.intValue();
        }
        try
        {
            return Integer.parseInt((arg instanceof Component text ? text.getString() : String.valueOf(arg)).strip());
        }
        catch (final NumberFormatException e)
        {
            return null;
        }
    }

    /** A copy of the message whose position hovers also say what a click does. */
    private static MutableComponent rebuild(final Component component, final Component hover)
    {
        final MutableComponent copy = MutableComponent.create(component.getContents());
        Style style = component.getStyle();
        final HoverEvent event = style.getHoverEvent();
        if (event != null && event.getValue(HoverEvent.Action.SHOW_TEXT) instanceof Component text
              && text.getContents() instanceof TranslatableContents contents && contents.getKey().equals(POSITION_KEY))
        {
            style = style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
        }
        copy.setStyle(style);
        for (final Component sibling : component.getSiblings())
        {
            copy.append(rebuild(sibling, hover));
        }
        return copy;
    }

    // ---- The click ----

    public static void registerCommands(final RegisterClientCommandsEvent event)
    {
        event.getDispatcher().register(Commands.literal(COMMAND).then(Commands.literal("locate")
          .then(Commands.argument("x", IntegerArgumentType.integer())
            .then(Commands.argument("y", IntegerArgumentType.integer())
              .then(Commands.argument("z", IntegerArgumentType.integer())
                .executes(CompletionMessages::locate))))));
    }

    private static int locate(final CommandContext<CommandSourceStack> context)
    {
        final BlockPos pos = new BlockPos(IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "y"),
          IntegerArgumentType.getInteger(context, "z"));
        final Component label = LABELS.getOrDefault(pos.asLong(), Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ()));
        HudConfig.CompletionClick mode = HudConfig.COMPLETION_CLICK.get();
        if (mode == HudConfig.CompletionClick.OFF)
        {
            mode = HudConfig.CompletionClick.POSITION;
        }
        final Minecraft mc = Minecraft.getInstance();
        try
        {
            if (mode.position())
            {
                showPosition(mc, pos, label);
            }
            if (mode.beam() && LocateBeams.toggle(pos, label) && mc.screen instanceof ChatScreen)
            {
                // Like the locate buttons: close the chat so the new beam is in sight.
                mc.setScreen(null);
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            MineColoniesVanillaTweaks.LOGGER.warn("Could not locate {}", pos, e);
        }
        return 1;
    }

    /** "<hut>: X Y Z, <n> m to the <direction>" in the chat, for the player only. */
    private static void showPosition(final Minecraft mc, final BlockPos pos, final Component label)
    {
        if (mc.player == null)
        {
            return;
        }
        final double dx = pos.getX() + 0.5 - mc.player.getX();
        final double dz = pos.getZ() + 0.5 - mc.player.getZ();
        final int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        // 0 = north (-Z), clockwise in eighths.
        final int direction = Math.floorMod(Math.round((float) (Mth.atan2(dx, -dz) * 4 / Math.PI)), 8);
        final Component text = distance < 4
          ? Component.translatable("mcvanillatweaks.completion.position.here", label, pos.getX(), pos.getY(), pos.getZ())
          : Component.translatable("mcvanillatweaks.completion.position", label, pos.getX(), pos.getY(), pos.getZ(), distance,
            Component.translatable("mcvanillatweaks.completion.direction." + direction));
        mc.player.displayClientMessage(text.copy().withStyle(ChatFormatting.YELLOW), false);
    }
}
