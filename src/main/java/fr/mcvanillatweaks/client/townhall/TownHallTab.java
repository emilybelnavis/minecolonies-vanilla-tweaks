package fr.mcvanillatweaks.client.townhall;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import fr.mcvanillatweaks.client.citizen.Dropdown;
import fr.mcvanillatweaks.client.citizen.DropdownButton;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Content of one tab of {@link TownHallScreen}, drawn in the content area of the frame. Vanilla widgets (buttons, text
 * fields) are added in {@link #init}, which runs again when the screen is resized: state that must survive belongs in
 * fields of the tab.
 */
abstract class TownHallTab
{
    /** Gap between the columns of a page, and between a heading and what follows. */
    protected static final int GAP = 8;
    protected static final int HEADING = 12;

    protected final TownHallScreen screen;
    protected final Minecraft mc = Minecraft.getInstance();
    protected final Font font = mc.font;
    protected int x;
    protected int y;
    protected int width;
    protected int height;

    TownHallTab(final TownHallScreen screen)
    {
        this.screen = screen;
    }

    protected BuildingTownHall.View view()
    {
        return screen.view();
    }

    protected IColonyView colony()
    {
        return screen.view().getColony();
    }

    void layout(final int x, final int y, final int width, final int height)
    {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** Adds the page's vanilla widgets. */
    void init()
    {
    }

    void tick()
    {
    }

    /** Draws the page and fills {@code tooltip} with what is under the mouse. */
    abstract void render(GuiGraphics g, int mouseX, int mouseY, Tooltip tooltip);

    boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        return false;
    }

    boolean mouseDragged(final double mouseX, final double mouseY)
    {
        return false;
    }

    void mouseReleased()
    {
    }

    boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        return false;
    }

    /** A button that opens a drop-down list of {@code entries} (read when it opens) and calls {@code onPick} with the chosen index. */
    protected DropdownButton dropdown(final int bx, final int by, final int bw, final Component label,
      final Supplier<List<Dropdown.Entry>> entries, final IntSupplier selected, final IntConsumer onPick)
    {
        return add(new DropdownButton(bx, by, bw, 20, label,
          b -> screen.openDropdown(b.getX(), b.getY(), b.getWidth(), b.getHeight(), entries.get(), selected.getAsInt(), onPick)));
    }

    protected <T extends AbstractWidget> T add(final T widget)
    {
        return screen.addPageWidget(widget);
    }

    /** Section title on the panel, vanilla container label style. */
    protected void heading(final GuiGraphics g, final Component text, final int atX, final int atY, final int maxWidth)
    {
        g.drawString(font, Gui.fit(font, text, maxWidth), atX + 1, atY, Gui.LABEL, false);
    }
}
