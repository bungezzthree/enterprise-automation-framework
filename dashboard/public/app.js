'use strict';

const state = { data: null, project: 'all', category: 'all', openRun: null };
const $ = (sel) => document.querySelector(sel);
const SERIES = ['--series-1', '--series-2', '--series-3'];
const CATEGORIES = [
  { key: 'regression', label: 'Regression' },
  { key: 'sanity', label: 'Sanity' },
  { key: 'newfeature', label: 'New feature' },
];
const STATUS = {
  PASSED: { icon: '✓', label: 'Passed' },
  FAILED: { icon: '✕', label: 'Failed' },
  PENDING: { icon: '!', label: 'Pending' },
  NONE: { icon: '–', label: 'Not run' },
};

// ---------- helpers ----------

const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const pct = (num, den) => (den > 0 ? (100 * num) / den : null);
const fmtPct = (v) => (v == null ? '—' : `${v >= 99.95 || v === 0 ? v.toFixed(0) : v.toFixed(1)}%`);
const fmtDuration = (ms) => {
  if (ms == null) return '—';
  if (ms < 1000) return `${ms} ms`;
  const s = ms / 1000;
  return s < 60 ? `${s.toFixed(1)} s` : `${Math.floor(s / 60)}m ${Math.round(s % 60)}s`;
};
const fmtWhen = (iso) => {
  if (!iso) return '—';
  const d = new Date(iso);
  const mins = Math.round((Date.now() - d.getTime()) / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins} min ago`;
  if (mins < 24 * 60) return `${Math.round(mins / 60)} h ago`;
  return d.toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
};
const fmtDateTime = (iso) => (iso ? new Date(iso).toLocaleString(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—');
const cssVar = (name) => getComputedStyle(document.documentElement).getPropertyValue(name).trim();

function inCategory(tc, cat) {
  if (cat === 'all') return true;
  if (cat === 'regression') return tc.is_regression === 1;
  if (cat === 'sanity') return tc.is_sanity === 1;
  return tc.is_new_feature === 1;
}

function statusBadge(status) {
  const key = STATUS[status] ? status : 'NONE';
  return `<span class="status status-${key}"><span class="status-icon" aria-hidden="true">${STATUS[key].icon}</span>${STATUS[key].label}</span>`;
}

function categoryChips(tc) {
  const chips = [];
  if (tc.is_regression) chips.push('Regression');
  if (tc.is_sanity) chips.push('Sanity');
  if (tc.is_new_feature) chips.push('New feature');
  return chips.map((c) => `<span class="chip">${c}</span>`).join('');
}

function meter(value) {
  const w = value == null ? 0 : Math.max(0, Math.min(100, value));
  return `<div class="meter"><div class="meter-track"><div class="meter-fill" style="width:${w}%"></div></div><span class="meter-value">${fmtPct(value)}</span></div>`;
}

function stackBar(m) {
  const total = m.passed + m.failed + m.pending;
  if (!total) return '<span class="muted small">No results</span>';
  const seg = (n, cls) => (n ? `<span class="${cls}" style="flex:${n}"></span>` : '');
  return `<div class="stackbar" data-tip="${esc(JSON.stringify({ title: 'Latest run', rows: [['Passed', m.passed], ['Failed', m.failed], ['Pending', m.pending]] }))}">
      ${seg(m.passed, 'seg-passed')}${seg(m.failed, 'seg-failed')}${seg(m.pending, 'seg-pending')}</div>
    <div class="counts">${m.passed} passed · ${m.failed} failed · ${m.pending} pending</div>`;
}

// ---------- metrics ----------

function projectsInScope() {
  const all = state.data.projects;
  return state.project === 'all' ? all : all.filter((p) => String(p.id) === state.project);
}

function latestRun(projectId) {
  const runs = state.data.runs.filter((r) => r.project_id === projectId);
  return runs[runs.length - 1] ?? null;
}

function metrics(projectIds, cat) {
  const ids = new Set(projectIds);
  const tcs = state.data.testCases.filter((tc) => ids.has(tc.project_id) && inCategory(tc, cat));
  const tcIds = new Set(tcs.map((tc) => tc.id));
  const results = state.data.latestResults.filter((r) => ids.has(r.project_id)
    && (r.test_case_id == null ? cat === 'all' : tcIds.has(r.test_case_id)));
  const count = (s) => results.filter((r) => r.status === s).length;
  const passed = count('PASSED');
  const failed = count('FAILED');
  const pending = count('PENDING');
  const automated = tcs.filter((tc) => tc.is_automated === 1).length;
  return {
    total: tcs.length,
    automated,
    coverage: pct(automated, tcs.length),
    passed,
    failed,
    pending,
    executed: passed + failed + pending,
    successRate: pct(passed, passed + failed + pending),
  };
}

// ---------- render ----------

function renderKpis() {
  const m = metrics(projectsInScope().map((p) => p.id), state.category);
  const tile = (label, value, sub, status) => `
    <div class="kpi">
      <span class="kpi-label">${status ? `<span class="status status-${status}"><span class="status-icon" aria-hidden="true">${STATUS[status].icon}</span></span>` : ''}${label}</span>
      <span class="kpi-value">${value}</span>
      <span class="kpi-sub">${sub}</span>
    </div>`;
  $('#kpis').innerHTML = [
    tile('Test cases', m.total, `${projectsInScope().length} project${projectsInScope().length === 1 ? '' : 's'}`),
    tile('Automated', m.automated, `${m.total - m.automated} still manual`),
    tile('Automation coverage', fmtPct(m.coverage), 'automated ÷ test cases'),
    tile('Success rate', fmtPct(m.successRate), `${m.executed} executed in latest runs`),
    tile('Passed', m.passed, 'latest runs', 'PASSED'),
    tile('Failed', m.failed, 'latest runs', 'FAILED'),
    tile('Pending', m.pending, 'skipped / not implemented', 'PENDING'),
  ].join('');
}

function renderProjects() {
  const rows = projectsInScope().map((p) => {
    const m = metrics([p.id], state.category);
    const run = latestRun(p.id);
    return `<tr>
      <td><div class="proj-name">${esc(p.name)}</div><div class="small muted"><span class="badge">${esc(p.type)}</span>${esc(p.project_key)}</div></td>
      <td class="num">${m.total}</td>
      <td class="num">${m.automated}</td>
      <td>${meter(m.coverage)}</td>
      <td class="num"><b>${fmtPct(m.successRate)}</b></td>
      <td>${stackBar(m)}</td>
      <td>${run ? `<div>${fmtWhen(run.finished_at ?? run.started_at)}</div><div class="small muted">#${run.id} · ${esc(run.trigger_source)} · ${esc(run.engine ?? '')}</div>` : '<span class="muted">Never run</span>'}</td>
    </tr>`;
  });
  $('#projectsTable').innerHTML = `<thead><tr>
      <th>Project</th><th class="num">Test cases</th><th class="num">Automated</th><th>Coverage</th>
      <th class="num">Success rate</th><th>Passed / failed / pending</th><th>Last run</th></tr></thead>
    <tbody>${rows.join('') || '<tr><td colspan="7" class="empty">No projects yet. Run a test suite to populate the dashboard.</td></tr>'}</tbody>`;
}

function renderCategories() {
  const ids = projectsInScope().map((p) => p.id);
  const rows = CATEGORIES.map(({ key, label }) => {
    const m = metrics(ids, key);
    return `<tr class="${state.category === key ? 'selected' : ''}">
      <td><b>${label}</b></td>
      <td class="num">${m.automated} / ${m.total}</td>
      <td>${meter(m.coverage)}</td>
      <td class="num">${fmtPct(m.successRate)}</td>
      <td>${stackBar(m)}</td>
    </tr>`;
  });
  $('#categoryTable').innerHTML = `<thead><tr><th>Category</th><th class="num">Automated</th><th>Coverage</th>
      <th class="num">Success</th><th>Latest results</th></tr></thead><tbody>${rows.join('')}</tbody>`;
}

function renderTestCases() {
  const ids = new Set(projectsInScope().map((p) => p.id));
  const search = $('#tcSearch').value.trim().toLowerCase();
  const statusFilter = $('#tcStatus').value;
  const projectName = Object.fromEntries(state.data.projects.map((p) => [p.id, p.name]));
  const tcs = state.data.testCases.filter((tc) => {
    if (!ids.has(tc.project_id) || !inCategory(tc, state.category)) return false;
    if (search && !`${tc.tc_key} ${tc.title}`.toLowerCase().includes(search)) return false;
    if (statusFilter === 'MANUAL') return tc.is_automated !== 1;
    if (statusFilter === 'NOT_RUN') return tc.is_automated === 1 && !tc.last_status;
    if (statusFilter !== 'all') return tc.last_status === statusFilter;
    return true;
  });
  const rows = tcs.map((tc) => `<tr>
      <td><b>${esc(tc.tc_key)}</b>${tc.testlink_id ? `<div class="small muted">${esc(tc.testlink_id)}</div>` : ''}</td>
      <td>${esc(tc.title)}${tc.last_status === 'FAILED' && tc.last_error ? `<div class="error-text">${esc(tc.last_error)}</div>` : ''}</td>
      <td class="small">${esc(projectName[tc.project_id])}</td>
      <td>${categoryChips(tc)}</td>
      <td>${tc.is_automated ? 'Automated' : '<span class="muted">Manual</span>'}</td>
      <td>${tc.is_automated ? statusBadge(tc.last_status) : '<span class="muted">—</span>'}</td>
      <td class="num">${fmtDuration(tc.last_duration_ms)}</td>
      <td class="small muted">${fmtWhen(tc.last_executed_at)}</td>
    </tr>`);
  $('#testCaseTable').innerHTML = `<thead><tr><th>ID</th><th>Title</th><th>Project</th><th>Category</th>
      <th>Automation</th><th>Last result</th><th class="num">Duration</th><th>Executed</th></tr></thead>
    <tbody>${rows.join('') || '<tr><td colspan="8" class="empty">No test cases match these filters.</td></tr>'}</tbody>`;
}

function renderRuns() {
  const ids = new Set(projectsInScope().map((p) => p.id));
  const projectName = Object.fromEntries(state.data.projects.map((p) => [p.id, p.name]));
  const runs = state.data.runs.filter((r) => ids.has(r.project_id)).slice().reverse().slice(0, 15);
  const rows = runs.map((r) => {
    const rate = pct(r.passed, r.total);
    const open = state.openRun === r.id;
    return `<tr class="clickable ${open ? 'selected' : ''}" tabindex="0" data-run="${r.id}" aria-expanded="${open}">
        <td><b>#${r.id}</b></td>
        <td>${esc(projectName[r.project_id])}</td>
        <td>${fmtDateTime(r.started_at)}</td>
        <td class="num">${fmtDuration(r.duration_ms)}</td>
        <td>${esc(r.environment)} · ${esc(r.trigger_source)}${r.build_number ? ` #${esc(r.build_number)}` : ''}</td>
        <td class="small">${esc(r.engine ?? '')}${r.tag_filter ? `<div class="muted">${esc(r.tag_filter)}</div>` : ''}</td>
        <td class="num">${r.passed} / ${r.failed} / ${r.pending}</td>
        <td class="num"><b>${fmtPct(rate)}</b></td>
      </tr>${open ? `<tr class="detail"><td colspan="8" id="runDetail">Loading…</td></tr>` : ''}`;
  });
  $('#runsTable').innerHTML = `<thead><tr><th>Run</th><th>Project</th><th>Started</th><th class="num">Duration</th>
      <th>Env · trigger</th><th>Engine · tags</th><th class="num">Pass / fail / pend</th><th class="num">Success</th></tr></thead>
    <tbody>${rows.join('') || '<tr><td colspan="8" class="empty">No runs yet.</td></tr>'}</tbody>`;
  if (state.openRun != null && runs.some((r) => r.id === state.openRun)) loadRunDetail(state.openRun);
}

async function loadRunDetail(runId) {
  const res = await fetch(`/api/runs/${runId}/results`);
  const results = await res.json();
  const cell = $('#runDetail');
  if (!cell) return;
  cell.innerHTML = `<table class="data"><thead><tr><th>ID</th><th>Scenario</th><th>Feature</th><th>Result</th><th class="num">Duration</th></tr></thead><tbody>
    ${results.map((r) => `<tr><td>${esc(r.tc_key ?? '—')}</td><td>${esc(r.scenario_name)}${r.error_message ? `<div class="error-text">${esc(r.error_message)}</div>` : ''}</td>
      <td class="small muted">${esc(r.feature_file ?? '')}</td><td>${statusBadge(r.status)}</td><td class="num">${fmtDuration(r.duration_ms)}</td></tr>`).join('')}
  </tbody></table>`;
}

// ---------- trend chart (SVG) ----------

function renderTrend() {
  const host = $('#trend');
  const projects = projectsInScope();
  const colorOf = Object.fromEntries(state.data.projects.map((p, i) => [p.id, SERIES[i % SERIES.length]]));
  const series = projects.map((p) => ({
    project: p,
    color: cssVar(colorOf[p.id]),
    points: state.data.runs.filter((r) => r.project_id === p.id && r.total > 0)
      .map((r) => ({ t: new Date(r.started_at).getTime(), v: (100 * r.passed) / r.total, run: r })),
  })).filter((s) => s.points.length);

  if (!series.length) {
    host.innerHTML = '<p class="empty">No completed runs yet.</p>';
    $('#trendLegend').innerHTML = '';
    return;
  }
  const W = 640, H = 240, m = { l: 40, r: 150, t: 16, b: 28 };
  const all = series.flatMap((s) => s.points);
  let t0 = Math.min(...all.map((p) => p.t)), t1 = Math.max(...all.map((p) => p.t));
  if (t0 === t1) { t0 -= 3600e3; t1 += 3600e3; }
  const x = (t) => m.l + ((t - t0) / (t1 - t0)) * (W - m.l - m.r);
  const y = (v) => m.t + (1 - v / 100) * (H - m.t - m.b);

  const grid = [0, 25, 50, 75, 100].map((v) => `
    <line class="${v === 0 ? 'baseline' : 'gridline'}" x1="${m.l}" x2="${W - m.r}" y1="${y(v)}" y2="${y(v)}"/>
    <text class="tick" x="${m.l - 8}" y="${y(v) + 4}" text-anchor="end">${v}%</text>`).join('');
  const fmtTick = (t) => new Date(t).toLocaleString(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
  const xTicks = [t0, t1].map((t, i) => `<text class="tick" x="${x(t)}" y="${H - 8}" text-anchor="${i ? 'end' : 'start'}">${fmtTick(t)}</text>`).join('');

  // Direct end labels sit in the right gutter, nudged apart so equal values never overlap.
  const labels = series.map((s) => ({ s, last: s.points[s.points.length - 1] }))
    .map((l) => ({ ...l, y: y(l.last.v) + 4 })).sort((a, b) => a.y - b.y);
  labels.forEach((l, i) => { if (i && l.y - labels[i - 1].y < 14) l.y = labels[i - 1].y + 14; });
  const lines = series.map((s) => {
    const d = s.points.map((p, i) => `${i ? 'L' : 'M'}${x(p.t).toFixed(1)},${y(p.v).toFixed(1)}`).join(' ');
    return `<path class="series-line" d="${d}" stroke="${s.color}"/>
      ${s.points.map((p) => `<circle class="dot" cx="${x(p.t)}" cy="${y(p.v)}" r="4.5" fill="${s.color}"/>`).join('')}`;
  }).join('') + labels.map((l) => `<text class="end-label" x="${W - m.r + 12}" y="${l.y}">${esc(l.s.project.type)} · ${fmtPct(l.last.v)}</text>`).join('');

  host.innerHTML = `<svg viewBox="0 0 ${W} ${H}" role="img" aria-label="Success rate per run for ${series.length} project(s)">
      ${grid}${xTicks}${lines}<line class="crosshair" id="xhair" y1="${m.t}" y2="${H - m.b}" visibility="hidden"/>
      <rect id="hit" x="${m.l}" y="${m.t}" width="${W - m.l - m.r}" height="${H - m.t - m.b}" fill="transparent"/></svg>`;
  $('#trendLegend').innerHTML = series.length > 1 ? series.map((s) => `<span class="legend-item"><span class="legend-swatch" style="background:${s.color}"></span>${esc(s.project.name)}</span>`).join('') : '';

  const svg = host.querySelector('svg');
  const hit = svg.querySelector('#hit');
  const xhair = svg.querySelector('#xhair');
  hit.addEventListener('mousemove', (ev) => {
    const box = svg.getBoundingClientRect();
    const px = ((ev.clientX - box.left) / box.width) * W;
    let best = null;
    for (const s of series) for (const p of s.points) {
      const dist = Math.abs(x(p.t) - px);
      if (!best || dist < best.dist) best = { dist, s, p };
    }
    if (!best) return;
    xhair.setAttribute('x1', x(best.p.t));
    xhair.setAttribute('x2', x(best.p.t));
    xhair.setAttribute('visibility', 'visible');
    const r = best.p.run;
    showTip(ev, { title: `${best.s.project.name} · run #${r.id}`, rows: [['Success rate', fmtPct(best.p.v)], ['Passed', r.passed], ['Failed', r.failed], ['Pending', r.pending], ['Started', fmtDateTime(r.started_at)]] });
  });
  hit.addEventListener('mouseleave', () => { xhair.setAttribute('visibility', 'hidden'); hideTip(); });
}

