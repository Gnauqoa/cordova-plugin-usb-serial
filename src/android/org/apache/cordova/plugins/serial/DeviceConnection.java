package org.apache.cordova.plugins.serial;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

public class DeviceConnection {

  private UsbDevice device;
  private UsbDeviceConnection connection;

  public DeviceConnection(UsbDevice device, UsbDeviceConnection connection) {
    this.device = device;
    this.connection = connection;
  }

  public UsbDevice getDevice() {
    return device;
  }

  public void setDevice(UsbDevice device) {
    this.device = device;
  }

  public UsbDeviceConnection getConnection() {
    return connection;
  }

  public void setConnection(UsbDeviceConnection connection) {
    this.connection = connection;
  }
}
