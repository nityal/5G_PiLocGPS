package nus.cirl.piloc;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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

public class RadioMapConfigService extends Service implements SensorEventListener {

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

	private ArrayList<Point> statesList = new ArrayList<Point>();
	private double[][] transmitMatrix = null;

	private int mWidth = 0;
	private int mHeight = 0;
	private Vector<StepInfo> mSendSteps = new Vector<StepInfo>();
	private Vector<StepInfo> mSteps = null;
	private Vector<StepInfo> mBackupSteps = null;
	private Vector<StepInfo> mNotConfirmedMapping = null;
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

	private String mConfidenceMap = "";

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
		public RadioMapConfigService getService() {
			// startCollectingFingerprints();
			return RadioMapConfigService.this;
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
		mBackupSteps = null;
		mNotConfirmedMapping = null;
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

		// new StepTrackingTask().execute(null , null, null);
		new Thread(new Runnable() {
			public void run() {
				try {
					while (mIsCollectionStarted) {
						if (mIsStepUpdated && startCoutingStep) {
							Vector<Fingerprint> tmp;
							if (mFPConfig == null)
								tmp = getFingerprint();
							else
								tmp = getFingerprint();

							if (mSteps == null || mSteps.size() == 0) {
								mLastX = 0;
								mLastY = 0;
								mSteps = new Vector<StepInfo>();
								mSteps.add(new StepInfo((int) ((450 - mAzimuth) % 360), tmp, mLastX, mLastY));
								mSendSteps.add(new StepInfo((int) ((450 - mAzimuth) % 360), tmp, mLastX, mLastY));
							} else {
								mLastX += 10.0 * Math.cos(((450 - mAzimuth) % 360) * 1.0 / 180 * Math.PI);
								mLastY -= 10.0 * Math.sin(((450 - mAzimuth) % 360) * 1.0 / 180 * Math.PI);
								mSteps.add(new StepInfo((int) ((450 - mAzimuth) % 360), tmp, mLastX, mLastY));
								mSendSteps.add(new StepInfo((int) ((450 - mAzimuth) % 360), tmp, mLastX, mLastY));

							}
							mIsStepUpdated = false;
						}
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();

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

	public Vector<Fingerprint> getFingerprint() {
		if (!mIsCollectingFP) {
			// Toast.makeText(getBaseContext(),"Fingerprint collection not
			// started!", Toast.LENGTH_SHORT).show();
			return null;
		}

		Vector<Fingerprint> currentFP = new Vector<Fingerprint>();
		HashMap<String, Integer> currentPre = new HashMap<String, Integer>();
		HashMap<String, Integer> currentfre = new HashMap<String, Integer>();

		// change here to add bluetooth mac and rssi

		if (mFPConfig == null || mFPConfig.mIsUseWifi) {
			List<ScanResult> result = mWifiManager.getScanResults();
			for (ScanResult r : result) {
				if(r.frequency>0){
//				Fingerprint fp = new Fingerprint( r.BSSID, Math.abs(r.level), 0);
//				fp.setFrequency(r.frequency);
//				currentFP.add(fp); 
				 
				String convertedmac = r.BSSID;
//				char a = r.BSSID.charAt((r.BSSID.length() - 1));
//				if(a >='0' && a<'9')				
//					convertedmac= r.BSSID.substring(0, 16) + "0";
//				else
//					convertedmac= r.BSSID.substring(0, 16) + "f";
				
				if (!currentPre.containsKey(convertedmac)) {
					currentPre.put(convertedmac, 1);
					currentfre.put(convertedmac, r.frequency);
					currentFP.add(new Fingerprint(convertedmac, Math.abs(r.level),r.frequency, 0)); 
				} else {
//					if(currentfre.get(convertedmac) ==r.frequency ){
						int number = currentPre.get(convertedmac);
						currentPre.put(convertedmac, number + 1);
						for (Fingerprint f : currentFP) {
							if (f.mMac.equals(convertedmac)) {
								f.mRSSI = (f.mRSSI * number + Math.abs(r.level)) / (number + 1);
								break;
							}
						}
//					}else{
//						currentPre.put( r.BSSID, 1);
//						currentfre.put( r.BSSID, r.frequency);
//						currentFP.add(new Fingerprint( r.BSSID, Math.abs(r.level),r.frequency, 0)); 
//					}
				}
			}
			}
		}
		
		
		

		if (mFPConfig != null && mFPConfig.mIsUseBluetooth) {
			for (String bmac : mBluetoothScanResult.keySet()) {
				currentFP.add(new Fingerprint(bmac, Math.abs(mBluetoothScanResult.get(bmac).mRSSI),0, 1));
			}
		}

//		if (mFPConfig != null && mFPConfig.mIsUseMag) {
//			currentFP.add(new Fingerprint("MAGNETIC_FIELD_X", (int) Math.abs(mMag[0]), 2));
//			currentFP.add(new Fingerprint("MAGNETIC_FIELD_Y", (int) Math.abs(mMag[1]), 2));
//			currentFP.add(new Fingerprint("MAGNETIC_FIELD_Z", (int) Math.abs(mMag[2]), 2));
//		}

		return currentFP;
	}
	
	static boolean isInSameVlan(String mac1, String mac2){
		if(mac1.substring(0, 16).equals(mac2.substring(0, 16))){
			char a = mac1.charAt((mac1.length() - 1));
			char b = mac2.charAt((mac2.length() - 1));
			
			if((a >='0' && a<'9'&&b>='0'&& b<'9')||(a >='9' && a<='f'&&b>='9'&& b<='f'))
				return true;
			else
				return false;
		}else
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

	public HashMap<String, Vector<Point>> getRadioDistMap() {
		return mRadioDistMap;

	}

	public HashMap<String, Double> getRadioImpactFactorMap() {
		return mRadioStDevMap;
	}

	public HashMap<String, Integer> getRadioMeanMap() {
		return mRadioMeanMap;
	}

	public HashMap<Point, Integer> getAPDensityMap() {
		return mAPDensityMap;
	}

	public HashMap<Point, Integer> getRadioDensityMap() {
		return mRadioDensityMap;
	}

	public Radiomap appendRadiomapFromMapping() {
		if (mCurrentFloorplan == null)
			return null;

		// int rowMax = mCurrentFloorplan.getWidth()/cellLength;
		// int columnMax = mCurrentFloorplan.getHeight()/cellLength;

		HashMap<Point, Vector<Fingerprint>> ID2StepInfo = new HashMap<Point, Vector<Fingerprint>>();

		Point tmpkey = null;
		for (StepInfo s : mMappedSteps) {
			tmpkey = new Point((int) s.mPosX / cellLength, (int) s.mPosY / cellLength);
			if (!ID2StepInfo.containsKey(tmpkey)) {
				Vector<Fingerprint> tmpInfo = new Vector<Fingerprint>();
				for (Fingerprint f : s.mFingerprints) {
					tmpInfo.add(f);
				}
				ID2StepInfo.put(tmpkey, tmpInfo);
			} else {
				for (Fingerprint f : s.mFingerprints) {
					ID2StepInfo.get(tmpkey).add(f);
				}
			}
		}

		HashMap<Point, Vector<Fingerprint>> convertedRadiomap;
		if (mRadioMap.mLocFingeprints == null)
			convertedRadiomap = new HashMap<Point, Vector<Fingerprint>>();
		else
			convertedRadiomap = mRadioMap.mLocFingeprints;

		// combine map information
		for (Point p : ID2StepInfo.keySet()) {
			int x = p.x * cellLength;
			int y = p.y * cellLength;

			Point closestPoint = PiLocHelper.getClickedPoint(mMapInfo, mCurrentFloorplan, x, y);

			if (closestPoint == null)
				continue;

			if (!convertedRadiomap.containsKey(new Point(closestPoint.x, closestPoint.y)))
				convertedRadiomap.put(new Point(closestPoint.x, closestPoint.y), ID2StepInfo.get(p));
			else
				convertedRadiomap.get(new Point(closestPoint.x, closestPoint.y)).addAll(ID2StepInfo.get(p));
		}
		mRadioMap.mLocFingeprints = convertedRadiomap;
		return mRadioMap;
	}

	public Point getDataBaseConfidenceQualityPointBased(Radiomap rm, Point p) {
		if (rm == null || p == null)
			return null;

		Vector<Fingerprint> fp = rm.mLocFingeprints.get(p);

		double sum = 0;
		mConfidenceMap = "";
		mConfidenceHashMap = new HashMap<Point, Double>();
		// find the closest point by weighted near neighbor
		for (Point k : rm.mLocFingeprints.keySet()) {
			sum = 0;
			double count = 0;
			for (Fingerprint f : fp) {
				if (mRadioGuassionMap.get(k).keySet().contains(f.mMac)) {
					GaussianParameter gp = mRadioGuassionMap.get(k).get(f.mMac);
					double impactFactor = mRadioStDevMap.get(f.mMac) + 1;
					double confi = getConfidence(gp.mean, gp.stdev, f.mRSSI);
					sum += confi * impactFactor;
					count += impactFactor;
				}
			}
			sum /= count;
			mConfidenceMap += k.x + "\t\t" + k.y + "\t\t" + Math.round(sum * 100.0) / 100.0 + "\n";
			mConfidenceHashMap.put(new Point(k.x, k.y), Math.round(sum * 100.0) / 100.0);
		}
		mCurrentConfidence = mConfidenceMap;
		return p;
	}

	public Point getDataBaseConfidenceQualityAreaBased(Radiomap rm, Point p) {
		if (mRadioGuassionMap == null || p == null)
			return null;
		mConfidenceMap = "";
		Vector<Fingerprint> fp = rm.mLocFingeprints.get(p);
		ConfidenceBasedLocalization (fp, mRadioGuassionMap.keySet());

		return p;
	}

	public Point ConfidenceBasedLocalization(Vector<Fingerprint> fp, Set <Point > CandidateSet){
		HashMap<Point, Vector<Double>> mTempConfidenceVectorHashMap = new HashMap<Point, Vector<Double>>();
		mConfidenceHashMap = new HashMap<Point, Double>();
		for (Point k :CandidateSet) {
			mTempConfidenceVectorHashMap.put(k, new Vector<Double>());
		}
		
		// calculate evidence map
		for (Fingerprint f : fp) {
			double sum = 0;
			HashMap<Point, Double> tmpconfiMap = new HashMap<Point, Double>();
			for (Point k :CandidateSet) {// need to make a decision about calculating from whole map or just from candidate set
				if (mRadioGuassionMap.get(k).keySet().contains(f.mMac)) {
					GaussianParameter gp = mRadioGuassionMap.get(k).get(f.mMac);
					double confi = getConfidence(gp.mean, gp.stdev, f.mRSSI);
					tmpconfiMap.put(k, confi);
					sum += confi;
				}
			}
			for (Point k : tmpconfiMap.keySet()) {
				if(sum >0)
					mTempConfidenceVectorHashMap.get(k).add(Math.abs(tmpconfiMap.get(k)/sum));
				else
					mTempConfidenceVectorHashMap.get(k).add(0.0); // sum=0 special case
			}
		}
					
		// get confidence from all the evidence
		double max =-1.0;
		Point Key = null;
		for (Point k : mTempConfidenceVectorHashMap.keySet()) {
			double confi = 0.0;
			for (double t : mTempConfidenceVectorHashMap.get(k)) {
				if(t>=0&&t<=1)
					confi += t;
				else
					mCurrentConfidence+="=.="+t;
			}
			confi = confi / mTempConfidenceVectorHashMap.get(k).size();
			mConfidenceHashMap.put(k, Math.round(100.0*confi)/100.0);
			if(confi>max){
				max = confi;
				Key = k;
			}
		}
		return Key;
	}
	
	public Point WeightedLocalization(Vector<Fingerprint> fp, Set <Point > CandidateSet) {

		Point Key = null;
		double sum = 0;
		double maxScore = 0;

		for (Point k :CandidateSet) {
			sum = 0;
			for (String mac : mRadioGuassionMap.get(k).keySet()) {
				for (Fingerprint f2 : fp) {
					if (mac.equals(f2.mMac)) {
						double diff = 1;
//						double impactFactor = 1.0/mRadioGuassionMap.get(k).get(mac).mean;
						double impactFactor = mRadioStDevMap.get(mac) + 1;
						if (mRadioGuassionMap.get(k).get(mac).mean != f2.mRSSI) {
							diff = Math.abs(mRadioGuassionMap.get(k).get(mac).mean - f2.mRSSI);
							sum += impactFactor * (1.0 / diff);
						} else {
							sum += impactFactor * (1.0);
						}
						break;
					}
				}
			}

			if (sum > maxScore) {
				maxScore = sum;
				Key = new Point(k.x, k.y);
			}
		}
		return Key;
	}
	
	//generate samples from distribution
	public Vector<Fingerprint> getSampleFromDistribution ( HashMap<String, GaussianParameter> gpmap){
		Vector<Fingerprint> fp = new  Vector<Fingerprint>();
		
		for(String  mac : gpmap.keySet()  ){
			NormalDistribution nd = new NormalDistribution(gpmap.get(mac).mean,gpmap.get(mac).stdev+3.0 );
			int rssi = (int) nd.sample();
			double prob =  getResponseRate(rssi);
			if(Math.random()<prob)
				fp.add(new Fingerprint(mac, rssi,0) );
		}
	
		return fp ;
	}
	public double getResponseRate(int rssi){
		if(rssi <50)
			return 1.0;
		else if(rssi <60)
			return 0.8;
		else if(rssi <70)
			return 0.6;
		else if(rssi <80)
			return 0.4;
		else if(rssi <90)
			return 0.2;
		else
			return 0.05;
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


	public boolean uploadRadiomapconfig(String serverIP, String remoteID, ArrayList<Point> mNodeConfigMap, boolean isSetTurning) {
		try {
			if (mNodeConfigMap == null || mNodeConfigMap.size() == 0)
				return false;

			// String sendString = ;
			StringBuilder builder = new StringBuilder();
			builder.append(remoteID + "#" + colorCode + "#"); // append more as
																// needed. Color
																// code:
																// ***RED***
			
			for (Point p : mNodeConfigMap) {
				builder.append((int) p.x + " " + (int) p.y);
				builder.append("\n");
			}
			String sendString = builder.toString();
			String requestURL = "http://" + serverIP + ":" + port;
			if(!isSetTurning)
				requestURL += "/UploadRadiomapConfig";
			else
				requestURL += "/UploadTurningConfig";

			String BOUNDARY = "---------";
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
				if (line.equals("config data uploaded successfully")) {
					return true;
				} else
					return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

		return false;

	}

	public Point getClickedPoint(int x, int y) {
		return PiLocHelper.getClickedPoint(mMapInfo, mCurrentFloorplan, x, y);
	}


}
