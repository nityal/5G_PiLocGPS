package nus.cirlab.menu;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
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
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import nus.cirl.piloc.DataStruture.Radiomap;
import nus.cirl.piloc.PiLocHelper;
import nus.cirl.piloc.SimulationService;

public class SimulationActivity extends Activity {

	private final String mServerIP = "piloc.d1.comp.nus.edu.sg";//

	private SimulationService mPilocService = null;
	private Radiomap mRadiomap = null;
	private Bitmap mFloorplan = null;
	private Bitmap mFloorplanBackup = null;
	private ImageView mFloorplanView = null;
	private EditText mFloorIDText = null;
	private Set<Point> mTurningPointSet = new HashSet<Point>();

	Vector<Point> mCurrentLocationToPaint = new Vector<Point>();
	Point mCurrentLocation = null;
	Point mConfiCurrentLocation = null;

	private CheckBox WeightedCheckBox, ConfidenceCheckBox, MagCheckBox, ViterbiCheckBox;
	private boolean mIsUseWiFi = true;
	private boolean mIsUseBluetooth = false;
	private boolean mIsUseMagntic = true;
	private boolean mIsCompressed = false;

	private String mCurrentConfidenceValue = "";
	private HashMap<Point, Double> mCurrentConfidenceMap = new HashMap<Point, Double>();

	private HashMap<Point, Integer> mRadioDensityMap = new HashMap<Point, Integer>();
	private HashMap<Point, Integer> mAPDensityMap = new HashMap<Point, Integer>();

