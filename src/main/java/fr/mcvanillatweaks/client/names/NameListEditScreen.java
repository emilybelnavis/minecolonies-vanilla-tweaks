package fr.mcvanillatweaks.client.names;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.mcvanillatweaks.names.NameList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * One name list, laid out like the game's Create World screen: tabs at the top (the list itself, then its male first
 * names, female first names and surnames), Done at the bottom. The list tab names the list and ticks it; each name tab
 * has an add field (several names separated by commas), an add button, a remove button for the selected name, and the
 * names in a vanilla selection list. Lists that come with the mod are read-only: their list tab offers a copy. The
 * list tab also picks the list's icon ({@link IconPickerScreen}) and exports it to a .json file ({@link NameFiles}).
 */
public final class NameListEditScreen extends Screen
{
    private static final int SOFT = 0xFFA0A0A0;
    private static final int NAME_ROW = 16;
    private static final SystemToast.SystemToastId EXPORT_TOAST = new SystemToast.SystemToastId(6000L);

    private final NameListsScreen parent;
    private final String id;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final TabManager tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
    private final List<Tab> tabs = new ArrayList<>();
    private final Collator collator = Collator.getInstance();
    @Nullable
    private TabNavigationBar bar;
    /** Tab shown, kept when the window is resized (init runs again). */
    private int tabIndex;

    /** @param fresh a new list or copy: opens on the list tab with the name field focused. */
    public NameListEditScreen(final NameListsScreen parent, final String id, final boolean fresh)
    {
        super(Component.translatable("mcvanillatweaks.names.title"));
        this.parent = parent;
        this.id = id;
        this.tabIndex = fresh ? 0 : 1;
        collator.setStrength(Collator.PRIMARY);
    }

    @Nullable
    private NameList list()
    {
        return NameLibrary.get(id);
    }

    private boolean custom()
    {
        final NameList list = list();
        return list != null && NameLibrary.isCustom(list);
    }

