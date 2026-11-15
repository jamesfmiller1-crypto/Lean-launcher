# Lean Launcher

A blocky pixel-world home screen for Android TV / Google TV.

- Your apps are blocks in an animated pixel landscape with day/night (follows the clock).
- Original creatures: Pebble (stone golem), Flits (birds), Bloop (peeks from a tree), Glowbugs (night).
- Clock and date, your chosen apps, "All apps" and "Settings".
- Animation pauses whenever another app is open.
- The only internet use is the weather, fetched from open-meteo.com (no account, no key). The world shows the real weather: clouds, rain or snow.
- Maintenance block: free storage, storage settings, app management, weather city.
- No libraries, a few hundred lines of code.

## Default apps

On first start it puts these on the home screen (if installed), in this order:
SmartTube, YouTube, Netflix, RTL+, IPTV Extreme, Premiumize.
Change the list in `DEFAULT_APPS` in `MainActivity.java`.

## Using it

- **OK**: open an app.
- **Hold OK** on a home tile: move left/right or remove.
- **All apps** → hold OK on an app: add it to / remove it from the home screen.

## Building

Every push to `main` builds a signed APK with GitHub Actions and publishes it as a release.
Add this repository's URL to Obtainium on the TV to install it and receive updates.

The signing key (`release.keystore`) is in the repo so builds are always signed the same way
and updates install cleanly.

## Making it the home screen

See the instructions in the chat, or:

    adb install LeanLauncher-*.apk
    # Google TV:
    adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
    # Android TV:
    adb shell pm disable-user --user 0 com.google.android.tvlauncher

Undo with `pm enable` instead of `pm disable-user`.
