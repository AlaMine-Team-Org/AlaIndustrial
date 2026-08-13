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
  const search = document.getElementById('wiki-search');
  if (search) {
    const cards = [...document.querySelectorAll('[data-search]')];
    const empty = document.getElementById('empty-search');
    const filter = () => {
      const query = search.value.trim().toLocaleLowerCase(document.documentElement.lang || 'ru');
      let visible = 0;
      cards.forEach(card => {
        const match = !query || (card.dataset.search + ' ' + card.textContent).toLocaleLowerCase().includes(query);
        card.hidden = !match;
        if (match) visible++;
      });
      if (empty) empty.style.display = visible ? 'none' : 'block';
    };
    search.addEventListener('input', filter);
    document.addEventListener('keydown', event => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault(); search.focus();
      }
    });
  }

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
        const per = (m1 - m0 + c1 - c0) / gap;
        for (let g = gap; g >= 1; g--) {
          out.push({
            date: new Date(Date.parse(d1) - (g - 1) * 86400000).toISOString().slice(0, 10),
            v: Math.max(0, Math.round(per)),
            est: gap > 1,
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
