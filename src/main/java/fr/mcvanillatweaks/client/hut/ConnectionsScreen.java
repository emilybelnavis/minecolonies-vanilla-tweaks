package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * What a player who isn't allowed in the colony's huts gets at its gatehouse (MineColonies opens
 * {@code ConnectionModuleWindow} without its module tabs): the connected colonies, travel paid in gold nuggets.
 * <pre>
 * [gatehouse] Connected colonies - Colony A
 * +------------------------------------------+
 * | Direct connections: ...                  |
 * +------------------------------------------+
 * </pre>
 */
public final class ConnectionsScreen extends Screen
{
    private static final int WIDTH = 300;
    private static final int HEIGHT = 200;

    private final IBuildingView building;
    private final ConnectionsList list;
    private final Tooltip tooltip = new Tooltip();
    private int left;
    private int top;

    public ConnectionsScreen(final IBuildingView building)
    {
        super(Component.translatable("com.minecolonies.core.gui.connections"));
        this.building = building;
        this.list = new ConnectionsList(building, true);
    }

    @Override
    protected void init()
    {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        list.layout(left + 8, top + 28, WIDTH - 16, HEIGHT - 36);
    }

    @Override
    public void tick()
    {
        list.refresh();
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
        tooltip.clear();
        g.renderItem(HutScreen.hutIcon(building), left + 8, top + 7);
        Component title = getTitle();
        if (building.getColony() != null)
        {
            title = getTitle().copy().append(" - " + building.getColony().getName());
        }
        g.drawString(font, Gui.fit(font, title, WIDTH - 36), left + 28, top + 11, Gui.LABEL, false);
        list.render(g, mouseX, mouseY, tooltip);
        tooltip.render(g, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (button == 0 && list.mouseClicked(mouseX, mouseY, this::onClose))
        {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY)
    {
        return list.mouseDragged(mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button)
    {
        list.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        return list.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
