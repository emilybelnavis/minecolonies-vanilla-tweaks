package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.buildings.modules.IAssignmentModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.client.gui.WindowBuildBuilding;
import com.minecolonies.core.client.gui.citizen.MainWindowCitizen;
import com.minecolonies.core.colony.buildings.moduleviews.BuildingResourcesModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.CourierAssignmentModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.FieldsModuleView;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBarracks;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingPlantation;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.core.colony.buildings.moduleviews.MinerGuardAssignModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.MinerLevelManagementModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WarehouseOptionsModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import com.minecolonies.core.colony.buildings.views.LivingBuildingView;
import com.minecolonies.core.colony.jobs.views.DmanJobView;
import com.minecolonies.core.network.messages.server.colony.OpenInventoryMessage;
import com.minecolonies.core.network.messages.server.colony.building.BuildRequestMessage;
import com.minecolonies.core.network.messages.server.colony.building.ChangeDeliveryPriorityMessage;
import com.minecolonies.core.network.messages.server.colony.building.ForcePickupMessage;
import com.minecolonies.core.network.messages.server.colony.building.RecallCitizenHutMessage;
import com.minecolonies.core.network.messages.server.colony.building.worker.RecallCitizenMessage;
import fr.mcvanillatweaks.client.citizen.CitizenInfo;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.IconButton;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import fr.mcvanillatweaks.client.citizen.VanillaIcons;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Home page (MineColonies' hut main page, with or without workers): the workers on the left with the hiring and recall
 * buttons, the building's actions on the right (build or upgrade, inventories, name, information, and for worker huts
 * the delivery priority and pickup). Each action sends the same message or opens the same MineColonies dialog as its
 * button in MineColonies' window. A hut without workers shows a summary card instead of the list; the builder's
 * worker list is shortened to make room for its current construction (click: resources page). The warehouse lists its
 * couriers (assigned through MineColonies' assignment module, with their pending deliveries) and gets a storage card
 * under its actions (click: storage page). The miner's list is shortened like the builder's for its current level and
 * guard huts (click: levels page), the farmer's and the plantation's for their fields (click: fields page). Houses list their residents (hire: residents page, recall: MineColonies' house
 * recall) and show MineColonies' warning for their next level.
 */
final class HomeTab extends HutTab
{
    private static final int BUTTON_HEIGHT = 20;
    private static final int STEP = 24;
    private static final int ROW = 22;

    private static final ItemStack PLAYER_HEAD = new ItemStack(Items.PLAYER_HEAD);

    private record Worker(String job, ICitizenDataView citizen)
    {
    }

    private final ScrollArea list = new ScrollArea();
    /** Worker huts show the worker list, priority and pickup, like MineColonies' worker placeholder. */
    private final boolean workerHut;
    /** Huts with workers or assigned citizens (the warehouse's couriers) list them, with the hire and recall buttons. */
    private final boolean staffed;
    private final boolean couriers;
    /** Houses and the tavern list their residents instead of workers. */
    @Nullable
    private final LivingBuildingView living;
    private IconButton build;
    private Button prioDown;
    private Button prioUp;
    /** MineColonies keeps its own copy of the priority, changed before the server answers. */
    private int prio;
    private List<Worker> workers = List.of();
    /** The builder's construction site, null for other huts. */
    @Nullable
    private final BuildingResourcesModuleView resources;
    @Nullable
    private BuilderSite site;
    /** The miner's levels, null for other huts. */
    @Nullable
    private final MinerLevelManagementModuleView mine;
    /** The farmer's or the plantation's fields, null for other huts. */
    @Nullable
    private final FieldsModuleView fields;
    /** The warehouse's options module, null for other huts. */
    @Nullable
    private final WarehouseOptionsModuleView warehouse;
    @Nullable
    private WarehouseTab.Storage storage;
    /** The barracks' towers, raids and spies, in place of the summary card; null for other huts. */
    @Nullable
    private final BarracksCard barracks;
    private int ticks;

    HomeTab(final HutScreen screen)
    {
        super(screen);
        prio = screen.building().getBuildingDmPrio();
        workerHut = !screen.building().getModuleViews(WorkerBuildingModuleView.class).isEmpty();
        living = screen.building() instanceof LivingBuildingView home ? home : null;
        staffed = !screen.building().getModuleViews(IAssignmentModuleView.class).isEmpty() || living != null;
        couriers = !screen.building().getModuleViews(CourierAssignmentModuleView.class).isEmpty();
        workers = workers();
        resources = workerHut ? BuilderSite.module(screen.building()) : null;
        site = resources == null ? null : BuilderSite.of(screen.building(), resources);
        mine = workerHut ? MinerLevelsTab.module(screen.building()) : null;
        fields = workerHut && site == null && mine == null ? screen.building().getModuleViewByType(FieldsModuleView.class) : null;
        warehouse = screen.building().getModuleViewByType(WarehouseOptionsModuleView.class);
        storage = warehouse == null ? null : WarehouseTab.scan(screen.building());
        barracks = screen.building() instanceof BuildingBarracks.View ? new BarracksCard(screen.building()) : null;
    }

    private int columnWidth()
    {
        return (width - GAP) / 2;
    }

    private int rightX()
    {
        return x + columnWidth() + GAP;
    }

    private int listBottom()
    {
        return y + height - 2 * STEP;
    }

    @Override
    protected void init()
    {
        final int w = columnWidth();
        if (staffed)
        {
            add(new IconButton(x, listBottom() + 4, w, BUTTON_HEIGHT, new ItemStack(living != null ? Items.RED_BED : Items.WRITABLE_BOOK),
              Component.translatable(living != null ? "com.minecolonies.coremod.gui.home.manage" : "com.minecolonies.coremod.gui.workerhuts.manage"), b -> hire()));
            add(new IconButton(x, listBottom() + 4 + STEP, w, BUTTON_HEIGHT, new ItemStack(Items.BELL),
              Component.translatable(living != null ? "mcvanillatweaks.hut.residents.recall" : "com.minecolonies.coremod.gui.workerhuts.recall"), b -> {
                  if (living != null)
                  {
                      new RecallCitizenHutMessage(living).sendToServer();
                  }
                  else
                  {
                      new RecallCitizenMessage(building()).sendToServer();
                  }
              }));
        }

        if (barracks != null && !staffed && barracks.buttonSpace() > 0)
        {
            add(barracks.createButton(x, y + height - BUTTON_HEIGHT, w, BUTTON_HEIGHT));
        }

        final int rx = rightX();
        final int half = (w - 4) / 2;
        int ry = y + HEADING;
        build = add(new IconButton(rx, ry, w, BUTTON_HEIGHT, HutScreen.hutIcon(building()), buildLabel(), b -> buildClicked()));
        ry += STEP;
        add(new IconButton(rx, ry, w, BUTTON_HEIGHT, new ItemStack(Items.CHEST), Component.translatable("mcvanillatweaks.hut.inventory"),
          b -> new OpenInventoryMessage(building()).sendToServer()));
        ry += STEP;
        // The warehouse's storage page shows the same contents as MineColonies' all-inventory window, with more.
        add(new IconButton(rx, ry, w, BUTTON_HEIGHT, new ItemStack(Items.BARREL), Component.translatable("mcvanillatweaks.hut.all_inventory"),
          b -> {
              if (warehouse != null)
              {
                  screen.open(HutPage.module(warehouse));
              }
              else
              {
                  screen.open(HutPage.STORAGE);
              }
          }));
        ry += STEP;
        final boolean info = hasInfo();
        add(new IconButton(rx, ry, info ? half : w, BUTTON_HEIGHT, new ItemStack(Items.NAME_TAG), Component.translatable("mcvanillatweaks.hut.rename"),
          b -> mc.setScreen(new RenameScreen(building()))));
        if (info)
        {
            add(new IconButton(rx + w - half, ry, half, BUTTON_HEIGHT, new ItemStack(Items.KNOWLEDGE_BOOK), Component.translatable("mcvanillatweaks.hut.info"),
              b -> mc.setScreen(new InfoScreen(building()))));
        }
        ry += STEP;
        if (workerHut)
        {
            // Delivery priority: label on the panel, then - and +.
            prioDown = add(Button.builder(Component.literal("-"), b -> changePriority(false))
                             .bounds(rx + w - 2 * BUTTON_HEIGHT - 2, ry, BUTTON_HEIGHT, BUTTON_HEIGHT).build());
            prioUp = add(Button.builder(Component.literal("+"), b -> changePriority(true)).bounds(rx + w - BUTTON_HEIGHT, ry, BUTTON_HEIGHT, BUTTON_HEIGHT).build());
            ry += STEP;
            add(new IconButton(rx, ry, w, BUTTON_HEIGHT, new ItemStack(Items.HOPPER), Component.translatable("com.minecolonies.coremod.gui.workerhuts.forcepickup"),
              b -> new ForcePickupMessage(building()).sendToServer()));
        }
        update();
    }

    @Override
    protected void tick()
    {
        workers = workers();
        if (++ticks % 20 == 0)
        {
            if (resources != null)
            {
                site = BuilderSite.of(building(), resources);
            }
            if (warehouse != null)
            {
                storage = WarehouseTab.scan(building());
            }
        }
        update();
    }

    private void update()
    {
        build.setMessage(buildLabel());
        if (prioDown != null)
        {
            prioDown.active = prio > 0;
            prioUp.active = prio < 10;
        }
        if (barracks != null)
        {
            barracks.update();
        }
    }

    /** MineColonies shows its info button only when the building has info pages. */
    private boolean hasInfo()
    {
        return I18n.exists("com.minecolonies.coremod.info." + building().getBuildingType().getTranslationKey().replace("com.minecolonies.building.", "") + ".0");
    }

    private void hire()
    {
        // Houses check their level, like MineColonies' house window.
        if (living != null ? building().getBuildingLevel() == 0 : !building().allowsAssignment())
        {
            if (mc.player != null)
            {
                mc.player.displayClientMessage(Component.translatable("com.minecolonies.coremod.gui.workerhuts.level0"), false);
            }
            return;
        }
        screen.open(HutPage.WORKERS);
    }

    private void changePriority(final boolean up)
    {
        prio = Math.max(0, Math.min(10, prio + (up ? 1 : -1)));
        new ChangeDeliveryPriorityMessage(building(), up).sendToServer();
        update();
    }

    // ------------------------------------------------------------------ build button, as MineColonies' main window

    private Component buildLabel()
    {
        return Component.translatable(buildKey());
    }

    private String buildKey()
    {
        final IBuildingView view = building();
        if (view.isBuilding())
        {
            return view.getBuildingLevel() == 0 ? "com.minecolonies.coremod.gui.workerhuts.cancelbuild" : "com.minecolonies.coremod.gui.workerhuts.cancelupgrade";
        }
        if (view.isRepairing())
        {
            return "com.minecolonies.coremod.gui.workerhuts.cancelrepair";
        }
        if (view.isDeconstructing())
        {
            return "com.minecolonies.coremod.gui.workerhuts.canceldeconstruction";
        }
        return "com.minecolonies.coremod.gui.workerhuts.buildrepair";
    }

    private void buildClicked()
    {
        switch (buildKey())
        {
            case "com.minecolonies.coremod.gui.workerhuts.cancelbuild", "com.minecolonies.coremod.gui.workerhuts.cancelupgrade" ->
              new BuildRequestMessage(building(), BuildRequestMessage.Mode.BUILD, BlockPos.ZERO).sendToServer();
            case "com.minecolonies.coremod.gui.workerhuts.cancelrepair" ->
              new BuildRequestMessage(building(), BuildRequestMessage.Mode.REPAIR, BlockPos.ZERO).sendToServer();
            case "com.minecolonies.coremod.gui.workerhuts.canceldeconstruction" ->
              new BuildRequestMessage(building(), BuildRequestMessage.Mode.REMOVE, BlockPos.ZERO).sendToServer();
            default -> new WindowBuildBuilding(building().getColony(), building()).open();
        }
    }

    // ------------------------------------------------------------------ drawing

    /** The assigned workers, with their job, as MineColonies lists them (its assignment window lists every assignment module). */
    private List<Worker> workers()
    {
        final List<Worker> list = new ArrayList<>();
        if (living != null)
        {
            for (final int id : living.getResidents())
            {
                final ICitizenDataView citizen = building().getColony().getCitizen(id);
                if (citizen != null)
                {
                    final String job = citizen.getJob() == null || citizen.getJob().isEmpty()
                                         ? I18n.get("com.minecolonies.coremod.gui.townhall.citizens.unemployed")
                                         : I18n.get(citizen.getJob());
                    list.add(new Worker(job, citizen));
                }
            }
            return list;
        }
        for (final IAssignmentModuleView module : building().getModuleViews(IAssignmentModuleView.class))
        {
            final String job = Component.translatable(module.getJobEntry().getTranslationKey()).getString();
            for (final int id : module.getAssignedCitizens())
            {
                final ICitizenDataView citizen = building().getColony().getCitizen(id);
                if (citizen != null)
                {
                    list.add(new Worker(job, citizen));
                }
            }
        }
        return list;
    }

    private int maxWorkers()
    {
        if (living != null)
        {
            return living.getMax();
        }
        int max = 0;
        for (final IAssignmentModuleView module : building().getModuleViews(IAssignmentModuleView.class))
        {
            max += module.getMaxInhabitants();
        }
        return max;
    }

    @Override
    protected void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final int w = columnWidth();
        if (staffed)
        {
            renderWorkers(g, mouseX, mouseY, tooltip, w);
        }
        else if (barracks != null)
        {
            heading(g, Component.translatable("mcvanillatweaks.hut.barracks.defense"), x, y, w);
            final int top = y + HEADING;
            barracks.render(g, x, top, w, y + height - top - barracks.buttonSpace(), mouseX, mouseY, tooltip);
            barracks.spiesTooltip(tooltip, mouseX, mouseY);
        }
        else
        {
            renderCard(g, w);
        }

        final int rx = rightX();
        heading(g, Component.translatable("mcvanillatweaks.hut.building"), rx, y, w);
        if (prioDown != null)
        {
            final int prioY = prioDown.getY();
            final Component prioLabel = prio == 0
                                          ? Component.translatable("mcvanillatweaks.hut.priority_never")
                                          : Component.translatable("mcvanillatweaks.hut.priority", prio);
            g.drawString(font, Gui.fit(font, prioLabel, w - 2 * BUTTON_HEIGHT - 8), rx + 1, prioY + 6, Gui.LABEL, false);
            if (Gui.inside(mouseX, mouseY, rx, prioY, w - 2 * BUTTON_HEIGHT - 4, BUTTON_HEIGHT))
            {
                tooltip.set(List.of(prioLabel, Component.translatable("mcvanillatweaks.hut.priority_hint").withStyle(ChatFormatting.GRAY)));
            }
        }
        if (storage != null)
        {
            renderStorage(g, storage, mouseX, mouseY, tooltip, w);
        }
        if (living != null)
        {
            renderHousingWarning(g, mouseX, mouseY, tooltip, w);
        }
    }

    /**
     * MineColonies' warning for the next level of a house (food production, restaurant menu), shown on its build button
     * there; cut with "..." when too long, whole in the tooltip.
     */
    private void renderHousingWarning(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip, final int w)
    {
        final String key;
        try
        {
            key = living.getHoverWarningForLevel();
        }
        catch (final RuntimeException e)
        {
            return;
        }
        if (key == null || key.isEmpty())
        {
            return;
        }
        final int rx = rightX();
        final int cy = y + HEADING + 4 * STEP + 4;
        final int h = y + height - cy;
        Gui.inset(g, rx, cy, w, h);
        final Component text = Component.translatable(key);
        final List<FormattedText> lines = font.getSplitter().splitLines(text, w - 10, Style.EMPTY);
        final int max = Math.max(1, (h - 8) / 10);
        int ty = cy + 5;
        for (int i = 0; i < Math.min(max, lines.size()); i++)
        {
            final boolean last = i == max - 1 && lines.size() > max;
            if (last)
            {
                // The rest of the text on one line, cut with "...".
                final StringBuilder rest = new StringBuilder();
                for (int j = i; j < lines.size(); j++)
                {
                    rest.append(lines.get(j).getString()).append(' ');
                }
                g.drawString(font, Gui.fit(font, Component.literal(rest.toString().trim()), w - 10), rx + 5, ty, Gui.GOLD, true);
            }
            else
            {
                g.drawString(font, Language.getInstance().getVisualOrder(lines.get(i)), rx + 5, ty, Gui.GOLD, true);
            }
            ty += 10;
        }
        if (Gui.inside(mouseX, mouseY, rx, cy, w, h))
        {
            final List<Component> wrapped = new ArrayList<>();
            for (final FormattedText line : font.getSplitter().splitLines(text, 220, Style.EMPTY))
            {
                wrapped.add(Component.literal(line.getString()).withStyle(ChatFormatting.GOLD));
            }
            tooltip.set(wrapped);
        }
    }

    /** A courier's waiting deliveries, from the courier job's request data store; -1 when unknown. */
    private static int pendingDeliveries(final ICitizenDataView citizen)
    {
        try
        {
            return citizen.getJobView() instanceof DmanJobView courier && courier.getDataStore() != null ? courier.getDataStore().getQueue().size() : -1;
        }
        catch (final RuntimeException e)
        {
            return -1;
        }
    }

    private void renderWorkers(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip, final int w)
    {
        final String title = living != null ? "mcvanillatweaks.hut.residents" : couriers ? "mcvanillatweaks.hut.couriers" : "mcvanillatweaks.hut.workers";
        heading(g, Component.translatable(title, workers.size(), maxWorkers()), x, y, w);
        final int listTop = y + HEADING;
        final int listEnd = site != null || mine != null || fields != null ? listTop + ROW + 2 : listBottom();
        list.set(x, listTop, w, listEnd - listTop, workers.size() * ROW, ROW);
        list.begin(g);
        if (workers.isEmpty())
        {
            g.drawString(font, Gui.fit(font, Component.translatable(living != null ? "mcvanillatweaks.hut.residents.none" : "mcvanillatweaks.hut.no_workers"), list.listWidth() - 8),
              list.listX() + 4, list.visibleTop() + 6, Gui.LIST_SOFT, true);
        }
        for (int i = 0; i < workers.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final Worker worker = workers.get(i);
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            // The citizen's visible status, or the citizen icon when it shows none (a courier at work).
            final ItemStack statusIcon = VanillaIcons.status(worker.citizen().getVisibleStatus());
            VanillaIcons.item(g, statusIcon.isEmpty() ? PLAYER_HEAD : statusIcon, list.listX() + 3, rowY + 3, 16);
            final int tx = list.listX() + 23;
            final int pending = couriers ? pendingDeliveries(worker.citizen()) : -1;
            int tw = list.listWidth() - 27;
            if (pending >= 0)
            {
                // Couriers: an empty minecart when idle, a chest minecart with the waiting deliveries like a stack size.
                final int ix = list.listX() + list.listWidth() - 19;
                final ItemStack cart = new ItemStack(pending == 0 ? Items.MINECART : Items.CHEST_MINECART);
                g.renderItem(cart, ix, rowY + 3);
                if (pending > 0)
                {
                    g.renderItemDecorations(font, cart, ix, rowY + 3, Integer.toString(pending));
                }
                tw -= 22;
            }
            g.drawString(font, Gui.fit(font, Component.literal(worker.citizen().getName()), tw), tx, rowY + 2, Gui.WHITE, true);
            g.drawString(font, Gui.fit(font, Component.literal(worker.job()), tw), tx, rowY + 12, Gui.LIST_SOFT, true);
            if (hovered)
            {
                final List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(worker.citizen().getName()));
                lines.add(Component.literal(worker.job()).withStyle(ChatFormatting.GRAY));
                if (pending >= 0)
                {
                    lines.add(Component.translatable("mcvanillatweaks.hut.deliveries_pending", pending).withStyle(ChatFormatting.GRAY));
                }
                final var status = worker.citizen().getVisibleStatus();
                if (status != null)
                {
                    lines.add(Component.translatable(status.getTranslationKey()).withStyle(ChatFormatting.GRAY));
                }
                lines.add(Component.translatable("mcvanillatweaks.citizen.happiness_value", CitizenInfo.format(worker.citizen().getHappiness()))
                            .withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("mcvanillatweaks.hut.open_citizen").withStyle(ChatFormatting.YELLOW));
                tooltip.setAround(lines, mouseX, rowY, rowY + ROW);
            }
        }
        list.end(g);
        if (site != null)
        {
            renderSite(g, site, listEnd + 8, mouseX, mouseY, tooltip, w);
        }
        else if (mine != null)
        {
            renderMine(g, mine, listEnd + 8, mouseX, mouseY, tooltip, w);
        }
        else if (fields != null)
        {
            renderFields(g, fields, listEnd + 8, mouseX, mouseY, tooltip, w);
        }
    }

    private int siteTop()
    {
        return y + HEADING + ROW + 2 + 8 + HEADING;
    }

    /** The builder's construction: name, step, supplied and used shares; a click opens the resources page. */
    private void renderSite(final GuiGraphics g, final BuilderSite site, final int top, final int mouseX, final int mouseY, final Tooltip tooltip, final int w)
    {
        heading(g, Component.translatable("mcvanillatweaks.hut.res.site"), x, top, w);
        final int cy = siteTop();
        final int h = listBottom() - cy;
        Gui.inset(g, x, cy, w, h);
        final boolean hovered = Gui.inside(mouseX, mouseY, x, cy, w, h);
        if (hovered)
        {
            g.fill(x + 1, cy + 1, x + w - 1, cy + h - 1, Gui.ROW_HOVER);
        }
        final Component name = site.name();
        if (name == null)
        {
            int ty = cy + 6;
            for (final var line : font.split(Component.translatable("mcvanillatweaks.hut.res.no_site"), w - 10))
            {
                g.drawString(font, line, x + 5, ty, Gui.LIST_SOFT, true);
                ty += 10;
            }
        }
        else
        {
            g.drawString(font, Gui.fit(font, name, w - 10), x + 5, cy + 5, Gui.WHITE, true);
            g.drawString(font, Component.translatable("com.minecolonies.coremod.gui.progress.step", resources.getCurrentStage(), resources.getTotalStages()),
              x + 5, cy + 17, Gui.LIST_SOFT, true);
            final int half = (w - 14) / 2;
            final Component supplied = Component.translatable("mcvanillatweaks.hut.res.supplied", Math.max(0, site.supplied));
            final Component used = Component.translatable("mcvanillatweaks.hut.res.used", Math.max(0, site.used));
            g.drawString(font, Gui.fit(font, supplied, half), x + 5, cy + 33, Gui.WHITE, true);
            g.drawString(font, Gui.fit(font, used, half), x + 9 + half, cy + 33, Gui.WHITE, true);
            Gui.bar(g, x + 5, cy + 44, Math.min(half, Gui.BAR_WIDTH), site.supplied < 0 ? 0 : site.supplied / 100.0F);
            Gui.bar(g, x + 9 + half, cy + 44, Math.min(half, Gui.BAR_WIDTH), site.used < 0 ? 0 : site.used / 100.0F);
            final long missing = site.resources.stream().filter(r -> r.getAvailable() < r.getAmount()).count();
            g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.hut.res.missing", missing), w - 10), x + 5, cy + 55,
              missing > 0 ? Gui.GOLD : Gui.GREEN, true);
        }
        if (hovered)
        {
            final List<Component> lines = new ArrayList<>();
            lines.add(name != null ? name : Component.translatable("hud.mcvanillatweaks.no_work_order"));
            lines.add(Component.translatable("mcvanillatweaks.hut.res.open").withStyle(ChatFormatting.YELLOW));
            tooltip.set(lines);
        }
    }

    /** The miner's current level, its levels and guard huts; a click opens the levels page. */
    private void renderMine(final GuiGraphics g, final MinerLevelManagementModuleView mine, final int top, final int mouseX, final int mouseY,
      final Tooltip tooltip, final int w)
    {
        heading(g, Component.translatable("mcvanillatweaks.hut.miner.card"), x, top, w);
        final int cy = siteTop();
        final int h = listBottom() - cy;
        Gui.inset(g, x, cy, w, h);
        final boolean hovered = Gui.inside(mouseX, mouseY, x, cy, w, h);
        if (hovered)
        {
            g.fill(x + 1, cy + 1, x + w - 1, cy + h - 1, Gui.ROW_HOVER);
        }
        final var levels = mine.levelsInfo == null ? List.<net.minecraft.util.Tuple<Integer, Integer>>of() : mine.levelsInfo;
        g.renderItem(new ItemStack(Items.LADDER), x + 4, cy + 5);
        final int tx = x + 24;
        final int tw = w - 28;
        if (levels.isEmpty())
        {
            int ty = cy + 6;
            for (final var line : font.split(Component.translatable("com.minecolonies.coremod.gui.workerhuts.minernolevels"), tw))
            {
                g.drawString(font, line, tx, ty, Gui.LIST_SOFT, true);
                ty += 10;
            }
        }
        else
        {
            final boolean valid = mine.current >= 0 && mine.current < levels.size();
            g.drawString(font, Gui.fit(font, valid ? Component.translatable("mcvanillatweaks.hut.miner.current", MinerLevelsTab.number(mine.current))
                                                   : Component.translatable("mcvanillatweaks.hut.miner.no_current"), tw), tx, cy + 5, Gui.WHITE, true);
            if (valid)
            {
                g.drawString(font, Gui.fit(font, MinerLevelsTab.detail(levels.get(mine.current)), tw), tx, cy + 15, Gui.LIST_SOFT, true);
            }
            g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.hut.miner.level_count", levels.size()), tw), tx, cy + 25, Gui.LIST_SOFT, true);
        }
        final boolean guards = building().getModuleViewByType(MinerGuardAssignModuleView.class) != null;
        if (guards)
        {
            final int gy = cy + 40;
            final int assigned = MineGuardsTab.assignedHuts(building());
            g.renderItem(new ItemStack(Items.SHIELD), x + 4, gy);
            g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.hut.mine_guards.short", assigned,
              MineGuardsTab.maxGuards(building().getBuildingLevel())), tw), tx, gy + 5, assigned > 0 ? Gui.WHITE : Gui.LIST_SOFT, true);
        }
        if (hovered)
        {
            tooltip.set(List.of(Component.translatable("com.minecolonies.coremod.gui.miner.levels"),
              Component.translatable("mcvanillatweaks.hut.miner.open").withStyle(ChatFormatting.YELLOW)));
        }
    }

    /** The fields this hut works: count, their crops as slots (plants for the plantation); a click opens the fields page. */
    private void renderFields(final GuiGraphics g, final FieldsModuleView fields, final int top, final int mouseX, final int mouseY, final Tooltip tooltip,
      final int w)
    {
        heading(g, Component.translatable("com.minecolonies.coremod.gui.workerhuts.fields"), x, top, w);
        final int cy = siteTop();
        final int h = listBottom() - cy;
        Gui.inset(g, x, cy, w, h);
        final boolean hovered = Gui.inside(mouseX, mouseY, x, cy, w, h);
        if (hovered)
        {
            g.fill(x + 1, cy + 1, x + w - 1, cy + h - 1, Gui.ROW_HOVER);
        }
        final List<IBuildingExtension> owned = fields.getOwnedFields();
        g.drawString(font, Gui.fit(font, Component.translatable("com.minecolonies.coremod.gui.workerhuts.fields.fieldcount", owned.size(), fields.getMaxFieldCount()),
          w - 10), x + 5, cy + 5, Gui.WHITE, true);
        int ty = cy + 16;
        if (fields instanceof BuildingPlantation.PlantationFieldsModuleView plantation)
        {
            g.drawString(font, Gui.fit(font, Component.translatable("com.minecolonies.coremod.gui.workerhuts.fields.plantcount", plantation.getCurrentPlants(),
              plantation.getMaxConcurrentPlants()), w - 10), x + 5, ty, Gui.LIST_SOFT, true);
            ty += 11;
        }
        if (owned.isEmpty())
        {
            for (final var line : font.split(Component.translatable(fields.assignFieldManually() ? "mcvanillatweaks.hut.fields.none_owned" : "mcvanillatweaks.hut.fields.auto_hint"),
              w - 10))
            {
                g.drawString(font, line, x + 5, ty, Gui.LIST_SOFT, true);
                ty += 10;
            }
        }
        else
        {
            final int perRow = (w - 8) / 18;
            for (int i = 0; i < Math.min(owned.size(), perRow); i++)
            {
                final int sx = x + 4 + i * 18;
                Gui.slot(g, sx, ty + 2);
                final ItemStack crop = FieldsTab.crop(owned.get(i));
                g.renderItem(crop.isEmpty() ? new ItemStack(Items.COARSE_DIRT) : crop, sx + 1, ty + 3);
            }
        }
        if (hovered)
        {
            final List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("com.minecolonies.coremod.gui.workerhuts.fields"));
            for (final IBuildingExtension field : owned)
            {
                lines.add(Component.literal("- ").append(FieldsTab.name(field)).append(Component.literal(" · ")).append(FieldsTab.distance(field, building()))
                            .withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.translatable("mcvanillatweaks.hut.fields.open").withStyle(ChatFormatting.YELLOW));
            tooltip.set(lines);
        }
    }

    private int storageTop()
    {
        return y + HEADING + 4 * STEP + 4;
    }

    /** The warehouse's racks: fill and contents in short; a click opens the storage page. */
    private void renderStorage(final GuiGraphics g, final WarehouseTab.Storage storage, final int mouseX, final int mouseY, final Tooltip tooltip, final int w)
    {
        final int rx = rightX();
        final int cy = storageTop();
        final int h = y + height - cy;
        Gui.inset(g, rx, cy, w, h);
        final boolean hovered = Gui.inside(mouseX, mouseY, rx, cy, w, h);
        if (hovered)
        {
            g.fill(rx + 1, cy + 1, rx + w - 1, cy + h - 1, Gui.ROW_HOVER);
        }
        g.renderItem(new ItemStack(Items.BARREL), rx + 4, cy + 4);
        g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.hut.warehouse.storage"), w - 28), rx + 24, cy + 5, Gui.WHITE, true);
        g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.hut.warehouse.items_kinds", ItemGrid.grouped(storage.total()), storage.items().size()),
          w - 28), rx + 24, cy + 15, Gui.LIST_SOFT, true);
        WarehouseTab.renderFill(g, font, storage, rx, cy + 29, w);
        if (hovered)
        {
            tooltip.set(List.of(Component.translatable("mcvanillatweaks.hut.warehouse.storage"),
              Component.translatable("mcvanillatweaks.hut.warehouse.open").withStyle(ChatFormatting.YELLOW)));
        }
    }

    /** A hut without workers: its block, name and level, in place of the worker list. */
    private void renderCard(final GuiGraphics g, final int w)
    {
        heading(g, Component.translatable("mcvanillatweaks.hut.summary"), x, y, w);
        final int top = y + HEADING;
        final int h = y + height - top;
        Gui.inset(g, x, top, w, h);
        final int cx = x + w / 2;
        VanillaIcons.item(g, HutScreen.hutIcon(building()), cx - 16, top + 14, 32);
        final Component name = Component.translatable(building().getBuildingDisplayName());
        g.drawCenteredString(font, Gui.fit(font, name, w - 12), cx, top + 56, Gui.WHITE);
        final Component level = Component.translatable("mcvanillatweaks.hut.level", building().getBuildingLevel(), building().getBuildingMaxLevel());
        g.drawCenteredString(font, level, cx, top + 70, Gui.LIST_SOFT);
        int ty = top + 92;
        for (final var line : font.split(Component.translatable("mcvanillatweaks.hut.no_workers_hint"), w - 16))
        {
            g.drawCenteredString(font, line, cx, ty, Gui.LIST_SOFT);
            ty += 10;
        }
    }

    @Override
    protected boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (warehouse != null && button == 0 && Gui.inside(mouseX, mouseY, rightX(), storageTop(), columnWidth(), y + height - storageTop()))
        {
            screen.open(HutPage.module(warehouse));
            return true;
        }
        if (!staffed)
        {
            return false;
        }
        if (resources != null && button == 0 && Gui.inside(mouseX, mouseY, x, siteTop(), columnWidth(), listBottom() - siteTop()))
        {
            screen.open(HutPage.module(resources));
            return true;
        }
        if (fields != null && button == 0 && Gui.inside(mouseX, mouseY, x, siteTop(), columnWidth(), listBottom() - siteTop()))
        {
            screen.open(HutPage.module(fields));
            return true;
        }
        if (resources == null && mine != null && button == 0 && Gui.inside(mouseX, mouseY, x, siteTop(), columnWidth(), listBottom() - siteTop()))
        {
            screen.open(HutPage.module(mine));
            return true;
        }
        if (list.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button == 0 && list.overRows(mouseX, mouseY))
        {
            final int index = (int) Math.floor((mouseY - list.listY()) / ROW);
            if (index >= 0 && index < workers.size())
            {
                Gui.clickSound();
                new MainWindowCitizen(workers.get(index).citizen()).open();
                return true;
            }
        }
        return false;
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
