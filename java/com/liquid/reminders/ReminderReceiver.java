package com.liquid.reminders;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONObject;

public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        long id = intent.getLongExtra("id", 0L);
        SharedPreferences sp = context.getSharedPreferences(
                ReminderScheduler.PREFS, Context.MODE_PRIVATE);
        String raw = sp.getString(ReminderScheduler.PREFIX + id, null);

        String title = "Reminder";
        String notes = "";
        if (raw != null) {
            try {
                JSONObject o = new JSONObject(raw);
                title = o.optString("title", title);
                notes = o.optString("notes", "");
            } catch (Exception ignored) {
            }
            /* The entry leaves the store before this receiver returns, and it leaves it
               synchronously. apply() only hands the write to a background thread, and a
               BroadcastReceiver's process may be killed the moment onReceive() comes back, so
               the removal could be lost while the tray copy above it was already posted: the
               entry would still be in the store, and resync() — which the page calls after
               every add, toggle and delete, and again as it loads — collects every stored entry
               that has come due and posts its tray copy, so a reminder that had just rung would
               be delivered a second time, and a reboot would do it again through
               rescheduleAll(). The write is one small key, and commit() is what the platform
               asks a receiver to use for a write that has to survive it. */
            sp.edit().remove(ReminderScheduler.PREFIX + id).commit();
        }

        /* The page alerts a reminder on its own timer at the due moment, and this alarm fires
           for the same reminder at that same moment: with the app open the user was told twice,
           once on the toast and once in the tray. The page's alert is the reminder while the
           app is on screen, so the tray copy is dropped here. The stored entry has already been
           consumed above either way, and the page's own list still carries the reminder, so
           nothing is left to be delivered a second time. */
        if (ReminderScheduler.isForeground()) return;
        ReminderScheduler.notify(context, id, title, notes);
    }
}
