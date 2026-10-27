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
            sp.edit().remove(ReminderScheduler.PREFIX + id).apply();
        }

        ReminderScheduler.notify(context, id, title, notes);
    }
}
