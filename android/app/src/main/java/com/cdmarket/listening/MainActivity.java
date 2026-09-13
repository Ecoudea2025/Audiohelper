package com.cdmarket.listening;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(CdmMediaPlugin.class);
    super.onCreate(savedInstanceState);
  }
}
