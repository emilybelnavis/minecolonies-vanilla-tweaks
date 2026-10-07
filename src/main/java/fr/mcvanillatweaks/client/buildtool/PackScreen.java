package fr.mcvanillatweaks.client.buildtool;

import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.structurize.storage.StructurePackMeta;
import com.ldtteam.structurize.storage.StructurePacks;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Replaces Structurize's style choice ({@code WindowSwitchPack}): the style packs grouped by owner (shuffled within a
 * group, as Structurize does so no author always comes first), as cards with the pack's own picture, name, description
 * and authors, and a search on the name. The panel takes most of the screen, with as many cards per row as fit. A click selects the pack ({@code switchSelectedPack}) and goes back
 * to the window that asked, like Structurize; disabled packs (player blueprints not allowed) can't be picked.
 * <pre>
 * Choose a style                       [Search...      ]
 * +----------------------------------------------------+
 * | ldtteam                                            |
 * | [pic] Medieval Oak       | [pic] Nordic            |
 * |       Oak and stone...   |       ...               |
 * +----------------------------------------------------+
 * [Cancel]
 * </pre>
 */
public final class PackScreen extends Screen
{
    private static final int MIN_WIDTH = 340;
    private static final int MAX_WIDTH = 760;
    private static final int MIN_HEIGHT = 222;
    private static final int MARGIN = 16;
    private static final int HEADER = 16;
    private static final int CARD = 58;
    private static final int PICTURE = 48;
    /** Narrowest card, picture included. */
    private static final int CARD_WIDTH = 220;

    /** A list row: an owner's heading, or up to {@link #columns} packs. */
    private record Row(@Nullable String owner, List<StructurePackMeta> packs)
    {
        int height()
        {
            return owner != null ? HEADER : CARD;
        }
    }

    @Nullable
    private final Supplier<BOWindow> previous;
    private final Predicate<StructurePackMeta> filter;
    private final ScrollArea list = new ScrollArea();
    private final Tooltip tooltip = new Tooltip();
    @Nullable
    private CompletableFuture<List<StructurePackMeta>> loading;
    private List<StructurePackMeta> packs = List.of();
    private List<Row> rows = List.of();
    private String search = "";
    private int left;
    private int top;
    private int panelWidth = MIN_WIDTH;
    private int panelHeight = MIN_HEIGHT;
    private int columns = 2;

    PackScreen(@Nullable final Supplier<BOWindow> previous, final Predicate<StructurePackMeta> filter)
    {
        super(Component.translatable("structurize.gui.buildtool.switchpack"));
        this.previous = previous;
        this.filter = filter;
        // As Structurize: wait for the packs to be loaded, off the game thread.
        loading = CompletableFuture.supplyAsync(() -> StructurePacks.waitUntilFinishedLoading()
                                                        ? new ArrayList<>(StructurePacks.getPackMetas().stream().filter(filter).toList())
                                                        : new ArrayList<>());
    }

    // ------------------------------------------------------------------ list

    private void layoutRows()
    {
        final Map<String, List<StructurePackMeta>> owners = new TreeMap<>();
        for (final StructurePackMeta meta : packs)
        {
            if (StringUtils.isBlank(search) || StringUtils.containsIgnoreCase(meta.getName(), search))
            {
                owners.computeIfAbsent(meta.getOwner(), key -> new ArrayList<>()).add(meta);
            }
        }
        final List<Row> list = new ArrayList<>();
        for (final Map.Entry<String, List<StructurePackMeta>> entry : owners.entrySet())
        {
            list.add(new Row(StringUtils.capitalize(entry.getKey()), List.of()));
            final List<StructurePackMeta> metas = entry.getValue();
            for (int i = 0; i < metas.size(); i += columns)
            {
                list.add(new Row(null, List.copyOf(metas.subList(i, Math.min(metas.size(), i + columns)))));
            }
        }
        rows = list;
    }

    private int contentHeight()
    {
        int h = 0;
        for (final Row row : rows)
        {
            h += row.height();
        }
        return h;
    }

    private void pick(final StructurePackMeta pack)
    {
        if (pack.isDisabled())
        {
            return;
        }
        Gui.clickSound();
        StructurePacks.switchSelectedPack(pack);
        back();
    }

    private void back()
    {
        if (previous == null)
        {
            onClose();
        }
        else
        {
            previous.get().open();
        }
    }

    // ------------------------------------------------------------------ screen

