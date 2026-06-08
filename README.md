# reddit-api-cyoa-indexer

Read-only, personal, non-commercial CYOA indexing tool for selected public Reddit communities. This project is intentionally separate from archive-source crawler code and uses the official Reddit API only.

## What It Does

- Reads public submission listings from configured subreddits.
- Collects post metadata, source URLs, public selftext, and gallery URL metadata.
- Preserves NSFW and spoiler flags.
- Classifies posts as `static`, `interactive`, `ignored`, or `unknown`.
- Writes raw and final rows to Google Sheets.
- Tracks cursors and run state in a Google Sheets `state` tab.

Default subreddits:

- `makeyourchoice`
- `InteractiveCYOA`
- `nsfwcyoa`

## What It Does Not Do

- It does not post, comment, vote, message, follow users, or perform moderation actions.
- It does not access private content or mod-only data.
- It does not download, mirror, or rehost media files.
- It does not train AI or ML models.
- It does not sell data or publish Reddit data.
- It only uses official Reddit API requests.

## Sheet Tabs

Create these tabs in the target Google Sheet:

- `reddit_live_raw`
- `reddit_backfill_raw`
- `reddit_verified_raw`
- `compare`
- `cyoa_index`
- `state`

The app writes headers automatically when a target tab is empty.

## Local Setup

Requirements:

- Java 17 or newer
- Maven 3.9 or newer

Build and test:

```powershell
mvn test
mvn package
```

Run the offline smoke command:

```powershell
java -jar target/app.jar smoke
```

The smoke command does not require live API credentials.

## Environment

Copy `.env.example` to `.env` for local reference, then export the variables in your shell or CI environment. Do not commit real credentials.

Required:

- `REDDIT_CLIENT_ID`
- `REDDIT_CLIENT_SECRET`
- `REDDIT_USER_AGENT`
- `GOOGLE_SHEET_ID`
- `GOOGLE_SERVICE_ACCOUNT_JSON` or `GOOGLE_APPLICATION_CREDENTIALS`
- `SUBREDDITS`
- `BACKFILL_START_DATE`

Optional:

- `REQUEST_DELAY_MILLIS`, default `1000`
- `BACKFILL_MAX_PAGES`, default `0` for no app-level page cap
- `VERIFY_IDS`, comma-separated Reddit post IDs for `verify-ids`

## Reddit API Credentials

Create a Reddit app for personal script/API use. Use a descriptive user agent that identifies this personal tool and your Reddit username. The app uses client credentials for read-only public API requests.

## Google Sheets Setup

1. Create a Google Cloud service account.
2. Enable the Google Sheets API for the project.
3. Share the target spreadsheet with the service account email.
4. Set either `GOOGLE_SERVICE_ACCOUNT_JSON` to the service account JSON text or `GOOGLE_APPLICATION_CREDENTIALS` to a local JSON file path.

## CLI Commands

```powershell
java -jar target/app.jar live
java -jar target/app.jar reddit-backfill
java -jar target/app.jar verify-ids
java -jar target/app.jar finalize
java -jar target/app.jar run-all
java -jar target/app.jar smoke
```

Command behavior:

- `live`: reads `/r/{subreddit}/new`, writes `reddit_live_raw`, and rechecks posts first seen within the last three days.
- `reddit-backfill`: pages backward through official `/new` listings until the target date is reached or Reddit stops returning pages.
- `verify-ids`: looks up specific public post IDs with `/api/info` and writes `reddit_verified_raw`.
- `finalize`: merges official Reddit API rows into `cyoa_index`, deduplicated by Reddit post ID.
- `run-all`: runs `live`, pending rechecks, and `finalize`.
- `smoke`: runs local checks without network credentials.

## GitHub Secrets

Set these repository secrets for workflows:

- `REDDIT_CLIENT_ID`
- `REDDIT_CLIENT_SECRET`
- `REDDIT_USER_AGENT`
- `GOOGLE_SHEET_ID`
- `GOOGLE_SERVICE_ACCOUNT_JSON`

## GitHub Actions

Included workflows:

- Manual smoke test
- Scheduled live run every 6 hours
- Manual historical backfill

The backfill workflow is manual on purpose. Keep request rates gentle and avoid running multiple backfills at once.

## Safety Notes

Keep `.env`, service account JSON, and token files out of version control. Review sheet sharing permissions before running scheduled jobs.
