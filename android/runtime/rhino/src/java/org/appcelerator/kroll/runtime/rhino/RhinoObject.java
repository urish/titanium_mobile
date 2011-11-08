/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.kroll.runtime.rhino;

import org.appcelerator.kroll.KrollObject;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptableObject;

/**
 * Rhino implementation of KrollObject
 */
public class RhinoObject extends KrollObject
{
	private Proxy proxy;
	private Function emitFunction, setWindowFunction;

	public RhinoObject(Proxy proxy)
	{
		this.proxy = proxy;
	}

	@Override
	public Object getNativeObject()
	{
		return this.proxy;
	}

	@Override
	protected void setProperty(String name, Object value)
	{
		Context context = Context.enter();
		context.setOptimizationLevel(-1);

		try {
			ScriptableObject.putProperty(proxy.getProperties(), name, 
				TypeConverter.javaObjectToJsObject(value, proxy.getProperties()));

		} finally {
			Context.exit();
		}
	}

	@Override
	protected boolean fireEvent(String type, Object data)
	{
		Context context = Context.enter();
		context.setOptimizationLevel(-1);

		try {
			if (emitFunction == null) {
				emitFunction = (Function) ScriptableObject.getProperty(proxy, "emit");
			}

			Object jsData = TypeConverter.javaObjectToJsObject(data, proxy);
			Object[] args;

			if (jsData == null) {
				args = new Object[] { type };

			} else {
				args = new Object[] { type, jsData };
			}

			Object result = emitFunction.call(context, proxy.getParentScope(), proxy, args);
			return TypeConverter.jsObjectToJavaBoolean(result, proxy);

		} finally {
			Context.exit();
		}
	}

	@Override
	protected void doRelease()
	{
	}

	@Override
	protected void doSetWindow(Object windowProxy)
	{
		Context context = Context.enter();
		context.setOptimizationLevel(-1);

		try {
			if (setWindowFunction == null) {
				setWindowFunction = (Function) ScriptableObject.getProperty(proxy, "setWindow");
			}

			Object jsWindow = TypeConverter.javaObjectToJsObject(windowProxy, proxy);
			Object[] args = new Object[] { jsWindow };

			setWindowFunction.call(context, this.proxy.getParentScope(), proxy, args);

		} finally {
			Context.exit();
		}
	}
}