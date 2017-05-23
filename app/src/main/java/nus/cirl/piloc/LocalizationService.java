package nus.cirl.piloc;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.special.Erf;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import nus.cirl.piloc.DataStruture.BTNeighbor;
import nus.cirl.piloc.DataStruture.FPConf;
import nus.cirl.piloc.DataStruture.Fingerprint;
import nus.cirl.piloc.DataStruture.GaussianParameter;
import nus.cirl.piloc.DataStruture.LocConf;
import nus.cirl.piloc.DataStruture.Radiomap;
import nus.cirl.piloc.DataStruture.StepInfo;
import nus.cirl.piloc.DataStruture.WalkingInfo;

public class LocalizationService extends Service implements SensorEventListener {

	private WifiManager mWifiManager = null;
	private BroadcastReceiver mWifiReceiver = null;
	private BluetoothAdapter mBluetoothAdapter = null;
	// private BroadcastReceiver mBluetoothReceiver = null;
	private HashMap<String, BTNeighbor> mBluetoothScanResult = new HashMap<String, BTNeighbor>();
	private Boolean mIsCollectingFP = false;
	private Boolean mIsCollectionStarted = false;
	private int mInitBTTimer = 2;
	// private DataCollectionService mService = null;
	private SensorManager mSensorManager = null;
	private Radiomap mRadioMap = new Radiomap(null);
	private HashMap<String, Vector<Point>> mRadioDistMap = new HashMap<String, Vector<Point>>();
	private HashMap<String, Vector<Integer>> mRadioMeanDistMap = new HashMap<String, Vector<Integer>>();
	private HashMap<String, Double> mRadioStDevMap = new HashMap<String, Double>();
	private HashMap<String, Integer> mRadioMeanMap = new HashMap<String, Integer>();
	private HashMap<Point, HashMap<String, GaussianParameter>> mRadioGuassionMap = new HashMap<Point, HashMap<String, GaussianParameter>>();
	private HashMap<Point, Integer> mRadioDensityMap = new HashMap<Point, Integer>();
	private HashMap<Point, Integer> mAPDensityMap = new HashMap<Point, Integer>();
	private HashMap<Point, Double> mConfidenceHashMap = new HashMap<Point, Double>();
	private HashMap<Point, HashSet<Point>> mNeighbourMap = new HashMap<Point, HashSet<Point>>();
	private HashMap<String, String> mMacConvertMap = new HashMap<String, String>();
	private Set<Point> TurningPointSet = new HashSet<Point>();
	private ArrayList<Point> statesList = new ArrayList<Point>();
	private double[][] transmitMatrix = null;
	private Vector<WalkingInfo> observation = new Vector<WalkingInfo>();

	private int mWidth = 0;
	private int mHeight = 0;
	private Vector<StepInfo> mSendSteps = new Vector<StepInfo>();
	private Vector<StepInfo> mSteps = null;
	private Vector<StepInfo> mMappedSteps = new Vector<StepInfo>();
	private int[][] mMapInfo = null;
	private Bitmap mCurrentFloorplan = null;
	private FPConf mFPConfig = null;
	private String mServerIP = null;
	private String MacAddr = null;
	private boolean startCoutingStep = false;

	// Step Detection Thresholds and Variables
	double mUpperThresh = 2.5;// 1.2;// 2;//2.5;
	double mLowerThresh = -1.5;// -0.5;// -1.2;//-1.5;
	int mMaxDistThresh = 150;// 600;// 250;//150;
	int mMinDistThresh = 15;// 5;// 10;//15;
	int mCurrentStepCount = 0;
	int mMinStepDistThresh = 15;// 5;// 10;//15;
	int mMaxStepDistThresh = 150;// 600;// 300;//150;
	int mMaxStillDistThresh = 600;// 2000;// 600;
	float mLastUpperPeak = -1;
	float mLastLowerPeak = -1;
	long mLastUpperPeakIndex = -1;
	long mLastLowerPeakIndex = -1;
	long mLastStepIndex = -1;
	long mSampleCount = 0;
	private boolean mIsWalking = false;
	private boolean mIsStepUpdated = false;
	private String mCurrentConfidence = "";
	// Phone orientation
	private float[] mOrientVals = new float[3];
	private float mAngle = 0;
	float mAzimuth = 0;
	float mLastAzimuth = 361;
	float l = 0.5f;
	private int mLastX;
	private int mLastY;
	Point mCurrentLocation = null;
	Point mConfiCurrentLocation = null;

	private float[] mRotationMatrix = new float[16];
	private float[] mLinearVector = new float[4];
	private float[] mWorldAcce = new float[4];
	private float[] mRotationVector = new float[4];
	private float[] mInverseRotationMatrix = new float[16];
	private float[] mMag = new float[3];

	int port = 8080;
	String colorCode = "black";

	private final int cellLength = 1;// 20;

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

	@Override
	public void onSensorChanged(SensorEvent event) {

		switch (event.sensor.getType()) {

		case Sensor.TYPE_ROTATION_VECTOR:
			// Calculate new rotation matrix
			SensorManager.getRotationMatrixFromVector(mRotationMatrix, event.values);

			mRotationVector[0] = event.values[0];
			mRotationVector[1] = event.values[1];
			mRotationVector[2] = event.values[2];

			SensorManager.getOrientation(mRotationMatrix, mOrientVals);
			mAngle = (float) normalizeAngle(mOrientVals[0]);
			mAzimuth = (float) Math.toDegrees(mAngle);
			break;

		case Sensor.TYPE_MAGNETIC_FIELD:
			mMag[0] = event.values[0];
			mMag[1] = event.values[1];
			mMag[2] = event.values[2];
			break;

		case Sensor.TYPE_LINEAR_ACCELERATION:
			// Update rotation matrix, inverted version
			mLinearVector[0] = event.values[0];
			mLinearVector[1] = event.values[1];
			mLinearVector[2] = event.values[2];

			android.opengl.Matrix.invertM(mInverseRotationMatrix, 0, mRotationMatrix, 0);
			android.opengl.Matrix.multiplyMV(mWorldAcce, 0, mInverseRotationMatrix, 0, mLinearVector, 0);

			// Update walking state and step count
			if (startCoutingStep)
				updateStep();

			break;
		}
	}

