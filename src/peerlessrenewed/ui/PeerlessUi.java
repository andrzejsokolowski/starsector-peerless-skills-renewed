package peerlessrenewed.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.BaseTooltipCreator;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import peerlessrenewed.OfficerPeerless;
import peerlessrenewed.PeerlessData;
import peerlessrenewed.PeerlessSkill;
import peerlessrenewed.Settings;
import peerlessrenewed.points.AchievementPoints;
import second_in_command.ui.SCPlayerPanel;
import second_in_command.ui.elements.CombatSkillWidgetElement;
import second_in_command.ui.elements.SkillWidgetElement;

/**
 * The Peerless part of Second-in-Command's Character screen (C): the Peerless skill points line under the
 * level, Save and Cancel under the Combat icon, and what a click on one of the fourteen combat skills does.
 *
 * Clicks: an Elite skill with a point to spare becomes "pending Peerless" (left click); Save spends the
 * points. A pending skill clicked again is no longer pending. A Peerless skill clicked (either button)
 * loses Peerless and gives its point back, and stays Elite. Every other click is Second-in-Command's.
 */
public final class PeerlessUi {

    private static final Logger log = Global.getLogger(PeerlessUi.class);

    /**
     * Second-in-Command's player column: "-Hover over the bar..." and "-Level N" end at y 241, the
     * Executive Officers bar starts at y 265 (275 without the compact layout). One line fits between.
     */
    private static final float POINTS_X = 0f;
    private static final float POINTS_Y = 246f;
    private static final float POINTS_W = 278f;
    private static final float POINTS_H = 18f;
    private static final float OFFICERS_W = 68f;

    /** Under the Combat icon (x 328 to 424, y 76 to 176) inside the combat frame (it ends at y 237). */
    private static final float BUTTONS_X = 331f;
    private static final float BUTTONS_Y = 183f;
    private static final float BUTTON_W = 90f;
    private static final float BUTTON_H = 22f;

    private static final Set<String> pending = new LinkedHashSet<>();
    private static Object lastPanel = null;

    private PeerlessUi() {}

    public static boolean isPending(String skillId) {
        return pending.contains(skillId);
    }

    /** Unspent points minus the ones the pending upgrades will take. */
    public static int available() {
        return PeerlessData.points() - pending.size();
    }

    // ------------------------------------------------------------------ clicks

    /** Returns true when the click was Peerless's and Second-in-Command must not handle it. */
    public static boolean handleClick(SkillWidgetElement widget, Object aptitudeIconRef, SCPlayerPanel panel,
                                      CustomPanelAPI subpanel, InputEventAPI event) {
        if (widget == null || event == null || !PeerlessData.hasCampaign()) return false;
        String id = widget.getId();
        if (!PeerlessSkill.isPeerless(id)) return false;
        if (isEditMode(aptitudeIconRef)) return false;

        if (PeerlessData.isTrained(id)) {
            PeerlessData.untrain(id);
            pending.remove(id);
            widget.playSound("ui_char_decrease_skill", 1f, 1f);
            PeerlessData.refreshEffects();
            rebuild(panel, subpanel);
            return true;
        }
        if (pending.remove(id)) {
            widget.playSound("ui_char_decrease_skill", 1f, 1f);
            rebuild(panel, subpanel);
            return true;
        }
        if (event.isRMBEvent()) return false;
        if (widget.getActivated() && widget.isElite() && widget.getPreAcquired() && PeerlessData.isElite(id)) {
            if (available() > 0) {
                pending.add(id);
                widget.playSound("ui_char_spent_story_point_combat", 1f, 1f);
                rebuild(panel, subpanel);
                return true;
            }
        }
        return false;
    }

    private static boolean isEditMode(Object aptitudeIconRef) {
        try {
            Object icon = aptitudeIconRef instanceof kotlin.jvm.internal.Ref.ObjectRef
                    ? ((kotlin.jvm.internal.Ref.ObjectRef<?>) aptitudeIconRef).element : aptitudeIconRef;
            return icon instanceof CombatSkillWidgetElement && ((CombatSkillWidgetElement) icon).isInEditMode();
        } catch (Throwable t) {
            return false;
        }
    }

    private static void rebuild(SCPlayerPanel panel, CustomPanelAPI subpanel) {
        if (panel == null || subpanel == null) return;
        try {
            panel.recreatePlayerPanel(subpanel);
        } catch (Throwable t) {
            log.error("Peerless Skills - Renewed: could not rebuild the character screen", t);
        }
    }

