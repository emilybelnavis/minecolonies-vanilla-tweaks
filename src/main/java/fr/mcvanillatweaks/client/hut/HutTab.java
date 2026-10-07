package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.buildings.views.IBuildingView;
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
 * Content of one page of {@link HutScreen}, like the town hall tabs: vanilla widgets are added in {@link #init} (run
 * again on resize, so state that must survive belongs in fields) and the rest is drawn in {@link #render}.
 */
public abstract class HutTab
{
    protected static final int GAP = 8;
    protected static final int HEADING = 12;

    protected final HutScreen screen;
    protected final Minecraft mc = Minecraft.getInstance();
    protected final Font font = mc.font;
    protected int x;
    protected int y;
    protected int width;
    protected int height;

    protected HutTab(final HutScreen screen)
    {
        this.screen = screen;
    }

    protected IBuildingView building()
    {
        return screen.building();
    }

    protected void layout(final int x, final int y, final int width, final int height)
    {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    protected void init()
    {
    }

    protected void tick()
    {
    }

    protected abstract void render(GuiGraphics g, int mouseX, int mouseY, Tooltip tooltip);

    protected boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        return false;
    }

    protected boolean mouseDragged(final double mouseX, final double mouseY)
    {
        return false;
    }

    protected void mouseReleased()
    {
    }

    protected boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        return false;
    }

    /** Called before the screen's own key handling (escape included). */
    protected boolean keyPressed(final int key)
    {
        return false;
    }

    protected <T extends AbstractWidget> T add(final T widget)
    {
        return screen.addPageWidget(widget);
    }

    protected DropdownButton dropdown(final int bx, final int by, final int bw, final Component label,
      final Supplier<List<Dropdown.Entry>> entries, final IntSupplier selected, final IntConsumer onPick)
    {
        return add(new DropdownButton(bx, by, bw, 20, label,
          b -> screen.openDropdown(b.getX(), b.getY(), b.getWidth(), b.getHeight(), entries.get(), selected.getAsInt(), onPick)));
    }

    /** Section title on the panel, vanilla container label style. */
    protected void heading(final GuiGraphics g, final Component text, final int atX, final int atY, final int maxWidth)
    {
        g.drawString(font, Gui.fit(font, text, maxWidth), atX + 1, atY, Gui.LABEL, false);
    }
}