	public void updateStep() {
		// Increase current sample count
		mSampleCount++;

		if (mSampleCount < 0) {
			mLastUpperPeak = -1;
			mLastLowerPeak = -1;
			mLastUpperPeakIndex = -1;
			mLastLowerPeakIndex = -1;
			mLastStepIndex = -1;
			mSampleCount = 0;
		}

		// If the user is standing still for too much time, reset the walking
		// state
		if (mSampleCount - mLastStepIndex > mMaxStillDistThresh) {
			mIsWalking = false;
		}

		// Detect steps based on zAcc
		if (mWorldAcce[2] > mUpperThresh) {
			mLastUpperPeak = mWorldAcce[2];
			mLastUpperPeakIndex = mSampleCount;

			if (mLastLowerPeakIndex != -1 && mLastUpperPeakIndex - mLastLowerPeakIndex < mMaxDistThresh
					&& mLastUpperPeakIndex - mLastLowerPeakIndex > mMinDistThresh
					&& mSampleCount - mLastStepIndex > mMinStepDistThresh) {
				// In the walking state, new step detected
				if (mIsWalking && startCoutingStep) {
					// Toast.makeText(getBaseContext(),"Step:"+mCurrentStepCount,
					// Toast.LENGTH_SHORT).show();
					mIsStepUpdated = true;

					mCurrentStepCount++;
					mLastStepIndex = mSampleCount;
					// Reset last lower peak for future steps
					mLastLowerPeakIndex = -1;

				} else {
					// Not in the walking state, transit to the walking state if
					// one candidate step detected
					if (mSampleCount - mLastStepIndex < mMaxStepDistThresh) {
						mIsWalking = true;
					}
					mLastStepIndex = mSampleCount;
				}
			}
		} else if (mWorldAcce[2] < mLowerThresh) {
			if (mWorldAcce[2] < mLastLowerPeak || mSampleCount - mLastLowerPeakIndex > mMaxDistThresh) {
				mLastLowerPeak = mWorldAcce[2];
				mLastLowerPeakIndex = mSampleCount;
			}
		}
	}

	public float normalizeAngle(float angle) {
		angle = (float) (angle % (2 * Math.PI));
		return (float) (angle < 0 ? angle + 2 * Math.PI : angle);
	}

	@Override
	public IBinder onBind(Intent intent) {
		IBinder result = null;
		if (null == result) {
			result = new MyBinder();
		}
		return result;
	}

	public class MyBinder extends Binder {
		public LocalizationService getService() {
			// startCollectingFingerprints();
			return LocalizationService.this;
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		stopCollectingFingerprints();
		stopCollection();
	}

	public void clear() {
		mSteps = null;
		mMappedSteps = new Vector<StepInfo>();
	}

	public void setFPConfAndStartColllectingFP(FPConf f) {

		mFPConfig = f;

		if (mIsCollectingFP) {
			stopCollectingFingerprints();
			startCollectingFingerprints();
		} else
			startCollectingFingerprints();
	}

	public void setFPConf(FPConf f) {
		mFPConfig = f;
	}

	public void startCollectingFingerprints() {
		if (mIsCollectingFP)
			return;

		// By default use wifi only
		if (mFPConfig == null || mFPConfig.mIsUseWifi) {
			mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
			if (mWifiManager != null)
				MacAddr = mWifiManager.getConnectionInfo().getMacAddress();
			if (!mWifiManager.isWifiEnabled())
				mWifiManager.setWifiEnabled(true);
		}

		if (mFPConfig != null && mFPConfig.mIsUseBluetooth) {
			BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);// add
																												// by
																												// hande
			mBluetoothAdapter = bluetoothManager.getAdapter();
			if (!mBluetoothAdapter.isEnabled())
				mBluetoothAdapter.enable();
		}

		// mService = this;

		new EnableWifiAndBTTask().execute(null, null, null);
	}

	private class EnableWifiAndBTTask extends AsyncTask<String, Void, String> {
		protected String doInBackground(String... url) {

			if (mFPConfig == null)
				while (!mWifiManager.isWifiEnabled()) {
					;
				} // wait for wifi by default
			else {
				if (mFPConfig.mIsUseBluetooth)
					while (!mBluetoothAdapter.isEnabled()) {
						;
					} // wait for Bluetooth

				if (mFPConfig.mIsUseWifi)
					while (!mWifiManager.isWifiEnabled()) {
						;
					} // wait for wifi
			}

			if (mFPConfig == null || mFPConfig.mIsUseWifi) {
				IntentFilter wifiIntent = new IntentFilter();
				wifiIntent.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
				mWifiReceiver = new BroadcastReceiver() {
					public void onReceive(Context c, Intent i) {
						mWifiManager.startScan();
					}
				};
				registerReceiver(mWifiReceiver, wifiIntent);
				mWifiManager.startScan();
			}

			if (mFPConfig != null && mFPConfig.mIsUseBluetooth) {

				mBluetoothAdapter.startLeScan(mLeScanCallback); // add by hande

				// mBluetoothReceiver = new BroadcastReceiver() {
				// public void onReceive(Context context, Intent intent) {
				// String action = intent.getAction();
				// if (BluetoothDevice.ACTION_FOUND.equals(action))
				// {
				// BluetoothDevice device =
				// intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				//
				// synchronized(mBluetoothScanResult){
				// mBluetoothScanResult.put(device.getAddress(), new
				// BTNeighbor(intent.getShortExtra(BluetoothDevice.EXTRA_RSSI,Short.MIN_VALUE),
				// mInitBTTimer));
				// }
				// }
				// if
				// (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
				// mBluetoothAdapter.startDiscovery();
				// }
				// }
				// };
				// IntentFilter bluetoothIntent = new
				// IntentFilter(BluetoothDevice.ACTION_FOUND);
				// mService.registerReceiver(mBluetoothReceiver,
				// bluetoothIntent);
				// bluetoothIntent = new
				// IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
				// mService.registerReceiver(mBluetoothReceiver,
				// bluetoothIntent);
				// mBluetoothAdapter.startDiscovery();

				// new BTNeighborManagementTask().execute(null , null, null);
				new Thread(new Runnable() {
					public void run() {
						try {
							while (mIsCollectingFP && mFPConfig != null && mFPConfig.mIsUseBluetooth) {
								synchronized (mBluetoothScanResult) {
									Vector<String> keysToBeRemoved = new Vector<String>();
									for (String s : mBluetoothScanResult.keySet()) {
										mBluetoothScanResult.get(s).mTimer = mBluetoothScanResult.get(s).mTimer - 1;
										if (mBluetoothScanResult.get(s).mTimer <= 0)
											keysToBeRemoved.add(s);
									}

									for (String s : keysToBeRemoved) {
										mBluetoothScanResult.remove(s);
									}

								}
								Thread.sleep(5000);
							}
							mBluetoothScanResult.clear();
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}).start();
			}

			mIsCollectingFP = true;

			return null;
		}
	}

	private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
		@Override
		public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
			int startByte = 2;
			boolean patternFound = false;
			// 瀵绘壘ibeaconmRadioDensityMap
			while (startByte <= 5) {
				if (((int) scanRecord[startByte + 2] & 0xff) == 0x02 && // Identifies
																		// an
																		// iBeacon
				((int) scanRecord[startByte + 3] & 0xff) == 0x15) { // Identifies
																	// correct
																	// data
																	// length
					patternFound = true;
					break;
				}
				startByte++;
			}
			// 濡傛灉鎵惧埌浜嗙殑璇�
			if (patternFound) {
				// 杞崲涓�16杩涘埗
				// byte[] uuidBytes = new byte[16];
				// System.arraycopy(scanRecord, startByte + 4, uuidBytes,
				// mRadioStDevMap0, 16);
				// String hexString = bytesToHex(uuidBytes);

				// // ibeacon鐨刄UID鍊�
				// String uuid = hexString.substring(0, 8) + "-"
				// + hexString.substring(8, 12) + "-"
				// + hexString.substring(12, 16) + "-"
				// + hexString.substring(16, 20) + "-"
				// + hexString.substring(20, 32);
				//
				// // ibeacon鐨凪ajor鍊�
				// int major = (scanRecord[startByte + 20] & 0xff) * 0x100
				// + (scanRecord[startByte + 21] & 0xff);
				//
				// // ibeacon鐨凪inor鍊�
				// int minor = (scanRecord[startByte + 22] & 0xff) * 0x100
				// + (scanRecord[startByte + 23] & 0xff);

				// String ibeaconName = device.getName();
				// String mac = device.getAddress();
				// int txPower = (scanRecord[startByte + 24]);

				synchronized (mBluetoothScanResult) {
					mBluetoothScanResult.put(device.getAddress(), new BTNeighbor(rssi, mInitBTTimer));
				}

				// Log.d("BLE",bytesToHex(scanRecord));
				// Log.d("BLE", "Name锛�" + ibeaconName + "\nMac锛�" + mac
				// + " \nUUID锛�" + uuid + "\nMajor锛�" + major + "\nMinor锛�"
				// + minor + "\nTxPower锛�" + txPower + "\nrssi锛�" + rssi);

			}
		}
	};

