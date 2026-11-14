package peerlessrenewed.effects;

import org.apache.log4j.Logger;
import org.json.JSONObject;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.CustomSkillDescription;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.ShipSkillEffect;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.loading.SkillSpec;

import peerlessrenewed.OfficerPeerless;
import peerlessrenewed.PeerlessData;
import peerlessrenewed.PeerlessSkill;
import peerlessrenewed.Settings;

/**
 * Adds a Peerless part to the game's own skill tooltips (the officer screen, the captain picker): one of
 * these is added at load to each of the fourteen combat skills, in their Elite group. It draws nothing
 * unless the character whose tooltip it is has that skill at Peerless. It changes no stats.
 *
 * Second-in-Command builds its tooltips from the .skill files, not from the loaded skills, so its Character
 * screen does not show this part twice ({@code PeerlessSkillTooltip} covers it there).
 */
public class PeerlessTooltipEffect implements ShipSkillEffect, CustomSkillDescription {

    private static final Logger log = Global.getLogger(PeerlessTooltipEffect.class);

    /** Called once at application load. */
    public static void addToSkills() {
        int added = 0;
        for (PeerlessSkill skill : PeerlessSkill.values()) {
            try {
                SkillSpecAPI api = Global.getSettings().getSkillSpec(skill.id);
                if (!(api instanceof SkillSpec)) continue;
                SkillSpec spec = (SkillSpec) api;
                boolean present = false;
                for (SkillSpec.SkillEffectSpec effect : spec.getEffects()) {
                    if (PeerlessTooltipEffect.class.getName().equals(effect.getEffectClass())) present = true;
                }
                if (present) continue;
                JSONObject json = new JSONObject();
                json.put("type", "SHIP");
                json.put("script", PeerlessTooltipEffect.class.getName());
                SkillSpec.SkillEffectSpec effect = new SkillSpec.SkillEffectSpec(json);
                effect.setGoverningSkill(skill.id);
                effect.setRequiredSkillLevel(2f);
                effect.setLevelBased(false);
                spec.getEffects().add(effect);
                added++;
            } catch (Throwable t) {
                log.warn("Peerless Skills - Renewed: could not add the Peerless tooltip part to " + skill.id, t);
            }
        }
        log.info("Peerless Skills - Renewed: Peerless tooltip part added to " + added + " skills");
    }

    /** The character these stats belong to: the player or one of the player's officers. */
    private static PersonAPI personOf(MutableCharacterStatsAPI stats) {
        if (stats == null || !PeerlessData.hasCampaign()) return null;
        PersonAPI player = Global.getSector().getPlayerPerson();
        if (player != null && player.getStats() == stats) return player;
        for (PersonAPI officer : OfficerPeerless.fleetOfficers()) {
            if (officer.getStats() == stats) return officer;
        }
        return null;
    }

    @Override
    public boolean hasCustomDescription() {
        return true;
    }

    @Override
    public void createCustomDescription(MutableCharacterStatsAPI stats, SkillSpecAPI skill, TooltipMakerAPI info, float width) {
        try {
            PeerlessSkill peerless = skill == null ? null : PeerlessSkill.of(skill.getId());
            PersonAPI person = personOf(stats);
            if (peerless == null || person == null) return;
            if (!PeerlessShipEffect.activeFor(person).contains(peerless)) return;
            info.addPara("Peerless", PeerlessData.GOLD, 10f);
            info.addPara(peerless.effectText(Settings.originalEffects()), Misc.getHighlightColor(), 3f);
        } catch (Throwable t) {
            // a tooltip must never break the screen
        }
    }

    @Override
    public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
    }

    @Override
    public String getEffectDescription(float level) {
        return null;
    }

    @Override
    public String getEffectPerLevelDescription() {
        return null;
    }

    @Override
    public ScopeDescription getScopeDescription() {
        return ScopeDescription.PILOTED_SHIP;
    }
}
