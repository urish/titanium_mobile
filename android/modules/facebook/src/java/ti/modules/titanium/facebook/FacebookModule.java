/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.facebook;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiConfig;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.io.TiBaseFile;
import org.appcelerator.titanium.util.TiActivityResultHandler;
import org.appcelerator.titanium.util.TiActivitySupport;
import org.appcelerator.titanium.util.TiConvert;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.facebook.android.AsyncFacebookRunner;
import com.facebook.android.AsyncFacebookRunner.RequestListener;
import com.facebook.android.DialogError;
import com.facebook.android.Facebook;
import com.facebook.android.Facebook.DialogListener;
import com.facebook.android.FacebookError;
import com.facebook.android.Util;


@Kroll.module(name="Facebook", id="ti.facebook")
public class FacebookModule extends KrollModule
{
	protected static final String LCAT = "FacebookModule";
	protected static final boolean DBG = TiConfig.LOGD;
	protected Facebook facebook = null;
	
	private boolean loggedIn = false;
	private ArrayList<TiFacebookStateListener> stateListeners = new ArrayList<TiFacebookStateListener>();
	private SessionListener sessionListener = null;
	private AsyncFacebookRunner fbrunner;
	
	// Constructors
	public FacebookModule() {
		super();
		sessionListener = new SessionListener(this);
		SessionEvents.addAuthListener(sessionListener);
		SessionEvents.addLogoutListener(sessionListener);
		debug("FacebookModule()");
		appid = SessionStore.getSavedAppId(TiApplication.getInstance());
		if (appid != null) {
			debug("Attempting session restore for appid " + appid);
			facebook = new Facebook(appid);
			SessionStore.restore(this, TiApplication.getInstance());
			if (facebook.isSessionValid()) {
				debug("Session restore succeeded.  Now logged in.");
				loggedIn = true;
			} else {
				debug("Session restore failed.  Not logged in.");
				loggedIn = false;
			}
		}
	}

	public FacebookModule(TiContext tiContext)
	{
		this();
	}
	
	// Public Properties with accessors
	@Kroll.getProperty @Kroll.method
	public boolean getLoggedIn()
	{
		return isLoggedIn();
	}

	@Kroll.getProperty @Kroll.method
	public String getAccessToken()
	{
		if (facebook != null) {
			return facebook.getAccessToken();
		} else {
			return null;
		}
	}
	
	private String appid = null;
	@Kroll.getProperty @Kroll.method
	public String getAppid()
	{
		return appid;
	}
	@Kroll.setProperty @Kroll.method
	public void setAppid(String appid)
	{
		if (this.appid != null && !this.appid.equals(appid)) {
			if (facebook != null && facebook.isSessionValid()) {
				// A facebook session existed, but the appid was changed.  Any session info
				// should be destroyed.
				Log.w(LCAT, "Appid was changed while session active.  Removing session info.");
				destroyFacebookSession();
				facebook = null;
			}
		}
		this.appid = appid;
		if (facebook == null || !facebook.getAppId().equals(appid)) {
			facebook = new Facebook(appid);
		}
	}
	
	protected String uid = null;
	@Kroll.getProperty @Kroll.method
	public String getUid()
	{
		return uid;
	}
	
	private String[] permissions = new String[]{};
	@Kroll.getProperty @Kroll.method
	public String[] getPermissions()
	{
		return permissions;
	}
	@Kroll.setProperty @Kroll.method
	public void setPermissions(String[] permissions)
	{
		this.permissions = permissions;
	}
	
	@Kroll.getProperty @Kroll.method
	public Date getExpirationDate()
	{
		if (facebook != null) {
			return TiConvert.toDate(facebook.getAccessExpires());
		} else {
			return new Date(0);
		}
	}
	
	private boolean forceDialogAuth = true;
	@Kroll.getProperty @Kroll.method
	public boolean getForceDialogAuth()
	{
		return forceDialogAuth;
	}
	@Kroll.setProperty @Kroll.method
	public void setForceDialogAuth(boolean value)
	{
		this.forceDialogAuth = value;
	}
	
	// Public Methods
	@Kroll.method
	public TiFacebookModuleLoginButtonProxy createLoginButton(@Kroll.argument(optional=true) KrollDict options)
	{
		TiFacebookModuleLoginButtonProxy login = new TiFacebookModuleLoginButtonProxy(this);
		if (options != null) {
			// TODO
			//login.extend(options);
		}
		return login;
	}
	
