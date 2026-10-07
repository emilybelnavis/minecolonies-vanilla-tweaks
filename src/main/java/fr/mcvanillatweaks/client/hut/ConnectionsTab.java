package fr.mcvanillatweaks.client.hut;

import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The gatehouse's "connected colonies" page (MineColonies' {@code ConnectionModuleWindow} opened from its module tab),
 * drawn by {@link ConnectionsList}. Diplomacy (alliances, feuds) stays on the town hall's alliances page.
 */
final class ConnectionsTab extends HutTab
{
    private final ConnectionsList list;

    ConnectionsTab(final HutScreen screen)
    {
        super(screen);
        this.list = new ConnectionsList(screen.building(), false);
    }

    @Override
    protected void tick()
    {
        list.refresh();
    }

    @Override
    protected void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        heading(g, Component.translatable("com.minecolonies.core.gui.connections"), x, y + 1, width);
        list.layout(x, y + HEADING + 2, width, height - HEADING - 2);
        list.render(g, mouseX, mouseY, tooltip);
    }

    @Override
    protected boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        return button == 0 && list.mouseClicked(mouseX, mouseY, screen::onClose);
    }

    @Override
    protected boolean mouseDragged(final double mouseX, final double mouseY)
    {
        return list.mouseDragged(mouseY);
    }

    @Override
    protected void mouseReleased()
    {
        list.mouseReleased();
    }

    @Override
    protected boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        return list.mouseScrolled(mouseX, mouseY, delta);
    }
}
