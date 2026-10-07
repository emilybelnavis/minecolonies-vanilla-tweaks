package fr.mcvanillatweaks.client.citizen;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.IVisitorViewData;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.interactionhandling.IInteractionResponseHandler;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.client.gui.citizen.MainWindowCitizen;
import com.minecolonies.core.colony.interactionhandling.QuestDialogueInteraction;
import com.minecolonies.core.colony.interactionhandling.RecruitmentInteraction;
import com.minecolonies.core.colony.interactionhandling.RequestBasedInteraction;
import com.minecolonies.core.network.messages.server.colony.InteractionClose;
import fr.mcvanillatweaks.MineColoniesVanillaTweaks;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Replaces MineColonies' citizen dialogue window ({@code WindowInteraction}), opened by right clicking a citizen who has
 * something to say. It walks through the citizen's interactions like MineColonies: hidden ones are dropped, chit-chat
 * after the first one is skipped (except quest dialogues), each answer goes to the interaction's own client handler
 * (which sends MineColonies' response message, or opens another window), and closing the window sends
 * {@code InteractionClose} for the interaction on screen. MineColonies' window has the citizen's page as parent: closing
 * it, or running out of things to say, opens that page (a citizen with nothing to say goes straight to it, see
 * {@link #hasSomethingToSay}). What MineColonies' handlers add to their window is drawn here: the requested item for
 * requests, and a visitor's recruitment cost.
 * <pre>
 * +----------+  Alice Martin                   Important  1/2 [x]
 * | portrait |  Farmer
 * |          |  +-------------------------------------------+
 * +----------+  | [item] I need 16 Oak Logs to work...      |
 *               +-------------------------------------------+
 *  [  I'm working on it!  ] [  Tell me more!          ]
 * </pre>
 */
public final class InteractionScreen extends Screen
{
    private static final int WIDTH = 320;
    private static final int PORTRAIT_WIDTH = 50;
    private static final int PORTRAIT_HEIGHT = 72;
    private static final int TEXT_X = 66;
    private static final int TEXT_WIDTH = WIDTH - TEXT_X - 8;
    private static final int ANSWER_WIDTH = (WIDTH - 16 - 6) / 2;
    private static final int LINE = 10;
    private static final int CLOSE_SIZE = 14;
    /** Gold darkened for the light panel. */
    private static final int IMPORTANT = 0xB86E00;

    private final ICitizenDataView citizen;
    /** MineColonies' window, handed to the interaction handlers (they don't draw in it when an answer is clicked). */
    @Nullable
    private final BOWindow source;
    private final List<IInteractionResponseHandler> interactions;
    private final Tooltip tooltip = new Tooltip();
    private int current;
    private boolean setUp;
    private int left;
    private int top;
    private int panelHeight;

    /** Layout of the current interaction, rebuilt by {@link #setupInteraction}. */
    private List<Component> answers = List.of();
    private List<FormattedCharSequence> speech = List.of();
    private ItemStack requestItem = ItemStack.EMPTY;
    /** A visitor's recruitment cost, drawn on the recruit answer. */
    private ItemStack recruitCost = ItemStack.EMPTY;
    private int recruitAnswer = -1;

    public InteractionScreen(final ICitizenDataView citizen, @Nullable final BOWindow source)
    {
        super(Component.literal(citizen.getName()));
        this.citizen = citizen;
        this.source = source;
        this.interactions = new ArrayList<>(citizen.getOrderedInteractions());
    }

    // ------------------------------------------------------------------ MineColonies' logic

    private void setupInteraction()
    {
        while (true)
        {
            if (current >= interactions.size())
            {
                if (minecraft != null && minecraft.screen == this)
                {
                    onClose();
                }
                return;
            }
            final IInteractionResponseHandler handler = interactions.get(current);
            if (handler.getPriority().getPriority() <= ChatPriority.CHITCHAT.getPriority() && current > 0 && !(handler instanceof QuestDialogueInteraction))
            {
                current++;
                continue;
            }
            handler.onOpened(minecraft.player);
            prepare(handler);
            return;
        }
    }

    /** What MineColonies' window shows for this interaction, plus what its handler adds in {@code onWindowOpened}. */
    private void prepare(final IInteractionResponseHandler handler)
    {
        answers = List.copyOf(handler.getPossibleResponses());
        requestItem = ItemStack.EMPTY;
        recruitCost = ItemStack.EMPTY;
        recruitAnswer = -1;
        final List<Component> paragraphs = new ArrayList<>();
        paragraphs.add(handler.getInquiry(minecraft.player));
        if (handler instanceof RequestBasedInteraction)
        {
            requestItem = requestItem(handler);
        }
        if (handler instanceof RecruitmentInteraction && answers.size() > 1 && citizen instanceof IVisitorViewData visitor)
        {
            // Same text as MineColonies' recruitment window; the cost item goes on the second answer, which is "recruit".
            recruitCost = visitor.getRecruitCost();
            recruitAnswer = 1;
            final IColonyView colony = citizen.getColony();
            final boolean room = colony == null || colony.getCitizens().size() < colony.getCitizenCountLimit();
            paragraphs.add(Component.translatable(room ? "com.minecolonies.coremod.gui.chat.recruitcost" : "com.minecolonies.coremod.gui.chat.nospacerecruit",
              recruitCost.getCount() + " " + recruitCost.getHoverName().getString()).withStyle(room ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        final int width = TEXT_WIDTH - 12 - (requestItem.isEmpty() ? 0 : 22);
        final List<FormattedCharSequence> lines = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++)
        {
            if (i > 0)
            {
                lines.add(FormattedCharSequence.EMPTY);
            }
            lines.addAll(font.split(paragraphs.get(i), width));
        }
        speech = lines;
        rebuildWidgets();
    }

    /** The requested item, read from the request like MineColonies' handler (its token is private). */
    private ItemStack requestItem(final IInteractionResponseHandler handler)
    {
        try
        {
            final Field field = RequestBasedInteraction.class.getDeclaredField("token");
            field.setAccessible(true);
            final IColonyView colony = citizen.getColony();
            if (colony != null && field.get(handler) instanceof IToken<?> token)
            {
                final IRequest<?> request = colony.getRequestManager().getRequestForToken(token);
                if (request != null && !request.getDisplayStacks().isEmpty())
                {
                    return request.getDisplayStacks().get(0);
                }
            }
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            MineColoniesVanillaTweaks.LOGGER.debug("No request item for the interaction", e);
        }
        return ItemStack.EMPTY;
    }

    private void answer(final int index)
    {
        if (interactions.isEmpty() || current >= interactions.size())
        {
            return;
        }
        final IInteractionResponseHandler handler = interactions.get(current);
        try
        {
            if (handler.onClientResponseTriggered(index, minecraft.player, citizen, source))
            {
                current++;
            }
        }
        catch (final Exception e)
        {
            MineColoniesVanillaTweaks.LOGGER.warn("Wrong answer of interaction", e);
        }
        // The handler may have opened another window (a request, the citizen's page) and closed this one.
        if (minecraft != null && minecraft.screen == this)
        {
            setupInteraction();
        }
    }

    @Override
    public void removed()
    {
        // MineColonies' onClosed: the interaction on screen is closed for now.
        if (setUp && current < interactions.size())
        {
            try
            {
                final IInteractionResponseHandler handler = interactions.get(current);
                handler.onClosed();
                if (minecraft != null && minecraft.level != null)
                {
                    new InteractionClose(citizen.getColonyId(), citizen.getId(), minecraft.level.dimension(), handler.getInquiry()).sendToServer();
                }
            }
            catch (final LinkageError | RuntimeException e)
            {
                CitizenScreens.report(e);
            }
        }
        super.removed();
    }

    /** Whether MineColonies' dialogue window would show something, rather than closing to the citizen's page at once. */
    static boolean hasSomethingToSay(final ICitizenDataView citizen, @Nullable final Level level)
    {
        for (final IInteractionResponseHandler interaction : citizen.getOrderedInteractions())
        {
            if (interaction.isVisible(level))
            {
                return true;
            }
        }
        return false;
    }

    /** Closes the dialogue like MineColonies' window: its parent, the citizen's page, opens (and is swapped in turn). */
    @Override
    public void onClose()
    {
        super.onClose();
        try
        {
            if (citizen.getColony() != null)
            {
                new MainWindowCitizen(citizen).open();
            }
        }
        catch (final LinkageError | RuntimeException e)
        {
            CitizenScreens.report(e);
        }
    }

    // ------------------------------------------------------------------ layout

    private int answerHeight(final Component text)
    {
        return font.split(text, ANSWER_WIDTH - 8 - (text == answerText(recruitAnswer) ? 18 : 0)).size() > 1 ? 30 : 20;
    }

    @Nullable
    private Component answerText(final int index)
    {
        return index >= 0 && index < answers.size() ? answers.get(index) : null;
    }

    /** Answers two per row; a row is as tall as its tallest answer. */
    private int[] rowHeights()
    {
        final int[] rows = new int[(answers.size() + 1) / 2];
        for (int i = 0; i < answers.size(); i++)
        {
            rows[i / 2] = Math.max(rows[i / 2], answerHeight(answers.get(i)));
        }
        return rows;
    }

    private int speechHeight()
    {
        return Math.max(Math.max(PORTRAIT_HEIGHT - 26, 30), speech.size() * LINE + 10);
    }

    private int answersTop()
    {
        return top + Math.max(8 + PORTRAIT_HEIGHT, 30 + speechHeight()) + 8;
    }

    private int answerX(final int index)
    {
        // A lone answer on the last row is centred.
        if (index == answers.size() - 1 && index % 2 == 0)
        {
            return left + (WIDTH - ANSWER_WIDTH) / 2;
        }
        return left + 8 + (index % 2) * (ANSWER_WIDTH + 6);
    }

    private int answerY(final int index)
    {
        final int[] rows = rowHeights();
        int y = answersTop();
        for (int r = 0; r < index / 2; r++)
        {
            y += rows[r] + 4;
        }
        return y;
    }

    private int answerAt(final double mouseX, final double mouseY)
    {
        final int[] rows = rowHeights();
        for (int i = 0; i < answers.size(); i++)
        {
            if (Gui.inside(mouseX, mouseY, answerX(i), answerY(i), ANSWER_WIDTH, rows[i / 2]))
            {
                return i;
            }
        }
        return -1;
    }

    private int closeX()
    {
        return left + WIDTH - 6 - CLOSE_SIZE;
    }

    // ------------------------------------------------------------------ screen

    @Override
    protected void init()
    {
        if (!setUp)
        {
            setUp = true;
            try
            {
                interactions.removeIf(interaction -> !interaction.isVisible(minecraft.level));
                setupInteraction();
            }
            catch (final LinkageError | RuntimeException e)
            {
                fail(e);
            }
            return;
        }
        int total = 0;
        for (final int row : rowHeights())
        {
            total += row + 4;
        }
        left = (width - WIDTH) / 2;
        final int contentTop = Math.max(8 + PORTRAIT_HEIGHT, 30 + speechHeight()) + 8;
        panelHeight = contentTop + total + 4;
        top = (height - panelHeight) / 2;
    }

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        renderTransparentBackground(g);
        Gui.panel(g, left, top, WIDTH, panelHeight);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick)
    {
        super.render(g, mouseX, mouseY, partialTick);
        tooltip.clear();
        if (current >= interactions.size())
        {
            return;
        }
        try
        {
            renderPortrait(g, mouseX, mouseY);
            renderHead(g, mouseX, mouseY);
            renderSpeech(g, mouseX, mouseY);
            renderAnswers(g, mouseX, mouseY);
        }
        catch (final LinkageError | RuntimeException e)
        {
            fail(e);
            return;
        }
        tooltip.render(g, font, mouseX, mouseY);
    }

    private void renderPortrait(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        final int x = left + 8;
        final int y = top + 8;
        Gui.inset(g, x, y, PORTRAIT_WIDTH, PORTRAIT_HEIGHT, 0xFF000000);
        final LivingEntity entity = CitizenInfo.entity(citizen);
        if (entity == null)
        {
            final Component away = Component.translatable("mcvanillatweaks.citizen.away");
            g.drawString(font, away, x + (PORTRAIT_WIDTH - font.width(away)) / 2, y + 32, Gui.LIST_SOFT, true);
            return;
        }
        // Hide the name tag MineColonies' renderer would draw above the head.
        final boolean hideGui = minecraft.options.hideGui;
        minecraft.options.hideGui = true;
        try
        {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 1, y + 1, x + PORTRAIT_WIDTH - 1, y + PORTRAIT_HEIGHT - 1, 30, 0.0625F, mouseX, mouseY, entity);
        }
        finally
        {
            minecraft.options.hideGui = hideGui;
        }
    }

    private void renderHead(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        final int x = left + TEXT_X;
        // Close button, as MineColonies' cross.
        final boolean overClose = Gui.inside(mouseX, mouseY, closeX(), top + 6, CLOSE_SIZE, CLOSE_SIZE);
        Gui.textButton(g, font, closeX(), top + 6, CLOSE_SIZE, CLOSE_SIZE, "x", true, overClose);
        if (overClose)
        {
            tooltip.set(Component.translatable("mcvanillatweaks.interaction.close"));
        }

        // Priority and position in the queue, right-aligned before the cross.
        final IInteractionResponseHandler handler = interactions.get(current);
        int right = closeX() - 6;
        if (interactions.size() > 1)
        {
            final String count = (current + 1) + "/" + interactions.size();
            right -= font.width(count);
            g.drawString(font, count, right, top + 9, Gui.LABEL_SOFT, false);
            if (Gui.inside(mouseX, mouseY, right - 1, top + 7, font.width(count) + 2, 11))
            {
                tooltip.set(Component.translatable("mcvanillatweaks.interaction.queue", current + 1, interactions.size()));
            }
            right -= 6;
        }
        final Component priority = priority(handler);
        if (priority != null)
        {
            right -= font.width(priority);
            g.drawString(font, priority, right, top + 9, Gui.LABEL, false);
            right -= 6;
        }

        g.drawString(font, Gui.fit(font, Component.literal(citizen.getName()), right - x), x, top + 9, Gui.LABEL, false);
        final Component job = CitizenInfo.isEmployed(citizen) ? CitizenInfo.jobName(citizen) : Component.translatable("com.minecolonies.coremod.gui.townhall.citizens.unemployed");
        g.drawString(font, Gui.fit(font, job, TEXT_WIDTH), x, top + 20, Gui.LABEL_SOFT, false);
    }

    /** MineColonies marks blocking and important interactions over the citizen's head: say it in words here. */
    @Nullable
    private static Component priority(final IInteractionResponseHandler handler)
    {
        final int level = handler.getPriority().getPriority();
        if (level >= ChatPriority.BLOCKING.getPriority())
        {
            return Component.translatable("mcvanillatweaks.interaction.blocking").withStyle(ChatFormatting.DARK_RED);
        }
        if (level >= ChatPriority.IMPORTANT.getPriority())
        {
            return Component.translatable("mcvanillatweaks.interaction.important").withColor(IMPORTANT);
        }
        return null;
    }

    private void renderSpeech(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        final int x = left + TEXT_X;
        final int y = top + 32;
        final int h = speechHeight();
        Gui.inset(g, x, y, TEXT_WIDTH, h);
        int tx = x + 6;
        if (!requestItem.isEmpty())
        {
            Gui.slot(g, tx - 1, y + 4);
            g.renderItem(requestItem, tx, y + 5);
            g.renderItemDecorations(font, requestItem, tx, y + 5);
            if (Gui.inside(mouseX, mouseY, tx - 1, y + 4, 18, 18))
            {
                tooltip.set(requestItem);
            }
            tx += 22;
        }
        int ty = y + 6;
        for (final FormattedCharSequence line : speech)
        {
            g.drawString(font, line, tx, ty, Gui.WHITE, true);
            ty += LINE;
        }
    }

    private void renderAnswers(final GuiGraphics g, final int mouseX, final int mouseY)
    {
        final int[] rows = rowHeights();
        final int hovered = answerAt(mouseX, mouseY);
        for (int i = 0; i < answers.size(); i++)
        {
            final int bx = answerX(i);
            final int by = answerY(i);
            final int bh = rows[i / 2];
            Gui.button(g, bx, by, ANSWER_WIDTH, bh, true, i == hovered);
            final boolean cost = i == recruitAnswer && !recruitCost.isEmpty();
            final int textWidth = ANSWER_WIDTH - 8 - (cost ? 18 : 0);
            final List<FormattedCharSequence> lines = font.split(answers.get(i), textWidth);
            final int shown = Math.min(2, lines.size());
            int ty = by + (bh - shown * LINE) / 2 + 1;
            for (int l = 0; l < shown; l++)
            {
                final FormattedCharSequence line = lines.get(l);
                g.drawString(font, line, bx + 4 + (textWidth - font.width(line)) / 2, ty, i == hovered ? Gui.YELLOW : Gui.WHITE, true);
                ty += LINE;
            }
            if (cost)
            {
                g.renderItem(recruitCost, bx + ANSWER_WIDTH - 20, by + (bh - 16) / 2);
                g.renderItemDecorations(font, recruitCost, bx + ANSWER_WIDTH - 20, by + (bh - 16) / 2);
            }
            if (i == hovered && (lines.size() > 2 || cost))
            {
                final List<Component> tip = new ArrayList<>();
                tip.add(answers.get(i));
                if (cost)
                {
                    tip.add(Component.translatable("mcvanillatweaks.interaction.cost", recruitCost.getCount(), recruitCost.getHoverName()).withStyle(ChatFormatting.GRAY));
                }
                tooltip.set(tip);
            }
        }
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (button == 0)
        {
            if (Gui.inside(mouseX, mouseY, closeX(), top + 6, CLOSE_SIZE, CLOSE_SIZE))
            {
                Gui.clickSound();
                onClose();
                return true;
            }
            final int index = answerAt(mouseX, mouseY);
            if (index >= 0)
            {
                Gui.clickSound();
                answer(index);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    private void fail(final Throwable e)
    {
        CitizenScreens.report(e);
        if (minecraft != null && minecraft.screen == this)
        {
            super.onClose();
        }
    }
}
