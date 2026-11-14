package peerlessrenewed.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.characters.PersonAPI;
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

import peerlessrenewed.OfficerPeerless;
import peerlessrenewed.PeerlessData;
import peerlessrenewed.PeerlessSkill;
import peerlessrenewed.Settings;
import peerlessrenewed.Settings.OfficerCost;

/**
 * The officer Peerless window, opened from the Officers button on the Character screen. One row per
 * officer in the fleet with a button for each of their Elite combat skills: click to make it Peerless
 * (paid with what LunaLib "Peerless for officers" says), click a Peerless one to undo it and get the
 * payment back. Escape or a click outside closes it. Built like Second-in-Command's own pop-up panels.
 */
public final class OfficerWindow {

    private static final Logger log = Global.getLogger(OfficerWindow.class);

    private static final float W = 860f;
    private static final float H = 520f;
    private static final float HEADER = 74f;
    private static final float FOOTER = 40f;
    private static final float PORTRAIT = 64f;
    private static final float BUTTON_W = 172f;
    private static final float BUTTON_H = 24f;
    private static final int PER_ROW = 4;

    private OfficerWindow() {}

    /** Opens the window on {@code parent} (Second-in-Command's menu panel). {@code onChange} runs after each change. */
    public static void open(CustomPanelAPI parent, Runnable onChange) {
        if (parent == null) return;
        try {
            build(parent, onChange);
        } catch (Throwable t) {
            log.error("Peerless Skills - Renewed: could not open the officer window", t);
        }
    }

    private static void build(CustomPanelAPI parent, Runnable onChange) {
        WindowPlugin plugin = new WindowPlugin(parent);
        CustomPanelAPI window = Global.getSettings().createCustom(W, H, plugin);
        plugin.window = window;
        parent.addComponent(window).inMid();

        OfficerCost cost = Settings.officerCost();
        Color gold = PeerlessData.GOLD;

        TooltipMakerAPI head = window.createUIElement(W - 30f, HEADER, false);
        window.addUIElement(head).inTL(15f, 12f);
        head.setParaInsigniaLarge();
        head.addPara("Peerless officers", gold, 0f);
        head.setParaFontDefault();
        head.addPara("Click an officer's Elite combat skill to make it Peerless for %s. Click a Peerless skill to undo it "
                + "and get back what it cost.", 6f, Misc.getTextColor(), gold, OfficerPeerless.costText(cost));
        head.addPara("You have: %s", 3f, Misc.getGrayColor(), Misc.getHighlightColor(), OfficerPeerless.haveText(cost));

        float listH = H - HEADER - FOOTER - 12f;
        TooltipMakerAPI list = window.createUIElement(W - 30f, listH, true);

        List<PersonAPI> officers = OfficerPeerless.fleetOfficers();
        if (officers.isEmpty()) {
            list.addPara("You have no officers.", Misc.getGrayColor(), 10f);
        }
        for (PersonAPI person : officers) {
            addOfficerRow(parent, window, list, person, cost, onChange);
        }
        window.addUIElement(list).inTL(15f, HEADER + 12f);

        TooltipMakerAPI foot = window.createUIElement(W - 30f, FOOTER - 8f, false);
        window.addUIElement(foot).inTL(15f, H - FOOTER);
        ButtonAPI close = foot.addButton("Close", WindowPlugin.CLOSE, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                Alignment.MID, CutStyle.ALL, 120f, BUTTON_H, 0f);
        close.getPosition().inTL(W - 30f - 120f, 4f);
        close.setShortcut(Keyboard.KEY_ESCAPE, false);
    }

