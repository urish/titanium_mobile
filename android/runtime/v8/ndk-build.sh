#!/bin/sh
#
# Appcelerator Titanium Mobile
# Copyright (c) 2011 by Appcelerator, Inc. All Rights Reserved.
# Licensed under the terms of the Apache Public License
# Please see the LICENSE included with this distribution for details.

if [ "$ANDROID_NDK" = "" ]; then
	echo "Error: The path to the Android NDK must be set in the ANDROID_NDK environment variable"
	exit 1
fi

THIS_DIR=$(cd "$(dirname "$0")"; pwd)

ARGS=
if [ "$NUM_CPUS" != "" ]; then
	ARGS="-j $NUM_CPUS"
fi

"$ANDROID_NDK/ndk-build" \
	NDK_APPLICATION_MK=$THIS_DIR/Application.mk \
	NDK_PROJECT_PATH=$THIS_DIR \
	NDK_MODULE_PATH=$THIS_DIR/src/ndk-modules \
	TI_DIST_DIR=$(cd "$THIS_DIR/../../../dist"; pwd) \
	$ARGS \
	$@
