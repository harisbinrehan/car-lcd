package com.harislcd.launcher;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The Apps page: every installed app, a search box, and stars to pin favourites to the top. */
public class AppsActivity extends AppCompatActivity {

    /** Starred the first time the page opens. Matched against the package name or the app's label. */
    private static final String[] DEFAULT_FAVOURITES = {
            "youtube", "airplay", "com.android.vending", "com.imotor.fmam", "tamasha", "tapmad", "chrome"
    };

    private static class AppEntry {
        String label, lower, key;
        Drawable icon;
        Intent launch;
    }

    private final List<AppEntry> all = new ArrayList<>();
    private final List<AppEntry> shown = new ArrayList<>();
    private final Set<String> favourites = new HashSet<>();
    private final AppAdapter adapter = new AppAdapter();
    private SharedPreferences prefs;
    private String query = "";
    private int accent, muted;
    private String appliedTheme;

    @Override
    protected void attachBaseContext(android.content.Context base) { super.attachBaseContext(Scale.wrap(base)); }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        appliedTheme = ThemeStore.theme(this);
        setTheme(ThemeStore.style(appliedTheme));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apps);
        accent = ThemeStore.color(this, R.attr.cAccent);
        muted = ThemeStore.color(this, R.attr.cMuted);
        prefs = getSharedPreferences("carlcd", MODE_PRIVATE);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_themes).setOnClickListener(v -> startActivity(new Intent(this, ThemesActivity.class)));

        loadApps();
        loadFavourites();
        GridView grid = findViewById(R.id.grid);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            try {
                startActivity(shown.get(position).launch);
            } catch (Exception e) {
                Toast.makeText(this, "Can't open that app", Toast.LENGTH_SHORT).show();
            }
        });
        // remote control: hold OK on an app to star or un-star it
        grid.setOnItemLongClickListener((parent, view, position, id) -> { toggleFavourite(shown.get(position)); return true; });
        ((EditText) findViewById(R.id.search)).addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.US);
                refresh();
            }
        });
        refresh();
    }

    /** Opened by touch, nothing is focused yet: the first remote key press lands on the first app. */
    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        int code = event.getKeyCode();
        boolean nav = code == android.view.KeyEvent.KEYCODE_DPAD_DOWN || code == android.view.KeyEvent.KEYCODE_DPAD_UP
                || code == android.view.KeyEvent.KEYCODE_DPAD_LEFT || code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT
                || code == android.view.KeyEvent.KEYCODE_DPAD_CENTER || code == android.view.KeyEvent.KEYCODE_ENTER;
        View cf = getCurrentFocus();
        int id = cf == null ? View.NO_ID : cf.getId();
        boolean placed = id == R.id.grid || id == R.id.search || id == R.id.btn_back || id == R.id.btn_themes;
        if (nav && !placed && !shown.isEmpty()) {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                GridView grid = findViewById(R.id.grid);
                grid.requestFocusFromTouch();
                grid.setSelection(0);
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!ThemeStore.theme(this).equals(appliedTheme)) recreate();   // changed on the Themes page
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            AppEntry e = new AppEntry();
            e.key = pkg + "/" + ri.activityInfo.name;
            e.label = String.valueOf(ri.loadLabel(pm));
            e.lower = e.label.toLowerCase(Locale.US);
            e.icon = ri.loadIcon(pm);
            e.launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(pkg, ri.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            all.add(e);
        }
    }

    private void loadFavourites() {
        Set<String> saved = prefs.getStringSet("favourites", null);
        if (saved != null) {
            favourites.addAll(saved);
            return;
        }
        for (AppEntry e : all) {
            String key = e.key.toLowerCase(Locale.US);
            for (String d : DEFAULT_FAVOURITES) {
                if (key.contains(d) || e.lower.contains(d)) { favourites.add(e.key); break; }
            }
        }
        prefs.edit().putStringSet("favourites", new HashSet<>(favourites)).apply();
    }

    private void toggleFavourite(AppEntry e) {
        if (!favourites.remove(e.key)) favourites.add(e.key);
        prefs.edit().putStringSet("favourites", new HashSet<>(favourites)).apply();
        refresh();
    }

    /** Favourites first, then everything else, each group A to Z; filtered by the search text. */
    private void refresh() {
        shown.clear();
        for (AppEntry e : all) if (query.isEmpty() || e.lower.contains(query)) shown.add(e);
        Collections.sort(shown, (a, b) -> {
            boolean fa = favourites.contains(a.key), fb = favourites.contains(b.key);
            return fa != fb ? (fa ? -1 : 1) : a.label.compareToIgnoreCase(b.label);
        });
        findViewById(R.id.empty).setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int position) { return shown.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView != null ? convertView
                    : LayoutInflater.from(AppsActivity.this).inflate(R.layout.item_app, parent, false);
            final AppEntry e = shown.get(position);
            ((ImageView) v.findViewById(R.id.app_icon)).setImageDrawable(e.icon);
            ((TextView) v.findViewById(R.id.app_name)).setText(e.label);
            TextView star = v.findViewById(R.id.app_star);
            boolean fav = favourites.contains(e.key);
            star.setText(fav ? "★" : "☆");
            star.setTextColor(fav ? accent : muted);
            star.setOnClickListener(x -> toggleFavourite(e));
            return v;
        }
    }
}
