// Generates the shared calc-parity case list (out/cases.json) that both the
// wiki calc and our engine run. Resolves item/monster names against the cloned
// wiki data so every case references data that actually exists, and records the
// exact (name, version) both sides must match on.
//
// Seed set is small and curated for v1: it validates the harness across melee,
// ranged and magic, and deliberately includes the open magic + slayer-helm
// discrepancy (~6.3 vs 6.6) so the oracle reproduces it. Expand freely.

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const CDN = path.join(HERE, 'wiki-calc', 'cdn', 'json');
const OUT = path.join(HERE, 'out');

const equipment = JSON.parse(fs.readFileSync(path.join(CDN, 'equipment.json'), 'utf8'));
const monsters = JSON.parse(fs.readFileSync(path.join(CDN, 'monsters.json'), 'utf8'));

const MAXED = {
  atk: 99, str: 99, def: 99, hp: 99, ranged: 99, magic: 99, prayer: 99,
};

const skipped = [];

function resolveItem(spec) {
  // spec is a bare name, or [name, version].
  const [name, version] = Array.isArray(spec) ? spec : [spec, undefined];
  const hits = equipment.filter((e) => e.name === name
    && (version === undefined || (e.version || '') === version));
  if (hits.length === 0) {
    skipped.push(`equipment '${name}'${version !== undefined ? ` (${version})` : ''}`);
    return null;
  }
  return { name: hits[0].name, version: hits[0].version || '' };
}

function resolveMonster(name, version) {
  const hits = monsters.filter((m) => m.name === name
    && (version === undefined || (m.version || '') === version));
  if (hits.length === 0) {
    skipped.push(`monster '${name}'`);
    return null;
  }
  return { name: hits[0].name, version: hits[0].version || '' };
}

// spec -> case (or null if any referenced data is missing)
function build(spec) {
  const monster = resolveMonster(spec.monster, spec.monsterVersion);
  if (!monster) return null;

  const equipmentRefs = {};
  // shorthand slots plus an optional full `gear` map (slot -> name | [name, version])
  const slots = {
    weapon: spec.weapon, ammo: spec.ammo, head: spec.head, ...(spec.gear || {}),
  };
  for (const [slot, item] of Object.entries(slots)) {
    if (!item) continue;
    const ref = resolveItem(item);
    if (!ref) return null;
    equipmentRefs[slot] = ref;
  }

  return {
    id: spec.id,
    monster,
    skills: MAXED,
    equipment: equipmentRefs,
    style: spec.spell ? null : spec.style,
    spell: spec.spell || null,
    prayers: spec.prayers || [],
    onSlayerTask: !!spec.onSlayerTask,
  };
}

const SLASH_AGG = { type: 'slash', stance: 'Aggressive' };
const STAB_AGG = { type: 'stab', stance: 'Aggressive' };
const WHIP_LASH = { type: 'slash', stance: 'Controlled' };
const RANGED_RAPID = { type: 'ranged', stance: 'Rapid' };
const MAGIC_ACC = { type: 'magic', stance: 'Accurate' };

