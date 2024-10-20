const serial = {
  requestPermission: function (opts, successCallback, errorCallback) {
    if (typeof opts === "function") {
      //user did not pass opts
      errorCallback = successCallback;
      successCallback = opts;
      opts = {};
    }
    cordova.exec(
      successCallback,
      errorCallback,
      "Serial",
      "requestPermission",
      [{ opts: opts }],
    );
  },
  readSerialByDeviceId: function (opts, successCallback, errorCallback) {
    cordova.exec(
      successCallback,
      errorCallback,
      "Serial",
      "readSerialByDeviceId",
      [{ opts: opts }],
    );
  },
  getActiveDevices: function (successCallback, errorCallback) {
    cordova.exec(
      successCallback,
      errorCallback,
      "Serial",
      "getActiveDevices",
      [],
    );
  },
  open: function (opts, successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, "Serial", "openSerial", [
      { opts: opts },
    ]);
  },
  write: function (data, successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, "Serial", "writeSerial", [
      { data: data },
    ]);
  },
  writeHex: function (hexString, successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, "Serial", "writeSerialHex", [
      { data: hexString },
    ]);
  },
  read: function (successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, "Serial", "readSerial", []);
  },
  close: function (successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, "Serial", "closeSerial", []);
  },
  registerReadCallback: function (successCallback, errorCallback) {
    cordova.exec(
      successCallback,
      errorCallback,
      "Serial",
      "registerReadCallback",
      [],
    );
  },
};
module.exports = serial;
