package com.aispl.btswitch;

import java.util.LinkedList;
import java.util.Queue;

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

/**
 * Background service to build any requested widget updates. Uses a single
 * background thread to walk through an update queue, querying
 * {@link WebserviceHelper} as needed to fill database. Also handles scheduling
 * of future updates, usually in 6-hour increments.
 */
public class BtWidgetUpdateService extends Service implements Runnable {
	private static final String TAG = "BTS :";

	public static final String ACTION_WIDGET_CLICK = "BTSwitchClick";

	private static Context context = null;
	private Handler mHandler = new Handler();

	/**
	 * Lock used when maintaining queue of requested updates.
	 */
	private static Object sLock = new Object();

	/**
	 * Flag if there is an update thread already running. We only launch a new
	 * thread if one isn't already running.
	 */
	private static boolean sThreadRunning = false;

	/**
	 * Internal queue of requested widget updates. You <b>must</b> access
	 * through {@link #requestUpdate(int[])} or {@link #getNextUpdate()} to make
	 * sure your access is correctly synchronized.
	 */
	private static Queue<Intent> qIntents = new LinkedList<Intent>();

	public static void handleOnReceive(Intent intent) {
		synchronized (sLock) {
			qIntents.add(intent);
			// Log.d(TAG, "handleOnReceive : currIntent : " + intent);
		}
	}

	/**
	 * Peek if we have more updates to perform. This method is special because
	 * it assumes you're calling from the update thread, and that you will
	 * terminate if no updates remain. (It atomically resets
	 * {@link #sThreadRunning} when none remain to prevent race conditions.)
	 */
	private static boolean hasMoreUpdates() {
		synchronized (sLock) {
			boolean hasMore = !qIntents.isEmpty();
			if (!hasMore) {
				sThreadRunning = false;
			}
			return hasMore;
		}
	}

	/**
	 * Poll the next widget update in the queue.
	 */
	private static Intent getNextUpdate() {
		synchronized (sLock) {
			if (qIntents.peek() == null) {
				return null;
			} else {
				return qIntents.poll();
			}
		}
	}

