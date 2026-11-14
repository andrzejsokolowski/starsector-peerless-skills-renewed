package peerlessrenewed;

import java.awt.Color;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * The campaign's Peerless state, in sector persistent data under plain Java types only (no class of
 * this mod in the save): unspent points, the trained skills, the achievements that already paid in this
 * campaign, the fraction of a point earned so far and the experience-bar progress.
 */
public final class PeerlessData {

    private static final Logger log = Global.getLogger(PeerlessData.class);

    public static final String HIDDEN_SKILL = "psr_peerless";

    private static final String KEY_POINTS = "psr_points";
    private static final String KEY_TRAINED = "psr_trained";
    private static final String KEY_PAID = "psr_paid";
    private static final String KEY_FRACTION = "psr_fraction";
    private static final String KEY_XP_BARS = "psr_xpBars";
    private static final String KEY_XP_LAST = "psr_xpLast";
    private static final String KEY_EARNED = "psr_earned";

    /** Peerless gold, used for the badge, the points and messages. */
    public static final Color GOLD = new Color(255, 200, 70);

    private PeerlessData() {}

    static Map<String, Object> data() {
        SectorAPI sector = Global.getSector();
        return sector == null ? null : sector.getPersistentData();
    }

    public static boolean hasCampaign() {
        return data() != null && Global.getSector().getPlayerPerson() != null;
    }

    // ---- points ----

    public static int points() {
        Map<String, Object> data = data();
        if (data == null) return 0;
        Object value = data.get(KEY_POINTS);
        return value instanceof Integer ? (Integer) value : 0;
    }

    static void setPoints(int points) {
        Map<String, Object> data = data();
        if (data != null) data.put(KEY_POINTS, Math.max(0, points));
    }

    /** Points earned in this campaign so far, spent ones included. */
    public static int earnedTotal() {
        Map<String, Object> data = data();
        if (data == null) return 0;
        Object value = data.get(KEY_EARNED);
        return value instanceof Integer ? (Integer) value : 0;
    }

    /** Adds (or with a negative amount takes away) unspent points directly. Used by the console. */
    public static void addPoints(int amount) {
        setPoints(points() + amount);
    }

    /**
     * Earns a share of a point, multiplied by the rate setting. Fractions add up until they make a whole
     * point. Returns the whole points gained.
     */
    public static int earn(float amount, String reason) {
        Map<String, Object> data = data();
        if (data == null || amount <= 0) return 0;
        float fraction = floatOf(data.get(KEY_FRACTION)) + amount * Settings.rate();
        int whole = (int) Math.floor(fraction + 0.0001f);
        data.put(KEY_FRACTION, Math.max(0f, fraction - whole));
        if (whole > 0) {
            setPoints(points() + whole);
            data.put(KEY_EARNED, earnedTotal() + whole);
            log.info("Peerless Skills - Renewed: +" + whole + " point(s) from " + reason);
            announce(whole, reason);
        }
        return whole;
    }

    public static float fraction() {
        Map<String, Object> data = data();
        return data == null ? 0f : floatOf(data.get(KEY_FRACTION));
    }

    private static void announce(int whole, String reason) {
        if (!Settings.announce()) return;
        try {
            String text = "+" + whole + " Peerless skill point" + (whole == 1 ? "" : "s")
                    + (reason == null || reason.isEmpty() ? "" : " (" + reason + ")");
            Global.getSector().getCampaignUI().addMessage(text, GOLD);
            Global.getSoundPlayer().playUISound("ui_char_gained_story_point", 1f, 1f);
        } catch (Throwable t) {
            // No campaign screen to show it on; the character screen shows the points.
        }
    }

    // ---- trained skills ----

    @SuppressWarnings("unchecked")
    private static Set<String> set(String key) {
        Map<String, Object> data = data();
        if (data == null) return new HashSet<>();
        Object value = data.get(key);
        if (value instanceof Set) return (Set<String>) value;
        Set<String> created = new LinkedHashSet<>();
        data.put(key, created);
        return created;
    }

