# Pin changelog

## 0.73

- Persistent notification: when expanded, each task is on its own line with full title and time (up to 12 lines, then "…and N more"). Carried-over tasks are marked "↻", important ones "❗". Collapsed it is still one narrow line.

## 0.72

- New: "Important dates" — marks for events and periods so you can later find when something happened, e.g. "got an award" or "vacation from 10.11.2026 for 17 days". Each shows a counter: "1 year 2 months 3 days ago (428 days)"; a period shows "day 3 of 17 · 14 days left" or "ended 2 months ago". Grouped by year, with search by title, note and tags; also found in global search.
- Important dates can be put into groups ("Vacations", "Awards"). Tap a group at the top to see a summary: entries, periods, their total days and days this year.
- New on the phone: a persistent notification so you don't forget your tasks. A narrow line at the bottom of the shade: "5 today · 1 carried over" and the first tasks. Silent, updates itself on every change and at the start of a new day. Turn off in Settings → Features → "Persistent notification".
- On the phone — in the "Plans" tab (switch "Plans | Important dates" at the top); in Pin for Windows — its own "Dates" tab. Can be turned off in "Features". Synced via Pin.zip (moments folder). Update Pin on all devices: older versions treat important dates as plans.

## 0.71

- A task can have up to five reminders. The task window has "Reminders": "+ Add reminder" adds a "Remind … before" row where you pick how early (2 hours, a day, custom — up to a week). The cross removes a row. Remove them all and the task won't remind. The notification says how much time is left: "At 18:00 — in 2 hours". Works on the phone and in Pin for Windows; reminders sync via Pin.zip.

## 0.70

- Fixed: a Pin.zip whose write was cut off exactly between archive entries could be read as whole but missing some tasks. Such a file is now recognised as damaged and replaced with full data.
- Fixed (Windows): if the shared file's folder disappeared (e.g. a drive was disconnected), the app could create a new empty folder with a new file in its place, and edits diverged. Now it is just "no connection to the file": edits wait on the computer until the folder returns.
- Pin for Windows: the autostart setting shows what is actually set in Windows. Internal code cleanup and new checks of shared-file handling (offline, after restart, with a damaged file).

## 0.69

