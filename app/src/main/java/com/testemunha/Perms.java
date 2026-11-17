package com.testemunha;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;

import java.util.ArrayList;
import java.util.List;

/** Verificacao das permissoes. O botao "Liberar tudo" pede todas de uma vez. */
final class Perms {
    private Perms() {}

    static final int REQ = 77;

    static boolean has(Context c, String p) {
        return c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean camera(Context c) { return has(c, Manifest.permission.CAMERA); }
    static boolean mic(Context c) { return has(c, Manifest.permission.RECORD_AUDIO); }
    static boolean location(Context c) { return has(c, Manifest.permission.ACCESS_FINE_LOCATION) || has(c, Manifest.permission.ACCESS_COARSE_LOCATION); }
    static boolean notifications(Context c) {
        return Build.VERSION.SDK_INT < 33 || has(c, Manifest.permission.POST_NOTIFICATIONS);
    }
    static boolean batteryFree(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }

    /** Falta alguma permissao de runtime (camera, mic, localizacao, notificacao)? */
    static boolean allRuntimeGranted(Context c) {
        return camera(c) && mic(c) && location(c) && notifications(c);
    }

    static String[] missing(Context c) {
        List<String> need = new ArrayList<>();
        if (!camera(c)) need.add(Manifest.permission.CAMERA);
        if (!mic(c)) need.add(Manifest.permission.RECORD_AUDIO);
        if (!location(c)) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33 && !notifications(c)) need.add(Manifest.permission.POST_NOTIFICATIONS);
        return need.toArray(new String[0]);
    }

    static void requestAll(Activity a) {
        String[] m = missing(a);
        if (m.length > 0) a.requestPermissions(m, REQ);
    }
}
