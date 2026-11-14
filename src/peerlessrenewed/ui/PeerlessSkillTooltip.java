package peerlessrenewed.ui;

import java.awt.Color;

import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import peerlessrenewed.PeerlessData;
import peerlessrenewed.PeerlessSkill;
import peerlessrenewed.Settings;
import second_in_command.misc.VanillaSkillTooltip;

/**
 * Second-in-Command's skill tooltip with a Peerless section after Elite. Second-in-Command sets
 * {@code level} every frame (0, 1 or 2), as for its own tooltip.
 */
public class PeerlessSkillTooltip extends VanillaSkillTooltip {

    private final SkillSpecAPI spec;

    public PeerlessSkillTooltip(TooltipMakerAPI tooltip, PersonAPI person, SkillSpecAPI skillSpec, int requiredSkillPoints) {
        super(tooltip, person, skillSpec, requiredSkillPoints);
        this.spec = skillSpec;
    }

    public static VanillaSkillTooltip addToTooltip(TooltipMakerAPI tooltip, PersonAPI person, SkillSpecAPI skillSpec, int requiredSkillPoints) {
        PeerlessSkillTooltip element = new PeerlessSkillTooltip(tooltip, person, skillSpec, requiredSkillPoints);
        tooltip.addTooltipToPrevious(element, TooltipMakerAPI.TooltipLocation.BELOW, false);
        return element;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
        super.createTooltip(tooltip, expanded, tooltipParam);
        PeerlessSkill skill = spec == null ? null : PeerlessSkill.of(spec.getId());
        if (skill == null || !PeerlessData.hasCampaign()) return;

        boolean trained = PeerlessData.isTrained(skill.id);
        boolean pending = PeerlessUi.isPending(skill.id);
        boolean elite = level >= 2f;
        boolean lit = trained || pending;

        Color gold = PeerlessData.GOLD;
        Color label = lit ? gold : Misc.scaleAlpha(gold, 0.6f);
        Color text = lit ? Misc.getTextColor() : Misc.scaleAlpha(Misc.getTextColor(), 0.6f);

        tooltip.addSpacer(7f);
        tooltip.addPara("Peerless", label, 0f);
        tooltip.addPara(skill.effectText(Settings.originalEffects()), text, 3f);

        if (trained) {
            tooltip.addPara("Peerless. Click to undo it and get the point back.", Misc.getGrayColor(), 8f);
        } else if (pending) {
            tooltip.addPara("Becomes Peerless when you press Save.", gold, 8f);
        } else if (!elite) {
            tooltip.addPara("Needs Elite first.", Misc.getNegativeHighlightColor(), 8f);
        } else if (PeerlessUi.available() > 0) {
            tooltip.addPara("Click to make it Peerless: costs 1 Peerless skill point.", 8f, Misc.getTextColor(), gold, "1 Peerless skill point");
        } else {
            tooltip.addPara("Needs a Peerless skill point.", Misc.getNegativeHighlightColor(), 8f);
        }
        tooltip.addSpacer(3f);
    }
}