const seeds = [
  // --- melee, weapon only ---
  { id: 'melee-whip-abby', weapon: 'Abyssal whip', monster: 'Abyssal demon', style: WHIP_LASH },
  { id: 'melee-scim-abby', weapon: 'Dragon scimitar', monster: 'Abyssal demon', style: SLASH_AGG },
  { id: 'melee-rapier-abby', weapon: 'Ghrazi rapier', monster: 'Abyssal demon', style: STAB_AGG },
  { id: 'melee-whip-graardor', weapon: 'Abyssal whip', monster: 'General Graardor', style: WHIP_LASH },
  { id: 'melee-scythe-graardor', weapon: 'Scythe of vitur', monster: 'General Graardor', style: SLASH_AGG },
  { id: 'melee-ags-graardor', weapon: 'Armadyl godsword', monster: 'General Graardor', style: SLASH_AGG },

  // --- ranged, weapon + ammo ---
  { id: 'ranged-tbow-abby', weapon: 'Twisted bow', ammo: 'Dragon arrow', monster: 'Abyssal demon', style: RANGED_RAPID },
  { id: 'ranged-bofa-abby', weapon: 'Bow of faerdhinen (c)', monster: 'Abyssal demon', style: RANGED_RAPID },
  { id: 'ranged-zcb-abby', weapon: 'Zaryte crossbow', ammo: 'Ruby dragon bolts (e)', monster: 'Abyssal demon', style: RANGED_RAPID },

  // baseline: plain crossbow + unenchanted bolts (isolates base ranged calc)
  { id: 'ranged-acb-plain', weapon: 'Armadyl crossbow', ammo: 'Broad bolts', monster: 'Abyssal demon', style: RANGED_RAPID },

  // --- enchanted bolt procs, on Zaryte crossbow (passive zcb bonus applies) ---
  ...['Ruby', 'Diamond', 'Onyx', 'Dragonstone', 'Opal', 'Pearl'].map((gem) => ({
    id: `bolt-zcb-${gem.toLowerCase()}`,
    weapon: 'Zaryte crossbow',
    ammo: `${gem} dragon bolts (e)`,
    monster: 'Abyssal demon',
    style: RANGED_RAPID,
  })),
  // --- enchanted bolts on a plain crossbow (base proc chances/caps) ---
  ...['Ruby', 'Diamond', 'Onyx'].map((gem) => ({
    id: `bolt-acb-${gem.toLowerCase()}`,
    weapon: 'Armadyl crossbow',
    ammo: `${gem} dragon bolts (e)`,
    monster: 'Abyssal demon',
    style: RANGED_RAPID,
  })),

  // --- magic, powered staves (no spell) ---
  { id: 'magic-trident-abby', weapon: 'Trident of the swamp', monster: 'Abyssal demon', style: MAGIC_ACC },
  { id: 'magic-sang-abby', weapon: 'Sanguinesti staff', monster: 'Abyssal demon', style: MAGIC_ACC },

  // --- magic, standard spell: the open slayer-helm discrepancy ---
  { id: 'magic-surge-slayerhelm-ontask', weapon: 'Staff of fire', head: 'Slayer helmet (i)', monster: 'Abyssal demon', spell: 'Fire Surge', onSlayerTask: true },
  { id: 'magic-surge-slayerhelm-offtask', weapon: 'Staff of fire', head: 'Slayer helmet (i)', monster: 'Abyssal demon', spell: 'Fire Surge', onSlayerTask: false },
  { id: 'magic-surge-nohelm', weapon: 'Staff of fire', monster: 'Abyssal demon', spell: 'Fire Surge' },

  // --- reported Kraken discrepancy: full mage loadout, on task, no prayer ---
  // Kraken has an earth weakness (severity 50), so Earth Surge is the strong pick.
  // --- prayers (attack/strength mods across all three styles) ---
  { id: 'prayer-melee-piety', weapon: 'Abyssal whip', monster: 'Abyssal demon', style: WHIP_LASH, prayers: ['PIETY'] },
  { id: 'prayer-ranged-rigour', weapon: 'Bow of faerdhinen (c)', monster: 'Abyssal demon', style: RANGED_RAPID, prayers: ['RIGOUR'] },
  { id: 'prayer-magic-augury', weapon: 'Trident of the swamp', monster: 'Abyssal demon', style: MAGIC_ACC, prayers: ['AUGURY'] },

  // --- bolt immunity / attribute branches (validate the new proc code) ---
  { id: 'immunity-onyx-undead', weapon: 'Zaryte crossbow', ammo: 'Onyx dragon bolts (e)', monster: 'Aberrant spectre', style: RANGED_RAPID },
  { id: 'immunity-onyx-vorkath', weapon: 'Zaryte crossbow', ammo: 'Onyx dragon bolts (e)', monster: 'Vorkath', monsterVersion: 'Post-quest', style: RANGED_RAPID },
  { id: 'immunity-dstone-vorkath', weapon: 'Zaryte crossbow', ammo: 'Dragonstone dragon bolts (e)', monster: 'Vorkath', monsterVersion: 'Post-quest', style: RANGED_RAPID },
  { id: 'fiery-pearl-vorkath', weapon: 'Zaryte crossbow', ammo: 'Pearl dragon bolts (e)', monster: 'Vorkath', monsterVersion: 'Post-quest', style: RANGED_RAPID },
  { id: 'ruby-infinite-crab', weapon: 'Zaryte crossbow', ammo: 'Ruby dragon bolts (e)', monster: 'Gemstone Crab', style: RANGED_RAPID },

  // --- ranged defence split: bow=standard(-15), crossbow=heavy(+15), thrown=light(+15) ---
  { id: 'rdef-bow-spectre', weapon: 'Bow of faerdhinen (c)', monster: 'Aberrant spectre', style: RANGED_RAPID },
  { id: 'rdef-thrown-spectre', weapon: 'Dragon knife', monster: 'Aberrant spectre', style: RANGED_RAPID },

  // --- salve amulet vs undead ---
  { id: 'salve-melee-undead', monster: 'Aberrant spectre', style: WHIP_LASH, gear: { weapon: 'Abyssal whip', neck: 'Salve amulet(ei)' } },

  // --- elite void ranged set ---
  { id: 'void-ranged', monster: 'Abyssal demon', style: RANGED_RAPID, gear: {
    weapon: 'Armadyl crossbow', ammo: 'Broad bolts',
    head: ['Void ranger helm', 'Normal'], body: ['Elite void top', 'Normal'],
    legs: ['Elite void robe', 'Normal'], hands: ['Void knight gloves', 'Normal'],
  } },

  // --- ancient spell (README-declared gap: quantify the divergence) ---
  { id: 'ancient-barrage', weapon: 'Kodai wand', monster: 'Abyssal demon', spell: 'Ice Barrage' },

  ...['Earth Surge', 'Fire Surge'].map((spell) => ({
    id: `kraken-${spell.split(' ')[0].toLowerCase()}surge`,
    monster: 'Kraken',
    monsterVersion: 'Kraken',
    onSlayerTask: true,
    spell,
    gear: {
      weapon: ["Ahrim's staff", 'Undamaged'],
      head: ['Slayer helmet (i)', ''],
      cape: ['Imbued saradomin cape', 'Normal'],
      neck: ['Amulet of glory', '6'],
      body: ['Blue moon chestplate', 'New'],
      legs: ['Blue moon tassets', 'New'],
      hands: ['Barrows gloves', ''],
      feet: ['Mystic boots', ''],
      ring: ['Lunar ring', ''],
      shield: ['Book of the dead', ''],
    },
  })),
];

const cases = seeds.map(build).filter(Boolean);

fs.mkdirSync(OUT, { recursive: true });
fs.writeFileSync(path.join(OUT, 'cases.json'), JSON.stringify(cases, null, 2));

console.log(`calc-parity: wrote ${cases.length} cases to out/cases.json`);
if (skipped.length) {
  console.log(`  skipped ${skipped.length} case(s) with missing data:`);
  for (const s of [...new Set(skipped)]) console.log(`    - ${s}`);
}
