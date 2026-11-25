package com.harislcd.launcher;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** Fetches current weather from Open-Meteo (no API key). Falls back silently if offline. */
public class WeatherClient {

    public interface Callback {
        void onWeather(int tempC, int highC, int lowC, String condition, int weatherCode);
        void onError();
    }

    private final Handler main = new Handler(Looper.getMainLooper());

    public void fetch(final double lat, final double lon, final Callback cb) {
        new Thread(() -> {
            try {
                String url = "https://api.open-meteo.com/v1/forecast"
                        + "?latitude=" + lat + "&longitude=" + lon
                        + "&current=temperature_2m,weather_code"
                        + "&daily=temperature_2m_max,temperature_2m_min"
                        + "&timezone=auto&forecast_days=1";
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("User-Agent", "CarLCD/1.0");
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                c.disconnect();

                JSONObject root = new JSONObject(sb.toString());
                JSONObject cur = root.getJSONObject("current");
                int temp = (int) Math.round(cur.getDouble("temperature_2m"));
                int code = cur.getInt("weather_code");
                JSONObject daily = root.getJSONObject("daily");
                int high = (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0));
                int low = (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0));
                final String cond = describe(code);
                main.post(() -> cb.onWeather(temp, high, low, cond, code));
            } catch (Exception e) {
                main.post(cb::onError);
            }
        }).start();
    }

    /** WMO weather interpretation codes -> short label. */
    public static String describe(int code) {
        if (code == 0) return "Clear sky";
        if (code == 1) return "Mainly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Rain showers";
        if (code >= 85 && code <= 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "—";
    }

    /** True for codes that should show a sunny glyph. */
    public static boolean isSunny(int code) {
        return code <= 1;
    }
}
