package com.testemunha;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.location.LocationManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Size;
import android.view.Surface;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Gravação em primeiro plano (continua com a tela apagada):
 *  - áudio (MediaRecorder -> m4a)
 *  - vídeo (camera2 + MediaRecorder -> mp4), câmera traseira, frontal ou as duas ao mesmo tempo
 *  - foto  (camera2 -> jpg), traseira, frontal ou uma de cada
 * Cada arquivo vai para a área privada do app e é registrado no Store, com hash e localização.
 */
public class RecorderService extends Service {

    static final String A_AUDIO = "audio", A_VIDEO = "video", A_PHOTO = "photo", A_STOP = "stop";
    static final int CAM_BACK = 0, CAM_FRONT = 1, CAM_BOTH = 2;
    private static final String CH = "rec";
    private static final int NID = 11;
    private static final long PHOTO_WARMUP_MS = 1200;
    private static final long PHOTO_TIMEOUT_MS = 10000;

    // estado lido pela tela (mesmo processo)
    static volatile boolean running = false;   // serviço ocupado (preparando, gravando ou fotografando)
    static volatile boolean live = false;      // áudio/vídeo efetivamente gravando
    static volatile int kind = -1;
    static volatile long startedAt = 0;
    static volatile String lastSavedId = null; // primeira prova salva na última operação
    static volatile int savedCount = 0;
    static volatile String lastError = null;
    static volatile String notice = null;      // aviso não fatal (ex.: celular não grava com as duas câmeras)

    private HandlerThread thread;
    private Handler handler;
    private PowerManager.WakeLock wake;
    private boolean ending = false;

    // áudio
    private MediaRecorder audioRecorder;
    private File audioFile;
    private long recStart;

    // vídeo: um "rig" por câmera
    private static final class Rig {
        String cameraId;
        int cam;
        MediaRecorder recorder;
        CameraDevice device;
        CameraCaptureSession session;
        File file;
        boolean started;
    }
    private final List<Rig> rigs = new ArrayList<>();

    // foto
    private final List<int[]> photoQueue = new ArrayList<>(); // {cam}
    private CameraDevice photoDevice;
    private CameraCaptureSession photoSession;
    private ImageReader jpegReader, previewReader;
    private boolean shotDone;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent != null ? intent.getAction() : null;
        if (a == null || A_STOP.equals(a)) {
            if (handler != null) handler.post(this::stopAndSave);
            else endService();
            return START_NOT_STICKY;
        }
        if (handler != null) return START_NOT_STICKY; // já há uma operação em andamento

        int type = A_AUDIO.equals(a) ? Store.KIND_AUDIO : A_VIDEO.equals(a) ? Store.KIND_VIDEO : Store.KIND_PHOTO;
        final int cam = intent.getIntExtra("cam", CAM_BACK);
        running = true; live = false; kind = type;
        lastError = null; notice = null; lastSavedId = null; savedCount = 0;

        try {
            startForegroundWith(type);
        } catch (Throwable t) {
            lastError = "O Android não deixou iniciar a gravação. Abra o app e tente de novo.";
            running = false; kind = -1;
            stopSelf();
            return START_NOT_STICKY;
        }
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Testemunha::rec");
        wake.setReferenceCounted(false);
        wake.acquire(6 * 60 * 60 * 1000L);

        thread = new HandlerThread("rec");
        thread.start();
        handler = new Handler(thread.getLooper());

