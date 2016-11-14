import java.util.*;
import java.awt.*;
import static java.lang.StrictMath.*;
import model.*;

public class MyRenderer
{
	private static Graphics _graphics;
    private static World _world;
    private static Game _game;

    private static int _canvasWidth;
    private static int _canvasHeight;

    private static double _left;
    private static double _top;
    private static double _width;
    private static double _height;
    
    
	public static void test()
	{
		
	}
	
	public static void DrawAfterScene(Graphics graphics, World world, Game game, int canvasWidth, int canvasHeight,
            double left, double top, double width, double height)
	{
		updateFields(graphics, world, game, canvasWidth, canvasHeight, left, top, width, height);
		
		wizzardHp(graphics, world, game);
	}
	
	public static void wizzardHp(Graphics graphics, World world, Game game)
	{
		for(Wizard wizard : world.getWizards())
		{
			Point2I p2w = toCanvasPosition(wizard.getX(), wizard.getY());
        	graphics.drawString(wizard.getLife() + "/" + wizard.getMaxLife(), p2w.getX(), p2w.getY());
		}
	}
	
	private static void updateFields(Graphics graphics, World world, Game game, int canvasWidth, int canvasHeight,
            double left, double top, double width, double height) 
	{
		_graphics = graphics;
		_world = world;
		_game = game;
		
		_canvasWidth = canvasWidth;
		_canvasHeight = canvasHeight;
		
		_left = left;
		_top = top;
		_width = width;
		_height = height;
	}
	
	private static Point2I toCanvasPosition(double x, double y) {
        return new Point2I((x - _left) * _canvasWidth / _width, (y - _top) * _canvasHeight / _height);
    }
	
	
	
	private static final class Point2I {
        private int x;
        private int y;

        private Point2I(double x, double y) {
            this.x = toInt(round(x));
            this.y = toInt(round(y));
        }

        private Point2I(int x, int y) {
            this.x = x;
            this.y = y;
        }

        private Point2I() {
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }
        
        private static int toInt(double value) {
            @SuppressWarnings("NumericCastThatLosesPrecision") int intValue = (int) value;
            if (abs((double) intValue - value) < 1.0D) {
                return intValue;
            }
            throw new IllegalArgumentException("Can't convert double " + value + " to int.");
        }
    }
}