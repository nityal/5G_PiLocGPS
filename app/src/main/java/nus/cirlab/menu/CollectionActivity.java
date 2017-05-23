package nus.cirlab.menu;

import java.util.HashMap;
import java.util.SortedSet;
import java.util.TreeSet;
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
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import nus.cirl.piloc.DataStruture.FPConf;
import nus.cirl.piloc.DataStruture.Fingerprint;
import nus.cirl.piloc.DataStruture.Radiomap;
import nus.cirl.piloc.DataStruture.StepInfo;
import nus.cirl.piloc.PiLocHelper;
import nus.cirl.piloc.RadioMapCollectionService;

public class CollectionActivity extends Activity {

	private final String mServerIP = "piloc.d1.comp.nus.edu.sg";//

	private RadioMapCollectionService mPilocService = null;
	private Radiomap mRadiomap = null;
	private HashMap<String, Vector<Point>> mRadioDistMap = null;
	private HashMap<String, Double> mImpactFactorMap = null;
	private HashMap<String, Integer> mImpactMeanMap = null;
	private Bitmap mFloorplan = null;
	private Bitmap mFloorplanBackup = null;
	private ImageView mFloorplanView = null;
	private EditText mFloorIDText = null;
	private boolean isStartCollecting = false;

	private double mXScale;
	private double mYScale;

	private Vector<StepInfo> mCurrentMappedSteps = null;
	private Boolean mIsRedoMapping = false;
	private Boolean mIsLocating = false;
	private Boolean mIsUpdatingFPToUI = false;

	Vector<Point> mCurrentLocationToPaint = new Vector<Point>();
	Vector<Point> mCurrentTopKLocations = null;
	Point mCurrentLocation = null;
	Point mConfiCurrentLocation = null;
	Point mLastLocation = null;
	long lastTime = 0;
	private String mCurrentFPString = "";
	private Button mLocalizationButton;
	private CheckBox WiFiCheckBox, BluetoothCheckBox, MagCheckBox, FingerprintDensity, SamplingCheckBox;
	private boolean mIsUseWiFi = true;
	private boolean mIsUseBluetooth = false;
	private boolean mIsUseMagntic = true;
	private boolean mIsShowFPDensity = false;
	private boolean mIsDoSampling = false;

	private boolean isUploadingLocation = false;
	private boolean isShowRadiomap = false;
	private String mCurrentConfidenceValue = "";
	private HashMap<Point, Double> mCurrentConfidenceMap = new HashMap<Point, Double>();
	private HashMap<Point, Integer> mRadioDensityMap = new HashMap<Point, Integer>();
	private HashMap<Point, Integer> mAPDensityMap = new HashMap<Point, Integer>();

	// private boolean isUseWeighted = true;
	// private boolean isUseConfidence = false;
	// private boolean isUseConfidenceMap = false;
	int AlgorithmOption = 0;
//	 private DataOutputStream logWriter = null;
	private Vector<Vector<Fingerprint>> FPHistoryWindows = new Vector<Vector<Fingerprint>>();
	private Vector<Fingerprint> showfp = new Vector<Fingerprint>();
//	int logcounter = 0;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.radiomapcollection);

		// Create and bind the PiLoc service
		Intent intent = new Intent(CollectionActivity.this, RadioMapCollectionService.class);
		this.getApplicationContext().bindService(intent, conn, Context.BIND_AUTO_CREATE);

		// Initialize the map view
		mFloorplanView = (ImageView) findViewById(R.id.floorplanView);
		mFloorplanView.setOnTouchListener(touchListener);

		mFloorIDText = (EditText) findViewById(R.id.remoteIDEditText);
			
		WiFiCheckBox = (CheckBox) findViewById(R.id.WiFicheckBox);
		BluetoothCheckBox = (CheckBox) findViewById(R.id.BluetoothcheckBox);
		MagCheckBox = (CheckBox) findViewById(R.id.MagneticcheckBox);
		FingerprintDensity = (CheckBox) findViewById(R.id.FPcheckBox);

