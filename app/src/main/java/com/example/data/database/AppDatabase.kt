package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [MediaStream::class, Playlist::class, PlaylistItem::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaStreamDao(): MediaStreamDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "stream_player_database"
                )
                .fallbackToDestructiveMigration()
                .addCallback(AppDatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class AppDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDatabase(database.mediaStreamDao())
                }
            }
        }

        suspend fun populateDatabase(dao: MediaStreamDao) {
            // Add default high-quality streaming media resources
            dao.insertStream(
                MediaStream(
                    title = "Sintel Multi-Sub & Audio (DASH Stream)",
                    url = "https://storage.googleapis.com/shaka-demo-assets/sintel/dash.mpd",
                    format = "DASH",
                    isCustom = false
                )
            )
            dao.insertStream(
                MediaStream(
                    title = "Tears of Steel HLS Feed (Live Simulation)",
                    url = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
                    format = "HLS",
                    isCustom = false
                )
            )
            dao.insertStream(
                MediaStream(
                    title = "Angel One Sci-Fi Multi-Audio (DASH Stream)",
                    url = "https://storage.googleapis.com/shaka-demo-assets/angel-one/dash.mpd",
                    format = "DASH",
                    isCustom = false
                )
            )
            dao.insertStream(
                MediaStream(
                    title = "Big Buck Bunny WebVTT (External Subtitle Demo)",
                    url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    subtitleUrl = "https://storage.googleapis.com/shaka-demo-assets/sintel/subtitles_en.vtt",
                    format = "MP4",
                    isCustom = false
                )
            )
            dao.insertStream(
                MediaStream(
                    title = "Sintel Movie Playlist (HLS Track Selection)",
                    url = "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8",
                    format = "HLS",
                    isCustom = false
                )
            )
        }
    }
}
