package com.kevin.tiertagger.model;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kevin.tiertagger.TierTagger;
import com.kevin.tiertagger.config.TierTaggerConfig;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
@Getter
@AllArgsConstructor
public class PlayerDisplay {
    private static final ScheduledExecutorService UPLOAD_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "tiertagger-layout-upload");
        thread.setDaemon(true);
        return thread;
    });
    private static ScheduledFuture<?> pendingUpload;
    private static volatile boolean startupUploadScheduled = false;
    private static volatile boolean sharedOnce = false;
    private final List<String> leftModeIds;
    private final List<String> rightModeIds;
    private final List<String> subLeftModeIds;
    private final List<String> subRightModeIds;
    private final TierTaggerConfig.TierPosition position;
    private final Boolean showIcons;
    private final TierTaggerConfig.HighestMode highestMode;
    private final Map<String, Integer> tierColors;
    private final Integer retiredColor;
    public static CompletableFuture<PlayerDisplay> fetch(HttpClient client, UUID uuid) {
        String endpoint = TierTagger.getManager().getConfig().getApiUrl() + "/v2/profile/" + uuid + "/display";
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> r.statusCode() == 200 ? parse(r.body()) : null)
                .exceptionally(t -> {
                    TierTagger.getLogger().debug("Error getting settings of {}", uuid, t);
                    return null;
                });
    }
    public static PlayerDisplay parse(String json) {
        try {
            if (json == null || json.isBlank()) return null;
            JsonObject obj = TierTagger.GSON.fromJson(json, JsonObject.class);
            if (obj == null || obj.size() == 0) return null;
            List<String> left = readSide(obj, "left");
            List<String> right = readSide(obj, "right");
            List<String> subLeft = readSide(obj, "sub_left");
            List<String> subRight = readSide(obj, "sub_right");
            return new PlayerDisplay(left, right, subLeft, subRight, readPosition(obj),
                    readBoolean(obj, "show_icons"), readHighest(obj), readColors(obj), readInt(obj, "retired_color"));
        } catch (Exception e) {
            TierTagger.getLogger().debug("Could not parse settings {}", json, e);
            return null;
        }
    }
    public RenderProfile toRenderProfile(TierTaggerConfig fallback) {
        Map<String, Integer> colors = (tierColors == null || tierColors.isEmpty()) ? fallback.getTierColors() : tierColors;
        return new RenderProfile(leftModeIds, rightModeIds,
                subLeftModeIds == null ? List.of() : subLeftModeIds,
                subRightModeIds == null ? List.of() : subRightModeIds,
                position,
                showIcons != null ? showIcons : fallback.isShowIcons(),
                highestMode != null ? highestMode : fallback.getHighestMode(),
                colors,
                retiredColor != null ? retiredColor : fallback.getRetiredColor());
    }
    private static List<String> readSide(JsonObject obj, String key) {
        List<String> ids = new ArrayList<>();
        if (!obj.has(key) || !obj.get(key).isJsonArray()) return ids;
        JsonArray array = obj.getAsJsonArray(key);
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive()) continue;
            String id = element.getAsString();
            if (id != null && !id.isBlank() && !ids.contains(id)) {
                ids.add(id);
            }
            if (ids.size() >= 2) break;
        }
        return ids;
    }
    private static TierTaggerConfig.TierPosition readPosition(JsonObject obj) {
        if (!obj.has("position") || !obj.get("position").isJsonPrimitive()) {
            return TierTaggerConfig.TierPosition.BOTH;
        }
        String raw = obj.get("position").getAsString();
        for (TierTaggerConfig.TierPosition candidate : TierTaggerConfig.TierPosition.values()) {
            if (candidate.getName().equalsIgnoreCase(raw) || candidate.name().equalsIgnoreCase(raw)) {
                return candidate;
            }
        }
        return TierTaggerConfig.TierPosition.BOTH;
    }
    private static Boolean readBoolean(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) return null;
        try {
            return obj.get(key).getAsBoolean();
        } catch (Exception e) {
            return null;
        }
    }
    private static Integer readInt(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) return null;
        try {
            return obj.get(key).getAsInt();
        } catch (Exception e) {
            return null;
        }
    }
    private static TierTaggerConfig.HighestMode readHighest(JsonObject obj) {
        if (!obj.has("highest_mode") || !obj.get("highest_mode").isJsonPrimitive()) return null;
        String raw = obj.get("highest_mode").getAsString();
        for (TierTaggerConfig.HighestMode candidate : TierTaggerConfig.HighestMode.values()) {
            if (candidate.getName().equalsIgnoreCase(raw) || candidate.name().equalsIgnoreCase(raw)) {
                return candidate;
            }
        }
        return null;
    }
    private static Map<String, Integer> readColors(JsonObject obj) {
        if (!obj.has("tier_colors") || !obj.get("tier_colors").isJsonObject()) return null;
        JsonObject colors = obj.getAsJsonObject("tier_colors");
        Map<String, Integer> out = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : colors.entrySet()) {
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isNumber()) continue;
            try {
                out.put(entry.getKey(), entry.getValue().getAsInt());
            } catch (Exception ignored) {
            }
        }
        return out.isEmpty() ? null : out;
    }
    public static CompletableFuture<Integer> upload(TierTaggerConfig config) {
        UUID uuid = ownUuid();
        if (uuid == null) {
            return CompletableFuture.completedFuture(-1);
        }
        JsonObject body = new JsonObject();
        body.addProperty("name", ownName());
        body.add("left", toArray(config.getLeftModeIds()));
        body.add("right", toArray(config.getRightModeIds()));
        body.add("sub_left", toArray(config.getSubLeftModeIds()));
        body.add("sub_right", toArray(config.getSubRightModeIds()));
        body.addProperty("position", config.getTierPosition().getName());
        body.addProperty("show_icons", config.isShowIcons());
        body.addProperty("highest_mode", config.getHighestMode().getName());
        body.addProperty("retired_color", config.getRetiredColor());
        body.add("tier_colors", toColorObject(config.getTierColors()));
        String endpoint = config.getApiUrl() + "/v2/profile/" + uuid + "/display";
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return TierTagger.getClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> r.statusCode())
                .exceptionally(t -> {
                    TierTagger.getLogger().warn("Could not upload nametag settings", t);
                    return -1;
                });
    }
    public static void scheduleUpload(TierTaggerConfig config) {
        ScheduledFuture<?> previous = pendingUpload;
        if (previous != null) {
            previous.cancel(false);
        }
        pendingUpload = UPLOAD_SCHEDULER.schedule(() -> upload(config).thenAccept(code -> {
            TierTagger.getLogger().info("Settings upload finished with HTTP {}", code);
            if (code != null && code >= 200 && code < 300) {
                sharedOnce = true;
            }
        }), 800, TimeUnit.MILLISECONDS);
    }
    public static void cancelScheduledUpload() {
        ScheduledFuture<?> previous = pendingUpload;
        if (previous != null) {
            previous.cancel(false);
            pendingUpload = null;
        }
    }
    public static void scheduleStartupUpload() {
        if (startupUploadScheduled) return;
        startupUploadScheduled = true;
        UPLOAD_SCHEDULER.scheduleWithFixedDelay(() -> {
            if (sharedOnce) return;
            if (ownUuid() == null) return;
            upload(TierTagger.getManager().getConfig()).thenAccept(code -> {
                if (code != null && code >= 200 && code < 300) {
                    sharedOnce = true;
                    TierTagger.getLogger().info("Shared my nametag settings with other players (HTTP {})", code);
                } else {
                    TierTagger.getLogger().info("Sharing nametag settings not possible yet (HTTP {})", code);
                }
            });
        }, 15, 30, TimeUnit.SECONDS);
    }
    private static JsonArray toArray(List<String> ids) {
        JsonArray array = new JsonArray();
        if (ids != null) {
            for (String id : ids) {
                array.add(id);
            }
        }
        return array;
    }
    private static JsonObject toColorObject(Map<String, Integer> colors) {
        JsonObject out = new JsonObject();
        if (colors != null) {
            colors.forEach(out::addProperty);
        }
        return out;
    }
    private static String ownName() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getUser() == null) {
            return "";
        }
        try {
            return minecraft.getUser().getName();
        } catch (Exception e) {
            return "";
        }
    }
    private static UUID ownUuid() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getUser() == null) {
            return null;
        }
        try {
            return minecraft.getUser().getProfileId();
        } catch (Exception e) {
            TierTagger.getLogger().warn("Could not read own uuid", e);
            return null;
        }
    }
}
