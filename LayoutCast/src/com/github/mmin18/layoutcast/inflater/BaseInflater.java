package com.github.mmin18.layoutcast.inflater;

import java.lang.reflect.Constructor;
import java.util.HashMap;

import android.content.Context;
import android.util.AttributeSet;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewStub;

public class BaseInflater extends LayoutInflater {
	private static final String[] sClassPrefixList = { "android.widget.",
			"android.view.", "android.webkit." };
	public LayoutInflater inflater;

	public BaseInflater(Context context) {
		super(context);
		setFactory2(factory);
	}

	protected BaseInflater(LayoutInflater original, Context newContext) {
		super(original, newContext);
		setFactory2(factory);
	}

	@Override
	protected View onCreateView(View parent, String name, AttributeSet attrs)
			throws ClassNotFoundException {
		throw new UnsupportedOperationException();
	}

	@Override
	protected View onCreateView(String name, AttributeSet attrs)
			throws ClassNotFoundException {
		throw new UnsupportedOperationException();
	}

	public LayoutInflater cloneInContext(Context newContext) {
		return new BaseInflater(this, newContext);
	}

	private final Factory2 factory = new Factory2() {
		@Override
		public View onCreateView(String name, Context context,
				AttributeSet attrs) {
			throw new UnsupportedOperationException();
		}

		@Override
		public View onCreateView(View parent, String name, Context context,
				AttributeSet attrs) {
			try {
				if (name.indexOf('.') == -1) {
					for (String prefix : sClassPrefixList) {
						try {
							View view = createViewInContext(parent, prefix
									+ name, context, attrs);
							if (view != null) {
								return view;
							}
						} catch (ClassNotFoundException e) {
							// In this case we want to let the base class take a
							// crack at it.
						}
					}
				}

				return createViewInContext(parent, name, context, attrs);
			} catch (ClassNotFoundException e) {
				InflateException ie = new InflateException(
						attrs.getPositionDescription()
								+ ": Error inflating class " + name);
				ie.initCause(e);
				throw ie;
			}
		}
	};

	private static final HashMap<String, Constructor<? extends View>> constructorMap = new HashMap<String, Constructor<? extends View>>();
	private static final Class<?>[] constructorSignature = new Class[] {
			Context.class, AttributeSet.class };
	private final Object[] constructorArgs = new Object[2];

	protected View createViewInContext(View parent, String name,
			Context context, AttributeSet attrs) throws ClassNotFoundException,
			InflateException {
		Constructor<? extends View> constructor = constructorMap.get(name);
		Class<? extends View> clazz = null;

		try {
			if (constructor == null) {
				// Class not found in the cache, see if it's real, and try to
				// add it
				clazz = context.getClassLoader().loadClass(name)
						.asSubclass(View.class);

				constructor = clazz.getConstructor(constructorSignature);
				constructorMap.put(name, constructor);
			}

			Object[] args = constructorArgs;
			args[0] = context;
			args[1] = attrs;

			final View view = constructor.newInstance(args);
			if (view instanceof ViewStub) {
				// always use ourselves when inflating ViewStub later
				final ViewStub viewStub = (ViewStub) view;
				viewStub.setLayoutInflater(this);
			}
			return view;

		} catch (NoSuchMethodException e) {
			InflateException ie = new InflateException(
					attrs.getPositionDescription() + ": Error inflating class "
							+ name);
			ie.initCause(e);
			throw ie;

		} catch (ClassCastException e) {
			// If loaded class is not a View subclass
			InflateException ie = new InflateException(
					attrs.getPositionDescription() + ": Class is not a View "
							+ name);
			ie.initCause(e);
			throw ie;
		} catch (ClassNotFoundException e) {
			// If loadClass fails, we should propagate the exception.
			throw e;
		} catch (Exception e) {
			InflateException ie = new InflateException(
					attrs.getPositionDescription() + ": Error inflating class "
							+ (clazz == null ? "<unknown>" : clazz.getName()));
			ie.initCause(e);
			throw ie;
		}
	}
}
