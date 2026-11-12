package dev.mox.theone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class OneCommands implements CommandExecutor, TabCompleter {
    private final TheOnePlugin plugin;

    public OneCommands(TheOnePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        if (name.equals("makeone")) return makeOne(sender, args);
        if (name.equals("removeone")) return removeOne(sender);
        if (name.equals("forceprog")) return forceProgress(sender, args);
        if (name.equals("oneinfo")) return info(sender, args);
        if (name.equals("progress")) return progress(sender);
        if (args.length > 0 && args[0].equalsIgnoreCase("ability1")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("This ability can only be used in-game.", NamedTextColor.RED));
                return true;
            }
            plugin.castAbility(player);
            return true;
        }
        if (!hasAdmin(sender)) return noPermission(sender);

        if (args.length == 0) {
            sender.sendMessage(Component.text("/one set <player> | remove | kills <amount> [player] | reset <player> | info [player] | reload | particles | wings | ability1 | cinematic [player]",
                    NamedTextColor.AQUA));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set" -> makeOne(sender, args.length > 1 ? new String[]{args[1]} : new String[0]);
            case "remove" -> removeOne(sender);
            case "kills" -> setKills(sender, args);
            case "reset" -> reset(sender, args);
            case "info" -> info(sender, args.length > 1 ? new String[]{args[1]} : new String[0]);
            case "reload" -> {
                plugin.reloadSettings();
                sender.sendMessage(Component.text("THE ONE configuration reloaded.", NamedTextColor.GREEN));
            }
            case "cinematic" -> {
                Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1])
                        : (sender instanceof Player player ? player : null);
                if (target == null) {
                    sender.sendMessage(Component.text("Usage: /one cinematic [online-player]", NamedTextColor.RED));
                    break;
                }
                plugin.playAscension(target);
                sender.sendMessage(Component.text("Played THE ONE ascension cinematic for " + target.getName() + ".", NamedTextColor.GREEN));
            }
            case "particles", "effects" -> {
                plugin.toggleEffects();
                sender.sendMessage(Component.text("Progression particles " +
                        (plugin.effectsEnabled() ? "enabled." : "disabled."),
                        plugin.effectsEnabled() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            }
            case "wings" -> {
                boolean enabled = plugin.toggleWings();
                sender.sendMessage(Component.text("THE ONE wings " + (enabled ? "enabled." : "disabled."),
                        enabled ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            }
            default -> sender.sendMessage(Component.text("Unknown subcommand. Use /one for help.", NamedTextColor.RED));
        }
        return true;
    }

    private boolean makeOne(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) return noPermission(sender);
        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /one set <player>", NamedTextColor.RED));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("That player must be online.", NamedTextColor.RED));
            return true;
        }
        try {
            plugin.setHolder(target);
            sender.sendMessage(Component.text(target.getName() + " is now THE ONE.", NamedTextColor.GOLD));
        } catch (SQLException exception) {
            sender.sendMessage(Component.text("Database error: " + exception.getMessage(), NamedTextColor.RED));
        }
        return true;
    }

    private boolean removeOne(CommandSender sender) {
        if (!hasAdmin(sender)) return noPermission(sender);
        try {
            plugin.setHolder(null);
            sender.sendMessage(Component.text("THE ONE holder removed.", NamedTextColor.YELLOW));
        } catch (SQLException exception) {
            sender.sendMessage(Component.text("Database error: " + exception.getMessage(), NamedTextColor.RED));
        }
        return true;
    }

    private boolean forceProgress(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) return noPermission(sender);
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command must be run by a player.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /forceprog <kills>", NamedTextColor.RED));
            return true;
        }
        Integer kills = parseKills(args[0]);
        if (kills == null) {
            sender.sendMessage(Component.text("Kills must be a whole number from 0 to 1000000.", NamedTextColor.RED));
            return true;
        }
        setPlayerKills(sender, player, kills);
        return true;
    }

    private boolean setKills(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /one kills <amount> [player]", NamedTextColor.RED));
            return true;
        }
        Integer kills = parseKills(args[1]);
        if (kills == null) {
            sender.sendMessage(Component.text("Kills must be a whole number from 0 to 1000000.", NamedTextColor.RED));
            return true;
        }
        Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) :
                (sender instanceof Player player ? player : null);
        if (target == null) {
            sender.sendMessage(Component.text("Specify an online player: /one kills <amount> <player>", NamedTextColor.RED));
            return true;
        }
        setPlayerKills(sender, target, kills);
        return true;
    }

    private void setPlayerKills(CommandSender sender, Player target, int kills) {
        try {
            plugin.setKills(target, kills);
            sender.sendMessage(Component.text("Set " + target.getName() + "'s progression to " + kills + " kills.",
                    NamedTextColor.GREEN));
            target.sendMessage(Component.text("Your THE ONE progression is now " + kills + " kills (" +
                    Stage.fromKills(kills).displayName() + ").", NamedTextColor.AQUA));
        } catch (SQLException exception) {
            sender.sendMessage(Component.text("Database error: " + exception.getMessage(), NamedTextColor.RED));
        }
    }

    private boolean reset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /one reset <player>", NamedTextColor.RED));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("That player must be online.", NamedTextColor.RED));
            return true;
        }
        try {
            plugin.resetProgress(target);
            sender.sendMessage(Component.text("Reset " + target.getName() + "'s progression.", NamedTextColor.GREEN));
        } catch (SQLException exception) {
            sender.sendMessage(Component.text("Database error: " + exception.getMessage(), NamedTextColor.RED));
        }
        return true;
    }

    private boolean info(CommandSender sender, String[] args) {
        Player target;
        if (args.length > 0) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("That player must be online.", NamedTextColor.RED));
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            UUID holder = plugin.getHolder();
            target = holder == null ? null : Bukkit.getPlayer(holder);
            if (target == null) {
                sender.sendMessage(Component.text("No online THE ONE holder.", NamedTextColor.YELLOW));
                return true;
            }
        }

        try {
            int kills = plugin.getKills(target.getUniqueId());
            Stage stage = Stage.fromKills(kills);
            sender.sendMessage(Component.text("✦ " + target.getName() + " — " + stage.displayName(),
                    NamedTextColor.GOLD));
            sender.sendMessage(Component.text("Kills: " + kills + " | Rings: " + stage.rings() +
                    " | Next: " + (stage == Stage.THE_ONE ? "MAX" : stage.nextThreshold()),
                    NamedTextColor.GRAY));
            sender.sendMessage(Component.text(plugin.isHolder(target) ? "Current holder" : "Not the current holder",
                    plugin.isHolder(target) ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY));
        } catch (SQLException exception) {
            sender.sendMessage(Component.text("Database error: " + exception.getMessage(), NamedTextColor.RED));
        }
        return true;
    }

    private boolean progress(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command must be run by a player.", NamedTextColor.RED));
            return true;
        }
        try {
            int kills = plugin.getKills(player.getUniqueId());
            Stage stage = Stage.fromKills(kills);
            sender.sendMessage(Component.text("YOUR EVOLUTION: " + stage.displayName(), NamedTextColor.GOLD));
            sender.sendMessage(Component.text("Kills: " + kills + " | Progress: " + stage.progressPercent(kills) + "%",
                    NamedTextColor.AQUA));
            sender.sendMessage(Component.text(plugin.isHolder(player) ?
                    "You currently hold THE ONE." : "You are not currently THE ONE holder.", NamedTextColor.GRAY));
        } catch (SQLException exception) {
            sender.sendMessage(Component.text("Database error: " + exception.getMessage(), NamedTextColor.RED));
        }
        return true;
    }

    private boolean hasAdmin(CommandSender sender) {
        return sender.hasPermission("theone.admin");
    }

    private boolean noPermission(CommandSender sender) {
        sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
        return true;
    }

    private static Integer parseKills(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed < 0 || parsed > 1_000_000 ? null : parsed;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1 && command.getName().equalsIgnoreCase("one")) {
            suggestions.addAll(List.of("set", "remove", "kills", "reset", "info", "reload", "particles", "effects", "wings", "ability1", "cinematic"));
        } else if (args.length == 1 && (command.getName().equalsIgnoreCase("makeone")
                || command.getName().equalsIgnoreCase("oneinfo"))) {
            Bukkit.getOnlinePlayers().forEach(player -> suggestions.add(player.getName()));
        } else if (args.length == 1 && command.getName().equalsIgnoreCase("forceprog")) {
            suggestions.addAll(List.of("0", "2", "5", "10", "15", "20", "30"));
        } else if (args.length == 2 && command.getName().equalsIgnoreCase("one")
                && List.of("set", "reset", "info", "cinematic").contains(args[0].toLowerCase())) {
            Bukkit.getOnlinePlayers().forEach(player -> suggestions.add(player.getName()));
        } else if (args.length == 3 && command.getName().equalsIgnoreCase("one")
                && args[0].equalsIgnoreCase("kills")) {
            Bukkit.getOnlinePlayers().forEach(player -> suggestions.add(player.getName()));
        }
        String input = args[args.length - 1].toLowerCase();
        return suggestions.stream().filter(s -> s.toLowerCase().startsWith(input)).toList();
    }
}
