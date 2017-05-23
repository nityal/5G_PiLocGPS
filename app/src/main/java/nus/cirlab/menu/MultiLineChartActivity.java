package nus.cirlab.menu;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.Legend.LegendPosition;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.github.mikephil.charting.utils.ColorTemplate;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import nus.cirl.piloc.DataStruture.Fingerprint;
import nus.cirl.piloc.SignalMonitorService;
import android.support.v4.app.FragmentActivity;

public class MultiLineChartActivity extends FragmentActivity
		implements OnSeekBarChangeListener, OnChartValueSelectedListener {

	private LineChart mChart;
	// private SeekBar mSeekBarX, mSeekBarY;
	// private TextView tvX, tvY;
	private SignalMonitorService mWiFiDataFeeder = null;
	private boolean isFeedData = true;
	private Vector<Fingerprint> fpList = new Vector<Fingerprint>();
	private HashMap<String, ArrayList<Integer>> macToChartData = new HashMap<String, ArrayList<Integer>>();
	private String selectedMac = null;
	int logcounter = 0;
	private DataOutputStream logWriter = null;
	int timeshift =0;



	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.signalmonitor);
		setTitle("WIFI Signal Inspection");

		Intent intent = new Intent(MultiLineChartActivity.this, SignalMonitorService.class);
		this.getApplicationContext().bindService(intent, conn, Context.BIND_AUTO_CREATE);

		// tvX = (TextView) findViewById(R.id.tvXMax);
		// tvY = (TextView) findViewById(R.id.tvYMax);

		// mSeekBarX = (SeekBar) findViewById(R.id.seekBar1);
		// mSeekBarX.setOnSeekBarChangeListener(this);
		//
		// mSeekBarY = (SeekBar) findViewById(R.id.seekBar2);
		// mSeekBarY.setOnSeekBarChangeListener(this);
		
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
		

		mChart = (LineChart) findViewById(R.id.chart1);
		mChart.setOnChartValueSelectedListener(this);

//		mChart.setDrawGridBackground(true);
		mChart.setDescription("");
		mChart.setDrawBorders(false);

		mChart.getAxisLeft().setEnabled(false);
		mChart.getAxisRight().setDrawAxisLine(false);
