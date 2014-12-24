package com.github.mmin18.layoutcast.context;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.WeakHashMap;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.os.Bundle;
import android.view.ContextThemeWrapper;

public class OverrideContext extends ContextWrapper {

	private final Context base;
	private Resources resources;
	private Theme theme;

	protected OverrideContext(Context base, Resources res) {
		super(base);
		this.base = base;
		this.resources = res;
	}

	@Override
	public AssetManager getAssets() {
		return resources == null ? base.getAssets() : resources.getAssets();
	}

	@Override
	public Resources getResources() {
		return resources == null ? base.getResources() : resources;
	}

	@Override
	public Theme getTheme() {
		if (resources == null) {
			return base.getTheme();
		}
		if (theme == null) {
			theme = resources.newTheme();
			theme.setTo(base.getTheme());
		}
		return theme;
	}

	protected void setResources(Resources res) {
		if (this.resources != res) {
			this.resources = res;
			this.theme = null;
		}
	}

	/**
	 * @param res
	 *            set null to reset original resources
	 */
	public static OverrideContext override(ContextWrapper orig, Resources res)
			throws Exception {
		Context base = orig.getBaseContext();
		OverrideContext oc;
		if (base instanceof OverrideContext) {
			oc = (OverrideContext) base;
			oc.setResources(res);
		} else {
			oc = new OverrideContext(base, res);

			Field fBase = ContextWrapper.class.getDeclaredField("mBase");
			fBase.setAccessible(true);
			fBase.set(orig, oc);
		}

		Field fResources = ContextThemeWrapper.class
				.getDeclaredField("mResources");
		fResources.setAccessible(true);
		fResources.set(orig, null);

		Field fTheme = ContextThemeWrapper.class.getDeclaredField("mTheme");
		fTheme.setAccessible(true);
		fTheme.set(orig, null);

		return oc;
	}

	//
	// Activities
	//

	public static final int ACTIVITY_NONE = 0;
	public static final int ACTIVITY_CREATED = 1;
	public static final int ACTIVITY_STARTED = 2;
	public static final int ACTIVITY_RESUMED = 3;
	private static final WeakHashMap<Activity, Integer> activities = new WeakHashMap<Activity, Integer>();

	public static void initApplication(Application app) {
		app.registerActivityLifecycleCallbacks(lifecycleCallback);
	}

	public static Activity[] getAllActivities() {
		ArrayList<Activity> list = new ArrayList<Activity>();
		for (Entry<Activity, Integer> e : activities.entrySet()) {
			Activity a = e.getKey();
			if (a != null && e.getValue().intValue() > 0) {
				list.add(a);
			}
		}
		return list.toArray(new Activity[list.size()]);
	}

	public static Activity getTopActivity() {
		Activity r = null;
		for (Entry<Activity, Integer> e : activities.entrySet()) {
			Activity a = e.getKey();
			if (a != null && e.getValue().intValue() == ACTIVITY_RESUMED) {
				r = a;
			}
		}
		return r;
	}

	public static int getActivityState(Activity a) {
		Integer i = activities.get(a);
		if (i == null) {
			return ACTIVITY_NONE;
		} else {
			return i.intValue();
		}
	}

	private static final Application.ActivityLifecycleCallbacks lifecycleCallback = new Application.ActivityLifecycleCallbacks() {
		@Override
		public void onActivityStopped(Activity activity) {
			activities.put(activity, ACTIVITY_CREATED);
		}

		@Override
		public void onActivityStarted(Activity activity) {
			activities.put(activity, ACTIVITY_STARTED);
		}

		@Override
		public void onActivitySaveInstanceState(Activity activity,
				Bundle outState) {
		}

		@Override
		public void onActivityResumed(Activity activity) {
			activities.put(activity, ACTIVITY_RESUMED);
		}

		@Override
		public void onActivityPaused(Activity activity) {
			activities.put(activity, ACTIVITY_STARTED);
		}

		@Override
		public void onActivityDestroyed(Activity activity) {
			activities.remove(activity);
		}

		@Override
		public void onActivityCreated(Activity activity,
				Bundle savedInstanceState) {
			activities.put(activity, ACTIVITY_CREATED);
		}
	};

	//
	// Global
	//

	private static Resources overrideResources;

	public static void setGlobalResources(Resources res) throws Exception {
		overrideResources = res;
		Exception err = null;
		for (Activity a : getAllActivities()) {
			try {
				override(a, res);
			} catch (Exception e) {
				err = e;
			}
		}
		if (err != null) {
			throw err;
		}
	}

	public static OverrideContext overrideDefault(ContextWrapper orig)
			throws Exception {
		return override(orig, overrideResources);
	}
}
