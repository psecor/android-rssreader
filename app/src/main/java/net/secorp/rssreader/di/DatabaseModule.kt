package net.secorp.rssreader.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.secorp.rssreader.data.db.MIGRATION_1_2
import net.secorp.rssreader.data.db.RssDatabase
import net.secorp.rssreader.data.db.dao.CategoryDao
import net.secorp.rssreader.data.db.dao.FeedDao
import net.secorp.rssreader.data.db.dao.FeedItemDao
import net.secorp.rssreader.data.db.dao.PendingActionDao

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RssDatabase =
        Room.databaseBuilder(context, RssDatabase::class.java, "rssreader.db")
            .addMigrations(MIGRATION_1_2)
            // Schema is still in flux during P2/P3; any unanticipated
            // upgrade path drops to a clean DB and a fresh sync rather
            // than crashing on launch. Tighten before v1 ship.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCategoryDao(db: RssDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideFeedDao(db: RssDatabase): FeedDao = db.feedDao()

    @Provides
    fun provideFeedItemDao(db: RssDatabase): FeedItemDao = db.feedItemDao()

    @Provides
    fun providePendingActionDao(db: RssDatabase): PendingActionDao = db.pendingActionDao()
}
