package com.github.mmin18.layoutcast;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.github.mmin18.layoutcast.context.OverrideContext;
import com.github.mmin18.layoutcast.inflater.BootInflater;
import com.github.mmin18.layoutcast.server.LcastServer;
import com.github.mmin18.layoutcast.util.ArtUtils;
import com.github.mmin18.layoutcast.util.ResUtils;

import java.io.File;

public class LayoutCast {

	private static boolean inited;
	private static Context appContext;

	public static void init(Context context) {
		if (inited)
			return;

		Application app = context instanceof Application ? (Application) context
				: (Application) context.getApplicationContext();
		appContext = app;

		LcastServer.cleanCache(app);
		File dir = new File(app.getCacheDir(), "lcast");
		File dex = new File(dir, "dex.ped");
		File res = new File(dir, "res.ped");

		if (dex.length() > 0) {
			File f = new File(dir, "dex.apk");
			dex.renameTo(f);
			File opt = new File(dir, "opt");
			opt.mkdirs();
			final String vmVersion = System.getProperty("java.vm.version");
			if (vmVersion != null && vmVersion.startsWith("2")) {
				ArtUtils.overrideClassLoader(app.getClassLoader(), f, opt);
			} else {
				Log.e("lcast", "cannot cast dex to daivik, only support ART now.");
			}
		}

		OverrideContext.initApplication(app);
		BootInflater.initApplication(app);

		if (res.length() > 0) {
			try {
				File f = new File(dir, "res.apk");
				res.renameTo(f);
				Resources r = ResUtils.getResources(app, f);
				OverrideContext.setGlobalResources(r);
			} catch (Exception e) {
				Log.e("lcast", "fail to cast " + res, e);
			}
		}

		LcastServer.app = app;
		LcastServer.start(app);

		inited = true;
	}

	public static boolean restart(boolean confirm) {
		Context top = OverrideContext.getTopActivity();
		if (top instanceof ResetActivity) {
			((ResetActivity) top).reset();
			return true;
		} else {
			Context ctx = appContext;
			try {
				Intent i = new Intent(ctx, ResetActivity.class);
				i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				i.putExtra("reset", confirm);
				ctx.startActivity(i);
				return true;
			} catch (Exception e) {
				final String str = "Fail to cast dex, make sure you have <Activity android:name=\"" + ResetActivity.class.getName() + "\"/> registered in AndroidManifest.xml";
				Log.e("lcast", str);
				new Handler(Looper.getMainLooper()).post(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(appContext, str, Toast.LENGTH_LONG).show();
					}
				});
				return false;
			}
		}
	}
}
