import model.*;
import java.util.*;

public final class MyStrategy implements Strategy {
	 private static final Double WAYPOINT_RADIUS = 100.0D;
	 private static final Double COLLISION_RADIUS = 5.0;

    private static final double LOW_HP_FACTOR = 0.25D;

    /**
     * Ключевые точки для каждой линии, позволяющие упростить управление перемещением волшебника.
     * <p>
     * Если всё хорошо, двигаемся к следующей точке и атакуем противников.
     * Если осталось мало жизненной энергии, отступаем к предыдущей точке.
     */
    
    private final Map<LaneType, Point2D[]> waypointsByLane = new EnumMap<>(LaneType.class);
    private Boolean moving = false;

    private Random random = new Random();

    private LaneType lane;
    private Point2D[] waypoints;
    
    private Double strafeSpeed = 0.0;
    

    private Wizard self;
    private World world;
    private Game game;
    private Move move;
    
    private Double speed;
    private Long ticks = 0l;
    
    private final Long TickLimit = 10l;
    
    @Override
    public void move(Wizard self, World world, Game game, Move move)
    {
    	initializeTick(self, world, game, move);
    	initializeStrategy(self, game);
    	
    	//world.getWidth()
    	
    	LivingUnit enemy = spotTarget();
    	
    	if(enemy != null)
    	{
    		fight(enemy);
    	}
    	else
    	{
    		walk();
    	}
    }
    
    private void walk()
    {
    	move.setTurn(self.getAngleTo(waypoints[1].getX(), waypoints[1].getY()));
    	if(self.getAngleTo(waypoints[1].getX(), waypoints[1].getY()) < Math.PI / 6)
    	{
    		move.setSpeed(game.getWizardForwardSpeed());
    		moving = true;
    	}
    	
    	Unit u = collisionDetector();
    	if(u != null)
    	{
    		Double angle = self.getAngleTo(u);
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
		case 3:
		case 8:
			lane = LaneType .MIDDLE;
		default :
			lane = LaneType.BOTTOM;
    	}
    }
    
    
    private Unit collisionDetector()
    {
    	if(moving != true)
    		return null;
    	
    	for(Building b : world.getBuildings())
    	{	
    		if(Math.abs(self.getAngleTo(b)) < Math.PI / 2 &&
    				self.getDistanceTo(b) < self.getRadius() + b.getRadius() + COLLISION_RADIUS)
    		{
    			return b;
    		}
    	}
    	
    	return null;
    }
    
    
    private void fight(LivingUnit target)
    {
    	move.setTurn(self.getAngleTo(target));
    	
    	if(Math.abs(self.getAngleTo(target)) < game.getStaffSector() / 2.0D)
    		move.setAction(ActionType.MAGIC_MISSILE);
    	
    	if(self.getDistanceTo(target) < self.getCastRange() * 0.9)
    	{
    		move.setSpeed(-game.getWizardForwardSpeed() / 2);
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
    	chooseLane();
    	
    	Building[] buildings = world.getBuildings();
    	waypoints = new Point2D[2];
    	
    	for(Building building : buildings)
    	{
    		if(building.getType() == BuildingType.FACTION_BASE)
    		{
    			if(building.getFaction() == self.getFaction())
    			{
    				waypoints[0] = new Point2D(building.getX(), building.getY());
    			}
    			
    		}
    	}
    	
    	waypoints[1] = new Point2D(world.getWidth() - waypoints[0].getX(), world.getHeight() - waypoints[0].getY());
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
