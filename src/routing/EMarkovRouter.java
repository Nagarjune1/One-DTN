package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import core.Connection;
import core.Coord;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.SimClock;
import util.Tuple;
import routing.MarkovModel.*;
import routing.util.EnergyModel;

public class EMarkovRouter extends ActiveRouter

{

	/** delivery predictability aging constant */
	public static final double GAMMA = 0.98;

	public static final String SECONDS_IN_UNIT_S ="secondsInTimeUnit";
	public static final int FLUSHING_INTERVAL = 1000;
	public static int FLUSHING_COUNTER = 0;
	public static final String EMARKOV_NS = "EMarkovRouter";
	private int secondsInTimeUnit;

	public double markovtable[][];
	
	private double initialProbability[][]={{0.2,0.55,0.1,0.1,0.05}};
	
	private double weight[] = {0.25,0.4,0.15,0.15,0.05};
	private double weight1[] = {0,1,0,0,0};
	//private double weight[] = {0.25,0.4,0.15,0.15,0.05};

	/** last delivery predictability update (sim)time */
	private double lastAgeUpdate;
	
	/** delivery predictabilities */
	private Map<Integer, ProbDistribution> preds;
	
	int presentregion=0;

	// Weights for energy and probablity resp;

	double alpha = 0.6;
	double beta = 0.4;

	// define Thresholds as global variables;

	double energy_threshold = 100, prob_threshold = 0.7;


	/**s
	 * Constructor. Creates a new message router based on the settings in
	 * the given Settings object.
	 * @param s The settings object
	 */

	public EMarkovRouter(Settings s)
	{
		super(s);

		//TODO: read&use epidemic router specific settings (if any)
		Settings emarkovSettings = new Settings(EMARKOV_NS);
		secondsInTimeUnit = emarkovSettings.getInt(SECONDS_IN_UNIT_S);
		initmaps();

	}
	
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected EMarkovRouter(EMarkovRouter r) {
		super(r);
		//TODO: copy epidemic settings here (if any)
		this.secondsInTimeUnit = r.secondsInTimeUnit;
		initmaps();
	}

	public void initmaps()
	{
		this.preds = new HashMap<Integer,ProbDistribution>();
		for(int i=0;i<200;i++)
		{
			ProbDistribution prob = new ProbDistribution();
			preds.put(i, prob);
		}
	}


	/**
	 * multiply the transition matrix of carrier and neighbor node
	 * @param other
	 * @return
	 */

	public double [][]  productTransitionMatrix(EMarkovRouter other, Message m)
	{
	
			ProbDistribution othProb = other.preds.get(m.getTo().getAddress());

			/*if(othProb == null){
				othProb = new ProbDistribution();
			}*/

			double submatrix[][] = othProb.getProbability();
		
			markovtable = this.preds.get(m.getTo().getAddress()).getProbability();
			double prod[][]=new double[5][5];
			
			double sum=0;

			/*
		 	* i specify number of times we want to multiply 
			 * the two transition matrix 
		 	*/
		
			for(int j=0;j<5;j++)
			{
				for(int k=0;k<5;k++)
				{
					for(int l=0;l<5;l++)
					{
						sum = sum +  markovtable[j][l] * submatrix[l][k];
					}
					prod[j][k]=sum;
					sum=0.0;
				}
			}

			//updateDeliveryPredFor(m.getTo(),prod);
			//ProbDistribution newp = new ProbDistribution();
			//newp.setProbability(prod);
			//this.preds.put(m.getTo().getAddress(), newp);

			return prod;
		
	}

	/**
	 * calculate the probability of neighbor node to
	 *  drift towards destination region
	 */

	public double calculateprobability(DTNHost other, Message m)
	{
		EMarkovRouter oth = (EMarkovRouter) other.getRouter();
		double transitionMatrix[][] = productTransitionMatrix(oth ,m);
		double solutionMatrix[][] = new double[5][1];
		double sum = 0.0;

		for(int i=0;i<1;i++)
		{
			for(int j=0;j<5;j++)
			{
				for(int k=0;k<5;k++)
				{
					sum = sum + initialProbability[i][j] * transitionMatrix[k][j];
				}
				solutionMatrix[j][i] = sum;
				sum = 0;
			}
		}
		double prob = weight[0]*solutionMatrix[0][0]+
				weight[1]*solutionMatrix[1][0]+weight[2]*solutionMatrix[2][0]+
				weight[3]*solutionMatrix[3][0]+weight[4]*solutionMatrix[4][0];
		return prob;
	}

