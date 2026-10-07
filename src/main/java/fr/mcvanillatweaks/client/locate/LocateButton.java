package fr.mcvanillatweaks.client.locate;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.citizen.IconButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.BooleanSupplier;

/**
 * The "locate" button of the citizen and hut screens (recovery compass, alone or with a label): toggles the
 * {@link LocateBeams} beam, and closes the screen when a beam appears so the player sees it right away.
 */
public final class LocateButton extends IconButton
{
    public static final int SIZE = 20;

    private final BooleanSupplier located;
    private final boolean labelled;
    private Boolean shown;
    /** For the hut button: whose citizens it beams, to say so (or that there is nobody) in its tooltip. */
    private IBuildingView occupantsOf;
    private int occupants = -1;

    private LocateButton(final int x, final int y, final int width, final boolean labelled, final BooleanSupplier located, final BooleanSupplier toggle)
    {
        super(x, y, width, SIZE, new ItemStack(Items.RECOVERY_COMPASS), Component.empty(), b -> {
            try
            {
                if (toggle.getAsBoolean())
                {
                    Minecraft.getInstance().setScreen(null);
                }
            }
            catch (final LinkageError | RuntimeException e)
            {
                MineColoniesVanillaTweaks.LOGGER.warn("Could not locate", e);
            }
        });
        this.located = located;
        this.labelled = labelled;
        update();
    }

    /** Icon-only button for a screen header. */
    public static LocateButton citizen(final int x, final int y, final ICitizenDataView citizen)
    {
        return new LocateButton(x, y, SIZE, false, () -> LocateBeams.isLocated(citizen), () -> LocateBeams.toggle(citizen));
    }

    /** Icon-only button for a hut screen's header: beams the hut's workers (or residents), read when clicked. */
    public static LocateButton occupants(final int x, final int y, final IBuildingView building)
    {
        final LocateButton button = new LocateButton(x, y, SIZE, false, () -> LocateBeams.anyLocated(LocateBeams.occupants(building)),
          () -> LocateBeams.toggle(LocateBeams.occupants(building)));
        button.occupantsOf = building;
        button.shown = null;
        button.update();
        return button;
    }

    /** Labelled button, for a citizen chosen in a list. */
    public static LocateButton citizen(final int x, final int y, final int width, final java.util.function.Supplier<ICitizenDataView> citizen)
    {
        return new LocateButton(x, y, width, true, () -> citizen.get() != null && LocateBeams.isLocated(citizen.get()),
          () -> citizen.get() != null && LocateBeams.toggle(citizen.get()));
    }

    private void update()
    {
        final boolean now = located.getAsBoolean();
        final int count = occupantsOf == null ? -1 : LocateBeams.occupants(occupantsOf).size();
        if (shown != null && shown == now && count == occupants)
        {
            return;
        }
        shown = now;
        occupants = count;
        if (count == 0)
        {
            active = false;
            setTooltip(Tooltip.create(Component.translatable("mcvanillatweaks.locate.nobody")));
            return;
        }
        active = true;
        final Component title = Component.translatable(now ? "mcvanillatweaks.locate.stop"
                                                         : occupantsOf != null ? "mcvanillatweaks.locate.occupants" : "mcvanillatweaks.locate.button");
        if (labelled)
        {
            setMessage(title);
        }
        setTooltip(Tooltip.create(now ? title
                                    : title.copy().append("\n").append(Component.translatable("mcvanillatweaks.locate.hint").withStyle(ChatFormatting.GRAY))));
    }

    @Override
    public void renderWidget(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        update();
        super.renderWidget(g, mouseX, mouseY, partialTick);
    }
}
