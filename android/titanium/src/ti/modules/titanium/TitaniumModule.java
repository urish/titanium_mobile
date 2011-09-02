/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;

import org.appcelerator.kroll.KrollInvocation;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.KrollModuleInfo;
import org.appcelerator.kroll.KrollObject;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.TiLaunchActivity;
import org.appcelerator.titanium.io.TiBaseFile;
import org.appcelerator.titanium.io.TiFileFactory;
import org.appcelerator.titanium.kroll.KrollCallback;
import org.appcelerator.titanium.kroll.KrollContext;
import org.appcelerator.titanium.kroll.KrollCoverage;
import org.appcelerator.titanium.proxy.TiWindowProxy;
import org.appcelerator.titanium.util.Log;
import org.appcelerator.titanium.util.TiConfig;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiPlatformHelper;
import org.appcelerator.titanium.util.TiRHelper;
import org.appcelerator.titanium.util.TiUIHelper;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import android.app.Activity;
import android.app.Service;
import android.os.Environment;
import android.os.Handler;

@Kroll.module @Kroll.topLevel({"Ti", "Titanium"})
public class TitaniumModule extends KrollModule
	implements TiContext.OnLifecycleEvent, TiContext.OnServiceLifecycleEvent
{
	private static final String LCAT = "TitaniumModule";
	private static final boolean DBG = TiConfig.LOGD;

	private Stack<String> basePath;
	private Map<String, NumberFormat> numberFormats = java.util.Collections.synchronizedMap(
		new HashMap<String, NumberFormat>());

	public TitaniumModule(TiContext tiContext)
	{
		super(tiContext);
		basePath = new Stack<String>();
		basePath.push(tiContext.getBaseUrl());
		if (tiContext.isServiceContext()) {
			tiContext.addOnServiceLifecycleEventListener(this);
		} else {
			tiContext.addOnLifecycleEventListener(this);
		}
	}

	@Kroll.getProperty @Kroll.method
	public String getUserAgent()
	{
		StringBuilder builder = new StringBuilder();
		String httpAgent = System.getProperty("http.agent");
		if (httpAgent != null) {
			builder.append(httpAgent);
		}
		builder.append(" Titanium/")
			.append(getVersion());
		return builder.toString();
	}

	@Kroll.getProperty @Kroll.method
	public String getVersion()
	{
		return getTiContext().getTiApp().getTiBuildVersion();
	}

	@Kroll.getProperty @Kroll.method
	public String getBuildTimestamp()
	{
		return getTiContext().getTiApp().getTiBuildTimestamp();
	}

	@Kroll.getProperty @Kroll.method
	public String getBuildDate()
	{
		return getTiContext().getTiApp().getTiBuildTimestamp();
	}

	@Kroll.getProperty @Kroll.method
	public String getBuildHash()
	{
		return getTiContext().getTiApp().getTiBuildHash();
	}

	// For testing exception handling.  Can remove after ticket 2032
	@Kroll.method
	public void testThrow(){ throw new Error("Testing throwing throwables"); }

	@Kroll.method
	public void include(KrollInvocation invocation, Object[] files)
	{
		TiContext tiContext = invocation.getTiContext();
		for(Object filename : files) {
			try {
				// we need to make sure paths included from sub-js files are actually relative
				boolean popContext = false;
				if (!basePath.contains(tiContext.getBaseUrl())) {
					basePath.push(tiContext.getBaseUrl());
					popContext = true;
				}
				String resolved = tiContext.resolveUrl(null, TiConvert.toString(filename), basePath.peek());
				basePath.push(resolved.substring(0, resolved.lastIndexOf('/')+1));
				tiContext.evalFile(resolved);
				basePath.pop();
				
				if (popContext) {
					basePath.pop();
				}
			} catch (IOException e) {
				Log.e(LCAT, "Error while evaluating: " + filename, e);
			}
		}
	}

	private HashMap<Thread, HashMap<Integer, Timer>> timers = new HashMap<Thread, HashMap<Integer, Timer>>();
	private int currentTimerId;

	protected class Timer implements Runnable
	{
		protected long timeout;
		protected boolean interval;
		protected Object[] args;
		protected KrollCallback callback;
		protected Handler handler;
		protected int id;
		protected boolean canceled;
	
		public Timer(int id, Handler handler, KrollCallback callback, long timeout, Object[] args, boolean interval)
		{
			this.id = id;
			this.handler = handler;
			this.callback = callback;
			this.timeout = timeout;
			this.args = args;
			this.interval = interval;
		}

		public void schedule()
		{
			handler.postDelayed(this, timeout);
		}

		@Override
		public void run()
		{
			if (canceled) return;
			if (DBG) {
				StringBuilder message = new StringBuilder("calling ")
					.append(interval ? "interval" : "timeout")
					.append(" timer ")
					.append(id)
					.append(" @")
					.append(new Date().getTime());
				Log.d(LCAT, message.toString());
			}
			long start = System.currentTimeMillis();
			callback.callSync(args);
			if (interval && !canceled) {
				handler.postDelayed(this, timeout - (System.currentTimeMillis() - start));
			}
		}

		public void cancel()
		{
			handler.removeCallbacks(this);
			canceled = true;
		}
	}

	private int createTimer(KrollContext context, Object fn, long timeout, Object[] args, boolean interval)
		throws IllegalArgumentException
	{
		// TODO: we should handle evaluatable code eventually too..
		if (fn instanceof KrollCallback) {
			KrollCallback callback = (KrollCallback) fn;
			int timerId = currentTimerId++;
			Handler handler = context.getMessageQueue().getHandler();

			Timer timer = new Timer(timerId, handler, callback, timeout, args, interval);
			Thread thread = handler.getLooper().getThread();
			HashMap<Integer, Timer> threadTimers = timers.get(thread);
			if (threadTimers == null) {
				threadTimers = new HashMap<Integer, Timer>();
				timers.put(thread, threadTimers);
			}
			threadTimers.put(timerId, timer);
			timer.schedule();
			return timerId;
		}
		else throw new IllegalArgumentException("Don't know how to call callback of type: " + fn.getClass().getName());
	}

	@Kroll.method @Kroll.topLevel
	public int setTimeout(KrollInvocation invocation, Object fn, long timeout, final Object[] args)
		throws IllegalArgumentException
	{
		return createTimer(invocation.getTiContext().getKrollContext(), fn, timeout, args, false);
	}

	@Kroll.method @Kroll.topLevel
	public void clearTimeout(int timerId)
	{
		for (Thread thread : timers.keySet()) {
			HashMap<Integer, Timer> threadTimers = timers.get(thread);
			if (threadTimers.containsKey(timerId)) {
				Timer timer = threadTimers.remove(timerId);
				timer.cancel();
				break;
			}
		}
	}

	@Kroll.method @Kroll.topLevel
	public int setInterval(KrollInvocation invocation, Object fn, long timeout, final Object[] args)
		throws IllegalArgumentException
	{
		return createTimer(invocation.getTiContext().getKrollContext(), fn, timeout, args, true);
	}

	@Kroll.method @Kroll.topLevel
	public void clearInterval(int timerId)
	{
		clearTimeout(timerId);
	}

	@Kroll.method @Kroll.topLevel
	public void alert(KrollInvocation invocation, Object message)
	{
		String msg = (message == null? null : message.toString());
		Log.i("ALERT", msg);
		if (invocation.getTiContext().isServiceContext()) {
			Log.w(LCAT, "alert() called inside service -- no attempt will be made to display it to user interface.");
			return;
		}
		TiUIHelper.doOkDialog("Alert", msg, null);
	}

	public void cancelTimers(TiBaseActivity activity)
	{
		TiWindowProxy window = activity.getWindowProxy();
		Thread thread = null;
		if (window != null) {
			thread = getKrollBridge().getKrollContext().getThread();
		} else {
			if (activity instanceof TiLaunchActivity) {
				TiLaunchActivity launchActivity = (TiLaunchActivity) activity;
				thread = launchActivity.getTiContext().getKrollContext().getThread();
			}
		}
		if (thread != null) {
			cancelTimers(thread);
		} else {
			Log.w(LCAT, "Tried cancelling timers for an activity with no associated JS thread: " + activity);
		}
	}

	public void cancelTimers(Thread thread)
	{
		HashMap<Integer, Timer> threadTimers = timers.get(thread);
		if (threadTimers == null) return;

		Iterator<Timer> timerIter = threadTimers.values().iterator();
		while (timerIter.hasNext()) {
			Timer timer = timerIter.next();
			if (timer != null) {
				timer.cancel();
				timerIter.remove();
			}
		}
		threadTimers.clear();
	}

	@Kroll.method @Kroll.topLevel("String.format")
	public String stringFormat(String format, Object args[])
	{
		try {
			// clean up formats for integers into doubles since thats how JS rolls
			format = format.replaceAll("%d", "%1.0f");
			// in case someone passes an iphone formatter symbol, convert
			format = format.replaceAll("%@", "%s");
			if (args.length == 0) {
				return String.format(format);
			} else {
				return String.format(format, args);
			}
		} catch (Exception ex) {
			Log.e(LCAT, "Error in string format", ex);
			return null;
		}
	}

	@Kroll.method @Kroll.topLevel("String.formatDate")
	public String stringFormatDate(Date date, @Kroll.argument(optional=true) String format)
	{
		int style = DateFormat.SHORT;
		if (format.equals("medium")) {
			style = DateFormat.MEDIUM;
		} else if (format.equals("long")) {
			style = DateFormat.LONG;
		}
		
		DateFormat fmt = DateFormat.getDateInstance(style);
		return fmt.format(date);
	}

	@Kroll.method @Kroll.topLevel("String.formatTime")
	public String stringFormatTime(Date time)
	{
		int style = DateFormat.SHORT;
		DateFormat fmt = DateFormat.getTimeInstance(style);
		return fmt.format(time);
	}

	@Kroll.method @Kroll.topLevel("String.formatCurrency")
	public String stringFormatCurrency(double currency)
	{
		return NumberFormat.getCurrencyInstance().format(currency);
	}

	@Kroll.method @Kroll.topLevel("String.formatDecimal")
	public String stringFormatDecimal(Object args[])
	{
		String pattern = null;
		String locale = null;
		if (args.length == 2) {
			// Is the second argument a locale string or a format string?
			String test = TiConvert.toString(args[1]);
			if (test != null && test.length() > 0) {
				if (test.contains(".") || test.contains("#") || test.contains("0")) {
					pattern = test;
				} else {
					locale = test;
				}
			}
		} else if (args.length >= 3) {
			// this is: stringFormatDecimal(n, locale_string, pattern_string);
			locale = TiConvert.toString(args[1]);
			pattern = TiConvert.toString(args[2]);
		}

		String key = (locale == null ? "" : locale ) + " keysep " + (pattern == null ? "": pattern);
		
		NumberFormat format;
		if (numberFormats.containsKey(key)) {
			format = numberFormats.get(key);
		} else {
			if (locale != null) {
				format = NumberFormat.getInstance(TiPlatformHelper.getLocale(locale));
			} else {
				format = NumberFormat.getInstance();
			}
		
			if (pattern != null && format instanceof DecimalFormat) {
				((DecimalFormat)format).applyPattern(pattern);
			}
			numberFormats.put(key, format);
		}

		return format.format((Number)args[0]);
	}

	@Kroll.method @Kroll.topLevel("L")
	public String localize(KrollInvocation invocation, Object args[])
	{
		String key = (String) args[0];
		String defaultValue = args.length > 1 ? (String) args[1] : null;
		try {
			int resid = TiRHelper.getResource("string." + key);
			if (resid != 0) {
				return invocation.getTiContext().getAndroidContext().getString(resid);
			} else {
				return defaultValue;
			}
		}
		catch (TiRHelper.ResourceNotFoundException e) {
			if (DBG) {
				Log.d(LCAT, "Resource string with key '" + key + "' not found.  Returning default value.");
			}
			return defaultValue;
		}
		catch (Exception e) {
			Log.e(LCAT, "Exception trying to localize string '" + key + "': ", e);
			return defaultValue;
		}
	}

	protected KrollModule requireNativeModule(TiContext context, String path)
	{
		if (DBG) {
			Log.d(LCAT, "Attempting to include native module: " + path);
		}
		KrollModuleInfo info = KrollModule.getModuleInfo(path);
		if (info == null) return null;

		return context.getTiApp().requireModule(context, info);
	}

	@Kroll.method @Kroll.topLevel
	public KrollProxy require(KrollInvocation invocation, String path)
	{
		// 1. look for a native module first
		// 2. then look for a cached module
		// 3. then attempt to load from resources
		TiContext ctx = invocation.getTiContext().getRootActivity().getTiContext();
		KrollModule module = requireNativeModule(ctx, path);
		StringBuilder builder = new StringBuilder();

		if (module != null) {
			KrollModuleInfo info = module.getModuleInfo();
			builder.append("Succesfully loaded module: ")
				.append(info.getName())
				.append("/")
				.append(info.getVersion());
			Log.i(LCAT, builder.toString());
			return module;
		}

		// NOTE: CommonJS modules load absolute to app:// in Titanium
		builder.setLength(0);
		builder.append(TiC.URL_APP_PREFIX)
			.append(path)
			.append(".js");
		String fileUrl = builder.toString();
		TiBaseFile tbf = TiFileFactory.createTitaniumFile(ctx, new String[]{ fileUrl }, false);
		if (tbf == null) {
			//the spec says we are required to throw an exception
			Context.reportError("Couldn't find module: " + path);
			return null;
		}

		if (DBG) {
			Log.d(LCAT, "Attempting to include JS module: " + tbf.nativePath());
		}
		try {
			TiBlob blob = (TiBlob) tbf.read();
			if (blob == null) {
				Log.e(LCAT, "Couldn't read required file: " + fileUrl);
				return null;
			}

			// TODO: we need to switch to the Rhino native require()
			// implementation, but in the meantime this will have to do

			// create the CommonJS exporter
			KrollProxy proxy = new KrollProxy(ctx);
			builder.setLength(0);
			builder.append("(function(exports){")
				.append(blob.getText())
				.append("return exports;")
				.append("})({})");

			Object result = ctx.evalJS(builder.toString());

			if (!(result instanceof Scriptable)) {
				builder.setLength(0);
				builder.append("Module did not correctly return an exports object: ")
					.append(path)
					.append(", result: ")
					.append(result);
				Context.throwAsScriptRuntimeEx(new Exception(builder.toString()));
				return null;
			}

			Scriptable exports = (Scriptable) result;
			// CommonJS modules export all functions/properties as 
			// properties of the special exports object provided
			for (Object key : exports.getIds()) {
				String propName = key.toString();
				proxy.setProperty(propName, exports.get(propName, exports));
			}

			// spec says you must have a read-only id property - we don't
			// currently support readonly in kroll so this is probably OK for now
			proxy.setProperty(TiC.PROPERTY_ID, path);
			// uri is optional but we point it to where we loaded it
			proxy.setProperty(TiC.PROPERTY_URI, fileUrl);
			return proxy;
		} catch (Exception ex) {
			Log.e(LCAT, "Error loading module named: " + path, ex);
			Context.throwAsScriptRuntimeEx(ex);
			return null;
		}
	}

	@Kroll.method
	public void dumpCoverage()
	{
		TiApplication app = getTiContext().getTiApp();
		if (app == null || !app.isCoverageEnabled()) {
			Log.w(LCAT, "Coverage is not enabled, no coverage data will be generated");
			return;
		}

		try {
			File extStorage = Environment.getExternalStorageDirectory();
			File reportFile = new File(new File(extStorage, app.getPackageName()), "coverage.json");
			FileOutputStream reportOut = new FileOutputStream(reportFile);
			KrollCoverage.writeCoverageReport(reportOut);
			reportOut.close();
		} catch (IOException e) {
			Log.e(LCAT, e.getMessage(), e);
		}
	}

	@Override
	public void onDestroy(Activity activity) {
		if (activity instanceof TiBaseActivity) {
			cancelTimers((TiBaseActivity) activity);
		}
		super.onDestroy(activity);
	}

	@Override
	public void onDestroy(Service service)
	{
	}

	@Override
	public KrollObject getKrollObject()
	{
		if (krollObject == null && coverageEnabled) {
			krollObject = new KrollCoverage("Titanium", this, null);
		}
		return super.getKrollObject();
	}
}
