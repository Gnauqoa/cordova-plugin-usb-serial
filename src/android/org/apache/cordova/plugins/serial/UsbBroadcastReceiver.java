package org.apache.cordova.plugins.serial;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbManager;
import android.util.Log;
import java.util.HashMap;
import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Custom {@link BroadcastReceiver} that can talk through a cordova {@link CallbackContext}
 * @author Xavier Seignard <xavier.seignard@gmail.com>
 */
public class UsbBroadcastReceiver extends BroadcastReceiver {

  // logging tag
  private final String TAG = UsbBroadcastReceiver.class.getSimpleName();
  // usb permission tag name
  public static final String USB_PERMISSION = "org.apache.cordova.plugins.serial.USB_PERMISSION";
  // cordova callback context to notify the success/error to the cordova app
  private RequestPermissionCallback requestPermissionCallback;
  // cordova activity to use it to unregister this broadcast receiver
  private Activity activity;
  private UsbDevice deviceRequest;

  /**
   * Custom broadcast receiver that will handle the cordova callback context
   * @param requestPermissionCallback
   * @param activity
   */
  public UsbBroadcastReceiver(
    RequestPermissionCallback requestPermissionCallback,
    Activity activity,
    UsbDevice deviceRequest
  ) {
    this.requestPermissionCallback = requestPermissionCallback;
    this.activity = activity;
    this.deviceRequest = deviceRequest;
  }

  /**
   * Handle permission answer
   * @param context
   * @param intent
   * @see android.content.BroadcastReceiver#onReceive(android.content.Context, android.content.Intent)
   */
  @Override
  public void onReceive(Context context, Intent intent) {
    String action = intent.getAction();
    Log.d(TAG, "Action: " + action);

    if (USB_PERMISSION.equals(action)) {
      synchronized (this) {
        // if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
        Log.d(TAG, "Permission to connect to the device was accepted!");
        try {
          JSONObject json = new JSONObject();

          json.put("deviceId", deviceRequest.getDeviceId());
          json.put("deviceName", deviceRequest.getDeviceName());
          json.put("vendorId", deviceRequest.getVendorId());
          json.put("productId", deviceRequest.getProductId());
          json.put("deviceClass", deviceRequest.getDeviceClass());
          json.put("deviceSubclass", deviceRequest.getDeviceSubclass());
          json.put("deviceProtocol", deviceRequest.getDeviceProtocol());
          json.put("version", deviceRequest.getVersion());
          json.put("interfaces", deviceRequest.getInterfaceCount());

          requestPermissionCallback.success(deviceRequest);
        } catch (JSONException e) {
          Log.d(TAG, "Error creating JSON object: " + e.getMessage());
        }
        // } else {
        //   Log.d(TAG, "Permission to connect to the device was denied!");
        //   requestPermissionCallback.error("Permission to connect to the device was denied!");
        // }
      }
    } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
      UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
      Log.d(TAG, "USB Device Attached: " + device);
      requestPermissionCallback.success(deviceRequest);
    } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
      UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
      Log.d(TAG, "USB Device Detached: " + device);
      requestPermissionCallback.success(deviceRequest);
    }

    activity.unregisterReceiver(this);
  }
}
