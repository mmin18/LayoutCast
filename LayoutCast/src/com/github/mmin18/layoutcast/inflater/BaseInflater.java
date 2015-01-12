package com.github.mmin18.layoutcast.inflater;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

public class BaseInflater extends LayoutInflater {
	private static final String[] sClassPrefixList = { "android.widget.",
			"android.webkit." };

	public BaseInflater(Context context) {
		super(context);
	}

	protected BaseInflater(LayoutInflater original, Context newContext) {
		super(original, newContext);
	}

	@Override
	protected View onCreateView(String name, AttributeSet attrs)
			throws ClassNotFoundException {
		for (String prefix : sClassPrefixList) {
			try {
				View view = createView(name, prefix, attrs);
				if (view != null) {
					return view;
				}
			} catch (ClassNotFoundException e) {
				// In this case we want to let the base class take a crack
				// at it.
			}
		}

		return super.onCreateView(name, attrs);
	}

	public LayoutInflater cloneInContext(Context newContext) {
		return new BaseInflater(this, newContext);
	}
}
