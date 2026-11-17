package com.testemunha;

import android.content.Intent;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

/** Bloco de Configuracoes rapidas (ao lado do Wi-Fi e da lanterna): liga/desliga o audio. */
public class QuickTile extends TileService {

    @Override public void onStartListening() { refresh(); }

    @Override
    public void onClick() {
        boolean recordingAudio = RecorderService.running && RecorderService.kind == Store.KIND_AUDIO;
        if (recordingAudio) {
            RecorderService.stop(this);
            Toast.makeText(this, "Áudio salvo no cofre", Toast.LENGTH_SHORT).show();
        } else if (RecorderService.running) {
            Toast.makeText(this, "Já há uma gravação em andamento", Toast.LENGTH_SHORT).show();
        } else if (!Perms.mic(this)) {
            startActivityAndCollapse(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else {
            RecorderService.start(this, RecorderService.A_AUDIO);
            Toast.makeText(this, "Gravando áudio", Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    private void refresh() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = RecorderService.running && RecorderService.kind == Store.KIND_AUDIO;
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel(on ? "Gravando..." : "Gravar áudio");
        t.updateTile();
    }
}
