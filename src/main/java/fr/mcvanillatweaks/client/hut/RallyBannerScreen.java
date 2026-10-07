package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.items.component.RallyData;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.items.ItemBannerRallyGuards;
import com.minecolonies.core.network.messages.server.RemoveFromRallyingListMessage;
import com.minecolonies.core.network.messages.server.ToggleBannerRallyGuardsMessage;
import com.mojang.datafixers.util.Pair;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces MineColonies' rally banner window ({@code WindowBannerRallyGuards}): the guard huts the banner rallies, each
 * with its guards, position and distance (a tower that no longer exists in red), a remove button on each row
 * ({@code RemoveFromRallyingListMessage}, and the banner's own copy updated like MineColonies), and the rally / dismiss
 * button ({@code ToggleBannerRallyGuardsMessage}, then the screen closes, as MineColonies).
 * <pre>
 * [banner] Rally banner          Guards at their posts
 * | [tower] Guard tower 3      2 guards · 12 m    [x] |
 * [Rally]                                    [Done]
 * </pre>
 */
public final class RallyBannerScreen extends Screen
{
    private static final int WIDTH = 280;
    private static final int HEIGHT = 210;
    private static final int ROW = 24;
    private static final int REMOVE = 16;

    /** Towers shown by the dev preview, which has no level to look them up in. */
    static List<Pair<BlockPos, AbstractBuildingGuards.View>> previewTowers;

    private final ItemStack banner;
    private final Tooltip tooltip = new Tooltip();
    private final ScrollArea list = new ScrollArea();
    private List<Pair<BlockPos, AbstractBuildingGuards.View>> towers = List.of();
    private int left;
    private int top;
    private int ticks;

    public RallyBannerScreen(final ItemStack banner)
    {
        super(banner.getHoverName());
        this.banner = banner;
        refresh();
    }

    private void refresh()
    {
        try
        {
            final Level level = Minecraft.getInstance().level;
            towers = previewTowers != null ? previewTowers : level == null ? List.of() : ItemBannerRallyGuards.getGuardTowerViews(banner, level);
        }
        catch (final LinkageError | RuntimeException e)
        {
            towers = List.of();
        }
    }

    private boolean active()
    {
        try
        {
            return RallyData.readFromItemStack(banner).active();
        }
        catch (final LinkageError | RuntimeException e)
        {
            return false;
        }
    }

    @Override
    protected void init()
    {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        final int bw = (WIDTH - 24) / 2;
        addRenderableWidget(Button.builder(Component.translatable(active() ? "com.minecolonies.coremod.item.bannerrallyguards.gui.dismiss"
                                                                           : "com.minecolonies.coremod.item.bannerrallyguards.gui.rally"), b -> {
            new ToggleBannerRallyGuardsMessage(banner).sendToServer();
            onClose();
        }).bounds(left + 8, top + HEIGHT - 28, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + WIDTH - 8 - bw, top + HEIGHT - 28, bw, 20).build());
    }

    @Override
    public void tick()
    {
        if (++ticks % 20 == 0)
        {
            refresh();
        }
    }

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        renderTransparentBackground(g);
        Gui.panel(g, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        tooltip.clear();
        super.render(g, mouseX, mouseY, partialTick);
        g.renderItem(banner, left + 8, top + 7);
        final boolean active = active();
        final Component state = Component.translatable(active ? "mcvanillatweaks.rally.active" : "mcvanillatweaks.rally.inactive");
        final int stateWidth = font.width(state);
        g.drawString(font, Gui.fit(font, title, WIDTH - 44 - stateWidth), left + 28, top + 11, Gui.LABEL, false);
        g.drawString(font, state, left + WIDTH - 9 - stateWidth, top + 11, active ? 0xFF00AA00 : Gui.LABEL_SOFT, false);
        g.drawString(font, Component.translatable("mcvanillatweaks.rally.towers", towers.size()), left + 9, top + 30, Gui.LABEL, false);

        final int listTop = top + 42;
        list.set(left + 8, listTop, WIDTH - 16, top + HEIGHT - 36 - listTop, towers.size() * ROW, ROW);
        list.begin(g);
        if (towers.isEmpty())
        {
            int ty = list.visibleTop() + 6;
            for (final var line : font.split(Component.translatable("com.minecolonies.coremod.item.bannerrallyguards.tooltipempty"), list.listWidth() - 10))
            {
                g.drawString(font, line, list.listX() + 5, ty, Gui.LIST_SOFT, true);
                ty += 10;
            }
        }
        for (int i = 0; i < towers.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            renderRow(g, towers.get(i), rowY, mouseX, mouseY);
        }
        list.end(g);
        tooltip.render(g, font, mouseX, mouseY);
    }

    private int removeX()
    {
        return list.listX() + list.listWidth() - REMOVE - 4;
    }

    private void renderRow(final GuiGraphics g, final Pair<BlockPos, AbstractBuildingGuards.View> tower, final int rowY, final int mouseX, final int mouseY)
    {
        final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
        final BlockPos pos = tower.getFirst();
        final AbstractBuildingGuards.View view = tower.getSecond();
        final int tx = list.listX() + 24;
        final int tw = removeX() - 6 - tx;
        g.renderItem(view != null ? HutScreen.hutIcon(view) : new ItemStack(Items.BARRIER), list.listX() + 4, rowY + 4);
        final Component name = view != null ? Component.translatable(view.getBuildingDisplayName()).append(" " + view.getBuildingLevel())
                                            : Component.translatable("com.minecolonies.coremod.item.bannerrallyguards.gui.towermissing");
        g.drawString(font, Gui.fit(font, name, tw), tx, rowY + 3, view != null ? Gui.WHITE : Gui.RED, true);
        Component detail = Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        if (view != null)
        {
            detail = Component.translatable("mcvanillatweaks.rally.guards", view.getGuards().size()).append(" · ").append(detail);
        }
        if (minecraft != null && minecraft.player != null)
        {
            final int distance = (int) Math.sqrt(minecraft.player.blockPosition().distSqr(pos));
            detail = detail.copy().append(" · ").append(Component.translatable("mcvanillatweaks.rally.distance", distance));
        }
        g.drawString(font, Gui.fit(font, detail, tw), tx, rowY + 13, Gui.LIST_SOFT, true);
        final boolean overRemove = Gui.inside(mouseX, mouseY, removeX(), rowY + 4, REMOVE, REMOVE) && list.contains(mouseX, mouseY);
        Gui.textButton(g, font, removeX(), rowY + 4, REMOVE, REMOVE, "✖", true, overRemove);
        if (overRemove)
        {
            tooltip.set(Component.translatable("com.minecolonies.coremod.item.bannerrallyguards.gui.remove"));
        }
        else if (hovered && view != null)
        {
            final List<Component> lines = new ArrayList<>();
            lines.add(name);
            for (final int id : view.getGuards())
            {
                final var citizen = view.getColony().getCitizen(id);
                if (citizen != null)
                {
                    lines.add(Component.literal("- " + citizen.getName()).withStyle(ChatFormatting.GRAY));
                }
            }
            tooltip.setAround(lines, mouseX, rowY, rowY + ROW);
        }
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (list.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button == 0 && list.overRows(mouseX, mouseY))
        {
            final int index = (int) Math.floor((mouseY - list.listY()) / ROW);
            if (index >= 0 && index < towers.size() && Gui.inside(mouseX, mouseY, removeX(), list.listY() + index * ROW + 4, REMOVE, REMOVE))
            {
                Gui.clickSound();
                final BlockPos pos = towers.get(index).getFirst();
                new RemoveFromRallyingListMessage(banner, pos).sendToServer();
                ItemBannerRallyGuards.removeGuardTowerAtLocation(banner, pos);
                refresh();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dx, final double dy)
    {
        return list.mouseDragged(mouseY) || super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button)
    {
        list.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        return list.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
