package org.pyneo.fieldunlock;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;


// Ninja 'Home default launcher' when lockscreen is active
public class LockHome extends Activity implements Util {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		finish();
		overridePendingTransition(0, 0);
	}
}
