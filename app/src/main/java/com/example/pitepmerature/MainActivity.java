package com.example.pitepmerature;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;

import com.google.android.things.contrib.driver.bmx280.Bme280;
import com.google.android.things.contrib.driver.bmx280.Bme280SensorDriver;
import com.google.android.things.contrib.driver.bmx280.Bmx280;
import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;
import java.util.List;

public class MainActivity extends Activity {

	private static final String TAG = "MainActivity";
	private static final int ADDRESS = 0x76;
	private final PeripheralManagerService managerService = new PeripheralManagerService();

	private Bme280SensorDriver mSensorDriver;
	private SensorEventListener mListener;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		printDeviceId();
//		readSample();

		final SensorManager mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
		mListener = new SensorEventListener() {
			@Override
			public void onSensorChanged(SensorEvent sensorEvent) {
				String type = "";
				if(sensorEvent.sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
					type = "Temp: ";
				} else if(sensorEvent.sensor.getType() == Sensor.TYPE_PRESSURE) {
					type = "Pressure: ";
				} else if(sensorEvent.sensor.getType() == Sensor.TYPE_RELATIVE_HUMIDITY) {
					type = "Humidity: ";
				}
				Log.d(TAG, type + sensorEvent.values[0]);
			}

			@Override
			public void onAccuracyChanged(Sensor sensor, int i) {

			}
		};

		mSensorManager.registerDynamicSensorCallback(new SensorManager.DynamicSensorCallback() {
			@Override
			public void onDynamicSensorConnected(Sensor sensor) {
				if (sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE ||
						sensor.getType() == Sensor.TYPE_PRESSURE ||
						sensor.getType() == Sensor.TYPE_RELATIVE_HUMIDITY
						) {
					mSensorManager.registerListener(mListener, sensor,
							SensorManager.SENSOR_DELAY_NORMAL);
				}
			}
		});

		try {
			I2cDevice device = managerService.openI2cDevice(managerService.getI2cBusList().get(0), ADDRESS);
			mSensorDriver = new Bme280SensorDriver(device);
			mSensorDriver.registerTemperatureSensor();
			mSensorDriver.registerPressureSensor();
			mSensorDriver.registerHumiditySensor();
		} catch (IOException e) {
			Log.e(TAG, e.getMessage(), e);
		}
	}

	private void printDeviceId() {
		List<String> deviceList = managerService.getI2cBusList();
		if (deviceList.isEmpty()) {
			Log.i(TAG, "No I2C bus available on this device.");
		} else {
			Log.i(TAG, "List of available devices: " + deviceList);
		}
		I2cDevice device = null;
		try {
			device = managerService.openI2cDevice(deviceList.get(0), ADDRESS);
			Log.d(TAG, "Device ID byte: 0x" + Integer.toHexString(device.readRegByte(0xD0)));
		} catch (IOException|RuntimeException e) {
			Log.e(TAG, e.getMessage(), e);
		} finally {
			try {
				device.close();
			} catch (Exception ex) {
				Log.d(TAG, "Error closing device");
			}
		}
	}

	private void readSample() {
		try (Bme280 bmxDriver = new Bme280(managerService.openI2cDevice(managerService.getI2cBusList().get(0), ADDRESS))){
			bmxDriver.setTemperatureOversampling(Bmx280.OVERSAMPLING_1X);
			bmxDriver.setPressureOversampling(Bmx280.OVERSAMPLING_1X);
			bmxDriver.setHumidityOversampling(Bmx280.OVERSAMPLING_1X);

			bmxDriver.setMode(Bme280.MODE_NORMAL);
			for(int i = 0 ; i < 5 ; i++) {
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
				}
				Log.d(TAG, "Temperature: " + bmxDriver.readTemperature());
				Log.d(TAG, "Pressure: " + bmxDriver.readPressure());
				Log.d(TAG, "Humidity: " + bmxDriver.readHumidity());
			}


		} catch (IOException e) {
			Log.e(TAG, "Error during IO", e);
			// error reading temperature
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		try {
			((SensorManager)getSystemService(Context.SENSOR_SERVICE)).unregisterListener(mListener);
			mSensorDriver.unregisterTemperatureSensor();
			mSensorDriver.close();
		} catch (Exception e) {
			// error closing sensor
		}
	}
}
