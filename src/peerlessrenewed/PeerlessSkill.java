package peerlessrenewed;

/**
 * The fourteen combat skills of the player that can be made Peerless, in Second-in-Command's order.
 * Each has the text of its Renewed effect and of its Original effect (the first Peerless Skills mod).
 * The four skills Andrzej did not rework use the same effect in both modes.
 */
public enum PeerlessSkill {

    HELMSMANSHIP("helmsmanship",
            "Accelerating backwards is twice as fast.",
            "Using the ship system gives a burst of speed: +100% of top speed, at most +100, fading over 1 second. Acceleration and turning are doubled during the burst."),
    COMBAT_ENDURANCE("combat_endurance",
            null,
            "Once per battle, when hull falls to 10%, the hull cannot drop below that for 5 seconds."),
    IMPACT_MITIGATION("impact_mitigation",
            "Every projectile or missile that hits your armor or hull deals 20 less damage, before armor reduction.",
            "+100% ship mass."),
    DAMAGE_CONTROL("damage_control",
            null,
            "Engines and weapons repair faster the higher your combat readiness is: at 70% combat readiness, repairs are 70% faster."),
    FIELD_MODULATION("field_modulation",
            null,
            "The first overload of each battle is cancelled and the ship vents if it can. +200% maneuverability, fading over 5 seconds."),
    TARGET_ANALYSIS("target_analysis",
            "Every enemy ship starts with a weak spot on its outer edge: 1 to 4 neighboring armor cells (frigate to capital) with no armor at all.",
            "Hits on armor make it decay over 5 seconds: 10% of the hit's damage, at most 100. The decay never damages hull."),
    SYSTEMS_EXPERTISE("systems_expertise",
            "Using the ship system gives +50% top speed and maneuverability, fading over 5 seconds.",
            "Using the ship system halves hull damage taken for 0.25 seconds, then the bonus fades out by 0.5 seconds."),
    POINT_DEFENSE("point_defense",
            "Point defense hits on missiles have a 10% chance to disarm the warhead.",
            "Point defense hits on armor or hull also deal EMP equal to 10% of their damage."),
    ENERGY_WEAPON_MASTERY("energy_weapon_mastery",
            "Energy weapons deal 25% more damage to armor.",
            "The energy weapon damage bonus stays full up to 600 range and fades out by 2000 range instead of 1000."),
    BALLISTIC_MASTERY("ballistic_mastery",
            null,
            "While firing, weapons build up to perfect autofire accuracy and no recoil over 5 seconds. Both fade 0.5 seconds after firing stops."),
    GUNNERY_IMPLANTS("gunnery_implants",
            "Weapons with less base range than your longest-range ballistic or energy weapon gain 10% of that base range, added to their final range.",
            "+5% to the maximum electronic warfare penalty your fleet can apply."),
    ORDNANCE_EXPERT("ordnance_expert",
            "Weapons cost 1 / 2 / 4 fewer ordnance points (small / medium / large).",
            "+0.75 flux dissipation and +10 flux capacity for every ordnance point spent on hull mods."),
    POLARIZED_ARMOR("polarized_armor",
            "After 3 seconds without armor or hull damage, armor slowly rebuilds itself, up to 50% of each cell's full armor.",
            "Engines and weapons repair twice as fast while venting."),
    MISSILE_SPECIALIZATION("missile_specialization",
            "Guided missiles turn 50% faster. Unguided missiles slowly steer toward their target.",
            "Large missile launchers that have run dry reload 5% of their full ammo per minute, at least 1.");

    public final String id;
    private final String renewed;
    private final String original;

    PeerlessSkill(String id, String renewed, String original) {
        this.id = id;
        this.renewed = renewed;
        this.original = original;
    }

    /** Kept from the first Peerless Skills mod in both modes. */
    public boolean isKept() {
        return renewed == null;
    }

    public String effectText(boolean originalMode) {
        return originalMode || renewed == null ? original : renewed;
    }

    public static PeerlessSkill of(String id) {
        if (id == null) return null;
        for (PeerlessSkill skill : values()) {
            if (skill.id.equals(id)) return skill;
        }
        return null;
    }

    public static boolean isPeerless(String id) {
        return of(id) != null;
    }
}