	/**
	 * Returns the current prediction (P) value for a host or 0 if entry for
	 * the host doesn't exist.
	 * @param host The host to look the P for
	 * @return the current P value
	 */

	public double getPredFor(DTNHost host, Message m)
	{
		//ageDeliveryPreds(); // make sure preds are updated before getting

		if (preds.containsKey(host.getAddress()))
		{
			return calculateprobability(host, m);
		}
		else 
		{
			return 0;
		}
	}
	
	public double getSelfPred(Message m)
	{
		double transitionMatrix [][] = preds.get(m.getTo().getAddress()).getProbability();
		double solutionMatrix[][] = new double[5][1];
		double sum = 0.0;
		for(int i=0;i<1;i++)
		{
			for(int j=0;j<5;j++)
			{
				for(int k=0;k<5;k++)
				{
					sum = sum + initialProbability[i][j] * transitionMatrix[k][j];
				}
				solutionMatrix[j][i] = sum;
				sum = 0;
			}
		}
		double prob = weight1[0]*solutionMatrix[0][0]+
				weight1[1]*solutionMatrix[1][0]+weight1[2]*solutionMatrix[2][0]+
				weight1[3]*solutionMatrix[3][0]+weight1[4]*solutionMatrix[4][0];
		return prob;
	}
	
	/**
	 * getting angle between destination and neighbor
	 * @param point1X
	 * @param point1Y
	 * @param point2X
	 * @param point2Y
	 * @param fixedX
	 * @param fixedY
	 * @return
	 */
	public double angleBetweenTwoPointsWithFixedPoint(double point1X, double point1Y, double point2X, double point2Y, double fixedX, double fixedY) 
	{
	    double angle1 = Math.atan2(point1Y - fixedY, point1X - fixedX);
	    double angle2 = Math.atan2(point2Y - fixedY, point2X - fixedX);

	    return angle1 - angle2; 
	}
	
	/**
	 * updating the probability of Transition Matrix based on the position 
	 * of neighbor node
	 * @param otherHost
	 */
	public void updateTransitionMatrix(DTNHost otherHost, Message m)
	{
		/*
		 * first find region of neihbour node
		 */
		Coord dest = m.getTo().getLocation();
		Coord neighbor = otherHost.getLocation();
		Coord carrier = this.getHost().getLocation();
		int region;
	
		double angle = Math.toDegrees(angleBetweenTwoPointsWithFixedPoint(dest.getX(),dest.getY(),
			       neighbor.getX(),neighbor.getY(),carrier.getX(),carrier.getY()));
		if (angle < 0.0) 
		{
		    angle += 360.0;
		}
		
		if(angle<=45 || angle>=315)
		{
			
			if(angle>=315)
			{
				angle = 360 - angle;
			}
			
			region =1;
		}else if(angle>45 && angle <=135)
		{
			angle = angle -90;
			if(angle < 0)
			{
				angle = -1 * angle ;
			}
			region =2;
		}else if( angle>135 && angle <=225)
		{
			angle = angle -180;
			if(angle<0)
			{
				angle = -1 * angle;
			}
			region =4;
		} else 
		{
			angle = angle -270;
			if(angle<0)
			{
				angle = -1 * angle;
			}
			region =3;
		}
		presentregion = region;
		double distance = Math.sqrt(Math.pow(carrier.getX()-neighbor.getX(), 2)+
				Math.pow(carrier.getY()-neighbor.getY(), 2));
		double transmitrng = 20;
		if(distance>20)
		{
			transmitrng = 1500;
		} 
		dynamicProbability(transmitrng,distance,angle, m, region);
			
	}
	
	
	/**
	 * dynamically calculates the probability of all regions
	 * @param rCarrier
	 * @param rneighbor
	 * @param angle
	 * @param m
	 * @param region
	 */

