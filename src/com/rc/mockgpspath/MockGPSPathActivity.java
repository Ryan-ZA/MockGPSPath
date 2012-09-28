package com.rc.mockgpspath;

import java.util.ArrayList;
import java.util.List;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.google.ads.AdRequest;
import com.google.ads.AdView;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;
import com.rc.mockgpspath.DraggableLayout.DragListener;
import com.rc.mockgpspath.quickaction.ActionItem;
import com.rc.mockgpspath.quickaction.QuickAction;
import com.rc.mockgpspath.quickaction.QuickAction.OnActionItemClickListener;

/**
 * Main activity. Handles all UI operations. Contains the main map view which is
 * used by the user to add points, and track the progress of the route.
 * 
 * @author Ryan
 * 
 */
public class MockGPSPathActivity extends MapActivity {

	final static int OK = 0;
	final static int CANCEL = 1;
	static int zoomLevel = 3;
	static GeoPoint centerPoint = null;

	final static int MODE_START = 0;
	final static int MODE_ADDING = 1;
	final static int MODE_PLAYING = 2;

	MapView mapView;
	NodeOverlay nodeOverlay;
	ImageView trash, play, stop;
	MyLocationOverlay myLocationOverlay;
	View mappin, mappin_holder, mappin_holder2, mappin_line, mappin_path;
	View holder_tooltip_1, holder_tooltip_2, holder_tooltip_3;

	Thread tooltip_thread = null;

