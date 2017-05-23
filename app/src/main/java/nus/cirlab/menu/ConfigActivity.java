package nus.cirlab.menu;

import java.util.ArrayList;
import java.util.Vector;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CompoundButton.OnCheckedChangeListener;
import nus.cirl.piloc.PiLocHelper;
import nus.cirl.piloc.RadioMapConfigService;
import nus.cirl.piloc.DataStruture.FPConf;
import nus.cirlab.menu.CollectionActivity.CheckBoxListener;

public class ConfigActivity extends Activity {

	private final String mServerIP = "piloc.d1.comp.nus.edu.sg";//

	private RadioMapConfigService mPilocService = null;
	private Bitmap mFloorplan = null;
	private Bitmap mFloorplanBackup = null;
	private ImageView mFloorplanView = null;
	private EditText mFloorIDText = null;
	private CheckBox TurningCheckBox;

	private double mXScale;
	private double mYScale;

	Vector<Point> mCurrentLocationToPaint = new Vector<Point>();
	Vector<Point> mCurrentTopKLocations = null;
	Point mCurrentLocation = null;
	Point mConfiCurrentLocation = null;
	Point mLastLocation = null;
	long lastTime = 0;

	int AlgorithmOption = 0;
	ArrayList<Point> mNodeConfigMap = null;
	private boolean isSetTurning = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.radiomapconfig);

		// Create and bind the PiLoc service
		Intent intent = new Intent(ConfigActivity.this, RadioMapConfigService.class);
		this.getApplicationContext().bindService(intent, conn, Context.BIND_AUTO_CREATE);

		// Initialize the map view
		mFloorplanView = (ImageView) findViewById(R.id.floorplanView);
		mFloorplanView.setOnTouchListener(touchListener);
		
		TurningCheckBox = (CheckBox) findViewById(R.id.TurningcheckBox);
		TurningCheckBox.setOnCheckedChangeListener(new CheckBoxListener(0));

		mFloorIDText = (EditText) findViewById(R.id.remoteIDEditText);

		findViewById(R.id.loadingPanel).setVisibility(View.GONE);
		mNodeConfigMap = new ArrayList<>();

	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mPilocService != null) {
			// Stop collecting annotated walking trajectories
			mPilocService.stopCollection();
		}

		// Unbind the service
		getApplicationContext().unbindService(conn);
	}

	private ServiceConnection conn = new ServiceConnection() {
		public void onServiceDisconnected(ComponentName name) {
			mPilocService.onDestroy();
		}

		public void onServiceConnected(ComponentName name, IBinder service) {
			RadioMapConfigService.MyBinder binder = (RadioMapConfigService.MyBinder) service;
			mPilocService = binder.getService();
			// Start collecting only WiFi fingerprints

			mPilocService.startCollection();
		}
	};
	
	class CheckBoxListener implements OnCheckedChangeListener {
		int mconf;

		CheckBoxListener(int conf) {
			mconf = conf;
		}

		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			if (mconf == 0) {
				if (isChecked){
					isSetTurning = true;
					mNodeConfigMap.clear();
					mFloorplan = PiLocHelper.refreshFloorplan(mFloorplanView, mFloorplan, mFloorplanBackup);
				}
				else
					isSetTurning = false;
			} 
		}
	}
	

	// The touch listener to coordinate the data collection interactions
	private View.OnTouchListener touchListener = new View.OnTouchListener() {
		Paint mPaint = new Paint();

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			if (mFloorplan == null)
				return false;

			switch (event.getAction()) {

			case MotionEvent.ACTION_DOWN:
				// Convert the screen tap location to the location in the map
				int x = (int) (event.getX() * mXScale);
				int y = (int) (event.getY() * mYScale);

				// Get the nearest walking path point on the map
				Point clickPoint = mPilocService.getClickedPoint(x, y);
				// Cannot get the clicked point
				if (clickPoint == null) {
					return false;
				}

				if (mNodeConfigMap.size() > 0) {
					// Update bitmap, set all previous mapped points to gray
					// color
					mPaint.setColor(Color.YELLOW);
					Vector<Point> pointsToDraw = new Vector<Point>();
					pointsToDraw.add(clickPoint);

					for (Point s : mNodeConfigMap) {
						pointsToDraw.add(new Point((int) s.x, (int) s.y));
					}

					mFloorplan = PiLocHelper.drawPointsOnTheFloorPlan(mPaint, pointsToDraw, mFloorplanView, mFloorplan);

				}
				// Set current click point to yellow color
				mPaint.setColor(Color.GREEN);
				Vector<Point> pointsToDraw = new Vector<Point>();
				pointsToDraw.add(clickPoint);
				mNodeConfigMap.add(clickPoint);
				mFloorplan = PiLocHelper.drawPointsOnTheFloorPlan(mPaint, pointsToDraw, mFloorplanView, mFloorplan);
				
				TextView tv = (TextView) findViewById(R.id.fingerprintTextView);
				tv.setText(mNodeConfigMap.size()+" node\n");
				break;

			default:
				break;
			}
			return true;
		}
	};

	public void onFloorPlanBtnClicked(View v) {
		// Get the user input floor ID
		String floorPlanID = mFloorIDText.getText().toString();

		// return immediately if no floor ID is entered
		if (floorPlanID.equals("")) {
			Toast.makeText(getBaseContext(), "Please enter the floor ID", Toast.LENGTH_SHORT).show();
			return;
		}

		// Show loading dialog
		findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);

		// Create an asyntask to get the floor plan with entered floorPlanID
		new GetFloorPlanTask().execute(floorPlanID, null, null);
	}

	public void onCancelMappingClicked(View v) {

		// No mapped steps, return immediately
		if (mNodeConfigMap == null)
			return;

		// Set previous mapped points to black color
		Point lastNode = mNodeConfigMap.get(mNodeConfigMap.size() - 1);
		Paint paint = new Paint();
		paint.setColor(Color.BLACK);
		Vector<Point> pointsToDraw = new Vector<Point>();
		pointsToDraw.add(lastNode);
		mNodeConfigMap.remove(mNodeConfigMap.size() - 1);
		mFloorplan = PiLocHelper.drawPointsOnTheFloorPlan(paint, pointsToDraw, mFloorplanView, mFloorplan);
		TextView tv = (TextView) findViewById(R.id.fingerprintTextView);
		tv.setText(mNodeConfigMap.size()+" node \n");

	}

	public void onUpdateRadiomapClicked(View v) {
		findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);
		// Start an asyntask to upload the collected data to the server
		new UpdateRadiomapConfigTask().execute(null, null, null);
	}

	private class GetFloorPlanTask extends AsyncTask<String, Void, String> {
		protected String doInBackground(String... floorPlanID) {
			try {
				// Get floor plan using the floorPlanID from server
				mFloorplan = mPilocService.getFloorPlan(mServerIP, floorPlanID[0]);
				// Back up the floor plan
				mFloorplanBackup = mFloorplan.copy(Bitmap.Config.ARGB_8888, true);
				// Reset radio map when a new floor is loaded
				// Clear the service states for the new floor
				mPilocService.clear();
			} catch (Exception e) {
				e.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onPostExecute(String s) {
			if (mFloorplan == null) {
				Toast.makeText(getBaseContext(), "Get floorplan Failed!", Toast.LENGTH_SHORT).show();
				return;
			}
			// Show the return floor plan to the image view
			ImageView imageview = (ImageView) findViewById(R.id.floorplanView);
			imageview.setImageBitmap(mFloorplan);
			Toast.makeText(getBaseContext(), "Get floorplan successfully", Toast.LENGTH_SHORT).show();
			imageview.setDrawingCacheEnabled(true);
			Bitmap bitmapOnScreen = imageview.getDrawingCache();

			// Store the ratio between image on the screen and the real image
			mXScale = mFloorplan.getWidth() * 1.0 / bitmapOnScreen.getWidth();
			mYScale = mFloorplan.getHeight() * 1.0 / bitmapOnScreen.getHeight();

			findViewById(R.id.loadingPanel).setVisibility(View.GONE);
		}
	}

	private class UpdateRadiomapConfigTask extends AsyncTask<String, Void, String> {
		protected String doInBackground(String... f) {
			try {
				String floorPlanID = mFloorIDText.getText().toString();
			
				// Upload the current config to the server
				if (mPilocService.uploadRadiomapconfig(mServerIP, floorPlanID, mNodeConfigMap,isSetTurning )) {
					// Refresh the image view after uploading the data
					mFloorplan = PiLocHelper.refreshFloorplan(mFloorplanView, mFloorplan, mFloorplanBackup);
					Toast.makeText(getBaseContext(), "Update map config successful", Toast.LENGTH_SHORT).show();
				} else {
					Toast.makeText(getBaseContext(), "Update map config failed", Toast.LENGTH_SHORT).show();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onPostExecute(String s) {
			findViewById(R.id.loadingPanel).setVisibility(View.GONE);
		}
	}

}