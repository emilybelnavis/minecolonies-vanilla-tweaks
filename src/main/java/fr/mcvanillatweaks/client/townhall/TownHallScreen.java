package fr.mcvanillatweaks.client.townhall;

import com.ldtteam.blockui.controls.Button;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.core.client.gui.townhall.AbstractWindowTownHall;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import com.minecolonies.core.network.messages.server.ClickGuiButtonTriggerMessage;
import fr.mcvanillatweaks.client.citizen.Dropdown;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Vanilla-style town hall screen, the counterpart of the citizen screen: one frame with creative inventory tabs on top,
 * the colony's name and summary in the header, and one page per tab.
 * <pre>
 *  [tab][tab][tab][tab][tab][tab][tab][tab]
 * +------------------------------------------------+
 * | Colony name                     Citizens 23/25 |
 * | Town hall - level 2              Happiness 9/10 |
 * |                                                |
 * |  page content                                  |
 * +------------------------------------------------+
 * </pre>
 */
public final class TownHallScreen extends Screen
{
    static final int WIDTH = 320;
    static final int HEIGHT = 212;
    private static final int CONTENT_X = 8;
    private static final int CONTENT_Y = 32;
    private static final int NAME_Y = 6;
    private static final int IDENTITY_Y = 17;

    private static final int TAB_WIDTH = 26;
    private static final int TAB_HEIGHT = 32;
    private static final int TAB_STEP = 27;
    private static final int TAB_RISE = 28;

    private final BuildingTownHall.View view;
    private final TownHallPage page;
    /** The MineColonies window this page replaces (null only in the dev preview). */
    @Nullable
    private final AbstractWindowTownHall source;
    private final TownHallTab tab;
    private final Tooltip tooltip = new Tooltip();
    private final Dropdown dropdown = new Dropdown();
    private int left;
    private int top;

    TownHallScreen(final BuildingTownHall.View view, final TownHallPage page, @Nullable final AbstractWindowTownHall source)
    {
        super(Component.literal(view.getColony().getName()));
        this.view = view;
        this.page = page;
        this.source = source;
        this.tab = switch (page)
        {
            case DASHBOARD -> new DashboardTab(this);
            case INFO -> new InfoTab(this);
            case CITIZENS -> new CitizensTab(this);
            case HAPPINESS -> new HappinessTab(this);
            case STATS -> new StatsTab(this);
            case PERMISSIONS -> new PermissionsTab(this);
            case ALLIANCES -> new AlliancesTab(this);
            case SETTINGS -> new SettingsTab(this);
            default -> new ActionsTab(this);
        };
    }

    BuildingTownHall.View view()
    {
        return view;
    }

    /** Sends the GUI click trigger MineColonies sends for this button of the replaced window (its advancements use it). */
    void trigger(final String buttonId)
    {
        if (source != null)
        {
            new ClickGuiButtonTriggerMessage(buttonId, source.getXmlResourceLocation()).sendToServer();
        }
    }

    /** Opens the drop-down list against a control of the page. */
    void openDropdown(final int ax, final int ay, final int aw, final int ah, final List<Dropdown.Entry> entries, final int selected,
      final java.util.function.IntConsumer onPick)
    {
        dropdown.open(ax, ay, aw, ah, entries, selected, onPick, height);
    }

    <T extends AbstractWidget> T addPageWidget(final T widget)
    {
        return addRenderableWidget(widget);
    }

    /**
     * Presses a button of the MineColonies window this page replaces: its own handler runs, with its GUI click trigger.
     *
     * @return false when the window has no such button.
     */
    boolean clickSource(final String id)
    {
        final Button button = source == null ? null : source.findPaneOfTypeByID(id, Button.class);
        if (button == null)
        {
            return false;
        }
        source.onButtonClicked(button);
        return true;
    }

    /** Re-creates the page's widgets (a section or a selection changed). */
    void rebuild()
    {
        rebuildWidgets();
    }

    void open(final TownHallPage target)
    {
        Gui.clickSound();
        TownHallScreens.open(view, target, source);
    }

    @Override
    protected void init()
    {
        dropdown.close();
        left = (width - WIDTH) / 2;
        top = Math.max(TAB_RISE, (height - HEIGHT - TAB_RISE) / 2 + TAB_RISE);
        try
        {
            tab.layout(left + CONTENT_X, top + CONTENT_Y, WIDTH - 2 * CONTENT_X, HEIGHT - CONTENT_Y - 8);
            tab.init();
        }
        catch (final LinkageError | RuntimeException e)
        {
            fail(e);
        }
    }