        if (type == Store.KIND_AUDIO) handler.post(this::startAudio);
        else if (type == Store.KIND_VIDEO) handler.post(() -> startVideo(cam));
        else handler.post(() -> startPhotos(cam));
        return START_NOT_STICKY;
    }

    // ============================== ÁUDIO ==============================

    private void startAudio() {
        try {
            audioFile = newFile(Store.KIND_AUDIO, -1);
            audioRecorder = newRecorder();
            audioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            audioRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            audioRecorder.setOutputFile(audioFile.getAbsolutePath());
            audioRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            audioRecorder.setAudioEncodingBitRate(128000);
            audioRecorder.setAudioSamplingRate(44100);
            audioRecorder.prepare();
            audioRecorder.start();
            goLive();
        } catch (Throwable t) {
            if (audioFile != null) audioFile.delete();
            fail("Não foi possível iniciar a gravação de áudio");
        }
    }

    // ============================== VÍDEO ==============================

    private void startVideo(int cam) {
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            String back = findCamera(cm, CameraCharacteristics.LENS_FACING_BACK);
            String front = findCamera(cm, CameraCharacteristics.LENS_FACING_FRONT);

            List<int[]> plan = new ArrayList<>();
            if (cam == CAM_BOTH) {
                if (back != null && front != null && concurrentOk(cm, back, front)) {
                    plan.add(new int[]{CAM_BACK});
                    plan.add(new int[]{CAM_FRONT});
                } else {
                    notice = "Este celular não grava vídeo com as duas câmeras ao mesmo tempo. Gravei com a traseira.";
                    plan.add(new int[]{back != null ? CAM_BACK : CAM_FRONT});
                }
            } else if (cam == CAM_FRONT && front != null) {
                plan.add(new int[]{CAM_FRONT});
            } else {
                plan.add(new int[]{back != null ? CAM_BACK : CAM_FRONT});
            }

            boolean dual = plan.size() > 1;
            for (int i = 0; i < plan.size(); i++) {
                Rig r = new Rig();
                r.cam = plan.get(i)[0];
                r.cameraId = r.cam == CAM_BACK ? back : front;
                if (r.cameraId == null) continue;
                prepareRig(cm, r, i == 0, dual); // só o primeiro grava o som
                rigs.add(r);
            }
            if (rigs.isEmpty()) { fail("Nenhuma câmera disponível"); return; }
            openRig(cm, 0);
        } catch (Throwable t) {
            releaseRigs(false);
            fail("Não foi possível iniciar a gravação de vídeo");
        }
    }

    private void prepareRig(CameraManager cm, Rig r, boolean withAudio, boolean dual) throws Exception {
        CameraCharacteristics ch = cm.getCameraCharacteristics(r.cameraId);
        Size v = videoSize(ch, dual);
        Integer sensor = ch.get(CameraCharacteristics.SENSOR_ORIENTATION);
        r.file = newFile(Store.KIND_VIDEO, r.cam);
        r.recorder = newRecorder();
        if (withAudio) r.recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        r.recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        r.recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        r.recorder.setOutputFile(r.file.getAbsolutePath());
        r.recorder.setVideoEncodingBitRate(dual ? 5_000_000 : 10_000_000);
        r.recorder.setVideoFrameRate(30);
        r.recorder.setVideoSize(v.getWidth(), v.getHeight());
        r.recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        if (withAudio) {
            r.recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            r.recorder.setAudioEncodingBitRate(128000);
            r.recorder.setAudioSamplingRate(44100);
        }
        if (sensor != null) r.recorder.setOrientationHint(sensor);
        r.recorder.prepare();
    }

    @SuppressLint("MissingPermission")
    private void openRig(final CameraManager cm, final int index) {
        if (index >= rigs.size()) return;
        final Rig r = rigs.get(index);
        try {
            cm.openCamera(r.cameraId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice c) {
                    r.device = c;
                    try {
                        final Surface surface = r.recorder.getSurface();
                        c.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                            @Override public void onConfigured(CameraCaptureSession s) {
                                r.session = s;
                                try {
                                    CaptureRequest.Builder b = c.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                                    b.addTarget(surface);
                                    b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
                                    s.setRepeatingRequest(b.build(), null, handler);
                                    r.recorder.start();
                                    r.started = true;
                                    if (!live) goLive();
                                    openRig(cm, index + 1);
                                } catch (Throwable t) { rigFailed(cm, index); }
                            }
                            @Override public void onConfigureFailed(CameraCaptureSession s) { rigFailed(cm, index); }
                        }, handler);
                    } catch (Throwable t) { rigFailed(cm, index); }
                }
                @Override public void onDisconnected(CameraDevice c) { c.close(); r.device = null; }
                @Override public void onError(CameraDevice c, int e) { c.close(); r.device = null; if (!r.started) rigFailed(cm, index); }
            }, handler);
        } catch (Throwable t) {
            rigFailed(cm, index);
        }
    }

    /** Uma das câmeras falhou: se a outra está gravando, segue só com ela. */
    private void rigFailed(CameraManager cm, int index) {
        if (ending) return;
        Rig r = rigs.get(index);
        closeRig(r, false);
        if (index == 0) {
            releaseRigs(false);
            fail("Não foi possível usar a câmera");
        } else {
            notice = "A segunda câmera não pôde ser usada. O vídeo foi gravado só com a primeira.";
        }
    }

    private void closeRig(Rig r, boolean save) {
        boolean ok = false;
        if (r.started) {
            try { r.recorder.stop(); ok = true; } catch (Throwable ignored) {}
        }
        try { if (r.session != null) r.session.close(); } catch (Throwable ignored) {}
        try { if (r.device != null) r.device.close(); } catch (Throwable ignored) {}
        try { if (r.recorder != null) r.recorder.release(); } catch (Throwable ignored) {}
        r.session = null; r.device = null; r.recorder = null;
        if (r.file != null) {
            if (ok && save && r.file.length() > 0) register(r.file, Store.KIND_VIDEO, r.cam, SystemClock.elapsedRealtime() - recStart);
            else r.file.delete();
        }
        r.started = false;
        r.file = null;
    }

    private void releaseRigs(boolean save) {
        for (Rig r : rigs) closeRig(r, save);
        rigs.clear();
    }

    // ============================== FOTO ==============================

    private void startPhotos(int cam) {
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            boolean hasBack = findCamera(cm, CameraCharacteristics.LENS_FACING_BACK) != null;
            boolean hasFront = findCamera(cm, CameraCharacteristics.LENS_FACING_FRONT) != null;
            if ((cam == CAM_BACK || cam == CAM_BOTH) && hasBack) photoQueue.add(new int[]{CAM_BACK});
            if ((cam == CAM_FRONT || cam == CAM_BOTH) && hasFront) photoQueue.add(new int[]{CAM_FRONT});
            if (photoQueue.isEmpty()) {
                if (hasBack) photoQueue.add(new int[]{CAM_BACK});
                else if (hasFront) photoQueue.add(new int[]{CAM_FRONT});
            }
            if (photoQueue.isEmpty()) { fail("Nenhuma câmera disponível"); return; }
            nextPhoto();
        } catch (Throwable t) {
            fail("Não foi possível usar a câmera");
        }
    }

    private final Runnable photoTimeout = () -> { if (!shotDone) photoDone(false); };

    @SuppressLint("MissingPermission")
    private void nextPhoto() {
        if (photoQueue.isEmpty()) {
            if (savedCount == 0 && lastError == null) lastError = "Não foi possível tirar a foto";
            endService();
            return;
        }
        final int cam = photoQueue.remove(0)[0];
        shotDone = false;
        handler.postDelayed(photoTimeout, PHOTO_TIMEOUT_MS);
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            String id = findCamera(cm, cam == CAM_BACK ? CameraCharacteristics.LENS_FACING_BACK : CameraCharacteristics.LENS_FACING_FRONT);
            CameraCharacteristics ch = cm.getCameraCharacteristics(id);
            StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size big = largest(map.getOutputSizes(ImageFormat.JPEG));
            Size small = smallest(map.getOutputSizes(ImageFormat.YUV_420_888));
            Integer sensor = ch.get(CameraCharacteristics.SENSOR_ORIENTATION);
            final int orientation = sensor == null ? 90 : sensor;
            final File out = newFile(Store.KIND_PHOTO, cam);

            jpegReader = ImageReader.newInstance(big.getWidth(), big.getHeight(), ImageFormat.JPEG, 2);
            jpegReader.setOnImageAvailableListener(r -> {
                Image img = r.acquireNextImage();
                if (img == null) return;
                boolean ok = false;
                try {
                    ByteBuffer buf = img.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buf.remaining()];
                    buf.get(bytes);
                    try (FileOutputStream os = new FileOutputStream(out)) { os.write(bytes); }
                    ok = true;
                } catch (Throwable ignored) {
                } finally { img.close(); }
                if (ok) register(out, Store.KIND_PHOTO, cam, 0);
                else out.delete();
                photoDone(ok);
            }, handler);

            // fluxo invisível só para foco e exposição se ajustarem antes do disparo
            previewReader = ImageReader.newInstance(small.getWidth(), small.getHeight(), ImageFormat.YUV_420_888, 3);
            previewReader.setOnImageAvailableListener(r -> {
                Image img = r.acquireLatestImage();
                if (img != null) img.close();
            }, handler);

            cm.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice c) {
                    photoDevice = c;
                    try {
                        c.createCaptureSession(Arrays.asList(previewReader.getSurface(), jpegReader.getSurface()),
                                new CameraCaptureSession.StateCallback() {
                                    @Override public void onConfigured(CameraCaptureSession s) {
                                        photoSession = s;
                                        try {
                                            CaptureRequest.Builder p = c.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                            p.addTarget(previewReader.getSurface());
                                            p.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                            p.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                                            s.setRepeatingRequest(p.build(), null, handler);
                                            handler.postDelayed(() -> shoot(orientation), PHOTO_WARMUP_MS);
                                        } catch (Throwable t) { photoDone(false); }
                                    }
                                    @Override public void onConfigureFailed(CameraCaptureSession s) { photoDone(false); }
                                }, handler);
                    } catch (Throwable t) { photoDone(false); }
                }
                @Override public void onDisconnected(CameraDevice c) { c.close(); photoDevice = null; photoDone(false); }
                @Override public void onError(CameraDevice c, int e) { c.close(); photoDevice = null; photoDone(false); }
            }, handler);
        } catch (Throwable t) {
            photoDone(false);
        }
    }

    private void shoot(int orientation) {
        if (shotDone || photoDevice == null || photoSession == null) return;
        try {
            CaptureRequest.Builder b = photoDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            b.addTarget(jpegReader.getSurface());
            b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            b.set(CaptureRequest.JPEG_ORIENTATION, orientation);
            b.set(CaptureRequest.JPEG_QUALITY, (byte) 95);
            photoSession.capture(b.build(), null, handler);
        } catch (Throwable t) { photoDone(false); }
    }

    private void photoDone(boolean ok) {
        if (shotDone) return;
        shotDone = true;
        handler.removeCallbacks(photoTimeout);
        try { if (photoSession != null) photoSession.close(); } catch (Throwable ignored) {}
        try { if (photoDevice != null) photoDevice.close(); } catch (Throwable ignored) {}
        try { if (jpegReader != null) jpegReader.close(); } catch (Throwable ignored) {}
        try { if (previewReader != null) previewReader.close(); } catch (Throwable ignored) {}
        photoSession = null; photoDevice = null; jpegReader = null; previewReader = null;
        // pequena pausa antes de abrir a outra câmera
        handler.postDelayed(this::nextPhoto, photoQueue.isEmpty() ? 0 : 250);
    }

    // ============================== PARAR / SALVAR ==============================

    private void goLive() {
        live = true;
        recStart = SystemClock.elapsedRealtime();
        startedAt = recStart;
        updateNotification();
    }

    /** Chamado no thread de gravação: fecha tudo e registra o que foi gravado. */
    private void stopAndSave() {
        if (ending) return;
        if (kind == Store.KIND_PHOTO) return; // a foto termina sozinha
        if (kind == Store.KIND_AUDIO && audioRecorder != null) {
            boolean ok = false;
            if (live) { try { audioRecorder.stop(); ok = true; } catch (Throwable ignored) {} }
            try { audioRecorder.release(); } catch (Throwable ignored) {}
            audioRecorder = null;
            if (ok && audioFile != null && audioFile.length() > 0)
                register(audioFile, Store.KIND_AUDIO, -1, SystemClock.elapsedRealtime() - recStart);
            else if (audioFile != null) audioFile.delete();
        }
        if (kind == Store.KIND_VIDEO) releaseRigs(true);
        if (savedCount == 0 && lastError == null) lastError = "A gravação foi curta demais e não pôde ser salva";
        endService();
    }

    private void register(File file, int type, int cam, long durationMs) {
        try {
            Store.Evidence e = new Store.Evidence();
            e.id = file.getName();
            e.kind = type;
            e.cam = cam;
            e.createdAt = System.currentTimeMillis();
            e.durationMs = durationMs;
            e.sizeBytes = file.length();
            e.hash = Store.sha256(file);
            e.caseId = "";
            e.note = "";
            Location loc = lastLocation();
            if (loc != null) { e.hasLoc = true; e.lat = loc.getLatitude(); e.lon = loc.getLongitude(); }
            Store.saveEvidence(this, e);
            if (lastSavedId == null) lastSavedId = e.id;
            savedCount++;
        } catch (Throwable ignored) {}
    }

    private void fail(String msg) {
        lastError = msg;
        endService();
    }

    private void endService() {
        if (ending) return;
        ending = true;
        try { if (audioRecorder != null) audioRecorder.release(); } catch (Throwable ignored) {}
        audioRecorder = null;
        if (wake != null && wake.isHeld()) wake.release();
        if (thread != null) { final HandlerThread t = thread; thread = null; t.quitSafely(); }
        handler = null;
        live = false;
        running = false; // a tela percebe a mudança e mostra o resultado
        kind = -1;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    // ============================== AUXILIARES ==============================

    private MediaRecorder newRecorder() {
        return Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
    }

    @SuppressLint("MissingPermission")
    private Location lastLocation() {
        try {
            if (!Perms.location(this)) return null;
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location best = null;
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            return best;
        } catch (Throwable t) { return null; }
    }

    private File newFile(int type, int cam) {
        String ext = type == Store.KIND_AUDIO ? ".m4a" : type == Store.KIND_VIDEO ? ".mp4" : ".jpg";
        String side = cam == CAM_FRONT ? "_F" : cam == CAM_BACK ? "_T" : "";
        return new File(Store.evidenceDir(this), "EV_" + System.currentTimeMillis() + side + ext);
    }

    private static String findCamera(CameraManager cm, int facing) {
        try {
            for (String id : cm.getCameraIdList()) {
                Integer f = cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                if (f != null && f == facing) return id;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** O aparelho consegue usar as duas câmeras ao mesmo tempo? (Android 11+ e depende do celular) */
    private static boolean concurrentOk(CameraManager cm, String a, String b) {
        if (Build.VERSION.SDK_INT < 30) return false;
        try {
            for (Set<String> s : cm.getConcurrentCameraIds()) if (s.contains(a) && s.contains(b)) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    private static Size largest(Size[] s) {
        Size best = s[0];
        for (Size x : s) if ((long) x.getWidth() * x.getHeight() > (long) best.getWidth() * best.getHeight()) best = x;
        return best;
    }

    private static Size smallest(Size[] s) {
        Size best = null;
        for (Size x : s) {
            long a = (long) x.getWidth() * x.getHeight();
            if (a >= 320 * 240 && (best == null || a < (long) best.getWidth() * best.getHeight())) best = x;
        }
        return best != null ? best : s[0];
    }

    /** 1080p numa câmera só; 720p quando as duas gravam juntas (limite garantido pelo Android). */
    private static Size videoSize(CameraCharacteristics ch, boolean dual) {
        StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size[] sizes = map.getOutputSizes(MediaRecorder.class);
        int maxW = dual ? 1280 : 1920, maxH = dual ? 720 : 1080;
        Size pick = null;
        for (Size s : sizes) {
            if (s.getWidth() == maxW && s.getHeight() == maxH) return s;
            if (s.getWidth() <= maxW && s.getHeight() <= maxH
                    && (pick == null || (long) s.getWidth() * s.getHeight() > (long) pick.getWidth() * pick.getHeight()))
                pick = s;
        }
        return pick != null ? pick : sizes[sizes.length - 1];
    }

    // ============================== NOTIFICAÇÃO ==============================

    private void startForegroundWith(int type) {
        createChannel();
        int fsType;
        if (type == Store.KIND_AUDIO) fsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
        else if (type == Store.KIND_VIDEO) fsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE | ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
        else fsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
        if (Perms.location(this)) fsType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
        Notification n = buildNotification(type);
        if (Build.VERSION.SDK_INT >= 30) startForeground(NID, n, fsType);
        else startForeground(NID, n);
    }

    private void updateNotification() {
        try { getSystemService(NotificationManager.class).notify(NID, buildNotification(kind)); }
        catch (Throwable ignored) {}
    }

    private Notification buildNotification(int type) {
        String what = type == Store.KIND_AUDIO ? "Gravando áudio" : type == Store.KIND_VIDEO ? "Gravando vídeo" : "Tirando foto";
        Intent stop = new Intent(this, RecorderService.class).setAction(A_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE);
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, CH)
                .setSmallIcon(type == Store.KIND_PHOTO ? R.drawable.ic_camera : type == Store.KIND_VIDEO ? R.drawable.ic_video : R.drawable.ic_mic)
                .setColor(Ui.ACCENT)
                .setContentTitle(what)
                .setContentText("Testemunha está registrando a prova")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setUsesChronometer(type != Store.KIND_PHOTO)
                .setWhen(System.currentTimeMillis())
                .setContentIntent(openPi);
        if (type != Store.KIND_PHOTO)
            b.addAction(new Notification.Action.Builder(null, "Parar e salvar", stopPi).build());
        if (Build.VERSION.SDK_INT >= 31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        return b.build();
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(CH, "Gravação", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    @Override public void onDestroy() {
        if (wake != null && wake.isHeld()) wake.release();
        running = false; live = false;
        super.onDestroy();
    }

    // ============================== CHAMADAS DA TELA / ATALHOS ==============================

    /** Câmera escolhida pela pessoa na aba Gravar (traseira, frontal ou as duas). */
    static int camPref(Context c) { return App.cfg(c).getInt("cam", CAM_BACK); }

    static void start(Context c, String action) {
        running = true; live = false;
        kind = A_AUDIO.equals(action) ? Store.KIND_AUDIO : A_VIDEO.equals(action) ? Store.KIND_VIDEO : Store.KIND_PHOTO;
        lastError = null; notice = null; lastSavedId = null; savedCount = 0;
        try {
            c.startForegroundService(new Intent(c, RecorderService.class).setAction(action).putExtra("cam", camPref(c)));
        } catch (Throwable t) {
            running = false; kind = -1;
            lastError = "O Android não deixou iniciar a gravação. Abra o app e tente de novo.";
        }
    }

    static void stop(Context c) {
        try { c.startService(new Intent(c, RecorderService.class).setAction(A_STOP)); }
        catch (Throwable ignored) {}
    }
}
