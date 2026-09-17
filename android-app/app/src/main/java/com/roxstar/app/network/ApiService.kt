package com.roxstar.app.network

import com.roxstar.app.data.models.Room
import retrofit2.Response
import retrofit2.http.*

data class CreateRoomRequest(
    val name: String,
    val ownerId: String,
    val maxParticipants: Int = 20
)

data class JoinRoomRequest(
    val userId: String,
    val username: String
)

data class LeaveRoomRequest(
    val userId: String
)

data class ShareDraftRequest(
    val userId: String,
    val draftId: String,
    val title: String,
    val durationMs: Long,
    val effectApplied: String
)

data class StartSpinRequest(
    val userId: String
)

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String?
)

interface ApiService {
    @POST("api/rooms")
    suspend fun createRoom(@Body request: CreateRoomRequest): Response<ApiResponse<Room>>

    @POST("api/rooms/{roomId}/join")
    suspend fun joinRoom(
        @Path("roomId") roomId: String,
        @Body request: JoinRoomRequest
    ): Response<ApiResponse<Room>>

    @POST("api/rooms/{roomId}/leave")
    suspend fun leaveRoom(
        @Path("roomId") roomId: String,
        @Body request: LeaveRoomRequest
    ): Response<ApiResponse<Room>>

    @GET("api/rooms/{roomId}")
    suspend fun getRoomState(@Path("roomId") roomId: String): Response<ApiResponse<Room>>

    @POST("api/rooms/{roomId}/drafts")
    suspend fun shareDraft(
        @Path("roomId") roomId: String,
        @Body request: ShareDraftRequest
    ): Response<ApiResponse<Room>>

    @POST("api/rooms/{roomId}/spin/start")
    suspend fun startSpin(
        @Path("roomId") roomId: String,
        @Body request: StartSpinRequest
    ): Response<ApiResponse<Map<String, Any>>>

    @GET("api/health")
    suspend fun checkHealth(): Response<Map<String, Any>>
}
