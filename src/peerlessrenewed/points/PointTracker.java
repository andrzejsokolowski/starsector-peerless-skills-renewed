package peerlessrenewed.points;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.plugins.LevelupPlugin;

import peerlessrenewed.PeerlessData;
import peerlessrenewed.Settings;

/**
 * Runs in the campaign (transient, added on every game load). Earns points from experience past the
 * level cap or from achievements, keeps the hidden skill on the player, refunds Peerless skills that
 * lost Elite and re-applies effects when the effect mode changes.
 */
public class PointTracker implements EveryFrameScript {

    private static final Logger log = Global.getLogger(PointTracker.class);

    private float achievementTimer = 0f;
    private float checkTimer = 0f;
    private Boolean lastOriginal = null;
    private boolean achievementsFailed = false;

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        if (!PeerlessData.hasCampaign()) return;

        checkTimer -= amount;
        if (checkTimer <= 0f) {
            checkTimer = 0.5f;
            PeerlessData.ensureHiddenSkill();
            boolean original = Settings.originalEffects();
            boolean refunded = PeerlessData.refundLostElite();
            if (refunded || lastOriginal == null || lastOriginal != original) {
                if (lastOriginal != null || refunded) PeerlessData.refreshEffects();
                lastOriginal = original;
            }
        }

        if (Settings.useXp()) {
            trackXp();
        } else {
            PeerlessData.setLastXp(-1L);
            if (Global.getSector().isPaused()) return;
            achievementTimer -= amount;
            if (achievementTimer <= 0f) {
                achievementTimer = 3f;
                checkAchievements();
            }
        }
    }

    private void checkAchievements() {
        if (achievementsFailed) return;
        try {
            AchievementPoints.check();
        } catch (Throwable t) {
            achievementsFailed = true;
            log.error("Peerless Skills - Renewed: could not read Achievements - Renewed; using nothing until restart", t);
        }
    }

    /** One full experience bar at the level cap is one "level" of experience; the game wraps the bar. */
    private void trackXp() {
        MutableCharacterStatsAPI stats = PeerlessData.playerStats();
        LevelupPlugin plugin = Global.getSettings().getLevelupPlugin();
        if (stats == null || plugin == null) return;
        long xp = stats.getXP();
        int level = stats.getLevel();
        if (level < plugin.getMaxLevel()) {
            PeerlessData.setLastXp(xp);
            return;
        }
        long last = PeerlessData.lastXp();
        PeerlessData.setLastXp(xp);
        if (last < 0) return;
        long bar = plugin.getXPForLevel(level + 1) - plugin.getXPForLevel(level);
        if (bar <= 0) bar = 100000L;
        long delta = xp - last;
        if (delta < 0) delta += bar;
        if (delta <= 0) return;
        float bars = PeerlessData.xpBars() + delta / (float) bar;
        int perPoint = Settings.xpBarsPerPoint();
        while (bars >= perPoint) {
            bars -= perPoint;
            PeerlessData.earn(1f, "experience past the level cap");
        }
        PeerlessData.setXpBars(bars);
    }
}
