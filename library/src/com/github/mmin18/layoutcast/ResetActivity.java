package com.github.mmin18.layoutcast;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.view.Gravity;
import android.widget.TextView;

/**
 * Created by mmin18 on 8/8/15.
 */
public class ResetActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		TextView tv = new TextView(this);
		tv.setGravity(Gravity.CENTER);
		tv.setText("Cast DEX in 2 second..");
		setContentView(tv);
	}

	@Override
	protected void onPostResume() {
		super.onPostResume();

		new Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				android.os.Process.killProcess(Process.myPid());
			}
		}, 2000);
	}
}
