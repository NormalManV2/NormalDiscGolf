package normalmanv2.normalDiscGolf.impl.command;

import normalmanv2.normalDiscGolf.impl.util.BukkitCompatibility;
import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.custom.CustomCourse;
import normalmanv2.normalDiscGolf.impl.course.custom.CustomCourseStore;
import normalmanv2.normalDiscGolf.impl.course.custom.CustomCourseStore.Draft;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid.GridPoint;
import normalmanv2.normalDiscGolf.impl.manager.round.queue.RoundQueueManager.QueueResult;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

final class CourseCommand {
    private static final String EDIT = "ndg.course.edit";
    private static final String ADMIN = "ndg.course.admin";
    private static final List<String> PUBLIC = List.of("list", "info", "play", "validate");
    private static final List<String> EDITOR = List.of("create", "edit", "tee", "basket", "par", "remove", "preview", "save", "cancel");

    private CourseCommand() {}

    static void execute(Player player, String[] args, Function<QueueResult, String> queueMessage) {
        var manager = NDGManager.getInstance();
        CustomCourseStore store = manager.getCustomCourses();
        UUID id = player.getUniqueId();
        if (args.length == 1) {
            player.sendMessage(ChatColor.AQUA + "/ndg course list | info <id> | play <id>");
            if (player.hasPermission(EDIT)) {
                player.sendMessage(ChatColor.GRAY + "/ndg course create <id> [name] or edit <id>");
                player.sendMessage(ChatColor.GRAY + "/ndg course tee <hole> | basket <hole> | par <hole> <2-6>");
                player.sendMessage(ChatColor.GRAY + "/ndg course preview | validate | save | remove <hole> | cancel");
            }
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        Draft draft = store.draft(id);
        if (action.equals("list")) { list(player, store, args); return; }
        if (action.equals("info") || action.equals("validate")) {
            CustomCourse course = args.length == 2 && draft != null ? draft.snapshot() : named(player, store, args);
            if (course != null) {
                if (action.equals("validate")) validate(player, course);
                else info(player, course);
            }
            return;
        }
        if (action.equals("play")) {
            CustomCourse course = named(player, store, args);
            if (course == null || !checkErrors(player, course.validate())) return;
            var party = manager.getPartyManager().getPartyById(id);
            if (party != null && !party.getOwner().equals(id)) {
                error(player, "Only the party leader can queue the party.");
                return;
            }
            var ids = party == null ? List.of(id) : List.copyOf(party.getTeamMembers());
            QueueResult result = manager.getRoundQueueManager().addPlayersToCustomCourse(ids, course);
            if (result != QueueResult.SUCCESS) { error(player, queueMessage.apply(result)); return; }
            for (UUID member : ids) {
                Player target = Bukkit.getPlayer(member);
                if (target != null) target.sendMessage(ChatColor.GREEN + "Queued for custom course " + course.name() + ".");
            }
            return;
        }
        if (!EDITOR.contains(action)) { error(player, "Use /ndg course for available commands."); return; }
        if (!player.hasPermission(EDIT)) { error(player, "You need permission to edit custom courses."); return; }
        if (action.equals("create") || action.equals("edit")) {
            open(player, store, args, draft, action.equals("create"));
            return;
        }
        if (draft == null) { error(player, "Open a draft with /ndg course create or edit."); return; }
        switch (action) {
            case "tee", "basket", "par", "remove" -> changeHole(player, draft, action, args);
            case "preview" -> {
                if (!draft.worldId().equals(player.getWorld().getUID())) {
                    error(player, "Return to the course world to preview its markers.");
                    return;
                }
                var course = draft.snapshot();
                course.layout().values().forEach(hole -> {
                    marker(player, hole.tee(), Color.LIME);
                    marker(player, hole.basket(), Color.ORANGE);
                });
                player.sendMessage(ChatColor.AQUA + "Draft markers shown. Tees are green and baskets are orange.");
            }
            case "save" -> save(player, store, draft);
            case "cancel" -> {
                store.closeDraft(id);
                player.sendMessage(ChatColor.YELLOW + "Course draft closed. Saved courses are still available.");
            }
            default -> throw new IllegalStateException(action);
        }
    }

    private static void open(Player player, CustomCourseStore store, String[] args, Draft draft, boolean create) {
        if (draft != null) { error(player, "Save or cancel your current draft first."); return; }
        if (!create) {
            var course = named(player, store, args);
            if (course == null) return;
            if (!course.owner().equals(player.getUniqueId()) && !player.hasPermission(ADMIN)) {
                error(player, "Only the course owner or an administrator can edit it.");
                return;
            }
            store.openDraft(player.getUniqueId(), new Draft(course));
            player.sendMessage(ChatColor.GREEN + "Editing course " + course.id() + ".");
            return;
        }
        String id = args.length < 3 ? "" : args[2].toLowerCase(Locale.ROOT);
        if (!CustomCourseStore.validId(id)) { error(player, "Use an ID with 1–32 letters, digits, or hyphens."); return; }
        if (store.get(id) != null) { error(player, "A saved course already uses that ID."); return; }
        var handler = NDGManager.getInstance().getRoundHandler();
        if (handler.isCourseWorld(player.getWorld())) { error(player, "Create custom courses in a permanent world."); return; }
        String name = args.length == 3 ? id : String.join(" ", Arrays.copyOfRange(args, 3, args.length)).trim();
        if (name.isBlank() || name.length() > 40 || name.indexOf('§') >= 0) {
            error(player, "Use a plain course name of 1–40 characters.");
            return;
        }
        store.openDraft(player.getUniqueId(), new Draft(id, name, player.getUniqueId(), player.getWorld()));
        player.sendMessage(ChatColor.GREEN + "Started course draft " + id + ".");
    }

    private static void changeHole(Player player, Draft draft, String action, String[] args) {
        int number = args.length < 3 ? -1 : integer(args[2]);
        if (number < 1 || number > CustomCourse.MAX_HOLES || args.length != (action.equals("par") ? 4 : 3)) {
            error(player, "Use /ndg course " + action + " <hole 1–18>" + (action.equals("par") ? " <2–6>." : "."));
            return;
        }
        int index = number - 1;
        if (action.equals("remove")) {
            if (draft.remove(index)) player.sendMessage(ChatColor.YELLOW + "Removed hole " + number + " from the draft.");
            else error(player, "That hole is not in the draft.");
            return;
        }
        var hole = draft.hole(index);
        if (action.equals("par")) {
            int par = integer(args[3]);
            if (par < 2 || par > 6) { error(player, "Par must be from 2 to 6."); return; }
            draft.set(index, hole.withPar(par));
            player.sendMessage(ChatColor.GREEN + "Set hole " + number + " par to " + par + ".");
            return;
        }
        if (!draft.worldId().equals(player.getWorld().getUID())) { error(player, "Place anchors in the draft's world."); return; }
        if (!player.isOnGround()) { error(player, "Stand on the block where the " + action + " should go."); return; }
        var location = player.getLocation();
        var point = new GridPoint(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        boolean tee = action.equals("tee");
        draft.set(index, tee ? hole.withTee(point) : hole.withBasket(point));
        marker(player, point, tee ? Color.LIME : Color.ORANGE);
        player.sendMessage(ChatColor.GREEN + "Placed hole " + number + " " + action + " at "
                + point.x() + ", " + point.y() + ", " + point.z() + ".");
    }

    private static void marker(Player player, GridPoint point, Color color) {
        if (point == null) return;
        player.spawnParticle(BukkitCompatibility.DUST, CustomCourse.centered(player.getWorld(), point).add(0, 1, 0),
                24, .42, .65, .42, 0, new Particle.DustOptions(color, 1.2f));
    }

    private static void save(Player player, CustomCourseStore store, Draft draft) {
        var course = draft.snapshot();
        if (!checkErrors(player, course.validate())) return;
        try {
            if (!store.save(course, draft.previous())) {
                error(player, "This course changed since you opened the draft. Cancel it and open the latest course.");
                return;
            }
            store.closeDraft(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "Saved custom course " + course.id() + ". Play with /ndg course play " + course.id() + ".");
        } catch (IOException failure) {
            NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).getLogger().warning("Could not save course " + draft.id() + ": " + failure.getMessage());
            error(player, "The course could not be saved. Your draft is still open.");
        }
    }

    private static void validate(Player player, CustomCourse course) {
        if (checkErrors(player, course.validate()))
            player.sendMessage(ChatColor.GREEN + "Course is valid. " + course.holes() + " hole(s), par " + totalPar(course) + ".");
    }

    private static boolean checkErrors(Player player, List<String> errors) {
        if (errors.isEmpty()) return true;
        error(player, "Course validation found " + errors.size() + " problem(s).");
        errors.forEach(issue -> error(player, issue));
        return false;
    }

    private static CustomCourse named(Player player, CustomCourseStore store, String[] args) {
        if (args.length != 3) { error(player, "Use /ndg course " + args[1] + " <id>."); return null; }
        var course = store.get(args[2].toLowerCase(Locale.ROOT));
        if (course == null) error(player, "No saved course uses that ID. See /ndg course list.");
        return course;
    }

    private static void info(Player player, CustomCourse course) {
        player.sendMessage(ChatColor.AQUA + course.name() + " (" + course.id() + ") in " + course.worldName());
        course.layout().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
            var hole = entry.getValue();
            player.sendMessage(ChatColor.GRAY + "Hole " + (entry.getKey() + 1) + " • Par "
                    + (hole.par() == null ? "unset" : hole.par()) + " • Tee " + point(hole.tee()) + " • Basket " + point(hole.basket()));
        });
    }

    private static String point(GridPoint point) {
        return point == null ? "unset" : point.x() + ", " + point.y() + ", " + point.z();
    }

    private static void list(Player player, CustomCourseStore store, String[] args) {
        List<CustomCourse> courses = List.copyOf(store.courses());
        if (courses.isEmpty()) { player.sendMessage(ChatColor.YELLOW + "There are no saved courses yet."); return; }
        int pages = (courses.size() + 7) / 8;
        int page = args.length == 2 ? 1 : integer(args[2]);
        if (page < 1 || page > pages || args.length > 3) { error(player, "Choose a course list page from 1 to " + pages + "."); return; }
        player.sendMessage(ChatColor.AQUA + "Saved courses (" + page + "/" + pages + ")");
        for (var course : courses.subList((page - 1) * 8, Math.min(page * 8, courses.size())))
            player.sendMessage(ChatColor.GRAY + course.id() + " • " + course.name() + " • " + course.holes() + " hole(s), par " + totalPar(course));
    }

    private static int totalPar(CustomCourse course) { return course.holePars().values().stream().mapToInt(Integer::intValue).sum(); }
    private static int integer(String text) { try { return Integer.parseInt(text); } catch (NumberFormatException ignored) { return -1; } }
    private static void error(Player player, String text) { player.sendMessage(ChatColor.RED + text); }

    static List<String> complete(Player player, String[] args) {
        if (args.length == 2) {
            var commands = new java.util.ArrayList<>(PUBLIC);
            if (player.hasPermission(EDIT)) commands.addAll(EDITOR);
            return commands;
        }
        if (args.length == 3 && List.of("info", "play", "edit", "validate").contains(args[1].toLowerCase(Locale.ROOT)))
            return NDGManager.getInstance().getCustomCourses().courses().stream().map(CustomCourse::id).toList();
        if (args.length == 4 && args[1].equalsIgnoreCase("par")) return List.of("2", "3", "4", "5", "6");
        return List.of();
    }
}
