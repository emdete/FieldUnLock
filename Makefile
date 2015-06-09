.PHONY: all run dbg clean gradle

all:
	./gradlew -q assembleRelease

run:
	./gradlew -q assembleDebug

dbg: run
	chmod 0644 art/FieldUnlock-logo.png build/outputs/apk/*-debug.apk
	rsync --verbose --archive art/FieldUnlock-logo.png build/outputs/apk/*-debug.apk littlelun.emdete.de:/var/www/pyneo.org/c/.
	#adb uninstall org.pyneo.android.gui
	adb install -r build/outputs/apk/*-debug.apk
	#adb shell am start org.pyneo.android.gui/.Sample

clean:
	./gradlew -q clean

art:
	make -C art

