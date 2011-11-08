/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui;

import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.view.TiUIView;

import ti.modules.titanium.ui.widget.TiUIText;
import android.app.Activity;

@Kroll.proxy(creatableInModule=UIModule.class, propertyAccessors = {
	TiC.PROPERTY_AUTOCAPITALIZATION,
	TiC.PROPERTY_AUTOCORRECT,
	TiC.PROPERTY_CLEAR_ON_EDIT,
	TiC.PROPERTY_COLOR,
	TiC.PROPERTY_EDITABLE,
	TiC.PROPERTY_ELLIPSIZE,
	TiC.PROPERTY_ENABLE_RETURN_KEY,
	TiC.PROPERTY_FONT,
	TiC.PROPERTY_HINT_TEXT,
	TiC.PROPERTY_KEYBOARD_TYPE,
	TiC.PROPERTY_PASSWORD_MASK,
	TiC.PROPERTY_TEXT_ALIGN,
	TiC.PROPERTY_VALUE,
	TiC.PROPERTY_VERTICAL_ALIGN,
	TiC.PROPERTY_RETURN_KEY_TYPE
})
public class TextFieldProxy extends TiViewProxy
{
	public TextFieldProxy()
	{
		super();
	}

	public TextFieldProxy(TiContext tiContext)
	{
		this();
	}

	@Override
	public void handleCreationArgs(KrollModule createdInModule, Object[] args) {
		super.handleCreationArgs(createdInModule, args);

		setProperty(TiC.PROPERTY_VALUE, "");
	}

	@Override
	public TiUIView createView(Activity activity)
	{
		return new TiUIText(this, true);
	}
}
