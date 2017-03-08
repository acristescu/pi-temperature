package com.example.pitepmerature;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.iotdata.AWSIotDataClient;
import com.amazonaws.services.iotdata.model.GetThingShadowRequest;
import com.amazonaws.services.iotdata.model.GetThingShadowResult;
import com.amazonaws.services.iotdata.model.UpdateThingShadowRequest;
import com.amazonaws.services.iotdata.model.UpdateThingShadowResult;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Properties;

/**
 * Created by acristescu on 06/03/2017.
 */

public class ShadowUpdater {
	private static final String THING_NAME = "AmbientStatus";
	private static final String PROPERTIES_FILE = "aws.properties";
	private static final int TIME_BETWEEN_UPDATES = 2000;

	private AWSIotDataClient iotDataClient;
	private float temperature;
	private float pressure;
	private float humidity;

	public ShadowUpdater(Context ctx) throws IOException {
		Properties props = new Properties();
		props.load(ctx.getAssets().open(PROPERTIES_FILE));
		AWSCredentials credentials = new PropertiesCredentials(ctx.getAssets().open(PROPERTIES_FILE));

		iotDataClient = new AWSIotDataClient(credentials);
		iotDataClient.setEndpoint(props.getProperty("endpoint"));

		new GetShadowTask(THING_NAME).execute();

		new Thread() {
			public void run() {
				while (true) {
					try {
						Thread.sleep(TIME_BETWEEN_UPDATES);
					} catch (InterruptedException e) {
					}
					UpdateShadowTask update = new UpdateShadowTask();
					update.setThingName(THING_NAME);

					JsonObject root = new JsonObject();
					JsonObject desired = new JsonObject();
					JsonObject state = new JsonObject();

					root.add("state", state);
					state.add("desired", desired);

					desired.addProperty("temperature", getTemperature());
					desired.addProperty("pressure", getPressure());
					desired.addProperty("humidity", getHumidity());

					update.setState(root.toString());
					update.execute();
				}
			}
		}.start();
	}

	public synchronized float getTemperature() {
		return temperature;
	}

	public synchronized void setTemperature(float temperature) {
		this.temperature = temperature;
	}

	public synchronized float getPressure() {
		return pressure;
	}

	public synchronized void setPressure(float pressure) {
		this.pressure = pressure;
	}

	public synchronized float getHumidity() {
		return humidity;
	}

	public synchronized void setHumidity(float humidity) {
		this.humidity = humidity;
	}

	private class GetShadowTask extends AsyncTask<Void, Void, AsyncTaskResult<String>> {

		private final String thingName;

		public GetShadowTask(String name) {
			thingName = name;
		}

		@Override
		protected AsyncTaskResult<String> doInBackground(Void... voids) {
			try {
				GetThingShadowRequest getThingShadowRequest = new GetThingShadowRequest()
						.withThingName(thingName);
				GetThingShadowResult result = iotDataClient.getThingShadow(getThingShadowRequest);
				byte[] bytes = new byte[result.getPayload().remaining()];
				result.getPayload().get(bytes);
				String resultString = new String(bytes);
				return new AsyncTaskResult<String>(resultString);
			} catch (Exception e) {
				Log.e("E", "getShadowTask", e);
				return new AsyncTaskResult<String>(e);
			}
		}

		@Override
		protected void onPostExecute(AsyncTaskResult<String> result) {
			if (result.getError() == null) {
				onStatusUpdated(result.getResult());
			} else {
				Log.e(GetShadowTask.class.getCanonicalName(), "getShadowTask", result.getError());
			}
		}
	}

	private void onStatusUpdated(String result) {
		Log.i(GetShadowTask.class.getCanonicalName(), result);

	}

	private class UpdateShadowTask extends AsyncTask<Void, Void, AsyncTaskResult<String>> {

		private String thingName;
		private String updateState;

		public void setThingName(String name) {
			thingName = name;
		}

		public void setState(String state) {
			updateState = state;
		}

		@Override
		protected AsyncTaskResult<String> doInBackground(Void... voids) {
			try {
				UpdateThingShadowRequest request = new UpdateThingShadowRequest();
				request.setThingName(thingName);

				ByteBuffer payloadBuffer = ByteBuffer.wrap(updateState.getBytes());
				request.setPayload(payloadBuffer);

				UpdateThingShadowResult result = iotDataClient.updateThingShadow(request);

				byte[] bytes = new byte[result.getPayload().remaining()];
				result.getPayload().get(bytes);
				String resultString = new String(bytes);
				return new AsyncTaskResult<String>(resultString);
			} catch (Exception e) {
				Log.e(UpdateShadowTask.class.getCanonicalName(), "updateShadowTask", e);
				return new AsyncTaskResult<String>(e);
			}
		}

		@Override
		protected void onPostExecute(AsyncTaskResult<String> result) {
			if (result.getError() == null) {
				Log.i(UpdateShadowTask.class.getCanonicalName(), result.getResult());
			} else {
				Log.e(UpdateShadowTask.class.getCanonicalName(), "Error in Update Shadow",
						result.getError());
			}
		}
	}

}
