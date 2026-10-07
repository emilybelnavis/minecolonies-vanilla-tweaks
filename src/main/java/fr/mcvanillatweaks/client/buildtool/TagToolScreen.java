package fr.mcvanillatweaks.client.buildtool;

import com.ldtteam.blockui.controls.TextField;
import com.ldtteam.structurize.blockentities.interfaces.IBlueprintDataProviderBE;
import com.ldtteam.structurize.client.gui.WindowTagTool;
import com.ldtteam.structurize.network.messages.AddRemoveTagMessage;
import com.ldtteam.structurize.util.BlockUtils;
import fr.mcvanillatweaks.client.citizen.Gui;
import fr.mcvanillatweaks.client.citizen.ScrollArea;
import fr.mcvanillatweaks.client.citizen.Tooltip;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Replaces Structurize's tag tool window ({@code WindowTagTool}): the tag the tool applies (typed, or picked among the
 * registered tags, with their descriptions), and the tags already set in the anchor's schematic, each removable (its last
 * tag, like Structurize, with {@code AddRemoveTagMessage}). Closing writes the tag back into the replaced window's field
 * and closes it, which stores it in the tool ({@code SetTagInTool}).
 * <pre>
 * Tag to apply: [groundlevel      ]
 * Registered tags          Tags in the schematic
 * | groundlevel [Use] |    | [log] [groundlevel]  x |
 * </pre>
 */
public final class TagToolScreen extends Screen
{
    private static final int WIDTH = 340;
    private static final int HEIGHT = 220;
    private static final int ROW = 20;

    private final WindowTagTool window;
    private final List<String> options;
    private final Level world;
    @Nullable
    private final BlockPos anchor;
    private final ScrollArea optionList = new ScrollArea();
    private final ScrollArea tagList = new ScrollArea();
    private final Tooltip tooltip = new Tooltip();
    private String current;
    private final ItemStack tool;
    private EditBox field;
    private int left;
    private int top;

    @SuppressWarnings("unchecked")
    public TagToolScreen(final WindowTagTool window) throws ReflectiveOperationException
    {
        super(Component.translatable("com.ldtteam.structurize.gui.tagtool.currenttag"));
        this.window = window;
        this.options = (List<String>) field("tagOptions");
        this.world = (Level) field("world");
        this.anchor = (BlockPos) field("anchorPos");
        this.current = field("currentTag") instanceof String tag ? tag : "";
        this.tool = field("stack") instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        // The replaced window fills its panes and registers its buttons when it opens.
        window.onOpened();
    }

    private Object field(final String name) throws ReflectiveOperationException
    {
        final Field field = WindowTagTool.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(window);
    }

    @Nullable
    private IBlueprintDataProviderBE data()
    {
        return anchor != null && world.getBlockEntity(anchor) instanceof IBlueprintDataProviderBE provider ? provider : null;
    }

