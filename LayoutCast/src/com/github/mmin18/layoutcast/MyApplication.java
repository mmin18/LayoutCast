package com.github.mmin18.layoutcast;

import android.app.Application;

public class MyApplication extends Application {

	@Override
	public void onCreate() {
		super.onCreate();

		if (BuildConfig.DEBUG) {
			LayoutCast.init(this);
		}
	}

}
