package peerlessrenewed.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import peerlessrenewed.PeerlessData;

public class PeerlessAddPoints implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage("Only in the campaign.");
            return CommandResult.WRONG_CONTEXT;
        }
        int amount = 1;
        String text = args == null ? "" : args.trim();
        if (!text.isEmpty()) {
            try {
                amount = Integer.parseInt(text);
            } catch (NumberFormatException e) {
                return CommandResult.BAD_SYNTAX;
            }
        }
        PeerlessData.addPoints(amount);
        Console.showMessage("Peerless skill points: " + PeerlessData.points());
        return CommandResult.SUCCESS;
    }
}
