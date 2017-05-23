package nus.cirlab.menu;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Vector;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import nus.cirl.piloc.DataStruture.FPConf;
import nus.cirl.piloc.DataStruture.Fingerprint;
import nus.cirl.piloc.LogService;
import java.util.Date;

import static java.sql.Types.TIMESTAMP;

public class LogActivity extends Activity {

	private LogService mPilocService = null;

	private Boolean mIsUpdatingFPToUI = false;

	private String mCurrentFPString = "";
	private CheckBox WiFiCheckBox, BluetoothCheckBox, MagCheckBox, FingerprintDensity;
	private boolean mIsUseWiFi = true;
	private boolean mIsUseBluetooth = false;
	private boolean mIsUseMagntic = true;
	private boolean mIsCompressed = false;

	private DataOutputStream logWriter = null;
	int logcounter = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.log);

		// Create and bind the PiLoc service
		Intent intent = new Intent(LogActivity.this, LogService.class);
		this.getApplicationContext().bindService(intent, conn, Context.BIND_AUTO_CREATE);

		String path = Environment.getExternalStorageDirectory().getAbsolutePath();
		File folder = new File(path + "/ConfidenceLog/");
		if (!folder.exists()) {
			folder.mkdirs();
		}
		File f = new File(path + "/ConfidenceLog/Confi" + logcounter + ".txt");
		while (f.exists()) {
			logcounter++;
			f = new File(path + "/ConfidenceLog/Confi" + logcounter + ".txt");
		}
		try {
			f.createNewFile();
			FileOutputStream fos = new FileOutputStream(f);
			logWriter = new DataOutputStream(fos);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

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
					mIsCompressed = false;
				else
					mIsCompressed = true;
			} else if (mconf == 3) {
				if (isChecked)
					mIsUseMagntic = true;
				else
					mIsUseMagntic = false;
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
		// Stop updating fingerprint
		mIsUpdatingFPToUI = false;
		if (logWriter != null)
			try {
				logWriter.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}

	private ServiceConnection conn = new ServiceConnection() {
		public void onServiceDisconnected(ComponentName name) {
			mPilocService.onDestroy();
		}

		public void onServiceConnected(ComponentName name, IBinder service) {
			LogService.MyBinder binder = (LogService.MyBinder) service;
			mPilocService = binder.getService();
			// Start collecting only WiFi fingerprints

			mPilocService.setFPConfAndStartColllectingFP(new FPConf(mIsUseWiFi, mIsUseBluetooth, mIsUseMagntic,mIsCompressed)); // also
			// collect
			// bluetooth
			// Start data collection service
			mPilocService.startCollection();
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
						SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
						String currentDateandTime = sdf.format(new Date());
						if (fpList != null) {
							// Construct fingerprint string
							String fpString = "";
							for (int i = 0; i < fpList.size(); i++) {
								fpString += fpList.get(i).mMac + "\t" + fpList.get(i).mFrequency + " "
										+ -fpList.get(i).mRSSI + " "+fpList.get(i).timestamp/1000.0+" "+currentDateandTime+" "+fpList.get(i).ssid+ "\n";
							}
							mCurrentFPString = fpString;
							logWriter.write((mCurrentFPString + "\n").getBytes());
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
						Thread.sleep(1400);
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();

	}

	public  String getDateCurrentTimeZone(long timestamp) {
		try{
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
			String dateString = formatter.format(new Date(timestamp));
			return dateString;
		}catch (Exception e) {
		}
		return "";
	}

}
