import model.*;
import java.util.*;

public final class MyStrategy implements Strategy {
	 //private static final Double WAYPOINT_RADIUS = 10.0;
	 private static final Double COLLISION_RADIUS = 4.0;

    private static final double LOW_HP_FACTOR = 0.25D;
    
    // Wizzard direction.
    private enum WD
    {
    	STD, FWD, BWD, FULL_BWD, BONUS, BONUS_BACK;
    };
   

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
    private Integer stuckTick = 20_000;
    
    
    private Random random = new Random();

    private LaneType lane = null;
    
    private Double strafeSpeed = 0.0;
    
    private Wizard self;
    private World world;
    private Game game;
    private Move move;
    
    private Double LANE_WIDTH;
    
    private final Long TickLimit = 10l;
    
    @Override
    public void move(Wizard self, World world, Game game, Move move)
    {
    	initializeTick(self, world, game, move);
    	initializeStrategy(self, game);
    	
    	LivingUnit enemy = spotTarget();
    	
    	if(enemy != null)
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
    			walk();
    	}
    	
    	stuckCounter();
    	
    }
    
    private void walk()
    {
    	myTactic = WD.FWD;
    	
    	Point2D walkTo = selectWaypont();
    	move.setTurn(self.getAngleTo(walkTo.getX(), walkTo.getY()));
    	if(self.getDistanceTo(walkTo.getX(), walkTo.getY()) < self.getRadius() * 3)
    		return;
    	if(self.getAngleTo(walkTo.getX(), walkTo.getY()) < Math.PI / 6)
    	{
    		move.setSpeed(game.getWizardForwardSpeed());
    		moving = true;
    	}
    	
    	collision();
    }
    
    private void collision()
    {
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
    			Double vel = game.getWizardBackwardSpeed();
    			move.setSpeed(vel * Math.cos(res));
    			move.setStrafeSpeed(vel * Math.sin(res));
    		}
    	}
    }
    
    private Point2D selectWaypont()
    {
    	switch(lane){
    	case MIDDLE:
    		//System.out.println("mid");
    		return waypointsByLane.get(LaneType.MIDDLE)[0];
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
    				stuckTick = world.getTickIndex();
    			}
    		}
    		else{
    			stopTick = world.getTickIndex();
    		}
    	}
    	else
    		stopTick = world.getTickIndex();
    }
    
    private Tree treeCollisionDetector()
    {
    	for(Tree t : world.getTrees())
    	{
    		if(myTactic == WD.FWD)
    		{
	    		if(Math.abs(self.getAngleTo(t)) < Math.PI / 2 &&
	    				self.getDistanceTo(t) < self.getRadius() + t.getRadius() + COLLISION_RADIUS)
	    		{
	    			return t;
	    		}
    		}
    		else
    		{
    			if(Math.abs(self.getAngleTo(t)) > Math.PI / 2 &&
	    				self.getDistanceTo(t) < self.getRadius() + t.getRadius() + COLLISION_RADIUS)
	    		{
	    			return t;
	    		}
    		}
    	}
    	
    	return null;
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
    		myTactic = WD.STD;
    	}
    }
    
    private void retreat()
    {
    	Double diff = 0.0;
    	Double angle = 0.0;
    	Double speed = game.getWizardBackwardSpeed();
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
    	
    	waypointsM = new Point2D[1];
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
    	
    	waypointsM[0] = new Point2D(world.getWidth() - LANE_WIDTH * 2.5, LANE_WIDTH * 2.5);
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
    
    private void takeBonus()
    {
    	Point2D bonus = getBonusPos();
    	if(isBonusHere())
    	{ // walk to bonus
    		myTactic = WD.BONUS;
    		move.setTurn(self.getAngleTo(bonus.getX(), bonus.getY()));
    		move.setSpeed(game.getWizardForwardSpeed());
    	}
    	/*
    	else
    	{
    		if(self.getDistanceTo(runeZone.get(lane).getX(), runeZone.get(lane).getY()) >  LANE_WIDTH);
    	}
    	*/
    	
    }
    
    private Boolean canTakeBonus()
    {
    	if(myTactic != WD.FWD && myTactic != WD.BONUS)
    		return false;
    		
    	if(isBonusSeen() && canITakeBonuz() && canIHazBonus())
    		return true;
    	
    	if(isItBonusTime() && isItBonusZone() && canIHazBonus() && canITakeBonuz())
    		return true;
    	return false;
    }
    
    private Integer getNextBonusSpawn()
    {
    	Integer tick = game.getTickCount();
    	return (tick / 2500) + 2500;
    }
    
    private Point2D getBonusPos()
    {
    	if(self.getX() + self.getY() < world.getWidth())
    		return runes.get(0);
    	return runes.get(1);
    }
    
    private Boolean isItBonusTime()
    {
    	Point2D bonus = getBonusPos();
    	if(self.getDistanceTo(bonus.getX(), bonus.getY()) / game.getWizardForwardSpeed() < getNextBonusSpawn() + 100)
    		return true;
    	return false;	
    }
    
    private Boolean isItBonusZone()
    {
    	Point2D myZone = runeZone.get(lane);
    	if(self.getDistanceTo(myZone.getX(), myZone.getY()) < 2 * game.getWizardCastRange())
    			return true;
    	return false;
    }
    
    /**
     * Проверяет что наша линия достаточно отпушена чтобы взять бонус.
     * @return
     */
    private Boolean canIHazBonus()
    {
    	for(Minion m : world.getMinions())
    	{
    		if(m.getFaction() == self.getFaction())
    		{
    			if(self.getDistanceTo(m) < self.getVisionRange())
    			{
    				if(m.getX() > m.getY())
    					return true;
    			}
    		}
    	}
    	return false;
    }
    
    /**
     * Проверяет являемся ли мы ближайшим к бонусу волшебником.
     * @return
     */
    private Boolean canITakeBonuz()
    {
    	Point2D bonus = getBonusPos();
    	Double myDistance = self.getDistanceTo(bonus.getX(), bonus.getY());
    	for(Wizard w : world.getWizards())
    	{
    		if(!w.isMe())
    			if(Math.abs(w.getAngleTo(bonus.getX(), bonus.getY())) < Math.PI / 8)
    				if(w.getDistanceTo(bonus.getX(), bonus.getY()) < myDistance)
    					return false;
    	}
    	return true;
    }
    
    private Boolean isBonusHere()
    {
    	Point2D bonus = getBonusPos();
    	if(self.getDistanceTo(bonus.getX(),  bonus.getY()) < self.getVisionRange())
    	{
    		for(Bonus b : world.getBonuses())
    		{
    			if(b.getX() == bonus.getX() && b.getY() == bonus.getY())
    				return true;
    		}
    		return false;
    	}
    	return true;
    }
    
    private Boolean isBonusSeen()
    {
    	Point2D bonus = getBonusPos();
    	if(self.getDistanceTo(bonus.getX(),  bonus.getY()) < self.getVisionRange())
    	{
    		for(Bonus b : world.getBonuses())
    		{
    			if(b.getX() == bonus.getX() && b.getY() == bonus.getY())
    				return true;
    		}
    		return false;
    	}
    	return false;
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
