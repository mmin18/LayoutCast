package com.github.mmin18.layoutcast;

import android.app.Application;
import android.content.Context;

import com.github.mmin18.layoutcast.context.OverrideContext;
import com.github.mmin18.layoutcast.inflater.BootInflater;
import com.github.mmin18.layoutcast.server.LcastServer;

public class LayoutCast {

	private static boolean inited;

	public static void init(Context context) {
		if (inited)
			return;

		Application app = context instanceof Application ? (Application) context
				: (Application) context.getApplicationContext();

		OverrideContext.initApplication(app);
		BootInflater.initApplication(app);

		LcastServer.app = app;
		LcastServer.cleanCache(app);
		LcastServer.start(app);

		inited = true;
	}

}
