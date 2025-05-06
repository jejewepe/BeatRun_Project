package com.example.runtune.service;

import android.app.*;
import android.content.*;
import android.hardware.*;
import android.os.*;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.runtune.R;
import com.example.runtune.controller.MusicController;
import com.example.runtune.model.Music;
import com.example.runtune.sensor.StepTracker;
import com.example.runtune.stats.StatsCalculator;
import com.example.runtune.util.Constants;
import com.example.runtune.util.DatabaseHelper;

public class StepMusicService extends Service implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor stepSensor;
    private static StepTracker stepTracker;
    private StatsCalculator statsCalculator;
    private MusicController musicController;
    private PowerManager.WakeLock wakeLock;
    private long lastStepTime = -1;

    private Handler intervalHandler;
    private Runnable intervalRunnable;

    private static final String CHANNEL_ID = "StepMusicChannel";

    @Override
    public void onCreate() {
        super.onCreate();
        acquireWakeLock();

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        stepTracker = new StepTracker();
        statsCalculator = new StatsCalculator(Constants.DEFAULT_STRIDE_LENGTH_METERS);
        musicController = new MusicController(this,null);

        Music music = new DatabaseHelper(this).getSelectedMusic();
        if (music != null) musicController.setMusic(music.getFilePath(),false);

        if (stepSensor != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_FASTEST);
        }

        createNotificationChannel();
        startForeground(2, new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("RunTune Step Service")
                .setContentText("Tracking steps")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()
        );

        // Interval logic from original MainActivity
        intervalHandler = new Handler(Looper.getMainLooper());
        intervalRunnable = () -> {
            int buffer = stepTracker.getStepBuffer();
            double distance = statsCalculator.calculateDistance(buffer);
            double speed = statsCalculator.calculateSpeed(distance, Constants.INACTIVITY_TIMEOUT_MS);

            boolean isMoving = buffer >= Constants.MIN_ACTIVE_STEPS;
            boolean isPlaying = musicController.isPlaying();

            if (!isMoving && isPlaying) {
                musicController.pause("Idle");
            } else if (isMoving && !isPlaying) {
                musicController.play();
            }

            // broadcast update
            Intent intent = new Intent("STEP_UPDATE");
            intent.putExtra("steps", stepTracker.getTotalSteps());
            intent.putExtra("distance", statsCalculator.calculateDistance(stepTracker.getTotalSteps()));
            intent.putExtra("speed", speed);
            sendBroadcast(intent);

            stepTracker.resetBuffer();
            intervalHandler.postDelayed(this.intervalRunnable, Constants.INACTIVITY_TIMEOUT_MS);
        };
        intervalHandler.postDelayed(intervalRunnable, Constants.INACTIVITY_TIMEOUT_MS);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (intervalHandler != null) intervalHandler.removeCallbacks(intervalRunnable);
        sensorManager.unregisterListener(this);
        musicController.pause("Stopped");
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RunTune::StepLock");
            wakeLock.acquire();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "RunTune Steps", NotificationManager.IMPORTANCE_LOW
            );
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
                stepTracker.processStep(event);
                long currentTime = System.currentTimeMillis();

                // Hitung real-time speed
                double speed = 0.0;
                if (lastStepTime > 0) {
                    long deltaTime = currentTime - lastStepTime;
                    if (deltaTime > 0) {
                        double distance = statsCalculator.calculateDistance(1);
                        speed = statsCalculator.calculateSpeed(distance, deltaTime); // km/h
                    }
                }
                lastStepTime = currentTime;

                // Musik logic berdasarkan speed real-time
                boolean isPlaying = musicController.isPlaying();
                if (speed >= 1.5 && !isPlaying) {
                    musicController.play();
                } else if (speed < 1.5 && isPlaying) {
                    musicController.pause("Slow movement");
                }

                // Broadcast semua data
                Intent intent = new Intent("STEP_UPDATE");
                intent.putExtra("steps", stepTracker.getTotalSteps());
                intent.putExtra("distance", statsCalculator.calculateDistance(stepTracker.getTotalSteps()));
                intent.putExtra("speed", speed);
                sendBroadcast(intent);
            }

        }



    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
