package ru.cherepokivan.standalonevoicebridge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Objects;

/** Delivers a pairing token only through the authenticated player's in-game session. */
final class StandalonePairingListener implements Listener {
    private final StandaloneTokenService tokenService;
    private final boolean automaticIssueEnabled;

    StandalonePairingListener(StandaloneTokenService tokenService, boolean automaticIssueEnabled) {
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService");
        this.automaticIssueEnabled = automaticIssueEnabled;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!automaticIssueEnabled) {
            return;
        }
        issueAndSend(event.getPlayer(), "автоматически создан");
    }

    void issueAndSend(Player player, String reason) {
        String token = tokenService.issue(player.getUniqueId());
        String displayedToken = StandaloneTokenService.display(token);
        long lifetimeSeconds = tokenService.lifetime().toSeconds();

        player.sendMessage(Component.text("[Standalone Voice] ", NamedTextColor.AQUA)
            .append(Component.text("Токен подключения " + reason + ".", NamedTextColor.GREEN)));
        player.sendMessage(Component.text("Код: ", NamedTextColor.GRAY)
            .append(Component.text(displayedToken, NamedTextColor.GOLD, TextDecoration.BOLD)));
        player.sendMessage(Component.text(
            "Введите его в приложении в течение " + lifetimeSeconds + " секунд. Новый код можно получить: /voice standalone",
            NamedTextColor.GRAY));
    }
}
