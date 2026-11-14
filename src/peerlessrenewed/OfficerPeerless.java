package peerlessrenewed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.util.Misc;

import peerlessrenewed.Settings.OfficerCost;

/**
 * Peerless skills of the player's officers (optional, LunaLib "Peerless for officers"). Each upgrade
 * remembers what was paid for it, so undoing it gives back exactly that, whatever the setting is now.
 *
 * State in sector persistent data, plain Java types: psr_officers (person id to a set of skill ids) and
 * psr_officerPaid ("personId|skillId" to "currency:amount", currency pp, sp or cr).
 */
public final class OfficerPeerless {

    private static final Logger log = Global.getLogger(OfficerPeerless.class);

    private static final String KEY_OFFICERS = "psr_officers";
    private static final String KEY_PAID = "psr_officerPaid";

    private OfficerPeerless() {}

    public static boolean enabled() {
        return Settings.officerCost() != OfficerCost.OFF;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Set<String>> officers() {
        Map<String, Object> data = PeerlessData.data();
        if (data == null) return new HashMap<>();
        Object value = data.get(KEY_OFFICERS);
        if (value instanceof Map) return (Map<String, Set<String>>) value;
        Map<String, Set<String>> created = new HashMap<>();
        data.put(KEY_OFFICERS, created);
        return created;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> paid() {
        Map<String, Object> data = PeerlessData.data();
        if (data == null) return new HashMap<>();
        Object value = data.get(KEY_PAID);
        if (value instanceof Map) return (Map<String, String>) value;
        Map<String, String> created = new HashMap<>();
        data.put(KEY_PAID, created);
        return created;
    }

    /** The officer's Peerless skills (empty set when none). */
    public static Set<String> trained(PersonAPI person) {
        if (person == null) return new HashSet<>();
        Set<String> set = officers().get(person.getId());
        return set == null ? new HashSet<String>() : set;
    }

    public static boolean isTrained(PersonAPI person, String skillId) {
        return trained(person).contains(skillId);
    }

    public static boolean isElite(PersonAPI person, String skillId) {
        return person != null && person.getStats().getSkillLevel(skillId) >= 2f;
    }

    /** The player's officers, in fleet order. */
    public static List<PersonAPI> fleetOfficers() {
        List<PersonAPI> list = new ArrayList<>();
        CampaignFleetAPI fleet = Global.getSector() == null ? null : Global.getSector().getPlayerFleet();
        if (fleet == null) return list;
        for (OfficerDataAPI officer : fleet.getFleetData().getOfficersCopy()) {
            PersonAPI person = officer.getPerson();
            if (person == null || person.isPlayer() || person.isAICore()) continue;
            list.add(person);
        }
        return list;
    }

    /** The officer's ship in the player's fleet, or null. */
    public static String shipText(PersonAPI person) {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return null;
        for (com.fs.starfarer.api.fleet.FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.getCaptain() == person) {
                return member.getShipName() + ", " + member.getHullSpec().getHullNameWithDashClass();
            }
        }
        return null;
    }

    // ---- costs ----

    public static String costText(OfficerCost cost) {
        switch (cost) {
            case PEERLESS_POINTS: return "1 Peerless skill point";
            case STORY_POINTS: {
                int sp = Settings.officerStoryPoints();
                return sp + " story point" + (sp == 1 ? "" : "s");
            }
            case CREDITS: return Misc.getDGSCredits(Settings.officerCredits());
            default: return "nothing";
        }
    }

    /** What the player has of the current currency, as text. */
    public static String haveText(OfficerCost cost) {
        switch (cost) {
            case PEERLESS_POINTS: return PeerlessData.pointsText(peerlessAvailable());
            case STORY_POINTS: {
                int sp = Global.getSector().getPlayerStats().getStoryPoints();
                return sp + " story point" + (sp == 1 ? "" : "s");
            }
            case CREDITS: return Misc.getDGSCredits(Global.getSector().getPlayerFleet().getCargo().getCredits().get());
            default: return "";
        }
    }

    /** Peerless points not already set aside for the player's own pending upgrades. */
    private static int peerlessAvailable() {
        try {
            return peerlessrenewed.ui.PeerlessUi.available();
        } catch (Throwable t) {
            return PeerlessData.points();
        }
    }

    public static boolean canAfford(OfficerCost cost) {
        switch (cost) {
            case PEERLESS_POINTS: return peerlessAvailable() >= 1;
            case STORY_POINTS: return Global.getSector().getPlayerStats().getStoryPoints() >= Settings.officerStoryPoints();
            case CREDITS: return Global.getSector().getPlayerFleet().getCargo().getCredits().get() >= Settings.officerCredits();
            default: return false;
        }
    }

    // ---- upgrade and undo ----

    /** Pays and makes the officer's skill Peerless. False when it cannot be done. */
    public static boolean train(PersonAPI person, String skillId) {
        OfficerCost cost = Settings.officerCost();
        if (person == null || cost == OfficerCost.OFF || !PeerlessSkill.isPeerless(skillId)) return false;
        if (isTrained(person, skillId) || !isElite(person, skillId) || !canAfford(cost)) return false;
        String record;
        switch (cost) {
            case PEERLESS_POINTS:
                PeerlessData.setPoints(PeerlessData.points() - 1);
                record = "pp:1";
                break;
            case STORY_POINTS: {
                int sp = Settings.officerStoryPoints();
                MutableCharacterStatsAPI stats = Global.getSector().getPlayerStats();
                stats.setStoryPoints(stats.getStoryPoints() - sp);
                record = "sp:" + sp;
                break;
            }
            case CREDITS: {
                int cr = Settings.officerCredits();
                Global.getSector().getPlayerFleet().getCargo().getCredits().subtract(cr);
                record = "cr:" + cr;
                break;
            }
            default:
                return false;
        }
        Set<String> set = officers().get(person.getId());
        if (set == null) {
            set = new LinkedHashSet<>();
            officers().put(person.getId(), set);
        }
        set.add(skillId);
        paid().put(person.getId() + "|" + skillId, record);
        ensureHiddenSkill(person);
        return true;
    }

    /** Removes the Peerless level and gives back what was paid for it. */
    public static boolean untrain(PersonAPI person, String skillId) {
        if (person == null) return false;
        Set<String> set = officers().get(person.getId());
        if (set == null || !set.remove(skillId)) return false;
        if (set.isEmpty()) officers().remove(person.getId());
        refund(paid().remove(person.getId() + "|" + skillId));
        ensureHiddenSkill(person);
        return true;
    }

    private static void refund(String record) {
        int[] parsed = parse(record);
        if (parsed == null) return;
        int amount = parsed[1];
        switch (parsed[0]) {
            case 0:
                PeerlessData.setPoints(PeerlessData.points() + amount);
                break;
            case 1: {
                MutableCharacterStatsAPI stats = Global.getSector().getPlayerStats();
                stats.setStoryPoints(stats.getStoryPoints() + amount);
                break;
            }
            case 2:
                Global.getSector().getPlayerFleet().getCargo().getCredits().add(amount);
                break;
            default:
                break;
        }
    }

    /** {currency (0 Peerless points, 1 story points, 2 credits), amount}, or null. */
    private static int[] parse(String record) {
        if (record == null) return null;
        int colon = record.indexOf(':');
        if (colon < 0) return null;
        String currency = record.substring(0, colon);
        int amount;
        try {
            amount = Integer.parseInt(record.substring(colon + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        if ("pp".equals(currency)) return new int[] {0, amount};
        if ("sp".equals(currency)) return new int[] {1, amount};
        if ("cr".equals(currency)) return new int[] {2, amount};
        return null;
    }

    /** What undoing this upgrade gives back, as text. */
    public static String refundText(PersonAPI person, String skillId) {
        int[] parsed = person == null ? null : parse(paid().get(person.getId() + "|" + skillId));
        if (parsed == null) return "nothing";
        int amount = parsed[1];
        switch (parsed[0]) {
            case 0: return PeerlessData.pointsText(amount);
            case 1: return amount + " story point" + (amount == 1 ? "" : "s");
            case 2: return Misc.getDGSCredits(amount);
            default: return "nothing";
        }
    }

    /** An officer with Peerless skills keeps the hidden skill that carries the effects; without, it goes. */
    public static void ensureHiddenSkill(PersonAPI person) {
        if (person == null || person.isPlayer()) return;
        boolean wants = !trained(person).isEmpty();
        float level = person.getStats().getSkillLevel(PeerlessData.HIDDEN_SKILL);
        if (wants && level < 1f) person.getStats().setSkillLevel(PeerlessData.HIDDEN_SKILL, 1f);
        if (!wants && level > 0f) person.getStats().setSkillLevel(PeerlessData.HIDDEN_SKILL, 0f);
    }

    /**
     * Officers in the fleet whose upgraded skill lost Elite (a respec) lose Peerless and get the payment
     * back. Also keeps the hidden skill in step. Returns true when anything changed.
     */
    public static boolean refundLostElite() {
        if (!PeerlessData.hasCampaign()) return false;
        boolean changed = false;
        for (PersonAPI person : fleetOfficers()) {
            Set<String> set = officers().get(person.getId());
            if (set != null) {
                for (String id : new ArrayList<>(set)) {
                    if (!isElite(person, id)) {
                        untrain(person, id);
                        changed = true;
                        log.info("Peerless Skills - Renewed: officer " + person.getNameString() + " lost Elite " + id
                                + ", Peerless refunded");
                    }
                }
            }
            ensureHiddenSkill(person);
        }
        return changed;
    }

    /** Console: removes every officer upgrade and gives everything back. Returns how many. */
    public static int refundAll() {
        int count = 0;
        for (String personId : new ArrayList<>(officers().keySet())) {
            Set<String> set = officers().get(personId);
            if (set == null) continue;
            for (String skillId : new ArrayList<>(set)) {
                refund(paid().remove(personId + "|" + skillId));
                count++;
            }
            officers().remove(personId);
        }
        for (PersonAPI person : fleetOfficers()) ensureHiddenSkill(person);
        return count;
    }
}