	public void dynamicProbability(double rCarrier, double rneighbor,double angle, Message m, int region)
	{
		
			double probleft, probright, probsame, probbackS0, probback;
		
			double area1, area2, area3, area4, totalarea;
		
			area1 = ((45-angle)*Math.PI * rCarrier * rCarrier)/360;
	
			area2 = (2*angle*Math.PI * rneighbor * rneighbor)/360;
		
			area3 = (2*angle*Math.PI * rCarrier * rCarrier)/360 - area2;
		
			area4 =area1;
		
			totalarea = (Math.PI * rCarrier * rCarrier)/4;
			
			probsame = (area2)*((90 - 2 * angle)/90)/(totalarea);
		
			probleft = (totalarea/2-area1)/totalarea;
			
			probright = area4/totalarea;
		
			double probremain = 1 - probright - probleft - probsame;
		
			probback = probremain/4;
		
			probbackS0 = 3*probremain/4;
			
			ProbDistribution destprob= this.preds.get(m.getTo().getAddress());
		
			double newprob[][] = destprob.getProbability();
			//double probsum1 = probback+probright+probleft+probsame+probbackS0;
		
			newprob[region][0] = (newprob[region][0] + probbackS0)/2;
			newprob[region][1] = (newprob[region][1] + probsame)/2;
			newprob[region][2] = (newprob[region][2] + probleft)/2;
			newprob[region][3] = (newprob[region][3] + probright)/2;
			newprob[region][4] = (newprob[region][4] + probback)/2;
			/*double probsum = newprob[region][0]+newprob[region][1]+newprob[region][2]+
				newprob[region][3]+newprob[region][4];*/
			//System.out.println(probsum1+" "+probsum);
			destprob.setProbability(newprob);
			this.preds.put(m.getTo().getAddress(), destprob);
		
	}
	
	@Override
	public void update()
	{
		super.update();
		if (isTransferring() || !canStartTransfer())
		{
			return; // transferring, don't try other connections yet
		}
		
		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}
		FLUSHING_COUNTER++;
		if(FLUSHING_COUNTER==FLUSHING_INTERVAL){
			cleanProbTable();
		}

		//Dynamic threshold calculation
		//getEnergyThreshold();
		//getProbThreshold();


		// then try any/all message to any/all connection
		tryOtherMessages();		
	}
	
	
	public void cleanProbTable()
	{
		for(int i=0;i<200;i++)
		{
			ProbDistribution prob = new ProbDistribution();
			preds.put(i, prob);
		}
		FLUSHING_COUNTER = 0;
	}


// calculate probability threshold
	public double getProbThreshold()
	{
		double sumProb = 0 , count = 0 , prob_val =0;

		Collection<Message> msgCollection = getMessageCollection();
		
		for (Connection con : getConnections())
		{
			DTNHost other = con.getOtherNode(getHost());
			EMarkovRouter othRouter = (EMarkovRouter)other.getRouter();
		
			for (Message m : msgCollection)
			{
				if (othRouter.hasMessage(m.getId())) 
				{
					continue; // skip messages that the other one has
				}
				
				updateTransitionMatrix(other,m);
				productTransitionMatrix(othRouter, m);

				
				//System.out.println(getPredFor(other,m) + "\n");

				prob_val = getPredFor(other,m);	
				sumProb = sumProb + prob_val;
				count =  count + 1;

			}

			
				
		}
		
		if(count != 0)
			prob_threshold = sumProb/count;
		else
			prob_threshold = 0.7;


		//System.out.println("\n Threshold P is" + prob_threshold + " \n");
		return prob_threshold;

	}

//calculate energy threshold

	public double getEnergyThreshold()
	{

		double sumEnergy = 0 , count = 0;
		for (Connection con : getConnections())
		{
			DTNHost other = con.getOtherNode(getHost());
			EMarkovRouter othRouter = (EMarkovRouter)other.getRouter();
		

			Double energy_val = (Double)other.getComBus().getProperty(routing.util.EnergyModel.ENERGY_VALUE_ID);
			//System.out.println("Energy val is " + energy_val + "\n");
			sumEnergy = sumEnergy + energy_val;
			count =  count + 1;
				
		}

		if(count != 0)
			energy_threshold = sumEnergy/count;
		else
			energy_threshold = 100;

		//System.out.println("\n Threshold E " + energy_threshold + " \n");
		return energy_threshold;
	}


