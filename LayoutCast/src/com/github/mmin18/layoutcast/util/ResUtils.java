package com.github.mmin18.layoutcast.util;

import java.io.File;
import java.lang.reflect.Field;
import java.util.HashMap;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.SparseArray;
import android.view.ContextThemeWrapper;

public class ResUtils {

	public static Resources loadResources(Context ctx, File file)
			throws Exception {
		AssetManager am = (AssetManager) AssetManager.class.newInstance();
		am.getClass().getMethod("addAssetPath", String.class)
				.invoke(am, file.getAbsolutePath());

		Resources superRes = ctx.getResources();
		Resources res = new Resources(am, superRes.getDisplayMetrics(),
				superRes.getConfiguration());
		return res;
	}

	/**
	 * @param res
	 *            set null to reset original resources
	 */
	public static void overrideContext(Context orig, Resources res)
			throws Exception {
		ContextWrapper cw = (ContextWrapper) orig;
		Context base = cw.getBaseContext();
		if (base instanceof ResContext) {
			base = ((ResContext) base).getBaseContext();
		}
		Field fBase = ContextWrapper.class.getDeclaredField("mBase");
		fBase.setAccessible(true);
		if (res == null) {
			fBase.set(orig, base);
		} else {
			ResContext ctx = new ResContext(base, res);
			fBase.set(orig, ctx);
		}

		Field fResources = ContextThemeWrapper.class
				.getDeclaredField("mResources");
		fResources.setAccessible(true);
		fResources.set(orig, null);

		Field fTheme = ContextThemeWrapper.class.getDeclaredField("mTheme");
		fTheme.setAccessible(true);
		fTheme.set(orig, null);
	}

	private static HashMap<String, Integer> layoutIds;
	private static SparseArray<String> layoutNames;

	public static String getLayoutName(Context ctx, Resources res, int id)
			throws Exception {
		if (layoutIds == null || layoutNames == null) {
			HashMap<String, Integer> map = new HashMap<String, Integer>();
			SparseArray<String> map2 = new SparseArray<String>();

			String rClazz = ctx.getPackageName() + ".R.layout";
			Class<?> r = ctx.getClassLoader().loadClass(rClazz);
			for (Field f : r.getDeclaredFields()) {
				if (java.lang.reflect.Modifier.isStatic(f.getModifiers())
						&& f.getType() == Integer.TYPE) {
					Integer val = (Integer) f.get(null);
					map.put(f.getName(), val.intValue());
					map2.put(val.intValue(), f.getName());
				}
			}

			layoutIds = map;
			layoutNames = map2;
		}

		return layoutNames.get(id);
	}

	public static int getLayoutId(Context ctx, Resources res, String name)
			throws Exception {
		if (layoutIds == null) {
			getLayoutName(ctx, res, 0);
		}
		Integer i = layoutIds.get(name);
		return i == null ? 0 : i.intValue();
	}
}
