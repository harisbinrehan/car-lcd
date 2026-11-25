package com.harislcd.launcher;

import android.app.Application;

/** Starts the diary (see Diag) before any screen opens, so crashes on the way up are caught too. */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Diag.init(this);
    }
}
