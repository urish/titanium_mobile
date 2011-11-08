/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium;

import java.util.Set;

import org.appcelerator.kroll.KrollRuntime;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiConfig;
import org.appcelerator.kroll.util.KrollAssetHelper;
import org.appcelerator.titanium.analytics.TiAnalyticsEventFactory;
import org.appcelerator.titanium.proxy.ActivityProxy;
import org.appcelerator.titanium.util.TiBindingHelper;
import org.appcelerator.titanium.util.TiColorHelper;
import org.appcelerator.titanium.util.TiUrl;
import org.appcelerator.titanium.view.TiCompositeLayout;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.Toast;

/**
 * Titanium launch activites have a single TiContext and launch an associated
 * Javascript URL during onCreate()
 */
public abstract class TiLaunchActivity extends TiBaseActivity
{
	private static final String TAG = "TiLaunchActivity";
	private static final boolean DBG = TiConfig.LOGD;
	
	private static final int MSG_FINISH = 100;
	private static final int RESTART_DELAY = 500;
	private static final int FINISH_DELAY = 500;

	protected TiUrl url;
	protected AlertDialog noLaunchCategoryAlert;

	/**
	 * @return The Javascript URL that this Activity should run
	 */
	public abstract String getUrl();

	/**
	 * Subclasses should override to perform custom behavior
	 * when the Launch Activity's script is finished loading
	 */
	protected void scriptLoaded() { }

	/**
	 * Subclasses should override to perform custom behavior
	 * when the TiContext has been created.
	 * This happens before the script is loaded.
	 */
	protected void contextCreated() { }

