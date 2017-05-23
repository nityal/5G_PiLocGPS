package nus.cirl.piloc;

import java.util.HashMap;
import java.util.Vector;
import nus.cirl.piloc.DataStruture.StepInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.util.Log;
import android.widget.ImageView;

public class PiLocHelper {
	
    private static int[] paintColors= new int[251];
    private static boolean iscolorInitialize = false;
    
	private static void InitializeColor() {
	 
	    for (int i=0;i<50;i++) 
	    	paintColors[i]= Color.rgb(0, i , i*5);//black to blue
	    for (int i=50;i<100;i++) 
	    	paintColors[i]= Color.rgb(0, i , (100-i)*5); //blue to dark green 
	    for (int i=100;i<150;i++) 
	    	paintColors[i]= Color.rgb((i-100)*3, (i-50)*2 , 0); //dark green to light green 
	    for (int i=150;i<=250;i++) 
	    	paintColors[i]= Color.rgb(i, 550-i*2 , 0);//green to yellow to red
	    
	    iscolorInitialize =true;
	}
    
	// parse map to matrix
	public static int[][] parseMap(Bitmap floorplan, String colorCode)
	{
		int[][] mapinfo = new int [floorplan.getWidth()][floorplan.getHeight()]; 
		
		for(int j=0;j<floorplan.getWidth();j++){
			for(int i=0;i<floorplan.getHeight();i++)
			{
				int Apixel = floorplan.getPixel(j,i);
				
				 if(Color.alpha(Apixel)==0){
					 mapinfo[j][i]=-1;
					 
				 }else if(colorCode.equals("red") && Color.red(Apixel)>250  && Color.blue(Apixel)<3 && Color.green(Apixel)<3){
					 //red is open area
					 mapinfo[j][i]=1;
				 }else if(colorCode.equals("black") && Color.red(Apixel)<10 && Color.blue(Apixel)<10 && Color.green(Apixel)<10){
					 //black is corridor or path
					 mapinfo[j][i]=1;
				 }else if(Color.red(Apixel)==54 && Color.blue(Apixel)==175 && Color.green(Apixel)==144){
					 //black is corridor or path
					 mapinfo[j][i]=1;
				 }
				 
				 else
				 {
					 mapinfo[j][i]=0;
				 }
			} 
		}
		return mapinfo;
	}
		
	public static Vector<StepInfo> mapTrajectory(int[][] map, int width, int height, Point startP, Point endP, Vector<StepInfo> steps) 
	{
		if(steps==null || steps.size()==0)
			return null;
		
		Vector<StepInfo> returnSteps = new Vector<StepInfo>();
		
		StepInfo lastpoint = steps.lastElement();
		double lamba = Math.sqrt(( (endP.x-startP.x)*(endP.x-startP.x)+(endP.y-startP.y)*(endP.y-startP.y)))/Math.sqrt((lastpoint.mPosX*lastpoint.mPosX+lastpoint.mPosY*lastpoint.mPosY));
		double rotateAngle = getRotateAngle(lastpoint.mPosX,lastpoint.mPosY,(endP.x-startP.x),(endP.y-startP.y));
		
		for(StepInfo step: steps){
        	double tempX = step.mPosX;
        	double tempY = step.mPosY;
			step.mPosX = Math.cos(rotateAngle)*tempX- Math.sin(rotateAngle)*tempY; 
			step.mPosY = Math.cos(rotateAngle)*tempY + Math.sin(rotateAngle)*tempX;
			
        	double a = step.mPosX*lamba+startP.x+0.5;
        	double b = startP.y+step.mPosY*lamba+0.5;
        	
			boolean found =false;
			int min = width>height? height:width;
			int stepLength = min/200 > 0 ? min/200 :  1;
			
//			for(int rad=0;rad<min;rad+=stepLength){			
//	        	for(int i=-1*(rad+1);i< (rad+1) &&  !found;i+=stepLength)
//	        		for(int j=-1*(rad+1);j<(rad+1);j+=stepLength){				        				
//	        			if((int)a+i>=0 && (int)a+i<width &&  (int)b+j>=0 && (int)b+j<height)
//	        			{
//	        				if(map[(int)a+i][(int)b+j] ==1){
//		        				a +=i;
//		        				b +=j;
//		        				found =true;
//			        			break;
//			        		}		
//	        			}		 
//	        		}
//			}		
			
			int x0 = (int)a;
			int y0 = (int)b;
			for(int i=0; !found; i++)
			{
				int length = i*stepLength;
				int rUpper = x0-length;
				int rLower = x0+length;
				int cLeft = y0-length;
				int cRight = y0+length;
				
				if(rUpper<0 || rLower>=width || cLeft<0 || cRight>=height)
				{
					break;
				}
				
				int m=rUpper;
				for(int n=cLeft; n<=cRight; n++)
				{
					if(map[m][n] ==1){
        				a = m;
        				b = n;
        				found =true;
	        			break;
	        		}		
				}
				if(found)
					break;
				
				m=rLower;
				for(int n=cLeft; n<=cRight; n++)
				{
					if(map[m][n] ==1){
        				a = m;
        				b = n;
        				found =true;
	        			break;
	        		}		
				}
				if(found)
					break;
				
				int n=cLeft;
				for(m=rUpper; m<=rLower; m++)
				{
					if(map[m][n] ==1){
        				a = m;
        				b = n;
        				found =true;
	        			break;
	        		}		
				}
				if(found)
					break;
				
				n=cRight;
				for(m=rUpper; m<=rLower; m++)
				{
					if(map[m][n] ==1){
        				a = m;
        				b = n;
        				found =true;
	        			break;
	        		}		
				}
			}
			
			if(found)
        	{
				step.mPosX=(int)a;
				step.mPosY=(int)b;
				returnSteps.add(step);
        	}else
        	{
        		step.mPosX=-1;
				step.mPosY=-1;
        	}
        	
		}
		
		return returnSteps;
	}
	