/**
	 * Tries to send all other messages to all connected hosts ordered by
	 * their delivery probability
	 * @return The return value of {@link #tryMessagesForConnected(List)}
	 */



	private Tuple<Message, Connection> tryOtherMessages() 
	{
		
		List<Tuple<Message, Connection>> messages = new ArrayList<Tuple<Message, Connection>>();
		
		List<Message> forwarded = new ArrayList<Message>();
	
		Collection<Message> msgCollection = getMessageCollection();


		double getSumEnergy = 0;
		double count_nodes = 0;
		double count_prob = 0;
		double getSumProb = 0;
		double prob_val;
		
		/**
		 * save the markov model for all the neighbours in current context
		 */
		for (Connection con : getConnections())
		{
			DTNHost other = con.getOtherNode(getHost());
		}
		
		/* for all connected hosts collect all messages that have a higher
		   probability of delivery by the other host */

		for (Connection con : getConnections())
		{
			DTNHost other = con.getOtherNode(getHost());
			EMarkovRouter othRouter = (EMarkovRouter)other.getRouter();
			
			

			if (othRouter.isTransferring()) 
			{
				continue; // skip hosts that are transferring
			}

			for (Message m : msgCollection)
			{
				if (othRouter.hasMessage(m.getId())) 
				{
					continue; // skip messages that the other one has
				}
				
				updateTransitionMatrix(other,m);
				productTransitionMatrix(othRouter, m);

				
		
				//System.out.println(getSelfPred(m)+" "+getPredFor(other,m));
			
			}
				
		}


		energy_threshold = getEnergyThreshold();
		prob_threshold = getProbThreshold();

		//Obtain EP parameters

		double ep_param = 1;
		count_nodes = 0;
		double ep_total = 0;
		double ep_val = 0;
			

		for (Connection con : getConnections())
		{
			DTNHost other = con.getOtherNode(getHost());

			prob_val = 0;
			Double energy_val = (Double)other.getComBus().getProperty(routing.util.EnergyModel.ENERGY_VALUE_ID);
	
			for (Message m : msgCollection)
			{
				prob_val = getPredFor(other,m);
			
				ep_val = ( alpha * (energy_val / energy_threshold)) + (beta * (prob_val / prob_threshold));

				//System.out.println("EP_val is "+ ep_val +" \n");

				ep_total = ep_total + ep_val;

				count_nodes =  count_nodes + 1;

			}
				
		}
		
		if(count_nodes != 0)	
			ep_param = ep_total / count_nodes;
		
		//System.out.println("EP_total is "+ ep_total  +" \n");
		//System.out.println("nodes is "+ count_nodes +" \n");		

		//System.out.println("EP_param is "+ ep_param  +" \n");

			for (Connection con : getConnections())
			{
				DTNHost other = con.getOtherNode(getHost());
				EMarkovRouter othRouter = (EMarkovRouter)other.getRouter();

				Double source_energy = (Double)getHost().getComBus().getProperty(routing.util.EnergyModel.ENERGY_VALUE_ID);
				Double dest_energy = (Double)other.getComBus().getProperty(routing.util.EnergyModel.ENERGY_VALUE_ID);

				if (othRouter.isTransferring()) 
				{
				continue; // skip hosts that are transferring
				}

				Double energy_val = (Double)other.getComBus().getProperty(routing.util.EnergyModel.ENERGY_VALUE_ID);

				for (Message m : msgCollection)
				{
					if (othRouter.hasMessage(m.getId())) 
					{
						continue; // skip messages that the other one has
					}
					
					if (source_energy!=null && dest_energy!=null) 
					{
						if(dest_energy<source_energy)
						{
							continue;				
						}
					}
					
					prob_val = getPredFor(other,m);

					ep_val = (alpha * (energy_val / energy_threshold )) + (beta * (prob_val / prob_threshold ));

					//System.out.println("Final routing decision \n");

					//System.out.println("EP_val is "+ ep_val +" \n");
					//System.out.println("EP_param is "+ ep_param +" \n");
				
					if (ep_val >= ep_param) 
					{
						// the other node has higher probability of delivery
					
						messages.add(new Tuple<Message, Connection>(m,con));


						/*if(presentregion == 1)
						{
							m.incRegion1();
						} else if(presentregion == 2)
						{
							m.incRegion2();
						}else if(presentregion == 3)
						{
							m.incRegion3();
						}else if(presentregion == 4)
						{
							m.incRegion4();
						}
					
						if(m.getRegion1()==6)
						{
							//System.out.println("region 1");
							forwarded.add(m);
						} else if(m.getRegion2()==8)
						{
							//System.out.println("region 2");
							forwarded.add(m);
						}else if(m.getRegion3()==8)
						{
							//System.out.println("region 3");
							forwarded.add(m);
						
						}else if(m.getRegion4()==10)
						{
							//System.out.println("region 4");
							forwarded.add(m);
						}*/
				}
			}
		}			
		
		if (messages.size() == 0) 
		{
			return null;
		}

		//deleteforwardedMessage(forwarded);
		return tryMessagesForConnected(messages);	// try to send messages
	
	}
	
	public void deleteforwardedMessage(List<Message> forwarded)
	{
		for(Message m:forwarded)
		{
			if(this.hasMessage(m.getId()))
			{
				deleteMessage(m.getId(),false);
			}
		}
	}

	@Override
	public EMarkovRouter replicate()
	{
		return new EMarkovRouter(this);
	}
}
