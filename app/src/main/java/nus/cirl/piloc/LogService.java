package nus.cirl.piloc;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.os.SystemClock;
import android.widget.Toast;

import nus.cirl.piloc.DataStruture.BTNeighbor;
import nus.cirl.piloc.DataStruture.FPConf;
import nus.cirl.piloc.DataStruture.Fingerprint;
import nus.cirl.piloc.DataStruture.StepInfo;

public class LogService extends Service implements SensorEventListener {

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

	// private int mWidth = 0;
	// private int mHeight = 0;
	private Vector<StepInfo> mSendSteps = new Vector<StepInfo>();
	private Vector<StepInfo> mSteps = null;

	private FPConf mFPConfig = null;
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
	String colorCode = "black";

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
			mAngle = normalizeAngle(mOrientVals[0]);
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
		public LogService getService() {
			// startCollectingFingerprints();
			return LogService.this;
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
		//	mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
			mWifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
			if (mWifiManager != null)
				MacAddr = mWifiManager.getConnectionInfo().getMacAddress();
			if (!mWifiManager.isWifiEnabled())
				mWifiManager.setWifiEnabled(true);
			//	mWifiManager.setWifiEnabled(false);
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

			if (mFPConfig == null) {
			//	mWifiManager.setWifiEnabled(false);
				mWifiManager.setWifiEnabled(true);
				while (!mWifiManager.isWifiEnabled()) {
				} // wait for wifi by default
			}
			else {
				if (mFPConfig.mIsUseBluetooth)
					while (!mBluetoothAdapter.isEnabled()) {
					} // wait for Bluetooth

				if (mFPConfig.mIsUseWifi)
				//	while (!mWifiManager.isWifiEnabled()) {
						;
				//	} // wait for wifi
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
		//	Toast.makeText(getBaseContext(),"Fingerprint collection not started!", Toast.LENGTH_SHORT).show();
			return null;
		}
//		Toast.makeText(getBaseContext(),"Fingerprint !", Toast.LENGTH_SHORT).show();
		Vector<Fingerprint> currentFP = new Vector<Fingerprint>();
		HashMap<String, Integer> currentPre = new HashMap<String, Integer>();
		HashMap<String, Integer> currentfre = new HashMap<String, Integer>();

		// change here to add bluetooth mac and rssi

		if (mFPConfig == null || mFPConfig.mIsUseWifi) {
			List<ScanResult> result = mWifiManager.getScanResults();
			if (mFPConfig.mIsCompressed) {
				for (ScanResult r : result) {
					String convertedmac;
					char a = r.BSSID.charAt((r.BSSID.length() - 1));
					if (a >= '0' && a < '9')
						convertedmac = r.BSSID.substring(0, 16) + "0";
					else
						convertedmac = r.BSSID.substring(0, 16) + "f";

					if (!currentPre.containsKey(convertedmac)) {
						currentPre.put(convertedmac, 1);
						currentfre.put(convertedmac, r.frequency);
						currentFP.add(new Fingerprint(convertedmac, Math.abs(r.level), r.frequency, 0));
					} else {
						if (currentfre.get(convertedmac) == r.frequency) {
							int number = currentPre.get(convertedmac);
							currentPre.put(convertedmac, number + 1);
							for (Fingerprint f : currentFP) {
								if (f.mMac.equals(convertedmac)) {
									f.mRSSI = (f.mRSSI * number + Math.abs(r.level)) / (number + 1);
									break;
								}
							}
						} else {
							currentPre.put(r.BSSID, 1);
							currentfre.put(r.BSSID, r.frequency);
							long actualTimeDelay = SystemClock.elapsedRealtime() - (r.timestamp / 1000);
							currentFP.add(new Fingerprint(r.BSSID, Math.abs(r.level), r.frequency, 0, actualTimeDelay, r.SSID));
							//Toast.makeText(getBaseContext(),"Fingerprint collection  started!", Toast.LENGTH_SHORT).show();
						}
					}
				}
			} else {
				for (ScanResult r : result) {
					long actualTimeDelay = SystemClock.elapsedRealtime() - (r.timestamp / 1000);
					currentFP.add(new Fingerprint(r.BSSID, Math.abs(r.level), r.frequency, 0, actualTimeDelay, r.SSID));
				}
			}
		}

		if (mFPConfig != null && mFPConfig.mIsUseBluetooth) {
			for (String bmac : mBluetoothScanResult.keySet()) {
				currentFP.add(new Fingerprint(bmac, Math.abs(mBluetoothScanResult.get(bmac).mRSSI), 1));
			}
		}
		mFPConfig.mIsUseMag= false;
		if (mFPConfig != null && mFPConfig.mIsUseMag) {
			currentFP.add(new Fingerprint("MAGNETIC_FIELD_X", (int) Math.abs(mMag[0]), 2));
			currentFP.add(new Fingerprint("MAGNETIC_FIELD_Y", (int) Math.abs(mMag[1]), 2));
			currentFP.add(new Fingerprint("MAGNETIC_FIELD_Z", (int) Math.abs(mMag[2]), 2));
		}

		return currentFP;
	}

	static boolean isInSameVlan(String mac1, String mac2) {
		if (mac1.substring(0, 16).equals(mac2.substring(0, 16))) {
			char a = mac1.charAt((mac1.length() - 1));
			char b = mac2.charAt((mac2.length() - 1));

			return (a >= '0' && a < '9' && b >= '0' && b < '9') || (a >= '9' && a <= 'f' && b >= '9' && b <= 'f');
		} else
			return false;
	}

	public void removeCollectedDataInSDCard(String filePath) {
		try {
			File file = new File(filePath);
			file.delete();
		} catch (Exception e) {
			e.printStackTrace();
		}
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

	public void setStartCoutingStep(boolean startCoutingStep) {
		this.startCoutingStep = startCoutingStep;
	}
}
