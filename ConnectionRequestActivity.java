
package com.mobileconnect.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.*;

import org.webrtc.*;

import java.util.*;

public class ConnectionRequestActivity extends AppCompatActivity {

    private static final int SCREEN_CAPTURE_REQUEST_CODE = 100;
    private static final String TAG = "ConnectionRequest";

    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;
    private SurfaceTextureHelper surfaceTextureHelper;
    private VideoSource videoSource;
    private VideoTrack videoTrack;

    private EglBase rootEglBase;

    private String localId;
    private String remoteId;
    private DatabaseReference signalingRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rootEglBase = EglBase.create();
        localId = FirebaseAuth.getInstance().getUid();
        remoteId = getIntent().getStringExtra("remoteId");

        initFirebase();
        initWebRTC();
        showPermissionDialog();
    }

    private void initFirebase() {
        signalingRef = FirebaseDatabase.getInstance().getReference("signals");
        listenForSignal();
    }

    private void initWebRTC() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(this)
                .setEnableInternalTracer(true).createInitializationOptions());

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(), true, true))
            .setVideoDecoderFactory(new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext()))
            .createPeerConnectionFactory();
    }

    private void showPermissionDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Are you sure?")
            .setMessage("Do you want to allow screen sharing and control?")
            .setCancelable(false)
            .setPositiveButton("YES", (dialog, which) -> startScreenCapture())
            .setNegativeButton("NO", (dialog, which) -> {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                finish();
            }).show();
    }

    private void startScreenCapture() {
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (manager != null) {
            Intent intent = manager.createScreenCaptureIntent();
            startActivityForResult(intent, SCREEN_CAPTURE_REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int reqCode, int resultCode, @Nullable Intent data) {
        if (reqCode == SCREEN_CAPTURE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            createPeerConnection();
            startStreaming(data, resultCode);
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
        }
        super.onActivityResult(reqCode, resultCode, data);
    }

    private void createPeerConnection() {
        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(new ArrayList<>());
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        peerConnection = factory.createPeerConnection(config, new PeerConnection.Observer() {
            @Override public void onIceCandidate(IceCandidate candidate) {
                Map<String, Object> map = new HashMap<>();
                map.put("type", "ice");
                map.put("sdpMid", candidate.sdpMid);
                map.put("sdpMLineIndex", candidate.sdpMLineIndex);
                map.put("candidate", candidate.sdp);
                signalingRef.child(remoteId).push().setValue(map);
            }
            @Override public void onAddStream(MediaStream stream) {}
            @Override public void onTrack(RtpTransceiver transceiver) {}
            @Override public void onConnectionChange(PeerConnection.PeerConnectionState state) {}
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) {}
            @Override public void onSignalingChange(PeerConnection.SignalingState state) {}
            @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) {}
            @Override public void onRemoveStream(MediaStream stream) {}
            @Override public void onDataChannel(DataChannel dc) {}
            @Override public void onRenegotiationNeeded() {}
        });
    }

    private void startStreaming(Intent data, int resultCode) {
        VideoCapturer capturer = createScreenCapturer(data, resultCode);
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());

        videoSource = factory.createVideoSource(false);
        capturer.initialize(surfaceTextureHelper, getApplicationContext(), videoSource.getCapturerObserver());
        capturer.startCapture(720, 1280, 30);

        videoTrack = factory.createVideoTrack("100", videoSource);
        peerConnection.addTrack(videoTrack);
        peerConnection.createOffer(new SdpAdapter("local offer") {
            @Override public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new SdpAdapter("set local"), sessionDescription);
                Map<String, Object> offer = new HashMap<>();
                offer.put("type", "offer");
                offer.put("sdp", sessionDescription.description);
                signalingRef.child(remoteId).push().setValue(offer);
            }
        }, new MediaConstraints());
    }

    private void listenForSignal() {
        signalingRef.child(localId).addChildEventListener(new ChildEventListener() {
            @Override public void onChildAdded(DataSnapshot snapshot, String prev) {
                Map<String, String> map = (Map<String, String>) snapshot.getValue();
                if (map == null || !map.containsKey("type")) return;
                String type = map.get("type");

                if ("answer".equals(type)) {
                    SessionDescription answer = new SessionDescription(SessionDescription.Type.ANSWER, map.get("sdp"));
                    peerConnection.setRemoteDescription(new SdpAdapter("remote answer"), answer);
                } else if ("ice".equals(type)) {
                    IceCandidate candidate = new IceCandidate(map.get("sdpMid"),
                        Integer.parseInt(map.get("sdpMLineIndex")), map.get("candidate"));
                    peerConnection.addIceCandidate(candidate);
                }
            }

            @Override public void onChildChanged(DataSnapshot ds, String prev) {}
            @Override public void onChildRemoved(DataSnapshot ds) {}
            @Override public void onChildMoved(DataSnapshot ds, String prev) {}
            @Override public void onCancelled(DatabaseError err) {}
        });
    }

    private VideoCapturer createScreenCapturer(Intent data, int resultCode) {
        return new ScreenCapturerAndroid(data, new MediaProjection.Callback() {});
    }
}
