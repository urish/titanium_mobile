/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
var tiBinding = kroll.binding('Titanium'),
	Titanium = tiBinding.Titanium,
	Proxy = tiBinding.Proxy,
	assets = kroll.binding('assets'),
	Script = kroll.binding('evals').Script,
	bootstrap = require('bootstrap'),
	path = require('path'),
	url = require('url');

var TAG = "Titanium";

//the app entry point
Titanium.sourceUrl = "app://app.js";

//a list of java APIs that need an invocation-specific URL
//passed in as the first argument
Titanium.invocationAPIs = [];

//Define lazy initializers for all Titanium APIs
bootstrap.bootstrap(Titanium);

// Custom JS extensions to Java modules
require("ui").bootstrap(Titanium);

var Properties = require("properties");
Properties.bootstrap(Titanium);

//Custom native modules
bootstrap.defineLazyBinding(Titanium, "API")

// Used just to differentiate scope vars on java side by checking
// constructor name
function ScopeVars() {};
// Context-bound modules -------------------------------------------------
//
// Specialized modules that require binding context specific data
// within a script execution scope. This is how Ti.UI.currentWindow,
// Ti.Android.currentActivity, and others are implemented.
function TitaniumWrapper(context) {
	var sourceUrl = this.sourceUrl = context.sourceUrl;

	// Special version of include to handle relative paths based on sourceUrl.
	this.include = function() {
		var baseUrl, context;
		var fileCount = arguments.length;

		var info = arguments[fileCount - 1];
		var baseUrl, context;
		if (info instanceof Array) {
			fileCount--;
			baseUrl = info[0];
			context = info[1];
		} else {
			baseUrl = sourceUrl;
		}

		for (var i = 0; i < fileCount; i++) {
			TiInclude(arguments[i], baseUrl, context);
		}
	}

	this.Android = new AndroidWrapper(context);
	this.UI = new UIWrapper(context, this.Android);

	var scopeVars = new ScopeVars();
	scopeVars.sourceUrl = sourceUrl;
	scopeVars.currentActivity = this.Android.currentActivity;

	Titanium.bindInvocationAPIs(this, scopeVars);
}
TitaniumWrapper.prototype = Titanium;
Titanium.Wrapper = TitaniumWrapper;

function UIWrapper(context, Android) {
	this.currentWindow = context.currentWindow;
	this.currentTab = context.currentTab;
	this.currentTabGroup = context.currentTabGroup;

	if (!context.currentWindow && Android.currentActivity) {
		this.currentWindow = Android.currentActivity.window;
	}
}
UIWrapper.prototype = Titanium.UI;

function AndroidWrapper(context) {
	this.currentActivity = context.currentActivity;
	var currentWindow = context.currentWindow;

	if (!this.currentActivity) {
		var topActivity;
		if (currentWindow && currentWindow.window && currentWindow.window.activity) {
			//Titanium.API.debug("getting activity from currentWindow: " + currentWindow.activity);
			this.currentActivity = currentWindow.activity;

		} else if (topActivity = Titanium.App.Android.getTopActivity()) {
			//Titanium.API.debug("getting activity from top activity: " + topActivity);
			this.currentActivity = topActivity;
		}
	}
}
AndroidWrapper.prototype = Titanium.Android;

// -----------------------------------------------------------------------

function createSandbox(ti) {
	var sandbox = { Ti: ti, Titanium: ti };

	if (kroll.runtime == "rhino") {
		return kroll.createSandbox(sandbox);
	}

	return sandbox;
}

function TiInclude(filename, baseUrl, context) {
	var sourceUrl = url.resolve(baseUrl || Titanium.sourceUrl, filename);
	var contextUrl = sourceUrl.href;

	// Create a context-bound Titanium module.
	if (kroll.runtime == "rhino") {
		contextUrl = require("rhino").getSourceUrl(sourceUrl);
	}

	var context = context || {};
	context.sourceUrl = contextUrl;
	var ti = new TitaniumWrapper(context);

	sandbox = createSandbox(ti);

	if (kroll.runtime == 'rhino') {
		return require("rhino").include(filename, baseUrl, sandbox);
	}

	var source;

	// Load the source code for the script.
	if (!('protocol' in sourceUrl)) {
		source = assets.readAsset(filename);
	} else if (sourceUrl.filePath) {
		var filepath = url.toFilePath(sourceUrl);
		source = assets.readFile(filepath);
	} else if (sourceUrl.assetPath) {
		var assetPath = url.toAssetPath(sourceUrl);
		source = assets.readAsset(assetPath);
	} else {
		throw new Error("Unable to load source for filename: " + filename);
	}

	var wrappedSource = "with(sandbox) { " + source + "\n }";
	return Script.runInThisContext(wrappedSource, sourceUrl.href, true);
}
TiInclude.prototype = global;
Titanium.include = TiInclude;

