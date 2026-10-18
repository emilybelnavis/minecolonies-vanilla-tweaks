package fr.mcvanillatweaks.client.dialog;

import com.ldtteam.blockui.BOScreen;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.controls.TextField;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.workorders.IWorkOrderView;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.api.util.Utils;
import com.minecolonies.core.client.gui.WindowBuildDecoration;
import com.minecolonies.core.client.gui.WindowConfirm;
import com.minecolonies.core.client.gui.WindowRequestDetail;
import com.minecolonies.core.client.gui.WindowSupplyStory;
import com.minecolonies.core.client.gui.modules.RequestTreeWindowModule;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.api.colony.requestsystem.requestable.IStackBasedTask;
import com.minecolonies.core.client.gui.containers.WindowBuildingInventory;
import com.minecolonies.core.client.gui.containers.WindowGrave;
import com.minecolonies.core.client.gui.containers.WindowRack;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import com.minecolonies.core.client.gui.WindowDecorationController;
import com.minecolonies.core.tileentities.TileEntityDecorationController;
import com.minecolonies.core.client.gui.WindowHutGuide;
import com.minecolonies.core.client.gui.WindowReactivateBuilding;
import com.minecolonies.core.client.gui.townhall.WindowTownHallCantCreateColony;
import com.minecolonies.core.client.gui.townhall.WindowTownHallColonyManage;
import com.minecolonies.core.client.gui.townhall.WindowTownHallColonyReactivate;
import com.minecolonies.core.client.gui.townhall.WindowTownHallDeleteAbandonColony;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import fr.mcvanillatweaks.config.HudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Swaps MineColonies' small one-shot windows for {@link DialogScreen}s (config {@code vanillaDialogs}): the generic
 * confirmation, the town hall placement windows (can't found here, founding covenant, reactivating an abandoned colony,
 * deleting or abandoning the old one), reactivating an abandoned hut and the builder's first-time guide. Also MultiPiston's
 * settings window ({@link MultiPistonScreen}).
 * <p>
 * Texts are read from the replaced window's panes (MineColonies computes them: random story, distances...), and
 * every action presses the replaced window's own button ({@link #press}), so its messages, sounds, advancement triggers
 * and follow-up windows are MineColonies' own.
 */
public final class DialogScreens
{
    /** MultiPiston's settings window (an optional mod, so matched by name). */
    private static final String MULTI_PISTON_WINDOW = "com.ldtteam.multipiston.WindowMultiPiston";

    private static boolean failed;

    private DialogScreens()
    {
    }

    public static boolean enabled()
    {
        if (failed)
        {
            return false;
        }
        try
        {
            return HudConfig.VANILLA_DIALOGS.get();
        }
        catch (final IllegalStateException e)
        {
            // Config not loaded yet.
            return true;
        }
    }

    public static void onScreenOpening(final ScreenEvent.Opening event)
    {
        if (enabled() && event.getNewScreen() instanceof AbstractContainerScreen<?> container
              && (container.getClass() == WindowBuildingInventory.class || container.getClass() == WindowGrave.class
                    || container.getClass() == WindowRack.class))
        {
            // Chest-like containers: same menu, vanilla chest look.
            try
            {
                final Minecraft mc = Minecraft.getInstance();
                if (mc.player != null)
                {
                    event.setNewScreen(new ChestLikeScreen<>(container.getMenu(), mc.player.getInventory(), container.getTitle()));
                }
            }
            catch (final LinkageError | RuntimeException e)
            {
                report(e);
            }
            return;
        }
        if (enabled() && event.getNewScreen() instanceof BOScreen multiPiston && multiPiston.getWindow() != null
              && MULTI_PISTON_WINDOW.equals(multiPiston.getWindow().getClass().getName()))
        {
            // MultiPiston is optional: its classes are only touched once its window shows up.
            try
            {
                final net.minecraft.client.gui.screens.Screen replacement = MultiPistonScreen.of(multiPiston.getWindow());
                if (replacement != null)
                {
                    event.setNewScreen(replacement);
                }
            }
            catch (final LinkageError | RuntimeException | ReflectiveOperationException e)
            {
                report(e);
            }
            return;
        }
        if (!enabled() || !(event.getNewScreen() instanceof BOScreen screen) || !(screen.getWindow() instanceof AbstractWindowSkeleton window))
        {
            return;
        }
        try
        {
            if (window.getClass() == WindowDecorationController.class)
            {
                event.setNewScreen(decorationController(window));
                return;
            }
            if (window.getClass() == WindowBuildDecoration.class)
            {
                event.setNewScreen(new DecorationBuildScreen(window));
                return;
            }
            if (window.getClass() == com.minecolonies.core.client.gui.WindowSchematicAnalyzer.class)
            {
                event.setNewScreen(new AnalyzerScreen(window));
                return;
            }
            if (window.getClass() == com.minecolonies.core.client.gui.WindowBuildingBrowser.class)
            {
                event.setNewScreen(new BuildingBrowserScreen(window));
                return;
            }
            final DialogScreen.Spec spec = spec(window);
            if (spec != null)
            {
                event.setNewScreen(new DialogScreen(spec));
            }
        }
        catch (final LinkageError | RuntimeException | ReflectiveOperationException e)
        {
            report(e);
        }
    }