    @Override
    protected void init()
    {
        panelWidth = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, width - 2 * MARGIN));
        panelHeight = Math.max(MIN_HEIGHT, height - 2 * MARGIN);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        // List width minus the scroller, split into cards of at least CARD_WIDTH.
        columns = Math.max(2, (panelWidth - 16 - Gui.SCROLLER_WIDTH - 4) / CARD_WIDTH);
        layoutRows();
        final EditBox box = addRenderableWidget(new EditBox(font, left + panelWidth - 8 - 160, top + 6, 160, 16, Component.translatable("mcvanillatweaks.hut.search")));
        box.setHint(Component.translatable("mcvanillatweaks.hut.search").withStyle(ChatFormatting.DARK_GRAY));
        box.setMaxLength(50);
        box.setValue(search);
        box.setResponder(text -> {
            search = text;
            layoutRows();
        });
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> back()).bounds(left + 8, top + panelHeight - 28, 70, 20).build());
    }

    @Override
    public void tick()
    {
        if (loading != null && loading.isDone())
        {
            try
            {
                final List<StructurePackMeta> loaded = new ArrayList<>(loading.get());
                Collections.shuffle(loaded);
                packs = loaded;
            }
            catch (final Exception e)
            {
                MineColoniesVanillaTweaks.LOGGER.error("Error resolving style packs", e);
            }
            loading = null;
            layoutRows();
        }
    }

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        renderTransparentBackground(g);
        Gui.panel(g, left, top, panelWidth, panelHeight);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        tooltip.clear();
        g.drawString(font, Component.translatable("mcvanillatweaks.buildtool.styles"), left + 8, top + 10, Gui.LABEL, false);

        final int listTop = top + 28;
        final int listBottom = top + panelHeight - 34;
        list.set(left + 8, listTop, panelWidth - 16, listBottom - listTop, contentHeight(), CARD);
        list.begin(g);
        if (loading != null || rows.isEmpty())
        {
            g.drawString(font, Component.translatable(loading != null ? "mcvanillatweaks.buildtool.loading" : "mcvanillatweaks.hut.search_none"),
              list.listX() + 4, list.visibleTop() + 6, Gui.LIST_SOFT, true);
        }
        final StructurePackMeta selected = StructurePacks.getSelectedPack();
        int rowY = list.listY();
        final int cardWidth = list.listWidth() / columns;
        for (final Row row : rows)
        {
            final int h = row.height();
            if (rowY + h >= list.visibleTop() && rowY <= list.visibleBottom())
            {
                if (row.owner() != null)
                {
                    g.drawString(font, row.owner(), list.listX() + 4, rowY + 5, Gui.YELLOW, true);
                }
                else
                {
                    for (int i = 0; i < row.packs().size(); i++)
                    {
                        card(g, row.packs().get(i), list.listX() + i * cardWidth, rowY, cardWidth, selected, mouseX, mouseY);
                    }
                }
            }
            rowY += h;
        }
        list.end(g);
        if (selected != null)
        {
            final Component current = Component.translatable("mcvanillatweaks.buildtool.current", selected.getName());
            g.drawString(font, Gui.fit(font, current, panelWidth - 100), left + panelWidth - 8 - Math.min(font.width(current), panelWidth - 100), top + panelHeight - 22,
              Gui.LABEL_SOFT, false);
        }
        tooltip.render(g, font, mouseX, mouseY);
    }

    private void card(final GuiGraphics g, final StructurePackMeta pack, final int x, final int y, final int w, @Nullable final StructurePackMeta selected,
      final int mouseX, final int mouseY)
    {
        final boolean hovered = list.overRows(mouseX, mouseY) && Gui.inside(mouseX, mouseY, x, y, w, CARD);
        if (selected != null && selected.getName().equals(pack.getName()))
        {
            g.fill(x, y, x + w, y + CARD, Gui.ROW_SELECTED);
        }
        else if (hovered)
        {
            g.fill(x, y, x + w, y + CARD, Gui.ROW_HOVER);
        }
        final ResourceLocation picture = PackIcons.icon(pack);
        final int px = x + 4;
        final int py = y + (CARD - PICTURE) / 2;
        if (picture != null)
        {
            g.blit(picture, px, py, 0, 0, PICTURE, PICTURE, PICTURE, PICTURE);
        }
        else
        {
            g.pose().pushPose();
            g.pose().translate(px + 4, py + 4, 0);
            g.pose().scale(2.5F, 2.5F, 1);
            g.renderItem(new ItemStack(Items.STRUCTURE_BLOCK), 0, 0);
            g.pose().popPose();
        }
        final int tx = px + PICTURE + 5;
        final int tw = x + w - 4 - tx;
        final int nameColour = pack.isDisabled() ? Gui.LIST_SOFT : Gui.WHITE;
        g.drawString(font, Gui.fit(font, Component.literal(pack.getName()), tw), tx, y + 5, nameColour, true);
        final List<FormattedCharSequence> desc = font.split(Component.literal(pack.getDesc() == null ? "" : pack.getDesc()), tw);
        for (int i = 0; i < Math.min(2, desc.size()); i++)
        {
            g.drawString(font, desc.get(i), tx, y + 17 + i * 10, Gui.LIST_SOFT, true);
        }
        final Component authors = Component.translatable("com.ldtteam.structurize.gui.switchpack.authors", String.join(", ", pack.getAuthors()));
        g.drawString(font, Gui.fit(font, authors, tw), tx, y + CARD - 14, 0xFF808080, true);
        if (hovered)
        {
            final List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(pack.getName()));
            if (desc.size() > 2)
            {
                lines.add(Component.literal(pack.getDesc()).withStyle(ChatFormatting.GRAY));
            }
            lines.add(authors.copy().withStyle(ChatFormatting.GRAY));
            lines.add(pack.isDisabled()
                        ? Component.translatable("com.ldtteam.structurize.gui.switchpack.pack_disabled.hover_text",
                          Component.literal("allowPlayerSchematics").withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.RED)
                        : Component.translatable("mcvanillatweaks.buildtool.pick").withStyle(ChatFormatting.YELLOW));
            tooltip.set(lines);
        }
    }

    @Nullable
    private StructurePackMeta packAt(final double mouseX, final double mouseY)
    {
        if (!list.overRows(mouseX, mouseY))
        {
            return null;
        }
        int rowY = list.listY();
        final int cardWidth = list.listWidth() / columns;
        for (final Row row : rows)
        {
            if (mouseY >= rowY && mouseY < rowY + row.height())
            {
                final int index = (int) ((mouseX - list.listX()) / cardWidth);
                return row.owner() == null && index >= 0 && index < row.packs().size() ? row.packs().get(index) : null;
            }
            rowY += row.height();
        }
        return null;
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (list.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        final StructurePackMeta pack = button == 0 ? packAt(mouseX, mouseY) : null;
        if (pack != null)
        {
            pick(pack);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY)
    {
        return list.mouseDragged(mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
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
