package monitoring;

import java.util.ArrayList;
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
	}

	private int TPS;
	private String name;
	private RobotDataClass<Double> energy;
	private RobotDataClass<Double> gunHeat;
	private RobotDataClass<Double> velocity;
	private RobotDataClass<Double> xPosition;
	private RobotDataClass<Double> yPosition;

	private boolean alive = true;
	private boolean disabled = false;
	private IProbePoint probePoint;

	// TODO continue here
	public PeerProbePoint(String name, IProbePoint i) {
		probePoint = i;
		this.name = name;
		this.energy = new RobotDataClass<Double>();
		this.gunHeat = new RobotDataClass<Double>();
		this.velocity = new RobotDataClass<Double>();
		this.xPosition = new RobotDataClass<Double>();
		this.yPosition = new RobotDataClass<Double>();
	}

	public String getRoboName() {
		return name;
	}

	public boolean isDead() {
		return !alive;
	}

	public void setEnergy(double energy) {
		this.energy.data.add(energy);
		if (energy == 0 && !disabled) {
			disabled = true;
			sendData("Robot.disabled");
		}
	}

	public void setGunHeat(double gunHeat) {
		this.gunHeat.data.add(gunHeat);
	}

	public void setVelocity(double velocity) {
		this.velocity.data.add(velocity);
	}

	public void setxPosition(double xPosition) {
		this.xPosition.data.add(xPosition);
	}

	public void setyPosition(double yPosition) {
		this.yPosition.data.add(yPosition);
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
		d.addKeyValue("EnergyMin", energy.dataMin);
		d.addKeyValue("EnergyAverage", energy.dataAverage);
		d.addKeyValue("EnergyMax", energy.dataMax);
		d.addKeyValue("GunHeatMin", gunHeat.dataMin);
		d.addKeyValue("GunHeatAverage", gunHeat.dataAverage);
		d.addKeyValue("GunHeatMax", gunHeat.dataMax);
		d.addKeyValue("VelocityMin", velocity.dataMin);
		d.addKeyValue("VelocityAverage", velocity.dataAverage);
		d.addKeyValue("VelocityMax", velocity.dataMax);
		d.addKeyValue("Max-X-Change", xPosition.maxDelta);
		d.addKeyValue("Max-Y-Change", yPosition.maxDelta);
		d.addKeyValue("isAlive", Boolean.toString(alive));

		ProbeData b = new ProbeData("QueueData");
		b.newJsonItem("energy", new Gson().toJson(energy));
		b.newJsonItem("gunHeat", new Gson().toJson(gunHeat));
		b.newJsonItem("velocity", new Gson().toJson(velocity));
		b.newJsonItem("xPosition", new Gson().toJson(xPosition));
		b.newJsonItem("yPosition", new Gson().toJson(yPosition));
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
		while (energy.data.size() > TPS) {
			energy.data.poll();
		}
		if (energy.data.isEmpty())
			energy.data.add((double) 0);
		energy.dataMin = energy.data.stream().mapToDouble(v -> v).min().getAsDouble();
		energy.dataMax = energy.data.stream().mapToDouble(v -> v).max().getAsDouble();
		energy.dataAverage = energy.data.stream().mapToDouble(v -> v).average().getAsDouble();

		while (gunHeat.data.size() > TPS) {
			gunHeat.data.poll();
		}
		if (gunHeat.data.isEmpty())
			gunHeat.data.add((double) 0);
		gunHeat.dataMin = gunHeat.data.stream().mapToDouble(v -> v).min().getAsDouble();
		gunHeat.dataMax = gunHeat.data.stream().mapToDouble(v -> v).max().getAsDouble();
		gunHeat.dataAverage = gunHeat.data.stream().mapToDouble(v -> v).average().getAsDouble();

		while (velocity.data.size() > TPS) {
			velocity.data.poll();
		}
		if (velocity.data.isEmpty())
			velocity.data.add((double) 0);
		velocity.dataMin = velocity.data.stream().mapToDouble(v -> v).min().getAsDouble();
		velocity.dataMax = velocity.data.stream().mapToDouble(v -> v).max().getAsDouble();
		velocity.dataAverage = velocity.data.stream().mapToDouble(v -> v).average().getAsDouble();

		while (xPosition.data.size() > TPS) {
			xPosition.data.poll();
		}

		xPosition.maxDelta = getMaxDelta((List<Double>) xPosition.data);
//		while(xPosition.data.size()>0) {
//			curr=xPosition.data.remove();
//			if(Math.abs(curr-xPosition.data.peek())>xPosition.maxDelta)
//				xPosition.maxDelta=Math.abs(curr-xPosition.data.peek());
//		}

		while (yPosition.data.size() > TPS) {
			yPosition.data.poll();
		}

		yPosition.maxDelta = getMaxDelta((List<Double>) yPosition.data);
//		yPosition.maxDelta=(double) 0;
//		curr=0;
//		while(yPosition.data.size()>1) {
//			curr=yPosition.data.remove();
//			if(Math.abs(curr-yPosition.data.peek())>yPosition.maxDelta)
//				yPosition.maxDelta=Math.abs(curr-yPosition.data.peek());
//		}
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
}
