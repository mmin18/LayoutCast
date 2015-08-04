package com.github.mmin18.layoutcast.gradle;

import android.app.Application;

import com.github.mmin18.layoutcast.LayoutCast;

/**
 * Created by mmin18 on 8/4/15.
 */
public class MyApplication extends Application {
	@Override
	public void onCreate() {
		super.onCreate();

		if (BuildConfig.DEBUG) {
			LayoutCast.init(this);
		}
	}
}
