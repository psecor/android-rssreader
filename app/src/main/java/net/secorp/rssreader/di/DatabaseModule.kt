package net.secorp.rssreader.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.secorp.rssreader.data.db.RssDatabase
import net.secorp.rssreader.data.db.dao.CategoryDao
import net.secorp.rssreader.data.db.dao.FeedDao
import net.secorp.rssreader.data.db.dao.FeedItemDao

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RssDatabase =
        Room.databaseBuilder(context, RssDatabase::class.java, "rssreader.db")
            .build()

    @Provides
    fun provideCategoryDao(db: RssDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideFeedDao(db: RssDatabase): FeedDao = db.feedDao()

    @Provides
    fun provideFeedItemDao(db: RssDatabase): FeedItemDao = db.feedItemDao()
}
