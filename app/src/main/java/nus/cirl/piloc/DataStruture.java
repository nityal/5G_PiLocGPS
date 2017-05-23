package nus.cirl.piloc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

import android.graphics.Point;

public class DataStruture {
	public static class FPConf
	{
		public Boolean mIsUseWifi;
		public Boolean mIsUseBluetooth;
		public Boolean mIsUseMag;
		public Boolean mIsCompressed;
		
		public FPConf(Boolean w, Boolean b, Boolean m, Boolean c)
		{
			mIsUseWifi = w;
			mIsUseBluetooth = b;
			mIsUseMag =m;
			mIsCompressed = c;
		};
	}
	
	//location algorithm
	public static class LocConf
	{
		public int mLocAlgoType;
		
		public LocConf(int locAlgo)
		{
			mLocAlgoType = locAlgo;
		};
	}
	
	public static class Fingerprint implements Serializable
	{
		public String mMac;
		public Integer mRSSI;
		public Integer mType; //0 WiFi; 1 Bluetooth; 2 Magnetic field
		public Integer mFrequency;
		public long timestamp;
		public String ssid;

		public Fingerprint(String m, int r) {
			mMac = m;
			mRSSI = r;
		}
		
		public Fingerprint(String m, int r, int t) {
			mMac = m;
			mRSSI = r;
			mType = t;
		}
		
		public Fingerprint(String m, int r, int f, int t) {
			mMac = m;
			mRSSI = r;
			mType = t;
			mFrequency = f;
		}

		public Fingerprint(String m, int r, int f, int t, long time, String s) {
			mMac = m;
			mRSSI = r;
			mType = t;
			mFrequency = f;
			timestamp = time;
			ssid =s;
		}
		
		public void setFrequency(int fre){
			mFrequency = fre;
		}
		
		
	}
	
	public static class FingerprintwithDuplicate {
		public String mMac;
		public ArrayList<Integer> mRSSI;
		public Integer mType; // 0 Wifi; 1 Bluetooth; 2 Magnetic field

		public FingerprintwithDuplicate(String m, int r) {
			mMac = m;
			mRSSI = new ArrayList<Integer>();
			mRSSI.add(r);
		}

		public FingerprintwithDuplicate(String m, int r, int t) {
			mMac = m;
			mRSSI = new ArrayList<Integer>();
			mRSSI.add(r);
			mType = t;
		}

		public int getMeanRssi() {
			int mean = 0;
			for (int r : mRSSI) {
				mean += r;
			}
			return mean / mRSSI.size();
		}

		public double getStdDevRssi() {
			int mean = getMeanRssi();
			double stdDev = 0;
			for (int r : mRSSI) {
				stdDev += r - mean;
			}
			return stdDev / mRSSI.size();
		}
	}
	
	
	public static class GaussianParameter
	{
//		public String mac;
		public Integer mean;
		public Double stdev; 

		public GaussianParameter( int r, double var) {
			mean = r;
			stdev = var;
		}
	
	}
	
	//Hash map of point to vector of fingerprint
	public static class Radiomap
	{
		//public Vector<LocFP> mLocFPList;
		public HashMap<Point,Vector<Fingerprint>> mLocFingeprints;
		Radiomap(HashMap<Point,Vector<Fingerprint>> f)
		{
			this.mLocFingeprints = f;
		}
	}
	
	
	public static class BTNeighbor
	{
		public int mRSSI;
		public int mTimer;
		
		BTNeighbor(int r, int t)
		{
			mRSSI = r;
			mTimer = t;
		}
	}
	
	public static class WalkingInfo implements Serializable
	{
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		public int mAngle;
		public Vector<Fingerprint> mFingerprints;
		
		WalkingInfo(int a, Vector<Fingerprint> f)
		{
			mAngle = a;
			mFingerprints = f;
			
		}
		
		public Object deepCopy() throws IOException, ClassNotFoundException{
	        ByteArrayOutputStream bos = new ByteArrayOutputStream();
	        ObjectOutputStream oos = new ObjectOutputStream(bos);
	        oos.writeObject(this);
	        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
	        ObjectInputStream ois = new ObjectInputStream(bis);
	        return ois.readObject();
	    }
	}
	
	public static class StepInfo implements Serializable
	{
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		public int mAngle;
		public Vector<Fingerprint> mFingerprints;
		public double mPosX;
		public double mPosY;
		
		StepInfo(int a, Vector<Fingerprint> f, double x, double y)
		{
			mAngle = a;
			mFingerprints = f;
			mPosX = x;
			mPosY = y;
		}
		
		public Object deepCopy() throws IOException, ClassNotFoundException{
	        ByteArrayOutputStream bos = new ByteArrayOutputStream();
	        ObjectOutputStream oos = new ObjectOutputStream(bos);
	        oos.writeObject(this);
	        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
	        ObjectInputStream ois = new ObjectInputStream(bis);
	        return ois.readObject();
	    }
	}
	
}
