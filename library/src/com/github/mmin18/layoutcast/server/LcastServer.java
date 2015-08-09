package com.github.mmin18.layoutcast.server;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.util.Log;

import com.github.mmin18.layoutcast.LayoutCast;
import com.github.mmin18.layoutcast.context.OverrideContext;
import com.github.mmin18.layoutcast.util.EmbedHttpServer;
import com.github.mmin18.layoutcast.util.ResUtils;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * GET /packagename (get the application package name)<br>
 * POST /pushres (upload resources file)<br>
 * PUT /pushres (upload resources file)<br>
 * POST /lcast (cast to all activities)<br>
 * POST /reset (reset all activities)<br>
 * GET /ids.xml<br>
 * GET /public.xml<br>
 *
 * @author mmin18
 */
public class LcastServer extends EmbedHttpServer {
	public static final int PORT_FROM = 41128;
	public static Application app;
	final Context context;
	File latestPushFile;

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
		if (path.equalsIgnoreCase("/appstate")) {
			response.setContentTypeText();
			response.write(String.valueOf(OverrideContext.getApplicationState()).getBytes("utf-8"));
			return;
		}
		if ("/vmversion".equalsIgnoreCase(path)) {
			final String vmVersion = System.getProperty("java.vm.version");
			response.setContentTypeText();
			if (vmVersion == null) {
				response.write('0');
			} else {
				response.write(vmVersion.getBytes("utf-8"));
			}
			return;
		}
		if ("/launcher".equalsIgnoreCase(path)) {
			PackageManager pm = app.getPackageManager();
			Intent i = new Intent(Intent.ACTION_MAIN);
			i.addCategory(Intent.CATEGORY_LAUNCHER);
			i.setPackage(app.getPackageName());
			ResolveInfo ri = pm.resolveActivity(i, 0);
			i = new Intent(Intent.ACTION_MAIN);
			i.addCategory(Intent.CATEGORY_LAUNCHER);
			response.setContentTypeText();
			response.write(ri.activityInfo.name.getBytes("utf-8"));
			return;
		}
		if (("post".equalsIgnoreCase(method) || "put".equalsIgnoreCase(method))
				&& path.equalsIgnoreCase("/pushres")) {
			File dir = new File(context.getCacheDir(), "lcast");
			dir.mkdir();
			File dex = new File(dir, "dex.ped");
			File file;
			if (dex.length() > 0) {
				file = new File(dir, "res.ped");
			} else {
				file = new File(dir, Integer.toHexString((int) (System
						.currentTimeMillis() / 100) & 0xfff) + ".apk");
			}
			FileOutputStream fos = new FileOutputStream(file);
			byte[] buf = new byte[4096];
			int l;
			while ((l = input.read(buf)) != -1) {
				fos.write(buf, 0, l);
			}
			fos.close();
			latestPushFile = file;
			response.setStatusCode(201);
			Log.d("lcast", "lcast resources file received (" + file.length()
					+ " bytes): " + file);
			return;
		}
		if (("post".equalsIgnoreCase(method) || "put".equalsIgnoreCase(method))
				&& path.equalsIgnoreCase("/pushdex")) {
			File dir = new File(context.getCacheDir(), "lcast");
			dir.mkdir();
			File file = new File(dir, "dex.ped");
			FileOutputStream fos = new FileOutputStream(file);
			byte[] buf = new byte[4096];
			int l;
			while ((l = input.read(buf)) != -1) {
				fos.write(buf, 0, l);
			}
			fos.close();
			response.setStatusCode(201);
			Log.d("lcast", "lcast dex file received (" + file.length() + " bytes)");
			return;
		}
		if ("/pcast".equalsIgnoreCase(path)) {
			LayoutCast.restart(false);
			response.setStatusCode(200);
			return;
		}
		if ("/lcast".equalsIgnoreCase(path)) {
			File dir = new File(context.getCacheDir(), "lcast");
			File dex = new File(dir, "dex.ped");
			if (dex.length() > 0) {
				if (latestPushFile != null) {
					File f = new File(dir, "res.ped");
					latestPushFile.renameTo(f);
				}
				Log.i("lcast", "cast with dex changes, need to restart the process (activity stack will be reserved)");
				boolean b = LayoutCast.restart(true);
				response.setStatusCode(b ? 200 : 500);
			} else {
				Resources res = ResUtils.getResources(app, latestPushFile);
				OverrideContext.setGlobalResources(res);
				response.setStatusCode(200);
				response.write(String.valueOf(latestPushFile).getBytes("utf-8"));
				Log.i("lcast", "cast with only res changes, just recreate the running activity.");
			}
			return;
		}
		if ("/reset".equalsIgnoreCase(path)) {
			OverrideContext.setGlobalResources(null);
			response.setStatusCode(200);
			response.write("OK".getBytes("utf-8"));
			return;
		}
		if ("/ids.xml".equalsIgnoreCase(path)) {
			String Rn = app.getPackageName() + ".R";
			Class<?> Rclazz = app.getClassLoader().loadClass(Rn);
			String str = new IdProfileBuilder(context.getResources())
					.buildIds(Rclazz);
			response.setStatusCode(200);
			response.setContentTypeText();
			response.write(str.getBytes("utf-8"));
			return;
		}
		if ("/public.xml".equalsIgnoreCase(path)) {
			String Rn = app.getPackageName() + ".R";
			Class<?> Rclazz = app.getClassLoader().loadClass(Rn);
			String str = new IdProfileBuilder(context.getResources())
					.buildPublic(Rclazz);
			response.setStatusCode(200);
			response.setContentTypeText();
			response.write(str.getBytes("utf-8"));
			return;
		}
		if ("/apkinfo".equalsIgnoreCase(path)) {
			ApplicationInfo ai = app.getApplicationInfo();
			File apkFile = new File(ai.sourceDir);
			JSONObject result = new JSONObject();
			result.put("size", apkFile.length());
			result.put("lastModified", apkFile.lastModified());

			FileInputStream fis = new FileInputStream(apkFile);
			MessageDigest md5 = MessageDigest.getInstance("MD5");
			byte[] buf = new byte[4096];
			int l;
			while ((l = fis.read(buf)) != -1) {
				md5.update(buf, 0, l);
			}
			fis.close();

			result.put("md5", byteArrayToHex(md5.digest()));
			response.setStatusCode(200);
			response.setContentTypeJson();
			response.write(result.toString().getBytes("utf-8"));
			return;
		}
		if ("/apkraw".equalsIgnoreCase(path)) {
			ApplicationInfo ai = app.getApplicationInfo();
			FileInputStream fis = new FileInputStream(ai.sourceDir);
			response.setStatusCode(200);
			response.setContentTypeBinary();
			byte[] buf = new byte[4096];
			int l;
			while ((l = fis.read(buf)) != -1) {
				response.write(buf, 0, l);
			}
			return;
		}
		if (path.startsWith("/fileinfo/")) {
			ApplicationInfo ai = app.getApplicationInfo();
			File apkFile = new File(ai.sourceDir);

			JarFile jarFile = new JarFile(apkFile);
			JarEntry je = jarFile.getJarEntry(path.substring("/fileinfo/".length()));
			InputStream ins = jarFile.getInputStream(je);
			MessageDigest md5 = MessageDigest.getInstance("MD5");
			byte[] buf = new byte[4096];
			int l, n = 0;
			while ((l = ins.read(buf)) != -1) {
				md5.update(buf, 0, l);
				n += l;
			}
			ins.close();
			jarFile.close();

			JSONObject result = new JSONObject();
			result.put("size", n);
			result.put("time", je.getTime());
			result.put("crc", je.getCrc());
			result.put("md5", byteArrayToHex(md5.digest()));

			response.setStatusCode(200);
			response.setContentTypeJson();
			response.write(result.toString().getBytes("utf-8"));
			return;
		}
		if (path.startsWith("/fileraw/")) {
			ApplicationInfo ai = app.getApplicationInfo();
			File apkFile = new File(ai.sourceDir);

			JarFile jarFile = new JarFile(apkFile);
			JarEntry je = jarFile.getJarEntry(path.substring("/fileraw/".length()));
			InputStream ins = jarFile.getInputStream(je);

			response.setStatusCode(200);
			response.setContentTypeBinary();
			byte[] buf = new byte[4096];
			int l;
			while ((l = ins.read(buf)) != -1) {
				response.write(buf, 0, l);
			}
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
			f.delete();
		} else if (f.getName().endsWith(".apk")) {
			f.delete();
		}
	}

	private static String byteArrayToHex(byte[] a) {
		StringBuilder sb = new StringBuilder(a.length * 2);
		for (byte b : a)
			sb.append(String.format("%02x", b & 0xff));
		return sb.toString();
	}

}