    @Override
    protected void init()
    {
        final NameList list = list();
        if (list == null)
        {
            minecraft.setScreen(parent);
            return;
        }
        tabs.clear();
        tabs.add(new ListTab());
        for (final NameList.Column column : NameList.Column.values())
        {
            tabs.add(new ColumnTab(column));
        }
        bar = TabNavigationBar.builder(tabManager, width).addTabs(tabs.toArray(Tab[]::new)).build();
        addRenderableWidget(bar);

        final LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        if (!custom())
        {
            footer.addChild(Button.builder(Component.translatable("mcvanillatweaks.names.copy.edit"), b -> copy()).build());
        }
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).build());
        layout.visitWidgets(widget -> {
            widget.setTabOrderGroup(1);
            addRenderableWidget(widget);
        });
        bar.selectTab(tabIndex, false);
        repositionElements();
    }

    @Override
    protected void setInitialFocus()
    {
        if (tabManager.getCurrentTab() instanceof ListTab tab && tab.name != null)
        {
            setInitialFocus(tab.name);
        }
    }

    @Override
    public void repositionElements()
    {
        if (bar == null)
        {
            return;
        }
        bar.setWidth(width);
        bar.arrangeElements();
        final int top = bar.getRectangle().bottom();
        tabManager.setTabArea(new ScreenRectangle(0, top, width, height - layout.getFooterHeight() - top));
        layout.setHeaderHeight(top);
        layout.arrangeElements();
    }

    private void copy()
    {
        final NameList list = list();
        if (list != null)
        {
            final NameList copy = NameListsScreen.copyOf(list);
            if (copy != null)
            {
                minecraft.setScreen(new NameListEditScreen(parent, copy.id(), true));
            }
        }
    }

    @Override
    public void onClose()
    {
        final NameList list = list();
        if (list != null && NameLibrary.isCustom(list) && list.name().isBlank())
        {
            NameLibrary.update(list.renamed(list.id(), Component.translatable("mcvanillatweaks.names.new.name").getString()));
        }
        minecraft.setScreen(parent);
    }

    @Override
    public void tick()
    {
        final int index = tabs.indexOf(tabManager.getCurrentTab());
        if (index >= 0)
        {
            tabIndex = index;
        }
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers)
    {
        if (bar != null && bar.keyPressed(key))
        {
            return true;
        }
        if (tabManager.getCurrentTab() instanceof ColumnTab tab && tab.keyPressed(key))
        {
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        RenderSystem.enableBlend();
        g.blit(minecraft.level == null ? Screen.FOOTER_SEPARATOR : Screen.INWORLD_FOOTER_SEPARATOR, 0, height - layout.getFooterHeight() - 2,
          0.0F, 0.0F, width, 2, 32, 2);
        RenderSystem.disableBlend();
        if (tabManager.getCurrentTab() instanceof ColumnTab tab)
        {
            tab.renderMessage(g);
        }
    }

    @Override
    protected void renderMenuBackground(final GuiGraphics g)
    {
        g.blit(CreateWorldScreen.TAB_HEADER_BACKGROUND, 0, 0, 0.0F, 0.0F, width, layout.getHeaderHeight(), 16, 16);
        renderMenuBackground(g, 0, layout.getHeaderHeight(), width, height);
    }

    // ---- The list tab ----

    /** Name and use of the list (or, for a built-in one, its name and what to do to edit it). */
    private final class ListTab implements Tab
    {
        private final List<AbstractWidget> widgets = new ArrayList<>();
        @Nullable
        private EditBox name;

        private ListTab()
        {
            final NameList list = list();
            if (list == null)
            {
                return;
            }
            if (NameLibrary.isCustom(list))
            {
                widgets.add(label(Component.translatable("mcvanillatweaks.names.list_name"), SOFT));
                name = new EditBox(font, 0, 0, 208, 20, Component.translatable("mcvanillatweaks.names.list_name"));
                name.setMaxLength(NameList.MAX_LENGTH);
                name.setValue(list.name());
                name.setResponder(text -> {
                    final NameList current = list();
                    if (current != null && !text.equals(current.name()))
                    {
                        NameLibrary.update(current.renamed(current.id(), text));
                    }
                });
                widgets.add(name);
                final Item icon = NameLibrary.item(list.icon());
                widgets.add(new IconButton(NameLibrary.icon(list), Component.translatable(icon == null
                  ? "mcvanillatweaks.names.icon.button.default" : "mcvanillatweaks.names.icon.button", NameLibrary.icon(list).getHoverName()),
                  b -> minecraft.setScreen(new IconPickerScreen(NameListEditScreen.this, icon, item -> {
                      final NameList current = list();
                      if (current != null)
                      {
                          NameLibrary.update(current.withIcon(item == null ? "" : BuiltInRegistries.ITEM.getKey(item).toString()));
                      }
                  }))));
            }
            else
            {
                widgets.add(label(Component.literal(NameLibrary.displayName(list)), 0xFFFFFFFF));
                widgets.add(label(Component.translatable("mcvanillatweaks.names.built_in.hint"), SOFT));
            }
            widgets.add(Checkbox.builder(Component.translatable("mcvanillatweaks.names.use"), font)
              .selected(NameLibrary.isEnabled(list))
              .onValueChange((box, value) -> {
                  final NameList current = list();
                  if (current != null)
                  {
                      NameLibrary.setEnabled(current, value);
                  }
              })
              .build());
            widgets.add(label(Component.translatable("mcvanillatweaks.names.counts", list.male().size(), list.female().size(), list.surnames().size()), SOFT));
            widgets.add(Button.builder(Component.translatable("mcvanillatweaks.names.export"), b -> {
                  final NameList current = list();
                  if (current != null)
                  {
                      NameFiles.chooseAndExport(current, message -> {
                          minecraft.getToasts().clear();
                          NameListsScreen.toast(EXPORT_TOAST, Component.translatable("mcvanillatweaks.names.export.title"), message);
                      });
                  }
              })
              .size(208, 20)
              .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("mcvanillatweaks.names.export.tooltip")))
              .build());
        }

        private StringWidget label(final Component text, final int colour)
        {
            final StringWidget label = new StringWidget(text, font);
            label.setColor(colour);
            return label;
        }

        @Override
        public Component getTabTitle()
        {
            return Component.translatable("mcvanillatweaks.names.tab.list");
        }

        @Override
        public void visitChildren(final Consumer<AbstractWidget> consumer)
        {
            widgets.forEach(consumer);
        }

        @Override
        public void doLayout(final ScreenRectangle area)
        {
            final int centerX = area.left() + area.width() / 2;
            final int x = centerX - 104;
            int y = area.top() + 20;
            for (final AbstractWidget widget : widgets)
            {
                if (widget instanceof StringWidget label)
                {
                    // The field's label sits over the field, like the world name; the other lines are centred.
                    final boolean fieldLabel = name != null && widget == widgets.getFirst();
                    final int labelWidth = Math.min(font.width(label.getMessage()), area.width() - 16);
                    label.setWidth(labelWidth);
                    label.setPosition(fieldLabel ? x : centerX - labelWidth / 2, y);
                    y += fieldLabel ? 13 : 18;
                }
                else
                {
                    widget.setPosition(widget instanceof Checkbox ? centerX - widget.getWidth() / 2 : x, y);
                    y += widget.getHeight() + 10;
                }
            }
        }
    }

    /** A button with an item drawn at its left, its text centred in the rest. */
    private static final class IconButton extends Button
    {
        private final ItemStack icon;

        private IconButton(final ItemStack icon, final Component message, final OnPress onPress)
        {
            super(0, 0, 208, 20, message, onPress, DEFAULT_NARRATION);
            this.icon = icon;
        }

        @Override
        protected void renderWidget(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
        {
            super.renderWidget(g, mouseX, mouseY, partialTick);
            g.renderItem(icon, getX() + 3, getY() + 2);
        }

        @Override
        public void renderString(final GuiGraphics g, final net.minecraft.client.gui.Font font, final int colour)
        {
            renderScrollingString(g, font, 22, colour);
        }
    }

    // ---- The name tabs ----

    private final class ColumnTab implements Tab
    {
        private final NameList.Column column;
        private final Names names;
        @Nullable
        private EditBox field;
        @Nullable
        private Button addButton;
        @Nullable
        private Button removeButton;
        private Component message = Component.empty();
        private int messageColour = SOFT;
        private int messageY;
        private int centerX;

        private ColumnTab(final NameList.Column column)
        {
            this.column = column;
            this.names = new Names(minecraft);
            if (custom())
            {
                field = new EditBox(font, 0, 0, 196, 20, Component.translatable("mcvanillatweaks.names.add"));
                field.setMaxLength(200);
                field.setHint(Component.translatable("mcvanillatweaks.names.add.hint").withStyle(ChatFormatting.DARK_GRAY));
                addButton = Button.builder(Component.translatable("mcvanillatweaks.names.add.button"), b -> add())
                  .size(70, 20)
                  .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("mcvanillatweaks.names.add.tooltip")))
                  .build();
                removeButton = Button.builder(Component.translatable("mcvanillatweaks.names.remove.button"), b -> remove()).size(70, 20).build();
            }
            else
            {
                message = Component.translatable("mcvanillatweaks.names.built_in.hint");
            }
            names.refill(null);
            updateRemove();
        }

        @Override
        public Component getTabTitle()
        {
            return Component.translatable("mcvanillatweaks.names.tab." + column.key());
        }

        @Override
        public void visitChildren(final Consumer<AbstractWidget> consumer)
        {
            if (field != null)
            {
                consumer.accept(field);
                consumer.accept(addButton);
                consumer.accept(removeButton);
            }
            consumer.accept(names);
        }

        @Override
        public void doLayout(final ScreenRectangle area)
        {
            centerX = area.left() + area.width() / 2;
            int y = area.top() + 8;
            if (field != null)
            {
                final int fieldWidth = Math.max(80, Math.min(196, area.width() - 16 - 2 * 74));
                final int x = centerX - (fieldWidth + 2 * 74) / 2;
                field.setWidth(fieldWidth);
                field.setPosition(x, y);
                addButton.setPosition(x + fieldWidth + 4, y);
                removeButton.setPosition(x + fieldWidth + 78, y);
                y += 26;
            }
            messageY = y + 1;
            y += 14;
            names.updateSizeAndPosition(area.width(), area.bottom() - y, y);
        }

        private void renderMessage(final GuiGraphics g)
        {
            g.drawCenteredString(font, message, centerX, messageY, messageColour);
        }

        private void say(final Component text, final int colour)
        {
            message = text;
            messageColour = colour;
        }

        private boolean keyPressed(final int key)
        {
            if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && field != null && field.isFocused())
            {
                add();
                return true;
            }
            if (key == GLFW.GLFW_KEY_DELETE && names.isFocused() && removeButton != null && removeButton.active)
            {
                remove();
                return true;
            }
            return false;
        }

        private void updateRemove()
        {
            if (removeButton != null)
            {
                removeButton.active = names.getSelected() != null;
            }
        }

        /** Adds the typed names (several can be separated by commas or semicolons). */
        private void add()
        {
            final NameList list = list();
            if (list == null || field == null || !NameLibrary.isCustom(list))
            {
                return;
            }
            final List<String> all = new ArrayList<>(list.get(column));
            final List<String> added = new ArrayList<>();
            String duplicate = null;
            boolean full = false;
            for (final String raw : field.getValue().split("[,;]"))
            {
                final String name = NameList.clean(column, raw);
                if (name == null)
                {
                    continue;
                }
                if (all.stream().anyMatch(existing -> existing.equalsIgnoreCase(name)))
                {
                    duplicate = name;
                    continue;
                }
                if (all.size() >= NameList.MAX_NAMES)
                {
                    full = true;
                    break;
                }
                all.add(name);
                added.add(name);
            }
            if (!added.isEmpty())
            {
                NameLibrary.update(list.with(column, all));
                names.refill(added.getLast());
                say(added.size() == 1 ? Component.translatable("mcvanillatweaks.names.added.short", added.getFirst())
                  : Component.translatable("mcvanillatweaks.names.added.many.short", added.size()), 0xFF80FF80);
            }
            else if (full)
            {
                say(Component.translatable("mcvanillatweaks.names.full", NameList.MAX_NAMES), 0xFFFF8080);
            }
            else if (duplicate != null)
            {
                say(Component.translatable("mcvanillatweaks.names.duplicate", duplicate), 0xFFFF8080);
            }
            field.setValue("");
            updateRemove();
        }

        private void remove()
        {
            final NameList list = list();
            final Name row = names.getSelected();
            if (list == null || row == null || !NameLibrary.isCustom(list))
            {
                return;
            }
            final List<String> all = new ArrayList<>(list.get(column));
            if (all.remove(row.name))
            {
                final int index = names.children().indexOf(row);
                NameLibrary.update(list.with(column, all));
                names.refill(null);
                if (!names.children().isEmpty())
                {
                    names.setSelected(names.children().get(Math.min(index, names.children().size() - 1)));
                }
                say(Component.translatable("mcvanillatweaks.names.removed", row.name), SOFT);
            }
            updateRemove();
        }

        private final class Names extends ObjectSelectionList<Name>
        {
            private Names(final Minecraft minecraft)
            {
                super(minecraft, 0, 0, 0, NAME_ROW);
            }

            /** Reloads the names, sorted, selecting {@code select} when given. */
            private void refill(@Nullable final String select)
            {
                final NameList list = list();
                final List<String> sorted = new ArrayList<>(list == null ? List.of() : list.get(column));
                sorted.sort(collator);
                clearEntries();
                Name selected = null;
                for (final String name : sorted)
                {
                    final Name row = new Name(name);
                    addEntry(row);
                    if (name.equals(select))
                    {
                        selected = row;
                    }
                }
                setSelected(selected);
                if (selected != null)
                {
                    ensureVisible(selected);
                }
            }

            @Override
            public void setSelected(@Nullable final Name row)
            {
                super.setSelected(row);
                updateRemove();
            }

            @Override
            public int getRowWidth()
            {
                return 220;
            }

            @Override
            public void renderWidget(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
            {
                super.renderWidget(g, mouseX, mouseY, partialTick);
                if (children().isEmpty())
                {
                    g.drawCenteredString(font, Component.translatable("mcvanillatweaks.names.empty"), getX() + getWidth() / 2, getY() + 10, SOFT);
                }
            }
        }

        private final class Name extends ObjectSelectionList.Entry<Name>
        {
            private final String name;

            private Name(final String name)
            {
                this.name = name;
            }

            @Override
            public void render(final GuiGraphics g, final int index, final int top, final int left, final int width, final int height,
              final int mouseX, final int mouseY, final boolean hovered, final float partialTick)
            {
                g.drawString(font, font.plainSubstrByWidth(name, width - 8), left + 4, top + (height - 8) / 2, 0xFFFFFFFF, false);
            }

            @Override
            public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
            {
                names.setSelected(this);
                return true;
            }

            @Override
            public Component getNarration()
            {
                return Component.translatable("narrator.select", name);
            }
        }
    }
}
