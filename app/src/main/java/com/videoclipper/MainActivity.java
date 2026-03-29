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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
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

    private String videoId = "";
    private String currentUrl = "";
    private List<String[]> availableFormats = new ArrayList<>(); // [label, itag]

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

        // Add a default clip
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
        currentUrl = url;
        videoId = extractYoutubeId(url);

        cardPreview.setVisibility(View.VISIBLE);
        pbThumbnail.setVisibility(View.VISIBLE);
        tvVideoTitle.setText("Loading video info...");
        tvVideoDuration.setText("");
        btnLoad.setEnabled(false);

        log("Fetching video info...");

        if (!videoId.isEmpty()) {
            // Load YouTube thumbnail directly
            String thumbUrl = "https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg";
            Glide.with(this).load(thumbUrl).into(ivThumbnail);
            pbThumbnail.setVisibility(View.GONE);
        }

        executor.execute(() -> {
            try {
                // Use yt-dlp API via public noembed/oEmbed for title
                String info = fetchVideoInfo(url);
                mainHandler.post(() -> {
                    tvVideoTitle.setText(info);
                    pbThumbnail.setVisibility(View.GONE);
                    btnLoad.setEnabled(true);

                    // Setup quality options
                    setupQualitySpinner();

                    cardClips.setVisibility(View.VISIBLE);
                    btnDownload.setVisibility(View.VISIBLE);
                    log("Video loaded! Set your clip times and press Download.");
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    tvVideoTitle.setText("Video URL loaded");
                    pbThumbnail.setVisibility(View.GONE);
                    btnLoad.setEnabled(true);
                    setupQualitySpinner();
                    cardClips.setVisibility(View.VISIBLE);
                    btnDownload.setVisibility(View.VISIBLE);
                    log("Ready. Set clip times and press Download.");
                });
            }
        });
    }

    private String fetchVideoInfo(String url) {
        try {
            // Try noembed for YouTube
            if (url.contains("youtube") || url.contains("youtu.be")) {
                String apiUrl = "https://noembed.com/embed?url=" + URLEncoder.encode(url, "UTF-8");
                OkHttpClient client = new OkHttpClient();
                Request req = new Request.Builder().url(apiUrl).build();
                Response resp = client.newCall(req).execute();
                if (resp.isSuccessful() && resp.body() != null) {
                    String body = resp.body().string();
                    JSONObject json = new JSONObject(body);
                    String title = json.optString("title", "YouTube Video");
                    return title;
                }
            }
        } catch (Exception ignored) {}
        return "Video loaded";
    }

    private void setupQualitySpinner() {
        availableFormats.clear();
        availableFormats.add(new String[]{"Best Quality (Auto)", "best"});
        availableFormats.add(new String[]{"1080p MP4", "137+140"});
        availableFormats.add(new String[]{"720p MP4", "136+140"});
        availableFormats.add(new String[]{"480p MP4", "135+140"});
        availableFormats.add(new String[]{"360p MP4", "134+140"});
        availableFormats.add(new String[]{"Audio Only (MP3)", "bestaudio"});

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
        // Collect clip times from adapter
        List<ClipItem> validClips = clipAdapter.getClips();

        if (validClips.isEmpty()) {
            Toast.makeText(this, "Add at least one clip with start and end time", Toast.LENGTH_SHORT).show();
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
        log("Starting download of " + validClips.size() + " clip(s)...");

        executor.execute(() -> {
            downloadClips(url, validClips, formatCode);
        });
    }

    private void downloadClips(String url, List<ClipItem> clips, String format) {
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
                // Convert time to seconds
                long startSec = parseTime(clip.startTime);
                long endSec = parseTime(clip.endTime);

                if (endSec <= startSec) {
                    mainHandler.post(() -> log("✗ Clip " + clipNum + ": End time must be after start time"));
                    continue;
                }

                String fileName = "clip_" + clipNum + "_" + clip.startTime.replace(":", "-")
                        + "_to_" + clip.endTime.replace(":", "-") + ".mp4";
                File outFile = new File(outputDir, fileName);

                // Build yt-dlp command with section download (no re-encoding)
                // Using download-sections for muxed streams
                List<String> cmd = new ArrayList<>();
                cmd.add(getYtDlpPath());
                cmd.add("--no-playlist");
                cmd.add("--format");
                cmd.add(format.equals("bestaudio") ? "bestaudio/best" :
                        format.equals("best") ? "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]" :
                        format + "/best[ext=mp4]");
                cmd.add("--download-sections");
                cmd.add("*" + startSec + "-" + endSec);
                cmd.add("--force-keyframes-at-cuts");
                cmd.add("-o");
                cmd.add(outFile.getAbsolutePath());
                cmd.add(url);

                runProcess(cmd, clipNum, outFile);

            } catch (Exception e) {
                final String err = e.getMessage();
                mainHandler.post(() -> log("✗ Clip " + clipNum + " error: " + err));
            }
        }

        mainHandler.post(() -> {
            btnDownload.setEnabled(true);
            tvProgressLabel.setText("All clips downloaded!");
            progressBar.setProgress(100);
            tvProgressPercent.setText("100%");
            log("✅ Done! Files saved to Downloads/VideoClipper/");
            Toast.makeText(this, "✅ Clips saved to Downloads/VideoClipper/", Toast.LENGTH_LONG).show();
        });
    }

    private void runProcess(List<String> cmd, int clipNum, File outFile) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            final String logLine = line;
            // Parse progress
            if (line.contains("[download]") && line.contains("%")) {
                try {
                    String pct = line.replaceAll(".*?(\\d+\\.\\d+)%.*", "$1");
                    int progress = (int) Double.parseDouble(pct);
                    mainHandler.post(() -> {
                        progressBar.setProgress(progress);
                        tvProgressPercent.setText(progress + "%");
                    });
                } catch (Exception ignored) {}
            }
            mainHandler.post(() -> log(logLine));
        }

        int exitCode = process.waitFor();
        if (exitCode == 0) {
            mainHandler.post(() -> log("✓ Clip " + clipNum + " saved: " + outFile.getName()));
        } else {
            mainHandler.post(() -> log("✗ Clip " + clipNum + " failed (exit " + exitCode + ")"));
        }
    }

    private String getYtDlpPath() {
        // yt-dlp bundled in app's files dir
        File ytdlp = new File(getFilesDir(), "yt-dlp");
        if (ytdlp.exists()) return ytdlp.getAbsolutePath();
        // fallback: try to extract from assets
        try {
            extractAsset("yt-dlp", ytdlp);
            ytdlp.setExecutable(true);
        } catch (Exception e) {
            log("yt-dlp not found, downloading...");
            downloadYtDlp(ytdlp);
        }
        return ytdlp.getAbsolutePath();
    }

    private void extractAsset(String assetName, File dest) throws Exception {
        InputStream in = getAssets().open(assetName);
        FileOutputStream out = new FileOutputStream(dest);
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        in.close();
        out.close();
    }

    private void downloadYtDlp(File dest) {
        try {
            // Download yt-dlp ARM binary
            String arch = System.getProperty("os.arch", "");
            String binUrl;
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                binUrl = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux_aarch64";
            } else {
                binUrl = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux_armv7l";
            }
            mainHandler.post(() -> log("Downloading yt-dlp binary..."));
            OkHttpClient client = new OkHttpClient.Builder()
                    .followRedirects(true)
                    .build();
            Request req = new Request.Builder().url(binUrl).build();
            Response resp = client.newCall(req).execute();
            if (resp.isSuccessful() && resp.body() != null) {
                FileOutputStream fos = new FileOutputStream(dest);
                fos.write(resp.body().bytes());
                fos.close();
                dest.setExecutable(true);
                mainHandler.post(() -> log("yt-dlp downloaded!"));
            }
        } catch (Exception e) {
            mainHandler.post(() -> log("Failed to download yt-dlp: " + e.getMessage()));
        }
    }

    private long parseTime(String time) {
        // Supports: ss, m:ss, mm:ss, h:mm:ss
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
            if (url.contains("youtu.be/")) {
                return url.split("youtu.be/")[1].split("[?&]")[0];
            }
            if (url.contains("v=")) {
                return url.split("v=")[1].split("[?&]")[0];
            }
        } catch (Exception ignored) {}
        return "";
    }

    private void log(String message) {
        mainHandler.post(() -> {
            String current = tvLog.getText().toString();
            String newLog = (current.isEmpty() ? "" : current + "\n") + "› " + message;
            // Keep last 20 lines
            String[] lines = newLog.split("\n");
            if (lines.length > 20) {
                StringBuilder sb = new StringBuilder();
                for (int i = lines.length - 20; i < lines.length; i++) {
                    sb.append(lines[i]);
                    if (i < lines.length - 1) sb.append("\n");
                }
                tvLog.setText(sb.toString());
            } else {
                tvLog.setText(newLog);
            }
        });
    }
}
