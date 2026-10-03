package com.ps3online.dnstester.system;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import android.os.Handler;
import android.os.Looper;

import java.util.HashMap;
import java.util.Map;

public class OnlinePresenceService {

    public interface Listener {
        void onOnlineCountChanged(long count);
        void onPresenceError(String message);
    }

    private static final long HEARTBEAT_MS = 180000L;

    private final Listener listener;
    private final FirebaseAuth auth;
    private final DatabaseReference presenceRoot;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private ValueEventListener countListener;
    private DatabaseReference myPresence;

    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            if (myPresence != null) {

                Map<String, Object> update =
                        new HashMap<>();

                update.put(
                        "online",
                        true
                );

                update.put(
                        "lastSeen",
                        ServerValue.TIMESTAMP
                );

                myPresence.updateChildren(update)
                        .addOnFailureListener(error -> {
                            if (listener != null) {
                                listener.onPresenceError(
                                        "Error actualizando presencia: "
                                                + error.getMessage()
                                );
                            }
                        });

                handler.postDelayed(
                        this,
                        HEARTBEAT_MS
                );
            }
        }
    };

    public OnlinePresenceService(Listener listener) {

        this.listener = listener;

        auth = FirebaseAuth.getInstance();

        presenceRoot =
                FirebaseDatabase.getInstance()
                        .getReference("presence");
    }

    public void start() {

        if (countListener == null) {

            countListener =
                    new ValueEventListener() {

                        @Override
                        public void onDataChange(
                                @NonNull DataSnapshot snapshot) {

                            if (listener != null) {

                                listener.onOnlineCountChanged(
                                        snapshot.getChildrenCount()
                                );
                            }
                        }

                        @Override
                        public void onCancelled(
                                @NonNull DatabaseError error) {

                            if (listener != null) {

                                listener.onPresenceError(
                                        error.getMessage()
                                );
                            }
                        }
                    };

            presenceRoot.addValueEventListener(
                    countListener
            );
        }

        if (auth.getCurrentUser() != null) {

            registerPresence(
                    auth.getCurrentUser().getUid()
            );

        } else {

            auth.signInAnonymously()
                    .addOnSuccessListener(result -> {

                        if (result.getUser() != null) {

                            registerPresence(
                                    result.getUser().getUid()
                            );
                        }
                    })
                    .addOnFailureListener(error -> {

                        if (listener != null) {

                            listener.onPresenceError(
                                    "No se pudo registrar la sesión: "
                                            + error.getMessage()
                            );
                        }
                    });
        }
    }

    private void registerPresence(
            String userId) {

        myPresence =
                presenceRoot.child(userId);

        /*
         * Si la conexión de Firebase se pierde,
         * elimina automáticamente esta presencia.
         */
        myPresence
                .onDisconnect()
                .removeValue();

        Map<String, Object> data =
                new HashMap<>();

        data.put(
                "online",
                true
        );

        data.put(
                "lastSeen",
                ServerValue.TIMESTAMP
        );

        data.put(
                "appVersion",
                "PS3 ONLINE DNS TEST"
        );

        myPresence.setValue(data)
                .addOnFailureListener(error -> {

                    if (listener != null) {

                        listener.onPresenceError(
                                "No se pudo registrar presencia: "
                                        + error.getMessage()
                        );
                    }
                });

        handler.removeCallbacks(
                heartbeat
        );

        handler.postDelayed(
                heartbeat,
                HEARTBEAT_MS
        );
    }

    public void stop() {

        handler.removeCallbacks(
                heartbeat
        );

        if (myPresence != null) {

            myPresence.removeValue();

            myPresence = null;
        }

        if (countListener != null) {

            presenceRoot.removeEventListener(
                    countListener
            );

            countListener = null;
        }
    }
}
