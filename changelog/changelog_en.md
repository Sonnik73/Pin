# Pin changelog

## 0.43

- The Today widget scales its font to its size and fits as many tasks as its height allows; resizing recalculates both.

## 0.42

- The Calendar widget scales its font to its size: on a big widget dates, marks, title and the "today" circle are larger, and resizing recalculates them.
- Birthdays no longer get an extra "!" in the widget or the calendar — they have their own 🎂.

## 0.41

- New "Calendar" widget: the month like in the Calendar tab — task and repeat dots, "!" for important, 🎂 birthdays, red holidays, today circled. ‹ › flip months, tapping a day opens it in Pin, "+" is quick add.
- Bottom bar labels shrink to fit: "Calendar" is no longer cut off on narrow screens.

## 0.40

- The person card shows the next birthday and age right under the date: "29 November, in 28 days · turns 51". The hint wraps to a second line when needed.

## 0.39

- Share people with birthdays: "People" → ⋮ → "Share people…" — tick who to send and pick a messenger, mail or Drive. A .vcf file goes out: name, relation, note, birthday and reminders (gifts stay with you).
- On the other phone open the file in Pin ("Open" / "Share → People in Pin") or use ⋮ → "Add from file…": see who's new, who gets a birthday added and who's already there; tick and "Add". Your own data is never overwritten. Contact app exports work too.

## 0.38

- Fixed doubled completions of tasks and habits after sync: an event coming back from the sync file was treated as new (the file stores time without milliseconds). Existing duplicates are removed automatically on launch, so the calendar and stats count each completion once again.

## 0.37

- Search in "People": the field at the top matches name, relation, note and gifts (case-insensitive).

## 0.36

- Redesigned update dialogs: "Checking…" with a spinner, "No updates" with an "Installed / On GitHub / Released" card and "What's new", "Couldn't check" with "Retry". The new-version offer shows "Current → New", file size and release notes.

## 0.35

- Update check: on launch (at most every 6 hours) Pin asks GitHub for a newer version and offers it with release notes. "Download" fetches the APK, "Skip" stops offering this version, "Later" asks again next time.
- Settings → "About": "Check for updates" and "Check now".

## 0.34

- Custom "remind before": the 🔔 chip opens a picker — on time, 5/15/30 minutes, an hour, 2 hours, a day, or "Custom…" with days, hours and minutes (up to a week).

## 0.33

- With "Summary" turned off in "Features", a "Habits" tab takes its place: the habit list with check-ins, "+ Habit" and the focus timer.

## 0.32

- Settings → "Features": turn off what you don't need — quick add, voice input, rolled-over review, "Soon", habits, lists, matrix, focus timer and the "People", "Plans", "Summary" tabs. Turning off only hides things: your data stays.

## 0.31

- Large system font no longer breaks the layout: bottom bar labels stay on one line, theme mode and "Description" labels don't wrap mid-word, matrix titles keep the count on the right, the quick-add hint gets an ellipsis.
- Habits in the calendar: one "🔥 … · habit done" row per day, without the reminder time or duplicates; dots under dates are for tasks only.

## 0.30

- Habit reminders: "🔔 Pick a time" in the habit card. The notification arrives on the habit's days only if it is not marked yet; "Done" marks it right from the notification.
- Ticked a task by accident? "Done: … · Undo" appears at the bottom.
- Completed tasks stay on "Today" until the end of the day (and in the matrix, struck through, with the tick removable), then go to the archive. "Completed archive →" link under the list.

## 0.29

- Plan descriptions get the same markdown toolbar as tasks (checkbox, bullet, heading, bold, italic, link) and a preview.
- Lists continue automatically: Enter after an item adds a new one with the same marker (empty checkbox, next number); Enter on an empty item ends the list. Works in all descriptions.
- In "Plans" sub-items expand right in the list and can be ticked without opening the plan.

## 0.28

- Task descriptions expand right in the list: a "▾ ☑ 1/3" (or "▾ description") button under the task. Tick sub-items and open links without opening the task. Works on "Today" and in "Calendar".
- Plan to task: after the date you pick the time right away (or "No time").

## 0.27

- The calendar shows importance: a red "!" under the date when the day has an important task; "❗" and an "important" label in the day list.

## 0.26

- Phone calendar in the "Calendar" tab: meetings and events appear next to tasks (a small square under the date) and in the day list. Tap opens the event in the calendar app.
- Turn on in Settings → "Phone calendar"; Pin only reads the calendar, never writes to it or stores events.

## 0.25

- Eisenhower matrix: the four-squares icon on "Today". Four boxes: "Do now", "Schedule", "Do quickly", "Postpone or drop".
- Urgent means due today, tomorrow or rolled over; important means high priority. The ☆/★ star moves a task between boxes, the checkbox completes it.
- Undated plans appear too: important ones go to "Schedule".

## 0.24

- Lists (projects): "Home", "Work", "Renovation", each with its own colour. Settings → "Lists": add, rename, recolour, delete (tasks stay).
- List picker in the editor; a coloured list label in task rows.
- List filter on "Today" and in "All entries".
- Quick add understands "@home": the task goes to that list, which is created if needed.
- Lists are stored in `lists/`, tasks get a `list` field (see FORMAT.md).

## 0.23

- Voice add: microphone in the quick-add line; Pin parses the date and time from what you say. Uses Android speech recognition, offline when possible.
- "Share → Task in Pin" from browsers, messengers and other apps: the title is the subject or first line, links go to the description.

## 0.22

