package com.spacecay.expenselog.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Book::class, CurrencyRate::class, Wallet::class, Category::class, Txn::class,
        Budget::class, Goal::class, GoalEntry::class, Debt::class, DebtEntry::class,
        Recurring::class, Template::class, PayeeRule::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        /** Never wipe data: every schema change gets an explicit migration. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN splitId INTEGER")
                db.execSQL("ALTER TABLE recurring ADD COLUMN confirm INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile private var instance: AppDatabase? = null

        /** Copy the database file before any upgrade touches it (kept as money.db.v<N>.bak). */
        private fun safetyCopy(context: Context) {
            try {
                val db = context.getDatabasePath("money.db")
                if (!db.exists()) return
                val bak = java.io.File(db.parentFile, "money.db.v2.bak")
                if (bak.exists()) return
                db.copyTo(bak)
                listOf("-wal", "-shm").forEach { s ->
                    val f = java.io.File(db.path + s)
                    if (f.exists()) f.copyTo(java.io.File(bak.path + s), overwrite = true)
                }
            } catch (_: Exception) {
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                if (instance == null) safetyCopy(context.applicationContext)
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "money.db")
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
