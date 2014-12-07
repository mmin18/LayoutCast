package com.github.mmin18.layoutcast.util;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.Resources.Theme;

public class ResContext extends ContextWrapper {

	private final Resources resources;
	private final Theme theme;

	public ResContext(Context base, Resources res) {
		super(base);
		this.resources = res;
		theme = resources.newTheme();
		theme.setTo(base.getTheme());
	}

	@Override
	public AssetManager getAssets() {
		return resources.getAssets();
	}

	@Override
	public Resources getResources() {
		return resources;
	}

	@Override
	public Theme getTheme() {
		return theme;
	}

}
