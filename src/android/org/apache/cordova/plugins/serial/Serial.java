package org.apache.cordova.plugins.serial;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Base64;
import android.util.Log;
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.Ch34xSerialDriver;
import com.hoho.android.usbserial.driver.Cp21xxSerialDriver;
import com.hoho.android.usbserial.driver.FtdiSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.ProlificSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.security.auth.callback.Callback;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Cordova plugin to communicate with the android serial port
 * @author Xavier Seignard <xavier.seignard@gmail.com>
 */
public class Serial extends CordovaPlugin {

  // logging tag
  private final String TAG = Serial.class.getSimpleName();
  // actions definitions
  private static final String ACTION_REQUEST_PERMISSION = "requestPermission";
  private static final String ACTION_OPEN = "openSerial";
  private static final String ACTION_READ = "readSerial";
  private static final String ACTION_WRITE = "writeSerial";
  private static final String ACTION_WRITE_HEX = "writeSerialHex";
  private static final String ACTION_CLOSE = "closeSerial";
  private static final String ACTION_READ_CALLBACK = "registerReadCallback";
  private static final String ACTION_GET_DEVICES = "getDevices";
  private static final String ACTION_READ_BY_DEVICE_ID = "readSerialByDeviceId";
  private static final String ACTION_WRITE_BY_DEVICE_ID = "writeSerialByDeviceId";
  private static final String ACTION_OPEN_BY_DEVICE_ID = "openSerialByDeviceId";
  private static final String ACTION_REGISTER_DETACH_CALLBACK = "registerDetachCallback";
  // UsbManager instance to deal with permission and opening
  private UsbManager manager;
  // The current driver that handle the serial port
  private UsbSerialDriver driver;
  // The serial port that will be used in this plugin
  private UsbSerialPort port;
  // Read buffer, and read params
  private static final int READ_WAIT_MILLIS = 200;
  private static final int BUFSIZ = 4096;
  private final ByteBuffer mReadBuffer = ByteBuffer.allocate(BUFSIZ);
  // Connection info
  private int previousOpenDeviceId = -1;
  private int baudRate;
  private int dataBits;
  private int stopBits;
  private int parity;
  private boolean setDTR;
  private boolean setRTS;
  private boolean sleepOnPause;

  private HashMap<Integer, DeviceConnection> deviceConnections = new HashMap<>();
  // callback that will be used to send back data to the cordova app
  private CallbackContext readCallback;
  private CallbackContext detachCallback;

  // I/O manager to handle new incoming serial data
  private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
  private SerialInputOutputManager mSerialIoManager;
  private final SerialInputOutputManager.Listener mListener = new SerialInputOutputManager.Listener() {
    @Override
    public void onRunError(Exception e) {
      // Log.d(TAG, "Runner stopped.");
    }

    @Override
    public void onNewData(final byte[] data) {
      Serial.this.updateReceivedData(data);
    }
  };

