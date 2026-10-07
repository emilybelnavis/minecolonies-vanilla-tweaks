package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import fr.mcvanillatweaks.client.citizen.Gui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Replaces MineColonies' hut rename window ({@code WindowHutNameEntry}): the name field (15 characters, MineColonies'
 * limit), "done" sets it with {@code setCustomName} like MineColonies, and both buttons go back to the hut.
 * <pre>
 * [hut] Rename Farmer's Hut
 * [Old Farm          ]
 * [Cancel]      [Done]
 * </pre>
 */
public final class RenameScreen extends Screen
{
    private static final int WIDTH = 220;
    private static final int HEIGHT = 92;
    /** MineColonies' WindowHutNameEntry.MAX_NAME_LENGTH. */
    private static final int MAX_LENGTH = 15;

    private final IBuildingView building;
    private String name;
    private EditBox field;
    private int left;
    private int top;

    public RenameScreen(final IBuildingView building)
    {
        super(Component.translatable("mcvanillatweaks.hut.rename"));
        this.building = building;
        // As MineColonies fills its field.
        final String custom = building.getCustomName() == null ? "" : building.getCustomName();
        this.name = Component.translatable(custom.toLowerCase(Locale.US)).getString();
    }

    @Override
    protected void init()
    {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        field = addRenderableWidget(new EditBox(font, left + 9, top + 30, WIDTH - 18, 18, Component.translatable("mcvanillatweaks.hut.rename")));
        field.setMaxLength(MAX_LENGTH);
        field.setValue(name.length() > MAX_LENGTH ? name.substring(0, MAX_LENGTH) : name);
        field.setHint(Component.translatable(building.getBuildingDisplayName()).withStyle(ChatFormatting.DARK_GRAY));
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
            building.setCustomName(field.getValue());
        }
        catch (final LinkageError | RuntimeException e)
        {
            HutScreens.report(e);
        }
        back();
    }

    private void back()
    {
        building.openGui(false);
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
        g.renderItem(HutScreen.hutIcon(building), left + 8, top + 7);
        final Component title = Component.translatable("mcvanillatweaks.hut.rename.title", Component.translatable(building.getBuildingDisplayName()));
        g.drawString(font, Gui.fit(font, title, WIDTH - 36), left + 28, top + 11, Gui.LABEL, false);
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
