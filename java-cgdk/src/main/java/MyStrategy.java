import model.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MyStrategy implements Strategy {
	 //private static final Double WAYPOINT_RADIUS = 10.0;
	 private static final Double COLLISION_RADIUS = 4.0;

    private static final double LOW_HP_FACTOR = 0.25D;
    
    // Wizzard direction.
    private enum WD
    {
    	STD, FWD, BWD, FULL_BWD, BONUS;
    };
   
    // Bonuses spots.
    public enum Spot {TOP, BOT};

    /**
     * Ключевые точки для каждой линии, позволяющие упростить управление перемещением волшебника.
     * <p>
     * Если всё хорошо, двигаемся к следующей точке и атакуем противников.
     * Если осталось мало жизненной энергии, отступаем к предыдущей точке.
     */
    
    private final Map<LaneType, Point2D[]> waypointsByLane = new EnumMap<>(LaneType.class);
    private final Map<LaneType, Point2D>   runeZone = new EnumMap<>(LaneType.class);
    private final List<Point2D> runes = new ArrayList<>();
    private Boolean moving = false;
    private WD myTactic;
    private Boolean iAmStuck = false;
    private Integer stopTick = 0;
    
    private Bonuses bonuses;
    
    
    private Random random = new Random();

    private LaneType lane = null;
    
    private Double strafeSpeed = 0.0;
    
    private Wizard self;
    private World world;
    private Game game;
    private Move move;
    
    private Double LANE_WIDTH;
    
    @Override
    public void move(Wizard self, World world, Game game, Move move)
    {
    	initializeTick(self, world, game, move);
    	
    	if(!self.isMaster() && world.getTickIndex() < 40)
    		return;
    	initializeStrategy(self, game);
    	
        ;
        
    	LivingUnit enemy = spotTarget();
    	
    	if(bonuses.run())
    	{
    		
    	}
    	else if(enemy != null)
    	{
    		fight(enemy);
    	}
    	else
    	{
    		// test if we are first in lane;
    		Point2D vanguard = findVanguard();
    		
    		/*
    		if(canTakeBonus())
    		{
    			takeBonus();
    		}
    		*/
    		
    		if(!amIFirst(vanguard))
    		{
    			myTactic = WD.FWD;
    			walk();
    		}
    	}
    	
    	stuckCounter();
    	
    }
    
    private double getSpeed()
    {
    	double speed = game.getWizardForwardSpeed();
    	Status[] statuses = self.getStatuses();
    	for(Status st : statuses)
    		if(st.getType() == StatusType.HASTENED)
    			speed = 1.5 * speed;
    	return speed;
    }
    
    private double getBackwardSpeed()
    {
    	double speed = game.getWizardBackwardSpeed();
    	Status[] statuses = self.getStatuses();
    	for(Status st : statuses)
    		if(st.getType() == StatusType.HASTENED)
    			speed = 1.5 * speed;
    	return speed;
    }
    
    private void walk()
    {
    	Point2D walkTo = selectWaypont();
    	move.setTurn(self.getAngleTo(walkTo.getX(), walkTo.getY()));
    	if(self.getDistanceTo(walkTo.getX(), walkTo.getY()) < self.getRadius() * 3)
    		return;
    	if(self.getAngleTo(walkTo.getX(), walkTo.getY()) < Math.PI / 6)
    	{
    		move.setSpeed(getSpeed());
    		moving = true;
    	}
    	collision();
    }
    
    private void collision()
    {
    	Tree tree = forwardTrees();
    	if(tree != null)
    	{
    		move.setCastAngle(self.getAngleTo(tree));
    		move.setAction(ActionType.MAGIC_MISSILE);
    	}
    	
    	List<Tree> collTrees = forwardTrees2Close();
    	if(!collTrees.isEmpty() && collTrees.size() > 1)
    	{
    		if(collTrees.size() == 1)
    		{
    			move.setAction(ActionType.MAGIC_MISSILE);
    			if(self.getAngleTo((Tree)collTrees.get(0)) > 0)
    				move.setStrafeSpeed(-game.getWizardStrafeSpeed());
    			else if(self.getAngleTo((Tree)collTrees.get(0)) < 0)
    				move.setStrafeSpeed(game.getWizardStrafeSpeed());
    		}
    		else {
    			Tree cur = null;
    			for(Tree t : collTrees) {
    				if(cur == null)
    					cur = t;
    				else
    					if(Math.abs(self.getAngleTo(t)) < Math.abs(self.getAngleTo(cur)))
    						cur = t;
    			}
    			
    			move.setTurn(self.getAngleTo(cur));
    			if(Math.abs(self.getAngleTo(cur)) < game.getStaffSector() / 2)
    				move.setAction(ActionType.MAGIC_MISSILE);
    		}
    		return;
    	}
    	List<Unit> u = collisionDetector();
    	if(u != null && !u.isEmpty())
    	{
    		if(u.size() == 1)
    		{
	    		Double angle = self.getAngleTo(u.get(0));
	    		if(angle != 0){
	    			strafeSpeed = -10 * Math.signum(angle);
	    		}
	    		else {
	    			if(strafeSpeed == 0)
	    				strafeSpeed = random.nextBoolean() ? 10.0 : -10.0;
	    		}
	    		move.setSpeed(0);
	    		move.setStrafeSpeed(strafeSpeed);
    		}
    		else
    		{
    			Double res = 0.0;
    			for(Unit unit : u)
    			{
    				res += self.getAngleTo(unit);
    			}
    			res = res / (u.size() + 1);
    			if(res > 0)
    				res -= Math.PI;
    			else
    				res += Math.PI;
    			Double vel = getBackwardSpeed();
    			move.setSpeed(vel * Math.cos(res));
    			move.setStrafeSpeed(vel * Math.sin(res));
    		}
    		return;
    	}
    	
    }
    
    private List<Unit> getAllUnits()
    {
    	List<Unit> list =  new ArrayList<>();
    	for(Building b : world.getBuildings())
    		list.add(b);
    	for(Wizard w : world.getWizards())
    		list.add(w);
    	for(Minion m : world.getMinions())
    		list.add(m);
    	for(Tree t : world.getTrees())
    		list.add(t);
    	return list;
    }
    
    private Point2D selectWaypont()
    {
    	/*
    	if(true)
    		return new Point2D(1200,1200);
    		*/
    	switch(lane){
    	case MIDDLE:
    		//System.out.println("mid");
    		if(self.getX() - LANE_WIDTH / 2 < self.getY())
    			return waypointsByLane.get(LaneType.MIDDLE)[0];
    		else 
    			return waypointsByLane.get(LaneType.MIDDLE)[1];
    	case TOP:
    		//System.out.print("top ");
    		if(self.getY() > LANE_WIDTH)
    		{	// До поворота
    			//System.out.println("before");
    			return waypointsByLane.get(LaneType.TOP)[1];
    		}
    		else
    		{
    			//System.out.println("after");
    			return waypointsByLane.get(LaneType.TOP)[2];
    		}
    	case BOTTOM:
    		//System.out.print("bot ");
    		if(self.getX() < world.getWidth() - LANE_WIDTH)
    		{	// До поворота
    			//System.out.println("before");
    			return waypointsByLane.get(LaneType.BOTTOM)[1];
    		}
    		else
    		{
    			//System.out.println("after");
    			return waypointsByLane.get(LaneType.BOTTOM)[2];
    		}
    	default:
    		return new Point2D(world.getWidth() - LANE_WIDTH/2, LANE_WIDTH/2);
    	}
    	
    	/*
    	if(currentWaypoint != waypoints.length - 1 && self.getDistanceTo(waypoints[currentWaypoint].getX(), waypoints[currentWaypoint].getY()) < WAYPOINT_RADIUS)
    	{
    		currentWaypoint ++;
    	}
    	return waypoints[currentWaypoint];
    	*/
    }
    
    private void chooseLane()
    {
    	Message[] ms = self.getMessages();
    	LaneType l1 = null;
    	for(Message m : ms)
    	{
    		l1 = m.getLane();
    	}
    	if(l1 != null)
    	{
    		lane = l1;
    		return;
    	}
    	switch((int) self.getId())
    	{
    	case 1:
    	case 2:
    	case 6: 
		case 7:
    		lane = LaneType.TOP;
    		break;
		case 3:
		case 8:
			lane = LaneType .MIDDLE;
			break;
		default :
			lane = LaneType.BOTTOM;
    	}
    }
    
    
    private List<Unit> collisionDetector()
    {
    	List<Unit> result = new ArrayList<Unit>();
    	if(moving != true || !iAmStuck)
    		return null;
    	
    	
    	for(Building b : world.getBuildings())
    	{	
    		if(myTactic == WD.FWD)
    		{
	    		if(Math.abs(self.getAngleTo(b)) < Math.PI / 2 &&
	    				self.getDistanceTo(b) < self.getRadius() + b.getRadius() + COLLISION_RADIUS)
	    		{
	    			result.add(b);
	    		}
    		}
    		else
    		{
    			if(Math.abs(self.getAngleTo(b)) > Math.PI / 2 &&
	    				self.getDistanceTo(b) < self.getRadius() + b.getRadius() + COLLISION_RADIUS)
	    		{
    				result.add(b);
	    		}
    		}
    	}
    	
    	for(Tree t : world.getTrees())
    	{	
    		if(myTactic == WD.FWD)
    		{
	    		if(Math.abs(self.getAngleTo(t)) < Math.PI / 2 &&
	    				self.getDistanceTo(t) < self.getRadius() + t.getRadius() + COLLISION_RADIUS)
	    		{
	    			result.add(t);
	    		}
    		}
    		else
    		{
    			if(Math.abs(self.getAngleTo(t)) > Math.PI / 2 &&
	    				self.getDistanceTo(t) < self.getRadius() + t.getRadius() + COLLISION_RADIUS)
	    		{
    				result.add(t);
	    		}
    		}
    	}
    	
    	for(Minion u : world.getMinions())
    	{	
    		if(myTactic == WD.FWD)
    		{
	    		if(Math.abs(self.getAngleTo(u)) < Math.PI / 2 &&
	    				self.getDistanceTo(u) < self.getRadius() + u.getRadius() + COLLISION_RADIUS)
	    		{
	    			result.add(u);
	    		}
    		}
    		else
    		{
    			if(Math.abs(self.getAngleTo(u)) > Math.PI / 2 &&
	    				self.getDistanceTo(u) < self.getRadius() + u.getRadius() + COLLISION_RADIUS)
	    		{
    				result.add(u);
	    		}
    		}
    	}
    	
    	for(Wizard w : world.getWizards())
    	{	
    		if(w.isMe())
    			continue;
    		if(myTactic == WD.FWD)
    		{
	    		if(((Math.abs(self.getAngleTo(w)) < Math.PI / 2) || iAmStuck ) &&
	    				self.getDistanceTo(w) < self.getRadius() + w.getRadius() + COLLISION_RADIUS)
	    		{
	    			result.add(w);
	    		}
    		}
    		else
    		{
    			if(((Math.abs(self.getAngleTo(w)) > Math.PI / 2) || iAmStuck) &&
	    				self.getDistanceTo(w) < self.getRadius() + w.getRadius() + COLLISION_RADIUS)
	    		{
    				result.add(w);
	    		}
    		}
    	}
    	
    	return result;
    }
    
    private void stuckCounter()
    {
     	iAmStuck = false;
    	if(myTactic == WD.FWD)
    	{
    		if(Math.abs(self.getSpeedX()) + Math.abs(self.getSpeedY()) < 1.0)
    		{
    			if(stopTick  + 3/*world.getTickCount() / 250*/ < world.getTickIndex())
    			{
    				iAmStuck = true;
    			}
    		}
    		else{
    			stopTick = world.getTickIndex();
    		}
    	}
    	else
    		stopTick = world.getTickIndex();
    }
    
    private List<Tree> treeCollisionDetector()
    {
    	List<Tree> list = new ArrayList<>(); 
    	for(Tree t : world.getTrees())
    	{
    		
    		if(Math.abs(self.getAngleTo(t)) < Math.PI / 2 &&
    				self.getDistanceTo(t) < self.getRadius() + t.getRadius() + COLLISION_RADIUS)
    		{
    			list.add(t);
    		}
    		
    	}
    	
    	return list;
    }
    
    private Tree forwardTrees()
    {
    	Tree res = null; 
    	for(Tree t : world.getTrees())
    	{
    		//if(self.getDistanceTo(t) < game.getWizardCastRange()){
    		if(Math.abs(self.getAngleTo(t)) < Math.PI / 2 &&
    				self.getDistanceTo(t) < self.getRadius() + t.getRadius() + COLLISION_RADIUS)
			{
				if(res == null)
					res = t;
				else if(self.getDistanceTo(t) < self.getDistanceTo(res))
					res = t;
			}
    		
    	}
    	
    	return res;
    }
    
    private List<Tree> forwardTrees2Close()
    {
    	List<Tree> res = new ArrayList<Tree>(); 
    	for(Tree t : world.getTrees())
    	{
    		if(self.getDistanceTo(t) < self.getRadius() + t.getRadius() + self.getRadius() / 4){
    			{
    				res.add(t);
    			}
    		}
    	}
    	
    	return res;
    }
    
    private Unit forwardEnemy()
    {
    	Unit u = null;
    	for(Wizard w : world.getWizards())
    	{
    		if(self.getDistanceTo(w) < game.getWizardCastRange() && Math.abs(self.getAngleTo(w)) < game.getStaffSector() / 2)
    			if(u == null)
    				u = w;
    			else if(self.getDistanceTo(u) > self.getDistanceTo(w))
    				u = w;
    	}
    	
    	return u;
    }
    
    
    private void fight(LivingUnit target)
    {
    	move.setTurn(self.getAngleTo(target));
    	
    	if(Math.abs(self.getAngleTo(target)) < game.getWizardCastRange())
    	{
    		move.setCastAngle(self.getAngleTo(target));
    		if(Math.abs(self.getAngleTo(target)) < game.getStaffSector() / 2.0)
    			move.setAction(ActionType.MAGIC_MISSILE);
    	}
    	
    	if(self.getDistanceTo(target) < self.getCastRange() * 0.8)
    	{
    		if(self.getLife() < LOW_HP_FACTOR * self.getMaxLife())
    		{
    			myTactic = WD.FULL_BWD;
    		}
    		else if(self.getDistanceTo(target) < self.getCastRange() * 0.4)
    		{
    			myTactic = WD.FULL_BWD;
    		}
    		else
    		{
    			myTactic = WD.BWD;
    		}
    		retreat();
    	}
    	else
    	{
    		strafe();
    		myTactic = WD.STD;
    	}
    }
    
    private void strafe()
    {
    	Double diff = 0.0;
    	Double angle = 0.0;
    	Double speed = getBackwardSpeed();
    	switch(lane)
    	{
    	case MIDDLE:
    		if(self.getX() + self.getY() < world.getWidth() - LANE_WIDTH)
    		{
    			move.setStrafeSpeed(getBackwardSpeed());
    		}
    		else if(self.getX() + self.getY() > world.getWidth() + LANE_WIDTH)
    		{
    			move.setStrafeSpeed(-getBackwardSpeed());
    		}
    		break;
    	case TOP:
    		if(self.getX() > LANE_WIDTH && self.getY() > LANE_WIDTH)
    		{	// before turn
    			move.setStrafeSpeed(-getBackwardSpeed());
    		}
    		break;
    	case BOTTOM:
    		if(self.getX() < world.getWidth() - LANE_WIDTH && self.getY() < world.getHeight() - LANE_WIDTH)
    		{	// before turnn
    			move.setStrafeSpeed(getBackwardSpeed());
    		}
    	}
    	
		//move.setSpeed(-);
    }
    
    private void retreat()
    {
    	Double diff = 0.0;
    	Double angle = 0.0;
    	Double speed = getBackwardSpeed();
    	switch(lane)
    	{
    	case MIDDLE:
    		diff = (self.getY() - (world.getWidth() - self.getX())) / 1.4;
    		angle = - Math.PI/4 - self.getAngle();
    		retreatMove(diff, speed, angle);
    		break;
    	case TOP:
    		if(self.getX() < LANE_WIDTH)
    		{	// before turn
    			diff = self.getX() - LANE_WIDTH/2;
    			angle = - Math.PI/2 - self.getAngle();
    			retreatMove(diff, speed, angle);
    		}
    		else
    		{	// after turn
    			diff = self.getY() - LANE_WIDTH/2;
    			retreatMove(diff, speed, self.getAngle());
    		}
    		break;
    	case BOTTOM:
    		if(self.getY() > world.getHeight() - LANE_WIDTH)
    		{	// before turnn
    			diff = self.getY() - (world.getHeight() - LANE_WIDTH/2);
    			retreatMove(diff, speed, self.getAngle());
    		}
    		else
    		{	// after turn
    			diff = self.getX() - (world.getWidth() - LANE_WIDTH/2);
    			angle = - Math.PI/2 - self.getAngle();
    			retreatMove(diff, speed, angle);
    		}
    	}
    	
		//move.setSpeed(-);
    }
    
    private void retreatMove(Double diff, Double speed, Double angle)
    {
    	Double revert = (Math.abs(angle) <= Math.PI/2) ? 1.0 : -1.0;
    	if(diff > 0)
		{
    		if(myTactic == WD.BWD)
    		{
    			move.setSpeed(-0.5 * speed / 1.4);
    			move.setStrafeSpeed(-0.5 * speed * revert / 1.4);
    		}
    		else
    		{
    			if(diff < self.getRadius() * 2)
    			{
    				move.setSpeed(-speed);
    			}
    			else
    			{
    				move.setSpeed(-speed / 1.4);
    				move.setStrafeSpeed(speed * revert / 1.4);
    			}
    		}
		}
		else
		{
			if(myTactic == WD.BWD)
			{
				move.setSpeed(-0.5 * speed / 1.4);
				move.setStrafeSpeed(0.5 * speed * revert / 1.4);
			}
			else
			{
				if(diff < self.getRadius() * 2)
				{
					move.setSpeed(-speed);
				}
				else
				{
					move.setSpeed(-speed / 1.4);
					move.setStrafeSpeed(speed * revert / 1.4);
				}
			}
		}
    }
   
    
    /**
     * Сохраняем все входные данные в полях класса для упрощения доступа к ним.
     */
    private void initializeTick(Wizard self, World world, Game game, Move move) {
        this.self = self;
        this.world = world;
        this.game = game;
        this.move = move;
    }
    
    private void initializeStrategy(Wizard self, Game game)
    {
    	if(lane != null)
    		return;
    	
    	myTactic = WD.STD;
    	
    	chooseLane();
    	
    	Building[] buildings = world.getBuildings();
    	
    	Point2D[] waypointsM;
    	
    	waypointsM = new Point2D[2];
    	for(Building building : buildings)
    	{
    		if(building.getType() == BuildingType.FACTION_BASE)
    		{
    			if(building.getFaction() == self.getFaction())
    			{
    				LANE_WIDTH = building.getX();
    			}
    			
    		}
    	}
    	
    	// lanes waypoints
    	waypointsM[0] = new Point2D(world.getWidth() / 2 + LANE_WIDTH / 3, world.getHeight() / 2 - LANE_WIDTH / 3);
    	waypointsM[1] = new Point2D(world.getWidth() - LANE_WIDTH * 2.5, LANE_WIDTH * 2.5);
    	waypointsByLane.put(LaneType.MIDDLE,waypointsM);
    	
		Point2D[] waypointsT = new Point2D[3];
		waypointsT[0] = new Point2D(LANE_WIDTH, world.getHeight() - LANE_WIDTH);
		waypointsT[1] = new Point2D(LANE_WIDTH/2, LANE_WIDTH/2);
		waypointsT[2] = new Point2D(world.getWidth() - LANE_WIDTH * 3, LANE_WIDTH / 2);
		waypointsByLane.put(LaneType.TOP, waypointsT);
		
		Point2D[] waypointsB = new Point2D[3];
		waypointsB[0] = new Point2D(LANE_WIDTH, world.getHeight() - LANE_WIDTH);
		waypointsB[1] = new Point2D(world.getWidth() - LANE_WIDTH/2, world.getHeight() - LANE_WIDTH/2);
		waypointsB[2] = new Point2D(world.getWidth() - LANE_WIDTH/2, LANE_WIDTH * 3);
    	waypointsByLane.put(LaneType.BOTTOM, waypointsB);
    	
    	// lanes rune zone
    	runeZone.put(LaneType.MIDDLE, new Point2D(world.getWidth() / 2, world.getHeight() / 2));
    	runeZone.put(LaneType.TOP, new Point2D(LANE_WIDTH * 1.4, LANE_WIDTH * 1.4));
    	runeZone.put(LaneType.BOTTOM, new Point2D(world.getWidth() - LANE_WIDTH * 1.4, world.getHeight() - LANE_WIDTH * 1.4));
    	
        bonuses = new Bonuses();
    	// run spots
    	runes.add(new Point2D(1200, 1200));
    	runes.add(new Point2D(2800, 2800));
    }

    private LivingUnit spotTarget()
    {
    	List<LivingUnit> targets = new ArrayList<>();
        targets.addAll(Arrays.asList(world.getBuildings()));
        targets.addAll(Arrays.asList(world.getWizards()));
        targets.addAll(Arrays.asList(world.getMinions()));

        LivingUnit nearestTarget = null;
        double nearestTargetDistance = Double.MAX_VALUE;

        for (LivingUnit target : targets) {
            if (target.getFaction() == Faction.NEUTRAL || target.getFaction() == self.getFaction()) {
                continue;
            }

            double distance = self.getDistanceTo(target);

            if (distance < nearestTargetDistance) {
                nearestTarget = target;
                nearestTargetDistance = distance;
            }
        }

        
        if(nearestTarget != null && self.getDistanceTo(nearestTarget) < game.getWizardCastRange())
        	return nearestTarget;
        
        return null;
    }
    
    
    private Point2D findVanguard()
    {	
    	Point2D object = null;
    	
    	for(Building building : world.getBuildings())
    	{
    		if(building.getFaction() == self.getFaction())
    		{
    			switch(lane)
    			{
    			case MIDDLE:
    				if(building.getY() - (world.getWidth() - building.getX()) <= LANE_WIDTH)
    				{	
    					if(object == null)
    						object = new Point2D(building.getX(), building.getY());
    					else if(object.getX() - object.getY() < building.getX() - building.getY())
    					{
    						object = new Point2D(building.getX(), building.getY());
    					}
    				}
    				break;
    			case TOP:
    				if(building.getX() <= LANE_WIDTH)
    				{
    					if(object == null)
    						object = new Point2D(building.getX(), building.getY());
    					else if(object.getY() > building.getY())
    					{
    						object = new Point2D(building.getX(), building.getY());
    					}
    				}
    				break;
    			case BOTTOM:
    				if(building.getY() >= world.getHeight() - LANE_WIDTH)
    				{
    					if(object == null)
    						object = new Point2D(building.getX(), building.getY());
    					else if(object.getX() < building.getX())
    						object = new Point2D(building.getX(), building.getY());
    				}
    			}
    		}
    	}
    	
    	for(Minion minion : world.getMinions())
    	{
    		if(minion.getFaction() == self.getFaction())
    		{
    			switch(lane)
    			{
    			case MIDDLE:
    				if(minion.getY() - (world.getWidth() - minion.getX()) <= LANE_WIDTH)
    				{	
    					if(object.getX() - object.getY() < minion.getX() - minion.getY())
    						object = new Point2D(minion.getX(), minion.getY());
    				}
    				break;
    			case TOP:
    				if(minion.getY() < LANE_WIDTH)
    				{
    					if(object.getX() < minion.getX() || object.getY() > LANE_WIDTH)
    						object = new Point2D(minion.getX(), minion.getY());
    				}
    				else if(minion.getX() <= LANE_WIDTH && !(object.getY() < LANE_WIDTH))
    				{
    					if(object.getY() > minion.getY())
    						object = new Point2D(minion.getX(), minion.getY());
    				}
    				break;
    			case BOTTOM:
    				if(minion.getX() >= world.getWidth() - LANE_WIDTH)
    				{
    					if(object.getY() > minion.getY())
    						object = new Point2D(minion.getX(), minion.getY());
    				}
    				if(minion.getY() >= world.getHeight() - LANE_WIDTH)
    				{
    					if(object.getX() < minion.getX())
    						object = new Point2D(minion.getX(), minion.getY());
    				}
    			}
    		}
    	}
    	
    	return object;
    }
    
    private Boolean amIFirst(Point2D vanguard)
    {
    	switch(lane)
    	{
    	case MIDDLE:
    		if(self.getX() - self.getY() > vanguard.getX() - vanguard.getY())
    			return true;
    		return false;
    	case TOP:
    		if(self.getX() < LANE_WIDTH)
    		{
    			return self.getY() < vanguard.getY();
    		}	
    		if(vanguard.getX() > LANE_WIDTH)
				return false;
    		return self.getX() < vanguard.getX();
    	case BOTTOM:
    		if(self.getY() > world.getHeight() - LANE_WIDTH)
    		{
    			return self.getX() > vanguard.getX();
    		}	
    		if(vanguard.getY() <  world.getHeight() - LANE_WIDTH)
				return false;
    		return self.getY() > vanguard.getY();
    	}
    	
    	return false;
    }
    
    
    //*******************************************************************************************************************
    //		Bonus section
    //*******************************************************************************************************************
    
    
   
    private final class Bonuses
    {
        private final Map<Spot,Point2D> bonuses = new EnumMap<>(Spot.class);
        private final Map<Spot, AtomicBoolean> exist = new EnumMap<>(Spot.class);
        boolean taking;
        
        public Bonuses()
        {
            bonuses.put(Spot.TOP, new Point2D(1200, 1200));
            bonuses.put(Spot.BOT, new Point2D(2800, 2800));
            
            exist.put(Spot.TOP, new AtomicBoolean(false));
            exist.put(Spot.BOT, new AtomicBoolean(false));
        
            debug();
            //System.out.println("start");
        }
        
        public void watch()
        {
            //System.out.println("test");
            int index= world.getTickIndex();
            if((index % 2500) == 1)
            {
                if(index > 1)
                {
                	exist.get(Spot.TOP).set(true);
                	exist.get(Spot.BOT).set(true);
                	//System.out.println("sbonuses set");
                }
            }
            
            // bonusesa taken
            Bonus[] b = world.getBonuses();
            if(exist.get(Spot.TOP).get())
            {
                for(Wizard w : world.getWizards())
                {
                    if(w.getDistanceTo(bonuses.get(Spot.TOP).getX(), bonuses.get(Spot.TOP).getY()) < w.getVisionRange())
                    {
                        if(b.length > 0 && b[0].getX() == bonuses.get(Spot.TOP).getX())
                        {}
                        else if(b.length > 1 && b[1].getX() == bonuses.get(Spot.TOP).getX())
                        {}
                        else{
                            exist.get(Spot.TOP).set(false);
                        }    
                        break;
                    }
                }
            }
            if(exist.get(Spot.BOT).get())
            {
                for(Wizard w : world.getWizards())
                {
                    if(w.getDistanceTo(bonuses.get(Spot.BOT).getX(), bonuses.get(Spot.BOT).getY()) < w.getVisionRange())
                    {
                        if(b.length > 0 && b[0].getX() == bonuses.get(Spot.BOT).getX())
                        {}
                        else if(b.length > 1 && b[1].getX() == bonuses.get(Spot.BOT).getX())
                        {}
                        else{
                            exist.get(Spot.BOT).set(false);
                        }    
                        break;
                    }
                }
            }
        }
        
        private Boolean isItBonusZone()
        {
        	Point2D myZone = runeZone.get(lane);
        	if(self.getDistanceTo(myZone.getX(), myZone.getY()) < 2 * game.getWizardCastRange())
        			return true;
        	return false;
        }
        
        private Boolean isPushed()
        {
        	return self.getX() > self.getY();
        }
        
        private Boolean nearBonus()
        {
        	if(self.getDistanceTo(bonuses.get(Spot.TOP).getX(),bonuses.get(Spot.TOP).getY()) < self.getVisionRange() * 0.7)
        		return true;
        	
        	if(self.getDistanceTo(bonuses.get(Spot.BOT).getX(),bonuses.get(Spot.BOT).getY()) < self.getVisionRange() * 0.7)
        		return true;
        	return false;
        }
        
        private Spot canIHazBonus()
        {
        	if((!isPushed() || !isItBonusZone()) && !nearBonus())
        		return null;
        	
        	switch(lane)
        	{
        	case TOP:
        		if(exist.get(Spot.TOP).get() && canIHazThatBonus(Spot.TOP))
        		{
        			//System.out.println("taking top!");
        			taking = true;
        			takeBonus(Spot.TOP);
        		}
        		break;
        	case BOTTOM:
        		if(exist.get(Spot.BOT).get() && canIHazThatBonus(Spot.BOT))
        		{
        			//System.out.println("taking bot!");
        			taking = true;
        			takeBonus(Spot.BOT);
        		}
        		break;
        	default:
        		boolean top = false, bot = false;
        		if(exist.get(Spot.TOP).get() && canIHazThatBonus(Spot.TOP))
        		{
        			//System.out.println("taking top!");
        			taking = true;
        			top  = true;
        		}
        		else if(exist.get(Spot.BOT).get() && canIHazThatBonus(Spot.BOT))
        		{
        			//System.out.println("taking bot!");
        			taking = true;
        			bot = true;
        		}
        		if(bot == true && top == true)
        		{
        			if(self.getDistanceTo(bonuses.get(Spot.TOP).getX(), bonuses.get(Spot.TOP).getY()) < 
        					self.getDistanceTo(bonuses.get(Spot.BOT).getX(), bonuses.get(Spot.BOT).getY())) {
        				takeBonus(Spot.TOP);
        			}
        			else {
        				takeBonus(Spot.BOT);
        			}
        		}
        		else if(top){
        			takeBonus(Spot.TOP);
        		}
        		else if(bot){
        			takeBonus(Spot.BOT);
        		}
        	}
        	return null;
        }
        
        private Boolean canIHazThatBonus(Spot spot)
        {
        	Point2D b = bonuses.get(spot);
    		for(Wizard w : world.getWizards())
    		{
    			if(!w.isMe())
    			{
    				if(w.getDistanceTo(b.getY(),b.getY()) < w.getVisionRange())
    				{
    					if(Math.abs(w.getAngleTo(b.getY(),b.getY())) <  game.getStaffSector())
    					{
    						if(w.getDistanceTo(b.getY(),b.getY()) < self.getDistanceTo(b.getY(),b.getY()))
    						{
    							return false;
    						}
    					}
    				}
    			}
    		}
        	return true;
        }
        
        private void takeBonus(Spot spot)
        {
        	List<Tree> collTrees = forwardTrees2Close();
        	if(!collTrees.isEmpty() && collTrees.size() > 1)
        	{
        		if(collTrees.size() == 1)
        		{
        			move.setAction(ActionType.MAGIC_MISSILE);
        			if(self.getAngleTo((Tree)collTrees.get(0)) > 0)
        				move.setStrafeSpeed(-game.getWizardStrafeSpeed());
        			else if(self.getAngleTo((Tree)collTrees.get(0)) < 0)
        				move.setStrafeSpeed(game.getWizardStrafeSpeed());
        		}
        		else {
        			Tree cur = null;
        			for(Tree t : collTrees) {
        				if(cur == null)
        					cur = t;
        				else
        					if(Math.abs(self.getAngleTo(t)) < Math.abs(self.getAngleTo(cur)))
        						cur = t;
        			}
        			
        			move.setTurn(self.getAngleTo(cur));
        			if(Math.abs(self.getAngleTo(cur)) < game.getStaffSector() / 2)
        				move.setAction(ActionType.MAGIC_MISSILE);
        		}
        		return;
        	}
        	
        	Tree forwTree = forwardTrees();
        	if(forwTree != null) {
        	
        		move.setCastAngle(self.getAngleTo(forwTree));
        		move.setAction(ActionType.MAGIC_MISSILE);
        	}
        	
        	Unit enemy = forwardEnemy();
        	if(enemy != null)
        	{
        		move.setCastAngle(self.getAngleTo(enemy));
        		move.setAction(ActionType.FIREBALL);
        	}
        	
        	double a = self.getAngleTo(bonuses.get(spot).getX(), bonuses.get(spot).getY());
        	move.setTurn(a);
        	if(Math.abs(a) < game.getStaffSector() /2)
        	{
        		move.setSpeed(getSpeed());
        	}
        	
        	collision();
        }
        
        
        
        public void debug()
        {
            //if(world.getTickCount() % 50 == 0)
            //System.out.println("top: " + exist.get(Spot.TOP).get() + " bot: " + exist.get(Spot.BOT).get());
        }
        
        public boolean run()
        {
        	taking = false;
            watch();
            canIHazBonus();
            //debug();
            return taking;
        }
    }
    
    

    /**
     * Вспомогательный класс для хранения позиций на карте.
     */
    private static final class Point2D {
        private final double x;
        private final double y;

        private Point2D(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getDistanceTo(double x, double y) {
            return StrictMath.hypot(this.x - x, this.y - y);
        }

        public double getDistanceTo(Point2D point) {
            return getDistanceTo(point.x, point.y);
        }

        public double getDistanceTo(Unit unit) {
            return getDistanceTo(unit.getX(), unit.getY());
        }
    }
}
