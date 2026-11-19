package normalmanv2.normalDiscGolf.impl.command;

import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.player.PlayerData;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import normalmanv2.normalDiscGolf.impl.skill.Skill;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class ProfileAdminCommand {
    static final String PERMISSION = "ndg.commands.profile.admin";
    private static final List<String> ACTIONS = List.of("save", "reload", "rating", "skill");

    static void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to administer profiles.");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        boolean storageAction = action.equals("save") || action.equals("reload");
        if (!ACTIONS.contains(action) || (storageAction && (args.length < 2 || args.length > 3))
                || (action.equals("rating") && args.length != 4)
                || (action.equals("skill") && args.length != 5 && args.length != 6)) {
            help(sender);
            return;
        }
        OfflinePlayer target = args.length >= 3 ? resolve(args[2]) : sender instanceof Player player ? player : null;
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Specify an online or previously joined player name, or a UUID.");
            return;
        }
        var manager = NDGManager.getInstance().getPlayerDataManager();
        String name = target.getName() == null ? target.getUniqueId().toString() : target.getName();
        try {
            if (action.equals("reload")) {
                manager.reloadPlayerData(target.getUniqueId());
                sender.sendMessage(ChatColor.GREEN + "Reloaded profile for " + name + " from disk.");
                return;
            }
            // Validate every argument before loading or changing profile data.
            int value = 0;
            double xp = 0;
            String skillName = "";
            if (action.equals("rating")) {
                value = Integer.parseInt(args[3]);
                if (value < 0) throw new IllegalArgumentException("Rating must be a nonnegative integer.");
            } else if (action.equals("skill")) {
                skillName = args[3].toLowerCase(Locale.ROOT);
                Skill definition = new PlayerSkills(1, 5).asMap().get(skillName);
                if (definition == null) throw new IllegalArgumentException("Unknown skill. Use tab completion for skill names.");
                value = Integer.parseInt(args[4]);
                if (value < 1 || value > definition.getMaxLevel()) {
                    throw new IllegalArgumentException("Skill level must be between 1 and " + definition.getMaxLevel() + ".");
                }
                definition.restoreProgress(value, 0);
                xp = args.length == 6 ? Double.parseDouble(args[5]) : 0;
                if (!Double.isFinite(xp) || xp < 0 || xp >= definition.getXpToNext()
                        || (value == definition.getMaxLevel() && xp != 0)) {
                    throw new IllegalArgumentException("XP must be finite, nonnegative, and below the next level threshold (0 at max level).");
                }
            }
            PlayerData data = manager.getOrLoadPlayerData(target.getUniqueId());
            if (action.equals("rating")) data.getRating().setRating(value);
            if (action.equals("skill")) data.getSkills().asMap().get(skillName).restoreProgress(value, xp);
            if (!manager.savePlayerData(target.getUniqueId())) {
                sender.sendMessage(ChatColor.RED + "Could not save profile. Changes remain in memory; check the server log and retry saving.");
                return;
            }
            String result = switch (action) {
                case "rating" -> "Set rating to " + value + " and saved profile for ";
                case "skill" -> "Set " + skillName + " to level " + value + " with " + xp + " XP and saved profile for ";
                default -> "Saved profile for ";
            };
            sender.sendMessage(ChatColor.GREEN + result + name + ".");
        } catch (NumberFormatException exception) {
            sender.sendMessage(ChatColor.RED + "Invalid number. Rating and level require integers; XP accepts decimals.");
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(ChatColor.RED + exception.getMessage());
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException exception) {
            sender.sendMessage(ChatColor.RED + "Could not reload profile: " + exception.getMessage() + ". In-memory data was preserved.");
        }
    }

    private static OfflinePlayer resolve(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) return online;
        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(input));
        } catch (IllegalArgumentException ignored) {
            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                if (input.equalsIgnoreCase(player.getName())) return player;
            }
            return null;
        }
    }

    static List<String> complete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) return List.of();
        List<String> options = List.of();
        if (args.length == 2) options = ACTIONS;
        else if (args.length == 3 && ACTIONS.contains(args[1].toLowerCase(Locale.ROOT))) {
            options = java.util.Arrays.stream(Bukkit.getOfflinePlayers())
                    .map(OfflinePlayer::getName).filter(java.util.Objects::nonNull).distinct().sorted().toList();
        } else if (args.length == 4 && args[1].equalsIgnoreCase("skill")) {
            options = new PlayerSkills(1, 5).asMap().keySet().stream().sorted().toList();
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    static void help(CommandSender sender) {
        sender.sendMessage(ChatColor.GRAY + "/ndg profile save [player|uuid] - Save (defaults to yourself).");
        sender.sendMessage(ChatColor.GRAY + "/ndg profile reload [player|uuid] - Replace memory with disk data; discards unsaved changes.");
        sender.sendMessage(ChatColor.GRAY + "/ndg profile rating <player|uuid> <rating> - Set rating and save.");
        sender.sendMessage(ChatColor.GRAY + "/ndg profile skill <player|uuid> <skill> <level> [xp] - Set progress and save (XP defaults to 0).");
    }
}
