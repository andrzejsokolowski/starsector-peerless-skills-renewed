package peerlessrenewed.effects;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ArmorGridAPI;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.combat.listeners.AdvanceableListener;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.combat.listeners.HullDamageAboutToBeTakenListener;
import com.fs.starfarer.api.combat.listeners.WeaponRangeModifier;
import com.fs.starfarer.api.util.Misc;

import peerlessrenewed.PeerlessData;
import peerlessrenewed.PeerlessSkill;

/**
 * Everything the Peerless skills do during a battle, for one ship piloted by the player. Made fresh for
 * every battle, so "once per battle" state lives in plain fields.
 */
public class PeerlessShip implements AdvanceableListener, DamageDealtModifier, DamageTakenModifier,
        HullDamageAboutToBeTakenListener, WeaponRangeModifier {

    private static final String ID = "psr_peerless";
    private static final String ID_HELM = ID + "_helm";
    private static final String ID_SYS = ID + "_sys";
    private static final String ID_FM = ID + "_fm";
    private static final String ID_DC = ID + "_dc";
    private static final String ID_PA = ID + "_pa";
    private static final String ID_BM = ID + "_bm";
    private static final String ID_IM = ID + "_im";
    private static final String ID_EWM = ID + "_ewm";
    private static final String WEAK_SPOTS = "psr_weakSpots";

    private static final Color ENDURING = new Color(255, 165, 90);

    private final ShipAPI ship;
    private final EnumSet<PeerlessSkill> active;
    private final boolean original;

    // ship system edge
    private boolean systemWasOn = false;
    private float helmBurst = 0f;
    private float helmBurstBonus = 0f;
    private float sysTimer = 0f;
    private float sysOriginalTime = -1f;

    // combat endurance
    private boolean enduranceUsed = false;
    private float enduring = 0f;

    // field modulation
    private boolean fieldUsed = false;
    private float fieldTimer = 0f;

    // ballistic mastery
    private float ballistic = 0f;

    // polarized armor (renewed)
    private float lastArmorHit = -999f;
    private float regenTick = 0f;
    private boolean regenerating = false;

    // target analysis (renewed)
    private float weakSpotTick = 0f;

    // target analysis (original)
    private final List<Decay> decays = new ArrayList<>();

    // missile specialization
    private final Map<MissileAPI, ShipAPI> missileTargets = new WeakHashMap<>();
    private final Map<WeaponAPI, float[]> reload = new HashMap<>();

    // point defense (renewed)
    private final Map<MissileAPI, Float> pdRolled = new WeakHashMap<>();

    // gunnery implants (renewed)
    private float longestBaseRange = -1f;

    private static boolean applyingEmp = false;

    public PeerlessShip(ShipAPI ship, EnumSet<PeerlessSkill> active, boolean original) {
        this.ship = ship;
        this.active = active;
        this.original = original;
    }

    private boolean has(PeerlessSkill skill) {
        return active.contains(skill);
    }

    private boolean renewed(PeerlessSkill skill) {
        return active.contains(skill) && !original && !skill.isKept();
    }

    private boolean originalOrKept(PeerlessSkill skill) {
        return active.contains(skill) && (original || skill.isKept());
    }

    /** Removes every stat change this listener can leave behind. */
    public static void clearStats(ShipAPI ship) {
        MutableShipStatsAPI stats = ship.getMutableStats();
        for (String id : new String[] {ID_HELM, ID_SYS, ID_FM, ID_DC, ID_PA, ID_BM}) {
            stats.getMaxSpeed().unmodify(id);
            stats.getAcceleration().unmodify(id);
            stats.getDeceleration().unmodify(id);
            stats.getTurnAcceleration().unmodify(id);
            stats.getMaxTurnRate().unmodify(id);
            stats.getHullDamageTakenMult().unmodify(id);
            stats.getCombatEngineRepairTimeMult().unmodify(id);
            stats.getCombatWeaponRepairTimeMult().unmodify(id);
            stats.getAutofireAimAccuracy().unmodify(id);
            stats.getRecoilPerShotMult().unmodify(id);
            stats.getMaxRecoilMult().unmodify(id);
        }
    }

    // ------------------------------------------------------------------ advance

    @Override
    public void advance(float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || ship == null) return;
        if (!ship.isAlive() || ship.isHulk()) {
            clearStats(ship);
            return;
        }
        if (engine.isPaused() || amount <= 0f) return;
        MutableShipStatsAPI stats = ship.getMutableStats();
        float now = engine.getTotalElapsedTime(false);

        boolean systemOn = isSystemOn();
        boolean systemStarted = systemOn && !systemWasOn;
        systemWasOn = systemOn;

        helmsmanship(stats, amount, systemStarted);
        systemsExpertise(stats, amount, systemStarted);
        combatEndurance(amount);
        damageControl(stats);
        fieldModulation(stats, amount);
        ballisticMastery(stats, amount);
        polarizedArmor(stats, amount, now);
        if (renewed(PeerlessSkill.TARGET_ANALYSIS)) weakSpots(engine, amount);
        if (originalOrKept(PeerlessSkill.TARGET_ANALYSIS)) armorDecay(amount);
        if (renewed(PeerlessSkill.MISSILE_SPECIALIZATION)) steerUnguided(engine, amount);
        if (originalOrKept(PeerlessSkill.MISSILE_SPECIALIZATION)) reloadLargeLaunchers(amount);
    }

    private boolean isSystemOn() {
        ShipSystemAPI system = ship.getSystem();
        return system != null && system.isActive();
    }

    private void status(PeerlessSkill skill, String text) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.getPlayerShip() != ship) return;
        String icon;
        try {
            icon = Global.getSettings().getSkillSpec(skill.id).getSpriteName();
        } catch (Throwable t) {
            icon = Global.getSettings().getSpriteName("ui", "icon_energy");
        }
        String name = Global.getSettings().getSkillSpec(skill.id).getName();
        engine.maintainStatusForPlayerShip(ID + skill.id, icon, "Peerless " + name, text, false);
    }

    private void helmsmanship(MutableShipStatsAPI stats, float amount, boolean systemStarted) {
        if (renewed(PeerlessSkill.HELMSMANSHIP)) {
            if (ship.getEngineController() != null && ship.getEngineController().isAcceleratingBackwards()) {
                stats.getAcceleration().modifyMult(ID_HELM, 2f);
                stats.getDeceleration().modifyMult(ID_HELM, 2f);
                status(PeerlessSkill.HELMSMANSHIP, "2x acceleration backwards");
            } else {
                stats.getAcceleration().unmodify(ID_HELM);
                stats.getDeceleration().unmodify(ID_HELM);
            }
            return;
        }
        if (!originalOrKept(PeerlessSkill.HELMSMANSHIP)) return;
        if (systemStarted) {
            stats.getMaxSpeed().unmodify(ID_HELM);
            float top = stats.getMaxSpeed().getModifiedValue();
            helmBurstBonus = Math.min(top, 100f);
            helmBurst = 1f;
        }
        if (helmBurst > 0f) {
            stats.getMaxSpeed().modifyFlat(ID_HELM, helmBurstBonus * helmBurst);
            stats.getAcceleration().modifyMult(ID_HELM, 2f);
            stats.getDeceleration().modifyMult(ID_HELM, 2f);
            stats.getTurnAcceleration().modifyMult(ID_HELM, 2f);
            stats.getMaxTurnRate().modifyMult(ID_HELM, 2f);
            status(PeerlessSkill.HELMSMANSHIP, "+" + Math.round(helmBurstBonus * helmBurst) + " top speed");
            helmBurst -= amount;
        } else {
            stats.getMaxSpeed().unmodify(ID_HELM);
            stats.getAcceleration().unmodify(ID_HELM);
            stats.getDeceleration().unmodify(ID_HELM);
            stats.getTurnAcceleration().unmodify(ID_HELM);
            stats.getMaxTurnRate().unmodify(ID_HELM);
        }
    }

    private void systemsExpertise(MutableShipStatsAPI stats, float amount, boolean systemStarted) {
        if (renewed(PeerlessSkill.SYSTEMS_EXPERTISE)) {
            if (systemStarted) sysTimer = 3f;
            if (sysTimer > 0f) {
                float percent = 50f * sysTimer / 3f;
                stats.getMaxSpeed().modifyPercent(ID_SYS, percent);
                stats.getAcceleration().modifyPercent(ID_SYS, percent);
                stats.getDeceleration().modifyPercent(ID_SYS, percent);
                stats.getTurnAcceleration().modifyPercent(ID_SYS, percent);
                stats.getMaxTurnRate().modifyPercent(ID_SYS, percent);
                status(PeerlessSkill.SYSTEMS_EXPERTISE, "+" + Math.round(percent) + "% speed and maneuverability");
                sysTimer -= amount;
            } else {
                stats.getMaxSpeed().unmodify(ID_SYS);
                stats.getAcceleration().unmodify(ID_SYS);
                stats.getDeceleration().unmodify(ID_SYS);
                stats.getTurnAcceleration().unmodify(ID_SYS);
                stats.getMaxTurnRate().unmodify(ID_SYS);
            }
            return;
        }
        if (!originalOrKept(PeerlessSkill.SYSTEMS_EXPERTISE)) return;
        if (systemStarted) sysOriginalTime = 0f;
        if (sysOriginalTime >= 0f && sysOriginalTime < 0.5f) {
            float reduction = sysOriginalTime <= 0.25f ? 0.5f : 0.5f * (1f - (sysOriginalTime - 0.25f) / 0.25f);
            stats.getHullDamageTakenMult().modifyMult(ID_SYS, 1f - reduction);
            status(PeerlessSkill.SYSTEMS_EXPERTISE, "-" + Math.round(reduction * 100f) + "% hull damage taken");
            sysOriginalTime += amount;
        } else {
            stats.getHullDamageTakenMult().unmodify(ID_SYS);
            sysOriginalTime = -1f;
        }
    }

    private void combatEndurance(float amount) {
        if (!has(PeerlessSkill.COMBAT_ENDURANCE) || enduring <= 0f) return;
        enduring -= amount;
        ship.setJitter(ID + "_ce", ENDURING, 0.6f, 3, 6f);
        status(PeerlessSkill.COMBAT_ENDURANCE, "Enduring: " + Math.max(0, Math.round(enduring * 10f) / 10f) + " s");
    }

    private void damageControl(MutableShipStatsAPI stats) {
        if (has(PeerlessSkill.DAMAGE_CONTROL)) {
            float cr = Math.max(0f, ship.getCurrentCR());
            float mult = 1f / (1f + cr);
            stats.getCombatEngineRepairTimeMult().modifyMult(ID_DC, mult);
            stats.getCombatWeaponRepairTimeMult().modifyMult(ID_DC, mult);
        }
    }

    private void fieldModulation(MutableShipStatsAPI stats, float amount) {
        if (!has(PeerlessSkill.FIELD_MODULATION)) return;
        if (!fieldUsed && ship.getFluxTracker().isOverloaded()) {
            fieldUsed = true;
            ship.getFluxTracker().stopOverload();
            boolean canVent = ship.getVariant() == null || !ship.getVariant().hasHullMod("safetyoverrides");
            if (canVent) ship.getFluxTracker().ventFlux();
            fieldTimer = 5f;
            Global.getCombatEngine().addFloatingText(ship.getLocation(), "Overload cancelled", 24f, PeerlessData.GOLD, ship, 0.5f, 1f);
        }
        if (fieldTimer > 0f) {
            float percent = 200f * fieldTimer / 5f;
            stats.getAcceleration().modifyPercent(ID_FM, percent);
            stats.getDeceleration().modifyPercent(ID_FM, percent);
            stats.getTurnAcceleration().modifyPercent(ID_FM, percent);
            stats.getMaxTurnRate().modifyPercent(ID_FM, percent);
            status(PeerlessSkill.FIELD_MODULATION, "+" + Math.round(percent) + "% maneuverability");
            fieldTimer -= amount;
        } else {
            stats.getAcceleration().unmodify(ID_FM);
            stats.getDeceleration().unmodify(ID_FM);
            stats.getTurnAcceleration().unmodify(ID_FM);
            stats.getMaxTurnRate().unmodify(ID_FM);
        }
    }

    private void ballisticMastery(MutableShipStatsAPI stats, float amount) {
        if (!has(PeerlessSkill.BALLISTIC_MASTERY)) return;
        boolean firing = false;
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (weapon.isFiring() && !weapon.isDecorative()) {
                firing = true;
                break;
            }
        }
        if (firing) ballistic = Math.min(1f, ballistic + amount / 5f);
        else ballistic = Math.max(0f, ballistic - amount / 0.5f);
        if (ballistic > 0f) {
            stats.getAutofireAimAccuracy().modifyFlat(ID_BM, ballistic);
            stats.getRecoilPerShotMult().modifyMult(ID_BM, 1f - ballistic);
            stats.getMaxRecoilMult().modifyMult(ID_BM, 1f - ballistic);
            status(PeerlessSkill.BALLISTIC_MASTERY, Math.round(ballistic * 100f) + "% steadier aim");
        } else {
            stats.getAutofireAimAccuracy().unmodify(ID_BM);
            stats.getRecoilPerShotMult().unmodify(ID_BM);
            stats.getMaxRecoilMult().unmodify(ID_BM);
        }
    }

    private void polarizedArmor(MutableShipStatsAPI stats, float amount, float now) {
        if (originalOrKept(PeerlessSkill.POLARIZED_ARMOR)) {
            if (ship.getFluxTracker().isVenting()) {
                stats.getCombatEngineRepairTimeMult().modifyMult(ID_PA, 0.5f);
                stats.getCombatWeaponRepairTimeMult().modifyMult(ID_PA, 0.5f);
                status(PeerlessSkill.POLARIZED_ARMOR, "2x repair speed while venting");
            } else {
                stats.getCombatEngineRepairTimeMult().unmodify(ID_PA);
                stats.getCombatWeaponRepairTimeMult().unmodify(ID_PA);
            }
            return;
        }
        if (!renewed(PeerlessSkill.POLARIZED_ARMOR)) return;
        if (now - lastArmorHit < 3f) {
            regenerating = false;
            return;
        }
        regenTick += amount;
        if (regenTick < 0.25f) {
            if (regenerating) status(PeerlessSkill.POLARIZED_ARMOR, "Armor rebuilding");
            return;
        }
        float step = regenTick;
        regenTick = 0f;
        ArmorGridAPI grid = ship.getArmorGrid();
        if (grid == null) return;
        float max = grid.getMaxArmorInCell();
        float cap = max * 0.5f;
        float gain = max * 0.03f * step;
        float[][] cells = grid.getGrid();
        boolean changed = false;
        for (int x = 0; x < cells.length; x++) {
            for (int y = 0; y < cells[x].length; y++) {
                float value = cells[x][y];
                if (value < cap) {
                    grid.setArmorValue(x, y, Math.min(cap, value + gain));
                    changed = true;
                }
            }
        }
        regenerating = changed;
        if (changed) {
            ship.syncWithArmorGridState();
            status(PeerlessSkill.POLARIZED_ARMOR, "Armor rebuilding");
        }
    }

    // ------------------------------------------------------------------ target analysis

    @SuppressWarnings("unchecked")
    private void weakSpots(CombatEngineAPI engine, float amount) {
        weakSpotTick -= amount;
        if (weakSpotTick > 0f) return;
        weakSpotTick = 1f;
        Object stored = engine.getCustomData().get(WEAK_SPOTS);
        Set<ShipAPI> done;
        if (stored instanceof Set) {
            done = (Set<ShipAPI>) stored;
        } else {
            done = Collections.newSetFromMap(new WeakHashMap<ShipAPI, Boolean>());
            engine.getCustomData().put(WEAK_SPOTS, done);
        }
        for (ShipAPI other : engine.getShips()) {
            if (other == null || other.isFighter() || other.isHulk() || !other.isAlive()) continue;
            if (other.getOwner() == ship.getOwner() || other.getOwner() == 100) continue;
            if (done.contains(other)) continue;
            done.add(other);
            makeWeakSpot(engine, other);
        }
    }

    private void makeWeakSpot(CombatEngineAPI engine, ShipAPI target) {
        ArmorGridAPI grid = target.getArmorGrid();
        if (grid == null) return;
        float[][] cells = grid.getGrid();
        List<int[]> inside = new ArrayList<>();
        for (int x = 0; x < cells.length; x++) {
            for (int y = 0; y < cells[x].length; y++) {
                if (cells[x][y] <= 0f) continue;
                Vector2f at = grid.getLocation(x, y);
                if (at != null && target.getExactBounds() != null && isInside(target, at)) inside.add(new int[] {x, y});
            }
        }
        if (inside.isEmpty()) {
            for (int x = 1; x < cells.length - 1; x++) {
                for (int y = 1; y < cells[x].length - 1; y++) {
                    if (cells[x][y] > 0f) inside.add(new int[] {x, y});
                }
            }
        }
        if (inside.isEmpty()) return;
        int[] cell = inside.get((int) (Math.random() * inside.size()));
        grid.setArmorValue(cell[0], cell[1], 0f);
        target.syncWithArmorGridState();
    }

    private static boolean isInside(ShipAPI target, Vector2f point) {
        try {
            return org.lazywizard.lazylib.CollisionUtils.isPointWithinBounds(point, target);
        } catch (Throwable t) {
            return Misc.getDistance(point, target.getLocation()) < target.getCollisionRadius() * 0.6f;
        }
    }

    private static final class Decay {
        final ShipAPI target;
        final int x;
        final int y;
        final float perTick;
        int ticks = 10;
        float timer = 0f;

        Decay(ShipAPI target, int x, int y, float perTick) {
            this.target = target;
            this.x = x;
            this.y = y;
            this.perTick = perTick;
        }
    }

    private void armorDecay(float amount) {
        if (decays.isEmpty()) return;
        Set<ShipAPI> touched = Collections.newSetFromMap(new HashMap<ShipAPI, Boolean>());
        for (Iterator<Decay> it = decays.iterator(); it.hasNext(); ) {
            Decay decay = it.next();
            if (decay.target == null || !decay.target.isAlive()) {
                it.remove();
                continue;
            }
            decay.timer += amount;
            while (decay.timer >= 0.5f && decay.ticks > 0) {
                decay.timer -= 0.5f;
                decay.ticks--;
                ArmorGridAPI grid = decay.target.getArmorGrid();
                if (grid == null) break;
                float value = grid.getArmorValue(decay.x, decay.y);
                grid.setArmorValue(decay.x, decay.y, Math.max(0f, value - decay.perTick));
                touched.add(decay.target);
            }
            if (decay.ticks <= 0) it.remove();
        }
        for (ShipAPI target : touched) target.syncWithArmorGridState();
    }

    // ------------------------------------------------------------------ missiles

    private void steerUnguided(CombatEngineAPI engine, float amount) {
        for (MissileAPI missile : engine.getMissiles()) {
            if (missile.getSource() != ship || missile.isGuided() || missile.isFlare() || missile.isMine()) continue;
            if (missile.isFizzling() || missile.didDamage()) continue;
            ShipAPI target = missileTargets.get(missile);
            if (target == null || !target.isAlive() || target.isHulk()) {
                target = pickTarget(engine, missile);
                if (target == null) continue;
                missileTargets.put(missile, target);
            }
            float wanted = Misc.getAngleInDegrees(missile.getLocation(), target.getLocation());
            float diff = Misc.getAngleDiff(missile.getFacing(), wanted);
            if (diff < 0.5f) continue;
            float turn = Math.min(diff, 25f * amount) * Misc.getClosestTurnDirection(missile.getFacing(), wanted);
            missile.setFacing(missile.getFacing() + turn);
            Vector2f vel = missile.getVelocity();
            double rad = Math.toRadians(turn);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            float vx = vel.x * cos - vel.y * sin;
            float vy = vel.x * sin + vel.y * cos;
            vel.set(vx, vy);
        }
    }

    private ShipAPI pickTarget(CombatEngineAPI engine, MissileAPI missile) {
        ShipAPI chosen = ship.getShipTarget();
        if (isGoodTarget(chosen, missile)) return chosen;
        chosen = null;
        float best = Float.MAX_VALUE;
        for (ShipAPI other : engine.getShips()) {
            if (!isGoodTarget(other, missile)) continue;
            float dist = Misc.getDistance(missile.getLocation(), other.getLocation());
            if (dist < best) {
                best = dist;
                chosen = other;
            }
        }
        return chosen;
    }

    private boolean isGoodTarget(ShipAPI other, MissileAPI missile) {
        if (other == null || !other.isAlive() || other.isHulk() || other.isFighter()) return false;
        if (other.getOwner() == ship.getOwner() || other.getOwner() == 100) return false;
        float angle = Misc.getAngleInDegrees(missile.getLocation(), other.getLocation());
        if (Misc.getAngleDiff(missile.getFacing(), angle) > 35f) return false;
        return Misc.getDistance(missile.getLocation(), other.getLocation()) < 3000f;
    }

    private void reloadLargeLaunchers(float amount) {
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (weapon.getType() != WeaponType.MISSILE || weapon.getSize() != WeaponSize.LARGE) continue;
            if (!weapon.usesAmmo() || weapon.getMaxAmmo() <= 0 || weapon.getSpec().getAmmoPerSecond() > 0f) continue;
            float[] state = reload.get(weapon);
            if (state == null) {
                state = new float[] {0f, 0f};
                reload.put(weapon, state);
            }
            if (weapon.getAmmo() <= 0) state[0] = 1f;
            if (state[0] < 1f) continue;
            if (weapon.getAmmo() >= weapon.getMaxAmmo()) {
                state[1] = 0f;
                continue;
            }
            float perMinute = Math.max(1f, (float) Math.floor(weapon.getMaxAmmo() * 0.05f));
            state[1] += amount * perMinute / 60f;
            while (state[1] >= 1f && weapon.getAmmo() < weapon.getMaxAmmo()) {
                state[1] -= 1f;
                weapon.setAmmo(weapon.getAmmo() + 1);
            }
            status(PeerlessSkill.MISSILE_SPECIALIZATION, weapon.getDisplayName() + " reloading");
        }
    }

    // ------------------------------------------------------------------ damage hooks

    private static WeaponAPI weaponOf(Object param) {
        if (param instanceof DamagingProjectileAPI) return ((DamagingProjectileAPI) param).getWeapon();
        if (param instanceof BeamAPI) return ((BeamAPI) param).getWeapon();
        return null;
    }

    private static boolean isPd(WeaponAPI weapon) {
        if (weapon == null || weapon.getSpec() == null || weapon.getSpec().getAIHints() == null) return false;
        return weapon.getSpec().getAIHints().contains(AIHints.PD)
                || weapon.getSpec().getAIHints().contains(AIHints.PD_ONLY)
                || weapon.getSpec().getAIHints().contains(AIHints.PD_ALSO);
    }

    private static float hitDamage(DamageAPI damage) {
        float amount = damage.getDamage() * Math.max(0f, damage.getModifier().getModifiedValue());
        if (damage.isDps()) amount *= damage.getDpsDuration();
        return amount;
    }

    @Override
    public String modifyDamageDealt(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
        if (applyingEmp || damage == null) return null;
        WeaponAPI weapon = weaponOf(param);
        if (weapon == null || weapon.getShip() != ship) return null;
        CombatEngineAPI engine = Global.getCombatEngine();
        String result = null;

        // Point Defense, Renewed: disarm missiles.
        if (renewed(PeerlessSkill.POINT_DEFENSE) && target instanceof MissileAPI && isPd(weapon)) {
            MissileAPI missile = (MissileAPI) target;
            float now = engine.getTotalElapsedTime(false);
            Float last = pdRolled.get(missile);
            boolean disarmed = last != null && last < 0f;
            // One roll per missile every 0.25 seconds, so a beam's many small hits are not many rolls.
            if (!disarmed && missile.getOwner() != ship.getOwner() && (last == null || now - last >= 0.25f)) {
                pdRolled.put(missile, now);
                if (Math.random() < 0.10) {
                    pdRolled.put(missile, -1f);
                    missile.setDamageAmount(0f);
                    missile.setArmingTime(1000000f);
                    engine.addFloatingText(missile.getLocation(), "Disarmed", 16f, PeerlessData.GOLD, null, 0f, 0f);
                }
            }
        }

        if (!(target instanceof ShipAPI)) return null;
        ShipAPI hit = (ShipAPI) target;
        if (hit.getOwner() == ship.getOwner()) return null;

        // Energy Weapon Mastery.
        if (weapon.getType() == WeaponType.ENERGY && has(PeerlessSkill.ENERGY_WEAPON_MASTERY)) {
            if (renewed(PeerlessSkill.ENERGY_WEAPON_MASTERY)) {
                if (!shieldHit && hasArmorAt(hit, point)) {
                    damage.getModifier().modifyPercent(ID_EWM, 25f);
                    result = ID_EWM;
                }
            } else {
                float mag = ship.getFluxBasedEnergyWeaponDamageMultiplier() - 1f;
                Vector2f from = param instanceof DamagingProjectileAPI
                        ? ((DamagingProjectileAPI) param).getSpawnLocation() : ((BeamAPI) param).getFrom();
                if (mag > 0f && from != null && point != null) {
                    float dist = Misc.getDistance(from, point);
                    float extra = mag * (taper(dist, 2000f) - taper(dist, 1000f));
                    if (extra > 0f) {
                        damage.getModifier().modifyPercent(ID_EWM, extra * 100f);
                        result = ID_EWM;
                    }
                }
            }
        }

        if (shieldHit) return result;

        // Point Defense, Original: EMP on hull hits.
        if (originalOrKept(PeerlessSkill.POINT_DEFENSE) && isPd(weapon) && engine != null) {
            float emp = 0.1f * hitDamage(damage);
            if (emp > 0f) {
                applyingEmp = true;
                try {
                    engine.applyDamage(hit, point, 0f, DamageType.ENERGY, emp, false, false, ship, false);
                } finally {
                    applyingEmp = false;
                }
            }
        }

        // Target Analysis, Original: armor decay.
        if (originalOrKept(PeerlessSkill.TARGET_ANALYSIS) && point != null) {
            ArmorGridAPI grid = hit.getArmorGrid();
            int[] cell = grid == null ? null : grid.getCellAtLocation(point);
            if (cell != null) {
                float total = Math.min(0.1f * hitDamage(damage), damage.isDps() ? 100f * damage.getDpsDuration() : 100f);
                if (total > 0f) {
                    if (decays.size() > 400) decays.remove(0);
                    decays.add(new Decay(hit, cell[0], cell[1], total / 10f));
                }
            }
        }
        return result;
    }

    private static float taper(float dist, float maxRange) {
        float min = 600f;
        if (dist <= min) return 1f;
        if (dist >= maxRange) return 0f;
        return 1f - (dist - min) / (maxRange - min);
    }

    private static boolean hasArmorAt(ShipAPI target, Vector2f point) {
        if (point == null) return false;
        ArmorGridAPI grid = target.getArmorGrid();
        if (grid == null) return false;
        int[] cell = grid.getCellAtLocation(point);
        return cell != null && grid.getArmorValue(cell[0], cell[1]) > 0f;
    }

    @Override
    public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
        if (target != ship || damage == null || shieldHit) return null;
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine != null) lastArmorHit = engine.getTotalElapsedTime(false);

        if (renewed(PeerlessSkill.IMPACT_MITIGATION) && param instanceof DamagingProjectileAPI && !damage.isDps()) {
            float base = damage.getDamage();
            if (base > 0f) {
                float mult = Math.max(0f, base - 20f) / base;
                damage.getModifier().modifyMult(ID_IM, mult);
                return ID_IM;
            }
        }
        return null;
    }

    @Override
    public boolean notifyAboutToTakeHullDamage(Object param, ShipAPI target, Vector2f point, float damageAmount) {
        if (target != ship || !has(PeerlessSkill.COMBAT_ENDURANCE)) return false;
        float floor = ship.getMaxHitpoints() * 0.1f;
        float hull = ship.getHitpoints();
        if (enduring > 0f) {
            if (hull - damageAmount < floor) {
                ship.setHitpoints(Math.max(hull, floor));
                return true;
            }
            return false;
        }
        if (!enduranceUsed && hull > floor && hull - damageAmount <= floor) {
            enduranceUsed = true;
            enduring = 5f;
            ship.setHitpoints(floor);
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine != null) engine.addFloatingText(ship.getLocation(), "Enduring!", 30f, ENDURING, ship, 1f, 1f);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ gunnery implants

    @Override
    public float getWeaponRangePercentMod(ShipAPI ship, WeaponAPI weapon) {
        return 0f;
    }

    @Override
    public float getWeaponRangeMultMod(ShipAPI ship, WeaponAPI weapon) {
        return 1f;
    }

    @Override
    public float getWeaponRangeFlatMod(ShipAPI ship, WeaponAPI weapon) {
        if (!renewed(PeerlessSkill.GUNNERY_IMPLANTS) || !countsForRange(weapon)) return 0f;
        if (longestBaseRange < 0f) {
            float longest = 0f;
            for (WeaponAPI other : this.ship.getAllWeapons()) {
                if (countsForRange(other)) longest = Math.max(longest, other.getSpec().getMaxRange());
            }
            longestBaseRange = longest;
        }
        if (weapon.getSpec().getMaxRange() >= longestBaseRange) return 0f;
        return 0.1f * longestBaseRange;
    }

    private static boolean countsForRange(WeaponAPI weapon) {
        if (weapon == null || weapon.getSpec() == null || weapon.isDecorative()) return false;
        if (weapon.getType() == WeaponType.MISSILE || weapon.getType() == WeaponType.LAUNCH_BAY) return false;
        return weapon.getSlot() == null || !weapon.getSlot().isSystemSlot();
    }
}
