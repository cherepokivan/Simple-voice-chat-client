package ru.cherepokivan.standalonevoicebridge;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;

/**
 * Minimal, fail-closed integration. It registers with the official Simple Voice Chat API but deliberately
 * exposes no standalone session endpoint: the current public API cannot issue an external UDP bootstrap.
 */
public final class StandaloneVoiceBridgePlugin extends JavaPlugin {
    private StandaloneTokenService tokenService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getConfig().getBoolean("enabled", true)) {
            getLogger().info("StandaloneVoiceBridge is disabled by configuration.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        BukkitVoicechatService service = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            getLogger().severe("Simple Voice Chat API service is unavailable; bridge will be disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        service.registerPlugin(new VoicechatIntegration(this));
        getLogger().info("Registered with the official Simple Voice Chat API.");

        int tokenLifetimeSeconds = getConfig().getInt("pairing.token-lifetime-seconds", 120);
        int tokenLength = getConfig().getInt("pairing.token-length", 12);
        tokenService = new StandaloneTokenService(Duration.ofSeconds(tokenLifetimeSeconds), tokenLength);
        StandalonePairingListener pairingListener = new StandalonePairingListener(
            tokenService,
            getConfig().getBoolean("pairing.issue-on-player-join", true));
        getServer().getPluginManager().registerEvents(pairingListener, this);

        PluginCommand command = getCommand("voice");
        if (command == null) {
            getLogger().severe("Command 'voice' is missing from plugin.yml; pairing command is disabled.");
        } else {
            StandaloneVoiceCommand executor = new StandaloneVoiceCommand(pairingListener);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getLogger().info("Standalone pairing tokens are enabled. Tokens are issued only through authenticated Minecraft sessions.");
        getLogger().warning("An external bootstrap endpoint remains disabled until a version-specific, authenticated SVC adapter is implemented.");

        if (getConfig().getBoolean("api.enabled", false)) {
            getLogger().warning("api.enabled is ignored. No HTTP endpoint is started by this fail-closed bridge.");
        }
    }
}
