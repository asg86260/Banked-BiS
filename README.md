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

## Current limitations

- Magic recommendations only consider powered staves (trident/sang/shadow,
  Thammaron's/accursed sceptres, etc.); autocast spell selection is not
  implemented yet.
- Rigour and Augury are assumed unlocked if your prayer level allows them.
- Items are matched by exact id, so an uncharged/broken variant of an item is
  treated as a different item than its usable form.
- Special attacks are not modeled; recommendations are sustained DPS only.
- Dharok's set (scales with missing HP) is not recommended since sustained
  full-HP DPS is assumed.

## Data sources & credits

- DPS engine adapted from [LlemonDuck/dps-calculator](https://github.com/LlemonDuck/dps-calculator)
  (BSD-2, see `LICENSE-dps-calculator`) - thank you Paul Norton.
- Equipment and monster stats from the OSRS Wiki team's
  [weirdgloop/osrs-dps-calc](https://github.com/weirdgloop/osrs-dps-calc) data,
  fetched at startup (cached for 24h under `.runelite/bank-bis/`).

Item ownership snapshots are stored locally per account under
`.runelite/bank-bis/` and never leave your machine.
