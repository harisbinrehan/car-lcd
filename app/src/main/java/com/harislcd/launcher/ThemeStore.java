package com.harislcd.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.TypedValue;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

/** The saved theme and light/dark mode, and everything that depends on them. */
final class ThemeStore {
    private ThemeStore() {}

    /** key, label. Every theme has a light and a dark version. */
    static final String[][] THEMES = {
            {"classic", "Classic"},
            {"crimson", "Crimson"},
            {"ivory", "Ivory"},
            {"gold", "Gold"},
            {"forest", "Forest"},
            {"violet", "Violet"},
            {"silver", "Silver"},
            {"sky", "Sky"},
            {"noir", "Noir"},
            {"amber", "Amber"},
            {"nordic", "Nordic"},
            {"midnight", "Midnight"},
            {"mocha", "Mocha"},
            {"carbon", "Carbon"},
    };

    /** auto = follow the car's lights, light, dark */
    static final String[] MODES = {"auto", "light", "dark"};

    /**
     * The unit's MCU reports the parking/head lights in this system setting:
     * 1 = lights on (the unit also dims the screen), 0 = lights off.
     */
    static final String LIGHTS_SETTING = "carletter_night_mode";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("carlcd", Context.MODE_PRIVATE);
    }

    static String theme(Context c) {
        String t = prefs(c).getString("theme", "classic");
        if (t.equals("auto") || t.equals("light") || t.equals("dark")) {   // older versions stored the mode here
            prefs(c).edit().putString("mode", t).putString("theme", "classic").apply();
            return "classic";
        }
        return t;
    }

    static void saveTheme(Context c, String theme) { prefs(c).edit().putString("theme", theme).apply(); }

    static String mode(Context c) { return prefs(c).getString("mode", "auto"); }

    /**
     * The text for the car's number plate. The pictures ship with a blank plate (the app and its source are
     * public); the owner types their own on the Themes page and it stays on this unit only. Never log it.
     */
    static String plate(Context c) { return prefs(c).getString("plate", ""); }

    static void savePlate(Context c, String text) { prefs(c).edit().putString("plate", text == null ? "" : text.trim()).apply(); }

    static void saveMode(Context c, String mode) { prefs(c).edit().putString("mode", mode).apply(); }

    static int style(String theme) {
        switch (theme) {
            case "crimson": return R.style.Theme_CarLCD_Crimson;
            case "ivory": return R.style.Theme_CarLCD_Ivory;
            case "gold": return R.style.Theme_CarLCD_Gold;
            case "forest": return R.style.Theme_CarLCD_Forest;
            case "violet": return R.style.Theme_CarLCD_Violet;
            case "silver": return R.style.Theme_CarLCD_Silver;
            case "sky": return R.style.Theme_CarLCD_Sky;
            case "noir": return R.style.Theme_CarLCD_Noir;
            case "amber": return R.style.Theme_CarLCD_Amber;
            case "nordic": return R.style.Theme_CarLCD_Nordic;
            case "midnight": return R.style.Theme_CarLCD_Midnight;
            case "mocha": return R.style.Theme_CarLCD_Mocha;
            case "carbon": return R.style.Theme_CarLCD_Carbon;
            default: return R.style.Theme_CarLCD;
        }
    }

    /** Each theme has its own arrangement of the same apps and widgets. */
    static int layout(String theme) {
        switch (theme) {
            case "crimson": return R.layout.activity_main_crimson;
            case "ivory": return R.layout.activity_main_ivory;
            case "gold": return R.layout.activity_main_gold;
            case "forest": return R.layout.activity_main_forest;
            case "violet": return R.layout.activity_main_violet;
            case "silver": return R.layout.activity_main_silver;
            case "sky": return R.layout.activity_main_sky;
            case "noir": return R.layout.activity_main_noir;
            case "amber": return R.layout.activity_main_amber;
            case "nordic": return R.layout.activity_main_nordic;
            case "midnight": return R.layout.activity_main_midnight;
            case "mocha": return R.layout.activity_main_mocha;
            case "carbon": return R.layout.activity_main_carbon;
            default: return R.layout.activity_main;
        }
    }

    static boolean lightsOn(Context c) {
        return Settings.System.getInt(c.getContentResolver(), LIGHTS_SETTING, 0) == 1;
    }

    /** Auto follows the car's lights: lights on = dark, lights off = light. */
    static boolean isDark(Context c) {
        String m = mode(c);
        return m.equals("auto") ? lightsOn(c) : m.equals("dark");
    }

    /** Returns true if the day/night mode changed (AppCompat then recreates the screen by itself). */
    static boolean applyNight(Context c) {
        int want = isDark(c) ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
        if (AppCompatDelegate.getDefaultNightMode() == want) return false;
        AppCompatDelegate.setDefaultNightMode(want);
        return true;
    }

    /** Resolves one of the cXxx theme colours. */
    static int color(Context c, int attr) {
        TypedValue v = new TypedValue();
        c.getTheme().resolveAttribute(attr, v, true);
        return v.resourceId != 0 ? ContextCompat.getColor(c, v.resourceId) : v.data;
    }
}
