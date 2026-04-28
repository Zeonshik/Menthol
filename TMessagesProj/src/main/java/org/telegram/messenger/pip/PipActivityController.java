package org.telegram.messenger.pip;

import android.app.Activity;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.pip.activity.IPipActivityActionListener;
import org.telegram.messenger.pip.activity.IPipActivityAnimationListener;
import org.telegram.messenger.pip.activity.IPipActivityHandler;
import org.telegram.messenger.pip.activity.IPipActivityListener;
import org.telegram.messenger.pip.utils.PipUtils;

import java.util.HashMap;

public class PipActivityController {
    private final HashMap<String, PipSource> sources = new HashMap<>();
    private final PipActivityHandler handler;
    private PipActivityContentLayout pipContentView;

    public final Activity activity;

    public PipActivityController(Activity activity) {
        this.activity = activity;
        this.handler = new PipActivityHandler(activity);

    }

    public IPipActivityHandler getHandler() {
        return handler;
    }

    public ViewGroup getPipContentView() {
        if (this.pipContentView == null) {
            this.pipContentView = new PipActivityContentLayout(activity);
        }

        return this.pipContentView;
    }









    private @Nullable PipSource maxPrioritySource;

    private void updateSources() {
        final PipSource oldSource = maxPrioritySource;
        PipSource newSource = null;

        for (PipSource source : sources.values()) {
            if (!source.isAvailable() && !source.state2.isAttachedToPip()) {
                continue;
            }

            if (newSource == null) {
                newSource = source;
            } else if (source.priority > newSource.priority) {
                newSource = source;
            }
        }

        if (oldSource != newSource) {
            maxPrioritySource = newSource;
            onMaxPrioritySourceChanged(oldSource, newSource);
        }
    }

    private void onMaxPrioritySourceChanged(PipSource oldSource, PipSource newSource) {
        PipUtils.applyPictureInPictureParams(activity, newSource);

        final boolean oldMediaSession = oldSource != null && oldSource.needMediaSession;
        final boolean newMediaSession = newSource != null && newSource.needMediaSession;
        if (oldMediaSession != newMediaSession) {
            if (mediaSessionConnector != null) {
                mediaSessionConnector.setPlayer(null);
                mediaSessionConnector = null;
            }
            if (mediaSession != null) {
                mediaSession.setActive(false);
                mediaSession.release();
                mediaSession = null;
                // Log.i(PipSource.TAG, "[MEDIA] stop media session");
            }

            if (newSource != null) {
                mediaSession = new MediaSessionCompat(activity, "pip-media-session");
                mediaSession.setQueue(null);
                mediaSession.setActive(true);
                mediaSessionConnector = new MediaSessionConnector(mediaSession);

                // Log.i(PipSource.TAG, "[MEDIA] start media session");
            }
        }

        if (oldSource != null) {
            oldSource.state2.onLoseMaxPriority();
        }

        if (newSource != null) {
            if (mediaSessionConnector != null) {
                mediaSessionConnector.setPlayer(newSource.player);
            }
            pipContentView.bringToFront();
            newSource.state2.onReceiveMaxPriority();
        } else if (oldSource != null) {
            if (AndroidUtilities.isInPictureInPictureMode(activity)) {
                activity.moveTaskToBack(false);
            }
        }



        pipContentView.invalidate();
    }

    private MediaSessionCompat mediaSession;
    MediaSessionConnector mediaSessionConnector;





    /* Source Internal */

    void dispatchSourceRegister(PipSource source) {
        this.sources.put(source.tag, source);
        updateSources();
    }

    void dispatchSourceUnregister(PipSource source) {
        if (this.sources.remove(source.tag) != null) {
            updateSources();
        }
    }

    void dispatchSourceAvailabilityChanged(PipSource source) {
        updateSources();
        pipContentView.invalidate();
    }

    void dispatchSourceParamsChanged(PipSource source) {
        if (maxPrioritySource == source) {
            PipUtils.applyPictureInPictureParams(activity, source);
            if (mediaSessionConnector != null) {
                mediaSessionConnector.setPlayer(source.player);
            }
        }
        pipContentView.invalidate();
    }



    /* * */

    public boolean hasContentForPictureInPictureMode() {
        return maxPrioritySource != null;
    }

    public void addPipListener(IPipActivityListener listener) {
        handler.addPipListener(listener);
    }

    public void removePipListener(IPipActivityListener listener) {
        handler.removePipListener(listener);
    }

    public void addAnimationListener(IPipActivityAnimationListener listener) {
        handler.addAnimationListener(listener);
    }

    public void removeAnimationListener(IPipActivityAnimationListener listener) {
        handler.removeAnimationListener(listener);
    }

    public void addActionListener(String sourceId, IPipActivityActionListener listener) {
        handler.addActionListener(sourceId, listener);
    }

    public void removeActionListener(String sourceId, IPipActivityActionListener listener) {
        handler.removeActionListener(sourceId, listener);
    }
}
