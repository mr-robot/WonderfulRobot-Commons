package com.wonderfulrobot.commons.music;

/*   
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnInfoListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import com.wonderfulrobot.commons.music.data.MusicProviderFactory;
import com.wonderfulrobot.commons.music.data.Track;
import com.wonderfulrobot.commons.music.data.TrackImpl;
import com.wonderfulrobot.commons.music.notification.INotificationHelper;
import com.wonderfulrobot.commons.music.notification.NotificationHelperFactory;
import com.wonderfulrobot.commons.music.util.AudioFocusHelper;
import com.wonderfulrobot.commons.music.util.MusicFocusable;
import com.wonderfulrobot.commons.music.util.ProviderCompleteListener;

/**
 * Service that handles media playback. This is the Service through which we perform all the media
 * handling in our application. Upon initialization, it starts a {@link MediaRetriever} to scan
 * the user's media. Then, it waits for Intents (which come from our main activity,
 * {@link MainActivity}, which signal the service to perform specific operations: Play, Pause,
 * Rewind, Skip, etc.
 */
public class MusicPlayerService extends Service implements OnCompletionListener, OnPreparedListener,
                OnErrorListener, MusicFocusable, OnBufferingUpdateListener,
                ProviderCompleteListener, OnInfoListener{

    //Foreground Compat
    private static final Class<?>[] mSetForegroundSignature = new Class[] {
        boolean.class};
    private static final Class<?>[] mStartForegroundSignature = new Class[] {
        int.class, Notification.class};
    private static final Class<?>[] mStopForegroundSignature = new Class[] {
        boolean.class};

    //Foreground Compat
    private Method mSetForeground;
    private Method mStartForeground;
    private Method mStopForeground;
    private Object[] mSetForegroundArgs = new Object[1];
    private Object[] mStartForegroundArgs = new Object[2];
    private Object[] mStopForegroundArgs = new Object[1];
	
	
    NotificationManager mNotificationManager;
    
    //NotificationHelper mNotificationHelper;
    
    INotificationHelper mNotificationHelper;

    // our media player
    MediaPlayer mPlayer = null;

    // our AudioFocusHelper object, if it's available (it's available on SDK level >= 8)
    // If not available, this will be null. Always check for null before using!
    AudioFocusHelper mAudioFocusHelper = null;

    // indicates the state our service:
    public enum State {
        Retrieving, // the MediaRetriever is retrieving music
        Stopped,    // media player is stopped and not prepared to play
        Preparing,  // media player is preparing...
        Playing,    // playback active (media player ready!). (but the media player may actually be
                    // paused in this state if we don't have audio focus. But we stay in this state
                    // so that we know we have to resume playback once we get focus back)
        Paused      // playback paused (media player ready!)
    };

    State mState = State.Stopped;

    // if in Retrieving mode, this flag indicates whether we should start playing immediately
    // when we are ready or not.
    boolean mStartPlayingAfterRetrieve = false;

    // if mStartPlayingAfterRetrieve is true, this variable indicates the URL that we should
    // start playing when we are ready. If null, we should play a random song from the device
    Uri mWhatToPlayAfterRetrieve = null;

    enum PauseReason {
        UserRequest,  // paused by user request
        FocusLoss,    // paused because of audio focus loss
    };

    // why did we pause? (only relevant if mState == State.Paused)
    PauseReason mPauseReason = PauseReason.UserRequest;

    // do we have audio focus?
    enum AudioFocus {
        NoFocusNoDuck,    // we don't have audio focus, and can't duck
        NoFocusCanDuck,   // we don't have focus, but can play at a low volume ("ducking")
        Focused           // we have full audio focus
    }
    AudioFocus mAudioFocus = AudioFocus.NoFocusNoDuck;

    // title of the song we are currently playing
    String mSongTitle = "";

    // whether the song we are playing is streaming from the network
    boolean mIsStreaming = false;

    // Wifi lock that we hold when streaming files from the internet, in order to prevent the
    // device from shutting off the Wifi radio
    WifiLock mWifiLock;

    // The tag we put on debug messages
    final static String TAG = "MUSICPLAYERSERVICE";

    // These are the Intent actions that we are prepared to handle. Notice that the fact these
    // constants exist in our class is a mere convenience: what really defines the actions our
    // service can handle are the <action> tags in the <intent-filters> tag for our service in
    // AndroidManifest.xml.
    public static final String ACTION_RELOAD = "com.wonderfulrobot.commons.music.musicplayerservice.action.RELOAD";
    
    public static final String ACTION_PLAY = "com.wonderfulrobot.commons.music.musicplayerservice.action.PLAY";
    public static final String ACTION_PAUSE = "com.wonderfulrobot.commons.music.musicplayerservice.action.PAUSE";
    public static final String ACTION_STOP = "com.wonderfulrobot.commons.music.musicplayerservice.action.STOP";
    public static final String ACTION_SKIP = "com.wonderfulrobot.commons.music.musicplayerservice.action.SKIP";
    public static final String ACTION_PREVIOUS = "com.wonderfulrobot.commons.music.musicplayerservice.action.PREVIOUS";
    public static final String ACTION_REWIND = "com.wonderfulrobot.commons.music.musicplayerservice.action.REWIND";
    public static final String ACTION_URL = "com.wonderfulrobot.commons.music.musicplayerservice.action.URL";

    // The volume we set the media player to when we lose audio focus, but are allowed to reduce
    // the volume instead of stopping playback.
    public final float DUCK_VOLUME = 0.1f;

    // The ID we use for the notification (the onscreen alert that appears at the notification
    // area at the top of the screen as an icon -- and as text as well if the user expands the
    // notification area).
    final int NOTIFICATION_ID = 91;

    // Our instance of our MusicRetriever, which handles scanning for media and
    // providing titles and URIs as we need.
    //MusicProvider mRetriever;
    
     // MusicProviderFactory mFactory;

    Notification mNotification = null;
    private final IBinder mBinder = new LocalBinder();
    
    private ArrayList<MusicPlayerServiceListener> listeners = new ArrayList<MusicPlayerServiceListener>();
	private Track mCurrentTrack;
	

	private long lastActionTime=0L;
	private Handler mCurrentPositionHandler;
	private boolean hasRetried;

    /**
     * Makes sure the media player exists and has been reset. This will create the media player
     * if needed, or reset the existing media player if one already exists.
     */
    void createMediaPlayerIfNeeded() {
        if (mPlayer == null) {
            mPlayer = new MediaPlayer();

            // Make sure the media player will acquire a wake-lock while playing. If we don't do
            // that, the CPU might go to sleep while the song is playing, causing playback to stop.
            //
            // Remember that to use this, we have to declare the android.permission.WAKE_LOCK
            // permission in AndroidManifest.xml.
            mPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);

            // we want the media player to notify us when it's ready preparing, and when it's done
            // playing:
            mPlayer.setOnPreparedListener(this);
            mPlayer.setOnCompletionListener(this);
            mPlayer.setOnErrorListener(this);
            mPlayer.setOnInfoListener(this);
        }
        else
            mPlayer.reset();
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "debug: Creating service");

        // Create the Wifi lock (this does not acquire the lock, this just creates it)
        mWifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE))
                        .createWifiLock(WifiManager.WIFI_MODE_FULL, "mylock");

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        

		mCurrentPositionHandler = new Handler();
		
        
        try {
            mStartForeground = getClass().getMethod("startForeground",
                    mStartForegroundSignature);
            mStopForeground = getClass().getMethod("stopForeground",
                    mStopForegroundSignature);
        } catch (NoSuchMethodException e) {
            // Running on an older platform.
            mStartForeground = mStopForeground = null;
            try {
                mSetForeground = getClass().getMethod("setForeground",
                        mSetForegroundSignature);
            } catch (NoSuchMethodException ee) {
                throw new IllegalStateException(
                        "OS doesn't have Service.startForeground OR Service.setForeground!");
            }
        }
        //.setProvider(NotificationHelperImpl.getInstance(this));
        
        mNotificationHelper = NotificationHelperFactory.getInstance(this).getProvider();
        

        //mNotificationHelper = NotificationHelper.getInstance(this);
        
        // Create the retriever and start an asynchronous task that will prepare it.
        //(new PrepareMusicRetrieverTask(mRetriever,this)).execute();

        // create the Audio Focus Helper, if the Audio Focus feature is available (SDK 8 or above)
        if (android.os.Build.VERSION.SDK_INT >= 8)
            mAudioFocusHelper = new AudioFocusHelper(getApplicationContext(), this);
        else
            mAudioFocus = AudioFocus.Focused; // no focus feature, so we always "have" audio focus
        

    }
    
    @Override
    public void onStart(Intent intent, int startId) {
        handleCommand(intent);
    }

    /**
     * Called when we receive an Intent. When we receive an intent sent to us via startService(),
     * this is the method that gets called. So here we react appropriately depending on the
     * Intent's action, which specifies what is being requested of us.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    	handleCommand(intent);

        return START_NOT_STICKY; // Means we started the service, but don't want it to
                                 // restart in case it's killed.
    }
    
    void handleCommand(Intent intent) {
    	if(intent != null && intent.getAction() != null){
            String action = intent.getAction();
            if (action.equals(ACTION_PLAY)) processPlayRequest();
            else if (action.equals(ACTION_RELOAD)) processReloadRequest();
            else if (action.equals(ACTION_PAUSE)) processPauseRequest();
            else if (action.equals(ACTION_SKIP)) processSkipRequest();
            else if (action.equals(ACTION_PREVIOUS)) processPreviousRequest();
            else if (action.equals(ACTION_STOP)) processStopRequest();
            else if (action.equals(ACTION_REWIND)) processRewindRequest();
            else if (action.equals(ACTION_URL)) processAddRequest(intent);
    	}
    }
    
    public void setListener(MusicPlayerServiceListener mListener, boolean syncState){
    	if(mListener != null){
    		if(!listeners.contains(mListener))
    			listeners.add(mListener);
    	}

		if(syncState){
			setListenerState();
		}
    	
    }
    

    public void removeListener(MusicPlayerServiceListener mListener){
    	if(mListener != null){
    		listeners.remove(mListener);
    	}
    }
    
    void processReloadRequest() {
        if (mState == State.Playing || mState == State.Paused) {
            mState = State.Stopped;           
        }
        
        processPlayRequest();
    }


    void processPlayRequest() {
        if (mState == State.Retrieving) {
            // If we are still retrieving media, just set the flag to start playing when we're
            // ready
            mWhatToPlayAfterRetrieve = null; // play a random song
            mStartPlayingAfterRetrieve = true;
            return;
        }

        tryToGetAudioFocus();

        if (mState == State.Stopped) {
            // If we're stopped, just go ahead to the next song and start playing
            playSong(true,false);
        }
        else if (mState == State.Paused) {
            // If we're paused, just continue playback and restore the 'foreground service' state.
            mState = State.Playing;
            notifyCurrentState(mState);
            setUpAsForeground(mSongTitle + " (playing)");
            configAndStartMediaPlayer();
        }
    }

    void processPauseRequest() {
        if (mState == State.Retrieving) {
            // If we are still retrieving media, clear the flag that indicates we should start
            // playing when we're ready
            mStartPlayingAfterRetrieve = false;
            return;
        }

        if (mState == State.Playing) {
            // Pause media player and cancel the 'foreground service' state.
            mState = State.Paused;
            notifyCurrentState(mState);
            mPlayer.pause();
            relaxResources(false); // while paused, we always retain the MediaPlayer
            giveUpAudioFocus();
        }
    }

    void processRewindRequest() {
        if (mState == State.Playing || mState == State.Paused)
            mPlayer.seekTo(0);
    }
    
    void processPreviousRequest() {
        if (mState == State.Playing || mState == State.Paused) {
            tryToGetAudioFocus();
            playSong(false,true);
        }
    }

    void processSkipRequest() {
        if (mState == State.Playing || mState == State.Paused) {
            tryToGetAudioFocus();
            playSong(true,true);
        }
    }
    
    void processStopRequest() {
        if (mState == State.Playing || mState == State.Paused) {
            mState = State.Stopped;

            // let go of all resources...
            relaxResources(true);
            giveUpAudioFocus();

            // service is no longer necessary. Will be started again if needed.
            stopSelf();
        }
    }

    /**
     * Releases resources used by the service for playback. This includes the "foreground service"
     * status and notification, the wake locks and possibly the MediaPlayer.
     *
     * @param releaseMediaPlayer Indicates whether the Media Player should also be released or not
     */
    void relaxResources(boolean releaseMediaPlayer) {
        // stop being a foreground service
        stopForegroundCompat(NOTIFICATION_ID);

        // stop and release the Media Player, if it's available
        if (releaseMediaPlayer && mPlayer != null) {
            mPlayer.reset();
            mPlayer.release();
            mPlayer = null;
        }

        // we can also release the Wifi lock, if we're holding it
        if (mWifiLock != null && mWifiLock.isHeld()) mWifiLock.release();
    }

    void giveUpAudioFocus() {
        if (mAudioFocus == AudioFocus.Focused && mAudioFocusHelper != null
                                && mAudioFocusHelper.abandonFocus())
            mAudioFocus = AudioFocus.NoFocusNoDuck;
    }

    /**
     * Reconfigures MediaPlayer according to audio focus settings and starts/restarts it. This
     * method starts/restarts the MediaPlayer respecting the current audio focus state. So if
     * we have focus, it will play normally; if we don't have focus, it will either leave the
     * MediaPlayer paused or set it to a low volume, depending on what is allowed by the
     * current focus settings. This method assumes mPlayer != null, so if you are calling it,
     * you have to do so from a context where you are sure this is the case.
     */
    void configAndStartMediaPlayer() {
        if (mAudioFocus == AudioFocus.NoFocusNoDuck) {
            // If we don't have audio focus and can't duck, we have to pause, even if mState
            // is State.Playing. But we stay in the Playing state so that we know we have to resume
            // playback once we get the focus back.
            if (mPlayer.isPlaying()) mPlayer.pause();
            return;
        }
        else if (mAudioFocus == AudioFocus.NoFocusCanDuck)
            mPlayer.setVolume(DUCK_VOLUME, DUCK_VOLUME);  // we'll be relatively quiet
        else
            mPlayer.setVolume(1.0f, 1.0f); // we can be loud

        if (!mPlayer.isPlaying()) mPlayer.start();
        


		mCurrentPositionHandler.postDelayed(onEverySecond, 1000);
    }

    void processAddRequest(Intent intent) {
        // user wants to play a song directly by URL or path. The URL or path comes in the "data"
        // part of the Intent. This Intent is sent by {@link MainActivity} after the user
        // specifies the URL/path via an alert box.
        if (mState == State.Retrieving) {
            // we'll play the requested URL right after we finish retrieving
            mWhatToPlayAfterRetrieve = intent.getData();
            mStartPlayingAfterRetrieve = true;
        }
        else if (mState == State.Playing || mState == State.Paused || mState == State.Stopped) {
            Log.i(TAG, "Playing from URL/path: " + intent.getData().toString());
            tryToGetAudioFocus();
            playSong(intent.getData().toString(),"");
        }
    }

    /**
     * Shortcut to making and displaying a toast. Seemed cleaner than repeating
     * this code everywhere.
     */
    void say(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    void tryToGetAudioFocus() {
        if (mAudioFocus != AudioFocus.Focused && mAudioFocusHelper != null
                        && mAudioFocusHelper.requestFocus())
            mAudioFocus = AudioFocus.Focused;
    }
    
    

    /**
     * Starts playing the next song. If manualUrl is null, the next song will be randomly selected
     * from our Media Retriever (that is, it will be a random song in the user's device). If
     * manualUrl is non-null, then it specifies the URL or path to the song that will be played
     * next.
     * 
     * @param isNext - Determines whether the next song or previous song should be played
     * 
     */
    public void playSong(Track track) {
        mState = State.Stopped;
        relaxResources(false); // release everything except MediaPlayer

        try {
        	if(track != null){
		        mCurrentTrack = track;
		
		        // set the source of the media player a a content URI
		        createMediaPlayerIfNeeded();
		        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		        mPlayer.setDataSource(track.getStreamURL());
		        //mPlayer.setDataSource(getApplicationContext(), item.getURI());
		        mSongTitle = track.getTitle();
	        }
        	prepareAndPlayTrack(mCurrentTrack, mSongTitle);
        }
        catch (IOException ex) {
            Log.e("MusicService", "IOException playing next song: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
    

    void playSong(String manualUrl, String mSongTitle) {
        mState = State.Stopped;
        relaxResources(false); // release everything except MediaPlayer

        try {
            if (manualUrl != null) {
                // set the source of the media player to a manual URL or path
                createMediaPlayerIfNeeded();
                mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mPlayer.setDataSource(manualUrl);
                mIsStreaming = manualUrl.startsWith("http:") || manualUrl.startsWith("https:");
                mCurrentTrack = new TrackImpl(mSongTitle, manualUrl);
                
            }

            prepareAndPlayTrack(mCurrentTrack, mSongTitle);
        }
        catch (IOException ex) {
            Log.e("MusicService", "IOException playing next song: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * 
     * 
     * 
     * @param isNext - Whether the Next or Previous Song should be loaded
     * @param isSkip - Whether we have completed the current and should automatically load another, or it was user initiated
     */
    void playSong( boolean isNext,boolean isSkip) {
        mState = State.Stopped;
        relaxResources(false); // release everything except MediaPlayer

        try {

            mIsStreaming = true; // playing a remote song

            //MusicRetriever.Item item = mRetriever.getRandomItem();
            
            Track track = null;
            
            if(MusicProviderFactory.getInstance().getProvider() == null)
            	return;
            
            if(isNext)
            	if(isSkip)
            		track = MusicProviderFactory.getInstance().getProvider().skipToTrack(this);
            	else
            		track = MusicProviderFactory.getInstance().getProvider().getNextTrack(this);
            else
            	track = MusicProviderFactory.getInstance().getProvider().getPreviousTrack(this);
            
            //We could be getting the track asynchronously, just return if we don't have a track
            if (track == null) {
                //say("No song to play :-(");
            	//say("No Track to play!");
                return;
            }
            
            mCurrentTrack = track;

            // set the source of the media player a a content URI
            createMediaPlayerIfNeeded();
            //mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mPlayer.setDataSource(track.getStreamURL());
            //mPlayer.setDataSource(getApplicationContext(), item.getURI());
            mSongTitle = track.getTitle();
            

            prepareAndPlayTrack(mCurrentTrack, mSongTitle);
        }
        catch (IOException ex) {
            Log.e("MusicService", "IOException playing next song: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
    
	private void prepareAndPlayTrack(Track track, String mSongTitle) {
		notifyCurrentTrack(track);
		mState = State.Preparing;
		notifyCurrentState(mState);
		setUpAsForeground(mSongTitle + " (loading)");

		// starts preparing the media player in the background. When it's done, it will call
		// our OnPreparedListener (that is, the onPrepared() method on this class, since we set
		// the listener to 'this').
		//
		// Until the media player is prepared, we *cannot* call start() on it!
		mPlayer.prepareAsync();

		// If we are streaming from the internet, we want to hold a Wifi lock, which prevents
		// the Wifi radio from going to sleep while the song is playing. If, on the other hand,
		// we are *not* streaming, we want to release the lock if we were holding it before.
		if (mIsStreaming) mWifiLock.acquire();
		else if (mWifiLock.isHeld()) mWifiLock.release();
	}

    /** Called when media player is done playing current song. */
    @Override
    public void onCompletion(MediaPlayer player) {
        // The media player finished playing the current song, so we go ahead and start the next.
        playSong(true,false);
    }

    /** Called when media player is done preparing. */
    @Override
    public void onPrepared(MediaPlayer player) {
        // The media player is done preparing. That means we can start playing!
    	Log.d(TAG, "onPrepared");
        mState = State.Playing;
    	hasRetried = false;
        //notifyCurrentState(mState);
        setListenerState();
        updateNotification(mSongTitle + " (playing)");
        configAndStartMediaPlayer();
    }

    /** Updates the notification. */
    void updateNotification(String text) {

    	//NotificationHelperFactory.getInstance().getProvider().updateNotification(text, mNotification);
        
    	mNotificationHelper.updateNotification(text, mNotification);
    	
        mNotificationManager.notify(NOTIFICATION_ID, mNotification);
    }

    /**
     * Configures service as a foreground service. A foreground service is a service that's doing
     * something the user is actively aware of (such as playing music), and must appear to the
     * user as a notification. That's why we create the notification here.
     */
    void setUpAsForeground(String text) {

    	mNotification = mNotificationHelper.getNewNotification(text);
    	
        startForegroundCompat(NOTIFICATION_ID, mNotification);
    }
    

    void invokeMethod(Method method, Object[] args) {
        try {
        	method.invoke(this, args);
        } catch (InvocationTargetException e) {
            // Should not happen.
            Log.w("ApiDemos", "Unable to invoke method", e);
        } catch (IllegalAccessException e) {
            // Should not happen.
            Log.w("ApiDemos", "Unable to invoke method", e);
        }
    }

    /**
     * This is a wrapper around the new startForeground method, using the older
     * APIs if it is not available.
     */
    void startForegroundCompat(int id, Notification notification) {
        // If we have the new startForeground API, then use it.
        if (mStartForeground != null) {
            mStartForegroundArgs[0] = Integer.valueOf(id);
            mStartForegroundArgs[1] = notification;
            invokeMethod(mStartForeground, mStartForegroundArgs);
            return;
        }
        // Fall back on the old API.
        mSetForegroundArgs[0] = Boolean.TRUE;
        invokeMethod(mSetForeground, mSetForegroundArgs);
        mNotificationManager.notify(id, mNotification);
    }

    /**
     * This is a wrapper around the new stopForeground method, using the older
     * APIs if it is not available.
     */
    void stopForegroundCompat(int id) {
        // If we have the new stopForeground API, then use it.
        if (mStopForeground != null) {
            mStopForegroundArgs[0] = Boolean.TRUE;
            try {
                mStopForeground.invoke(this, mStopForegroundArgs);
            } catch (InvocationTargetException e) {
                // Should not happen.
                Log.w("Commons", "Unable to invoke stopForeground", e);
            } catch (IllegalAccessException e) {
                // Should not happen.
                Log.w("Commons", "Unable to invoke stopForeground", e);
            }
            return;
        }

        // Fall back on the old API.  Note to cancel BEFORE changing the
        // foreground state, since we could be killed at that point.
        if(mNotificationManager != null)
        	mNotificationManager.cancel(id);
        
	    if(mSetForeground != null){
	        mSetForegroundArgs[0] = Boolean.FALSE;
	        invokeMethod(mSetForeground, mSetForegroundArgs);
        }
    }
    
	@Override
	public boolean onInfo(MediaPlayer mp, int what, int extra) {
		return true;
	}

    /**
     * Called when there's an error playing media. When this happens, the media player goes to
     * the Error state. We warn the user about the error and reset the media player.
     */
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Toast.makeText(getApplicationContext(), "Media player error! Resetting.",
            Toast.LENGTH_SHORT).show();
        Log.e(TAG, "Error: what=" + String.valueOf(what) + ", extra=" + String.valueOf(extra));

        if(!hasRetried){
        	hasRetried = true;
        	playSong(mCurrentTrack);
        }
        else{
	        mState = State.Stopped;
	        notifyCurrentState(mState);
	        notifySetError("Music Player Error");
	        relaxResources(true);
	        giveUpAudioFocus();
        }
        return true; // true indicates we handled the error
    }

    @Override
    public void onGainedAudioFocus() {
        //Toast.makeText(getApplicationContext(), "gained audio focus.", Toast.LENGTH_SHORT).show();
        mAudioFocus = AudioFocus.Focused;

        // restart media player with new focus settings
        if (mState == State.Playing)
            configAndStartMediaPlayer();
    }

    @Override
    public void onLostAudioFocus(boolean canDuck) {
        // Toast.makeText(getApplicationContext(), "lost audio focus." + (canDuck ? "can duck" :
        //    "no duck"), Toast.LENGTH_SHORT).show();
        mAudioFocus = canDuck ? AudioFocus.NoFocusCanDuck : AudioFocus.NoFocusNoDuck;

        // start/restart/pause media player with new focus settings
        if (mPlayer != null && mPlayer.isPlaying())
            configAndStartMediaPlayer();
    }

    @Override
    public void onMusicProviderComplete() {
        // Done retrieving!
        mState = State.Stopped;

        // If the flag indicates we should start playing after retrieving, let's do that now.
        if (mStartPlayingAfterRetrieve) {
            tryToGetAudioFocus();
            playSong(true,false);
        }
    }
    

	@Override
	public void onBufferingUpdate(MediaPlayer mp, int percent) {
		notifyBufferingUpdate(percent);
	}
    
	private void setListenerState(){
		if(mPlayer != null){
			notifyDuration(mPlayer.getDuration());
			notifyCurrentPosition(mPlayer.getCurrentPosition());

			notifyCurrentState(mState);
			notifyIsLooping(mPlayer.isLooping());
			notifyCurrentTrack(mCurrentTrack);
		}
	}
    
    private void notifyDuration(int duration){
    	for(MusicPlayerServiceListener listener : listeners){
    		listener.setDuration(duration);
    	}
    }
    private void notifyCurrentPosition(int position){
    	for(MusicPlayerServiceListener listener : listeners){
    		listener.setCurrentPosition(position);
    	}
    }
    private void notifyCurrentState(State state){
    	for(MusicPlayerServiceListener listener : listeners){
    		listener.setCurrentState(state);
    	}
    }
    
    private void notifyIsLooping(boolean looping){
    	for(MusicPlayerServiceListener listener : listeners){
    		listener.setIsLooping(looping);
    	}
	}


    private void notifyCurrentTrack(Track currentPlayable){
    	for(MusicPlayerServiceListener listener : listeners){
    		listener.setCurrentTrack(currentPlayable);
    	}
	}
	
    private void notifyBufferingUpdate(int percent){
    	for(MusicPlayerServiceListener listener : listeners){
    		listener.bufferingUpdate(percent);
    	}
    }
	
	private void notifySetError(String error){
    	for(MusicPlayerServiceListener listener : listeners){
    		listener.setError(error);
    	}
	}


    @Override
    public void onDestroy() {
        // Service is being killed, so make sure we release our resources
        mState = State.Stopped;
        closeHandler();
        notifyCurrentState(mState);
        relaxResources(true);
        giveUpAudioFocus();
    }

    private void closeHandler() {
    	if(onEverySecond != null){
    		mCurrentPositionHandler.removeCallbacks(onEverySecond);
    	}
	}

	@Override
    public IBinder onBind(Intent arg0) {
        return mBinder;
    }
    
    public class LocalBinder extends Binder {
        public MusicPlayerService getService() {
            return MusicPlayerService.this;
        }
    }
    

	 private Runnable onEverySecond=new Runnable() {
		 public void run() {
			 if (lastActionTime>0 &&
			 SystemClock.elapsedRealtime()-lastActionTime>3000) {
	
			 }
	
			 if (mPlayer!=null && mPlayer.isPlaying()) {
			 //timeline.setProgress(mLocalService.getCurrentPosition());
			 
				notifyCurrentPosition(mPlayer.getCurrentPosition());
	
				//notifyCurrentState(state).setIsPlaying(mPlayer.isPlaying());
			 
			 }
	
		 //if (!isPaused) {
			 mCurrentPositionHandler.postDelayed(onEverySecond, 1000);
		 //}
		 }
	 };



}