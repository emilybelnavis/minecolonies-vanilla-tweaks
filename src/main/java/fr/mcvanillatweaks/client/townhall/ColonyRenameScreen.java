package fr.mcvanillatweaks.client.townhall;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import fr.mcvanillatweaks.client.citizen.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Replaces MineColonies' colony rename window ({@code WindowTownHallNameEntry}): the name field (25 characters, the
 * limit of MineColonies' layout), "done" sets a non-empty name with {@code IColonyView.setName} like MineColonies, and
 * both buttons go back to the town hall.
 * <pre>
 * [town hall] Rename the colony
 * [Ravenhold                    ]
 * [Cancel]               [Done]
 * </pre>
 */
public final class ColonyRenameScreen extends Screen
{
    private static final int WIDTH = 240;
    private static final int HEIGHT = 92;
    /** maxlength of the name field in MineColonies' windowtownhallnameentry.xml. */
    private static final int MAX_LENGTH = 25;

    private final IColonyView colony;
    private String name;
    private EditBox field;
    private int left;
    private int top;

    public ColonyRenameScreen(final IColonyView colony)
    {
        super(Component.translatable("com.minecolonies.coremod.gui.townhall.rename.title"));
        this.colony = colony;
        // MineColonies fills its field with the lower-cased name translated (for default names that are lang keys), which
        // also lower-cases a name the player typed: only translate actual keys.
        final String key = colony.getName().toLowerCase(Locale.US);
        this.name = I18n.exists(key) ? I18n.get(key) : colony.getName();
    }

    @Override
    protected void init()
    {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        field = addRenderableWidget(new EditBox(font, left + 9, top + 30, WIDTH - 18, 18, getTitle()));
        field.setMaxLength(MAX_LENGTH);
        field.setValue(name.length() > MAX_LENGTH ? name.substring(0, MAX_LENGTH) : name);
        field.setResponder(text -> name = text);
        setInitialFocus(field);
        final int bw = (WIDTH - 24) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> back()).bounds(left + 8, top + HEIGHT - 28, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> done()).bounds(left + WIDTH - 8 - bw, top + HEIGHT - 28, bw, 20).build());
    }

    private void done()
    {
        try
        {
            if (!field.getValue().isEmpty())
            {
                colony.setName(field.getValue());
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            TownHallScreens.report(e);
        }
        back();
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

    private ItemStack icon()
    {
        try
        {
            final IBuildingView townHall = colony.getClientBuildingManager().getTownHall();
            if (townHall != null)
            {
                return fr.mcvanillatweaks.client.hut.HutScreen.hutIcon(townHall);
            }
        }
        catch (final LinkageError | RuntimeException ignored)
        {
            // Only the picture.
        }
        return new ItemStack(Items.BELL);
    }

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        renderTransparentBackground(g);
        Gui.panel(g, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        g.renderItem(icon(), left + 8, top + 7);
        g.drawString(font, Gui.fit(font, getTitle(), WIDTH - 36), left + 28, top + 11, Gui.LABEL, false);
        final String count = field.getValue().length() + " / " + MAX_LENGTH;
        g.drawString(font, count, left + WIDTH - 9 - font.width(count), top + 50, Gui.LABEL_SOFT, false);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers)
    {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)
        {
            done();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
