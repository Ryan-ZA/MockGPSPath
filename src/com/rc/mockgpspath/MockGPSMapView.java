package com.rc.mockgpspath;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.google.android.maps.MapView;

/**
 * A slightly modified MapView that allows for double-tap-to-zoom functionality.
 * 
 * @author Ryan
 * 
 */
public class MockGPSMapView extends MapView {

	private long lastTouchTime = -1;

	public MockGPSMapView(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.setBuiltInZoomControls(true);
		//this.setReticleDrawMode(MapView.ReticleDrawMode.DRAW_RETICLE_OVER);
	}

	/*
	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		if (ev.getAction() == MotionEvent.ACTION_DOWN) {
			long thisTime = System.currentTimeMillis();
			if (thisTime - lastTouchTime < 250) {
				// Double tap
				this.getController().zoomInFixing((int) ev.getX(), (int) ev.getY());
				lastTouchTime = -1;
			} else {
				// Too slow :)
				lastTouchTime = thisTime;
			}
		}

		return super.onInterceptTouchEvent(ev);
	}
	*/
}