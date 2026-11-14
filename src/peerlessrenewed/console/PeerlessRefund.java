package peerlessrenewed.console;

import java.util.ArrayList;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import peerlessrenewed.PeerlessData;

public class PeerlessRefund implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage("Only in the campaign.");
            return CommandResult.WRONG_CONTEXT;
        }
        int count = 0;
        for (String id : new ArrayList<>(PeerlessData.trained())) {
            if (PeerlessData.untrain(id)) count++;
        }
        PeerlessData.refreshEffects();
        Console.showMessage("Removed " + count + " Peerless upgrade(s). Peerless skill points: " + PeerlessData.points());
        return CommandResult.SUCCESS;
    }
}
