package fr.mcvanillatweaks.client.townhall;

import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.client.gui.GuiGraphics;

/** The town hall's dashboard tab (no MineColonies window): {@link DashboardView}, whose requests lead to the clipboard. */
final class DashboardTab extends TownHallTab
{
    private final DashboardView dashboard;

    DashboardTab(final TownHallScreen screen)
    {
        super(screen);
        dashboard = new DashboardView(colony(), this::view, () -> mc.setScreen(new ClipboardScreen(colony(), false, ClipboardScreen.Page.REQUESTS)));
    }

    @Override
    void tick()
    {
        dashboard.tick();
    }

    @Override
    void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        dashboard.render(g, x, y, width, height, mouseX, mouseY, tooltip);
    }

    @Override
    boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        return dashboard.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    boolean mouseDragged(final double mouseX, final double mouseY)
    {
        return dashboard.mouseDragged(mouseX, mouseY);
    }

    @Override
    void mouseReleased()
    {
        dashboard.mouseReleased();
    }

    @Override
    boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        return dashboard.mouseScrolled(mouseX, mouseY, delta);
    }
}
