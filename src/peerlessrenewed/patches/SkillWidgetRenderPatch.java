package peerlessrenewed.patches;

import java.awt.Color;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;

import patchlib.api.context.AfterContext;
import patchlib.api.context.BeforeContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Before;
import patchlib.api.patch.Patch;
import peerlessrenewed.PeerlessData;
import peerlessrenewed.PeerlessSkill;
import peerlessrenewed.ui.PeerlessUi;
import second_in_command.ui.elements.SkillWidgetElement;

/**
 * The Peerless look of Second-in-Command's skill icons on the player's combat row: a gold diamond where
 * Elite has its three stars (the widget's own star sprite is swapped before it draws) and a gold border,
 * pulsing while the upgrade waits for Save.
 */
@Patch(target = @ClassMatch(type = SkillWidgetElement.class))
public class SkillWidgetRenderPatch {

    private static final Logger log = Global.getLogger(SkillWidgetRenderPatch.class);
    private static boolean failed = false;
    private static SpriteAPI border;
    private static SpriteAPI stars;
    private static SpriteAPI diamond;

    public static final String DIAMOND = "graphics/icons/psr_peerless_diamond.png";

    @Before(target = @MethodMatch(methodName = "render", parameterCount = 1))
    public static void beforeRender(BeforeContext context) {
        if (failed) return;
        try {
            SkillWidgetElement widget = context.getInferredSelf();
            if (widget == null || !"combat".equals(widget.getAptitudeId())) return;
            String id = widget.getId();
            boolean peerless = PeerlessSkill.isPeerless(id) && widget.getActivated() && widget.isElite()
                    && (PeerlessData.isTrained(id) || PeerlessUi.isPending(id));
            if (stars == null) stars = Global.getSettings().getSprite("graphics/secondInCommand/elite_stars.png");
            if (diamond == null) diamond = Global.getSettings().getSprite(DIAMOND);
            SpriteAPI want = peerless ? diamond : stars;
            if (widget.getEliteStars() != want) widget.setEliteStars(want);
        } catch (Throwable t) {
            failed = true;
            log.error("Peerless Skills - Renewed: could not swap the Elite stars, turned off for this session", t);
        }
    }

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
    }
}
