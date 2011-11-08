package org.appcelerator.titanium;

import java.util.Collection;
import java.util.HashMap;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiConfig;

public abstract class TiStylesheet {
	private static final String TAG = "TiStylesheet";
	private static final boolean DBG = TiConfig.DEBUG;
	
	protected final HashMap<String,HashMap<String,KrollDict>> classesMap;
	protected final HashMap<String,HashMap<String,KrollDict>> idsMap;
	protected final HashMap<String,HashMap<String,HashMap<String,KrollDict>>> classesDensityMap;
	protected final HashMap<String,HashMap<String,HashMap<String,KrollDict>>> idsDensityMap;
	
	// The concrete implementation fills these
	public TiStylesheet() {
		classesMap = new HashMap<String,HashMap<String,KrollDict>>();
		idsMap = new HashMap<String,HashMap<String,KrollDict>>();
		classesDensityMap = new HashMap<String, HashMap<String,HashMap<String,KrollDict>>>();
		idsDensityMap = new HashMap<String, HashMap<String,HashMap<String,KrollDict>>>();
	}

	protected void addAll(KrollDict result, HashMap<String, KrollDict> map, String key) {
		if (map != null) {
			KrollDict d = map.get(key);
			if (d != null) {
				result.putAll(d);
			}
		}
	}
	
	public final KrollDict getStylesheet(String objectId, Collection<String> classes, String density, String basename)
	{
		if (DBG) {
			Log.d(TAG, "getStylesheet id: "+objectId+", classes: "+classes+", density: " + density + ", basename: " + basename);
		}
		
		KrollDict result = new KrollDict();
		if (classesMap != null)
		{
			HashMap<String, KrollDict> classMap = classesMap.get(basename);
			HashMap<String, KrollDict> globalMap = classesMap.get("global");
			if (globalMap != null || classMap != null) {
				for (String clazz : classes) {
					addAll(result, globalMap, clazz);
					addAll(result, classMap, clazz);
				}
			}
		}
		if (classesDensityMap != null)
		{
			HashMap<String,KrollDict> globalDensityMap = null;
			if (classesDensityMap.containsKey("global")) {
				globalDensityMap = classesDensityMap.get("global").get(density);
			}

			HashMap<String, KrollDict> classDensityMap = null;
			if (classesDensityMap.containsKey(basename)) {
				classDensityMap = classesDensityMap.get(basename).get(density);
			}

			if (globalDensityMap != null || classDensityMap != null) {
				for (String clazz : classes) {
					addAll(result, globalDensityMap, clazz);
					addAll(result, classDensityMap, clazz);
				}
			}
		}
		if (idsMap != null && objectId != null)
		{
			addAll(result, idsMap.get("global"), objectId);
			addAll(result, idsMap.get(basename), objectId);
		}
		if (idsDensityMap != null && objectId != null)
		{
			HashMap<String,KrollDict> globalDensityMap = null;
			if (idsDensityMap.containsKey("global")) {
				globalDensityMap = idsDensityMap.get("global").get(density);
			}
			HashMap<String,KrollDict> idDensityMap = null;
			if (idsDensityMap.containsKey(basename)) {
				idDensityMap = idsDensityMap.get(basename).get(density);
			}
			addAll(result, globalDensityMap, objectId);
			addAll(result, idDensityMap, objectId);
		}
		return result;
	}
}
