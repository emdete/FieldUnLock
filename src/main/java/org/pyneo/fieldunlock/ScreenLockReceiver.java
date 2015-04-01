package org.pyneo.fieldunlock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;


public class ScreenLockReceiver extends BroadcastReceiver implements Util {

	private Boolean lockscreen = false;
	private JSONObject root, settings;
	private Context thisContext;


	public void readFromJSON() {
		// Read from JSON file
		try {
			BufferedReader bRead = new BufferedReader(new InputStreamReader
					(thisContext.getApplicationContext().openFileInput("settings.json")));
			root = new JSONObject(bRead.readLine());

		} catch (FileNotFoundException e) {
			Log.e(TAG, e.toString(), e);
		} catch (JSONException e) {
			Log.e(TAG, e.toString(), e);
		} catch (IOException e) {
			Log.e(TAG, e.toString(), e);
		}

		// Read settings object from root
		try {
			settings = root.getJSONObject("settings");

		} catch (JSONException e) {
			Log.e(TAG, e.toString(), e);
		}

		// Read required items from settings object
		try {
			lockscreen = settings.getBoolean("lockscreen");

		} catch (JSONException e) {
			Log.e(TAG, e.toString(), e);
		}
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		thisContext = context;
		readFromJSON();

		// If SCREEN_OFF event detected, start lockscreen if enabled
		// As new task and single top, with no animation
		if (lockscreen && intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
			Intent intent1 = new Intent(context, LockActivity.class);
			intent1.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
					| Intent.FLAG_ACTIVITY_NEW_TASK
					| Intent.FLAG_ACTIVITY_NO_ANIMATION);

			context.startActivity(intent1);
		}

		// If BOOT_COMPLETED event detected, start service and lockscreen if enabled
		// As new task and single top, with no animation
		else if (lockscreen && intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
			context.startService(new Intent(context, ScreenLockService.class));

			Intent intent1 = new Intent(context, LockActivity.class);
			intent1.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
					| Intent.FLAG_ACTIVITY_NEW_TASK
					| Intent.FLAG_ACTIVITY_NO_ANIMATION);

			context.startActivity(intent1);
		}
	}
}
