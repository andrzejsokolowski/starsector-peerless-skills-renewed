package peerlessrenewed.effects;

import java.util.EnumSet;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.characters.AfterShipCreationSkillEffect;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.loading.HullModSpecAPI;

import peerlessrenewed.OfficerPeerless;
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
    private static final String OP_ID = "psr_peerless_ordnance";

    /**
     * The trained Peerless skills that count for this captain: the player's, or an officer's when officer
     * Peerless is on. Only skills still Elite on the captain count.
     */
    public static EnumSet<PeerlessSkill> activeFor(PersonAPI captain) {
        EnumSet<PeerlessSkill> active = EnumSet.noneOf(PeerlessSkill.class);
        if (captain == null || !PeerlessData.hasCampaign()) return active;
        Set<String> trained;
        if (captain.isPlayer() || captain == Global.getSector().getPlayerPerson()) {
            trained = PeerlessData.trained();
        } else if (OfficerPeerless.enabled()) {
            trained = OfficerPeerless.trained(captain);
        } else {
            return active;
        }
        for (String id : trained) {
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
        // Stats without a fleet member (some refit copies): find the player's ship with this variant.
        try {
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            if (fleet != null && stats.getVariant() != null) {
                for (FleetMemberAPI m : fleet.getFleetData().getMembersListCopy()) {
                    if (m.getVariant() == stats.getVariant() || m.getStats() == stats) return m.getCaptain();
                }
            }
        } catch (Throwable t) {
            return null;
        }
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

        boolean cheaperWeapons = active.contains(PeerlessSkill.ORDNANCE_EXPERT) && !original;
        if (active.contains(PeerlessSkill.ORDNANCE_EXPERT) && original) {
            float spent = hullModPointsSpent(stats.getVariant(), hullSize);
            stats.getFluxDissipation().modifyFlat(id, 0.75f * spent);
            stats.getFluxCapacity().modifyFlat(id, 10f * spent);
        } else {
            stats.getFluxDissipation().unmodify(id);
            stats.getFluxCapacity().unmodify(id);
        }
        setWeaponCosts(stats, cheaperWeapons);
        setWeaponCosts(opStats(stats), cheaperWeapons);
        if (cheaperWeapons) clearOtherShips(stats);

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
        setWeaponCosts(stats, false);
        setWeaponCosts(opStats(stats), false);
        stats.getMissileMaxTurnRateBonus().unmodify(id);
        stats.getMissileTurnAccelerationBonus().unmodify(id);
    }

    /**
     * Weapon ordnance point costs are read from the variant's own stats (getStatsForOpCosts), which only
     * hull mods change; a skill has to change both those and the ship's stats. The modifier uses a fixed
     * id so that it can be found and removed from any ship.
     */
    static MutableShipStatsAPI opStats(MutableShipStatsAPI stats) {
        if (stats == null || stats.getVariant() == null) return null;
        try {
            MutableShipStatsAPI op = stats.getVariant().getStatsForOpCosts();
            return op == stats ? null : op;
        } catch (Throwable t) {
            return null;
        }
    }

    static void setWeaponCosts(MutableShipStatsAPI stats, boolean on) {
        if (stats == null) return;
        String[][] mods = {
                {Stats.SMALL_BALLISTIC_MOD, Stats.SMALL_ENERGY_MOD, Stats.SMALL_MISSILE_MOD},
                {Stats.MEDIUM_BALLISTIC_MOD, Stats.MEDIUM_ENERGY_MOD, Stats.MEDIUM_MISSILE_MOD},
                {Stats.LARGE_BALLISTIC_MOD, Stats.LARGE_ENERGY_MOD, Stats.LARGE_MISSILE_MOD}};
        float[] cut = {1f, 2f, 4f};
        for (int size = 0; size < 3; size++) {
            for (String mod : mods[size]) {
                if (on) stats.getDynamic().getMod(mod).modifyFlat(OP_ID, -cut[size]);
                else stats.getDynamic().getMod(mod).unmodify(OP_ID);
            }
        }
    }

    /**
     * The OP-cost stats belong to the variant and keep the cut after its captain moves to another ship, when
     * this skill no longer runs for it. Each time the cut is applied, the other ships of the player's fleet
     * whose captain does not have it lose it.
     */
    private static void clearOtherShips(MutableShipStatsAPI stats) {
        try {
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            if (fleet == null) return;
            for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
                ShipVariantAPI variant = member.getVariant();
                if (variant == null || variant == stats.getVariant()) continue;
                EnumSet<PeerlessSkill> active = activeFor(member.getCaptain());
                if (active.contains(PeerlessSkill.ORDNANCE_EXPERT) && !Settings.originalEffects()) continue;
                setWeaponCosts(variant.getStatsForOpCosts(), false);
                setWeaponCosts(member.getStats(), false);
            }
        } catch (Throwable t) {
            // the refit still works without this clean-up
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
