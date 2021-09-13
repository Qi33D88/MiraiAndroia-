@file:Suppress(
    "EXPERIMENTAL_API_USAGE",
    "DEPRECATION_ERROR",
    "OverridingDeprecatedMember",
    "INVISIBLE_REFERENCE",
    "INVISIBLE_MEMBER"
)

package io.github.mzdluo123.mirai.android.miraiconsole

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import io.github.mzdluo123.mirai.android.AppSettings
import io.github.mzdluo123.mirai.android.BotApplication
import io.github.mzdluo123.mirai.android.BuildConfig
import io.github.mzdluo123.mirai.android.NotificationFactory
import io.github.mzdluo123.mirai.android.appcenter.trace
import io.github.mzdluo123.mirai.android.service.BotService
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.console.ConsoleFrontEndImplementation
import net.mamoe.mirai.console.MiraiConsoleFrontEndDescription
import net.mamoe.mirai.console.MiraiConsoleImplementation
import net.mamoe.mirai.console.data.MultiFilePluginDataStorage
import net.mamoe.mirai.console.data.PluginDataStorage
import net.mamoe.mirai.console.internal.logging.LoggerControllerImpl
import net.mamoe.mirai.console.logging.LoggerController
import net.mamoe.mirai.console.plugin.loader.PluginLoader
import net.mamoe.mirai.console.util.ConsoleInput
import net.mamoe.mirai.console.util.ConsoleInternalApi
import net.mamoe.mirai.console.util.NamedSupervisorJob
import net.mamoe.mirai.console.util.SemVersion
import net.mamoe.mirai.event.events.BotOfflineEvent
import net.mamoe.mirai.event.events.BotReloginEvent
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.event.subscribeMessages
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.utils.BotConfiguration
import net.mamoe.mirai.utils.LoginSolver
import net.mamoe.mirai.utils.MiraiLogger
import net.mamoe.mirai.utils.SimpleLogger
import java.nio.file.Path
import java.nio.file.Paths

