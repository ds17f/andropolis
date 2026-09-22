# Micropolis Port — build & run on the Android emulator.
# Adapted from the harness in ~/Developer/micropolis-android.
#
# Targets:
#   make build      Build the debug APK (Gradle + NDK).
#   make run        Build, boot the emulator, install, and launch the app.
#   make install    Install the APK to a connected/running device.
#   make launch     Launch the app on a connected/running device.
#   make screenshot Grab a PNG from the device into build/screenshot.png.
#   make log        Tail logcat for the app.
#   make stop       Kill running emulators.
#   make clean      Delete the Gradle build output.
#
# Override: AVD=<avd> make run   HEADLESS=0 make run  (show the emulator window)

PKG      := micropolis.port
APK      := android/app/build/outputs/apk/debug/app-debug.apk
AVD      ?= Pixel_API_36
HEADLESS ?= 1

ANDROID_HOME ?= $(shell if [ -f android/local.properties ]; then sed -n "s/^sdk.dir=//p" android/local.properties; fi)
ADB      := $(ANDROID_HOME)/platform-tools/adb
EMULATOR := $(ANDROID_HOME)/emulator/emulator
GRADLE   := ./android/gradlew -p android

# Wait until the emulator reports fully booted (boot_completed fires after
# `adb wait-for-device`, which only waits for the daemon connection).
define wait_boot
@echo "Waiting for boot (AVD=$(AVD))..."
@$(ADB) wait-for-device
@for i in $$(seq 1 120); do \
    if [ "$$($(ADB) shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then \
        echo "Boot completed"; break; \
    fi; \
    sleep 2; \
done
endef

.PHONY: build run install launch screenshot log stop clean

build:
	$(GRADLE) :app:assembleDebug

install: build
	@$(ADB) wait-for-device
	$(ADB) install -r $(APK)

launch:
	@$(ADB) wait-for-device
	$(ADB) shell monkey -p $(PKG) -c android.intent.category.LAUNCHER 1

run: build
ifeq ($(HEADLESS),1)
	@pgrep -f "emulator.*$(AVD)" >/dev/null || $(EMULATOR) -avd $(AVD) -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect &
else
	@pgrep -f "emulator.*$(AVD)" >/dev/null || $(EMULATOR) -avd $(AVD) &
endif
	$(call wait_boot)
	$(ADB) install -r $(APK)
	@$(ADB) shell monkey -p $(PKG) -c android.intent.category.LAUNCHER 1

screenshot:
	@mkdir -p build
	$(ADB) exec-out screencap -p > build/screenshot.png
	@echo "wrote build/screenshot.png"

log:
	$(ADB) logcat --pid=$$($(ADB) shell pidof $(PKG))

stop:
	@pkill -f "emulator.*$(AVD)" || true

clean:
	$(GRADLE) clean
