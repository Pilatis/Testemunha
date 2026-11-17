package com.testemunha;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;
import java.util.Locale;

/** Detalhe de uma prova: reproducao, dados, descricao, caso, compartilhar e apagar. */
public class EvidenceActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Store.Evidence e;
    private MediaPlayer player;
    private ImageView playIcon;
    private TextView caseValue;
    private EditText noteField;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String id = getIntent().getStringExtra("id");
        e = id == null ? null : Store.evidence(this, id);
        if (e == null) { finish(); return; }

        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);
        String kindName = Store.kindLabel(e);
        root.addView(Ui.topBar(this, kindName, this::finish));

        ScrollView scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout c = Ui.col(this);
        int g = Ui.dp(this, 16);
        c.setPadding(g, 0, g, Ui.dp(this, 24));
        scroll.addView(c);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        c.addView(preview());
        c.addView(dataCard());

        c.addView(Ui.section(this, "Descrição"));
        noteField = Ui.input(this, "O que aconteceu? Quem estava presente? Escreva com suas palavras.", true);
        noteField.setText(e.note);
        noteField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int bb, int cc) {}
            @Override public void onTextChanged(CharSequence s, int a, int bb, int cc) {}
            @Override public void afterTextChanged(Editable s) { e.note = s.toString(); Store.saveEvidence(EvidenceActivity.this, e); }
        });
        c.addView(noteField);

        c.addView(Ui.section(this, "Caso"));
        c.addView(caseRow());

        c.addView(Ui.section(this, "Ações"));
        LinearLayout share = Ui.button(this, R.drawable.ic_share, "Compartilhar arquivo", 2, v -> share());
        share.setLayoutParams(Ui.lpTop(this, -1, Ui.dp(this, 50), 0));
        c.addView(share);
        LinearLayout open = Ui.button(this, R.drawable.ic_upload, "Abrir em outro app", 2, v -> openExternal());
        open.setLayoutParams(Ui.lpTop(this, -1, Ui.dp(this, 50), 10));
        c.addView(open);
        LinearLayout del = Ui.button(this, R.drawable.ic_delete, "Apagar prova", 3, v -> confirmDelete());
        del.setLayoutParams(Ui.lpTop(this, -1, Ui.dp(this, 50), 10));
        ((TextView) del.getChildAt(1)).setTextColor(Ui.RED);
        del.getChildAt(0).setBackground(null);
        ((ImageView) del.getChildAt(0)).setImageTintList(android.content.res.ColorStateList.valueOf(Ui.RED));
        c.addView(del);

        setContentView(root);
        Ui.edgeToEdge(this, root);
    }

    private View preview() {
        if (e.kind == Store.KIND_PHOTO) {
            ImageView img = new ImageView(this);
            img.setAdjustViewBounds(true);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            img.setBackground(Ui.round(Ui.CARD, Ui.dp(this, 16)));
            img.setClipToOutline(true);
            img.setLayoutParams(Ui.lpTop(this, -1, Ui.dp(this, 320), 8));
            final File pf = e.file(this);
            new Thread(() -> {
                try {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inSampleSize = 2;
                    final Bitmap bm = BitmapFactory.decodeFile(pf.getAbsolutePath(), o);
                    if (bm != null) ui.post(() -> img.setImageBitmap(bm));
                } catch (Throwable ignored) {}
            }).start();
            img.setOnClickListener(v -> openExternal());
            return img;
        }
        // audio/video: botao de reproduzir
        LinearLayout card = Ui.card(this);
        card.setGravity(Gravity.CENTER);
        card.setLayoutParams(Ui.lpTop(this, -1, -2, 8));
        playIcon = new ImageView(this);
        playIcon.setImageResource(R.drawable.ic_play);
        playIcon.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.ON_ACCENT));
        int pad = Ui.dp(this, 22);
        playIcon.setPadding(pad, pad, pad, pad);
        playIcon.setBackground(Ui.oval(Ui.ACCENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 76), Ui.dp(this, 76));
        playIcon.setLayoutParams(lp);
        playIcon.setOnClickListener(v -> toggle());
        card.addView(playIcon);
        TextView t = Ui.text(this, e.kind == Store.KIND_VIDEO ? "Toque para ver o vídeo" : "Toque para ouvir", 13, Ui.TEXT2, false);
        t.setPadding(0, Ui.dp(this, 14), 0, 0);
        card.addView(t);
        if (e.durationMs > 0) {
            TextView d = Ui.monoPlain(this, Ui.clock(e.durationMs / 1000), 15, Ui.TEXT);
            d.setPadding(0, Ui.dp(this, 6), 0, 0);
            card.addView(d);
        }
        return card;
    }

    private void toggle() {
        if (e.kind == Store.KIND_VIDEO) { openExternal(); return; }
        try {
            if (player != null && player.isPlaying()) {
                player.pause();
                playIcon.setImageResource(R.drawable.ic_play);
                return;
            }
            if (player == null) {
                player = new MediaPlayer();
                player.setDataSource(e.file(this).getAbsolutePath());
                player.prepare();
                player.setOnCompletionListener(mp -> playIcon.setImageResource(R.drawable.ic_play));
            }
            player.start();
            playIcon.setImageResource(R.drawable.ic_pause);
        } catch (Exception ex) {
            Toast.makeText(this, "Não foi possível reproduzir", Toast.LENGTH_SHORT).show();
        }
    }

    private View dataCard() {
        LinearLayout card = Ui.card(this);
        card.setLayoutParams(Ui.lpTop(this, -1, -2, 14));
        row(card, "Data e hora", Store.dateTime(e.createdAt), false);
        card.addView(Ui.divider(this, 12));
        row(card, "Tamanho", Ui.size(e.sizeBytes), false);
        if (e.hasLoc) {
            card.addView(Ui.divider(this, 12));
            String coord = String.format(Locale.US, "%.5f, %.5f", e.lat, e.lon);
            View r = row(card, "Local", coord + "  (abrir no mapa)", false);
            r.setOnClickListener(v -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + e.lat + "," + e.lon + "?q=" + e.lat + "," + e.lon))); }
                catch (Exception ex) { Toast.makeText(this, coord, Toast.LENGTH_SHORT).show(); }
            });
        }
        card.addView(Ui.divider(this, 12));
        // hash
        LinearLayout hh = Ui.col(this);
        LinearLayout top = Ui.row(this);
        top.addView(Ui.mono(this, "Impressão digital (SHA-256)", 10, Ui.TEXT2), new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView copy = Ui.icon(this, R.drawable.ic_copy, Ui.ACCENT, 18);
        copy.setOnClickListener(v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("hash", e.hash));
            Toast.makeText(this, "Código copiado", Toast.LENGTH_SHORT).show();
        });
        top.addView(copy);
        hh.addView(top);
        TextView hv = Ui.monoPlain(this, e.hash == null || e.hash.isEmpty() ? "-" : e.hash, 11, Ui.TEXT3);
        hv.setPadding(0, Ui.dp(this, 6), 0, 0);
        hh.addView(hv);
        TextView expl = Ui.text(this, "Esse código muda se o arquivo for alterado. Serve para provar que a gravação é original.", 11, Ui.TEXT3, false);
        expl.setLineSpacing(0, 1.2f);
        expl.setPadding(0, Ui.dp(this, 6), 0, 0);
        hh.addView(expl);
        card.addView(hh, Ui.lpTop(this, -1, -2, 12));
        return card;
    }

    private View row(LinearLayout parent, String label, String value, boolean mono) {
        LinearLayout r = Ui.row(this);
        r.addView(Ui.text(this, label, 13, Ui.TEXT2, false), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView v = Ui.text(this, value, 13, Ui.TEXT, true);
        v.setGravity(Gravity.END);
        r.addView(v, new LinearLayout.LayoutParams(0, -2, 1.4f));
        parent.addView(r);
        return r;
    }

    private View caseRow() {
        LinearLayout r = Ui.row(this);
        int p = Ui.dp(this, 14);
        r.setPadding(p, p, p, p);
        r.setBackground(Ui.touch(Ui.roundStroke(Ui.CARD, 0xFF1F232B, Ui.dp(this, 14), Ui.dp(this, 1)), Ui.dp(this, 14)));
        r.addView(Ui.icon(this, R.drawable.ic_folder, Ui.ACCENT, 20));
        Store.Case cs = e.caseId == null || e.caseId.isEmpty() ? null : Store.caseById(this, e.caseId);
        caseValue = Ui.text(this, cs != null ? cs.title : "Sem caso - toque para vincular", 14, Ui.TEXT, false);
        caseValue.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
        r.addView(caseValue, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(Ui.icon(this, R.drawable.ic_chevron, Ui.TEXT3, 20));
        r.setOnClickListener(v -> pickCase());
        return r;
    }

    private void pickCase() {
        List<Store.Case> cases = Store.cases(this);
        String[] labels = new String[cases.size() + 1];
        labels[0] = "Sem caso";
        for (int i = 0; i < cases.size(); i++) labels[i + 1] = cases.get(i).title;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Vincular a um caso")
                .setItems(labels, (d, w) -> {
                    e.caseId = w == 0 ? "" : cases.get(w - 1).id;
                    Store.saveEvidence(this, e);
                    caseValue.setText(w == 0 ? "Sem caso - toque para vincular" : cases.get(w - 1).title);
                })
                .setNeutralButton("Novo caso", (d, w) -> startActivity(new Intent(this, CaseActivity.class)))
                .show();
    }

    private void share() {
        Uri u = EvidenceProvider.uriFor(e.file(this));
        Intent i = new Intent(Intent.ACTION_SEND)
                .setType(typeOf())
                .putExtra(Intent.EXTRA_STREAM, u)
                .putExtra(Intent.EXTRA_TEXT, shareCaption())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Compartilhar prova"));
    }

    private void openExternal() {
        Uri u = EvidenceProvider.uriFor(e.file(this));
        try {
            startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(u, typeOf()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (Exception ex) {
            Toast.makeText(this, "Nenhum app para abrir este arquivo", Toast.LENGTH_SHORT).show();
        }
    }

    private String shareCaption() {
        StringBuilder s = new StringBuilder();
        s.append(e.kind == Store.KIND_AUDIO ? "Áudio" : e.kind == Store.KIND_VIDEO ? "Vídeo" : "Foto");
        s.append(" registrado em ").append(Store.dateTime(e.createdAt)).append(".\n");
        if (e.hasLoc) s.append("Local: ").append(String.format(Locale.US, "%.5f, %.5f", e.lat, e.lon)).append("\n");
        if (e.note != null && !e.note.isEmpty()) s.append("Descrição: ").append(e.note).append("\n");
        s.append("Impressão digital SHA-256: ").append(e.hash);
        return s.toString();
    }

    private String typeOf() {
        return e.kind == Store.KIND_AUDIO ? "audio/mp4" : e.kind == Store.KIND_VIDEO ? "video/mp4" : "image/jpeg";
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Apagar esta prova?")
                .setMessage("O arquivo será removido do cofre. Não dá para desfazer.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Apagar", (d, w) -> {
                    Store.deleteEvidence(this, e.id);
                    finish();
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (App.needsUnlock(this)) finish(); // volta para a tela principal, que pede o PIN
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            try { if (player.isPlaying()) player.pause(); } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) { try { player.release(); } catch (Exception ignored) {} player = null; }
    }
}