	int AlgorithmOption = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.simulation);

		// Create and bind the PiLoc service
		Intent intent = new Intent(SimulationActivity.this, SimulationService.class);
		this.getApplicationContext().bindService(intent, conn, Context.BIND_AUTO_CREATE);

		// Initialize the map view
		mFloorplanView = (ImageView) findViewById(R.id.floorplanView);

		mFloorIDText = (EditText) findViewById(R.id.remoteIDEditText);

		WeightedCheckBox = (CheckBox) findViewById(R.id.WeightedcheckBox);
		ConfidenceCheckBox = (CheckBox) findViewById(R.id.ConfidencecheckBox);
		ViterbiCheckBox = (CheckBox) findViewById(R.id.ViterbicheckBox);
		MagCheckBox = (CheckBox) findViewById(R.id.MagcheckBox);

		WeightedCheckBox.setOnCheckedChangeListener(new CheckBoxListener(0));
		ConfidenceCheckBox.setOnCheckedChangeListener(new CheckBoxListener(1));
		ViterbiCheckBox.setOnCheckedChangeListener(new CheckBoxListener(2));
		MagCheckBox.setOnCheckedChangeListener(new CheckBoxListener(3));

		findViewById(R.id.loadingPanel).setVisibility(View.GONE);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;

	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		menu.clear();
		String fpString = "";

		int i = 0;
		menu.add(0, i, Menu.NONE, "ALL");
		i++;
		for (Point p : mRadioDensityMap.keySet()) {
			menu.add(0, i, Menu.NONE, p.x + " " + p.y);
			i++;
			if(!mTurningPointSet.contains(p))
				fpString += p.x + "\t\t" + p.y + "\t\t" + mRadioDensityMap.get(p) + "nodes\t\t" + mAPDensityMap.get(p)
					+ "aps\n";
			else
				fpString += p.x + "\t\t" + p.y + "\t\t" + mRadioDensityMap.get(p) + "nodes\t\t" + mAPDensityMap.get(p)
				+ "aps *\n";
		}

		if (mRadioDensityMap.size() > 0) {
			Paint mPaint = new Paint();
			mPaint.setColor(Color.GRAY);
			mFloorplan = PiLocHelper.drawDensityMapOnTheFloorPlan(mRadioDensityMap, mFloorplanView, mFloorplan);
		}

		TextView tv = (TextView) findViewById(R.id.fingerprintTextView);
		tv.setText(fpString);

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		String pointinfo = (String) item.getTitle();
		if(pointinfo.equalsIgnoreCase("ALL")){
			mPilocService.getDataBaseConfidenceSimulationForAll( AlgorithmOption);
			mCurrentConfidenceMap = mPilocService.fetchSimulationMap();
			Paint paint = new Paint();
			if (mCurrentConfidenceMap != null) {
				mFloorplan = PiLocHelper.drawPointMapOnTheOriginalFloorPlan(paint, null, mCurrentConfidenceMap, "",
						mFloorplanView, mFloorplan, mFloorplanBackup);
				TextView tv = (TextView) findViewById(R.id.fingerprintTextView);
				tv.setText(mPilocService.fetchConfidence());
			}
		}else{
			String[] Stringsets = pointinfo.split(" ");
			Point p = new Point(Integer.parseInt(Stringsets[0]), Integer.parseInt(Stringsets[1]));
			mPilocService.getDataBaseConfidenceSimulation(p, AlgorithmOption);
			mCurrentConfidenceMap = mPilocService.fetchSimulationMap();
			Paint paint = new Paint();
			if (mCurrentConfidenceMap != null) {
				mFloorplan = PiLocHelper.drawPointMapOnTheOriginalFloorPlan(paint, p, mCurrentConfidenceMap, "",
						mFloorplanView, mFloorplan, mFloorplanBackup);
				TextView tv = (TextView) findViewById(R.id.fingerprintTextView);
				tv.setText(mPilocService.fetchConfidence());
			}
		}
	
		return super.onOptionsItemSelected(item);
	}

	class CheckBoxListener implements OnCheckedChangeListener {
		int mconf;

		CheckBoxListener(int conf) {
			mconf = conf;
		}

		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			if (mconf == 0) {
				if (isChecked)
					AlgorithmOption = 0;
			} else if (mconf == 1) {
				if (isChecked)
					AlgorithmOption = 1;
			} else if (mconf == 2) {
				if (isChecked)
					AlgorithmOption = 2;
			} else if (mconf == 3) {
				if (isChecked)
					AlgorithmOption = 3;
//				if (isChecked)
//					mIsUseMagntic = true;
//				else
//					mIsUseMagntic = false;
			}
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mPilocService != null) {
			// Stop collecting annotated walking trajectories
		}

		// Unbind the service
		getApplicationContext().unbindService(conn);
		// Stop all localization threads
		// Stop updating fingerprint
	}

	private ServiceConnection conn = new ServiceConnection() {
		public void onServiceDisconnected(ComponentName name) {
			mPilocService.onDestroy();
		}

		public void onServiceConnected(ComponentName name, IBinder service) {
			SimulationService.MyBinder binder = (SimulationService.MyBinder) service;
			mPilocService = binder.getService();
			// Start collecting only WiFi fingerprints
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
		new GetRadioMapTask().execute(null, null, null);
	}

	private class GetFloorPlanTask extends AsyncTask<String, Void, String> {
		protected String doInBackground(String... floorPlanID) {
			try {
				// Get floor plan using the floorPlanID from server
				mFloorplan = mPilocService.getFloorPlan(mServerIP, floorPlanID[0]);
				// Back up the floor plan
				mFloorplanBackup = mFloorplan.copy(Bitmap.Config.ARGB_8888, true);
				// Reset radio map when a new floor is loaded
				mRadiomap = null;
				// Rest the collected data of previous floor
				// Clear the service states for the new floor
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

			findViewById(R.id.loadingPanel).setVisibility(View.GONE);
		}
	}

	private class GetRadioMapTask extends AsyncTask<String, Void, String> {
		protected String doInBackground(String... s) {
			try {
				// Get radio map using the floor ID from server
				String floorID = mFloorIDText.getText().toString();
				mRadiomap = mPilocService.getRadiomap(mServerIP, floorID);
				mRadioDensityMap = mPilocService.getRadioDensityMap();
				mAPDensityMap = mPilocService.getAPDensityMap();
				mTurningPointSet = mPilocService.getTurningPointSet();

				Vector<Point> pointsToDraw = new Vector<Point>();
				for (Point p : mRadiomap.mLocFingeprints.keySet()) {
					if (!pointsToDraw.contains(p))
						pointsToDraw.add(p);
				}
				if (mRadiomap != null && pointsToDraw.size() > 0) {
					invalidateOptionsMenu();
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onPostExecute(String s) {
//			String floorID = mFloorIDText.getText().toString();
//			mRadiomap = mPilocService.getRadiomap(mServerIP, floorID);
			if (mRadiomap != null) {
				findViewById(R.id.loadingPanel).setVisibility(View.GONE);
				Toast.makeText(getBaseContext(), "Get radiomap successfully", Toast.LENGTH_SHORT).show();
			} else
				Toast.makeText(getBaseContext(), "Get radiomap failed", Toast.LENGTH_SHORT).show();

		}
	}

}