    // ------------------------------------------------------------------ the box

    /** Called after Second-in-Command builds (or rebuilds) the player part of the screen. */
    public static void onPlayerPanelBuilt(SCPlayerPanel panel, CustomPanelAPI subpanel) {
        if (panel == null || subpanel == null || !PeerlessData.hasCampaign()) return;
        if (panel != lastPanel) {
            pending.clear();
            lastPanel = panel;
        }
        if (PeerlessData.refundLostElite()) PeerlessData.refreshEffects();
        pending.removeIf(id -> PeerlessData.isTrained(id) || !PeerlessData.isElite(id));
        while (available() < 0 && !pending.isEmpty()) {
            List<String> list = new ArrayList<>(pending);
            pending.remove(list.get(list.size() - 1));
        }
        try {
            addPeerlessUi(panel, subpanel);
        } catch (Throwable t) {
            log.error("Peerless Skills - Renewed: could not add the Peerless points", t);
        }
    }

    private static void addPeerlessUi(SCPlayerPanel panel, CustomPanelAPI subpanel) {
        int available = available();

        // The points, as one more line under "-Level N", in the same style.
        CustomPanelAPI line = Global.getSettings().createCustom(POINTS_W, POINTS_H, new LinePlugin(panel, subpanel));
        subpanel.addComponent(line).inTL(POINTS_X, POINTS_Y);
        TooltipMakerAPI text = line.createUIElement(POINTS_W, POINTS_H, false);
        line.addUIElement(text).inTL(0f, 0f);
        LabelAPI label = text.addPara("-Peerless skill points: %s", 0f, Misc.getGrayColor(),
                available > 0 ? PeerlessData.GOLD : Misc.getHighlightColor(), "" + available);
        text.addTooltipToPrevious(new PointsTooltip(), TooltipMakerAPI.TooltipLocation.RIGHT);
        label.getPosition().inTL(0f, 0f);

        // Officers button, when officer Peerless is on in LunaLib.
        if (OfficerPeerless.enabled()) {
            ButtonAPI officers = text.addButton("Officers", LinePlugin.OFFICERS, PeerlessData.GOLD,
                    Misc.scaleColorOnly(PeerlessData.GOLD, 0.3f), Alignment.MID, CutStyle.ALL, OFFICERS_W, POINTS_H, 0f);
            officers.getPosition().inTL(POINTS_W - OFFICERS_W, 0f);
            text.addTooltipToPrevious(new OfficersTooltip(), TooltipMakerAPI.TooltipLocation.RIGHT);
        }

        // Save and Cancel, only while a change waits, under the Combat icon.
        if (pending.isEmpty()) return;
        float h = BUTTON_H * 2f + 4f;
        CustomPanelAPI buttons = Global.getSettings().createCustom(BUTTON_W, h, new ButtonsPlugin(panel, subpanel));
        subpanel.addComponent(buttons).inTL(BUTTONS_X, BUTTONS_Y);
        TooltipMakerAPI el = buttons.createUIElement(BUTTON_W, h, false);
        buttons.addUIElement(el).inTL(0f, 0f);
        Color gold = PeerlessData.GOLD;
        ButtonAPI save = el.addButton("Save", ButtonsPlugin.SAVE, gold, Misc.scaleColorOnly(gold, 0.3f),
                Alignment.MID, CutStyle.ALL, BUTTON_W, BUTTON_H, 0f);
        save.getPosition().inTL(0f, 0f);
        ButtonAPI cancel = el.addButton("Cancel", ButtonsPlugin.CANCEL, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                Alignment.MID, CutStyle.ALL, BUTTON_W, BUTTON_H, 0f);
        cancel.getPosition().belowLeft(save, 4f);
    }

    private static void save(SCPlayerPanel panel, CustomPanelAPI subpanel) {
        int trained = 0;
        for (String id : new ArrayList<>(pending)) {
            if (PeerlessData.train(id)) trained++;
        }
        pending.clear();
        if (trained > 0) {
            PeerlessData.refreshEffects();
            Global.getSoundPlayer().playUISound("ui_char_level_up", 1f, 1f);
        }
        rebuild(panel, subpanel);
    }

    private static void cancel(SCPlayerPanel panel, CustomPanelAPI subpanel) {
        pending.clear();
        Global.getSoundPlayer().playUISound("ui_char_decrease_skill", 1f, 1f);
        rebuild(panel, subpanel);
    }

