package com.duckblade.osrs.dpscalc.calc;

import com.duckblade.osrs.dpscalc.calc.compute.Computable;
import com.duckblade.osrs.dpscalc.calc.compute.ComputeContext;
import com.duckblade.osrs.dpscalc.calc.multihit.MultiHitDptComputable;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class DptComputable implements Computable<Double>
{

	private final BaseHitDptComputable baseHitDptComputable;
	private final Set<MultiHitDptComputable> multiHitDptComputables;
	private final DamageReductionComputable damageReductionComputable;

	@Override
	public Double compute(ComputeContext context)
	{
		double dpt = multiHitDptComputables.stream()
			.filter(mhdc -> mhdc.isApplicable(context))
			.findFirst()
			.map(context::get)
			.orElseGet(() -> context.get(baseHitDptComputable));
		// flat monster damage reductions (e.g. Kraken takes 1/7 from ranged)
		return dpt * context.get(damageReductionComputable);
	}

}
