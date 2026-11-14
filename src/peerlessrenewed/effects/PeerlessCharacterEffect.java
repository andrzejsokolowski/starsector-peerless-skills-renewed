package peerlessrenewed.effects;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.CharacterStatsSkillEffect;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

import peerlessrenewed.PeerlessData;
import peerlessrenewed.PeerlessSkill;
import peerlessrenewed.Settings;

/** Original Gunnery Implants: +5 to the fleet's maximum electronic warfare penalty (a commander stat). */
public class PeerlessCharacterEffect implements CharacterStatsSkillEffect {

    public static final float ECM_BONUS = 5f;

    @Override
    public void apply(MutableCharacterStatsAPI stats, String id, float level) {
        boolean on = false;
        try {
            on = Settings.originalEffects()
                    && Global.getSector() != null
                    && stats == PeerlessData.playerStats()
                    && PeerlessData.isTrained(PeerlessSkill.GUNNERY_IMPLANTS.id)
                    && stats.getSkillLevel(PeerlessSkill.GUNNERY_IMPLANTS.id) >= 2f;
        } catch (Throwable t) {
            on = false;
        }
        if (on) {
            stats.getDynamic().getMod(Stats.ELECTRONIC_WARFARE_MAX).modifyFlat(id, ECM_BONUS);
        } else {
            stats.getDynamic().getMod(Stats.ELECTRONIC_WARFARE_MAX).unmodify(id);
        }
    }

    @Override
    public void unapply(MutableCharacterStatsAPI stats, String id) {
        stats.getDynamic().getMod(Stats.ELECTRONIC_WARFARE_MAX).unmodify(id);
    }

    @Override
    public String getEffectDescription(float level) {
        return "Peerless skills";
    }

    @Override
    public String getEffectPerLevelDescription() {
        return null;
    }

    @Override
    public ScopeDescription getScopeDescription() {
        return ScopeDescription.FLEET;
    }
}