	@Kroll.method
	public void authorize()
	{
		debug("authorize; permissions.length == " + permissions.length);
		if (this.isLoggedIn()) {
			// if already authorized, this should do nothing
			debug("Already logged in, ignoring authorize() request");
			return;
		}
		
		if (appid == null) {
			Log.w(LCAT, "authorize() called without appid being set; throwing...");
			throw new IllegalStateException("missing appid");
		}
		
		// forget session in case this fails.
		SessionStore.clear(TiApplication.getInstance());
		
		if (facebook == null) {
			facebook = new Facebook(appid);
		}
		
		executeAuthorize(getActivity());
	}
	
	@Kroll.method
	public void logout()
	{
		boolean wasLoggedIn = isLoggedIn();
		destroyFacebookSession();
		if (facebook != null && wasLoggedIn) {
			SessionEvents.onLogoutBegin();
			executeLogout(getActivity());
		}
	}
	
	@Kroll.method
	public void requestWithGraphPath(String path, KrollDict params, String httpMethod, KrollFunction callback)
	{
		if (facebook == null) {
			Log.w(LCAT, "requestWithGraphPath called without Facebook being instantiated.  Have you set appid?");
			return;
		}
		AsyncFacebookRunner runner = getFBRunner();
		Bundle paramBundle = Utils.mapToBundle(params);
		if (httpMethod == null || httpMethod.length() == 0) {
			httpMethod = "GET";
		}
		runner.request(path, paramBundle, httpMethod.toUpperCase(), new TiRequestListener(this, path, true, callback));
	}
	
	@Kroll.method
	public void request(String method, KrollDict params, KrollFunction callback)
	{
		if (facebook == null) {
			Log.w(LCAT, "request called without Facebook being instantiated.  Have you set appid?");
			return;
		}
		
		String httpMethod = "GET";
		if (params != null) {
			for (Object v : params.values()) {
				if (v instanceof TiBlob || v instanceof TiBaseFile) {
					httpMethod = "POST";
					break;
				}
			}
		}
		
		Bundle bundle = Utils.mapToBundle(params);
		if (!bundle.containsKey("method")) {
			bundle.putString("method", method);
		}
		getFBRunner().request(null, bundle, httpMethod, new TiRequestListener(this, method, false, callback));
	}
	
	@Kroll.method(runOnUiThread=true)
	public void dialog(String action, KrollDict params, KrollFunction callback)
	{
		if (facebook == null) {
			Log.w(LCAT, "dialog called without Facebook being instantiated.  Have you set appid?");
			return;
		}
		facebook.dialog(getActivity(), action, Utils.mapToBundle(params), new TiDialogListener(this, callback, action));
	}

	// Protected methods
	protected void completeLogin()
	{
		getFBRunner().request("me", new RequestListener()
		{
			@Override
			public void onMalformedURLException(MalformedURLException e)
			{
				loginError(e);
			}
			
			@Override
			public void onIOException(IOException e)
			{
				loginError(e);
			}
			
			@Override
			public void onFileNotFoundException(FileNotFoundException e)
			{
				loginError(e);
			}
			
			@Override
			public void onFacebookError(FacebookError e)
			{
				loginError(e);
			}
			
			@Override
			public void onComplete(String response)
			{
				try {
					debug("onComplete (getting 'me'): " + response);
					JSONObject json = Util.parseJson(response);
					uid = json.getString("id");
					loggedIn = true;
					SessionStore.save(FacebookModule.this, TiApplication.getInstance());
					KrollDict data = new KrollDict();
					data.put("cancelled", false);
					data.put("success", true);
					data.put("uid", uid);
					data.put("data", response);
					fireLoginChange();
					fireEvent("login", data);
				} catch (JSONException e) {
					Log.e(LCAT, e.getMessage(), e);
				} catch (FacebookError e) {
					Log.e(LCAT, e.getMessage(), e);
				}
			}
		});
	}
	
	protected void completeLogout()
	{
		destroyFacebookSession();
		fireLoginChange();
		fireEvent("logout", new KrollDict());
	}
	
	protected void debug(String message)
	{
		if (DBG) {
			Log.d(LCAT, message);
		}
	}
	
	protected void addListener(TiFacebookStateListener listener)
	{
		if (!stateListeners.contains(listener)) {
			stateListeners.add(listener);
		}
	}
	
