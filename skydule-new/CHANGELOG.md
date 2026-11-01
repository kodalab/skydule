# Changelog

Version name is what people see; the build number (version code) is Play's internal counter and must go up with every upload.

## 1.0.0 (build 49): closed testing update
- Deleting a single changed occurrence of a repeating event now cancels that occurrence (it used to only undo the change, so the original came back).
- "Calendar default" shows correctly on all-day events.
- Calendar choices saved by an older build and restored from a backup onto a new phone are dropped instead of being applied to the wrong calendars.
- Cancelled occurrences no longer show.

## 1.0.0 (build 48): closed testing update
- Reminder notifications hide the event's title and place on the lock screen ("Event reminder" until the phone is unlocked).
- A snoozed reminder that comes back says how long is left now ("In 5 minutes", "Started 5 minutes ago") instead of repeating the original lead time.
- Deleting a single changed occurrence of a series says only that occurrence is removed.
- Editing an event with a calendar-default reminder keeps it as the calendar default (it used to become a fixed 10-minute alert).
- Snooze and dismiss no longer race with the background reminder update.

## 1.0.0 (build 47): closed testing update
- Hidden calendars and colour choices now come back correctly after a restore onto a new phone (they follow the calendar, not the phone's internal id). Settings are included in Google / Samsung cloud backups again.
- Test suite: emulator UI tests for the main journeys (create, edit, delete, tabs, settings, reminder tap).

## 1.0.0 (build 46): closed testing update
- Editing an event no longer deletes its email reminders, or changes Confidential visibility / Tentative availability.
- A reminder held back by battery saving is no longer cancelled by a calendar update; alarms are only cancelled when their event is gone.
- Meetings you declined, and cancelled events, no longer ring.
- Opening an event no longer looks up its saved location.
- Reminder updates run off the main thread and can no longer crash the app on a provider error.
- Settings (hidden calendars, colours) are no longer restored from a backup onto a different phone, where calendar ids differ.
- Repeating events: the editor says changes apply to the whole series, and no longer offers "Does not repeat" for one.
- Foldables: Close and Save keep working after folding or unfolding with the editor open.

## 1.0.0 (build 45): closed testing update
- Tapping a reminder while Skydule is closed opens the event (it could briefly say the event was gone).

## 1.0.0 (build 44): closed testing update
- Reminders can no longer be left ringing for a deleted or moved event when several background updates overlap.
- Saving an edit is all-or-nothing: the event and its notifications change together or not at all.
- Tapping a reminder for an event that has since been deleted (or whose calendar is hidden) says so, instead of opening it later.
- The debug build on GitHub now runs the tests and lint first.

## 1.0.0 (build 43): closed testing update
- Tapping a reminder for a repeating event opens that occurrence, not the first one in the list.
- If Android refuses to save or delete an event, the editor stays open with your changes and says so, instead of closing as if it worked.
- Editing a timed event keeps the time zone it was created in.
- Reminder notifications open the app the same way the widgets do (no second copy of Skydule on top of the first).
- The sky glow no longer animates forever (less battery). "Today" also drops the loaded days back to normal size after a long scroll.

## 1.0.0 (build 42): closed testing update
- Editing an event no longer moves it to another calendar; the calendar shows locked while editing. Events in read-only calendars (holidays, subscriptions) can't be edited or deleted.
- Clearing an event's location or notes now saves.
- Editing a repeating event synced from other calendar apps no longer shrinks it to one minute (all standard duration formats are read).
- All-day reminders now ring at the right time ("1 day before at 9:00 AM" at 9:00 the day before, not 9 hours late), with all-day notification choices and a custom days-before-and-time option. All-day notifications say Today, Tomorrow or In N days.
- Reminders set weeks ahead (up to 4 weeks) now fire.
- Reminders for events synced in while Skydule is closed are scheduled in the background.

## 1.0.0 (build 40): closed testing update
- Optional anonymous crash reports (Firebase Crashlytics, switch in Settings > About) and a Send feedback button that emails us with app and phone details.
- Place suggestions credit "Google Maps" in plain white, as Google asks.
- Location suggestions now use Google Places when a key is built in (finds home addresses and businesses OpenStreetMap missed), ranked near the phone; OpenStreetMap (Photon) remains the fallback. Rate-limited and cached.

## 1.0.0 (build 22): closed testing update
- Calendar settings (Settings > Calendar settings): show or hide each calendar, grouped by account, and recolour it from a 24-colour palette or a custom colour picker. Hidden calendars also stop their reminders. Only changes Skydule, not other apps.
- Weather: if the phone's Location switch is off, the pill says "Turn on location" and opens the system setting (found by a tester) instead of "Weather unavailable".

## 1.0.0 (build 21): first closed-testing build
- Schedule, Week and Month views over the calendars already on the phone (Calendar Provider, no account).
- Month: full calendar with titled event chips; tap a day for an animated day panel with a + to add an event.
- Event card slides in beside the list on foldables and tablets; phones stay in portrait.
- Empty days show a "Clear day / Tap to add an event" card. Day dividers removed.
- Themes: Sky, Material You, AMOLED black. App icon: default or dynamic (today's date and sky). The icon switches when the app is closed, so choosing one doesn't close the app.
- Reminders with snooze, optional weather (Open-Meteo, approximate location), place suggestions (Photon).

Earlier builds (3 to 20) were internal and not released.
