package com.wonderfulrobot.commons.music.notification;

import android.app.Notification;

public interface INotificationHelper {

	public Notification getNewNotification(String text);
	public Notification updateNotification(String text, Notification mNotification);
}
