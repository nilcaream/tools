# Unread senders

A one-page Google Apps Script web app: the senders of your unread Gmail, with the count for each, most first. Each row links into Gmail, opened on that sender's unread mail, in the same account. You act there with Gmail's own buttons; the page only reads.

It reads with `gmail.readonly` ("View your email messages and settings") and `userinfo.email` (your address, for the links). No Cloud project, no OAuth client, no third-party service.

## Set it up, once per Gmail account

Do all of it in that account's own Chrome profile, and repeat every step for each account. Each account gets its own project and its own URL.

1. Open https://script.google.com and click **New project**.
2. From the root of this repository, run `mousepad unread-senders/Code.gs`, then press Ctrl+A, Ctrl+C in the window that opens. In the editor's `Code.gs`, press Ctrl+A, Ctrl+V, then save with Ctrl+S (or the disk icon).
3. Click **Project Settings** (gear, left) and tick **Show "appsscript.json" manifest file in editor**.
4. From the root of this repository, run `mousepad unread-senders/appsscript.json`, then press Ctrl+A, Ctrl+C. Click **Editor** (left), open `appsscript.json`, press Ctrl+A, Ctrl+V, then save with Ctrl+S (or the disk icon).
5. Click **Deploy** › **New deployment**, then next to **Select type** click the gear › **Web app**.
6. Set **Execute as**: Me, and **Who has access**: Only myself. Click **Deploy**.
7. Click **Authorize access** and pick the account.
8. On "Google hasn't verified this app", click **Advanced** › **Go to Untitled project (unsafe)**, then **Allow** (tick both permissions if Google shows checkboxes). The wording may differ slightly.
9. Copy the **Web app** URL (it ends in `/exec`), open it, and bookmark it. On a big inbox the first load can take a few minutes, and the page stays blank until it is done.
10. Use that URL only in this account's Chrome profile.

The page scans up to 5000 unread threads and stops after 4 minutes; when it stops early it says "Only the first N unread threads were scanned". To change what it counts, edit `QUERY` at the top of `Code.gs` (default `is:unread in:inbox`), then **Deploy** › **Manage deployments** › edit › **Version: New version** › **Deploy**.

If step 9 shows a permission error, the narrow scope was not enough: delete the `oauthScopes` block from `appsscript.json`, save, and open the URL again. Google then asks for full Gmail access instead.

## Tests

`node --test test/*.test.mjs` runs the grouping, paging, link and page code against a stub of GmailApp.
