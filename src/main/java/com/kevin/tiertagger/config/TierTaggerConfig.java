package com.kevin.tiertagger.config;
import com.google.gson.internal.LinkedTreeMap;
import com.kevin.tiertagger.TierCache;
import com.kevin.tiertagger.model.GameMode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TierTaggerConfig implements Serializable {
    private boolean enabled = true;
    private String gameMode = "vanilla";
    private String gameMode2 = "";
    private String secondGameMode = "";
    private String secondGameMode2 = "";
    private String subGameMode = "";
    private String subGameMode2 = "";
    private String subSecondGameMode = "";
    private String subSecondGameMode2 = "";
    private HighestMode highestMode = HighestMode.NOT_FOUND;
    private boolean showIcons = true;
    private boolean playerList = true;
    private boolean swapTiers = false;
    private int retiredColor = 0xa2d6ff;
    private LinkedTreeMap<String, Integer> tierColors = defaultColors();
    private TierPosition tierPosition = TierPosition.BOTH;
    private List<String> visibleGamemodes = new ArrayList<>();
    private boolean showAllGamemodes = false;
    private LinkedTreeMap<String, Boolean> gamemodeSides = new LinkedTreeMap<>();
    private String apiUrl = "https://your-api-url.example.com";
    public String getApiUrl() {
        return this.apiUrl;
    }
    public String getGameModeId() {
        return this.gameMode;
    }
    public String getSecondGameModeId() {
        return this.secondGameMode;
    }
    public List<String> getLeftModeIds() {
        List<String> ids = new ArrayList<>();
        if (this.gameMode != null && !this.gameMode.isBlank()) ids.add(this.gameMode);
        if (this.gameMode2 != null && !this.gameMode2.isBlank() && !ids.contains(this.gameMode2)) ids.add(this.gameMode2);
        return ids;
    }
    public List<String> getRightModeIds() {
        List<String> ids = new ArrayList<>();
        String primary = getSecondGameModeId();
        if (primary != null && !primary.isBlank()) ids.add(primary);
        if (this.secondGameMode2 != null && !this.secondGameMode2.isBlank() && !ids.contains(this.secondGameMode2)) ids.add(this.secondGameMode2);
        return ids;
    }
    public List<String> getSubLeftModeIds() {
        List<String> ids = new ArrayList<>();
        if (this.subGameMode != null && !this.subGameMode.isBlank()) ids.add(this.subGameMode);
        if (this.subGameMode2 != null && !this.subGameMode2.isBlank() && !ids.contains(this.subGameMode2)) ids.add(this.subGameMode2);
        return ids;
    }
    public List<String> getSubRightModeIds() {
        List<String> ids = new ArrayList<>();
        if (this.subSecondGameMode != null && !this.subSecondGameMode.isBlank()) ids.add(this.subSecondGameMode);
        if (this.subSecondGameMode2 != null && !this.subSecondGameMode2.isBlank() && !ids.contains(this.subSecondGameMode2)) ids.add(this.subSecondGameMode2);
        return ids;
    }
    public GameMode getGameMode() {
        if (this.gameMode == null || this.gameMode.isBlank()) {
            return TierCache.getGamemodes().getFirst();
        }
        Optional<GameMode> opt = TierCache.findMode(this.gameMode);
        if (opt.isPresent()) {
            return opt.get();
        } else {
            GameMode first = TierCache.getGamemodes().getFirst();
            if (!first.isNone()) this.gameMode = first.id();
            return first;
        }
    }
    public boolean isModeOn(String modeId) {
        return this.visibleGamemodes.contains(modeId);
    }
    public void setModeOn(String modeId, boolean on) {
        if (on) {
            if (!this.visibleGamemodes.contains(modeId)) {
                this.visibleGamemodes.add(modeId);
            }
        } else {
            this.visibleGamemodes.remove(modeId);
        }
    }
    public boolean isModeRightSide(String modeId) {
        return Boolean.TRUE.equals(this.gamemodeSides.get(modeId));
    }
    public void setModeSide(String modeId, boolean rightSide) {
        if (rightSide) {
            this.gamemodeSides.put(modeId, Boolean.TRUE);
        } else {
            this.gamemodeSides.put(modeId, Boolean.FALSE);
        }
    }
    public void toggleModeSide(String modeId) {
        setModeSide(modeId, !isModeRightSide(modeId));
    }
    private static LinkedTreeMap<String, Integer> defaultColors() {
        LinkedTreeMap<String, Integer> colors = new LinkedTreeMap<>();
        colors.put("HT1", 0xe8ba3a);
        colors.put("LT1", 0xd5b355);
        colors.put("HT2", 0xc4d3e7);
        colors.put("LT2", 0xa0a7b2);
        colors.put("HT3", 0xf89f5a);
        colors.put("LT3", 0xc67b42);
        colors.put("HT4", 0x81749a);
        colors.put("LT4", 0x655b79);
        colors.put("HT5", 0x8f82a8);
        colors.put("LT5", 0x655b79);
        return colors;
    }
    @Getter
    @AllArgsConstructor
    public enum HighestMode {
        NEVER("never", "tiertagger.highest.never"),
        NOT_FOUND("not_found", "tiertagger.highest.not_found"),
        ALWAYS("always", "tiertagger.highest.always"),
        ;
        private final String name;
        private final String translationKey;
    }
    @Getter
    @AllArgsConstructor
    public enum TierPosition {
        LEFT("left", "tiertagger.position.left"),
        RIGHT("right", "tiertagger.position.right"),
        BOTH("both", "tiertagger.position.both"),
        ;
        private final String name;
        private final String translationKey;
    }
}
