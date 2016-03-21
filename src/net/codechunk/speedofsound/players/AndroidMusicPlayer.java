package net.codechunk.speedofsound.players;

import android.content.Context;
import android.content.Intent;
import net.codechunk.speedofsound.util.SongInfo;
import sparta.checkers.quals.Extra;
import sparta.checkers.quals.IntentMap;

public class AndroidMusicPlayer extends BasePlayer
{
	@Override
	public PlaybackAction getPlaybackAction(Context context, Intent intent)
	{
		String action = intent.getAction();

		if (action.equals("com.android.music.playbackcomplete"))
			return PlaybackAction.STOPPED;

		if (action.equals("com.android.music.metachanged"))
			return PlaybackAction.CHANGED;

		return null;
	}

	@Override
	public SongInfo getSongInfo(Context context, @IntentMap({@Extra(key="track"), @Extra(key="artist"), @Extra(key="album")}) Intent intent)
	{
		SongInfo info = new SongInfo();
		info.track = intent.getStringExtra("track");
		info.artist = intent.getStringExtra("artist");
		info.album = intent.getStringExtra("album");
		return info;
	}
}
