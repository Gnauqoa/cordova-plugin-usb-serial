package org.apache.cordova.plugins.serial;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

public class DeviceConnection {

  private UsbDevice device = null;
  private UsbDeviceConnection connection = null;

  public DeviceConnection(UsbDevice device) {
    this.device = device;
    // this.connection = connection;
  }

  public UsbDevice getDevice() {
    return device;
  }

  public void close() {
    if (connection != null) {
      connection.close();
    }
  }

  public boolean isOpen() {
    return connection != null;
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