    static void report(final Throwable e)
    {
        if (!failed)
        {
            failed = true;
            MineColoniesVanillaTweaks.LOGGER.warn("Vanilla dialogs unavailable, falling back to MineColonies' windows", e);
        }
    }

    @Nullable
    private static DialogScreen.Spec spec(final AbstractWindowSkeleton window) throws ReflectiveOperationException
    {
        final Class<?> type = window.getClass();
        if (type == WindowConfirm.class)
        {
            return confirm(window);
        }
        if (type == WindowTownHallCantCreateColony.class)
        {
            return cantFound(window);
        }
        if (type == WindowTownHallColonyReactivate.class)
        {
            return reactivateColony(window);
        }
        if (type == WindowTownHallColonyManage.class)
        {
            return covenant(window);
        }
        if (type == WindowTownHallDeleteAbandonColony.class)
        {
            return deleteAbandon(window);
        }
        if (type == WindowReactivateBuilding.class)
        {
            return reactivateBuilding(window);
        }
        if (type == WindowHutGuide.class)
        {
            return guide(window);
        }
        if (type == WindowSupplyStory.class)
        {
            return supplyStory(window);
        }
        if (type == WindowRequestDetail.class)
        {
            return requestDetail(window);
        }
        return null;
    }

    // ------------------------------------------------------------------ the dialogs

    /** WindowConfirm: its "cancel" button is labelled yes and runs the action, "confirm" is labelled cancel and closes. */
    private static DialogScreen.Spec confirm(final AbstractWindowSkeleton window) throws ReflectiveOperationException
    {
        final List<DialogScreen.Line> lines = new ArrayList<>();
        for (final MutableComponent text : text(window, "warning"))
        {
            lines.add(DialogScreen.Line.of(text));
        }
        return new DialogScreen.Spec(new ItemStack(Items.ENDER_PEARL), joined(text(window, "title")), lines,
          List.of(action(Component.translatable("gui.cancel"), window, "confirm"), action(Component.translatable("gui.yes"), window, "cancel")),
          260, () -> press(window, "confirm"));
    }

    /** The town hall was placed where no colony can be founded: the reason, and a button to pick the block up. */
    private static DialogScreen.Spec cantFound(final AbstractWindowSkeleton window) throws ReflectiveOperationException
    {
        final List<DialogScreen.Line> lines = new ArrayList<>();
        for (final MutableComponent text : text(window, "text1"))
        {
            lines.add(DialogScreen.Line.of(text));
        }
        final Pane text = window.findPaneOfTypeByID("text1", Text.class);
        if (text != null && text.getHoverPane() != null)
        {
            lines.add(DialogScreen.Line.soft(Component.translatable("com.minecolonies.core.configsetting")));
        }
        return new DialogScreen.Spec(new ItemStack(Items.BARRIER), Component.translatable("mcvanillatweaks.dialog.cant_found"), lines,
          List.of(action(Component.translatable("gui.cancel"), window, "cancel"),
            action(Component.translatable("com.minecolonies.core.founding.pickup"), window, "pickup")),
          280, () -> press(window, "cancel"));
    }

