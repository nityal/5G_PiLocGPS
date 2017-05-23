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
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import nus.cirl.piloc.DataStruture.FPConf;
import nus.cirl.piloc.DataStruture.Fingerprint;
import nus.cirl.piloc.DataStruture.GaussianParameter;
import nus.cirl.piloc.DataStruture.LocConf;
import nus.cirl.piloc.DataStruture.Radiomap;
import nus.cirl.piloc.DataStruture.StepInfo;

public class SimulationService extends Service  {


	private Radiomap mRadioMap = new Radiomap(null);
	private HashMap<String, Vector<Point>> mRadioDistMap = new HashMap<String, Vector<Point>>();
	private HashMap<String, Vector<Integer>> mRadioMeanDistMap = new HashMap<String, Vector<Integer>>();
	private HashMap<String, Double> mRadioStDevMap = new HashMap<String, Double>();
	private HashMap<String, Integer> mRadioMeanMap = new HashMap<String, Integer>();
	private HashMap<Point, HashMap<String, GaussianParameter>> mRadioGuassionMap = new HashMap<Point, HashMap<String, GaussianParameter>>();
	private HashMap<Point, Integer> mRadioDensityMap = new HashMap<Point, Integer>();
	private HashMap<Point, Integer> mAPDensityMap = new HashMap<Point, Integer>();
	private HashMap<Point, Double> mConfidenceHashMap = new HashMap<Point, Double>();
	private HashMap<Point, Double> mSimulationHashMap = new HashMap<Point, Double>();
	private HashMap<Point, HashSet<Point>> mNeighbourMap = new HashMap<Point, HashSet<Point>>();
	private HashMap<String, String> mMacConvertMap = new HashMap<String, String>();
	private Set<Point> TurningPointSet = new HashSet<Point>();

	private ArrayList<Point> statesList = new ArrayList<Point>();
	private double[][] transmitMatrix = null;

	private int mWidth = 0;
	private int mHeight = 0;

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

	private String mCurrentConfidence = "";

	private String mConfidenceMap = "";


	float mAzimuth = 0;
	float mLastAzimuth = 361;
	float l = 0.5f;
	private int mLastX;
	private int mLastY;
	Point mCurrentLocation = null;
	Point mConfiCurrentLocation = null;


	int port = 8080;
	String colorCode = "black";

	private final int cellLength = 1;// 20;

	@Override
	public IBinder onBind(Intent intent) {
		IBinder result = null;
		if (null == result) {
			result = new MyBinder();
		}
		return result;
	}

