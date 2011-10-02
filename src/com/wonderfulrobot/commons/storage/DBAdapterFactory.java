package com.wonderfulrobot.commons.storage;

public interface DBAdapterFactory {
	
	public DBAdapter createAdapter();

	public DBAdapter createAdapter(String dbName);
}
