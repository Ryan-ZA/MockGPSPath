package com.rc.mockgpspath;

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Transformation;
import android.widget.RelativeLayout;

/**
 * Special layout which allows for dragging of children. Catches touch events
 * and determines if they are drags or not. Uses spacial transforms in the
 * getChildStaticTransformation to have the dragged object follow the user's
 * finger.
 * 
 * @author Ryan
 * 
 */
public class DraggableLayout extends RelativeLayout {

	private ArrayList<Draggable> draggables = new ArrayList<Draggable>();
	private Rect mRect = new Rect();
	private Draggable currentDraggable = null;
	private DragListener dragListener = null;

	private void init() {
		setStaticTransformationsEnabled(true);
		setChildrenDrawingCacheEnabled(true);
		setChildrenDrawnWithCacheEnabled(true);
	}

	public DraggableLayout(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	public DraggableLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public DraggableLayout(Context context) {
		super(context);
		init();
	}

	@Override
	protected boolean getChildStaticTransformation(View child, Transformation t) {
		for (Draggable draggable : draggables) {
			if (child == draggable.child) {
				t.getMatrix().setTranslate(-draggable.x, -draggable.y);
				return true;
			}
		}
		return super.getChildStaticTransformation(child, t);
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			for (Draggable draggable : draggables) {
				if (draggable.child.getVisibility() != View.VISIBLE)
					continue;

				draggable.child.getHitRect(mRect);
				if (mRect.contains((int) event.getX(), (int) event.getY())) {
					return true;
				}
			}
		}
		return super.onInterceptTouchEvent(event);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			for (Draggable draggable : draggables) {
				if (draggable.child.getVisibility() != View.VISIBLE)
					continue;

				draggable.child.getHitRect(mRect);
				if (mRect.contains((int) event.getX(), (int) event.getY())) {
					currentDraggable = draggable;
					currentDraggable.x = 0;
					currentDraggable.y = 0;
					currentDraggable.touchStartX = event.getX();
					currentDraggable.touchStartY = event.getY();
					currentDraggable.touchStartXinChild = event.getX() - currentDraggable.child.getLeft();
					currentDraggable.touchStartYinChild = event.getY() - currentDraggable.child.getTop();
					currentDraggable.child.bringToFront();
					if (dragListener != null) {
						dragListener.DragBegun(currentDraggable.child);
					}
					return true;
				}
			}
			return false;
		}
		if (event.getAction() != MotionEvent.ACTION_MOVE) {
			if (currentDraggable != null) {
				if (dragListener != null) {
					dragListener.DragFinished(currentDraggable.child, event.getX(), event.getY(), currentDraggable.touchStartXinChild, currentDraggable.touchStartYinChild);
				}
				currentDraggable.x = 0;
				currentDraggable.y = 0;
				currentDraggable = null;
				invalidate();
			}
			return true;
		} else {
			if (currentDraggable != null) {
				currentDraggable.x = currentDraggable.touchStartX - event.getX();
				currentDraggable.y = currentDraggable.touchStartY - event.getY();
				invalidate();
			}
		}

		return true;
	}

	/**
	 * Mark a view as being draggable. By default, all views are undraggable
	 * until this method is called on them.
	 * 
	 * @param v
	 *            The view to mark as draggable
	 */
	public void addDraggable(View v) {
		Draggable draggable = new Draggable();
		draggable.child = v;
		draggables.add(draggable);
	}

	public void setDragListener(DragListener dragListener) {
		this.dragListener = dragListener;
	}

	/**
	 * Helper class to store information on any views in the container that are
	 * draggable.
	 * 
	 * @author Ryan
	 * 
	 */
	private static class Draggable {
		View child;
		float x, y;
		float touchStartX, touchStartY;
		float touchStartXinChild, touchStartYinChild;
	}

	/**
	 * Interface that can be passed to setDragListener to be notified of drag
	 * events.
	 * 
	 * @author Ryan
	 * 
	 */
	public interface DragListener {
		/**
		 * Called when a drag event begins.
		 * 
		 * @param view
		 *            The view that is being dragged.
		 */
		public void DragBegun(View view);

		/**
		 * Called when a view has been dragged into a new position and the user
		 * has released it.
		 * 
		 * @param view
		 *            The view that has been dragged.
		 * @param xInScreen
		 *            The X position of the user's finger when released.
		 * @param yInScreen
		 *            The Y position of the user's finger when released.
		 * @param touchStartXinChild
		 *            The X position of the user's finger inside the view when
		 *            he began to drag the view. This is the offset to use when
		 *            considering things like points in the map pin.
		 * @param touchStartYinChild
		 *            The Y position of the user's finger inside the view when
		 *            he began to drag the view. This is the offset to use when
		 *            considering things like points in the map pin.
		 */
		public void DragFinished(View view, float xInScreen, float yInScreen, float touchStartXinChild, float touchStartYinChild);
	}
}
