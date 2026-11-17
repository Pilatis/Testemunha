package com.testemunha;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private FrameLayout host;
    private LinearLayout nav;
    private int tab = 0;
    private final int[] navIcons = {R.drawable.ic_mic, R.drawable.ic_folder, R.drawable.ic_shield, R.drawable.ic_phone};
    private final String[] navLabels = {"Gravar", "Provas", "Casos", "Ajuda"};
    private final ImageView[] navIv = new ImageView[4];
    private final TextView[] navTv = new TextView[4];

    // aba Gravar (tela viva)
    private TextView recTimer, recState, recHint, camHint;
    private LinearLayout recLiveCard, recButtonsCard, recStop;
    private ImageView recPulse;
    private boolean wasBusy = false;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateRecording();
            ui.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!App.setupDone(this)) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);
        host = new FrameLayout(this);
        root.addView(host, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(buildNav());
        setContentView(root);
        Ui.edgeToEdge(this, root);
        select(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!App.setupDone(this)) return;
        if (!Lock.hasPin(this) && !App.cfg(this).getBoolean("pin_offered", false)) {
            // primeira abertura: oferece criar o PIN do cofre (voltar = pular)
            App.cfg(this).edit().putBoolean("pin_offered", true).apply();
            startActivity(new Intent(this, LockActivity.class).putExtra(LockActivity.EXTRA_MODE, "create"));
            return;
        }
        if (App.needsUnlock(this)) {
            startActivityForResult(new Intent(this, LockActivity.class).putExtra(LockActivity.EXTRA_MODE, "open"), 9);
            return;
        }
        refreshTab();
        ui.post(tick);
    }

    @Override protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 9 && res != RESULT_OK) { moveTaskToBack(true); }
        else if (req == 9) { refreshTab(); ui.post(tick); }
    }

    // ---------------- navegacao ----------------

    private LinearLayout buildNav() {
        // Barra inferior: 4 colunas iguais, altura fixa, tudo centralizado com medidas explícitas.
        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBaselineAligned(false);
        nav.setBackgroundColor(Ui.BG);
        nav.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setClipToPadding(false);

            // "pílula" de 56x30dp com o ícone de 22dp exatamente no centro
            ImageView ic = new ImageView(this);
            ic.setImageResource(navIcons[i]);
            ic.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int ph = Ui.dp(this, 17), pv = Ui.dp(this, 4);
            ic.setPadding(ph, pv, ph, pv);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 30));
            ilp.gravity = Gravity.CENTER_HORIZONTAL;
            item.addView(ic, ilp);

            TextView tv = Ui.text(this, navLabels[i], 11, Ui.TEXT3, true);
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine(true);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
            tlp.gravity = Gravity.CENTER_HORIZONTAL;
            tlp.topMargin = Ui.dp(this, 5);
            item.addView(tv, tlp);

            navIv[i] = ic; navTv[i] = tv;
            item.setContentDescription(navLabels[i]);
            item.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); select(idx); });
            nav.addView(item, new LinearLayout.LayoutParams(0, Ui.dp(this, 64), 1f));
        }
        View line = new View(this);
        line.setBackgroundColor(Ui.STROKE);
        LinearLayout wrap = Ui.col(this);
        wrap.setBackgroundColor(Ui.BG);
        wrap.addView(line, new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(this, 1))));
        wrap.addView(nav, new LinearLayout.LayoutParams(-1, -2));
        return wrap;
    }

    private void select(int i) {
        tab = i;
        for (int k = 0; k < 4; k++) {
            boolean on = k == i;
            navIv[k].setImageTintList(android.content.res.ColorStateList.valueOf(on ? Ui.ACCENT : Ui.TEXT3));
            navIv[k].setBackground(on ? Ui.round(Ui.ACCENT_SOFT, Ui.dp(this, 15)) : null);
            navTv[k].setTextColor(on ? Ui.ACCENT : Ui.TEXT3);
        }
        refreshTab();
    }

    private void refreshTab() {
        host.removeAllViews();
        switch (tab) {
            case 0: host.addView(tabRecord()); updateRecording(); break;
            case 1: host.addView(tabEvidence()); break;
            case 2: host.addView(tabCases()); break;
            default: host.addView(tabHelp()); break;
        }
    }

    private ScrollView scroller(View content) {
        ScrollView s = new ScrollView(this);
        s.setOverScrollMode(View.OVER_SCROLL_NEVER);
        s.setVerticalScrollBarEnabled(false);
        s.addView(content);
        return s;
    }

    private LinearLayout page(String kicker, String title) {
        LinearLayout c = Ui.col(this);
        int g = Ui.dp(this, 16);
        c.setPadding(g, Ui.dp(this, 18), g, Ui.dp(this, 20));
        c.addView(Ui.mono(this, kicker, 11, Ui.ACCENT));
        TextView t = Ui.text(this, title, 26, Ui.TEXT, true);
        t.setPadding(0, Ui.dp(this, 6), 0, 0);
        c.addView(t);
        return c;
    }

    // ---------------- aba GRAVAR ----------------

    private View tabRecord() {
        LinearLayout c = page("Registro rápido", "Gravar prova");

        recLiveCard = Ui.card(this);
        LinearLayout live = Ui.row(this);
        recPulse = new ImageView(this);
        recPulse.setImageResource(R.drawable.ic_mic);
        recPulse.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.RED));
        recPulse.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 26), Ui.dp(this, 26)));
        live.addView(recPulse);
        LinearLayout lt = Ui.col(this);
        lt.setPadding(Ui.dp(this, 12), 0, 0, 0);
        recState = Ui.text(this, "Gravando", 15, Ui.TEXT, true);
        recTimer = Ui.monoPlain(this, "00:00", 26, Ui.RED);
        lt.addView(recState);
        lt.addView(recTimer);
        live.addView(lt, new LinearLayout.LayoutParams(0, -2, 1f));
        recLiveCard.addView(live);
        recStop = Ui.button(this, R.drawable.ic_stop, "Parar e salvar no cofre", 1, v -> {
            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            RecorderService.stop(this);
        });
        recStop.setLayoutParams(Ui.lpTop(this, -1, Ui.dp(this, 52), 16));
        recLiveCard.addView(recStop);
        recLiveCard.setLayoutParams(Ui.lpTop(this, -1, -2, 16));
        c.addView(recLiveCard);

        // câmera usada no vídeo e na foto
        LinearLayout camCard = Ui.card(this);
        camCard.setLayoutParams(Ui.lpTop(this, -1, -2, 16));
        camCard.addView(Ui.mono(this, "Câmera do vídeo e da foto", 10, Ui.TEXT2));
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setBaselineAligned(false);
        int sp = Ui.dp(this, 4);
        seg.setPadding(sp, sp, sp, sp);
        seg.setBackground(Ui.round(Ui.CARD2, Ui.dp(this, 14)));
        final String[] camLabels = {"Traseira", "Frontal", "As duas"};
        final TextView[] camTv = new TextView[3];
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView t = Ui.text(this, camLabels[i], 14, Ui.TEXT2, true);
            t.setGravity(Gravity.CENTER);
            t.setOnClickListener(v -> {
                if (RecorderService.running) return;
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                App.cfg(this).edit().putInt("cam", idx).apply();
                paintCam(camTv, idx);
            });
            camTv[i] = t;
            seg.addView(t, new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1f));
        }
        paintCam(camTv, RecorderService.camPref(this));
        camCard.addView(seg, Ui.lpTop(this, -1, -2, 10));
        camHint = Ui.text(this, "", 12, Ui.TEXT3, false);
        camHint.setLineSpacing(0, 1.25f);
        camCard.addView(camHint, Ui.lpTop(this, -1, -2, 10));
        paintCamHint(RecorderService.camPref(this));
        c.addView(camCard);

        recButtonsCard = Ui.col(this);
        recButtonsCard.addView(bigRecord(R.drawable.ic_mic, "Áudio", "Grava a conversa, inclusive com a tela apagada", Ui.ACCENT, () -> startRec(RecorderService.A_AUDIO)));
        recButtonsCard.addView(bigRecord(R.drawable.ic_video, "Vídeo", "Imagem e som do que está acontecendo", Ui.BLUE, () -> startRec(RecorderService.A_VIDEO)));
        recButtonsCard.addView(bigRecord(R.drawable.ic_camera, "Foto", "Registra uma cena na hora", Ui.GREEN, () -> startRec(RecorderService.A_PHOTO)));
        recButtonsCard.setLayoutParams(Ui.lpTop(this, -1, -2, 4));
        c.addView(recButtonsCard);

        recHint = Ui.text(this,
                "Dica: segure o ícone do Testemunha na tela inicial para gravar sem abrir o app, "
                        + "ou adicione o bloco \"Gravar áudio\" nas Configurações rápidas.",
                12, Ui.TEXT3, false);
        recHint.setLineSpacing(0, 1.3f);
        recHint.setPadding(Ui.dp(this, 4), Ui.dp(this, 18), Ui.dp(this, 4), 0);
        c.addView(recHint);

        return scroller(c);
    }

    private LinearLayout bigRecord(int icon, String title, String sub, int color, Runnable onClick) {
        LinearLayout r = Ui.row(this);
        int p = Ui.dp(this, 16);
        r.setPadding(p, p, p, p);
        r.setBackground(Ui.touch(Ui.roundStroke(Ui.CARD, 0xFF1F232B, Ui.dp(this, 18), Ui.dp(this, 1)), Ui.dp(this, 18)));
        r.setLayoutParams(Ui.lpTop(this, -1, -2, 12));
        r.addView(Ui.badge(this, icon, color, 52));
        LinearLayout t = Ui.col(this);
        t.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 8), 0);
        t.addView(Ui.text(this, title, 18, Ui.TEXT, true));
        TextView s = Ui.text(this, sub, 12, Ui.TEXT2, false);
        s.setPadding(0, Ui.dp(this, 4), 0, 0);
        s.setLineSpacing(0, 1.2f);
        t.addView(s);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView go = Ui.icon(this, R.drawable.ic_chevron, color, 22);
        r.addView(go);
        r.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CONFIRM); onClick.run(); });
        return r;
    }

    private void startRec(String action) {
        boolean needCam = RecorderService.A_VIDEO.equals(action) || RecorderService.A_PHOTO.equals(action);
        boolean needMic = !RecorderService.A_PHOTO.equals(action);
        if (RecorderService.running) return;
        if ((needMic && !Perms.mic(this)) || (needCam && !Perms.camera(this))) {
            startActivity(new Intent(this, SetupActivity.class));
            Toast.makeText(this, "Libere as permissões para gravar", Toast.LENGTH_SHORT).show();
            return;
        }
        RecorderService.start(this, action);
        wasBusy = true;      // quando o serviço terminar, showResult() mostra o que aconteceu
        updateRecording();
    }

    private void paintCam(TextView[] tv, int sel) {
        for (int i = 0; i < tv.length; i++) {
            boolean on = i == sel;
            tv[i].setTextColor(on ? Ui.ON_ACCENT : Ui.TEXT2);
            tv[i].setBackground(on ? Ui.round(Ui.ACCENT, Ui.dp(this, 11)) : null);
        }
        paintCamHint(sel);
    }

    private void paintCamHint(int sel) {
        if (camHint == null) return;
        camHint.setText(sel == RecorderService.CAM_BOTH
                ? "Foto: uma de cada câmera, em sequência. Vídeo: as duas ao mesmo tempo, se o seu celular permitir; senão, só a traseira."
                : sel == RecorderService.CAM_FRONT ? "Usa a câmera da frente, a da tela."
                : "Usa a câmera de trás.");
    }

    /** O serviço terminou (salvou ou falhou): avisa a pessoa e abre a prova. */
    private void showResult() {
        String err = RecorderService.lastError, note = RecorderService.notice, id = RecorderService.lastSavedId;
        int n = RecorderService.savedCount;
        RecorderService.lastError = null;
        RecorderService.notice = null;
        RecorderService.lastSavedId = null;
        RecorderService.savedCount = 0;
        if (n == 0) {
            Toast.makeText(this, err != null ? err : "Nada foi salvo", Toast.LENGTH_LONG).show();
            return;
        }
        if (note != null) Toast.makeText(this, note, Toast.LENGTH_LONG).show();
        if (n == 1 && id != null) {
            Toast.makeText(this, "Salvo no cofre", Toast.LENGTH_SHORT).show();
            // abre a prova para a pessoa escrever o que aconteceu
            startActivity(new Intent(this, EvidenceActivity.class).putExtra("id", id));
        } else {
            Toast.makeText(this, n + " provas salvas no cofre", Toast.LENGTH_SHORT).show();
            select(1); // mostra as duas na aba Provas
        }
    }

    private void updateRecording() {
        boolean busy = RecorderService.running;
        if (wasBusy && !busy) {
            wasBusy = false;
            showResult();
            if (tab != 0) return;
        }
        if (busy) wasBusy = true;
        if (recLiveCard == null) return;

        recLiveCard.setVisibility(busy ? View.VISIBLE : View.GONE);
        recButtonsCard.setAlpha(busy ? 0.4f : 1f);
        setClickable(recButtonsCard, !busy);
        if (!busy) return;

        int k = RecorderService.kind;
        boolean photo = k == Store.KIND_PHOTO;
        recPulse.setImageResource(photo ? R.drawable.ic_camera : k == Store.KIND_VIDEO ? R.drawable.ic_video : R.drawable.ic_mic);
        recStop.setVisibility(photo ? View.GONE : View.VISIBLE);
        if (photo) {
            recState.setText("Tirando a foto…");
            recTimer.setText("aguarde");
            recPulse.setAlpha(1f);
        } else if (!RecorderService.live) {
            recState.setText("Preparando…");
            recTimer.setText("00:00");
            recPulse.setAlpha(1f);
        } else {
            long sec = (SystemClock.elapsedRealtime() - RecorderService.startedAt) / 1000;
            recTimer.setText(Ui.clock(sec));
            recState.setText(k == Store.KIND_VIDEO ? "Gravando vídeo" : "Gravando áudio");
            recPulse.setAlpha((sec % 2 == 0) ? 1f : 0.35f);
        }
    }

    private void setClickable(View v, boolean c) {
        v.setEnabled(c);
        if (v instanceof LinearLayout) {
            LinearLayout g = (LinearLayout) v;
            for (int i = 0; i < g.getChildCount(); i++) g.getChildAt(i).setEnabled(c);
        }
    }

    // ---------------- aba PROVAS ----------------

    private View tabEvidence() {
        LinearLayout c = page("Cofre", "Provas");
        List<Store.Evidence> all = Store.evidences(this);

        LinearLayout header = Ui.row(this);
        header.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 6));
        TextView count = Ui.text(this, all.size() + (all.size() == 1 ? " prova guardada" : " provas guardadas"), 13, Ui.TEXT2, false);
        header.addView(count, new LinearLayout.LayoutParams(0, -2, 1f));
        if (!all.isEmpty()) {
            TextView size = Ui.mono(this, Ui.size(Store.totalBytes(this)), 10, Ui.TEXT3);
            header.addView(size);
        }
        c.addView(header);

        if (all.isEmpty()) {
            c.addView(emptyBox(R.drawable.ic_folder, "Nenhuma prova ainda",
                    "Grave um áudio, vídeo ou foto na aba Gravar. Tudo fica guardado aqui, com data, hora e uma marca que comprova que o arquivo não foi alterado."));
        } else {
            long lastDay = -1;
            for (Store.Evidence e : all) {
                long day = e.createdAt / 86400000L;
                if (day != lastDay) {
                    TextView d = Ui.mono(this, Store.dateShort(e.createdAt), 10, Ui.TEXT3);
                    d.setPadding(Ui.dp(this, 2), Ui.dp(this, 16), 0, Ui.dp(this, 8));
                    c.addView(d);
                    lastDay = day;
                }
                c.addView(evidenceRow(e));
            }
        }
        return scroller(c);
    }

    private View evidenceRow(Store.Evidence e) {
        LinearLayout r = Ui.row(this);
        int p = Ui.dp(this, 12);
        r.setPadding(p, p, p, p);
        r.setBackground(Ui.touch(Ui.roundStroke(Ui.CARD, 0xFF1F232B, Ui.dp(this, 16), Ui.dp(this, 1)), Ui.dp(this, 16)));
        r.setLayoutParams(Ui.lpTop(this, -1, -2, 8));

        int icon = e.kind == Store.KIND_AUDIO ? R.drawable.ic_mic : e.kind == Store.KIND_VIDEO ? R.drawable.ic_video : R.drawable.ic_camera;
        int color = e.kind == Store.KIND_AUDIO ? Ui.ACCENT : e.kind == Store.KIND_VIDEO ? Ui.BLUE : Ui.GREEN;
        r.addView(Ui.badge(this, icon, color, 46));

        LinearLayout t = Ui.col(this);
        t.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 8), 0);
        String kindName = Store.kindLabel(e);
        String head = kindName + " - " + Store.timeShort(e.createdAt);
        if (e.durationMs > 0) head += " - " + Ui.clock(e.durationMs / 1000);
        t.addView(Ui.text(this, head, 15, Ui.TEXT, true));
        LinearLayout meta = Ui.row(this);
        meta.setPadding(0, Ui.dp(this, 5), 0, 0);
        if (e.hasLoc) {
            meta.addView(Ui.icon(this, R.drawable.ic_location, Ui.TEXT3, 13));
        }
        TextView note = Ui.text(this, e.note != null && !e.note.isEmpty() ? e.note : (e.hasLoc ? "  com local" : "sem descrição"), 12, Ui.TEXT3, false);
        note.setMaxLines(1);
        note.setSingleLine(true);
        note.setPadding(e.hasLoc ? Ui.dp(this, 4) : 0, 0, 0, 0);
        meta.addView(note);
        t.addView(meta);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));

        if (e.caseId != null && !e.caseId.isEmpty()) {
            r.addView(Ui.chip(this, "caso", Ui.ACCENT));
        }
        r.setOnClickListener(v -> startActivity(new Intent(this, EvidenceActivity.class).putExtra("id", e.id)));
        return r;
    }

    // ---------------- aba CASOS ----------------

    private View tabCases() {
        LinearLayout c = page("Organize", "Casos");
        TextView intro = Ui.text(this, "Agrupe as provas por situação. Em assédio, o conjunto de episódios ao longo do tempo é o que mais pesa.", 13, Ui.TEXT2, false);
        intro.setLineSpacing(0, 1.25f);
        intro.setPadding(0, 0, 0, Ui.dp(this, 6));
        c.addView(intro);

        LinearLayout add = Ui.button(this, R.drawable.ic_add, "Novo caso", 2, v ->
                startActivity(new Intent(this, CaseActivity.class)));
        add.setLayoutParams(Ui.lpTop(this, -1, Ui.dp(this, 50), 8));
        c.addView(add);

        List<Store.Case> cases = Store.cases(this);
        if (cases.isEmpty()) {
            c.addView(emptyBox(R.drawable.ic_shield, "Nenhum caso ainda",
                    "Crie um caso, por exemplo \"Assédio no trabalho\", e anexe as provas a ele. Depois dá para exportar tudo junto num dossiê."));
        } else {
            for (Store.Case cs : cases) c.addView(caseRow(cs));
        }
        return scroller(c);
    }

    private View caseRow(Store.Case cs) {
        LinearLayout r = Ui.row(this);
        int p = Ui.dp(this, 14);
        r.setPadding(p, p, p, p);
        r.setBackground(Ui.touch(Ui.roundStroke(Ui.CARD, 0xFF1F232B, Ui.dp(this, 16), Ui.dp(this, 1)), Ui.dp(this, 16)));
        r.setLayoutParams(Ui.lpTop(this, -1, -2, 10));
        r.addView(Ui.badge(this, R.drawable.ic_folder, Ui.ACCENT, 46));
        LinearLayout t = Ui.col(this);
        t.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 8), 0);
        t.addView(Ui.text(this, cs.title == null || cs.title.isEmpty() ? "Caso sem título" : cs.title, 16, Ui.TEXT, true));
        int n = Store.countForCase(this, cs.id);
        TextView s = Ui.text(this, n + (n == 1 ? " prova" : " provas") + " - desde " + Store.dateShort(cs.createdAt), 12, Ui.TEXT2, false);
        s.setPadding(0, Ui.dp(this, 4), 0, 0);
        t.addView(s);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(Ui.icon(this, R.drawable.ic_chevron, Ui.TEXT3, 22));
        r.setOnClickListener(v -> startActivity(new Intent(this, CaseActivity.class).putExtra("id", cs.id)));
        return r;
    }

    // ---------------- aba AJUDA ----------------

    private View tabHelp() {
        LinearLayout c = page("Seus direitos", "Ajuda");

        c.addView(rightsCard());

        c.addView(Ui.section(this, "Telefones úteis"));
        c.addView(contact("190", "Polícia Militar - emergência", "190"));
        c.addView(contact("180", "Central de Atendimento à Mulher", "180"));
        c.addView(contact("100", "Direitos Humanos - denúncias", "100"));
        c.addView(contact("188", "CVV - apoio emocional (24h)", "188"));

        c.addView(Ui.section(this, "Para onde levar as provas"));
        c.addView(infoCard("Assédio no trabalho",
                "Sindicato da categoria, RH da empresa, um advogado trabalhista ou o Ministério Público do Trabalho (MPT), pelo site mpt.mp.br."));
        c.addView(infoCard("Ameaça ou agressão",
                "Delegacia (de preferência a Delegacia da Mulher, se for o caso) ou a Defensoria Pública do seu estado, que é gratuita."));

        TextView foot = Ui.text(this, "O Testemunha ajuda a reunir provas, mas não substitui orientação jurídica. Um advogado ou a Defensoria Pública pode dizer o melhor caminho para o seu caso.", 12, Ui.TEXT3, false);
        foot.setLineSpacing(0, 1.3f);
        foot.setPadding(Ui.dp(this, 4), Ui.dp(this, 22), Ui.dp(this, 4), 0);
        c.addView(foot);

        return scroller(c);
    }

    private View rightsCard() {
        LinearLayout card = Ui.card(this);
        card.setLayoutParams(Ui.lpTop(this, -1, -2, 8));
        LinearLayout h = Ui.row(this);
        h.addView(Ui.icon(this, R.drawable.ic_check, Ui.GREEN, 20));
        TextView ht = Ui.text(this, "O que você pode registrar", 16, Ui.TEXT, true);
        ht.setPadding(Ui.dp(this, 10), 0, 0, 0);
        h.addView(ht);
        card.addView(h);
        card.addView(bullet(true, "Gravar uma conversa da qual você participa, mesmo sem avisar a outra pessoa. O STF reconhece isso como prova lícita."));
        card.addView(bullet(true, "Fotografar ou filmar o que acontece com você, no trabalho ou em local público."));
        card.addView(bullet(false, "Deixar o celular gravando um ambiente sozinho, sem você presente, para ouvir terceiros. Isso pode ser crime."));
        card.addView(bullet(false, "Publicar a gravação nas redes. O certo é entregar a um advogado ou às autoridades."));
        return card;
    }

    private View bullet(boolean ok, String text) {
        LinearLayout r = Ui.row(this);
        r.setGravity(Gravity.TOP);
        r.setPadding(0, Ui.dp(this, 12), 0, 0);
        r.addView(Ui.icon(this, ok ? R.drawable.ic_check : R.drawable.ic_warning, ok ? Ui.GREEN : Ui.RED, 18));
        TextView t = Ui.text(this, text, 13, Ui.TEXT2, false);
        t.setLineSpacing(0, 1.25f);
        t.setPadding(Ui.dp(this, 10), 0, 0, 0);
        r.addView(t);
        return r;
    }

    private View contact(String number, String label, final String dial) {
        LinearLayout r = Ui.row(this);
        int p = Ui.dp(this, 14);
        r.setPadding(p, p, p, p);
        r.setBackground(Ui.touch(Ui.roundStroke(Ui.CARD, 0xFF1F232B, Ui.dp(this, 16), Ui.dp(this, 1)), Ui.dp(this, 16)));
        r.setLayoutParams(Ui.lpTop(this, -1, -2, 8));
        TextView num = Ui.text(this, number, 20, Ui.ACCENT, true);
        num.setMinWidth(Ui.dp(this, 58));
        r.addView(num);
        TextView t = Ui.text(this, label, 14, Ui.TEXT, false);
        t.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 8), 0);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(Ui.icon(this, R.drawable.ic_phone, Ui.TEXT2, 20));
        r.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + dial))); }
            catch (Exception e) { Toast.makeText(this, "Número: " + dial, Toast.LENGTH_SHORT).show(); }
        });
        return r;
    }

    private View infoCard(String title, String body) {
        LinearLayout card = Ui.card(this);
        card.setLayoutParams(Ui.lpTop(this, -1, -2, 8));
        card.addView(Ui.text(this, title, 15, Ui.TEXT, true));
        TextView t = Ui.text(this, body, 13, Ui.TEXT2, false);
        t.setLineSpacing(0, 1.3f);
        t.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(t);
        return card;
    }

    private View emptyBox(int icon, String title, String body) {
        LinearLayout e = Ui.col(this);
        e.setGravity(Gravity.CENTER_HORIZONTAL);
        e.setPadding(Ui.dp(this, 20), Ui.dp(this, 40), Ui.dp(this, 20), Ui.dp(this, 20));
        e.addView(Ui.icon(this, icon, Ui.TEXT3, 48));
        TextView t = Ui.text(this, title, 17, Ui.TEXT, true);
        t.setPadding(0, Ui.dp(this, 16), 0, 0);
        e.addView(t);
        TextView d = Ui.text(this, body, 13, Ui.TEXT2, false);
        d.setGravity(Gravity.CENTER);
        d.setLineSpacing(0, 1.3f);
        d.setPadding(0, Ui.dp(this, 8), 0, 0);
        e.addView(d);
        return e;
    }
}
