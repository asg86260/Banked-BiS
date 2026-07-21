# Banked BiS

Tells you the best gear **you actually own** for the content you're about to do.

Pick an activity (a raid room, a God Wars boss, your slayer task, ...) and Banked BiS
searches everything in your bank, group storage, inventory, and worn equipment for
the highest-DPS setup per combat style, using the same combat formulas as the
OSRS Wiki DPS calculator.

## Usage

1. Enable the plugin and open the side panel (gold "BiS" icon).
2. Open your bank once so the plugin knows what you own. It keeps itself up to
   date from then on, even across client restarts.
3. Pick a category and activity, then hit **Find my best gear**.

You'll get a loadout per combat style (melee / ranged / magic), ranked by DPS
against that activity's monster, computed with your current stats and boosts.
Styles the target is immune to (e.g. melee at Zulrah) are omitted.

## What it accounts for

- Full accuracy/max-hit/DPS formulas, including gear special cases: scythe
  multi-hits, Tumeken's shadow scaling, twisted bow scaling, slayer helmet and
  salve stacking, demonbane, dragonhunter weapons, Keris, leafy immunity,
  vampyre tiers, ToB party scaling, and more.
- 2h vs. one-hand + shield trade-offs, matching ammo for bows/crossbows, and
  your best darts for the blowpipe.
- Slayer task presets assume you are on-task (slayer helmet bonuses apply).

- Void, Inquisitor's, and crystal armour sets are evaluated as complete sets
  when you own the pieces, alongside conventional gear.
- Slayer helmets, salve amulets, tomes, and broad ammo are recognized as
  special-cased items, not just stat sticks.
- Ammo compatibility is enforced (arrow tiers per bow, dragon bolts only in
  dragon-tier crossbows, bolt racks only in Karil's, javelins in ballistae).

## Party bank sharing (opt-in)

Enable **Share banks with party** and join a RuneLite party with your group
(e.g. your GIM team): everyone with the setting on shares their equippable
item list (ids and quantities only - no wealth values, no locations) through
the RuneLite party service. Recommendations then include gear a party member
could lend, marked "(party)" in the results. Disabled by default; nothing is
ever shared while the setting is off.

## Accuracy vs. the Wiki DPS Calculator

The math follows the OSRS Wiki DPS calculator's formulas, and the biggest
mechanics are at parity: melee, ranged, powered staves, standard elemental
spellcasting with **elemental weaknesses** (accuracy and max-hit bonuses),
prayer unlock detection, potion boosts, CoX party/Challenge Mode defence
scaling, and the gear special cases listed above.

Known gaps, so you know exactly when to double-check against the wiki calc:

- **Spellbooks**: only standard elemental spells are auto-selected (best
  castable per element). Ancients, Arceuus (demonbane/grasps), god spells,
  Iban Blast, and Magic Dart are not tried - autocast and unlock state are
  not detectable from the client.
- **Ranged defence split**: monsters' light/standard/heavy ranged defence is
  collapsed to "standard"; heavy crossbow vs. dart distinctions against
  split-defence monsters (e.g. Araxxor) may be slightly off.
- **Flat armour** (Tormented demons' damage reduction) is not modeled.
- **Special attacks** are not modeled; recommendations are sustained DPS.
- **ToA invocations and ToB/ToA party size** scale monster HP only, which
  never changes gear ranking, so they intentionally do not alter results.
- **CoX scaling** assumes a maxed party (the wiki calc's highest-HP/combat
  inputs); Tekton CM uses its exact 20/35% defence boost.
- **Consumables/sundries**: rune costs, sunfire runes, and charge states are
  ignored; items are matched by exact id, so uncharged/broken variants are
  treated as different items.
- **Dharok's** (scales with missing HP) is not recommended since sustained
  full-HP DPS is assumed.
- The engine's item special cases are maintained by hand; very recent items
  may be plain stat-sticks until added.

## Data sources & credits

- DPS engine adapted from [LlemonDuck/dps-calculator](https://github.com/LlemonDuck/dps-calculator)
  (BSD-2, see `LICENSE-dps-calculator`) - thank you Paul Norton.
- Equipment and monster stats from the OSRS Wiki team's
  [weirdgloop/osrs-dps-calc](https://github.com/weirdgloop/osrs-dps-calc) data,
  fetched at startup (cached for 24h under `.runelite/bank-bis/`).

Item ownership snapshots are stored locally per account under
`.runelite/bank-bis/` and never leave your machine.
