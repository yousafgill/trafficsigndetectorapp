package com.duvallsoftware.odbhelpers;

import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import com.duvallsoftware.odbhelpers.ObdCommandJob.ObdCommandJobState;
import com.duvallsoftware.trafficsigndetector.TrafficSignDetectorActivity;

import pt.lighthouselabs.obd.commands.SpeedObdCommand;
import pt.lighthouselabs.obd.commands.protocol.EchoOffObdCommand;
import pt.lighthouselabs.obd.commands.protocol.LineFeedOffObdCommand;
import pt.lighthouselabs.obd.commands.protocol.ObdResetCommand;
import pt.lighthouselabs.obd.commands.protocol.SelectProtocolObdCommand;
import pt.lighthouselabs.obd.commands.protocol.TimeoutObdCommand;
import pt.lighthouselabs.obd.commands.temperature.AmbientAirTemperatureObdCommand;
import pt.lighthouselabs.obd.enums.ObdProtocols;

/**
 * This class uses some base code from the OBD Reader application from
 * com.github.pires.obd.reader
 */
public class MockObdGatewayService extends AbstractGatewayService {

	private static final String TAG = TrafficSignDetectorActivity.TAG;

	public void startService() {
		Log.d(TAG, "Starting " + this.getClass().getName() + " service..");

		// Let's configure the connection.
		Log.d(TAG, "Queing jobs for connection configuration..");
		queueJob(new ObdCommandJob(new ObdResetCommand()));
		queueJob(new ObdCommandJob(new EchoOffObdCommand()));

		/*
		 * Will send second-time based on tests.
		 * 
		 * TODO this can be done w/o having to queue jobs by just issuing
		 * command.run(), command.getResult() and validate the result.
		 */
		queueJob(new ObdCommandJob(new EchoOffObdCommand()));
		queueJob(new ObdCommandJob(new LineFeedOffObdCommand()));
		queueJob(new ObdCommandJob(new TimeoutObdCommand(62)));

		// For now set protocol to AUTO
		queueJob(new ObdCommandJob(new SelectProtocolObdCommand(ObdProtocols.AUTO)));

		// Job for returning dummy data
		queueJob(new ObdCommandJob(new SpeedObdCommand()));

		queueCounter = 0L;
		Log.d(TAG, "Initialization jobs queued.");

		isRunning = true;
	}

	/**
	 * Runs the queue until the service is stopped
	 */
	protected void executeQueue() {
		Log.d(TAG, "Executing queue..");
		while (!Thread.currentThread().isInterrupted()) {
			ObdCommandJob job = null;
			try {
				job = jobsQueue.take();

				Log.d(TAG, "Taking job[" + job.getId() + "] from queue..");

				if (job.getState().equals(ObdCommandJobState.NEW)) {
					Log.d(TAG, "Job state is NEW. Run it..");
					job.setState(ObdCommandJobState.RUNNING);
					Log.d(TAG, job.getCommand().getName());
					job.getCommand().run(new ByteArrayInputStream("41 00 00 00>41 00 00 00>41 00 00 00>".getBytes()),
							new ByteArrayOutputStream());
				} else {
					Log.e(TAG, "Job state was not new, so it shouldn't be in queue. BUG ALERT!");
				}
			} catch (InterruptedException i) {
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				e.printStackTrace();
				job.setState(ObdCommandJobState.EXECUTION_ERROR);
				Log.e(TAG, "Failed to run command. -> " + e.getMessage());
			}

			if (job != null) {
				Log.d(TAG, "Job is finished.");
				job.setState(ObdCommandJobState.FINISHED);
				final ObdCommandJob job2 = job;
				((TrafficSignDetectorActivity) ctx).runOnUiThread(new Runnable() {
					@Override
					public void run() {
						((TrafficSignDetectorActivity) ctx).stateUpdate(job2);
					}
				});

			}
		}
	}

	/**
	 * Stop OBD connection and queue processing.
	 */
	public void stopService() {
		Log.d(TAG, "Stopping service..");

		notificationManager.cancel(NOTIFICATION_ID);
		jobsQueue.removeAll(jobsQueue);
		isRunning = false;

		// kill service
		stopSelf();
	}

}