    private static void addOfficerRow(CustomPanelAPI parent, CustomPanelAPI window, TooltipMakerAPI list,
                                      PersonAPI person, OfficerCost cost, Runnable onChange) {
        List<PeerlessSkill> elite = new ArrayList<>();
        for (PeerlessSkill skill : PeerlessSkill.values()) {
            if (OfficerPeerless.isElite(person, skill.id)) elite.add(skill);
        }
        int lines = Math.max(1, (elite.size() + PER_ROW - 1) / PER_ROW);
        float rowW = W - 50f;
        float rowH = Math.max(PORTRAIT, 22f + lines * (BUTTON_H + 6f)) + 8f;

        RowPlugin plugin = new RowPlugin(parent, window, person, onChange);
        CustomPanelAPI row = Global.getSettings().createCustom(rowW, rowH, plugin);
        TooltipMakerAPI el = row.createUIElement(rowW, rowH, false);
        row.addUIElement(el).inTL(0f, 0f);

        el.addImage(person.getPortraitSprite(), PORTRAIT, PORTRAIT, 0f);
        el.getPrev().getPosition().inTL(0f, 0f);

        String ship = OfficerPeerless.shipText(person);
        LabelAPI name = el.addPara("%s, level %s" + (ship == null ? ", no ship" : ", " + ship), 0f,
                Misc.getGrayColor(), Misc.getHighlightColor(), person.getNameString(), "" + person.getStats().getLevel());
        name.getPosition().inTL(PORTRAIT + 12f, 0f);

        if (elite.isEmpty()) {
            LabelAPI none = el.addPara("No Elite combat skills yet.", Misc.getGrayColor(), 0f);
            none.getPosition().inTL(PORTRAIT + 12f, 22f);
        }
        boolean afford = OfficerPeerless.canAfford(cost);
        for (int i = 0; i < elite.size(); i++) {
            PeerlessSkill skill = elite.get(i);
            boolean peerless = OfficerPeerless.isTrained(person, skill.id);
            String label = Global.getSettings().getSkillSpec(skill.id).getName();
            Color base = peerless ? PeerlessData.GOLD : Misc.getBasePlayerColor();
            Color dark = peerless ? Misc.scaleColorOnly(PeerlessData.GOLD, 0.3f) : Misc.getDarkPlayerColor();
            ButtonAPI button = el.addButton(label, skill.id, base, dark, Alignment.MID, CutStyle.ALL, BUTTON_W, BUTTON_H, 0f);
            el.addTooltipToPrevious(new SkillTooltip(person, skill, peerless, cost), TooltipMakerAPI.TooltipLocation.BELOW);
            if (!peerless && !afford) button.setEnabled(false);
            if (peerless) button.highlight();
            int col = i % PER_ROW;
            int line = i / PER_ROW;
            button.getPosition().inTL(PORTRAIT + 12f + col * (BUTTON_W + 8f), 22f + line * (BUTTON_H + 6f));
        }
        list.addCustom(row, 8f);
    }

    private static final class SkillTooltip extends BaseTooltipCreator {
        private final PersonAPI person;
        private final PeerlessSkill skill;
        private final boolean peerless;
        private final OfficerCost cost;

        SkillTooltip(PersonAPI person, PeerlessSkill skill, boolean peerless, OfficerCost cost) {
            this.person = person;
            this.skill = skill;
            this.peerless = peerless;
            this.cost = cost;
        }

        @Override
        public float getTooltipWidth(Object tooltipParam) {
            return 400f;
        }

