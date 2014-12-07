package com.github.mmin18.layoutcast;

import android.app.Application;

import com.github.mmin18.layoutcast.inflater.BootInflater;
import com.github.mmin18.layoutcast.server.LcastServer;

public class MyApplication extends Application {

	@Override
	public void onCreate() {
		super.onCreate();

		BootInflater.initApplication(this);
		LcastServer.cleanCache(this);
		LcastServer.start(this);
	}

}
