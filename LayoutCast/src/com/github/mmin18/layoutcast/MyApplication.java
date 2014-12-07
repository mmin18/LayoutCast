package com.github.mmin18.layoutcast;

import android.app.Application;

import com.github.mmin18.layoutcast.inflater.BootInflater;

public class MyApplication extends Application {

	@Override
	public void onCreate() {
		super.onCreate();

		BootInflater.initApplication(this);
	}

}
