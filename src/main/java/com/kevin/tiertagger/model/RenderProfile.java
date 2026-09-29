package com.kevin.tiertagger.model;
import com.kevin.tiertagger.TierCache;
import com.kevin.tiertagger.config.TierTaggerConfig;
import java.util.List;
import java.util.Map;
import java.util.UUID;
public record RenderProfile(List<String> leftModeIds,
                            List<String> rightModeIds,
                            List<String> subLeftModeIds,
                            List<String> subRightModeIds,
                            TierTaggerConfig.TierPosition position,
                            boolean showIcons,
                            TierTaggerConfig.HighestMode highestMode,
                            Map<String, Integer> tierColors,
                            int retiredColor) {
    public static RenderProfile of(TierTaggerConfig config) {
        return new RenderProfile(config.getLeftModeIds(), config.getRightModeIds(),
                config.getSubLeftModeIds(), config.getSubRightModeIds(),
                config.getTierPosition(), config.isShowIcons(), config.getHighestMode(),
                config.getTierColors(), config.getRetiredColor());
    }
    public static RenderProfile forPlayer(UUID uuid, TierTaggerConfig viewerConfig) {
        PlayerDisplay shared = TierCache.getDisplay(uuid).orElse(null);
        return shared == null ? of(viewerConfig) : shared.toRenderProfile(viewerConfig);
    }
    public int tierColor(String tier) {
        if (tier.startsWith("R")) {
            return retiredColor;
        }
        return tierColors.getOrDefault(tier, 0xD3D3D3);
    }
}