	public static double getRotateAngle(double x1, double y1, double x2, double y2)
	{
		 double epsilon = 1.0e-6;
		 double nyPI = Math.PI;
		 double dist, dot, degree, rotateAngle;
		 
		 dist = Math.sqrt( x1 * x1 + y1 * y1 );
		 x1 /= dist;
		 y1 /= dist;
		 dist = Math.sqrt( x2 * x2 + y2 * y2 );
		 x2 /= dist;
		 y2 /= dist;
		 dot = x1 * x2 + y1 * y2;
		 if ( Math.abs(dot-1.0) <= epsilon ) 
			 rotateAngle = 0.0;
		 else if ( Math.abs(dot+1.0) <= epsilon ) 
			 rotateAngle = nyPI;
		 else {
		  double cross;
		  
		  rotateAngle = Math.acos(dot);
		  cross = x1 * y2 - x2 * y1;
		  if (cross < 0 ) { 
			 rotateAngle = 2 * nyPI - rotateAngle;
		  }    
		 }
		 return rotateAngle;
	}
		
	//get nearest point that valid
	public static Point getClickedPoint(int[][] mapInfo, Bitmap floorplan, int x, int y)
	{
		boolean found =false;
		int width = floorplan.getWidth();
		int height = floorplan.getHeight();
		int min = width>height? height:width;
		int stepLength = min/400 > 1 ? min/400 :  1;
		int x0 = x;
		int y0 = y;
		int a=-1,b=-1;
		
		for(int i=0; !found; i++)
		{
			int length = i*stepLength;
			int rUpper = x0-length;
			int rLower = x0+length;
			int cLeft = y0-length;
			int cRight = y0+length;
			
			if(rUpper<0 || rLower>=width || cLeft<0 || cRight>=height)
			{
				break;
			}
			
			int m=rUpper;
			for(int n=cLeft; n<cRight; n++)
			{
				if(mapInfo[m][n] ==1){
    				a = m;
    				b = n;
    				found =true;
        			break;
        		}		
			}
			if(found)
				break;
			
			m=rLower;
			for(int n=cLeft; n<cRight; n++)
			{
				if(mapInfo[m][n] ==1){
    				a = m;
    				b = n;
    				found =true;
        			break;
        		}		
			}
			if(found)
				break;
			
			int n=cLeft;
			for(m=rUpper; m<rLower; m++)
			{
				if(mapInfo[m][n] ==1){
    				a = m;
    				b = n;
    				found =true;
        			break;
        		}		
			}
			if(found)
				break;
			
			n=cRight;
			for(m=rUpper; m<rLower; m++)
			{
				if(mapInfo[m][n] ==1){
    				a = m;
    				b = n;
    				found =true;
        			break;
        		}		
			}
		}
		
		if(found)
    	{
			return new Point(a,b);
    	}else
    	{
    		return null;
    	}
	}
		
