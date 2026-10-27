package com.liquid.reminders;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;

/** Stores reminders that have a due time and registers them with the system alarm clock. */
public final class ReminderScheduler {

    static final String PREFS = "liquid_reminders_native";
    static final String PREFIX = "item_";
    static final String CHANNEL = "reminders";

    private ReminderScheduler() {
    }

    static PendingIntent fireIntent(Context c, long id) {
        Intent i = new Intent(c, ReminderReceiver.class);
        i.setAction("com.liquid.reminders.FIRE");
        i.putExtra("id", id);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(c, (int) (Math.abs(id) % 1000000), i, flags);
    }

    static void schedule(Context c, long id, String title, String notes, long due) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("title", title);
            o.put("notes", notes);
            o.put("due", due);
            sp.edit().putString(PREFIX + id, o.toString()).apply();
        } catch (Exception ignored) {
        }

        if (due <= System.currentTimeMillis()) return;

        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = fireIntent(c, id);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pi);
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, due, pi);
            }
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC_WAKEUP, due, pi);
        }
    }

    static void cancel(Context c, long id) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().remove(PREFIX + id).apply();
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(fireIntent(c, id));
    }

    static void clearAll(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            String k = e.getKey();
            if (!k.startsWith(PREFIX)) continue;
            try {
                long id = Long.parseLong(k.substring(PREFIX.length()));
                if (am != null) am.cancel(fireIntent(c, id));
            } catch (Exception ignored) {
            }
        }
        sp.edit().clear().apply();
    }

    /** Called by the web layer whenever the list changes: rebuild every alarm from scratch. */
    static void resync(Context c, String json) {
        clearAll(c);
        if (json == null) return;
        long now = System.currentTimeMillis();
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                if (o.optBoolean("done", false)) continue;
                long due = o.optLong("due", 0L);
                if (due <= now) continue;
                schedule(c, o.optLong("id", 0L), o.optString("title", ""),
                        o.optString("notes", ""), due);
            }
        } catch (Exception ignored) {
        }
    }

    /** Re-arm everything after a reboot or app update. */
    static void rescheduleAll(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            String k = e.getKey();
            if (!k.startsWith(PREFIX)) continue;
            Object v = e.getValue();
            if (!(v instanceof String)) continue;
            try {
                JSONObject o = new JSONObject((String) v);
                long due = o.optLong("due", 0L);
                if (due > now) {
                    schedule(c, o.optLong("id", 0L), o.optString("title", ""),
                            o.optString("notes", ""), due);
                } else {
                    sp.edit().remove(k).apply();
                }
            } catch (Exception ignored) {
            }
        }
    }

    static void notify(Context c, long id, String title, String notes) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Due reminders from Liquid Reminders");
            ch.enableVibration(true);
            ch.enableLights(true);
            nm.createNotificationChannel(ch);
        }

        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) fl |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent content = PendingIntent.getActivity(c, (int) (Math.abs(id) % 1000000), open, fl);

        boolean hasNotes = notes != null && notes.trim().length() > 0;
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CHANNEL)
                : new Notification.Builder(c);

        b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title == null || title.length() == 0 ? "Reminder" : title)
                .setContentText(hasNotes ? notes : "Reminder is due now")
                .setStyle(new Notification.BigTextStyle().bigText(hasNotes ? notes : "Reminder is due now"))
                .setAutoCancel(true)
                .setContentIntent(content)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_ALL);

        try {
            nm.notify((int) (Math.abs(id) % 1000000), b.build());
        } catch (SecurityException ignored) {
            // Notification permission revoked by the user.
        }
    }
}
