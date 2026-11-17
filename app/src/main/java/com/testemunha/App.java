package com.testemunha;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

/** Estado global leve: controla quando o cofre deve pedir o PIN de novo. */
public class App extends Application {

    private static long unlockedUntil = 0;
    private static final long GRACE_MS = 60_000; // nao repede o PIN por 1 min ao voltar

    static SharedPreferences cfg(Context c) {
        return c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    static boolean setupDone(Context c) {
        return cfg(c).getBoolean("setup_done", false);
    }

    static void setSetupDone(Context c) {
        cfg(c).edit().putBoolean("setup_done", true).apply();
    }

    static void markUnlocked() {
        unlockedUntil = System.currentTimeMillis() + GRACE_MS;
    }

    static void lockNow() {
        unlockedUntil = 0;
    }

    static boolean needsUnlock(Context c) {
        return Lock.hasPin(c) && System.currentTimeMillis() > unlockedUntil;
    }
}
