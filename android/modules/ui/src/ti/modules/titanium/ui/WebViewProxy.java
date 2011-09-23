/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui;

import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.util.AsyncResult;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiUIView;

import ti.modules.titanium.ui.widget.webview.TiUIWebView;
import android.app.Activity;
import android.os.Handler;
import android.os.Message;

@Kroll.proxy(creatableInModule=UIModule.class)
@Kroll.dynamicApis(properties = {
	TiC.PROPERTY_DATA,
	TiC.PROPERTY_HTML,
	TiC.PROPERTY_SCALES_PAGE_TO_FIT,
	TiC.PROPERTY_URL
})
public class WebViewProxy extends ViewProxy
	implements Handler.Callback
{

	private static final int MSG_FIRST_ID = ViewProxy.MSG_LAST_ID + 1;

	private static final int MSG_EVAL_JS = MSG_FIRST_ID + 100;
	private static final int MSG_GO_BACK = MSG_FIRST_ID + 101;
	private static final int MSG_GO_FORWARD = MSG_FIRST_ID + 102;
	private static final int MSG_RELOAD = MSG_FIRST_ID + 103;
	private static final int MSG_STOP_LOADING = MSG_FIRST_ID + 104;
	
	protected static final int MSG_LAST_ID = MSG_FIRST_ID + 999;

	public WebViewProxy(TiContext context) {
		super(context);
	}

	@Override
	public TiUIView createView(Activity activity) {
		TiUIWebView webView = new TiUIWebView(this);
		webView.focus();
		return webView;
	}

	public TiUIWebView getWebView() {
		return (TiUIWebView)getView(getTiContext().getActivity());
	}

	@Kroll.method
	public Object evalJS(String code) {
		if (getTiContext().isUIThread()) {
			return getWebView().getJSValue(code);
		} else {
			return sendBlockingUiMessage(MSG_EVAL_JS, code);
		}
	}

	@Override
	public boolean handleMessage(Message msg) {
		switch (msg.what) {
			case MSG_EVAL_JS:
				AsyncResult result = (AsyncResult)msg.obj;
				String value = getWebView().getJSValue((String)result.getArg());
				result.setResult(value);
				return true;
			case MSG_GO_BACK:
				getWebView().goBack();
				return true;
			case MSG_GO_FORWARD:
				getWebView().goForward();
				return true;
			case MSG_RELOAD:
				getWebView().reload();
				return true;
			case MSG_STOP_LOADING:
				getWebView().stopLoading();
				return true;
		}
		return super.handleMessage(msg);
	}
	
	@Kroll.method
	public void setBasicAuthentication(String username, String password)
	{
		getWebView().setBasicAuthentication(username, password);
	}
	
	@Kroll.method
	public boolean canGoBack() {
		return getWebView().canGoBack();
	}
	
	@Kroll.method
	public boolean canGoForward() {
		return getWebView().canGoForward();
	}
	
	@Kroll.method
	public void goBack() {
		getUIHandler().sendEmptyMessage(MSG_GO_BACK);
	}
	
	
	@Kroll.method
	public void goForward() {
		getUIHandler().sendEmptyMessage(MSG_GO_FORWARD);
	}
	
	@Kroll.method
	public void reload() {
		getUIHandler().sendEmptyMessage(MSG_RELOAD);
	}
	
	@Kroll.method
	public void stopLoading() {
		getUIHandler().sendEmptyMessage(MSG_STOP_LOADING);

	}
	
	@Kroll.method @Kroll.getProperty
	public int getPluginState()
	{
		int pluginState = TiUIWebView.PLUGIN_STATE_OFF;
		
		if (hasProperty(TiC.PROPERTY_PLUGIN_STATE)) {
			pluginState = TiConvert.toInt(getProperty(TiC.PROPERTY_PLUGIN_STATE));
		}
		
		return pluginState;
	}
	
	@Kroll.method @Kroll.setProperty
	public void setPluginState(int pluginState) 
	{
		switch(pluginState) {
			case TiUIWebView.PLUGIN_STATE_OFF :
			case TiUIWebView.PLUGIN_STATE_ON :
			case TiUIWebView.PLUGIN_STATE_ON_DEMAND :
				setProperty(TiC.PROPERTY_PLUGIN_STATE, pluginState, true);
				break;
			default:
				setProperty(TiC.PROPERTY_PLUGIN_STATE, TiUIWebView.PLUGIN_STATE_OFF, true);
		}
	}
	
	@Kroll.method
	public void pause() 
	{
		getWebView().pauseWebView();
	}
	
	@Kroll.method
	public void resume()
	{
		getWebView().resumeWebView();
	}
	
	@Kroll.method(runOnUiThread=true) @Kroll.setProperty(runOnUiThread=true)
	public void setEnableZoomControls(boolean enabled)
	{
		setProperty(TiC.PROPERTY_ENABLE_ZOOM_CONTROLS, enabled, true);
	}
	
	@Kroll.method @Kroll.getProperty
	public boolean getEnableZoomControls()
	{
		boolean enabled = true;
		
		if(hasProperty(TiC.PROPERTY_ENABLE_ZOOM_CONTROLS)) {
			enabled = TiConvert.toBoolean(getProperty(TiC.PROPERTY_ENABLE_ZOOM_CONTROLS));
		}
		return enabled;
	}
	
	@Override
	public void releaseViews()
	{
		// See Lighthouse #1936 - we can't allow the releasing
		// of the view because Android's WebViewCoreThread seems
		// to refer back to it in GC and freak out (crash the app)
		// if it's not there.
		// So we're just overriding and not calling super.
	}


}
