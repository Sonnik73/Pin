# Pin changelog

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
