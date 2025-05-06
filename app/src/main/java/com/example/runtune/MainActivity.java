package com.example.runtune;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.runtune.controller.MusicController;
import com.example.runtune.model.Music;
import com.example.runtune.service.GpsTrackingService;
import com.example.runtune.service.StepMusicService;
import com.example.runtune.util.DatabaseHelper;
import com.example.runtune.util.TrackingMode;

import java.util.*;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 101;
    private static final int PICK_AUDIO_REQUEST = 102;

    private TextView tvSteps, tvSpeed, tvDistance;
    private Button btnToggle;
    private ListView musicListView;

    private TrackingMode mode = TrackingMode.INDOOR;
    private DatabaseHelper dbHelper;
    private MusicController musicController;
    private boolean isMusicListVisible = false;

    private final BroadcastReceiver dataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case "STEP_UPDATE":
                    int steps = intent.getIntExtra("steps", 0);
                    double sDistance = intent.getDoubleExtra("distance", 0.0);
                    double sSpeed = intent.getDoubleExtra("speed", 0.0);
                    updateUI(steps, sDistance, sSpeed);
                    break;

                case "GPS_UPDATE":
                    double gDistance = intent.getDoubleExtra("distance", 0.0);
                    double gSpeed = intent.getDoubleExtra("speed", 0.0);
                    updateUI(-1, gDistance, gSpeed); // -1 berarti tidak update langkah
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPermissions();
        registerReceivers();
        initViews();

        dbHelper = new DatabaseHelper(this);
        musicController = new MusicController(this, findViewById(R.id.tvMusicStatus)); // digunakan hanya untuk set musik dari UI
        setupMusicUI();

        startTrackingServiceByMode();
    }

    private void initViews() {
        tvSteps = findViewById(R.id.tvSteps);
        tvSpeed = findViewById(R.id.tvSpeed);
        tvDistance = findViewById(R.id.tvDistance);
        btnToggle = findViewById(R.id.btnToggleMode);
        musicListView = findViewById(R.id.musicListView);

        btnToggle.setOnClickListener(v -> switchMode());
    }

    private void setupMusicUI() {
        Button btnUpload = findViewById(R.id.btnUpload);
        Button btnManage = findViewById(R.id.btnManageMusic);

        btnUpload.setOnClickListener(v -> openFilePicker());
        btnManage.setOnClickListener(v -> {
            isMusicListVisible = !isMusicListVisible;
            musicListView.setVisibility(isMusicListVisible ? View.VISIBLE : View.GONE);
            refreshMusicList();
        });

        musicListView.setOnItemClickListener((parent, view, position, id) -> {
            Music music = (Music) parent.getItemAtPosition(position);
            if (music != null) {
                dbHelper.setSelectedMusic(music.getId());

                // Restart StepMusicService agar membaca lagu baru dari DB
                stopService(new Intent(this, StepMusicService.class));
                ContextCompat.startForegroundService(this, new Intent(this, StepMusicService.class));

                musicController.setMusic(music.getFilePath(), false); // optional preview
            }
        });

        musicListView.setOnItemLongClickListener((parent, view, position, id) -> {
            Music music = (Music) parent.getItemAtPosition(position);
            if (music != null) {
                showMusicOptionsDialog(music);
            }
            return true;
        });



        refreshMusicList();
    }

    private void refreshMusicList() {
        List<Music> musics = dbHelper.getAllMusics();
        ArrayAdapter<Music> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, musics);
        musicListView.setAdapter(adapter);
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        startActivityForResult(intent, PICK_AUDIO_REQUEST);
    }

    @Override
    protected void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK_AUDIO_REQUEST && res == RESULT_OK && data != null) {
            Uri uri = data.getData();
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            String name = uri.getLastPathSegment();
            Music music = new Music(name, uri.toString());
            dbHelper.addMusic(music); // hanya simpan, jangan auto-select
            refreshMusicList();       // biar muncul di list
        }
    }

    private void showMusicOptionsDialog(Music music) {
        String[] options = {"Ganti Nama", "Hapus Lagu"};
        new AlertDialog.Builder(this)
                .setTitle(music.getTitle())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showRenameDialog(music);
                    else confirmDeleteMusic(music);
                })
                .show();
    }

    private void showRenameDialog(Music music) {
        EditText input = new EditText(this);
        input.setText(music.getTitle());

        new AlertDialog.Builder(this)
                .setTitle("Ganti Nama")
                .setView(input)
                .setPositiveButton("Simpan", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        dbHelper.renameMusic(music.getId(), newName);
                        refreshMusicList();
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void confirmDeleteMusic(Music music) {
        new AlertDialog.Builder(this)
                .setTitle("Hapus Lagu")
                .setMessage("Yakin ingin menghapus lagu ini?")
                .setPositiveButton("Hapus", (dialog, which) -> {
                    dbHelper.deleteMusic(music.getId());

                    // jika sedang diputar, hentikan
                    Music current = dbHelper.getSelectedMusic();
                    if (current != null && current.getId() == music.getId()) {
                        stopService(new Intent(this, StepMusicService.class));
                    }

                    refreshMusicList();
                })
                .setNegativeButton("Batal", null)
                .show();
    }



    private void updateUI(int steps, double distance, double speed) {
        if (steps >= 0) {
            tvSteps.setText("Steps: " + steps);
        }
        tvDistance.setText(String.format("Distance: %.2f m", distance));
        tvSpeed.setText(String.format("Speed: %.2f km/h", speed));
    }

    private void startTrackingServiceByMode() {
        if (mode == TrackingMode.INDOOR) startStepService();
        else startGpsService();
    }

    private void switchMode() {
        if (mode == TrackingMode.INDOOR) {
            stopStepService();
            startGpsService();
            mode = TrackingMode.OUTDOOR;
            btnToggle.setText("Switch to Indoor");
        } else {
            stopGpsService();
            startStepService();
            mode = TrackingMode.INDOOR;
            btnToggle.setText("Switch to Outdoor");
        }
    }

    private void startStepService() {
        Intent intent = new Intent(this, StepMusicService.class);
        ContextCompat.startForegroundService(this, intent);
    }

    private void stopStepService() {
        stopService(new Intent(this, StepMusicService.class));
    }

    private void startGpsService() {
        Intent intent = new Intent(this, GpsTrackingService.class);
        ContextCompat.startForegroundService(this, intent);
    }

    private void stopGpsService() {
        stopService(new Intent(this, GpsTrackingService.class));
    }

    private void registerReceivers() {
        registerReceiver(dataReceiver, new IntentFilter("STEP_UPDATE"), Context.RECEIVER_NOT_EXPORTED);
        registerReceiver(dataReceiver, new IntentFilter("GPS_UPDATE"), Context.RECEIVER_NOT_EXPORTED);
    }

    private void requestPermissions() {

        List<String> permissions = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissions.toArray(new String[0]),
                    REQUEST_PERMISSIONS
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                startTrackingServiceByMode(); // ⬅️ start service jika semua izin sudah granted
            } else {
                Toast.makeText(this, "Semua izin dibutuhkan agar aplikasi dapat berjalan", Toast.LENGTH_LONG).show();
            }
        }

    }

    

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(dataReceiver);
    }
}
