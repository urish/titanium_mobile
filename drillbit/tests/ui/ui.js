describe("Ti.UI tests", {

	// https://appcelerator.lighthouseapp.com/projects/32238-titanium-mobile/tickets/2583
	webviewEvalJSLockup: asyncTest( {
		start: function(callback) {
			var w = Ti.UI.createWindow();
			w.open();
			var wv = Ti.UI.createWebView({top: 0, width: 10, height: 10, url: 'test.html'});
			var listener = this.async(function(){
				valueOf(wv.evalJS('Mickey')).shouldBe('');
				//w.close();
			});
			wv.addEventListener('load', listener);
			w.add(wv);
		},
		timeout: 10000,
		timeoutError: 'Timed out waiting for page to load and JS to eval'
	}),
	//https://appcelerator.lighthouseapp.com/projects/32238-titanium-mobile/tickets/1036
	webviewBindingUnavailable: asyncTest( {
		start: function(callback) {
			var w = Ti.UI.createWindow();
			w.open();
			var wv = Ti.UI.createWebView({top: 0, width: 10, height: 10, url: 'http://www.google.com'});
			var listener = this.async(function(){
				valueOf(wv.evalJS('Titanium')).shouldBe('');
				//w.close();
			});
			wv.addEventListener('load', listener);
			w.add(wv);
		},
		timeout: 10000,
		timeoutError: 'Timed out waiting for page to load and JS to eval'
	}),
	// https://appcelerator.lighthouseapp.com/projects/32238-titanium-mobile/tickets/2153
	webviewBindingAvailable: asyncTest( {
		start: function(callback) {
			var w = Ti.UI.createWindow();
			w.open();
			var wv = Ti.UI.createWebView({top: 0, width: 10, height: 10, url: 'test.html'});
			var listener = this.async(function(){
				valueOf(wv.evalJS('typeof Titanium')).shouldBe('object');
				//w.close();
			});
			wv.addEventListener('load', listener);
			w.add(wv);
		},
		timeout: 10000,
		timeoutError: 'Timed out waiting for page to load and JS to eval'
	}),
	webviewBindingAvailableAfterSetHtml: asyncTest( {
		start: function(callback) {
			var w = Ti.UI.createWindow();
			w.open();
			var wv = Ti.UI.createWebView({top: 0, width: 10, height: 10});
			var listener = this.async(function(){
				valueOf(wv.evalJS('typeof Titanium')).shouldBe('object');
				//w.close();
			});
			wv.addEventListener('load', listener);
			w.add(wv);
			wv.html = "<html><body>x</body></html>";
		},
		timeout: 10000,
		timeoutError: 'Timed out waiting for page to load and JS to eval'
	}),

	//https://appcelerator.lighthouseapp.com/projects/32238/tickets/2443-android-paths-beginning-with-are-not-recognised
	dotslashWindow: function() {
		var w = Ti.UI.createWindow({url:'./testwin.js'});
		valueOf(function(){w.open();}).shouldNotThrowException();
	},

	// https://appcelerator.lighthouseapp.com/projects/32238-titanium-mobile/tickets/2230-android-resolve-url-failing-from-event-context#ticket-2230-6
	absoluteAndRelativeWinURLs: asyncTest( {
		start: function(callback) {
			var w = Ti.UI.createWindow({ url: 'dir/relative.js' });
			w.addEventListener("close", this.async(function() {
				valueOf(true).shouldBe(true);
			}));
			w.open();
		},
		timeout: 10000,
		timeoutError: 'Timed out waiting for relative and absolute window to auto close'
	}),
	// https://appcelerator.lighthouseapp.com/projects/32238-titanium-mobile/tickets/873
	appendRowWithHeader_as_async: function(callback) {
		var w = Ti.UI.createWindow();
		w.open();
		var data = [Ti.UI.createTableViewRow({title: 'blah'})];
		var tv = Ti.UI.createTableView({data:data});
		w.add(tv);
		setTimeout(function(){
			tv.appendRow( Ti.UI.createTableViewRow({title:'blah2', header:'header1'}) );
			setTimeout(function() {
				valueOf(tv.data.length).shouldBe(2);
				callback.passed();
			}, 1000);
		},1000);
	},

	appendRowAsArray: asyncTest(function(callback) {
		var w = Ti.UI.createWindow();
		var tv = Ti.UI.createTableView();
		w.add(tv);

		var listener = this.async(function(){
			var rows = [];
			rows.push(Ti.UI.createTableViewRow({title:'title 1'}));
			rows.push(Ti.UI.createTableViewRow({title:'title 2'}));
			rows.push(Ti.UI.createTableViewRow({title:'title 3'}));

			valueOf(function(){tv.appendRow(rows);}).shouldNotThrowException();
			valueOf(tv.data[0].rowCount).shouldBe(rows.length);
		});
		w.addEventListener("open", listener);
		w.open();
	}),

	// http://jira.appcelerator.org/browse/TIMOB-2853
	opacityCrash_as_async: function(callback) {
		var failureTimeout = null;
		var w = Ti.UI.createWindow();
		var btn = Ti.UI.createImageView({
			opacity: 1,
			image: 'KS_nav_ui.png',
			top: 1, width: 50, left: 1, height: 40
		});
		w.add( btn );
		w.addEventListener('open', function() {
			setTimeout(function(){
				if (failureTimeout !== null) {
					clearTimeout(failureTimeout);
				}
				callback.passed();
			}, 1000);
		});
		failureTimeout = setTimeout(function(){
			callback.failed("Test may have crashed app.  Opacity of 1 test.");
		},3000);
		w.open();

	},

	windowOrientation: function() {
		var w = Ti.UI.createWindow();
		valueOf(w.orientation).shouldBeOneOf([Ti.UI.PORTRAIT, Ti.UI.LANDSCAPE_LEFT]);
	},
	
	windowPixelFormat: function() {
		if (Ti.Platform.name === 'android') {
			var w = Ti.UI.createWindow();
			valueOf(w.getWindowPixelFormat).shouldBeFunction();
			valueOf(w.setWindowPixelFormat).shouldBeFunction();
			valueOf("windowPixelFormat" in w).shouldBeTrue();
			
			valueOf(w.windowPixelFormat).shouldBe(Ti.UI.Android.PIXEL_FORMAT_UNKNOWN);
			valueOf(w.getWindowPixelFormat()).shouldBe(Ti.UI.Android.PIXEL_FORMAT_UNKNOWN);
			
			w.windowPixelFormat = Ti.UI.Android.PIXEL_FORMAT_RGB_565;
			valueOf(w.windowPixelFormat).shouldBe(Ti.UI.Android.PIXEL_FORMAT_RGB_565);
			valueOf(w.getWindowPixelFormat()).shouldBe(Ti.UI.Android.PIXEL_FORMAT_RGB_565);
			
			w.setWindowPixelFormat(Ti.UI.Android.PIXEL_FORMAT_RGBA_8888);
			valueOf(w.windowPixelFormat).shouldBe(Ti.UI.Android.PIXEL_FORMAT_RGBA_8888);
			valueOf(w.getWindowPixelFormat()).shouldBe(Ti.UI.Android.PIXEL_FORMAT_RGBA_8888);
		}
	}
});