    /** An abandoned colony's town hall: its random story, and "reestablish" opens the founding covenant. */
    private static DialogScreen.Spec reactivateColony(final AbstractWindowSkeleton window) throws ReflectiveOperationException
    {
        final List<DialogScreen.Line> lines = new ArrayList<>();
        for (final MutableComponent text : text(window, "text1"))
        {
            lines.add(DialogScreen.Line.of(text));
        }
        for (final MutableComponent text : text(window, "text2"))
        {
            lines.add(DialogScreen.Line.soft(text));
        }
        return new DialogScreen.Spec(townHall(), joined(text(window, "title")), lines,
          List.of(action(Component.translatable("gui.cancel"), window, "cancel"),
            action(Component.translatable("com.minecolonies.core.gui.reactivate.button"), window, "create")),
          300, () -> press(window, "cancel"));
    }

    /** The founding covenant: the colony name goes into the replaced window's field before its "create" button is pressed. */
    private static DialogScreen.Spec covenant(final AbstractWindowSkeleton window) throws ReflectiveOperationException
    {
        final TextField nameField = window.findPaneOfTypeByID("colonyname", TextField.class);
        final List<DialogScreen.Line> lines = new ArrayList<>();
        for (final MutableComponent text : text(window, "text1"))
        {
            lines.add(DialogScreen.Line.of(text));
        }
        final int afterLines = lines.size();
        lines.add(DialogScreen.Line.of(Component.translatable("com.minecolonies.core.settlementcovenant2")));
        for (final MutableComponent text : text(window, "text3"))
        {
            lines.add(DialogScreen.Line.of(text));
        }
        lines.add(DialogScreen.Line.soft(Component.translatable("com.minecolonies.core.settlementcovenant4")));
        return new DialogScreen.Spec(new ItemStack(Items.WRITTEN_BOOK), Component.translatable("com.minecolonies.core.settlementcovenant.title"), lines,
          new DialogScreen.Input(nameField == null ? "" : nameField.getText(), 32, afterLines),
          List.of(action(Component.translatable("gui.cancel"), window, "cancel"),
            new DialogScreen.Action(Component.translatable("com.minecolonies.coremod.gui.colony.create"), screen -> {
                if (nameField != null)
                {
                    nameField.setText(screen.value());
                }
                press(window, "create");
            })),
          320, () -> press(window, "cancel"));
    }

    /**
     * The player already runs a colony: pick the town hall up, or destroy / abandon (when the server allows several
     * colonies) the old one, each after a confirmation step like MineColonies' dialog.
     */
    private static DialogScreen.Spec deleteAbandon(final AbstractWindowSkeleton window) throws ReflectiveOperationException
    {
        final List<DialogScreen.Line> lines = new ArrayList<>();
        for (final MutableComponent text : text(window, "warningtext"))
        {
            lines.add(DialogScreen.Line.of(text));
        }
        final Pane abandon = window.findPaneOfTypeByID("abandon", ButtonImage.class);
        final boolean canAbandon = abandon != null && abandon.isVisible();
        final List<DialogScreen.Action> actions = new ArrayList<>();
        actions.add(action(Component.translatable("com.minecolonies.core.founding.pickup"), window, "pickup"));
        final DialogScreen.Spec[] first = new DialogScreen.Spec[1];
        actions.add(new DialogScreen.Action(Component.translatable("com.minecolonies.core.founding.delete"), screen -> screen.show(
          confirmStep(window, first[0], "com.minecolonies.core.gui.colony.delete.proceed", "com.minecolonies.core.gui.colony.delete.confirm",
            "confirmdelete"))));
        if (canAbandon)
        {
            actions.add(new DialogScreen.Action(Component.translatable("com.minecolonies.core.founding.abandon"), screen -> screen.show(
              confirmStep(window, first[0], "com.minecolonies.core.gui.colony.abandon.proceed", "com.minecolonies.core.gui.colony.abandon.confirm",
                "confirmabandon"))));
        }
        actions.add(action(Component.translatable("gui.cancel"), window, "cancel"));
        first[0] = new DialogScreen.Spec(townHall(), Component.translatable("mcvanillatweaks.dialog.old_colony"), lines, actions, 320,
          () -> press(window, "cancel"));
        return first[0];
    }