        @Override
        public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
            tooltip.addPara("Peerless " + Global.getSettings().getSkillSpec(skill.id).getName(), PeerlessData.GOLD, 0f);
            tooltip.addPara(skill.effectText(Settings.originalEffects()), 6f);
            if (peerless) {
                tooltip.addPara("Peerless. Click to undo it and get back %s.", 10f, Misc.getTextColor(),
                        Misc.getHighlightColor(), OfficerPeerless.refundText(person, skill.id));
            } else if (OfficerPeerless.canAfford(cost)) {
                tooltip.addPara("Click to make it Peerless: costs %s.", 10f, Misc.getTextColor(),
                        Misc.getHighlightColor(), OfficerPeerless.costText(cost));
            } else {
                tooltip.addPara("Costs %s. You have %s.", 10f, Misc.getNegativeHighlightColor(), Misc.getHighlightColor(),
                        OfficerPeerless.costText(cost), OfficerPeerless.haveText(cost));
            }
        }
    }

    /** One officer row: its buttons report here. */
    private static final class RowPlugin extends BaseCustomUIPanelPlugin {
        private final CustomPanelAPI parent;
        private final CustomPanelAPI window;
        private final PersonAPI person;
        private final Runnable onChange;

        RowPlugin(CustomPanelAPI parent, CustomPanelAPI window, PersonAPI person, Runnable onChange) {
            this.parent = parent;
            this.window = window;
            this.person = person;
            this.onChange = onChange;
        }

        @Override
        public void buttonPressed(Object buttonId) {
            if (!(buttonId instanceof String)) return;
            String skillId = (String) buttonId;
            boolean changed;
            if (OfficerPeerless.isTrained(person, skillId)) {
                changed = OfficerPeerless.untrain(person, skillId);
                if (changed) Global.getSoundPlayer().playUISound("ui_char_decrease_skill", 1f, 1f);
            } else {
                changed = OfficerPeerless.train(person, skillId);
                if (changed) Global.getSoundPlayer().playUISound("ui_char_spent_story_point_combat", 1f, 1f);
            }
            if (!changed) return;
            PeerlessData.refreshEffects();
            parent.removeComponent(window);
            if (onChange != null) {
                try {
                    onChange.run();
                } catch (Throwable t) {
                    log.warn("Peerless Skills - Renewed: could not refresh the character screen", t);
                }
            }
            open(parent, onChange);
        }
    }

    /** Dark backdrop, border, Escape and clicks outside close it; the window takes all mouse input. */
    private static final class WindowPlugin extends BaseCustomUIPanelPlugin {
        static final String CLOSE = "psr_officers_close";

        private final CustomPanelAPI parent;
        CustomPanelAPI window;
        private PositionAPI position;

        WindowPlugin(CustomPanelAPI parent) {
            this.parent = parent;
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
            GL11.glPushMatrix();
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(0f, 0f, 0f, 0.5f * alphaMult);
            GL11.glRectf(0f, 0f, Global.getSettings().getScreenWidth(), Global.getSettings().getScreenHeight());
            GL11.glColor4f(0f, 0f, 0f, alphaMult);
            GL11.glRectf(x, y, x + w, y + h);
            Color gold = PeerlessData.GOLD;
            GL11.glColor4f(gold.getRed() / 255f, gold.getGreen() / 255f, gold.getBlue() / 255f, 0.8f * alphaMult);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glVertex2f(x + 0.5f, y + 0.5f);
            GL11.glVertex2f(x + w - 0.5f, y + 0.5f);
            GL11.glVertex2f(x + w - 0.5f, y + h - 0.5f);
            GL11.glVertex2f(x + 0.5f, y + h - 0.5f);
            GL11.glEnd();
            GL11.glPopMatrix();
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
            if (position == null) return;
            for (InputEventAPI event : events) {
                if (event.isConsumed()) continue;
                if (event.isKeyUpEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE) {
                    event.consume();
                    close();
                    continue;
                }
                if (event.isKeyboardEvent()) event.consume();
                if (event.isMouseDownEvent()) {
                    boolean inside = event.getX() >= position.getX() && event.getX() <= position.getX() + position.getWidth()
                            && event.getY() >= position.getY() && event.getY() <= position.getY() + position.getHeight();
                    if (!inside) {
                        event.consume();
                        close();
                        continue;
                    }
                }
                if (event.isMouseMoveEvent() || event.isMouseDownEvent() || event.isMouseScrollEvent()) event.consume();
            }
        }

        @Override
        public void buttonPressed(Object buttonId) {
            if (CLOSE.equals(buttonId)) close();
        }

        private void close() {
            if (window != null) parent.removeComponent(window);
            window = null;
        }
    }
}
