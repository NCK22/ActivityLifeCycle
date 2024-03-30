package com.example.ardemo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.example.ardemo.databinding.ActivityMainBinding;
import com.google.ar.core.Anchor;
import com.google.ar.core.Config;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.assets.RenderableSource;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final String TAG = "MainActivity";
    private static final double MIN_OPENGL_VERSION = 3.0;

    private Session session;
    private ActivityMainBinding activityMainBinding;
    private ModelRenderable shapeRenderable;
    private ModelRenderable modelRenderable;
    private Material originalMaterial;
    private ArSceneView sceneView;
    private Material colorMaterial = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        activityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        sceneView = activityMainBinding.sceneView;

        checkIsSupportedDeviceOrFinish(this);
        if (!isCameraPermissionGranted()) {
            requestCameraPermission();
        }

        // Load the 3D model
        loadModel();

        initializeARCore();

        sceneView.setOnTouchListener(this::onTouched);

//        setListener();
        initRenderable();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Resume ARCore session
        try {
            session.resume();
            sceneView.resume();
        } catch (CameraNotAvailableException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Method to check if camera permission is granted or not.
     */
    private boolean isCameraPermissionGranted() {
        return ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Method to request camera permission to user.
     */
    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_CODE
        );
    }

    public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(TAG, "Sceneform requires Android N or later");
            Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show();
            activity.finish();

            return false;
        }
        String openGlVersionString =
                ((ActivityManager)

                        activity.getSystemService(Context.ACTIVITY_SERVICE))
                        .getDeviceConfigurationInfo()
                        .getGlEsVersion();

        Log.e("##", "openGL version " + Double.parseDouble(openGlVersionString));
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                    .show();
            activity.finish();
            return false;
        }
        return true;
    }

    private void initializeARCore() {
        try {
            // Create a new Session and configure it.
            Log.e("##", "inside initializeARCore");
            session = new Session(getApplicationContext());
            Log.e("##", "session initialized");
            Config config = new Config(session);
            config.setFocusMode(Config.FocusMode.AUTO);
            config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
            config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
            session.configure(config);
            // Connect the Session to the ARSceneView
            sceneView.setupSession(session);
            Log.e("##", "ARCore installed");
        } catch (UnavailableArcoreNotInstalledException e) {
            // Handle ARCore not installed
            Log.e("##", "first catch ARCore not installed");
        } catch (Exception e) {
            // Handle other exceptions
            Log.e("##", "second catch other Exception");
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setListener() {
        // Set touch listener on ArSceneView
        sceneView.setOnTouchListener((View.OnTouchListener) (v, event) -> {
             // Perform a hit test on the ARFrame to get the hit result
            List<HitResult> hitResults =
                    sceneView.getArFrame().hitTest(event);
             // Filter the hit results to find a plane
            if (hitResults != null && !hitResults.isEmpty()) {
                for (HitResult hitResult : hitResults) {
                    Trackable trackable = hitResult.getTrackable();
                    if (trackable instanceof Plane && ((Plane) trackable).isPoseInPolygon(hitResult.getHitPose())) {
                        // Create an anchor at the hit pose
                        Anchor anchor = hitResult.createAnchor();

                        // Create an AnchorNode to hold the anchor
                        AnchorNode anchorNode = new AnchorNode(anchor);
                        anchorNode.setParent(sceneView.getScene());
                        anchorNode.setRenderable(shapeRenderable);
                        anchorNode.setLocalScale(new Vector3(1f, 1f, 1f));

                        // Only handle the first plane tap and break the loop
                        return true;
                    }
                }
            }
            return false;
        });
    }

    private boolean onTouched(View view, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            // Perform a hit test on the ARFrame to get the hit result
            List<HitResult> hitResults = sceneView.getArFrame().hitTest(event);

            // Filter the hit results to find a plane
            if (hitResults != null && !hitResults.isEmpty()) {
                for (HitResult hitResult: hitResults) {
                    Trackable trackable = hitResult.getTrackable();
                    if (trackable instanceof Plane
                            && ((Plane) trackable).isPoseInPolygon(hitResult.getHitPose())) {
                        // Create an anchor at the hit pose
                        Anchor anchor = hitResult.createAnchor();

                        // Create an AnchorNode to hold the anchor
                        AnchorNode anchorNode = new AnchorNode(anchor);
                        anchorNode.setParent(sceneView.getScene());
                        anchorNode.setRenderable(modelRenderable);
                        anchorNode.setLocalScale(new Vector3(0.5f, 0.5f, 0.5f));

                        // Only handle the first plane tap and break the loop
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Release ARCore session
        session.close();
        session = null;
        sceneView = null;
    }

    private void initRenderable() {
        MaterialFactory.makeOpaqueWithColor(this, new
                Color(android.graphics.Color.RED)).thenAccept(material -> originalMaterial =
                material);
        makeOpaqueWithShape(Shape.CUBE);
    }

    private void makeOpaqueWithShape(Shape shapeType) {
        MaterialFactory.makeOpaqueWithColor(this, new
                Color(android.graphics.Color.RED)).thenAccept(material -> {
            switch (shapeType) {
                case CUBE:
                    Vector3 vector3 = new Vector3(0.05f, 0.05f, 0.05f);
                    shapeRenderable = ShapeFactory.makeCube(vector3, Vector3.zero(), material);
                    break;
                case SPHERE:
                    shapeRenderable = ShapeFactory.makeSphere(0.05f, Vector3.zero(), material);
                    break;
                case CYLINDER:
                    shapeRenderable = ShapeFactory.makeCylinder(0.05f, 0.05f, Vector3.zero(), material);
                    break;

            }
            originalMaterial = material;

            shapeRenderable.setShadowCaster(false);
            shapeRenderable.setShadowReceiver(false);

        });
    }

    private void loadModel() {
// Load 3D model here and handle the result in the callback RenderableSource renderableSource =
        RenderableSource renderableSource = RenderableSource.builder()
                .setSource(this, Uri.parse("models/human.glb"),
                        RenderableSource.SourceType.GLB)
                .setRecenterMode(RenderableSource.RecenterMode.CENTER) .setScale(0.1f)
                .build();
        ModelRenderable.builder()
                .setSource(this, renderableSource)
                .build()
                .thenAccept(renderable -> {
                    if (colorMaterial != null) {
                        renderable.setMaterial(colorMaterial);
                    }
                    modelRenderable = renderable;
                });
    }


    enum Shape {
        CUBE,
        SPHERE,
        CYLINDER
    }
}