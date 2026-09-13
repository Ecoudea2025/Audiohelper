package com.cdmarket.listening;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.io.InputStream;

/**
 * CdmMedia — system media notification (play/pause/prev/next) backed by a
 * framework MediaSession. The actual audio keeps playing in the WebView;
 * this plugin only mirrors state to the OS and routes control events back
 * to JS via the "mediaButton" listener ({action: play|pause|next|previous|close}).
 */
@CapacitorPlugin(
  name = "CdmMedia",
  permissions = { @Permission(strings = { Manifest.permission.POST_NOTIFICATIONS }, alias = "notifications") }
)
public class CdmMediaPlugin extends Plugin {

  static final String ACT_PLAY = "com.cdmarket.listening.CDM_PLAY";
  static final String ACT_PAUSE = "com.cdmarket.listening.CDM_PAUSE";
  static final String ACT_PREV = "com.cdmarket.listening.CDM_PREV";
  static final String ACT_NEXT = "com.cdmarket.listening.CDM_NEXT";

  private static final String CHANNEL_ID = "cdm_playback";
  private static final int NOTIF_ID = 1979;

  static CdmMediaPlugin instance;

  private MediaSession session;
  private CdmMediaReceiver receiver;

  // Current track state (mirrored from JS)
  private String track = "";
  private String artist = "";
  private String album = "";
  private String cover = "";
  private long durationMs = 0;
  private long positionMs = 0;
  private boolean isPlaying = false;
  private boolean active = false;

  private PluginCall pendingShowCall;

  private void ensureSession() {
    if (session != null) return;
    Context ctx = getContext();
    session = new MediaSession(ctx, "CdmMedia");
    session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
    session.setCallback(new MediaSession.Callback() {
      @Override public void onPlay() { onAction("play"); }
      @Override public void onPause() { onAction("pause"); }
      @Override public void onSkipToNext() { onAction("next"); }
      @Override public void onSkipToPrevious() { onAction("previous"); }
      @Override public void onStop() { onAction("close"); }
      @Override public void onSeekTo(long pos) { seekTo(pos); }
    });
    IntentFilterHolder.register(ctx, getReceiver());
  }

  private CdmMediaReceiver getReceiver() {
    if (receiver == null) receiver = new CdmMediaReceiver();
    return receiver;
  }

  /** Dynamic receiver registration that works on API 33+ (NOT_EXPORTED). */
  private static class IntentFilterHolder {
    static void register(Context ctx, CdmMediaReceiver r) {
      android.content.IntentFilter f = new android.content.IntentFilter();
      f.addAction(ACT_PLAY);
      f.addAction(ACT_PAUSE);
      f.addAction(ACT_PREV);
      f.addAction(ACT_NEXT);
      if (Build.VERSION.SDK_INT >= 33) {
        ctx.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
      } else {
        ctx.registerReceiver(r, f);
      }
    }
  }

  private PendingIntent actionIntent(String action) {
    Intent i = new Intent(action);
    i.setPackage(getContext().getPackageName());
    int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    int code = action.hashCode();
    return PendingIntent.getBroadcast(getContext(), code, i, flags);
  }

  private void ensureChannel() {
    if (Build.VERSION.SDK_INT < 26) return;
    NotificationManager nm = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
    if (nm.getNotificationChannel(CHANNEL_ID) == null) {
      NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Reproducción", NotificationManager.IMPORTANCE_LOW);
      ch.setDescription("Controles de reproducción del curso");
      ch.setShowBadge(false);
      ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
      nm.createNotificationChannel(ch);
    }
  }

  private Bitmap loadCover() {
    // Bundled web assets live under assets/public/.
    if (cover != null && !cover.isEmpty()) {
      try {
        InputStream in = getContext().getAssets().open("public/" + cover);
        Bitmap b = BitmapFactory.decodeStream(in);
        in.close();
        if (b != null) return b;
      } catch (Exception ignored) {}
    }
    try {
      int icon = getContext().getApplicationInfo().icon;
      return BitmapFactory.decodeResource(getContext().getResources(), icon);
    } catch (Exception ignored) {}
    return null;
  }

  private void publish() {
    Context ctx = getContext();
    ensureChannel();
    ensureSession();

    Bitmap art = loadCover();

    MediaMetadata.Builder mb = new MediaMetadata.Builder()
      .putString(MediaMetadata.METADATA_KEY_TITLE, track)
      .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
      .putString(MediaMetadata.METADATA_KEY_ALBUM, album);
    if (durationMs > 0) mb.putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs);
    if (art != null) {
      mb.putBitmap(MediaMetadata.METADATA_KEY_ART, art);
      mb.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
    }
    session.setMetadata(mb.build());