  /**
   * Overridden execute method
   * @param action the string representation of the action to execute
   * @param callbackContext the cordova {@link CallbackContext}
   * @return true if the action exists, false otherwise
   * @throws JSONException if the args parsing fails
   */
  @Override
  public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
    // Log.d(TAG, "Action: " + action);
    JSONObject arg_object = args.optJSONObject(0);
    // request permission
    if (ACTION_REQUEST_PERMISSION.equals(action)) {
      JSONObject opts = arg_object.has("opts") ? arg_object.getJSONObject("opts") : new JSONObject();
      requestPermission(opts, callbackContext);
      return true;
    }
    // open serial port
    else if (ACTION_OPEN.equals(action)) {
      JSONObject opts = arg_object.has("opts") ? arg_object.getJSONObject("opts") : new JSONObject();
      openSerial(opts, callbackContext);
      return true;
    }
    // write to the serial port
    else if (ACTION_WRITE.equals(action)) {
      String data = arg_object.getString("data");
      writeSerial(data, callbackContext);
      return true;
    }
    // write hex to the serial port
    else if (ACTION_WRITE_HEX.equals(action)) {
      String data = arg_object.getString("data");
      writeSerialHex(data, callbackContext);
      return true;
    }
    // read on the serial port
    else if (ACTION_READ.equals(action)) {
      readSerial(callbackContext);
      return true;
    }
    // close the serial port
    else if (ACTION_CLOSE.equals(action)) {
      closeSerial(callbackContext);
      return true;
    }
    // Register read callback
    else if (ACTION_READ_CALLBACK.equals(action)) {
      registerReadCallback(callbackContext);
      return true;
    }
    // Get active devices
    else if (ACTION_GET_DEVICES.equals(action)) {
      getDevices(callbackContext);
      return true;
    }
    // Read by device id
    else if (ACTION_READ_BY_DEVICE_ID.equals(action)) {
      JSONObject opts = arg_object.has("opts") ? arg_object.getJSONObject("opts") : new JSONObject();
      readSerialByDeviceId(opts, callbackContext);
      return true;
    } else if (ACTION_WRITE_BY_DEVICE_ID.equals(action)) {
      JSONObject opts = arg_object.has("opts") ? arg_object.getJSONObject("opts") : new JSONObject();
      writeSerialByDeviceId(opts, callbackContext);
      return true;
    } else if (ACTION_OPEN_BY_DEVICE_ID.equals(action)) {
      JSONObject opts = arg_object.has("opts") ? arg_object.getJSONObject("opts") : new JSONObject();
      openSerialByDeviceId(opts, callbackContext);
      return true;
    } else if (ACTION_REGISTER_DETACH_CALLBACK.equals(action)) {
      registerDetachCallback(callbackContext);
      return true;
    }
    // the action doesn't exist
    return false;
  }

  private void registerDetachCallback(final CallbackContext callbackContext) {
    detachCallback = callbackContext;

    JSONObject returnObj = new JSONObject();
    addProperty(returnObj, "registerDetachCallback", "true");

    PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
    pluginResult.setKeepCallback(true);
    callbackContext.sendPluginResult(pluginResult);
  }

  /**
   * Connect to device
   * @param callbackContext
   */
  private void openSerialByDeviceId(final JSONObject opts, final CallbackContext callbackContext) {
    cordova
      .getThreadPool()
      .execute(
        new Runnable() {
          public void run() {
            if (driver == null) {
              callbackContext.error("Request permissions before attempting opening port");
              return;
            }
            try {
              int deviceId = opts.has("deviceId") ? opts.getInt("deviceId") : 1013;
              UsbDevice device = findDeviceWithId(deviceId);

              UsbDeviceConnection connection = manager.openDevice(device);

              if (connection != null) {
                // get first port and open it
                port = driver.getPorts().get(0);

                // get connection params or the default values
                baudRate = opts.has("baudRate") ? opts.getInt("baudRate") : 115200;
                dataBits = opts.has("dataBits") ? opts.getInt("dataBits") : UsbSerialPort.DATABITS_8;
                stopBits = opts.has("stopBits") ? opts.getInt("stopBits") : UsbSerialPort.STOPBITS_1;
                parity = opts.has("parity") ? opts.getInt("parity") : UsbSerialPort.PARITY_NONE;
                setDTR = opts.has("dtr") && opts.getBoolean("dtr");
                setRTS = opts.has("rts") && opts.getBoolean("rts");
                // Sleep On Pause defaults to true
                sleepOnPause = !opts.has("sleepOnPause") || opts.getBoolean("sleepOnPause");

                port.open(connection);
                port.setParameters(baudRate, dataBits, stopBits, parity);
                if (setDTR) port.setDTR(true);
                if (setRTS) port.setRTS(true);

                // Log.d(TAG, "Serial port opened!");
                callbackContext.success("Serial port opened!");
              } else {
                // Log.d(TAG, "Cannot connect to the device!");
                callbackContext.error("Cannot connect to the device!");
              }

              onDeviceStateChange();
            } catch (IOException | JSONException e) {
              // deal with error
              // Log.d(TAG, Objects.requireNonNull(e.getMessage()));
              callbackContext.error(e.getMessage());
            }
          }
        }
      );
  }

  /**
   * Overridden execute method
   * @param callbackContext the cordova {@link CallbackContext}
   * @return true if the action exists, false otherwise
   * @throws JSONException if the args parsing fails
   */
  private void getDevices(final CallbackContext callbackContext) {
    // Get the USB service
    manager = (UsbManager) cordova.getActivity().getSystemService(Context.USB_SERVICE);

    if (manager == null) {
      Log.e(TAG, "UsbManager is null");
      callbackContext.error("UsbManager is unavailable");
      return;
    }

    HashMap<String, UsbDevice> deviceList = manager.getDeviceList();

    if (deviceList.isEmpty()) {
      // Log.d(TAG, "No USB devices found");
      callbackContext.error("No USB devices found");
      return;
    }

    // Create a JSON array to hold device information
    JSONArray devicesArray = new JSONArray();

    Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

    // Iterate through each device and add its info to the JSON array
    while (deviceIterator.hasNext()) {
      UsbDevice usbDevice = deviceIterator.next();

      try {
        JSONObject json = new JSONObject();

        json.put("deviceName", usbDevice.getDeviceName());
        json.put("vendorId", usbDevice.getVendorId());
        json.put("productId", usbDevice.getProductId());
        json.put("deviceClass", usbDevice.getDeviceClass());
        json.put("deviceSubclass", usbDevice.getDeviceSubclass());
        json.put("deviceProtocol", usbDevice.getDeviceProtocol());
        json.put("version", usbDevice.getVersion());
        json.put("interfaces", usbDevice.getInterfaceCount());
        json.put("deviceId", usbDevice.getDeviceId());
        // Add the device info to the JSON array
        devicesArray.put(json);
      } catch (JSONException e) {
        Log.e(TAG, "Error creating JSON object for USB device", e);
        callbackContext.error("Error creating JSON object for USB device");
        return;
      }
    }

    // Send the JSON array back as the success callback
    callbackContext.success(devicesArray);
  }

  private void checkConnectionsList() {
    if (deviceConnections.isEmpty()) {
      return;
    }

    manager = (UsbManager) cordova.getActivity().getSystemService(Context.USB_SERVICE);

    HashMap<String, UsbDevice> deviceList = manager.getDeviceList();

    if (deviceList.isEmpty()) {
      // Log.d(TAG, "No USB devices found");

      for (DeviceConnection deviceConnection : deviceConnections.values()) {
        UsbDevice device = deviceConnection.getDevice();
        UsbDeviceConnection connection = deviceConnection.getConnection();
        if (connection != null) {
          connection.close();
        }
        deviceConnections.remove(device.getDeviceId());

        JSONObject returnObj = new JSONObject();
        addProperty(returnObj, "device", deviceToJSONObj(device));

        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
        pluginResult.setKeepCallback(true);
        detachCallback.sendPluginResult(pluginResult);
      }
      return;
    }

    for (DeviceConnection deviceConnection : deviceConnections.values()) {
      UsbDevice device = deviceConnection.getDevice();
      if (!deviceList.containsValue(device)) {
        UsbDeviceConnection connection = deviceConnection.getConnection();

        connection.close();

        deviceConnections.remove(device.getDeviceId());

        JSONObject returnObj = new JSONObject();
        addProperty(returnObj, "device", deviceToJSONObj(device));

        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
        pluginResult.setKeepCallback(true);
        detachCallback.sendPluginResult(pluginResult);
      }
    }
  }

  /**
   * Request permission the the user for the app to use the USB/serial port
   * @param callbackContext the cordova {@link CallbackContext}
   */
  private void requestPermission(final JSONObject opts, final CallbackContext callbackContext) {
    cordova
      .getThreadPool()
      .execute(
        new Runnable() {
          public void run() {
            // get UsbManager from Android
            manager = (UsbManager) cordova.getActivity().getSystemService(Context.USB_SERVICE);
            UsbSerialProber prober;

            if (opts.has("vid") && opts.has("pid")) {
              ProbeTable customTable = new ProbeTable();
              Object o_vid = opts.opt("vid"); //can be an integer Number or a hex String
              Object o_pid = opts.opt("pid"); //can be an integer Number or a hex String
              int vid = o_vid instanceof Number ? ((Number) o_vid).intValue() : Integer.parseInt((String) o_vid, 16);
              int pid = o_pid instanceof Number ? ((Number) o_pid).intValue() : Integer.parseInt((String) o_pid, 16);
              String driver = opts.has("driver") ? (String) opts.opt("driver") : "CdcAcmSerialDriver";

              assert driver != null;
              switch (driver) {
                case "FtdiSerialDriver":
                  customTable.addProduct(vid, pid, FtdiSerialDriver.class);
                  break;
                case "CdcAcmSerialDriver":
                  customTable.addProduct(vid, pid, CdcAcmSerialDriver.class);
                  break;
                case "Cp21xxSerialDriver":
                  customTable.addProduct(vid, pid, Cp21xxSerialDriver.class);
                  break;
                case "ProlificSerialDriver":
                  customTable.addProduct(vid, pid, ProlificSerialDriver.class);
                  break;
                case "Ch34xSerialDriver":
                  customTable.addProduct(vid, pid, Ch34xSerialDriver.class);
                  break;
                default:
                  // Log.d(TAG, "Unknown driver!");
                  callbackContext.error("Unknown driver!");
                  break;
              }

              prober = new UsbSerialProber(customTable);
            } else {
              // find all available drivers from attached devices.
              prober = UsbSerialProber.getDefaultProber();
            }

            List<UsbSerialDriver> availableDrivers = prober.findAllDrivers(manager);
            checkConnectionsList();
            if (!availableDrivers.isEmpty()) {
              // get the first one as there is a high chance that there is no more than one usb device attached to your android
              // Lấy thiết bị và yêu cầu quyền truy cập
              driver = availableDrivers.get(0);

              UsbDevice device = driver.getDevice();
              int deviceId = device.getDeviceId();
              if (deviceConnections.containsKey(deviceId)) {
                // Log.d(TAG, "Device already connected!");
                callbackContext.success("Device already connected!");
                return;
              }

              // Tạo intent dùng để yêu cầu quyền truy cập thiết bị
              PendingIntent pendingIntent = PendingIntent.getBroadcast(
                cordova.getActivity(),
                0,
                new Intent(UsbBroadcastReceiver.USB_PERMISSION),
                PendingIntent.FLAG_IMMUTABLE
              );

              // Tạo IntentFilter để lắng nghe các sự kiện gắn và tháo thiết bị USB
              IntentFilter filter = new IntentFilter();
              filter.addAction(UsbBroadcastReceiver.USB_PERMISSION);
              filter.addAction(manager.ACTION_USB_DEVICE_ATTACHED);
              filter.addAction(manager.ACTION_USB_DEVICE_DETACHED);

              // Tạo BroadcastReceiver để xử lý các sự kiện
              UsbBroadcastReceiver usbReceiver = new UsbBroadcastReceiver(
                new RequestPermissionCallback() {
                  @Override
                  void success(UsbDevice device) {
                    DeviceConnection deviceConnection = new DeviceConnection(device);

                    deviceConnections.put(device.getDeviceId(), deviceConnection);
                    // Log.d(TAG, "Permission to connect to the device was accepted!");
                    JSONObject returnObj = new JSONObject();
                    addProperty(returnObj, "device", deviceToJSONObj(device));

                    PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
                    pluginResult.setKeepCallback(true);
                    callbackContext.sendPluginResult(pluginResult);
                  }
                },
                cordova.getActivity(),
                device
              );
              cordova.getActivity().registerReceiver(usbReceiver, filter, cordova.getActivity().RECEIVER_EXPORTED);

              // Yêu cầu quyền truy cập thiết bị USB
              manager.requestPermission(device, pendingIntent);
            } else {
              // Log.d(TAG, "No device found!");
              callbackContext.error("No device found!");
            }
          }
        }
      );
  }

  private DeviceConnection addDeviceConnection(UsbDevice device) {
    int deviceId = device.getDeviceId();
    if (deviceConnections.containsKey(deviceId)) {
      // Log.d(TAG, "Device already connected!");
      return deviceConnections.get(deviceId);
    }

    DeviceConnection deviceConnection = new DeviceConnection(device);
    deviceConnections.put(device.getDeviceId(), deviceConnection);

    return deviceConnection;
  }

  private void runOpenSerial(
    final JSONObject opts,
    final CallbackContext callbackContext,
    DeviceConnection deviceConnection
  ) {
    UsbDevice device = deviceConnection.getDevice();
    UsbDeviceConnection connection = null;

    if (!deviceConnection.isOpen()) {
      Log.d(TAG, "Device not open, opening it now");
      runCloseSerial(callbackContext);
      connection = manager.openDevice(device);
      deviceConnection.setConnection(connection);
    } else {
      Log.d(TAG, "Device already open");
      // connection = deviceConnection.getConnection();
      return;
    }

    // get first port and open it
    port = driver.getPorts().get(0);
    try {
      // get connection params or the default values
      baudRate = opts.has("baudRate") ? opts.getInt("baudRate") : 9600;
      dataBits = opts.has("dataBits") ? opts.getInt("dataBits") : UsbSerialPort.DATABITS_8;
      stopBits = opts.has("stopBits") ? opts.getInt("stopBits") : UsbSerialPort.STOPBITS_1;
      parity = opts.has("parity") ? opts.getInt("parity") : UsbSerialPort.PARITY_NONE;
      setDTR = opts.has("dtr") && opts.getBoolean("dtr");
      setRTS = opts.has("rts") && opts.getBoolean("rts");
      // Sleep On Pause defaults to true
      sleepOnPause = !opts.has("sleepOnPause") || opts.getBoolean("sleepOnPause");

      port.open(connection);
      port.setParameters(baudRate, dataBits, stopBits, parity);
      if (setDTR) port.setDTR(true);
      if (setRTS) port.setRTS(true);

      previousOpenDeviceId = device.getDeviceId();
    } catch (IOException | JSONException e) {
      // deal with error
      // Log.d(TAG, Objects.requireNonNull(e.getMessage()));
      callbackContext.error(e.getMessage());
    }
  }

  /**
   * Open the serial port from Cordova
   * @param opts a {@link JSONObject} containing the connection paramters
   * @param callbackContext the cordova {@link CallbackContext}
   */
  private void openSerial(final JSONObject opts, final CallbackContext callbackContext) {
    cordova
      .getThreadPool()
      .execute(
        new Runnable() {
          public void run() {
            if (driver == null) {
              callbackContext.error("Request permissions before attempting opening port");
              return;
            }

            UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
            if (connection != null) {
              // get first port and open it
              port = driver.getPorts().get(0);
              try {
                // get connection params or the default values
                baudRate = opts.has("baudRate") ? opts.getInt("baudRate") : 9600;
                dataBits = opts.has("dataBits") ? opts.getInt("dataBits") : UsbSerialPort.DATABITS_8;
                stopBits = opts.has("stopBits") ? opts.getInt("stopBits") : UsbSerialPort.STOPBITS_1;
                parity = opts.has("parity") ? opts.getInt("parity") : UsbSerialPort.PARITY_NONE;
                setDTR = opts.has("dtr") && opts.getBoolean("dtr");
                setRTS = opts.has("rts") && opts.getBoolean("rts");
                // Sleep On Pause defaults to true
                sleepOnPause = !opts.has("sleepOnPause") || opts.getBoolean("sleepOnPause");

                port.open(connection);
                port.setParameters(baudRate, dataBits, stopBits, parity);
                if (setDTR) port.setDTR(true);
                if (setRTS) port.setRTS(true);
              } catch (IOException | JSONException e) {
                // deal with error
                // Log.d(TAG, Objects.requireNonNull(e.getMessage()));
                callbackContext.error(e.getMessage());
              }

              // Log.d(TAG, "Serial port opened!");
              callbackContext.success("Serial port opened!");
            } else {
              // Log.d(TAG, "Cannot connect to the device!");
              callbackContext.error("Cannot connect to the device!");
            }
            onDeviceStateChange();
          }
        }
      );
  }

  /**
   * Write on the serial port
   * @param data the {@link String} representation of the data to be written on the port
   * @param callbackContext the cordova {@link CallbackContext}
   */
  private void writeSerial(final String data, final CallbackContext callbackContext) {
    cordova
      .getThreadPool()
      .execute(() -> {
        if (port == null) {
          callbackContext.error("Writing a closed port.");
        } else {
          try {
            // Log.d(TAG, data);
            byte[] buffer = data.getBytes();
            port.write(buffer, 1000);
            callbackContext.success(buffer.length + "character written.");
          } catch (IOException | NullPointerException e) {
            // deal with error
            // Log.d(TAG, Objects.requireNonNull(e.getMessage()));
            callbackContext.error(e.getMessage());
          }
        }
      });
  }

  private boolean runWriteSerial(final String data, final CallbackContext callbackContext) {
    if (port == null) {
      callbackContext.error("Cannot write to a closed port.");
      return false;
    } else {
      try {
        // Log.d(TAG, "Writing data: " + data);
        byte[] buffer = data.getBytes();
        port.write(buffer, 1000); // Write data to the port with a 1-second timeout
        return true;
      } catch (IOException | NullPointerException e) {
        // Handle the error and report it
        // Log.d(TAG, "Error writing to port: " + Objects.requireNonNull(e.getMessage()));
        callbackContext.error("Error writing to port: " + e.getMessage());
        return false;
      }
    }
  }

  private void writeSerialByDeviceId(final JSONObject opts, final CallbackContext callbackContext) {
    cordova
      .getThreadPool()
      .execute(
        new Runnable() {
          public void run() {
            try {
              if (!opts.has("deviceId")) {
                // Log.d(TAG, "No device specified.");
                callbackContext.error("No device specified.");
                return;
              }

              if (!opts.has("data")) {
                // Log.d(TAG, "No data specified.");
                callbackContext.error("No data specified.");
                return;
              }

              DeviceConnection deviceConnection = deviceConnections.get(opts.getInt("deviceId"));

              if (deviceConnection == null) {
                // Log.d(TAG, "Device not found.");
                callbackContext.error("Device not found.");
                return;
              }

              runOpenSerial(opts, callbackContext, deviceConnection);

              String data = opts.getString("data");

              boolean status = runWriteSerial(data, callbackContext);

              if (status) {
                Log.d(TAG, "Data written successfully!");
                callbackContext.success("Data written successfully!");
              }
            } catch (JSONException e) {
              // deal with error
              // Log.d(TAG, Objects.requireNonNull(e.getMessage()));
              callbackContext.error(e.getMessage());
            }
          }
        }
      );
  }

  /**
   * Write hex on the serial port
   * @param data the {@link String} representation of the data to be written on the port as hexadecimal string
   *             e.g. "ff55aaeeef000233"
   * @param callbackContext the cordova {@link CallbackContext}
   */
  private void writeSerialHex(final String data, final CallbackContext callbackContext) {
    cordova
      .getThreadPool()
      .execute(() -> {
        if (port == null) {
          callbackContext.error("Writing a closed port.");
        } else {
          try {
            // Log.d(TAG, data);
            byte[] buffer = hexStringToByteArray(data);
            port.write(buffer, 1000);
            callbackContext.success(buffer.length + "bytes written.");
          } catch (IOException | StringIndexOutOfBoundsException | NullPointerException e) {
            // deal with error
            // Log.d(TAG, Objects.requireNonNull(e.getMessage()));
            callbackContext.error(e.getMessage());
          }
        }
      });
  }

  /**
   * Convert a given string of hexadecimal numbers
   * into a byte[] array where every 2 hex chars get packed into
   * a single byte.
   * E.g. "ffaa55" results in a 3 byte long byte array
   *
   */
  private byte[] hexStringToByteArray(String s) throws StringIndexOutOfBoundsException {
    int len = s.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
    }
    return data;
  }

  private UsbDevice findDeviceWithId(int deviceId) {
    HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
    if (deviceList.isEmpty()) {
      // Log.d(TAG, "No USB devices found");
      return null;
    }

    UsbDevice usbDevice = null;
    for (UsbDevice device : deviceList.values()) {
      if (device.getDeviceId() == deviceId) {
        // Log.d(TAG, "Device found in devices list: " + device.getDeviceName());
        usbDevice = device;
        break;
      }
    }

    return usbDevice; // Now it can return the UsbDevice or null
  }

  JSONObject deviceToJSONObj(UsbDevice device) {
    JSONObject json = new JSONObject();
    try {
      json.put("deviceName", device.getDeviceName());
      json.put("vendorId", device.getVendorId());
      json.put("productId", device.getProductId());
      json.put("deviceClass", device.getDeviceClass());
      json.put("deviceSubclass", device.getDeviceSubclass());
      json.put("deviceProtocol", device.getDeviceProtocol());
      json.put("version", device.getVersion());
      json.put("interfaces", device.getInterfaceCount());
      json.put("deviceId", device.getDeviceId());
    } catch (JSONException e) {
      Log.e(TAG, "Error creating JSON object for USB device", e);
    }
    return json;
  }

  /**
   * Open the serial port from Cordova
   * @param opts a {@link JSONObject} containing the connection paramters
   * @param callbackContext the cordova {@link CallbackContext}
   */
  private void readSerialByDeviceId(final JSONObject opts, final CallbackContext callbackContext) {
    cordova
      .getThreadPool()
      .execute(
        new Runnable() {
          public void run() {
            try {
              if (!opts.has("deviceId")) {
                // Log.d(TAG, "No device specified.");
                callbackContext.error("No device specified.");
                return;
              }

              DeviceConnection deviceConnection = deviceConnections.get(opts.getInt("deviceId"));
              if (deviceConnection == null) {
                // Log.d(TAG, "Device not found.");
                callbackContext.error("Device not found.");
                return;
              }

              runOpenSerial(opts, callbackContext, deviceConnection);

              // Ensure read operation completes before closing
              final byte[] data = runReadSerial(callbackContext, opts);

              // Closing the serial port after reading
              PluginResult.Status status = PluginResult.Status.OK;
              callbackContext.sendPluginResult(new PluginResult(status, data));
            } catch (JSONException e) {
              // deal with error
              // Log.d(TAG, Objects.requireNonNull(e.getMessage()));
              callbackContext.error(e.getMessage());
            }
          }
        }
      );
  }

  private final byte[] runReadSerial(final CallbackContext callbackContext, JSONObject opts) {
    if (port == null) {
      // Log.d(TAG, "Reading a closed port.");
      callbackContext.error("Reading a closed port.");
      return new byte[0]; // Return an empty byte array
    } else {
      try {
        int timeout = opts.has("timeout") ? opts.getInt("timeout") : READ_WAIT_MILLIS;
        // Log.d(TAG, "Read with timeout: " + timeout);
        int len = port.read(mReadBuffer.array(), timeout);
        // Whatever happens, we send an "OK" result, up to the
        // receiver to check that len > 0
        PluginResult.Status status = PluginResult.Status.OK;
        if (len > 0) {
          // Log.d(TAG, "Read data len=" + len);
          final byte[] data = new byte[len];
          mReadBuffer.get(data, 0, len);
          mReadBuffer.clear();
          return data;
        } else {
          final byte[] data = new byte[0];
          return data;
        }
      } catch (JSONException | IOException | NullPointerException e) {
        // deal with error
        // Log.d(TAG, Objects.requireNonNull(e.getMessage()));
        callbackContext.error(e.getMessage());
        return new byte[0]; // Return an empty byte array
      }
    }
  }

  private final byte[] runReadSerial(final CallbackContext callbackContext) {
    if (port == null) {
      // Log.d(TAG, "Reading a closed port.");
      callbackContext.error("Reading a closed port.");
      return new byte[0]; // Return an empty byte array
    } else {
      try {
        int len = port.read(mReadBuffer.array(), READ_WAIT_MILLIS);
        // Whatever happens, we send an "OK" result, up to the
        // receiver to check that len > 0
        PluginResult.Status status = PluginResult.Status.OK;
        if (len > 0) {
          // Log.d(TAG, "Read data len=" + len);
          final byte[] data = new byte[len];
          mReadBuffer.get(data, 0, len);
          mReadBuffer.clear();
          return data;
        } else {
          final byte[] data = new byte[0];
          return data;
        }
      } catch (IOException | NullPointerException e) {
        // deal with error
        // Log.d(TAG, Objects.requireNonNull(e.getMessage()));
        callbackContext.error(e.getMessage());
        return new byte[0]; // Return an empty byte array
      }
    }
  }

  /**
   * Read on the serial port
   * @param callbackContext the {@link CallbackContext}
   */
  private void readSerial(final CallbackContext callbackContext) {
    cordova
      .getThreadPool()
      .execute(
        new Runnable() {
          public void run() {
            final byte[] data = runReadSerial(callbackContext);
            PluginResult.Status status = PluginResult.Status.OK;
            callbackContext.sendPluginResult(new PluginResult(status, data));
          }
        }
      );
  }

  private void runCloseSerial(final CallbackContext callbackContext) {
    try {
      // Make sure we don't die if we try to close an non-existing port!
      if (port != null) {
        port.close();
        DeviceConnection deviceConnection = deviceConnections.get(previousOpenDeviceId);
        if (deviceConnection != null) {
          deviceConnection.setConnection(null);
        }
        Log.d(TAG, "Close device connection: " + previousOpenDeviceId);
      }
      port = null;
      // Log.d(TAG, "Serial port closed!");
    } catch (IOException | NullPointerException e) {
      // deal with error
      Log.d(TAG + "close err123:", Objects.requireNonNull(e.getMessage()));
      // callbackContext.error(e.getMessage());
    }
    onDeviceStateChange();
  }

  /**
   * Close the serial port
   * @param callbackContext the cordova {@link CallbackContext}
   */
  private void closeSerial(final CallbackContext callbackContext) {
    cordova
      .getThreadPool()
      .execute(
        new Runnable() {
          public void run() {
            try {
              // Make sure we don't die if we try to close an non-existing port!
              if (port != null) {
                port.close();
              }
              port = null;
              callbackContext.success("Serial port cloesd!");
            } catch (IOException | NullPointerException e) {
              // deal with error
              // Log.d(TAG, Objects.requireNonNull(e.getMessage()));
              callbackContext.error(e.getMessage());
            }
            onDeviceStateChange();
          }
        }
      );
  }

  /**
   * Stop observing serial connection
   */
  private void stopIoManager() {
    if (mSerialIoManager != null) {
      Log.i(TAG, "Stopping io manager.");
      mSerialIoManager.stop();
      mSerialIoManager = null;
    }
  }

  /**
   * Observe serial connection
   */
  private void startIoManager() {
    if (port != null) {
      Log.i(TAG, "Starting io manager.");
      mSerialIoManager = new SerialInputOutputManager(port, mListener);
      mExecutor.submit(mSerialIoManager);
    }
  }

  /**
   * Restart the observation of the serial connection
   */
  private void onDeviceStateChange() {
    // stopIoManager();
    // startIoManager();
  }

  /**
   * Dispatch read data to javascript
   * @param data the array of bytes to dispatch
   */
  private void updateReceivedData(byte[] data) {
    // Log.d(TAG, "Read data len=" + data.length);

    if (readCallback != null) {
      PluginResult result = new PluginResult(PluginResult.Status.OK, data);
      result.setKeepCallback(true);
      readCallback.sendPluginResult(result);
    }
  }

  /**
   * Register callback for read data
   * @param callbackContext the cordova {@link CallbackContext}
   */
  private void registerReadCallback(final CallbackContext callbackContext) {
    // Log.d(TAG, "Registering callback");
    cordova
      .getThreadPool()
      .execute(
        new Runnable() {
          public void run() {
            // Log.d(TAG, "Registering Read Callback");
            readCallback = callbackContext;
            JSONObject returnObj = new JSONObject();
            addProperty(returnObj, "registerReadCallback", "true");
            // Keep the callback
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
            pluginResult.setKeepCallback(true);
            callbackContext.sendPluginResult(pluginResult);
          }
        }
      );
  }

  /**
   * Paused activity handler
   * @see org.apache.cordova.CordovaPlugin#onPause(boolean)
   */
  @Override
  public void onPause(boolean multitasking) {
    if (sleepOnPause) {
      stopIoManager();
      if (port != null) {
        try {
          port.close();
        } catch (IOException | NullPointerException e) {
          // Ignore
        }
        port = null;
      }
    }
  }

  /**
   * Resumed activity handler
   * @see org.apache.cordova.CordovaPlugin#onResume(boolean)
   */
  @Override
  public void onResume(boolean multitasking) {
    // Log.d(TAG, "Resumed, driver=" + driver);
    if (sleepOnPause) {
      if (driver == null) {
        // Log.d(TAG, "No serial device to resume.");
      } else {
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        if (connection != null) {
          // get first port and open it
          port = driver.getPorts().get(0);
          try {
            port.open(connection);
            port.setParameters(baudRate, dataBits, stopBits, parity);
            if (setDTR) port.setDTR(true);
            if (setRTS) port.setRTS(true);
          } catch (IOException e) {
            // deal with error
            // Log.d(TAG, Objects.requireNonNull(e.getMessage()));
          }
          // Log.d(TAG, "Serial port opened!");
        } else {
          // Log.d(TAG, "Cannot connect to the device!");
        }
        // Log.d(TAG, "Serial device: " + driver.getClass().getSimpleName());
      }

      onDeviceStateChange();
    }
  }

  /**
   * Destroy activity handler
   * @see org.apache.cordova.CordovaPlugin#onDestroy()
   */
  @Override
  public void onDestroy() {
    // Log.d(TAG, "Destroy, port=" + port);
    if (port != null) {
      try {
        port.close();
      } catch (IOException | NullPointerException e) {
        // Log.d(TAG, Objects.requireNonNull(e.getMessage()));
      }
    }
    onDeviceStateChange();
  }

  /**
   * Utility method to add some properties to a {@link JSONObject}
   * @param obj the json object where to add the new property
   * @param key property key
   * @param value value of the property
   */
  private void addProperty(JSONObject obj, String key, Object value) {
    try {
      obj.put(key, value);
    } catch (JSONException ignored) {}
  }

  /**
   * Utility method to add some properties to a {@link JSONObject}
   * @param obj the json object where to add the new property
   * @param key property key
   * @param bytes the array of byte to add as value to the {@link JSONObject}
   */
  private void addPropertyBytes(JSONObject obj, String key, byte[] bytes) {
    String string = Base64.encodeToString(bytes, Base64.NO_WRAP);
    this.addProperty(obj, key, string);
  }
}
