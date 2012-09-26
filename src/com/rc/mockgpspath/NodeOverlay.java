package com.rc.mockgpspath;

import java.util.ArrayList;
import java.util.List;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathDashPathEffect;
import android.graphics.Point;
import android.graphics.Path.FillType;
import android.graphics.PathDashPathEffect.Style;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapView;
import com.google.android.maps.OverlayItem;
import com.google.android.maps.Projection;

/**
 * A google maps overlay that will draw our current route. Draws lines between
 * points.
 * 
 * @author Ryan
 * 
 */
public class NodeOverlay extends ItemizedOverlay<RouteNodeOverlayItem> {

	private final MockGPSPathActivity mockGPSPathActivity;
	List<RouteNodeOverlayItem> overlaylist = new ArrayList<RouteNodeOverlayItem>();
	private List<RouteNodeOverlayItem> overlaylistReal = new ArrayList<RouteNodeOverlayItem>();

	public NodeOverlay(MockGPSPathActivity mockGPSPathActivity) {
		super(boundCenterBottom(mockGPSPathActivity.getResources().getDrawable(R.drawable.mappin)));
		this.mockGPSPathActivity = mockGPSPathActivity;
		populate();
	}

	@Override
	protected RouteNodeOverlayItem createItem(int arg0) {
		return overlaylistReal.get(arg0);
	}

	@Override
	public int size() {
		return overlaylistReal.size();
	}

	/**
	 * Add a new point to the current route.
	 * 
	 * @param item
	 *            An item containing the map point
	 * @param dont_make_path
	 *            Whether or not to make a map pin on this new point.
	 */
	public void addItem(final RouteNodeOverlayItem item, boolean dont_make_path) {
		if (overlaylist.size() > 0 && item.realpoint && !dont_make_path) {
			final RouteNodeOverlayItem lastItem = overlaylist.get(overlaylist.size() - 1);
			new Thread() {
				public void run() {
					try {
						final ArrayList<GeoPoint> extraPoints = MapsHelper.getJavascriptDirections(lastItem.getPoint(), item.getPoint());
						NodeOverlay.this.mockGPSPathActivity.runOnUiThread(new Runnable() {

							@Override
							public void run() {
								overlaylist.remove(item);
								overlaylistReal.remove(item);
								for (GeoPoint point : extraPoints) {
									overlaylist.add(new RouteNodeOverlayItem(point, false));
								}
								overlaylist.add(item);
								overlaylistReal.add(item);
								populate();
								NodeOverlay.this.mockGPSPathActivity.mapView.invalidate();
							}
						});
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				};
			}.start();
		}

		overlaylist.add(item);
		if (item.realpoint) {
			overlaylistReal.add(item);
			populate();
			this.mockGPSPathActivity.mapView.invalidate();
		}
	}

	/**
	 * Clear all points in this path and reset the linked mapview.
	 */
	public void clear() {
		overlaylist.clear();
		overlaylistReal.clear();
		populate();
		this.mockGPSPathActivity.mapView.invalidate();
	}

	public List<GeoPoint> getLocations() {
		ArrayList<GeoPoint> list = new ArrayList<GeoPoint>();

		for (RouteNodeOverlayItem item : overlaylist) {
			list.add(item.getPoint());
		}

		return list;
	}

	@Override
	public boolean draw(Canvas canvas, MapView mapView, boolean shadow, long when) {
		if (shadow) {
			return super.draw(canvas, mapView, shadow, when);
		}
		Projection projection = mapView.getProjection();

		Paint paint = new Paint();
		paint.setAntiAlias(true);
		paint.setColor(Color.RED);
		paint.setStrokeWidth(3);
		paint.setStrokeJoin(Paint.Join.ROUND);
		paint.setStrokeCap(Paint.Cap.ROUND);

		if (mapView.getZoomLevel() < 5) {
			paint.setStyle(Paint.Style.FILL_AND_STROKE);
			Path path = new Path();
			path.setFillType(FillType.INVERSE_EVEN_ODD);
			path.moveTo(-1, 1);
			path.lineTo(4, 4);
			path.lineTo(5, 3);
			path.lineTo(1, 1);
			path.lineTo(10, 1);
			path.lineTo(10, -1);
			path.lineTo(1, -1);
			path.lineTo(5, -3);
			path.lineTo(4, -4);
			path.lineTo(-1, -1);

			PathDashPathEffect dashPath2 = new PathDashPathEffect(path, 20, 20, Style.MORPH);
			paint.setPathEffect(dashPath2);
		}
		paint.setAlpha(120);

		synchronized (overlaylist) {
			int skip = overlaylist.size() / 100;
			int count = 0;

			int lx = Integer.MIN_VALUE;
			int ly = Integer.MIN_VALUE;
			Point p = new Point();
			for (RouteNodeOverlayItem rni : overlaylist) {
				count++;
				if (count < skip && !rni.realpoint)
					continue;
				count = 0;

				projection.toPixels(rni.getPoint(), p);
				if (lx != Integer.MIN_VALUE) {
					canvas.drawLine(p.x, p.y, lx, ly, paint);
				}
				lx = p.x;
				ly = p.y;
			}
		}

		return super.draw(canvas, mapView, shadow, when);
	}

}

/**
 * Standard maps OverlayItem. Stores whether or not this is a realpoint. A
 * realpoint is one that shows a map pin on it. Points part of a route from
 * google's direction service are not realpoints.
 * 
 * @author Ryan
 * 
 */
class RouteNodeOverlayItem extends OverlayItem {

	public boolean realpoint;

	public RouteNodeOverlayItem(GeoPoint point, boolean realpoint) {
		super(point, null, null);
		this.realpoint = realpoint;
	}
}