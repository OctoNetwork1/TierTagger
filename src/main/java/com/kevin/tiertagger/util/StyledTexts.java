package com.kevin.tiertagger.util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
public final class StyledTexts {
    public static Component copy(FormattedCharSequence sequence) {
        MutableComponent out = Component.empty();
        StringBuilder buffer = new StringBuilder();
        Style[] current = {Style.EMPTY};
        sequence.accept((index, style, codePoint) -> {
            if (buffer.length() > 0 && !style.equals(current[0])) {
                out.append(Component.literal(buffer.toString()).withStyle(current[0]));
                buffer.setLength(0);
            }
            current[0] = style;
            buffer.appendCodePoint(codePoint);
            return true;
        });
        if (buffer.length() > 0) {
            out.append(Component.literal(buffer.toString()).withStyle(current[0]));
        }
        return out;
    }
    private StyledTexts() {
    }
}
