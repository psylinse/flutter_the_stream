package io.getstream.flutter_the_stream

import android.os.Bundle
import com.fasterxml.jackson.databind.ObjectMapper
import com.getstream.sdk.chat.StreamChat
import com.getstream.sdk.chat.model.Channel
import com.getstream.sdk.chat.model.Event
import com.getstream.sdk.chat.rest.Message
import com.getstream.sdk.chat.rest.User
import com.getstream.sdk.chat.rest.core.ChatChannelEventHandler
import com.getstream.sdk.chat.rest.interfaces.MessageCallback
import com.getstream.sdk.chat.rest.interfaces.QueryChannelCallback
import com.getstream.sdk.chat.rest.interfaces.QueryWatchCallback
import com.getstream.sdk.chat.rest.request.ChannelQueryRequest
import com.getstream.sdk.chat.rest.request.ChannelWatchRequest
import com.getstream.sdk.chat.rest.response.ChannelState
import com.getstream.sdk.chat.rest.response.MessageResponse

import io.flutter.app.FlutterActivity
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugins.GeneratedPluginRegistrant
import io.getstream.cloud.CloudClient
import io.getstream.core.models.Activity
import java.util.*
import io.getstream.core.options.Limit
import io.flutter.plugin.common.EventChannel


class MainActivity : FlutterActivity() {
  private val CHANNEL = "io.getstream/backend"
  private val API_KEY = "<API_KEY>"
  private val eventChannels: MutableMap<String, EventChannel> = mutableMapOf()

  override fun onCreate(savedInstanceState: Bundle?) {

    super.onCreate(savedInstanceState)
    GeneratedPluginRegistrant.registerWith(this)

    MethodChannel(flutterView, CHANNEL).setMethodCallHandler { call, result ->
      if (call.method == "setupChat") {
        setupChat(
          call.argument<String>("user")!!,
          call.argument<String>("token")!!
        )
        result.success(true)
      } else if (call.method == "postMessage") {
        postMessage(
          call.argument<String>("user")!!,
          call.argument<String>("token")!!,
          call.argument<String>("message")!!
        )
        result.success(true)
      } else if (call.method == "getActivities") {
        val activities = getActivities(
          call.argument<String>("user")!!,
          call.argument<String>("token")!!
        )
        result.success(ObjectMapper().writeValueAsString(activities))
      } else if (call.method == "getTimeline") {
        val activities = getTimeline(
          call.argument<String>("user")!!,
          call.argument<String>("token")!!
        )
        result.success(ObjectMapper().writeValueAsString(activities))
      } else if (call.method == "follow") {
        follow(
          call.argument<String>("user")!!,
          call.argument<String>("token")!!,
          call.argument<String>("userToFollow")!!
        )
        result.success(true)
      } else if (call.method == "getChatMessages") {
        getChatMessages(
          result,
          call.argument<String>("user")!!,
          call.argument<String>("userToChatWith")!!,
          call.argument<String>("token")!!
        )
      } else if (call.method == "postChatMessage") {
        postChatMessage(
          result,
          call.argument<String>("user")!!,
          call.argument<String>("userToChatWith")!!,
          call.argument<String>("message")!!,
          call.argument<String>("token")!!
        )
      } else if (call.method == "setupChannel") {
        setupChannel(
          result,
          call.argument<String>("user")!!,
          call.argument<String>("userToChatWith")!!,
          call.argument<String>("token")!!
        )
      } else {
        result.notImplemented()
      }
    }
  }

  private fun setupChat(user: String, token: String) {
    StreamChat.init(API_KEY, applicationContext)
    val client = StreamChat.getInstance(this.application)
    client.setUser(User(user), token)
  }

  private fun postMessage(user: String, token: String, message: String) {
    val client = CloudClient.builder(API_KEY, token, user).build()

    val feed = client.flatFeed("user")
    feed.addActivity(
      Activity
        .builder()
        .actor("SU:${user}")
        .verb("post")
        .`object`(UUID.randomUUID().toString())
        .extraField("message", message)
        .build()
    ).join()
  }

  private fun getActivities(user: String, token: String): List<Activity> {
    val client = CloudClient.builder(API_KEY, token, user).build()

    return client.flatFeed("user").getActivities(Limit(25)).join()
  }

  private fun getTimeline(user: String, token: String): List<Activity> {
    val client = CloudClient.builder(API_KEY, token, user).build()

    return client.flatFeed("timeline").getActivities(Limit(25)).join()
  }

  private fun follow(user: String, token: String, userToFollow: String): Boolean {
    val client = CloudClient.builder(API_KEY, token, user).build()

    client.flatFeed("timeline").follow(client.flatFeed("user", userToFollow)).join()
    return true
  }

  private fun getChatMessages(result: MethodChannel.Result, user: String, userToChatWith: String, token: String) {
    val client = StreamChat.getInstance(this.application)
    val channel = client.channel("messaging", listOf(user, userToChatWith).sorted().joinToString("-"))

    channel.query(ChannelQueryRequest().withMessages(50), object : QueryChannelCallback {
      override fun onSuccess(response: ChannelState) {
        result.success(ObjectMapper().writeValueAsString(response.messages))
      }

      override fun onError(errMsg: String, errCode: Int) {


      }
    })
  }

  private fun setupChannel(result: MethodChannel.Result, user: String, userToChatWith: String, token: String) {
    val application = this.application;
    val channelName = listOf(user, userToChatWith).sorted().joinToString("-")
    val eventChannel = EventChannel(flutterView, "io.getstream/events/${channelName}")

    eventChannel.setStreamHandler(object : EventChannel.StreamHandler {
      override fun onListen(listener: Any, eventSink: EventChannel.EventSink) {
        val client = StreamChat.getInstance(application)
        val channel = client.channel("messaging", channelName)
        channel.watch(ChannelWatchRequest(), object : QueryWatchCallback {
          override fun onSuccess(response: ChannelState) {
            eventSink.success(ObjectMapper().writeValueAsString(response.messages))
          }

          override fun onError(errMsg: String, errCode: Int) {
            println("test")
          }
        })

        channel.addEventHandler(object : ChatChannelEventHandler() {
          override fun onMessageNew(event: Event) {
            eventSink.success(ObjectMapper().writeValueAsString(listOf(event.message)))
          }
        })
      }

      override fun onCancel(listener: Any) {
//        cancelListening(listener)
      }
    })

    eventChannels[channelName] = eventChannel

    result.success(channelName)
  }

  private fun postChatMessage(result: MethodChannel.Result, user: String, userToChatWith: String, message: String, token: String) {
    val client = StreamChat.getInstance(this.application)
    val channel = client.channel("messaging", listOf(user, userToChatWith).sorted().joinToString("-"))
    val streamMessage = Message()
    streamMessage.text = message
    channel.sendMessage(streamMessage, object : MessageCallback {
      override fun onSuccess(response: MessageResponse?) {
        result.success(true)
      }

      override fun onError(errMsg: String?, errCode: Int) {
        result.error("FAILURE", errMsg, null)

      }
    })
  }
}
