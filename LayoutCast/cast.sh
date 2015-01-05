#!/bin/bash
set -e

adb forward tcp:41128 tcp:41128
mkdir -p bin/lcast/values
curl --silent --output bin/lcast/values/ids.xml http://127.0.0.1:41128/ids.xml
curl --silent --output bin/lcast/values/public.xml http://127.0.0.1:41128/public.xml
aapt package -f --auto-add-overlay -F bin/res.zip -S bin/lcast -S res/ -M AndroidManifest.xml -I /Applications/android-sdk-mac_86/platforms/android-19/android.jar
curl -T bin/res.zip http://localhost:41128/pushres
curl http://localhost:41128/lcast
