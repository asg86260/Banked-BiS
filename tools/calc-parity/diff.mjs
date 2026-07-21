// Joins the wiki and engine result sets on case id and reports every
// divergence beyond tolerance. Reads out/wiki-out.json + out/engine-out.json,
// writes out/report.txt, and prints a summary. Exit code 1 if any divergence
// or error is present (so run.mjs / CI can gate on it).

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.join(HERE, 'out');

// tolerances: max hit must match exactly; accuracy/dps within these fractions.
const ACC_TOL = 0.001; // 0.1%
const DPS_TOL = 0.005; // 0.5%

const cases = JSON.parse(fs.readFileSync(path.join(OUT, 'cases.json'), 'utf8'));
const wiki = index(JSON.parse(fs.readFileSync(path.join(OUT, 'wiki-out.json'), 'utf8')));
const engine = index(JSON.parse(fs.readFileSync(path.join(OUT, 'engine-out.json'), 'utf8')));

function index(arr) {
  const m = new Map();
  for (const r of arr) m.set(r.id, r);
  return m;
}

function relDiff(a, b) {
  if (a === b) return 0;
  const denom = Math.max(Math.abs(a), Math.abs(b), 1e-9);
  return Math.abs(a - b) / denom;
}

const rows = [];
for (const c of cases) {
  const w = wiki.get(c.id);
  const e = engine.get(c.id);
  const label = describe(c);

  if (!w || !e) {
    rows.push({ id: c.id, label, kind: 'MISSING', detail: `wiki=${!!w} engine=${!!e}` });
    continue;
  }
  if (w.error || e.error) {
    rows.push({
      id: c.id, label, kind: 'ERROR',
      detail: `wiki: ${w.error || 'ok'} | engine: ${e.error || 'ok'}`,
    });
    continue;
  }

  const issues = [];
  if (w.maxHit !== e.maxHit) {
    issues.push(`maxHit wiki=${w.maxHit} engine=${e.maxHit}`);
  }
  const accD = relDiff(w.accuracy, e.accuracy);
  if (accD > ACC_TOL) {
    issues.push(`accuracy wiki=${fmt(w.accuracy)} engine=${fmt(e.accuracy)} (${pct(accD)})`);
  }
  const dpsD = relDiff(w.dps, e.dps);
  if (dpsD > DPS_TOL) {
    issues.push(`dps wiki=${fmt(w.dps)} engine=${fmt(e.dps)} (${pct(dpsD)})`);
  }

  if (issues.length) {
    rows.push({ id: c.id, label, kind: 'DIVERGE', detail: issues.join('; '), sort: dpsD });
  } else {
    rows.push({ id: c.id, label, kind: 'OK', detail: `dps=${fmt(e.dps)}`, sort: 0 });
  }
}

function describe(c) {
  const parts = [c.equipment.weapon ? c.equipment.weapon.name : '(unarmed)'];
  if (c.spell) parts.push(`+ ${c.spell}`);
  if (c.onSlayerTask) parts.push('[task]');
  parts.push(`vs ${c.monster.name}`);
  return parts.join(' ');
}

function fmt(n) { return typeof n === 'number' ? n.toFixed(4) : String(n); }
function pct(f) { return `${(f * 100).toFixed(2)}%`; }

const problems = rows.filter((r) => r.kind !== 'OK');
problems.sort((a, b) => (b.sort || 1) - (a.sort || 1));
const ok = rows.filter((r) => r.kind === 'OK');

const lines = [];
lines.push('calc-parity report');
lines.push('='.repeat(60));
lines.push(`cases: ${rows.length}  ok: ${ok.length}  problems: ${problems.length}`);
lines.push('');
if (problems.length) {
  lines.push('PROBLEMS (worst dps divergence first):');
  for (const r of problems) {
    lines.push(`  [${r.kind}] ${r.label}`);
    lines.push(`         ${r.detail}`);
  }
  lines.push('');
}
lines.push('MATCHING:');
for (const r of ok) lines.push(`  [OK] ${r.label}  (${r.detail})`);

const report = lines.join('\n');
fs.writeFileSync(path.join(OUT, 'report.txt'), report);
console.log(report);

process.exit(problems.length ? 1 : 0);
