#!/bin/bash

aapt package -f -F bin/res.zip -S res/ -M AndroidManifest.xml -I /Applications/android-sdk-mac_86/platforms/android-19/android.jar
adb forward tcp:41128 tcp:41128
curl -T bin/res.zip http://localhost:41128/pushres
curl http://localhost:41128/lcast
