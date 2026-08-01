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
        if (!sender.hasPermission("auriongo.command.punishment." + this.type.key())) {
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
            case BAN -> this.service.applyBan(sender, target, parsed.reason(), parsed.durationMillis(), parsed.silent());
            case KICK -> this.service.applyKick(sender, target, parsed.reason(), parsed.silent());
            case MUTE -> this.service.applyMute(sender, target, parsed.reason(), parsed.durationMillis(), parsed.silent());
            case WARN -> this.service.applyWarn(sender, target, parsed.reason(), parsed.durationMillis(), parsed.silent());
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

        if (hasSilentFlagBeforeCurrent(args)) {
            return List.of();
        }

        if (this.type != PunishmentType.KICK && args.length == 2) {
            return filterByPrefix(args[1], List.of("10m", "30m", "1h", "6h", "12h", "1d", "7d", "30d", "perm", "навсегда", "-s"));
        }

        return filterByPrefix(args[args.length - 1], List.of("-s"));
    }

    private boolean hasSilentFlagBeforeCurrent(String[] args) {
        for (int index = 0; index < args.length - 1; index++) {
            if (args[index].equalsIgnoreCase("-s")) {
                return true;
            }
        }

        return false;
    }

    private ParsedInput parseInput(String[] input) {
        if (input.length == 0) {
            return new ParsedInput("", null, false);
        }

        boolean silent = input[input.length - 1].equalsIgnoreCase("-s");
        String[] sanitizedInput = silent
            ? Arrays.copyOf(input, input.length - 1)
            : input;

        if (sanitizedInput.length == 0) {
            return new ParsedInput("", null, true);
        }

        if (this.type != PunishmentType.KICK) {
            String first = sanitizedInput[0];
            Long firstDuration = DurationParser.parseToMillis(first);

            if (firstDuration != null || isPermanentKeyword(first)) {
                String reason = String.join(" ", Arrays.copyOfRange(sanitizedInput, 1, sanitizedInput.length)).trim();
                return new ParsedInput(reason, firstDuration, silent);
            }
        }

        String last = sanitizedInput[sanitizedInput.length - 1];
        Long duration = this.type == PunishmentType.KICK ? null : DurationParser.parseToMillis(last);

        if (duration != null || isPermanentKeyword(last)) {
            String reason = String.join(" ", Arrays.copyOf(sanitizedInput, sanitizedInput.length - 1)).trim();
            return new ParsedInput(reason, duration, silent);
        }

        return new ParsedInput(String.join(" ", sanitizedInput).trim(), null, silent);
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

    private record ParsedInput(String reason, Long durationMillis, boolean silent) {
    }
}