- Focus timer (pomodoro): 25 minutes of work, 5 minutes break, every fourth — 15. Start from a task or from "Summary".
- Countdown in the notification and as a banner on "Today"; a signal at the end of work and break.
- Focus minutes in "Summary" and for Krepost (`focus` in the contract).

## 0.21

- Habits: daily or on chosen weekdays, tick on "Today", streak 🔥, best streak, 30-day rate and a year heatmap.
- New habits and the list are on the "Summary" tab; past week ticks are in the habit card.
- Krepost sees habits: new `habits` path in the contract and `habit` completions.

## 0.20

- Early reminders: 5, 15, 30 minutes or an hour before the task time (🔔 button in the editor).
- Reminder notifications have "+15 min", "+1 h", "Tomorrow" and "Done".

## 0.19

- "Review rolled-over": stuck tasks one by one — today, tomorrow, pick a date, already done, to plans (skip for repeats), delete.
- The review button is on "Today" above rolled-over tasks and in the morning summary.

## 0.18

- Search and "All entries" (magnifier on "Today"): by title, description, tags and people's names.
- Filters: open, overdue, no date, done; important; by type and by tag.

## 0.17

- Calendar: birthdays are marked with a cake 🎂, holidays with a red date like in a paper calendar; tasks, repeats and completed items are dots of different colours. A legend below explains the marks.
- The day list shows the age for birthdays.

## 0.16

- Date fields insert the dots automatically: type 05031996 and see 05.03.1996.
- Text fields start with a capital letter: titles, descriptions, notes, gifts, holidays.
- Tags are suggested in a drop-down list as you type.
- People: new "husband" category and custom categories ("+ Custom…"), which are then offered as buttons.

## 0.15

- After an update Pin shows once what is new in the version, with a link to the full history.

## 0.14

- Version history in settings: what changed in each release.

## 0.13

- "Soon" shows the date next to "tomorrow" and "the day after tomorrow".

## 0.12

- Google Drive without Google Cloud: keep everything in one Pin.zip file via the Google Drive app (Settings → Google Drive — as a file). Pin saves changes to it and pulls edits from another phone on open.
- Folder sync of .md files remains — it needs a one-time Google Cloud setup.

## 0.11

- Clear message when Google cannot find the Pin OAuth client (UNREGISTERED_ON_API_CONSOLE): what to check in Google Cloud.

## 0.10

- Themes like in SonnikToPower: light, dark or system; palettes Pin, Steel, Forest, Sunset, Amethyst, Graphite, Forge, Garrison and Wallpaper (Android 12+); black background for AMOLED.
- Choose in Settings → Appearance, with live palette swatches.

## 0.9

- Google Drive sync: a "Pin" folder of .md files (same as the export), in the background and on demand. Pin only sees its own files (drive.file scope).
- Conflicts: the newer edit wins, the losing version is kept as a copy in Pin/conflicts. Deletions are synced as marks.
- Connect in Settings → Google Drive. Requires Google Play services and a one-time Google Cloud setup (docs/DRIVE.md).

## 0.8

- "Today" home-screen widget: today's tasks (rolled-over first), event of the day, tap the circle to complete, "+" for quick add.
- One-line quick add on "Today" and from the widget: "завтра в 18 позвонить маме !", "в пятницу отчёт #работа", "12 ноября техосмотр", "через 3 дня полить цветы".

## 0.7

- Pin shares task numbers with Krepost: per-day summaries and a list of completions — no task titles or names.
- Only ecosystem apps signed with the same key can read them. See docs/CONTRACT.md.

## 0.5

- "Summary" tab: done of planned, on-time share, rollovers and misses, streak of days without debts (current and best).
- Chart for a week, a month (by day) and a year (by month): done vs. not done on its day.
- Breakdown by type and tag, list of the most "stuck" tasks.

## 0.4

- Markdown task description with preview: headings, lists, checkboxes, bold, italic, links, code.
- Checkboxes can be ticked right in the preview; the task list shows "☑ 2/5".
- Formatting bar: item, list, heading, bold, italic, link.
- Export everything to zip — an Obsidian-compatible folder of .md files; import from zip with merge (newer wins).
- Hand-edited files are read: unknown fields are kept, missing fields do not reset data.

## 0.3

- People: wife, friends, family — with birthday (year optional for age) and a note.
- Birthdays: reminders 7 and 1 day before (configurable) and a "Congratulate" task on the day; not rolled over for a year if missed.
- Holidays: built-in Russian ones (can be turned off) and custom dates such as a wedding anniversary.
- Gifts: idea → bought → given, price, link, occasion; "buy a gift" a week before if nothing is bought.
- Plans without exact date: someday, a month or a year; can be turned into a task.
- "Today" shows an events card with holidays and upcoming occasions; they are also in the morning summary.

## 0.2

- Recurring tasks: daily, every N days, on weekdays, monthly (date or last day), yearly.
- An unfinished occurrence rolls over until the next one; then the old one is closed as missed — no duplicates.
- Calendar: month view with dots for tasks, completions and upcoming occurrences; add a task to the selected day.
- Morning summary (08:00 by default) and time reminders with a "Done" button.
- Settings screen: day boundary, summary time, reminders, Android permissions.

## 0.1

- First version: one-time tasks with date, time, priority (low, normal, important), tags and description.
- "Today" screen: rolled-over tasks on top, today's tasks, completed and the next 7 days.
- An unfinished task moves to the next day by itself with a "rolled over N times" counter; missed days are caught up when the app is opened.
- Day boundary is 04:00, as in Krepost (Garnizon); configurable in settings.
- Completion and rollover journal — the basis for future statistics and Krepost bonuses.
- Signed with the shared ecosystem key.
