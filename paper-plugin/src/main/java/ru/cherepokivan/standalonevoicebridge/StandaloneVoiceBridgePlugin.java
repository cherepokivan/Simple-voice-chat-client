package ru.cherepokivan.standalonevoicebridge;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Minimal, fail-closed integration. It registers with the official Simple Voice Chat API but deliberately
 * exposes no standalone session endpoint: the current public API cannot issue an external UDP bootstrap.
 */
public final class StandaloneVoiceBridgePlugin extends JavaPlugin {

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
        getLogger().warning("Standalone session issuance is intentionally disabled: public SVC API has no supported external bootstrap contract.");

        if (getConfig().getBoolean("api.enabled", false)) {
            getLogger().warning("api.enabled is ignored. No HTTP endpoint is started by this fail-closed bridge.");
        }
    }
}
