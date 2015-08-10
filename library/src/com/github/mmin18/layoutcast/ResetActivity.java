package com.github.mmin18.layoutcast;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.TextView;

/**
 * Created by mmin18 on 8/8/15.
 */
public class ResetActivity extends Activity {
	private static final Handler HANDLER = new Handler(Looper.getMainLooper());
	private static final long RESET_WAIT = 2000;
	private long createTime;
	private boolean ready;
	private int back;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		TextView tv = new TextView(this);
		tv.setGravity(Gravity.CENTER);
		tv.setText("Cast DEX in 2 second..");
		setContentView(tv);
		createTime = SystemClock.uptimeMillis();

		ready = getIntent().getBooleanExtra("reset", false);
		if (ready) {
			reset();
		}
	}

	@Override
	protected void onDestroy() {
		HANDLER.removeCallbacks(reset);
		super.onDestroy();
	}

	public void reset() {
		ready = true;
		HANDLER.removeCallbacks(reset);
		long d = SystemClock.uptimeMillis() - createTime;
		if (d > RESET_WAIT) {
			HANDLER.post(reset);
		} else {
			HANDLER.postDelayed(reset, RESET_WAIT - d);
		}
	}

	private final Runnable reset = new Runnable() {
		@Override
		public void run() {
			android.os.Process.killProcess(Process.myPid());
		}
	};

	@Override
	public void onBackPressed() {
		if (back++ > 0) {
			if (ready) {
				reset.run();
			}
			super.onBackPressed();
		}
	}
}