	/**
	 * Start this service, creating a background processing thread, if not
	 * already running.
	 */
	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);

		// Only start processing thread if not already running
		synchronized (sLock) {
			if (!sThreadRunning) {
				sThreadRunning = true;
				context = getApplicationContext();
				new Thread(this).start();

			}
		}
	}

	/**
	 * Main thread for running through any requested widget updates until none
	 * remain. Also sets alarm to perform next update.
	 */
	public void run() {
		// Log.d(TAG, "Processing thread started");
		// preparing a looper on current thread
		// the current thread is being detected implicitly
		Looper.prepare();

		while (hasMoreUpdates()) {
			Intent currIntent = getNextUpdate();

			handleIntent(currIntent);

		}

		synchronized (sLock) {
			sThreadRunning = false;
			// No updates remaining, so stop service
			// Log.d(TAG, "Processing thread Ended");
		}
		stopSelf();

		// After the following line the thread will start
		// running the message loop and will not normally
		// exit the loop unless a problem happens or you
		// quit() the looper (see below)
		Looper.loop();
	}

	private void handleIntent(Intent currIntent) {
		String action = currIntent.getAction();

		// check, if our widget was clicked
		if (action.equals(ACTION_WIDGET_CLICK)) {
			handleWidgetClick(context, currIntent);
		}

		// Handle Bluetooth state switched on or off
		if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
			handleConnectivityChange(context, currIntent);
		}
		//
		// // Handle Widget Create - an instance of an AppWidget is added to a
		// host for the first time.
		// if (action.equals(AppWidgetManager.ACTION_APPWIDGET_ENABLED)) {
		// Log.d(TAG,"APPWIDGET_ENABLED");
		// }

		// Handle Widget Update - Time to update the Widget.
		if (action.equals(AppWidgetManager.ACTION_APPWIDGET_UPDATE)) {
			// Log.d(TAG, "APPWIDGET_UPDATE");
			try {
				Bundle extras = currIntent.getExtras();
				int[] appWidgetIds = extras
						.getIntArray(AppWidgetManager.EXTRA_APPWIDGET_IDS);
				if (appWidgetIds != null) {
					for (int appWidgetId : appWidgetIds) {
						// Log.d(TAG, "appWidgetId : " + appWidgetId);
						updateWidget(appWidgetId);
					}
				}
			} catch (Exception e) {
				Log.e(TAG, "handleIntent " + e);
			}
		}

		// // Handle Widget Delete - Widget instance removed from host.
		// if (action.equals(AppWidgetManager.ACTION_APPWIDGET_DELETED)) {
		// Log.d(TAG,"APPWIDGET_DELETE");
		// //
		// // Bundle extras = currIntent.getExtras();
		// // int appWidgetId =
		// extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID);
		// }
		//
		// // Handle Widget Disabled - Last instance of an AppWidget is removed
		// from the host.
		// if (action.equals(AppWidgetManager.ACTION_APPWIDGET_DISABLED)) {
		// Log.d(TAG,"APPWIDGET_DISABLED");
		// }
	}

	private void updateWidget(int appWidgetId) {
		AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);

		RemoteViews remoteView = new RemoteViews(context.getPackageName(),
				R.layout.bt_switch_widget_layout);

		// based on the bt state set the image
		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter
				.getDefaultAdapter();
		if (mBluetoothAdapter != null) {
			int btState = mBluetoothAdapter.getState();
			switch (btState) {
			case BluetoothAdapter.STATE_TURNING_ON:
			case BluetoothAdapter.STATE_TURNING_OFF:
				remoteView.setImageViewResource(R.id.btSwitchWidgetButton,
						R.drawable.bt_state_changing);
				break;
			case BluetoothAdapter.STATE_OFF:
				remoteView.setImageViewResource(R.id.btSwitchWidgetButton,
						R.drawable.bt_state_off);
				break;
			case BluetoothAdapter.STATE_ON:
				remoteView.setImageViewResource(R.id.btSwitchWidgetButton,
						R.drawable.bt_state_on);
				break;
			}
		} else {
			CharSequence msg = context.getString(R.string.noBtDevice);
			// Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
			showToast(msg);
			remoteView.setImageViewResource(R.id.btSwitchWidgetButton,
					R.drawable.bt_state_off);
		}
		addPendingIntents(context, remoteView);

		appWidgetManager.updateAppWidget(appWidgetId, remoteView);
	}

	private void handleWidgetClick(Context context, Intent intent) {
		CharSequence msg = "";
		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter
				.getDefaultAdapter();
		if (mBluetoothAdapter == null) {
			// Device does not support Bluetooth
			msg = context.getString(R.string.noBtDevice);
		} else {
			int btState = mBluetoothAdapter.getState();
			switch (btState) {
			// case BluetoothAdapter.STATE_TURNING_ON:
			// case BluetoothAdapter.STATE_TURNING_OFF:
			// remoteView.setImageViewResource(R.id.btSwitchWidgetButton,
			// R.drawable.bt_state_changing);
			// break;
			case BluetoothAdapter.STATE_OFF:
				mBluetoothAdapter.enable();
				msg = context.getString(R.string.enableBT);
				break;
			case BluetoothAdapter.STATE_ON:
				mBluetoothAdapter.disable();
				msg = context.getString(R.string.disableBT);
				break;
			}
		}
		if (msg != "") {
			// Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
			showToast(msg);
		}
	}

	private void handleConnectivityChange(Context context, Intent intent) {
		int btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
				BluetoothAdapter.ERROR);

		RemoteViews remoteView = new RemoteViews(context.getPackageName(),
				R.layout.bt_switch_widget_layout);

		switch (btState) {
		case BluetoothAdapter.STATE_TURNING_ON:
		case BluetoothAdapter.STATE_TURNING_OFF:
			remoteView.setImageViewResource(R.id.btSwitchWidgetButton,
					R.drawable.bt_state_changing);
			break;
		case BluetoothAdapter.STATE_OFF:
			remoteView.setImageViewResource(R.id.btSwitchWidgetButton,
					R.drawable.bt_state_off);
			break;
		case BluetoothAdapter.STATE_ON:
			remoteView.setImageViewResource(R.id.btSwitchWidgetButton,
					R.drawable.bt_state_on);
			break;
		}

		if (btState != BluetoothAdapter.ERROR) {
			addPendingIntents(context, remoteView);
			ComponentName cn = new ComponentName(context, BtSwitchWidget.class);
			AppWidgetManager.getInstance(context).updateAppWidget(cn,
					remoteView);
		}
	}

	private void addPendingIntents(Context context, RemoteViews remoteView) {
		Intent active = new Intent(context, BtSwitchWidget.class);
		active.setAction(ACTION_WIDGET_CLICK);

		PendingIntent actionPendingIntent = PendingIntent.getBroadcast(context,
				0, active, 0);

		remoteView.setOnClickPendingIntent(R.id.btSwitchWidgetButton,
				actionPendingIntent);
	}

	private void showToast(final CharSequence msg) {
		mHandler.post(new Runnable() {
			public void run() {
				Toast
						.makeText(getApplicationContext(), msg,
								Toast.LENGTH_SHORT).show();
			}
		});
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
