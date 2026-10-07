package fr.mcvanillatweaks.client.townhall;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.network.messages.server.colony.HireMercenaryMessage;
import fr.mcvanillatweaks.client.citizen.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Replaces MineColonies' mercenary window ({@code WindowTownHallMercenary}): its story, how many mercenaries come
 * (citizens / 10 + 3, the server's count; MineColonies draws at most 9 faces, here one sword each) and what they cost
 * (they steal from the huts and the citizens). "Hire" does what MineColonies does: {@code usedMercenaries},
 * {@code HireMercenaryMessage} and the thunder sound, then closes; "cancel" goes back to the town hall.
 * <pre>
 * [sword] Mercenary help
 * You met some mercenaries close to your colony...
 * 5 mercenaries will come:  [sword][sword][sword][sword][sword]
 * Their price: what they take from your huts and citizens.
 * [Cancel]                  [Hire]
 * </pre>
 */
public final class MercenaryScreen extends Screen
{
    private static final int WIDTH = 260;
    private static final int TEXT_WIDTH = WIDTH - 20;
    private static final int MAX_SWORDS = 12;

    private final IColonyView colony;
    private List<FormattedCharSequence> story = List.of();
    private List<FormattedCharSequence> price = List.of();
    private int count;
    private int left;
    private int top;
    private int panelHeight;

    public MercenaryScreen(final IColonyView colony)
    {
        super(Component.translatable("com.minecolonies.coremod.gui.townhall.mercenary.title"));
        this.colony = colony;
    }

    @Override
    protected void init()
    {
        count = colony.getCitizenCount() / 10 + 3;
        story = font.split(Component.translatable("com.minecolonies.coremod.gui.townhall.mercenarystory"), TEXT_WIDTH);
        price = font.split(Component.translatable("mcvanillatweaks.townhall.mercenary.price"), TEXT_WIDTH);
        panelHeight = 30 + story.size() * 10 + 8 + 20 + 6 + price.size() * 10 + 10 + 20 + 8;
        left = (width - WIDTH) / 2;
        top = (height - panelHeight) / 2;
        final int bw = (WIDTH - 24) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> back()).bounds(left + 8, top + panelHeight - 28, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("com.minecolonies.coremod.gui.townhall.mercenaryhire"), b -> hire())
                              .bounds(left + WIDTH - 8 - bw, top + panelHeight - 28, bw, 20).build());
    }

    private void hire()
    {
        try
        {
            colony.usedMercenaries();
            new HireMercenaryMessage(colony).sendToServer();
            if (minecraft != null && minecraft.player != null)
            {
                minecraft.player.playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0F, 1.0F);
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            TownHallScreens.report(e);
        }
        onClose();
    }

    private void back()
    {
        try
        {
            final IBuildingView townHall = colony.getClientBuildingManager().getTownHall();
            if (townHall != null)
            {
                townHall.openGui(false);
                return;
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            TownHallScreens.report(e);
        }
        onClose();
    }

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        renderTransparentBackground(g);
        Gui.panel(g, left, top, WIDTH, panelHeight);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        final ItemStack sword = new ItemStack(Items.IRON_SWORD);
        g.renderItem(sword, left + 8, top + 7);
        g.drawString(font, Gui.fit(font, getTitle(), WIDTH - 36), left + 28, top + 11, Gui.LABEL, false);

        int ty = top + 30;
        for (final FormattedCharSequence line : story)
        {
            g.drawString(font, line, left + 10, ty, Gui.LABEL, false);
            ty += 10;
        }
        ty += 8;

        final Component coming = Component.translatable("mcvanillatweaks.townhall.mercenary.count", count);
        g.drawString(font, coming, left + 10, ty + 6, Gui.LABEL, false);
        int sx = left + 10 + font.width(coming) + 6;
        final int shown = Math.min(count, MAX_SWORDS);
        final int step = Math.max(8, Math.min(16, (left + WIDTH - 10 - 16 - sx) / Math.max(1, shown - 1)));
        for (int i = 0; i < shown; i++)
        {
            g.renderItem(sword, sx, ty + 2);
            sx += step;
        }
        ty += 20 + 6;

        for (final FormattedCharSequence line : price)
        {
            g.drawString(font, line, left + 10, ty, Gui.LABEL_SOFT, false);
            ty += 10;
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
