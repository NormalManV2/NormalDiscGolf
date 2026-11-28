package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.common.round.DefaultRoundSettings;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AceRaceRound extends SingleThrowRound {
    public record Standing(int place, String name, int aces) {
        public String line() { return place + ". " + name + " - " + aceCount(aces); }
    }

    public AceRaceRound(CourseImpl course, String id, DefaultRoundSettings settings) {
        super(course, id, settings);
    }

    @Override
    public String holeResult(Team team, int hole) {
        if (hole > getCurrentHole() || hole == getCurrentHole() && !hasFinishedHole(team)) return "Pending";
        return getScoreCardByTeam(team).getBaskets().containsKey(hole) ? "Ace" : "Miss";
    }

    @Override
    public void miss(Team team, String reason) {
        if (hasFinishedHole(team)) return;
        completeWithoutBasket(team);
        play().message(ChatColor.GRAY + name(team.getOwner()) + " missed hole " + (getCurrentHole() + 1)
                + (reason == null ? "." : " (" + reason + ")."));
    }

    public List<Standing> standings() {
        var entries = getTeams().stream()
                .map(team -> new Standing(0, name(team.getOwner()), getScoreCardByTeam(team).getHoledOutCount()))
                .sorted(Comparator.comparingInt(Standing::aces).reversed().thenComparing(Standing::name))
                .toList();
        var standings = new ArrayList<Standing>(entries.size());
        int place = 0;
        int previousAces = -1;
        for (var entry : entries) {
            if (entry.aces() != previousAces) place = standings.size() + 1;
            standings.add(new Standing(place, entry.name(), entry.aces()));
            previousAces = entry.aces();
        }
        return standings;
    }

    public String result(List<Standing> standings) {
        String total = aceCount(standings.get(0).aces());
        if (standings.size() == 1) return "Ace Race result: " + standings.get(0).name() + " finished with " + total + ".";
        var leaders = standings.stream().filter(entry -> entry.place() == 1).map(Standing::name).toList();
        return leaders.size() == 1 ? "Ace Race result: " + leaders.get(0) + " wins with " + total + "."
                : "Ace Race result: " + String.join(", ", leaders) + " tie for first with " + total + " each.";
    }

    public static String aceCount(int aces) { return aces + (aces == 1 ? " ace" : " aces"); }

}
