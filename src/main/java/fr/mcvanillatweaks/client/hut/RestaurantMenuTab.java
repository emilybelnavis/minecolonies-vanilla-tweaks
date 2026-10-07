package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.items.IMinecoloniesFoodItem;
import com.minecolonies.api.research.util.ResearchConstants;
import com.minecolonies.api.util.FoodUtils;
import com.minecolonies.core.client.gui.modules.building.RestaurantMenuModuleWindow;
import com.minecolonies.core.colony.buildings.moduleviews.RestaurantMenuModuleView;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import com.minecolonies.core.network.messages.server.colony.building.AlterRestaurantMenuItemMessage;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Restaurant menu page (MineColonies' menu module, also the nether miner's food for its trips): the dishes on the menu
 * on the left, with their quality strip (bronze, silver, gold for MineColonies' tiers 1-3) and a remove button, then
 * the ingredients they need, each with its name and MineColonies' daily estimate for the restaurant's customers (only a
 * tooltip in MineColonies: here the range is written on the row). On the right, the dishes
 * the hut's level allows, searchable; a click adds one (up to 5 per level) or removes it. Sorting, warnings and
 * messages are MineColonies'.
 * <pre>
 * Menu (3 / 15)              Food options
 * +-------------------+      [Search...           ]
 * |▌[bread] Bread   x |      [][][][][][][]
 * +-------------------+      [][][][][][][]
 * Ingredients per day (3 customers)
 * |[wheat] Wheat      6-9 |
 * </pre>
 */
final class RestaurantMenuTab extends HutTab
{
    private static final int ROW = 20;
    private static final int MENU_HEIGHT = 70;
    private static final int INGREDIENT_ROW = 18;
    private static final int SEARCH_HEIGHT = 18;
    private static final int REMOVE = 14;

    /** Set by the dev preview: MineColonies lists the edibles once a world is loaded, and recipes need a level. */
    @Nullable
    static Collection<ItemStorage> previewEdibles;
    @Nullable
    static Map<ItemStack, Double> previewIngredients;

    private final RestaurantMenuModuleView module;
    private final ScrollArea menuList = new ScrollArea();
    private final ItemGrid options = new ItemGrid();
    private final ScrollArea ingredientList = new ScrollArea();
    private List<ItemStack> ingredientItems = List.of();
    private final List<ItemStorage> edibles = new ArrayList<>();
    private List<ItemStorage> menu = List.of();
    /** Ingredient -> amount per dish served, summed over the menu. */
    private Map<ItemStack, Double> ingredients = Map.of();
    /** MineColonies' daily servings estimate for the restaurant's customers; 0 without customers. */
    private double consumption;
    private boolean customers;
    private String filter = "";
    private int filterTimer;

    RestaurantMenuTab(final HutScreen screen, final RestaurantMenuModuleView module)
    {
        super(screen);
        this.module = module;
        edibles.addAll(previewEdibles != null ? previewEdibles
                         : IColonyManager.getInstance().getCompatibilityManager().getEdibles(screen.building().getBuildingLevel() - 1));
        sort(edibles);
        options.setCounts(null, this::optionLines);
        refreshMenu();
        refreshOptions();
    }

    private int columnWidth()
    {
        return (width - GAP) / 2;
    }

    private int rightX()
    {
        return x + columnWidth() + GAP;
    }

    // ------------------------------------------------------------------ data, as RestaurantMenuModuleWindow

    /** MineColonies' order: best tier first, then most nutritious, then by name. */
    private static void sort(final List<ItemStorage> list)
    {
        list.sort(Comparator.<ItemStorage>comparingInt(s -> FoodUtils.getFoodTier(s.getItemStack()) * -100 - nutrition(s.getItemStack()))
                    .thenComparing(s -> s.getItemStack().getDisplayName().getString().toLowerCase(Locale.US)));
    }

    private static int nutrition(final ItemStack stack)
    {
        final FoodProperties food = stack.getFoodProperties(null);
        return food == null ? 0 : food.nutrition();
    }

    private boolean inMenu(final ItemStack stack)
    {
        return module.getMenu().contains(new ItemStorage(stack));
    }

    private void refreshOptions()
    {
        final String search = filter.toLowerCase(Locale.US);
        final List<ItemStack> shown = new ArrayList<>();
        for (final ItemStorage storage : edibles)
        {
            final ItemStack stack = storage.getItemStack();
            if (search.isEmpty() || stack.getDescriptionId().toLowerCase(Locale.US).contains(search)
                  || stack.getHoverName().getString().toLowerCase(Locale.US).contains(search))
            {
                shown.add(stack);
            }
        }
        options.setItems(shown);
    }

    private void refreshMenu()
    {
        final List<ItemStorage> sorted = new ArrayList<>(module.getMenu());
        sort(sorted);
        menu = sorted;
        computeIngredients();
    }

    /** The ingredients of every dish, through MineColonies' own recipe lookup, and the daily estimate. */
    private void computeIngredients()
    {
        final Map<ItemStack, Double> result = new LinkedHashMap<>();
        double saturation = 0;
        final double research = researchBonus();
        for (final ItemStorage dish : menu)
        {
            if (mc.level != null)
            {
                final ItemStorage input = new ItemStorage(dish.getItemStack());
                for (final ItemStorage ingredient : RestaurantMenuModuleWindow.getRecipeFromStack(input, true, 5))
                {
                    merge(result, ingredient.getItemStack(), (double) ingredient.getAmount() / input.getAmount());
                }
            }
            final FoodProperties food = dish.getItemStack().getFoodProperties(null);
            saturation = (int) (saturation + FoodUtils.getFoodValue(dish.getItemStack(), food, research));
        }
        if (mc.level == null && previewIngredients != null)
        {
            result.putAll(previewIngredients);
        }
        final List<Map.Entry<ItemStack, Double>> entries = new ArrayList<>(result.entrySet());
        entries.sort(Comparator.comparingDouble(e -> -e.getValue()));
        final Map<ItemStack, Double> ordered = new LinkedHashMap<>();
        for (final Map.Entry<ItemStack, Double> entry : entries)
        {
            ordered.put(entry.getKey(), entry.getValue());
        }
        ingredients = ordered;
        ingredientItems = new ArrayList<>(ordered.keySet());

        consumption = 0;
        customers = false;
        if (building() instanceof BuildingCook.View cook && cook.getCustomers() != null && !cook.getCustomers().isEmpty() && saturation > 0)
        {
            double sum = 0;
            for (final int id : cook.getCustomers())
            {
                final ICitizenDataView citizen = cook.getColony().getCitizen(id);
                if (citizen != null)
                {
                    final IBuildingView home = citizen.getHomeBuilding() == null ? null : cook.getColony().getClientBuildingManager().getBuilding(citizen.getHomeBuilding());
                    sum += FoodUtils.computeSaturationConsumptionFactor(home == null ? 0 : home.getBuildingLevelEquivalent());
                }
            }
            final int count = cook.getCustomers().size();
            consumption = sum / count * 10.0D * count / saturation;
            customers = true;
        }
    }

    private static void merge(final Map<ItemStack, Double> map, final ItemStack stack, final double amount)
    {
        for (final Map.Entry<ItemStack, Double> entry : map.entrySet())
        {
            if (ItemStack.isSameItemSameComponents(entry.getKey(), stack))
            {
                entry.setValue(entry.getValue() + amount);
                return;
            }
        }
        map.put(stack.copyWithCount(1), amount);
    }

    private double researchBonus()
    {
        try
        {
            return (int) building().getColony().getResearchManager().getResearchEffects().getEffectStrength(ResearchConstants.SATURATION);
        }
        catch (final RuntimeException e)
        {
            return 0;
        }
    }

    private boolean poorMenu()
    {
        for (final ItemStorage dish : menu)
        {
            if (dish.getItem() instanceof IMinecoloniesFoodItem food && food.getTier() >= 2)
            {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ actions

    private void add(final ItemStack stack)
    {
        if (module.hasReachedLimit() || inMenu(stack))
        {
            return;
        }
        AlterRestaurantMenuItemMessage.addMenuItem(building(), stack, module.getProducer().getRuntimeID()).sendToServer();
        module.getMenu().add(new ItemStorage(stack));
        refreshMenu();
    }

    private void remove(final ItemStorage dish)
    {
        module.getMenu().remove(dish);
        AlterRestaurantMenuItemMessage.removeMenuItem(building(), dish.getItemStack(), module.getProducer().getRuntimeID()).sendToServer();
        refreshMenu();
    }

    // ------------------------------------------------------------------ widgets

    @Override
    protected void init()
    {
        final EditBox search = add(new EditBox(font, rightX() + 1, y + HEADING + 1, columnWidth() - 2, SEARCH_HEIGHT - 2, Component.translatable("mcvanillatweaks.hut.search")));
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
    }

    @Override
    protected void tick()
    {
        if (filterTimer > 0 && --filterTimer == 0)
        {
            refreshOptions();
            options.scrollToTop();
        }
        if (module.getMenu().size() != menu.size())
        {
            refreshMenu();
        }
    }

    // ------------------------------------------------------------------ text

    /** Colour of MineColonies' tier strip, or 0 for vanilla food. */
    private static int tierColour(final ItemStack stack)
    {
        return switch (FoodUtils.getFoodTier(stack))
        {
            case 3 -> 0xFFFFD700;
            case 2 -> 0xFFD3D3D3;
            case 1 -> 0xFFCD7F32;
            default -> 0;
        };
    }

    /** Who eats it, and MineColonies' note on plain vanilla food. */
    private static List<Component> quality(final ItemStack stack)
    {
        final List<Component> lines = new ArrayList<>();
        final int tier = FoodUtils.getFoodTier(stack);
        if (tier > 0)
        {
            lines.add(Component.translatable("mcvanillatweaks.hut.menu.tier", tier).withStyle(tier == 3 ? ChatFormatting.GOLD : tier == 2 ? ChatFormatting.WHITE : ChatFormatting.RED));
        }
        lines.add(Component.translatable("com.minecolonies.core.gui.restaurant.foodquality", FoodUtils.getBuildingLevelForFood(stack)).withStyle(ChatFormatting.GRAY));
        if (!(stack.getItem() instanceof IMinecoloniesFoodItem) && tier <= 0)
        {
            lines.add(Component.translatable("com.minecolonies.core.gui.restaurant.vanillafoodquality").withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    private List<Component> optionLines(final ItemStack stack)
    {
        final List<Component> lines = quality(stack);
        if (inMenu(stack))
        {
            lines.add(Component.translatable("mcvanillatweaks.hut.menu.on_menu").withStyle(ChatFormatting.GREEN));
            lines.add(Component.translatable("mcvanillatweaks.hut.menu.click_remove").withStyle(ChatFormatting.YELLOW));
        }
        else if (module.hasReachedLimit())
        {
            lines.add(Component.translatable("com.minecolonies.coremod.gui.warehouse.limitreached").withStyle(ChatFormatting.RED));
        }
        else
        {
            lines.add(Component.translatable("mcvanillatweaks.hut.menu.click_add").withStyle(ChatFormatting.YELLOW));
        }
        return lines;
    }

    /**
     * MineColonies' daily range for this ingredient: low, and half as much again. MineColonies truncates the servings
     * estimate first, which gives 0 for a small restaurant; it is rounded here instead, at least 1 when used at all.
     */
    private int[] dailyRange(final ItemStack stack)
    {
        final double low = consumption * ingredients.getOrDefault(stack, 0.0D);
        return low <= 0 ? new int[] {0, 0} : new int[] {Math.max(1, (int) Math.round(low)), Math.max(1, (int) Math.ceil(low * 1.5D))};
    }

    private List<Component> ingredientLines(final ItemStack stack)
    {
        if (!customers)
        {
            return List.of(Component.translatable("mcvanillatweaks.hut.menu.no_customers").withStyle(ChatFormatting.GRAY));
        }
        final int[] range = dailyRange(stack);
        return List.of(Component.translatable("com.minecolonies.core.gui.restaurant.foodconsumption", range[0], range[1]).withStyle(ChatFormatting.GRAY),
          Component.translatable("mcvanillatweaks.hut.menu.customers", customerCount()).withStyle(ChatFormatting.GRAY));
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final int lw = columnWidth();
        heading(g, Component.translatable("mcvanillatweaks.hut.menu.count", menu.size(), building().getBuildingLevel() * 5), x, y, lw);
        renderMenu(g, mouseX, mouseY, tooltip, lw);

        final int ingredientsY = y + HEADING + MENU_HEIGHT + 6;
        heading(g, customers ? Component.translatable("mcvanillatweaks.hut.menu.ingredients_daily")
                     : Component.translatable("com.minecolonies.core.menu.ingredients"), x, ingredientsY, lw);
        renderIngredients(g, mouseX, mouseY, tooltip, lw, ingredientsY + HEADING);

        final int rx = rightX();
        heading(g, Component.translatable("com.minecolonies.core.food_list.title"), rx, y, lw);
        final int optionsTop = y + HEADING + SEARCH_HEIGHT + 4;
        options.set(rx, optionsTop, ItemGrid.columnsFor(lw), (y + height - optionsTop) / ItemGrid.SLOT);
        options.render(g, font, mouseX, mouseY, tooltip, this::inMenu);
        if (options.items().isEmpty())
        {
            g.drawString(font, Gui.fit(font, Component.translatable(edibles.isEmpty() ? "mcvanillatweaks.hut.list.empty" : "mcvanillatweaks.hut.search_none"), lw - 4),
              rx + 2, optionsTop + 5, Gui.LABEL_SOFT, false);
        }
    }

    private int customerCount()
    {
        return building() instanceof BuildingCook.View cook && cook.getCustomers() != null ? cook.getCustomers().size() : 0;
    }

    /** One row per ingredient: icon, name and, with customers, how many the restaurant uses per day. */
    private void renderIngredients(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip, final int lw, final int top)
    {
        ingredientList.set(x, top, lw, y + height - top, ingredientItems.size() * INGREDIENT_ROW, INGREDIENT_ROW);
        ingredientList.begin(g);
        if (ingredientItems.isEmpty())
        {
            final Component empty = Component.translatable(menu.isEmpty() ? "mcvanillatweaks.hut.menu.no_menu" : "mcvanillatweaks.hut.menu.no_ingredients");
            g.drawString(font, Gui.fit(font, empty, ingredientList.listWidth() - 8), ingredientList.listX() + 4, ingredientList.visibleTop() + 5, Gui.LIST_SOFT, true);
        }
        for (int i = 0; i < ingredientItems.size(); i++)
        {
            final int rowY = ingredientList.listY() + i * INGREDIENT_ROW;
            if (rowY + INGREDIENT_ROW < ingredientList.visibleTop() || rowY > ingredientList.visibleBottom())
            {
                continue;
            }
            final ItemStack stack = ingredientItems.get(i);
            final boolean hovered = ingredientList.hover(g, mouseX, mouseY, rowY, INGREDIENT_ROW);
            g.renderItem(stack, ingredientList.listX() + 3, rowY + 1);
            int right = ingredientList.listX() + ingredientList.listWidth() - 4;
            if (customers)
            {
                final int[] range = dailyRange(stack);
                final String amount = range[0] == range[1] ? Integer.toString(range[0]) : range[0] + "-" + range[1];
                right -= font.width(amount);
                g.drawString(font, amount, right, rowY + 5, Gui.WHITE, true);
                right -= 6;
            }
            final int tx = ingredientList.listX() + 23;
            g.drawString(font, Gui.fit(font, stack.getHoverName(), right - tx), tx, rowY + 5, Gui.LIST_SOFT, true);
            if (hovered)
            {
                tooltip.set(stack, ingredientLines(stack));
            }
        }
        ingredientList.end(g);
    }

    private void renderMenu(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip, final int lw)
    {
        final int top = y + HEADING;
        final boolean poor = !menu.isEmpty() && poorMenu();
        final var warning = font.split(Component.translatable(menu.isEmpty() ? "com.minecolonies.core.menu.warning" : "com.minecolonies.core.poor.menu.warning"),
          lw - 22);
        final int warningHeight = menu.isEmpty() || poor ? warning.size() * 10 + 8 : 0;
        menuList.set(x, top, lw, MENU_HEIGHT, menu.size() * ROW + warningHeight, ROW);
        menuList.begin(g);
        for (int i = 0; i < menu.size(); i++)
        {
            final int rowY = menuList.listY() + i * ROW;
            if (rowY + ROW < menuList.visibleTop() || rowY > menuList.visibleBottom())
            {
                continue;
            }
            final ItemStack stack = menu.get(i).getItemStack();
            final boolean hovered = menuList.hover(g, mouseX, mouseY, rowY, ROW);
            final int colour = tierColour(stack);
            if (colour != 0)
            {
                g.fill(menuList.listX(), rowY + 1, menuList.listX() + 2, rowY + ROW - 1, colour);
            }
            g.renderItem(stack, menuList.listX() + 4, rowY + 2);
            final int bx = menuList.listX() + menuList.listWidth() - REMOVE - 3;
            final int by = rowY + (ROW - REMOVE) / 2;
            final boolean overRemove = menuList.overRows(mouseX, mouseY) && Gui.inside(mouseX, mouseY, bx, by, REMOVE, REMOVE);
            Gui.textButton(g, font, bx, by, REMOVE, REMOVE, "x", true, overRemove);
            final int tx = menuList.listX() + 24;
            g.drawString(font, Gui.fit(font, stack.getHoverName(), bx - 4 - tx), tx, rowY + 6, Gui.WHITE, true);
            if (overRemove)
            {
                tooltip.set(Component.translatable("mcvanillatweaks.hut.menu.remove"));
            }
            else if (hovered)
            {
                tooltip.set(stack, quality(stack));
            }
        }
        if (warningHeight > 0)
        {
            int ty = menuList.listY() + menu.size() * ROW + 5;
            g.renderItem(new ItemStack(Items.BOWL), menuList.listX() + 3, ty - 2);
            for (final var line : warning)
            {
                g.drawString(font, line, menuList.listX() + 22, ty, menu.isEmpty() ? Gui.LIST_SOFT : Gui.GOLD, true);
                ty += 10;
            }
        }
        menuList.end(g);
    }

    @Override
    protected boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (menuList.mouseClicked(mouseX, mouseY) || options.mouseClicked(mouseX, mouseY) || ingredientList.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button != 0)
        {
            return false;
        }
        final ItemStack option = options.itemAt(mouseX, mouseY);
        if (option != null)
        {
            if (inMenu(option))
            {
                Gui.clickSound();
                remove(new ItemStorage(option));
            }
            else if (!module.hasReachedLimit())
            {
                Gui.clickSound();
                add(option);
            }
            return true;
        }
        if (menuList.overRows(mouseX, mouseY))
        {
            final int index = (int) Math.floor((mouseY - menuList.listY()) / ROW);
            if (index >= 0 && index < menu.size())
            {
                final int rowY = menuList.listY() + index * ROW;
                final int bx = menuList.listX() + menuList.listWidth() - REMOVE - 3;
                if (Gui.inside(mouseX, mouseY, bx, rowY + (ROW - REMOVE) / 2, REMOVE, REMOVE))
                {
                    Gui.clickSound();
                    remove(menu.get(index));
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected boolean mouseDragged(final double mouseX, final double mouseY)
    {
        return menuList.mouseDragged(mouseY) || options.mouseDragged(mouseY) || ingredientList.mouseDragged(mouseY);
    }

    @Override
    protected void mouseReleased()
    {
        menuList.mouseReleased();
        options.mouseReleased();
        ingredientList.mouseReleased();
    }

    @Override
    protected boolean mouseScrolled(final double mouseX, final double mouseY, final double delta)
    {
        return menuList.mouseScrolled(mouseX, mouseY, delta) || options.mouseScrolled(mouseX, mouseY, delta) || ingredientList.mouseScrolled(mouseX, mouseY, delta);
    }
}
