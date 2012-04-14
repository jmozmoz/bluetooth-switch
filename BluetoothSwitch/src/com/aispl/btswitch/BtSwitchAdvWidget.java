package com.aispl.btswitch;

import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

public class BtSwitchAdvWidget extends AppWidgetProvider {

	@Override
	public void onReceive(Context context, Intent intent) {

		super.onReceive(context, intent);
		
		BtAdvWidgetUpdateService.handleOnReceive(intent);
		context.startService(new Intent(context, BtAdvWidgetUpdateService.class));
	}
}
