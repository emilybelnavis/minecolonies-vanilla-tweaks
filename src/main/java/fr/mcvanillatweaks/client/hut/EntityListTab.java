package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.modules.IEntityListModuleView;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Entity list page (MineColonies' filterable entity list; the guards' hostiles): every monster MineColonies knows, with
 * its spawn egg, a vanilla checkbox, a search field and the reset button, laid out like {@link ItemListTab}. The guards'
 * list is inverted: the listed monsters are the ones the guards leave alone, so a checked box means "attacked". A row
 * click switches it through the module (MineColonies' message). Reset removes every entry with the same message,
 * where MineColonies' own button only cleared its local copy.
 * <pre>
 * [Search...                         ]  [Reset]
 * 40 / 42 attacked
 * +-----------------------------------------------+
 * | [egg] Zombie                             [x]  |
 * +-----------------------------------------------+
 * </pre>
 */
final class EntityListTab extends HutTab
{
    private static final int ROW = 20;
    private static final int BUTTON_HEIGHT = 20;
    private static final int RESET_WIDTH = 90;
    private static final int BOX = 13;
    private static final String HOSTILES = "hostiles";

    /** Set by the dev preview: MineColonies only lists the monsters once a world is loaded. */
    @Nullable
    static Collection<ResourceLocation> previewMonsters;

    private final IEntityListModuleView module;
    private final ScrollArea list = new ScrollArea();
    private final List<ResourceLocation> all = new ArrayList<>();
    private final Map<ResourceLocation, ItemStack> icons = new HashMap<>();
    private List<ResourceLocation> shown = List.of();
    private String filter = "";
    private int filterTimer;

    EntityListTab(final HutScreen screen, final IEntityListModuleView module)
    {
        super(screen);
        this.module = module;
        all.addAll(previewMonsters != null ? previewMonsters : IColonyManager.getInstance().getCompatibilityManager().getAllMonsters());
        refresh();
    }

    private boolean hostiles()
    {
        return HOSTILES.equals(module.getId());
    }

    /** What MineColonies' button shows as "On". */
    private boolean enabled(final ResourceLocation entity)
    {
        return module.isAllowedEntity(entity) != module.isInverted();
    }

    private static Component name(final ResourceLocation entity)
    {
        return BuiltInRegistries.ENTITY_TYPE.get(entity).getDescription();
    }

    /** The monster's spawn egg, or a spawner for mobs without one. */
    private ItemStack icon(final ResourceLocation entity)
    {
        return icons.computeIfAbsent(entity, id -> {
            final EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
            final SpawnEggItem egg = SpawnEggItem.byId(type);
            return new ItemStack(egg != null ? egg : Items.SPAWNER);
        });
    }

    // ------------------------------------------------------------------ widgets

    @Override
    protected void init()
    {
        final EditBox search = add(new EditBox(font, x + 1, y + 1, width - RESET_WIDTH - GAP - 2, BUTTON_HEIGHT - 2, Component.translatable("mcvanillatweaks.hut.search")));
        search.setHint(Component.translatable("mcvanillatweaks.hut.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setMaxLength(50);
        search.setValue(filter);
        search.setResponder(text -> {
            if (!text.equals(filter))
            {
                filter = text;
                filterTimer = 5;
            }
        });
        final Button reset = add(Button.builder(Component.translatable("mcvanillatweaks.hut.list.reset"), b -> reset())
                                   .bounds(x + width - RESET_WIDTH, y, RESET_WIDTH, BUTTON_HEIGHT).build());
        reset.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(
          hostiles() ? "mcvanillatweaks.hut.entity.reset_hostiles" : "com.minecolonies.coremod.gui.workerhuts.resetdefault")));
    }

    @Override
    protected void tick()
    {
        if (filterTimer > 0 && --filterTimer == 0)
        {
            refresh();
        }
    }

    /** Filters like MineColonies (name or id) and puts the enabled entities first, sorted only on open and search. */
    private void refresh()
    {
        final String search = filter.toLowerCase(Locale.ROOT).trim();
        final List<ResourceLocation> result = new ArrayList<>();
        for (final ResourceLocation entity : all)
        {
            if (search.isEmpty() || name(entity).getString().toLowerCase(Locale.ROOT).contains(search) || entity.toString().contains(search))
            {
                result.add(entity);
            }
        }
        result.sort(Comparator.<ResourceLocation, Boolean>comparing(entity -> !enabled(entity)).thenComparing(entity -> name(entity).getString()));
        shown = result;
    }

    private void toggle(final ResourceLocation entity)
    {
        if (module.isAllowedEntity(entity))
        {
            module.removeEntity(entity);
        }
        else
        {
            module.addEntity(entity);
        }
    }

    private void reset()
    {
        for (final ResourceLocation entity : all)
        {
            if (module.isAllowedEntity(entity))
            {
                module.removeEntity(entity);
            }
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final long on = all.stream().filter(this::enabled).count();
        final Component count = Component.translatable(hostiles() ? "mcvanillatweaks.hut.entity.count_hostiles" : "mcvanillatweaks.hut.list.count", on, all.size());
        g.drawString(font, count, x + 1, y + BUTTON_HEIGHT + 8, Gui.LABEL, false);
        if (hostiles())
        {
            final int hintX = x + font.width(count) + 12;
            g.drawString(font, Gui.fit(font, Component.literal("· ").append(Component.translatable("mcvanillatweaks.hut.entity.hostiles_hint")), x + width - hintX),
              hintX, y + BUTTON_HEIGHT + 8, Gui.LABEL_SOFT, false);
        }

        final int top = y + BUTTON_HEIGHT + 22;
        list.set(x, top, width, y + height - top, shown.size() * ROW, ROW);
        list.begin(g);
        if (shown.isEmpty())
        {
            g.drawString(font, Component.translatable(all.isEmpty() ? "mcvanillatweaks.hut.list.empty" : "mcvanillatweaks.hut.search_none"),
              list.listX() + 4, list.visibleTop() + 6, Gui.LIST_SOFT, true);
        }
        for (int i = 0; i < shown.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final ResourceLocation entity = shown.get(i);
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            final boolean enabled = enabled(entity);
            g.renderItem(icon(entity), list.listX() + 3, rowY + 2);
            final int boxX = list.listX() + list.listWidth() - BOX - 5;
            g.blitSprite(Gui.checkbox(enabled, hovered), boxX, rowY + (ROW - BOX) / 2, BOX, BOX);
            final int textX = list.listX() + 24;
            g.drawString(font, Gui.fit(font, name(entity), boxX - 6 - textX), textX, rowY + 6, enabled ? Gui.WHITE : Gui.LIST_SOFT, true);
            if (hovered)
            {
                final List<Component> lines = new ArrayList<>();
                lines.add(name(entity));
                lines.add(Component.literal(entity.toString()).withStyle(ChatFormatting.GRAY));
                if (hostiles())
                {
                    lines.add(Component.translatable(enabled ? "mcvanillatweaks.hut.entity.attacked" : "mcvanillatweaks.hut.entity.ignored")
                                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
                }
                else
                {
                    lines.add(Component.translatable(enabled ? "com.minecolonies.coremod.gui.workerhuts.retrieveon" : "com.minecolonies.coremod.gui.workerhuts.retrieveoff")
                                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
                }
                lines.add(Component.translatable("mcvanillatweaks.hut.list.click").withStyle(ChatFormatting.GRAY));
                tooltip.setAround(lines, mouseX, rowY, rowY + ROW);
            }
        }
        list.end(g);
    }

    @Override
    protected boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (list.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button != 0 || !list.overRows(mouseX, mouseY))
        {
            return false;
        }
        final int index = (int) Math.floor((mouseY - list.listY()) / ROW);
        if (index < 0 || index >= shown.size())
        {
            return false;
        }
        Gui.clickSound();
        toggle(shown.get(index));
        return true;
    }

    @Override
    protected boolean mouseDragged(final double mouseX, final double mouseY)
    {
        return list.mouseDragged(mouseY);
    }

    @Override
    protected void mouseReleased()
    {
        list.mouseReleased();
    }

    @Override
    protected boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        return list.mouseScrolled(mouseX, mouseY, delta);
    }
}
