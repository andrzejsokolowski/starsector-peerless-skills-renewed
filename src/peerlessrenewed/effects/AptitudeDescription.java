package peerlessrenewed.effects;

import java.awt.Color;

import com.fs.starfarer.api.characters.DescriptionSkillEffect;
import com.fs.starfarer.api.util.Misc;

/** Text of the hidden Peerless aptitude. No screen shows it; the game needs one for every aptitude. */
public class AptitudeDescription implements DescriptionSkillEffect {

    @Override
    public String getString() {
        return "The Peerless level of your combat skills.";
    }

    @Override
    public Color[] getHighlightColors() {
        return new Color[0];
    }

    @Override
    public String[] getHighlights() {
        return new String[0];
    }

    @Override
    public Color getTextColor() {
        return Misc.getTextColor();
    }
}
