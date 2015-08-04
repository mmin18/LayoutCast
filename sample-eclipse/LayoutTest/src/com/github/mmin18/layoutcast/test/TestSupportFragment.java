package com.github.mmin18.layoutcast.test;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class TestSupportFragment extends Fragment {

	public TestSupportFragment() {
		new Exception().printStackTrace();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		return inflater.inflate(R.layout.aaa_text, container, false);
	}

}
