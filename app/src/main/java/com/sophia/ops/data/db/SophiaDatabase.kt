package com.sophia.ops.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sophia.ops.data.dao.BluetoothDao
import com.sophia.ops.data.dao.WifiDao
import com.sophia.ops.data.dao.ScanSessionDao
import com.sophia.ops.data.entities.BluetoothDeviceEntity
import com.sophia.ops.data.entities.WifiNetwork
import com.sophia.ops.data.entities.ScanSession

@Database(
    entities = [
        WifiNetwork::class,
        BluetoothDeviceEntity::class,
        ScanSession::class
    ],
    version = 13,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class SophiaDatabase : RoomDatabase() {

    abstract fun wifiDao(): WifiDao
    abstract fun bluetoothDao(): BluetoothDao
    abstract fun scanSessionDao(): ScanSessionDao

    companion object {
        @Volatile
        private var INSTANCE: SophiaDatabase? = null

        /**
         * Migration v12 → v13: Adds a unique index on wifi_networks.bssid to
         * prevent duplicate Wi-Fi entries. Existing duplicates are deduplicated
         * by keeping the most recent row per BSSID before creating the index.
         */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Deduplicate: keep only the most recent row per bssid
                db.execSQL(
                    """
                    DELETE FROM wifi_networks
                    WHERE id NOT IN (
                        SELECT id FROM wifi_networks w1
                        WHERE w1.id = (
                            SELECT MAX(w2.id) FROM wifi_networks w2
                            WHERE w2.bssid = w1.bssid
                        )
                    )
                    """.trimIndent()
                )
                // Create the unique index
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_wifi_networks_bssid ON wifi_networks(bssid)"
                )
            }
        }

        fun getInstance(context: Context): SophiaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SophiaDatabase::class.java,
                    "sophia-db"
                )
                .addMigrations(MIGRATION_12_13)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
