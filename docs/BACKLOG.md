# Backlog

The queue for the scheduled maintenance runs (1-20 November, ten runs a day).

**Rules for every run**

- Take the **first unchecked item**. Do one item per run, not more.
- Never change the visual design, layout, spacing, colours or wording of the interface.
  "Better" means faster, smoother, safer — not different looking.
- Never add a dependency, a framework or a bundler. `assets/index.html` stays one self-contained file.
- Never touch the signing key, the package name or the version code policy.
- Verify before committing: the page must load in headless Chromium with no console errors, the
  alarm bridge must still be called, and `./build.sh` must produce a signed APK.
- If anything fails, revert and leave the item unchecked. A failed run that changes nothing is fine;
  a broken app is not.
- Tick the box and add one line under "Done" saying what changed.

Items marked **[release]** also cut a GitHub release with the rebuilt APK.

---

## Performance

- [ ] rAF-throttle the specular `pointermove` handler — it calls `getBoundingClientRect()` on every event, forcing layout
- [ ] Cache `pages.clientWidth` in `sync()` instead of reading it on every scroll frame
- [ ] Debounce the `resize` handler so it runs once after the user stops rotating
- [ ] Use `offsetX`/`offsetY` for the press tilt instead of `getBoundingClientRect()`
- [ ] Skip the parallax rAF loop entirely while a sheet is open
- [ ] Stop the `float` and `drift` background animations when the app is backgrounded (`visibilitychange`)
- [ ] Reduce `backdrop-filter` work on `.glass::after` — the specular layer repaints on every pointermove
- [ ] Replace the 15 s `setInterval` tick with a timer that wakes exactly at the next due reminder
- [ ] Render lists into a `DocumentFragment` instead of building one big HTML string
- [ ] Avoid rebuilding the whole list on toggle — move just the one node between pages
- [ ] Add `content-visibility:auto` to off-screen pages so the pager does less layout work
- [ ] Drop `will-change` where it is not animating (it pins compositor layers)
- [ ] Measure and log a first-paint timing marker so runs can compare before/after
- [ ] Trim unused CSS rules left over from the design iterations

## Bugs and robustness

- [ ] Guard `JSON.parse` of stored items — a corrupt value currently leaves the app empty forever
- [ ] Handle `localStorage` quota errors with a visible message instead of silently losing the write
- [ ] Normalise `due` values on load (`NaN`, negative, strings) so one bad row cannot break sorting
- [ ] Dedupe ids — two reminders added in the same millisecond collide
- [ ] Validate `color` read from storage against the known palette before injecting it into `style`
- [ ] Reject whitespace-only titles on save
- [ ] Fix `endOfToday()` around DST and midnight rollover
- [ ] Handle the clock moving backwards so `fired` reminders can alert again
- [ ] Cap the stored list length so a runaway import cannot freeze the render
- [ ] Escape notes properly in the notification path
- [ ] Make `fmt()` fall back gracefully for dates in the far future
- [ ] Recover from a thrown error inside `tick()` so one bad item stops the interval
- [ ] Guard against `Notification` being unavailable rather than relying on try/catch
- [ ] Handle a reminder due exactly now on app open (it should fire, not sit silent)

## Accessibility

- [ ] Announce the in-app toast with `aria-live="polite"`
- [ ] Trap focus inside the add and settings sheets while open
- [ ] Give the colour dots a `aria-pressed` state
- [ ] Label both sliders with `aria-valuetext` in percent
- [ ] Increase the delete button hit area to at least 44 px without changing how it looks
- [ ] Make the tab bar reachable and operable with arrow keys
- [ ] Respect the OS text-size setting (`text-size-adjust`) without breaking the layout
- [ ] Add a visible focus ring to the gear button
- [ ] Mark the check buttons with the reminder title so screen readers say what they toggle

## Native shell

- [ ] Back button should close the settings sheet too, not just the add sheet
- [ ] Re-arm exact alarms when the user grants the exact-alarm permission later
- [ ] Recreate the notification channel if its importance was changed by the user
- [ ] Show a one-time hint if notifications are denied, instead of failing silently
- [ ] Survive process death: restore the last open tab
- [ ] Skip redundant alarm re-registration when the list has not changed
- [ ] Confirm the boot receiver re-arms after an app update, not only a reboot
- [ ] Reduce WebView cache size and disable unused settings
- [ ] Handle a reminder firing while the app is foregrounded without a duplicate notification

## Code quality

- [ ] Single source of truth for the colour palette (it is currently duplicated in JS and CSS)
- [ ] Name the magic numbers in the animation CSS
- [ ] Document the bridge contract in a comment block at the top of the file
- [ ] Split the settings defaults into one object
- [ ] Remove dead branches left from earlier design passes
- [ ] Keep the README in sync with any behaviour change

---

## Done

_(each run appends one line here)_
