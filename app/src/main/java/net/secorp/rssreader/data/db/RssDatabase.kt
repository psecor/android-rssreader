package net.secorp.rssreader.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.secorp.rssreader.data.db.dao.CategoryDao
import net.secorp.rssreader.data.db.dao.FeedDao
import net.secorp.rssreader.data.db.dao.FeedItemDao
import net.secorp.rssreader.data.db.dao.PendingActionDao
import net.secorp.rssreader.data.db.entity.CategoryEntity
import net.secorp.rssreader.data.db.entity.FeedEntity
import net.secorp.rssreader.data.db.entity.FeedItemEntity
import net.secorp.rssreader.data.db.entity.PendingActionEntity

@Database(
    entities = [
        CategoryEntity::class,
        FeedEntity::class,
        FeedItemEntity::class,
        PendingActionEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RssDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun feedDao(): FeedDao
    abstract fun feedItemDao(): FeedItemDao
    abstract fun pendingActionDao(): PendingActionDao
}
