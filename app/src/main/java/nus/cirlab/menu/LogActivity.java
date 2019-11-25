package nus.cirlab.menu;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Vector;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.GpsStatus;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.ActivityCompat;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import nus.cirl.piloc.DataStruture.FPConf;
import nus.cirl.piloc.DataStruture.Fingerprint;
import nus.cirl.piloc.LogService;
import java.util.Date;

import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.content.ContentResolver;
import android.app.AlertDialog;
import android.content.DialogInterface;

import android.provider.Settings;

import static android.content.ContentValues.TAG;
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


	private Spinner spinner1, spinner2;


	// Location GPS
	protected LocationManager locationManager;
	protected LocationListener locationListener;
	private String Latitude = "0,0" ;

	boolean running = true;

	private DataOutputStream logWriter = null;
	int logcounter = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.log);

		// Create and bind the service
		Intent intent = new Intent(LogActivity.this, LogService.class);
		this.getApplicationContext().bindService(intent, conn, Context.BIND_AUTO_CREATE);

		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);


		//GPS location
		Boolean flag = false;
		flag = displayGpsStatus();

		if (flag) {

			locationListener = new MyLocationListener();
			if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
					&& ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

				//	alertbox("Gps Status!!", "Your GPS is: On");

				//Exception thrown when GPS or Network provider were not available on the user's device.
				try {
					Criteria criteria = new Criteria();
					criteria.setAccuracy(Criteria.ACCURACY_FINE);
					criteria.setPowerRequirement(Criteria.POWER_HIGH);
					criteria.setAltitudeRequired(false);
					criteria.setSpeedRequired(true);
					criteria.setCostAllowed(true);
					criteria.setBearingRequired(false);

					//API level 9 and up
					criteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
					criteria.setVerticalAccuracy(Criteria.ACCURACY_HIGH);

					String bestProvider = locationManager.getBestProvider(criteria, false);

					locationManager.requestLocationUpdates(bestProvider, 0, 0, locationListener);

				} catch (IllegalArgumentException e) {
					//	Log.e(LOG_TAG, e.getLocalizedMessage());
				} catch (SecurityException e) {
					//	Log.e(LOG_TAG, e.getLocalizedMessage());
				} catch (RuntimeException e) {
					//	Log.e(LOG_TAG, e.getLocalizedMessage());
				}

			}
			else {
				alertbox("Gps Status!!", "Your GPS is : OFF");
				onDestroy();
			}

		} else {
			alertbox("Gps Status!!", "Your GPS is : OFF");
		}

		TextView T= findViewById(R.id.GPSTextView);
		if(Latitude.equals("0,0"))
		{

			//T.setText("GPS  NOT LOCKED");
			//T.setText("NO GPS  LOCKED");
			/*
			Button btn2 = (Button) findViewById(R.id.fingerprintButton);
			btn2.setEnabled(false);
			Button btn1 = (Button) findViewById(R.id.StopfingerprintButton);
			btn1.setEnabled(false);
			Button btn3 = (Button) findViewById(R.id.LocationButton);
			btn3.setEnabled(false);

			 */

		}


		findViewById(R.id.loadingPanel).setVisibility(View.GONE);
		addListenerOnSpinnerItemSelection();


	}

	public void addListenerOnSpinnerItemSelection() {
		spinner1 = findViewById(R.id.spinner1);

	}



	class CheckBoxListener implements OnCheckedChangeListener {
		int mconf;

		CheckBoxListener(int conf) {
			mconf = conf;
		}

		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			if (mconf == 0) {
                mIsUseWiFi = isChecked;
			} else if (mconf == 1) {
                mIsUseBluetooth = isChecked;
			} else if (mconf == 2) {
                mIsCompressed = !isChecked;
			} else if (mconf == 3) {
                mIsUseMagntic = isChecked;
			}
		//	mPilocService.setFPConf(new FPConf(mIsUseWiFi, mIsUseBluetooth, mIsUseMagntic, mIsCompressed));
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


	//	locationManager.removeUpdates(locationListener);
	//	locationListener = null;

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
		//
			//	mPilocService.startCollection();
		}
	};


	////////////////////////// Location GPS   //////////////////////////////


	/*----------Listener class to get coordinates ------------- */
	private class MyLocationListener implements LocationListener {


		@Override
		public void onLocationChanged(Location loc) {

			Latitude =  loc.getLatitude() + "," + loc.getLongitude();


			if(Latitude.equals("0,0"))
			{
				TextView T= findViewById(R.id.GPSTextView);
				T.setText("GPS LOCKED");
				Button btn2 = findViewById(R.id.fingerprintButton);
				btn2.setEnabled(true);
				Button btn1 = findViewById(R.id.StopfingerprintButton);
				btn1.setEnabled(true);
				Button btn3 = findViewById(R.id.LocationButton);
				btn3.setEnabled(true);
			}



		}

		@Override
		public void onProviderDisabled(String provider) {
			// TODO Auto-generated method stub
		}

		@Override
		public void onProviderEnabled(String provider) {
			// TODO Auto-generated method stub
		}

		@Override
		public void onStatusChanged(String provider,
									int status, Bundle extras) {
			// TODO Auto-generated method stub
		}


	}

	/*----Method to Check GPS is enable or disable ----- */
	private Boolean displayGpsStatus() {
		ContentResolver contentResolver = getBaseContext().getContentResolver();
		//  @SuppressWarnings("deprecation")
		boolean gpsStatus = Settings.Secure.isLocationProviderEnabled(contentResolver,LocationManager.GPS_PROVIDER);
        return gpsStatus;
	}

	/*----------Method to create an AlertBox ------------- */

	protected void alertbox(String title, String mymessage) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(mymessage)
				.setCancelable(false)
				.setTitle(title)
				.setPositiveButton("Switch On",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								// finish the current activity
								Intent myIntent = new Intent(
										Settings.ACTION_APPLICATION_SETTINGS);
								startActivity(myIntent);
								dialog.cancel();
							}
						})
				.setNegativeButton("Cancel",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								// cancel the dialog box
								dialog.cancel();
							}
						});
		AlertDialog alert = builder.create();
		alert.show();
	}


	/* .. alert for storage */

	public  boolean isWriteStoragePermissionGranted() {

        return ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
	}

	public void onStopFPBtnClicked(View v) {

		running=false;
	//	mIsUpdatingFPToUI =false;
		mPilocService.stopCollectingFingerprints();
		Button btn = findViewById(R.id.fingerprintButton);
		btn.setEnabled(false);
	}


		///////////////////////// Location GPS   //////////////////////////////

	public void onLocBtnClicked(View v) {


		String state;

		TextView T= findViewById(R.id.FileTextView);
		mPilocService.setFPConfAndStartColllectingFP(new FPConf(mIsUseWiFi, mIsUseBluetooth, mIsUseMagntic,mIsCompressed)); // also

		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
		{
			state = "true";
		}
		else
		{
			state = "false";
		}

		if (state.equals("false")) {
			alertbox("Storage Status!!", "Your storage is: OFF");
			onDestroy();
		}


		String Loc = String.valueOf(spinner1.getSelectedItem());
		String result="";
		EditText LOCText = findViewById(R.id.LocationEditText);
		String round = LOCText.getText().toString();

		if(round.equals("") || Loc.equals(" ")) {
			//T = (TextView)findViewById(R.id.FileTextView);
			T.setText("Enter correct filename");
		}

		else {

			Button btn = findViewById(R.id.fingerprintButton);
			btn.setEnabled(true);


			LOCText.clearFocus();
			T.setText(" ");
			result = result + Loc + "-Trial-" + round;

			T.setText(result);

			String path = Environment.getExternalStorageDirectory().getAbsolutePath();
			File folder = new File(path + "/GPSLog/");
			if (!folder.exists()) {
				folder.mkdirs();
			}

			SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS");
			String format = simpleDateFormat.format(new Date());
			File f = new File(path + "/GPSLog/" + result + "_" + format + ".txt");

			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(LOCText.getWindowToken(), 0);





			try {
				f.createNewFile();
				FileOutputStream fos = new FileOutputStream(f);
				logWriter = new DataOutputStream(fos);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			spinner1.setSelection(0);
			LOCText.setText("");
		}

	}


  // https://github.com/mizutori/AndroidLocationStarterKit?source=post_page---------------------------

	public void onFPBtnClicked(View v) {



		if (mIsUpdatingFPToUI)
			return;

		running=true;
		mIsUpdatingFPToUI = true;



		new Thread(new Runnable() {
			public void run() {
				try {

					// Run as long as the updating flag is set to true
					while (mIsUpdatingFPToUI) {
						// Get current fingerprints
						Vector<Fingerprint> fpList = mPilocService.getFingerprint();
					//	Toast.makeText("Fingerprint !", Toast.LENGTH_SHORT).show();

						SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
						String currentDateandTime = sdf.format(new Date());
						Date date = sdf.parse(currentDateandTime);
						long timeinMilSec = date.getTime();

						if (fpList != null) {

							//SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS");
							//String format = simpleDateFormat.format(new Date());
							// Construct fingerprint string


							String fpString ="";

							fpString = currentDateandTime + "," + timeinMilSec + "," +Latitude +",";
							//fpString = currentDateandTime + " " + timeinMilSec + " " + Latitude +" ";

							for (int i = 0; i < fpList.size(); i++) {
								//fpString += fpList.get(i).mMac + " " + fpList.get(i).mFrequency + " "
								//		+ -fpList.get(i).mRSSI + " "+fpList.get(i).timestamp/1000.0+" "+currentDateandTime+" "+fpList.get(i).ssid+ " ";

								String MAC = fpList.get(i).mMac;
								MAC = MAC.replace(":", "");
								fpString += MAC + "," + fpList.get(i).mFrequency + "," + -fpList.get(i).mRSSI + ","+fpList.get(i).ssid +",";
							}


						//	if (Latitude != "0,0") {
								//TextView tv = (TextView) findViewById(R.id.fingerprintTextView);
								//tv.setText(" GPS locked ");

								mCurrentFPString = fpString;
								logWriter.write((mCurrentFPString + "\n").getBytes());
								//logWriter.write((mCurrentFPString).getBytes());
								// Run on UI thread to show the fingerprints to the
								// screen
						//	}



								runOnUiThread(new Runnable() {
								@Override
								public void run() {
										TextView tv = findViewById(R.id.fingerprintTextView);
										tv.setText(mCurrentFPString);
									Log.d("Threads", "w");
									if(running == false) {
											mIsUpdatingFPToUI =false;
											tv.setText("");
											return;
										}
										}
							});
							}
						//Thread.sleep(100);
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
