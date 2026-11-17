package com.testemunha;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Criar/editar um caso, ver as provas vinculadas e exportar o dossie em PDF. */
public class CaseActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Store.Case cs;
    private boolean isNew;
    private EditText title, location, aggressor, witnesses, description;
    private LinearLayout evidenceList;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String id = getIntent().getStringExtra("id");
        cs = id == null ? null : Store.caseById(this, id);
        isNew = cs == null;
        if (isNew) {
            cs = new Store.Case();
            cs.id = "C" + System.currentTimeMillis();
            cs.createdAt = System.currentTimeMillis();
            cs.title = ""; cs.location = ""; cs.aggressor = ""; cs.witnesses = ""; cs.description = "";
        }

        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(Ui.topBar(this, isNew ? "Novo caso" : "Caso", this::saveAndFinish));

        ScrollView scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout c = Ui.col(this);
        int g = Ui.dp(this, 16);
        c.setPadding(g, 0, g, Ui.dp(this, 24));
        scroll.addView(c);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        title = field(c, "Título", "Ex.: Assédio no trabalho", cs.title, false);
        location = field(c, "Onde acontece", "Ex.: Empresa X, setor financeiro", cs.location, false);
        aggressor = field(c, "Pessoa(s) envolvida(s)", "Nome e cargo, se souber", cs.aggressor, false);
        witnesses = field(c, "Testemunhas", "Quem viu ou ouviu", cs.witnesses, false);
        description = field(c, "Relato", "Conte o que vem acontecendo, com datas se lembrar", cs.description, true);

        c.addView(Ui.section(this, "Provas deste caso"));
        evidenceList = Ui.col(this);
        c.addView(evidenceList);

        LinearLayout link = Ui.button(this, R.drawable.ic_add, "Vincular provas", 2, v -> pickEvidence());
        link.setLayoutParams(Ui.lpTop(this, -1, Ui.dp(this, 50), 10));
        c.addView(link);

        c.addView(Ui.section(this, "Dossiê"));
        LinearLayout export = Ui.button(this, R.drawable.ic_doc, "Exportar dossiê em PDF", 0, v -> export());
        export.setLayoutParams(Ui.lpTop(this, -1, Ui.dp(this, 54), 0));
        c.addView(export);
        TextView hint = Ui.text(this, "O PDF traz o relato e a lista de provas com data, hora, local e a impressão digital de cada arquivo. Os arquivos vão junto no compartilhamento.", 12, Ui.TEXT3, false);
        hint.setLineSpacing(0, 1.3f);
        hint.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4), 0);
        c.addView(hint);

        if (!isNew) {
            LinearLayout del = Ui.button(this, R.drawable.ic_delete, "Apagar caso", 3, v -> confirmDelete());
            del.setLayoutParams(Ui.lpTop(this, -1, Ui.dp(this, 50), 18));
            ((TextView) del.getChildAt(1)).setTextColor(Ui.RED);
            ((android.widget.ImageView) del.getChildAt(0)).setImageTintList(android.content.res.ColorStateList.valueOf(Ui.RED));
            c.addView(del);
        }

        setContentView(root);
        Ui.edgeToEdge(this, root);
    }

    private EditText field(LinearLayout parent, String label, String hint, String value, boolean multi) {
        parent.addView(Ui.section(this, label));
        EditText e = Ui.input(this, hint, multi);
        e.setText(value);
        parent.addView(e);
        return e;
    }

    private void collect() {
        cs.title = title.getText().toString().trim();
        cs.location = location.getText().toString().trim();
        cs.aggressor = aggressor.getText().toString().trim();
        cs.witnesses = witnesses.getText().toString().trim();
        cs.description = description.getText().toString().trim();
    }

    private boolean hasContent() {
        return !cs.title.isEmpty() || !cs.location.isEmpty() || !cs.aggressor.isEmpty()
                || !cs.witnesses.isEmpty() || !cs.description.isEmpty() || Store.countForCase(this, cs.id) > 0;
    }

    private void save() {
        collect();
        if (isNew && !hasContent()) return; // nao cria caso vazio
        if (cs.title.isEmpty()) cs.title = "Caso de " + Store.dateShort(cs.createdAt);
        Store.saveCase(this, cs);
        isNew = false;
    }

    private void saveAndFinish() { save(); finish(); }

    @Override public void onBackPressed() { saveAndFinish(); }

    @Override protected void onPause() { super.onPause(); save(); }

    @Override
    protected void onResume() {
        super.onResume();
        if (App.needsUnlock(this)) { finish(); return; }
        refreshEvidence();
    }

    private void refreshEvidence() {
        evidenceList.removeAllViews();
        int n = 0;
        for (Store.Evidence e : Store.evidences(this)) {
            if (!cs.id.equals(e.caseId)) continue;
            n++;
            LinearLayout r = Ui.row(this);
            int p = Ui.dp(this, 12);
            r.setPadding(p, p, p, p);
            r.setBackground(Ui.touch(Ui.roundStroke(Ui.CARD, 0xFF1F232B, Ui.dp(this, 14), Ui.dp(this, 1)), Ui.dp(this, 14)));
            r.setLayoutParams(Ui.lpTop(this, -1, -2, n == 1 ? 0 : 8));
            int icon = e.kind == Store.KIND_AUDIO ? R.drawable.ic_mic : e.kind == Store.KIND_VIDEO ? R.drawable.ic_video : R.drawable.ic_camera;
            int color = e.kind == Store.KIND_AUDIO ? Ui.ACCENT : e.kind == Store.KIND_VIDEO ? Ui.BLUE : Ui.GREEN;
            r.addView(Ui.badge(this, icon, color, 40));
            String kind = Store.kindLabel(e);
            TextView t = Ui.text(this, kind + " - " + Store.dateShort(e.createdAt) + " " + Store.timeShort(e.createdAt), 14, Ui.TEXT, true);
            t.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            r.addView(Ui.icon(this, R.drawable.ic_chevron, Ui.TEXT3, 20));
            r.setOnClickListener(v -> startActivity(new Intent(this, EvidenceActivity.class).putExtra("id", e.id)));
            evidenceList.addView(r);
        }
        if (n == 0) {
            TextView empty = Ui.text(this, "Nenhuma prova vinculada ainda.", 13, Ui.TEXT3, false);
            empty.setPadding(Ui.dp(this, 4), 0, 0, 0);
            evidenceList.addView(empty);
        }
    }

    private void pickEvidence() {
        final List<Store.Evidence> all = Store.evidences(this);
        if (all.isEmpty()) {
            Toast.makeText(this, "Grave uma prova primeiro, na aba Gravar", Toast.LENGTH_SHORT).show();
            return;
        }
        save();
        if (isNew) { // caso ainda vazio: cria para poder vincular
            if (cs.title.isEmpty()) cs.title = "Caso de " + Store.dateShort(cs.createdAt);
            Store.saveCase(this, cs);
            isNew = false;
            title.setText(cs.title);
        }
        String[] labels = new String[all.size()];
        final boolean[] checked = new boolean[all.size()];
        for (int i = 0; i < all.size(); i++) {
            Store.Evidence e = all.get(i);
            String kind = Store.kindLabel(e);
            labels[i] = kind + " - " + Store.dateShort(e.createdAt) + " " + Store.timeShort(e.createdAt);
            checked[i] = cs.id.equals(e.caseId);
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Provas deste caso")
                .setMultiChoiceItems(labels, checked, (d, which, is) -> checked[which] = is)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", (d, w) -> {
                    for (int i = 0; i < all.size(); i++) {
                        Store.Evidence e = all.get(i);
                        boolean was = cs.id.equals(e.caseId);
                        if (checked[i] && !was) { e.caseId = cs.id; Store.saveEvidence(this, e); }
                        else if (!checked[i] && was) { e.caseId = ""; Store.saveEvidence(this, e); }
                    }
                    refreshEvidence();
                })
                .show();
    }

    private void export() {
        save();
        if (isNew) {
            Toast.makeText(this, "Preencha o caso antes de exportar", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Gerando dossiê...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                File pdf = Dossier.build(this, cs);
                ArrayList<Uri> uris = new ArrayList<>();
                uris.add(EvidenceProvider.uriForCache(pdf));
                for (Store.Evidence e : Store.evidences(this)) {
                    if (cs.id.equals(e.caseId) && e.file(this).exists()) uris.add(EvidenceProvider.uriFor(e.file(this)));
                }
                ui.post(() -> {
                    Intent i = new Intent(Intent.ACTION_SEND_MULTIPLE)
                            .setType("*/*")
                            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                            .putExtra(Intent.EXTRA_SUBJECT, "Dossiê - " + cs.title)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(i, "Enviar dossiê"));
                });
            } catch (Throwable t) {
                ui.post(() -> Toast.makeText(this, "Não foi possível gerar o dossiê", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Apagar este caso?")
                .setMessage("As provas continuam no cofre, só deixam de estar vinculadas.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Apagar", (d, w) -> {
                    Store.deleteCase(this, cs.id);
                    isNew = true; // evita salvar de novo no onPause
                    title.setText(""); location.setText(""); aggressor.setText(""); witnesses.setText(""); description.setText("");
                    finish();
                })
                .show();
    }
}
