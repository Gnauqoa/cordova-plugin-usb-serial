package org.apache.cordova.plugins.serial;

import org.apache.cordova.CallbackContext;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;

import org.apache.cordova.CallbackContext;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Broadcast receiver to handle USB permission results and device connections
 */
public class UsbBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "UsbBroadcastReceiver";
    public static final String USB_PERMISSION = "org.apache.cordova.plugins.serial.USB_PERMISSION";

    private CallbackContext callbackContext;
    private Context context;
    private UsbManager usbManager;

    public UsbBroadcastReceiver(CallbackContext callbackContext, Context context) {
        this.callbackContext = callbackContext;
        this.context = context;
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

        if (USB_PERMISSION.equals(action)) {
            synchronized (this) {
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    if (device != null) {
                        Log.d(TAG, "Permission granted for device " + device.getDeviceId());
                        // Handle multiple devices connection
                        handleDeviceConnection(device);
                    }
                } else {
                    Log.d(TAG, "Permission denied for device " + device.getDeviceId());
                    callbackContext.error("Permission denied for device " + device.getDeviceId());
                }
            }
        }
    }

    private void handleDeviceConnection(UsbDevice device) {
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while (deviceIterator.hasNext()) {
            UsbDevice usbDevice = deviceIterator.next();
            if (usbDevice.getDeviceId() == device.getDeviceId()) {
                // Log device information for debugging
                Log.d(TAG, "Connected to USB device: " + usbDevice.getDeviceId());
                // callbackContext.success("Connected to USB device: " + usbDevice.getDeviceId());
            }
        }
    }

    public static IntentFilter getUsbIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(USB_PERMISSION);
        return filter;
    }
}
