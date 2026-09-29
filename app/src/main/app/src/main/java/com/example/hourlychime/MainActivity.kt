package com.example.hourlychime

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar
import kotlin.concurrent.thread
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("ChimePrefs", Context.MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(50, 50, 50, 50)
        }

        val title = TextView(this).apply {
            text = "ساعت زنگ‌دار ساعتی"
            textSize = 22f
            setTextColor(Color.parseColor("#38BDF8"))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }

        val statusText = TextView(this).apply {
            val active = prefs.getBoolean("is_alarm_active", false)
            text = if (active) "وضعیت: روشن (نواختن سر ساعت)" else "وضعیت: خاموش"
            textSize = 15f
            setTextColor(if (active) Color.parseColor("#4ADE80") else Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }

        val btnToggle = Button(this).apply {
            val isActive = prefs.getBoolean("is_alarm_active", false)
            text = if (isActive) "خاموش کردن زنگ" else "روشن کردن زنگ ساعتی"
            setBackgroundColor(if (isActive) Color.parseColor("#DC2626") else Color.parseColor("#0284C7"))
            setTextColor(Color.WHITE)

            setOnClickListener {
                val currentState = prefs.getBoolean("is_alarm_active", false)
                val newState = !currentState
                prefs.edit().putBoolean("is_alarm_active", newState).apply()

                if (newState) {
                    HourlyChimeReceiver.scheduleNextChime(this@MainActivity)
                    text = "خاموش کردن زنگ"
                    setBackgroundColor(Color.parseColor("#DC2626"))
                    statusText.text = "وضعیت: روشن (نواختن سر ساعت)"
                    statusText.setTextColor(Color.parseColor("#4ADE80"))
                    Toast.makeText(this@MainActivity, "زنگ ساعتی فعال شد", Toast.LENGTH_SHORT).show()
                } else {
                    HourlyChimeReceiver.cancelChime(this@MainActivity)
                    text = "روشن کردن زنگ ساعتی"
                    setBackgroundColor(Color.parseColor("#0284C7"))
                    statusText.text = "وضعیت: خاموش"
                    statusText.setTextColor(Color.parseColor("#94A3B8"))
                    Toast.makeText(this@MainActivity, "زنگ خاموش شد", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val chkQuiet = CheckBox(this).apply {
            text = "حالت بی‌صدا در شب (۲۳:۰۰ الی ۰۷:۰۰)"
            setTextColor(Color.parseColor("#E2E8F0"))
            isChecked = prefs.getBoolean("quiet_enabled", true)
            setPadding(0, 30, 0, 20)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("quiet_enabled", checked).apply()
            }
        }

        val btnTest = Button(this).apply {
            text = "تست زنگ ساعت جاری"
            setBackgroundColor(Color.parseColor("#334155"))
            setTextColor(Color.parseColor("#CBD5E1"))
            setOnClickListener {
                val count = when (val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) % 12) {
                    0 -> 12
                    else -> h
                }
                Toast.makeText(this@MainActivity, "پخش $count ضربه زنگ...", Toast.LENGTH_SHORT).show()
                HourlyChimeReceiver.playBellSequence(count)
            }
        }

        root.addView(title)
        root.addView(statusText)
        root.addView(btnToggle)
        root.addView(chkQuiet)
        root.addView(btnTest)

        setContentView(root)
    }
}

class HourlyChimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val hour24 = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        if (!isQuietHour(context, hour24)) {
            val count = when (val h = hour24 % 12) {
                0 -> 12
                else -> h
            }
            playBellSequence(count)
        }

        scheduleNextChime(context)
    }

    private fun isQuietHour(context: Context, currentHour: Int): Boolean {
        val prefs = context.getSharedPreferences("ChimePrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("quiet_enabled", true)) return false

        val start = prefs.getInt("quiet_start", 23)
        val end = prefs.getInt("quiet_end", 7)
        return currentHour >= start || currentHour < end
    }

    companion object {
        fun playBellSequence(totalRounds: Int) {
            thread {
                for (i in 0 until totalRounds) {
                    playTone()
                    Thread.sleep(1200)
                }
            }
        }

        private fun playTone() {
            val sampleRate = 44100
            val duration = 1.1
            val numSamples = (duration * sampleRate).toInt()
            val buffer = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                val envelope = Math.exp(-3.8 * t)
                val sample = sin(2 * Math.PI * 587.33 * t) * envelope
                buffer[i] = (sample * 32767).toInt().toShort()
            }

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(numSamples * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(buffer, 0, numSamples)
            track.play()
            Thread.sleep(1200)
            track.release()
        }

        fun scheduleNextChime(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, HourlyChimeReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 1001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cal = Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            }
        }

        fun cancelChime(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, HourlyChimeReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 1001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val prefs = context.getSharedPreferences("ChimePrefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("is_alarm_active", false)) {
                HourlyChimeReceiver.scheduleNextChime(context)
            }
        }
    }
}
