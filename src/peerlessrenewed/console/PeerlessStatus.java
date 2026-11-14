package peerlessrenewed.console;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import peerlessrenewed.PeerlessData;
import peerlessrenewed.Settings;
import peerlessrenewed.points.AchievementPoints;

public class PeerlessStatus implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage("Only in the campaign.");
            return CommandResult.WRONG_CONTEXT;
        }
        StringBuilder out = new StringBuilder();
        out.append("Peerless skill points: ").append(PeerlessData.points()).append('\n');
        out.append("Earned in this campaign: ").append(PeerlessData.earnedTotal())
                .append(", toward the next: ").append(Math.round(PeerlessData.fraction() * 100f)).append("%\n");
        out.append("Peerless skills (").append(PeerlessData.trained().size()).append(" of ").append(ConsoleText.count())
                .append("): ").append(ConsoleText.trainedNames()).append('\n');
        out.append("Effects: ").append(Settings.originalEffects() ? "Original" : "Renewed")
                .append(", point rate x").append(Settings.rate()).append('\n');
        if (Settings.useXp()) {
            out.append("Source: experience past the level cap, ")
                    .append(String.format("%.2f", PeerlessData.xpBars())).append(" of ")
                    .append(Settings.xpBarsPerPoint()).append(" bars");
        } else {
            out.append("Source: achievements\n");
            try {
                for (String line : AchievementPoints.lines()) out.append("  ").append(line).append('\n');
            } catch (Throwable t) {
                out.append("  Achievements - Renewed could not be read\n");
            }
        }
        Console.showMessage(out.toString());
        return CommandResult.SUCCESS;
    }
}
