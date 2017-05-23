package nus.cirlab.menu;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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
import android.os.Environment;
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
import nus.cirl.piloc.DataStruture.FingerprintwithDuplicate;
import nus.cirl.piloc.DataStruture.Radiomap;
import nus.cirl.piloc.ErrorService;
import nus.cirlab.menu.LogActivity.CheckBoxListener;
import nus.cirl.piloc.PiLocHelper;

public class ErrorActivity extends Activity {

	private final String mServerIP = "piloc.d1.comp.nus.edu.sg";//

	private ErrorService mPilocService = null;
	private Radiomap mRadiomap = null;
	private Bitmap mFloorplan = null;
	private Bitmap mFloorplanBackup = null;
	private ImageView mFloorplanView = null;
	private EditText mFloorIDText = null;

	private Boolean mIsLocating = false;

	Vector<Point> mCurrentLocationToPaint = new Vector<Point>();
	Point mCurrentLocation = null;
	Point mConfiCurrentLocation = null;
	Point mLastLocation = null;
	long lastTime = 0;
	private Button mLocalizationButton;
	private CheckBox WiFiCheckBox, BluetoothCheckBox, MagCheckBox, FingerprintDensity;
	private boolean mIsUseWiFi = true;
	private boolean mIsUseBluetooth = false;
	private boolean mIsUseMagntic = false;
	private boolean mIsCompressed = true;
	private boolean isShowError = false;
	private Point testPoint = new Point();
	private ArrayList<Double> errorList = new ArrayList<Double>();
	// int count = 0;
	private double mXScale;
	private double mYScale;
	ArrayList<Point> mNodeConfigMap = null;

	private boolean isUploadingLocation = false;
	private String mCurrentConfidenceValue = "";
	private HashMap<Point, Double> mCurrentConfidenceMap = new HashMap<Point, Double>();

	int AlgorithmOption = 0;
	private Vector<Vector<Fingerprint>> FPHistoryWindows = new Vector<Vector<Fingerprint>>();
	private Vector<Fingerprint> showfp = new Vector<Fingerprint>();
	
