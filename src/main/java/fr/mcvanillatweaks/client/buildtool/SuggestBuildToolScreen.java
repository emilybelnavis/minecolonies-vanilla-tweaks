package fr.mcvanillatweaks.client.buildtool;

import com.ldtteam.structurize.client.gui.WindowExtendedBuildTool;
import com.ldtteam.structurize.items.ModItems;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.network.messages.server.DirectPlaceMessage;
import com.minecolonies.core.network.messages.server.SwitchBuildingWithToolMessage;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.IconButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Replaces MineColonies' placement suggestion ({@code WindowSuggestBuildTool}), shown when a hut block is placed by hand:
 * "use the build tool" (needs one in the inventory; sends {@code SwitchBuildingWithToolMessage} and opens the build tool
 * at that spot), "place anyway" ({@code DirectPlaceMessage}) or cancel, like MineColonies.
 * <pre>
 * [hut] We suggest using the build tool...
 * [Use the build tool] [Place anyway]
 *               [Cancel]
 * </pre>
 */
public final class SuggestBuildToolScreen extends Screen
{
    private static final int WIDTH = 260;

    private final BlockPos pos;
    private final BlockState building;
    private final ItemStack stack;
    private int left;
    private int top;
    private int panelHeight;

    SuggestBuildToolScreen(final BlockPos pos, final BlockState building, final ItemStack stack)
    {
        super(Component.translatable("com.minecolonies.coremod.gui.placement.buildtool"));
        this.pos = pos;
        this.building = building;
        this.stack = stack;
    }

    private List<FormattedCharSequence> lines()
    {
        return font.split(Component.translatable("com.minecolonies.coremod.gui.placement.warning"), WIDTH - 44);
    }

    @Override
    protected void init()
    {
        panelHeight = 14 + Math.max(16, lines().size() * 10) + 12 + 20 + 4 + 20 + 8;
        left = (width - WIDTH) / 2;
        top = (height - panelHeight) / 2;
        final int by = top + panelHeight - 52;
        final int half = (WIDTH - 20) / 2;
        addRenderableWidget(new IconButton(left + 8, by, half, 20, new ItemStack(ModItems.buildTool.get()), Component.translatable("com.minecolonies.coremod.gui.placement.buildtool"),
          b -> buildTool()));
        addRenderableWidget(Button.builder(Component.translatable("com.minecolonies.coremod.gui.placement.direct"), b -> direct())
                              .bounds(left + WIDTH - 8 - half, by, half, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(left + (WIDTH - 100) / 2, by + 24, 100, 20).build());
    }

    private void buildTool()
    {
        if (minecraft == null || minecraft.player == null || minecraft.level == null)
        {
            return;
        }
        if (minecraft.player.getInventory().contains(item -> item.is(ModItems.buildTool.get())))
        {
            new SwitchBuildingWithToolMessage(stack).sendToServer();
            new WindowExtendedBuildTool(pos, 1, minecraft.level.registryAccess()).open();
        }
        else
        {
            MessageUtils.format("item.buildtool.missing").sendTo(minecraft.player);
            onClose();
        }
    }

    private void direct()
    {
        new DirectPlaceMessage(building, pos, stack).sendToServer();
        onClose();
    }

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        renderTransparentBackground(g);
        Gui.panel(g, left, top, WIDTH, panelHeight);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        g.renderItem(stack.isEmpty() ? new ItemStack(building.getBlock()) : stack, left + 10, top + 12);
        int ty = top + 12;
        for (final FormattedCharSequence line : lines())
        {
            g.drawString(font, line, left + 34, ty, Gui.LABEL, false);
            ty += 10;
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
