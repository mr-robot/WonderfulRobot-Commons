package com.wonderfulrobot.commons.music.util;

import com.wonderfulrobot.commons.R;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * 
 * Helper Class for Interacting with the Android {@link NotificationManager}
 * 
 * @author T804772
 *
 */
public class NotificationHelper {
	
	private Context ctx;
	
	public NotificationHelper(Context ctx){
		this.ctx = ctx;
	}
	
	private static NotificationHelper _instance;
	
	 public static synchronized NotificationHelper getInstance(Context context) {
		  if (_instance==null) {
			  _instance = new NotificationHelper(context);
		  }
		  return _instance;
	 } 
	
	public Notification getNewNotification(String text){
        PendingIntent pi = PendingIntent.getActivity(ctx, 0,
                new Intent(), // new Intent(getApplicationContext(), MainActivity.class)
                PendingIntent.FLAG_UPDATE_CURRENT);
        Notification mNotification = new Notification(R.drawable.status_icon, text, System.currentTimeMillis());
        
       // mNotification.icon = R.drawable.status_icon;
        //mNotification.flags |= Notification.FLAG_ONGOING_EVENT;
        mNotification.setLatestEventInfo(ctx, "Commons",
                text, pi);
        
        return mNotification;
	}
	
	public Notification updateNotification(String text, Notification mNotification){
        PendingIntent pi = PendingIntent.getActivity(ctx, 0,
        		new Intent(), // new Intent(getApplicationContext(), MainActivity.class)
                PendingIntent.FLAG_UPDATE_CURRENT);
        mNotification.setLatestEventInfo(ctx, "MusicPlayer", text, pi);
        return mNotification;
	}
}
