package com.wonderfulrobot.commons.music.data;

import com.wonderfulrobot.commons.music.MusicPlayerService;

/**
 * 
 * Basic Implementation of {@link Track} for use with {@link MusicProvider} and {@link MusicPlayerService}
 * 
 * @author Mr Robot
 *
 */
public class TrackImpl implements Track {
	
	/*
	 * URL representing a Music Stream
	 */
	private String url;
	private String title;

	public TrackImpl(String title, String url){
		this.url = url;
		this.title = title;
	}

	@Override
	public String getTitle() {
		return title;
	}

	@Override
	public String getStreamURL() {
		return url;
	}

}
