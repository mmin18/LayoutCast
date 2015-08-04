package com.github.mmin18.layoutcast.test;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class Activity2 extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity2);
	}

	public void activity2(View v) {
		startActivity(new Intent(this, Activity2.class));
	}

}
