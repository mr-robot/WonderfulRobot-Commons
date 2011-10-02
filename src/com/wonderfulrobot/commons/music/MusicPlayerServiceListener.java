package com.wonderfulrobot.commons.music;

import com.wonderfulrobot.commons.music.MusicPlayerService.State;
import com.wonderfulrobot.commons.music.data.Track;

/**
 * 
 * Callback interface for the MusicPlayerService to notify Clients of updates to Music Playback
 * 
 * @author Mr Robot
 * 
 */
public interface MusicPlayerServiceListener {

	public void setDuration(int duration);
	
	public void setCurrentPosition(int currentPosition);

	public void setCurrentState(State state);

	public void setIsLooping(boolean looping);
	
	public void setNotSeekable(boolean seekable);

	public void setCurrentTrack(Track currentPlayable);
	
	public void bufferingUpdate(int percent);
	
	public void setError(String error);
	
}
