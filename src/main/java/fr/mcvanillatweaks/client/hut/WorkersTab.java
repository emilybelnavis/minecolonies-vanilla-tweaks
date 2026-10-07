package fr.mcvanillatweaks.client.hut;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.HiringMode;
import com.minecolonies.api.colony.buildings.modules.IAssignmentModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.client.gui.citizen.MainWindowCitizen;
import com.minecolonies.core.colony.buildings.moduleviews.PupilBuildingModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import com.minecolonies.core.network.messages.server.colony.citizen.PauseCitizenMessage;
import com.minecolonies.core.network.messages.server.colony.citizen.RestartCitizenMessage;
import com.minecolonies.core.util.BuildingUtils;
import fr.mcvanillatweaks.client.citizen.CitizenInfo;
import fr.mcvanillatweaks.client.citizen.Dropdown;
import fr.mcvanillatweaks.client.citizen.DropdownButton;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import fr.mcvanillatweaks.client.citizen.VanillaIcons;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Workers page (MineColonies' hiring window): the job to staff and its hiring mode on top, then the citizens who can
 * take the job, with the hire, fire, pause and restart buttons of MineColonies' list. The list is filtered and sorted
 * like MineColonies (this hut's workers, then the unemployed, then citizens employed elsewhere, nearest homes first),
 * and each button sends the same message or calls the same module method.
 * <pre>
 * [Job: Farmer (1/1)       v]  [Hiring: Default      v]
 * [x] Show employed citizens
 * +-----------------------------------------------------+
 * | (o) Farmer: Alice                 [Pause] [Fire]    |
 * |     Stamina 12  Athletics 8 · lives here            |
 * +-----------------------------------------------------+
 * </pre>
 */
final class WorkersTab extends HutTab
{
    private static final int ROW = 26;
    private static final int BUTTON_HEIGHT = 16;
    private static final int CONTROL_HEIGHT = 20;
    private static final HiringMode[] MODES = {HiringMode.DEFAULT, HiringMode.AUTO, HiringMode.MANUAL};
    private static final int MODE_WIDTH = 130;

    /** A button drawn in a row of the list. */
    private record RowButton(String label, int x, int width, Runnable action)
    {
    }

    /** The selected job is remembered for the session per hut, like the town hall remembers its pages. */
    private static BlockPos lastBuilding;
    private static int lastModule;
    private static boolean lastShowEmployed;

    private final ScrollArea list = new ScrollArea();
    private final List<IAssignmentModuleView> modules;
    private int selected;
    private boolean showEmployed;
    private List<ICitizenDataView> citizens = List.of();
    private DropdownButton jobButton;
    private DropdownButton modeButton;
    private int ticks;

    WorkersTab(final HutScreen screen)
    {
        super(screen);
        modules = modules(screen.building());
        if (screen.building().getPosition().equals(lastBuilding))
        {
            selected = Math.min(lastModule, Math.max(0, modules.size() - 1));
            showEmployed = lastShowEmployed && canShowEmployed();
        }
        lastBuilding = screen.building().getPosition();
        refresh();
    }

    /** The jobs MineColonies' hiring window offers: modules with room, whose job the hut allows or that already employ someone. */
    static List<IAssignmentModuleView> modules(final IBuildingView building)
    {
        final List<IAssignmentModuleView> all = building.getModuleViews(IAssignmentModuleView.class);
        if (all.isEmpty())
        {
            return List.of();
        }
        final Predicate<JobEntry> allowed = allowedJobs(building);
        final List<IAssignmentModuleView> result = new ArrayList<>();
        for (final IAssignmentModuleView module : all)
        {
            if (module.getMaxInhabitants() > 0 && (!module.getAssignedCitizens().isEmpty() || allowed.test(module.getJobEntry())) && unlocked(building, module))
            {
                result.add(module);
            }
        }
        return result;
    }

    private static Predicate<JobEntry> allowedJobs(final IBuildingView building)
    {
        final IColonyView colony = building.getColony();
        return colony == null || colony.getWorld() == null ? BuildingUtils.UNRESTRICTED : BuildingUtils.getAllowedJobs(colony.getWorld(), building.getPosition());
    }

    /** MineColonies hides the job buttons whose research is not done. */
    private static boolean unlocked(final IBuildingView building, final IAssignmentModuleView module)
    {
        return module.getResearchRequirement() == null
                 || building.getColony().getResearchManager().getResearchEffects().getEffectStrength(module.getResearchRequirement()) > 0;
    }

    @Nullable
    private IAssignmentModuleView module()
    {
        return modules.isEmpty() ? null : modules.get(Math.min(selected, modules.size() - 1));
    }

    private boolean canShowEmployed()
    {
        final IAssignmentModuleView module = module();
        return module instanceof WorkerBuildingModuleView && !(module instanceof PupilBuildingModuleView);
    }

    // ------------------------------------------------------------------ widgets

    @Override
    protected void init()
    {
        final IAssignmentModuleView module = module();
        if (module == null)
        {
            return;
        }
        // Several jobs (e.g. a guard tower's knight and archer): a drop-down; one job is just a title.
        if (modules.size() > 1)
        {
            jobButton = dropdown(x, y, jobWidth(), jobLabel(), this::jobEntries, () -> selected, index -> {
                selected = index;
                lastModule = index;
                showEmployed = showEmployed && canShowEmployed();
                lastShowEmployed = showEmployed;
                refresh();
                screen.rebuild();
            });
        }
        modeButton = dropdown(x + width - MODE_WIDTH, y, MODE_WIDTH, modeLabel(module), () -> {
            final List<Dropdown.Entry> entries = new ArrayList<>();
            for (final HiringMode mode : MODES)
            {
                entries.add(new Dropdown.Entry(modeName(mode)));
            }
            return entries;
        }, () -> List.of(MODES).indexOf(module.getHiringMode()), index -> {
            module.setHiringMode(MODES[index]);
            modeButton.setMessage(modeLabel(module));
        });
        final Checkbox employed = add(Checkbox.builder(Component.empty(), font).pos(x, y + CONTROL_HEIGHT + 6).selected(showEmployed)
                                        .onValueChange((box, value) -> {
                                            showEmployed = value;
                                            lastShowEmployed = value;
                                            refresh();
                                        }).build());
        employed.active = canShowEmployed();
    }

    private Component jobLabel()
    {
        final IAssignmentModuleView module = module();
        return module == null ? Component.empty() : jobName(module);
    }

    private static MutableComponent jobName(final IAssignmentModuleView module)
    {
        return Component.translatable(module.getJobEntry().getTranslationKey()).append(" (" + module.getAssignedCitizens().size() + "/" + module.getMaxInhabitants() + ")");
    }

    private List<Dropdown.Entry> jobEntries()
    {
        final List<Dropdown.Entry> entries = new ArrayList<>();
        for (final IAssignmentModuleView module : modules)
        {
            entries.add(new Dropdown.Entry(jobName(module)));
        }
        return entries;
    }

    private int jobWidth()
    {
        return width - MODE_WIDTH - GAP - font.width(Component.translatable("mcvanillatweaks.hut.hiring")) - 6;
    }

    private static Component modeLabel(final IAssignmentModuleView module)
    {
        return modeName(module.getHiringMode());
    }

    /** Short names: MineColonies' ones don't fit a button ("Default (controlled by the colony)"). */
    private static Component modeName(final HiringMode mode)
    {
        return Component.translatable("mcvanillatweaks.hut.hiring." + mode.name().toLowerCase(Locale.ROOT));
    }

    @Override
    protected void tick()
    {
        if (++ticks % 20 == 0)
        {
            refresh();
        }
        if (jobButton != null)
        {
            jobButton.setMessage(jobLabel());
        }
    }

    // ------------------------------------------------------------------ citizens, as WindowHireWorker

    private boolean listed(final ICitizenDataView citizen, final IAssignmentModuleView module)
    {
        return showEmployed && !citizen.isChild() || module.canAssign(citizen);
    }

    private int priority(final ICitizenDataView citizen, final IAssignmentModuleView module)
    {
        if (building().getPosition().equals(citizen.getWorkBuilding()))
        {
            return 0;
        }
        if (!module.canAssign(citizen))
        {
            return 3;
        }
        return citizen.getWorkBuilding() == null ? 1 : 2;
    }

    /** MineColonies rounds the distance to the hut by 40 blocks before sorting by name. */
    private double distanceBucket(final ICitizenDataView citizen)
    {
        final BlockPos home = citizen.getHomeBuilding();
        if (home == null)
        {
            return 100.0D;
        }
        double distance = Math.sqrt(home.distSqr(building().getPosition()));
        if (distance % 40.0D > 20.0D)
        {
            distance = distance - distance % 40.0D + 40.0D;
        }
        else
        {
            distance -= distance % 40.0D;
        }
        return distance;
    }

    private void refresh()
    {
        final IAssignmentModuleView module = module();
        if (module == null || building().getColony() == null)
        {
            citizens = List.of();
            return;
        }
        citizens = building().getColony().getCitizens().values().stream()
                     .filter(c -> listed(c, module))
                     .sorted(Comparator.<ICitizenDataView>comparingInt(c -> priority(c, module))
                               .thenComparingDouble(this::distanceBucket)
                               .thenComparing(ICitizenDataView::getName))
                     .toList();
    }

    private List<RowButton> buttons(final ICitizenDataView citizen, final IAssignmentModuleView module, final int right, final int rowY)
    {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        final boolean assigned = module.getAssignedCitizens().contains(citizen.getId());
        // Listed citizens can all be hired while there is room (MineColonies' row rules).
        if (!assigned && !module.isFull())
        {
            labels.add("com.minecolonies.coremod.gui.hiring.buttonhire");
            actions.add(() -> hire(citizen, module));
        }
        else if (assigned)
        {
            labels.add(citizen.isPaused() ? "com.minecolonies.coremod.gui.hiring.buttonunpaused" : "com.minecolonies.coremod.gui.hiring.buttonpaused");
            actions.add(() -> pause(citizen));
            if (!building().getColony().getTravellingManager().isTravelling(citizen))
            {
                labels.add("com.minecolonies.coremod.gui.hiring.buttonfire");
                actions.add(() -> fire(citizen, module));
            }
        }
        if (citizen.isPaused())
        {
            labels.add(0, "com.minecolonies.coremod.gui.hiring.restart");
            actions.add(0, () -> restart(citizen));
        }
        final List<RowButton> result = new ArrayList<>();
        int bx = right;
        for (int i = labels.size() - 1; i >= 0; i--)
        {
            final String label = I18n.get(labels.get(i));
            final int w = font.width(label) + 10;
            bx -= w;
            result.add(0, new RowButton(label, bx, w, actions.get(i)));
            bx -= 2;
        }
        return result;
    }

    private void hire(final ICitizenDataView citizen, final IAssignmentModuleView module)
    {
        // Like MineColonies: a worker employed elsewhere leaves that job first.
        if (citizen.getWorkBuilding() != null && module instanceof WorkerBuildingModuleView && citizen.getJobView() != null)
        {
            final IBuildingView oldJob = building().getColony().getClientBuildingManager().getBuilding(citizen.getWorkBuilding());
            if (oldJob != null)
            {
                final IAssignmentModuleView old = oldJob.getModuleViewMatching(IAssignmentModuleView.class, m -> m.getJobEntry() == citizen.getJobView().getEntry());
                if (old != null)
                {
                    old.removeCitizen(citizen);
                }
            }
        }
        module.addCitizen(citizen);
        refresh();
    }

    private void fire(final ICitizenDataView citizen, final IAssignmentModuleView module)
    {
        module.removeCitizen(citizen);
        refresh();
    }

    private void pause(final ICitizenDataView citizen)
    {
        if (building() instanceof AbstractBuildingView view)
        {
            new PauseCitizenMessage(view, citizen.getId()).sendToServer();
            citizen.setPaused(!citizen.isPaused());
        }
    }

    private void restart(final ICitizenDataView citizen)
    {
        if (building() instanceof AbstractBuildingView view)
        {
            new RestartCitizenMessage(view, citizen.getId()).sendToServer();
        }
    }

    // ------------------------------------------------------------------ drawing

    private int listTop()
    {
        return y + 2 * CONTROL_HEIGHT + 14;
    }

    private Component jobOf(final ICitizenDataView citizen)
    {
        return citizen.getJob() == null || citizen.getJob().isEmpty()
                 ? Component.translatable("com.minecolonies.coremod.gui.townhall.citizens.unemployed")
                 : Component.translatable(citizen.getJob());
    }

    /** Where the citizen lives: MineColonies' sentence ({@code full}) or a short form for the rows. */
    private Component home(final ICitizenDataView citizen, final boolean full)
    {
        final String prefix = full ? "com.minecolonies.core.gui.hiring." : "mcvanillatweaks.hut.home.";
        final BlockPos home = citizen.getHomeBuilding();
        if (home == null)
        {
            return Component.translatable(prefix + "homeless");
        }
        if (home.equals(building().getPosition()))
        {
            return Component.translatable(prefix + "liveshere");
        }
        if (home.equals(citizen.getWorkBuilding()))
        {
            return Component.translatable(prefix + "livesatwork");
        }
        return Component.translatable(prefix + "distance", (int) Math.sqrt(home.distSqr(building().getPosition())));
    }

    @Nullable
    private Skill primary(final IAssignmentModuleView module)
    {
        return module instanceof WorkerBuildingModuleView worker ? worker.getPrimarySkill() : null;
    }

    @Nullable
    private Skill secondary(final IAssignmentModuleView module)
    {
        return module instanceof WorkerBuildingModuleView worker ? worker.getSecondarySkill() : null;
    }

    @Override
    protected void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final IAssignmentModuleView module = module();
        if (module == null)
        {
            heading(g, Component.translatable("mcvanillatweaks.hut.no_jobs"), x, y + 4, width);
            return;
        }
        g.drawString(font, Component.translatable("com.minecolonies.coremod.gui.showemployed"), x + 24, y + CONTROL_HEIGHT + 12, canShowEmployed() ? Gui.LABEL : Gui.LABEL_SOFT, false);
        final Component hiring = Component.translatable("mcvanillatweaks.hut.hiring");
        g.drawString(font, hiring, x + width - MODE_WIDTH - 4 - font.width(hiring), y + 6, Gui.LABEL, false);
        if (modeButton != null && modeButton.isHovered())
        {
            tooltip.set(List.of(Component.translatable(module.getHiringMode().getTranslationKey()),
              Component.translatable("mcvanillatweaks.hut.hiring_mode_hint").withStyle(ChatFormatting.GRAY)));
        }
        final boolean overJob = jobButton != null ? jobButton.isHovered() : Gui.inside(mouseX, mouseY, x, y, jobWidth(), CONTROL_HEIGHT);
        if (jobButton == null)
        {
            heading(g, jobLabel(), x, y + 6, jobWidth());
        }
        if (overJob)
        {
            tooltip.set(List.of(Component.translatable(module.getJobEntry().getTranslationKey()),
              Component.translatable(module.getJobEntry().getKey() + ".job.desc").withStyle(ChatFormatting.GRAY)));
        }

        final int top = listTop();
        list.set(x, top, width, y + height - top, citizens.size() * ROW, ROW);
        list.begin(g);
        if (citizens.isEmpty())
        {
            g.drawString(font, Gui.fit(font, Component.translatable("mcvanillatweaks.hut.no_candidates"), list.listWidth() - 8),
              list.listX() + 4, list.visibleTop() + 6, Gui.LIST_SOFT, true);
        }
        final Skill primary = primary(module);
        final Skill secondary = secondary(module);
        for (int i = 0; i < citizens.size(); i++)
        {
            final int rowY = list.listY() + i * ROW;
            if (rowY + ROW < list.visibleTop() || rowY > list.visibleBottom())
            {
                continue;
            }
            final ICitizenDataView citizen = citizens.get(i);
            final boolean hovered = list.hover(g, mouseX, mouseY, rowY, ROW);
            final boolean here = building().getPosition().equals(citizen.getWorkBuilding());
            if (here)
            {
                // This hut's workers stand out, like a selected row.
                g.fill(list.listX(), rowY, list.listX() + list.listWidth(), rowY + ROW, Gui.ROW_HOVER);
            }
            VanillaIcons.item(g, VanillaIcons.status(citizen.getVisibleStatus()), list.listX() + 3, rowY + 5, 16);

            final List<RowButton> buttons = buttons(citizen, module, list.listX() + list.listWidth() - 3, rowY);
            final int textRight = buttons.isEmpty() ? list.listX() + list.listWidth() - 3 : buttons.get(0).x() - 4;
            final int tx = list.listX() + 23;
            final int tw = textRight - tx;
            final Component name = Component.literal(citizen.getName()).append(Component.literal(" · ").append(jobOf(citizen)).withStyle(ChatFormatting.GRAY));
            g.drawString(font, Gui.fit(font, name, tw), tx, rowY + 3, citizen.isPaused() ? Gui.YELLOW : Gui.WHITE, true);
            final MutableComponent skills = Component.empty();
            if (primary != null)
            {
                skills.append(Component.literal(CitizenInfo.skillName(primary).getString() + " " + CitizenInfo.skillLevel(citizen, primary)).withStyle(ChatFormatting.GREEN));
            }
            if (secondary != null && secondary != primary)
            {
                skills.append("  ").append(Component.literal(CitizenInfo.skillName(secondary).getString() + " " + CitizenInfo.skillLevel(citizen, secondary))
                                             .withStyle(ChatFormatting.GOLD));
            }
            skills.append(Component.literal((primary != null ? "  ·  " : "")).withStyle(ChatFormatting.DARK_GRAY)).append(home(citizen, false).copy().withStyle(ChatFormatting.GRAY));
            g.drawString(font, Gui.fit(font, skills, tw), tx, rowY + 14, Gui.WHITE, true);

            boolean overButton = false;
            for (final RowButton button : buttons)
            {
                final int by = rowY + (ROW - BUTTON_HEIGHT) / 2;
                final boolean over = list.overRows(mouseX, mouseY) && Gui.inside(mouseX, mouseY, button.x(), by, button.width(), BUTTON_HEIGHT);
                overButton |= over;
                Gui.textButton(g, font, button.x(), by, button.width(), BUTTON_HEIGHT, button.label(), true, over);
            }
            if (hovered && !overButton)
            {
                tooltip.setAround(citizenTooltip(citizen, primary, secondary), mouseX, rowY, rowY + ROW);
            }
        }
        list.end(g);
    }

    /** Name, job and every skill, the job's two skills first and coloured like MineColonies. */
    private List<Component> citizenTooltip(final ICitizenDataView citizen, @Nullable final Skill primary, @Nullable final Skill secondary)
    {
        final List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(citizen.getName()));
        lines.add(jobOf(citizen).copy().withStyle(ChatFormatting.GRAY));
        lines.add(home(citizen, true).copy().withStyle(ChatFormatting.GRAY));
        if (citizen.isPaused())
        {
            lines.add(Component.translatable("mcvanillatweaks.hut.paused").withStyle(ChatFormatting.YELLOW));
        }
        final List<Skill> skills = new ArrayList<>(List.of(Skill.values()));
        skills.sort(Comparator.comparingInt(s -> s == primary ? 0 : s == secondary ? 1 : 2));
        for (final Skill skill : skills)
        {
            final ChatFormatting colour = skill == primary ? ChatFormatting.GREEN : skill == secondary ? ChatFormatting.GOLD : ChatFormatting.GRAY;
            lines.add(Component.literal(CitizenInfo.skillName(skill).getString() + " : " + CitizenInfo.skillLevel(citizen, skill)).withStyle(colour));
        }
        lines.add(Component.translatable("mcvanillatweaks.hut.open_citizen").withStyle(ChatFormatting.YELLOW));
        return lines;
    }

    @Override
    protected boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (list.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        final IAssignmentModuleView module = module();
        if (button != 0 || module == null || !list.overRows(mouseX, mouseY))
        {
            return false;
        }
        final int index = (int) Math.floor((mouseY - list.listY()) / ROW);
        if (index < 0 || index >= citizens.size())
        {
            return false;
        }
        final ICitizenDataView citizen = citizens.get(index);
        final int rowY = list.listY() + index * ROW;
        for (final RowButton rowButton : buttons(citizen, module, list.listX() + list.listWidth() - 3, rowY))
        {
            if (Gui.inside(mouseX, mouseY, rowButton.x(), rowY + (ROW - BUTTON_HEIGHT) / 2, rowButton.width(), BUTTON_HEIGHT))
            {
                Gui.clickSound();
                rowButton.action().run();
                return true;
            }
        }
        Gui.clickSound();
        new MainWindowCitizen(citizen).open();
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
