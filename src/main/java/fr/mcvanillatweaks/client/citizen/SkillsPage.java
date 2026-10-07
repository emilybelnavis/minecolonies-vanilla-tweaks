package fr.mcvanillatweaks.client.citizen;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.network.messages.server.colony.citizen.AdjustSkillCitizenMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Skills" tab: the eleven skills of MineColonies' main page, each with its level as a bar and the share of job
 * experience it receives (what MineColonies' separate job page listed). A skill can get two shares (say 10% as the
 * primary skill's complement and -5% as the secondary's adverse): MineColonies adds and removes them independently, so
 * the row shows their sum and the tooltip the detail. In creative mode, the same +/- buttons as MineColonies replace
 * the bar.
 */
final class SkillsPage extends PageView
{
    private static final int ROW = 12;
    private static final int BUTTON = 10;
    private static final int NAME_WIDTH = 68;
    /** Right edge of the level number. */
    private static final int LEVEL_RIGHT = 98;
    private static final int BAR_X = 102;
    private static final int BAR_WIDTH = 28;

    private CitizenInfo.JobSkills jobSkills;
    private Map<Skill, List<Integer>> shares = Map.of();
    private int ticks;

    SkillsPage(final CitizenScreen screen)
    {
        super(screen);
        refresh();
    }

    @Override
    void tick()
    {
        if (++ticks % 20 == 0)
        {
            refresh();
        }
    }

    private void refresh()
    {
        jobSkills = CitizenInfo.jobSkills(citizen());
        shares = CitizenInfo.xpShares(jobSkills);
    }

    private static boolean creative()
    {
        final var player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && player.isCreative();
    }

    private int listTop()
    {
        return y;
    }

    private int minusX()
    {
        return x + BAR_X + 2;
    }

    private int plusX()
    {
        return minusX() + BUTTON + 1;
    }

    @Override
    void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final ICitizenDataView citizen = citizen();
        final Skill[] skills = Skill.values();
        Gui.inset(g, x, listTop(), width, skills.length * ROW + 4);
        final boolean creative = creative();

        for (int i = 0; i < skills.length; i++)
        {
            final Skill skill = skills[i];
            final int rowY = listTop() + 2 + i * ROW;
            final boolean hovered = Gui.inside(mouseX, mouseY, x + 1, rowY, width - 2, ROW);
            final List<Integer> share = shares.getOrDefault(skill, List.of());
            if (!share.isEmpty())
            {
                g.fill(x + 1, rowY, x + width - 1, rowY + ROW, 0x18FFFFFF);
            }
            if (hovered)
            {
                g.fill(x + 1, rowY, x + width - 1, rowY + ROW, Gui.ROW_HOVER);
            }

            VanillaIcons.item(g, VanillaIcons.skill(skill), x + 2, rowY + 1, 10);
            final Component name = CitizenInfo.skillName(skill);
            g.drawString(font, Gui.fit(font, name, NAME_WIDTH), x + 15, rowY + 2, Gui.WHITE, true);
            final int levelValue = CitizenInfo.skillLevel(citizen, skill);
            final String level = Integer.toString(levelValue);
            g.drawString(font, level, x + LEVEL_RIGHT - font.width(level), rowY + 2, Gui.WHITE, true);

            if (creative)
            {
                Gui.textButton(g, font, minusX(), rowY + 1, BUTTON, BUTTON, "-", true, Gui.inside(mouseX, mouseY, minusX(), rowY + 1, BUTTON, BUTTON));
                Gui.textButton(g, font, plusX(), rowY + 1, BUTTON, BUTTON, "+", true, Gui.inside(mouseX, mouseY, plusX(), rowY + 1, BUTTON, BUTTON));
            }
            else
            {
                Gui.bar(g, x + BAR_X, rowY + 3, BAR_WIDTH, (float) levelValue / CitizenInfo.MAX_SKILL_LEVEL);
            }

            if (!share.isEmpty())
            {
                final Component tag = tag(share);
                g.drawString(font, tag, x + width - 4 - font.width(tag), rowY + 2, Gui.WHITE, true);
            }

            if (hovered)
            {
                tooltip.set(tooltip(skill, level, share));
            }
        }

        final Component legend = Component.translatable(jobSkills == null ? "mcvanillatweaks.citizen.no_job_skills" : "mcvanillatweaks.citizen.skills_legend");
        g.drawString(font, Gui.fit(font, legend, width - 2), x + 1, listTop() + skills.length * ROW + 10, Gui.LABEL, false);
    }

    private static int total(final List<Integer> share)
    {
        return share.stream().mapToInt(Integer::intValue).sum();
    }

    /** "100%", "50%", "10%", "-10%"... coloured like their importance. */
    private static MutableComponent tag(final List<Integer> share)
    {
        final int percent = total(share);
        final ChatFormatting colour = percent >= 100 ? ChatFormatting.GOLD
                                      : percent >= 50 ? ChatFormatting.YELLOW
                                      : percent > 0 ? ChatFormatting.GREEN
                                      : percent < 0 ? ChatFormatting.RED
                                      : ChatFormatting.GRAY;
        return Component.literal(percent + "%").withStyle(colour);
    }

    private static List<Component> tooltip(final Skill skill, final String level, final List<Integer> share)
    {
        final List<Component> lines = new ArrayList<>();
        lines.add(CitizenInfo.skillName(skill));
        lines.add(Component.translatable("mcvanillatweaks.citizen.skill_level", level, CitizenInfo.MAX_SKILL_LEVEL).withStyle(ChatFormatting.GRAY));
        for (final int percent : share)
        {
            final String key = switch (percent)
            {
                case 100 -> "mcvanillatweaks.citizen.primary";
                case 50 -> "mcvanillatweaks.citizen.secondary";
                default -> percent > 0 ? "mcvanillatweaks.citizen.complementary" : "mcvanillatweaks.citizen.adverse";
            };
            lines.add(Component.translatable(key).append(" : ").append(Component.translatable("mcvanillatweaks.citizen.xp_share", percent + "%"))
              .withStyle(percent > 0 ? ChatFormatting.GRAY : ChatFormatting.RED));
        }
        if (share.size() > 1)
        {
            lines.add(Component.translatable("mcvanillatweaks.citizen.xp_share_total", total(share) + "%").withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    @Override
    boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        if (button != 0 || !creative())
        {
            return false;
        }
        final Skill[] skills = Skill.values();
        for (int i = 0; i < skills.length; i++)
        {
            final int rowY = listTop() + 2 + i * ROW;
            final int delta = Gui.inside(mouseX, mouseY, minusX(), rowY + 1, BUTTON, BUTTON) ? -1
                                : Gui.inside(mouseX, mouseY, plusX(), rowY + 1, BUTTON, BUTTON) ? 1 : 0;
            if (delta != 0)
            {
                Gui.clickSound();
                final ICitizenDataView citizen = citizen();
                // Same message and button id as MineColonies' main citizen page.
                new AdjustSkillCitizenMessage(citizen.getColony(), citizen, delta, skills[i]).sendToServer();
                CitizenScreens.trigger((delta > 0 ? "plus_" : "minus_") + skills[i].name().toLowerCase(Locale.US),
                  screen.source() == null ? null : screen.source().getXmlResourceLocation());
                return true;
            }
        }
        return false;
    }
}
