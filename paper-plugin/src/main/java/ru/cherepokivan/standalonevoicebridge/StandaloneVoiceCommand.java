package ru.cherepokivan.standalonevoicebridge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Provides the manual `/voice standalone` pairing path for an authenticated Minecraft player. */
final class StandaloneVoiceCommand implements CommandExecutor, TabCompleter {
    private final StandalonePairingListener pairingListener;

    StandaloneVoiceCommand(StandalonePairingListener pairingListener) {
        this.pairingListener = Objects.requireNonNull(pairingListener, "pairingListener");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Эта команда доступна только игроку в Minecraft.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 1 && "standalone".equals(args[0].toLowerCase(Locale.ROOT))) {
            pairingListener.issueAndSend(player, "создан вручную");
            return true;
        }

        player.sendMessage(Component.text("Использование: /voice standalone", NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && "standalone".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("standalone");
        }
        return Collections.emptyList();
    }
}
