package monitoring;

import at.jku.ase.logging.IDoplerLogger;
import at.jku.ase.logging.LoggingProvider;
import at.jku.mevss.eventdistributor.core.DistributionException;
import at.jku.mevss.eventpublisher.core.api.IProbePoint;
import at.jku.mevss.eventpublisher.core.service.PublishService;

public class Probe {

	final IDoplerLogger LOGGER = LoggingProvider.getLogger(Probe.class);
	
	public TurnProbePoint addProbePoint(String name) {
		try {
			
			IProbePoint probePoint  = PublishService.getInstance()
					.createProbePoint(name, "Robocode", "battle.robocode.battlemanager", Probe.class.getName());

			TurnProbePoint p = new TurnProbePoint(probePoint);
			p.start();
			return p;
		} catch (DistributionException e) {
			LOGGER.error(e);
		}
		return null;
	}
}