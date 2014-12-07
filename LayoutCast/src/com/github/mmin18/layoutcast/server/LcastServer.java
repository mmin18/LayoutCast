package com.github.mmin18.layoutcast.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;

import android.content.Context;
import android.util.Log;

import com.github.mmin18.layoutcast.util.EmbedHttpServer;

public class LcastServer extends EmbedHttpServer {
	public static final int PORT_FROM = 41128;
	final Context context;

	private LcastServer(Context ctx, int port) {
		super(port);
		context = ctx;
	}

	@Override
	protected void handle(String method, String path,
			HashMap<String, String> headers, InputStream input,
			ResponseOutputStream response) throws Exception {
		if (path.equalsIgnoreCase("/packagename")) {
			response.setContentTypeText();
			response.write(context.getPackageName().getBytes("utf-8"));
			return;
		}
		if (("post".equalsIgnoreCase(method) || "put".equalsIgnoreCase(method))
				&& path.equalsIgnoreCase("/pushres")) {
			File dir = new File(context.getCacheDir(), "lcast");
			dir.mkdir();
			File file = new File(dir, Integer.toHexString((int) (System
					.currentTimeMillis() / 100) & 0xfff) + ".apk");
			FileOutputStream fos = new FileOutputStream(file);
			byte[] buf = new byte[4096];
			int l;
			while ((l = input.read(buf)) != -1) {
				fos.write(buf, 0, l);
			}
			fos.close();
			response.setStatusCode(201);
			Log.d("lcast", "lcast resources file received (" + file.length()
					+ " bytes): " + file);
			return;
		}
		super.handle(method, path, headers, input, response);
	}

	private static LcastServer runningServer;

	public static void start(Context ctx) {
		if (runningServer != null) {
			Log.d("lcast", "lcast server is already running");
			return;
		}

		for (int i = 0; i < 100; i++) {
			LcastServer s = new LcastServer(ctx, PORT_FROM + i);
			try {
				s.start();
				runningServer = s;
				Log.d("lcast", "lcast server running on port "
						+ (PORT_FROM + i));
				break;
			} catch (Exception e) {
			}
		}
	}

	public static void cleanCache(Context ctx) {
		File dir = new File(ctx.getCacheDir(), "lcast");
		File[] fs = dir.listFiles();
		if (fs != null) {
			for (File f : fs) {
				rm(f);
			}
		}
	}

	private static void rm(File f) {
		if (f.isDirectory()) {
			for (File ff : f.listFiles()) {
				rm(ff);
			}
		} else {
			f.delete();
		}
	}
}
