package org.pyneo.fieldunlock;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.PixelFormat;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.nfc.Tag;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class LockActivity extends Activity implements View.OnClickListener, View.OnTouchListener, Util {
	private static final int SCREEN_TIMEOUT = 15000; // Screen timeout flag
	private static final int TIMEOUT_DELAY = 3000; // For getting system screen timeout
	private static final int PIN_LOCKED_RUNNABLE_DELAY = 30000; // Unlock PIN keypad after x milliseconds
	// Alarm check flags
	private static final String ALARM_ALERT_ACTION = "com.android.deskclock.ALARM_ALERT";
	private static final String ALARM_SNOOZE_ACTION = "com.android.deskclock.ALARM_SNOOZE";
	private static final String ALARM_DISMISS_ACTION = "com.android.deskclock.ALARM_DISMISS";
	private static final String ALARM_DONE_ACTION = "com.android.deskclock.ALARM_DONE";
	private PendingIntent pIntent; // Pending intent for NFC Tag discovery
	private int flags; // Window flags
	private WindowManager wm;
	private ActivityManager activityManager;
	private PackageManager packageManager;
	// Components for home launcher activity
	private ComponentName cnHome;
	private int componentDisabled, componentEnabled;
	private NfcAdapter nfcAdapter;
	private Vibrator vibrator;
	private Boolean vibratorAvailable = false; // True if device vibrator exists, false otherwise
	private ContentResolver cResolver; // Content resolver for system settings get and put
	private int systemScreenTimeout = -1; // For getting default system screen timeout
	private int ringerMode; // 1 (NORMAL), 2 (VIBRATE) and 3 (SILENT)
	private int taskId;
	private String pinEntered = ""; // The PIN the user inputs
	private int pinAttempts = 5; // The number of attempts before PIN locked for 30s
	// For updating time and date values
	private Calendar calendar;
	// Layout items
	private View disableStatusBar;
	private TextView time;
	private TextView date;
	private TextView battery;
	private TextView unlockText;
	private TextView pinInput;
	// Max number of times the launcher pick dialog toast should show
	private int launcherPickToast = 2;
	private Boolean isPhoneCalling = false; // True if phone state listener is ringing/offhook
	private Boolean isAlarmRinging = false; // True if alarm is ringing
	// Contents of JSON file
	private JSONObject root;
	private JSONObject settings;
	private JSONArray tags;
	private String pin;
	private Boolean pinLocked;
	private int blur;
	private Handler mHandler = new Handler();
	private UnattendedPic unattendedPic = new UnattendedPic() {
		public void captured(File file) {
			send(file);
		}
	};
	// Vibrate, set pinLocked to false, store and reset pinEntered
	protected Runnable pinLockedRunnable = new Runnable() {
		@Override
		public void run() {
			if (vibratorAvailable)
				vibrator.vibrate(150);

			if (nfcAdapter != null) {
				if (nfcAdapter.isEnabled())
					unlockText.setText("");

				else
					unlockText.setText(getResources().getString(R.string.scan_to_unlock_nfc_off));
			} else
				unlockText.setText(getResources().getString(R.string.scan_to_unlock_nfc_off));

			pinLocked = false;

			try {
				settings.put("pinLocked", false);

			} catch (JSONException e) {
				Log.e(TAG, e.toString(), e);
			}

			writeToJSON();

			updatePIN(null);
		}
	};
	// Broadcast receiver for time, date and battery changed
	private BroadcastReceiver mChangeReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();

			if (action.equals(Intent.ACTION_TIME_TICK))
				updateTime();

			else if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
				int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
				updateBattery(level);
			} else if (action.equals(Intent.ACTION_DATE_CHANGED))
				updateDate();
		}
	};
	// Alarm broadcast receiver
	private BroadcastReceiver mAlarmReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			// If alarm is ringing, move task to back
			if (action.equals(ALARM_ALERT_ACTION)) {
				isAlarmRinging = true;
				moveTaskToBack(true);
			}

			// If alarm dismissed or snoozed, move task to front and set 'isAlarmRinging' to false
			else if (action.equals(ALARM_DISMISS_ACTION) || action.equals(ALARM_DONE_ACTION) || action.equals(ALARM_SNOOZE_ACTION)) {
				if (isAlarmRinging) {
					activityManager.moveTaskToFront(taskId, 0);
					isAlarmRinging = false;
				}
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Get screen density and calculate disableStatusBar view height
		float screenDensity = getResources().getDisplayMetrics().density;

		float disableStatusBar_height = screenDensity * 70;

		readFromJSON();

		// Activity window flags
		flags = WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
				WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
				WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
				WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

		disableStatusBar = new View(this);

		// DisableStatusBar view parameters
		WindowManager.LayoutParams handleParamsTop = new WindowManager.LayoutParams(
				WindowManager.LayoutParams.MATCH_PARENT,
				(int) disableStatusBar_height,
				WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
						WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
						WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
				PixelFormat.TRANSPARENT);

		handleParamsTop.gravity = Gravity.TOP | Gravity.CENTER;

		// Initialize package manager and components
		packageManager = getPackageManager();
		componentEnabled = PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
		componentDisabled = PackageManager.COMPONENT_ENABLED_STATE_DISABLED;

		cnHome = new ComponentName(this, "org.pyneo.fieldunlock.LockHome");

		// Enable home launcher activity component
		packageManager.setComponentEnabledSetting(cnHome, componentEnabled, PackageManager.DONT_KILL_APP);

		// Get NFC service and adapter
		NfcManager nfcManager = (NfcManager) this.getSystemService(Context.NFC_SERVICE);
		nfcAdapter = nfcManager.getDefaultAdapter();

		// If Android version less than 4.4, createPendingResult for NFC tag discovery
		if (Build.VERSION.SDK_INT < 19) {
			pIntent = PendingIntent.getActivity(
					this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
		}

		taskId = getTaskId();

		// Get window manager service, add the window flags and the disableStatusBar view
		wm = (WindowManager) getApplicationContext().getSystemService(WINDOW_SERVICE);
		this.getWindow().addFlags(flags);
		wm.addView(disableStatusBar, handleParamsTop);

		// If Android version 4.4 or bigger, add translucent status bar flag
		if (Build.VERSION.SDK_INT >= 19) {
			this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
		}

		// To listen for calls, so user can accept or deny a call
		StateListener phoneStateListener = new StateListener();
		TelephonyManager telephonyManager = (TelephonyManager) this.getSystemService(TELEPHONY_SERVICE);
		telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

		activityManager = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);
		vibrator = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

		// Check if device has vibrator and store true/false in var
		vibratorAvailable = vibrator.hasVibrator();

		setContentView(R.layout.activity_lock);

		// When disableStatusBar view touched, consume touch
		disableStatusBar.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				return true;
			}
		});

		// If Android 4.1 or lower or blur is 0 or blurred wallpaper doesn't exist
		// Get default wallpaper and set as background drawable
		if (Build.VERSION.SDK_INT < 17 || blur == 0 || !ImageUtils.doesBlurredWallpaperExist()) {
			WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
			Drawable wallpaperDrawable = wallpaperManager.peekFastDrawable();

			if (wallpaperDrawable != null)
				getWindow().setBackgroundDrawable(wallpaperDrawable);
		}

		// Otherwise, retrieve blurred wallpaper and set as background drawable
		else {
			Drawable blurredWallpaper = ImageUtils.retrieveWallpaperDrawable();

			if (blurredWallpaper != null)
				getWindow().setBackgroundDrawable(blurredWallpaper);

			else {
				WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
				Drawable wallpaperDrawable = wallpaperManager.peekFastDrawable();

				if (wallpaperDrawable != null)
					getWindow().setBackgroundDrawable(wallpaperDrawable);
			}
		}

		RelativeLayout lockRelativeLayout = (RelativeLayout) findViewById(R.id.lockRelativeLayout);

		// If Android version 4.4 or bigger, set layout fits system windows to true
		if (Build.VERSION.SDK_INT >= 19) {
			lockRelativeLayout.setFitsSystemWindows(true);
		}

		cResolver = getContentResolver();

		// Get current system screen timeout and store in int
		systemScreenTimeout = Settings.System.getInt(cResolver, Settings.System.SCREEN_OFF_TIMEOUT,
				TIMEOUT_DELAY);

		// Set lockscreen screen timeout to 15s
		Settings.System.putInt(cResolver, Settings.System.SCREEN_OFF_TIMEOUT, SCREEN_TIMEOUT);

		// Initialize layout items
		time = (TextView) findViewById(R.id.time);
		date = (TextView) findViewById(R.id.date);
		battery = (TextView) findViewById(R.id.battery);
		unlockText = (TextView) findViewById(R.id.unlockText);
		pinInput = (TextView) findViewById(R.id.pinInput);
		updatePIN(null);

		// Set onClick listeners
		((ImageButton) findViewById(R.id.ic_0)).setOnClickListener(this);
		((ImageButton) findViewById(R.id.ic_1)).setOnClickListener(this);
		((ImageButton) findViewById(R.id.ic_2)).setOnClickListener(this);
		((ImageButton) findViewById(R.id.ic_3)).setOnClickListener(this);
		((ImageButton) findViewById(R.id.ic_4)).setOnClickListener(this);
		((ImageButton) findViewById(R.id.ic_5)).setOnClickListener(this);
		((ImageButton) findViewById(R.id.ic_6)).setOnClickListener(this);
		((ImageButton) findViewById(R.id.ic_7)).setOnClickListener(this);
		((ImageButton) findViewById(R.id.ic_8)).setOnClickListener(this);
		((ImageButton) findViewById(R.id.ic_9)).setOnClickListener(this);

		// Initialize calendar and time/date/battery views
		calendar = Calendar.getInstance();

		updateTime();
		updateDate();
		updateBattery(getBatteryLevel());

		// Create intent filter for alarm states and register alarm receiver
		IntentFilter alarmFilter = new IntentFilter(ALARM_ALERT_ACTION);
		alarmFilter.addAction(ALARM_DISMISS_ACTION);
		alarmFilter.addAction(ALARM_SNOOZE_ACTION);
		alarmFilter.addAction(ALARM_DONE_ACTION);
		this.registerReceiver(mAlarmReceiver, alarmFilter);

		// Create an intent filter with time/date/battery changes and register receiver
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(Intent.ACTION_TIME_TICK);
		intentFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
		intentFilter.addAction(Intent.ACTION_DATE_CHANGED);
		this.registerReceiver(mChangeReceiver, intentFilter);

		// Check default launcher, if current package isn't the default one
		// Open the 'Select home app' dialog for user to pick default home launcher
		if (!isMyLauncherDefault()) {
			packageManager.clearPackagePreferredActivities(getPackageName());

			Intent launcherPicker = new Intent();
			launcherPicker.setAction(Intent.ACTION_MAIN);
			launcherPicker.addCategory(Intent.CATEGORY_HOME);
			startActivity(launcherPicker);

			if (launcherPickToast > 0) {
				launcherPickToast -= 1;

				Toast toast = Toast.makeText(getApplicationContext(),
						R.string.toast_launcher_dialog,
						Toast.LENGTH_LONG);
				toast.show();
			}
		}
	}

	// Write content to JSON file
	public void writeToJSON() {
		try {
			BufferedWriter bWrite = new BufferedWriter(new OutputStreamWriter(
					openFileOutput("settings.json", Context.MODE_PRIVATE)));
			bWrite.write(root.toString());
			bWrite.close();

		} catch (FileNotFoundException e) {
			Log.e(TAG, e.toString(), e);
		} catch (IOException e) {
			Log.e(TAG, e.toString(), e);
		}
	}

	public void readFromJSON() {
		// Read from JSON file
		try {
			BufferedReader bRead = new BufferedReader(new InputStreamReader
					(openFileInput("settings.json")));
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
			pin = settings.getString("pin");
			pinLocked = settings.getBoolean("pinLocked");
			blur = settings.getInt("blur");
			tags = settings.getJSONArray("tags");

		} catch (JSONException e) {
			Log.e(TAG, e.toString(), e);
		}
	}


	// Method to update time view
	public void updateTime() {
		calendar = Calendar.getInstance();
		String hourDay = String.valueOf(calendar.get(Calendar.HOUR_OF_DAY));

		String minuteDay = ":" + String.valueOf(calendar.get(Calendar.MINUTE));

		if (minuteDay.length() < 3)
			minuteDay = ":" + "0" + String.valueOf(calendar.get(Calendar.MINUTE));

		String timeFinal = hourDay + minuteDay;

		time.setText(timeFinal);
	}

	// Method to update date view
	public void updateDate() {
		calendar = Calendar.getInstance();
		String weekDay = String.valueOf(calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT,
				Locale.US));
		String month = " " + String.valueOf(calendar.getDisplayName(Calendar.MONTH, Calendar.SHORT,
				Locale.US));
		String dateDay = " " + String.valueOf(calendar.get(Calendar.DATE));
		String dateFinal = weekDay + dateDay + month;

		date.setText(dateFinal);
	}

	// Method to update battery view
	public void updateBattery(int batteryLevel) {
		battery.setText(String.valueOf(batteryLevel) + "% " +
				getResources().getString(R.string.battery_text));

		// Set text color depending on battery level
		if (batteryLevel > 15)
			battery.setTextColor(getResources().getColor(R.color.white_90));

		else
			battery.setTextColor(getResources().getColor(R.color.light_red));
	}

	// Returns the current battery level
	public int getBatteryLevel() {
		Intent batteryIntent = getApplicationContext().registerReceiver(null,
				new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		int level = -1;
		try {
			level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
		} catch (NullPointerException e) {
			Log.e(TAG, e.toString(), e);
		}
		return level;
	}

	// Method to check whether the package is the default home launcher or not
	public boolean isMyLauncherDefault() {
		final IntentFilter launcherFilter = new IntentFilter(Intent.ACTION_MAIN);
		launcherFilter.addCategory(Intent.CATEGORY_HOME);

		List<IntentFilter> filters = new ArrayList<IntentFilter>();
		filters.add(launcherFilter);

		final String myPackageName = getPackageName();
		List<ComponentName> activities = new ArrayList<ComponentName>();

		packageManager.getPreferredActivities(filters, activities, "org.pyneo.fieldunlock");

		for (ComponentName activity : activities) {
			if (myPackageName.equals(activity.getPackageName())) {
				return true;
			}
		}

		return false;
	}

	public void updatePIN(String s) {
		if (s == null) {
			s = "";
		}
		pinEntered = s;
		if (s.length() > 0) {
			s = "FieldUnlock" + s;
		}
		s = Long.toString(s.hashCode() & 0x0ffffffffL, 16);
		while (s.length() < 8) {
			s = "0" + s;
		}
		pinInput.setText(s);
	}

	// Method called each time the user presses a keypad button
	public void enterPIN(char c) {
		if (pinLocked) {
			return;
		}
		updatePIN(pinEntered + c);

		// If correct PIN entered, reset pinEntered, remove handle callbacks and messages,
		// Set home launcher activity component disabled and finish
		if (pinEntered.equals(pin)) {
			updatePIN(null);

			mHandler.removeCallbacksAndMessages(null);

			packageManager.setComponentEnabledSetting(cnHome, componentDisabled,
					PackageManager.DONT_KILL_APP);

			finish();
			overridePendingTransition(0, 0);
		}
		// If incorrect PIN entered, reset drawable and pinEntered, lower pinAttempts
		// Vibrate and display 'Wrong PIN. Try again' for 1s
		if (pinEntered.length() > 10) {
			if (pinAttempts > 0) {
				pinAttempts -= 1;

				updatePIN(null);

				unlockText.postDelayed(new Runnable() {
					@Override
					public void run() {
						if (pinAttempts >= 0) {
							if (nfcAdapter != null) {
								if (nfcAdapter.isEnabled())
									unlockText.setText("");

								else {
									unlockText.setText(getResources().getString(
											R.string.scan_to_unlock_nfc_off));
								}
							} else {
								unlockText.setText(getResources().getString(
										R.string.scan_to_unlock_nfc_off));
							}
						}
					}
				}, 1000);

				if (vibratorAvailable)
					vibrator.vibrate(250);

				unlockText.setText(getResources().getString(R.string.wrong_pin));
			}

			// 0 attempts left, vibrate, reset pinEntered and pinAttempts,
			// Set pinLocked to true, store and post reset PIN keypad runnable in 30s
			else {
				if (vibratorAvailable)
					vibrator.vibrate(500);

				if (nfcAdapter != null) {
					if (nfcAdapter.isEnabled())
						unlockText.setText(getResources().getString(R.string.pin_locked));

					else
						unlockText.setText(getResources().getString(R.string.pin_locked_nfc_off));
				} else
					unlockText.setText(getResources().getString(R.string.pin_locked_nfc_off));


				updatePIN(null);

				pinLocked = true;

				pinAttempts = 5;

				try {
					settings.put("pinLocked", true);
				} catch (JSONException e) {
					Log.e(TAG, e.toString(), e);
				}

				writeToJSON();

				mHandler.postDelayed(pinLockedRunnable, PIN_LOCKED_RUNNABLE_DELAY);
			}
		}
	}

	@Override
	public void onClick(View v) {
		// take a pic if clicked
		try {
			unattendedPic.capture(this);
		}
		catch (Exception e) {
			Log.e(TAG, e.toString(), e);
		}
		// If PIN keypad button pressed, add pressed number to pinEntered and call checkPin method
		if (v.getId() == R.id.ic_0) {
			enterPIN('0');
		} else if (v.getId() == R.id.ic_1) {
			enterPIN('1');
		} else if (v.getId() == R.id.ic_2) {
			enterPIN('2');
		} else if (v.getId() == R.id.ic_3) {
			enterPIN('3');
		} else if (v.getId() == R.id.ic_4) {
			enterPIN('4');
		} else if (v.getId() == R.id.ic_5) {
			enterPIN('5');
		} else if (v.getId() == R.id.ic_6) {
			enterPIN('6');
		} else if (v.getId() == R.id.ic_7) {
			enterPIN('7');
		} else if (v.getId() == R.id.ic_8) {
			enterPIN('8');
		} else if (v.getId() == R.id.ic_9) {
			enterPIN('9');
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);

		if (!hasFocus && !isPhoneCalling) {
			activityManager.moveTaskToFront(taskId, 0);
			Intent closeDialog = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
			sendBroadcast(closeDialog);
		}
	}

	// If the user wants to leave the app like pressing recent apps button, move task to queue front
	@Override
	protected void onUserLeaveHint() {
		super.onUserLeaveHint();

		activityManager.moveTaskToFront(taskId, 0);
	}

	// Empty methods required for GestureListener
	public boolean onDown(MotionEvent e) {
		return false;
	}

	public void onShowPress(MotionEvent e) {
	}

	public boolean onSingleTapUp(MotionEvent e) {
		return false;
	}

	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		return false;
	}

	public void onLongPress(MotionEvent e) {
	}

	// If window touched, reset brightness and if touched outside window, move task to queue front
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
			activityManager.moveTaskToFront(taskId, 0);

			return true;
		}
		return true;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
			activityManager.moveTaskToFront(taskId, 0);
			return true;
		}
		return true;
	}

	@Override
	public void onBackPressed() {
		// If back button pressed, do nothing
	}

	// Added just to be safe; home key event detected - move task to queue front
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_HOME) {
			activityManager.moveTaskToFront(taskId, 0);

			return true;
		}

		return false;
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		if (event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
			activityManager.moveTaskToFront(taskId, 0);

			return true;
		}

		return false;
	}

	// Call state listener
	class StateListener extends PhoneStateListener {
		@Override
		public void onCallStateChanged(int state, String incomingNumber) {
			super.onCallStateChanged(state, incomingNumber);

			switch (state) {
				// If state is ringing, set isPhoneCalling var to true and move task to back
				case TelephonyManager.CALL_STATE_RINGING:
					isPhoneCalling = true;
					moveTaskToBack(true);
					break;

				// If state is offhook, set isPhoneCalling var to true and move task to back
				case TelephonyManager.CALL_STATE_OFFHOOK:
					isPhoneCalling = true;
					moveTaskToBack(true);
					break;

				// If call stopped or idle and isPhoneCalling var is true, move task to queue front
				case TelephonyManager.CALL_STATE_IDLE:
					if (isPhoneCalling) {
						activityManager.moveTaskToFront(taskId, 0);
						isPhoneCalling = false;
					}
					break;
			}
		}
	}

	// If activity resumed, enable NFC tag discovery
	@Override
	protected void onResume() {
		super.onResume();

		activityManager.moveTaskToFront(taskId, 0);

		// Re-enable home launcher activity component
		if (packageManager != null) {
			packageManager.setComponentEnabledSetting(cnHome, componentEnabled,
					PackageManager.DONT_KILL_APP);
		}

		if (nfcAdapter != null) {
			if (nfcAdapter.isEnabled()) {
				// If Android version lower than 4.4, use foreground dispatch method with tech filters
				if (Build.VERSION.SDK_INT < 19) {
					nfcAdapter.enableForegroundDispatch(this, pIntent,
							new IntentFilter[]{new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)},
							new String[][]{
									new String[]{"android.nfc.tech.MifareClassic"},
									new String[]{"android.nfc.tech.MifareUltralight"},
									new String[]{"android.nfc.tech.NfcA"},
									new String[]{"android.nfc.tech.NfcB"},
									new String[]{"android.nfc.tech.NfcF"},
									new String[]{"android.nfc.tech.NfcV"},
									new String[]{"android.nfc.tech.Ndef"},
									new String[]{"android.nfc.tech.IsoDep"},
									new String[]{"android.nfc.tech.NdefFormatable"},
							}
					);
				}

				// If Android version 4.4 or bigger, use enableReaderMode method
				else {
					nfcAdapter.enableReaderMode(this, new NfcAdapter.ReaderCallback() {

						@Override
						public void onTagDiscovered(Tag tag) {
							// Get tag id and convert to readable string
							byte[] tagID = tag.getId();
							String tagDiscovered = bytesToHex(tagID);

							if (!tagDiscovered.equals("")) {
								// Loop through added NFC tags
								for (int i = 0; i < tags.length(); i++) {
									try {
										// When tag discovered id is equal to one of the stored tags id
										if (tagDiscovered.equals(tags.getJSONObject(i).getString("tagID"))) {
											// Reset tagDiscovered string, set pinLocked false and store
											// Remove handle callback and messages
											// Disable home launcher activity component and finish

											tagDiscovered = "";

											pinLocked = false;
											settings.put("pinLocked", false);

											writeToJSON();

											mHandler.removeCallbacksAndMessages(null);

											packageManager.setComponentEnabledSetting(cnHome,
													componentDisabled, PackageManager.DONT_KILL_APP);

											finish();
											overridePendingTransition(0, 0);
											return;
										}

									} catch (JSONException e) {
										Log.e(TAG, e.toString(), e);
									}
								}

								// When tag discovered id is not correct
								// Vibrate, display 'Wrong NFC Tag' and after 1.5s switch back to normal
								unlockText.postDelayed(new Runnable() {
									@Override
									public void run() {
										if (!pinLocked) {
											runOnUiThread(new Runnable() {
												@Override
												public void run() {
													unlockText.setText("");
												}
											});
										} else {
											runOnUiThread(new Runnable() {
												@Override
												public void run() {
													unlockText.setText(getResources().getString(
															R.string.pin_locked));
												}
											});
										}
									}
								}, 1500);

								if (vibratorAvailable)
									vibrator.vibrate(250);

								runOnUiThread(new Runnable() {
									@Override
									public void run() {
										unlockText.setText(getResources().getString(R.string.wrong_tag));
									}
								});
							}
						}
					}, NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS | NfcAdapter.FLAG_READER_NFC_A |
							NfcAdapter.FLAG_READER_NFC_B | NfcAdapter.FLAG_READER_NFC_BARCODE |
							NfcAdapter.FLAG_READER_NFC_F | NfcAdapter.FLAG_READER_NFC_V |
							NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
				}
			}

			// If NFC is disabled
			else {
				if (pinLocked)
					unlockText.setText(getResources().getString(R.string.pin_locked_nfc_off));

				else
					unlockText.setText(getResources().getString(R.string.scan_to_unlock_nfc_off));
			}
		}

		// If NFC is absent
		else {
			if (pinLocked)
				unlockText.setText(getResources().getString(R.string.pin_locked_nfc_off));

			else
				unlockText.setText(getResources().getString(R.string.scan_to_unlock_nfc_off));
		}

		// Check default launcher, if current package isn't the default one
		// Open the 'Select home app' dialog for user to pick default home launcher
		if (!isMyLauncherDefault()) {
			packageManager.clearPackagePreferredActivities(getPackageName());

			Intent launcherPicker = new Intent();
			launcherPicker.setAction(Intent.ACTION_MAIN);
			launcherPicker.addCategory(Intent.CATEGORY_HOME);
			startActivity(launcherPicker);

			if (launcherPickToast > 0) {
				launcherPickToast -= 1;

				Toast toast = Toast.makeText(getApplicationContext(),
						R.string.toast_launcher_dialog,
						Toast.LENGTH_LONG);
				toast.show();
			}
		}
	}

	// If activity offline or paused, disable NFC tag discovery and clear flag that kept screen on
	@Override
	protected void onPause() {
		super.onPause();

		if (Build.VERSION.SDK_INT < 19) {
			if (nfcAdapter != null)
				nfcAdapter.disableForegroundDispatch(this);
		} else if (Build.VERSION.SDK_INT >= 19) {
			if (nfcAdapter != null)
				nfcAdapter.disableReaderMode(this);
		}
		unattendedPic.stop();
	}

	// If activity finished and stopped, disable home launcher activity component,
	// Remove disableStatusBar view, clear window flags, unregister receiver, release the camera,
	// Set pinLocked false and store, reset brightness and screen timeout and remove handler callbacks
	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (packageManager != null) {
			packageManager.setComponentEnabledSetting(cnHome, componentDisabled,
					PackageManager.DONT_KILL_APP);
		}

		if (wm != null) {
			try {
				wm.removeView(disableStatusBar);

			} catch (IllegalArgumentException e) {
				Log.e(TAG, e.toString(), e);
			}
		}

		this.getWindow().clearFlags(flags);

		try {
			unregisterReceiver(mChangeReceiver);
			unregisterReceiver(mAlarmReceiver);

		} catch (IllegalArgumentException e) {
			Log.e(TAG, e.toString(), e);
		}

		if (Build.VERSION.SDK_INT >= 19) {
			this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
		}

		isPhoneCalling = false;

		try {
			settings.put("pinLocked", false);

		} catch (JSONException e) {
			Log.e(TAG, e.toString(), e);
		}

		writeToJSON();

		// Reset screen timeout to initial
		if (cResolver != null && systemScreenTimeout != -1)
			Settings.System.putInt(cResolver, Settings.System.SCREEN_OFF_TIMEOUT, systemScreenTimeout);

		// Remove all handler callbacks
		mHandler.removeCallbacksAndMessages(null);
	}

	// Tag discovery for Android versions lower than 4.4
	// If correct NFC tag detected, unlock device
	// Else, display wrong tag detected and vibrate
	@Override
	protected void onNewIntent(Intent intent) {
		String action = intent.getAction();

		if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
			Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

			// Get tag id and convert to readable string
			byte[] tagID = tag.getId();
			String tagDiscovered = bytesToHex(tagID);

			if (!tagDiscovered.equals("")) {
				// Loop through added NFC tags
				for (int i = 0; i < tags.length(); i++) {
					// When tag discovered id is equal to one of the stored tags id
					try {
						if (tagDiscovered.equals(tags.getJSONObject(i).getString("tagID"))) {
							// Reset tagDiscovered string, set pinLocked false and store
							// Remove handle callback and messages
							// Disable home launcher activity component and finish

							tagDiscovered = "";

							pinLocked = false;
							settings.put("pinLocked", false);

							writeToJSON();

							mHandler.removeCallbacksAndMessages(null);

							packageManager.setComponentEnabledSetting(cnHome, componentDisabled,
									PackageManager.DONT_KILL_APP);

							finish();
							overridePendingTransition(0, 0);
							return;
						}

					} catch (JSONException e) {
						Log.e(TAG, e.toString(), e);
					}
				}

				// If wrong NFC tag id detected
				// Vibrate, display 'Wrong NFC Tag' and after 1.5s switch back to normal
				unlockText.postDelayed(new Runnable() {
					@Override
					public void run() {
						if (!pinLocked)
							unlockText.setText("");

						else
							unlockText.setText(getResources().getString(R.string.pin_locked));
					}
				}, 1500);

				if (vibratorAvailable)
					vibrator.vibrate(250);

				unlockText.setText(getResources().getString(R.string.wrong_tag));
			}
		}
	}

	// Char array for bytes to hex string method
	final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

	// Bytes to hex string method
	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int i = 0; i < bytes.length; i++) {
			int x = bytes[i] & 0xFF;
			hexChars[i * 2] = hexArray[x >>> 4];
			hexChars[i * 2 + 1] = hexArray[x & 0x0F];
		}
		return new String(hexChars);
	}

	void send(File f) {
		Log.i(TAG, "send f=" + f);
	}
}
