package net.secorp.rssreader.data.db

import androidx.room.TypeConverter
import java.time.Instant

class Converters {
    @TypeConverter
    fun instantFromMillis(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()
}