    @Override
    public void tick()
    {
        try
        {
            tab.tick();
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
        final TownHallPage[] pages = TownHallPage.values();
        for (int i = 0; i < pages.length; i++)
        {
            if (pages[i] != page)
            {
                renderTab(g, i, false);
            }
        }
        Gui.panel(g, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void render(final GuiGraphics g, final int realMouseX, final int realMouseY, final float partialTick)
    {
        // An open drop-down is modal: nothing under it reacts to the mouse.
        final int mouseX = dropdown.isOpen() ? -1000 : realMouseX;
        final int mouseY = dropdown.isOpen() ? -1000 : realMouseY;
        super.render(g, mouseX, mouseY, partialTick);
        tooltip.clear();
        try
        {
            renderTab(g, page.ordinal(), true);
            renderHeader(g, mouseX, mouseY);
            tab.render(g, mouseX, mouseY, tooltip);
            final TownHallPage hoveredTab = tabAt(mouseX, mouseY);
            if (hoveredTab != null)
            {
                tooltip.set(hoveredTab.title());
            }
            dropdown.render(g, font, realMouseX, realMouseY);
        }
        catch (final LinkageError | RuntimeException e)
        {
            fail(e);
            return;
        }
        tooltip.render(g, font, mouseX, mouseY);
    }

    private void renderHeader(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        final IColonyView colony = view.getColony();
        final Component name = Component.literal(colony.getName());
        g.drawString(font, Gui.fit(font, name, 180), left + 8, top + NAME_Y, Gui.LABEL, false);
        final Component identity = Component.translatable("mcvanillatweaks.townhall.identity",
          Component.translatable(view.getBuildingDisplayName()), view.getBuildingLevel());
        g.drawString(font, Gui.fit(font, identity, 180), left + 8, top + IDENTITY_Y, Gui.LABEL_SOFT, false);

        final Component citizens = Component.translatable("mcvanillatweaks.townhall.citizens_count",
          colony.getCitizens().size(), Math.max(colony.getCitizens().size(), colony.getCitizenCountLimit()));
        final Component happiness = Component.translatable("mcvanillatweaks.townhall.happiness_value", format(colony.getOverallHappiness()));
        final int right = left + WIDTH - 8;
        g.drawString(font, citizens, right - font.width(citizens), top + NAME_Y, Gui.LABEL_SOFT, false);
        g.drawString(font, happiness, right - font.width(happiness), top + IDENTITY_Y, Gui.LABEL_SOFT, false);
        if (Gui.inside(mouseX, mouseY, right - font.width(happiness), top + IDENTITY_Y - 1, font.width(happiness), 10))
        {
            tooltip.set(List.of(Component.translatable("mcvanillatweaks.townhall.overall_happiness"),
              Component.translatable("mcvanillatweaks.townhall.happiness_hint").withStyle(net.minecraft.ChatFormatting.GRAY)));
        }
    }

    static String format(final double value)
    {
        final double rounded = Math.round(value * 10.0D) / 10.0D;
        return rounded == Math.rint(rounded) ? Integer.toString((int) rounded) : String.format(Locale.ROOT, "%.1f", rounded);
    }

    private void renderTab(final GuiGraphics g, final int index, final boolean selected)
    {
        final int x = left + TAB_STEP * index;
        final int y = top - TAB_RISE;
        final int sprite = Math.min(index + 1, 6);
        g.blitSprite(ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_" + (selected ? "selected_" : "unselected_") + sprite),
          x, y, TAB_WIDTH, TAB_HEIGHT);
        g.pose().pushPose();
        g.pose().translate(0, 0, 100);
        g.renderItem(TownHallPage.values()[index].icon(), x + 5, y + 9);
        g.pose().popPose();
    }

    private TownHallPage tabAt(final double mouseX, final double mouseY)
    {
        final TownHallPage[] pages = TownHallPage.values();
        for (int i = 0; i < pages.length; i++)
        {
            if (Gui.inside(mouseX, mouseY, left + TAB_STEP * i, top - TAB_RISE, TAB_WIDTH, TAB_RISE))
            {
                return pages[i];
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        try
        {
            if (dropdown.mouseClicked(mouseX, mouseY, button))
            {
                return true;
            }
            final TownHallPage target = tabAt(mouseX, mouseY);
            if (target != null)
            {
                if (target != page && button == 0)
                {
                    open(target);
                }
                return true;
            }
            if (super.mouseClicked(mouseX, mouseY, button))
            {
                return true;
            }
            return tab.mouseClicked(mouseX, mouseY, button);
        }
        catch (final LinkageError | RuntimeException e)
        {
            fail(e);
            return true;
        }
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY)
    {
        return tab.mouseDragged(mouseX, mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button)
    {
        tab.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        if (dropdown.mouseScrolled(mouseX, mouseY, scrollY))
        {
            return true;
        }
        return tab.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers)
    {
        return dropdown.keyPressed(key) || super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    /** Something in MineColonies changed: fall back to its own window for this page. */
    private void fail(final Throwable e)
    {
        TownHallScreens.report(e);
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
