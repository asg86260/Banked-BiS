import { describe, test } from '@jest/globals';
import * as fs from 'fs';
import * as path from 'path';
import {
  calculatePlayerVsNpc, findEquipment, findSpell, getTestMonster, getTestPlayer,
} from '@/tests/utils/TestUtils';
import { getCombatStylesForCategory } from '@/utils';
import { Prayer } from '@/enums/Prayer';

/**
 * Wiki side of the calc-parity oracle. Copied into the cloned wiki calc's
 * src/tests/ at run time (so the @/ aliases + cdn json resolve), then run via
 * their own jest. Reads the shared case list, runs each case through the wiki
 * calc, and writes {id, maxHit, accuracy, dps} for diffing against our engine.
 *
 * Running their published test harness locally is the intended use of the GPL
 * source: nothing of theirs is redistributed. Driven by run.mjs.
 */

const OUT = process.env.CALC_PARITY_OUT || path.resolve(__dirname, '../../out');

interface ItemRef { name: string; version?: string }
interface Case {
  id: string;
  monster: { name: string; version?: string };
  skills: Record<string, number>;
  equipment: Record<string, ItemRef>;
  style: { type: string; stance: string } | null;
  spell: string | null;
  prayers: string[];
  onSlayerTask: boolean;
}
interface Result {
  id: string;
  maxHit: number | null;
  accuracy: number | null;
  dps: number | null;
  error: string | null;
}

function runCase(c: Case): Result {
  try {
    const monster = getTestMonster(c.monster.name, c.monster.version || '');

    const equipment: Record<string, unknown> = {};
    for (const [slot, ref] of Object.entries(c.equipment)) {
      equipment[slot] = findEquipment(ref.name, ref.version || '');
    }

    const overrides: Record<string, unknown> = {
      skills: c.skills,
      equipment,
      prayers: (c.prayers || []).map((k) => (Prayer as Record<string, unknown>)[k]),
      buffs: { onSlayerTask: c.onSlayerTask },
    };

    if (c.spell) {
      overrides.style = { name: 'Autocast', type: 'magic', stance: 'Autocast' };
      overrides.spell = findSpell(c.spell);
    } else {
      if (!c.style) {
        throw new Error('case has neither spell nor style');
      }
      const weapon = equipment.weapon as { category: unknown } | undefined;
      if (!weapon) {
        throw new Error('no weapon in loadout');
      }
      const st = getCombatStylesForCategory(weapon.category as never)
        .find((s) => s.type === c.style!.type && s.stance === c.style!.stance);
      if (!st) {
        throw new Error(`no style ${c.style.type}/${c.style.stance} for weapon`);
      }
      overrides.style = st;
    }

    // the wiki test harness hard-codes monsterCurrentHp at 150; use full hp
    // instead so %-current-hp effects (ruby bolts) match the engine's assumption
    if (monster.inputs) {
      monster.inputs.monsterCurrentHp = monster.skills.hp;
    }

    const player = getTestPlayer(monster, overrides as never);
    const r = calculatePlayerVsNpc(monster, player);
    return {
      id: c.id, maxHit: r.maxHit, accuracy: r.accuracy, dps: r.dps, error: null,
    };
  } catch (e) {
    return {
      id: c.id, maxHit: null, accuracy: null, dps: null, error: (e as Error).message,
    };
  }
}

describe('calc-parity wiki driver', () => {
  test('run all cases', () => {
    const cases: Case[] = JSON.parse(fs.readFileSync(path.join(OUT, 'cases.json'), 'utf8'));
    const results = cases.map(runCase);
    fs.writeFileSync(path.join(OUT, 'wiki-out.json'), JSON.stringify(results, null, 0));
    // eslint-disable-next-line no-console
    console.log(`calc-parity: wrote ${results.length} wiki results to ${OUT}`);
  });
});
