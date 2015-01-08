package com.github.mmin18.layoutcast.test;

import android.app.Application;

import com.github.mmin18.layoutcast.LayoutCast;

public class MyApplication extends Application {

	@Override
	public void onCreate() {
		super.onCreate();

		if (BuildConfig.DEBUG) {
			LayoutCast.init(this);
		}
	}

}
