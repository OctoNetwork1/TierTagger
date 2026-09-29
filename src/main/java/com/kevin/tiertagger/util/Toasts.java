package com.kevin.tiertagger.util;
import net.minecraft.network.chat.Component;
import net.uku3lig.ukulib.utils.Ukutils;
public final class Toasts {
    private Toasts() {
    }
    public static void send(Component title, Component text) {
        Ukutils.sendToast(title, text);
    }
}
