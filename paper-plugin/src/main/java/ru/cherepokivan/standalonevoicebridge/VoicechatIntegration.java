package ru.cherepokivan.standalonevoicebridge;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import java.util.Objects;

final class VoicechatIntegration implements VoicechatPlugin {
    private final StandaloneVoiceBridgePlugin plugin;

    VoicechatIntegration(StandaloneVoiceBridgePlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public String getPluginId() {
        return "standalone_voice_bridge";
    }

    @Override
    public void initialize(VoicechatApi voicechatApi) {
        Objects.requireNonNull(voicechatApi, "voicechatApi");
        plugin.getLogger().info("Simple Voice Chat API initialized. No credentials, UUIDs, group passwords, or session secrets are read or emitted.");
    }
}
