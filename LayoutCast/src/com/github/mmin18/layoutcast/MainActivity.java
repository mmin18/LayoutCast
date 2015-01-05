package com.github.mmin18.layoutcast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Environment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.github.mmin18.layoutcast.context.OverrideContext;
import com.github.mmin18.layoutcast.util.ResUtils;

public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		System.out.println(getLayoutInflater());
	}

	public void activity2(View v) {
		startActivity(new Intent(this, Activity2.class));
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		switch (id) {
		case R.id.action_inflate: {
			setContentView(R.layout.activity_main);
			return true;
		}
		case R.id.action_reset: {
			try {
				OverrideContext.override(this, null);
			} catch (Exception e) {
				Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
			}
			return true;
		}
		case R.id.action_sdcard: {
			try {
				File src = new File(Environment.getExternalStorageDirectory(),
						"res");
				File dst = new File(getFilesDir(), "res.apk");
				cp(src, dst);
				System.out.println("before: " + getResources());
				Resources res = ResUtils.getResources(this, dst);
				OverrideContext.override(this, res);
				System.out.println("after: " + getResources());
				Toast.makeText(this, "Resources has been replaced to " + src,
						Toast.LENGTH_SHORT).show();
			} catch (Exception e) {
				Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
			}
			return true;
		}
		case R.id.action_latest: {
			File dir = new File(getCacheDir(), "lcast");
			File[] fs = dir.listFiles();
			File latest = null;
			if (fs != null) {
				long latestTime = Long.MIN_VALUE;
				for (File f : fs) {
					if (f.getName().endsWith(".apk")) {
						long t = f.lastModified();
						if (t > latestTime) {
							latest = f;
							latestTime = t;
						}
					}
				}
			}
			if (latest == null) {
				Toast.makeText(this, "latest not found", Toast.LENGTH_SHORT)
						.show();
			} else {
				try {
					Resources res = ResUtils.getResources(this, latest);
					OverrideContext.override(this, res);
					Toast.makeText(this,
							"Resources has been replaced to latest" + latest,
							Toast.LENGTH_SHORT).show();
				} catch (Exception e) {
					Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT)
							.show();
				}
			}
			return true;
		}
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private void cp(File src, File dst) throws IOException {
		FileInputStream fis = new FileInputStream(src);
		FileOutputStream fos = new FileOutputStream(dst);
		byte[] buf = new byte[4096];
		int l;
		while ((l = fis.read(buf)) != -1) {
			fos.write(buf, 0, l);
		}
		fos.close();
		fis.close();
	}

}