		WiFiCheckBox.setOnCheckedChangeListener(new CheckBoxListener(0));
		BluetoothCheckBox.setOnCheckedChangeListener(new CheckBoxListener(1));
		FingerprintDensity.setOnCheckedChangeListener(new CheckBoxListener(2));
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
		if (isShowRadiomap && mRadioDistMap != null) {
			String fpString = "";
			if (mIsShowFPDensity) {
				int i = 0;
				for (Point p : mRadioDensityMap.keySet()) {
					menu.add(0, i, Menu.NONE, p.x + " " + p.y);
					i++;
					fpString += p.x + "\t\t" + p.y + "\t\t" + mRadioDensityMap.get(p) + "nodes\t\t"
							+ mAPDensityMap.get(p) + "aps\n";
				}

				if (mRadioDensityMap.size() > 0) {
					Paint mPaint = new Paint();
					mPaint.setColor(Color.GRAY);
					mFloorplan = PiLocHelper.drawDensityMapOnTheFloorPlan(mRadioDensityMap, mFloorplanView, mFloorplan);
				}

			} else {
				SortedSet<String> sortedset = new TreeSet<String>(mRadioDistMap.keySet());
//				String[] keyArray = mRadioDistMap.keySet().toArray(new String[mRadioDistMap.keySet().size()]);
				int i=0;
				for (String mac: sortedset) {
					menu.add(0, i, Menu.NONE, mac);
					i++;
				}

				Vector<Point> pointsToDraw = new Vector<Point>();
				for (Point p : mRadiomap.mLocFingeprints.keySet()) {
					if (!pointsToDraw.contains(p))
						pointsToDraw.add(p);
				}
				if (pointsToDraw.size() > 0) {
					Paint mPaint = new Paint();
					mPaint.setColor(Color.GRAY);
					mFloorplan = PiLocHelper.drawPointsOnTheFloorPlan(mPaint, pointsToDraw, mFloorplanView, mFloorplan);
				}

				for (String mac : mImpactFactorMap.keySet()) {
					fpString += mac + "\t\t" + mImpactMeanMap.get(mac) + "dbm\t\t" + mImpactFactorMap.get(mac)
							+ "dbm\n";

				}
			}

			TextView tv = (TextView) findViewById(R.id.fingerprintTextView);
			tv.setText(fpString);

		} 
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		if (isShowRadiomap) {
			if (!mIsShowFPDensity) {
				String mac = (String) item.getTitle();

				Vector<Point> pointsToDraw = mRadioDistMap.get(mac);
				Vector<Integer> rssiMap = new Vector<Integer>();

				// String fpString = "";
				for (Point node : pointsToDraw) {
					Vector<Fingerprint> fps = mRadiomap.mLocFingeprints.get(node);
					for (Fingerprint fp : fps) {
						if (fp.mMac.equals(mac)) {
							// fpString +=node.x+"\t"+node.y+"\t"+fp.mRSSI+"
							// dbm\n";
							rssiMap.add(fp.mRSSI);
							break;
						}
					}
				}
				TextView tv = (TextView) findViewById(R.id.fingerprintTextView);
				tv.setText("");
				Paint paint = new Paint();
				if (pointsToDraw.size() > 0) {
					mFloorplan = PiLocHelper.drawHeatMapOnTheFloorPlan(paint, pointsToDraw, rssiMap, mFloorplanView,
							mFloorplan, mFloorplanBackup);
				}
			} else {// for each location show info or do the sampling
				// get the point
				String pointinfo = (String) item.getTitle();
				String[] Stringsets = pointinfo.split(" ");
				Point p = new Point(Integer.parseInt(Stringsets[0]), Integer.parseInt(Stringsets[1]));

			
					// get confidence level estimation
					mPilocService.getDataBaseConfidenceQualityAreaBased(mRadiomap, p);
					mCurrentConfidenceMap = mPilocService.fetchConfidenceMap();
					Paint paint = new Paint();
					if (mCurrentConfidenceMap != null) {
						mFloorplan = PiLocHelper.drawPointMapOnTheOriginalFloorPlan(paint, p, mCurrentConfidenceMap, "",
								mFloorplanView, mFloorplan, mFloorplanBackup);
						TextView tv = (TextView) findViewById(R.id.fingerprintTextView);
						tv.setText(mPilocService.fetchConfidence());
					}
				

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
					mIsUseWiFi = true;
				else
					mIsUseWiFi = false;
			} else if (mconf == 1) {
				if (isChecked)
					mIsUseBluetooth = true;
				else
					mIsUseBluetooth = false;
			} else if (mconf == 2) {
				if (isChecked)
					mIsShowFPDensity = true;
				else
					mIsShowFPDensity = false;
			} else if (mconf == 3) {
				if (isChecked)
					mIsUseMagntic = true;
				else
					mIsUseMagntic = false;
			} 
			mPilocService.setFPConf(new FPConf(mIsUseWiFi, mIsUseBluetooth, mIsUseMagntic, false));
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mPilocService != null) {
			// Stop collecting annotated walking trajectories
			mPilocService.stopCollection();
		}
//		try {
//			logWriter.close();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
		// Unbind the service
		getApplicationContext().unbindService(conn);
		// Stop all localization threads
		mIsLocating = false;
		// Stop updating fingerprint
		mIsUpdatingFPToUI = false;
	}

	private ServiceConnection conn = new ServiceConnection() {
		public void onServiceDisconnected(ComponentName name) {
			mPilocService.onDestroy();
		}

		public void onServiceConnected(ComponentName name, IBinder service) {
			RadioMapCollectionService.MyBinder binder = (RadioMapCollectionService.MyBinder) service;
			mPilocService = binder.getService();
			// Start collecting only WiFi fingerprints

			mPilocService.setFPConfAndStartColllectingFP(new FPConf(mIsUseWiFi, mIsUseBluetooth, mIsUseMagntic,false)); // also
			// collect
			// bluetooth
			// Start data collection service
			mPilocService.startCollection();
		}
	};

	// The touch listener to coordinate the data collection interactions
	private View.OnTouchListener touchListener = new View.OnTouchListener() {
		// Initialise the tapping locations
		private Point mStartLoc = null;
		private Point mEndLoc = null;
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

				// No starting location yet, set current point as the starting
				// location
				if (mStartLoc == null) {
					mPilocService.setStartCoutingStep(true);
					isStartCollecting = true;
					mStartLoc = clickPoint;
				} else {
					// If it is not re-mapping, set previous ending point as the
					// starting location
					if (!mIsRedoMapping && mEndLoc != null)
						mStartLoc = mEndLoc;

					// Set current point as the ending location
					mEndLoc = clickPoint;
				}

				if (mStartLoc != null && mEndLoc != null) {
					// Update bitmap, set all previous mapped points to gray
					// color
					mPaint.setColor(Color.GRAY);
					Vector<Point> pointsToDraw = new Vector<Point>();
					pointsToDraw.add(mStartLoc);
					if (mCurrentMappedSteps != null) {
						for (StepInfo s : mCurrentMappedSteps) {
							pointsToDraw.add(new Point((int) s.mPosX, (int) s.mPosY));
						}
					}
					mFloorplan = PiLocHelper.drawPointsOnTheFloorPlan(mPaint, pointsToDraw, mFloorplanView, mFloorplan);

					// If it is not re-mapping, confirm the previous mapping
					if (!mIsRedoMapping)
						mPilocService.confirmCurrentMapping();
					else
						mIsRedoMapping = false;

					// Get mapping for the newly collected annotated walking
					// trajectory
					mCurrentMappedSteps = mPilocService.mapCurrentTrajectory(mStartLoc, mEndLoc, mFloorplan);
					if (mCurrentMappedSteps != null) {
						// Set newly mapped points to green color on the bitmap
						mPaint.setColor(Color.GREEN);
						pointsToDraw = new Vector<Point>();
						for (StepInfo s : mCurrentMappedSteps) {
							if (s.mPosX != -1 && s.mPosY != -1)
								pointsToDraw.add(new Point((int) s.mPosX, (int) s.mPosY));
						}
						mFloorplan = PiLocHelper.drawPointsOnTheFloorPlan(mPaint, pointsToDraw, mFloorplanView,
								mFloorplan);
					}
				}
				// Set current click point to yellow color
				mPaint.setColor(Color.YELLOW);
				Vector<Point> pointsToDraw = new Vector<Point>();
				pointsToDraw.add(clickPoint);
				mFloorplan = PiLocHelper.drawPointsOnTheFloorPlan(mPaint, pointsToDraw, mFloorplanView, mFloorplan);
				break;

			default:
				break;
			}
			return true;
		}
	};

	public void onFPBtnClicked(View v) {
		// Avoid creating duplicated threads
		if (mIsUpdatingFPToUI)
			return;

		mIsUpdatingFPToUI = true;

		// Create thread to continuously update fingerprints to the screen

		new Thread(new Runnable() {
			public void run() {
				try {
					// Run as long as the updating flag is set to true
					while (mIsUpdatingFPToUI) {
						// Get current fingerprints
						Vector<Fingerprint> fpList = mPilocService.getFingerprint();

						if (fpList != null) {
							// Construct fingerprint string
							String fpString = "";
							for (int i = 0; i < fpList.size(); i++) {
								fpString += fpList.get(i).mMac + " " + fpList.get(i).mFrequency + " "
										+ -fpList.get(i).mRSSI + "\n";
							}
							mCurrentFPString = fpString;
//							logWriter.write((mCurrentFPString+"\n").getBytes());
							// Run on UI thread to show the fingerprints to the
							// screen
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									TextView tv = (TextView) findViewById(R.id.fingerprintTextView);
									tv.setText(mCurrentFPString);
								}
							});
						}
						Thread.sleep(500);
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();

	}

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
		// If it is already in the re-mapping state, remove the current mapping
		if (mIsRedoMapping) {
			mPilocService.removeCurrentMapping();
			mIsRedoMapping = false;
			Toast.makeText(getBaseContext(), "Previous mapping removed", Toast.LENGTH_SHORT).show();
		} else {
			// No mapped steps, return immediately
			if (mCurrentMappedSteps == null)
				return;

			// Set the remap flag
			mIsRedoMapping = true;

			// Set previous mapped points to black color
			Paint paint = new Paint();
			paint.setColor(Color.BLACK);
			Vector<Point> pointsToDraw = new Vector<Point>();
			for (StepInfo s : mCurrentMappedSteps) {
				pointsToDraw.add(new Point((int) s.mPosX, (int) s.mPosY));
			}
			mFloorplan = PiLocHelper.drawPointsOnTheFloorPlan(paint, pointsToDraw, mFloorplanView, mFloorplan);
		}
	}

	public void onUpdateRadiomapClicked(View v) {
		findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);
		// Start an asyntask to upload the collected data to the server
		new UpdateRadiomapTask().execute(null, null, null);
	}

	public void onLoadRadiomapClicked(View v) {
		findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);
		// Start an asyntask to download radiomap from the server
		new GetRadioMapTask().execute(null, null, null);
		// if(isShowRadiomap)
		// isShowRadiomap =false;
		// else
		isShowRadiomap = true;

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
				mCurrentMappedSteps = null;
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

	private class GetRadioMapTask extends AsyncTask<String, Void, String> {
		protected String doInBackground(String... s) {
			try {
				// Get radio map using the floor ID from server
				String floorID = mFloorIDText.getText().toString();
				mRadiomap = mPilocService.getRadiomap(mServerIP, floorID);
				mRadioDistMap = mPilocService.getRadioDistMap();
				mImpactFactorMap = mPilocService.getRadioImpactFactorMap();
				mImpactMeanMap = mPilocService.getRadioMeanMap();
				mRadioDensityMap = mPilocService.getRadioDensityMap();
				mAPDensityMap = mPilocService.getAPDensityMap();

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
			if (mRadiomap != null) {
				findViewById(R.id.loadingPanel).setVisibility(View.GONE);
				Toast.makeText(getBaseContext(), "Get radiomap successfully", Toast.LENGTH_SHORT).show();
			} else
				Toast.makeText(getBaseContext(), "Get radiomap failed", Toast.LENGTH_SHORT).show();

		}
	}

	private class UpdateRadiomapTask extends AsyncTask<String, Void, String> {
		protected String doInBackground(String... f) {
			try {
				String floorPlanID = mFloorIDText.getText().toString();

				// Append the newly mapped fingerprints to current radiomap
				mPilocService.appendRadiomapFromMapping();

				// Upload the current radiomap to the server
				if (mPilocService.uploadRadiomap(mServerIP, floorPlanID)) {
					// Refresh the image view after uploading the data
					mFloorplan = PiLocHelper.refreshFloorplan(mFloorplanView, mFloorplan, mFloorplanBackup);
					Toast.makeText(getBaseContext(), "Update radiomap successful", Toast.LENGTH_SHORT).show();
				} else {
					Toast.makeText(getBaseContext(), "Update radiomap failed", Toast.LENGTH_SHORT).show();
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