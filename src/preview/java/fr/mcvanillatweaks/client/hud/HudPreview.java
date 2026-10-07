package fr.mcvanillatweaks.client.hud;

import com.minecolonies.core.colony.buildings.utils.BuildingBuilderResource.RessourceAvailability;
import fr.mcvanillatweaks.client.data.BuilderRef;
import fr.mcvanillatweaks.client.data.HudSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Dev preview: draws the resource HUD with sample construction sites (captured as {@code hut_hud.png}).
 */
public final class HudPreview
{
    private HudPreview()
    {
    }

    public static Screen screen()
    {
        final HudSnapshot snapshot = new HudSnapshot(List.of(
          new HudSnapshot.Panel(new BuilderRef(Level.OVERWORLD, 1, new BlockPos(10, 64, 10)), false, "Bob Bâtisseur", "Boulangerie niveau 2",
            HudSnapshot.State.OK, 64, 42, List.of(
            new HudSnapshot.Line(new ItemStack(Items.OAK_PLANKS), "Planches de chêne", 12, 0, 96, RessourceAvailability.DONT_HAVE),
            new HudSnapshot.Line(new ItemStack(Items.COBBLESTONE), "Pierre taillée", 30, 40, 64, RessourceAvailability.HAVE_ENOUGH),
            new HudSnapshot.Line(new ItemStack(Items.GLASS_PANE), "Vitre", 8, 0, 16, RessourceAvailability.IN_DELIVERY),
            new HudSnapshot.Line(new ItemStack(Items.TORCH), "Torche", 12, 0, 12, RessourceAvailability.NOT_NEEDED)), 2),
          new HudSnapshot.Panel(new BuilderRef(Level.OVERWORLD, 1, new BlockPos(20, 64, 20)), false, "Alice Bâtisseuse", "Maison niveau 1",
            HudSnapshot.State.OK, 100, 100, List.of(), 0)));
        return new Screen(Component.empty())
        {
            @Override
            public void render(final GuiGraphics graphics, final int mouseX, final int mouseY, final float partialTick)
            {
                super.render(graphics, mouseX, mouseY, partialTick);
                HudRenderer.INSTANCE.draw(graphics, font, HudRenderer.INSTANCE.place(font, snapshot, width, height));
            }
        };
    }
}
