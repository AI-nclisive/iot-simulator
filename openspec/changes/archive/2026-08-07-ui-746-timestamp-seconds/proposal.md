# UI-746: Add seconds to timestamp display format

## Why
Timestamps are displayed as "DD MMM YYYY, HH:mm" which lacks precision. Users cannot distinguish events that occur within the same minute, making it hard to correlate events across the system.

## What Changes
Update timestamp formatting to include seconds: "DD MMM YYYY, HH:mm:ss"

Affected areas:
- Recording Values tab timestamps
- Recordings list page capture timestamps
- Scenarios list page last-run timestamps
- Project cards timestamps
