package nus.cirlab.menu;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.ImageView;

public class MainMenu extends Activity {
//	OnClickListener CollectionListener = null;
//	OnClickListener LocalizationListener = null;
	OnClickListener LogListener = null;
//	OnClickListener SimulationListener = null;
//	OnClickListener ErrorListener = null;
//	OnClickListener ConfigListener = null;
//	OnClickListener SignalMonitorListener = null;
	

//	ImageView CollectionButton;
//	ImageView LocalizationButton;
	ImageView LogButton;	
//	ImageView SimulationButton;
//	ImageView ErrorButton;
//	ImageView ConfigButton;
//	ImageView SignalMonitorButton;

	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /*
        CollectionListener = new OnClickListener() {
			public void onClick(View v) {	
				Intent CollectionIntent = new Intent(MainMenu.this, CollectionActivity.class);
				CollectionButton.setImageResource(R.drawable.collection);
				startActivity(CollectionIntent);
			}
		};
		LocalizationListener = new OnClickListener() {
			public void onClick(View v) {
				Intent LocalizationIntent = new Intent(MainMenu.this, LocalizationActivity.class);
				LocalizationButton.setImageResource(R.drawable.localization);
				startActivity(LocalizationIntent);
			}
		};
		*/
		LogListener = new OnClickListener() {
			public void onClick(View v) {
				Intent LogIntent = new Intent(MainMenu.this, LogActivity.class);
				LogButton.setImageResource(R.drawable.log);
				startActivity(LogIntent);
			}
		};
/*
		SimulationListener = new OnClickListener() {
			public void onClick(View v) {
				Intent SimulationIntent = new Intent(MainMenu.this, SimulationActivity.class);
				SimulationButton.setImageResource(R.drawable.simulation);
				startActivity(SimulationIntent);
			}
		};
		
		ErrorListener = new OnClickListener() {
			public void onClick(View v) {
				Intent ErrorIntent = new Intent(MainMenu.this, ErrorActivity.class);
				ErrorButton.setImageResource(R.drawable.error);
				startActivity(ErrorIntent);
			}
		};
		ConfigListener = new OnClickListener() {
			public void onClick(View v) {
				Intent ConfigIntent = new Intent(MainMenu.this, ConfigActivity.class);
				ErrorButton.setImageResource(R.drawable.config);
				startActivity(ConfigIntent);
			}
		};
		
		SignalMonitorListener = new OnClickListener() {
			public void onClick(View v) {
				Intent SignalMonitorIntent = new Intent(MainMenu.this, MultiLineChartActivity.class);
				SignalMonitorButton.setImageResource(R.drawable.signalmonitor);
				startActivity(SignalMonitorIntent);
			}
		};
*/
        setContentView(R.layout.main);
  //      CollectionButton = (ImageView) findViewById(R.id.Btn_collection);
   //     CollectionButton.setOnClickListener(CollectionListener);
		
        //LocalizationButton = (ImageView) findViewById(R.id.Btn_localization);
        //LocalizationButton.setOnClickListener(LocalizationListener);

        LogButton = findViewById(R.id.Btn_log);
        LogButton.setOnClickListener(LogListener);
/*
        SimulationButton = (ImageView) findViewById(R.id.Btn_simulation);
        SimulationButton.setOnClickListener(SimulationListener);
        
        ErrorButton = (ImageView) findViewById(R.id.Btn_error);
        ErrorButton.setOnClickListener(ErrorListener);
        
        ConfigButton = (ImageView) findViewById(R.id.Btn_config);
        ConfigButton.setOnClickListener(ConfigListener);
        
        SignalMonitorButton = (ImageView) findViewById(R.id.Btn_signalmonitor);
        SignalMonitorButton.setOnClickListener(SignalMonitorListener);
*/
    }
    
    public boolean onPrepareOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		menu.clear();
		menu.add(0, 0, Menu.NONE, "About");
		
		return super.onPrepareOptionsMenu(menu);
	}
    
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent AboutIntent = new Intent(MainMenu.this, About.class);
		startActivity(AboutIntent);

		return super.onOptionsItemSelected(item);
	}
    
}