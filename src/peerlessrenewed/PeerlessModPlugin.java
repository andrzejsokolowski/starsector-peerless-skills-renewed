package peerlessrenewed;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

import peerlessrenewed.points.PointTracker;

public class PeerlessModPlugin extends BaseModPlugin {

    private static final Logger log = Global.getLogger(PeerlessModPlugin.class);

    @Override
    public void onApplicationLoad() {
        if (Global.getSettings().getModManager().isModEnabled("peerless_skills")) {
            log.warn("Peerless Skills - Renewed: the first Peerless Skills mod is also enabled. Both add Peerless effects; turn one off.");
        }
        try {
            Global.getSettings().loadTexture(peerlessrenewed.patches.SkillWidgetRenderPatch.DIAMOND);
        } catch (Exception e) {
            log.warn("Peerless Skills - Renewed: could not load the Peerless diamond", e);
        }
        peerlessrenewed.effects.PeerlessTooltipEffect.addToSkills();
    }

    @Override
    public void onGameLoad(boolean newGame) {
        PeerlessData.ensureHiddenSkill();
        Global.getSector().addTransientScript(new PointTracker());
        PeerlessData.refundLostElite();
        PeerlessData.refreshEffects();
    }
}
