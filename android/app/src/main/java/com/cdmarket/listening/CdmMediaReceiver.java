package com.cdmarket.listening;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Forwards notification action taps to the plugin instance. */
public class CdmMediaReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent == null || intent.getAction() == null) return;
    CdmMediaPlugin host = CdmMediaPlugin.instance;
    if (host == null) return;
    String a = intent.getAction();
    if (CdmMediaPlugin.ACT_PLAY.equals(a)) host.onAction("play");
    else if (CdmMediaPlugin.ACT_PAUSE.equals(a)) host.onAction("pause");
    else if (CdmMediaPlugin.ACT_PREV.equals(a)) host.onAction("previous");
    else if (CdmMediaPlugin.ACT_NEXT.equals(a)) host.onAction("next");
  }
}
