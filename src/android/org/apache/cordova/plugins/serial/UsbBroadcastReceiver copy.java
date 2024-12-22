package org.apache.cordova.plugins.serial;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;
import java.util.HashMap;
import org.apache.cordova.CallbackContext;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Custom {@link BroadcastReceiver} that can talk through a cordova {@link CallbackContext}
 */
public class UsbBroadcastReceiver extends BroadcastReceiver {

  private final String TAG = UsbBroadcastReceiver.class.getSimpleName();
  public static final String USB_PERMISSION = "org.apache.cordova.plugins.serial.USB_PERMISSION";

  private RequestPermissionCallback requestPermissionCallback;
  private Activity activity;
  private UsbDevice device;

  public UsbBroadcastReceiver(
    RequestPermissionCallback requestPermissionCallback,
    Activity activity,
    UsbDevice device
  ) {
    this.requestPermissionCallback = requestPermissionCallback;
    this.activity = activity;
    this.device = device;
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    String action = intent.getAction();
    Log.d(TAG, "Received broadcast with action: " + action);

    try {
      if (USB_PERMISSION.equals(action)) {
        Log.d(TAG, "Handling USB permission response.");
        handlePermissionResponse();
      } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
        Log.d(TAG, "Handling USB device attached event.");
        handleDeviceAttached(intent);
      } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
        Log.d(TAG, "Handling USB device detached event.");
        handleDeviceDetached(intent);
      }
    } catch (Exception e) {
      Log.e(TAG, "Error handling broadcast: " + e.getMessage(), e);
    } finally {
      Log.d(TAG, "Unregistering UsbBroadcastReceiver.");
      activity.unregisterReceiver(this);
    }
  }

  /**
   * Handles the response for USB permission.
   */
  private void handlePermissionResponse() {
    synchronized (this) {
      Log.d(TAG, "USB permission granted.");
      try {
        requestPermissionCallback.onComplete(device);
      } catch (Exception e) {
        Log.e(TAG, "Error handling USB permission: " + e.getMessage(), e);
      }
    }
  }

  /**
   * Handles USB device attachment event.
   */
  private void handleDeviceAttached(Intent intent) {
    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
    Log.d(TAG, "USB device attached: " + (device != null ? device.getDeviceName() : "null"));
    requestPermissionCallback.onComplete(device);
  }

  /**
   * Handles USB device detachment event.
   */
  private void handleDeviceDetached(Intent intent) {
    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
    Log.d(TAG, "USB device detached: " + (device != null ? device.getDeviceName() : "null"));
    requestPermissionCallback.onComplete(device);
  }

  /**
   * Create a JSON object with device information.
   */
  private JSONObject createDeviceInfoJson(UsbDevice device) {
    JSONObject json = new JSONObject();
    try {
      json.put("deviceId", device.getDeviceId());
      json.put("deviceName", device.getDeviceName());
      json.put("vendorId", device.getVendorId());
      json.put("productId", device.getProductId());
      json.put("deviceClass", device.getDeviceClass());
      json.put("deviceSubclass", device.getDeviceSubclass());
      json.put("deviceProtocol", device.getDeviceProtocol());
      json.put("version", device.getVersion());
      json.put("interfaces", device.getInterfaceCount());
      Log.d(TAG, "Device info JSON created: " + json.toString());
    } catch (JSONException e) {
      Log.e(TAG, "Error creating device info JSON", e);
    }
    return json;
  }
}
