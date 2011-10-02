package com.wonderfulrobot.commons.music.notification;

import com.wonderfulrobot.commons.R;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class NotificationHelperImpl implements INotificationHelper {


	private Context ctx;
	private Class intentClass;
	
	public NotificationHelperImpl(Context ctx){
		this.ctx = ctx;
	}
	
	public NotificationHelperImpl(Context ctx, Class intentClass){
		this.ctx = ctx;
		this.intentClass = intentClass;
	}
	
	private static NotificationHelperImpl _instance;
	
	 public static synchronized INotificationHelper getInstance(Context context) {
		  if (_instance==null) {
			  _instance = new NotificationHelperImpl(context);
		  }
		  return _instance;
	 } 
	
	public Notification getNewNotification(String text){
		
		Log.i("NotificationHelperImpl", "New Notification " + text);
		
		PendingIntent pi = null;
		
		if(intentClass != null){
	        pi = PendingIntent.getActivity(ctx, 0,
	                new Intent(ctx.getApplicationContext(), intentClass),
	                PendingIntent.FLAG_UPDATE_CURRENT);
		}
		else{
	        pi = PendingIntent.getActivity(ctx, 0,
	                new Intent(), // new Intent(getApplicationContext(), MainActivity.class)
	                PendingIntent.FLAG_UPDATE_CURRENT);
		}
		

        Notification mNotification = new Notification(R.drawable.status_icon, text,System.currentTimeMillis() );
        //mNotification.tickerText = text;
       // mNotification.when = System.currentTimeMillis();
        
       // mNotification.icon = R.drawable.status_icon;
       // mNotification.flags |= Notification.FLAG_ONGOING_EVENT;
        mNotification.setLatestEventInfo(ctx, "Playing",
                text, pi);
        
        return mNotification;
	}
	
	public Notification updateNotification(String text, Notification mNotification){

		Log.i("NotificationHelperImpl", "Updating with " + text);
		
        PendingIntent pi = PendingIntent.getActivity(ctx, 0,
        		new Intent(), // new Intent(getApplicationContext(), MainActivity.class)
                PendingIntent.FLAG_UPDATE_CURRENT);
        mNotification.setLatestEventInfo(ctx, "Playing", text, pi);
        return mNotification;
	}
}
