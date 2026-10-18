package fr.mcvanillatweaks.client.locate;

import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

/**
 * Log check of {@link CompletionMessages} ({@code [completion check]}): a builder's "finished" message built the way
 * MineColonies 1.1.1368 does (EntityAIStructureBuilder.sendCompletionMessage + MessageUtils.forCitizen), sent through
 * the component codec like a chat packet, then given to the chat hook. A real message needs a colony.
 */
public final class CompletionPreview
{
    private CompletionPreview()
    {
    }

    public static void check()
    {
        try
        {
            final MutableComponent done = Component.translatableEscape("entity.builder.messagebuildcomplete",
                Component.translatable("com.minecolonies.building.hospital"), "north-east")
              .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                Component.translatable("message.positiondist", 120, 64, -30, 85))))
              .withStyle(ChatFormatting.GREEN);
            final MutableComponent message = Component.translatable("com.minecolonies.job.builder").copy()
              .append(Component.literal(" ")).append(Component.literal("Bob Lapin")).append(Component.literal(": ")).append(done.copy());
            final Style priority = Style.EMPTY.withColor(ChatFormatting.WHITE);
            message.withStyle(priority);
            message.getSiblings().forEach(sibling -> ((MutableComponent) sibling).withStyle(priority));

            final Tag tag = ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, message).getOrThrow();
            final Component received = ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
            final ClientChatReceivedEvent.System event = new ClientChatReceivedEvent.System(received, false);
            CompletionMessages.onChatReceived(event);
            final Component result = event.getMessage();
            final ClickEvent click = result.getStyle().getClickEvent();
            final HoverEvent inner = result.getSiblings().get(result.getSiblings().size() - 1).getStyle().getHoverEvent();
            MineColoniesVanillaTweaks.LOGGER.info("[completion check] text: {} | click: {} | hover: {}", result.getString(),
              click == null ? null : click.getValue(),
              inner == null ? null : inner.getValue(HoverEvent.Action.SHOW_TEXT).getString().replace('\n', '/'));

            final ClientChatReceivedEvent.System other = new ClientChatReceivedEvent.System(Component.literal("Hello"), false);
            CompletionMessages.onChatReceived(other);
            MineColoniesVanillaTweaks.LOGGER.info("[completion check] other message untouched: {}", other.getMessage().getStyle().getClickEvent() == null);
        }
        catch (final RuntimeException e)
        {
            MineColoniesVanillaTweaks.LOGGER.error("[completion check] failed", e);
        }
    }
}
