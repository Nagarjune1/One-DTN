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
public class EnergyDistanceRouter extends ActiveRouterEnergy {

	/** EnergyDistanceRouter router's setting namespace ({@value})*/ 
	public static final String ENERGY_DIST_R_NS = "EnergyDistanceRouter";

	/** number of encounters of this node with other nodes */
	//private Map<DTNHost, Integer> encounters;
	
	/**
	 * @param s
	 */
	public EnergyDistanceRouter(Settings s) {
		super(s);
		//Settings eedrSettings = new Settings(EEDR_NS);
		//setEnergyModel(eedrSettings);
	}

	/**
	 * @param r
	 */
	public EnergyDistanceRouter(EnergyDistanceRouter r) {
		super(r);
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
			sumOfDist += ((EnergyDistanceRouter)h.getRouter()).getDistFor(dest);
		}
		return sumOfDist;
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
			EnergyDistanceRouter othRouter = (EnergyDistanceRouter)othHost.getRouter();

			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring.
			}

			if (othRouter.getEnergy() < getEnergy()) {
				continue;
			}

			connections.add(con);
			neighbors.add(othHost);
		}

		for (Connection con : connections) {
			EnergyDistanceRouter othRouter = (EnergyDistanceRouter)con.getOtherNode(getHost())
				.getRouter();
			for (Message m : msgCollection) {
				if (othRouter.hasMessage(m.getId())) {
					continue;
				}

				// double distThresh;

				// if (neighbors.size() > 0) {
				// 	distThresh = getSumDist(neighbors, m.getTo())/neighbors.size();
				// }

				if (othRouter.getDistFor(m.getTo()) < getDistFor(m.getTo())) {
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
		EnergyDistanceRouter r = new EnergyDistanceRouter(this);
		return r;
	}
	
	@Override
	public RoutingInfo getRoutingInfo() {
		RoutingInfo top = super.getRoutingInfo();
		return top;
	}

}
