package com.pusher.chatkit

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import com.pusher.platform.BaseClient
import com.pusher.platform.Instance
import com.pusher.platform.logger.AndroidLogger
import com.pusher.platform.logger.LogLevel
import com.pusher.platform.tokenProvider.TokenProvider

class ChatManager(
        val instanceLocator: String,
        val userId: String,
        val context: Context,
        val tokenProvider: TokenProvider? = null,
        val tokenParams: ChatkitTokenParams? = null,
        logLevel: LogLevel = LogLevel.DEBUG
){

    companion object {
        val GSON = GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create()
    }

    class Builder {

        private var instanceLocator: String? = null
        private var userId: String? = null
        private var context: Context? = null
        private var tokenProvider: TokenProvider? = null
        private var tokenParams: ChatkitTokenParams? = null
        private var logLevel = LogLevel.DEBUG

        fun instanceLocator(instanceLocator: String): Builder{
            this.instanceLocator = instanceLocator
            return this
        }

        fun userId(userId: String): Builder{
            this.userId = userId
            return this
        }

        fun context(context: Context): Builder{
            this.context = context
            return this
        }

        fun tokenProvider(tokenProvider:  TokenProvider): Builder{
            this.tokenProvider = tokenProvider
            return this
        }

        fun tokenParams(tokenParams: ChatkitTokenParams): Builder{
            this.tokenParams = tokenParams
            return this
        }

        fun logLevel(logLevel: LogLevel): Builder{
            this.logLevel = logLevel
            return this
        }

        fun build(): ChatManager {
            if(instanceLocator == null){
                throw Error("setInstanceId() not called")
            }
            if(userId == null) {
                throw Error("userId() not called")
            }
            if(context == null){
                throw Error("setContext() not called")
            }
            if(tokenProvider == null){
                throw Error("setTokenProvider() not called")
            }

            return ChatManager(
                    instanceLocator!!,
                    userId!!,
                    context!!,
                    tokenProvider,
                    tokenParams,
                    logLevel
            )
        }
    }

    var currentUser: CurrentUser? = null
    var baseClient: BaseClient? = null
    val apiServiceName = "chatkit"
    val cursorsServiceName = "chatkit_cursors"
    val filesServiceName = "chatkit_files"
    val presenceServiceName = "chatkit_presence"
    val serviceVersion = "v1"
    val logger = AndroidLogger(logLevel)


    var userSubscription: UserSubscription? = null //Initialised when connect() is called.

    fun connect(listener: UserSubscriptionListener){
        val splitInstanceLocator = instanceLocator.split(":")
        if (splitInstanceLocator.size == 3) {
            // Let the platform library do the error handling if this is of the wrong format
            val cluster = splitInstanceLocator[1]
            baseClient = BaseClient(
                    host = "$cluster.pusherplatform.io",
                    logger = logger,
                    context = context
            )
        }

        val apiInstance = Instance(
                locator = instanceLocator,
                serviceName = apiServiceName,
                serviceVersion = serviceVersion,
                context = context,
                logger = logger,
                baseClient = baseClient
        )

        val cursorsInstance = Instance(
                locator = instanceLocator,
                serviceName = cursorsServiceName,
                serviceVersion = serviceVersion,
                context = context,
                logger = logger,
                baseClient = baseClient
        )

        val filesInstance = Instance(
                locator = instanceLocator,
                serviceName = filesServiceName,
                serviceVersion = "v1",
                context = context,
                logger = logger,
                baseClient = baseClient
        )

        val presenceInstance = Instance(
                locator = instanceLocator,
                serviceName = presenceServiceName,
                serviceVersion = "v1",
                context = context,
                logger = logger,
                baseClient = baseClient
        )

        val userStore = GlobalUserStore(
                apiInstance = apiInstance,
                logger = logger,
                tokenProvider = tokenProvider,
                tokenParams = tokenParams
        )

        if (tokenProvider is ChatkitTokenProvider) {
            tokenProvider.userId = userId
        }
        val mainThreadListeners = ThreadedUserSubscriptionListeners.from(
                listener = listener,
                thread = Handler(Looper.getMainLooper()))

        val path = "users"
        this.userSubscription = UserSubscription(
                userId = userId,
                apiInstance = apiInstance,
                cursorsInstance = cursorsInstance,
                filesInstance = filesInstance,
                presenceInstance = presenceInstance,
                path = path,
                userStore = userStore,
                tokenProvider = tokenProvider!!,
                tokenParams = null,
                logger = logger,
                listeners = mainThreadListeners
        )
    }
}

class ThreadedUserSubscriptionListeners
private constructor(
        val currentUserListener: (CurrentUser) -> Unit,
        val onError: (elements.Error) -> Unit,
        val removedFromRoom: (Int) -> Unit,
        val addedToRoom: (Room) -> Unit,
        val roomUpdated: (Room) -> Unit,
        val roomDeleted: (Int) -> Unit,
        val userJoined: (User, Room) -> Unit,
        val userLeft: (User, Room) -> Unit,
        val userCameOnline: (User) -> Unit,
        var userWentOffline: (User) -> Unit
)
{
    companion object {
        fun from(listener: UserSubscriptionListener, thread: Handler): ThreadedUserSubscriptionListeners{
            return ThreadedUserSubscriptionListeners(
                    currentUserListener = { user -> thread.post { listener.currentUserReceived(user) }},
                    onError = { error -> thread.post {  listener.onError(error) }},
                    removedFromRoom = { roomId -> thread.post { listener.removedFromRoom(roomId) }},
                    addedToRoom = { room -> thread.post { listener.addedToRoom(room) }},
                    roomUpdated = { room -> thread.post { listener.roomUpdated(room) }},
                    roomDeleted = { roomId -> thread.post { listener.roomDeleted(roomId) }},
                    userJoined = { user, room -> thread.post { listener.userJoined(user, room) }},
                    userLeft = { user, room -> thread.post { listener.userLeft(user, room) }},
                    userCameOnline = { user -> thread.post { listener.userCameOnline(user) }},
                    userWentOffline = { user -> thread.post { listener.userWentOffline(user) }}
            )
        }
    }
}


data class Message(
        val id: Int,
        val userId: String,
        val roomId: Int,
        val text: String? = null,
        val attachment: Attachment? = null,
        val createdAt: String,
        val updatedAt: String,

        var user: User?,
        var room: Room?
)

data class Attachment(
        @Transient var fetchRequired: Boolean = false,
        @SerializedName("resource_link") val link: String,
        val type: String
)

data class Cursor(
        val userId: String,
        val roomId: Int,
        val type: Int,
        val position: Int,
        val updatedAt: String,

        var user: User?,
        var room: Room?
)

data class ChatEvent(
        val eventName: String,
        val userId: String? = null,
        val timestamp: String,
        val data: JsonElement
)

typealias CustomData = MutableMap<String, String>
