package com.github.mmin18.layoutcast.gradle;

import android.app.Activity;
import android.os.Bundle;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.view.SimpleDraweeView;

/**
 * Created by mmin18 on 8/5/15.
 */
public class DetailActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Fresco.initialize(this);
		setContentView(R.layout.detail_view);

		((SimpleDraweeView) findViewById(R.id.image)).setImageURI(getIntent().getData());
	}
}
