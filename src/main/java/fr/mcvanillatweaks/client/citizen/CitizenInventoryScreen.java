package fr.mcvanillatweaks.client.citizen;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.inventory.container.ContainerCitizenInventory;
import com.minecolonies.core.client.gui.containers.WindowCitizenInventory;
import fr.mcvanillatweaks.client.locate.LocateButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.Slot;

/**
 * "Inventory" tab: MineColonies' citizen inventory menu (unchanged, its slot positions come from the server) inside
 * the citizen frame, so the tabs, name, portrait and equipment stay where they are on the other pages.
 */
public final class CitizenInventoryScreen extends AbstractContainerScreen<ContainerCitizenInventory>
{
    /** Player inventory, then hotbar, below the citizen rows (same as MineColonies' menu). */
    private static final int ARMOR_SLOTS = 4;
    private static final int PLAYER_SLOTS = 36;

    private final WindowCitizenInventory original;
    private final CitizenFrame frame;
    /** Citizen inventory rows as laid out by the menu. */
    private final int menuRows;
    private final Tooltip tooltip = new Tooltip();

    CitizenInventoryScreen(final WindowCitizenInventory original, final ICitizenDataView citizen)
    {
        super(original.getMenu(), Minecraft.getInstance().player.getInventory(), original.getTitle());
        this.original = original;
        this.menuRows = Math.min(8, Math.max(0, (menu.slots.size() - ARMOR_SLOTS - PLAYER_SLOTS) / 9));
        this.frame = new CitizenFrame(citizen, CitizenPage.INVENTORY, Math.max(3, menuRows));
        this.imageWidth = CitizenFrame.WIDTH;
        this.imageHeight = frame.height();
    }

    @Override
    protected void init()
    {
        super.init();
        frame.layout(width, height);
        leftPos = frame.left();
        topPos = frame.top() + CitizenFrame.MENU_Y;
        addRenderableWidget(LocateButton.citizen(frame.locateX(), frame.locateY(), frame.citizen()));
    }

    @Override
    protected void containerTick()
    {
        frame.tick();
    }

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY)
    {
        frame.renderBackground(g);
        for (final Slot slot : menu.slots)
        {
            Gui.slot(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
        frame.renderForeground(g, mouseX, mouseY, true);
    }

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        // The name is drawn by the frame; only the player inventory title remains (coordinates relative to the menu).
        g.drawString(font, playerInventoryTitle, 8, 25 + menuRows * 18, Gui.LABEL, false);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        if (hoveredSlot != null && hoveredSlot.hasItem())
        {
            renderTooltip(g, mouseX, mouseY);
            return;
        }
        tooltip.clear();
        frame.collectTooltip(tooltip, mouseX, mouseY, true);
        tooltip.render(g, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        final CitizenPage tab = frame.tabAt(mouseX, mouseY);
        if (tab != null)
        {
            if (tab != CitizenPage.INVENTORY && button == 0 && menu.getCarried().isEmpty())
            {
                open(tab);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean hasClickedOutside(final double mouseX, final double mouseY, final int left, final int top, final int button)
    {
        return super.hasClickedOutside(mouseX, mouseY, left, top, button) && !frame.contains(mouseX, mouseY);
    }

    /**
     * Leaves the menu (the server closes it as usual) without clearing the screen first, so the cursor does not jump
     * to the centre, then opens the page like its tab would.
     */
    private void open(final CitizenPage page)
    {
        if (minecraft == null || minecraft.player == null)
        {
            return;
        }
        minecraft.player.connection.send(new ServerboundContainerClosePacket(menu.containerId));
        minecraft.player.containerMenu = minecraft.player.inventoryMenu;
        CitizenScreens.open(frame.citizen(), page, null);
    }

    @Override
    public void removed()
    {
        super.removed();
        // MineColonies' food tooltips look at the open citizen inventory.
        if (WindowCitizenInventory.activeCitizenInventory == original)
        {
            WindowCitizenInventory.activeCitizenInventory = null;
        }
    }
}