	public static Bitmap drawPointsOnTheFloorPlan(Paint paint, Vector<Point> points, ImageView mFloorplanView, Bitmap mFloorplan)
	{
		if(mFloorplanView==null || mFloorplan==null || points==null || points.size()==0)
			return null;
		
		Bitmap mutableBitmap = mFloorplan.copy(Bitmap.Config.ARGB_8888, true);
		Canvas canvas = new Canvas(mutableBitmap);
		int pointSize = mFloorplan.getWidth()/50;
		Paint whitepaint = new Paint();
		whitepaint.setColor(Color.WHITE);
		Paint blackpaint = new Paint();
		blackpaint.setColor(Color.BLACK);
		blackpaint.setTextSize(28f);
		for(Point p : points)
		{		
			canvas.drawRect(0, 0, 150, 40, whitepaint);
			canvas.drawCircle(p.x, p.y, pointSize, paint);	
			canvas.drawText(p.x+" "+p.y, 30, 40, blackpaint);
		}
		mFloorplanView.setImageBitmap(mutableBitmap);
		
		return mutableBitmap;
	}
	
	public static Bitmap drawHeatMapOnTheFloorPlan(Paint paint, Vector<Point> points, Vector<Integer> rssiMap, ImageView mFloorplanView, Bitmap mFloorplan, Bitmap mFloorplanBackup)
	{
		if(mFloorplanBackup==null || mFloorplan==null || points==null || points.size()==0)
			return null;
		
		if(!iscolorInitialize)
			InitializeColor();
		
		Bitmap mutableBitmap = mFloorplanBackup.copy(Bitmap.Config.ARGB_8888, true);
		Canvas canvas = new Canvas(mutableBitmap);
		int pointSize = mFloorplan.getWidth()/50;
		for(int i=0;i<points.size();i++)
		{
			paint.setColor(paintColors[(3*(100-rssiMap.get(i)))%250]);
			paint.setTextSize(12f);
			canvas.drawCircle(points.get(i).x, points.get(i).y, pointSize, paint);	
			canvas.drawText(rssiMap.get(i)+"",points.get(i).x+30, points.get(i).y+30, paint);
		}
		mFloorplanView.setImageBitmap(mutableBitmap);
		
		return mutableBitmap;
	}
		
	public static Bitmap drawDensityMapOnTheFloorPlan(  HashMap<Point, Integer> mRadioDensityMap, ImageView mFloorplanView, Bitmap mFloorplan)
	{
		if(mFloorplanView==null || mFloorplan==null || mRadioDensityMap==null || mRadioDensityMap.size()==0)
			return null;
		
		if(!iscolorInitialize)
			InitializeColor();
		
		Bitmap mutableBitmap = mFloorplan.copy(Bitmap.Config.ARGB_8888, true);
		Canvas canvas = new Canvas(mutableBitmap);
		int pointSize = mFloorplan.getWidth()/50;
		Paint paint = new Paint();	
		for(Point node:mRadioDensityMap.keySet())
		{
			int colorindex = 4*mRadioDensityMap.get(node);
			if(colorindex>250)
				colorindex =250;
			paint.setColor(paintColors[colorindex]);
			paint.setTextSize(12f);
			canvas.drawCircle(node.x, node.y, pointSize, paint);	
			canvas.drawText(mRadioDensityMap.get(node)+"", node.x+30, node.y+30, paint);
		}
		mFloorplanView.setImageBitmap(mutableBitmap);
		
		return mutableBitmap;
	}
	
