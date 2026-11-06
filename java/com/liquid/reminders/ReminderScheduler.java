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

import java.util.HashMap;
import java.util.Map;

/** Stores reminders that have a due time and registers them with the system alarm clock. */
public final class ReminderScheduler {

    static final String PREFS = "liquid_reminders_native";
    static final String PREFIX = "item_";
    static final String CHANNEL = "reminders";
    static final String KEY_EXACT = "exact_alarms_granted";
    static final String KEY_ASKED = "notif_permission_asked";

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

        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        /* a due that has already gone by — the app opened a moment after it, or the alarms were
           rebuilt as it arrived — used to return here with no alarm armed at all, so the
           reminder was never delivered. It is owed all the same, so it is armed for this
           instant and the system delivers it straight away. */
        long now = System.currentTimeMillis();
        long at = due > now ? due : now;
        PendingIntent pi = fireIntent(c, id);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi);
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
        long now = System.currentTimeMillis();
        /* an entry leaves the store the moment its alarm is delivered, so whatever is still in
           it is a reminder the system has not fired yet. The app is opened at, or a moment
           after, the due time often enough, and clearAll() below cancels every pending alarm —
           a reminder due exactly now was cancelled a few milliseconds before it would have
           rung and then dropped, leaving the tray silent. Those are collected here, before the
           store is cleared, and handed to the tray at the end instead of being thrown away. */
        Map<Long, JSONObject> came = new HashMap<Long, JSONObject>();
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            String k = e.getKey();
            if (!k.startsWith(PREFIX)) continue;
            Object v = e.getValue();
            if (!(v instanceof String)) continue;
            try {
                long id = Long.parseLong(k.substring(PREFIX.length()));
                JSONObject o = new JSONObject((String) v);
                if (o.optLong("due", 0L) <= now) came.put(id, o);
            } catch (Exception ignored) {
            }
        }
        clearAll(c);
        if (json == null) return;
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
            /* and the ones that came due while the alarms were being rebuilt are delivered
               now: an id the list no longer carries, or one that has been ticked off, is left
               out with the rest, so deleting or completing a reminder still silences it */
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                if (o.optBoolean("done", false)) continue;
                long id = o.optLong("id", 0L);
                if (!came.containsKey(id)) continue;
                notify(c, id, o.optString("title", ""), o.optString("notes", ""));
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

    /* The exact-alarm permission is granted outside the app, in system Settings, so an alarm
       armed while it was missing was handed to the inexact API and stayed approximate for good,
       even after the user allowed exact alarms a moment later. The state last seen is kept in
       the store; when the app is opened again and the permission has since been granted, every
       pending reminder is rebuilt, which re-arms it through the exact path in schedule(). */
    static void rearmIfExactAlarmsGranted(Context c) {
        if (Build.VERSION.SDK_INT < 31) return;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        boolean granted;
        try {
            granted = am.canScheduleExactAlarms();
        } catch (Exception e) {
            return;
        }
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean was = sp.getBoolean(KEY_EXACT, false);
        if (granted != was) sp.edit().putBoolean(KEY_EXACT, granted).apply();
        if (granted && !was) rescheduleAll(c);
    }

    /* Whether a notification posted now would actually reach the tray. On Android 13+ this is
       the POST_NOTIFICATIONS permission; below that it is the app's own switch in system
       Settings, which the user can turn off without the app ever asking. Both are reported by
       the same call, so the answer is read here once instead of being guessed from the SDK
       level. A missing NotificationManager answers "allowed": there is nothing to report then,
       and a hint would be about a problem that may not exist. */
    static boolean notificationsAllowed(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return true;
        try {
            return nm.areNotificationsEnabled();
        } catch (Exception e) {
            return true;
        }
    }

    /* "Not allowed" is only a refusal once the user has answered. On Android 13+ the permission
       is asked for on the first launch, and until that dialog comes back a missing permission
       means "not asked yet" — hinting then would put the hint on screen underneath the system
       dialog the user is reading. Below 33 there is no dialog to wait for, so the app's own
       switch is the whole answer. */
    static boolean notificationsRefused(Context c) {
        if (notificationsAllowed(c)) return false;
        if (Build.VERSION.SDK_INT < 33) return true;
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false);
    }

    /** Recorded when the permission dialog has been answered, granted or not. */
    static void markAsked(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ASKED, true).apply();
    }

    /* A reminder's title and notes are free text written by the user, and the notification is
       the one place the app hands that text to another app. It is cleaned here, once, on the
       way into the tray: the entities an older build wrote into storage are turned back into
       the characters they stand for, the invisible marks a paste brings (zero-width space and
       joiners, word joiner, byte-order mark) are dropped, a line break or a tab becomes a
       single space — the tray draws one line, and BigTextStyle would otherwise show the break
       as a gap — and any other control character goes with them. Whitespace is collapsed and
       trimmed, so a note of nothing but blanks counts as no note at all. */
    static String plainText(String s) {
        if (s == null) return "";
        String t = s;
        if (t.indexOf('&') >= 0) {
            t = t.replace("&lt;", "<").replace("&gt;", ">")
                 .replace("&quot;", "\"").replace("&apos;", "'")
                 .replace("&#39;", "'").replace("&nbsp;", " ")
                 .replace("&amp;", "&");
        }
        StringBuilder out = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch == '\u200b' || ch == '\u200c' || ch == '\u200d'
                    || ch == '\u2060' || ch == '\ufeff') continue;
            if (ch == '\n' || ch == '\r' || ch == '\t' || ch == ' ') {
                if (out.length() == 0 || out.charAt(out.length() - 1) == ' ') continue;
                out.append(' ');
                continue;
            }
            if (ch < 0x20 || ch == 0x7f) continue;
            out.append(ch);
        }
        return out.toString().trim();
    }

    private static NotificationChannel buildChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Due reminders from Liquid Reminders");
        ch.enableVibration(true);
        ch.enableLights(true);
        return ch;
    }

    /* A channel's importance is the app's to ask for exactly once: after that the user owns it,
       and createNotificationChannel() on a channel that already exists only updates the name
       and the description, never the importance. Someone who had once dropped Reminders to
       "Silent", or below High, kept a channel that could no longer ring — every later
       notification was posted into it and the reminder passed without a sound. So when the app
       is opened the channel that exists is compared with the one the app asks for, and if it
       has been lowered it is deleted and created again, which is the only way the importance
       can be brought back. Nothing is touched while the channel still matches, and a channel
       that does not exist yet is simply created. */
    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel existing;
        try {
            existing = nm.getNotificationChannel(CHANNEL);
        } catch (Exception e) {
            return;
        }
        if (existing != null) {
            if (existing.getImportance() >= NotificationManager.IMPORTANCE_HIGH) return;
            try {
                nm.deleteNotificationChannel(CHANNEL);
            } catch (Exception e) {
                return;
            }
        }
        nm.createNotificationChannel(buildChannel());
    }

    static void notify(Context c, long id, String title, String notes) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= 26) {
            /* the definition only: a channel that already exists keeps the importance the user
               gave it here, and ensureChannel() on open is what restores a lowered one */
            nm.createNotificationChannel(buildChannel());
        }

        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) fl |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent content = PendingIntent.getActivity(c, (int) (Math.abs(id) % 1000000), open, fl);

        String t = plainText(title);
        String n = plainText(notes);
        boolean hasNotes = n.length() > 0;
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CHANNEL)
                : new Notification.Builder(c);

        b.setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(t.length() == 0 ? "Reminder" : t)
                .setContentText(hasNotes ? n : "Reminder is due now")
                .setStyle(new Notification.BigTextStyle().bigText(hasNotes ? n : "Reminder is due now"))
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
