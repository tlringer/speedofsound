package net.codechunk.speedofsound;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import com.acg.lib.model.Location;
import net.codechunk.speedofsound.players.BasePlayer;
import net.codechunk.speedofsound.service.SoundService;
import net.codechunk.speedofsound.util.SongInfo;
import sparta.checkers.quals.Extra;
import sparta.checkers.quals.IntentMap;
import sparta.checkers.quals.Sink;
import sparta.checkers.quals.Source;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SongTracker {
	private static final String TAG = "SongTracker";
	private static SongTracker inst = null;

    @Sink()
	private Context context;

	private SQLiteOpener sqlite;
    @Source("DATABASE")
	private SQLiteDatabase db;

	/**
	 * Maximum update rate in ms.
	 */
	private static final int UPDATE_RATE = 1500;

	/**
	 * Minimum update distance in meters.
	 */
	private static final int MIN_DISTANCE = 10;

    @Source("ACG(location)")
	private Location previousLocation;
	private long routeId;

	private SongInfo currentSong = null;

	public static SongTracker getInstance(Context context) {
		if (inst == null) {
			inst = new SongTracker(context);
		}

		return inst;
	}

	private SongTracker(Context context) {
		this.context = context;
		this.sqlite = new SQLiteOpener(context);
		this.db = this.sqlite.getWritableDatabase();

		// subscribe to song and location broadcasts
		LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(context);
		IntentFilter filter = new IntentFilter();
		filter.addAction(SoundService.LOCATION_UPDATE_BROADCAST);
		filter.addAction(BasePlayer.PLAYBACK_CHANGED_BROADCAST);
		filter.addAction(BasePlayer.PLAYBACK_STOPPED_BROADCAST);
		lbm.registerReceiver(this.messageReceiver, filter);
	}

	/**
	 * External classes can get a read-only view of the data set.
	 *
	 * @return a read-only SQLite database
	 */
    @Source("DATABASE")
	public SQLiteDatabase getReadableDatabase() {
		return this.sqlite.getReadableDatabase();
	}

	/**
	 * Start a new route.
	 *
	 * TODO: it would be nice if we could detect the age of the previous route
	 * and use that instead, if it wasn't too long ago, say, 30s.
	 *
	 * @return the route ID
	 */
	public long startRoute() {
		// XXX: we're not supporting multiple routes in 0.8 initially.
		// but when we do, get rid of this!
		this.db.delete("points", null, null);
		this.db.delete("songs", null, null);
		this.db.delete("routes", null, null);

		// grab the current time
		Date date = new Date();
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

		// set a nice name according to the user's locale settings
		DateFormat localeDF = android.text.format.DateFormat.getMediumDateFormat(this.context);
		DateFormat localeTF = android.text.format.DateFormat.getTimeFormat(this.context);
		String routeName = localeDF.format(date) + " " + localeTF.format(date);

		// store the route
		ContentValues values = new ContentValues();
		values.put("name", routeName);
		values.put("start", df.format(date));
		this.routeId = this.db.insert("routes", null, values);

		// clear out the current song ID (it's linked to a route)
		if (this.currentSong != null) {
			this.currentSong.id = 0;
		}

		Log.d(TAG, "Starting new route with id " + this.routeId);
		return this.routeId;
	}

	/**
	 * Stop recording to the current route.
	 *
	 * TODO: store the end time
	 */
	public void endRoute() {
		this.routeId = 0;
	}

	/**
	 * Return a cursor for all points associated with a route.
	 *
	 * @param routeId ID of the route containing points
	 * @return a cursor to iterate
	 */
    @Source("DATABASE")
	public Cursor getRoutePoints(@Source("DATABASE") long routeId) {
		return this.db.query("points",
				new String[]{"id", "song_id", "latitude", "longitude"},
				"route_id = ?", new String[]{Long.toString(routeId)},
				null, null, "id ASC");
	}

	/**
	 * Get the info/meta associated with a given song ID.
	 *
	 * @param songId ID of the song
	 * @return song meta
	 */
    @Source("DATABASE")
	public SongInfo getSongInfo(@Source("DATABASE") long songId) {
		Cursor cursor = this.db.query("songs",
				new String[]{"track", "artist", "album"},
				"id = ?", new String[]{Long.toString(songId)},
				null, null, null);
		cursor.moveToFirst();

		if (cursor.isAfterLast()) {
			Log.w(TAG, "Song with ID " + songId + " doesn't exist");
			cursor.close();
			return null;
		}

		SongInfo info = new SongInfo();
		info.id = songId;
		info.track = cursor.getString(0);
		info.artist = cursor.getString(1);
		info.album = cursor.getString(2);
		cursor.close();

		return info;
	}

	/**
	 * Find a song's id, creating an entry if it doesn't have one.
	 *
	 * @param routeId Route ID of the song.
	 * @param track   Track name
	 * @param artist  Song artist
	 * @param album   Song album
	 * @return the ID of the song
	 */
	private long findSong(long routeId, @Source("DATABASE") String track, @Source("DATABASE") String artist, @Source("DATABASE") String album) {
		Cursor cursor = this.db.query("songs", new String[]{"id"},
				"track = ? AND artist = ? AND album = ?",
				new String[]{track, artist, album},
				null, null, null);
		cursor.moveToFirst();

		// create the song if it wasn't found
		if (cursor.isAfterLast()) {
			cursor.close();
			ContentValues values = new ContentValues();
			values.put("route_id", routeId);
			values.put("track", track);
			values.put("artist", artist);
			values.put("album", album);
			return this.db.insert("songs", null, values);
		} else {
			long id = cursor.getLong(0);
			cursor.close();
			return id;
		}
	}

	/**
	 * Local broadcast receiver for location and song updates.
	 */
	private BroadcastReceiver messageReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, @IntentMap({@Extra(key = "location"),
        @Extra(key="track"), @Extra(key="artist"), @Extra(key="album")})  Intent intent) {
			String action = intent.getAction();

			// new location reported
			if (action.equals(SoundService.LOCATION_UPDATE_BROADCAST)) {
                @Source("ACG(location)")
				Location location = intent.getParcelableExtra("location");
				SongTracker.this.locationUpdate(location);
			}

			// new song reported
			else if (action.equals(BasePlayer.PLAYBACK_CHANGED_BROADCAST)) {
				// set it as the current song
				SongInfo si = new SongInfo();
				si.track = intent.getStringExtra("track");
				si.artist = intent.getStringExtra("artist");
				si.album = intent.getStringExtra("album");
				si.id = 0;
				SongTracker.this.currentSong = si;

				Log.v(TAG, "New song set: " + SongTracker.this.currentSong.track +
						" by " + SongTracker.this.currentSong.artist);
			}

			// they've stopped playing music :(
			else if (action.equals(BasePlayer.PLAYBACK_STOPPED_BROADCAST)) {
				SongTracker.this.currentSong = null;

				Log.v(TAG, "Playback stopped");
			}
		}
	};

	/**
	 * Process a location update broadcast.
	 *
	 * @param location New location.
	 */
	private void locationUpdate(@Source("ACG(location)") Location location) {
		// ignore if we have no route yet
		if (this.routeId == 0)
			return;

		// we must have song information
		if (this.currentSong == null)
			return;

		// do we need to update song meta?
		if (this.currentSong.id == 0) {
			Log.v(TAG, "Updating song meta");
			this.currentSong.id = this.findSong(this.routeId,
					this.currentSong.track, this.currentSong.artist, this.currentSong.album);
		}

		// rate limiting
		if (this.previousLocation != null) {
			// time-based
			if (location.getTime() - this.previousLocation.getTime() < SongTracker.UPDATE_RATE)
				return;

			// distance-based
			if (previousLocation.distanceTo(location) < SongTracker.MIN_DISTANCE)
				return;
		}

		Log.v(TAG, "Storing point");

		// get the data we need to store
		int latitudeE6 = (int) (location.getLatitude() * 1000000);
		int longitudeE6 = (int) (location.getLongitude() * 1000000);

		// store the point
		ContentValues values = new ContentValues();
		values.put("route_id", this.routeId);
		values.put("song_id", this.currentSong.id);
		values.put("latitude", latitudeE6);
		values.put("longitude", longitudeE6);
		this.db.insert("points", null, values);

		// store the location
		this.previousLocation = location;
	}

	/**
	 * Android hook to open the SQLite database.
	 */
	private class SQLiteOpener extends SQLiteOpenHelper {
		public static final String DB_NAME = "songtracker";
		public static final int DB_VERSION = 2;

		public SQLiteOpener(Context context) {
			super(context, DB_NAME, null, DB_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE routes (" +
					"id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
					"name TEXT," +
					"start DATETIME," +
					"end DATETIME);");
			db.execSQL("CREATE TABLE songs (" +
					"id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
					"route_id INTEGER," +
					"track TEXT," +
					"artist TEXT," +
					"album TEXT);");
			db.execSQL("CREATE TABLE points (" +
					"id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
					"route_id INTEGER," +
					"song_id INTEGER," +
					"latitude INTEGER," +
					"longitude INTEGER);");
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		}
	}
}
