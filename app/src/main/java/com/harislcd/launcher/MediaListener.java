package com.harislcd.launcher;

import android.service.notification.NotificationListenerService;

/**
 * Does nothing by itself. Android only lets an app see what other apps are playing if it owns an
 * enabled notification listener, so this exists to make the Now Playing section possible.
 */
public class MediaListener extends NotificationListenerService { }
