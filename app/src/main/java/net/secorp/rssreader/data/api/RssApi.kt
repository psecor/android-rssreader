package net.secorp.rssreader.data.api

import kotlinx.serialization.Serializable
import net.secorp.rssreader.data.api.dto.CategoryDto
import net.secorp.rssreader.data.api.dto.FeedDto
import net.secorp.rssreader.data.api.dto.FeedItemDto
import net.secorp.rssreader.data.api.dto.ReadStatusDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class RefreshAllResponse(
    val message: String,
    val total: Int,
    val newItemsCount: Int,
    val errors: Int,
)

@Serializable
data class MarkReadRequest(
    val feedItemIds: List<Long>,
    val opened: Boolean = false,
)

@Serializable
data class MarkUnreadRequest(val feedItemId: Long)

interface RssApi {
    @GET("api/categories")
    suspend fun listCategories(): List<CategoryDto>

    @GET("api/feeds")
    suspend fun listFeeds(): List<FeedDto>

    @GET("api/feed-items")
    suspend fun listItems(
        @Query("limit") limit: Int = 200,
        @Query("offset") offset: Int = 0,
        @Query("feedId") feedId: Long? = null,
        @Query("categoryId") categoryId: Long? = null,
        @Query("isRead") isRead: Boolean? = null,
        @Query("since") since: String? = null,
    ): List<FeedItemDto>

    @GET("api/read-status")
    suspend fun listReadStatuses(
        @Query("since") since: String? = null,
        @Query("limit") limit: Int = 500,
        @Query("offset") offset: Int = 0,
    ): List<ReadStatusDto>

    @GET("api/feed-items/{id}")
    suspend fun getItem(@Path("id") id: Long): FeedItemDto

    @POST("api/feeds/refresh-all")
    suspend fun refreshAllFeeds(): RefreshAllResponse

    // Write endpoints — wired in P3 along with the local write queue.
    @POST("api/read-status/mark-read")
    suspend fun markRead(@Body body: MarkReadRequest)

    @POST("api/read-status/mark-unread")
    suspend fun markUnread(@Body body: MarkUnreadRequest)
}
