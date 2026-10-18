package fr.mcvanillatweaks.client.names;

import fr.mcvanillatweaks.names.NameGenerator;
import fr.mcvanillatweaks.names.NameList;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * The citizen name lists ({@link NameLibrary}), laid out like the game's own menus (Select World): title, search
 * field (list names and the names they hold, accents ignored) and state line at the top, the lists in a vanilla
 * selection list under their category's header (a click opens or closes it), each with a checkbox (used for new
 * citizens), a book (built in) or book and quill (the player's own), double click = open; buttons in two rows at the
 * bottom. A list is viewed and edited in
 * {@link NameListEditScreen}; delete asks through the game's confirmation screen; "example" shows a few names drawn
 * from the ticked lists in a toast; "import" reads .json files ({@link NameFiles}), also dropped on the window.
 * <p>
 * The lists are sent to the server when this screen closes back to the settings ({@link NameSync}).
 */
public final class NameListsScreen extends Screen
{
    private static final int HEADER = 60;
    private static final int FOOTER = 64;
    private static final int ROW_HEIGHT = 36;
    private static final int ROW_WIDTH = 300;
    private static final int GREY = 0xFF808080;
    private static final int SOFT = 0xFFA0A0A0;
    private static final SystemToast.SystemToastId SAMPLE_TOAST = new SystemToast.SystemToastId(5000L);
    private static final SystemToast.SystemToastId FILE_TOAST = new SystemToast.SystemToastId(6000L);
    private static final SystemToast.SystemToastId FILE_PROBLEM_TOAST = new SystemToast.SystemToastId(8000L);

    /** Selected list, kept for the session. */
    @Nullable
    private static String selectedId;
    /** Closed categories and the search, kept for the session. */
    private static final Set<String> COLLAPSED = new HashSet<>();
    private static String query = "";

    private final Screen parent;
    private final Random random = new Random();
    private Lists lists;
    private EditBox search;
    private Button editButton;
    private Button copyButton;
    private Button deleteButton;

    public NameListsScreen(final Screen parent)
    {
        super(Component.translatable("mcvanillatweaks.names.title"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        search = addRenderableWidget(new EditBox(font, width / 2 - 100, 20, 200, 20, Component.translatable("mcvanillatweaks.names.search")));
        search.setHint(Component.translatable("mcvanillatweaks.names.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(text -> {
            if (!text.equals(query))
            {
                query = text;
                lists.rebuild();
            }
        });
        lists = addRenderableWidget(new Lists(minecraft, width, height - HEADER - FOOTER, HEADER));
        final int center = width / 2;
        editButton = addRenderableWidget(Button.builder(Component.translatable("mcvanillatweaks.names.edit"), b -> edit())
          .bounds(center - 154, height - 52, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("mcvanillatweaks.names.new"), b -> create())
          .bounds(center - 50, height - 52, 100, 20).build()).active = NameLibrary.canCreate();
        addRenderableWidget(Button.builder(Component.translatable("mcvanillatweaks.names.import"), b -> NameFiles.chooseAndImport(this::imported))
          .bounds(center + 54, height - 52, 100, 20)
          .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("mcvanillatweaks.names.import.tooltip")))
          .build()).active = NameLibrary.canCreate();
        copyButton = addRenderableWidget(Button.builder(Component.translatable("mcvanillatweaks.names.copy"), b -> copy())
          .bounds(center - 154, height - 28, 72, 20)
          .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("mcvanillatweaks.names.copy.tooltip")))
          .build());
        deleteButton = addRenderableWidget(Button.builder(Component.translatable("mcvanillatweaks.names.delete"), b -> delete())
          .bounds(center - 76, height - 28, 72, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("mcvanillatweaks.names.sample.button"), b -> showSample())
          .bounds(center + 4, height - 28, 72, 20)
          .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("mcvanillatweaks.names.sample.button.tooltip")))
          .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
          .bounds(center + 82, height - 28, 72, 20).build());
        lists.rebuild();
        lists.selectInitial();
    }

    @Nullable
    private NameList selected()
    {
        return lists.getSelected() instanceof Row row ? NameLibrary.get(row.id) : null;
    }

    private void updateButtons()
    {
        final NameList list = selected();
        editButton.active = list != null;
        editButton.setMessage(Component.translatable(list != null && !NameLibrary.isCustom(list) ? "mcvanillatweaks.names.view" : "mcvanillatweaks.names.edit"));
        copyButton.active = list != null && NameLibrary.canCreate();
        deleteButton.active = list != null && NameLibrary.isCustom(list);
        deleteButton.setTooltip(list != null && !NameLibrary.isCustom(list)
          ? net.minecraft.client.gui.components.Tooltip.create(Component.translatable("mcvanillatweaks.names.delete.built_in")) : null);
    }

    // ---- Actions ----

    private void edit()
    {
        final NameList list = selected();
        if (list != null)
        {
            minecraft.setScreen(new NameListEditScreen(this, list.id(), false));
        }
    }

    private void create()
    {
        final NameList list = NameLibrary.create(uniqueName(Component.translatable("mcvanillatweaks.names.new.name").getString()), null);
        if (list != null)
        {
            selectedId = list.id();
            minecraft.setScreen(new NameListEditScreen(this, list.id(), true));
        }
    }

    private void copy()
    {
        final NameList source = selected();
        if (source != null)
        {
            final NameList copy = copyOf(source);
            if (copy != null)
            {
                minecraft.setScreen(new NameListEditScreen(this, copy.id(), true));
            }
        }
    }

    /** An editable copy, which takes the original's place among the ticked lists. */
    @Nullable
    static NameList copyOf(final NameList source)
    {
        String name = uniqueName(Component.translatable("mcvanillatweaks.names.copy.name", NameLibrary.displayName(source)).getString());
        if (name.length() > NameList.MAX_LENGTH)
        {
            name = name.substring(0, NameList.MAX_LENGTH);
        }
        final boolean wasEnabled = NameLibrary.isEnabled(source);
        final NameList copy = NameLibrary.create(name, source);
        if (copy != null)
        {
            NameLibrary.setEnabled(source, false);
            NameLibrary.setEnabled(copy, wasEnabled);
            selectedId = copy.id();
        }
        return copy;
    }

    static String uniqueName(final String base)
    {
        String name = base;
        int number = 2;
        while (nameTaken(name))
        {
            name = base + " " + number++;
        }
        return name;
    }

    private static boolean nameTaken(final String name)
    {
        return NameLibrary.all().stream().anyMatch(list -> NameLibrary.displayName(list).equalsIgnoreCase(name));
    }

    private void delete()
    {
        final NameList list = selected();
        if (list == null || !NameLibrary.isCustom(list))
        {
            return;
        }
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed)
            {
                NameLibrary.delete(list);
                selectedId = null;
            }
            minecraft.setScreen(this);
        }, Component.translatable("mcvanillatweaks.names.delete.question", list.name()),
          Component.translatable("mcvanillatweaks.names.delete.warning"),
          Component.translatable("mcvanillatweaks.names.delete"), CommonComponents.GUI_CANCEL));
    }

    /** Two female and two male names from the ticked lists, in a toast ("…" where MineColonies would fill in). */
    private void showSample()
    {
        final List<NameList> enabled = NameLibrary.enabled();
        final Component body;
        if (enabled.isEmpty())
        {
            body = Component.translatable("mcvanillatweaks.names.sample.none");
        }
        else
        {
            final List<String> names = new ArrayList<>();
            final List<String> unknown = List.of("…");
            for (final boolean female : new boolean[] {true, false, true, false})
            {
                final String name = NameGenerator.newName(random, female, enabled, unknown, unknown, Set.copyOf(names));
                names.add(name == null ? "…" : name);
            }
            body = Component.literal(String.join(", ", names));
        }
        minecraft.getToasts().clear();
        minecraft.getToasts().addToast(SystemToast.multiline(minecraft, SAMPLE_TOAST, Component.translatable("mcvanillatweaks.names.sample.title"), body));
    }

    @Override
    public void onFilesDrop(final List<Path> paths)
    {
        imported(NameFiles.importFiles(paths));
    }

    /** Shows what an import did and selects the last new list. */
    private void imported(final NameFiles.Result result)
    {
        minecraft.getToasts().clear();
        final List<NameList> lists = result.imported();
        if (!lists.isEmpty())
        {
            final NameList last = lists.getLast();
            selectedId = last.id();
            COLLAPSED.remove(NameLibrary.MINE);
            if (lists.size() == 1)
            {
                toast(FILE_TOAST, Component.translatable("mcvanillatweaks.names.import.done", last.name()),
                  Component.translatable("mcvanillatweaks.names.counts", last.male().size(), last.female().size(), last.surnames().size()));
            }
            else
            {
                toast(FILE_TOAST, Component.translatable("mcvanillatweaks.names.import.done.many", lists.size()),
                  Component.literal(String.join(", ", lists.stream().map(NameList::name).toList())));
            }
        }
        if (!result.problems().isEmpty())
        {
            Component body = result.problems().getFirst();
            if (result.problems().size() > 1)
            {
                body = Component.translatable("mcvanillatweaks.names.import.more", body, result.problems().size() - 1);
            }
            toast(FILE_PROBLEM_TOAST, Component.translatable("mcvanillatweaks.names.import.failed"), body);
        }
        if (minecraft.screen == this)
        {
            init(minecraft, width, height);
        }
    }

    static void toast(final SystemToast.SystemToastId id, final Component title, final Component body)
    {
        final Minecraft minecraft = Minecraft.getInstance();
        minecraft.getToasts().addToast(SystemToast.multiline(minecraft, id, title, body));
    }

    // ---- Drawing ----

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 7, 0xFFFFFFFF);
        g.drawCenteredString(font, stateText(), width / 2, 46, SOFT);
    }

    static Component stateText()
    {
        if (NameLibrary.enabled().isEmpty())
        {
            return Component.translatable("mcvanillatweaks.names.state.none");
        }
        final NameSync.State state = NameSync.state();
        final Component text = Component.translatable("mcvanillatweaks.names.state." + state.name().toLowerCase(Locale.ROOT));
        return state == NameSync.State.UNSUPPORTED ? text.copy().withStyle(ChatFormatting.GOLD) : text;
    }

    @Override
    public void onClose()
    {
        NameSync.sendIfChanged();
        minecraft.setScreen(parent);
    }

    /** For the preview: the list selected on opening. */
    static void preselect(final String id)
    {
        selectedId = id;
    }

    /** For the preview: the search typed on opening. */
    static void presearch(final String text)
    {
        query = text;
    }

    // ---- The list ----

    /** Lower case without accents, for the search. */
    static String fold(final String text)
    {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /**
     * The lists grouped under their category's header (a click opens or closes it; all open while searching). The
     * search keeps the lists whose name, or one of whose names, contains the text.
     */
    private final class Lists extends ObjectSelectionList<ListEntry>
    {
        private Lists(final Minecraft minecraft, final int width, final int height, final int y)
        {
            super(minecraft, width, height, y, ROW_HEIGHT);
        }

        /** Selects the remembered list, else the first one, and scrolls to it. */
        private void selectInitial()
        {
            if (getSelected() == null)
            {
                children().stream().filter(Row.class::isInstance).findFirst().ifPresent(this::setSelected);
            }
            if (getSelected() != null)
            {
                centerScrollOn(getSelected());
            }
        }

        private void rebuild()
        {
            final String folded = fold(query.strip());
            clearEntries();
            ListEntry toSelect = null;
            final List<NameList> all = NameLibrary.all();
            for (final NameLibrary.Category category : NameLibrary.categories())
            {
                final List<Row> rows = new ArrayList<>();
                int ticked = 0;
                for (final NameList list : all)
                {
                    if (!NameLibrary.categoryOf(list).equals(category.id()))
                    {
                        continue;
                    }
                    String match = null;
                    if (!folded.isEmpty() && !fold(NameLibrary.displayName(list)).contains(folded))
                    {
                        match = firstMatch(list, folded);
                        if (match == null)
                        {
                            continue;
                        }
                    }
                    rows.add(new Row(list.id(), match));
                    ticked += NameLibrary.isEnabled(list) ? 1 : 0;
                }
                // The player's lists keep their header when empty: it says how to make one.
                if (rows.isEmpty() && !(category.id().equals(NameLibrary.MINE) && folded.isEmpty()))
                {
                    continue;
                }
                addEntry(new Section(category, rows.size(), ticked));
                if (!folded.isEmpty() || !COLLAPSED.contains(category.id()))
                {
                    for (final Row row : rows)
                    {
                        addEntry(row);
                        if (row.id.equals(selectedId))
                        {
                            toSelect = row;
                        }
                    }
                }
            }
            super.setSelected(toSelect);
            clampScrollAmount();
            updateButtons();
        }

        @Nullable
        private String firstMatch(final NameList list, final String folded)
        {
            for (final NameList.Column column : NameList.Column.values())
            {
                for (final String name : list.get(column))
                {
                    if (fold(name).contains(folded))
                    {
                        return name;
                    }
                }
            }
            return null;
        }

        @Override
        public int getRowWidth()
        {
            return ROW_WIDTH;
        }

        @Override
        public void setSelected(@Nullable final ListEntry entry)
        {
            super.setSelected(entry);
            if (entry instanceof Row row)
            {
                selectedId = row.id;
            }
            updateButtons();
        }

        @Override
        public void renderWidget(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
        {
            super.renderWidget(g, mouseX, mouseY, partialTick);
            if (children().isEmpty())
            {
                g.drawCenteredString(font, Component.translatable("mcvanillatweaks.names.search.none"), getX() + getWidth() / 2, getY() + 20, SOFT);
            }
        }
    }

    private abstract static class ListEntry extends ObjectSelectionList.Entry<ListEntry>
    {
    }

    /** A category header: its item, name, how many lists and how many ticked, an arrow saying whether it is open. */
    private final class Section extends ListEntry
    {
        private final NameLibrary.Category category;
        private final int count;
        private final int ticked;

        private Section(final NameLibrary.Category category, final int count, final int ticked)
        {
            this.category = category;
            this.count = count;
            this.ticked = ticked;
        }

        @Override
        public void render(final GuiGraphics g, final int index, final int top, final int left, final int width, final int height,
          final int mouseX, final int mouseY, final boolean hovered, final float partialTick)
        {
            g.renderItem(category.icon(), left + 4, top + 9);
            g.drawString(font, Component.literal(category.displayName()).withStyle(ChatFormatting.BOLD), left + 26, top + 7, 0xFFFFFFFF, false);
            final Component detail = count == 0
              ? Component.translatable("mcvanillatweaks.names.category.mine.empty")
              : Component.translatable("mcvanillatweaks.names.category.count", count, ticked);
            g.drawString(font, font.plainSubstrByWidth(detail.getString(), width - 46), left + 26, top + 19, GREY, false);
            if (query.isBlank() && count > 0)
            {
                g.drawString(font, COLLAPSED.contains(category.id()) ? "▶" : "▼", left + width - 14, top + 13, hovered ? 0xFFFFFFFF : SOFT, false);
            }
        }

        @Override
        public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
        {
            if (query.isBlank() && count > 0)
            {
                if (!COLLAPSED.remove(category.id()))
                {
                    COLLAPSED.add(category.id());
                }
                clickSound();
                lists.rebuild();
            }
            return true;
        }

        @Override
        public Component getNarration()
        {
            return Component.literal(category.displayName());
        }
    }

    private final class Row extends ListEntry
    {
        private final String id;
        /** The name that made the search keep this list, when its own name does not match. */
        @Nullable
        private final String match;
        private long lastClick;

        private Row(final String id, @Nullable final String match)
        {
            this.id = id;
            this.match = match;
        }

        @Override
        public void render(final GuiGraphics g, final int index, final int top, final int left, final int width, final int height,
          final int mouseX, final int mouseY, final boolean hovered, final float partialTick)
        {
            final NameList list = NameLibrary.get(id);
            if (list == null)
            {
                return;
            }
            final boolean enabled = NameLibrary.isEnabled(list);
            final int boxX = left + 14;
            final boolean overBox = hovered && mouseX >= boxX - 2 && mouseX < boxX + 19;
            final ResourceLocation box = ResourceLocation.withDefaultNamespace("widget/checkbox" + (enabled ? "_selected" : "") + (overBox ? "_highlighted" : ""));
            g.blitSprite(box, boxX, top + 7, 17, 17);
            g.renderItem(NameLibrary.icon(list), boxX + 22, top + 8);
            final int textX = boxX + 44;
            final int textWidth = left + width - 4 - textX;
            g.drawString(font, font.plainSubstrByWidth(NameLibrary.displayName(list), textWidth), textX, top + 1, 0xFFFFFFFF, false);
            final Component second = match != null
              ? Component.translatable("mcvanillatweaks.names.search.contains", match)
              : Component.translatable(NameLibrary.isCustom(list) ? "mcvanillatweaks.names.custom" : "mcvanillatweaks.names.built_in");
            g.drawString(font, font.plainSubstrByWidth(second.getString(), textWidth), textX, top + 12, match != null ? 0xFFFFFF55 : GREY, false);
            final Component counts = Component.translatable("mcvanillatweaks.names.counts", list.male().size(), list.female().size(), list.surnames().size());
            g.drawString(font, font.plainSubstrByWidth(counts.getString(), textWidth), textX, top + 23, GREY, false);
            if (overBox)
            {
                setTooltipForNextRenderPass(List.of(
                  Component.translatable(enabled ? "mcvanillatweaks.names.ticked" : "mcvanillatweaks.names.unticked").getVisualOrderText(),
                  Component.translatable(enabled ? "mcvanillatweaks.names.untick" : "mcvanillatweaks.names.tick").withStyle(ChatFormatting.GRAY).getVisualOrderText()));
            }
        }

        @Override
        public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
        {
            final NameList list = NameLibrary.get(id);
            if (list == null)
            {
                return false;
            }
            lists.setSelected(this);
            final double x = mouseX - lists.getRowLeft();
            if (x >= 12 && x < 33)
            {
                NameLibrary.setEnabled(list, !NameLibrary.isEnabled(list));
                clickSound();
                lists.rebuild();
                return true;
            }
            if (Util.getMillis() - lastClick < 250L)
            {
                edit();
                return true;
            }
            lastClick = Util.getMillis();
            return true;
        }

        @Override
        public Component getNarration()
        {
            final NameList list = NameLibrary.get(id);
            return Component.translatable("narrator.select", list == null ? "" : NameLibrary.displayName(list));
        }
    }

    private static void clickSound()
    {
        Minecraft.getInstance().getSoundManager().play(
          net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
