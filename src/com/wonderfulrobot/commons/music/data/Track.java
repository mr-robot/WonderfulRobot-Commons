package com.wonderfulrobot.commons.music.data;

import com.wonderfulrobot.commons.music.MusicPlayerService;

/**
 * 
 * Representation of a Track for Playback by the {@link MusicPlayerService}
 * 
 * @author Mr Robot
 *
 */
public interface Track {

	public String getTitle();
	public String getStreamURL();
	
}
