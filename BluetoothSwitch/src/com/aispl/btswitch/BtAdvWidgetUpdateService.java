package com.aispl.btswitch;

import java.util.LinkedList;
import java.util.Queue;

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.RemoteViews;

/**
 * Background service to build any requested widget updates. Uses a single
 * background thread to walk through an update queue, querying
 * {@link WebserviceHelper} as needed to fill database. Also handles scheduling
 * of future updates, usually in 6-hour increments.
 */
public class BtAdvWidgetUpdateService extends Service implements Runnable {
	private static final String TAG = "BTS A :";

	public static final String ACTION_WIDGET_CLICK = "BTSwitchAdvClick";

	public static final String ACTION_WIDGET_SETTINGS_CLICK = "BTSwitchSettingsClick";

	private static Context context = null;
	Handler handler;

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
				// Log.d(TAG, "Context : "+ context);
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
		// Log.d(TAG, "Handle Intent" + currIntent);
		String action = currIntent.getAction();

		// check, if our widget was clicked
		if (action.equals(ACTION_WIDGET_CLICK)) {
			handleWidgetClick(context, currIntent);
		}
		// check, if Bluetooth was switched on or off
		if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
			handleBtStateChanged(context, currIntent);
		}

		// BT Adaptor Name Changed
		if (action.equals(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED)) {
			handleAdaptorNameChanged(context, currIntent);
		}

		// BT Adaptor Scan mode Changed
		if (action.equals(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)) {
			handleBtScanModeChanged(context, currIntent);
		}

		// Bluetooth Connected to remote device
		if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
			handleBtConnected(context, currIntent);
		}

		// Bluetooth disConnected from remote device
		if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
			handleBtDisConnected(context, currIntent);
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
				R.layout.bt_switch_widget_adv_layout);

		initBtAdvWidget(context, remoteView);

		appWidgetManager.updateAppWidget(appWidgetId, remoteView);
	}

	private void handleWidgetClick(Context context, Intent intent) {
		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter
				.getDefaultAdapter();
		if (mBluetoothAdapter == null) {
			// Device does not support Bluetooth
			RemoteViews remoteView = new RemoteViews(context.getPackageName(),
					R.layout.bt_switch_widget_adv_layout);
			remoteView.setTextViewText(R.id.txtStatus, context
					.getString(R.string.noBtDevice));
			addPendingIntents(context, remoteView);
			ComponentName cn = new ComponentName(context,
					BtSwitchAdvWidget.class);
			AppWidgetManager.getInstance(context).updateAppWidget(cn,
					remoteView);
		} else {
			int btState = mBluetoothAdapter.getState();
			switch (btState) {
			case BluetoothAdapter.STATE_OFF:
				mBluetoothAdapter.enable();
				break;
			case BluetoothAdapter.STATE_ON:
				mBluetoothAdapter.disable();
				break;
			}
		}
	}

	/**
	 * Handle BluetoothAdapter.ACTION_STATE_CHANGED
	 * 
	 * @param context
	 * @param intent
	 */
	private void handleBtStateChanged(Context context, Intent intent) {
		int btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
				BluetoothAdapter.ERROR);

		RemoteViews remoteView = new RemoteViews(context.getPackageName(),
				R.layout.bt_switch_widget_adv_layout);

		switch (btState) {
		case BluetoothAdapter.STATE_TURNING_ON:
			remoteView.setTextViewText(R.id.txtStatus, context
					.getString(R.string.enableBT));
			remoteView.setImageViewResource(R.id.btSwitchAdvWidget,
					R.drawable.bt_state_changing);
			remoteView.setImageViewResource(R.id.statusIcon,
					R.drawable.filler38);
			break;
		case BluetoothAdapter.STATE_TURNING_OFF:
			remoteView.setTextViewText(R.id.txtStatus, context
					.getString(R.string.disableBT));
			remoteView.setImageViewResource(R.id.btSwitchAdvWidget,
					R.drawable.bt_state_changing);
			remoteView.setImageViewResource(R.id.statusIcon,
					R.drawable.filler38);
			break;
		case BluetoothAdapter.STATE_OFF:
			remoteView.setTextViewText(R.id.txtAdaptorName, " ");
			remoteView.setTextViewText(R.id.txtRemoteDeviceName, " ");
			remoteView.setImageViewResource(R.id.btSwitchAdvWidget,
					R.drawable.bt_state_off);
			remoteView.setImageViewResource(R.id.statusIcon,
					R.drawable.filler38);
			remoteView.setTextViewText(R.id.txtStatus, context
					.getString(R.string.btOff));
			break;
		case BluetoothAdapter.STATE_ON:
			BluetoothAdapter mBluetoothAdapter = BluetoothAdapter
					.getDefaultAdapter();
			if (mBluetoothAdapter != null) {
				CharSequence btAdaptorName = "";

				btAdaptorName = mBluetoothAdapter.getName();
				if (btAdaptorName != null) {
					remoteView.setTextViewText(R.id.txtAdaptorName,
							btAdaptorName);
				}
			} else {
				remoteView.setTextViewText(R.id.txtAdaptorName, " ");
			}
			remoteView.setImageViewResource(R.id.btSwitchAdvWidget,
					R.drawable.bt_state_on);
			remoteView.setImageViewResource(R.id.statusIcon,
					R.drawable.bluetooth);
			remoteView.setTextViewText(R.id.txtStatus, context
					.getString(R.string.btOn));
			break;
		}

		if (btState != BluetoothAdapter.ERROR) {
			addPendingIntents(context, remoteView);
			ComponentName cn = new ComponentName(context,
					BtSwitchAdvWidget.class);
			AppWidgetManager.getInstance(context).updateAppWidget(cn,
					remoteView);
		}
	}

	/**
	 * 
	 * 
	 * @param context
	 * @param intent
	 */
	private void handleBtConnected(Context context, Intent intent) {
		BluetoothDevice btD = (BluetoothDevice) intent
				.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
		String btRemoteName = btD.getName();

		RemoteViews remoteView = new RemoteViews(context.getPackageName(),
				R.layout.bt_switch_widget_adv_layout);

		remoteView.setTextViewText(R.id.txtRemoteDeviceName, btRemoteName);
		remoteView.setImageViewResource(R.id.statusIcon,
				R.drawable.bluetooth_connected);
		remoteView.setTextViewText(R.id.txtStatus, context
				.getString(R.string.btConnected));

		addPendingIntents(context, remoteView);

		ComponentName cn = new ComponentName(context, BtSwitchAdvWidget.class);
		AppWidgetManager.getInstance(context).updateAppWidget(cn, remoteView);
	}

	/**
	 * 
	 * 
	 * @param context
	 * @param intent
	 */
	private void handleBtDisConnected(Context context, Intent intent) {
		RemoteViews remoteView = new RemoteViews(context.getPackageName(),
				R.layout.bt_switch_widget_adv_layout);

		remoteView.setTextViewText(R.id.txtRemoteDeviceName, " ");
		remoteView.setImageViewResource(R.id.statusIcon, R.drawable.bluetooth);
		remoteView.setTextViewText(R.id.txtStatus, context
				.getString(R.string.btDisConnected));

		addPendingIntents(context, remoteView);

		ComponentName cn = new ComponentName(context, BtSwitchAdvWidget.class);
		AppWidgetManager.getInstance(context).updateAppWidget(cn, remoteView);
	}

	/**
	 * Handle BluetoothAdapter.ACTION_SCAN_MODE_CHANGED
	 * 
	 * @param context
	 * @param intent
	 */
	private void handleBtScanModeChanged(Context context, Intent intent) {
		int btScanMode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE,
				BluetoothAdapter.ERROR);
		String btScanModeTxt = " ";

		switch (btScanMode) {
		case BluetoothAdapter.SCAN_MODE_NONE:
			btScanModeTxt = context.getString(R.string.btNoScan);
			break;
		case BluetoothAdapter.SCAN_MODE_CONNECTABLE:
			btScanModeTxt = context.getString(R.string.btConnectable);
			break;
		case BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE:
			btScanModeTxt = context.getString(R.string.btDiscoverable);
			break;
		case BluetoothAdapter.ERROR:
			btScanModeTxt = context.getString(R.string.btError);
			break;
		}
		RemoteViews remoteView = new RemoteViews(context.getPackageName(),
				R.layout.bt_switch_widget_adv_layout);
		remoteView.setTextViewText(R.id.txtStatus, btScanModeTxt);

		addPendingIntents(context, remoteView);

		ComponentName cn = new ComponentName(context, BtSwitchAdvWidget.class);
		AppWidgetManager.getInstance(context).updateAppWidget(cn, remoteView);
	}

	/**
	 * @param context
	 * @param intent
	 */
	private void handleAdaptorNameChanged(Context context, Intent intent) {
		String btLocalName = intent
				.getStringExtra(BluetoothAdapter.EXTRA_LOCAL_NAME);

		RemoteViews remoteView = new RemoteViews(context.getPackageName(),
				R.layout.bt_switch_widget_adv_layout);
		remoteView.setTextViewText(R.id.txtAdaptorName, btLocalName);

		addPendingIntents(context, remoteView);

		ComponentName cn = new ComponentName(context, BtSwitchAdvWidget.class);
		AppWidgetManager.getInstance(context).updateAppWidget(cn, remoteView);
	}

	/**
	 * @param context
	 * @param remoteView
	 */
	private void initBtAdvWidget(Context context, RemoteViews remoteView) {
		remoteView.setTextViewText(R.id.txtAdaptorName, " ");
		remoteView.setTextViewText(R.id.txtRemoteDeviceName, " ");
		remoteView.setTextViewText(R.id.txtStatus, " ");

		// based on the bt state set the image
		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter
				.getDefaultAdapter();

		if (mBluetoothAdapter != null) {
			CharSequence btAdaptorName = "";

			btAdaptorName = mBluetoothAdapter.getName();
			if (btAdaptorName != null) {
				remoteView.setTextViewText(R.id.txtAdaptorName, btAdaptorName);
			} else {
				remoteView.setTextViewText(R.id.txtAdaptorName, " ");
			}

			int btState = mBluetoothAdapter.getState();
			switch (btState) {
			case BluetoothAdapter.STATE_TURNING_ON:
				remoteView.setTextViewText(R.id.txtStatus, context
						.getString(R.string.enableBT));
				remoteView.setImageViewResource(R.id.btSwitchAdvWidget,
						R.drawable.bt_state_changing);
				remoteView.setImageViewResource(R.id.statusIcon,
						R.drawable.filler38);
				break;
			case BluetoothAdapter.STATE_TURNING_OFF:
				remoteView.setTextViewText(R.id.txtStatus, context
						.getString(R.string.disableBT));
				remoteView.setImageViewResource(R.id.btSwitchAdvWidget,
						R.drawable.bt_state_changing);
				remoteView.setImageViewResource(R.id.statusIcon,
						R.drawable.filler38);
				break;
			case BluetoothAdapter.STATE_OFF:
				remoteView.setTextViewText(R.id.txtAdaptorName, " ");
				remoteView.setTextViewText(R.id.txtRemoteDeviceName, " ");
				remoteView.setImageViewResource(R.id.btSwitchAdvWidget,
						R.drawable.bt_state_off);
				remoteView.setImageViewResource(R.id.statusIcon,
						R.drawable.filler38);
				remoteView.setTextViewText(R.id.txtStatus, context
						.getString(R.string.btOff));
				break;
			case BluetoothAdapter.STATE_ON:
				if (mBluetoothAdapter != null) {
					btAdaptorName = mBluetoothAdapter.getName();
					if (btAdaptorName != null) {
						remoteView.setTextViewText(R.id.txtAdaptorName,
								btAdaptorName);
					}
				} else {
					remoteView.setTextViewText(R.id.txtAdaptorName, " ");
				}
				remoteView.setImageViewResource(R.id.btSwitchAdvWidget,
						R.drawable.bt_state_on);
				remoteView.setImageViewResource(R.id.statusIcon,
						R.drawable.bluetooth);
				remoteView.setTextViewText(R.id.txtStatus, context
						.getString(R.string.btOn));
				break;
			}
		} else {
			remoteView.setTextViewText(R.id.txtStatus, context
					.getString(R.string.noBtDevice));
			remoteView.setImageViewResource(R.id.btSwitchAdvWidget,
					R.drawable.bt_state_off);
			remoteView.setImageViewResource(R.id.statusIcon,
					R.drawable.filler38);
		}
		addPendingIntents(context, remoteView);
	}

	private void addPendingIntents(Context context, RemoteViews remoteView) {
		// set up intent for widget click
		Intent btActivate = new Intent(context, BtSwitchAdvWidget.class);
		btActivate.setAction(ACTION_WIDGET_CLICK);

		PendingIntent actionPendingIntent = PendingIntent.getBroadcast(context,
				0, btActivate, 0);

		remoteView.setOnClickPendingIntent(R.id.btSwitchAdvWidget,
				actionPendingIntent);

		// setup intent to launch the bluetooth settings directly
		Intent btSettings = new Intent();
		btSettings
				.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);

		PendingIntent actionPendingIntent1 = PendingIntent.getActivity(context,
				0, btSettings, 0);

		remoteView.setOnClickPendingIntent(R.id.btSwitchSettings,
				actionPendingIntent1);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