	public void stopCollectingFingerprints() {
		if (!mIsCollectingFP)
			return;

		if (mFPConfig == null || mFPConfig.mIsUseWifi)
			this.unregisterReceiver(mWifiReceiver);

		if (mFPConfig != null && mFPConfig.mIsUseBluetooth) {
			// this.unregisterReceiver(mBluetoothReceiver);
			if (mBluetoothAdapter != null) {
				// mBluetoothAdapter.cancelDiscovery();
				mBluetoothAdapter.stopLeScan(mLeScanCallback);
				;
			}
		}
		mIsCollectingFP = false;
		// Toast.makeText(getBaseContext(),"Fingerprint collection stopped",
		// Toast.LENGTH_SHORT).show();
	}

	public void startCollection() {
		if (mIsCollectionStarted)
			return;

		mSensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
		mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
				SensorManager.SENSOR_DELAY_FASTEST);
		mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION),
				SensorManager.SENSOR_DELAY_FASTEST);
		mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
				SensorManager.SENSOR_DELAY_FASTEST);

		mIsCollectionStarted = true;

		// // new StepTrackingTask().execute(null , null, null);
		// new Thread(new Runnable() {
		// public void run() {
		// try {
		// while (mIsCollectionStarted) {
		// if (mIsStepUpdated && startCoutingStep) {
		// WalkingInfo tmp;
		// if (mFPConfig == null)
		// tmp = getFingerprint();
		// else
		// tmp = getFingerprint();
		//
		// if (mSteps == null || mSteps.size() == 0) {
		// mLastX = 0;
		// mLastY = 0;
		// mSteps = new Vector<StepInfo>();
		// mSteps.add(new StepInfo((int) ((450 - mAzimuth) % 360), tmp, mLastX,
		// mLastY));
		// mSendSteps.add(new StepInfo((int) ((450 - mAzimuth) % 360), tmp,
		// mLastX, mLastY));
		// } else {
		// mLastX += 10.0 * Math.cos(((450 - mAzimuth) % 360) * 1.0 / 180 *
		// Math.PI);
		// mLastY -= 10.0 * Math.sin(((450 - mAzimuth) % 360) * 1.0 / 180 *
		// Math.PI);
		// mSteps.add(new StepInfo((int) ((450 - mAzimuth) % 360), tmp, mLastX,
		// mLastY));
		// mSendSteps.add(new StepInfo((int) ((450 - mAzimuth) % 360), tmp,
		// mLastX, mLastY));
		//
		// }
		// mIsStepUpdated = false;
		// }
		// }
		//
		// } catch (Exception e) {
		// e.printStackTrace();
		// }
		// }
		// }).start();

		// Toast.makeText(getBaseContext(),"Collection thread started!",
		// Toast.LENGTH_SHORT).show();
	}

	public void stopCollection() {
		if (!mIsCollectionStarted)
			return;

		mIsCollectionStarted = false;
		mSensorManager.unregisterListener(this);
		// Toast.makeText(getBaseContext(),"Collection thread stopped!",
		// Toast.LENGTH_SHORT).show();
	}

	public WalkingInfo getFingerprint() {
		if (!mIsCollectingFP) {
			// Toast.makeText(getBaseContext(),"Fingerprint collection not
			// started!", Toast.LENGTH_SHORT).show();
			return null;
		}

		Vector<Fingerprint> currentFP = new Vector<Fingerprint>();
		HashMap<String, Integer> currentPre = new HashMap<String, Integer>();
		// HashMap<String, Integer> currentfre = new HashMap<String, Integer>();

		// change here to add bluetooth mac and rssi

		if (mFPConfig == null || mFPConfig.mIsUseWifi) {
			List<ScanResult> result = mWifiManager.getScanResults();
			if (mFPConfig.mIsCompressed) {
				for (ScanResult r : result) {
					String convertedmac;
					if (mMacConvertMap.containsKey(r.BSSID)) {
						convertedmac = mMacConvertMap.get(r.BSSID);
					} else {
						convertedmac = r.BSSID;
					}

					if (!currentPre.containsKey(convertedmac)) {
						currentPre.put(convertedmac, 1);
						// currentfre.put(convertedmac, r.frequency);
						currentFP.add(new Fingerprint(convertedmac, Math.abs(r.level), r.frequency, 0));
					} else {
						// if (currentfre.get(convertedmac) == r.frequency) {
						int number = currentPre.get(convertedmac);
						currentPre.put(convertedmac, number + 1);
						for (Fingerprint f : currentFP) {
							if (f.mMac.equals(convertedmac)) {
								f.mRSSI = (f.mRSSI * number + Math.abs(r.level)) / (number + 1);
								break;
							}
						}
						// } else {
						// currentPre.put(r.BSSID, 1);
						// currentfre.put(r.BSSID, r.frequency);
						// currentFP.add(new Fingerprint(r.BSSID,
						// Math.abs(r.level), r.frequency, 0));
						// }
					}
				}
			} else {
				for (ScanResult r : result) {

					String convertedmac = r.BSSID;
					if (!currentPre.containsKey(convertedmac)) {
						currentPre.put(convertedmac, 1);
						// currentfre.put(convertedmac, r.frequency);
						currentFP.add(new Fingerprint(convertedmac, Math.abs(r.level), r.frequency, 0));
					} else {
						int number = currentPre.get(convertedmac);
						currentPre.put(convertedmac, number + 1);
						for (Fingerprint f : currentFP) {
							if (f.mMac.equals(convertedmac)) {
								f.mRSSI = (f.mRSSI * number + Math.abs(r.level)) / (number + 1);
								break;
							}
						}
					}
				}
			}
		}

		if (mFPConfig != null && mFPConfig.mIsUseBluetooth) {
			for (String bmac : mBluetoothScanResult.keySet()) {
				currentFP.add(new Fingerprint(bmac, Math.abs(mBluetoothScanResult.get(bmac).mRSSI), 1));
			}
		}

		if (mFPConfig != null && mFPConfig.mIsUseMag) {
			currentFP.add(new Fingerprint("MAGNETIC_FIELD_X", (int) Math.abs(mMag[0]), 2));
			currentFP.add(new Fingerprint("MAGNETIC_FIELD_Y", (int) Math.abs(mMag[1]), 2));
			currentFP.add(new Fingerprint("MAGNETIC_FIELD_Z", (int) Math.abs(mMag[2]), 2));
		}
		return new WalkingInfo((int) (450 - mAzimuth) % 360, currentFP);
	}

	static boolean isInSameVlan(String mac1, String mac2) {
		if (mac1.substring(0, 16).equals(mac2.substring(0, 16))) {
			char a = mac1.charAt((mac1.length() - 1));
			char b = mac2.charAt((mac2.length() - 1));

			if ((a >= '0' && a < '9' && b >= '0' && b < '9') || (a >= '9' && a <= 'f' && b >= '9' && b <= 'f'))
				return true;
			else
				return false;
		} else
			return false;
	}

	public Bitmap getFloorPlan(String url, String remoteID) {
		try {
			mServerIP = url;
			try {
				String request = "http://" + mServerIP + ":" + port + "/Download?id=" + remoteID + "&type=fp";
				URL imageUrl = new URL(request);
				HttpURLConnection urlConn = (HttpURLConnection) imageUrl.openConnection();
				urlConn.setDoInput(true);
				urlConn.connect();
				InputStream is = urlConn.getInputStream();
				BufferedInputStream bis = new BufferedInputStream(is);
				mCurrentFloorplan = BitmapFactory.decodeStream(bis);
				bis.close();
				is.close();

				mWidth = mCurrentFloorplan.getWidth();
				mHeight = mCurrentFloorplan.getHeight();
				mMapInfo = PiLocHelper.parseMap(mCurrentFloorplan, colorCode);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return mCurrentFloorplan;
	}

	public Radiomap getRadiomap(String url, String remoteID) {
		try {
			mServerIP = url;
			try {
				String request = "http://" + mServerIP + ":" + port + "/Download?id=" + remoteID + "&type=gp";

				URL rmUrl = new URL(request);
				HttpURLConnection urlConn = (HttpURLConnection) rmUrl.openConnection();
				urlConn.setDoInput(true);
				urlConn.connect();
				BufferedReader in = new BufferedReader(new InputStreamReader(urlConn.getInputStream()));

				String readLine = "";
				Vector<String> result = new Vector<String>();
				while ((readLine = in.readLine()) != null)
					result.add(readLine);

				// loadRadiomapFromString(result);
				loadGaussianMapFromString(result);

			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		mMappedSteps.clear();

		return mRadioMap;
	}

	public Point getWeightedLocation(Vector<Fingerprint> fp) {
		if (mRadioGuassionMap == null || fp == null)
			return null;
		mCurrentLocation = WeightedLocalization(fp, mRadioGuassionMap.keySet());
		return mCurrentLocation;
	}

	public Point getRadarLocation(Vector<Fingerprint> fp) {
		if (mRadioGuassionMap == null || fp == null)
			return null;
		mCurrentLocation = Radar(fp, mRadioGuassionMap.keySet());
		return mCurrentLocation;
	}

	public Point getWeightedNeighbourLocation(Vector<Fingerprint> fp) {
		if (mRadioGuassionMap == null || fp == null)
			return null;

		if (mCurrentLocation != null) {
			mCurrentLocation = WeightedLocalization(fp, mNeighbourMap.get(mCurrentLocation));
		} else {
			mCurrentLocation = WeightedLocalization(fp, mRadioGuassionMap.keySet());
		}

		return mCurrentLocation;
	}

	public Point getLocationByConfidencePointBased(Radiomap rm, Vector<Fingerprint> fp) {
		if (rm == null || fp == null)
			return null;

		Point MinKey = new Point(0, 0);
		double sum = 0;
		double maxScore = 0;
		mConfidenceHashMap = new HashMap<Point, Double>();
		HashMap<Point, Double> mTempConfidenceHashMap = new HashMap<Point, Double>();
		// find the closest point by weighted near neighbor
		for (Point k : rm.mLocFingeprints.keySet()) {
			sum = 0;
			double count = 0;
			String detail = "";
			for (Fingerprint f : fp) {
				if (mRadioGuassionMap.get(k).keySet().contains(f.mMac)) {
					GaussianParameter gp = mRadioGuassionMap.get(k).get(f.mMac);
					double impactFactor = mRadioStDevMap.get(f.mMac) + 1;
					double confi = getConfidence(gp.mean, gp.stdev, f.mRSSI);
					detail += " " + confi + "*" + Math.round(impactFactor * 100.0) / 100.0;
					sum += confi * impactFactor;
					count += impactFactor;
				}
			}
			sum /= count;
			// mConfidenceMap
			// +=k.x+"\t\t"+k.y+"\t\t"+Math.round(sum*100.0)/100.0+"\n";
			mTempConfidenceHashMap.put(new Point(k.x, k.y), Math.round(sum * 100.0) / 100.0);
			if (sum > maxScore) {
				maxScore = sum;
				MinKey = new Point(k.x, k.y);
				mCurrentConfidence = detail;
			}
		}

		for (Point p1 : mTempConfidenceHashMap.keySet()) {
			int size = mNeighbourMap.get(p1).size();
			Vector<Point> smoothSet = new Vector<Point>();
			for (Point p2 : mNeighbourMap.get(p1)) {
				smoothSet.add(p2);
			}

			if (size == 0) {
				mConfidenceHashMap.put(p1, mTempConfidenceHashMap.get(p1));
			} else {
				double mean = mTempConfidenceHashMap.get(p1) * 0.5;
				for (Point p2 : mNeighbourMap.get(p1)) {
					mean += mTempConfidenceHashMap.get(p2) * 0.5 / size;
				}
				mean = Math.round(100.0 * mean) / 100.0;
				mConfidenceHashMap.put(p1, mean);
			}
		}

		mCurrentConfidence = Math.round(maxScore * 100.0) / 100.0 + "#" + fp.size();
		mCurrentLocation = MinKey;
		return MinKey;
	}

	public Point getLocationByConfidenceAreaBased(Vector<Fingerprint> fp) {
		if (mRadioGuassionMap == null || fp == null)
			return null;
		mCurrentConfidence = "";
		mConfiCurrentLocation = ConfidenceBasedLocalization(fp, mRadioGuassionMap.keySet());

		HashMap<Point, Double> mTempConfidenceHashMap = mConfidenceHashMap;
		mConfidenceHashMap = new HashMap<Point, Double>();

		// smooth with neighbor
		for (Point p1 : mTempConfidenceHashMap.keySet()) {
			int size = mNeighbourMap.get(p1).size();
			if (size == 0) {
				mConfidenceHashMap.put(p1, mTempConfidenceHashMap.get(p1));
			} else {
				double mean = mTempConfidenceHashMap.get(p1) * 0.5;
				for (Point p2 : mNeighbourMap.get(p1)) {
					mean += mTempConfidenceHashMap.get(p2) * 0.5 / size;
				}
				mean = Math.round(100.0 * mean) / 100.0;
				mConfidenceHashMap.put(p1, mean);
			}
		}
		return mConfiCurrentLocation;
	}

	public Point getLocationByConfidenceAreaBasedNeighbour(Vector<Fingerprint> fp) {
		if (mRadioGuassionMap == null || fp == null)
			return null;

		mCurrentConfidence = "";
		mConfiCurrentLocation = ConfidenceBasedLocalization(fp, mNeighbourMap.get(mConfiCurrentLocation));

		return mConfiCurrentLocation;
	}

	public Point getLocationByConfidenceAreaBasedViterbi(Radiomap rm, WalkingInfo fp) {
		if (rm == null || fp == null)
			return null;

		if (observation.size() < 5)
			observation.add(fp);
		else {
			observation.removeElementAt(0);
			observation.add(fp);
		}
		// initial start states, contain all the cell
		double[][] V = new double[observation.size() + 1][statesList.size()];
		for (int i = 0; i < statesList.size(); i++) {
			V[0][i] = 1;
			V[1][i] = 0.1;
		}

		for (int t = 1; t <= observation.size(); t++) {
			WalkingInfo ifp = observation.get(t - 1);
			int rotateAngle = 0;
			if (t > 1) {
				rotateAngle = Math.abs(observation.get(t - 1).mAngle - observation.get(t - 2).mAngle);
				if (rotateAngle > 180)
					rotateAngle = 360 - rotateAngle;
			}
			Set<Point> PointRange = new HashSet<Point>();
			if (rotateAngle > 70) {
				PointRange = TurningPointSet;
			} else {
				PointRange = rm.mLocFingeprints.keySet();
			}

			HashMap<Point, Vector<Double>> mTempConfidenceVectorHashMap = new HashMap<Point, Vector<Double>>();
			for (Point k : rm.mLocFingeprints.keySet()) {
				mTempConfidenceVectorHashMap.put(k, new Vector<Double>());
			}

			// calculate normalize evidence map
			for (Fingerprint f : ifp.mFingerprints) {
				double sum = 0;
				HashMap<Point, Double> tmpconfiMap = new HashMap<Point, Double>();
				for (Point k : PointRange) {
					if (mRadioGuassionMap.get(k).keySet().contains(f.mMac)) {
						GaussianParameter gp = mRadioGuassionMap.get(k).get(f.mMac);
						double confi = getConfidence(gp.mean, gp.stdev, f.mRSSI);
						tmpconfiMap.put(k, confi);
						sum += confi;
					}
				}
				if (sum > 0) {
					for (Point k : tmpconfiMap.keySet()) {
						mTempConfidenceVectorHashMap.get(k).add(tmpconfiMap.get(k) / sum);
					}
				}
			}
			// calculate all the maximum probability for observation t
			double sum = 0;
			for (int k = 0; k < statesList.size(); k++) {
				double confi = 0;
				for (double a : mTempConfidenceVectorHashMap.get(statesList.get(k))) {
					confi += a;
				}
				// if
				// (mTempConfidenceVectorHashMap.get(statesList.get(k)).size() >
				// 0)
				// confi = confi /
				// mTempConfidenceVectorHashMap.get(statesList.get(k)).size();
				double prob = -1;
				for (int j = 0; j < statesList.size(); j++) {
					double nprob = Math.round(100.0 * (V[t - 1][j] + transmitMatrix[j][k] * confi)) / 100.0;
					if (nprob > prob) {
						prob = nprob;
						V[t][k] = prob;
					}
					sum += nprob;
				}
			}

		}
		double prob = -1;
		Point LocationKey = new Point(0, 0);

		for (int i = 0; i < statesList.size(); i++) {
			mConfidenceHashMap.put(statesList.get(i), V[observation.size()][i]);
			if (V[observation.size()][i] > prob) {
				prob = V[observation.size()][i];
				LocationKey = statesList.get(i);
			}
		}
		mCurrentConfidence = "" + prob;
		return LocationKey;
	}

	public Point ConfidenceBasedLocalization(Vector<Fingerprint> fp, Set<Point> CandidateSet) {
		HashMap<Point, Vector<Double>> mTempConfidenceVectorHashMap = new HashMap<Point, Vector<Double>>();
		mConfidenceHashMap = new HashMap<Point, Double>();
		for (Point k : CandidateSet) {
			mTempConfidenceVectorHashMap.put(k, new Vector<Double>());
		}

		// calculate evidence map
		for (Fingerprint f : fp) {
			double sum = 0;
			HashMap<Point, Double> tmpconfiMap = new HashMap<Point, Double>();
			for (Point k : CandidateSet) {// need to make a decision about
											// calculating from whole map or
											// just from candidate set
				if (mRadioGuassionMap.get(k).keySet().contains(f.mMac)) {
					GaussianParameter gp = mRadioGuassionMap.get(k).get(f.mMac);
					double confi = getConfidence(gp.mean, gp.stdev, f.mRSSI);
					tmpconfiMap.put(k, confi);
					sum += confi;
				}
			}
			for (Point k : tmpconfiMap.keySet()) {
				if (sum > 0)
					mTempConfidenceVectorHashMap.get(k).add(Math.abs(tmpconfiMap.get(k) / sum));
				else
					mTempConfidenceVectorHashMap.get(k).add(0.0); // sum=0
																	// special
																	// case
			}
		}

		// get confidence from all the evidence
		double max = -1.0;
		Point Key = null;
		for (Point k : mTempConfidenceVectorHashMap.keySet()) {
			double confi = 0.0;
			for (double t : mTempConfidenceVectorHashMap.get(k)) {
				if (t >= 0 && t <= 1)
					confi += t;
				else
					mCurrentConfidence += "=.=" + t;
			}
			// confi = confi / mTempConfidenceVectorHashMap.get(k).size();
			mConfidenceHashMap.put(k, Math.round(100.0 * confi) / 100.0);
			if (confi > max) {
				max = confi;
				Key = k;
			}
		}
		return Key;
	}

	public Point WeightedLocalization(Vector<Fingerprint> fp, Set<Point> CandidateSet) {

		Point Key = null;
		double sum = 0;
		double maxScore = 0;

		for (Point k : CandidateSet) {
			int number = 0 ;
			sum = 0;
			for (Fingerprint f2 : fp) {
				if (mRadioGuassionMap.get(k).containsKey(f2.mMac)) {
					number++;
					double diff = 1;
					double impactFactor = 1.0 / mRadioGuassionMap.get(k).get(f2.mMac).mean;
					// double impactFactor = mRadioStDevMap.get(mac);
					if (mRadioGuassionMap.get(k).get(f2.mMac).mean != f2.mRSSI) {
						diff = Math.abs(mRadioGuassionMap.get(k).get(f2.mMac).mean - f2.mRSSI);
						sum += impactFactor * (1.0 / diff);
					} else {
						sum += impactFactor * (1.0);
					}
				}
			}

			if (number > fp.size() / 2 && sum > maxScore) {
				mCurrentConfidence = number + "/" + fp.size();
				maxScore = sum;
				Key = new Point(k.x, k.y);
			}
		}
		return Key;
	}

	public Point Radar(Vector<Fingerprint> fp, Set<Point> CandidateSet) {

		ArrayList<Point> Candidate = new ArrayList<Point>();
		ArrayList<Integer> Sum = new ArrayList<Integer>();

		for (Point k : CandidateSet) {
			int number = 0;
			int sum = 0;
			for (Fingerprint f2 : fp) {
				if (mRadioGuassionMap.get(k).containsKey(f2.mMac)) {
					number++;
					sum += Math.pow(mRadioGuassionMap.get(k).get(f2.mMac).mean - f2.mRSSI, 2);
				}
			}

			if (number > fp.size() / 2) {
				Candidate.add(k);
				Sum.add(sum);
			}
		}

		if (Candidate.size() == 0)
			return null;
		else if (Candidate.size() == 1) {
			mCurrentConfidence = "1 node";
			return Candidate.get(0);
		} else if (Candidate.size() == 2) {
			mCurrentConfidence = "2 node";
			return new Point((Candidate.get(0).x + Candidate.get(1).x) / 2,
					(Candidate.get(0).y + Candidate.get(1).y) / 2);
		} else if (Candidate.size() == 3) {
			mCurrentConfidence = "3 node";
			return new Point((Candidate.get(0).x + Candidate.get(1).x + Candidate.get(2).x) / 3,
					(Candidate.get(0).y + Candidate.get(1).y + Candidate.get(2).y) / 3);
		} else {
			mCurrentConfidence = ">3 node";

			int min1 = Integer.MAX_VALUE;
			int min2 = Integer.MAX_VALUE;
			int min3 = Integer.MAX_VALUE;

			int index1 = -1;
			int index2 = -1;
			int index3 = -1;

			for (int i = 0; i < Candidate.size(); i++) {
				if (Sum.get(i) < min1) {
					min3 = min2;
					min2 = min1;
					min1 = Sum.get(i);
					index3 = index2;
					index2 = index1;
					index1 = i;
				} else if (Sum.get(i) < min2) {
					min3 = min2;
					min2 = Sum.get(i);
					index3 = index2;
					index2 = i;
				} else if (Sum.get(i) < min3) {
					min3 = Sum.get(i);
					index3 = i;
				}
			}

			return new Point((Candidate.get(index1).x + Candidate.get(index2).x + Candidate.get(index3).x) / 3,
					(Candidate.get(index1).y + Candidate.get(index2).y + Candidate.get(index3).y) / 3);
		}
	}

	public String fetchConfidence() {
		return mCurrentConfidence;
	}

	public HashMap<Point, Double> fetchConfidenceMap() {
		return mConfidenceHashMap;
	}

	private double getConfidence(double mean, double stdev, double value) {
		double input = Math.abs((value - mean) / stdev);

		if (stdev == 0 && value == mean)
			return 1;
		else if (stdev == 0 && value != mean)
			return 0;
		else
			return Math.round(2 * (0.5 - 0.5 * Erf.erf(input / Math.sqrt(2))) * 1000.0) / 1000.0; // bug

	}

	public boolean uploadLocation(String serverIP, String remoteID, Point pos) {
		try {

			String requestURL = "http://" + serverIP + ":" + port + "/UpdateLocation";
			String BOUNDARY = "---------";
			String sendString = remoteID + "#" + pos.x + "#" + pos.y + "#" + MacAddr + "\n";
			URL url = new URL(requestURL);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();

			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setUseCaches(false);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("connection", "Keep-Alive");
			conn.setRequestProperty("user-agent", "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1; SV1)");
			conn.setRequestProperty("Charsert", "UTF-8");
			conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);

			OutputStream out = new DataOutputStream(conn.getOutputStream());
			byte[] end_data = ("\r\n--" + BOUNDARY + "--\r\n").getBytes();

			StringBuilder sb = new StringBuilder();
			sb.append("--");
			sb.append(BOUNDARY);
			sb.append("\r\n");
			sb.append("Content-Type:application/octet-stream\r\n\r\n");

			out.write(sendString.getBytes(), 0, sendString.getBytes().length);
			out.flush();
			out.close();

			BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String line = null;
			while ((line = reader.readLine()) != null) {
				if (line.equals("Location updated successfully")) {
					// uploadAllSteps(serverIP, remoteID);
					return true;
				} else
					return false;
			}

		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public Vector<String> getCurrentFloorIDList(String serverIP, Vector<Fingerprint> fp) {
		Vector<String> IDList = new Vector<String>();
		mServerIP = serverIP;

		try {
			String request = "http://" + mServerIP + ":" + port + "/QueryFp?fingerprint=";

			for (Fingerprint f : fp) {
				request += f.mMac + ",";
			}
			request.substring(0, request.length() - 1);

			URL rmUrl = new URL(request);
			HttpURLConnection urlConn = (HttpURLConnection) rmUrl.openConnection();
			urlConn.setDoInput(true);
			urlConn.connect();

			BufferedReader in = new BufferedReader(new InputStreamReader(urlConn.getInputStream()));

			String readLine = "";
			while ((readLine = in.readLine()) != null) {
				String[] tokens = readLine.split(" ");
				for (int i = 0; i < tokens.length; i++) {
					IDList.add(tokens[i]);
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

		return IDList;
	}

	public boolean saveRadiomap(String filePath) {
		try {
			if (mRadioMap == null)
				return false;

			String path = Environment.getExternalStorageDirectory().getAbsolutePath();
			File folder = new File(path + "/PiLoc/");
			if (!folder.exists()) {
				folder.mkdirs();
			}

			File f = new File(path + "/PiLoc/" + filePath);
			FileOutputStream fos = new FileOutputStream(f);
			for (Point p : mRadioMap.mLocFingeprints.keySet()) {
				String writeString = p.x + " " + p.y;
				for (Fingerprint fp : mRadioMap.mLocFingeprints.get(p)) {
					writeString += " " + fp.mMac + " " + fp.mRSSI + " " + fp.mType;
				}
				writeString += "\n";
				fos.write(writeString.getBytes());
			}
			fos.close();
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	public Radiomap loadRadiomapFromString(Vector<String> mapStrings)// modify
																		// by
																		// hande
	{
		try {
			HashMap<Point, Vector<Fingerprint>> locMap = new HashMap<Point, Vector<Fingerprint>>();
			mRadioDistMap = new HashMap<String, Vector<Point>>();
			mRadioMeanDistMap = new HashMap<String, Vector<Integer>>();
			mRadioStDevMap = new HashMap<String, Double>();
			mRadioMeanMap = new HashMap<String, Integer>();
			mRadioGuassionMap = new HashMap<Point, HashMap<String, GaussianParameter>>();
			mRadioDensityMap = new HashMap<Point, Integer>();
			mAPDensityMap = new HashMap<Point, Integer>();
			statesList = new ArrayList<Point>();

			int width = mCurrentFloorplan.getWidth();
			int dis = width / 20 > 100 ? width / 20 : 100;
			dis = (int) 2 * dis;

			for (String line : mapStrings) {
				String[] tokens = line.split(" ");
				Point loc = new Point(Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1]));
				Vector<Fingerprint> fp = new Vector<Fingerprint>();
				HashMap<String, GaussianParameter> gp = new HashMap<String, GaussianParameter>();
				int maxPointNum = 0;
				for (int i = 0; i < (tokens.length - 2) / 2; i++) {

					String[] rssi = tokens[i * 2 + 3].split(",");
					if (maxPointNum < rssi.length)
						maxPointNum = rssi.length;

					int AvgRssi = 0;
					for (int k = 0; k < rssi.length; k++) {
						AvgRssi += Integer.parseInt(rssi[k]);
					}
					AvgRssi /= rssi.length;

					double stdev = 0;
					for (int k = 0; k < rssi.length; k++) {
						stdev += Math.pow((Integer.parseInt(rssi[k]) - AvgRssi), 2);
					}
					stdev = Math.sqrt(stdev / rssi.length);

					gp.put(tokens[i * 2 + 2], new GaussianParameter(AvgRssi, stdev));
					fp.add(new Fingerprint(tokens[i * 2 + 2], AvgRssi, 0));
					if (!mRadioDistMap.containsKey(tokens[i * 2 + 2])) {
						Vector<Point> Points = new Vector<Point>();
						Points.add(loc);
						mRadioDistMap.put(tokens[i * 2 + 2], Points);
						Vector<Integer> Means = new Vector<Integer>();
						Means.add(AvgRssi);
						mRadioMeanDistMap.put(tokens[i * 2 + 2], Means);
					} else {
						mRadioDistMap.get(tokens[i * 2 + 2]).add(loc);
						mRadioMeanDistMap.get(tokens[i * 2 + 2]).add(AvgRssi);
					}

				}
				statesList.add(loc);
				mRadioDensityMap.put(loc, maxPointNum);
				mRadioGuassionMap.put(loc, gp);
				mAPDensityMap.put(loc, gp.size());
				locMap.put(loc, fp);
			}
			mRadioMap.mLocFingeprints = locMap;

			for (String mac : mRadioMeanDistMap.keySet()) {
				mRadioStDevMap.put(mac, (double) getStDev(mRadioMeanDistMap.get(mac)));
				mRadioMeanMap.put(mac, (int) getMeans(mRadioMeanDistMap.get(mac)));
			}

			mNeighbourMap = new HashMap<Point, HashSet<Point>>();
			for (Point p1 : mRadioDensityMap.keySet()) {
				HashSet<Point> mp = new HashSet<Point>();
				for (Point p2 : mRadioDensityMap.keySet()) {
					if ((p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y) < dis * dis) {
						mp.add(p2);
					}
				}
				mNeighbourMap.put(p1, mp);
			}

			transmitMatrix = new double[statesList.size()][statesList.size()];
			for (int i = 0; i < statesList.size(); i++) {
				for (int j = 0; j < statesList.size(); j++) {
					double diff = (statesList.get(i).x - statesList.get(j).x)
							* (statesList.get(i).x - statesList.get(j).x)
							+ (statesList.get(i).y - statesList.get(j).y) * (statesList.get(i).y - statesList.get(j).y);
					if (diff < dis * dis) {
						transmitMatrix[i][j] = 1.0;/// (double)mNeighbourMap.get(statesList.get(i)).size();
					} else
						transmitMatrix[i][j] = 0.2;
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return mRadioMap;
	}

	public Radiomap loadGaussianMapFromString(Vector<String> mapStrings)

	{
		try {
			HashMap<Point, Vector<Fingerprint>> locMap = new HashMap<Point, Vector<Fingerprint>>();
			mRadioDistMap = new HashMap<String, Vector<Point>>(); // coverage
																	// for each
																	// AP
			mRadioMeanDistMap = new HashMap<String, Vector<Integer>>(); // rssi
																		// value
																		// at
																		// all
																		// the
																		// positions
			mRadioStDevMap = new HashMap<String, Double>(); // variance for each
															// MAC over all the
															// map
			mRadioMeanMap = new HashMap<String, Integer>(); // mean for each MAC
															// over all the map

			mRadioGuassionMap = new HashMap<Point, HashMap<String, GaussianParameter>>(); // GaussianParameter
																							// for
																							// each
																							// mac
																							// in
																							// each
																							// position
			mRadioDensityMap = new HashMap<Point, Integer>(); // number of
																// samples
																// collected
			mAPDensityMap = new HashMap<Point, Integer>(); // number of APs
															// collected in each
															// position
			statesList = new ArrayList<Point>();
			mMacConvertMap = new HashMap<String, String>();
			int width = mCurrentFloorplan.getWidth();
			int dis = width / 20 > 80 ? width / 20 : 80;
			dis = (int) 2 * dis;

			for (String line : mapStrings) {
				if (line.startsWith("#")) {
					String[] tokens = line.substring(1).split(" ");
					mMacConvertMap.put(tokens[0], tokens[1]);
				} else if (line.startsWith("$")) {
					String[] tokens = line.substring(1).split(" ");
					TurningPointSet.add(new Point(Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1])));
				} else {
					String[] tokens = line.split(" ");
					Point loc = new Point(Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1]));
					Vector<Fingerprint> fp = new Vector<Fingerprint>();
					HashMap<String, GaussianParameter> gp = new HashMap<String, GaussianParameter>();
					int maxPointNum = 0;
					for (int i = 0; i < (tokens.length - 2) / 2; i++) {
						String mac = tokens[i * 2 + 2];
						String[] gsInfo = tokens[i * 2 + 3].split(",");
						int AvgRssi = Integer.parseInt(gsInfo[0]);
						double stdev = Double.parseDouble(gsInfo[1]);// bonus
						int numberofSample = Integer.parseInt(gsInfo[2]);

						if (maxPointNum < numberofSample)
							maxPointNum = numberofSample;

						gp.put(mac, new GaussianParameter(AvgRssi, stdev + 2.0));
						fp.add(new Fingerprint(mac, AvgRssi, 0));
						if (!mRadioDistMap.containsKey(mac)) {
							Vector<Point> Points = new Vector<Point>();
							Points.add(loc);
							mRadioDistMap.put(mac, Points);
							Vector<Integer> Means = new Vector<Integer>();
							Means.add(AvgRssi);
							mRadioMeanDistMap.put(mac, Means);
						} else {
							mRadioDistMap.get(mac).add(loc);
							mRadioMeanDistMap.get(mac).add(AvgRssi);
						}

					}
					statesList.add(loc);
					mRadioDensityMap.put(loc, maxPointNum);
					mRadioGuassionMap.put(loc, gp);
					mAPDensityMap.put(loc, gp.size());
					locMap.put(loc, fp);
				}

			}
			mRadioMap.mLocFingeprints = locMap;

			for (String mac : mRadioMeanDistMap.keySet()) {
				mRadioStDevMap.put(mac, (double) getStDev(mRadioMeanDistMap.get(mac)));
				mRadioMeanMap.put(mac, (int) getMeans(mRadioMeanDistMap.get(mac)));
			}

			mNeighbourMap = new HashMap<Point, HashSet<Point>>();
			for (Point p1 : mRadioDensityMap.keySet()) {
				HashSet<Point> mp = new HashSet<Point>();
				for (Point p2 : mRadioDensityMap.keySet()) {
					if ((p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y) < dis * dis) {
						mp.add(p2);
					}
				}
				mNeighbourMap.put(p1, mp);
			}

			transmitMatrix = new double[statesList.size()][statesList.size()];
			for (int i = 0; i < statesList.size(); i++) {
				for (int j = 0; j < statesList.size(); j++) {
					double diff = (statesList.get(i).x - statesList.get(j).x)
							* (statesList.get(i).x - statesList.get(j).x)
							+ (statesList.get(i).y - statesList.get(j).y) * (statesList.get(i).y - statesList.get(j).y);
					if (diff < dis * dis) {
						transmitMatrix[i][j] = 1.0;/// (double)mNeighbourMap.get(statesList.get(i)).size();
					} else
						transmitMatrix[i][j] = 0.2;
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return mRadioMap;
	}

	public double getStDev(Vector<Integer> data) {
		double stdev = 0;
		double mean = getMeans(data);

		for (double a : data) {
			stdev += (a - mean) * (a - mean);
		}

		// return (int)(Math.sqrt(stdev/data.size())+1);
		return Math.round((1000.0 * Math.sqrt(stdev / data.size())) + 500) / 1000.0;

	}

	public double getMeans(Vector<Integer> data) {
		double mean = 0;

		for (int a : data) {
			mean += a;
		}

		return mean / (double) data.size();

	}

	public Radiomap loadRadiomap(String filePath) {
		try {
			String path = Environment.getExternalStorageDirectory().getAbsolutePath();
			File folder = new File(path + "/PiLoc/");
			if (!folder.exists()) {
				folder.mkdirs();
			}

			File f = new File(path + "/PiLoc/" + filePath);
			FileInputStream fis = new FileInputStream(f);
			DataInputStream dis = new DataInputStream(fis);

			HashMap<Point, Vector<Fingerprint>> locMap = new HashMap<Point, Vector<Fingerprint>>();
			String line = "";
			while ((line = dis.readLine()) != null) {
				String[] tokens = line.split(" ");
				Point loc = new Point(Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1]));
				Vector<Fingerprint> fp = new Vector<Fingerprint>();
				for (int i = 0; i < (tokens.length - 2) / 3; i++) {
					fp.add(new Fingerprint(tokens[i * 3 + 2], Integer.parseInt(tokens[i * 3 + 3]),
							Integer.parseInt(tokens[i * 3 + 4])));
				}
				locMap.put(loc, fp);
			}
			mRadioMap.mLocFingeprints = locMap;
			dis.close();
			fis.close();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return mRadioMap;
	}

	public void setStartCoutingStep(boolean startCoutingStep) {
		this.startCoutingStep = startCoutingStep;
	}
}
