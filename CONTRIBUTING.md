# Contributing

Thanks for wanting to help. This is a small project and it is meant to stay that way.

## The shape of the code

- `assets/index.html` is the entire interface — HTML, CSS and JS in one file, no build step,
  no framework, no dependencies. It runs in any modern browser, which is the easiest way to
  iterate: open it directly, seed `localStorage["liquid.reminders.v1"]` with a JSON array of
  reminders, and reload.
- `java/com/liquid/reminders/` is the native shell. Four files, no third-party libraries.
  `MainActivity` hosts the WebView and the JS bridge, `ReminderScheduler` owns the alarm and
  notification logic, and the two receivers handle firing and re-arming after a reboot.
- `res/` holds the theme, the launcher icon and the notification icon.

## Building and testing

```bash
export ANDROID_HOME=/path/to/android-sdk   # build-tools 34 + platform 34, JDK 17
./build.sh                                 # -> build/Reminders.apk
adb install -r build/Reminders.apk
```

There is no test suite. If you change alarm scheduling, check it on a real device with the app
closed and the screen off — that is the case that breaks.

## Sending a change

1. Open an issue first for anything bigger than a bug fix, so we can agree on the approach.
2. Keep the front end a single self-contained file. Do not introduce a bundler.
3. Match the existing style: no external dependencies, no Gradle, no AndroidX.
4. Describe what you tested on in the pull request.

## Licence

By contributing you agree your work is released under the [MIT licence](LICENSE).
