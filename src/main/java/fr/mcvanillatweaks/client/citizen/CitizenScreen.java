package fr.mcvanillatweaks.client.citizen;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.core.client.gui.citizen.AbstractWindowCitizen;
import com.minecolonies.core.client.gui.citizen.RequestWindowCitizen;
import fr.mcvanillatweaks.client.locate.LocateButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla-style citizen screen: one frame, one tab per page (see {@link CitizenFrame}). The inventory tab is the
 * separate {@link CitizenInventoryScreen}, drawn with the same frame.
 */
public final class CitizenScreen extends Screen
{
    private final CitizenFrame frame;
    /** The MineColonies window this page replaces (null only in the dev preview). */
    @Nullable
    private final AbstractWindowCitizen source;
    private final PageView view;
    private final Tooltip tooltip = new Tooltip();
    private LocateButton locate;

    CitizenScreen(final ICitizenDataView citizen, final CitizenPage page, @Nullable final AbstractWindowCitizen source)
    {
        super(Component.literal(citizen.getName()));
        this.frame = new CitizenFrame(citizen, page, CitizenInfo.inventoryRows(citizen));
        this.source = source;
        this.view = switch (page)
        {
            case SKILLS -> new SkillsPage(this);
            case REQUESTS -> new RequestsPage(this, source instanceof RequestWindowCitizen requests ? requests : null);
            case HAPPINESS -> new HappinessPage(this);
            case FAMILY -> new FamilyPage(this);
            default -> new OverviewPage(this);
        };
    }

    CitizenFrame frame()
    {
        return frame;
    }

    @Nullable
    AbstractWindowCitizen source()
    {
        return source;
    }

    void open(final CitizenPage page)
    {
        CitizenScreens.open(frame.citizen(), page, source);
    }

    @Override
    protected void init()
    {
        frame.layout(width, height);
        // Also called from tick when the frame's height changes, without clearing the widgets.
        if (locate != null)
        {
            removeWidget(locate);
        }
        locate = addRenderableWidget(LocateButton.citizen(frame.locateX(), frame.locateY(), frame.citizen()));
        view.layout(frame.left() + CitizenFrame.CONTENT_X, frame.top() + CitizenFrame.PAGE_Y, CitizenFrame.CONTENT_WIDTH,
          frame.height() - CitizenFrame.PAGE_Y - 7);
    }

    @Override
    public void tick()
    {
        try
        {
            frame.tick();
            final int rows = CitizenInfo.inventoryRows(frame.citizen());
            if (CitizenFrame.height(rows) != frame.height())
            {
                frame.setRows(rows);
                init();
            }
            view.tick();
        }
        catch (final LinkageError | RuntimeException e)
        {
            fail(e);
        }
    }

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        renderTransparentBackground(g);
        frame.renderBackground(g);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        tooltip.clear();
        try
        {
            frame.renderForeground(g, mouseX, mouseY, false);
            view.render(g, mouseX, mouseY, tooltip);
            if (tooltip.isEmpty())
            {
                frame.collectTooltip(tooltip, mouseX, mouseY, false);
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            fail(e);
            return;
        }
        tooltip.render(g, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        try
        {
            final CitizenPage tab = frame.tabAt(mouseX, mouseY);
            if (tab != null)
            {
                if (tab != frame.page() && button == 0)
                {
                    open(tab);
                }
                return true;
            }
            if (view.mouseClicked(mouseX, mouseY, button))
            {
                return true;
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            fail(e);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY)
    {
        return view.mouseDragged(mouseX, mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button)
    {
        view.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        return view.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers)
    {
        // Like a container screen, the inventory key closes it.
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode))
        {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    /** Something in MineColonies changed: fall back to its own window for this page. */
    private void fail(final Throwable e)
    {
        CitizenScreens.report(e);
        if (minecraft != null && minecraft.screen == this)
        {
            if (source != null)
            {
                source.open();
            }
            else
            {
                onClose();
            }
        }
    }
}
