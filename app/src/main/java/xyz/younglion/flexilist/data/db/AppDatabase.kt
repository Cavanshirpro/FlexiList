package xyz.younglion.flexilist.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import xyz.younglion.flexilist.data.model.ListItemEntity
import xyz.younglion.flexilist.data.model.NotebookEntity
import xyz.younglion.flexilist.data.model.WorkoutLogEntity
import xyz.younglion.flexilist.data.model.WaterIntakeEntity

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [NotebookEntity::class, ListItemEntity::class, WorkoutLogEntity::class, WaterIntakeEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notebookDao(): NotebookDao
    abstract fun listItemDao(): ListItemDao
    abstract fun workoutLogDao(): WorkoutLogDao
    abstract fun waterIntakeDao(): WaterIntakeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notebooks ADD COLUMN priorityConfigJson TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_list_items_notebookId_sortOrder ON list_items(notebookId, sortOrder)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_list_items_notebookId_completed ON list_items(notebookId, completed)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_list_items_dueDate ON list_items(dueDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notebooks_pinned_sortOrder ON notebooks(pinned, sortOrder)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notebooks_type ON notebooks(type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_logs_notebookId_createdAt ON workout_logs(notebookId, createdAt)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "flexilist_database"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
