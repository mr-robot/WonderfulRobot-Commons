package com.wonderfulrobot.commons.music.data;

import com.wonderfulrobot.commons.music.MusicPlayerService;

/**
 * 
 * Defines an interface for the MusicPlayerService to consume in order to retrieve content to play
 * 
 * @author Mr Robot
 *
 */
public interface IMusicProvider {

	/*
	 * Either Provide the Track immediately if it is ready, or load it asynchronously and hit the callback
	 */
	public abstract Track getNextTrack(MusicPlayerService service);

	public abstract Track skipToTrack(MusicPlayerService service);
	
	/*
	 * Either Provide the Track immediately if it is ready, or load it asynchronously and hit the callback
	 */
	public abstract Track getPreviousTrack(MusicPlayerService service);

}