// ---------- tooltip ----------

function showTip(ev, { title, rows }) {
  const tip = $('#tooltip');
  tip.innerHTML = `<div class="tt-title">${esc(title)}</div>${rows.map(([k, v]) => `<div class="tt-row"><span>${esc(k)}</span><b>${esc(v)}</b></div>`).join('')}`;
  tip.hidden = false;
  const pad = 14;
  const { width, height } = tip.getBoundingClientRect();
  tip.style.left = `${Math.min(ev.clientX + pad, window.innerWidth - width - 8)}px`;
  tip.style.top = `${Math.min(ev.clientY + pad, window.innerHeight - height - 8)}px`;
}
function hideTip() { $('#tooltip').hidden = true; }

document.addEventListener('mousemove', (ev) => {
  const el = ev.target.closest?.('[data-tip]');
  if (el) showTip(ev, JSON.parse(el.dataset.tip));
  else if (!ev.target.closest?.('#trend')) hideTip();
});

// ---------- wiring ----------

function render() {
  renderKpis();
  renderProjects();
  renderTrend();
  renderCategories();
  renderTestCases();
  renderRuns();
}

async function load() {
  try {
    const res = await fetch('/api/dashboard', { cache: 'no-store' });
    state.data = await res.json();
    const select = $('#projectFilter');
    const current = state.project;
    select.innerHTML = '<option value="all">All projects</option>'
      + state.data.projects.map((p) => `<option value="${p.id}">${esc(p.name)} (${esc(p.type)})</option>`).join('');
    select.value = state.data.projects.some((p) => String(p.id) === current) ? current : 'all';
    state.project = select.value;
    $('#updated').textContent = `Updated ${new Date(state.data.generatedAt).toLocaleTimeString()}`;
    render();
  } catch (err) {
    $('#updated').textContent = `Could not load data: ${err.message}`;
  }
}

