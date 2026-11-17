package com.testemunha;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Atalho invisivel: comeca (ou para) a gravacao direto, sem abrir o app.
 * Acionado pelos atalhos de segurar o icone e pelo bloco de configuracoes rapidas.
 */
public class QuickActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String action = getIntent() != null ? getIntent().getAction() : null;

        // Se ja estiver gravando audio/video, o atalho para e salva.
        if (RecorderService.running && (RecorderService.kind == Store.KIND_AUDIO || RecorderService.kind == Store.KIND_VIDEO)) {
            RecorderService.stop(this);
            toast("Gravação salva no cofre");
            finish();
            return;
        }

        boolean needCam = "com.testemunha.VIDEO".equals(action) || "com.testemunha.PHOTO".equals(action);
        boolean needMic = !"com.testemunha.PHOTO".equals(action);
        boolean ok = (!needMic || Perms.mic(this)) && (!needCam || Perms.camera(this));
        if (RecorderService.running) { toast("Já há um registro em andamento"); finish(); return; }
        if (!ok) {
            // faltou permissao: abre o app para liberar
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            toast("Libere as permissões primeiro");
            finish();
            return;
        }

        if ("com.testemunha.VIDEO".equals(action)) {
            RecorderService.start(this, RecorderService.A_VIDEO);
            toast("Gravando vídeo");
        } else if ("com.testemunha.PHOTO".equals(action)) {
            RecorderService.start(this, RecorderService.A_PHOTO);
            toast("Tirando foto…");
        } else {
            RecorderService.start(this, RecorderService.A_AUDIO);
            toast("Gravando áudio");
        }
        finish();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
