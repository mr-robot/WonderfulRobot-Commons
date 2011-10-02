package com.wonderfulrobot.commons.storage;

import java.util.HashMap;

import android.database.SQLException;

public interface DBAdapter {
	public abstract DBAdapter open(boolean readOnly) throws SQLException;

	public abstract DBAdapter open(boolean readOnly, String databaseName)
			throws SQLException;

	public abstract DBAdapter open(boolean readOnly,
			String databaseName, int databaseVersion) throws SQLException;

	public abstract void close();

	public abstract void createAll();

	public abstract void dropAll();

	public abstract void execSQL(String sql);

	public abstract int getDBVersion();
	
	public abstract boolean persistObjects(String table, HashMap<String, Object> data);
}