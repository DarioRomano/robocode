package monitoring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import com.google.gson.Gson;

import at.jku.mevss.eventdistributor.core.transmit.TransmittableEventDataObject;
import at.jku.mevss.eventdistributor.core.transmit.TransmittableEventObject;
import at.jku.mevss.eventdistributor.core.transmit.TransmittableObjectFactory;
import at.jku.mevss.eventpublisher.core.api.IProbePoint;
import at.jku.mevss.eventpublisher.core.api.ProbeData;
import at.jku.mevss.eventpublisher.core.api.ProbeData.ProbeDataItem;
import at.jku.mevss.util.utils.PreciseTimestamp;

public class PeerProbePoint extends Thread {

	private class RobotDataClass<T> {
		private Queue<T> data;
		private T dataMin;
		private T dataAverage;
		private T dataMax;
		private T maxDelta;

		public RobotDataClass() {
			data = new LinkedList<T>();
		}
		
		private void add(T d) {
			data.add(d);
		}
	}

	private int TPS;
	private String name;
	private HashMap<String,RobotDataClass<Double>> dataClasses;

	private boolean alive = true;
	private boolean disabled = false;
	private IProbePoint probePoint;

	// TODO continue here
	public PeerProbePoint(String name, IProbePoint i) {
		probePoint = i;
		this.name = name;
		dataClasses = new HashMap<String,RobotDataClass<Double>>();
		dataClasses.put("energy",new RobotDataClass<Double>());
		dataClasses.put("gunHeat",new RobotDataClass<Double>());
		dataClasses.put("velocity",new RobotDataClass<Double>());
		dataClasses.put("xPosition",new RobotDataClass<Double>());
		dataClasses.put("yPosition",new RobotDataClass<Double>());
		dataClasses.put("robotHeading",new RobotDataClass<Double>());
		dataClasses.put("gunHeading",new RobotDataClass<Double>());
		dataClasses.put("radarHeading",new RobotDataClass<Double>());
	}

	public String getRoboName() {
		return name;
	}

	public boolean isDead() {
		return !alive;
	}
	
	public void setRobotHeading(double rheading) {
		dataClasses.get("robotHeading").add(rheading);
	}
	
	public void setGunHeading(double gheading) {
		dataClasses.get("gunHeading").add(gheading);
	}

	public void setRadarheading(double radarHeading) {
		dataClasses.get("radarHeading").add(radarHeading);
	}

	public void setEnergy(double energy) {
		dataClasses.get("energy").add(energy);
		if (energy == 0 && !disabled) {
			disabled = true;
			sendData("Robot.disabled");
		}
	}

	public void setGunHeat(double gunHeat) {
		dataClasses.get("gunHeat").add(gunHeat);
	}

	public void setVelocity(double velocity) {
		dataClasses.get("velocity").add(velocity);
	}

	public void setxPosition(double xPosition) {
		dataClasses.get("xPosition").add(xPosition);
	}

	public void setyPosition(double yPosition) {
		dataClasses.get("yPosition").add(yPosition);
	}

	public void setTPS(int tps) {
		this.TPS = tps;
	}

	public void setHealth(boolean alive2) {
		if (!alive2 && alive) {
			this.alive = alive2;
			sendData("Robot.died");
		}
		this.alive = alive2;
	}

	public void sendData(String eventType) {
		calculatePeriodicData();

		TransmittableEventObject ob = TransmittableObjectFactory.createEventObject(PreciseTimestamp.create(),
				eventType);

		ProbeData d = new ProbeData("PeerData");
		d.addKeyValue("Name", name);
		for(String r:dataClasses.keySet()) {
			d.addKeyValue(r+"Min", dataClasses.get(r).dataMin);
			d.addKeyValue(r+"Average", dataClasses.get(r).dataAverage);
			d.addKeyValue(r+"Max", dataClasses.get(r).dataMax);
			d.addKeyValue(r+"MaxDelta", dataClasses.get(r).maxDelta);
		}
		d.addKeyValue("isAlive", Boolean.toString(alive));

		ProbeData b = new ProbeData();
		for(String r:dataClasses.keySet()) {
			b.newJsonItem(r, new Gson().toJson(dataClasses.get(r)));
		}

		for (ProbeDataItem item : d.getItems()) {
			TransmittableEventDataObject data = TransmittableObjectFactory.createEventData(item.getData(),
					item.getName());
			ob.addData(data);
		}
		for (ProbeDataItem item : b.getItems()) {
			TransmittableEventDataObject data = TransmittableObjectFactory.createEventData(item.getData(),
					item.getName());
			ob.addData(data);
		}

		probePoint.sendData(ob);
	}

	/**
	 * calculates the statistical data for the last turn period from the queues that
	 * contain all the data for the individual turns
	 */
	private void calculatePeriodicData() {
		for(String r:dataClasses.keySet()) {
			calculateStatisticValues(dataClasses.get(r));
		}
	}

	private double getMaxDelta(List l) {
		if (!l.isEmpty()) {
			double last = (double) l.get(0);
			double maxDelta = 0;
			for (int i = 0; i < l.size(); i++) {
				if (Math.abs((double) l.get(i) - last) > maxDelta) {
					maxDelta = Math.abs((double) l.get(i) - last);
				}
				last = (double) l.get(i);
			}
			return maxDelta;
		} else {
			return 0;
		}
	}
	
	private void calculateStatisticValues(RobotDataClass<Double> rdata) {
		while (rdata.data.size() > TPS) {
			rdata.data.poll();
		}
		if (rdata.data.isEmpty())
			rdata.add((double) 0);
		rdata.dataMin = rdata.data.stream().mapToDouble(v -> v).min().getAsDouble();
		rdata.dataMax = rdata.data.stream().mapToDouble(v -> v).max().getAsDouble();
		rdata.dataAverage = rdata.data.stream().mapToDouble(v -> v).average().getAsDouble();
		rdata.maxDelta = getMaxDelta((List<Double>) rdata.data);
	}

}
