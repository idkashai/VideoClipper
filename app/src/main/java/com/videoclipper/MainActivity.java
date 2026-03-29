package com.videoclipper;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;

import org.json.JSONObject;

import java.io.*;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.*;

public class MainActivity extends AppCompatActivity {

    private EditText etUrl;
    private Button btnLoad, btnDownload, btnAddClip;
    private ImageView ivThumbnail;
    private ProgressBar pbThumbnail, progressBar;
    private TextView tvVideoTitle, tvVideoDuration, tvProgressLabel, tvProgressPercent, tvLog;
    private Spinner spinnerQuality;
    private CardView cardPreview, cardClips, cardProgress, cardLog;
    private RecyclerView rvClips;

    private ClipAdapter clipAdapter;
    private List<ClipItem> clips = new ArrayList<>();
    private List<String[]> availableFormats = new ArrayList<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final int PERM_REQUEST = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        requestPermissions();
        handleSharedIntent(getIntent());
        btnLoad.setOnClickListener(v -> loadVideo());
        btnAddClip.setOnClickListener(v -> addClip());
        btnDownload.setOnClickListener(v -> startDownload());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleSharedIntent(intent);
    }

    private void handleSharedIntent(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null && !text.isEmpty()) {
                etUrl.setText(text.trim());
                loadVideo();
            }
        }
    }

    private void initViews() {
        etUrl = findViewById(R.id.etUrl);
        btnLoad = findViewById(R.id.btnLoad);
        btnDownload = findViewById(R.id.btnDownload);
        btnAddClip = findViewById(R.id.btnAddClip);
        ivThumbnail = findViewById(R.id.ivThumbnail);
        pbThumbnail = findViewById(R.id.pbThumbnail);
        tvVideoTitle = findViewById(R.id.tvVideoTitle);
        tvVideoDuration = findViewById(R.id.tvVideoDuration);
        tvProgressLabel = findViewById(R.id.tvProgressLabel);
        tvProgressPercent = findViewById(R.id.tvProgressPercent);
        tvLog = findViewById(R.id.tvLog);
        spinnerQuality = findViewById(R.id.spinnerQuality);
        cardPreview = findViewById(R.id.cardPreview);
        cardClips = findViewById(R.id.cardClips);
        cardProgress = findViewById(R.id.cardProgress);
        cardLog = findViewById(R.id.cardLog);
        progressBar = findViewById(R.id.progressBar);
        rvClips = findViewById(R.id.rvClips);

        clipAdapter = new ClipAdapter(clips, this::removeClip);
        rvClips.setLayoutManager(new LinearLayoutManager(this));
        rvClips.setAdapter(clipAdapter);
        clips.add(new ClipItem("", ""));
        clipAdapter.notifyItemInserted(0);
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE}, PERM_REQUEST);
            }
        }
    }

    private void loadVideo() {
        String url = etUrl.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "Please enter a video URL", Toast.LENGTH_SHORT).show();
            return;
        }
        String videoId = extractYoutubeId(url);
        cardPreview.setVisibility(View.VISIBLE);
        pbThumbnail.setVisibility(View.VISIBLE);
        tvVideoTitle.setText("Loading...");
        btnLoad.setEnabled(false);
        log("Fetching video info...");

        if (!videoId.isEmpty()) {
            String thumbUrl = "https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg";
            Glide.with(this).load(thumbUrl).into(ivThumbnail);
            pbThumbnail.setVisibility(View.GONE);
        }

        executor.execute(() -> {
            String title = fetchTitle(url);
            mainHandler.post(() -> {
                tvVideoTitle.setText(title);
                pbThumbnail.setVisibility(View.GONE);
                btnLoad.setEnabled(true);
                setupQualitySpinner();
                cardClips.setVisibility(View.VISIBLE);
                btnDownload.setVisibility(View.VISIBLE);
                log("Ready! Set your clip times and press Download.");
            });
        });
    }

    private String fetchTitle(String url) {
        try {
            OkHttpClient client = new OkHttpClient();
            String apiUrl = "https://noembed.com/embed?url=" + URLEncoder.encode(url, "UTF-8");
            Request req = new Request.Builder().url(apiUrl).build();
            Response resp = client.newCall(req).execute();
            if (resp.isSuccessful() && resp.body() != null) {
                JSONObject json = new JSONObject(resp.body().string());
                return json.optString("title", "Video loaded");
            }
        } catch (Exception ignored) {}
        return "Video loaded";
    }

    private void setupQualitySpinner() {
        availableFormats.clear();
        availableFormats.add(new String[]{"Best Quality (Auto)", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"});
        availableFormats.add(new String[]{"1080p", "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[height<=1080]"});
        availableFormats.add(new String[]{"720p", "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720]"});
        availableFormats.add(new String[]{"480p", "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best[height<=480]"});
        availableFormats.add(new String[]{"360p", "bestvideo[height<=360][ext=mp4]+bestaudio[ext=m4a]/best[height<=360]"});
        availableFormats.add(new String[]{"Audio Only (m4a)", "bestaudio[ext=m4a]/bestaudio"});

        List<String> labels = new ArrayList<>();
        for (String[] f : availableFormats) labels.add(f[0]);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerQuality.setAdapter(adapter);
    }

    private void addClip() {
        clips.add(new ClipItem("", ""));
        clipAdapter.notifyItemInserted(clips.size() - 1);
    }

    private void removeClip(int position) {
        if (clips.size() > 1) {
            clips.remove(position);
            clipAdapter.notifyItemRemoved(position);
            clipAdapter.notifyItemRangeChanged(position, clips.size());
        } else {
            Toast.makeText(this, "Need at least one clip", Toast.LENGTH_SHORT).show();
        }
    }

    private void startDownload() {
        List<ClipItem> validClips = clipAdapter.getClips();
        if (validClips.isEmpty()) {
            Toast.makeText(this, "Enter start and end time for at least one clip", Toast.LENGTH_SHORT).show();
            return;
        }
        String url = etUrl.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "Please load a video first", Toast.LENGTH_SHORT).show();
            return;
        }
        int selectedFormat = spinnerQuality.getSelectedItemPosition();
        String formatCode = availableFormats.get(selectedFormat)[1];

        btnDownload.setEnabled(false);
        cardProgress.setVisibility(View.VISIBLE);
        cardLog.setVisibility(View.VISIBLE);
        log("Starting " + validClips.size() + " clip(s)...");

        executor.execute(() -> downloadClips(url, validClips, formatCode));
    }

    private void downloadClips(String url, List<ClipItem> clips, String format) {
        // First ensure yt-dlp is available
        String ytdlpPath = getYtDlpPath();
        if (ytdlpPath == null) {
            mainHandler.post(() -> {
                log("✗ Failed to get yt-dlp. Check internet connection.");
                btnDownload.setEnabled(true);
            });
            return;
        }

        File outputDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "VideoClipper");
        if (!outputDir.exists()) outputDir.mkdirs();

        for (int i = 0; i < clips.size(); i++) {
            ClipItem clip = clips.get(i);
            final int clipNum = i + 1;
            final int total = clips.size();

            mainHandler.post(() -> {
                tvProgressLabel.setText("Downloading clip " + clipNum + " of " + total + "...");
                progressBar.setProgress(0);
                tvProgressPercent.setText("0%");
                log("▶ Clip " + clipNum + ": " + clip.startTime + " → " + clip.endTime);
            });

            try {
                long startSec = parseTime(clip.startTime);
                long endSec = parseTime(clip.endTime);
                if (endSec <= startSec) {
                    mainHandler.post(() -> log("✗ Clip " + clipNum + ": end time must be after start time"));
                    continue;
                }

                String safeStart = clip.startTime.replace(":", "-");
                String safeEnd = clip.endTime.replace(":", "-");
                String fileName = "clip" + clipNum + "_" + safeStart + "_" + safeEnd + ".mp4";
                File outFile = new File(outputDir, fileName);

                // Build yt-dlp command
                List<String> cmd = new ArrayList<>(Arrays.asList(
                        ytdlpPath,
                        "--no-playlist",
                        "--format", format,
                        "--download-sections", "*" + startSec + "-" + endSec,
                        "--force-keyframes-at-cuts",
                        "--no-part",
                        "-o", outFile.getAbsolutePath(),
                        url
                ));

                boolean success = runProcess(cmd, clipNum);
                if (!success) {
                    // Retry without download-sections (some sites don't support it)
                    mainHandler.post(() -> log("Retrying clip " + clipNum + " with fallback method..."));
                    List<String> cmd2 = new ArrayList<>(Arrays.asList(
                            ytdlpPath,
                            "--no-playlist",
                            "--format", format,
                            "--no-part",
                            "-o", outFile.getAbsolutePath(),
                            url
                    ));
                    runProcess(cmd2, clipNum);
                }

            } catch (Exception e) {
                final String err = e.getMessage();
                mainHandler.post(() -> log("✗ Clip " + clipNum + " error: " + err));
            }
        }

        mainHandler.post(() -> {
            btnDownload.setEnabled(true);
            tvProgressLabel.setText("Done!");
            progressBar.setProgress(100);
            tvProgressPercent.setText("100%");
            log("✅ Saved to Downloads/VideoClipper/");
            Toast.makeText(this, "✅ Done! Check Downloads/VideoClipper/", Toast.LENGTH_LONG).show();
        });
    }

    private boolean runProcess(List<String> cmd, int clipNum) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            final String logLine = line;
            if (line.contains("%")) {
                try {
                    String pct = line.replaceAll(".*?(\\d+\\.?\\d*)%.*", "$1");
                    int progress = (int) Double.parseDouble(pct);
                    mainHandler.post(() -> {
                        progressBar.setProgress(progress);
                        tvProgressPercent.setText(progress + "%");
                    });
                } catch (Exception ignored) {}
            }
            mainHandler.post(() -> log(logLine));
        }
        int exit = process.waitFor();
        if (exit == 0) {
            mainHandler.post(() -> log("✓ Clip " + clipNum + " done!"));
            return true;
        }
        return false;
    }

    private String getYtDlpPath() {
        File ytdlp = new File(getFilesDir(), "yt-dlp");
        if (ytdlp.exists() && ytdlp.canExecute()) return ytdlp.getAbsolutePath();
        mainHandler.post(() -> log("Downloading yt-dlp..."));
        try {
            String arch = System.getProperty("os.arch", "");
            String binUrl;
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                binUrl = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux_aarch64";
            } else if (arch.contains("arm")) {
                binUrl = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux_armv7l";
            } else {
                binUrl = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux_x86_64";
            }
            OkHttpClient client = new OkHttpClient.Builder()
                    .followRedirects(true)
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            Request req = new Request.Builder().url(binUrl).build();
            Response resp = client.newCall(req).execute();
            if (resp.isSuccessful() && resp.body() != null) {
                FileOutputStream fos = new FileOutputStream(ytdlp);
                fos.write(resp.body().bytes());
                fos.close();
                ytdlp.setExecutable(true);
                mainHandler.post(() -> log("✓ yt-dlp ready!"));
                return ytdlp.getAbsolutePath();
            }
        } catch (Exception e) {
            mainHandler.post(() -> log("✗ yt-dlp download failed: " + e.getMessage()));
        }
        return null;
    }

    private long parseTime(String time) {
        try {
            time = time.trim();
            String[] parts = time.split(":");
            if (parts.length == 1) return Long.parseLong(parts[0]);
            if (parts.length == 2) return Long.parseLong(parts[0]) * 60 + Long.parseLong(parts[1]);
            if (parts.length == 3) return Long.parseLong(parts[0]) * 3600
                    + Long.parseLong(parts[1]) * 60 + Long.parseLong(parts[2]);
        } catch (Exception ignored) {}
        return 0;
    }

    private String extractYoutubeId(String url) {
        try {
            if (url.contains("youtu.be/")) return url.split("youtu.be/")[1].split("[?&]")[0];
            if (url.contains("v=")) return url.split("v=")[1].split("[?&]")[0];
        } catch (Exception ignored) {}
        return "";
    }

    private void log(String message) {
        mainHandler.post(() -> {
            String current = tvLog.getText().toString();
            String newLog = (current.isEmpty() ? "" : current + "\n") + "› " + message;
            String[] lines = newLog.split("\n");
            if (lines.length > 25) {
                StringBuilder sb = new StringBuilder();
                for (int i = lines.length - 25; i < lines.length; i++) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(lines[i]);
                }
                tvLog.setText(sb.toString());
            } else {
                tvLog.setText(newLog);
            }
        });
    }
}
