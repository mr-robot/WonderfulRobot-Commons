package com.wonderfulrobot.commons.music.data;

public class MusicProviderFactory {
	
	private static MusicProviderFactory _instance;
	
	public static MusicProviderFactory getInstance(){
		if(_instance == null)
			_instance = new MusicProviderFactory();
		return _instance;
	}

	private IMusicProvider mProvider;
	
	public IMusicProvider getProvider(){
		return mProvider;
	}
	
	public void setProvider(IMusicProvider provider){
		this.mProvider = provider;
	}

}
