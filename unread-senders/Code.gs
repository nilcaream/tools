// Unread senders: lists who sent the unread mail in this Gmail account, most first,
// each with a link into Gmail opened on that sender's unread mail.

const QUERY = 'is:unread in:inbox';
const PAGE_SIZE = 500;              // threads per GmailApp.search call
const MAX_THREADS = 5000;           // stop scanning after this many threads
const TIME_BUDGET_MS = 4 * 60 * 1000; // stop well before the 6-minute limit

function doGet() {
  const account = Session.getEffectiveUser().getEmail();
  const scan = scanUnread(GmailApp, QUERY, Date.now);
  return HtmlService.createHtmlOutput(renderPage(account, QUERY, scan))
    .setTitle('Unread senders');
}

// Counts unread messages per From address over the threads that match the query.
function scanUnread(gmail, query, now) {
  const started = now();
  const senders = new Map();
  const seen = new Set();
  let threads = 0;
  let messages = 0;
  let complete = true;
  for (let offset = 0; ; offset += PAGE_SIZE) {
    if (offset >= MAX_THREADS || now() - started > TIME_BUDGET_MS) {
      complete = gmail.search(query, offset, 1).length === 0;
      break;
    }
    const page = gmail.search(query, offset, PAGE_SIZE);
    // New mail arriving mid-scan shifts the pages; a thread is counted once.
    const fresh = page.filter(thread => !seen.has(thread.getId()));
    fresh.forEach(thread => seen.add(thread.getId()));
    threads += fresh.length;
    if (fresh.length > 0) {
      for (const thread of gmail.getMessagesForThreads(fresh)) {
        for (const message of thread) {
          if (!message.isUnread()) continue;
          messages++;
          addSender(senders, message.getFrom());
        }
      }
    }
    if (page.length < PAGE_SIZE) break;
  }
  return { senders: sortSenders(senders), threads, messages, complete };
}

function addSender(senders, from) {
  const { name, address } = parseFrom(from);
  const entry = senders.get(address);
  if (entry) {
    entry.count++;
    if (!entry.name) entry.name = name;
  } else {
    senders.set(address, { name, address, count: 1 });
  }
}

// "Name <a@b.c>", "\"Name\" <a@b.c>" or "a@b.c" -> { name, address }.
function parseFrom(from) {
  const match = /^(.*)<([^<>]+)>\s*$/.exec(from || '');
  if (!match) return { name: '', address: (from || '').trim().toLowerCase() };
  const name = match[1].trim().replace(/^"(.*)"$/, '$1').trim();
  return { name, address: match[2].trim().toLowerCase() };
}

function sortSenders(senders) {
  return [...senders.values()].sort((a, b) =>
    b.count - a.count || (a.address < b.address ? -1 : a.address > b.address ? 1 : 0));
}

// authuser=<email> picks the account whatever its /u/N index in this browser.
function gmailLink(account, query, address) {
  return 'https://mail.google.com/mail/u/?authuser=' + encodeURIComponent(account) +
    '#search/' + encodeURIComponent(query + ' from:' + address);
}

function escapeHtml(text) {
  return String(text).replace(/[&<>"']/g, c =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
}

function renderPage(account, query, scan) {
  const rows = scan.senders.map(s =>
    '<tr><td>' + escapeHtml(s.name) + '</td><td>' + escapeHtml(s.address) +
    '</td><td class="n">' + s.count + '</td><td><a href="' +
    escapeHtml(gmailLink(account, query, s.address)) +
    '" target="_blank" rel="noopener">Open in Gmail</a></td></tr>').join('\n');
  const cap = scan.complete ? '' :
    '<p class="cap">Only the first ' + scan.threads + ' unread threads were scanned.</p>';
  return '<!doctype html><html><head><meta charset="utf-8">' +
    '<meta name="viewport" content="width=device-width, initial-scale=1">' +
    '<style>body{font:14px system-ui,sans-serif;margin:16px;color:#222;background:#fff}' +
    'table{border-collapse:collapse}td,th{padding:4px 10px;border-bottom:1px solid #ddd;text-align:left}' +
    '.n{text-align:right}.cap{color:#a40}</style></head><body>' +
    '<p>' + escapeHtml(account) + ' · <code>' + escapeHtml(query) + '</code> · ' +
    scan.messages + ' unread from ' + scan.senders.length + ' senders</p>' + cap +
    '<table><thead><tr><th>Name</th><th>Address</th><th class="n">Unread</th><th></th></tr></thead>' +
    '<tbody>\n' + rows + '\n</tbody></table></body></html>';
}
