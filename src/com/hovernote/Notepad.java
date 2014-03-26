package com.hovernote;

import android.view.View;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Typeface;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

public class Notepad extends Service implements OnTouchListener, OnClickListener {
	
	private final int BOUNDARY = 300;
	
	private View topLeftView;
	private EditText pad;
	private Display display;
	private int width;
	private int height;
	private Point size;
	private float offsetX;
	private float offsetY;
	private int originalXPos;
	private int originalYPos;
	private WindowManager wm;
	private InputMethodManager imm;
	private int previousXPos;
	private Typeface face;
	private SharedPreferences prefs;
	private String getText;
	private boolean displayedToast;
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		// SharedPreferences shouldn't typically be used for data storage
		// This is just a hack and the data is quite small so it shouldn't matter much
		prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
		imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		
		// Get size of screen
		size = new Point();
		display = wm.getDefaultDisplay();
		display.getSize(size);
		height = size.y;
		width = size.x;
		
		// Settings up the main "Notepad" view
		pad = new EditText(this);
		pad.setBackgroundColor(Color.parseColor("#5ba525"));
		pad.setHintTextColor(Color.WHITE);
		pad.setOnTouchListener(this);
		pad.setWidth(width);
		pad.setHeight(height/5);	
		
		// Set text if old notes exist
		// otherwise display hint
		getText = prefs.getString("Notes", "").toString();
		
		if (getText != "") {		
			pad.setText(getText);
			pad.setSelection(pad.getText().toString().length());
		}
		else {
			pad.setText(null);
			// This service has its own context, otherwise use string resource for hint
			pad.setHint("Start noting the world...");
		}
		
		// Watch for text being changed and act accordingly
		pad.addTextChangedListener(new TextWatcher() {

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				
				if (pad.getLineCount() == 0) {
					pad.setText(null);
					pad.setHint("Start noting the world...");
				}
				else if (pad.getLineCount() > 5) {
					
					// Check if toast has been displayed already if # of lines exceeds 5
					// If so, don't create any new toast messages
					if (!displayedToast) {
						displayedToast = true;
						Toast toast = Toast.makeText(getApplicationContext(), 
													"More than 5 lines is not visibly supported for now :(", 
													Toast.LENGTH_SHORT);
							
						toast.setGravity(Gravity.CENTER | Gravity.TOP, 0, 100);
						toast.show();
						return;
					}
				}
				else {
					displayedToast = false;
					// Store notes in case of close/crash
					SharedPreferences.Editor editor = prefs.edit();
					editor.putString("Notes", pad.getText().toString());
					editor.commit();
				}
			}

			@Override
			public void afterTextChanged(Editable s) {}
			
		});
		
		// Set the font
		face = Typeface.createFromAsset(getAssets(), "CaeciliaLTStd-Roman.ttf");
		pad.setTypeface(face);
		
		// Set up the pad's parameters for the WindowManager
		WindowManager.LayoutParams params = new WindowManager.LayoutParams(
				WindowManager.LayoutParams.MATCH_PARENT,
				WindowManager.LayoutParams.MATCH_PARENT,
				WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
				WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
				| WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
				PixelFormat.TRANSLUCENT);
		
		params.gravity = Gravity.LEFT | Gravity.TOP;
		params.x = width - 100;
		params.y = height / 6;
		params.width = width - 200;
		params.height = height / 5;
		previousXPos = 0;
		wm.addView(pad, params);
		
		// topLeftView is used to calculate pad's relative location on the screen
		topLeftView = new View (this);
		WindowManager.LayoutParams topLeftParams = new WindowManager.LayoutParams(
				WindowManager.LayoutParams.WRAP_CONTENT,
				WindowManager.LayoutParams.WRAP_CONTENT,
				WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
				PixelFormat.TRANSLUCENT);
		
		topLeftParams.gravity = Gravity.LEFT | Gravity.TOP;
		topLeftParams.x = 0;
		topLeftParams.y = 0;
		topLeftParams.width = 0;
		topLeftParams.height = height / 5;
		wm.addView(topLeftView, topLeftParams);
				
	}
	
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		if (pad != null) {
			wm.removeView(pad);
			wm.removeView(topLeftView);
			pad = null;
			topLeftView = null;
		}
	}
	 
	@Override
	public boolean onTouch(View v, MotionEvent event) {
	
		float x;
		float y;
		int deltaX;

		switch(event.getAction() & MotionEvent.ACTION_MASK) {
	
			case MotionEvent.ACTION_DOWN:
				x = event.getRawX();
				y = event.getRawY();
				
				int[] location = new int [2];
				pad.getLocationOnScreen(location);
				
				originalXPos = location[0];
				originalYPos = location[1];
				
				offsetX = originalXPos - x;
				offsetY = originalYPos - y;
				
				// For Views, onTouchListener has a higher priority than onClickListener
				// As a result,  detect if an action is a click or a touch
				// to move the pad around is basically not possible
				// Here we calculate the difference between the old position and the new
				// one and if the difference is less than 2 pixels it's a good indicator
				// that the action was intended to be a click
				deltaX = originalXPos - previousXPos;

				if (Math.abs(deltaX) < 2)
					this.onClick(pad);
				
				previousXPos = originalXPos;
					
				break;
				
			case MotionEvent.ACTION_MOVE:
				
				int[] topLeftLocationOnScreen = new int [2];
				topLeftView.getLocationOnScreen(topLeftLocationOnScreen);
				
				x = event.getRawX();
				y = event.getRawY();
				
				WindowManager.LayoutParams updatedParams = (LayoutParams) pad.getLayoutParams();
				
				updatedParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
									 WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
				
				int newX = (int) (offsetX + x);
				int newY = (int) (offsetY + y);

				updatedParams.x = newX - (topLeftLocationOnScreen[0]);
				updatedParams.y = newY - (topLeftLocationOnScreen[1]);
				
				if (newX > width - BOUNDARY ||
					newX < (-width) + BOUNDARY)
					returnDefaultFocus();
				
				// Prevents keyboard from going completely off the screen
				if (newX > width - BOUNDARY ||
					newX < (-width) + BOUNDARY) {
					System.out.println("Can't go further to the right. You might lose the pad");
					return false;
				}
			
				wm.updateViewLayout(pad, updatedParams);
				
				break;
				
			case MotionEvent.ACTION_UP:
					return true;
			
			default:
				return true;
		}
		
		return true;
	}
	
	@Override
	public void onClick (View v) {
		imm.showSoftInput(v, 0);
	}
	
	public void returnDefaultFocus() {
		WindowManager.LayoutParams updatedParams = (LayoutParams) pad.getLayoutParams();
		updatedParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
						WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
						WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
		wm.updateViewLayout(pad, updatedParams);
	}
}
