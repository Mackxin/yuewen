package com.example.yuewen.data.db

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.yuewen.data.model.Article
import com.example.yuewen.data.model.Note

@Database(entities = [Article::class, Note::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun noteDao(): NoteDao

    companion object {
        /** v1 → v2：新增 folder（收藏夹）与 readAt（阅读历史时间戳）两个字段，保留旧数据。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN folder TEXT NOT NULL DEFAULT '默认'")
                db.execSQL("ALTER TABLE articles ADD COLUMN readAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v2 → v3：新增 fullText / fullFetched（本地抽取的正文全文缓存），保留旧数据。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN fullText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE articles ADD COLUMN fullFetched INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3 → v4：新增 readProgress（阅读进度千分比），保留旧数据与收藏。 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN readProgress INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v4 → v5：新增 notes 表（摘录 / 笔记）。
         *
         * 建表语句必须与 [Note] 的字段顺序、类型、默认值逐字对上，
         * 否则 Room 运行期的 schema 校验会抛
         * `Migration didn't properly handle …` 直接闪退。
         * 逐项对照：id TEXT NOT NULL（主键）、link TEXT NOT NULL、
         * articleTitle/sourceName/quote/note TEXT NOT NULL DEFAULT ''、createdAt INTEGER NOT NULL DEFAULT 0。
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notes` (" +
                            "`id` TEXT NOT NULL, " +
                            "`link` TEXT NOT NULL, " +
                            "`articleTitle` TEXT NOT NULL DEFAULT '', " +
                            "`sourceName` TEXT NOT NULL DEFAULT '', " +
                            "`quote` TEXT NOT NULL DEFAULT '', " +
                            "`note` TEXT NOT NULL DEFAULT '', " +
                            "`createdAt` INTEGER NOT NULL DEFAULT 0, " +
                            "PRIMARY KEY(`id`))"
                )
            }
        }
    }
}
