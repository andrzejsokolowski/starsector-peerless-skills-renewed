package peerlessrenewed.console;

import java.util.ArrayList;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import peerlessrenewed.OfficerPeerless;
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
        int officers = OfficerPeerless.refundAll();
        PeerlessData.refreshEffects();
        Console.showMessage("Removed " + count + " Peerless upgrade(s) of yours and " + officers
                + " of your officers, and gave back what they cost. Peerless skill points: " + PeerlessData.points());
        return CommandResult.SUCCESS;
    }
}
