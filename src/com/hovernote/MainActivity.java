package com.hovernote;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;


public class MainActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent service = new Intent(this, Notepad.class);
		startService(service);
		finish();
	}
}