	protected void loadActivityScript()
	{
		try {
			String fullUrl = url.resolve();

			if (DBG) {
				Log.d(TAG, "Eval JS Activity:" + fullUrl);
			}

			if (fullUrl.startsWith(TiC.URL_APP_PREFIX)) {
				fullUrl = fullUrl.replaceAll("app:/", "Resources");

			} else if (fullUrl.startsWith(TiC.URL_ANDROID_ASSET_RESOURCES)) {
				fullUrl = fullUrl.replaceAll("file:///android_asset/", "");
			}

			KrollRuntime.getInstance().runModule(KrollAssetHelper.readAsset(fullUrl), fullUrl, activityProxy);

		} finally {
			if (DBG) {
				Log.d(TAG, "Signal JS loaded");
			}
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		Intent intent = getIntent();
		if (intent != null) {
			TiProperties systemProperties = getTiApp().getSystemProperties();
			boolean detectionDisabled = systemProperties.getBool("ti.android.bug2373.disableDetection", false);
			if (!detectionDisabled && checkMissingLauncher(intent, savedInstanceState)) {
				return;
			}
		}

		url = TiUrl.normalizeWindowUrl(getUrl());

		// removed with the change to create the activity proxy in the onCreate of TiBaseActivity
		/*
		if (activityProxy == null) {
			setActivityProxy(new ActivityProxy(this));
		}
		*/

		// we only want to set the current activity for good in the resume state but we need it right now.
		// save off the existing current activity, set ourselves to be the new current activity temporarily 
		// so we don't run into problems when we bind the current activity
		TiApplication tiApp = getTiApp();
		Activity tempCurrentActivity = tiApp.getCurrentActivity();
		tiApp.setCurrentActivity(this, this);

		// TODO this isnt used anymore, remove?
		//TiBindingHelper.bindCurrentActivity(activityProxy);

		// set the current activity back to what it was originally
		tiApp.setCurrentActivity(this, tempCurrentActivity);

		contextCreated();
		super.onCreate(savedInstanceState);
	}

	@Override
	protected void windowCreated()
	{
		super.windowCreated();
		loadActivityScript();
		scriptLoaded();
	}

	protected boolean checkMissingLauncher(Intent intent, Bundle savedInstanceState)
	{
		String action = intent.getAction();
		if (action != null && action.equals(Intent.ACTION_MAIN)) {
			Set<String> categories = intent.getCategories();
			boolean b2373Detected = true; // Absence of LAUNCHER is the problem.

			if (categories != null) {
				for(String category : categories) {
					if (category.equals(Intent.CATEGORY_LAUNCHER)) {
						b2373Detected = false;

						break;
					}
				}
			}
			
			if(b2373Detected) {
				// removed call to setInstanceCount in log statement below.  Method is gone beginning in api 11.
				Log.e(TAG, "Android issue 2373 detected (missing intent CATEGORY_LAUNCHER), restarting app.");
				layout = new TiCompositeLayout(this);
				setContentView(layout);
				TiProperties systemProperties = getTiApp().getSystemProperties();
				int backgroundColor = TiColorHelper.parseColor(systemProperties.getString("ti.android.bug2373.backgroundColor", "black"));
				getWindow().getDecorView().setBackgroundColor(backgroundColor);
				layout.setBackgroundColor(backgroundColor);

				activityOnCreate(savedInstanceState);

				return true;
			}
		}

		return false;
	}

	protected void alertMissingLauncher()
	{
		// No context, we have a launch problem.
		TiProperties systemProperties = getTiApp().getSystemProperties();
		String message = systemProperties.getString("ti.android.bug2373.message", "An application restart is required");
		final int restartDelay = systemProperties.getInt("ti.android.bug2373.restartDelay", RESTART_DELAY);
		final int finishDelay = systemProperties.getInt("ti.android.bug2373.finishDelay", FINISH_DELAY);
		
		if (systemProperties.getBool("ti.android.bug2373.skipAlert", false)) {
			if (message != null && message.length() > 0) {
				Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
			}
			restartActivity(restartDelay, finishDelay);
		} else {
			OnClickListener restartListener = new OnClickListener() 
			{	
				public void onClick(DialogInterface arg0, int arg1) {
					restartActivity(restartDelay, finishDelay);
				}
			};
	
			String title = systemProperties.getString("ti.android.bug2373.title", "Restart Required");
			String buttonText = systemProperties.getString("ti.android.bug2373.buttonText", "Continue");
			noLaunchCategoryAlert = new AlertDialog.Builder(this)
				.setTitle(title)
				.setMessage(message)
				.setPositiveButton(buttonText, restartListener)
				.setCancelable(false).create();
			noLaunchCategoryAlert.show();
		}
	}

	protected void restartActivity(int delay)
	{
		restartActivity(delay, 0);
	}
	
	protected void restartActivity(int delay, int finishDelay)
	{
		Intent relaunch = new Intent(getApplicationContext(), getClass());
		relaunch.setAction(Intent.ACTION_MAIN);
		relaunch.addCategory(Intent.CATEGORY_LAUNCHER);

		AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
		if (am != null) {
			PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0, relaunch, PendingIntent.FLAG_ONE_SHOT);
			am.set(AlarmManager.RTC, System.currentTimeMillis() + delay, pi);
		}
		
		if (finishDelay > 0) {
			Handler handler = new Handler() 
			{	
				@Override
				public void handleMessage(Message msg) 
				{					
					if (msg.what == MSG_FINISH) {
						finish();		
					} else {
						super.handleMessage(msg);
					}
				}			
			};
			
			handler.sendEmptyMessageDelayed(MSG_FINISH, finishDelay);
		} else {
			finish();
		}
	}

	@Override
	protected void onRestart()
	{
		super.onRestart();
		TiProperties systemProperties = getTiApp().getSystemProperties();

		boolean restart = systemProperties.getBool("ti.android.root.reappears.restart", false);
		if (restart) {
			Log.w(TAG, "Tasks may have been destroyed by Android OS for inactivity. Restarting.");
			restartActivity(250);
		}
	}

	@Override
	protected void onPause()
	{
		// TODO - enable this
		/*
		if (tiContext == null) {
			// Not in a good state. Let's get out.
			if (noLaunchCategoryAlert != null && noLaunchCategoryAlert.isShowing()) {
				noLaunchCategoryAlert.cancel();
				noLaunchCategoryAlert = null;
			}

			finish();

		} else {
			tiContext.fireLifecycleEvent(this, TiContext.LIFECYCLE_ON_PAUSE);	
		}
		*/

		super.onPause();
	}

	@Override
	protected void onDestroy()
	{
		// TODO - enable this
		/*
		if (tiContext != null) {
			tiContext.fireLifecycleEvent(this, TiContext.LIFECYCLE_ON_DESTROY);
			TiApplication tiApp = tiContext.getTiApp();
			if (tiApp != null) {
				tiApp.postAnalyticsEvent(TiAnalyticsEventFactory.createAppEndEvent());
			}
		}
		*/

		TiApplication tiApp = TiApplication.getInstance();
		if (tiApp != null) {
			tiApp.postAnalyticsEvent(TiAnalyticsEventFactory.createAppEndEvent());
		}

		super.onDestroy();
	}
}
