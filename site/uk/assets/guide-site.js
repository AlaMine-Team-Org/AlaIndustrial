(() => {
  const toggle = document.querySelector('.nav-toggle');
  const sidebar = document.getElementById('wiki-sidebar');
  if (toggle && sidebar) {
    const close = () => { document.body.classList.remove('nav-open'); toggle.setAttribute('aria-expanded', 'false'); };
    toggle.addEventListener('click', () => {
      const open = document.body.classList.toggle('nav-open');
      toggle.setAttribute('aria-expanded', String(open));
    });
    document.addEventListener('keydown', event => { if (event.key === 'Escape') close(); });
    sidebar.addEventListener('click', event => { if (event.target.closest('a')) close(); });
  }
  /* ── Full-text search over the whole wiki (MOD-414) ────────────────────────
     The index is built per locale when the site is generated
     (assets/search-index.json) and fetched lazily on the first focus of a
     search field. Matching runs entirely on the client: the query and every
     indexed string go through the SAME normalization pipeline below, so the
     only Unicode logic in existence lives in this file — the generator writes
     raw text and never reimplements it. The file itself is byte-identical in
     all five locales: every UI string arrives via data-l10n on the input. */
  const searchFields = [...document.querySelectorAll('input[data-wiki-search]')];

  /* Normalization pipeline (order is fixed, query and index share it):
     1. drop zero-width/invisible chars that break mid-word matching;
     2. NFKD — folds full-width to ASCII, NBSP to space, splits diacritics;
     3. hiragana -> katakana (parallel code blocks, +0x60); must run after NFKD
        so half-width kana is already expanded;
     4. strip combining marks EXCEPT the meaning-bearing ones: U+3099/U+309A
        (Japanese dakuten — a naive strip turns ga into ka) and U+0306 (the
        breve that makes Cyrillic short-i what it is). This is what folds
        yo->e and yi-with-diaeresis->i while keeping short-i a letter of its
        own;
     5. NFC — recomposes what survived (base vowel + breve back into the
        short-i letter, katakana + dakuten into one char), so short-i does not
        merge into plain i (that would make "mi" match "mii");
     6. fold the three visually identical apostrophes and typographic
        quotes/dashes down to ASCII;
     7. lowercase (NOT toLocaleLowerCase: locale-independent by spec, so the
        index is identical for every visitor);
     8. collapse whitespace runs to one space and trim.
     With withMap, returns {s, map} where map[k] is the source index of output
     char k — snippets are cut from the raw text (original yo/katakana kept)
     with highlights landing at the right spots. */
  const INVISIBLE_RE = /[\u200B-\u200F\u2060-\u2064\uFEFF\u00AD]/;
  const MARK_RE = /\p{Mn}/u;
  const norm = (str, withMap) => {
    const out = [];
    const map = [];
    const put = (ch, src) => { out.push(ch); map.push(src); };
    for (let i = 0; i < str.length; i++) {
      const code = str.charCodeAt(i);
      // ASCII is untouched by every step except lowercasing — skip the heavy path.
      if (code < 0x80) {
        put(code >= 0x41 && code <= 0x5A ? String.fromCharCode(code + 32) : str[i], i);
        continue;
      }
      // Plain Cyrillic (U+0410..U+044F except short-i, which must decompose)
      // and CJK ideographs pass every step unchanged but lowercasing — two
      // more fast lanes that keep the one-time index preparation cheap.
      if ((code >= 0x0410 && code <= 0x044F && code !== 0x0419 && code !== 0x0439) ||
          (code >= 0x4E00 && code <= 0x9FFF) || (code >= 0x3400 && code <= 0x4DBF)) {
        put(code >= 0x0410 && code <= 0x042F ? String.fromCharCode(code + 32) : str[i], i);
        continue;
      }
      if (INVISIBLE_RE.test(str[i])) continue;
      let s = str[i].normalize('NFKD');
      let folded = '';
      for (const d of s) {
        const c = d.codePointAt(0);
        folded += c >= 0x3041 && c <= 0x3096 ? String.fromCodePoint(c + 0x60) : d;
      }
      let kept = '';
      for (const d of folded) {
        const c = d.codePointAt(0);
        if (c === 0x3099 || c === 0x309A || c === 0x0306 || !MARK_RE.test(d)) kept += d;
      }
      s = kept.normalize('NFC').toLowerCase();
      for (const ch of s) {
        const c = ch.codePointAt(0);
        // Half-width kana arrives as TWO source chars (base + dakuten mark)
        // while the precomposed syllable is one; merge the trailing mark into
        // the previous output char so both spellings compare equal.
        if ((c === 0x3099 || c === 0x309A) && out.length) {
          const merged = (out[out.length - 1] + ch).normalize('NFC');
          if (merged.length === 1) { out[out.length - 1] = merged; continue; }
        }
        if (c === 0x02BC || c === 0x2018 || c === 0x2019) put("'", i);
        else if (c === 0x00AB || c === 0x00BB || c === 0x201E || c === 0x201C || c === 0x201D) put('"', i);
        else if (c >= 0x2010 && c <= 0x2014) put('-', i);
        else put(ch, i);
      }
    }
    const res = [], rmap = [];
    for (let k = 0; k < out.length; k++) {
      const sp = /\s/.test(out[k]);
      if (sp && (!res.length || res[res.length - 1] === ' ')) continue;
      res.push(sp ? ' ' : out[k]);
      rmap.push(map[k]);
    }
    while (res.length && res[res.length - 1] === ' ') { res.pop(); rmap.pop(); }
    return withMap ? { s: res.join(''), map: rmap } : res.join('');
  };

  /* Pre-normalize the whole index once at load; searches then only substring-
     scan prepared strings. Headings and titles carry no map — snippets are cut
     from section bodies only. */
  const prepareIndex = data => ({
    pages: data.pages.map(p => {
      const c = p.c.map(sec => {
        const n = norm(sec.t, true);
        return { h: sec.h, a: sec.a, raw: sec.t, hn: norm(sec.h), sn: n.s, map: n.map };
      });
      return { u: p.u, t: p.t, s: p.s, i: p.i, tn: norm(p.t), c };
    }),
  });

  /* Occurrence counter with saturation: repeats beyond the third add nothing
     (a wall of the same word must not drown out every other page). */
  const countHits = (hay, needle) => {
    let n = 0, i = hay.indexOf(needle);
    while (i !== -1 && n < 3) { n++; i = hay.indexOf(needle, i + needle.length); }
    return n;
  };

  /* Additive scoring: page title x5, section heading x3, section text x1.
     Whitespace-separated query terms combine with AND within one section (the
     page title counts towards every section). A light length penalty keeps
     equally-relevant short pages above equally-relevant walls of text. */
  const runSearch = (index, query) => {
    const terms = query.split(' ').filter(Boolean);
    if (!terms.length) return [];
    const out = [];
    for (const page of index.pages) {
      const titleHits = terms.map(term => countHits(page.tn, term));
      let best = null, bestScore = 0;
      for (const sec of page.c) {
        let secScore = 0, matchedAll = true;
        for (let k = 0; k < terms.length; k++) {
          const inTitle = titleHits[k];
          const inHead = countHits(sec.hn, terms[k]);
          const inText = countHits(sec.sn, terms[k]);
          if (!inTitle && !inHead && !inText) { matchedAll = false; break; }
          secScore += 5 * inTitle + 3 * inHead + inText;
        }
        if (matchedAll && secScore > bestScore) { bestScore = secScore; best = sec; }
      }
      if (!best && !titleHits.some(n => n)) continue;
      const len = page.c.reduce((s, c) => s + c.sn.length, 0);
      const score = best ? bestScore : 5 * titleHits.reduce((a, b) => a + b, 0);
      out.push({ page, sec: best, score: score - Math.round(len / 10000) });
    }
    out.sort((a, b) => b.score - a.score);  // stable: ties keep generator order
    return out.slice(0, 8);
  };

  /* Cut a ~90-150 char window around the first match, word-aligned for spaced
     scripts (fall back to a hard cut mid-run for CJK, never mid-surrogate),
     then collect every term occurrence inside the window for highlighting. */
  const rawEnd = (raw, idx) => idx + (raw.codePointAt(idx) > 0xffff ? 2 : 1);
  const sliceMarks = (raw, map, sn, terms) => {
    let start = -1, mlen = 0;
    for (const term of terms) {
      const i = sn.indexOf(term);
      if (i !== -1 && (start === -1 || i < start)) { start = i; mlen = term.length; }
    }
    if (start === -1) return null;
    const rStart = map[start];
    const rEnd = rawEnd(raw, map[start + mlen - 1]);
    let s0 = rStart;
    const hardStart = Math.max(0, rStart - 70);
    while (s0 > hardStart && !/\s/.test(raw[s0 - 1])) s0--;
    if (rStart - s0 > 70) s0 = rStart - 60;
    while (s0 > 0 && (raw.charCodeAt(s0) & 0xfc00) === 0xdc00) s0--;
    let e1 = rEnd;
    const hardEnd = Math.min(raw.length, rEnd + 90);
    while (e1 < hardEnd && !/\s/.test(raw[e1])) e1++;
    if (e1 - rEnd > 90) e1 = rEnd + 80;
    while (e1 < raw.length && (raw.charCodeAt(e1) & 0xfc00) === 0xdc00) e1--;
    const marks = [];
    for (const term of terms) {
      let i = sn.indexOf(term);
      while (i !== -1) {
        const a = map[i], b = rawEnd(raw, map[i + term.length - 1]);
        if (a >= s0 && b <= e1) marks.push([a, b]);
        i = sn.indexOf(term, i + term.length);
      }
    }
    marks.sort((x, y) => x[0] - y[0]);
    return { s0, e1, marks };
  };

  /* Snippet is assembled from text nodes and <mark> elements only — index text
     never passes through innerHTML. */
  const makeSnippet = (result, terms) => {
    const frag = document.createDocumentFragment();
    const sec = result.sec || result.page.c[0];
    if (!sec) return frag;
    const parts = result.sec ? sliceMarks(sec.raw, sec.map, sec.sn, terms) : null;
    if (!parts) {
      // Title-only hit: show how the page opens, no highlight to place.
      frag.append([...sec.raw].slice(0, 140).join('') + '…');
      return frag;
    }
    if (parts.s0 > 0) frag.append('…');
    let cursor = parts.s0;
    for (const [a, b] of parts.marks) {
      if (a < cursor) continue;  // overlapping matches — first one wins
      if (a > cursor) frag.append(sec.raw.slice(cursor, a));
      const mark = document.createElement('mark');
      mark.textContent = sec.raw.slice(a, b);
      frag.append(mark);
      cursor = b;
    }
    if (cursor < parts.e1) frag.append(sec.raw.slice(cursor, parts.e1));
    if (parts.e1 < sec.raw.length) frag.append('…');
    return frag;
  };

  let indexState = null;
  const ensureIndex = () => {
    if (!indexState) {
      const src = searchFields.length ? searchFields[0].dataset.index : null;
      // Plain HTTP caching (no 'no-cache' like the stats fetch): the index
      // changes only when the site is regenerated, so ETag revalidation is
      // pure profit between pages.
      indexState = { ready: false, data: null };
      indexState.promise = src
        ? fetch(src)
            .then(r => (r.ok ? r.json() : Promise.reject(r.status)))
            .then(data => { indexState.data = prepareIndex(data); indexState.ready = true; })
            .catch(() => { indexState = null; })  // a later focus retries
        : Promise.resolve();
    }
    return indexState.promise;
  };

  searchFields.forEach((field, ordinal) => {
    let L = {};
    try { L = JSON.parse(field.dataset.l10n || '{}'); } catch (e) { /* keep {} */ }
    // The popup and the live region are created here: static ids in the markup
    // would collide between the header field and the hero field of the home page.
    const pop = document.createElement('div');
    pop.className = 'wiki-search-pop';
    pop.id = 'wiki-search-pop-' + ordinal;
    pop.setAttribute('role', 'listbox');
    pop.setAttribute('aria-label', L.list || 'Results');
    pop.hidden = true;
    const status = document.createElement('div');
    status.className = 'wiki-search-status';
    status.setAttribute('role', 'status');
    field.after(pop);
    field.after(status);
    field.setAttribute('aria-controls', pop.id);

    let results = [], optionEls = [], active = -1, composing = false, timer = 0, terms = [];

    const plural = n => {
      const lang = (document.documentElement.lang || '').slice(0, 2);
      const n10 = n % 10, n100 = n % 100;
      if (lang === 'ru' || lang === 'uk') {
        if (n10 === 1 && n100 !== 11) return L.one;
        if (n10 >= 2 && n10 <= 4 && (n100 < 12 || n100 > 14)) return L.few;
        return L.many;
      }
      return n === 1 ? L.one : L.many;
    };
    const setStatus = text => { status.textContent = text || ''; };
    const close = () => {
      pop.hidden = true;
      field.setAttribute('aria-expanded', 'false');
      field.removeAttribute('aria-activedescendant');
      active = -1;
    };
    const open = () => { pop.hidden = false; field.setAttribute('aria-expanded', 'true'); };
    const setActive = idx => {
      active = idx;
      optionEls.forEach((el, i) => {
        const on = i === idx;
        el.setAttribute('aria-selected', String(on));
        el.classList.toggle('on', on);
      });
      if (optionEls[idx]) {
        field.setAttribute('aria-activedescendant', optionEls[idx].id);
        optionEls[idx].scrollIntoView({ block: 'nearest' });
      } else {
        field.removeAttribute('aria-activedescendant');
      }
    };
    const go = result => {
      if (!result) return;
      const root = field.dataset.root || '';
      location.href = root + result.page.u + (result.sec && result.sec.a ? '#' + result.sec.a : '');
    };

    const render = list => {
      results = list;
      pop.textContent = '';
      optionEls = [];
      if (!list.length) {
        const empty = document.createElement('div');
        empty.className = 'wso-empty';
        empty.textContent = L.nothing || '';
        pop.append(empty);
        return;
      }
      const root = field.dataset.root || '';
      list.forEach((r, idx) => {
        const opt = document.createElement('div');
        opt.className = 'wso';
        opt.setAttribute('role', 'option');
        opt.id = pop.id + '-opt-' + idx;
        // mousedown is cancelled so focus never leaves the input (APG
        // combobox: DOM focus stays in the field, options are pointed at via
        // aria-activedescendant); the click itself navigates.
        opt.addEventListener('mousedown', e => e.preventDefault());
        opt.addEventListener('click', () => go(r));
        if (r.page.i) {
          const img = document.createElement('img');
          img.className = 'wso-icon';
          img.src = root + r.page.i;
          img.alt = '';
          img.loading = 'lazy';
          opt.append(img);
        }
        const body = document.createElement('span');
        body.className = 'wso-body';
        const title = document.createElement('span');
        title.className = 'wso-title';
        title.textContent = r.page.t;
        const sec = document.createElement('span');
        sec.className = 'wso-sec';
        sec.textContent = r.page.s + (r.sec && r.sec.h ? ' · ' + r.sec.h : '');
        const snip = document.createElement('span');
        snip.className = 'wso-snip';
        snip.append(makeSnippet(r, terms));
        body.append(title, sec, snip);
        opt.append(body);
        pop.append(opt);
        optionEls.push(opt);
      });
    };

    const run = () => {
      const q = norm(field.value);
      if (!q) { close(); setStatus(''); return; }
      if (!indexState || !indexState.ready) { setStatus(L.loading || '…'); return; }
      terms = q.split(' ').filter(Boolean);
      const list = runSearch(indexState.data, q);
      render(list);
      open();
      setStatus(list.length ? list.length + ' ' + plural(list.length) : (L.nothing || ''));
      setActive(list.length ? 0 : -1);
    };

    field.addEventListener('focus', () => {
      ensureIndex().then(() => { if (norm(field.value)) run(); });
    });
    field.addEventListener('input', e => {
      if (composing || e.isComposing) return;
      clearTimeout(timer);
      timer = setTimeout(run, 200);
    });
    // IME gate: 'input' fires mid-composition for ja/zh — searching on every
    // pinyin syllable flickers, and Enter confirms the candidate, not a result.
    field.addEventListener('compositionstart', () => { composing = true; clearTimeout(timer); });
    field.addEventListener('compositionend', () => { composing = false; clearTimeout(timer); run(); });
    field.addEventListener('keydown', e => {
      if (e.isComposing || e.keyCode === 229) return;
      const isOpen = !pop.hidden;
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault();
        if (!indexState || !indexState.ready) { ensureIndex().then(() => { if (norm(field.value)) run(); }); return; }
        if (!isOpen && optionEls.length === 0) run();
        if (!optionEls.length) return;
        const down = e.key === 'ArrowDown';
        if (!isOpen) { open(); setActive(down ? 0 : optionEls.length - 1); }
        else setActive(down ? (active + 1) % optionEls.length : (active - 1 + optionEls.length) % optionEls.length);
      } else if (e.key === 'Enter') {
        if (isOpen && active >= 0 && results[active]) { e.preventDefault(); go(results[active]); }
      } else if (e.key === 'Escape') {
        // Two steps: first Esc closes the popup, second clears the field.
        // preventDefault stops Chrome's type=search native clear from eating
        // the first Esc and skipping the close step.
        e.preventDefault();
        if (isOpen) close();
        else { field.value = ''; clearTimeout(timer); run(); }
      } else if (e.key === 'Tab') {
        close();
      }
    });
    field.addEventListener('focusout', e => {
      if (!pop.contains(e.relatedTarget)) close();
    });
    document.addEventListener('click', e => {
      const wrap = field.parentElement;
      if (wrap && !wrap.contains(e.target)) close();
    });
  });

  // Ctrl/Cmd+K focuses the first search field — the header one on every page.
  // event.code names the PHYSICAL key: on a Cyrillic layout Ctrl+K reports
  // key=U+043A, not 'k' — a key-only check would silently die for ru/uk users.
  document.addEventListener('keydown', event => {
    if ((event.ctrlKey || event.metaKey) && searchFields.length &&
        (event.code === 'KeyK' || event.key.toLowerCase() === 'k')) {
      event.preventDefault();
      searchFields[0].focus();
      searchFields[0].select();
    }
  });

  /* ── Download statistics block (MOD-412) ─────────────────────────────────
     The markup ships hidden; the figures come from data/stats.json, refreshed by a
     daily workflow. The generated HTML holds no figure of its own — otherwise the
     site's stale gate would go red every time the data changed. */
  const stats = document.getElementById('stats-block');
  if (stats) {
    const L = JSON.parse(stats.dataset.l10n);
    const locale = document.documentElement.lang || 'en';
    const nf = new Intl.NumberFormat(locale);
    const fmtDate = iso => new Date(iso + 'T00:00:00Z')
      .toLocaleDateString(locale, { day: 'numeric', month: 'short', timeZone: 'UTC' });
    /* Russian and Ukrainian need three forms of the word "download"; the other
       locales supply the same string in all three slots. */
    const plural = n => {
      const f = L.dl_forms;
      if (f.length < 3) return f[0];
      const n10 = n % 10, n100 = n % 100;
      if (n10 === 1 && n100 !== 11) return f[0];
      if (n10 >= 2 && n10 <= 4 && (n100 < 12 || n100 > 14)) return f[1];
      return f[2];
    };

    /* Cumulative totals -> per-day figures. A missing day (the workflow did not
       run) is spread evenly across the gap: otherwise the chart grows a fake spike
       on the day collection recovers. */
    const dailyFrom = series => {
      const out = [];
      for (let i = 1; i < series.length; i++) {
        const [d0, m0, c0] = series[i - 1];
        const [d1, m1, c1] = series[i];
        const gap = Math.max(1, Math.round((Date.parse(d1) - Date.parse(d0)) / 86400000));
        /* CurseForge publishes no history, so days restored from Modrinth analytics
           carry null in its place. Such a day counts Modrinth only and is marked, so
           the tooltip can say the figure is not the full picture. */
        const partial = c0 === null || c1 === null;
        const per = (m1 - m0 + (partial ? 0 : c1 - c0)) / gap;
        for (let g = gap; g >= 1; g--) {
          out.push({
            date: new Date(Date.parse(d1) - (g - 1) * 86400000).toISOString().slice(0, 10),
            v: Math.max(0, Math.round(per)),
            est: gap > 1 || partial,
          });
        }
      }
      return out;
    };

    /* The longer the range, the coarser the bucket: two years by day would be 700
       columns one pixel wide. */
    const bucket = days => {
      if (days.length <= 45) return { data: days, unit: L.per_day };
      const size = days.length <= 200 ? 7 : 30;
      const out = [];
      for (let i = days.length % size; i < days.length; i += size) {
        const slice = days.slice(i, i + size);
        if (!slice.length) continue;
        out.push({
          date: slice[0].date, end: slice[slice.length - 1].date,
          v: slice.reduce((s, d) => s + d.v, 0), est: slice.some(d => d.est),
        });
      }
      return { data: out, unit: size === 7 ? L.per_week : L.per_month };
    };

    const drawChart = (all, range) => {
      const host = document.getElementById('st-chart-body');
      const unitEl = document.getElementById('st-unit');
      const tip = document.getElementById('st-tip');
      const { data, unit } = bucket(range === 'all' ? all : all.slice(-range));
      unitEl.textContent = data.length > 1 ? unit : '';
      if (data.length < 2) { host.innerHTML = '<div class="st-empty">' + L.no_data + '</div>'; return; }

      const W = 1000, H = 270, PL = 52, PR = 12, PT = 14, PB = 30;
      const iw = W - PL - PR, ih = H - PT - PB;
      const peak = Math.max(...data.map(d => d.v)) || 1;
      /* Round axis step - 1/2/5x10^n, otherwise labels come out as 63/125/188. */
      const mag = Math.pow(10, Math.floor(Math.log10(Math.max(peak, 4) / 4)));
      const step = [1, 2, 5, 10].map(m => m * mag).find(s => peak / s <= 4) || 10 * mag;
      const top = Math.ceil(peak / step) * step;
      const sw = iw / data.length;
      const y = v => PT + ih - (v / top) * ih;

      let area = 'M ' + PL + ' ' + (PT + ih), line = '', grid = '', ticks = '', hits = '';
      data.forEach((d, i) => {
        const x0 = PL + i * sw, x1 = PL + (i + 1) * sw, yy = y(d.v).toFixed(1);
        area += ' L ' + x0.toFixed(1) + ' ' + yy + ' L ' + x1.toFixed(1) + ' ' + yy;
        line += (i ? 'L' : 'M') + ' ' + x0.toFixed(1) + ' ' + yy + ' L ' + x1.toFixed(1) + ' ' + yy + ' ';
        hits += '<rect class="st-hit" data-i="' + i + '" x="' + x0.toFixed(1) + '" y="' + PT +
                '" width="' + sw.toFixed(1) + '" height="' + ih + '"/>';
      });
      area += ' L ' + (PL + iw) + ' ' + (PT + ih) + ' Z';
      for (let v = 0; v <= top + 1e-9; v += step) {
        const gy = (PT + ih - (v / top) * ih).toFixed(1);
        grid += '<line x1="' + PL + '" y1="' + gy + '" x2="' + (W - PR) + '" y2="' + gy +
                '" stroke="#3b4047" stroke-width="2"/><text x="' + (PL - 9) + '" y="' + (+gy + 6) +
                '" fill="#9aa0a8" font-size="17" font-family="VT323,monospace" text-anchor="end">' +
                nf.format(v) + '</text>';
      }
      const every = Math.max(1, Math.round(data.length / 4));
      data.forEach((d, i) => {
        if (i % every) return;
        ticks += '<text x="' + (PL + i * sw + sw / 2).toFixed(1) + '" y="' + (H - 8) +
                 '" fill="#9aa0a8" font-size="17" font-family="VT323,monospace" text-anchor="middle">' +
                 fmtDate(d.date) + '</text>';
      });

      host.innerHTML =
        '<svg viewBox="0 0 ' + W + ' ' + H + '" role="img" aria-label="' + L.chart + '">' + grid +
        '<path d="' + area + '" fill="rgba(85,255,255,.18)"/>' +
        '<path d="' + line + '" fill="none" stroke="#55ffff" stroke-width="4" stroke-linejoin="miter"/>' +
        '<g id="st-marker" style="display:none"><line stroke="#fcd12a" stroke-width="3" stroke-dasharray="6 4"/>' +
        '<rect width="9" height="9" fill="#fcd12a" stroke="#101216" stroke-width="2"/></g>' +
        ticks + hits + '</svg>' +
        /* Text twin of the series for screen readers: the chart itself is opaque to
           them, and hundreds of focusable points would make navigation unusable. */
        '<table class="st-table"><caption>' + L.chart + '</caption><tbody>' +
        data.map(d => '<tr><th scope="row">' + (d.end && d.end !== d.date
          ? fmtDate(d.date) + ' — ' + fmtDate(d.end) : fmtDate(d.date)) +
          '</th><td>' + nf.format(d.v) + '</td></tr>').join('') + '</tbody></table>';

      /* Footnote explaining the asterisk, shown only while the visible range
         actually contains such days. */
      const note = document.getElementById('st-note');
      if (note) {
        note.textContent = L.partial_note || '';
        note.hidden = !L.partial_note || !data.some(d => d.est);
      }

      const svg = host.querySelector('svg');
      const marker = host.querySelector('#st-marker');
      const mLine = marker.querySelector('line'), mDot = marker.querySelector('rect');
      const clear = () => {
        tip.classList.remove('on'); marker.style.display = 'none';
        host.querySelectorAll('.st-hit.on').forEach(x => x.classList.remove('on'));
      };
      host.querySelectorAll('.st-hit').forEach(r => {
        /* Own tooltip instead of <title>: the browser one appears after nearly a
           second and never shows which column the cursor is on. */
        r.addEventListener('pointerenter', () => {
          host.querySelectorAll('.st-hit.on').forEach(x => x.classList.remove('on'));
          r.classList.add('on');
          const d = data[+r.dataset.i], cx = +r.getAttribute('x') + sw / 2;
          marker.style.display = '';
          mLine.setAttribute('x1', cx); mLine.setAttribute('x2', cx);
          mLine.setAttribute('y1', PT); mLine.setAttribute('y2', PT + ih);
          mDot.setAttribute('x', cx - 4.5); mDot.setAttribute('y', y(d.v) - 4.5);
          tip.innerHTML = (d.end && d.end !== d.date ? fmtDate(d.date) + ' — ' + fmtDate(d.end) : fmtDate(d.date)) +
                          '<br><b>' + nf.format(d.v) + '</b> ' + plural(d.v) + (d.est ? ' *' : '');
          /* Measure against the element the tooltip is positioned inside (.st-chart)
             rather than the chart container: they differ by the header height, which
             pushed the tooltip onto the range buttons. */
          const box = svg.getBoundingClientRect();
          const base = tip.offsetParent.getBoundingClientRect();
          const px = box.left - base.left + (cx / W) * box.width;
          const py = box.top - base.top + (y(d.v) / H) * box.height;
          tip.classList.add('on');   // measure after showing: a hidden element has no size
          tip.style.left = Math.min(Math.max(px - tip.offsetWidth / 2, 4),
                                    base.width - tip.offsetWidth - 4) + 'px';
          /* No room above - show it below the point, otherwise the tooltip escapes
             the card. */
          const above = py - tip.offsetHeight - 12;
          tip.style.top = (above < 4 ? py + 16 : above) + 'px';
        });
      });
      svg.addEventListener('pointerleave', clear);
      window.addEventListener('scroll', clear, { passive: true });
    };

    const render = data => {
      const t = data.totals || {};
      const total = (t.modrinth || 0) + (t.curseforge || 0);
      if (!total) return;
      const all = dailyFrom(data.series || []);
      /* Snapshots are dated with the day they close, so the newest one is always
         yesterday and the chart ends there. Should a row for the current day turn
         up anyway — a hand-edited file, an older collector — it is still filling
         up, and its bar would grow from a sliver all day and read as a crash in
         downloads. Never draw a day that is not over. */
      const todayUTC = new Date().toISOString().slice(0, 10);
      while (all.length && all[all.length - 1].date >= todayUTC) all.pop();
      const week = all.slice(-7).reduce((s, d) => s + d.v, 0);
      const prev = all.slice(-14, -7).reduce((s, d) => s + d.v, 0);
      const set = (id, text) => { const el = document.getElementById(id); if (el) el.textContent = text; };

      set('st-total', nf.format(total));
      /* On day one there is no history: the weekly figures would be honest zeros
         that read as a collapse, so they stay hidden until data exists. */
      const weekCard = document.getElementById('st-week').closest('.st-card');
      if (all.length) {
        set('st-total-d', '+' + nf.format(week) + ' ' + L.week_suffix);
        set('st-week', nf.format(week));
        set('st-week-d', prev ? (week >= prev ? '+' : '') + Math.round((week - prev) / prev * 100) + '%' : '');
        weekCard.hidden = false;
      } else {
        set('st-total-d', '');
        weekCard.hidden = true;
      }
      set('st-mr', nf.format(t.modrinth || 0));
      set('st-mr-d', L.followers.replace('{n}', nf.format(t.followers || 0)));
      set('st-cf', nf.format(t.curseforge || 0));
      set('st-updated', L.updated.replace('{d}', fmtDate((data.generated || '').slice(0, 10))));

      const loaders = t.loaders || {};
      const lsum = (loaders.fabric || 0) + (loaders.neoforge || 0);
      const box = document.getElementById('st-loaders');
      if (lsum && box) {
        const pct = Math.round(loaders.fabric / lsum * 100), n = 40, on = Math.round(pct / 100 * n);
        let cells = '';
        for (let i = 0; i < n; i++) cells += '<i class="' + (i < on ? 'aqua' : 'gold') + '"></i>';
        document.getElementById('st-split').innerHTML = cells;
        set('st-fabric', nf.format(loaders.fabric));
        set('st-neoforge', nf.format(loaders.neoforge));
        set('st-fabric-pct', pct + '%');
        set('st-neoforge-pct', (100 - pct) + '%');
        box.hidden = false;
      }

      stats.hidden = false;
      /* With no history the chart card is not rendered at all: an empty frame saying
         "not enough data" fills half the screen and reads as breakage. */
      const chartCard = stats.querySelector('.st-chart');
      if (all.length < 2) { chartCard.hidden = true; return; }
      chartCard.hidden = false;

      let range = 'all';
      drawChart(all, range);
      stats.querySelectorAll('.st-range').forEach(b => {
        b.addEventListener('click', () => {
          stats.querySelectorAll('.st-range').forEach(x => x.setAttribute('aria-pressed', 'false'));
          b.setAttribute('aria-pressed', 'true');
          range = b.dataset.range === 'all' ? 'all' : +b.dataset.range;
          drawChart(all, range);
        });
      });
      window.addEventListener('resize', () => drawChart(all, range));
    };

    /* no-cache: Pages serves the JSON with its own lifetime while the data changes
       once a day - revalidating by ETag is cheaper than showing yesterday's figures.
       No data or an unreachable file simply leaves the block hidden. */
    fetch(stats.dataset.src, { cache: 'no-cache' })
      .then(r => (r.ok ? r.json() : Promise.reject(r.status)))
      .then(render)
      .catch(() => {});
  }
})();
