package com.testemunha;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Paleta "arquivo de investigacao" e construtores de views. */
final class Ui {
    private Ui() {}

    static final int BG = 0xFF0A0C0F;
    static final int CARD = 0xFF13161B;
    static final int CARD2 = 0xFF1C2028;
    static final int STROKE = 0xFF2A2F38;
    static final int TEXT = 0xFFF1F3F6;
    static final int TEXT2 = 0xFF97A0AE;
    static final int TEXT3 = 0xFF5F6775;
    static final int ACCENT = 0xFFF2C230;
    static final int ACCENT_SOFT = 0x24F2C230;
    static final int ON_ACCENT = 0xFF14110A;
    static final int RED = 0xFFFF4D4F;
    static final int RED_SOFT = 0x26FF4D4F;
    static final int GREEN = 0xFF3DDC97;
    static final int GREEN_SOFT = 0x223DDC97;
    static final int BLUE = 0xFF6EA8FF;

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(r);
        return g;
    }

    static GradientDrawable roundStroke(int fill, int stroke, float r, int w) {
        GradientDrawable g = round(fill, r);
        g.setStroke(w, stroke);
        return g;
    }

    static GradientDrawable oval(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    static Drawable touch(Drawable content, float r) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, round(0xFFFFFFFF, r));
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView mono(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s.toUpperCase(Locale.ROOT));
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        t.setLetterSpacing(0.12f);
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView monoPlain(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.MONOSPACE);
        t.setIncludeFontPadding(false);
        return t;
    }

    static ImageView icon(Context c, int res, int tint, int sizeDp) {
        ImageView i = new ImageView(c);
        i.setImageResource(res);
        i.setImageTintList(ColorStateList.valueOf(tint));
        i.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return i;
    }

    static ImageView iconButton(Context c, int res, int tint, String desc, View.OnClickListener l) {
        ImageView i = new ImageView(c);
        i.setImageResource(res);
        i.setImageTintList(ColorStateList.valueOf(tint));
        int p = dp(c, 10);
        i.setPadding(p, p, p, p);
        i.setBackground(touch(oval(CARD2), dp(c, 22)));
        i.setContentDescription(desc);
        i.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 44), dp(c, 44));
        lp.leftMargin = dp(c, 8);
        i.setLayoutParams(lp);
        return i;
    }

    static ImageView badge(Context c, int res, int color, int sizeDp) {
        ImageView i = new ImageView(c);
        i.setImageResource(res);
        i.setImageTintList(ColorStateList.valueOf(color));
        int p = dp(c, sizeDp * 0.24f);
        i.setPadding(p, p, p, p);
        i.setBackground(round((color & 0x00FFFFFF) | 0x24000000, dp(c, sizeDp * 0.3f)));
        i.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return i;
    }

    static TextView chip(Context c, String s, int color) {
        TextView t = mono(c, s, 10, color);
        t.setPadding(dp(c, 8), dp(c, 5), dp(c, 8), dp(c, 5));
        t.setBackground(round((color & 0x00FFFFFF) | 0x24000000, dp(c, 6)));
        return t;
    }

    /** kind: 0 amarelo, 1 vermelho, 2 neutro, 3 transparente. */
    static LinearLayout button(Context c, int iconRes, String label, int kind, View.OnClickListener l) {
        LinearLayout b = row(c);
        b.setGravity(Gravity.CENTER);
        int bg = kind == 0 ? ACCENT : kind == 1 ? RED : kind == 2 ? CARD2 : 0;
        int fg = kind == 0 ? ON_ACCENT : kind == 1 ? 0xFFFFFFFF : TEXT;
        b.setBackground(touch(round(bg, dp(c, 16)), dp(c, 16)));
        if (iconRes != 0) b.addView(icon(c, iconRes, fg, 20));
        TextView t = text(c, label, 15, fg, true);
        if (iconRes != 0) t.setPadding(dp(c, 10), 0, 0, 0);
        b.addView(t);
        b.setOnClickListener(l);
        b.setMinimumHeight(dp(c, 52));
        return b;
    }

    static EditText input(Context c, String hint, boolean multiline) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(TEXT3);
        e.setTextColor(TEXT);
        e.setTextSize(15);
        int p = dp(c, 14);
        e.setPadding(p, p, p, p);
        e.setBackground(roundStroke(CARD2, STROKE, dp(c, 12), dp(c, 1)));
        if (multiline) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            e.setMinLines(3);
            e.setGravity(Gravity.TOP | Gravity.START);
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            e.setSingleLine(true);
        }
        return e;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = col(c);
        l.setBackground(roundStroke(CARD, 0xFF1F232B, dp(c, 18), dp(c, 1)));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        return l;
    }

    static View divider(Context c, int vMarginDp) {
        View v = new View(c);
        v.setBackgroundColor(STROKE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(c, 1)));
        lp.topMargin = dp(c, vMarginDp);
        lp.bottomMargin = dp(c, vMarginDp);
        v.setLayoutParams(lp);
        return v;
    }

    static View spacer(Context c) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
        return v;
    }

    static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    static LinearLayout.LayoutParams lpTop(Context c, int w, int h, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.topMargin = dp(c, topDp);
        return lp;
    }

    static TextView section(Context c, String s) {
        TextView t = mono(c, s, 11, TEXT2);
        t.setPadding(dp(c, 4), dp(c, 22), 0, dp(c, 10));
        return t;
    }

    /** Cabecalho com voltar + titulo. */
    static LinearLayout topBar(Activity a, String title, Runnable onBack) {
        LinearLayout h = row(a);
        h.setPadding(dp(a, 12), dp(a, 10), dp(a, 16), dp(a, 10));
        ImageView back = iconButton(a, R.drawable.ic_back, TEXT, "Voltar", v -> onBack.run());
        ((LinearLayout.LayoutParams) back.getLayoutParams()).leftMargin = 0;
        h.addView(back);
        TextView t = text(a, title, 21, TEXT, true);
        t.setPadding(dp(a, 12), 0, 0, 0);
        h.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        return h;
    }

    @SuppressWarnings("deprecation")
    static void edgeToEdge(Activity a, View root) {
        Window w = a.getWindow();
        w.setStatusBarColor(0);
        w.setNavigationBarColor(0);
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        final int l = root.getPaddingLeft(), t = root.getPaddingTop(),
                r = root.getPaddingRight(), b = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, in) -> {
            int it, ib, il, ir;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets s = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                it = s.top; ib = s.bottom; il = s.left; ir = s.right;
            } else {
                it = in.getSystemWindowInsetTop(); ib = in.getSystemWindowInsetBottom();
                il = in.getSystemWindowInsetLeft(); ir = in.getSystemWindowInsetRight();
            }
            v.setPadding(l + il, t + it, r + ir, b + ib);
            return in;
        });
    }

    static String clock(long sec) {
        long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }
}