class AndroidMiraiConsole(
    val context: Context,
    rootPath: Path,
) : MiraiConsoleImplementation,
    CoroutineScope by CoroutineScope(NamedSupervisorJob("MiraiAndroid") + CoroutineExceptionHandler { _, throwable ->
        Log.e("MiraiAndroid", "发生异常")
        logException(throwable)

    }
    ) {

    val loginSolver =
        AndroidLoginSolver(context)

    // 使用一个[60s/refreshPerMinute]的数组存放每4秒消息条数
    // 读取时增加最新一分钟，减去最老一分钟
    private val refreshPerMinute = AppSettings.refreshPerMinute
    private val msgSpeeds = IntArray(refreshPerMinute)
    private var refreshCurrentPos = 0

    private var sendOfflineMsgJob: Job? = null


    @ConsoleFrontEndImplementation
    override val rootPath: Path = Paths.get(context.getExternalFilesDir("")!!.absolutePath)

    @ConsoleFrontEndImplementation
    override fun createLogger(identity: String?): MiraiLogger = MiraiAndroidLogger

    @ConsoleFrontEndImplementation
    override fun createLoginSolver(
        requesterBot: Long,
        configuration: BotConfiguration
    ): LoginSolver = loginSolver

    @ConsoleFrontEndImplementation
    override val builtInPluginLoaders: List<Lazy<PluginLoader<*, *>>> =
        listOf(lazy { DexPluginLoader(context.getExternalFilesDir("odex")!!.path) })

    @ConsoleFrontEndImplementation
    override val frontEndDescription: MiraiConsoleFrontEndDescription =
        AndroidConsoleFrontEndDescImpl

    @ConsoleFrontEndImplementation
    override val consoleCommandSender: MiraiConsoleImplementation.ConsoleCommandSenderImpl =
        AndroidConsoleCommandSenderImpl

    @ConsoleFrontEndImplementation
    override val consoleInput: ConsoleInput
        get() = AndroidConsoleInput


    @ConsoleFrontEndImplementation
    override val dataStorageForJvmPluginLoader: PluginDataStorage =
        MultiFilePluginDataStorage(rootPath.resolve("data"))

    @ConsoleFrontEndImplementation
    override val dataStorageForBuiltIns: PluginDataStorage =
        MultiFilePluginDataStorage(rootPath.resolve("data"))

    @ConsoleFrontEndImplementation
    override val configStorageForJvmPluginLoader: PluginDataStorage =
        MultiFilePluginDataStorage(rootPath.resolve("config"))

    @ConsoleFrontEndImplementation
    override val configStorageForBuiltIns: PluginDataStorage =
        MultiFilePluginDataStorage(rootPath.resolve("config"))

    @ConsoleInternalApi
    @ConsoleFrontEndImplementation
    override val loggerController: LoggerController
        get() = if (AppSettings.printToLogcat || BuildConfig.DEBUG) { // 显示所有级别的日志
            object : LoggerController {
                override fun shouldLog(
                    identity: String?,
                    priority: SimpleLogger.LogPriority
                ): Boolean {
                    return true
                }
            }
        } else {
            LoggerControllerImpl
        }

    fun afterBotLogin(bot: Bot) {
        startRefreshNotificationJob(bot)
        bot.eventChannel.subscribeAlways<BotOfflineEvent>() {
            if (this is BotOfflineEvent.Force) {
                NotificationManagerCompat.from(BotApplication.context).apply {
                    notify(
                        BotService.OFFLINE_NOTIFICATION_ID,
                        NotificationFactory.offlineNotification(message, true)
                    )
                }
                return@subscribeAlways
            }
        }
        bot.eventChannel.subscribeAlways<BotReloginEvent>() {

        if (sendOfflineMsgJob != null && sendOfflineMsgJob!!.isActive) {
                sendOfflineMsgJob!!.cancel()
            }
            NotificationManagerCompat.from(BotApplication.context)
                .cancel(BotService.OFFLINE_NOTIFICATION_ID)
        }
        trace("login success")
    }


    private fun startRefreshNotificationJob(bot: Bot) {
        this.globalEventChannel().subscribeMessages { always { msgSpeeds[refreshCurrentPos] += 1 } }
        launch {
            val avatar = downloadAvatar(bot)  // 获取通知展示用的头像
            var msgSpeed = 0
            while (isActive) {
                /*
                * 总速度+=最新速度 [0] [1] ... [14]
                * 总速度-=最老速度 [1] [2] ... [0]
                */
                msgSpeed += msgSpeeds[refreshCurrentPos]
                if (refreshCurrentPos != refreshPerMinute - 1) {
                    refreshCurrentPos += 1
                } else {
                    refreshCurrentPos = 0
                }
                msgSpeed -= msgSpeeds[refreshCurrentPos]
                msgSpeeds[refreshCurrentPos] = 0
                if (msgSpeed < 0) {
                    msgSpeed = 0
                }
                NotificationManagerCompat.from(BotApplication.context).apply {
                    notify(
                        BotService.NOTIFICATION_ID,
                        NotificationFactory.statusNotification("消息速度 ${msgSpeed}/min", avatar)
                    )
                }
                delay(60L / refreshPerMinute * 1000)
            }
        }
    }

    private suspend fun downloadAvatar(bot: Bot): Bitmap =
        try {
//            bot.logger.info("正在加载头像....")

            HttpClient().get<ByteArray>(bot.avatarUrl).let { avatarData ->
                BitmapFactory.decodeByteArray(avatarData, 0, avatarData.size)
            }
        } catch (e: Exception) {
            delay(1000)
            downloadAvatar(bot)
        }

}


object AndroidConsoleFrontEndDescImpl : MiraiConsoleFrontEndDescription {
    override val name: String get() = "Android"
    override val vendor: String get() = "Mamoe Technologies"

    // net.mamoe.mirai.console.internal.MiraiConsoleBuildConstants.version
    // is console's version not frontend's version
    override val version: SemVersion = SemVersion(BuildConfig.VERSION_NAME)
}


@ConsoleFrontEndImplementation
object AndroidConsoleCommandSenderImpl : MiraiConsoleImplementation.ConsoleCommandSenderImpl {

    @JvmSynthetic
    override suspend fun sendMessage(message: String) {
        MiraiAndroidLogger.info(message)
    }

    @JvmSynthetic
    override suspend fun sendMessage(message: Message) {
        MiraiAndroidLogger.info(message.contentToString())
    }

}