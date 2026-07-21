// calc-parity orchestrator. One command to: ensure the wiki calc is cloned at
// the pinned commit, generate cases, run both engines, and diff.
//
//   node run.mjs           full run
//   node run.mjs --wiki    (re)clone + install the wiki calc, then run
//
// The clone and all outputs are gitignored. Nothing of the wiki calc's GPL
// source is committed here; we only run it locally as a test tool.

import { spawnSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const WIKI = path.join(HERE, 'wiki-calc');
const OUT = path.join(HERE, 'out');
const REPO_ROOT = path.resolve(HERE, '..', '..');
const PIN = fs.readFileSync(path.join(HERE, 'pin.txt'), 'utf8').trim();
const REMOTE = 'https://github.com/weirdgloop/osrs-dps-calc.git';

function run(cmd, args, opts = {}) {
  console.log(`\n$ ${cmd} ${args.join(' ')}`);
  const r = spawnSync(cmd, args, { stdio: 'inherit', shell: true, ...opts });
  if (r.status !== 0) {
    throw new Error(`command failed (${r.status}): ${cmd} ${args.join(' ')}`);
  }
}

function ensureWikiClone() {
  if (!fs.existsSync(path.join(WIKI, '.git'))) {
    console.log('cloning wiki calc...');
    run('git', ['clone', REMOTE, JSON.stringify(WIKI)]);
  }
  run('git', ['-C', JSON.stringify(WIKI), 'fetch', '--depth', '1', 'origin', PIN]);
  run('git', ['-C', JSON.stringify(WIKI), 'checkout', '--force', PIN]);
  if (!fs.existsSync(path.join(WIKI, 'node_modules'))) {
    console.log('installing wiki calc deps (yarn)...');
    run('corepack', ['yarn', 'install'], { cwd: WIKI });
  }
}

function stageWikiDriver() {
  const dest = path.join(WIKI, 'src', 'tests', 'CalcParityDriver.test.ts');
  fs.copyFileSync(path.join(HERE, 'wiki-driver.test.ts'), dest);
}

const wantWiki = process.argv.includes('--wiki');

if (wantWiki || !fs.existsSync(path.join(WIKI, 'cdn', 'json', 'equipment.json'))) {
  ensureWikiClone();
}

console.log('\n=== generate cases ===');
run('node', [JSON.stringify(path.join(HERE, 'generate-cases.mjs'))]);

console.log('\n=== run wiki calc ===');
stageWikiDriver();
run('corepack', ['yarn', 'jest', '--coverage=false', 'src/tests/CalcParityDriver.test.ts'], {
  cwd: WIKI,
  env: { ...process.env, CALC_PARITY_OUT: OUT },
});

console.log('\n=== run engine ===');
const gradlew = process.platform === 'win32'
  ? JSON.stringify(path.join(REPO_ROOT, 'gradlew.bat'))
  : './gradlew';
run(gradlew, ['test', '--tests', 'com.bankbis.parity.CalcParityDriverTest', '--rerun', '-q'], {
  cwd: REPO_ROOT,
  env: { ...process.env, CALC_PARITY_DIR: HERE },
});

console.log('\n=== diff ===');
const diff = spawnSync('node', [JSON.stringify(path.join(HERE, 'diff.mjs'))], {
  stdio: 'inherit', shell: true,
});
process.exit(diff.status || 0);