$('#projectFilter').addEventListener('change', (e) => { state.project = e.target.value; state.openRun = null; render(); });
$('#categoryFilter').addEventListener('click', (e) => {
  const btn = e.target.closest('button[data-cat]');
  if (!btn) return;
  state.category = btn.dataset.cat;
  document.querySelectorAll('#categoryFilter button').forEach((b) => b.setAttribute('aria-checked', String(b === btn)));
  render();
});
$('#tcSearch').addEventListener('input', renderTestCases);
$('#tcStatus').addEventListener('change', renderTestCases);
$('#runsTable').addEventListener('click', (e) => {
  const row = e.target.closest('tr[data-run]');
  if (!row) return;
  const id = Number(row.dataset.run);
  state.openRun = state.openRun === id ? null : id;
  renderRuns();
});
$('#runsTable').addEventListener('keydown', (e) => {
  if ((e.key === 'Enter' || e.key === ' ') && e.target.matches('tr[data-run]')) { e.preventDefault(); e.target.click(); }
});
$('#refresh').addEventListener('click', load);

const THEME_KEY = 'qm-theme';
try { const saved = localStorage.getItem(THEME_KEY); if (saved) document.documentElement.dataset.theme = saved; } catch { /* storage unavailable */ }
$('#theme').addEventListener('click', () => {
  const dark = document.documentElement.dataset.theme
    ? document.documentElement.dataset.theme === 'dark'
    : matchMedia('(prefers-color-scheme: dark)').matches;
  document.documentElement.dataset.theme = dark ? 'light' : 'dark';
  try { localStorage.setItem(THEME_KEY, document.documentElement.dataset.theme); } catch { /* ignore */ }
  if (state.data) renderTrend();
});

load();
setInterval(load, 30000);