- Fixed: removing a task's time, attachment, list or tags on one device left them on the others. Removals now reach all devices.
- Attachments are synced as a stream, one file at a time, not all in memory — the phone won't crash on big files. Attachments total up to 50 MB: that fits Pin.zip without a long Drive upload on every edit. An attachment removed on one device is deleted from the others.
- A damaged Pin.zip (if a write was ever cut off) no longer stops syncing: the phone and the computer replace it with their full data. After writing, the phone verifies the file.
- Pin for Windows: "New day starts at" in settings (like the phone's day boundary). The calendar shows an overdue recurring task on today, like the phone.

## 0.68

- Fixed: the phone's nightly rollover of unfinished tasks could overwrite an edit made in the evening on another device (another phone or a computer). Rollover no longer counts as an edit.
- Fixed: a removed mark — a habit or an accidental "done" — came back when syncing with another device. Removals now reach all devices.
- Pin for Windows: the local data copy is kept per file, so switching files no longer loses unsaved edits of the previous one; attachments in the copy are no longer rewritten on every edit.

## 0.67

- Pin for Windows keeps a copy of all data on the computer itself. If the shared file is unavailable (the computer with the shared folder is off, the network is down, Google Drive is not running), tasks, people and plans stay on screen and can be edited as usual — with a quiet "no connection to the file" mark on the left. When the file comes back, edits merge into it automatically. Unsaved edits survive closing the program too: on the next start they go to the shared file again.

## 0.66

- Pin for Windows: one Pin.zip can safely be shared by two computers on a local network (a shared folder). The file is locked while saving: the other computer waits a fraction of a second and saves right after, so simultaneous edits no longer overwrite each other. After writing, the program checks that the file holds exactly what was written; if not, edits stay in memory and the write is retried.

## 0.65

- Pin for Windows: the "People" tab gets "Import from file…" (.zip from "Share people", .vcf or .txt with "Anna 10.11.1997" per line) with a preview and checkboxes: new people are added, your own only get a missing birthday, nothing is duplicated. And "Share…" — pick people and save a .zip that opens in Pin on the phone.

## 0.64

- Pin for Windows: reminders as Windows notifications — task time (with "remind in advance"), habits, a morning summary with birthdays, holidays and "gift not bought yet". The summary time is in Settings.
- Pin for Windows: the close button hides the window to the tray and reminders keep coming; quit via right click on the Pin icon. Pin can start with Windows — straight to the tray.
- Pin for Windows: update check on GitHub (at most every 6 hours, can be turned off). A banner at the top with a "Download" button announces a new version — the installer replaces the program, tasks stay.

## 0.63

- Pin for Windows: gifts for people — ideas with price, link and occasion; click the status: idea → bought → given. A person's card shows how many ideas and bought gifts there are.
- Pin for Windows, Settings: lists (create, rename, recolor, delete — tasks stay), holidays (turn on and off, your own holiday, restore Russian holidays), "What's new" and "Features" — the same switches as on the phone.
- Pin for Windows: a new file started from scratch gets Russian holidays right away — with the same ids as the phone, so they won't be duplicated when the phone connects.

## 0.62

- Pin for Windows: "Summary" — done, on time, rollovers, streak, a "done and debts" chart for a week, month or year, breakdown by type and tag, the most "stuck" tasks.
- Pin for Windows: Eisenhower matrix — four quadrants, mark done, the star toggles importance.
- Pin for Windows: focus timer (25/5/15 minutes) — from the task menu "⏱ Focus on task" or the "Focus" tab. Minutes go to the journal and show up in the phone's "Summary"; a Windows notification marks the end of a session. The program now has a notification-area icon.

## 0.61

- Phone: Settings → "Features" can turn off "Google Drive over network" — the .md folder sync with a Google sign-in. The section disappears from settings and Pin does not go online for it. "Google Drive — as a file" (Pin.zip) works as before.
- Pin for Windows: the permanent file line at the top of the window is gone. If Google Drive briefly locks Pin.zip, the program retries reading and writing by itself and edits are not lost; a dismissible message appears only if the problem persists. File details are in Settings.
- Pin for Windows, "Today" like on the phone: habits, "Review rolled over" one by one, list filter, collapsible "Done" and "Upcoming", "Done archive". Each task has a menu on right click or "⋯": today, tomorrow, in a week, to plans, skip occurrence, delete. "- [ ]" items in the description can be ticked right in the task row.
- Pin for Windows: a "Search" tab — by title, description, tag and person, with filters by state, type, importance, list and tag.

## 0.60

- "Share people" now sends a .zip file (with a .vcf with birthdays inside). It can be forwarded via Telegram: a bare .vcf was turned into a contact card and the birthdays were lost.
- "Add from file" and "Open in Pin" understand .zip — both the new one from "Share people" and your own archive with a .vcf or .txt inside. Old .vcf and .txt files still work.

## 0.59

- Pin for Windows can start from scratch without a phone: "Create a new Pin.zip…" on the first screen (or Settings → "New file…"). Put the file into the Google Drive folder, and later the phone can connect it — Settings → "Google Drive — as a file" → "Connect an existing file", and tasks from both sides merge.

## 0.58

- Calendar: the day panel at the bottom now sits right under the calendar legend ("task", "repeat", "holiday"…), and the whole grid with the legend is visible without scrolling. Works for both month and week. To get the old behaviour back — Settings → "Features" → "Calendar without scrolling". On a small screen where the grid does not fit, scrolling stays.

## 0.57

- Settings → "Features" can also turn off the left swipe on a task (move to tomorrow). A task can still be moved from its card or in "Review rolled over".

## 0.56

- Settings → "Features" can turn off the right swipe on a task (mark as done), so tasks are not closed by accident while scrolling. The checkbox still marks tasks done, and the left swipe "to tomorrow" stays.

## 0.55

- Pin for Windows gets "Plans", "Habits", "People" and "Settings". Plans are grouped by month, year and "someday", can be edited and turned into a task for a chosen day and time. Habits are marked with circles for the last week, with the streak and the 30-day rate; habits can be added and edited. People come with search, nearest birthdays and ages; a person and their birthday can be added, edited and deleted.
- The program settings have the theme (system, light, dark), pure black background and the same palettes as the phone: Pin, Steel, Forest, Sunset, Amethyst, Graphite, Forge and Garrison.
- A mark removed on the computer no longer comes back from the file when saving.

## 0.54

- Pin for Windows can edit tasks: a quick-add line ("tomorrow at 18 call mom ! @home"), a "Task" button, an edit window with date, time, repeat, priority, list, tags, markdown description (lists continue on Enter) and attachments. The box next to a task marks it done, clicking a task opens it; the calendar can add a task to the selected day.
- Before writing, the program re-reads Pin.zip and merges its edits with what the phone has written: the newer edit wins, the journal has no duplicates. The file is overwritten in place, so the phone keeps seeing the same file on Drive.

## 0.53

- Pin for Windows: releases now include the Pin-0.53.msi installer. The program opens Pin.zip from the Google Drive folder and shows "Today" (rolled over, today, done, upcoming, nearest birthdays) and a monthly "Calendar" with birthdays and holidays. Descriptions with checklists expand, attachments open in the default Windows app. The file is re-read automatically when the phone syncs. View-only for now — adding and completing tasks come next.

## 0.52

- Task attachments: "📎 Attach a file" in the editor (photos, PDFs, documents up to 10 MB). Tasks in the list and calendar get a "📎" button: one attachment opens right away, several — from a menu. Attachments travel to the other phone in Pin.zip and go into the export.

## 0.51

- Birthdays from a text file: "People" → ⋮ → "Add from file (.vcf, .txt)…". In .txt each line holds a name and a date in any order ("Anya 10.11.1997", "Mom — 04.03", "Vasya: 5 марта 1990"). A list with checkboxes comes first; existing people aren't duplicated. Lines without a date are skipped and reported. Windows Notepad ANSI files work too.

## 0.50

- Widgets follow the chosen theme: palette-tinted background, the "+" button, the "today" circle and dots in the app's accent colours; light or dark per the theme mode. Changing the theme repaints them.
- Habit rings: a week ring around the check-in (done of planned), and "week 3/7" in the subtitle.

## 0.49

- Quick edit: tapping a one-off task opens a bottom panel — title, "Today / Tomorrow / In a week", time, importance. "Expand ↗" opens the full editor.
- Empty screens get illustrations: Today, Plans, People, search and lists.
- New icon — a note pinned with a pin — and a launch splash screen.

## 0.48

- Calendar heatmap: the more tasks a day has, the stronger its tint.
- Swipe the grid to flip months, plus a "Month / Week" switch; in week mode arrows and swipes move by weeks. "Today" jumps back.
- The selected day lives in a bottom panel you can drag up to full screen, with "+ Task" right there.

## 0.47

- Swipe tasks on "Today": right — "✓ Done" ("↺ Undo" for completed), left — "Tomorrow →".
- Ticking feels alive: the check and strike-through appear at once, then the task glides into "Done". Tasks move between sections smoothly.
- Confetti 🎉 when everything for today is done.

## 0.46

- "Today": a progress card on top with a ring — "Done 3 of 7", what's left, rolled over and important. When everything is done — "All done!".
- Tasks are rounded cards; the stripe on the left shows the list colour, or importance for tasks without a list.
- "Done" and "Soon" collapse with a tap on the header; section headers stick to the top while scrolling.

## 0.45

- More holidays: memorable dates (Old New Year, Students' Day, Valentine's Day, Cosmonautics Day, Children's Day, Youth Day, Family Day, Flag Day, Knowledge Day, Elderly Day, Teachers' Day, Constitution Day) and military days (Special Operations Forces, Airborne, Aerospace Forces, Ground Forces, Signal troops, Missile troops and artillery, Strategic Missile Forces).
- Only days off are red in the calendar; memorable dates get ✨, military days 🎖. "Settings → Holidays" shows the kind, and any of them can be turned off.

## 0.44

- Settings → "Widgets" → "Background transparency": a slider from solid to text only, with a preview. Applies to both the Today and Calendar widgets.
- Task dots in the calendar are bigger and easier to see, in the tab and in the widget.

## 0.43

- The calendar widget lists the month's birthdays and holidays below the grid ("🎂 14 — Anya, 29"), nearest first, as many lines as fit.
- Widgets are laid out for the exact size reported by the launcher (Android 12+): on a small calendar widget 🎂 and marks are no longer clipped.
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
