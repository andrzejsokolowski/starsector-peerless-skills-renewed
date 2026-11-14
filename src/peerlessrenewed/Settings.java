package peerlessrenewed;

import com.fs.starfarer.api.Global;

import lunalib.lunaSettings.LunaSettings;

/** LunaLib settings. Every read falls back to the default if LunaLib has no value yet. */
public final class Settings {

    public static final String MOD_ID = "peerless_skills_renewed";
    public static final String ACR_MOD_ID = "achievements_renewed";

    private Settings() {}

    /** True when the Original effects of the first Peerless Skills mod are chosen. */
    public static boolean originalEffects() {
        try {
            String value = LunaSettings.getString(MOD_ID, "psr_effects");
            return value != null && value.trim().equalsIgnoreCase("Original");
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean achievementsInstalled() {
        try {
            return Global.getSettings().getModManager().isModEnabled(ACR_MOD_ID);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Points come from experience bars when asked for, or when Achievements - Renewed is missing. */
    public static boolean useXp() {
        return !achievementsInstalled() || flag("psr_useXp", false);
    }

    public static int xpBarsPerPoint() {
        try {
            Integer value = LunaSettings.getInt(MOD_ID, "psr_xpBarsPerPoint");
            return value == null ? 4 : Math.max(1, value);
        } catch (Throwable t) {
            return 4;
        }
    }

    public static float rate() {
        try {
            Double value = LunaSettings.getDouble(MOD_ID, "psr_rate");
            return value == null ? 1f : (float) Math.max(0.05, value);
        } catch (Throwable t) {
            return 1f;
        }
    }

    public static boolean announce() {
        return flag("psr_notify", true);
    }

    private static boolean flag(String key, boolean fallback) {
        try {
            Boolean value = LunaSettings.getBoolean(MOD_ID, key);
            return value == null ? fallback : value;
        } catch (Throwable t) {
            return fallback;
        }
    }
}
