package peerlessrenewed.patches;

import java.awt.Color;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;

import patchlib.api.context.AfterContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;
import peerlessrenewed.PeerlessData;
import peerlessrenewed.PeerlessSkill;
import peerlessrenewed.ui.PeerlessUi;
import second_in_command.ui.elements.SkillWidgetElement;

/**
 * Draws the Peerless look over Second-in-Command's skill icon on the player's combat row: a gold border
 * and gold stars when Peerless, the same pulsing while it waits for Save.
 */
@Patch(target = @ClassMatch(type = SkillWidgetElement.class))
public class SkillWidgetRenderPatch {

    private static final Logger log = Global.getLogger(SkillWidgetRenderPatch.class);
    private static boolean failed = false;
    private static SpriteAPI border;
    private static SpriteAPI stars;

    @After(target = @MethodMatch(methodName = "render", parameterCount = 1))
    public static void afterRender(AfterContext context) {
        if (failed) return;
        try {
            SkillWidgetElement widget = context.getInferredSelf();
            if (widget == null || !"combat".equals(widget.getAptitudeId())) return;
            String id = widget.getId();
            if (!PeerlessSkill.isPeerless(id) || !widget.getActivated() || !widget.isElite()) return;
            boolean trained = PeerlessData.isTrained(id);
            boolean pending = !trained && PeerlessUi.isPending(id);
            if (!trained && !pending) return;

            float alphaMult = context.getArg(0) instanceof Float ? (Float) context.getArg(0) : 1f;
            float pulse = 1f;
            if (pending) {
                double t = Global.getSector() == null ? 0 : System.currentTimeMillis() / 1000.0;
                pulse = 0.55f + 0.45f * (float) Math.abs(Math.sin(t * 3.0));
            }
            draw(widget, alphaMult * pulse);
        } catch (Throwable t) {
            failed = true;
            log.error("Peerless Skills - Renewed: could not draw the Peerless border, turned off for this session", t);
        }
    }

    private static void draw(SkillWidgetElement widget, float alpha) {
        if (border == null) border = Global.getSettings().getSprite("graphics/secondInCommand/skillBorderActive.png");
        if (stars == null) stars = Global.getSettings().getSprite("graphics/secondInCommand/elite_stars.png");
        float w = widget.getWidth();
        float h = widget.getHeight();
        float cx = widget.getX() + (int) (w / 2f);
        float cy = widget.getY() + (int) (h / 2f);
        Color gold = PeerlessData.GOLD;

        border.setNormalBlend();
        border.setColor(gold);
        border.setSize(w, h);
        border.setAlphaMult(alpha);
        border.renderAtCenter(cx, cy);
        border.setAdditiveBlend();
        border.setAlphaMult(alpha * 0.45f);
        border.renderAtCenter(cx, cy);

        stars.setNormalBlend();
        stars.setColor(gold);
        stars.setSize(w - 8f, h - 8f);
        stars.setAlphaMult(alpha);
        stars.renderAtCenter(cx, cy);
        stars.setAdditiveBlend();
        stars.setAlphaMult(alpha * 0.5f);
        stars.renderAtCenter(cx, cy);
    }
}
