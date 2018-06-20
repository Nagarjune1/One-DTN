/**
 * 
 */
package routing;

import java.lang.RuntimeException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import routing.util.EnergyModel;

import core.Connection;
import core.Coord;
import core.DTNHost;
import core.Message;
import core.Settings;
import routing.util.RoutingInfo;
import util.Tuple;

/**
 *
 */
public class EEDRRouter extends ActiveRouterEnergy {

	/** PEDRRouter router's setting namespace ({@value})*/ 
	public static final String EEDR_NS = "EEDRRouter";

	/** number of encounters of this node with other nodes */
	private Map<DTNHost, Integer> encounters;
	
	/**
	 * @param s
	 */
	public EEDRRouter(Settings s) {
		super(s);
		//Settings eedrSettings = new Settings(EEDR_NS);
		//setEnergyModel(eedrSettings);
		initEncounter();
	}

	/**
	 * @param r
	 */
	public EEDRRouter(EEDRRouter r) {
		super(r);
		initEncounter();
	}

	private void initEncounter() {
		this.encounters = new HashMap<DTNHost, Integer>();
	}

	/**
	 * @param othHost, the destination host.
	 * @return number of encounters of the current
	 * host with the othHost host.
	 */
	private int getEncounters(DTNHost othHost) {
		if (this.encounters.containsKey(othHost)) {
			return this.encounters.get(othHost);
		} else return 0;
	}
	
	/**
	 * @param neighbors
	 * @return sum of all the encounters of neighbor
	 * nodes with destination.
	 */
	private int getSumEncounters(List<DTNHost> neighbors, DTNHost dest) {
		int sumOfEncounters = 0;
		for (DTNHost h : neighbors) {
			sumOfEncounters += ((EEDRRouter)h.getRouter()).getEncounters(dest);
		}
		return sumOfEncounters;
	}
	
	/**
	 * @param host, host that will come in contact with
	 * the current host.
	 * Update the encounters Hashmap for both the encountered 
	 * and the current host.
	 */
	private void updateEncounters(DTNHost host) {
		if (this.encounters == null) {
			this.initEncounter();
		}
		if (this.encounters.containsKey(host)) {
			this.encounters.put(host, this.encounters.get(host)+1);
		} else {
			this.encounters.put(host, 1);
		}
	}
	
	/**
	 * @param host, host to which distance from this
	 * current host has to be found.
	 * @return distance of the host from the current
	 * host.
	 */
	private double getDistFor(DTNHost host) {
		Coord othHostLoc = host.getLocation();
		Coord thisHostLoc = this.getHost().getLocation();
		
		double x1 = othHostLoc.getX();
		double y1 = othHostLoc.getY();
		double x2 = thisHostLoc.getX();
		double y2 = thisHostLoc.getY();
		double xdiff = x1 - x2;
		double ydiff = y1 - y2;
		double dist = Math.pow(xdiff*xdiff + ydiff*ydiff, 0.5);
		
		return dist;
	}
	
	/**
	 * @param neighbors, dest
	 * @return sum of all the distance of neighbor nodes
	 * of current node w.r.t destination
	 */
	private double getSumDist(List<DTNHost> neighbors, DTNHost dest) {
		double sumOfDist = 0;
		for (DTNHost h : neighbors) {
			sumOfDist += ((EEDRRouter)h.getRouter()).getDistFor(dest);
		}
		return sumOfDist;
	}
	
	public double getParam(DTNHost dest) {
		List<DTNHost> neighbors = new ArrayList<DTNHost>();
		double encounterParam = 0, distParam;

		for (Connection con : getConnections()) {
			DTNHost othHost = con.getOtherNode(getHost());
			EEDRRouter othRouter = (EEDRRouter)othHost.getRouter();

			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring.
			}

			if (othRouter.getEnergy() < getEnergy()) {
				continue;
			}

			neighbors.add(othHost);
		}

		int sumOfEncounters = getSumEncounters(neighbors, dest);
		if (sumOfEncounters != 0) {
			encounterParam = getEncounters(dest) / sumOfEncounters;
		}
		distParam = getDistFor(dest) / getSumDist(neighbors, dest);
		return encounterParam / distParam;
	}

	/**
	 * @return The return value of {@link #tryMessagesForConnected(List)}
	 */
	private Tuple<Message, Connection> tryOtherMessages() {
		List <Tuple<Message, Connection>> messages =
				new ArrayList<Tuple<Message, Connection>>();
		Collection<Message> msgCollection = this.getMessageCollection();
		List<Connection> connections = new ArrayList<Connection>();
		List<DTNHost> neighbors = new ArrayList<DTNHost>();

		for (Connection con : getConnections()) {
			DTNHost othHost = con.getOtherNode(getHost());
			EEDRRouter othRouter = (EEDRRouter)othHost.getRouter();

			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring.
			}

			// System.out.println("Neightbor Energy: " + othRouter.getEnergy());
			// System.out.println("Energy THis node: " + getEnergy());
			if (othRouter.getEnergy() < getEnergy()) {
				continue;
			}

			connections.add(con);
			neighbors.add(othHost);
		}

		for (Connection con : connections) {
			EEDRRouter othRouter = (EEDRRouter)con.getOtherNode(getHost())
				.getRouter();
			for (Message m : msgCollection) {
				if (othRouter.hasMessage(m.getId())) {
					continue;
				}
				double encounterParam = 0, distParam, threshold;
				int sumOfEncounters = getSumEncounters(neighbors, m.getTo());
				if (sumOfEncounters != 0) {
					encounterParam = getEncounters(m.getTo())
						/ sumOfEncounters;
				}
				distParam = getDistFor(m.getTo())/getSumDist(neighbors, m.getTo());
				threshold = encounterParam/distParam;
				for (DTNHost h : neighbors) {
					threshold += ((EEDRRouter)h.getRouter()).getParam(m.getTo());
				}

				if (neighbors.size() > 0) {
					threshold /= neighbors.size();
				}

				if (othRouter.getParam(m.getTo()) >= threshold) {
					messages.add(new Tuple<Message, Connection>(m, con));
				}
			}
		}
		
		if (messages.size() == 0) {
			return null;
		}
		
		return this.tryMessagesForConnected(messages); // try to send all messages
	}
	
	@Override
	public void changedConnection(Connection con) {
		super.changedConnection(con);
		if(con.isUp()) {
			DTNHost othHost = con.getOtherNode(this.getHost());
			this.updateEncounters(othHost);
		}
	}
	
	@Override
	public void update() {
		super.update();
		if (!this.canStartTransfer() || this.isTransferring()) {
			return; // Nothing to transfer or is currently transferring.
		}
		
		// try messages that could be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return;
		}
		
		tryOtherMessages();
	}
	
	/* (non-Javadoc)
	 * @see routing.MessageRouter#replicate()
	 */
	@Override
	public MessageRouter replicate() {
		EEDRRouter r = new EEDRRouter(this);
		return r;
	}
	
	@Override
	public RoutingInfo getRoutingInfo() {
		RoutingInfo top = super.getRoutingInfo();
		RoutingInfo ri = (new RoutingInfo(encounters.size() +
				" host encounter(s)"));
		
		for (Map.Entry<DTNHost, Integer> e : encounters.entrySet()) {
			DTNHost host = e.getKey();
			int value = e.getValue();
			
			ri.addMoreInfo(new RoutingInfo(String.format("%s : %d",
					host, value)));
		}
		
		top.addMoreInfo(ri);
		return top;
	}

}