	public class MyBinder extends Binder {
		public SimulationService getService() {
			// startCollectingFingerprints();
			return SimulationService.this;
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
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

//				loadRadiomapFromString(result);
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

	public  HashMap<Point, Double> getDataBaseConfidenceSimulation( Point p,int algorithm) {
		if (mRadioGuassionMap == null || p == null)
			return null;
		mCurrentConfidence ="";
		mSimulationHashMap = new HashMap<Point, Double>();
		for (Point k :mRadioGuassionMap.keySet()) {
			mSimulationHashMap.put(k, 0.0);
		}
		for(int i=0;i<100;i++){
			Vector<Fingerprint> fp = getSampleFromDistribution( mRadioGuassionMap.get(p)  );
			mCurrentConfidence+=mRadioGuassionMap.get(p).keySet().size()+" "+fp.size()+"\n";
			Point CandidateKey=null;
			
			 if(algorithm==0)
				 CandidateKey =	WeightedLocalization(fp, mRadioGuassionMap.keySet());
			 else if(algorithm ==1)
				 CandidateKey = ConfidenceBasedLocalization (fp, mRadioGuassionMap.keySet());
			 else if(algorithm ==2){
				 Vector<Vector<Fingerprint>> observation = new Vector<Vector<Fingerprint>> ();
				 observation.add(fp);
				 for(int j=0;j<4;j++)
					 observation.add(getSampleFromDistribution( mRadioGuassionMap.get(p) ));
				 
				 CandidateKey = getLocationByConfidenceAreaBasedViterbi(observation);
			 }
			 else{
				 CandidateKey =	Radar(fp, mRadioGuassionMap.keySet());
				 int min =Integer.MAX_VALUE;
				 Point key =null;
				 for(Point temp: mSimulationHashMap.keySet()){
					 int dis = (temp.x-CandidateKey.x)*(temp.x-CandidateKey.x)+ (temp.y-CandidateKey.y)*(temp.y-CandidateKey.y);
					 if(dis<min){
						 min = dis;
						 key = temp;
					 }
				 }
				 CandidateKey = key;
			 }
			
			if(CandidateKey!=null)
				mSimulationHashMap.put(CandidateKey, mSimulationHashMap.get(CandidateKey)+1.0);
		}
		
		return mSimulationHashMap;
	}
	public  HashMap<Point, Double> getDataBaseConfidenceSimulationForAll( int algorithm) {
		if (mRadioGuassionMap == null )
			return null;
		mCurrentConfidence ="";
		mSimulationHashMap = new HashMap<Point, Double>();
		for (Point k :mRadioGuassionMap.keySet()) {
			mSimulationHashMap.put(k, 0.0);
		}
		int sum =0;
		for(Point p : mRadioGuassionMap.keySet()){
			for(int i=0;i<100;i++){
				Vector<Fingerprint> fp = getSampleFromDistribution( mRadioGuassionMap.get(p)  );
				mCurrentConfidence+=mRadioGuassionMap.get(p).keySet().size()+" "+fp.size()+"\n";
				Point CandidateKey =null;
				
				 if(algorithm==0)
					 CandidateKey =	WeightedLocalizationImpactFactor(fp, mRadioGuassionMap.keySet());
				 else if(algorithm ==1)
					 CandidateKey = ConfidenceBasedLocalization (fp, mRadioGuassionMap.keySet());
				 else if(algorithm ==2){
					 Vector<Vector<Fingerprint>> observation = new Vector<Vector<Fingerprint>> ();
					 observation.add(fp);
					 for(int j=0;j<4;j++)
						 observation.add(getSampleFromDistribution( mRadioGuassionMap.get(p) ));
					 
					 CandidateKey = getLocationByConfidenceAreaBasedViterbi(observation);
				 }
				 else
					 CandidateKey =	WeightedLocalization(fp, mRadioGuassionMap.keySet());
				
				if(CandidateKey!=null && CandidateKey.equals(p) ){
					mSimulationHashMap.put(CandidateKey, mSimulationHashMap.get(CandidateKey)+1.0);
				}

			}
			sum +=mSimulationHashMap.get(p);
		}
		mCurrentConfidence = sum / mRadioGuassionMap.keySet().size()+"\n";
		
		return mSimulationHashMap;
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
//				if(t>=0&&t<=1)
					confi += t;
//				else
//					mCurrentConfidence+="=.="+t;
			}
//			confi = confi / mTempConfidenceVectorHashMap.get(k).size();
			mConfidenceHashMap.put(k, Math.round(100.0*confi)/100.0);
			if(confi>max){
				max = confi;
				Key = k;
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
	
	public Point WeightedLocalization(Vector<Fingerprint> fp, Set<Point> CandidateSet) {

		Point Key = null;
		double sum = 0;
		double maxScore = 0;

		for (Point k : CandidateSet) {
			int number = 0;
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

			if (number>fp.size()/2&&sum > maxScore) {
				mCurrentConfidence = number+"/"+fp.size();
				maxScore = sum;
				Key = new Point(k.x, k.y);
			}
		}
		return Key;
	}
	
	public Point WeightedLocalizationImpactFactor(Vector<Fingerprint> fp, Set<Point> CandidateSet) {

		Point Key = null;
		double sum = 0;
		double maxScore = 0;

		for (Point k : CandidateSet) {
			int number = 0;
			sum = 0;
			for (Fingerprint f2 : fp) {
				if (mRadioGuassionMap.get(k).containsKey(f2.mMac)) {
					number++;
					double diff = 1;
					//double impactFactor = 1.0 / mRadioGuassionMap.get(k).get(f2.mMac).mean;
					double impactFactor = mRadioStDevMap.get(f2.mMac);
					if (mRadioGuassionMap.get(k).get(f2.mMac).mean != f2.mRSSI) {
						diff = Math.abs(mRadioGuassionMap.get(k).get(f2.mMac).mean - f2.mRSSI);
						sum += impactFactor * (1.0 / diff);
					} else {
						sum += impactFactor * (1.0);
					}
				}
			}

			if (number>fp.size()/2&&sum > maxScore) {
				mCurrentConfidence = number+"/"+fp.size();
				maxScore = sum;
				Key = new Point(k.x, k.y);
			}
		}
		return Key;
	}
	
	public Point getLocationByConfidenceAreaBasedViterbi(Vector<Vector<Fingerprint>> observation) {
		
		// initial start states
		double[][] V = new double[observation.size() + 1][statesList.size()];
		for (int i = 0; i < statesList.size(); i++) {
			V[0][i] = 1;
			V[1][i] = 0.1;
		}

		for (int t = 1; t <= observation.size(); t++) {
			Vector<Fingerprint> ifp = observation.get(t - 1);

			HashMap<Point, Vector<Double>> mTempConfidenceVectorHashMap = new HashMap<Point, Vector<Double>>();
			for (Point k : mRadioGuassionMap.keySet()) {
				mTempConfidenceVectorHashMap.put(k, new Vector<Double>());
			}

			// calculate normalize evidence map
			for (Fingerprint f : ifp) {
				double sum = 0;
				HashMap<Point, Double> tmpconfiMap = new HashMap<Point, Double>();
				for (Point k : mRadioGuassionMap.keySet()) {
					if (mRadioGuassionMap.get(k).keySet().contains(f.mMac)) {
						GaussianParameter gp = mRadioGuassionMap.get(k).get(f.mMac);
						double confi = getConfidence(gp.mean, gp.stdev, f.mRSSI);
						tmpconfiMap.put(k, confi);
						sum += confi;
					}
				}
				if (sum > 0) {
					for (Point k : tmpconfiMap.keySet()) {
						mTempConfidenceVectorHashMap.get(k).add(tmpconfiMap.get(k)/sum);
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
//				if (mTempConfidenceVectorHashMap.get(statesList.get(k)).size() > 0)
//					confi = confi / mTempConfidenceVectorHashMap.get(statesList.get(k)).size();
				
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
	
	//generate samples from distribution
	public Vector<Fingerprint> getSampleFromDistribution ( HashMap<String, GaussianParameter> gpmap){
		Vector<Fingerprint> fp = new  Vector<Fingerprint>();
		
		for(String  mac : gpmap.keySet()  ){
			NormalDistribution nd = new NormalDistribution(gpmap.get(mac).mean,gpmap.get(mac).stdev );
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
			return 0.9;
		else if(rssi <70)
			return 0.8;
		else if(rssi <80)
			return 0.7;
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
	
	public HashMap<Point, Double> fetchSimulationMap() {
		return mSimulationHashMap;
	}
	
	public Set<Point> getTurningPointSet() {
		return TurningPointSet;
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
			mRadioDistMap = new HashMap<String, Vector<Point>>(); //coverage for each AP 
			mRadioMeanDistMap = new HashMap<String, Vector<Integer>>(); //rssi value at all the positions
			mRadioStDevMap = new HashMap<String, Double>(); //variance for each MAC over all the map
			mRadioMeanMap = new HashMap<String, Integer>(); // mean for each MAC over all the map
			
			mRadioGuassionMap = new HashMap<Point, HashMap<String, GaussianParameter>>(); //GaussianParameter for each mac in each position
			mRadioDensityMap = new HashMap<Point, Integer>();  //number of samples collected
			mAPDensityMap = new HashMap<Point, Integer>(); //number of APs collected in each position 
			statesList = new ArrayList<Point>();
			 mMacConvertMap = new HashMap<String, String>();

			int width = mCurrentFloorplan.getWidth();
			int dis = width / 20 > 80 ? width / 20 : 80;
			dis = (int) 2 * dis;

			for (String line : mapStrings) {
				
				if(line.startsWith("#")){
					String[] tokens = line.substring(1).split(" ");
					mMacConvertMap.put(tokens[0], tokens[1]);
				}else if(line.startsWith("$")){
					String[] tokens = line.substring(1).split(" ");
					TurningPointSet.add(new Point(Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1])));
				}else{
					String[] tokens = line.split(" ");
					Point loc = new Point(Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1]));
					Vector<Fingerprint> fp = new Vector<Fingerprint>();
					HashMap<String, GaussianParameter> gp = new HashMap<String, GaussianParameter>();
					int maxPointNum = 0;
					for (int i = 0; i < (tokens.length - 2) / 2; i++) {
						String mac = tokens[i * 2 + 2];
						String[] gsInfo = tokens[i * 2 + 3].split(",");
						int AvgRssi =  Integer.parseInt( gsInfo[0] );
						double stdev = Double.parseDouble(gsInfo[1]);//bonus
						int numberofSample =   Integer.parseInt( gsInfo[2] );
						
						if (maxPointNum <numberofSample)
							maxPointNum =numberofSample;
						
						gp.put(mac, new GaussianParameter(AvgRssi, stdev+1.0));
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
//					if(gp.size()>30)
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




}
