package com.github.mmin18.layoutcast.gradle;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.view.SimpleDraweeView;
import com.github.mmin18.layoutcast.library.Lib;


/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends ListFragment {

	public static String[] IMAGES = {
			"http://i3.mifile.cn/a4/T1sAVjBCAT1RXrhCrK.jpg",
			"http://i3.mifile.cn/a4/T1lhVjB5hT1RXrhCrK.jpg",
			"http://i3.mifile.cn/a4/T1MZdgBgJT1RXrhCrK.jpg",
			"http://i3.mifile.cn/a4/T1XIEjBCxT1RXrhCrK.jpg",
			"http://i3.mifile.cn/a4/T1F0WjBCCT1RXrhCrK.jpg",
			"http://i3.mifile.cn/a4/T18yZQBCET1RXrhCrK.jpg",
			"http://i3.mifile.cn/a4/T16nVjBCKT1RXrhCrK.jpg",
			"http://i3.mifile.cn/a4/T18zWQB4WT1RXrhCrK.jpg",
			"http://i3.mifile.cn/a4/T1YoZQByYT1RXrhCrK.jpg",
			"http://i3.mifile.cn/a4/T1oixjB5bT1RXrhCrK.jpg",
			"http://i3.mifile.cn/a4/T12WdjB5YT1RXrhCrK.jpg",
			"http://i3.mifile.cn/a4/T1KzbQBvbT1RXrhCrK.jpg",
			"http://i3.mifile.cn/a4/T1odEjB5bT1RXrhCrK.jpg"
	};

	public MainActivityFragment() {
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Fresco.initialize(getActivity());
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_main, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		((TextView) view.findViewById(R.id.text)).setText(new Lib().toString());
		setListAdapter(new Adapter());
		getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Uri uri = Uri.parse(IMAGES[position]);
				Intent i = new Intent(getActivity(), DetailActivity.class);
				i.setData(uri);
				startActivity(i);
			}
		});
	}

	private class Adapter extends BaseAdapter {
		@Override
		public int getCount() {
			return IMAGES.length;
		}

		@Override
		public String getItem(int position) {
			return IMAGES[position];
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view;
			if (convertView != null) {
				view = convertView;
			} else {
				view = getLayoutInflater(null).inflate(R.layout.list_item, parent, false);
			}
			String url = getItem(position);
			((TextView) view.findViewById(R.id.url)).setText(url);
			((SimpleDraweeView) view.findViewById(R.id.image)).setImageURI(Uri.parse(url));
			return view;
		}
	}
}
