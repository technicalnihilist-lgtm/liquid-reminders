# Backlog

The queue for the scheduled maintenance runs (1-20 November, ten runs a day).

**Rules for every run**

- Take the **first unchecked item**. Do one item per run, not more.
- If no unchecked items remain, find one new safe improvement yourself, add it to the list, then do it.
- Never change the visual design, layout, spacing, colours or wording of the interface.
  "Better" means faster, smoother, safer — not different looking.
- Never add a dependency, a framework or a bundler. `assets/index.html` stays one self-contained file.
- Never touch the signing key, the package name or the version code policy.
- Verify before committing: the page must load in headless Chromium with no console errors, the
  alarm bridge must still be called, and `./build.sh` must produce a signed APK.
- If anything fails, revert and leave the item unchecked. A failed run that changes nothing is fine;
  a broken app is not.
- Tick the box and add one line under "Done" saying what changed.

Every fifth completed item also cuts a GitHub release with the rebuilt APK, so there is an
installable build roughly twice a day. Items marked **[release]** cut one regardless.

---

## Performance

- [x] rAF-throttle the specular `pointermove` handler — it calls `getBoundingClientRect()` on every event, forcing layout
- [x] Cache `pages.clientWidth` in `sync()` instead of reading it on every scroll frame
- [x] Debounce the `resize` handler so it runs once after the user stops rotating
- [x] Use `offsetX`/`offsetY` for the press tilt instead of `getBoundingClientRect()`
- [x] Skip the parallax rAF loop entirely while a sheet is open
- [x] Stop the `float` and `drift` background animations when the app is backgrounded (`visibilitychange`)
- [x] Reduce `backdrop-filter` work on `.glass::after` — the specular layer repaints on every pointermove
- [x] Replace the 15 s `setInterval` tick with a timer that wakes exactly at the next due reminder
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

- **1 Nov, run 1** — specular pointermove now coalesces to one `getBoundingClientRect()` per animation frame. 25 rapid moves produced one layout read and the highlight still lands on the right pixel; no console errors, APK rebuilds clean.
- **1 Nov, run 2** — `sync()` reuses a cached page width (measured on load, on `resize` and via a `ResizeObserver`) instead of reading `pages.clientWidth` on every scroll frame. 40 scroll frames now cost one layout read in total, tab switching and rotation still land on the right page; no console errors, APK rebuilds clean.
- **1 Nov, run 3** — the `resize` handler is debounced by 140 ms, so a rotation burst now re-measures and re-aligns the pager once instead of on every event. 20 rapid resize events produced one `clientWidth` read (was 20), rotation still lands on the same tab with the scroll position aligned; no console errors, APK rebuilds clean.
- **1 Nov, run 4** — the press tilt now reads the browser-measured `offsetX`/`offsetY` and walks the target's offsets up into the card, instead of calling `getBoundingClientRect()`. A press costs zero layout reads (was one) and the tilt still lands on the same point: a press over the title, the padding and the check button all matched the card geometry to within a tenth of a degree; no console errors, APK rebuilds clean.
- **1 Nov, run 5** — the parallax rAF loop now parks while either sheet is open: `kick()` and `loop()` return early when `#sheet` or `#settings` carries `.open`, so moving the pointer over a sheet no longer animates the background behind the scrim. 25 pointer moves with the settings sheet open cost 0 parallax style writes (was 68) and the add sheet 0 (was 50); the loop resumes on the first move after the sheet closes, the alarm bridge is still called on save and nothing else changed; no console errors, APK rebuilds clean.
- **1 Nov, run 6** — the `float` and `drift` background animations now park while the app is backgrounded: the existing `visibilitychange` handler toggles a `paused` class on `<html>`, and one rule sets `animation-play-state:paused` on `.blob` and `.orb`. Backgrounding froze both (`paused`) and returning to the foreground resumed them (`running`), with no jump in position and no other class on `<html>` disturbed; the alarm bridge is still called on toggle, no console errors, APK rebuilds clean.
- **1 Nov, run 7** — the specular layer on `.glass::after` now sits on its own compositor layer (`will-change:opacity`), so moving the highlight repaints only that layer instead of dirtying the card underneath and forcing its `backdrop-filter` to be re-run. A 60-move sweep over a card now costs 37 ms of raster work (was 383 ms) and 25 ms of paint (was 46 ms) at an unchanged 61 raster tasks; screenshots of four states match the previous build to within one level per channel on the glass pixels (text and margins byte-identical), so nothing looks different. The highlight still lands under the pointer, the alarm bridge is still called on save and toggle, no console errors, APK rebuilds clean.
- **1 Nov, run 8** — the 15 s `setInterval` poll is gone: one `setTimeout` is now armed for the earliest un-fired, not-done `due` and re-armed from `persist()` and at the end of `tick()`, so adding, toggling or deleting a reminder moves it and nothing is left scheduled once no reminder is waiting to fire. A reminder due 2 s after load alerts at 1.4 s (was 15.3 s on the old poll) and a done reminder re-opened 2.5 s before its due time alerts at 2.2 s (was 15.1 s); the only long timer armed is the next due date itself. Six screenshots (Today, Upcoming and the add sheet, dark and light) are pixel-identical to the previous build, the alarm bridge is still called on save and toggle, no new console errors (the only entry is the same headless-Chromium vibrate notice as before), APK rebuilds clean.