	protected void removeListener(TiFacebookStateListener listener)
	{
		stateListeners.remove(listener);
	}
	
	protected void executeAuthorize(Activity activity)
	{
		int activityCode = Facebook.FORCE_DIALOG_AUTH;
		if (forceDialogAuth) {
			facebook.authorize(activity, permissions, activityCode, new LoginDialogListener());
		} else {
			// Single sign-on support
			TiActivitySupport activitySupport  = (TiActivitySupport) activity;
			activityCode = activitySupport.getUniqueResultCode();
			TiActivityResultHandler resultHandler = new TiActivityResultHandler()
			{
				@Override
				public void onResult(Activity activity, int requestCode, int resultCode, Intent data)
				{
					if (DBG) {
						Log.d(LCAT, "onResult from Facebook single sign-on attempt. resultCode: " + resultCode);
					}
					facebook.authorizeCallback(requestCode, resultCode, data);
				}
				@Override
				public void onError(Activity activity, int requestCode, Exception e)
				{
					Log.e(LCAT, e.getLocalizedMessage(), e);
				}
			};
			facebook.authorize(activity, activitySupport, permissions, activityCode, new LoginDialogListener(), 
					resultHandler);
		}
	}
	
	protected void executeLogout(Activity activity)
	{
		getFBRunner().logout(activity, new LogoutRequestListener());
	}
	
	// Private methods
	private boolean isLoggedIn()
	{
		return loggedIn && facebook != null && facebook.isSessionValid();
	}
	
	private void loginError(Throwable t)
	{
		Log.e(LCAT, t.getMessage(), t);
		loggedIn = false;
		KrollDict data = new KrollDict();
		data.put("cancelled", false);
		data.put("success", false);
		data.put("error", t.getMessage());
		fireEvent("login", data);
	}
	
	private void loginCancel()
	{
		debug("login canceled");
		loggedIn = false;
		KrollDict data = new KrollDict();
		data.put("cancelled", true);
		data.put("success", false);
		fireEvent("login", data);
	}
	
	private AsyncFacebookRunner getFBRunner()
	{
		if (fbrunner == null) {
			fbrunner = new AsyncFacebookRunner(facebook);
		}
		return fbrunner;
	}
	
	private void destroyFacebookSession()
	{
		SessionStore.clear(TiApplication.getInstance());
		uid = null;
		loggedIn = false;
	}
	
	private void fireLoginChange()
	{
		for (TiFacebookStateListener listener : stateListeners) {
			if (getLoggedIn()) {
				listener.login();
			} else {
				listener.logout();
			}
		}
	}
	
	// Private classes
	private final class LoginDialogListener implements DialogListener {
        public void onComplete(Bundle values) {
        	debug("LoginDialogListener onComplete");
            SessionEvents.onLoginSuccess();
        }

        public void onFacebookError(FacebookError error) {
        	Log.e(LCAT, "LoginDialogListener onFacebookError: " + error.getMessage(), error);
            SessionEvents.onLoginError(error.getMessage());
        }
        
        public void onError(DialogError error) {
        	Log.e(LCAT, "LoginDialogListener onError: " + error.getMessage(), error);
            SessionEvents.onLoginError(error.getMessage());
        }

        public void onCancel() {
        	FacebookModule.this.loginCancel();
        }
    }
	
	private final class LogoutRequestListener implements RequestListener
	{
		@Override
		public void onComplete(String response)
		{
			debug("Logout request complete: " + response);
			SessionEvents.onLogoutFinish();
		}

		@Override
		public void onFacebookError(FacebookError e)
		{
			Log.e(LCAT, "Logout failure: " + e.getMessage(), e);
		}

		@Override
		public void onFileNotFoundException(FileNotFoundException e)
		{
			Log.e(LCAT, "Logout failure: " + e.getMessage(), e);
		}

		@Override
		public void onIOException(IOException e)
		{
			Log.e(LCAT, "Logout failure: " + e.getMessage(), e);
		}

		@Override
		public void onMalformedURLException(MalformedURLException e)
		{
			Log.e(LCAT, "Logout failure: " + e.getMessage(), e);
		}
	}

	// KrollModule Overrides
	@Override
	public void onDestroy(Activity activity)
	{
		super.onDestroy(activity);
		if (sessionListener != null) {
			SessionEvents.removeAuthListener(sessionListener);
			SessionEvents.removeLogoutListener(sessionListener);
			sessionListener = null;
		}
	}
}
