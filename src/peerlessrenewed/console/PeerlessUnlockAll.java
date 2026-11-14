package peerlessrenewed.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;

import peerlessrenewed.PeerlessData;
import peerlessrenewed.PeerlessSkill;

public class PeerlessUnlockAll implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage("Only in the campaign.");
            return CommandResult.WRONG_CONTEXT;
        }
        MutableCharacterStatsAPI stats = PeerlessData.playerStats();
        if (stats == null) return CommandResult.ERROR;
        for (PeerlessSkill skill : PeerlessSkill.values()) {
            stats.setSkillLevel(skill.id, 2f);
            PeerlessData.trainFree(skill.id);
        }
        PeerlessData.refreshEffects();
        Console.showMessage("All " + PeerlessSkill.values().length + " combat skills are Elite and Peerless. Close and reopen the character screen to see it.");
        return CommandResult.SUCCESS;
    }
}