    private static DialogScreen.Spec confirmStep(final AbstractWindowSkeleton window, final DialogScreen.Spec back, final String question,
      final String confirm, final String button)
    {
        return new DialogScreen.Spec(new ItemStack(Items.TNT), Component.translatable(confirm),
          List.of(DialogScreen.Line.of(Component.translatable(question))),
          List.of(new DialogScreen.Action(Component.translatable("com.minecolonies.core.gui.colony.manage.cancel"), screen -> screen.show(back)),
            action(Component.translatable(confirm), window, button)),
          280, null);
    }

    /** An abandoned hut of a reactivated colony: MineColonies' message (living or working hut) and "reactivate". */
    private static DialogScreen.Spec reactivateBuilding(final AbstractWindowSkeleton window) throws ReflectiveOperationException
    {
        final BlockPos pos = (BlockPos) field(window, "pos");
        ItemStack icon = new ItemStack(Items.COBWEB);
        Component title = Component.translatable("mcvanillatweaks.dialog.abandoned_hut");
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && pos != null)
        {
            final ItemStack block = new ItemStack(mc.level.getBlockState(pos).getBlock());
            if (!block.isEmpty())
            {
                icon = block;
                title = Component.translatable("mcvanillatweaks.dialog.abandoned", block.getHoverName());
            }
        }
        final List<DialogScreen.Line> lines = new ArrayList<>();
        for (final MutableComponent text : text(window, "text"))
        {
            lines.add(DialogScreen.Line.of(text));
        }
        return new DialogScreen.Spec(icon, title, lines,
          List.of(action(Component.translatable("gui.cancel"), window, "cancel"),
            action(Component.translatable("com.minecolonies.coremod.gui.reactivate.button"), window, "reactivate")),
          280, () -> press(window, "cancel"));
    }

    /**
     * The builder's hut guide shown once, before its hut window: MineColonies' three hints, each with the icon of the
     * button they mean in the hut screen ("Got it" opens the hut and records the guide as read).
     */
    private static DialogScreen.Spec guide(final AbstractWindowSkeleton window)
    {
        return new DialogScreen.Spec(new ItemStack(ModBlocks.blockHutBuilder), Component.translatable("mcvanillatweaks.dialog.guide"),
          List.of(DialogScreen.Line.icon(new ItemStack(Items.KNOWLEDGE_BOOK), Component.translatable("com.minecolonies.coremod.gui.workerhuts.guide.questionmark")),
            DialogScreen.Line.icon(new ItemStack(Items.PAPER), Component.translatable("com.minecolonies.coremod.gui.workerhuts.guide.arrow")),
            DialogScreen.Line.icon(new ItemStack(Items.BARREL), Component.translatable("com.minecolonies.coremod.gui.workerhuts.guide.chest"))),
          List.of(action(Component.translatable("com.minecolonies.coremod.gui.workerhuts.guide.confirm"), window, "confirm")),
          260, () -> press(window, "close"));
    }

    /**
     * A decoration controller: the decoration's name, style and level, its work order if any, and MineColonies' buttons
     * as they appear (build next level / cancel, repair: shown once the blueprints are checked, so followed every tick).
     */
    private static DialogScreen decorationController(final AbstractWindowSkeleton window) throws ReflectiveOperationException
    {
        final TileEntityDecorationController controller = (TileEntityDecorationController) field(window, "controller");
        final Minecraft mc = Minecraft.getInstance();
        final String path = controller.getBlueprintPath().replace(".blueprint", "").replace("\\", "/");
        final String[] parts = path.split("/");
        final List<DialogScreen.Line> lines = new ArrayList<>();
        lines.add(DialogScreen.Line.of(Component.literal(parts[parts.length - 1])));
        final List<String> folders = new ArrayList<>();
        for (int i = 0; i < parts.length - 1; i++)
        {
            if (!parts[i].isBlank())
            {
                folders.add(parts[i]);
            }
        }
        final String pack = controller.getPackName() == null || mc.player == null ? "" : controller.getPackName().replace(mc.player.getUUID().toString(), "");
        lines.add(DialogScreen.Line.soft(Component.translatable("mcvanillatweaks.decoration.where", pack, String.join(" / ", folders))));
        final int level = Utils.getBlueprintLevel(controller.getBlueprintPath());
        if (level != -1)
        {
            lines.add(DialogScreen.Line.soft(Component.translatable("mcvanillatweaks.decoration.level", level)));
        }
        final IColonyView colony = mc.level == null ? null : IColonyManager.getInstance().getClosestColonyView(mc.level, controller.getBlockPos());
        if (colony != null)
        {
            for (final IWorkOrderView order : colony.getWorkOrders())
            {
                if (order.getLocation().equals(controller.getBlockPos()))
                {
                    lines.add(DialogScreen.Line.icon(new ItemStack(Items.WRITABLE_BOOK), Component.translatable(
                      order.getWorkOrderType() == WorkOrderType.REPAIR ? "mcvanillatweaks.decoration.repairing" : "mcvanillatweaks.decoration.building")));
                }
            }
        }
        final ItemStack icon = mc.level == null ? new ItemStack(Items.STRUCTURE_BLOCK) : new ItemStack(mc.level.getBlockState(controller.getBlockPos()).getBlock());
        final Component title = icon.isEmpty() ? Component.translatable("mcvanillatweaks.decoration.title") : icon.getHoverName();
        final java.util.function.Supplier<DialogScreen.Spec> spec = () -> {
            final List<DialogScreen.Action> actions = new ArrayList<>();
            actions.add(action(Component.translatable("gui.cancel"), window, "cancel"));
            for (final String id : new String[] {"repair", "build"})
            {
                final Button button = window.findPaneOfTypeByID(id, Button.class);
                if (button != null && button.isVisible())
                {
                    actions.add(action(button.getText(), window, id));
                }
            }
            return new DialogScreen.Spec(icon.isEmpty() ? new ItemStack(Items.STRUCTURE_BLOCK) : icon, title, lines, actions, 280, () -> press(window, "cancel"));
        };
        final int[] shown = {visibleButtons(window)};
        return new DialogScreen(spec.get()).onTick(screen -> {
            final int now = visibleButtons(window);
            if (now != shown[0])
            {
                shown[0] = now;
                screen.show(spec.get());
            }
        });
    }

    private static int visibleButtons(final AbstractWindowSkeleton window)
    {
        int mask = 0;
        final String[] ids = {"repair", "build"};
        for (int i = 0; i < ids.length; i++)
        {
            final Pane pane = window.findPaneByID(ids[i]);
            if (pane != null && pane.isVisible())
            {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    /**
     * The story of a supply camp / ship (an item, used once): the story MineColonies wrote in its text pane, then
     * "choose style" (Structurize's pack window, then back here) and "place" (the placement tool).
     */
    private static DialogScreen.Spec supplyStory(final AbstractWindowSkeleton window) throws ReflectiveOperationException
    {
        final ItemStack stack = field(window, "stack") instanceof ItemStack item ? item : ItemStack.EMPTY;
        final List<DialogScreen.Line> lines = new ArrayList<>();
        for (final MutableComponent text : text(window, "text"))
        {
            if (!text.getString().isBlank())
            {
                lines.add(DialogScreen.Line.of(text));
            }
        }
        final Button place = window.findPaneOfTypeByID("place", Button.class);
        return new DialogScreen.Spec(stack.isEmpty() ? new ItemStack(Items.CHEST) : stack.copyWithCount(1),
          stack.isEmpty() ? Component.translatable("mcvanillatweaks.dialog.supplies") : stack.getHoverName(), lines,
          List.of(action(Component.translatable("gui.cancel"), window, "cancel"),
            action(Component.translatable("com.minecolonies.core.gui.supplies.selectstyle"), window, "colonyStylePicker"),
            action(place == null ? Component.translatable("mcvanillatweaks.dialog.place") : place.getText(), window, "place")),
          340, () -> press(window, "cancel"));
    }

    /**
     * A request's details (MineColonies opens it from its request lists; ours show the details in place): what is asked,
     * by whom, where, who handles it, and fulfil / cancel when the request tree allows them.
     */
    private static DialogScreen.Spec requestDetail(final AbstractWindowSkeleton window) throws ReflectiveOperationException
    {
        final IRequest<?> request = (IRequest<?>) field(window, "request");
        final int colonyId = (Integer) field(window, "colonyId");
        final RequestTreeWindowModule module = (RequestTreeWindowModule) field(window, "requestTreeWindowModule");
        final Minecraft mc = Minecraft.getInstance();
        final IColonyView colony = mc.level == null ? null : IColonyManager.getInstance().getColonyView(colonyId, mc.level.dimension());
        ItemStack icon = new ItemStack(Items.WRITABLE_BOOK);
        Component title;
        if (request instanceof IStackBasedTask task)
        {
            icon = task.getTaskStack().copyWithCount(Math.max(1, task.getDisplayCount()));
            title = task.getDisplayPrefix().copy().append(task.getTaskStack().getHoverName());
        }
        else
        {
            title = Component.literal(request.getLongDisplayString().getString().replace("§f", ""));
            if (!request.getDisplayStacks().isEmpty())
            {
                icon = request.getDisplayStacks().get(0).copyWithCount(1);
            }
        }
        final List<DialogScreen.Line> lines = new ArrayList<>();
        if (!(request instanceof IStackBasedTask))
        {
            lines.add(DialogScreen.Line.of(title));
            title = Component.translatable("com.minecolonies.coremod.gui.citizen.requests");
        }
        if (colony != null)
        {
            lines.add(DialogScreen.Line.of(Component.translatable("mcvanillatweaks.dialog.request.by",
              request.getRequester().getRequesterDisplayName(colony.getRequestManager(), request))));
            lines.add(DialogScreen.Line.soft(Component.translatable("mcvanillatweaks.dialog.request.where", request.getRequester().getLocation().toString())));
            try
            {
                final IRequestResolver<?> resolver = colony.getRequestManager().getResolverForRequest(request.getId());
                if (resolver != null)
                {
                    lines.add(DialogScreen.Line.of(Component.translatable("mcvanillatweaks.dialog.request.resolver",
                      resolver.getRequesterDisplayName(colony.getRequestManager(), request))));
                }
            }
            catch (final IllegalArgumentException ignored)
            {
                // No resolver yet.
            }
        }
        final List<DialogScreen.Action> actions = new ArrayList<>();
        actions.add(new DialogScreen.Action(Component.translatable("com.minecolonies.coremod.gui.back"), screen -> window.close()));
        if (module != null && module.isFulfillable(request))
        {
            actions.add(action(Component.translatable("com.minecolonies.coremod.gui.citizen.requestsfulfill"), window, "fulfill"));
        }
        if (module != null && module.isCancellable(request))
        {
            actions.add(action(Component.translatable("com.minecolonies.coremod.gui.citizen.requestscancel"), window, "cancel"));
        }
        return new DialogScreen.Spec(icon, title, lines, actions, 300, window::close);
    }

    // ------------------------------------------------------------------ helpers

    private static ItemStack townHall()
    {
        return new ItemStack(ModBlocks.blockHutTownHall);
    }

    private static DialogScreen.Action action(final Component label, final AbstractWindowSkeleton window, final String button)
    {
        return new DialogScreen.Action(label, screen -> press(window, button));
    }

    /**
     * Runs the replaced window's button handler, with its GUI click trigger. The handler usually ends with
     * {@code close()}, which closes the current (our) screen and reopens the window's parent.
     */
    static void press(final AbstractWindowSkeleton window, final String id)
    {
        try
        {
            Button button = window.findPaneOfTypeByID(id, Button.class);
            if (button == null)
            {
                button = new ButtonImage();
                button.setID(id);
            }
            window.onButtonClicked(button);
        }
        catch (final LinkageError | RuntimeException e)
        {
            report(e);
            Minecraft.getInstance().setScreen(null);
        }
    }

    /** The lines of a BlockUI text pane. */
    static List<MutableComponent> text(final AbstractWindowSkeleton window, final String id)
    {
        final Text pane = window.findPaneOfTypeByID(id, Text.class);
        final List<MutableComponent> lines = pane == null ? null : pane.getTextAsList();
        return lines == null ? List.of() : lines;
    }

    private static Component joined(final List<MutableComponent> lines)
    {
        final MutableComponent out = Component.empty();
        for (int i = 0; i < lines.size(); i++)
        {
            if (i > 0)
            {
                out.append(" ");
            }
            out.append(lines.get(i));
        }
        return out;
    }

    @Nullable
    static Object field(final Object window, final String name) throws ReflectiveOperationException
    {
        Class<?> type = window.getClass();
        while (type != null)
        {
            try
            {
                final Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(window);
            }
            catch (final NoSuchFieldException e)
            {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
