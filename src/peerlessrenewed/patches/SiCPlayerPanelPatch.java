package peerlessrenewed.patches;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import kotlin.Unit;
import kotlin.jvm.internal.Ref;
import patchlib.api.context.AfterContext;
import patchlib.api.context.BeforeContext;
import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Before;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectMethodCall;
import peerlessrenewed.ui.PeerlessSkillTooltip;
import peerlessrenewed.ui.PeerlessUi;
import second_in_command.misc.VanillaSkillTooltip;
import second_in_command.ui.SCPlayerPanel;
import second_in_command.ui.elements.SkillWidgetElement;

/**
 * Hooks into Second-in-Command's player panel (the top of the Character screen, C), without replacing
 * any of it:
 * - after the panel is built, the Peerless box is added;
 * - the tooltip of each of the fourteen combat skills is built by {@link PeerlessSkillTooltip};
 * - a click on a skill reaches {@link PeerlessUi#handleClick} first. The click handler is the Kotlin
 *   lambda inside recreateAptitudePanel; it is matched by its parameter types, not its generated name.
 */
@Patch(target = @ClassMatch(type = SCPlayerPanel.class))
public class SiCPlayerPanelPatch {

    private static final Logger log = Global.getLogger(SiCPlayerPanelPatch.class);
    private static boolean announcedBuild = false;
    private static boolean announcedClick = false;

    @After(target = @MethodMatch(methodName = "recreatePlayerPanel", parameterCount = 1))
    public static void afterRecreatePlayerPanel(AfterContext context) {
        try {
            if (!announcedBuild) {
                announcedBuild = true;
                log.info("Peerless Skills - Renewed: Second-in-Command player panel patch is active");
            }
            SCPlayerPanel panel = context.getInferredSelf();
            Object subpanel = context.getArg(0);
            if (subpanel instanceof CustomPanelAPI) PeerlessUi.onPlayerPanelBuilt(panel, (CustomPanelAPI) subpanel);
        } catch (Throwable t) {
            log.error("Peerless Skills - Renewed: could not add the Peerless box", t);
        }
    }

    @RedirectMethodCall(target = @MethodMatch(methodName = "recreateAptitudePanel", parameterCount = 1),
            call = @MethodMatch(methodName = "addToTooltip", parameterCount = 4),
            owner = @ClassMatch(type = VanillaSkillTooltip.class))
    public static void redirectSkillTooltip(MethodCallContext context) {
        try {
            TooltipMakerAPI tooltip = (TooltipMakerAPI) context.getCallArg(0);
            PersonAPI person = (PersonAPI) context.getCallArg(1);
            SkillSpecAPI spec = (SkillSpecAPI) context.getCallArg(2);
            int cost = (Integer) context.getCallArg(3);
            context.setResult(PeerlessSkillTooltip.addToTooltip(tooltip, person, spec, cost));
        } catch (Throwable t) {
            log.error("Peerless Skills - Renewed: could not build the Peerless tooltip, using Second-in-Command's", t);
            context.setResult(context.call());
        }
    }

    @Before(target = @MethodMatch(parameterTypes = {SkillWidgetElement.class, Ref.ObjectRef.class, SCPlayerPanel.class,
            CustomPanelAPI.class, Ref.ObjectRef.class, Ref.ObjectRef.class, InputEventAPI.class}))
    public static void beforeSkillClick(BeforeContext context) {
        try {
            if (!announcedClick) {
                announcedClick = true;
                log.info("Peerless Skills - Renewed: skill click patch is active");
            }
            SkillWidgetElement widget = (SkillWidgetElement) context.getArg(0);
            SCPlayerPanel panel = (SCPlayerPanel) context.getArg(2);
            CustomPanelAPI subpanel = (CustomPanelAPI) context.getArg(3);
            InputEventAPI event = (InputEventAPI) context.getArg(6);
            if (PeerlessUi.handleClick(widget, context.getArg(1), panel, subpanel, event)) {
                context.skipOriginal(Unit.INSTANCE);
            }
        } catch (Throwable t) {
            log.error("Peerless Skills - Renewed: skill click failed, Second-in-Command handles it", t);
        }
    }
}
