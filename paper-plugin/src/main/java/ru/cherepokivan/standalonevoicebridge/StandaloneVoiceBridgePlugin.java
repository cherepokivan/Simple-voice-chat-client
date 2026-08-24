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
    private BootstrapRelay bootstrapRelay;
    private StandalonePairingListener pairingListener;

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
        bootstrapRelay = createBootstrapRelay();
        pairingListener = new StandalonePairingListener(
            tokenService,
            getConfig().getBoolean("pairing.issue-on-player-join", true),
            bootstrapRelay);
        getServer().getPluginManager().registerEvents(pairingListener, this);

        PluginCommand command = getCommand("voice");
        if (command == null) {
            getLogger().severe("Command 'voice' is missing from plugin.yml; pairing command is disabled.");
        } else {
            StandaloneVoiceCommand executor = new StandaloneVoiceCommand(this, pairingListener);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getLogger().info("Standalone pairing tokens are enabled. Tokens are issued only through authenticated Minecraft sessions.");
        if (bootstrapRelay == null) {
            getLogger().warning("External bootstrap relay is disabled or incomplete; no standalone token will be shown.");
        } else {
            getLogger().info("External bootstrap relay is enabled. The Minecraft host opens no incoming bridge port.");
        }
    }

    void reloadBridgeConfiguration() {
        if (bootstrapRelay != null) {
            bootstrapRelay.close();
            bootstrapRelay = null;
        }

        reloadConfig();
        int tokenLifetimeSeconds = getConfig().getInt("pairing.token-lifetime-seconds", 120);
        int tokenLength = getConfig().getInt("pairing.token-length", 12);
        tokenService = new StandaloneTokenService(Duration.ofSeconds(tokenLifetimeSeconds), tokenLength);
        bootstrapRelay = createBootstrapRelay();
        pairingListener.reconfigure(
            tokenService,
            getConfig().getBoolean("pairing.issue-on-player-join", true),
            bootstrapRelay);

        if (bootstrapRelay == null) {
            getLogger().warning("Standalone bridge configuration was reloaded, but the external relay is disabled or invalid.");
        } else {
            getLogger().info("Standalone bridge configuration and external relay were reloaded.");
        }
    }

    @Override
    public void onDisable() {
        if (bootstrapRelay != null) {
            bootstrapRelay.close();
            bootstrapRelay = null;
        }
    }

    private BootstrapRelay createBootstrapRelay() {
        if (!getConfig().getBoolean("external-bootstrap.enabled", false)) {
            return null;
        }
        try {
            org.bukkit.plugin.Plugin voicechatPlugin = getServer().getPluginManager().getPlugin("voicechat");
            if (voicechatPlugin == null || !voicechatPlugin.isEnabled()) {
                throw new IllegalStateException("Simple Voice Chat plugin is unavailable.");
            }
            ExternalBootstrapClient client = new ExternalBootstrapClient(
                getConfig().getString("external-bootstrap.base-url", ""),
                getConfig().getString("external-bootstrap.server-id", ""),
                getConfig().getString("external-bootstrap.shared-secret", ""));
            SVCReflectionBootstrapIssuer issuer = new SVCReflectionBootstrapIssuer(
                voicechatPlugin,
                getConfig().getString("external-bootstrap.public-voice-host", ""));
            return new BootstrapRelay(
                this,
                tokenService,
                client,
                issuer,
                getConfig().getLong("external-bootstrap.poll-interval-ticks", 20L));
        } catch (RuntimeException exception) {
            getLogger().severe("External bootstrap relay configuration is invalid: " + exception.getMessage());
            return null;
        }
    }
}
