package ru.cherepokivan.standalonevoicebridge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Provides the manual pairing path and a controlled configuration reload. */
final class StandaloneVoiceCommand implements CommandExecutor, TabCompleter {
    private final StandaloneVoiceBridgePlugin plugin;
    private final StandalonePairingListener pairingListener;

    StandaloneVoiceCommand(StandaloneVoiceBridgePlugin plugin, StandalonePairingListener pairingListener) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.pairingListener = Objects.requireNonNull(pairingListener, "pairingListener");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && "reload".equals(args[0].toLowerCase(Locale.ROOT))) {
            if (!sender.hasPermission("standalonevoicebridge.reload")) {
                sender.sendMessage(Component.text("У вас нет права на перезагрузку Standalone Voice Bridge.", NamedTextColor.RED));
                return true;
            }
            try {
                plugin.reloadBridgeConfiguration();
                sender.sendMessage(Component.text(
                    "Конфигурация Standalone Voice Bridge перезагружена. Активные одноразовые коды отозваны; запросите новый код.",
                    NamedTextColor.GREEN));
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Standalone bridge reload failed: " + exception.getMessage());
                sender.sendMessage(Component.text("Не удалось перезагрузить конфигурацию. Проверьте server log.", NamedTextColor.RED));
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Эта команда доступна только игроку в Minecraft.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 1 && "standalone".equals(args[0].toLowerCase(Locale.ROOT))) {
            pairingListener.issueAndSend(player, "создан вручную");
            return true;
        }

        player.sendMessage(Component.text("Использование: /voice standalone", NamedTextColor.YELLOW));
        if (player.hasPermission("standalonevoicebridge.reload")) {
            player.sendMessage(Component.text("Администрирование: /voice reload", NamedTextColor.GRAY));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> completions = new ArrayList<>();
        if ("standalone".startsWith(prefix)) {
            completions.add("standalone");
        }
        if (sender.hasPermission("standalonevoicebridge.reload") && "reload".startsWith(prefix)) {
            completions.add("reload");
        }
        return completions;
    }
}
