package ru.cherepokivan.standalonevoicebridge;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded asynchronous poller for one token at a time. It does not open any incoming port on the
 * Minecraft host; all network traffic is outbound HTTPS to the Vercel relay.
 */
final class BootstrapRelay {
    private final JavaPlugin plugin;
    private final StandaloneTokenService tokenService;
    private final ExternalBootstrapClient bridge;
    private final SVCReflectionBootstrapIssuer issuer;
    private final long pollIntervalTicks;
    private final Map<String, PendingToken> pending = new ConcurrentHashMap<>();

    BootstrapRelay(
        JavaPlugin plugin,
        StandaloneTokenService tokenService,
        ExternalBootstrapClient bridge,
        SVCReflectionBootstrapIssuer issuer,
        long pollIntervalTicks
    ) {
        this.plugin = plugin;
        this.tokenService = tokenService;
        this.bridge = bridge;
        this.issuer = issuer;
        this.pollIntervalTicks = Math.max(20L, pollIntervalTicks);
    }

    void register(UUID playerUuid, String token, Runnable onRegistered, Runnable onFailure) {
        String tokenHash = BridgeCrypto.sha256Base64Url(token);
        PendingToken entry = new PendingToken(playerUuid, token, System.currentTimeMillis() + tokenService.lifetime().toMillis());
        pending.put(tokenHash, entry);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                bridge.register(tokenHash, playerUuid);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    startPolling(tokenHash);
                    onRegistered.run();
                });
            } catch (RuntimeException exception) {
                pending.remove(tokenHash);
                tokenService.revoke(playerUuid);
                plugin.getLogger().warning("Could not register a standalone pairing token with the external relay: " + safeFailureReason(exception));
                Bukkit.getScheduler().runTask(plugin, onFailure);
            }
        });
    }

    void close() {
        pending.values().forEach(PendingToken::cancel);
        pending.clear();
    }

    private void startPolling(String tokenHash) {
        PendingToken entry = pending.get(tokenHash);
        if (entry == null || entry.task != null) {
            return;
        }
        entry.task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> poll(tokenHash), pollIntervalTicks, pollIntervalTicks);
    }

    private void poll(String tokenHash) {
        PendingToken entry = pending.get(tokenHash);
        if (entry == null) {
            return;
        }
        if (System.currentTimeMillis() >= entry.deadlineEpochMs) {
            remove(tokenHash);
            return;
        }
        try {
            ExternalBootstrapClient.BridgeClaim claim = bridge.check(tokenHash);
            if (claim.claimed() && claim.requestId() != null) {
                cancelTask(entry);
                Bukkit.getScheduler().runTask(plugin, () -> issueAndComplete(tokenHash, claim.requestId()));
            }
        } catch (RuntimeException ignored) {
            // The bounded task retries until expiry; no sensitive payload is logged.
        }
    }

    private void issueAndComplete(String tokenHash, String requestId) {
        PendingToken entry = pending.get(tokenHash);
        if (entry == null) {
            return;
        }
        Optional<UUID> owner = tokenService.consume(entry.token);
        if (owner.isEmpty() || !owner.get().equals(entry.playerUuid)) {
            remove(tokenHash);
            return;
        }
        try {
            String bootstrapJson = issuer.issue(entry.playerUuid).toJson();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    bridge.complete(tokenHash, requestId, bootstrapJson);
                } catch (RuntimeException exception) {
                    plugin.getLogger().warning("Could not deliver a standalone bootstrap to the external relay.");
                } finally {
                    remove(tokenHash);
                }
            });
        } catch (RuntimeException exception) {
            remove(tokenHash);
            plugin.getLogger().warning("Could not issue a standalone bootstrap for the installed SVC version.");
        }
    }

    private void remove(String tokenHash) {
        PendingToken entry = pending.remove(tokenHash);
        if (entry != null) {
            cancelTask(entry);
        }
    }

    private static String safeFailureReason(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        // Error messages are deliberately limited to transport and HTTP metadata; request bodies and secrets are never logged.
        return message.replaceAll("[\\r\\n]+", " ");
    }

    private static void cancelTask(PendingToken entry) {
        if (entry.task != null) {
            entry.task.cancel();
            entry.task = null;
        }
    }

    private static final class PendingToken {
        private final UUID playerUuid;
        private final String token;
        private final long deadlineEpochMs;
        private volatile BukkitTask task;

        private PendingToken(UUID playerUuid, String token, long deadlineEpochMs) {
            this.playerUuid = playerUuid;
            this.token = token;
            this.deadlineEpochMs = deadlineEpochMs;
        }

        private void cancel() {
            cancelTask(this);
        }
    }
}
