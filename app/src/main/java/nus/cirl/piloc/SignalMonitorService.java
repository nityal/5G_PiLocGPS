package nus.cirl.piloc;

import java.util.List;
import java.util.Vector;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import nus.cirl.piloc.DataStruture.Fingerprint;

public class SignalMonitorService extends Service  {

	private WifiManager mWifiManager = null;
	private BroadcastReceiver mWifiReceiver = null;
	private Boolean mIsCollectingFP = false;



	@Override
	public IBinder onBind(Intent intent) {
		IBinder result = null;
		if (null == result) {
			result = new MyBinder();
		}
		return result;
	}

	public class MyBinder extends Binder {
		public SignalMonitorService getService() {
			// startCollectingFingerprints();
			return SignalMonitorService.this;
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		stopCollectingFingerprints();
	}

	public void startCollectingFingerprints() {

		// By default use wifi only
		mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

		if (!mWifiManager.isWifiEnabled())
			mWifiManager.setWifiEnabled(true);

		new EnableWifiTask().execute(null, null, null);
	}

	private class EnableWifiTask extends AsyncTask<String, Void, String> {
		protected String doInBackground(String... url) {
			while (!mWifiManager.isWifiEnabled()) {
				;
			} // wait for wifi

			IntentFilter wifiIntent = new IntentFilter();
			wifiIntent.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
			mWifiReceiver = new BroadcastReceiver() {
				public void onReceive(Context c, Intent i) {
					mWifiManager.startScan();
				}
			};
			registerReceiver(mWifiReceiver, wifiIntent);
			mWifiManager.startScan();
			mIsCollectingFP = true;

			return null;
		}
	}

	public void stopCollectingFingerprints() {
		if (!mIsCollectingFP)
			return;
		this.unregisterReceiver(mWifiReceiver);
		mIsCollectingFP = false;
	}

	public Vector<Fingerprint> getFingerprint() {

		Vector<Fingerprint> currentFP = new Vector<Fingerprint>();
		List<ScanResult> result = mWifiManager.getScanResults();
		for (ScanResult r : result) {
			currentFP.add(new Fingerprint(r.BSSID, r.level, r.frequency, 0));
		}

		return currentFP;
	}

}
