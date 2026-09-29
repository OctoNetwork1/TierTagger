package com.kevin.tiertagger;
import com.kevin.tiertagger.model.GameMode;
import com.kevin.tiertagger.model.PlayerDisplay;
import com.kevin.tiertagger.model.PlayerInfo;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
public class TierCache {
    private static final List<GameMode> GAMEMODES = new ArrayList<>();
    private static final Map<UUID, Optional<Map<String, PlayerInfo.Ranking>>> TIERS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_FETCH_TIMES = new ConcurrentHashMap<>();
    private static final Map<UUID, Optional<PlayerDisplay>> LAYOUTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_LAYOUT_FETCH_TIMES = new ConcurrentHashMap<>();
    private static final long RETRY_COOLDOWN_MS = 5000L;
    private static final long LAYOUT_REFRESH_MS = 2500L;
    private static volatile long lastInitAttempt = 0L;
    private static volatile boolean initRunning = false;
    public static void init() {
        long now = System.currentTimeMillis();
        if (initRunning || now - lastInitAttempt < RETRY_COOLDOWN_MS) {
            return;
        }
        lastInitAttempt = now;
        initRunning = true;
        try {
            GAMEMODES.clear();
            List<GameMode> modes = new ArrayList<>(GameMode.fetchGamemodes(TierTagger.getClient()).get());
            appendMissing(modes);
            GAMEMODES.addAll(modes);
            TierTagger.getLogger().info("Found {} tierlists: {}", GAMEMODES.size(), GAMEMODES.stream().map(GameMode::id).toList());
        } catch (ExecutionException e) {
            TierTagger.getLogger().error("Failed to load gamemodes!", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (GAMEMODES.isEmpty()) {
                GAMEMODES.addAll(GameMode.FALLBACK);
                TierTagger.getLogger().info("Using built in gamemode list ({} modes)", GAMEMODES.size());
            }
            initRunning = false;
        }
    }
    private static void appendMissing(List<GameMode> modes) {
        Set<String> known = new HashSet<>();
        for (GameMode mode : modes) {
            known.add(mode.id());
        }
        for (GameMode fallback : GameMode.FALLBACK) {
            if (!known.contains(fallback.id())) {
                modes.add(fallback);
                known.add(fallback.id());
            }
        }
    }
    public static List<GameMode> getGamemodes() {
        if (GAMEMODES.isEmpty()) {
            init();
        }
        if (GAMEMODES.isEmpty()) {
            return Collections.singletonList(GameMode.NONE);
        }
        return GAMEMODES;
    }
    public static Optional<Map<String, PlayerInfo.Ranking>> getPlayerRankings(UUID uuid) {
        long now = System.currentTimeMillis();
        Long lastFetch = LAST_FETCH_TIMES.get(uuid);
        if (uuid.version() == 4 && (lastFetch == null || now - lastFetch > 1000)) {
            LAST_FETCH_TIMES.put(uuid, now);
            PlayerInfo.getRankings(TierTagger.getClient(), uuid).thenAccept(info -> TIERS.put(uuid, Optional.ofNullable(info)));
        }
        return TIERS.getOrDefault(uuid, Optional.empty());
    }
    public static Optional<PlayerDisplay> getDisplay(UUID uuid) {
        if (uuid.version() != 4) return Optional.empty();
        long now = System.currentTimeMillis();
        Long lastFetch = LAST_LAYOUT_FETCH_TIMES.get(uuid);
        if (lastFetch == null || now - lastFetch > LAYOUT_REFRESH_MS) {
            LAST_LAYOUT_FETCH_TIMES.put(uuid, now);
            PlayerDisplay.fetch(TierTagger.getClient(), uuid)
                    .thenAccept(layout -> LAYOUTS.put(uuid, Optional.ofNullable(layout)));
        }
        return LAYOUTS.getOrDefault(uuid, Optional.empty());
    }
    public static CompletableFuture<PlayerInfo> searchPlayer(String query) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return lookupProfile(query);
            } catch (Exception e) {
                return Optional.<com.mojang.authlib.GameProfile>empty();
            }
        }).thenCompose(optProfile -> {
            if (optProfile.isPresent()) {
                UUID uuid = optProfile.get().id();
                return PlayerInfo.get(TierTagger.getClient(), uuid).thenApply(p -> {
                    TIERS.put(uuid, Optional.ofNullable(p.rankings()));
                    LAST_FETCH_TIMES.put(uuid, System.currentTimeMillis());
                    return p;
                });
            } else {
                return PlayerInfo.search(TierTagger.getClient(), query).thenApply(p -> {
                    UUID uuid = parseUUID(p.uuid());
                    TIERS.put(uuid, Optional.ofNullable(p.rankings()));
                    LAST_FETCH_TIMES.put(uuid, System.currentTimeMillis());
                    return p;
                });
            }
        });
    }
    public static Optional<com.mojang.authlib.GameProfile> lookupProfile(String name) {
        return net.minecraft.client.Minecraft.getInstance().services().profileResolver().fetchByName(name);
    }
    public static void clearCache() {
        TIERS.clear();
        LAST_FETCH_TIMES.clear();
        LAYOUTS.clear();
        LAST_LAYOUT_FETCH_TIMES.clear();
        lastInitAttempt = 0L;
        init();
    }
    public static GameMode findNextMode(GameMode current) {
        if (GAMEMODES.isEmpty()) {
            return GameMode.NONE;
        } else {
            return GAMEMODES.get((GAMEMODES.indexOf(current) + 1) % GAMEMODES.size());
        }
    }
    public static Optional<GameMode> findMode(String id) {
        return GAMEMODES.stream().filter(m -> m.id().equalsIgnoreCase(id)).findFirst();
    }
    public static GameMode findModeOrUgly(String id) {
        return findMode(id).orElseGet(() -> new GameMode(id, id, "mctiers"));
    }
    private static UUID parseUUID(String uuid) {
        try {
            return UUID.fromString(uuid);
        } catch (Exception e) {
            long mostSignificant = Long.parseUnsignedLong(uuid.substring(0, 16), 16);
            long leastSignificant = Long.parseUnsignedLong(uuid.substring(16), 16);
            return new UUID(mostSignificant, leastSignificant);
        }
    }
    private TierCache() {
    }
}
