package fr.mcvanillatweaks.client.citizen;

import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * "Overview" tab: what the citizen does and where (job, workplace, home, current activity, open requests), a verdict on
 * whether it works ({@link CitizenDiagnosis}, explained on hover) and the two skills that matter for the job.
 */
final class OverviewPage extends PageView
{
    private static final int ROW = 14;
    /** Text inside a row. */
    private static final int TEXT_Y = 3;
    private static final int INFO_ROWS = 6;
    private static final int REQUESTS_ROW = 5;

    OverviewPage(final CitizenScreen screen)
    {
        super(screen);
    }

    private int infoTop()
    {
        return y;
    }

    private int skillsTop()
    {
        return infoTop() + INFO_ROWS * ROW + 4 + 18;
    }

    @Override
    void render(final GuiGraphics g, final int mouseX, final int mouseY, final Tooltip tooltip)
    {
        final ICitizenDataView citizen = citizen();
        final int requestCount = screen.frame().requestCount();
        final int top = infoTop();
        Gui.inset(g, x, top, width, INFO_ROWS * ROW + 4);

        row(g, 0, Component.translatable("mcvanillatweaks.citizen.row.job"), CitizenInfo.jobName(citizen), null, mouseX, mouseY, tooltip);
        row(g, 1, Component.translatable("mcvanillatweaks.citizen.row.work"),
          CitizenInfo.buildingName(citizen, citizen.getWorkBuilding(), "mcvanillatweaks.citizen.no_work"), null, mouseX, mouseY, tooltip);
        row(g, 2, Component.translatable("mcvanillatweaks.citizen.row.home"),
          CitizenInfo.buildingName(citizen, citizen.getHomeBuilding(), "mcvanillatweaks.citizen.no_home"), null, mouseX, mouseY, tooltip);
        final VisibleCitizenStatus status = citizen.getVisibleStatus();
        row(g, 3, Component.translatable("mcvanillatweaks.citizen.row.activity"),
          status == null ? Component.translatable("mcvanillatweaks.citizen.activity.none") : Component.translatable(status.getTranslationKey()),
          VanillaIcons.status(status), mouseX, mouseY, tooltip);
        // Verdict: the worst reason the citizen does not work (or works slowly), every reason explained on hover.
        final List<CitizenDiagnosis.Reason> reasons = CitizenDiagnosis.of(citizen);
        final CitizenDiagnosis.Reason main = CitizenDiagnosis.main(reasons);
        final net.minecraft.network.chat.MutableComponent verdict = main.text().copy().withStyle(main.level().colour());
        if (reasons.size() > 1)
        {
            verdict.append(Component.literal(" (+" + (reasons.size() - 1) + ")").withStyle(ChatFormatting.GRAY));
        }
        row(g, 4, Component.translatable("mcvanillatweaks.citizen.row.diagnosis"), verdict, null, mouseX, mouseY, null);
        final int diagnosisY = top + 2 + 4 * ROW;
        if (Gui.inside(mouseX, mouseY, x + 1, diagnosisY, width - 2, ROW))
        {
            g.fill(x + 1, diagnosisY, x + width - 1, diagnosisY + ROW, Gui.ROW_HOVER);
            tooltip.set(CitizenDiagnosis.tooltip(reasons));
        }

        // Open requests: a link to the requests tab.
        final int requestsY = top + 2 + REQUESTS_ROW * ROW;
        final boolean hovered = Gui.inside(mouseX, mouseY, x + 1, requestsY, width - 2, ROW);
        if (hovered)
        {
            g.fill(x + 1, requestsY, x + width - 1, requestsY + ROW, Gui.ROW_HOVER);
            tooltip.set(Component.translatable("mcvanillatweaks.citizen.requests.open_tab"));
        }
        final Component count = requestCount <= 0
                                  ? Component.translatable("mcvanillatweaks.citizen.requests.none_short")
                                  : Component.translatable("mcvanillatweaks.citizen.requests.count", requestCount);
        row(g, REQUESTS_ROW, Component.translatable("mcvanillatweaks.citizen.row.requests"),
          count.copy().append(" »").withStyle(hovered ? ChatFormatting.YELLOW : ChatFormatting.WHITE), null, mouseX, mouseY, null);

        heading(g, Component.translatable("mcvanillatweaks.citizen.job_skills"), skillsTop() - 11);
        Gui.inset(g, x, skillsTop(), width, 2 * ROW + 4);
        final CitizenInfo.JobSkills skills = CitizenInfo.jobSkills(citizen);
        if (skills == null)
        {
            final Component none = Component.translatable("mcvanillatweaks.citizen.no_job_skills");
            g.drawString(font, Gui.fit(font, none, width - 8), x + 4, skillsTop() + 2 + ROW - 4, Gui.LIST_SOFT, true);
        }
        else
        {
            skill(g, 0, skills.primary(), 100, mouseX, mouseY, tooltip);
            skill(g, 1, skills.secondary(), 50, mouseX, mouseY, tooltip);
        }
    }

