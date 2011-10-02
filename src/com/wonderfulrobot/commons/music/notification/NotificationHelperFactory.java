package com.wonderfulrobot.commons.music.notification;

import android.content.Context;

public class NotificationHelperFactory {
	
	private static NotificationHelperFactory _instance;

	public NotificationHelperFactory(){
	}
	
	public NotificationHelperFactory(Context ctx){
		this.mHelper = new NotificationHelperImpl(ctx);
	}

	public static NotificationHelperFactory getInstance(Context ctx){
		if(_instance == null)
			_instance = new NotificationHelperFactory(ctx);
		return _instance;
	}
	
	public static NotificationHelperFactory getInstance(){
		if(_instance == null)
			_instance = new NotificationHelperFactory();
		return _instance;
	}

	private INotificationHelper mHelper;
	
	public INotificationHelper getProvider(){
		return mHelper;
	}
	
	public void setProvider(INotificationHelper mHelper){
		this.mHelper = mHelper;
	}

}
