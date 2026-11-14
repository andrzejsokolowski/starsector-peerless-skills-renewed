package peerlessrenewed.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.lwjgl.opengl.GL11;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.BaseTooltipCreator;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import peerlessrenewed.PeerlessData;
import peerlessrenewed.PeerlessSkill;
import peerlessrenewed.Settings;
import peerlessrenewed.points.AchievementPoints;
import second_in_command.ui.SCPlayerPanel;
import second_in_command.ui.elements.CombatSkillWidgetElement;
import second_in_command.ui.elements.SkillWidgetElement;

/**
 * The Peerless part of Second-in-Command's Character screen (C): the box with the Peerless skill points,
 * Save and Cancel, and what a click on one of the fourteen combat skills does.
 *
 * Clicks: an Elite skill with a point to spare becomes "pending Peerless" (left click); Save spends the
 * points. A pending skill clicked again is no longer pending. A Peerless skill clicked (either button)
 * loses Peerless and gives its point back, and stays Elite. Every other click is Second-in-Command's.
 */
public final class PeerlessUi {

    private static final Logger log = Global.getLogger(PeerlessUi.class);

    /** SiC draws its combat frame 755 wide from x 310 of this panel; the box sits to its right. */
    private static final float WIDE_X = 1085f;
    private static final float WIDE_Y = 12f;
    private static final float WIDE_W = 250f;
    private static final float WIDE_H = 225f;

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
            addBox(panel, subpanel);
        } catch (Throwable t) {
            log.error("Peerless Skills - Renewed: could not add the Peerless box", t);
        }
    }

    private static void addBox(SCPlayerPanel panel, CustomPanelAPI subpanel) {
        float panelWidth = subpanel.getPosition().getWidth();
        boolean wide = panelWidth <= 0f || panelWidth >= WIDE_X + WIDE_W + 20f;
        float w = wide ? WIDE_W : 260f;
        float h = wide ? WIDE_H : 54f;

        BoxPlugin plugin = new BoxPlugin(panel, subpanel);
        CustomPanelAPI box = Global.getSettings().createCustom(w, h, plugin);
        subpanel.addComponent(box).inTL(wide ? WIDE_X : 0f, wide ? WIDE_Y : 252f);

        TooltipMakerAPI el = box.createUIElement(w - 16f, h - 10f, false);
        box.addUIElement(el).inTL(8f, 6f);

        int available = available();
        if (wide) {
            el.setParaInsigniaLarge();
            el.addPara("Peerless", PeerlessData.GOLD, 0f);
            el.setParaFontDefault();
            el.addSpacer(6f);
        }
        LabelAPI line = el.addPara("Peerless skill points: %s", 0f, Misc.getTextColor(),
                available > 0 ? PeerlessData.GOLD : Misc.getGrayColor(), "" + available);
        el.addTooltipToPrevious(new BoxTooltip(), TooltipMakerAPI.TooltipLocation.BELOW);
        if (line == null) return;

        if (wide) {
            el.addSpacer(6f);
            el.addPara("Effects: %s", 0f, Misc.getGrayColor(), Misc.getHighlightColor(),
                    Settings.originalEffects() ? "Original" : "Renewed");
            el.addPara(sourceLine(), Misc.getGrayColor(), 3f);
            el.addSpacer(8f);
            if (!pending.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (String id : pending) names.add(Global.getSettings().getSkillSpec(id).getName());
                el.addPara("Making Peerless: %s", 0f, Misc.getTextColor(), PeerlessData.GOLD, String.join(", ", names));
            } else {
                el.addPara("Trained: %s of 14", 0f, Misc.getGrayColor(), PeerlessData.GOLD,
                        "" + PeerlessData.trained().size());
            }
        }

        if (!pending.isEmpty()) {
            Color base = PeerlessData.GOLD;
            Color dark = Misc.scaleColorOnly(base, 0.3f);
            float bw = wide ? 100f : 70f;
            ButtonAPI save = el.addButton("Save", BoxPlugin.SAVE, base, dark, Alignment.MID, CutStyle.ALL, bw, 24f, wide ? 10f : 4f);
            ButtonAPI cancel = el.addButton("Cancel", BoxPlugin.CANCEL, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                    Alignment.MID, CutStyle.ALL, bw, 24f, 0f);
            if (wide) {
                cancel.getPosition().rightOfTop(save, 10f);
            } else {
                save.getPosition().inTL(w - 16f - bw * 2f - 6f, 0f);
                cancel.getPosition().rightOfTop(save, 6f);
            }
        }
    }

    private static String sourceLine() {
        if (Settings.useXp()) {
            return String.format("From experience past the level cap: %.1f of %d bars",
                    PeerlessData.xpBars(), Settings.xpBarsPerPoint());
        }
        return "From achievements, once per campaign";
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

    private static final class BoxPlugin extends BaseCustomUIPanelPlugin {
        static final String SAVE = "psr_save";
        static final String CANCEL = "psr_cancel";

        private final SCPlayerPanel panel;
        private final CustomPanelAPI subpanel;
        private PositionAPI position;

        BoxPlugin(SCPlayerPanel panel, CustomPanelAPI subpanel) {
            this.panel = panel;
            this.subpanel = subpanel;
        }

        @Override
        public void positionChanged(PositionAPI position) {
            this.position = position;
        }

        @Override
        public void renderBelow(float alphaMult) {
            if (position == null) return;
            float x = position.getX();
            float y = position.getY();
            float w = position.getWidth();
            float h = position.getHeight();
            Color gold = PeerlessData.GOLD;
            GL11.glPushMatrix();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(0f, 0f, 0f, 0.55f * alphaMult);
            GL11.glRectf(x, y, x + w, y + h);
            GL11.glColor4f(gold.getRed() / 255f, gold.getGreen() / 255f, gold.getBlue() / 255f, 0.8f * alphaMult);
            GL11.glLineWidth(1f);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex2f(x + 0.5f, y + 0.5f);
            GL11.glVertex2f(x + w - 0.5f, y + 0.5f);
            GL11.glVertex2f(x + w - 0.5f, y + h - 0.5f);
            GL11.glVertex2f(x + 0.5f, y + h - 0.5f);
            GL11.glEnd();
            GL11.glPopMatrix();
        }

        @Override
        public void buttonPressed(Object buttonId) {
            if (SAVE.equals(buttonId)) save(panel, subpanel);
            else if (CANCEL.equals(buttonId)) cancel(panel, subpanel);
        }
    }

    private static final class BoxTooltip extends BaseTooltipCreator {
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
                tooltip.addPara("You earn a point for every %s full experience bars past the level cap.", 10f,
                        Misc.getHighlightColor(), "" + Settings.xpBarsPerPoint());
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
        }
    }
}
