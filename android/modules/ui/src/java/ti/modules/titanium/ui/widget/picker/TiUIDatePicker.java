/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget.picker;

import java.util.Calendar;
import java.util.Date;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiConfig;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.view.TiUIView;

import android.app.Activity;
import android.widget.DatePicker;
import android.widget.DatePicker.OnDateChangedListener;

public class TiUIDatePicker extends TiUIView
	implements OnDateChangedListener
{
	private boolean suppressChangeEvent = false;
	private static final String LCAT = "TiUIDatePicker";
	private static final boolean DBG = TiConfig.LOGD;

	protected Date minDate, maxDate;
	protected int minuteInterval;
	
	public TiUIDatePicker(TiViewProxy proxy)
	{
		super(proxy);
	}
	public TiUIDatePicker(TiViewProxy proxy, Activity activity)
	{
		this(proxy);
		if (DBG) {
			Log.d(LCAT, "Creating a date picker");
		}
		
		DatePicker picker = new DatePicker(activity);
		setNativeView(picker);
	}
	
	@Override
	public void processProperties(KrollDict d) {
		super.processProperties(d);
		
		boolean valueExistsInProxy = false;
		Calendar calendar = Calendar.getInstance();
        DatePicker picker = (DatePicker) getNativeView();

        if (d.containsKey("value")) {
        	calendar.setTime((Date) d.get("value"));
            valueExistsInProxy = true;
        }   
        if (d.containsKey("minDate")) {
        	Calendar minDateCalendar = Calendar.getInstance();
        	minDateCalendar.setTime((Date) d.get("minDate"));
        	minDateCalendar.set(Calendar.HOUR_OF_DAY, 0);
        	minDateCalendar.set(Calendar.MINUTE, 0);
        	minDateCalendar.set(Calendar.SECOND, 0);
        	minDateCalendar.set(Calendar.MILLISECOND, 0);

        	this.minDate = minDateCalendar.getTime();
        }
        if (d.containsKey("maxDate")) {
        	Calendar maxDateCalendar = Calendar.getInstance();
        	maxDateCalendar.setTime((Date) d.get("maxDate"));
        	maxDateCalendar.set(Calendar.HOUR_OF_DAY, 0);
        	maxDateCalendar.set(Calendar.MINUTE, 0);
        	maxDateCalendar.set(Calendar.SECOND, 0);
        	maxDateCalendar.set(Calendar.MILLISECOND, 0);

        	this.maxDate = maxDateCalendar.getTime();
        }
        if (d.containsKey("minuteInterval")) {
            int mi = d.getInt("minuteInterval");
            if (mi >= 1 && mi <= 30 && mi % 60 == 0) {
                this.minuteInterval = mi; 
            }
        }
        suppressChangeEvent = true;
        picker.init(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH), this);
        suppressChangeEvent = false;
        
        if (!valueExistsInProxy) {
        	proxy.setProperty("value", calendar.getTime());
        }
        
        //iPhone ignores both values if max <= min
        if (minDate != null && maxDate != null) {
            if (maxDate.compareTo(minDate) <= 0) {
                Log.w(LCAT, "maxDate is less or equal minDate, ignoring both settings.");
                minDate = null;
                maxDate = null;
            }   
        }
	}
	
	@Override
	public void propertyChanged(String key, Object oldValue, Object newValue,
			KrollProxy proxy)
	{
		if (key.equals("value"))
		{
			Date date = (Date)newValue;
			setValue(date.getTime());
		}
		super.propertyChanged(key, oldValue, newValue, proxy);
	}
	
	public void onDateChanged(DatePicker picker, int year, int monthOfYear, int dayOfMonth)
	{
    	Calendar targetCalendar = Calendar.getInstance();
    	targetCalendar.set(Calendar.YEAR, year);
    	targetCalendar.set(Calendar.MONTH, monthOfYear);
    	targetCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
    	targetCalendar.set(Calendar.HOUR_OF_DAY, 0);
    	targetCalendar.set(Calendar.MINUTE, 0);
    	targetCalendar.set(Calendar.SECOND, 0);
    	targetCalendar.set(Calendar.MILLISECOND, 0);

		if ((null != minDate) && (targetCalendar.getTime().before(minDate))) {
			targetCalendar.setTime(minDate);
			setValue(minDate.getTime(), true);
		}
		if ((null != maxDate) && (targetCalendar.getTime().after(maxDate))) {
			targetCalendar.setTime(maxDate);
			setValue(maxDate.getTime(), true);
		}
		if (!suppressChangeEvent) {
			KrollDict data = new KrollDict();
			data.put("value", targetCalendar.getTime());
			proxy.fireEvent("change", data);
		}
		proxy.setProperty("value", targetCalendar.getTime());
	}
	
	public void setValue(long value)
	{
		setValue(value, false);
	}
	
	public void setValue(long value, boolean suppressEvent)
	{
		DatePicker picker = (DatePicker) getNativeView();
		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(value);
		suppressChangeEvent = suppressEvent;
		picker.updateDate(calendar.get(Calendar.YEAR), calendar
				.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
		suppressChangeEvent = false;
	}
}
