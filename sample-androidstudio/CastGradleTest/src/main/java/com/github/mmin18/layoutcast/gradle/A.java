package com.github.mmin18.layoutcast.gradle;

/**
 * Created by mmin18 on 8/7/15.
 */
public class A {

	@Override
	public String toString() {
		return "ORIG A (" + new B() + ")";
	}

	public String a() {
		return "orig a";
	}

}
