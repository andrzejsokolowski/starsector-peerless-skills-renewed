package peerlessrenewed.console;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;

import peerlessrenewed.PeerlessData;
import peerlessrenewed.PeerlessSkill;

final class ConsoleText {

    private ConsoleText() {}

    static String trainedNames() {
        List<String> names = new ArrayList<>();
        for (String id : PeerlessData.trained()) names.add(Global.getSettings().getSkillSpec(id).getName());
        return names.isEmpty() ? "none" : String.join(", ", names);
    }

    static int count() {
        return PeerlessSkill.values().length;
    }
}
