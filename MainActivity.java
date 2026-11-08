package com.walkieibu;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import android.media.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class MainActivity extends Activity {
    private static final int PORT = 50555;
    private static final int FRAME = 640; // 20 ms, 16 kHz, mono, 16-bit PCM
    private static final int RATE = 16000;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private final List<ClientPeer> peers = new CopyOnWriteArrayList<>();
    private Socket socket;
    private DataOutputStream out;
    private volatile boolean talking = false;
    private volatile boolean connected = false;
    private AudioRecord recorder;
    private AudioTrack player;
    private Thread recordThread, receiveThread;
    private TextView status, hostInfo;
    private EditText ipField;
    private Button hostButton, connectButton, talkButton;
    private String mode = "Belum terhubung";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(12, 48, 35));
        getWindow().setNavigationBarColor(Color.rgb(8, 28, 22));
        buildUi();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10);
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 30, 24, 24);
        root.setBackgroundColor(Color.rgb(8, 28, 22));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("WALKIE IBU");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("KOMUNIKASI SUARA • WIFI LOKAL • OFFLINE");
        sub.setTextColor(Color.rgb(163, 220, 184));
        sub.setTextSize(12);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 8, 0, 26);
        root.addView(sub);

        status = new TextView(this);
        status.setText("Status: Belum terhubung");
        status.setTextColor(Color.WHITE);
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        status.setPadding(12, 14, 12, 14);
        status.setBackgroundColor(Color.rgb(24, 58, 45));
        root.addView(status);

        hostInfo = new TextView(this);
        hostInfo.setText("HOST: Buat hotspot Wi-Fi dari Pengaturan HP terlebih dahulu.");
        hostInfo.setTextColor(Color.LTGRAY);
        hostInfo.setTextSize(14);
        hostInfo.setPadding(4, 18, 4, 12);
        root.addView(hostInfo);

        hostButton = makeButton("JADIKAN HP INI HOST");
        root.addView(hostButton);
        hostButton.setOnClickListener(v -> startHost());

        TextView label = new TextView(this);
        label.setText("ALAMAT IP HP HOST");
        label.setTextColor(Color.WHITE);
        label.setPadding(4, 22, 4, 6);
        root.addView(label);

        ipField = new EditText(this);
        ipField.setSingleLine(true);
        ipField.setText("192.168.43.1");
        ipField.setHint("Contoh: 192.168.43.1");
        ipField.setTextColor(Color.WHITE);
        ipField.setHintTextColor(Color.GRAY);
        ipField.setPadding(16, 10, 16, 10);
        ipField.setBackgroundColor(Color.rgb(30, 55, 46));
        root.addView(ipField);

        connectButton = makeButton("GABUNG KE HOST");
        root.addView(connectButton);
        connectButton.setOnClickListener(v -> connectToHost(ipField.getText().toString().trim()));

        talkButton = makeButton("TEKAN & TAHAN UNTUK BICARA");
        talkButton.setTextSize(17);
        talkButton.setBackgroundColor(Color.rgb(28, 145, 83));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, 150);
        tp.setMargins(0, 28, 0, 12);
        root.addView(talkButton, tp);
        talkButton.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                startTalking();
                return true;
            } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                stopTalking();
                return true;
            }
            return true;
        });

        TextView note = new TextView(this);
        note.setText("PANDUAN SINGKAT\\n1. Satu HP aktifkan Hotspot Pribadi.\\n2. HP lain sambungkan ke hotspot itu.\\n3. Pada HP host tekan JADIKAN HP INI HOST.\\n4. HP anggota masukkan IP host lalu tekan GABUNG.\\n5. Tekan dan tahan tombol hijau untuk bicara.\\n\\nCatatan: alamat host sering 192.168.43.1, tetapi bisa berbeda menurut merek HP. Semua HP harus berada di jaringan Wi-Fi yang sama. Internet/kuota tidak diperlukan.");
        note.setTextColor(Color.rgb(205, 218, 210));
        note.setTextSize(13);
        note.setPadding(6, 20, 6, 10);
        root.addView(note);
        setContentView(scroll);
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(24, 112, 69)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, 58);
        p.setMargins(0, 10, 0, 4);
        b.setLayoutParams(p);
        return b;
    }

    private void setStatus(String s) {
        runOnUiThread(() -> status.setText("Status: " + s));
    }

    private boolean micPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10);
            setStatus("Izinkan akses mikrofon lalu coba lagi");
            return false;
        }
        return true;
    }

    private void startHost() {
        new Thread(() -> {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    setStatus("Host sudah aktif. Sambungkan HP anggota.");
                    return;
                }
                serverSocket = new ServerSocket(PORT);
                setStatus("HOST AKTIF • Menunggu anggota");
                runOnUiThread(() -> hostInfo.setText("HOST AKTIF pada port " + PORT + ". Coba IP 192.168.43.1 pada HP anggota."));
                acceptThread = new Thread(() -> {
                    while (!serverSocket.isClosed()) {
                        try {
                            Socket s = serverSocket.accept();
                            ClientPeer p = new ClientPeer(s);
                            peers.add(p);
                            p.start();
                            setStatus("Host aktif • Anggota tersambung: " + peers.size());
                        } catch (Exception ex) { break; }
                    }
                });
                acceptThread.start();
                // Host connects to its own server so its microphone and speaker work like a member.
                Thread.sleep(250);
                connectSocket("127.0.0.1");
            } catch (Exception e) {
                setStatus("Gagal membuat host: " + e.getMessage());
            }
        }).start();
    }

    private void connectToHost(String ip) {
        if (ip.isEmpty()) { setStatus("Masukkan IP host"); return; }
        new Thread(() -> connectSocket(ip)).start();
    }

    private synchronized void connectSocket(String ip) {
        try {
            if (connected) { setStatus("Sudah terhubung"); return; }
            Socket s = new Socket();
            s.connect(new InetSocketAddress(ip, PORT), 5000);
            s.setTcpNoDelay(true);
            socket = s;
            out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
            connected = true;
            setStatus("Terhubung ke " + ip + " • Siap bicara");
            receiveThread = new Thread(() -> receiveAudio(s));
            receiveThread.start();
        } catch (Exception e) {
            setStatus("Gagal terhubung. Periksa Wi-Fi dan IP host.");
        }
    }

    private void receiveAudio(Socket s) {
        int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        try {
            player = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(Math.max(min, FRAME * 8))
                .setTransferMode(AudioTrack.MODE_STREAM).build();
            player.play();
            DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            byte[] buf = new byte[FRAME];
            while (connected && !s.isClosed()) {
                in.readFully(buf);
                if (player != null) player.write(buf, 0, buf.length);
            }
        } catch (Exception ignored) {
        } finally {
            setStatus("Koneksi terputus");
            connected = false;
            if (player != null) { try { player.stop(); player.release(); } catch (Exception ignored) {} player = null; }
        }
    }

    private void startTalking() {
        if (!connected) { setStatus("Hubungkan ke host terlebih dahulu"); return; }
        if (!micPermission() || talking) return;
        talking = true;
        talkButton.setText("SEDANG BERBICARA • LEPASKAN UNTUK BERHENTI");
        recordThread = new Thread(() -> {
            int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int bufferSize = Math.max(min, FRAME * 4);
            try {
                recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
                recorder.startRecording();
                byte[] buf = new byte[FRAME];
                while (talking && connected) {
                    int n = recorder.read(buf, 0, buf.length);
                    if (n > 0 && out != null) {
                        synchronized (out) {
                            out.write(buf, 0, n);
                            if (n < FRAME) {
                                for (int i = n; i < FRAME; i++) buf[i] = 0;
                                out.write(buf, n, FRAME - n);
                            }
                            out.flush();
                        }
                    }
                }
            } catch (Exception e) { setStatus("Audio bermasalah: " + e.getMessage()); }
            finally {
                if (recorder != null) { try { recorder.stop(); recorder.release(); } catch (Exception ignored) {} recorder = null; }
            }
        });
        recordThread.start();
    }

    private void stopTalking() {
        talking = false;
        runOnUiThread(() -> talkButton.setText("TEKAN & TAHAN UNTUK BICARA"));
    }

    private class ClientPeer extends Thread {
        private final Socket s;
        ClientPeer(Socket socket) { s = socket; }
        public void run() {
            try {
                DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
                byte[] frame = new byte[FRAME];
                while (!s.isClosed()) {
                    in.readFully(frame);
                    for (ClientPeer peer : peers) {
                        if (peer != this && !peer.s.isClosed()) {
                            try {
                                synchronized (peer.s) {
                                    OutputStream os = peer.s.getOutputStream();
                                    os.write(frame);
                                    os.flush();
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                peers.remove(this);
                try { s.close(); } catch (Exception ignored) {}
                setStatus("Host aktif • Anggota tersambung: " + peers.size());
            }
        }
    }

    @Override protected void onDestroy() {
        stopTalking();
        connected = false;
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        for (ClientPeer p : peers) try { p.s.close(); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
