package com.testemunha;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Teclado do PIN. Modo "create" (define/confirma) ou "open" (verifica). */
public class LockActivity extends Activity {

    static final String EXTRA_MODE = "mode";
    private final Handler ui = new Handler(Looper.getMainLooper());

    private boolean creating;
    private String first = null;
    private final StringBuilder cur = new StringBuilder();
    private TextView title, subtitle;
    private final View[] dots = new View[8];
    private LinearLayout dotRow;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        creating = "create".equals(getIntent().getStringExtra(EXTRA_MODE));

        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int g = Ui.dp(this, 24);
        root.setPadding(g, Ui.dp(this, 56), g, Ui.dp(this, 24));

        root.addView(Ui.icon(this, R.drawable.ic_lock, Ui.ACCENT, 44));
        title = Ui.text(this, creating ? "Crie um PIN" : "Cofre protegido", 24, Ui.TEXT, true);
        title.setPadding(0, Ui.dp(this, 16), 0, 0);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        subtitle = Ui.text(this, creating ? "4 a 8 dígitos. Toque no check para confirmar." : "Digite o PIN e toque no check", 14, Ui.TEXT2, false);
        subtitle.setPadding(0, Ui.dp(this, 8), 0, 0);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);

        dotRow = Ui.row(this);
        dotRow.setGravity(Gravity.CENTER);
        dotRow.setPadding(0, Ui.dp(this, 34), 0, Ui.dp(this, 30));
        for (int i = 0; i < dots.length; i++) {
            View d = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 13), Ui.dp(this, 13));
            lp.leftMargin = lp.rightMargin = Ui.dp(this, 7);
            d.setLayoutParams(lp);
            dots[i] = d;
            dotRow.addView(d);
        }
        root.addView(dotRow);
        refreshDots();
        root.addView(keypad());

        setContentView(root);
        Ui.edgeToEdge(this, root);
    }

    private LinearLayout keypad() {
        LinearLayout pad = Ui.col(this);
        pad.setLayoutParams(Ui.lpTop(this, Ui.dp(this, 288), -2, 6));
        int[][] rows = {{1, 2, 3}, {4, 5, 6}, {7, 8, 9}, {-1, 0, -2}};
        for (int[] r : rows) {
            LinearLayout row = Ui.row(this);
            row.setGravity(Gravity.CENTER);
            for (int n : r) row.addView(key(n));
            pad.addView(row, Ui.lpTop(this, -1, -2, 10));
        }
        return pad;
    }

    private View key(int n) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 80), Ui.dp(this, 62));
        lp.leftMargin = lp.rightMargin = Ui.dp(this, 6);
        if (n == -1) { // confirmar
            ImageView ok = new ImageView(this);
            ok.setImageResource(R.drawable.ic_check);
            ok.setImageTintList(ColorStateList.valueOf(Ui.ON_ACCENT));
            int p = Ui.dp(this, 18);
            ok.setPadding(p, p, p, p);
            ok.setBackground(Ui.touch(Ui.round(Ui.ACCENT, Ui.dp(this, 18)), Ui.dp(this, 18)));
            ok.setLayoutParams(lp);
            ok.setContentDescription("Confirmar");
            ok.setOnClickListener(v -> { v.performHapticFeedback(HapticFeedbackConstants.CONFIRM); submit(); });
            return ok;
        }
        if (n == -2) { // apagar
            ImageView del = new ImageView(this);
            del.setImageResource(R.drawable.ic_backspace);
            del.setImageTintList(ColorStateList.valueOf(Ui.TEXT2));
            int p = Ui.dp(this, 18);
            del.setPadding(p, p, p, p);
            del.setBackground(Ui.touch(Ui.round(0, Ui.dp(this, 18)), Ui.dp(this, 18)));
            del.setLayoutParams(lp);
            del.setContentDescription("Apagar");
            del.setOnClickListener(v -> {
                if (cur.length() > 0) cur.deleteCharAt(cur.length() - 1);
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                refreshDots();
            });
            return del;
        }
        TextView t = Ui.text(this, String.valueOf(n), 26, Ui.TEXT, false);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.touch(Ui.round(Ui.CARD, Ui.dp(this, 18)), Ui.dp(this, 18)));
        t.setLayoutParams(lp);
        t.setOnClickListener(v -> {
            if (cur.length() >= 8) return;
            cur.append(n);
            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            refreshDots();
        });
        return t;
    }

    private void refreshDots() {
        int len = cur.length();
        int shown = Math.max(4, Math.min(8, len == 0 ? 4 : len));
        for (int i = 0; i < dots.length; i++) {
            dots[i].setVisibility(i < shown ? View.VISIBLE : View.GONE);
            dots[i].setBackground(Ui.oval(i < len ? Ui.ACCENT : Ui.CARD2));
        }
    }

    private void submit() {
        String pin = cur.toString();
        if (pin.length() < 4) { shake("Use pelo menos 4 dígitos"); return; }
        if (creating) {
            if (first == null) {
                first = pin;
                cur.setLength(0);
                title.setText("Confirme o PIN");
                subtitle.setText("Digite de novo e toque no check");
                refreshDots();
            } else if (first.equals(pin)) {
                Lock.setPin(this, pin);
                App.markUnlocked();
                finishOk();
            } else {
                first = null;
                cur.setLength(0);
                title.setText("Crie um PIN");
                shake("Os PINs não coincidem. Comece de novo.");
            }
        } else {
            if (Lock.check(this, pin)) {
                App.markUnlocked();
                finishOk();
            } else {
                cur.setLength(0);
                shake("PIN incorreto");
            }
        }
    }

    private void shake(String msg) {
        subtitle.setText(msg);
        subtitle.setTextColor(Ui.RED);
        dotRow.animate().translationX(Ui.dp(this, 10)).setDuration(60).withEndAction(() ->
                dotRow.animate().translationX(-Ui.dp(this, 10)).setDuration(60).withEndAction(() ->
                        dotRow.animate().translationX(0).setDuration(60).start()).start()).start();
        ui.postDelayed(() -> subtitle.setTextColor(Ui.TEXT2), 1400);
        refreshDots();
    }

    private void finishOk() {
        setResult(RESULT_OK);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (creating) super.onBackPressed();
        else moveTaskToBack(true);
    }
}
