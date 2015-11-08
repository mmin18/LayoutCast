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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

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
		String vmVersion = System.getProperty("java.vm.version");
		pathch(dir,context,app,"Hack.apk");
		if (dex.length() > 0) {
			File f = new File(dir, "dex.apk");
			dex.renameTo(f);
			File opt = new File(dir, "opt");
			opt.mkdirs();
			if (vmVersion != null && vmVersion.startsWith("2")) {
				ArtUtils.overrideClassLoader(app.getClassLoader(), f, opt);
			} else {
				File fnew = new File(dir, "sam.dex");
				f.renameTo(fnew);
				ArtUtils.overrideClassLoader(app.getClassLoader(), fnew, opt);
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
	public static boolean pathch(File dir,Context context,Application app,String fname){
		boolean result = true;
		File fout = new File(dir, fname);
		try {
			if(!fout.exists()) {
				if (!fout.getParentFile().exists()){
					fout.getParentFile().mkdir();
				}
				fout.createNewFile();
			}
			FileOutputStream fileOutputStream = null;
			fileOutputStream = new FileOutputStream(fout);
			InputStream in = context.getAssets().open(fname);
			byte [] buffer = new byte[1024];
			int len = -1;
			while ((len = in.read(buffer)) != -1){
				fileOutputStream.write(buffer,0,len);
			}
			in.close();
			fileOutputStream.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		File opt = new File(dir, "opt");
		opt.mkdirs();
		ArtUtils.overrideClassLoader(app.getClassLoader(), fout, opt);
		return result;
	}
}
