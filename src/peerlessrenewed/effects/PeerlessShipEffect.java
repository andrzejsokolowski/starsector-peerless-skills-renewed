package peerlessrenewed.effects;

import java.util.EnumSet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.AfterShipCreationSkillEffect;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.loading.HullModSpecAPI;

import peerlessrenewed.PeerlessData;
import peerlessrenewed.PeerlessSkill;
import peerlessrenewed.Settings;

/**
 * The effect of the hidden skill psr_peerless (scope: piloted ship). Stats that the refit screen has to
 * show (weapon ordnance points, flux from hull mods, missile turning) are set here; everything that
 * happens during a battle runs in {@link PeerlessShip}, added after the ship is created.
 */
public class PeerlessShipEffect implements AfterShipCreationSkillEffect {

    public static final String MASS_KEY = "psr_baseMass";

    /** The trained Peerless skills that count for this captain: still Elite on the captain. */
    public static EnumSet<PeerlessSkill> activeFor(PersonAPI captain) {
        EnumSet<PeerlessSkill> active = EnumSet.noneOf(PeerlessSkill.class);
        if (captain == null || !PeerlessData.hasCampaign()) return active;
        if (!captain.isPlayer() && captain != Global.getSector().getPlayerPerson()) return active;
        for (String id : PeerlessData.trained()) {
            PeerlessSkill skill = PeerlessSkill.of(id);
            if (skill != null && captain.getStats().getSkillLevel(id) >= 2f) active.add(skill);
        }
        return active;
    }

    static PersonAPI captainOf(MutableShipStatsAPI stats) {
        if (stats == null) return null;
        FleetMemberAPI member = stats.getFleetMember();
        if (member != null) return member.getCaptain();
        if (stats.getEntity() instanceof ShipAPI) return ((ShipAPI) stats.getEntity()).getCaptain();
        return null;
    }

    @Override
    public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
        EnumSet<PeerlessSkill> active;
        try {
            active = activeFor(captainOf(stats));
        } catch (Throwable t) {
            return;
        }
        if (active.isEmpty()) {
            unapply(stats, hullSize, id);
            return;
        }
        boolean original = Settings.originalEffects();

        if (active.contains(PeerlessSkill.ORDNANCE_EXPERT)) {
            if (original) {
                float spent = hullModPointsSpent(stats.getVariant(), hullSize);
                stats.getFluxDissipation().modifyFlat(id, 0.75f * spent);
                stats.getFluxCapacity().modifyFlat(id, 10f * spent);
                clearWeaponCosts(stats, id);
            } else {
                stats.getFluxDissipation().unmodify(id);
                stats.getFluxCapacity().unmodify(id);
                for (String mod : new String[] {Stats.SMALL_BALLISTIC_MOD, Stats.SMALL_ENERGY_MOD, Stats.SMALL_MISSILE_MOD}) {
                    stats.getDynamic().getMod(mod).modifyFlat(id, -1f);
                }
                for (String mod : new String[] {Stats.MEDIUM_BALLISTIC_MOD, Stats.MEDIUM_ENERGY_MOD, Stats.MEDIUM_MISSILE_MOD}) {
                    stats.getDynamic().getMod(mod).modifyFlat(id, -2f);
                }
                for (String mod : new String[] {Stats.LARGE_BALLISTIC_MOD, Stats.LARGE_ENERGY_MOD, Stats.LARGE_MISSILE_MOD}) {
                    stats.getDynamic().getMod(mod).modifyFlat(id, -4f);
                }
            }
        } else {
            stats.getFluxDissipation().unmodify(id);
            stats.getFluxCapacity().unmodify(id);
            clearWeaponCosts(stats, id);
        }

        if (active.contains(PeerlessSkill.MISSILE_SPECIALIZATION) && !original) {
            stats.getMissileMaxTurnRateBonus().modifyPercent(id, 50f);
            stats.getMissileTurnAccelerationBonus().modifyPercent(id, 50f);
        } else {
            stats.getMissileMaxTurnRateBonus().unmodify(id);
            stats.getMissileTurnAccelerationBonus().unmodify(id);
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
        stats.getFluxDissipation().unmodify(id);
        stats.getFluxCapacity().unmodify(id);
        clearWeaponCosts(stats, id);
        stats.getMissileMaxTurnRateBonus().unmodify(id);
        stats.getMissileTurnAccelerationBonus().unmodify(id);
    }

    private static void clearWeaponCosts(MutableShipStatsAPI stats, String id) {
        for (String mod : new String[] {
                Stats.SMALL_BALLISTIC_MOD, Stats.SMALL_ENERGY_MOD, Stats.SMALL_MISSILE_MOD,
                Stats.MEDIUM_BALLISTIC_MOD, Stats.MEDIUM_ENERGY_MOD, Stats.MEDIUM_MISSILE_MOD,
                Stats.LARGE_BALLISTIC_MOD, Stats.LARGE_ENERGY_MOD, Stats.LARGE_MISSILE_MOD}) {
            stats.getDynamic().getMod(mod).unmodify(id);
        }
    }

    /** Ordnance points actually paid for hull mods: built-in and S-modded ones are free. */
    static float hullModPointsSpent(ShipVariantAPI variant, HullSize hullSize) {
        if (variant == null) return 0f;
        float total = 0f;
        for (String modId : variant.getNonBuiltInHullmods()) {
            if (variant.getSMods().contains(modId)) continue;
            HullModSpecAPI spec = Global.getSettings().getHullModSpec(modId);
            if (spec == null || spec.isHidden()) continue;
            total += spec.getCostFor(hullSize);
        }
        return total;
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if (ship == null) return;
        ship.removeListenerOfClass(PeerlessShip.class);
        EnumSet<PeerlessSkill> active;
        try {
            active = activeFor(ship.getCaptain());
        } catch (Throwable t) {
            return;
        }
        if (active.isEmpty()) return;
        boolean original = Settings.originalEffects();
        if (original && active.contains(PeerlessSkill.IMPACT_MITIGATION)) {
            Object base = ship.getCustomData().get(MASS_KEY);
            float baseMass = base instanceof Float ? (Float) base : ship.getMass();
            ship.setCustomData(MASS_KEY, baseMass);
            ship.setMass(baseMass * 2f);
        }
        ship.addListener(new PeerlessShip(ship, active, original));
    }

    @Override
    public void unapplyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if (ship == null) return;
        ship.removeListenerOfClass(PeerlessShip.class);
        Object base = ship.getCustomData().get(MASS_KEY);
        if (base instanceof Float) {
            ship.setMass((Float) base);
            ship.removeCustomData(MASS_KEY);
        }
        PeerlessShip.clearStats(ship);
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
        return ScopeDescription.PILOTED_SHIP;
    }
}