	private DataOutputStream logWriter = null;
	int logcounter = 0;
	int writeCounter =0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.error);

		// Create and bind the PiLoc service
		Intent intent = new Intent(ErrorActivity.this, ErrorService.class);
		this.getApplicationContext().bindService(intent, conn, Context.BIND_AUTO_CREATE);
		
		
		String path = Environment.getExternalStorageDirectory().getAbsolutePath();
		File folder = new File(path + "/ErrorLog/");
		if (!folder.exists()) {
			folder.mkdirs();
		}
		File f = new File(path + "/ErrorLog/Node" + logcounter + ".txt");
		while (f.exists()) {
			logcounter++;
			f = new File(path + "/ErrorLog/Node" + logcounter + ".txt");
		}
		try {
			f.createNewFile();
			FileOutputStream fos = new FileOutputStream(f);
			logWriter = new DataOutputStream(fos);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		

		// Initialize the map view
		mFloorplanView = (ImageView) findViewById(R.id.floorplanView);
		mFloorplanView.setOnTouchListener(touchListener);
		mNodeConfigMap = new ArrayList<Point>();

		mFloorIDText = (EditText) findViewById(R.id.remoteIDEditText);
		mLocalizationButton = (Button) findViewById(R.id.startLocalizationButton);
		mLocalizationButton.setTag(0);// not localize default
		mLocalizationButton.setText("Loc is Off");
		mLocalizationButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				final int status = (Integer) v.getTag();
				if (status == 1) {
					mIsLocating = false;
					mLocalizationButton.setText("Loc is Off");
					// mPilocService.setLocationState(mIsLocating);
					v.setTag(0);
				} else {
					// Localization started already, return immediately
					if (mIsLocating) {
						return;
					}
					if (mFloorplan == null) {
						String floorPlanID = mFloorIDText.getText().toString();
						// Show loading dialog
						findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);

						// Create an asyntask to get the floor plan with entered
						// floorPlanID
						new GetFloorPlanTask().execute(floorPlanID, null, null);
					}

					// If no radio map is download, start an Asynctask to
					// download radiomap first
					if (mRadiomap == null) {
						findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);
						new GetRadioMapTask().execute(null, null, null);

					}

					// Start a new thread to continuously update the current
					// location on the map

					new Thread(new Runnable() {
						public void run() {
							try {
								mIsLocating = true;
								// mPilocService.setLocationState(mIsLocating);
								while (mIsLocating) {
									// Get current fingerprints
									Vector<Fingerprint> sfp = mPilocService.getFingerprint();

									if (FPHistoryWindows.size() < 3) {
										FPHistoryWindows.add(sfp);
									} else {
										FPHistoryWindows.remove(0);
										FPHistoryWindows.add(sfp);
									}

									Vector<Fingerprint> fp = getSlideFP(FPHistoryWindows);

									// Find current location using the
									// fingerprints
									// String floorPlanID =
									// mFloorIDText.getText().toString();
									// long time = System.currentTimeMillis();
									mCurrentLocation = null;
									mConfiCurrentLocation = null;
									// AlgorithmOption = 1;// hande
									if (AlgorithmOption == 0) {
										mCurrentLocation = mPilocService.getWeightedLocation(fp, isShowError,
												testPoint);
									}
									if (AlgorithmOption == 1) {
										mCurrentLocation = mPilocService.getLocationByConfidenceAreaBased(fp);
									}
									if (AlgorithmOption == 2) {
										mCurrentLocation = mPilocService.getRadarLocation(fp);
									}
									if (AlgorithmOption == 4) {
										mCurrentLocation = mPilocService.getWeightedNeighbourLocation(fp);
									}
									if (AlgorithmOption == 5) {
										mCurrentLocation = mPilocService.getLocationByConfidenceAreaBasedNeighbour(fp);
									}
									if (AlgorithmOption == 6) {
										mCurrentLocation = mPilocService
												.getLocationByConfidenceAreaBasedViterbi(mRadiomap, fp);
									}

									// Sleep while the radiomap is loading and
									// no location is returned
									if (mCurrentLocation == null && mConfiCurrentLocation == null) {
										Thread.sleep(1000);
										continue;
									} else {
										runOnUiThread(new Runnable() {
											@Override
											public void run() {
												Paint paint = new Paint();
												paint.setColor(Color.RED);
												if (AlgorithmOption < 7) {

													double scale = 0.07;// lab
													mFloorplan = PiLocHelper.drawPointOnTheOriginalFloorPlanWithError(
															paint, mCurrentLocation, testPoint, mFloorplanView,
															mFloorplan, mFloorplanBackup);
													if (isShowError && testPoint!=null) {
														double error = Math.round(
																100.0 * Math.sqrt((mCurrentLocation.x - testPoint.x)
																		* (mCurrentLocation.x - testPoint.x)
																		+ (mCurrentLocation.y - testPoint.y)
																				* (mCurrentLocation.y - testPoint.y))
																		* scale)
																/ 100.0;
														TextView tv = (TextView) findViewById(R.id.fingerprintTextView);
														try {
												
															if(writeCounter<=50){
																logWriter.write((testPoint.x+"\t"+testPoint.y+"\t"+mCurrentLocation.x+"\t"+mCurrentLocation.y+"\t"+error+"\n")
																		.getBytes());
																writeCounter++;
																tv.setText(tv.getText().toString() + error + ", ");
															}else
															{
																logWriter.close();
																tv.setText("Done");
																
															}
															
															
														} catch (IOException e) {
															// TODO Auto-generated catch block
															e.printStackTrace();
														}
//														tv.setText(tv.getText().toString() + error + ", ");
//														errorList.add(error);
														// count++;

//														if (errorList.size() % 50 == 0) {
//															tv.setText(tv.getText() + "\n");
//															double sum = 0;
//															for (double er : errorList) {
//																sum += er;
//															}
//															sum = Math.round(100.0 * sum / 50.0) / 100.0;
//															tv.setText(tv.getText().toString() + "=====" + sum + "\n");
//															errorList.clear();
//															// count = 0;
//														}
													}
												}

											}
										});
										Thread.sleep(2000);
									}
								}
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}).start();

					mLocalizationButton.setText("Loc is On");
					v.setTag(1);
				}
			}
		});

		WiFiCheckBox = (CheckBox) findViewById(R.id.WiFicheckBox);
		BluetoothCheckBox = (CheckBox) findViewById(R.id.BluetoothcheckBox);
		MagCheckBox = (CheckBox) findViewById(R.id.MagneticcheckBox);
		FingerprintDensity = (CheckBox) findViewById(R.id.FPcheckBox);

		WiFiCheckBox.setOnCheckedChangeListener(new CheckBoxListener(0));
		BluetoothCheckBox.setOnCheckedChangeListener(new CheckBoxListener(1));
		MagCheckBox.setOnCheckedChangeListener(new CheckBoxListener(2));
		FingerprintDensity.setOnCheckedChangeListener(new CheckBoxListener(3));

		findViewById(R.id.loadingPanel).setVisibility(View.GONE);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;

	}

	private Vector<Fingerprint> getSlideFP(Vector<Vector<Fingerprint>> fpss) {
		Vector<FingerprintwithDuplicate> showfpd = null;
		for (Vector<Fingerprint> fps : fpss) {
			for (Fingerprint fp : fps) {
				if (showfpd == null) {
					showfpd = new Vector<FingerprintwithDuplicate>();
					showfpd.add(new FingerprintwithDuplicate(fp.mMac, fp.mRSSI, fp.mType));
				} else {
					boolean found = false;
					for (FingerprintwithDuplicate fpd : showfpd) {
						if (fpd.mMac.equals(fp.mMac)) {
							fpd.mRSSI.add(fp.mRSSI);
							found = true;
						}

						break;
					}
					if (!found) {
						showfpd.add(new FingerprintwithDuplicate(fp.mMac, fp.mRSSI, fp.mType));
					}
				}

			}
		}

		showfp = new Vector<Fingerprint>();

		for (FingerprintwithDuplicate fpd : showfpd) {
			if (fpd.mRSSI.size() == 1) {
				showfp.add(new Fingerprint(fpd.mMac, fpd.mRSSI.get(0), fpd.mType));
			} else if (fpd.mRSSI.size() == 2) {
				showfp.add(new Fingerprint(fpd.mMac, (fpd.mRSSI.get(0) + fpd.mRSSI.get(1)) / 2, fpd.mType));
			} else if (fpd.mRSSI.size() == 3) {
				showfp.add(new Fingerprint(fpd.mMac, (fpd.mRSSI.get(0) + fpd.mRSSI.get(1) + fpd.mRSSI.get(2)) / 3,
						fpd.mType));
			}
		}

		return showfp;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		menu.clear();
		if (mIsLocating) {
			menu.add(0, 0, Menu.NONE, "PiLoc");
			menu.add(0, 1, Menu.NONE, "Horus");
			menu.add(0, 2, Menu.NONE, "Radar");
			// menu.add(0, 3, Menu.NONE, "Confi Map");
			menu.add(0, 4, Menu.NONE, "PiLocNeigh");
			menu.add(0, 5, Menu.NONE, "HorusNeigh");
			menu.add(0, 6, Menu.NONE, "5-Viterbi");
		}
		return super.onPrepareOptionsMenu(menu);
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

				if (mIsLocating) {
					// Get the nearest walking path point on the map
					testPoint = mPilocService.getClickedPoint(x, y);
					// Cannot get the clicked point
					if (testPoint == null) {
						return false;
					}

					isShowError = true;

					TextView tv = (TextView) findViewById(R.id.fingerprintTextView);
					tv.setText("");
					// count = 0;
					errorList.clear();

					if (mNodeConfigMap.size() > 0) {
						// Update bitmap, set all previous mapped points to gray
						// color
						mPaint.setColor(Color.YELLOW);
						Vector<Point> pointsToDraw = new Vector<Point>();
						pointsToDraw.add(testPoint);

						for (Point s : mNodeConfigMap) {
							pointsToDraw.add(new Point((int) s.x, (int) s.y));
						}

						mFloorplan = PiLocHelper.drawPointsOnTheFloorPlan(mPaint, pointsToDraw, mFloorplanView,
								mFloorplan);

					}
					// Set current click point to yellow color
					mPaint.setColor(Color.GREEN);
					Vector<Point> pointsToDraw = new Vector<Point>();
					pointsToDraw.add(testPoint);
					mNodeConfigMap.add(testPoint);
					mFloorplan = PiLocHelper.drawPointsOnTheFloorPlan(mPaint, pointsToDraw, mFloorplanView, mFloorplan);
				}

				break;

			default:
				break;
			}
			return true;
		}
	};

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		if (mIsLocating) {
			AlgorithmOption = item.getItemId();

			TextView tv = (TextView) findViewById(R.id.fingerprintTextView);
			tv.setText("");
			// count = 0;
			errorList.clear();
		}

		return super.onOptionsItemSelected(item);
	}

	// @Override
	// public boolean onPrepareOptionsMenu(Menu menu) {
	// getMenuInflater().inflate(R.menu.main, menu);
	// menu.clear();
	//
	// int i = 0;
	// menu.add(0, i, Menu.NONE, "ALL");
	// if (mIsLocating) {
	// for (Point p : mRadiomap.mLocFingeprints.keySet()) {
	// menu.add(0, i, Menu.NONE, p.x + " " + p.y);
	// }
	// }
	//
	// return super.onPrepareOptionsMenu(menu);
	// }

	// @Override
	// public boolean onOptionsItemSelected(MenuItem item) {
	// String pointinfo = (String) item.getTitle();
	// if (mIsLocating) {
	// String[] Stringsets = pointinfo.split(" ");
	// testPoint = new Point(Integer.parseInt(Stringsets[0]),
	// Integer.parseInt(Stringsets[1]));
	// isShowError = true;
	//
	// TextView tv = (TextView) findViewById(R.id.fingerprintTextView);
	// tv.setText("");
	// count = 0;
	// errorList.clear();
	// }
	// return super.onOptionsItemSelected(item);
	// }
	//

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
					mIsUseMagntic = true;
				else
					mIsUseMagntic = false;
			} else if (mconf == 3) {
				if (isChecked)
					mIsCompressed = false;
				else
					mIsCompressed = true;
			}
			mPilocService.setFPConf(new FPConf(mIsUseWiFi, mIsUseBluetooth, mIsUseMagntic, mIsCompressed));
		}
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
		// Stop all localization threads
		mIsLocating = false;
		// Stop updating fingerprint
	}

	private ServiceConnection conn = new ServiceConnection() {
		public void onServiceDisconnected(ComponentName name) {
			mPilocService.onDestroy();
		}

		public void onServiceConnected(ComponentName name, IBinder service) {
			ErrorService.MyBinder binder = (ErrorService.MyBinder) service;
			mPilocService = binder.getService();
			// Start collecting only WiFi fingerprints

			mPilocService.setFPConfAndStartColllectingFP(
					new FPConf(mIsUseWiFi, mIsUseBluetooth, mIsUseMagntic, mIsCompressed)); // also
			// collect
			// bluetooth
			// Start data collection service
			mPilocService.startCollection();
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

		// Stop localization when switching to new floor plans
		if (mIsLocating)
			mIsLocating = false;

		// Show loading dialog
		findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);

		// Create an asyntask to get the floor plan with entered floorPlanID
		new GetFloorPlanTask().execute(floorPlanID, null, null);
	}

	public void onSetYourLocationClicked(View v) {
		if (isShowError)
			isShowError = false;
		else
			isShowError = true;

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

}
