package fr.mcvanillatweaks.client.citizen;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import fr.mcvanillatweaks.client.locate.LocateButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Everything the citizen pages have in common, drawn at the same place on every page (inventory included):
 * <pre>
 *  [tab][tab][tab][tab][tab][tab]                    [debug]
 * +----------------------------------------------------------+
 * | Name (gender)                                            |
 * | Job - workplace (level)                                  |
 * |  +--------- page content ---------+ +portrait+ [H]       |
 * |  |                                | |        | [C]      |
 * |  |                                | |        | [L] [main]|
 * |  |                                | +--------+ [F] [off] |
 * |  |                                |  hearts / food / joy |
 * |  |                                |    (icon) activity   |
 * +----------------------------------------------------------+
 * </pre>
 * The right-hand column matches the MineColonies citizen inventory menu, whose slot positions are fixed by the server:
 * the armour slots are real slots there and display-only slots on the other pages. The menu is drawn {@link #MENU_Y}
 * lower than the frame, which leaves room for the identity line above the inventory rows too.
 */
final class CitizenFrame
{
    static final int WIDTH = 265;
    /** Left edge of the page content (same as the first inventory slot background). */
    static final int CONTENT_X = 7;
    /** The inventory menu's origin, below the frame's top (its slot positions come from the server). */
    static final int MENU_Y = 12;
    /** Extra margin under the menu's hotbar, so the other pages get more room. */
    private static final int BOTTOM_EXTRA = 10;
    /** Top of the other pages' content, level with the portrait. */
    static final int PAGE_Y = 22 + MENU_Y;
    private static final int NAME_Y = 6;
    private static final int IDENTITY_Y = 17;
    /** Content width: nine slots. */
    static final int CONTENT_WIDTH = 162;

    private static final int TAB_WIDTH = 26;
    private static final int TAB_HEIGHT = 32;
    private static final int TAB_STEP = 27;
    /** Tabs rise this much above the frame. */
    static final int TAB_RISE = 28;

    private static final int PORTRAIT_X = 171;
    private static final int PORTRAIT_Y = 22 + MENU_Y;
    private static final int PORTRAIT_WIDTH = 50;
    private static final int PORTRAIT_HEIGHT = 72;
    /** Background of the armour slots of the citizen inventory menu (slot x = 223, y = 23 + 18 i). */
    static final int ARMOR_X = 222;
    static final int ARMOR_Y = 22 + MENU_Y;
    private static final int HANDS_X = 240;
    private static final int MAIN_HAND_Y = ARMOR_Y + 36;
    private static final int OFF_HAND_Y = ARMOR_Y + 54;
    private static final int VITALS_X = 171;
    private static final int HEARTS_Y = PORTRAIT_Y + 77;
    private static final int FOOD_Y = HEARTS_Y + 11;
    private static final int HAPPINESS_Y = FOOD_Y + 11;
    private static final int VITALS_WIDTH = 81;
    private static final int ACTIVITY_Y = HAPPINESS_Y + 15;
    private static final int COLUMN_WIDTH = WIDTH - 7 - VITALS_X;
    /** Text on the panel left of the portrait. */
    private static final int TEXT_WIDTH = PORTRAIT_X - 2 - 8;
    private static final int REFRESH_TICKS = 20;

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final ResourceLocation[] EMPTY_ARMOR = {
      InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
      InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS};
    private static final ResourceLocation EMPTY_MAIN_HAND = ResourceLocation.withDefaultNamespace("item/empty_slot_sword");

    private final Minecraft mc = Minecraft.getInstance();
    private final Font font = mc.font;
    private final CitizenPage page;
    private final List<CitizenPage> tabs = new ArrayList<>();
    private ICitizenDataView citizen;
    private int rows;
    private int left;
    private int top;
    private int requestCount;
    private int ticks;

    CitizenFrame(final ICitizenDataView citizen, final CitizenPage page, final int rows)
    {
        this.citizen = citizen;
        this.page = page;
        this.rows = rows;
        for (final CitizenPage tab : CitizenPage.values())
        {
            if (tab != CitizenPage.DEBUG || CitizenInfo.debugEnabled())
            {
                tabs.add(tab);
            }
        }
        countRequests();
    }

    static int height(final int rows)
    {
        return MENU_Y + 118 + 18 * Math.max(3, rows) + BOTTOM_EXTRA;
    }

    int height()
    {
        return height(rows);
    }

    /** Centres the frame (tabs included) in a screen of the given size. */
    void layout(final int screenWidth, final int screenHeight)
    {
        left = (screenWidth - WIDTH) / 2;
        top = Math.max(TAB_RISE, (screenHeight - height() - TAB_RISE) / 2 + TAB_RISE);
    }

    void setRows(final int rows)
    {
        this.rows = rows;
    }

    int left()
    {
        return left;
    }

    /** Top right corner of the header: the locate button. */
    int locateX()
    {
        return left + WIDTH - 7 - LocateButton.SIZE;
    }

    int locateY()
    {
        return top + 5;
    }

    int top()
    {
        return top;
    }

    ICitizenDataView citizen()
    {
        return citizen;
    }

    CitizenPage page()
    {
        return page;
    }

    /** Picks up MineColonies' latest data (called every tick). */
    void tick()
    {
        citizen = CitizenInfo.refresh(citizen);
        if (++ticks % REFRESH_TICKS == 0)
        {
            countRequests();
        }
    }

    private void countRequests()
    {
        requestCount = (int) CitizenInfo.openRequests(citizen).stream().filter(node -> node.depth() == 0).count();
    }

    /** Open requests of the citizen (sub-requests not counted), shown on the requests tab. */
    int requestCount()
    {
        return requestCount;
    }

    @Nullable
    LivingEntity entity()
    {
        return CitizenInfo.entity(citizen);
    }

    // ------------------------------------------------------------------ drawing

    /** Unselected tabs and the frame. */
    void renderBackground(final GuiGraphics g)
    {
        for (int i = 0; i < tabs.size(); i++)
        {
            if (tabs.get(i) != page)
            {
                renderTab(g, i, false);
            }
        }
        Gui.panel(g, left, top, WIDTH, height());
    }

    /**
     * Selected tab, name, identity line and identity column.
     *
     * @param inventory the inventory page: its armour slots are real slots and its rows start under the name.
     */
    void renderForeground(final GuiGraphics g, final int mouseX, final int mouseY, final boolean inventory)
    {
        for (int i = 0; i < tabs.size(); i++)
        {
            if (tabs.get(i) == page)
            {
                renderTab(g, i, true);
            }
        }

        final Component name = Component.literal(citizen.getName());
        g.drawString(font, name, left + 8, top + NAME_Y, Gui.LABEL, false);
        final int nameEnd = left + 8 + font.width(name) + 3;
        Gui.gender(g, nameEnd, top + NAME_Y - 1, citizen.isFemale() ? 'f' : 'm');
        g.drawString(font, Gui.fit(font, identity(), TEXT_WIDTH), left + 8, top + IDENTITY_Y, Gui.LABEL_SOFT, false);

        final LivingEntity entity = entity();
        Gui.inset(g, left + PORTRAIT_X, top + PORTRAIT_Y, PORTRAIT_WIDTH, PORTRAIT_HEIGHT, 0xFF000000);
        if (entity != null)
        {
            renderPortrait(g, entity, mouseX, mouseY);
        }
        else
        {
            final Component away = Component.translatable("mcvanillatweaks.citizen.away");
            g.drawString(font, away, left + PORTRAIT_X + (PORTRAIT_WIDTH - font.width(away)) / 2, top + PORTRAIT_Y + 32, Gui.LIST_SOFT, true);
        }

        if (!inventory)
        {
            for (int i = 0; i < ARMOR.length; i++)
            {
                final int y = top + ARMOR_Y + i * 18;
                Gui.slot(g, left + ARMOR_X, y);
                Gui.slotItem(g, font, left + ARMOR_X, y, armor(entity, i), EMPTY_ARMOR[i]);
            }
        }
        Gui.slot(g, left + HANDS_X, top + MAIN_HAND_Y);
        Gui.slotItem(g, font, left + HANDS_X, top + MAIN_HAND_Y, entity == null ? ItemStack.EMPTY : entity.getMainHandItem(), EMPTY_MAIN_HAND);
        Gui.slot(g, left + HANDS_X, top + OFF_HAND_Y);
        Gui.slotItem(g, font, left + HANDS_X, top + OFF_HAND_Y, entity == null ? ItemStack.EMPTY : entity.getOffhandItem(),
          InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);

        Vitals.hearts(g, citizen, left + VITALS_X, top + HEARTS_Y);
        Vitals.food(g, citizen, left + VITALS_X, top + FOOD_Y);
        Vitals.happiness(g, citizen.getHappiness(), left + VITALS_X, top + HAPPINESS_Y);

        // Current activity, centred under the vitals with an item picturing it.
        final VisibleCitizenStatus status = citizen.getVisibleStatus();
        final ItemStack icon = VanillaIcons.status(status);
        final int iconWidth = icon.isEmpty() ? 0 : 12;
        final Component activity = activity();
        final int textWidth = Math.min(font.width(activity), COLUMN_WIDTH - iconWidth);
        final int activityX = left + VITALS_X + (COLUMN_WIDTH - iconWidth - textWidth) / 2;
        VanillaIcons.item(g, icon, activityX, top + ACTIVITY_Y - 1, 10);
        g.drawString(font, Gui.fit(font, activity, COLUMN_WIDTH - iconWidth), activityX + iconWidth, top + ACTIVITY_Y, Gui.LABEL, false);
    }

    /** "Job - workplace (level n)", or only the job when the citizen has no workplace. */
    private Component identity()
    {
        final Component job = CitizenInfo.jobName(citizen);
        if (CitizenInfo.building(citizen, citizen.getWorkBuilding()) == null)
        {
            return job;
        }
        return Component.translatable("mcvanillatweaks.citizen.identity", job,
          CitizenInfo.buildingName(citizen, citizen.getWorkBuilding(), "mcvanillatweaks.citizen.no_work"));
    }

    private Component activity()
    {
        final VisibleCitizenStatus status = citizen.getVisibleStatus();
        return status == null ? Component.translatable("mcvanillatweaks.citizen.activity.idle") : Component.translatable(status.getTranslationKey());
    }

    private void renderTab(final GuiGraphics g, final int index, final boolean selected)
    {
        final CitizenPage tab = tabs.get(index);
        final int x = tabX(index);
        final int y = top - TAB_RISE;
        final int sprite = tab == CitizenPage.DEBUG ? 7 : Math.min(index + 1, 6);
        g.blitSprite(ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_" + (selected ? "selected_" : "unselected_") + sprite),
          x, y, TAB_WIDTH, TAB_HEIGHT);
        g.pose().pushPose();
        g.pose().translate(0, 0, 100);
        g.renderItem(tab.icon(), x + 5, y + 9);
        if (tab == CitizenPage.REQUESTS && requestCount > 0)
        {
            // Like a stack size: the number of open requests.
            g.renderItemDecorations(font, tab.icon(), x + 5, y + 9, requestCount > 99 ? "99+" : Integer.toString(requestCount));
        }
        g.pose().popPose();
    }

    private int tabX(final int index)
    {
        return tabs.get(index) == CitizenPage.DEBUG ? left + WIDTH - TAB_WIDTH : left + TAB_STEP * index;
    }

    private void renderPortrait(final GuiGraphics g, final LivingEntity entity, final int mouseX, final int mouseY)
    {
        // Hide the name tag MineColonies' renderer would draw above the head.
        final boolean hideGui = mc.options.hideGui;
        mc.options.hideGui = true;
        try
        {
            final int x = left + PORTRAIT_X + 1;
            final int y = top + PORTRAIT_Y + 1;
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x, y, x + PORTRAIT_WIDTH - 2, y + PORTRAIT_HEIGHT - 2, 30, 0.0625F,
              mouseX, mouseY, entity);
        }
        finally
        {
            mc.options.hideGui = hideGui;
        }
    }

    private ItemStack armor(@Nullable final LivingEntity entity, final int index)
    {
        final ItemStack shown = citizen.getDisplayArmor(ARMOR[index]);
        if (shown != null && !shown.isEmpty())
        {
            return shown;
        }
        return entity == null ? ItemStack.EMPTY : entity.getItemBySlot(ARMOR[index]);
    }

    // ------------------------------------------------------------------ interaction

    /** The tab under the mouse, if any. */
    @Nullable
    CitizenPage tabAt(final double mouseX, final double mouseY)
    {
        for (int i = 0; i < tabs.size(); i++)
        {
            if (Gui.inside(mouseX, mouseY, tabX(i), top - TAB_RISE, TAB_WIDTH, TAB_RISE))
            {
                return tabs.get(i);
            }
        }
        return null;
    }

    /** Whether the point is on the frame or on its tabs. */
    boolean contains(final double mouseX, final double mouseY)
    {
        return Gui.inside(mouseX, mouseY, left, top, WIDTH, height()) || tabAt(mouseX, mouseY) != null;
    }

    /** Tooltip of the common elements under the mouse. */
    void collectTooltip(final Tooltip tooltip, final int mouseX, final int mouseY, final boolean inventory)
    {
        final CitizenPage tab = tabAt(mouseX, mouseY);
        if (tab != null)
        {
            if (tab == CitizenPage.REQUESTS && requestCount > 0)
            {
                tooltip.set(List.of(tab.title(),
                  Component.translatable("mcvanillatweaks.citizen.requests.count", requestCount).withStyle(ChatFormatting.GRAY)));
            }
            else
            {
                tooltip.set(tab.title());
            }
            return;
        }
        if (Gui.inside(mouseX, mouseY, left + 8, top + NAME_Y - 1, TEXT_WIDTH, 10)
              || Gui.inside(mouseX, mouseY, left + 8, top + IDENTITY_Y - 1, TEXT_WIDTH, 10))
        {
            tooltip.set(List.of(Component.literal(citizen.getName()),
              CitizenInfo.jobName(citizen).copy().withStyle(ChatFormatting.GRAY),
              CitizenInfo.buildingName(citizen, citizen.getWorkBuilding(), "mcvanillatweaks.citizen.no_work").copy()
                .withStyle(ChatFormatting.GRAY)));
            return;
        }
        final LivingEntity entity = entity();
        if (!inventory)
        {
            for (int i = 0; i < ARMOR.length; i++)
            {
                if (Gui.inside(mouseX, mouseY, left + ARMOR_X, top + ARMOR_Y + i * 18, 18, 18))
                {
                    tooltip.set(armor(entity, i));
                    return;
                }
            }
        }
        if (entity != null && Gui.inside(mouseX, mouseY, left + HANDS_X, top + MAIN_HAND_Y, 18, 18))
        {
            tooltip.set(entity.getMainHandItem());
            return;
        }
        if (entity != null && Gui.inside(mouseX, mouseY, left + HANDS_X, top + OFF_HAND_Y, 18, 18))
        {
            tooltip.set(entity.getOffhandItem());
            return;
        }
        if (Gui.inside(mouseX, mouseY, left + VITALS_X, top + HEARTS_Y, VITALS_WIDTH, 9))
        {
            tooltip.set(Component.translatable("mcvanillatweaks.citizen.health_value", format(citizen.getHealth()), format(citizen.getMaxHealth())));
        }
        else if (Gui.inside(mouseX, mouseY, left + VITALS_X, top + FOOD_Y, VITALS_WIDTH, 9))
        {
            tooltip.set(Component.translatable("mcvanillatweaks.citizen.saturation_value", format(citizen.getSaturation()), format(CitizenInfo.FULL_SATURATION)));
        }
        else if (Gui.inside(mouseX, mouseY, left + VITALS_X, top + HAPPINESS_Y, VITALS_WIDTH, 9))
        {
            tooltip.set(Component.translatable("mcvanillatweaks.citizen.happiness_value", format(citizen.getHappiness())));
        }
        else if (Gui.inside(mouseX, mouseY, left + VITALS_X, top + ACTIVITY_Y - 1, COLUMN_WIDTH, 10))
        {
            tooltip.set(List.of(Component.translatable("mcvanillatweaks.citizen.row.activity"),
              activity().copy().withStyle(ChatFormatting.GRAY)));
        }
    }

    static String format(final double value)
    {
        final double rounded = Math.round(value * 10.0D) / 10.0D;
        return rounded == Math.rint(rounded) ? Integer.toString((int) rounded) : String.format(Locale.ROOT, "%.1f", rounded);
    }
}