Titanium.bindInvocationAPIs = function(sandboxTi, scopeVars) {
	// This loops through all known APIs that require an
	// Invocation object and wraps them so we can pass a
	// source URL as the first argument

	function genInvoker(invocationAPI) {
		var namespace = invocationAPI.namespace;
		var names = namespace.split(".");
		var length = names.length;
		if (namespace == "Titanium") {
			length = 0;
		}

		var apiNamespace = sandboxTi;
		var realAPI = tiBinding.Titanium;

		for (var j = 0, namesLen = length; j < namesLen; ++j) {
			var name = names[j];
			var api;

			// Create a module wrapper only if it hasn't been wrapped already.
			if (apiNamespace.hasOwnProperty(name)) {
				api = apiNamespace[name];
			} else {
				function SandboxAPI() {}
				SandboxAPI.prototype = apiNamespace[name];

				api = new SandboxAPI();
				apiNamespace[name] = api;
			}

			apiNamespace = api;
			realAPI = realAPI[name];
		}

		var delegate = realAPI[invocationAPI.api];

		// These invokers form a call hierarchy so we need to
		// provide a way back to the actual root Titanium / actual impl.
		while (delegate.__delegate__) {
			delegate = delegate.__delegate__;
		}

		function createInvoker(delegate) {
			var urlInvoker = function invoker() {
				var args = Array.prototype.slice.call(arguments);
				args.splice(0, 0, invoker.scopeVars);

				return delegate.apply(invoker.__thisObj__, args);
			}

			urlInvoker.scopeVars = scopeVars;
			urlInvoker.__delegate__ = delegate;
			urlInvoker.__thisObj__ = realAPI;

			return urlInvoker;
		}

		apiNamespace[invocationAPI.api] = createInvoker(delegate);
	}

	var len = Titanium.invocationAPIs.length;
	for (var i = 0; i < len; ++i) {
		// separate each invoker into it's own private scope
		genInvoker(Titanium.invocationAPIs[i]);
	}
}

Titanium.Proxy = Proxy;

// Use defineProperty so we can avoid our custom extensions being enumerated
Object.defineProperty(Object.prototype, "extend", {
	value: function(other) {
		if (!other) return;
	
		for (var name in other) {
			if (other.hasOwnProperty(name)) {
				this[name] = other[name];
			}
		}
		return this;
	},
	enumerable: false
});

Proxy.defineProperties = function(proxyPrototype, names) {
	var properties = {};
	var len = names.length;

	for (var i = 0; i < len; ++i) {
		var name = names[i];
		properties[name] = {
			get: function() { return this.getProperty(name); },
			set: function(value) { this.setPropertyAndFire(name, value); },
			enumerable: true
		};
	}

	Object.defineProperties(proxyPrototype, properties);
}

Object.defineProperty(Proxy.prototype, "getProperty", {
	value: function(property) {
		return this._properties[property];
	},
	enumerable: false
});

Object.defineProperty(Proxy.prototype, "setProperty", {
	value: function(property, value) {
		return this._properties[property] = value;
	},
	enumerable: false
});

Object.defineProperty(Proxy.prototype, "setPropertiesAndFire", {
	value: function(properties) {
		var ownNames = Object.getOwnPropertyNames(properties);
		var len = ownNames.length;
		var changes = [];

		for (var i = 0; i < len; ++i) {
			var property = ownNames[i];
			var value = properties[property];

			if (!property) continue;

			var oldValue = this._properties[property];
			this._properties[property] = value;

			if (value != oldValue) {
				changes.push([property, oldValue, value]);
			}
		}

		if (changes.length > 0) {
			this.onPropertiesChanged(changes);
		}
	},
	enumerable: false
});

// Custom native modules
bootstrap.defineLazyBinding(Titanium, "API");

Object.defineProperty(Titanium, "Yahoo", {
	get: function() {
		delete this.Yahoo;
		delete this.__proto__.Yahoo;

		var value = require("yahoo").bootstrap(Titanium);
		this.Yahoo = this.__proto__.Yahoo = value;

		return value;
	}
});

// Do not serialize the parent view. Doing so will result
// in a circular reference loop.
Object.defineProperty(Titanium.TiView.prototype, "toJSON", {
	value: function () {
		var keys = Object.keys(this);
		var keyCount = keys.length;
		var serialized = {};

		for (var i = 0; i < keyCount; i++) {
			var k = keys[i];
			if (k === "parent") {
				continue;
			}
			serialized[k] = this[k];
		}

		return serialized;
	},
	enumerable: false
});

Object.defineProperty(Titanium.Activity.prototype, "toJSON", {
	value: function () {
		var keys = Object.keys(this);
		var keyCount = keys.length;
		var serialized = {};

		for (var i = 0; i < keyCount; i++) {
			var k = keys[i];
			if (k === "activity" || k === "window" || k === "intent") {
				continue;
			}
			serialized[k] = this[k];
		}

		return serialized;
	},
	enumerable: false
});

module.exports = new TitaniumWrapper({
	sourceUrl: Titanium.sourceUrl
});
