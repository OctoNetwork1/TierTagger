package com.kevin.tiertagger.config;
import com.kevin.tiertagger.TierCache;
import com.kevin.tiertagger.TierTagger;
import com.kevin.tiertagger.model.GameMode;
import com.kevin.tiertagger.model.PlayerDisplay;
import com.kevin.tiertagger.model.TierList;
import com.kevin.tiertagger.util.Toasts;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.uku3lig.ukulib.config.option.*;
import net.uku3lig.ukulib.config.option.widget.ButtonTab;
import net.uku3lig.ukulib.config.screen.TabbedConfigScreen;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
public class TTConfigScreen extends TabbedConfigScreen<TierTaggerConfig> {
    private static final int TAB_SETTINGS = 0;
    private static final int TAB_GAMEMODES = 1;
    private static final int TAB_SUBTIERS = 2;
    private static final int TAB_COLORS = 3;
    private final int startTab;
    public TTConfigScreen(Screen parent) {
        this(parent, TAB_SETTINGS);
    }
    public TTConfigScreen(Screen parent, int startTab) {
        super("OctoTagger Config", parent, TierTagger.getManager());
        this.startTab = startTab;
    }
    @Override
    protected void init() {
        super.init();
        if (startTab > 0) {
            selectTab(startTab);
        }
    }
    @Override
    protected Tab[] getTabs(TierTaggerConfig config) {
        return new Tab[]{new MainSettingsTab(), new McTiersTab(), new SubtiersTab(), new ColorsTab()};
    }
    private void reopen(int tab) {
        setScreenCompat(new TTConfigScreen(this.parent, tab));
    }
    private static void setScreenCompat(Screen screen) {
        Minecraft.getInstance().setScreenAndShow(screen);
    }
    private static WidgetCreator simpleButton(Component text, Button.OnPress action, boolean active) {
        return new SimpleButton(text, action, active);
    }
    private void selectTab(int index) {
        try {
            Field field = TabbedConfigScreen.class.getDeclaredField("tabWidget");
            field.setAccessible(true);
            Object tabBar = field.get(this);
            Method select = null;
            for (Method m : tabBar.getClass().getMethods()) {
                if (m.getName().equals("selectTab") && m.getParameterCount() == 2) {
                    select = m;
                    break;
                }
            }
            if (select == null) {
                for (Method m : tabBar.getClass().getMethods()) {
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length == 2 && p[0] == int.class && p[1] == boolean.class) {
                        select = m;
                        break;
                    }
                }
            }
            if (select != null) {
                select.invoke(tabBar, index, false);
            }
        } catch (ReflectiveOperationException e) {
            TierTagger.getLogger().warn("Could not restore config tab {}", index, e);
        }
    }
    private static CyclingOption<Boolean> coloredBool(String key, boolean value, Consumer<Boolean> setter) {
        return new CyclingOption<>(
                key,
                List.of(Boolean.TRUE, Boolean.FALSE),
                value,
                setter,
                b -> Component.literal(b ? "ON" : "OFF")
                        .withStyle(b ? ChatFormatting.GREEN : ChatFormatting.RED)
        );
    }
    private static List<GameMode> realModes() {
        return TierCache.getGamemodes().stream().filter(m -> !m.isNone()).toList();
    }
    private void openModeSelect(String labelKey, String currentId, boolean allowNone, Consumer<String> setter,
                                String group, int reopenTab) {
        Component title = Component.translatable(labelKey);
        setScreenCompat(new GamemodeSelectScreen(
                this,
                title,
                currentId,
                allowNone,
                group,
                id -> {
                    setter.accept(id);
                    this.manager.saveConfig();
                },
                () -> reopen(reopenTab)
        ));
    }
    private ModeSlotOption slotOption(String labelKey,
                                      java.util.function.Supplier<String> getter,
                                      boolean cycleNone, boolean displayNone,
                                      Consumer<String> setter,
                                      String group, int reopenTab) {
        return new ModeSlotOption(
                labelKey,
                getter,
                cycleNone,
                displayNone,
                group,
                v -> {
                    setter.accept(v);
                    this.manager.saveConfig();
                    PlayerDisplay.scheduleUpload(this.manager.getConfig());
                },
                () -> openModeSelect(labelKey, getter.get(), cycleNone, setter, group, reopenTab)
        );
    }
    public class MainSettingsTab extends ButtonTab<TierTaggerConfig> {
        public MainSettingsTab() {
            super("tiertagger.config", TTConfigScreen.this.manager);
        }
        @Override
        protected WidgetCreator[] getWidgets(TierTaggerConfig config) {
            return new WidgetCreator[]{
                    coloredBool("tiertagger.config.enabled", config.isEnabled(), v -> {
                        config.setEnabled(v);
                        this.manager.saveConfig();
                        reopen(TAB_SETTINGS);
                    }),
                    coloredBool("tiertagger.config.icons", config.isShowIcons(), v -> {
                        config.setShowIcons(v);
                        this.manager.saveConfig();
                        PlayerDisplay.scheduleUpload(config);
                        reopen(TAB_SETTINGS);
                    }),
                    coloredBool("tiertagger.config.playerList", config.isPlayerList(), v -> {
                        config.setPlayerList(v);
                        this.manager.saveConfig();
                        reopen(TAB_SETTINGS);
                    }),
                    CyclingOption.ofEnum("tiertagger.config.highest", TierTaggerConfig.HighestMode.class, config.getHighestMode(), v -> {
                        config.setHighestMode(v);
                        this.manager.saveConfig();
                        PlayerDisplay.scheduleUpload(config);
                        reopen(TAB_SETTINGS);
                    }, v -> Component.translatable(v.getTranslationKey()), OptionInstance.cachedConstantTooltip(Component.translatable("tiertagger.config.highest.desc"))),
                    new SimpleButton("tiertagger.clear", b -> {
                        TierCache.clearCache();
                        Toasts.send(Component.literal("OctoTagger"), Component.literal("Tier cache cleared"));
                        reopen(TAB_SETTINGS);
                    })
            };
        }
    }
    public class McTiersTab extends ButtonTab<TierTaggerConfig> {
        public McTiersTab() {
            super("tiertagger.config.mctiers", TTConfigScreen.this.manager);
        }
        @Override
        protected WidgetCreator[] getWidgets(TierTaggerConfig config) {
            return new WidgetCreator[]{
                    slotOption("tiertagger.config.leftMode", config::getGameModeId, true, true, config::setGameMode, "mctiers", TAB_GAMEMODES),
                    slotOption("tiertagger.config.leftMode2", config::getGameMode2, true, true, config::setGameMode2, "mctiers", TAB_GAMEMODES),
                    slotOption("tiertagger.config.rightMode", config::getSecondGameMode, true, true, config::setSecondGameMode, "mctiers", TAB_GAMEMODES),
                    slotOption("tiertagger.config.rightMode2", config::getSecondGameMode2, true, true, config::setSecondGameMode2, "mctiers", TAB_GAMEMODES)
            };
        }
    }
    public class SubtiersTab extends ButtonTab<TierTaggerConfig> {
        public SubtiersTab() {
            super("tiertagger.config.subtiers", TTConfigScreen.this.manager);
        }
        @Override
        protected WidgetCreator[] getWidgets(TierTaggerConfig config) {
            return new WidgetCreator[]{
                    slotOption("tiertagger.config.leftMode", config::getSubGameMode, true, true, config::setSubGameMode, "subtiers", TAB_SUBTIERS),
                    slotOption("tiertagger.config.leftMode2", config::getSubGameMode2, true, true, config::setSubGameMode2, "subtiers", TAB_SUBTIERS),
                    slotOption("tiertagger.config.rightMode", config::getSubSecondGameMode, true, true, config::setSubSecondGameMode, "subtiers", TAB_SUBTIERS),
                    slotOption("tiertagger.config.rightMode2", config::getSubSecondGameMode2, true, true, config::setSubSecondGameMode2, "subtiers", TAB_SUBTIERS)
            };
        }
    }
    public class TierlistTab extends ButtonTab<TierTaggerConfig> {
        public TierlistTab() {
            super("tiertagger.config.tierlists", TTConfigScreen.this.manager);
        }
        @Override
        protected WidgetCreator[] getWidgets(TierTaggerConfig config) {
            Optional<TierList> current = TierList.findByUrl(config.getApiUrl());
            List<WidgetCreator> widgets = Arrays.stream(TierList.values())
                    .map(t -> {
                        boolean isCurrent = current.isPresent() && current.get() == t;
                        return simpleButton(Component.literal(t.styledName(isCurrent)), b -> {
                            config.setApiUrl(t.getUrl());
                            TierTagger.getManager().saveConfig();
                            reopen(TAB_SETTINGS);
                            TierCache.init();
                            Toasts.send(Component.literal("Tierlist changed to " + t.getName() + "!"), Component.literal("Reloading tiers..."));
                        }, !isCurrent);
                    })
                    .collect(Collectors.toList());
            if (current.isEmpty()) {
                widgets.add(simpleButton(Component.literal("Custom (selected, " + config.getApiUrl() + ")"), b -> {}, false));
            }
            return widgets.toArray(WidgetCreator[]::new);
        }
    }
    public class ColorsTab extends ButtonTab<TierTaggerConfig> {
        protected ColorsTab() {
            super("tiertagger.colors", TTConfigScreen.this.manager);
        }
        @Override
        protected WidgetCreator[] getWidgets(TierTaggerConfig config) {
            Comparator<Map.Entry<String, Integer>> comparator = Comparator.comparing(e -> e.getKey().charAt(2));
            comparator = comparator.thenComparing(e -> e.getKey().charAt(0));
            List<ColorOption> tiers = config.getTierColors().entrySet().stream()
                    .sorted(comparator)
                    .map(e -> new ColorOption(e.getKey(), e.getValue(), val -> {
                        config.getTierColors().put(e.getKey(), val);
                        PlayerDisplay.scheduleUpload(config);
                    }))
                    .collect(Collectors.toList());
            tiers.addLast(new ColorOption("tiertagger.colors.retired", config.getRetiredColor(), val -> {
                config.setRetiredColor(val);
                PlayerDisplay.scheduleUpload(config);
            }));
            return tiers.toArray(WidgetCreator[]::new);
        }
    }
}
