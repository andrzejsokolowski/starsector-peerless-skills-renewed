package peerlessrenewed.points;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.fs.starfarer.api.Global;

import achievementsrenewed.api.AchievementSpec;
import achievementsrenewed.api.Achievements;
import peerlessrenewed.PeerlessData;
import peerlessrenewed.Settings;

/**
 * Points from Achievements - Renewed. Only this class names that mod, and it only loads when the mod is
 * enabled. Achievements are kept for the whole profile, so this does not ask whether one is done: it asks
 * whether its goal is reached in this campaign (the achievement's own count for this save), and pays each
 * one once per campaign.
 */
public final class AchievementPoints {

    private static final Logger log = Global.getLogger(AchievementPoints.class);
    public static final String TABLE = "data/config/peerless_renewed/achievement_points.csv";

    public static final class Source {
        public final String achievement;
        public final float points;
        public final String note;

        Source(String achievement, float points, String note) {
            this.achievement = achievement;
            this.points = points;
            this.note = note;
        }
    }

    private static List<Source> sources;

    private AchievementPoints() {}

    /** The table, merged from every mod that has one (later mods replace rows with the same id). */
    public static List<Source> sources() {
        if (sources != null) return sources;
        List<Source> list = new ArrayList<>();
        try {
            JSONArray rows = Global.getSettings().getMergedSpreadsheetDataForMod("achievement", TABLE, Settings.MOD_ID);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String id = row.optString("achievement", "").trim();
                if (id.isEmpty() || id.startsWith("#")) continue;
                float points = (float) row.optDouble("points", 1.0);
                if (points <= 0f) continue;
                list.add(new Source(id, points, row.optString("note", "")));
            }
        } catch (Throwable t) {
            log.error("Peerless Skills - Renewed: could not read " + TABLE, t);
        }
        sources = list;
        return list;
    }

    /** Pays every source whose goal is reached in this campaign and has not paid yet. */
    public static void check() {
        for (Source source : sources()) {
            if (PeerlessData.isPaid(source.achievement)) continue;
            AchievementSpec spec = Achievements.get(source.achievement);
            if (spec == null) continue;
            if (Achievements.progress(spec.id) < Math.max(1, spec.goal)) continue;
            PeerlessData.markPaid(source.achievement);
            PeerlessData.earn(source.points, spec.name);
        }
    }

    /** For the status command and tooltips: how many listed achievements exist here, and how many paid. */
    public static int[] counts() {
        int known = 0;
        int paid = 0;
        for (Source source : sources()) {
            if (Achievements.get(source.achievement) == null) continue;
            known++;
            if (PeerlessData.isPaid(source.achievement)) paid++;
        }
        return new int[] {known, paid};
    }

    /** "Name: done in this campaign" lines for the status command. */
    public static List<String> lines() {
        List<String> lines = new ArrayList<>();
        for (Source source : sources()) {
            AchievementSpec spec = Achievements.get(source.achievement);
            if (spec == null) continue;
            String state = PeerlessData.isPaid(source.achievement) ? "paid" : "not yet";
            lines.add(spec.name + " (" + source.note + "): " + state);
        }
        return lines;
    }
}
