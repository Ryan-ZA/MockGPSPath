package com.rc.mockgpspath;

import java.util.ArrayList;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
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

		private double curLat, curLong;
		private long startTime;
		private float curBearing;

		private double lastMetersTravelled = 0;

		@Override
		public void run() {
			Log.i("MockGPSService", "Starting UpdateGPSThread");
			createProgressNotification();
			Running = true;

			LocationManager locationManager = (LocationManager) getSystemService("location");
			locationManager.addTestProvider("gps", false, false, false, false, false, true, true, 1, 1);
			locationManager.setTestProviderEnabled("gps", true);

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
				locationManager.setTestProviderLocation("gps", loc);
				try {
					Thread.sleep(TIME_BETWEEN_UPDATES_MS);
				} catch (Exception e) {
				}
			}
			locationManager.setTestProviderEnabled("gps", false);
			locationManager.removeTestProvider("gps");
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