    long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
      | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS
      | PlaybackState.ACTION_SEEK_TO;
    PlaybackState ps = new PlaybackState.Builder()
      .setActions(actions)
      .setState(isPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, positionMs, isPlaying ? 1.0f : 0.0f)
      .build();
    session.setPlaybackState(ps);
    if (!session.isActive()) session.setActive(true);

    Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
    PendingIntent content = PendingIntent.getActivity(ctx, 0, launch,
      PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

    Notification.Builder nb;
    if (Build.VERSION.SDK_INT >= 26) nb = new Notification.Builder(ctx, CHANNEL_ID);
    else nb = new Notification.Builder(ctx);
    nb.setSmallIcon(R.drawable.ic_music_note)
      .setContentTitle(track.isEmpty() ? "CD Market" : track)
      .setContentText(artist.isEmpty() ? "Listening Course" : artist)
      .setSubText(album.isEmpty() ? null : album)
      .setContentIntent(content)
      .setShowWhen(false)
      .setOngoing(false)
      .setVisibility(Notification.VISIBILITY_PUBLIC)
      .setStyle(new Notification.MediaStyle()
        .setMediaSession(session.getSessionToken())
        .setShowActionsInCompactView(0, 1, 2))
      .addAction(new Notification.Action.Builder(null, "Anterior", actionIntent(ACT_PREV)).build())
      .addAction(new Notification.Action.Builder(null, isPlaying ? "Pausar" : "Reproducir",
        actionIntent(isPlaying ? ACT_PAUSE : ACT_PLAY)).build())
      .addAction(new Notification.Action.Builder(null, "Siguiente", actionIntent(ACT_NEXT)).build());
    if (art != null) nb.setLargeIcon(art);

    NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
    nm.notify(NOTIF_ID, nb.build());
    active = true;
  }

  void onAction(String action) {
    if ("play".equals(action)) isPlaying = true;
    else if ("pause".equals(action)) isPlaying = false;
    if (active) {
      try { publish(); } catch (Exception ignored) {}
    }
    try {
      JSObject data = new JSObject();
      data.put("action", action);
      notifyListeners("mediaButton", data);
    } catch (Exception ignored) {}
  }

  private void seekTo(long pos) {
    positionMs = Math.max(0, pos);
    try {
      JSObject data = new JSObject();
      data.put("action", "seek");
      data.put("position", positionMs / 1000.0);
      notifyListeners("mediaButton", data);
    } catch (Exception ignored) {}
  }

  private boolean notificationsAllowed() {
    if (Build.VERSION.SDK_INT < 33) return true;
    NotificationManager nm = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
    return nm.areNotificationsEnabled();
  }

  private void readFields(PluginCall call) {
    String v;
    if ((v = call.getString("track")) != null) track = v;
    if ((v = call.getString("artist")) != null) artist = v;
    if ((v = call.getString("album")) != null) album = v;
    if ((v = call.getString("cover")) != null) cover = v;
    Double d;
    if ((d = call.getDouble("durationMs")) != null) durationMs = d.longValue();
    if ((d = call.getDouble("elapsedMs")) != null) positionMs = d.longValue();
    Boolean b;
    if ((b = call.getBoolean("isPlaying")) != null) isPlaying = b;
  }

  @PluginMethod
  public void show(PluginCall call) {
    instance = this;
    readFields(call);
    positionMs = 0;
    if (!notificationsAllowed()) {
      pendingShowCall = call;
      requestPermissionForAlias("notifications", call, "notifPermResult");
      return;
    }
    try {
      publish();
      JSObject r = new JSObject();
      r.put("granted", true);
      call.resolve(r);
    } catch (Exception e) {
      call.reject(e.getMessage());
    }
  }

  @PermissionCallback
  public void notifPermResult(PluginCall call) {
    try {
      if (notificationsAllowed()) {
        publish();
        JSObject r = new JSObject();
        r.put("granted", true);
        call.resolve(r);
      } else {
        JSObject r = new JSObject();
        r.put("granted", false);
        call.resolve(r);
      }
    } catch (Exception e) {
      call.reject(e.getMessage());
    }
    pendingShowCall = null;
  }

  @PluginMethod
  public void update(PluginCall call) {
    instance = this;
    readFields(call);
    if (!active) {
      // Nothing shown yet: behave like show (without resetting position).
      if (!notificationsAllowed()) { call.resolve(new JSObject()); return; }
      try { publish(); call.resolve(new JSObject()); }
      catch (Exception e) { call.reject(e.getMessage()); }
      return;
    }
    try {
      publish();
      call.resolve(new JSObject());
    } catch (Exception e) {
      call.reject(e.getMessage());
    }
  }

  @PluginMethod
  public void destroy(PluginCall call) {
    try {
      NotificationManager nm = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
      nm.cancel(NOTIF_ID);
      if (session != null) {
        session.setActive(false);
        session.release();
        session = null;
      }
      active = false;
      call.resolve(new JSObject());
    } catch (Exception e) {
      call.reject(e.getMessage());
    }
  }
}
