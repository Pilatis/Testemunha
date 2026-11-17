package com.testemunha;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Abertura: explica o app e libera tudo num botao so.
 * "Liberar tudo" pede camera, microfone, localizacao e notificacoes de uma vez;
 * depois a bateria e o "não remover permissões" (acesso vitalicio).
 */
public class SetupActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout list;
    private TextView mainBtnLabel;
    private LinearLayout mainBtn;
    private TextView skip;
    private int step = 0; // 0 runtime, 1 bateria, 2 auto-revoke, 3 pronto

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);
        LinearLayout root = Ui.col(this);
        int g = Ui.dp(this, 22);
        root.setPadding(g, Ui.dp(this, 40), g, Ui.dp(this, 24));
        scroll.addView(root);

        ImageView shield = Ui.icon(this, R.drawable.ic_shield, Ui.ACCENT, 56);
        root.addView(shield);

        TextView title = Ui.text(this, "Testemunha", 30, Ui.TEXT, true);
        title.setPadding(0, Ui.dp(this, 16), 0, 0);
        root.addView(title);

        TextView sub = Ui.text(this,
                "Registre provas de assédio, perigo ou abuso de forma rápida e segura. "
                        + "Áudio, vídeo e fotos com data, hora e local, guardados só no seu celular.",
                15, Ui.TEXT2, false);
        sub.setLineSpacing(0, 1.3f);
        sub.setPadding(0, Ui.dp(this, 10), 0, 0);
        root.addView(sub);

        // lista de permissoes
        list = Ui.card(this);
        list.setLayoutParams(Ui.lpTop(this, -1, -2, 26));
        root.addView(list);
        buildPermList();

        // privacidade
        LinearLayout priv = Ui.row(this);
        priv.setPadding(Ui.dp(this, 4), Ui.dp(this, 18), Ui.dp(this, 4), 0);
        priv.addView(Ui.icon(this, R.drawable.ic_lock, Ui.GREEN, 18));
        TextView pv = Ui.text(this, "Nada sai do celular: o app não tem acesso à internet. As provas ficam numa área privada, protegida por PIN.", 12, Ui.TEXT3, false);
        pv.setLineSpacing(0, 1.25f);
        pv.setPadding(Ui.dp(this, 10), 0, 0, 0);
        priv.addView(pv);
        root.addView(priv);

        root.addView(Ui.spacer(this));

        mainBtn = Ui.button(this, R.drawable.ic_check, "Liberar tudo", 0, v -> onMain());
        mainBtn.setLayoutParams(Ui.lpTop(this, -1, Ui.dp(this, 56), 26));
        mainBtnLabel = (TextView) mainBtn.getChildAt(1);
        root.addView(mainBtn);

        skip = Ui.text(this, "Agora não", 14, Ui.TEXT2, true);
        skip.setGravity(Gravity.CENTER);
        skip.setPadding(0, Ui.dp(this, 14), 0, 0);
        skip.setOnClickListener(v -> done());
        root.addView(skip);

        setContentView(scroll);
        Ui.edgeToEdge(this, scroll);
    }

    private void buildPermList() {
        list.removeAllViews();
        addPerm(R.drawable.ic_camera, "Câmera", "Fotos e vídeo das situações", Perms.camera(this));
        list.addView(Ui.divider(this, 12));
        addPerm(R.drawable.ic_mic, "Microfone", "Gravar áudio das conversas", Perms.mic(this));
        list.addView(Ui.divider(this, 12));
        addPerm(R.drawable.ic_location, "Localização", "Marcar onde cada prova foi feita", Perms.location(this));
        list.addView(Ui.divider(this, 12));
        addPerm(R.drawable.ic_bell, "Notificações", "Mostrar quando está gravando", Perms.notifications(this));
        list.addView(Ui.divider(this, 12));
        addPerm(R.drawable.ic_battery, "Bateria livre", "Não pausar com a tela apagada", Perms.batteryFree(this));
    }

    private void addPerm(int icon, String title, String sub, boolean ok) {
        LinearLayout r = Ui.row(this);
        r.addView(Ui.badge(this, icon, ok ? Ui.GREEN : Ui.ACCENT, 42));
        LinearLayout t = Ui.col(this);
        t.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 8), 0);
        t.addView(Ui.text(this, title, 15, Ui.TEXT, true));
        TextView s = Ui.text(this, sub, 12, Ui.TEXT2, false);
        s.setPadding(0, Ui.dp(this, 3), 0, 0);
        t.addView(s);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView mark = Ui.icon(this, ok ? R.drawable.ic_check : R.drawable.ic_chevron, ok ? Ui.GREEN : Ui.TEXT3, 20);
        r.addView(mark);
        list.addView(r);
    }

    @SuppressLint("BatteryLife")
    private void onMain() {
        // passo 1: permissoes de runtime juntas
        if (!Perms.allRuntimeGranted(this)) {
            Perms.requestAll(this);
            return;
        }
        // passo 2: bateria
        if (!Perms.batteryFree(this)) {
            try {
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                openAppSettings();
            }
            step = 2;
            return;
        }
        // passo 3: acesso vitalicio (nao remover permissoes de apps pouco usados)
        if (step < 3) {
            requestNoAutoRevoke();
            step = 3;
            return;
        }
        done();
    }

    /** Pede ao Android para nunca tirar as permissoes por falta de uso = acesso vitalicio. */
    private void requestNoAutoRevoke() {
        try {
            Intent i = new Intent("android.intent.action.AUTO_REVOKE_PERMISSIONS",
                    Uri.parse("package:" + getPackageName()));
            if (i.resolveActivity(getPackageManager()) != null) {
                startActivity(i);
                return;
            }
        } catch (Exception ignored) {}
        // nem todo celular tem essa tela: cai nas configuracoes do app
        openAppSettings();
    }

    private void openAppSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {}
    }

    private void done() {
        App.setSetupDone(this);
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        buildPermList();
        ui.postDelayed(this::onMain, 250); // segue para bateria/vitalicio
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (list != null) buildPermList();
        updateButton();
    }

    private void updateButton() {
        if (Perms.allRuntimeGranted(this) && Perms.batteryFree(this)) {
            mainBtnLabel.setText(step >= 3 ? "Comecar" : "Concluir liberação");
        } else {
            mainBtnLabel.setText("Liberar tudo");
        }
    }
}