//		mChart.getAxisRight().setDrawGridLines(false);
		mChart.getXAxis().setDrawAxisLine(false);
		mChart.getXAxis().setDrawGridLines(false);

		// enable touch gestures
		mChart.setTouchEnabled(true);

		// enable scaling and dragging
		mChart.setDragEnabled(true);
		mChart.setScaleEnabled(true);

		// if disabled, scaling can be done on x- and y-axis separately
		mChart.setPinchZoom(false);

		// mSeekBarX.setProgress(20);
		// mSeekBarY.setProgress(100);

		Legend l = mChart.getLegend();
		l.setPosition(LegendPosition.RIGHT_OF_CHART);
	}

	private ServiceConnection conn = new ServiceConnection() {
		public void onServiceDisconnected(ComponentName name) {
			mWiFiDataFeeder.onDestroy();
		}

		public void onServiceConnected(ComponentName name, IBinder service) {
			SignalMonitorService.MyBinder binder = (SignalMonitorService.MyBinder) service;
			mWiFiDataFeeder = binder.getService();
			// Start collecting only WiFi fingerprints

			mWiFiDataFeeder.startCollectingFingerprints();

			new Thread(new Runnable() {
				public void run() {
					try {
						// Run as long as the updating flag is set to true

						while (isFeedData) {
							// Get current fingerprints
							fpList = mWiFiDataFeeder.getFingerprint();
							
							String fpString = "";
							for (int i = 0; i < fpList.size(); i++) {
								fpString += fpList.get(i).mMac + " " + fpList.get(i).mFrequency + " "
										+ -fpList.get(i).mRSSI + "\n";
							}
							logWriter.write((fpString + "\n").getBytes());
							timeshift++;
							
							
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									updateLineChart(fpList);
									invalidateOptionsMenu();

								}
							});
							// updateLineChart(fpList);
							Thread.sleep(1000);

						}

					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}).start();
		}

	};

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		menu.clear();
		if (macToChartData.size() > 1) {
			for (String mac : macToChartData.keySet()) {
				menu.add(0, 0, Menu.NONE, mac);
			}
		}

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		selectedMac = (String) item.getTitle();
		return true;
	}

	private int[] mColors = new int[] { ColorTemplate.VORDIPLOM_COLORS[0], ColorTemplate.VORDIPLOM_COLORS[1],
			ColorTemplate.VORDIPLOM_COLORS[2], ColorTemplate.VORDIPLOM_COLORS[3], ColorTemplate.VORDIPLOM_COLORS[4] };

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

		mChart.resetTracking();

		// tvX.setText("" + (mSeekBarX.getProgress()));
		// tvY.setText("" + (mSeekBarY.getProgress()));

		// ArrayList<String> xVals = new ArrayList<String>();
		// for (int i = 0; i < mSeekBarX.getProgress(); i++) {
		// xVals.add((i) + "");
		// }

		// ArrayList<ILineDataSet> dataSets = new ArrayList<ILineDataSet>();

		// for (int z = 0; z < 3; z++) {
		//
		// ArrayList<Entry> values = new ArrayList<Entry>();
		//
		// for (int i = 0; i < mSeekBarX.getProgress(); i++) {
		// double val = (Math.random() * mSeekBarY.getProgress()) + 3;
		// values.add(new Entry((float) val, i));
		// }
		//
		// LineDataSet d = new LineDataSet(values, "DataSet " + (z + 1));
		// d.setLineWidth(2.5f);
		// d.setCircleRadius(4f);
		//
		// int color = mColors[z % mColors.length];
		// d.setColor(color);
		// d.setCircleColor(color);
		// dataSets.add(d);
		// }

		// make the first DataSet dashed
		// ((LineDataSet) dataSets.get(0)).enableDashedLine(10, 10, 0);
		// ((LineDataSet)
		// dataSets.get(0)).setColors(ColorTemplate.VORDIPLOM_COLORS);
		// ((LineDataSet)
		// dataSets.get(0)).setCircleColors(ColorTemplate.VORDIPLOM_COLORS);

		// LineData data = new LineData(xVals, dataSets);
		// mChart.setData(data);
		// mChart.invalidate();
	}

	public void updateLineChart(Vector<Fingerprint> fplist) {

		mChart.resetTracking();
		// tvX.setText("" + (mSeekBarX.getProgress()));
		// tvY.setText("" + (mSeekBarY.getProgress()));

		int bufSize = 60;// mSeekBarX.getProgress();
		ArrayList<String> xVals = new ArrayList<String>();
		for (int i = timeshift; i < bufSize+timeshift; i++) {
			xVals.add((i) + "");
		}

		for (Fingerprint fp : fplist) {
			if (!macToChartData.containsKey(fp.mMac)) {
				ArrayList<Integer> rssiVec = new ArrayList<Integer>();
				for (int i = 0; i < bufSize - 1; i++) {
					rssiVec.add(0);
				}
				rssiVec.add(fp.mRSSI);
				macToChartData.put(fp.mMac, rssiVec);
			} else {
				// if(macToChartData.get(fp.mMac).size()<mSeekBarX.getProgress())
				// macToChartData.get(fp.mMac).add(fp.mRSSI);
				// else{
				macToChartData.get(fp.mMac).remove(0);
				macToChartData.get(fp.mMac).add(fp.mRSSI);
				// }
			}
		}

		ArrayList<ILineDataSet> dataSets = new ArrayList<ILineDataSet>();
		int colorIndex = 0;
		if (selectedMac == null) {
			for (String mac : macToChartData.keySet()) {

				ArrayList<Entry> values = new ArrayList<Entry>();

				for (int i = 0; i < bufSize; i++) {
					double val = macToChartData.get(mac).get(i);
					values.add(new Entry((float) val, i));
				}

				LineDataSet d = new LineDataSet(values, mac);
				d.setLineWidth(1.5f);
				d.setCircleRadius(2f);

				int color = mColors[colorIndex % mColors.length];
				d.setColor(color);
				d.setCircleColor(color);
				dataSets.add(d);
				colorIndex++;
			}
		} else {

			ArrayList<Entry> values = new ArrayList<Entry>();

			for (int i = 0; i < bufSize; i++) {
				double val = macToChartData.get(selectedMac).get(i);
				values.add(new Entry((float) val, i));
			}

			LineDataSet d = new LineDataSet(values, selectedMac);
			d.setLineWidth(1.5f);
			d.setCircleRadius(2f);

			int color = mColors[colorIndex % mColors.length];
			d.setColor(color);
			d.setCircleColor(color);
			dataSets.add(d);
			colorIndex++;

		}

		// // make the first DataSet dashed
		// ((LineDataSet) dataSets.get(0)).enableDashedLine(10, 10, 0);
		// ((LineDataSet)
		// dataSets.get(0)).setColors(ColorTemplate.VORDIPLOM_COLORS);
		// ((LineDataSet)
		// dataSets.get(0)).setCircleColors(ColorTemplate.VORDIPLOM_COLORS);

		LineData data = new LineData(xVals, dataSets);
		mChart.setData(data);
		mChart.invalidate();
	}

	@Override
	public void onValueSelected(Entry e, int dataSetIndex, Highlight h) {
		Log.i("VAL SELECTED",
				"Value: " + e.getVal() + ", xIndex: " + e.getXIndex() + ", DataSet index: " + dataSetIndex);
	}

	@Override
	public void onNothingSelected() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		isFeedData = false;
		if (mWiFiDataFeeder != null) {
			// Stop collecting annotated walking trajectories
			mWiFiDataFeeder.stopCollectingFingerprints();
		}

		// Unbind the service
		getApplicationContext().unbindService(conn);

	}

}