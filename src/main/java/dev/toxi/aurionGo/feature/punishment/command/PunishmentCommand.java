package dev.toxi.aurionGo.feature.punishment.command;

import dev.toxi.aurionGo.feature.punishment.DurationParser;
import dev.toxi.aurionGo.feature.punishment.PunishmentService;
import dev.toxi.aurionGo.feature.punishment.PunishmentType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public final class PunishmentCommand implements CommandExecutor, TabCompleter {
    private final PunishmentService service;
    private final PunishmentType type;

    public PunishmentCommand(PunishmentService service, PunishmentType type) {
        this.service = service;
        this.type = type;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("auriongo.punish." + this.type.key())) {
            sender.sendMessage(this.service.renderNoPermission());
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(this.service.renderUsage(this.type));
            return true;
        }

        String target = args[0];
        String[] remainder = Arrays.copyOfRange(args, 1, args.length);
        ParsedInput parsed = parseInput(remainder);

        switch (this.type) {
            case BAN -> this.service.applyBan(sender, target, parsed.reason(), parsed.durationMillis());
            case KICK -> this.service.applyKick(sender, target, parsed.reason());
            case MUTE -> this.service.applyMute(sender, target, parsed.reason(), parsed.durationMillis());
            case WARN -> this.service.applyWarn(sender, target, parsed.reason(), parsed.durationMillis());
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }

        if (this.type != PunishmentType.KICK && args.length == 2) {
            return filterByPrefix(args[1], List.of("10m", "30m", "1h", "6h", "12h", "1d", "7d", "30d", "perm", "навсегда"));
        }

        return List.of();
    }

    private ParsedInput parseInput(String[] input) {
        if (input.length == 0) {
            return new ParsedInput("", null);
        }

        if (this.type != PunishmentType.KICK) {
            String first = input[0];
            Long firstDuration = DurationParser.parseToMillis(first);

            if (firstDuration != null || isPermanentKeyword(first)) {
                String reason = String.join(" ", Arrays.copyOfRange(input, 1, input.length)).trim();
                return new ParsedInput(reason, firstDuration);
            }
        }

        String last = input[input.length - 1];
        Long duration = this.type == PunishmentType.KICK ? null : DurationParser.parseToMillis(last);

        if (duration != null || isPermanentKeyword(last)) {
            String reason = String.join(" ", Arrays.copyOf(input, input.length - 1)).trim();
            return new ParsedInput(reason, duration);
        }

        return new ParsedInput(String.join(" ", input).trim(), null);
    }

    private boolean isPermanentKeyword(String input) {
        return input.equalsIgnoreCase("perm")
                || input.equalsIgnoreCase("permanent")
                || input.equalsIgnoreCase("навсегда");
    }

    private List<String> filterByPrefix(String input, List<String> values) {
        String partial = input.toLowerCase();
        return values.stream()
                .filter(value -> value.toLowerCase().startsWith(partial))
                .toList();
    }

    private record ParsedInput(String reason, Long durationMillis) {
    }
}
