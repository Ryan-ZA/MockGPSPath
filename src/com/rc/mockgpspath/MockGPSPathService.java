package com.rc.mockgpspath;

import java.util.ArrayList;
import java.lang.String;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.stericson.RootTools.CommandCapture;
import com.stericson.RootTools.RootTools;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.provider.Settings;
import android.os.IBinder;
import android.util.Log;

import com.google.android.maps.GeoPoint;

/**
 * Core service. Updates the fake location using standard Android APIs and
 * calculates the correct current position.
 * 
 * @author Ryan
 * 
 */
public class MockGPSPathService extends Service {

	/**
	 * How quickly to update the location. Lower values will give a better
	 * output but use more CPU in calculations.
	 */
	private static long TIME_BETWEEN_UPDATES_MS = 500;

	/**
	 * Special instance variable that is set whenever this service is running.
	 * Allows easy access to the service without having to use a binder. Only
	 * works from within the same process, however, which is fine for our use.
	 */
	public static MockGPSPathService instance = null;

	UpdateGPSThread currentThread = null;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent.getStringExtra("action").equalsIgnoreCase("com.rc.mockgpspath.start")) {
			if (currentThread != null) {
				currentThread.Running = false;
			}

			currentThread = new UpdateGPSThread();

			ArrayList<String> startLocations = intent.getStringArrayListExtra("locations");
			for (String loc : startLocations) {
				Log.i("Loc", loc);
				String[] arr = loc.split(":");
				GeoPoint newloc = new GeoPoint((int) (Double.parseDouble(arr[0]) * 1E6), (int) (Double.parseDouble(arr[1]) * 1E6));
				currentThread.locations.add(newloc);
			}

			currentThread.realpoints = intent.getBooleanArrayExtra("realpoints");
			currentThread.MperSec = intent.getDoubleExtra("MperSec", 500);
			currentThread.randomizespeed = intent.getBooleanExtra("randomizespeed", false);
			//currentThread.canUpdateMock = intent.getBooleanExtra("canUpdateMock", false);
			currentThread.start();
		}

		if (intent.getStringExtra("action").equalsIgnoreCase("com.rc.mockgpspath.stop")) {
			if (currentThread != null) {
				currentThread.Running = false;
				currentThread.interrupt();
				currentThread = null;
				stopSelf();
			}
		}

		return START_STICKY;
	}

	public void createProgressNotification() {
		Notification notification = new Notification(R.drawable.ic_launcher, getString(R.string.mockgpspath_running_), System.currentTimeMillis());
		notification.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;

		Intent notificationIntent = new Intent(this, MockGPSPathActivity.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

		notification.setLatestEventInfo(this, getString(R.string.mockgpspath_running_), getString(R.string.mockgpspath_running_), contentIntent);

		startForeground(1337, notification);
	}

	public void removeProgressNotification() {
		stopForeground(true);
	}

	/**
	 * The actual worker thread that will do calculations and update the GPS
	 * location.
	 * 
	 * @author Ryan
	 * 
	 */
	class UpdateGPSThread extends Thread {

		ArrayList<GeoPoint> locations = new ArrayList<GeoPoint>();
		boolean[] realpoints;
		double[] distances;
		public boolean Running;
		public double MperSec;
		public boolean randomizespeed;

		public boolean canUpdateMock;
		
		private double curLat, curLong;
		private long startTime;
		private float curBearing;

		private double lastMetersTravelled = 0;

		private String quote(String s) {
			return "\""+s.replace("\\","\\\\").replace("\"", "\\\"")+"\"";
		}
		
		private void restoreMock(boolean mock) {
			if (canUpdateMock) {
				int value = mock?1:0;
				CommandCapture command = new CommandCapture(0, "su -c "
						+quote("sqlite3 /data/data/com.android.providers.settings/databases/settings.db "
								+quote("update secure set value="+quote(Integer.toString(value))+" where name="+quote("mock_location")+";")));
				try {
					RootTools.getShell(true).add(command).waitForFinish();
					RootTools.log("su command completed");
				} catch (Exception e) {
					RootTools.log(e.getStackTrace().toString());
				}
			}
		}

		private boolean getMockValue() {
			boolean oldMock = false;
			try {
				oldMock = Settings.Secure.getInt(getContentResolver(), "mock_location")!=0;
			} catch (Exception e) {
				RootTools.log(e.getStackTrace().toString());
			}
			return oldMock;
		}
		
		private boolean enableMockAndGetOldValue() {
			boolean oldMock;
			oldMock = getMockValue();
			RootTools.log("old mock: "+oldMock);
			restoreMock(true);
			RootTools.log("double checking...");
			while(getMockValue()!=true) {
				RootTools.log("waiting for mock..");
			}
			RootTools.log("got it...");
			String permission = "android.permission.ACCESS_MOCK_LOCATION";
		    RootTools.log("also, "+permission+" is "+(getApplicationContext().checkCallingOrSelfPermission(permission)==PackageManager.PERMISSION_GRANTED));
			
			return oldMock;
		}
		
		
		@Override
		public void run() {
			boolean oldMock;
			
			if(RootTools.isAccessGiven()) {
				RootTools.log("got access");
				canUpdateMock = true;
			} else {
				RootTools.log("no access");
			}
			
			Log.i("MockGPSService", "Starting UpdateGPSThread");
			createProgressNotification();
			Running = true;

			LocationManager locationManager = (LocationManager) getSystemService("location");
						
			//locationManager.addTestProvider("gps", false, false, false, false, false, true, true, 1, 1);
			
			// the way FreeGPS does it:
			
			oldMock = enableMockAndGetOldValue();
			
			locationManager.addTestProvider("gps", false, true, false, false, true, false, false, 1, 1);
			locationManager.setTestProviderEnabled("gps", true);

			restoreMock(oldMock);
			
			startTime = System.currentTimeMillis();
			distances = new double[locations.size() - 1];
			for (int i = 0; i < locations.size() - 1; i++) {
				distances[i] = MapsHelper.distance(locations.get(i), locations.get(i + 1));
			}

			while (Running) {
				calcCurrentPosition();

				Location loc = new Location("gps");
				loc.setTime(System.currentTimeMillis());
				loc.setLatitude(curLat);
				loc.setLongitude(curLong);
				loc.setBearing(curBearing);
				loc.setSpeed((float) MperSec);
				loc.setAccuracy(4);
				
				// crazy jellybean fix
				try {
					Location.class.getMethod("makeComplete").invoke(loc);
				} catch (NoSuchMethodException e) {
					;
			    } catch (Exception e) {
					e.printStackTrace();
				}
				
				oldMock = enableMockAndGetOldValue();
				locationManager.setTestProviderLocation("gps", loc);
				restoreMock(oldMock);
				
				try {
					Thread.sleep(TIME_BETWEEN_UPDATES_MS);
				} catch (Exception e) {
				}
			}
			
			oldMock = enableMockAndGetOldValue();
			locationManager.setTestProviderEnabled("gps", false);
			locationManager.removeTestProvider("gps");
			restoreMock(oldMock);
			
			removeProgressNotification();
			if (currentThread == this)
				currentThread = null;
			Log.i("MockGPSService", "Ending UpdateGPSThread");
		}

		private void calcCurrentPosition() {
			long nanoSecondsPassed = System.currentTimeMillis() - startTime;
			double metersTravelled = nanoSecondsPassed / 1000.0 * MperSec;
			if (randomizespeed) {
				// Bugged. The intervals are too small and most navigation has a
				// smoothing function which smoothes out this randomization.
				// Algorithm needs to be changed to one that works over large
				// time intervals (10sec+) in order to avoid the randomization
				// being smoothed out.
				double diff = metersTravelled - lastMetersTravelled;
				metersTravelled = lastMetersTravelled + Math.random() * 2.0 * diff;

				lastMetersTravelled = metersTravelled;
			}
			for (int i = 0; i < locations.size() - 1; i++) {
				if (metersTravelled < distances[i]) {
					double perc = metersTravelled / distances[i];
					curLat = locations.get(i).getLatitudeE6() / 1E6;
					curLat -= perc * (locations.get(i).getLatitudeE6() / 1E6 - locations.get(i + 1).getLatitudeE6() / 1E6);
					curLong = locations.get(i).getLongitudeE6() / 1E6;
					curLong -= perc * (locations.get(i).getLongitudeE6() / 1E6 - locations.get(i + 1).getLongitudeE6() / 1E6);
					curBearing = MapsHelper.bearing(locations.get(i), locations.get(i + 1));
					return;
				}
				metersTravelled -= distances[i];
			}

			curLat = locations.get(locations.size() - 1).getLatitudeE6() / 1E6;
			curLong = locations.get(locations.size() - 1).getLongitudeE6() / 1E6;
			Running = false;
		}

	}

	@Override
	public void onCreate() {
		super.onCreate();
		instance = this;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (instance == this)
			instance = null;
	}

}
