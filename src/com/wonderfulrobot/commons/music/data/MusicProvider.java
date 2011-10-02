package com.wonderfulrobot.commons.music.data;

import com.wonderfulrobot.commons.music.MusicPlayerService;


public abstract class MusicProvider implements IMusicProvider {
	
	public MusicProvider(){
		
	}
	/*
	 * Either Provide the Track immediately if it is ready, or load it asynchronously and hit the callback
	 */
	/* (non-Javadoc)
	 * @see com.wonderfulrobot.commons.music.data.IMusicProvider#getNextTrack(com.wonderfulrobot.commons.music.MusicPlayerService)
	 */
	@Override
	public Track getNextTrack(MusicPlayerService service){
		
		//
		service.playSong(null);
		return null;
	}
	
	/* (non-Javadoc)
	 * @see com.wonderfulrobot.commons.music.data.IMusicProvider#getPreviousTrack(com.wonderfulrobot.commons.music.MusicPlayerService)
	 */
	@Override
	public Track getPreviousTrack(MusicPlayerService service){
		
		service.playSong(null);
		
		return null;
	}

}
