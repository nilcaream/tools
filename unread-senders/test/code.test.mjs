import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const source = readFileSync(new URL('../Code.gs', import.meta.url), 'utf8');

function load() {
  const context = vm.createContext({});
  vm.runInContext(source, context);
  return vm.runInContext(
    '({ QUERY, PAGE_SIZE, MAX_THREADS, TIME_BUDGET_MS, scanUnread, parseFrom, gmailLink, renderPage, doGet })',
    context);
}

const code = load();

function message(from, unread = true) {
  return { getFrom: () => from, isUnread: () => unread };
}

// A GmailApp stand-in over a list of threads, each { id, messages }.
function fakeGmail(threads, calls = []) {
  return {
    search(query, start, max) {
      calls.push(['search', query, start, max]);
      return threads.slice(start, start + max).map(t => ({ getId: () => t.id, t }));
    },
    getMessagesForThreads(page) {
      calls.push(['get', page.length]);
      return page.map(p => p.t.messages);
    },
  };
}

const clock = () => 0;

test('counts unread messages per lowercased address, most first', () => {
  const gmail = fakeGmail([
    { id: 'a', messages: [message('Ann <Ann@Example.com>'), message('ann@example.com', false)] },
    { id: 'b', messages: [message('"Bob B" <bob@example.com>')] },
    { id: 'c', messages: [message('ann@example.com'), message('Bob <BOB@example.com>')] },
    { id: 'd', messages: [message('ann@example.com')] },
  ]);
  const scan = code.scanUnread(gmail, code.QUERY, clock);
  assert.deepEqual(JSON.parse(JSON.stringify(scan.senders)), [
    { name: 'Ann', address: 'ann@example.com', count: 3 },
    { name: 'Bob B', address: 'bob@example.com', count: 2 },
  ]);
  assert.equal(scan.messages, 5);
  assert.equal(scan.threads, 4);
  assert.equal(scan.complete, true);
});

test('equal counts sort by address', () => {
  const gmail = fakeGmail([
    { id: 'a', messages: [message('zed@example.com')] },
    { id: 'b', messages: [message('amy@example.com')] },
  ]);
  const addresses = code.scanUnread(gmail, code.QUERY, clock).senders.map(s => s.address);
  assert.deepEqual([...addresses],['amy@example.com', 'zed@example.com']);
});

test('parseFrom handles the From shapes Gmail returns', () => {
  const plain = o => JSON.parse(JSON.stringify(o));
  assert.deepEqual(plain(code.parseFrom('A B <X@Y.z>')), { name: 'A B', address: 'x@y.z' });
  assert.deepEqual(plain(code.parseFrom('"Last, First" <x@y.z>')), { name: 'Last, First', address: 'x@y.z' });
  assert.deepEqual(plain(code.parseFrom(' X@Y.z ')), { name: '', address: 'x@y.z' });
  assert.deepEqual(plain(code.parseFrom('<x@y.z>')), { name: '', address: 'x@y.z' });
});

test('pages through the search until a short page', () => {
  const threads = Array.from({ length: code.PAGE_SIZE + 3 }, (_, i) =>
    ({ id: 't' + i, messages: [message('s@example.com')] }));
  const calls = [];
  const scan = code.scanUnread(fakeGmail(threads, calls), code.QUERY, clock);
  assert.deepEqual(calls.filter(c => c[0] === 'search').map(c => c[2]), [0, code.PAGE_SIZE]);
  assert.equal(scan.senders[0].count, code.PAGE_SIZE + 3);
  assert.equal(scan.complete, true);
});

test('a thread shifted onto the next page by new mail is counted once', () => {
  const threads = Array.from({ length: code.PAGE_SIZE + 1 }, (_, i) =>
    ({ id: 't' + i, messages: [message('s@example.com')] }));
  const gmail = fakeGmail(threads);
  const search = gmail.search;
  let first = true;
  gmail.search = (q, start, max) => {
    const page = search(q, start, max);
    if (first) { first = false; threads.unshift({ id: 'new', messages: [message('n@example.com')] }); }
    return page;
  };
  const scan = code.scanUnread(gmail, code.QUERY, clock);
  assert.equal(scan.threads, code.PAGE_SIZE + 1);
  assert.equal(scan.senders.find(s => s.address === 's@example.com').count, code.PAGE_SIZE + 1);
});

test('stops at MAX_THREADS and says so when more remain', () => {
  const threads = Array.from({ length: code.MAX_THREADS + 1 }, (_, i) =>
    ({ id: 't' + i, messages: [message('s@example.com')] }));
  const scan = code.scanUnread(fakeGmail(threads), code.QUERY, clock);
  assert.equal(scan.threads, code.MAX_THREADS);
  assert.equal(scan.complete, false);
  const html = code.renderPage('me@example.com', code.QUERY, scan);
  assert.match(html, new RegExp('Only the first ' + code.MAX_THREADS + ' unread threads were scanned'));
});

test('exactly MAX_THREADS threads is complete', () => {
  const threads = Array.from({ length: code.MAX_THREADS }, (_, i) =>
    ({ id: 't' + i, messages: [message('s@example.com')] }));
  const scan = code.scanUnread(fakeGmail(threads), code.QUERY, clock);
  assert.equal(scan.complete, true);
  assert.doesNotMatch(code.renderPage('me@example.com', code.QUERY, scan), /Only the first/);
});

test('stops when the time budget is spent', () => {
  const threads = Array.from({ length: code.PAGE_SIZE * 3 }, (_, i) =>
    ({ id: 't' + i, messages: [message('s@example.com')] }));
  let t = 0;
  const now = () => { const v = t; t += code.TIME_BUDGET_MS; return v; };
  const scan = code.scanUnread(fakeGmail(threads), code.QUERY, now);
  assert.equal(scan.threads, code.PAGE_SIZE);
  assert.equal(scan.complete, false);
});

test('gmailLink names the account and searches the query plus the sender', () => {
  assert.equal(code.gmailLink('me+x@example.com', 'is:unread in:inbox', 'a@b.c'),
    'https://mail.google.com/mail/u/?authuser=me%2Bx%40example.com#search/is%3Aunread%20in%3Ainbox%20from%3Aa%40b.c');
});

test('renderPage escapes names and addresses', () => {
  const scan = { senders: [{ name: '<b>"Evil" & co</b>', address: 'e@x.y', count: 2 }], threads: 1, messages: 2, complete: true };
  const html = code.renderPage('me@example.com', code.QUERY, scan);
  assert.ok(html.includes('&lt;b&gt;&quot;Evil&quot; &amp; co&lt;/b&gt;'));
  assert.ok(!html.includes('<b>"Evil"'));
  assert.ok(html.includes('<td class="n">2</td>'));
  assert.ok(html.includes('2 unread from 1 senders'));
});

test('doGet renders the page for the effective user', () => {
  const context = vm.createContext({
    Session: { getEffectiveUser: () => ({ getEmail: () => 'me@example.com' }) },
    GmailApp: fakeGmail([{ id: 'a', messages: [message('A <a@example.com>')] }]),
    HtmlService: { createHtmlOutput: html => ({ html, setTitle(title) { this.title = title; return this; } }) },
  });
  vm.runInContext(source, context);
  const out = vm.runInContext('doGet()', context);
  assert.equal(out.title, 'Unread senders');
  assert.ok(out.html.includes('authuser=me%40example.com#search/'));
  assert.ok(out.html.includes('<td>a@example.com</td>'));
});
