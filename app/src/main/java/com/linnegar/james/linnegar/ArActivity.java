package com.linnegar.james.linnegar;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Toast;

import java.util.Random;

import com.google.ar.core.Anchor;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;

import android.os.Vibrator;


import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;


public class ArActivity extends AppCompatActivity {

    Session mSession;

    private static final String TAG = ArActivity.class.getSimpleName();
    private static final double MIN_OPENGL_VERSION = 3.0;

    private ArFragment arFragment;
    private ArSceneView arSceneView;
    private ModelRenderable modelRenderable;

    private boolean modelAdded = false; // only add model once
    private boolean sessionConfigured = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!checkIsSupportedDeviceOrFinish(this)) {
            return;
        }

        setContentView(R.layout.activity_ar);

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);

        arSceneView= arFragment.getArSceneView();
        // hiding the plane discovery

        arFragment.getArSceneView().getScene().addOnUpdateListener(this::onUpdateFrame);

        arFragment.setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                    if (modelRenderable == null) {
                        return;
                    }

                    Anchor anchor = hitResult.createAnchor();
                    addParty(anchor);
                });
        loadModels();

    }

    private void loadModels() {

        ModelRenderable.builder()
                .setSource(this, Uri.parse("Balloon.sfb"))
                .build()
                .thenAccept(renderable -> modelRenderable = renderable);

    }

    private boolean setupAugmentedImageDb(Config config) {
        AugmentedImageDatabase augmentedImageDatabase;

        Bitmap augmentedImageBitmap = loadAugmentedImage();
        if (augmentedImageBitmap == null) {
            return false;
        }

        augmentedImageDatabase = new AugmentedImageDatabase(mSession);
        augmentedImageDatabase.addImage("animals", augmentedImageBitmap);

        config.setAugmentedImageDatabase(augmentedImageDatabase);
        return true;
    }

    private Bitmap loadAugmentedImage(){
        try (InputStream is = getAssets().open("animals.jpg")){
            return BitmapFactory.decodeStream(is);
        }
        catch (IOException e){
            Log.e("ImageLoad", "IO Exception while loading", e);
        }
        return null;
    }


    private void onUpdateFrame(FrameTime frameTime){
        Frame frame = arFragment.getArSceneView().getArFrame();

        Collection<AugmentedImage> augmentedImages = frame.getUpdatedTrackables(AugmentedImage.class);

        for (AugmentedImage augmentedImage : augmentedImages)
            if (augmentedImage.getTrackingState() == TrackingState.TRACKING) {

                if (!modelAdded || modelRenderable == null) {
                    Pose centerPose = augmentedImage.getCenterPose();

                    // Create the Anchor.
                    Anchor anchor = augmentedImage.createAnchor(centerPose);

                    addParty(anchor);

                    Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    v.vibrate(400);
                }
            }

    }

    public void addParty(Anchor anchor){
        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(arFragment.getArSceneView().getScene());

        Random rand = new Random();

        // Create the transformable andy and add it to the anchor.
        for (int i = 0; i < 10; i++) {
            Node node = new Node();
            float distance = 0.8f;
            float height = rand.nextFloat() * distance * 4;
            float x = (rand.nextFloat() * distance) - (distance / 2);
            float z = (rand.nextFloat() * distance) - (distance / 2);

            node.setParent(anchorNode);
            node.setLocalScale(new Vector3(0.5f, 0.5f, 0.5f));
            node.setLocalPosition(new Vector3(x, height, z));

            MaterialFactory.makeOpaqueWithColor(this, new Color(rand.nextFloat(), rand.nextFloat(), rand.nextFloat(), 0.8f))
                    .thenAccept(
                            material -> {
                                ModelRenderable balloonRenderable = modelRenderable.makeCopy();
                                balloonRenderable.setMaterial(material);
                                node.setRenderable(balloonRenderable);
                            });

        }
        modelAdded = true;
    }


    @Override
    public void onPause() {
        super.onPause();
        if (mSession != null) {
            arSceneView.pause();
            mSession.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mSession == null) {
            String message = null;
            Exception exception = null;
            try {
                mSession = new Session(this);
            } catch (UnavailableArcoreNotInstalledException
                    e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update android";
                exception = e;
            } catch (Exception e) {
                message = "AR is not supported";
                exception = e;
            }

            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Exception creating session", exception);
                return;
            }
            sessionConfigured = true;

        }
        if (sessionConfigured) {
            configureSession();
            sessionConfigured = false;

            arSceneView.setupSession(mSession);
        }

    }

    private void configureSession() {
        Config config = new Config(mSession);
        if (!setupAugmentedImageDb(config)) {
            Toast.makeText(this, "Unable to setup augmented", Toast.LENGTH_SHORT).show();
        }
        config.setFocusMode(Config.FocusMode.AUTO);
        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
        mSession.configure(config);
    }

    public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(TAG, "Sceneform requires Android N or later");
            Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show();
            activity.finish();
            return false;
        }
        String openGlVersionString =
                ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
                        .getDeviceConfigurationInfo()
                        .getGlEsVersion();
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                    .show();
            activity.finish();
            return false;
        }
        Toast.makeText(activity, "All Good", Toast.LENGTH_LONG).show();
        return true;
    }

}