	public static Bitmap drawPointOnTheOriginalFloorPlan(Paint paint, Point point1,Point point2,String confi, ImageView mFloorplanView, Bitmap mFloorplan, Bitmap mFloorplanBackup)
	{
		if(mFloorplanBackup==null || mFloorplan==null || (point1 ==null && point2 ==null ))
			return null;

		Bitmap mutableBitmap = mFloorplanBackup.copy(Bitmap.Config.ARGB_8888, true);
		Canvas canvas = new Canvas(mutableBitmap);
		int pointSize = mFloorplan.getWidth()/50;
		if(point1!=null){
			paint.setColor(Color.GREEN);
			paint.setTextSize(28f);
			canvas.drawCircle(point1.x, point1.y, pointSize, paint);	
			canvas.drawText(confi, 100, 40, paint);
		}
		if(point2!=null){
			paint.setColor(Color.RED);
			paint.setTextSize(28f);
			canvas.drawCircle(point2.x, point2.y, pointSize, paint);	
	//		canvas.drawText(confi, 30, 40, paint);
		}
		
		mFloorplanView.setImageBitmap(mutableBitmap);
		
		return mutableBitmap;
	}
	
	public static Bitmap drawPointOnTheOriginalFloorPlanWithError(Paint paint, Point point1,Point point2, ImageView mFloorplanView, Bitmap mFloorplan, Bitmap mFloorplanBackup)
	{
		if(mFloorplanBackup==null || mFloorplan==null || (point1 ==null && point2 ==null ))
			return null;

		Bitmap mutableBitmap = mFloorplanBackup.copy(Bitmap.Config.ARGB_8888, true);
		Canvas canvas = new Canvas(mutableBitmap);
		int pointSize = mFloorplan.getWidth()/50;
		if(point1!=null){
			paint.setColor(Color.GREEN);
			canvas.drawCircle(point1.x, point1.y, pointSize, paint);	
		}
		if(point2!=null){
			paint.setColor(Color.RED);
			paint.setTextSize(28f);
			canvas.drawCircle(point2.x, point2.y, pointSize, paint);	
			canvas.drawText(point2.x+" "+point2.y, 30, 40, paint);
		}
		
		mFloorplanView.setImageBitmap(mutableBitmap);
		
		return mutableBitmap;
	}
		
	public static Bitmap drawPointMapOnTheOriginalFloorPlan(Paint paint,Point point2, HashMap<Point, Double> pMap,String confi, ImageView mFloorplanView, Bitmap mFloorplan, Bitmap mFloorplanBackup)
	{
		if(mFloorplanBackup==null|| mFloorplan==null  || pMap == null || pMap.size()==0 )
			return null;

		if(!iscolorInitialize)
			InitializeColor();
		
		Bitmap mutableBitmap = mFloorplanBackup.copy(Bitmap.Config.ARGB_8888, true);
		Canvas canvas = new Canvas(mutableBitmap);
		int pointSize = mFloorplan.getWidth()/50;
		
		if(point2!=null){
			paint.setColor(Color.RED);
			paint.setTextSize(28f);
			canvas.drawCircle(point2.x, point2.y, pointSize*2, paint);	
			canvas.drawText(confi+" "+pMap.size(), 30, 40, paint);
		}
		
		for(Point p : pMap.keySet()){
			paint.setTextSize(12f);
			paint.setColor(paintColors[(int) (pMap.get(p)*250.0*4)%250]);		
			canvas.drawCircle(p.x, p.y, pointSize, paint);		
			if(pMap.get(p)>0)
				canvas.drawText(pMap.get(p)+"", p.x+30, p.y+30, paint);
		}

		
		mFloorplanView.setImageBitmap(mutableBitmap);
		
		return mutableBitmap;
	}
	
	public static Bitmap refreshFloorplan(ImageView mFloorplanView, Bitmap mFloorplan, Bitmap mFloorplanBackup)
	{
		if(mFloorplanBackup==null)
			return null;
		Bitmap mutableBitmap = mFloorplanBackup.copy(Bitmap.Config.ARGB_8888, true);
		Canvas canvas = new Canvas(mutableBitmap);
		int pointSize = mFloorplan.getWidth()/50;
		mFloorplanView.setImageBitmap(mutableBitmap);
		
		return mutableBitmap;
	}
}
