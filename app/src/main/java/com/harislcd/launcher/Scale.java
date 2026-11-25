package com.harislcd.launcher;

import android.content.Context;
import android.content.res.Configuration;

/**
 * The screens are laid out for a 1280 x 648 dp area (the car unit: 1280 x 720 minus its top bar).
 * On any other screen this picks the density that makes the same area fit, so a 1080p or 4K TV
 * shows the same layout scaled up instead of a different, broken one. System font scaling is
 * ignored for the same reason.
 */
final class Scale {
    private Scale() {}

    static Context wrap(Context base) {
        Configuration cur = base.getResources().getConfiguration();
        float d = cur.densityDpi / 160f;
        float wPx = Math.max(cur.screenWidthDp, cur.screenHeightDp) * d;
        float hPx = Math.min(cur.screenWidthDp, cur.screenHeightDp) * d;
        float s = Math.min(wPx / 1280f, hPx / 648f);
        int dpi = Math.max(80, Math.round(160f * s));
        if (Math.abs(dpi - cur.densityDpi) <= 2 && cur.fontScale == 1f) return base;
        Configuration c = new Configuration(cur);
        c.densityDpi = dpi;
        c.fontScale = 1f;
        return base.createConfigurationContext(c);
    }
}
