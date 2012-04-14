package com.aispl.btswitch;

import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

public class BtSwitchWidget extends AppWidgetProvider {

	@Override
	public void onReceive(Context context, Intent intent) {

		super.onReceive(context, intent);

		BtWidgetUpdateService.handleOnReceive(intent);
		context.startService(new Intent(context, BtWidgetUpdateService.class));
	}
}