    /** Label on the left, value on the right (cut with a tooltip when too long). */
    private void row(final GuiGraphics g, final int index, final Component label, final Component value,
      @Nullable final net.minecraft.world.item.ItemStack icon, final int mouseX, final int mouseY, @Nullable final Tooltip tooltip)
    {
        final int rowY = infoTop() + 2 + index * ROW;
        g.drawString(font, label, x + 4, rowY + TEXT_Y, Gui.LIST_SOFT, true);
        final int labelWidth = font.width(label) + 8;
        final int iconWidth = icon == null || icon.isEmpty() ? 0 : 12;
        final int room = width - 8 - labelWidth - iconWidth;
        final int valueWidth = Math.min(font.width(value), room);
        final int valueX = x + width - 4 - valueWidth;
        if (iconWidth > 0)
        {
            VanillaIcons.item(g, icon, valueX - iconWidth, rowY + TEXT_Y - 1, 10);
        }
        g.drawString(font, Gui.fit(font, value, room), valueX, rowY + TEXT_Y, Gui.WHITE, true);
        if (tooltip != null && Gui.isCut(font, value, room) && Gui.inside(mouseX, mouseY, x + 1, rowY, width - 2, ROW))
        {
            tooltip.set(List.of(label, value));
        }
    }

    private void skill(final GuiGraphics g, final int index, final Skill skill, final int share, final int mouseX, final int mouseY,
      final Tooltip tooltip)
    {
        final int rowY = skillsTop() + 2 + index * ROW;
        VanillaIcons.item(g, VanillaIcons.skill(skill), x + 3, rowY + TEXT_Y - 1, 10);
        final Component name = CitizenInfo.skillName(skill);
        g.drawString(font, Gui.fit(font, name, 80), x + 16, rowY + TEXT_Y, Gui.WHITE, true);
        final String level = Integer.toString(CitizenInfo.skillLevel(citizen(), skill));
        g.drawString(font, level, x + 118 - font.width(level), rowY + TEXT_Y, Gui.WHITE, true);
        final String tag = share + "%";
        g.drawString(font, tag, x + width - 4 - font.width(tag), rowY + TEXT_Y, share == 100 ? Gui.GOLD : Gui.YELLOW, true);
        if (Gui.inside(mouseX, mouseY, x + 1, rowY, width - 2, ROW))
        {
            tooltip.set(List.of(name,
              Component.translatable("mcvanillatweaks.citizen.skill_level", level, CitizenInfo.MAX_SKILL_LEVEL).withStyle(ChatFormatting.GRAY),
              Component.translatable(index == 0 ? "mcvanillatweaks.citizen.primary" : "mcvanillatweaks.citizen.secondary")
                .withStyle(ChatFormatting.GRAY),
              Component.translatable("mcvanillatweaks.citizen.xp_share", tag).withStyle(ChatFormatting.GRAY)));
        }
    }

    @Override
    boolean mouseClicked(final double mouseX, final double mouseY, final int button)
    {
        final int requestsY = infoTop() + 2 + REQUESTS_ROW * ROW;
        if (button == 0 && Gui.inside(mouseX, mouseY, x + 1, requestsY, width - 2, ROW))
        {
            Gui.clickSound();
            screen.open(CitizenPage.REQUESTS);
            return true;
        }
        return false;
    }
}
