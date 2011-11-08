/*
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#include <android/log.h>
#include <v8.h>
#include <string.h>

#include "AndroidUtil.h"

#include "APIModule.h"
#include "V8Util.h"
#include "org.appcelerator.kroll.KrollModule.h"

namespace titanium {

using namespace v8;

#define LOG_LEVEL_TRACE 1
#define LOG_LEVEL_DEBUG 2
#define LOG_LEVEL_INFO 3
#define LOG_LEVEL_NOTICE 4
#define LOG_LEVEL_WARN 5
#define LOG_LEVEL_ERROR 6
#define LOG_LEVEL_CRITICAL 7
#define LOG_LEVEL_FATAL 8

#define LCAT "TiAPI"

Persistent<FunctionTemplate> APIModule::constructorTemplate;


void APIModule::Initialize(Handle<Object> target)
{
	HandleScope scope;
	constructorTemplate = Persistent<FunctionTemplate>::New(FunctionTemplate::New());
	DEFINE_PROTOTYPE_METHOD(constructorTemplate, "debug", logDebug);
	DEFINE_PROTOTYPE_METHOD(constructorTemplate, "info", logInfo);
	DEFINE_PROTOTYPE_METHOD(constructorTemplate, "warn", logWarn);
	DEFINE_PROTOTYPE_METHOD(constructorTemplate, "error", logError);
	DEFINE_PROTOTYPE_METHOD(constructorTemplate, "trace", logTrace);
	DEFINE_PROTOTYPE_METHOD(constructorTemplate, "notice", logNotice);
	DEFINE_PROTOTYPE_METHOD(constructorTemplate, "critical", logCritical);
	DEFINE_PROTOTYPE_METHOD(constructorTemplate, "fatal", logFatal);
	DEFINE_PROTOTYPE_METHOD(constructorTemplate, "log", log);
	constructorTemplate->Inherit(KrollModule::proxyTemplate);

	target->Set(String::NewSymbol("API"), constructorTemplate->GetFunction()->NewInstance());
}


Handle<Value> APIModule::logDebug(const Arguments& args)
{
	String::Utf8Value message(args[0]);
	APIModule::logInternal(LOG_LEVEL_DEBUG, LCAT, *message);
	return Undefined();
}


Handle<Value> APIModule::logInfo(const Arguments& args)
{
	String::Utf8Value message(args[0]);
	APIModule::logInternal(LOG_LEVEL_INFO, LCAT, *message);
	return Undefined();
}


Handle<Value> APIModule::logWarn(const Arguments& args)
{
	String::Utf8Value message(args[0]);
	APIModule::logInternal(LOG_LEVEL_WARN, LCAT, *message);
	return Undefined();
}


Handle<Value> APIModule::logError(const Arguments& args)
{
	String::Utf8Value message(args[0]);
	APIModule::logInternal(LOG_LEVEL_ERROR, LCAT, *message);
	return Undefined();
}


Handle<Value> APIModule::logTrace(const Arguments& args)
{
	String::Utf8Value message(args[0]);
	APIModule::logInternal(LOG_LEVEL_TRACE, LCAT, *message);
	return Undefined();
}


Handle<Value> APIModule::logNotice(const Arguments& args)
{
	String::Utf8Value message(args[0]);
	APIModule::logInternal(LOG_LEVEL_NOTICE, LCAT, *message);
	return Undefined();
}


Handle<Value> APIModule::logCritical(const Arguments& args)
{
	String::Utf8Value message(args[0]);
	APIModule::logInternal(LOG_LEVEL_CRITICAL, LCAT, *message);
	return Undefined();
}


Handle<Value> APIModule::logFatal(const Arguments& args)
{
	String::Utf8Value message(args[0]);
	APIModule::logInternal(LOG_LEVEL_FATAL, LCAT, *message);
	return Undefined();
}


void APIModule::logInternal(int logLevel, const char *messageTag, const char *message)
{
	if (logLevel == LOG_LEVEL_TRACE) {
		LOG(VERBOSE, messageTag, message);
	} else if (logLevel < LOG_LEVEL_INFO) {
		LOG(DEBUG, messageTag, message);
	} else if (logLevel < LOG_LEVEL_WARN) {
		LOG(INFO, messageTag, message);
	} else if (logLevel == LOG_LEVEL_WARN) {
		LOG(WARN, messageTag, message);
	} else {
		LOG(ERROR, messageTag, message);
	}
}


Handle<Value> APIModule::log(const Arguments& args)
{
	String::Utf8Value level(args[0]);
	String::Utf8Value message(args[1]);

	if (strcasecmp(*level, "TRACE") == 0) {
		APIModule::logInternal(LOG_LEVEL_TRACE, LCAT, *message);
	} else if (strcasecmp(*level, "DEBUG") == 0) {
		APIModule::logInternal(LOG_LEVEL_DEBUG, LCAT, *message);
	} else if (strcasecmp(*level, "INFO") == 0) {
		APIModule::logInternal(LOG_LEVEL_INFO, LCAT, *message);
	} else if (strcasecmp(*level, "NOTICE") == 0) {
		APIModule::logInternal(LOG_LEVEL_NOTICE, LCAT, *message);
	} else if (strcasecmp(*level, "WARN") == 0) {
		APIModule::logInternal(LOG_LEVEL_WARN, LCAT, *message);
	} else if (strcasecmp(*level, "ERROR") == 0) {
		APIModule::logInternal(LOG_LEVEL_ERROR, LCAT, *message);
	} else if (strcasecmp(*level, "CRITICAL") == 0) {
		APIModule::logInternal(LOG_LEVEL_CRITICAL, LCAT, *message);
	} else if (strcasecmp(*level, "FATAL") == 0) {
		APIModule::logInternal(LOG_LEVEL_FATAL, LCAT, *message);
	} else {
		APIModule::logInternal(LOG_LEVEL_INFO, LCAT, *message);
	}

	return Undefined();
}

}
