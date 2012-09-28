package com.rc.mockgpspath;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;

/**
 * Dialog that is shown to the user if he has not enabled mock locations. All it
 * does is open the normal android settings page for that option.
 * 
 * @author Ryan
 * 
 */
public class EnableMockLocationDialogFragment {

	public final static int MOCKDIALOG = 426252;

	public static Dialog createDialog(final Activity activity) {
		AlertDialog.Builder builder = new Builder(activity);

		builder.setMessage(R.string.you_must_enable_allow_mock_locations_to_use_this_app_);
		builder.setPositiveButton(R.string.enable_now, new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				activity.startActivity(new Intent().setClassName("com.android.settings", "com.android.settings.DevelopmentSettings"));
			}
		});
		builder.setNegativeButton(R.string.quit, new OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				activity.finish();
			}
		});
		builder.setCancelable(false);

		return builder.create();
	}

}