	boolean isSearching = false;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);

		mapView = (MapView) findViewById(R.id.mapview);
		mapView.getController().setZoom(2);

		DraggableLayout draggableLayout = (DraggableLayout) findViewById(R.id.topbar);
		mappin = findViewById(R.id.mappin);
		mappin_holder = findViewById(R.id.mappin_holder);
		mappin_holder2 = findViewById(R.id.mappin_holder2);
		mappin_line = findViewById(R.id.mappin_line);
		mappin_path = findViewById(R.id.mappin_path);

		holder_tooltip_1 = findViewById(R.id.holder_tooltip_1);
		holder_tooltip_2 = findViewById(R.id.holder_tooltip_2);
		holder_tooltip_3 = findViewById(R.id.holder_tooltip_3);

		draggableLayout.addDraggable(mappin);
		draggableLayout.addDraggable(mappin_line);
		draggableLayout.addDraggable(mappin_path);

		draggableLayout.setDragListener(dragListener);

		List<Overlay> overlays = mapView.getOverlays();
		nodeOverlay = new NodeOverlay(this);
		overlays.add(nodeOverlay);
		myLocationOverlay = new MyLocationOverlay(MockGPSPathActivity.this, mapView);
		overlays.add(myLocationOverlay);

		trash = (ImageView) findViewById(R.id.trash);
		play = (ImageView) findViewById(R.id.play);
		stop = (ImageView) findViewById(R.id.stop);

		trash.setOnClickListener(trashClickListener);
		play.setOnClickListener(playClickListener);
		stop.setOnClickListener(stopClickListener);

		if (MockGPSPathService.instance != null && MockGPSPathService.instance.currentThread != null) {
			// If the service is running, then we already have a path and are
			// following is. Swap to running mode and pull the map points from
			// the service.
			runningMode();
			for (int i = 0; i < MockGPSPathService.instance.currentThread.locations.size(); i++) {
				GeoPoint loc = MockGPSPathService.instance.currentThread.locations.get(i);
				RouteNodeOverlayItem item = new RouteNodeOverlayItem(loc, MockGPSPathService.instance.currentThread.realpoints[i]);
				nodeOverlay.addItem(item, true);
			}
		} else {
			@SuppressWarnings("unchecked")
			List<RouteNodeOverlayItem> lastList = (List<RouteNodeOverlayItem>) getLastNonConfigurationInstance();

			if (lastList != null && lastList.size() > 0) {
				// If we already had a list in the last config, it means this is
				// just an activity change and we need to re-add all of the
				// previous points.
				for (RouteNodeOverlayItem item : lastList) {
					nodeOverlay.addItem(item, true);
				}
				addingPointsMode();
			} else {
				// No points, regular start mode
				startupMode();
			}
		}

		mapView.getController().setZoom(zoomLevel);
		if (centerPoint != null)
			mapView.getController().setCenter(centerPoint);

		AdView adView = (AdView) findViewById(R.id.adView);
		adView.setVisibility(View.VISIBLE);
		AdRequest adRequest = new AdRequest();
		adView.loadAd(adRequest);

		View search = findViewById(R.id.search);
		search.setOnClickListener(searchClickListener);
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		return nodeOverlay.overlaylist;
	}

	public void startupMode() {
		showView(holder_tooltip_3);
		showView(mappin);
		showView(mappin_holder);
		hideView(play);
		hideView(stop);
		hideView(trash);
		hideView(mappin_holder2);
		hideView(mappin_line);
		hideView(mappin_path);
		hideView(holder_tooltip_1);
		hideView(holder_tooltip_2);

		if (tooltip_thread != null) {
			tooltip_thread.interrupt();
			tooltip_thread = null;
		}
	}

	public void addingPointsMode() {
		hideView(holder_tooltip_3);
		hideView(stop);
		showView(play);
		hideView(mappin);
		showView(mappin_holder);
		showView(trash);
		showView(mappin_holder2);
		showView(mappin_line);
		showView(mappin_path);

		if (tooltip_thread != null) {
			tooltip_thread.interrupt();
			tooltip_thread = null;
		}

		if (nodeOverlay.overlaylist.size() == 1) {
			showView(holder_tooltip_1);
			showView(holder_tooltip_2);

			tooltip_thread = new Thread() {
				public void run() {
					try {
						Thread.sleep(12000);
						if (tooltip_thread == null)
							return;
						runOnUiThread(new Runnable() {

							@Override
							public void run() {
								hideView(holder_tooltip_1);
								hideView(holder_tooltip_2);
							}
						});
					} catch (Exception ex) {
					}
					tooltip_thread = null;
				};
			};
			tooltip_thread.start();
		} else {
			hideView(holder_tooltip_1);
			hideView(holder_tooltip_2);
		}
	}

	public void runningMode() {
		hideView(holder_tooltip_3);
		showView(stop);
		hideView(play);
		hideView(mappin);
		hideView(mappin_holder);
		hideView(trash);
		hideView(mappin_holder2);
		hideView(mappin_line);
		hideView(mappin_path);
		hideView(holder_tooltip_1);
		hideView(holder_tooltip_2);

		if (tooltip_thread != null) {
			tooltip_thread.interrupt();
			tooltip_thread = null;
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		myLocationOverlay.enableMyLocation();
		checkIfMockEnabled();
	}

	private void checkIfMockEnabled() {
		try {
			int mock_location = Settings.Secure.getInt(getContentResolver(), "mock_location");
			if (mock_location == 0) {
				try {
					Settings.Secure.putInt(getContentResolver(), "mock_location", 1);
				} catch (Exception ex) {
				}
				mock_location = Settings.Secure.getInt(getContentResolver(), "mock_location");
			}

			if (mock_location == 0) {
				showDialog(EnableMockLocationDialogFragment.MOCKDIALOG);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		myLocationOverlay.disableMyLocation();
		zoomLevel = mapView.getZoomLevel();
		centerPoint = mapView.getMapCenter();
	}

	@Override
	protected boolean isRouteDisplayed() {
		return true;
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		// This is not done using a fragment directly as MapActivity does not
		// allow fragments...
		if (id == EnableMockLocationDialogFragment.MOCKDIALOG) {
			return EnableMockLocationDialogFragment.createDialog(this);
		}
		return super.onCreateDialog(id);
	};

	DragListener dragListener = new DragListener() {

		@Override
		public void DragFinished(View view, float xInScreen, float yInScreen, float touchStartXinChild, float touchStartYinChild) {
			Rect rect = new Rect();

			int x = (int) xInScreen;
			int y = (int) yInScreen;

			mapView.getHitRect(rect);
			if (rect.contains(x, y)) {
				Projection proj = mapView.getProjection();
				x -= mapView.getLeft();
				y -= mapView.getTop();

				x -= touchStartXinChild;
				y -= touchStartYinChild;

				x += view.getWidth() / 2;
				y += view.getHeight();

				GeoPoint gp = proj.fromPixels(x, y);
				Log.i("MockGPS", "New point in map " + gp.getLatitudeE6() + " " + gp.getLongitudeE6());

				RouteNodeOverlayItem item = new RouteNodeOverlayItem(gp, true);
				Log.i("MockGPS", "dont_make_path: " + (view != mappin_path) + " " + view);
				nodeOverlay.addItem(item, view != mappin_path);

				addingPointsMode();

				if (mapView.getZoomLevel() < 8 && nodeOverlay.overlaylist.size() == 1) {
					mapView.getController().setCenter(gp);
					mapView.getController().zoomIn();
					new Thread() {
						public void run() {
							while (mapView.getZoomLevel() < 8) {
								try {
									Thread.sleep(300);
								} catch (InterruptedException e) {
								}
								runOnUiThread(new Runnable() {

									@Override
									public void run() {
										mapView.getController().zoomIn();
									}
								});
							}
						};
					}.start();
				}
			}
		}

		@Override
		public void DragBegun(View view) {
			hideView(holder_tooltip_1);
			hideView(holder_tooltip_2);
		}
	};

	/**
	 * Convenience method to show a view with animation
	 * 
	 * @param v
	 *            The view.
	 */
	public void showView(View v) {
		if (v.getVisibility() == View.VISIBLE)
			return;

		v.setVisibility(View.VISIBLE);
		popinAnim(v);
	}

	/**
	 * Convenience method to hide a view with animation
	 * 
	 * @param v
	 *            The view.
	 */
	public void hideView(View v) {
		if (v.getVisibility() == View.GONE)
			return;

		v.setVisibility(View.GONE);
		popoutAnim(v);
	}

	/**
	 * A basic fade in + resize animation
	 * 
	 * @param v
	 */
	public void popinAnim(View v) {
		v.clearAnimation();

		AnimationSet as = new AnimationSet(false);

		AlphaAnimation a = new AlphaAnimation(0f, 1f);
		a.setDuration(300);
		as.addAnimation(a);

		ScaleAnimation s = new ScaleAnimation(0f, 1f, 0f, 1f, Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF, 0.5f);
		s.setDuration(300);
		as.addAnimation(s);

		v.startAnimation(as);
	}

	/**
	 * A basic fade out + resize animation
	 * 
	 * @param v
	 */
	public void popoutAnim(View v) {
		v.clearAnimation();

		AnimationSet as = new AnimationSet(false);

		AlphaAnimation a = new AlphaAnimation(1f, 0f);
		a.setDuration(300);
		as.addAnimation(a);

		ScaleAnimation s = new ScaleAnimation(1f, 0f, 1f, 0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
		s.setDuration(300);
		as.addAnimation(s);

		v.startAnimation(as);
	}

	private OnClickListener trashClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			QuickAction quickAction = new QuickAction(MockGPSPathActivity.this);
			TextView textView = new TextView(MockGPSPathActivity.this);
			textView.setText("Clear current paths");
			quickAction.addDividerView(textView);

			ActionItem item = new ActionItem();
			item.setTitle(getString(R.string.ok));
			item.setActionId(OK);
			item.setIcon(getResources().getDrawable(R.drawable.trash));
			quickAction.addActionItem(item);

			item = new ActionItem();
			item.setTitle(getString(R.string.cancel));
			item.setActionId(CANCEL);
			item.setIcon(getResources().getDrawable(R.drawable.cancel));
			quickAction.addActionItem(item);

			quickAction.setOnActionItemClickListener(new OnActionItemClickListener() {

				@Override
				public void onItemClick(QuickAction source, int pos, int actionId) {
					source.dismiss();

					if (actionId == OK) {
						nodeOverlay.clear();
						startupMode();
					}
				}
			});

			quickAction.show(v);
		}
	};

	private OnClickListener playClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			List<GeoPoint> locations = nodeOverlay.getLocations();
			final double distance = MapsHelper.distance(locations);

			LayoutInflater inflater = LayoutInflater.from(MockGPSPathActivity.this);
			View start = inflater.inflate(R.layout.start, null);

			final TextView distanceTV = (TextView) start.findViewById(R.id.distance);
			final TextView speedTV = (TextView) start.findViewById(R.id.speed);
			final TextView elapsetimeTV = (TextView) start.findViewById(R.id.elapsetime);
			final TextView finishtimeTV = (TextView) start.findViewById(R.id.finishtime);
			final SeekBar speedSeek = (SeekBar) start.findViewById(R.id.speedbar);
			final CheckBox randomizespeed = (CheckBox) start.findViewById(R.id.randomizespeed);

			QuickAction quickAction = new QuickAction(MockGPSPathActivity.this);
			distanceTV.setText(String.format("%,1.2f km", distance / 1000));
			quickAction.addDividerView(start);

			speedSeek.setInterpolator(new AccelerateInterpolator());
			speedSeek.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {
				}

				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {
				}

				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					MapsHelper.calcTimes(distance, progress, elapsetimeTV, finishtimeTV, speedTV);
				}
			});
			MapsHelper.calcTimes(distance, speedSeek.getProgress(), elapsetimeTV, finishtimeTV, speedTV);

			ActionItem item = new ActionItem();
			item.setTitle("Start Mock GPS");
			item.setIcon(getResources().getDrawable(R.drawable.play));
			quickAction.addActionItem(item);

			quickAction.setOnActionItemClickListener(new OnActionItemClickListener() {

				@Override
				public void onItemClick(QuickAction source, int pos, int actionId) {
					source.dismiss();

					int progress = speedSeek.getProgress();
					if (progress > 100) {
						progress -= 90;
						progress *= progress;
					}
					double MperSec = progress * 1000.0 / 3600.0;

					startMockPaths(MperSec, randomizespeed.isChecked());
				}
			});

			quickAction.show(v);
		}
	};

	private OnClickListener searchClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			EditText searchtext = (EditText) findViewById(R.id.searchtext);

			if (isSearching) {
				hideView(searchtext);
				isSearching = false;
				InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
				inputMethodManager.hideSoftInputFromWindow(searchtext.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);

				String searchstring = searchtext.getText().toString();
				if (!searchstring.isEmpty()) {
					MapsHelper.getLocationFromString(MockGPSPathActivity.this, searchstring);
				}
			} else {
				showView(searchtext);
				searchtext.requestFocus();
				InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
				inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
				isSearching = true;
			}
		}
	};

	/**
	 * Sends an intent to start the MockGPSPathService with the node items
	 * stored inside the current map.
	 * 
	 * @param MperSec
	 *            The speed that the path should be followed.
	 * @param randomizespeed
	 *            Whether or not to use a random speed.
	 */
	void startMockPaths(double MperSec, boolean randomizespeed) {
		Intent i = new Intent(MockGPSPathActivity.this, MockGPSPathService.class);

		i.putExtra("action", "com.rc.mockgpspath.start");
		i.putExtra("MperSec", MperSec);
		i.putExtra("randomizespeed", randomizespeed);

		ArrayList<String> pass = new ArrayList<String>();
		boolean[] realpoints = new boolean[nodeOverlay.overlaylist.size()];
		for (int j = 0; j < nodeOverlay.overlaylist.size(); j++) {
			RouteNodeOverlayItem item = nodeOverlay.overlaylist.get(j);
			String ns = Double.toString(item.getPoint().getLatitudeE6() / 1E6) + ":" + Double.toString(item.getPoint().getLongitudeE6() / 1E6);
			pass.add(ns);
			realpoints[j] = item.realpoint;
		}

		i.putStringArrayListExtra("locations", pass);
		i.putExtra("realpoints", realpoints);

		startService(i);

		runningMode();
		myLocationOverlay.disableMyLocation();

		new Thread() {
			public void run() {
				try {
					Thread.sleep(250);
				} catch (InterruptedException e) {
				}
				runOnUiThread(new Runnable() {

					@Override
					public void run() {
						myLocationOverlay.enableMyLocation();
					}
				});
			};
		}.start();
	}

	private OnClickListener stopClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			QuickAction quickAction = new QuickAction(MockGPSPathActivity.this);
			TextView textView = new TextView(MockGPSPathActivity.this);
			textView.setText(R.string.stop_current_mock_paths);
			quickAction.addDividerView(textView);

			ActionItem item = new ActionItem();
			item.setTitle(getString(R.string.ok));
			item.setActionId(OK);
			item.setIcon(getResources().getDrawable(R.drawable.cancel));
			quickAction.addActionItem(item);

			item = new ActionItem();
			item.setTitle(getString(R.string.cancel));
			item.setActionId(CANCEL);
			quickAction.addActionItem(item);

			quickAction.setOnActionItemClickListener(new OnActionItemClickListener() {

				@Override
				public void onItemClick(QuickAction source, int pos, int actionId) {
					source.dismiss();

					if (actionId == OK) {
						Intent i = new Intent(MockGPSPathActivity.this, MockGPSPathService.class);

						i.putExtra("action", "com.rc.mockgpspath.stop");

						startService(i);
						nodeOverlay.clear();
						startupMode();
					}
				}
			});

			quickAction.show(v);
		}
	};

}