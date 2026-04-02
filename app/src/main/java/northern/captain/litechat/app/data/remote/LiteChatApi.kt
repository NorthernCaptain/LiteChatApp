package northern.captain.litechat.app.data.remote

import northern.captain.litechat.app.data.remote.dto.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.*

interface LiteChatApi {

    @GET("litechat/api/v1/users/me")
    suspend fun getMe(): MeResponseDto

    @GET("litechat/api/v1/users")
    suspend fun getUsers(): UsersResponseDto

    @GET("litechat/api/v1/users/{userId}/avatar")
    suspend fun getUserAvatar(@Path("userId") userId: Long): ResponseBody

    @Multipart
    @POST("litechat/api/v1/users/me/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): AvatarResponseDto

    @POST("litechat/api/v1/users/me/avatar")
    suspend fun removeAvatar(): AvatarResponseDto

    @POST("litechat/api/v1/conversations")
    suspend fun createConversation(@Body body: CreateConversationRequestDto): ConversationDto

    @GET("litechat/api/v1/conversations")
    suspend fun getConversations(): ConversationsResponseDto

    @GET("litechat/api/v1/conversations/{id}")
    suspend fun getConversation(@Path("id") id: String): ConversationDto

    @GET("litechat/api/v1/conversations/{id}/messages")
    suspend fun getMessages(
        @Path("id") conversationId: String,
        @Query("after") after: String = "0",
        @Query("limit") limit: Int = 50
    ): MessagesResponseDto

    @POST("litechat/api/v1/conversations/{id}/messages")
    suspend fun sendMessage(
        @Path("id") conversationId: String,
        @Body body: SendMessageRequestDto
    ): MessageDto

    @Multipart
    @POST("litechat/api/v1/attachments/upload")
    suspend fun uploadAttachment(@Part file: MultipartBody.Part): AttachmentDto

    @GET("litechat/api/v1/attachments/{id}")
    suspend fun downloadAttachment(@Path("id") id: String): ResponseBody

    @GET("litechat/api/v1/attachments/{id}/thumbnail")
    suspend fun downloadThumbnail(@Path("id") id: String): ResponseBody

    @POST("litechat/api/v1/messages/{id}/reactions")
    suspend fun addReaction(
        @Path("id") messageId: String,
        @Body body: ReactionRequestDto
    ): ReactionResponseDto

    @HTTP(method = "DELETE", path = "litechat/api/v1/messages/{id}/reactions", hasBody = true)
    suspend fun removeReaction(
        @Path("id") messageId: String,
        @Body body: ReactionRequestDto
    ): Any

    @POST("litechat/api/v1/conversations/{id}/ack")
    suspend fun acknowledgeDelivery(@Path("id") conversationId: String, @Body body: AckRequestDto)

    @POST("litechat/api/v1/conversations/{id}/read")
    suspend fun acknowledgeRead(@Path("id") conversationId: String, @Body body: AckRequestDto)

    @POST("litechat/api/v1/conversations/{id}/typing")
    suspend fun sendTypingEvent(
        @Path("id") conversationId: String,
        @Body body: TypingRequestDto
    )

    @POST("litechat/api/v1/poll")
    suspend fun poll(@Body body: PollRequestDto): PollResponseDto

    @POST("litechat/api/v1/users/me/fcmtoken")
    suspend fun registerFcmToken(@Body body: FcmTokenRequestDto)

    @HTTP(method = "DELETE", path = "litechat/api/v1/users/me/fcmtoken", hasBody = true)
    suspend fun unregisterFcmToken(@Body body: FcmTokenRequestDto)
}