    public static Set<String> trained() {
        return set(KEY_TRAINED);
    }

    public static boolean isTrained(String skillId) {
        return trained().contains(skillId);
    }

    /** Spends a point and makes the skill Peerless. False when there is no point or it is not Elite. */
    public static boolean train(String skillId) {
        if (!PeerlessSkill.isPeerless(skillId) || isTrained(skillId)) return false;
        if (points() <= 0 || !isElite(skillId)) return false;
        setPoints(points() - 1);
        trained().add(skillId);
        return true;
    }

    /** Makes the skill Peerless without spending a point (console). */
    public static void trainFree(String skillId) {
        if (PeerlessSkill.isPeerless(skillId)) trained().add(skillId);
    }

    /** Removes the Peerless level and gives the point back. */
    public static boolean untrain(String skillId) {
        if (!trained().remove(skillId)) return false;
        setPoints(points() + 1);
        return true;
    }

    public static boolean isElite(String skillId) {
        MutableCharacterStatsAPI stats = playerStats();
        return stats != null && stats.getSkillLevel(skillId) >= 2f;
    }

    /**
     * A trained skill that lost its Elite level (respec, Second-in-Command reset) loses Peerless too, and
     * its point comes back. Returns true when anything changed.
     */
    public static boolean refundLostElite() {
        if (!hasCampaign()) return false;
        boolean changed = false;
        for (String id : new HashSet<>(trained())) {
            if (!isElite(id)) {
                untrain(id);
                changed = true;
                log.info("Peerless Skills - Renewed: " + id + " is no longer Elite, its Peerless point is back");
            }
        }
        return changed;
    }

    // ---- achievements and experience ----

    public static boolean isPaid(String achievementId) {
        return set(KEY_PAID).contains(achievementId);
    }

    public static void markPaid(String achievementId) {
        set(KEY_PAID).add(achievementId);
    }

    public static Set<String> paid() {
        return set(KEY_PAID);
    }

    public static float xpBars() {
        Map<String, Object> data = data();
        return data == null ? 0f : floatOf(data.get(KEY_XP_BARS));
    }

    public static void setXpBars(float bars) {
        Map<String, Object> data = data();
        if (data != null) data.put(KEY_XP_BARS, Math.max(0f, bars));
    }

    /** The experience seen last frame, or -1 before the first reading. */
    public static long lastXp() {
        Map<String, Object> data = data();
        if (data == null) return -1;
        Object value = data.get(KEY_XP_LAST);
        return value instanceof Long ? (Long) value : -1L;
    }

    public static void setLastXp(long xp) {
        Map<String, Object> data = data();
        if (data != null) data.put(KEY_XP_LAST, xp);
    }

    // ---- the hidden skill that carries the effects ----

    public static MutableCharacterStatsAPI playerStats() {
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.getPlayerPerson() == null) return null;
        return sector.getPlayerPerson().getStats();
    }

    /** The player keeps the hidden skill psr_peerless at level 1; it applies the trained effects. */
    public static void ensureHiddenSkill() {
        MutableCharacterStatsAPI stats = playerStats();
        if (stats == null) return;
        if (stats.getSkillLevel(HIDDEN_SKILL) < 1f) stats.setSkillLevel(HIDDEN_SKILL, 1f);
    }

    /** Re-applies skill effects to the fleet and the character after a change. */
    public static void refreshEffects() {
        try {
            MutableCharacterStatsAPI stats = playerStats();
            if (stats != null) stats.refreshCharacterStatsEffects();
            if (Global.getSector().getPlayerFleet() != null) {
                for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                    member.updateStats();
                }
            }
        } catch (Throwable t) {
            log.warn("Peerless Skills - Renewed: could not refresh the fleet's stats", t);
        }
    }

    private static float floatOf(Object value) {
        return value instanceof Number ? ((Number) value).floatValue() : 0f;
    }

    public static String pointsText(int points) {
        return points + " Peerless skill point" + (points == 1 ? "" : "s");
    }

    public static Color dim(Color c, float alpha) {
        return Misc.scaleAlpha(c, alpha);
    }
}
