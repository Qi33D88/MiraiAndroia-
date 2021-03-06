package io.github.mzdluo123.mirai.android

import splitties.preferences.Preferences
import kotlin.reflect.KProperty

object AppSettings : Preferences("setting") {

    class IntPrefSaveAsStr(private val key: String, private val defaultValue: Int) {
        operator fun getValue(thisRef: Preferences, prop: KProperty<*>): Int {
            return prefs.getString(key, null)?.toInt() ?: defaultValue
        }

        operator fun setValue(thisRef: Preferences, prop: KProperty<*>, value: Int) {
            prefs.edit().putString(key, value.toString()).apply()
        }

    }

    var allowPushMsg by BoolPref("allow_push_msg_preference", false)
    var logBuffer by IntPrefSaveAsStr("log_buffer_preference", 300)
    var printToLogcat by BoolPref("print_to_logcat_preference", false)
    var refreshPerMinute by IntPrefSaveAsStr("status_refresh_count", 15)
    var startOnBoot by BoolPref("start_on_boot_preference", false)
    var waitingDebugger by BoolPref("waiting_debugger_preference", false)

    var keepLive by BoolPref("keeplive_preference", false)
}