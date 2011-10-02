package com.wonderfulrobot.commons.storage;

import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import android.os.Handler;

/**
 * 
 * Generic Object Storage Class for providing a Cache for in-memory object storage.
 *  
 * 
 * @author Mr Robot
 *
 */
public class Storage {
	
    private static int HARD_CACHE_CAPACITY = 60; //Number of Objects
    private static int DELAY_BEFORE_PURGE = 3 * 60 * 1000; //Purge Cycle
    
    
    private static Storage INSTANCE;
    
    public Storage(){
    	
    }
    
    public Storage( int hardCapacity, int purgeDelay ){
    	HARD_CACHE_CAPACITY = hardCapacity;
    	DELAY_BEFORE_PURGE = purgeDelay;
    }
    
    /**
     * Get or create an instance of Storage
     */
    public static Storage getInstance(  ) {
        if( INSTANCE == null )
            INSTANCE = new Storage( );
        return INSTANCE;
    }
    
    
    /**
     * Get or create an instance of Storage
     */
    public static Storage getInstance( int hardCapacity, int purgeDelay  ) {
        if( INSTANCE == null )
            INSTANCE = new Storage( hardCapacity, purgeDelay );
        return INSTANCE;
    }


    // Hard cache, with a fixed maximum capacity and a life duration
    private final Map<String, Object> sHardObjectCache = new LinkedHashMap<String, Object>(HARD_CACHE_CAPACITY / 2, 0.75f, true) {
        /**
		 * 
		 */
		private static final long serialVersionUID = 1059418568363227220L;

		@Override
        protected boolean removeEldestEntry(LinkedHashMap.Entry<String, Object> eldest) {
            if (size() > HARD_CACHE_CAPACITY) {
                // Entries push-out of hard reference cache are transferred to soft reference cache
                sSoftObjectCache.put(eldest.getKey(), new SoftReference<Object>(eldest.getValue()));
                return true;
            } else
                return false;
        }
    };

    // Soft cache for objects kicked out of hard cache
    private final static ConcurrentHashMap<String, SoftReference<Object>> sSoftObjectCache =
        new ConcurrentHashMap<String, SoftReference<Object>>(HARD_CACHE_CAPACITY / 2);

    private final Handler purgeHandler = new Handler();

    private final Runnable purger = new Runnable() {
        public void run() {
            clearCache();
        }
    };

    /**
     * Adds this object to the cache.
     * @param element The new object.
     */
    private void addElementToCache(String url, Object element) {
        if (element != null) {
            synchronized (sHardObjectCache) {
                sHardObjectCache.put(url, element);
            }
        }
    }
    
    public Object getObject(String id){
    	return getElement(id);
    }
    

    
    public void putElement(String id, Object element){
    	addElementToCache(id, element);
    }

    /**
     * @param id The id of the object that will be retrieved from the cache.
     * @return The cached object or null if it was not found.
     */
	protected Object getElement(final String id) {
        // First try the hard reference cache
        synchronized (sHardObjectCache) {
            final Object obj = sHardObjectCache.get(id);
            if (obj != null) {
            	resetPurgeTimer();
                // Object found in hard cache
                // Move element to first position, so that it is removed last
                sHardObjectCache.remove(id);
                sHardObjectCache.put(id, obj);
                return obj;
            }
        }

        // Then try the soft reference cache
        SoftReference<Object> objReference = sSoftObjectCache.get(id);
        if (objReference != null) {
            final Object obj = objReference.get();
            if (obj != null) {
                //Object found in soft cache
                return obj;
            } else {
                // Soft reference has been Garbage Collected
                sSoftObjectCache.remove(obj);
            }
        }

        return null;
    }
    
    public synchronized Set<String> getKeyIterator(){
    	return Collections.synchronizedSet(sHardObjectCache.keySet());
    }
    
    public synchronized Iterator<Object> getValuesIterator(){
    	return sHardObjectCache.values().iterator();
    }
 
    /**
     * Clears the image cache used internally to improve performance. Note that for memory
     * efficiency reasons, the cache will automatically be cleared after a certain inactivity delay.
     */
    public void clearCache() {
        sHardObjectCache.clear();
        sSoftObjectCache.clear();
    }

    /**
     * Allow a new delay before the automatic cache clear is done.
     */
    private void resetPurgeTimer() {
        purgeHandler.removeCallbacks(purger);
        purgeHandler.postDelayed(purger, DELAY_BEFORE_PURGE);
    }


}
