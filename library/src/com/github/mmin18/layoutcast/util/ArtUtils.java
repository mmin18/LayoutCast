package com.github.mmin18.layoutcast.util;

import android.util.Log;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.DexClassLoader;

/**
 * Created by mmin18 on 8/8/15.
 */
public class ArtUtils {

	public static boolean overrideClassLoader(ClassLoader cl, File dex, File opt) {
		try {
			ClassLoader bootstrap = cl.getParent();
			Field fPathList = BaseDexClassLoader.class.getDeclaredField("pathList");
			fPathList.setAccessible(true);
			Object pathList = fPathList.get(cl);
			Class cDexPathList = bootstrap.loadClass("dalvik.system.DexPathList");
			Field fDexElements = cDexPathList.getDeclaredField("dexElements");
			fDexElements.setAccessible(true);
			Object dexElements = fDexElements.get(pathList);
			DexClassLoader cl2 = new DexClassLoader(dex.getAbsolutePath(), opt.getAbsolutePath(), null, bootstrap);
			Object pathList2 = fPathList.get(cl2);
			Object dexElements2 = fDexElements.get(pathList2);
			Object element2 = Array.get(dexElements2, 0);
			int n = Array.getLength(dexElements) + 1;
			Object newDexElements = Array.newInstance(fDexElements.getType().getComponentType(), n);
			Array.set(newDexElements, 0, element2);
			for (int i = 0; i < n - 1; i++) {
				Object element = Array.get(dexElements, i);
				Array.set(newDexElements, i + 1, element);
			}
			fDexElements.set(pathList, newDexElements);
			return true;
		} catch (Exception e) {
			Log.e("lcast", "fail to override classloader " + cl + " with " + dex, e);
			return false;
		}
	}

}