    @Override
    protected void init()
    {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        final int labelWidth = font.width(getTitle()) + 6;
        field = addRenderableWidget(new EditBox(font, left + 8 + labelWidth, top + 26, WIDTH - 16 - labelWidth, 18, getTitle()));
        field.setMaxLength(100);
        field.setValue(current);
        field.setResponder(text -> current = text);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> close()).bounds(left + 8, top + HEIGHT - 28, WIDTH - 16, 20).build());
    }

    private void close()
    {
        final TextField source = window.findPaneOfTypeByID("currentTag", TextField.class);
        if (source != null)
        {
            source.setText(current);
        }
        window.close();
    }

    @Override
    public void onClose()
    {
        close();
    }

    private List<BlockPos> positions()
    {
        final IBlueprintDataProviderBE data = data();
        return data == null ? List.of() : new ArrayList<>(data.getPositionedTags().keySet());
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
        super.render(g, mouseX, mouseY, partialTick);
        tooltip.clear();
        if (!tool.isEmpty())
        {
            g.renderItem(tool, left + 8, top + 5);
            g.drawString(font, tool.getHoverName(), left + 28, top + 9, Gui.LABEL, false);
        }
        g.drawString(font, getTitle(), left + 8, top + 31, Gui.LABEL, false);
        final int half = (WIDTH - 16 - 8) / 2;
        final int listTop = top + 64;
        final int listHeight = top + HEIGHT - 34 - listTop;
        g.drawString(font, Component.translatable("com.ldtteam.structurize.gui.tagoptions.title"), left + 9, listTop - 12, Gui.LABEL, false);
        g.drawString(font, Component.translatable("com.ldtteam.structurize.gui.taglist.title"), left + 8 + half + 9, listTop - 12, Gui.LABEL, false);

        optionList.set(left + 8, listTop, half, listHeight, options.size() * ROW, ROW);
        optionList.begin(g);
        for (int i = 0; i < options.size(); i++)
        {
            final int rowY = optionList.listY() + i * ROW;
            if (rowY + ROW < optionList.visibleTop() || rowY > optionList.visibleBottom())
            {
                continue;
            }
            final String tag = options.get(i);
            final boolean hovered = optionList.hover(g, mouseX, mouseY, rowY, ROW);
            final boolean chosen = tag.equals(current);
            g.drawString(font, Gui.fit(font, Component.literal(tag), optionList.listWidth() - 8), optionList.listX() + 4, rowY + 6,
              chosen ? Gui.GREEN : Gui.WHITE, true);
            if (hovered)
            {
                tooltip.set(List.of(Component.literal(tag), Component.translatable("com.ldtteam.tag.tooltip." + tag).withStyle(ChatFormatting.GRAY),
                  Component.translatable(chosen ? "mcvanillatweaks.tagtool.chosen" : "mcvanillatweaks.tagtool.pick").withStyle(ChatFormatting.YELLOW)));
            }
        }
        optionList.end(g);

        final List<BlockPos> positions = positions();
        final IBlueprintDataProviderBE data = data();
        tagList.set(left + 8 + half + 8, listTop, half, listHeight, positions.size() * ROW, ROW);
        tagList.begin(g);
        if (data == null)
        {
            int ty = tagList.visibleTop() + 6;
            for (final var line : font.split(Component.translatable("com.ldtteam.structurize.gui.tagtool.noanchor"), tagList.listWidth() - 8))
            {
                g.drawString(font, line, tagList.listX() + 4, ty, Gui.LIST_SOFT, true);
                ty += 10;
            }
        }
        for (int i = 0; data != null && i < positions.size(); i++)
        {
            final int rowY = tagList.listY() + i * ROW;
            if (rowY + ROW < tagList.visibleTop() || rowY > tagList.visibleBottom())
            {
                continue;
            }
            final BlockPos pos = positions.get(i);
            final boolean hovered = tagList.hover(g, mouseX, mouseY, rowY, ROW);
            final ItemStack icon = BlockUtils.getItemStackFromBlockState(world.getBlockState(data.getRealWorldPos(pos)));
            g.renderItem(icon.isEmpty() ? new ItemStack(Items.BARRIER) : icon, tagList.listX() + 2, rowY + 2);
            final List<String> tags = data.getPositionedTags().getOrDefault(pos, List.of());
            final int removeX = tagList.listX() + tagList.listWidth() - 14;
            g.drawString(font, Gui.fit(font, Component.literal(String.join(", ", tags)), removeX - 6 - (tagList.listX() + 22)), tagList.listX() + 22, rowY + 6,
              Gui.WHITE, true);
            final boolean overRemove = Gui.inside(mouseX, mouseY, removeX - 2, rowY + 4, 12, 12);
            g.drawString(font, "✖", removeX, rowY + 6, overRemove ? Gui.RED : Gui.LIST_SOFT, true);
            if (overRemove && !tags.isEmpty())
            {
                tooltip.set(Component.translatable("mcvanillatweaks.tagtool.remove", tags.get(tags.size() - 1)));
            }
            else if (hovered)
            {
                tooltip.set(List.of(Component.literal(String.join(", ", tags)), Component.literal(data.getRealWorldPos(pos).toShortString())
                  .withStyle(ChatFormatting.GRAY)));
            }
        }
        tagList.end(g);
        tooltip.render(g, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (optionList.mouseClicked(mouseX, mouseY) || tagList.mouseClicked(mouseX, mouseY))
        {
            return true;
        }
        if (button == 0 && optionList.overRows(mouseX, mouseY))
        {
            final int index = (int) ((mouseY - optionList.listY()) / ROW);
            if (index >= 0 && index < options.size())
            {
                Gui.clickSound();
                current = options.get(index);
                field.setValue(current);
                return true;
            }
        }
        final IBlueprintDataProviderBE data = data();
        if (button == 0 && data != null && tagList.overRows(mouseX, mouseY))
        {
            final List<BlockPos> positions = positions();
            final int index = (int) ((mouseY - tagList.listY()) / ROW);
            final int removeX = tagList.listX() + tagList.listWidth() - 14;
            if (index >= 0 && index < positions.size() && Gui.inside(mouseX, mouseY, removeX - 2, tagList.listY() + index * ROW + 4, 12, 12))
            {
                final BlockPos pos = positions.get(index);
                final Map<BlockPos, List<String>> map = data.getPositionedTags();
                if (map.containsKey(pos) && !map.get(pos).isEmpty())
                {
                    Gui.clickSound();
                    final String tag = map.get(pos).get(map.get(pos).size() - 1);
                    data.removeTag(pos, tag);
                    new AddRemoveTagMessage(false, tag, pos, anchor).sendToServer();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY)
    {
        return optionList.mouseDragged(mouseY) || tagList.mouseDragged(mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button)
    {
        optionList.mouseReleased();
        tagList.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY)
    {
        return optionList.mouseScrolled(mouseX, mouseY, scrollY) || tagList.mouseScrolled(mouseX, mouseY, scrollY)
                 || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }
}
