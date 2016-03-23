package net.codechunk.speedofsound.players;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import net.codechunk.speedofsound.util.SongInfo;
import sparta.checkers.quals.Extra;
import sparta.checkers.quals.IntentMap;
import sparta.checkers.quals.Sink;
import sparta.checkers.quals.Source;

public abstract class BasePlayer extends BroadcastReceiver
{
	private static final String TAG = "BasePlayer";

	public static final String PLAYBACK_CHANGED_BROADCAST = "playback-changed";

	public static final String PLAYBACK_STOPPED_BROADCAST = "playback-stopped";

	protected enum PlaybackAction
	{
		CHANGED, STOPPED, PAUSED, RESUMED
	}

	public abstract PlaybackAction getPlaybackAction(Context context, Intent intent);

	public abstract SongInfo getSongInfo(Context context, @IntentMap({@Extra(key="track"), @Extra(key="artist"), @Extra(key="album")}) Intent intent);

	@Override
	public void onReceive(Context context, @IntentMap({@Extra(key="track"), @Extra(key="artist"), @Extra(key="album")}) Intent intent)
	{
		Log.v(TAG, "Playback broadcast received: " + intent.getAction());

		// get the action from the player
		PlaybackAction action = getPlaybackAction(context, intent);

		LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(context);
		if (action == PlaybackAction.CHANGED)
		{
			Log.d(TAG, "Sending song change broadcast");

			// get the track info from the player
			SongInfo info = this.getSongInfo(context, intent);

			// send out a broadcast with the details
			@IntentMap({@Extra(key="track"), @Extra(key="artist"), @Extra(key="album")}) @Source() @Sink("ANY")
			Intent broadcast = new Intent(BasePlayer.PLAYBACK_CHANGED_BROADCAST);
			broadcast.putExtra("track", info.track == null ? "Unknown" : info.track);
			broadcast.putExtra("artist", info.artist == null ? "Unknown" : info.artist);
			broadcast.putExtra("album", info.album == null ? "Unknown" : info.album);
			lbm.sendBroadcast(broadcast);
		}
		else if (action == PlaybackAction.STOPPED)
		{
			Log.d(TAG, "Sending playback stopped broadcast");

            @Source() @Sink("ANY")
			Intent broadcast = new Intent(BasePlayer.PLAYBACK_STOPPED_BROADCAST);
			lbm.sendBroadcast(broadcast);
		}
	}

}
