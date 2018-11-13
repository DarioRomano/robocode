package monitoring;

import at.jku.mevss.eventdistributor.core.transmit.TransmittableEventDataObject;
import at.jku.mevss.eventdistributor.core.transmit.TransmittableEventObject;
import at.jku.mevss.eventdistributor.core.transmit.TransmittableObjectFactory;
import at.jku.mevss.eventpublisher.core.api.IProbePoint;
import at.jku.mevss.eventpublisher.core.api.ProbeData;
import at.jku.mevss.eventpublisher.core.api.ProbeData.ProbeDataItem;
import at.jku.mevss.util.utils.PreciseTimestamp;

/**
 * 
 * @author Dario Romano
 *	This is a probe class for Robocode that sends
 *	basic information about a turn to the 
 *	ReMinds Framework for evaluation
 */
public class TurnProbePoint extends Thread {
	private IProbePoint probePoint;
	private int robotsCount;
	private int totalTurns;
	private int TPS;
	private boolean running;
	private int maxBullets;
	private int lastTimeTurns;
	private int roundNumber;
	private int battleTime;
	private boolean paused;

	/**
	 * 
	 * @param i adds a Probepoint that sends the available
	 * data
	 */
	public TurnProbePoint(IProbePoint i) {
		this.probePoint = i;
		lastTimeTurns = 0;
	}

	public void run() {
		try {
			while (running) {
				while(paused) {
					Thread.sleep(500);
				}
				Thread.sleep(1000);
				sendData("TurnInformation");
			}
		} catch (Exception e) {

		}
	}

	public void beginBattle() {
		sendData("Battle.start");
	}

	/**
	 * 
	 * @param bullets sets curent amount of bullets
	 */
	public void setCurrentBulletsCount(int bullets) {
		maxBullets = Math.max(bullets, maxBullets);
	}

	public void setTotalTurns(int total) {
		totalTurns = total;
	}

	public void setTPS(int tps) {
		TPS = tps;
	}

	public void setRoundNumber(int roundNum) {
		roundNumber = roundNum;
	}

	public void setBattleTime(int bTime) {
		battleTime = bTime;
	}
	
	public void setPaused(boolean pause) {
		if(!paused && pause) {
		sendData("Battle.paused");
		}else if(paused && !pause) {
		sendData("Battle.resumed");
		}
	}
	
	public void stopThread() {
		running=false;
	}

	/**
	 * Sends the currently available data with the given name
	 * @param eventType the name for the event in ReMinds
	 */
	public void sendData(String eventType) {

		TransmittableEventObject ob = TransmittableObjectFactory.createEventObject(PreciseTimestamp.create(),
				eventType);

		ProbeData d = new ProbeData("Battle");
		d.addKeyValue("maxBullets", maxBullets);
		d.addKeyValue("robotCount", robotsCount);
		d.addKeyValue("turnDelta", totalTurns - lastTimeTurns);
		d.addKeyValue("turnsPerSecond", TPS);
		d.addKeyValue("battleNumber", roundNumber);
		d.addKeyValue("currentTime", battleTime);
		lastTimeTurns = totalTurns;
		maxBullets = 0;

		for (ProbeDataItem item : d.getItems()) {
			TransmittableEventDataObject data = TransmittableObjectFactory.createEventData(item.getData(),
					item.getName());
			ob.addData(data);
		}

		probePoint.sendData(ob);
	}

}
