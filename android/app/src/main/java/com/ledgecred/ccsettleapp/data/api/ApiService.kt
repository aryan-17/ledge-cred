package com.ledgecred.ccsettleapp.data.api

import com.ledgecred.ccsettleapp.data.api.dto.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @POST("users/register")
    suspend fun register(@Body body: RegisterRequest): Response<Unit>

    @POST("sync")
    suspend fun sync(@Body body: SyncRequest): SyncResponse

    @PUT("fcm/token")
    suspend fun updateFcmToken(@Body body: Map<String, String>): Response<Unit>

    @POST("fcm/notify")
    suspend fun sendNotification(@Body body: FcmNotifyRequest): Response<Unit>

    @DELETE("transactions/{id}")
    suspend fun deleteTransaction(@Path("id") id: String): Response<Unit>

    @GET("cards")
    suspend fun getCards(): CardsResponse

    @POST("cards")
    suspend fun addCard(@Body body: AddCardRequest): Response<Unit>

    @DELETE("cards/{id}")
    suspend fun deleteCard(@Path("id") id: String): Response<Unit>
}
