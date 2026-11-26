package com.harislcd.launcher;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/** The Themes page: a card with a preview for every theme, and the light / dark / auto switch. */
public class ThemesActivity extends AppCompatActivity {

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void attachBaseContext(android.content.Context base) { super.attachBaseContext(Scale.wrap(base)); }

    @Override
    protected void onResume() {
        super.onResume();
        Updater.continueIfWaiting(this);   // back from allowing installs
    }

    /** Asks for the plate text. It is kept on this unit only and never written to the log. */
    private void askPlate() {
        final android.widget.EditText in = new android.widget.EditText(this);
        in.setText(ThemeStore.plate(this));
        in.setSelection(in.getText().length());
        in.setTextSize(28);
        in.setSingleLine(true);
        in.setHint("e.g. ABC 123");
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        in.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(12), new android.text.InputFilter.AllCaps()});
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(in);
        android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this)
                .setTitle("Number plate shown on the car")
                .setMessage("Stays on this unit. Leave it empty for a blank plate.")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    ThemeStore.savePlate(this, in.getText().toString());
                    Diag.log("themes", ThemeStore.plate(this).isEmpty() ? "plate cleared" : "plate set");
                    android.widget.Toast.makeText(this, ThemeStore.plate(this).isEmpty() ? "Plate is blank" : "Plate saved",
                            android.widget.Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
        for (int which : new int[]{android.app.AlertDialog.BUTTON_POSITIVE, android.app.AlertDialog.BUTTON_NEGATIVE}) {
            android.widget.Button b = dlg.getButton(which);
            if (b == null) continue;
            b.setTextSize(19);
            b.setMinHeight(dp(56));
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        final String current = ThemeStore.theme(this), mode = ThemeStore.mode(this);
        setTheme(ThemeStore.style(current));
        ThemeStore.applyNight(this);
        super.onCreate(savedInstanceState);
        int fg = ThemeStore.color(this, R.attr.cFg), hair = ThemeStore.color(this, R.attr.cHair), muted = ThemeStore.color(this, R.attr.cMuted);
        int accent = ThemeStore.color(this, R.attr.cAccent), onAccent = ThemeStore.color(this, R.attr.cOnAccent);
        int card = ThemeStore.color(this, R.attr.cCard);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.screen_bg);
        root.setPadding(dp(28), dp(16), dp(28), 0);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_back);
        back.setPadding(dp(8), dp(8), dp(8), dp(8));
        back.setOnClickListener(v -> finish());
        back.setBackgroundResource(R.drawable.focus_bg);
        back.setFocusable(true);
        head.addView(back, new LinearLayout.LayoutParams(dp(64), dp(64)));
        TextView title = new TextView(this);
        title.setText("THEMES");
        title.setTextColor(fg);
        title.setTextSize(26);
        title.setLetterSpacing(0.1f);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setPadding(dp(10), 0, 0, 0);
        head.addView(title);
        head.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        // the owner's number plate: the pictures are blank, the text typed here is drawn on the car
        TextView plate = new TextView(this);
        plate.setText("Number plate");
        plate.setTextSize(16);
        plate.setTextColor(fg);
        plate.setGravity(Gravity.CENTER);
        plate.setPadding(dp(18), 0, dp(18), 0);
        GradientDrawable nb = new GradientDrawable();
        nb.setCornerRadius(dp(28));
        nb.setColor(card);
        nb.setStroke(dp(1), hair);
        GradientDrawable nf = new GradientDrawable();
        nf.setCornerRadius(dp(28));
        nf.setColor(card);
        nf.setStroke(dp(3), fg);
        android.graphics.drawable.StateListDrawable ns = new android.graphics.drawable.StateListDrawable();
        ns.addState(new int[]{android.R.attr.state_focused}, nf);
        ns.addState(new int[]{}, nb);
        plate.setBackground(ns);
        plate.setFocusable(true);
        plate.setOnClickListener(v -> askPlate());
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(56));
        nlp.setMargins(0, 0, dp(14), 0);
        head.addView(plate, nlp);

        // version + manual update check
        TextView ver = new TextView(this);
        ver.setText("Version " + Updater.installedName(this) + "  ·  Check for updates");
        ver.setTextSize(16);
        ver.setTextColor(fg);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(dp(18), 0, dp(18), 0);
        GradientDrawable vb = new GradientDrawable();
        vb.setCornerRadius(dp(28));
        vb.setColor(card);
        vb.setStroke(dp(1), hair);
        GradientDrawable vf = new GradientDrawable();
        vf.setCornerRadius(dp(28));
        vf.setColor(card);
        vf.setStroke(dp(3), fg);
        android.graphics.drawable.StateListDrawable vs = new android.graphics.drawable.StateListDrawable();
        vs.addState(new int[]{android.R.attr.state_focused}, vf);
        vs.addState(new int[]{}, vb);
        ver.setBackground(vs);
        ver.setFocusable(true);
        ver.setOnClickListener(v -> {
            ver.setText("Checking…");
            Updater.check(this, (newer, err) -> {
                ver.setText("Version " + Updater.installedName(this) + "  ·  Check for updates");
                if (isFinishing()) return;
                if (newer != null) Updater.offer(this, newer);
                else android.widget.Toast.makeText(this, err != null ? "Couldn't check: " + err
                        : "You have the latest version (" + Updater.installedName(this) + ")", android.widget.Toast.LENGTH_LONG).show();
            });
        });
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(56));
        vlp.setMargins(0, 0, dp(14), 0);
        head.addView(ver, vlp);

        // sends the app's diary (see Diag) so a bug can be looked at
        TextView rep = new TextView(this);
        rep.setText("Send report");
        rep.setTextSize(16);
        rep.setTextColor(fg);
        rep.setGravity(Gravity.CENTER);
        rep.setPadding(dp(18), 0, dp(18), 0);
        GradientDrawable rb = new GradientDrawable();
        rb.setCornerRadius(dp(28));
        rb.setColor(card);
        rb.setStroke(dp(1), hair);
        GradientDrawable rf = new GradientDrawable();
        rf.setCornerRadius(dp(28));
        rf.setColor(card);
        rf.setStroke(dp(3), fg);
        android.graphics.drawable.StateListDrawable rs = new android.graphics.drawable.StateListDrawable();
        rs.addState(new int[]{android.R.attr.state_focused}, rf);
        rs.addState(new int[]{}, rb);
        rep.setBackground(rs);
        rep.setFocusable(true);
        rep.setOnClickListener(v -> {
            if (!rep.getText().toString().equals("Send report")) return;   // one is already on its way
            rep.setText("Sending…");
            Diag.send(this, (sent, err) -> {
                rep.setText("Send report");
                android.widget.Toast.makeText(getApplicationContext(), sent != null ? "Report sent (" + sent + ")"
                        : "Couldn't send the report: " + err, android.widget.Toast.LENGTH_LONG).show();
            });
        });
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(56));
        rlp.setMargins(0, 0, dp(4), 0);
        head.addView(rep, rlp);

        // light / dark switch: applies to whichever theme is chosen
        String[] labels = {"Auto (car lights)", "Light", "Dark"};
        for (int i = 0; i < ThemeStore.MODES.length; i++) {
            final String m = ThemeStore.MODES[i];
            boolean on = m.equals(mode);
            TextView pill = new TextView(this);
            pill.setText(labels[i]);
            pill.setTextSize(17);
            pill.setTextColor(on ? onAccent : fg);
            pill.setGravity(Gravity.CENTER);
            pill.setPadding(dp(20), 0, dp(20), 0);
            GradientDrawable pb = new GradientDrawable();
            pb.setCornerRadius(dp(28));
            pb.setColor(on ? accent : card);
            pb.setStroke(dp(1), on ? accent : hair);
            GradientDrawable pf = new GradientDrawable();
            pf.setCornerRadius(dp(28));
            pf.setColor(on ? accent : card);
            pf.setStroke(dp(3), fg);
            android.graphics.drawable.StateListDrawable ps = new android.graphics.drawable.StateListDrawable();
            ps.addState(new int[]{android.R.attr.state_focused}, pf);
            ps.addState(new int[]{}, pb);
            pill.setBackground(ps);
            pill.setFocusable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(56));
            lp.setMargins(dp(10), 0, 0, 0);
            head.addView(pill, lp);
            pill.setOnClickListener(v -> {
                Diag.log("themes", "mode " + m);
                ThemeStore.saveMode(this, m);
                if (!ThemeStore.applyNight(this)) recreate();   // a real change recreates the page by itself
            });
        }
        root.addView(head);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(12), 0, dp(18));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row = null;
        for (int i = 0; i < ThemeStore.THEMES.length; i++) {
            final String[] t = ThemeStore.THEMES[i];
            if (i % 5 == 0) {
                row = new LinearLayout(this);
                list.addView(row);
            }
            boolean on = t[0].equals(current);
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setPadding(dp(9), dp(9), dp(9), dp(11));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(card);
            bg.setCornerRadius(dp(18));
            bg.setStroke(dp(on ? 3 : 1), on ? accent : hair);
            GradientDrawable fb = new GradientDrawable();
            fb.setColor(card);
            fb.setCornerRadius(dp(18));
            fb.setStroke(dp(4), fg);
            android.graphics.drawable.StateListDrawable sl = new android.graphics.drawable.StateListDrawable();
            sl.addState(new int[]{android.R.attr.state_focused}, fb);
            sl.addState(new int[]{}, bg);
            tile.setBackground(sl);
            tile.setFocusable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(6), dp(7), dp(6), dp(7));
            row.addView(tile, lp);

            ImageView preview = new ImageView(this);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            int res = getResources().getIdentifier("preview_" + t[0], "drawable", getPackageName());
            if (res != 0) preview.setImageResource(res);
            tile.addView(preview, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(112)));

            TextView name = new TextView(this);
            name.setText(on ? t[1] + "  ✓" : t[1]);
            name.setTextColor(on ? accent : fg);
            name.setTextSize(18);
            name.setPadding(dp(4), dp(9), 0, 0);
            tile.addView(name);

            tile.setOnClickListener(v -> {
                Diag.log("themes", "theme " + t[0]);
                ThemeStore.saveTheme(this, t[0]);
                finish();   // the home screen rebuilds itself with the new theme when it comes back
            });
        }
        // a last row that is not full gets empty places, so its cards stay the size of the others
        for (int i = ThemeStore.THEMES.length; i % 5 != 0; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1, 1f);
            lp.setMargins(dp(6), dp(7), dp(6), dp(7));
            row.addView(new View(this), lp);
        }
        setContentView(root);
    }
}
