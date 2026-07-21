# calc-parity

Dev-only oracle that checks our DPS engine against the [wiki DPS
calc](https://github.com/weirdgloop/osrs-dps-calc) over generated setups.

It generates a list of gear/monster/spell cases, runs each through **both** the
wiki calc and our engine, and reports every case where `maxHit`, `accuracy`, or
`dps` disagree. Each divergence becomes a fix plus a pinned regression test.

## Why this is licensing-clean

The wiki calc is GPL-3; our plugin is BSD-2. This tool **never redistributes**
their code: it clones their repo into a gitignored `wiki-calc/` directory and
runs their own published test harness locally, the same as opening their website
and typing in gear. Nothing of theirs is committed here or shipped in the
plugin. Do not copy their source into the plugin — re-derive formulas only.

## Run it

Requires Node, Git, and JDK 11 (the plugin's toolchain).

```
node run.mjs --wiki    # first time: clone + install the wiki calc, then run
node run.mjs           # subsequent runs
```

This will:
1. clone/checkout the wiki calc at the commit in `pin.txt`,
2. generate `out/cases.json`,
3. run the wiki calc (their jest) → `out/wiki-out.json`,
4. run our engine (`CalcParityDriverTest`) → `out/engine-out.json`,
5. diff → `out/report.txt` (also printed; exit 1 if anything diverges).

## Case schema

`out/cases.json` is a list of neutral cases both sides interpret identically:

```json
{
  "id": "melee-whip-abby",
  "monster": { "name": "Abyssal demon", "version": "" },
  "skills": { "atk": 99, "str": 99, "def": 99, "hp": 99, "ranged": 99, "magic": 99, "prayer": 99 },
  "equipment": { "weapon": { "name": "Abyssal whip", "version": "" } },
  "style": { "type": "slash", "stance": "Controlled" },
  "spell": null,
  "onSlayerTask": false
}
```

- Items/monsters are keyed by `(name, version)` — both sides pull from the same
  weirdgloop JSON, so names line up without an id map.
- `style` is `(type, stance)`, e.g. `slash`/`Aggressive`, `ranged`/`Rapid`,
  `magic`/`Accurate`. For standard-spellbook casting set `spell` instead (e.g.
  `"Fire Surge"`); `style` is ignored.

## Pieces

| file | role |
|------|------|
| `generate-cases.mjs` | curated seed → `out/cases.json` (resolves/validates names) |
| `wiki-driver.test.ts` | copied into the clone, run via their jest → `out/wiki-out.json` |
| `CalcParityDriverTest.java` (in `src/test`) | our engine → `out/engine-out.json` |
| `diff.mjs` | joins on id, reports divergences → `out/report.txt` |
| `run.mjs` | orchestrates all of the above |
| `pin.txt` | wiki calc commit the oracle is pinned to |

## Expanding

Add entries to the `seeds` array in `generate-cases.mjs`. To track upstream
changes, bump `pin.txt` to a newer wiki-calc commit and re-run; new divergences
are the wiki team's changes surfaced as work items.

## Tolerances

`maxHit` must match exactly; `accuracy` within 0.1%, `dps` within 0.5% (see
`diff.mjs`). Tighten as parity improves.
