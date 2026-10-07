package fr.mcvanillatweaks.client.fixes;

import com.minecolonies.api.colony.ICitizen;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.client.gui.citizen.FamilyWindowCitizen;
import com.minecolonies.core.client.gui.citizen.HappinessWindowCitizen;
import com.minecolonies.core.client.gui.citizen.JobWindowCitizen;
import com.minecolonies.core.client.gui.citizen.MainWindowCitizen;
import com.minecolonies.core.client.gui.citizen.RequestWindowCitizen;
import com.minecolonies.core.client.gui.containers.WindowCitizenInventory;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import com.minecolonies.core.debug.DebugPlayerManager;
import com.minecolonies.core.debug.gui.DebugWindowCitizen;
import com.mojang.blaze3d.platform.NativeImage;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.citizen.LastCitizen;
import fr.mcvanillatweaks.client.citizen.VanillaTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.function.Consumer;

/**
 * MineColonies bug: the citizen inventory screen has no tabs, so there is no way back to the other citizen pages.
 * Adds the same tabs as the other citizen windows (same targets, same visibility rules), in the vanilla side-tab style.
 */
public final class CitizenInventoryTabs
{
    private static final int TAB_WIDTH = 28;
    private static final int TAB_HEIGHT = 24;
    private static final int TAB_SPACING = 26;

    private static ResourceLocation tabTexture;
    private static ResourceLocation selectedTabTexture;

    private CitizenInventoryTabs()
    {
    }

    public static void onScreenInit(final ScreenEvent.Init.Post event)
    {
        if (!(event.getScreen() instanceof WindowCitizenInventory screen))
        {
            return;
        }
        try
        {
            addTabs(event, screen);
        }
        catch (final LinkageError | RuntimeException e)
        {
            MineColoniesVanillaTweaks.LOGGER.warn("Could not add tabs to the citizen inventory", e);
        }
    }

    private static void addTabs(final ScreenEvent.Init.Post event, final WindowCitizenInventory screen)
    {
        final ICitizen data = screen.getCitizenData();
        final ICitizenDataView citizen = data instanceof ICitizenDataView view ? view : LastCitizen.get();
        if (citizen == null)
        {
            return;
        }
        LastCitizen.remember(citizen);

        final int x = screen.getGuiLeft() - TAB_WIDTH;
        int y = screen.getGuiTop() + 6;

        final Consumer<Runnable> open = action -> {
            screen.onClose();
            action.run();
        };

        event.addListener(new TabButton(x, y, Items.NAME_TAG, "com.minecolonies.coremod.gui.citizen.main", false,
          () -> open.accept(() -> new MainWindowCitizen(citizen).open())));
        y += TAB_SPACING;
        event.addListener(new TabButton(x, y, Items.WRITABLE_BOOK, "com.minecolonies.coremod.gui.citizen.requests", false,
          () -> open.accept(() -> new RequestWindowCitizen(citizen).open())));
        y += TAB_SPACING;
        event.addListener(new TabButton(x, y, Items.BUNDLE, "com.minecolonies.coremod.gui.citizen.inventory", true, () -> {}));
        y += TAB_SPACING;
        event.addListener(new TabButton(x, y, Items.CAKE, "com.minecolonies.coremod.gui.citizen.happiness", false,
          () -> open.accept(() -> new HappinessWindowCitizen(citizen).open())));
        y += TAB_SPACING;
        event.addListener(new TabButton(x, y, Items.POPPY, "com.minecolonies.coremod.gui.citizen.family", false,
          () -> open.accept(() -> new FamilyWindowCitizen(citizen).open())));
        y += TAB_SPACING;
        if (hasJobPage(citizen))
        {
            event.addListener(new TabButton(x, y, Items.IRON_PICKAXE, "com.minecolonies.coremod.gui.citizen.job", false,
              () -> open.accept(() -> new JobWindowCitizen(citizen).open())));
            y += TAB_SPACING;
        }
        if (Minecraft.getInstance().player != null && DebugPlayerManager.hasDebugEnabled(Minecraft.getInstance().player))
        {
            event.addListener(new TabButton(x, y, Items.COMPARATOR, "com.minecolonies.coremod.debug.gui.tabicon", false,
              () -> open.accept(() -> new DebugWindowCitizen(citizen).open())));
        }
    }

    /** Same rule as MineColonies' citizen windows: a work building that is not the library. */
    private static boolean hasJobPage(final ICitizenDataView citizen)
    {
        if (citizen.getColony() == null || citizen.getWorkBuilding() == null)
        {
            return false;
        }
        final IBuildingView building = citizen.getColony().getClientBuildingManager().getBuilding(citizen.getWorkBuilding());
        return building instanceof AbstractBuildingView && building.getBuildingType() != ModBuildings.library.get();
    }

    private static void ensureTextures()
    {
        if (tabTexture == null)
        {
            tabTexture = register("citizen_tab", VanillaTextures.tab(TAB_WIDTH, TAB_HEIGHT, true, true));
            selectedTabTexture = register("citizen_tab_selected", VanillaTextures.tab(TAB_WIDTH + 3, TAB_HEIGHT, true, false));
        }
    }

    private static ResourceLocation register(final String name, final NativeImage image)
    {
        final ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MineColoniesVanillaTweaks.MOD_ID, "dynamic/" + name);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        return id;
    }

    /** Vanilla creative-inventory-like side tab with an item icon. */
    private static final class TabButton extends AbstractButton
    {
        private final ItemStack icon;
        private final boolean selected;
        private final Runnable action;

        private TabButton(final int x, final int y, final net.minecraft.world.item.Item icon, final String tooltipKey, final boolean selected, final Runnable action)
        {
            super(x, y, selected ? TAB_WIDTH + 3 : TAB_WIDTH, TAB_HEIGHT, Component.translatable(tooltipKey));
            this.icon = new ItemStack(icon);
            this.selected = selected;
            this.action = action;
            setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        }

        @Override
        protected void renderWidget(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick)
        {
            ensureTextures();
            graphics.blit(selected ? selectedTabTexture : tabTexture, getX(), getY(), 0, 0, width, height, width, height);
            graphics.renderItem(icon, getX() + 6, getY() + 4);
            if (isHovered() && !selected)
            {
                graphics.fill(getX() + 3, getY() + 3, getX() + width - 1, getY() + height - 3, 0x30FFFFFF);
            }
        }

        @Override
        public void onPress()
        {
            if (!selected)
            {
                action.run();
            }
        }

        @Override
        protected void updateWidgetNarration(final NarrationElementOutput output)
        {
            defaultButtonNarrationText(output);
        }
    }
}
