package com.kevin.tiertagger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kevin.tiertagger.config.TierTaggerConfig;
import com.kevin.tiertagger.model.GameMode;
import com.kevin.tiertagger.model.PlayerDisplay;
import com.kevin.tiertagger.model.PlayerInfo;
import com.kevin.tiertagger.model.RenderProfile;
import com.mojang.brigadier.context.CommandContext;
import lombok.Getter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.uku3lig.ukulib.config.ConfigManager;
import net.uku3lig.ukulib.fabric.PlayerArgumentType;
import net.uku3lig.ukulib.utils.Ukutils;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;
public class TierTagger implements ModInitializer {
    public static final String MOD_ID = "axotiers-tiertagger";
    private static final String UPDATE_URL_FORMAT = "https://api.modrinth.com/v2/project/dpkYdLu5/version?game_versions=%s";
    public static final Gson GSON = new GsonBuilder().create();
    @Getter
    private static final ConfigManager<TierTaggerConfig> manager = ConfigManager.createDefault(TierTaggerConfig.class, MOD_ID);
    @Getter
    private static final Logger logger = LoggerFactory.getLogger(TierTagger.class);
    @Getter
    private static final HttpClient client = HttpClient.newHttpClient();
    @Getter
    private static Version latestVersion = null;
    private static final AtomicBoolean isObsolete = new AtomicBoolean(false);
    @Override
    public void onInitialize() {
        TierCache.init();
        PlayerDisplay.scheduleStartupUpload();
        try {
            ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                    literal("Tier")
                            .then(argument("player", PlayerArgumentType.player())
                                    .executes(TierTagger::displayTierInfo))));
        } catch (LinkageError e) {
            logger.warn("fabric-command-api-v2 not present, /Tier command disabled", e);
        }
        try {
            Ukutils.registerKeybinding(new KeyMapping("tiertagger.keybind.gamemode", GLFW.GLFW_KEY_UNKNOWN, KeyMapping.Category.register(Identifier.fromNamespaceAndPath("tiertagger", "key"))),
                    mc -> {
                        GameMode next = TierCache.findNextMode(manager.getConfig().getGameMode());
                        manager.getConfig().setGameMode(next.id());
                        if (mc.player != null) {
                            Component message = Component.literal("Displayed gamemode: ").append(next.asStyled(false));
                            mc.player.sendOverlayMessage(message);
                        }
                    });
        } catch (LinkageError e) {
            logger.warn("fabric key binding / lifecycle API not present, gamemode keybind disabled", e);
        }
        checkForUpdates();
    }
    public static Component appendTier(UUID uuid, Component text) {
        TierTaggerConfig config = manager.getConfig();
        Optional<PlayerDisplay> stored = TierCache.getDisplay(uuid);
        if (!config.isEnabled() && stored.isEmpty()) return text;
        Optional<Map<String, PlayerInfo.Ranking>> rankingsOpt = TierCache.getPlayerRankings(uuid);
        if (rankingsOpt.isEmpty()) return text;
        Map<String, PlayerInfo.Ranking> rankings = rankingsOpt.get();
        RenderProfile profile = stored.map(s -> s.toRenderProfile(config)).orElseGet(() -> RenderProfile.of(config));
        Component leftTier = joinGroups(tiersForSide(rankings, profile.leftModeIds(), profile),
                tiersForSide(rankings, profile.subLeftModeIds(), profile));
        Component rightTier = joinGroups(tiersForSide(rankings, profile.rightModeIds(), profile),
                tiersForSide(rankings, profile.subRightModeIds(), profile));
        return switch (profile.position()) {
            case LEFT -> leftTier == null ? text : formatWithPosition(text, leftTier, leftTier, TierTaggerConfig.TierPosition.LEFT);
            case RIGHT -> rightTier == null ? text : formatWithPosition(text, rightTier, rightTier, TierTaggerConfig.TierPosition.RIGHT);
            case BOTH -> {
                if (leftTier == null && rightTier == null) yield text;
                if (rightTier == null) yield formatWithPosition(text, leftTier, leftTier, TierTaggerConfig.TierPosition.LEFT);
                if (leftTier == null) yield formatWithPosition(text, rightTier, rightTier, TierTaggerConfig.TierPosition.RIGHT);
                yield formatWithPosition(text, leftTier, rightTier, TierTaggerConfig.TierPosition.BOTH);
            }
        };
    }
    private static Component joinGroups(Component mcTiers, Component subTiers) {
        if (mcTiers == null) return subTiers;
        if (subTiers == null) return mcTiers;
        MutableComponent joined = mcTiers.copy();
        joined.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY));
        joined.append(subTiers);
        return joined;
    }
    private static Component tiersForSide(Map<String, PlayerInfo.Ranking> rankings, List<String> modeIds, RenderProfile profile) {
        List<Component> tiers = new ArrayList<>();
        for (String modeId : modeIds) {
            Component tier = tierForMode(rankings, modeId, profile);
            if (tier != null) tiers.add(tier);
        }
        if (tiers.isEmpty()) return null;
        MutableComponent joined = tiers.getFirst().copy();
        for (int i = 1; i < tiers.size(); i++) {
            joined.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY));
            joined.append(tiers.get(i));
        }
        return joined;
    }
    private static Component tierForMode(Map<String, PlayerInfo.Ranking> rankings, String modeId, RenderProfile profile) {
        GameMode mode = TierCache.findMode(modeId).orElse(null);
        PlayerInfo.Ranking ranking = mode != null ? rankings.get(mode.id()) : null;
        Optional<PlayerInfo.NamedRanking> highest = PlayerInfo.getHighestRanking(rankings);
        if (ranking == null) {
            if (profile.highestMode() != TierTaggerConfig.HighestMode.NEVER && highest.isPresent()) {
                return rankedComponent(highest.get().mode(), highest.get().ranking(), profile);
            }
            return null;
        }
        if (profile.highestMode() == TierTaggerConfig.HighestMode.ALWAYS && highest.isPresent()) {
            return rankedComponent(highest.get().mode(), highest.get().ranking(), profile);
        }
        return rankedComponent(mode, ranking, profile);
    }
    private static Component rankedComponent(GameMode mode, PlayerInfo.Ranking ranking, RenderProfile profile) {
        Component tierText = getRankingText(ranking, false, profile);
        if (profile.showIcons() && mode != null && mode.icon().isPresent()) {
            tierText = Component.literal(mode.icon().get().toString()).append(tierText);
        }
        return tierText;
    }
    private static Component formatWithPosition(Component nameText, Component tierText, TierTaggerConfig.TierPosition position) {
        return formatWithPosition(nameText, tierText, tierText, position);
    }
    private static Component formatWithPosition(Component nameText, Component leftText, Component rightText, TierTaggerConfig.TierPosition position) {
        return switch (position) {
            case LEFT -> {
                MutableComponent result = leftText.copy();
                result.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY));
                yield result.append(nameText);
            }
            case RIGHT -> {
                MutableComponent result = Component.empty().append(nameText);
                result.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY));
                yield result.append(rightText);
            }
            case BOTH -> {
                MutableComponent result = leftText.copy();
                result.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY));
                result.append(nameText);
                result.append(Component.literal(" | ").withStyle(ChatFormatting.GRAY));
                yield result.append(rightText);
            }
        };
    }
    public static Optional<PlayerInfo.NamedRanking> getPlayerTier(UUID uuid) {
        return getPlayerTier(uuid, manager.getConfig().getGameModeId());
    }
    public static Optional<PlayerInfo.NamedRanking> getPlayerTier(UUID uuid, String modeId) {
        String modeKey = (modeId == null || modeId.isBlank()) ? manager.getConfig().getGameModeId() : modeId;
        GameMode mode = TierCache.findMode(modeKey).orElse(null);
        return TierCache.getPlayerRankings(uuid).map(rankings -> {
            TierTaggerConfig.HighestMode highestMode = manager.getConfig().getHighestMode();
            Optional<PlayerInfo.NamedRanking> highest = PlayerInfo.getHighestRanking(rankings);
            PlayerInfo.Ranking ranking = mode != null ? rankings.get(mode.id()) : null;
            if (ranking == null) {
                if (highestMode != TierTaggerConfig.HighestMode.NEVER && highest.isPresent()) {
                    return highest.get();
                } else {
                    return null;
                }
            } else {
                if (highestMode == TierTaggerConfig.HighestMode.ALWAYS && highest.isPresent()) {
                    return highest.get();
                } else {
                    return ranking.asNamed(mode);
                }
            }
        });
    }
    private static MutableComponent getTierText(int tier, int pos, boolean retired, RenderProfile profile) {
        StringBuilder text = new StringBuilder();
        if (retired) text.append("R");
        text.append(pos == 0 ? "H" : "L").append("T").append(tier);
        int color = profile.tierColor(text.toString());
        return Component.literal(text.toString()).withStyle(s -> s.withColor(color));
    }
    public static Component getRankingText(PlayerInfo.Ranking ranking, boolean showPeak) {
        return getRankingText(ranking, showPeak, RenderProfile.of(manager.getConfig()));
    }
    public static Component getRankingText(PlayerInfo.Ranking ranking, boolean showPeak, RenderProfile profile) {
        if (ranking.retired() && ranking.peakTier() != null && ranking.peakPos() != null) {
            return getTierText(ranking.peakTier(), ranking.peakPos(), true, profile);
        } else {
            MutableComponent tierText = getTierText(ranking.tier(), ranking.pos(), false, profile);
            if (showPeak && ranking.comparablePeak() < ranking.comparableTier()) {
                tierText.append(Component.literal(" (peak: ").withStyle(s -> s.withColor(ChatFormatting.GRAY)))
                        .append(getTierText(ranking.peakTier(), ranking.peakPos(), false, profile))
                        .append(Component.literal(")").withStyle(s -> s.withColor(ChatFormatting.GRAY)));
            }
            return tierText;
        }
    }
    private static int displayTierInfo(CommandContext<FabricClientCommandSource> ctx) {
        PlayerArgumentType.PlayerSelector selector = ctx.getArgument("player", PlayerArgumentType.PlayerSelector.class);
        Optional<Map<String, PlayerInfo.Ranking>> rankings = ctx.getSource().getLevel().players().stream()
                .filter(p -> p.getScoreboardName().equalsIgnoreCase(selector.name()) || p.getStringUUID().equalsIgnoreCase(selector.name()))
                .findFirst()
                .map(Entity::getUUID)
                .flatMap(TierCache::getPlayerRankings);
        if (rankings.isPresent()) {
            ctx.getSource().sendFeedback(printPlayerInfo(selector.name(), rankings.get()));
        } else {
            ctx.getSource().sendFeedback(Component.literal("[OctoTagger] Searching..."));
            TierCache.searchPlayer(selector.name())
                    .thenAccept(p -> Minecraft.getInstance().execute(() -> ctx.getSource().sendFeedback(printPlayerInfo(selector.name(), p.rankings()))))
                    .exceptionally(t -> {
                        ctx.getSource().sendError(Component.literal("Could not find player " + selector.name()));
                        return null;
                    });
        }
        return 0;
    }
    private static Component printPlayerInfo(String name, Map<String, PlayerInfo.Ranking> rankings) {
        if (rankings.isEmpty()) {
            return Component.literal(name + " does not have any tiers.");
        } else {
            MutableComponent text = Component.empty().append("=== Rankings for " + name + " ===");
            rankings.forEach((m, r) -> {
                if (m == null) return;
                GameMode mode = TierCache.findModeOrUgly(m);
                Component tierText = getRankingText(r, true);
                text.append(Component.literal("\n").append(mode.asStyled(true)).append(": ").append(tierText));
            });
            return text;
        }
    }
    public static int getTierColor(String tier) {
        if (tier.startsWith("R")) {
            return manager.getConfig().getRetiredColor();
        } else {
            return manager.getConfig().getTierColors().getOrDefault(tier, 0xD3D3D3);
        }
    }
    private static void checkForUpdates() {
        String versionParam = "[\"%s\"]".formatted(SharedConstants.getCurrentVersion().name());
        String fullUrl = UPDATE_URL_FORMAT.formatted(URLEncoder.encode(versionParam, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(fullUrl)).GET().build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> {
                    String body = r.body();
                    JsonArray array = GSON.fromJson(body, JsonArray.class);
                    if (!array.isEmpty()) {
                        JsonObject root = array.get(0).getAsJsonObject();
                        String versionName = root.get("name").getAsString();
                        if (versionName != null && versionName.toLowerCase(Locale.ROOT).startsWith("[o")) {
                            isObsolete.set(true);
                        }
                        String latestVer = root.get("version_number").getAsString();
                        try {
                            return Version.parse(latestVer);
                        } catch (VersionParsingException e) {
                            logger.warn("Could not parse version number {}", latestVer);
                        }
                    }
                    return null;
                })
                .exceptionally(t -> {
                    logger.warn("Error checking for updates", t);
                    return null;
                }).thenAccept(v -> {
                    logger.info("Found latest version {}", v.getFriendlyString());
                    latestVersion = v;
                });
    }
    public static boolean isObsolete() {
        return isObsolete.get();
    }
}
