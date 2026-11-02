package fr.mcvanillatweaks.client.citizen;

import com.minecolonies.api.colony.ICitizenDataView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Optional integration: no Voice Expanded classes are linked when the mod is absent. */
final class VoiceExpandedScreen extends Screen
{
    private final Screen parent;
    private final UUID entity;
    private final List<Map.Entry<String, String>> voices;
    private int page;
    private static final int PAGE_SIZE = 6;

    private VoiceExpandedScreen(final Screen parent, final UUID entity, final List<Map.Entry<String, String>> voices)
    {
        super(Component.translatable("mcvanillatweaks.citizen.voice.title"));
        this.parent = parent;
        this.entity = entity;
        this.voices = voices;
    }

    static boolean available(final ICitizenDataView citizen)
    {
        return ModList.get().isLoaded("voiceexpanded") && !citizen.isChild();
    }

    static void open(final Screen parent, final ICitizenDataView citizen)
    {
        final Minecraft mc = Minecraft.getInstance();
        if (!available(citizen) || mc.level == null)
        {
            return;
        }
        final var entity = mc.level.getEntity(citizen.getEntityId());
        if (entity == null)
        {
            return;
        }
        try
        {
            final Object labels = Class.forName("dev.emily.voiceexpanded.VoiceExpanded").getField("LABELS").get(null);
            final List<Map.Entry<String, String>> voices = new ArrayList<>();
            if (labels instanceof Map<?, ?> map)
            {
                map.forEach((key, value) -> {
                    if (key instanceof String id && value instanceof String label)
                    {
                        voices.add(Map.entry(id, label));
                    }
                });
            }
            mc.setScreen(new VoiceExpandedScreen(parent, entity.getUUID(), voices));
        }
        catch (ReflectiveOperationException | LinkageError e)
        {
            fr.mcvanillatweaks.MineColoniesVanillaTweaks.LOGGER.warn("Voice Expanded UI integration unavailable", e);
        }
    }

    @Override
    protected void init()
    {
        final int left = (width - 240) / 2;
        final int top = Math.max(30, (height - 210) / 2);
        for (int row = 0; row < PAGE_SIZE; row++)
        {
            final int index = page * PAGE_SIZE + row;
            if (index < voices.size())
            {
                final var voice = voices.get(index);
                addRenderableWidget(Button.builder(Component.literal(voice.getValue()), b -> select(voice.getKey()))
                  .bounds(left, top + row * 23, 240, 20).build());
            }
        }
        addRenderableWidget(Button.builder(Component.translatable("mcvanillatweaks.citizen.voice.random"), b -> select("random"))
          .bounds(left, top + 140, 240, 20).build());
        final Button previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); })
          .bounds(left, top + 164, 40, 20).build());
        previous.active = page > 0;
        final Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); })
          .bounds(left + 200, top + 164, 40, 20).build());
        next.active = (page + 1) * PAGE_SIZE < voices.size();
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
          .bounds(left + 45, top + 164, 150, 20).build());
    }

    private void select(final String voice)
    {
        if (minecraft != null && minecraft.player != null)
        {
            // The voice mod's server command checks colony-management permission.
            minecraft.player.connection.sendCommand("colonistvoice " + entity + " " + voice);
        }
        onClose();
    }

    @Override
    public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, Math.max(30, (height - 210) / 2) - 20, 0xFFFFFF);
    }

    @Override
    public void onClose()
    {
        if (minecraft != null)
        {
            minecraft.setScreen(parent);
        }
    }
}