    private static final class ButtonsPlugin extends BaseCustomUIPanelPlugin {
        static final String SAVE = "psr_save";
        static final String CANCEL = "psr_cancel";

        private final SCPlayerPanel panel;
        private final CustomPanelAPI subpanel;

        ButtonsPlugin(SCPlayerPanel panel, CustomPanelAPI subpanel) {
            this.panel = panel;
            this.subpanel = subpanel;
        }

        @Override
        public void buttonPressed(Object buttonId) {
            if (SAVE.equals(buttonId)) save(panel, subpanel);
            else if (CANCEL.equals(buttonId)) cancel(panel, subpanel);
        }
    }

    /** The points line: its Officers button opens the officer window on Second-in-Command's menu panel. */
    private static final class LinePlugin extends BaseCustomUIPanelPlugin {
        static final String OFFICERS = "psr_officers";

        private final SCPlayerPanel panel;
        private final CustomPanelAPI subpanel;

        LinePlugin(SCPlayerPanel panel, CustomPanelAPI subpanel) {
            this.panel = panel;
            this.subpanel = subpanel;
        }

        @Override
        public void buttonPressed(Object buttonId) {
            if (!OFFICERS.equals(buttonId)) return;
            CustomPanelAPI menuPanel;
            try {
                menuPanel = panel.getMenu().getPanel();
            } catch (Throwable t) {
                log.error("Peerless Skills - Renewed: no Second-in-Command menu panel for the officer window", t);
                return;
            }
            Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f);
            OfficerWindow.open(menuPanel, () -> rebuild(panel, subpanel));
        }
    }

    private static final class OfficersTooltip extends BaseTooltipCreator {
        @Override
        public float getTooltipWidth(Object tooltipParam) {
            return 360f;
        }

        @Override
        public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
            tooltip.addPara("Make your officers' Elite combat skills Peerless. Each costs %s.", 0f,
                    Misc.getTextColor(), Misc.getHighlightColor(), OfficerPeerless.costText(Settings.officerCost()));
        }
    }

    private static final class PointsTooltip extends BaseTooltipCreator {
        @Override
        public float getTooltipWidth(Object tooltipParam) {
            return 420f;
        }

        @Override
        public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
            tooltip.addPara("Peerless is a third level for your own combat skills, past Elite. "
                    + "Click an Elite skill to make it Peerless, then press Save. Each costs one Peerless skill point.",
                    0f, Misc.getTextColor(), PeerlessData.GOLD, "Peerless", "Save");
            tooltip.addPara("Click a Peerless skill to undo it and get its point back. "
                    + "A skill that loses Elite also loses Peerless, and the point comes back.", 10f);
            if (Settings.useXp()) {
                tooltip.addPara("You earn a point for every %s full experience bars past the level cap. So far: %s.", 10f,
                        Misc.getHighlightColor(), "" + Settings.xpBarsPerPoint(),
                        String.format("%.1f of %d bars", PeerlessData.xpBars(), Settings.xpBarsPerPoint()));
            } else {
                String counted = "";
                try {
                    int[] counts = AchievementPoints.counts();
                    counted = " Paid so far in this campaign: " + counts[1] + " of " + counts[0] + ".";
                } catch (Throwable t) {
                    counted = "";
                }
                tooltip.addPara("You earn points for big achievements of Achievements - Renewed, each once per campaign."
                        + counted, 10f);
            }
            float rate = Settings.rate();
            if (Math.abs(rate - 1f) > 0.001f) {
                tooltip.addPara("Point rate: %s", 10f, Misc.getHighlightColor(), "x" + Misc.getRoundedValueMaxOneAfterDecimal(rate));
            }
            float fraction = PeerlessData.fraction();
            if (fraction > 0.001f) {
                tooltip.addPara("Toward the next point: %s", 3f, Misc.getHighlightColor(),
                        Math.round(fraction * 100f) + "%");
            }
            tooltip.addPara("Earned in this campaign: %s", 10f, PeerlessData.GOLD, "" + PeerlessData.earnedTotal());
            tooltip.addPara("Peerless skills: %s of 14", 3f, PeerlessData.GOLD, "" + PeerlessData.trained().size());
            tooltip.addPara("Effects: %s (LunaLib settings)", 10f, Misc.getGrayColor(), Misc.getHighlightColor(),
                    Settings.originalEffects() ? "Original" : "Renewed");
        }
    }
}
