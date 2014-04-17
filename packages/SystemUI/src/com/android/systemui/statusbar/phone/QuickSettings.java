/*
 * Copyright (C) 2012 The Android Open Source Project
 * This code has been modified. Portions copyright (C) 2013-2014 ParanoidAndroid Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.phone;

import android.animation.ValueAnimator;
import android.app.ActivityManagerNative;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.ExifInterface;
import android.media.IAudioService;
import android.media.MediaMetadataEditor;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaRecorder;
import android.media.MediaRouter;
import android.media.RemoteControlClient;
import android.media.RemoteController;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.AlarmClock;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Profile;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.MediaColumns;
import android.provider.Settings;
import android.security.KeyChain;
import android.telephony.TelephonyManager;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.util.Pair;
import android.util.SettingConfirmationHelper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.app.MediaRouteDialogPresenter;
import com.android.systemui.BatteryMeterView;
import com.android.systemui.BatteryCircleMeterView;
import com.android.systemui.BatteryPieMeterView;
import com.android.internal.app.MediaRouteDialogPresenter;
import com.android.internal.util.omni.DeviceUtils;
import com.android.internal.util.slim.TRDSActions;
import com.android.internal.util.slim.TRDSConstant;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsModel.ActivityState;
import com.android.systemui.statusbar.phone.QuickSettingsModel.BluetoothState;
import com.android.systemui.statusbar.phone.QuickSettingsModel.RSSIState;
import com.android.systemui.statusbar.phone.QuickSettingsModel.State;
import com.android.systemui.statusbar.phone.QuickSettingsModel.UserState;
import com.android.systemui.statusbar.phone.QuickSettingsModel.WifiState;
import com.android.systemui.statusbar.phone.QuickSettingsModel.RecordingState;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.RotationLockController;

import com.android.internal.util.omni.OmniTorchConstants;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 *
 */
class QuickSettings {
    static final boolean DEBUG_GONE_TILES = false;
    private static final String TAG = "QuickSettings";
    public static final boolean SHOW_IME_TILE = false;
    private static final String AUTO_START = "AUTO_START";
    private static final String TOGGLE_FLASHLIGHT = "TOGGLE_FLASHLIGHT";
    private static final String DEFAULT_IMAGE_FILE_NAME_FORMAT = "'IMG'_yyyyMMdd_HHmmss";
    private static final int CAMERA_ID = 0;

    public enum Tile {
        USER,
        BRIGHTNESS,
        SETTINGS,
        WIFI,
        RSSI,
        ROTATION,
        BATTERY,
        AIRPLANE,
        BLUETOOTH,
        LOCATION,
        IMMERSIVE,
        SLEEP,
        SYNC,
        NETADB,
        TORCH,
        VOLUME,
        THEME_MODE,
        NFC,
        QUICKRECORD,
        CAMERA,
        MUSIC,
        FCHARGE
    }

    public static final String NO_TILES = "NO_TILES";
    public static final String DELIMITER = ";";
    public static final String DEFAULT_TILES = Tile.USER 
        + DELIMITER + Tile.BRIGHTNESS
        + DELIMITER + Tile.SETTINGS 
        + DELIMITER + Tile.WIFI 
        + DELIMITER + Tile.RSSI
        + DELIMITER + Tile.ROTATION 
        + DELIMITER + Tile.BATTERY 
        + DELIMITER + Tile.AIRPLANE
        + DELIMITER + Tile.BLUETOOTH
        + DELIMITER + Tile.LOCATION 
        + DELIMITER + Tile.IMMERSIVE 
        + DELIMITER + Tile.SLEEP
        + DELIMITER + Tile.SYNC 
        + DELIMITER + Tile.NETADB
        + DELIMITER + Tile.TORCH 
        + DELIMITER + Tile.VOLUME
        + DELIMITER + Tile.THEME_MODE
        + DELIMITER + Tile.NFC
        + DELIMITER + Tile.QUICKRECORD
        + DELIMITER + Tile.CAMERA
        + DELIMITER + Tile.MUSIC
        + DELIMITER + Tile.FCHARGE;


    private Context mContext;
    private PanelBar mBar;
    private QuickSettingsModel mModel;
    private View mIconContainer;
    private FrameLayout mSurfaceLayout;
    private SurfaceView mSurfaceView;
    private View mFlashView;
    private ViewGroup mContainerView;
    private Camera mCamera;
    private CameraOrientationListener mCameraOrientationListener = null;
    private int mOrientation;
    private int mJpegRotation;
    private int mDisplayRotation;
    private Camera.Size mCameraSize;
    private boolean mCameraStarted;
    private boolean mCameraBusy;

    private Camera.CameraInfo mCameraInfo = new Camera.CameraInfo();
    private Camera.Parameters mParams;
    final QuickSettingsBasicCameraTile cameraTile;

    private boolean mActive = false;
    private boolean mClientIdLost = true;
    protected Metadata mMetadata = new Metadata();
    
    private RemoteController mRemoteController;
    private IAudioService mAudioService = null;

    private Storage mStorage = new Storage();
    private SimpleDateFormat mImageNameFormatter;

    private DevicePolicyManager mDevicePolicyManager;
    private PhoneStatusBar mStatusBarService;
    private BluetoothState mBluetoothState;
//    private RecordingState QuickSettingsModel.mRecordingState;
    private BluetoothAdapter mBluetoothAdapter;
    private WifiManager mWifiManager;
    private ConnectivityManager mConnectivityManager;

    private BluetoothController mBluetoothController;
    private RotationLockController mRotationLockController;
    private LocationController mLocationController;

    private AsyncTask<Void, Void, Pair<String, Drawable>> mUserInfoTask;
    private AsyncTask<Void, Void, Pair<Boolean, Boolean>> mQueryCertTask;

    boolean mTilesSetUp = false;
    boolean mUseDefaultAvatar = false;

    private File mFile;
    private Handler mHandler;
    private QuickSettingsBasicBatteryTile batteryTile;
    private int mBatteryStyle;
    private int mThemeAutoMode;
    private MediaPlayer mPlayer = null;
    private MediaRecorder mRecorder = null;
    private static String mQuickAudio = null;

    protected boolean fcenabled = false;

    public static final int THEME_MODE_MANUAL       = 0;
    public static final int THEME_MODE_LIGHT_SENSOR = 1;
    public static final int THEME_MODE_TWILIGHT     = 2;


    public static final int QR_IDLE = 0;
    public static final int QR_PLAYING = 1;
    public static final int QR_RECORDING = 2;
    public static final int QR_JUST_RECORDED = 3;
    public static final int QR_NO_RECORDING = 4;
    public static final int MAX_RECORD_TIME = 120000; 

    public QuickSettings(Context context, QuickSettingsContainerView container) {
        mDevicePolicyManager
            = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        mContext = context;
        cameraTile = new QuickSettingsBasicCameraTile(context);
        mContainerView = container;
        mModel = new QuickSettingsModel(context);
        mBluetoothState = new QuickSettingsModel.BluetoothState();
//        QuickSettingsModel.mRecordingState = QuickSettingsModel.mRecordingState;


        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mConnectivityManager =
                   (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        mHandler = new Handler();
        mFile = new File(mContext.getFilesDir() + File.separator
                + "quickrecord.3gp");
        mQuickAudio = mFile.getAbsolutePath();


        IntentFilter filter = new IntentFilter();
        filter.addAction(DisplayManager.ACTION_WIFI_DISPLAY_STATUS_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        filter.addAction(KeyChain.ACTION_STORAGE_CHANGED);
        mContext.registerReceiver(mReceiver, filter);

        IntentFilter profileFilter = new IntentFilter();
        profileFilter.addAction(ContactsContract.Intents.ACTION_PROFILE_CHANGED);
        profileFilter.addAction(Intent.ACTION_USER_INFO_CHANGED);
        mContext.registerReceiverAsUser(mProfileReceiver, UserHandle.ALL, profileFilter,
                null, null);
    }

    void setBar(PanelBar bar) {
        mBar = bar;
    }

    public void setService(PhoneStatusBar phoneStatusBar) {
        mStatusBarService = phoneStatusBar;
    }

    public PhoneStatusBar getService() {
        return mStatusBarService;
    }

    public void setImeWindowStatus(boolean visible) {
        mModel.onImeWindowStatusChanged(visible);
    }

    void setup(NetworkController networkController, BluetoothController bluetoothController,
            BatteryController batteryController, LocationController locationController,
            RotationLockController rotationLockController) {
        mBluetoothController = bluetoothController;
        mRotationLockController = rotationLockController;
        mLocationController = locationController;

        setupQuickSettings();
        updateResources();
        applyLocationEnabledStatus();

        networkController.addNetworkSignalChangedCallback(mModel);
        bluetoothController.addStateChangedCallback(mModel);
        batteryController.addStateChangedCallback(mModel);
        locationController.addSettingsChangedCallback(mModel);
        rotationLockController.addRotationLockControllerCallback(mModel);
    }

    private void queryForSslCaCerts() {
        mQueryCertTask = new AsyncTask<Void, Void, Pair<Boolean, Boolean>>() {
            @Override
            protected Pair<Boolean, Boolean> doInBackground(Void... params) {
                boolean hasCert = DevicePolicyManager.hasAnyCaCertsInstalled();
                boolean isManaged = mDevicePolicyManager.getDeviceOwner() != null;

                return Pair.create(hasCert, isManaged);
            }
            @Override
            protected void onPostExecute(Pair<Boolean, Boolean> result) {
                super.onPostExecute(result);
                boolean hasCert = result.first;
                boolean isManaged = result.second;
                mModel.setSslCaCertWarningTileInfo(hasCert, isManaged);
            }
        };
        mQueryCertTask.execute();
    }

    private void queryForUserInformation() {
        Context currentUserContext = null;
        UserInfo userInfo = null;
        try {
            userInfo = ActivityManagerNative.getDefault().getCurrentUser();
            currentUserContext = mContext.createPackageContextAsUser("android", 0,
                    new UserHandle(userInfo.id));
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Couldn't create user context", e);
            throw new RuntimeException(e);
        } catch (RemoteException e) {
            Log.e(TAG, "Couldn't get user info", e);
        }
        final int userId = userInfo.id;
        final String userName = userInfo.name;

        final Context context = currentUserContext;
        mUserInfoTask = new AsyncTask<Void, Void, Pair<String, Drawable>>() {
            @Override
            protected Pair<String, Drawable> doInBackground(Void... params) {
                final UserManager um = UserManager.get(mContext);

                // Fall back to the UserManager nickname if we can't read the name from the local
                // profile below.
                String name = userName;
                Drawable avatar = null;
                Bitmap rawAvatar = um.getUserIcon(userId);
                if (rawAvatar != null) {
                    avatar = new BitmapDrawable(mContext.getResources(), rawAvatar);
                } else {
                    avatar = mContext.getResources().getDrawable(R.drawable.ic_qs_default_user);
                    mUseDefaultAvatar = true;
                }

                // If it's a single-user device, get the profile name, since the nickname is not
                // usually valid
                if (um.getUsers().size() <= 1) {
                    // Try and read the display name from the local profile
                    final Cursor cursor = context.getContentResolver().query(
                            Profile.CONTENT_URI, new String[] {Phone._ID, Phone.DISPLAY_NAME},
                            null, null, null);
                    if (cursor != null) {
                        try {
                            if (cursor.moveToFirst()) {
                                name = cursor.getString(cursor.getColumnIndex(Phone.DISPLAY_NAME));
                            }
                        } finally {
                            cursor.close();
                        }
                    }
                }
                return new Pair<String, Drawable>(name, avatar);
            }

            @Override
            protected void onPostExecute(Pair<String, Drawable> result) {
                super.onPostExecute(result);
                mModel.setUserTileInfo(result.first, result.second);
                mUserInfoTask = null;
            }
        };
        mUserInfoTask.execute();
    }

    private void setupQuickSettings() {
        addTiles(mContainerView, false);
        addTemporaryTiles(mContainerView);

        queryForUserInformation();
        queryForSslCaCerts();
        mTilesSetUp = true;
    }

    private void startSettingsActivity(final String action) {
        if (immersiveStyleSelected()) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(action);
                    startSettingsActivity(intent);
                }
            }, 70);
        } else {
           Intent intent = new Intent(action);
           startSettingsActivity(intent);
        }
    }

    private void startSettingsActivity(final Intent intent) {
        if (immersiveStyleSelected()) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startSettingsActivity(intent, true);
                }
            }, 70);
        } else {
           startSettingsActivity(intent, true);
        }
    }

    private void collapsePanels() {
        getService().animateCollapsePanels();
    }

    private void startSettingsActivity(Intent intent, boolean onlyProvisioned) {
        if (onlyProvisioned && !getService().isDeviceProvisioned()) return;
        try {
            // Dismiss the lock screen when Settings starts.
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (RemoteException e) {
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mContext.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
        collapsePanels();
    }

    public void updateBattery() {
        if (batteryTile == null || mModel == null) {
            return;
        }
        mBatteryStyle = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_BATTERY_STYLE, 0);
        batteryTile.updateBatterySettings();
        mModel.refreshBatteryTile();
    }

   private boolean immersiveStyleSelected() {
        int selection = Settings.System.getInt(mContext.getContentResolver(),
                            Settings.System.PIE_STATE, 0);
        return selection == 1 || selection == 2;
    }

    private void addTiles(ViewGroup parent, boolean addMissing) {
        // Load all the customizable tiles. If not yet modified by the user, load default ones.
        // After enabled tiles are loaded, proceed to load missing tiles and set them to View.GONE.
        // If all the tiles were deleted, they are still loaded, but their visibility is changed
        String tileContainer = Settings.System.getString(mContext.getContentResolver(),
                Settings.System.QUICK_SETTINGS_TILES);
        if(tileContainer == null) tileContainer = DEFAULT_TILES;
        Tile[] allTiles = Tile.values();
        String[] storedTiles = tileContainer.split(DELIMITER);
        List<String> allTilesArray = enumToStringArray(allTiles);
        List<String> storedTilesArray = Arrays.asList(storedTiles);

        for(String tile : addMissing ? allTilesArray : storedTilesArray) {
            boolean addTile = storedTilesArray.contains(tile);
            if(addMissing) addTile = !addTile;
            if(addTile) {
                if(Tile.USER.toString().equals(tile.toString())) { // User tile
                    final QuickSettingsBasicUserTile userTile
                        = new QuickSettingsBasicUserTile(mContext);
                    userTile.setTileId(Tile.USER);
                    userTile.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            collapsePanels();
                            final UserManager um = UserManager.get(mContext);
                            if (um.getUsers(true).size() > 1) {
                                try {
                                    WindowManagerGlobal.getWindowManagerService().lockNow(null);
                                } catch (RemoteException e) {
                                    Log.e(TAG, "Couldn't show user switcher", e);
                                }
                            } else {
                                Intent intent = ContactsContract.QuickContact
                                        .composeQuickContactsIntent(mContext, v,
                                        ContactsContract.Profile.CONTENT_URI,
                                        ContactsContract.QuickContact.MODE_LARGE, null);
                                mContext.startActivityAsUser(intent,
                                        new UserHandle(UserHandle.USER_CURRENT));
                            }
                        }
                    });
                    userTile.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            collapsePanels();
                            startSettingsActivity(
                                    android.provider.Settings.ACTION_SYNC_SETTINGS);
                            return true; // Consume click
                        }
                    });
                    mModel.addUserTile(userTile, new QuickSettingsModel.RefreshCallback() {
                        @Override
                        public void refreshView(QuickSettingsTileView view, State state) {
                            UserState us = (UserState) state;
                            userTile.setImageDrawable(us.avatar);
                            userTile.setText(state.label);
                            userTile.setContentDescription(mContext.getString(
                                    R.string.accessibility_quick_settings_user, state.label));
                        }
                    });
                    parent.addView(userTile);
                    if(addMissing) userTile.setVisibility(View.GONE);
                } else if(Tile.BRIGHTNESS.toString().equals(tile.toString())) { // Brightness tile
                    final QuickSettingsBasicTile brightnessTile
                            = new QuickSettingsBasicTile(mContext);
                    brightnessTile.setTileId(Tile.BRIGHTNESS);
                    brightnessTile.setImageResource(R.drawable.ic_qs_brightness_auto_off);
                    brightnessTile.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            collapsePanels();
                            showBrightnessDialog();
                        }
                    });
                    brightnessTile.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            boolean automaticAvailable = mContext.getResources().getBoolean(
                                    com.android.internal.R.bool.config_automatic_brightness_available);
                            // If we have automatic brightness available, toggle it
                            if (automaticAvailable) {
                                int automatic;
                                try {
                                    automatic = Settings.System.getIntForUser(mContext.getContentResolver(),
                                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                                            UserHandle.USER_CURRENT);
                                } catch (SettingNotFoundException snfe) {
                                    automatic = 0;
                                }
                                Settings.System.putIntForUser(mContext.getContentResolver(),
                                        Settings.System.SCREEN_BRIGHTNESS_MODE, automatic != 0 ? 0 : 1,
                                        UserHandle.USER_CURRENT);
                            }
                            return true; // Consume click
                        }
                    });
                    mModel.addBrightnessTile(brightnessTile,
                            new QuickSettingsModel.BasicRefreshCallback(brightnessTile));
                    parent.addView(brightnessTile);
                    if(addMissing) brightnessTile.setVisibility(View.GONE);
                } else if(Tile.FCHARGE.toString().equals(tile.toString())) { // Brightness tile
                    final QuickSettingsBasicTile fchargeTile
                            = new QuickSettingsBasicTile(mContext);
                    fchargeTile.setTileId(Tile.FCHARGE);
                    fchargeTile.setImageResource(R.drawable.ic_qs_fcharge_off);
                    fchargeTile.setTextResource(R.string.quick_settings_fcharge);
                    fchargeTile.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            try {
                                fcenabled = !mModel.isFastChargeOn();
                                String fchargePath = mContext.getResources()
                                  .getString(com.android.internal.R.string.config_fastChargePath);
                                if (!fchargePath.isEmpty()) {
                                    File fastcharge = new File(fchargePath);
                                    if (fastcharge.exists()) {
                                        FileWriter fwriter = new FileWriter(fastcharge);
                                        BufferedWriter bwriter = new BufferedWriter(fwriter);
                                        bwriter.write(fcenabled ? "1" : "0");
                                        bwriter.close();
                                        Settings.System.putInt(mContext.getContentResolver(),
                                            Settings.System.FCHARGE_ENABLED, fcenabled ? 1 : 0);
                                    }
                                }
                            } catch (IOException e) {
                                Log.e("FChargeToggle", "Couldn't write fast_charge file");
                                Settings.System.putInt(mContext.getContentResolver(),
                                  Settings.System.FCHARGE_ENABLED, 0);
                            }
                            mModel.updateFastChargeTile();
                        }
                    });
                    mModel.addFastChargeTile(fchargeTile,
                            new QuickSettingsModel.BasicRefreshCallback(fchargeTile));
                    parent.addView(fchargeTile);
                    if(addMissing) fchargeTile.setVisibility(View.GONE);
                } else if(Tile.SETTINGS.toString().equals(tile.toString())) { // Settings tile
                    final QuickSettingsBasicTile settingsTile
                            = new QuickSettingsBasicTile(mContext);
                    settingsTile.setTileId(Tile.SETTINGS);
                    settingsTile.setImageResource(R.drawable.ic_qs_settings);
                    settingsTile.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            collapsePanels();
                            startSettingsActivity(
                                    android.provider.Settings.ACTION_SETTINGS);
                        }
                    });
                    settingsTile.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            collapsePanels();
                            Intent intent = new Intent(Intent.ACTION_MAIN);
                            intent.addFlags(Intent.FLAG_FLOATING_WINDOW);
                            intent.setClassName("com.android.settings", "com.android.settings.Settings");
                            startSettingsActivity(intent);
                            return true;
                        }
                    });
                    mModel.addSettingsTile(settingsTile,
                            new QuickSettingsModel.BasicRefreshCallback(settingsTile));
                    parent.addView(settingsTile);
                    if(addMissing) settingsTile.setVisibility(View.GONE);
                } else if(Tile.WIFI.toString().equals(tile.toString())) { // Wi-fi tile
                    final QuickSettingsDualWifiTile wifiTile
                            = new QuickSettingsDualWifiTile(mContext);
                    wifiTile.setTileId(Tile.WIFI);
                    // Front side (Turn on/off wifi connection)
                    wifiTile.setFrontOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            final boolean enable =
                                    (mWifiManager.getWifiState() !=
                                            WifiManager.WIFI_STATE_ENABLED);
                            new AsyncTask<Void, Void, Void>() {
                                @Override
                                protected Void doInBackground(Void... args) {
                                    // Disable tethering if enabling Wifi
                                    final int wifiApState = mWifiManager.getWifiApState();
                                    if (enable &&
                                            ((wifiApState == WifiManager.WIFI_AP_STATE_ENABLING) ||
                                            (wifiApState == WifiManager.WIFI_AP_STATE_ENABLED))) {
                                        mWifiManager.setWifiApEnabled(null, false);
                                    }

                                    mWifiManager.setWifiEnabled(enable);
                                    return null;
                                }
                            }.execute();
                        }
                    });
                    wifiTile.setFrontOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            collapsePanels();
                            startSettingsActivity(
                                    android.provider.Settings.ACTION_WIFI_SETTINGS);
                            return true; // Consume click
                        }
                    });
                    mModel.addWifiTile(wifiTile.getFront(), new NetworkActivityCallback() {
                        @Override
                        public void refreshView(QuickSettingsTileView view, State state) {
                            WifiState wifiState = (WifiState) state;
                            wifiTile.setFrontImageResource(wifiState.iconId);
                            setActivity(view, wifiState);
                            wifiTile.setFrontText(wifiState.label);
                            wifiTile.setFrontContentDescription(mContext.getString(
                                    R.string.accessibility_quick_settings_wifi,
                                    wifiState.signalContentDescription,
                                    (wifiState.connected) ? wifiState.label : ""));
                        }
                    });
                    // Back side (Turn on/off wifi AP)
                    wifiTile.setBackOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mModel.toggleWifiApState();
                        }
                    });
                    wifiTile.setBackOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            collapsePanels();
                            Intent intent = new Intent(Intent.ACTION_MAIN);
                            intent.setClassName("com.android.settings", "com.android.settings.TetherSettings");
                            startSettingsActivity(intent);
                            return true; // Consume click
                        }
                    });
                    mModel.addWifiApTile(wifiTile.getBack(), new QuickSettingsModel.RefreshCallback() {
                        @Override
                        public void refreshView(QuickSettingsTileView unused, State state) {
                            wifiTile.setBackImageResource(state.iconId);
                            wifiTile.setBackText(state.label);
                        }
                    });
                    parent.addView(wifiTile);
                    if(addMissing) wifiTile.setVisibility(View.GONE);
                } else if(Tile.RSSI.toString().equals(tile.toString())) { // RSSI tile
                    if (mModel.deviceHasMobileData()) {
                        final QuickSettingsDualRssiTile rssiTile
                            = new QuickSettingsDualRssiTile(mContext);
                        rssiTile.setTileId(Tile.RSSI);
                        // Front side (Turn on/off data)
                        rssiTile.setFrontText(mContext.getString(R.string.quick_settings_network_type));
                        rssiTile.setFrontOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                boolean currentState = mConnectivityManager.getMobileDataEnabled();
                                mConnectivityManager.setMobileDataEnabled(!currentState);
                            }
                        });
                        rssiTile.setFrontOnLongClickListener(new View.OnLongClickListener() {
                            @Override
                            public boolean onLongClick(View v) {
                                collapsePanels();
                                Intent intent = new Intent();
                                intent.setComponent(new ComponentName("com.android.settings",
                                        "com.android.settings.Settings$DataUsageSummaryActivity"));
                                startSettingsActivity(intent);
                                return true; // Consume click
                            }
                        });
                        mModel.addRSSITile(rssiTile.getFront(), new NetworkActivityCallback() {
                            @Override
                            public void refreshView(QuickSettingsTileView view, State state) {
                                RSSIState rssiState = (RSSIState) state;
                                // Force refresh
                                rssiTile.setFrontImageDrawable(null);
                                rssiTile.setFrontImageResource(rssiState.signalIconId);

                                if (rssiState.dataTypeIconId > 0) {
                                    rssiTile.setFrontImageOverlayResource(rssiState.dataTypeIconId);
                                } else if (!mModel.isMobileDataEnabled(mContext)) {
                                    rssiTile.setFrontImageOverlayResource(R.drawable.ic_qs_signal_data_off);
                                } else {
                                    rssiTile.setFrontImageOverlayDrawable(null);
                                }

                                setActivity(view, rssiState);

                                rssiTile.setFrontText(state.label);
                                rssiTile.setFrontContentDescription(mContext.getResources().getString(
                                        R.string.accessibility_quick_settings_mobile,
                                        rssiState.signalContentDescription,
                                        rssiState.dataContentDescription,
                                        state.label));
                            }
                        });
                        // Back side (Mobile networks modes)
                        if (mModel.mUsesAospDialer) {
                            rssiTile.setBackTextResource(R.string.quick_settings_network_unknown);
                        } else {
                            rssiTile.setBackTextResource(R.string.quick_settings_network_disabled);
                        }
                        rssiTile.setBackImageResource(R.drawable.ic_qs_unexpected_network);
                        rssiTile.setBackOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (mModel.mUsesAospDialer) {
                                    mModel.toggleMobileNetworkState();
                                } else {
                                    collapsePanels();
                                    Toast.makeText(mContext,
                                                   R.string.quick_settings_network_toast_disabled,
                                                   Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                        rssiTile.setBackOnLongClickListener(new View.OnLongClickListener() {
                            @Override
                            public boolean onLongClick(View v) {
                                Intent intent = new Intent(Intent.ACTION_MAIN);
                                intent.setClassName("com.android.phone", "com.android.phone.Settings");
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startSettingsActivity(intent);
                                return true;
                            }
                        });
                        mModel.addMobileNetworkTile(rssiTile.getBack(), new QuickSettingsModel.RefreshCallback() {
                            @Override
                            public void refreshView(QuickSettingsTileView unused, State mobileNetworkState) {
                                rssiTile.setBackImageResource(mobileNetworkState.iconId);
                                rssiTile.setBackText(mobileNetworkState.label);
                            }
                        });
                        parent.addView(rssiTile);
                        if(addMissing) rssiTile.setVisibility(View.GONE);
                    }
                } else if(Tile.ROTATION.toString().equals(tile.toString())) { // Rotation Lock Tile
                    if (mContext.getResources()
                            .getBoolean(R.bool.quick_settings_show_rotation_lock)
                                    || DEBUG_GONE_TILES) {
                        final QuickSettingsBasicTile rotationLockTile
                            = new QuickSettingsBasicTile(mContext);
                        rotationLockTile.setTileId(Tile.ROTATION);
                        rotationLockTile.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                final boolean locked = mRotationLockController.isRotationLocked();
                                mRotationLockController.setRotationLocked(!locked);
                            }
                        });
                        rotationLockTile.setOnLongClickListener(new View.OnLongClickListener() {
                            @Override
                            public boolean onLongClick(View v) {
                                collapsePanels();
                                startSettingsActivity(
                                        android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                                return true; // Consume click
                            }
                        });
                        mModel.addRotationLockTile(rotationLockTile, mRotationLockController,
                                new QuickSettingsModel.RefreshCallback() {
                                    @Override
                                    public void refreshView(QuickSettingsTileView view,
                                            State state) {
                                        QuickSettingsModel.RotationLockState rotationLockState =
                                                (QuickSettingsModel.RotationLockState) state;
                                        // always enabled
                                        view.setEnabled(true);
                                        if (state.iconId != 0) {
                                            // needed to flush any cached IDs
                                            rotationLockTile.setImageDrawable(null);
                                            rotationLockTile.setImageResource(state.iconId);
                                        }
                                        if (state.label != null) {
                                            rotationLockTile.setText(state.label);
                                        }
                                    }
                                });
                        parent.addView(rotationLockTile);
                        if(addMissing) rotationLockTile.setVisibility(View.GONE);
                    }
                  } else if (Tile.TORCH.toString().equals(tile.toString())) { // torch tile
                  // Torch
                  final QuickSettingsBasicTile torchTile
                        = new QuickSettingsBasicTile(mContext);
                  torchTile.setTileId(Tile.TORCH);
                  torchTile.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            startSettingsActivity(OmniTorchConstants.INTENT_LAUNCH_APP);
                            return true;
                        }
                  });
                  mModel.addTorchTile(torchTile, new QuickSettingsModel.RefreshCallback() {
                        @Override
                        public void refreshView(QuickSettingsTileView unused, State state) {
                            torchTile.setImageResource(state.iconId);
                            torchTile.setText(state.label);
                        }
                  });
                  parent.addView(torchTile);
                  if (addMissing) torchTile.setVisibility(View.GONE);
                    } else if (Tile.SYNC.toString().equals(tile.toString())) { // sync tile
                  // sync
                  final QuickSettingsBasicTile SyncTile
                        = new QuickSettingsBasicTile(mContext);
                  SyncTile.setTileId(Tile.SYNC);
                  SyncTile.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            Intent intent = new Intent("android.settings.SYNC_SETTINGS");
                            intent.addCategory(Intent.CATEGORY_DEFAULT);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startSettingsActivity(intent);
                            return true;
                        }
                  });
                  mModel.addSyncModeTile(SyncTile, new QuickSettingsModel.RefreshCallback() {
                        @Override
                        public void refreshView(QuickSettingsTileView unused, State state) {
                            SyncTile.setImageResource(state.iconId);
                            SyncTile.setText(state.label);
                        }
                  });
                  parent.addView(SyncTile);
                  if (addMissing) SyncTile.setVisibility(View.GONE);
                } else if(Tile.BATTERY.toString().equals(tile.toString())) { // Battery tile
                    batteryTile = new QuickSettingsBasicBatteryTile(mContext);
                    batteryTile.setTileId(Tile.BATTERY);
                    updateBattery();
                    batteryTile.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            collapsePanels();
                            startSettingsActivity(Intent.ACTION_POWER_USAGE_SUMMARY);
                        }
                    });
                    mModel.addBatteryTile(batteryTile, new QuickSettingsModel.RefreshCallback() {
                        @Override
                        public void refreshView(QuickSettingsTileView unused, State state) {
                            QuickSettingsModel.BatteryState batteryState =
                                    (QuickSettingsModel.BatteryState) state;
                            String t;
                            if (batteryState.batteryLevel == 100) {
                                t = mContext.getString(
                                        R.string.quick_settings_battery_charged_label);
                            } else {
                                if (batteryState.pluggedIn) {
                                    t = (mBatteryStyle != 3 || mBatteryStyle !=5) // circle percent
                                        ? mContext.getString(R.string.quick_settings_battery_charging_label,
                                            batteryState.batteryLevel)
                                        : mContext.getString(R.string.quick_settings_battery_charging);
                                } else {     // battery bar or battery circle
                                    t = (mBatteryStyle == 0 || mBatteryStyle == 2)
                                        ? mContext.getString(R.string.status_bar_settings_battery_meter_format,
                                            batteryState.batteryLevel)
                                        : mContext.getString(R.string.quick_settings_battery_discharging);
                                }
                            }
                            batteryTile.setText(t);
                            batteryTile.setContentDescription(mContext.getString(
                                    R.string.accessibility_quick_settings_battery, t));
                        }
                    });
                    parent.addView(batteryTile);
                    if(addMissing) batteryTile.setVisibility(View.GONE);
                } else if(Tile.AIRPLANE.toString().equals(tile.toString())) { // Airplane Mode tile
                    final QuickSettingsBasicTile airplaneTile
                            = new QuickSettingsBasicTile(mContext);
                    airplaneTile.setTileId(Tile.AIRPLANE);
                    mModel.addAirplaneModeTile(airplaneTile,
                            new QuickSettingsModel.RefreshCallback() {
                        @Override
                        public void refreshView(QuickSettingsTileView unused, State state) {
                            airplaneTile.setImageResource(state.iconId);

                            String airplaneState = mContext.getString(
                                    (state.enabled) ? R.string.accessibility_desc_on
                                            : R.string.accessibility_desc_off);
                            airplaneTile.setContentDescription(
                                    mContext.getString(
                                            R.string.accessibility_quick_settings_airplane,
                                            airplaneState));
                            airplaneTile.setText(state.label);
                        }
                    });
                    parent.addView(airplaneTile);
                    if(addMissing) airplaneTile.setVisibility(View.GONE);
                } else if(Tile.BLUETOOTH.toString().equals(tile.toString())) { // Bluetooth tile
                    if (mModel.deviceSupportsBluetooth()
                            || DEBUG_GONE_TILES) {
                        final QuickSettingsDualBasicTile bluetoothTile
                            = new QuickSettingsDualBasicTile(mContext);
                        bluetoothTile.setDefaultContent();
                        bluetoothTile.setTileId(Tile.BLUETOOTH);
                        // Front side (Turn on/off bluetooth)
                        bluetoothTile.setFrontOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (mBluetoothAdapter.isEnabled()) {
                                    mBluetoothAdapter.disable();
                                } else {
                                    mBluetoothAdapter.enable();
                                }
                            }
                        });
                        bluetoothTile.setFrontOnLongClickListener(new View.OnLongClickListener() {
                            @Override
                            public boolean onLongClick(View v) {
                                collapsePanels();
                                startSettingsActivity(
                                        android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);

                                return true; // Consume click
                            }
                        });
                        // Back side (Toggle discoverability)
                        bluetoothTile.setBackOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                // instead of just returning, assume user wants to turn on bluetooth
                                if (!mBluetoothAdapter.isEnabled()) {
                                    bluetoothTile.swapTiles(true);
                                    return;
                                }
                                if (mBluetoothAdapter.getScanMode()
                                        != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                                    mBluetoothAdapter.setScanMode(
                                            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, 300);
                                    bluetoothTile.setBackImageResource(
                                            R.drawable.ic_qs_bluetooth_discoverable);
                                    bluetoothTile.setBackText(mContext.getString(
                                            R.string.quick_settings_bluetooth_discoverable_label));
                                } else {
                                    mBluetoothAdapter.setScanMode(
                                            BluetoothAdapter.SCAN_MODE_CONNECTABLE, 300);
                                    bluetoothTile.setBackImageResource(
                                            R.drawable.ic_qs_bluetooth_discoverable_off);
                                    bluetoothTile.setBackText(mContext.getString(
                                            R.string.quick_settings_bluetooth_not_discoverable_label));
                                }
                            }
                        });
                        bluetoothTile.setBackOnLongClickListener(new View.OnLongClickListener() {
                            @Override
                            public boolean onLongClick(View v) {
                                collapsePanels();
                                startSettingsActivity(
                                        android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                                return true; // Consume click
                            }
                        });
                        mModel.addBluetoothTile(bluetoothTile.getFront(),
                                new QuickSettingsModel.RefreshCallback() {
                            @Override
                            public void refreshView(QuickSettingsTileView unused, State state) {
                                BluetoothState bluetoothState = (BluetoothState) state;
                                bluetoothTile.setFrontImageResource(state.iconId);
                                bluetoothTile.setFrontContentDescription(mContext.getString(
                                        R.string.accessibility_quick_settings_bluetooth,
                                        bluetoothState.stateContentDescription));
                                bluetoothTile.setFrontText(state.label);
                            }
                        });
                        mModel.addBluetoothExtraTile(bluetoothTile.getBack(), new QuickSettingsModel.RefreshCallback() {
                            @Override
                            public void refreshView(QuickSettingsTileView unused, State state) {
                                BluetoothState bluetoothState = (BluetoothState) state;
                                bluetoothTile.setBackImageResource(state.iconId);
                                bluetoothTile.setBackContentDescription(mContext.getString(
                                            R.string.accessibility_quick_settings_bluetooth,
                                            bluetoothState.stateContentDescription));
                                bluetoothTile.setBackText(state.label);
                                if (mBluetoothAdapter.getScanMode()
                                    == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                                    bluetoothTile.setBackImageResource(R.drawable.ic_qs_bluetooth_discoverable);
                                    bluetoothTile.setBackText(
                                            mContext.getString(R.string.quick_settings_bluetooth_discoverable_label));
                                } else {
                                    bluetoothTile.setBackImageResource(R.drawable.ic_qs_bluetooth_discoverable_off);
                                    bluetoothTile.setBackText(
                                            mContext.getString(R.string.quick_settings_bluetooth_not_discoverable_label));
                                }
                            }
                        });
                        parent.addView(bluetoothTile);
                        if(addMissing) bluetoothTile.setVisibility(View.GONE);
                    }
                } else if(Tile.LOCATION.toString().equals(tile.toString())) { // Location tile
                    final QuickSettingsDualBasicTile locationTile
                            = new QuickSettingsDualBasicTile(mContext);
                    locationTile.setDefaultContent();
                    locationTile.setTileId(Tile.LOCATION);
                    // Front side (Turn on/off location services)
                    locationTile.setFrontImageResource(R.drawable.ic_qs_location_on);
                    locationTile.setFrontTextResource(R.string.quick_settings_location_label);
                    locationTile.setFrontOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            boolean newLocationEnabledState
                                    = !mLocationController.isLocationEnabled();
                            if (mLocationController.setLocationEnabled(newLocationEnabledState)
                                    && newLocationEnabledState) {
                                // If we've successfully switched from location off to on, close
                                // the notifications tray to show the network location provider
                                // consent dialog.
                                Intent closeDialog
                                        = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
                                mContext.sendBroadcast(closeDialog);
                            }
                        }
                    });
                    locationTile.setFrontOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            collapsePanels();
                            startSettingsActivity(
                                    android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            return true; // Consume click
                        }
                    });
                    mModel.addLocationTile(locationTile.getFront(),
                            new QuickSettingsModel.RefreshCallback() {
                        @Override
                        public void refreshView(QuickSettingsTileView unused, State state) {
                            locationTile.setFrontImageResource(state.iconId);
                            locationTile.setFrontText(state.label);
                        }
                    });
                    // Back side (Toggle location services accuracy)
                    locationTile.setBackOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if(!mLocationController.isLocationEnabled()) {
                                locationTile.swapTiles(true);
                                return;
                            }
                            int newLocationMode = mLocationController.locationMode();
                            if (mLocationController.isLocationEnabled() && mLocationController.setBackLocationEnabled(newLocationMode)) {
                                    if (mLocationController.isLocationAllowPanelCollapse()) {
                                        Intent closeDialog = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
                                        mContext.sendBroadcast(closeDialog);
                                    }
                            }
                        }} );
                    locationTile.setBackOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            collapsePanels();
                            startSettingsActivity(
                                    android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            return true; // Consume click
                        }
                    });
                    mModel.addLocationExtraTile(locationTile.getBack(), mLocationController,
                            new QuickSettingsModel.RefreshCallback() {
                        @Override
                        public void refreshView(QuickSettingsTileView unused, State state) {
                            locationTile.setBackImageResource(state.iconId);
                            locationTile.setBackText(state.label);
                        }
                    });
                    parent.addView(locationTile);
                    if(addMissing) locationTile.setVisibility(View.GONE);
                } else if (Tile.THEME_MODE.toString().equals(tile.toString())) { //Theme
                    final QuickSettingsBasicTile themeTile
                           = new QuickSettingsBasicTile(mContext);
                    themeTile.setTileId(Tile.THEME_MODE);
                    themeTile.setImageResource(R.drawable.ic_qs_theme_manual);
                    themeTile.setTextResource(R.string.quick_settings_theme_switch_light);
                    themeTile.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            TRDSActions.processAction(mContext, TRDSConstant.ACTION_THEME_SWITCH, false);
                        }
                    });

                    themeTile.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                        if (mThemeAutoMode == THEME_MODE_TWILIGHT) {
                            mThemeAutoMode = THEME_MODE_MANUAL;
                        } else {
                            mThemeAutoMode = mThemeAutoMode + 1;
                        }
                        Settings.Secure.putIntForUser(mContext.getContentResolver(),
                            Settings.Secure.UI_THEME_AUTO_MODE, mThemeAutoMode,
                            UserHandle.USER_CURRENT);
                        return true;
                        }
                    });

                    mModel.addThemeTile(themeTile,
                        new QuickSettingsModel.RefreshCallback() {
                       @Override
                       public void refreshView(QuickSettingsTileView unused, State state){
                           themeTile.setText(state.label);
                           themeTile.setImageResource(state.iconId);
                       }
                    });
                    parent.addView(themeTile);
                    if (addMissing) themeTile.setVisibility(View.GONE);
                } else if (Tile.CAMERA.toString().equals(tile.toString())) {
                    cameraTile.setTileId(Tile.CAMERA);
                    cameraTile.setTextResource(R.string.quick_settings_camera_label);
                    cameraTile.setImageResource(R.drawable.ic_qs_camera);
                    mIconContainer = cameraTile.findViewById(R.id.icon_container);
                    mSurfaceLayout = (FrameLayout) cameraTile.findViewById(R.id.camera_surface_holder);
                    mFlashView = cameraTile.findViewById(R.id.camera_surface_flash_overlay);

                    String imageFileNameFormat = DEFAULT_IMAGE_FILE_NAME_FORMAT;
                    try {
                        final Resources camRes = mContext.getPackageManager()
                           .getResourcesForApplication("com.android.gallery3d");
                        int imageFileNameFormatResId = camRes.getIdentifier(
                           "image_file_name_format", "string", "com.android.gallery3d");
                        imageFileNameFormat = camRes.getString(imageFileNameFormatResId);
                    } catch (PackageManager.NameNotFoundException ex) {
                    // Use default
                    } catch (Resources.NotFoundException ex) {
                    // Use default
                    }
                    mImageNameFormatter = new SimpleDateFormat(imageFileNameFormat);
                    cameraTile.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (mCamera == null) {
                                mHandler.post(mStartRunnable);
                            } else {
                                mHandler.post(mTakePictureRunnable);
                            }
                        }
                    });
                    cameraTile.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            if (mCamera != null) {
                                return false;
                            }
                            Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                            startSettingsActivity(intent);
                            return true;
                        }
                    });
                    mModel.addCameraTile(cameraTile,
                        new QuickSettingsModel.RefreshCallback() {
                       @Override
                       public void refreshView(QuickSettingsTileView unused, State state){
                           cameraTile.setText(state.label);
                           cameraTile.setImageResource(state.iconId);
                           Log.v("QUICKSETTINGS",state.label+","+state.iconId);

                       }
                    });
                    parent.addView(cameraTile);
                    mHandler.post(mReleaseCameraRunnable);
                    mIconContainer.setVisibility(View.VISIBLE);
                    Log.v("QUICKSETTINGS", "mIconContainer set to VISIBLE");
                    if (addMissing) cameraTile.setVisibility(View.GONE);
                } else if (Tile.MUSIC.toString().equals(tile.toString())) {
                    final QuickSettingsBasicMusicTile musicTile
                           = new QuickSettingsBasicMusicTile(mContext);
                    musicTile.setTileId(Tile.MUSIC);
                    musicTile.setTextResource(R.string.quick_settings_music_label);
//                   cameraTile.setImageResource(R.drawable.ic_qs_camera);
                    mRemoteController = new RemoteController(mContext, mRCClientUpdateListener);
                    AudioManager manager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                    manager.registerRemoteController(mRemoteController);
                    mRemoteController.setArtworkConfiguration(true,100,80);

                    musicTile.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            sendMediaButtonClick(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                        }
                    });
                    musicTile.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            sendMediaButtonClick(KeyEvent.KEYCODE_MEDIA_NEXT);
                            return true;
                        }
                    });
                    mModel.addMusicTile(musicTile,
                        new QuickSettingsModel.RefreshCallback() {
                       @Override
                       public void refreshView(QuickSettingsTileView unused, State state){
                           musicTile.setText(state.label);
                           musicTile.setImageResource(state.iconId);
                           Log.v("QUICKSETTINGS",state.label+","+state.iconId);

                       }
                    });
                    parent.addView(musicTile);
                    if (addMissing) musicTile.setVisibility(View.GONE);
                } else if(Tile.QUICKRECORD.toString().equals(tile.toString())) {
                    final QuickSettingsBasicTile recordTile
                           = new QuickSettingsBasicTile(mContext);
                    recordTile.setTileId(Tile.QUICKRECORD);
                    recordTile.setImageResource(R.drawable.ic_qs_quickrecord);
                    recordTile.setTextResource(R.string.quick_settings_quick_record_def);
                    recordTile.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (!mFile.exists()) {
                                QuickSettingsModel.mRecordingState.recording = QR_NO_RECORDING;
                            }
                            switch (QuickSettingsModel.mRecordingState.recording) {
                                case QR_RECORDING:
                                    Log.v("QUICKSETTINGS","RECORDING");
                                    stopRecording();
                                    break;
                                case QR_NO_RECORDING:
                                    Log.v("QUICKSETTINGS","NO RECORDING");
                                    return;
                                case QR_IDLE:
                                    Log.v("QUICKSETTINGS","IDLE");
                                case QR_JUST_RECORDED:
                                    Log.v("QUICKSETTINGS","JUST RECORDED");   
                                    startPlaying();
                                    break;
                                case QR_PLAYING:
                                    Log.v("QUICKSETTINGS","PLAYING");
                                    stopPlaying();
                                    break;
                            } 
                            mModel.updateRecordingTile();
                        }
                    });

                    recordTile.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            switch (QuickSettingsModel.mRecordingState.recording) {
                                case QR_NO_RECORDING:
                               case QR_IDLE:
                               case QR_JUST_RECORDED:
                               startRecording();
                               Log.v("QUICKSETTINGS","STARTRECORDING");
                               break;
                            }
                            mModel.updateRecordingTile();
                            return true;
                        }
                    });
                    mModel.addRecordingTile(recordTile,
                        new QuickSettingsModel.RefreshCallback() {
                       @Override
                       public void refreshView(QuickSettingsTileView unused, State state){
                           Log.v("QUICKSETTINGS",state.label+","+state.iconId);
                           recordTile.setText(state.label);
                           recordTile.setImageResource(state.iconId);
                       }
                    });
                    parent.addView(recordTile);
                    if (addMissing) recordTile.setVisibility(View.GONE);
                } else if(Tile.IMMERSIVE.toString().equals(tile.toString())) { // Immersive mode tile
                    final QuickSettingsDualBasicTile immersiveTile
                            = new QuickSettingsDualBasicTile(mContext);
                    immersiveTile.setDefaultContent();
                    immersiveTile.setTileId(Tile.IMMERSIVE);
                    // Front side (Toggles global immersive state On/Off)
                    immersiveTile.setFrontImageResource(R.drawable.ic_qs_immersive_global_off);
                    immersiveTile.setFrontTextResource(R.string.quick_settings_immersive_global_off_label);
                    immersiveTile.setFrontOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (!immersiveStyleSelected() && mModel.getImmersiveMode() == 0) {
                                selectImmersiveStyle();
                            } else {
                                mModel.switchImmersiveGlobal();
                                mModel.refreshImmersiveGlobalTile();
                            }
                        }
                    });
                    mModel.addImmersiveGlobalTile(immersiveTile.getFront(), new QuickSettingsModel.RefreshCallback() {
                        @Override
                        public void refreshView(QuickSettingsTileView unused, State state) {
                            immersiveTile.setFrontImageResource(state.iconId);
                            immersiveTile.setFrontText(state.label);
                        }
                    });
                    // Back side (Toggles active immersive modes if global is on)
                    immersiveTile.setBackImageResource(R.drawable.ic_qs_immersive_off);
                    immersiveTile.setBackTextResource(R.string.quick_settings_immersive_mode_off_label);
                    immersiveTile.setBackOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // instead of just returning, assume user wants to turn on immersive
                            if(mModel.getImmersiveMode() == 0) {
                                immersiveTile.swapTiles(true);
                                return;
                            }
                            mModel.switchImmersiveMode();
                            mModel.refreshImmersiveModeTile();
                        }
                    });
                    mModel.addImmersiveModeTile(immersiveTile.getBack(), new QuickSettingsModel.RefreshCallback() {
                        @Override
                        public void refreshView(QuickSettingsTileView unused, State state) {
                            immersiveTile.setBackImageResource(state.iconId);
                            immersiveTile.setBackText(state.label);
                        }
                    });
                    parent.addView(immersiveTile);
                    if(addMissing) immersiveTile.setVisibility(View.GONE);
               } else if (Tile.VOLUME.toString().equals(tile.toString())) { // Volume tile
                  // Volume mode
                  final QuickSettingsDualBasicTile volumeTile
                        = new QuickSettingsDualBasicTile(mContext);
                  volumeTile.setDefaultContent();
                  volumeTile.setTileId(Tile.VOLUME);
                  volumeTile.setFrontImageResource(R.drawable.ic_qs_volume);
                  volumeTile.setFrontText(mContext.getString(R.string.quick_settings_volume));
//                  volumeTile.setBackText(mContext.getString(R.string.quick_settings_volume_status));
                  volumeTile.setFrontOnClickListener(new View.OnClickListener() {
                       @Override
                       public void onClick(View v) {
                           collapsePanels();
                           AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                           am.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
                      }
                  });
                  volumeTile.setFrontOnLongClickListener(new View.OnLongClickListener() {
                      @Override
                      public boolean onLongClick(View v) {
                           startSettingsActivity(android.provider.Settings.ACTION_SOUND_SETTINGS);
                           return true;
                      }
                  });
                  mModel.addRingerModeTile(volumeTile.getBack(), new QuickSettingsModel.RefreshCallback() {
                        @Override
                        public void refreshView(QuickSettingsTileView view, State state) {
                            volumeTile.setBackImageResource(state.iconId);
                            volumeTile.setBackText(state.label);
                        }
                  });
                  volumeTile.setBackOnLongClickListener(new View.OnLongClickListener() {
                      @Override
                      public boolean onLongClick(View v) {
                           startSettingsActivity(android.provider.Settings.ACTION_SOUND_SETTINGS);
                           return true;
                      }
                  });
                  parent.addView(volumeTile);
                  if (addMissing) volumeTile.setVisibility(View.GONE);
                } else if(Tile.SLEEP.toString().equals(tile.toString())) { // Sleep tile
                    final PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
                    final QuickSettingsDualBasicTile sleepTile
                        = new QuickSettingsDualBasicTile(mContext);
                    sleepTile.setDefaultContent();
                    sleepTile.setTileId(Tile.SLEEP);
                    // Front side (Put device into sleep mode)
                    sleepTile.setFrontImageResource(R.drawable.ic_qs_sleep_action);
                    sleepTile.setFrontTextResource(R.string.quick_settings_sleep_action_label);
                    sleepTile.setFrontOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            pm.goToSleep(SystemClock.uptimeMillis());
                        }
                    });
                    sleepTile.setFrontOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            collapsePanels();
                            Intent intent = new Intent(Intent.ACTION_POWERMENU);
                            mContext.sendBroadcast(intent);
                            return true; // Consume click
                        }
                    });
                    // Back side (Toggle screen off timeout)
                    mModel.addSleepTimeTile(sleepTile.getBack(), new QuickSettingsModel.RefreshCallback() {
                        @Override
                        public void refreshView(QuickSettingsTileView view, State state) {
                            sleepTile.setBackImageResource(state.iconId);
                            sleepTile.setBackText(state.label);
                        }
                    });
                    parent.addView(sleepTile);
                    if(addMissing) sleepTile.setVisibility(View.GONE);
                
               } else if (Tile.NETADB.toString().equals(tile.toString())) { // Network ADB Tile
                 // Network ADB mode
                 final QuickSettingsBasicTile netAdbTile
                       = new QuickSettingsBasicTile(mContext);
                 netAdbTile.setTileId(Tile.NETADB);
                 netAdbTile.setOnLongClickListener(new View.OnLongClickListener() {
                       @Override
                       public boolean onLongClick(View V) {
                           Intent intent = new Intent(Intent.ACTION_MAIN);
                           intent.setClassName("com.android.settings",
                               "com.android.settings.Settings$DevelopmentSettingsActivity");
                           startSettingsActivity(intent);
                           return true;
                       }
                 });
                 mModel.addNetAdbTile(netAdbTile, new QuickSettingsModel.RefreshCallback() {
                       @Override
                       public void refreshView(QuickSettingsTileView unused, State state) {
                           netAdbTile.setImageResource(state.iconId);
                           netAdbTile.setText(state.label);
                       }
                 });
                 parent.addView(netAdbTile);
                 if (addMissing) netAdbTile.setVisibility(View.GONE);
               }  else if (Tile.NFC.toString().equals(tile.toString())) { // NFC tile
                  // NFC
//                  if(mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC)) {
                      final QuickSettingsBasicTile nfcTile 
                             = new QuickSettingsBasicTile(mContext);
                      nfcTile.setTileId(Tile.NFC);
                      nfcTile.setImageResource(R.drawable.ic_qs_nfc_off);
                      nfcTile.setTextResource(R.string.quick_settings_nfc_off);
                      nfcTile.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                try {
                                    if(NfcAdapter.getNfcAdapter(mContext).isEnabled()) {
                                        NfcAdapter.getNfcAdapter(mContext).disable();
                                    } else {
                                        NfcAdapter.getNfcAdapter(mContext).enable();
                                    }
                                } catch (Exception e) {}
                            }
                      });
                      nfcTile.setOnLongClickListener(new View.OnLongClickListener() {
                            @Override
                            public boolean onLongClick(View v) {
                                startSettingsActivity(android.provider.Settings.ACTION_WIRELESS_SETTINGS);
                                return true;
                            }
                      });
                      mModel.addNfcTile(nfcTile, new QuickSettingsModel.RefreshCallback() {
                          @Override
                          public void refreshView(QuickSettingsTileView unused, State state) {
                           nfcTile.setImageResource(state.iconId);
                           nfcTile.setText(state.label);
                           }
                      });
                      parent.addView(nfcTile);
                      if (addMissing) nfcTile.setVisibility(View.GONE);
//                    }
                  }
              }
          }
          if(!addMissing) addTiles(parent, true);
    }

    private void addTemporaryTiles(final ViewGroup parent) {
        // Alarm tile
        final QuickSettingsBasicTile alarmTile
                = new QuickSettingsBasicTile(mContext);
        alarmTile.setTemporary(true);
        alarmTile.setImageResource(R.drawable.ic_qs_alarm_on);
        alarmTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSettingsActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS));
            }
        });
        mModel.addAlarmTile(alarmTile, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView unused, State alarmState) {
                alarmTile.setText(alarmState.label);
                alarmTile.setVisibility(alarmState.enabled ? View.VISIBLE : View.GONE);
                alarmTile.setContentDescription(mContext.getString(
                        R.string.accessibility_quick_settings_alarm, alarmState.label));
            }
        });
        parent.addView(alarmTile);

        // Usb Mode
        final QuickSettingsBasicTile usbModeTile
                = new QuickSettingsBasicTile(mContext);
        usbModeTile.setTemporary(true);
        usbModeTile.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mConnectivityManager.getTetherableWifiRegexs().length != 0) {
                    Intent intent = new Intent();
                    intent.setComponent(new ComponentName(
                            "com.android.settings",
                            "com.android.settings.Settings$TetherSettingsActivity"));
                    startSettingsActivity(intent);
                }
                return true;
            }
        });

        mModel.addUsbModeTile(usbModeTile, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView unused, State usbState) {
                usbModeTile.setImageResource(usbState.iconId);
                usbModeTile.setText(usbState.label);
                usbModeTile.setVisibility(usbState.enabled ? View.VISIBLE : View.GONE);
            }
        });

        parent.addView(usbModeTile);

        // On-the-go tile
        Log.v("ONTHEGO","Setting up onthego");        
        if (DeviceUtils.deviceSupportsCamera(mContext)
            && DeviceUtils.deviceSupportsFrontCamera(mContext)) {
            Log.v("ONTHEGO", "Supports camera!");
            final QuickSettingsBasicTile onthegoTile
                       = new QuickSettingsBasicTile(mContext);
            onthegoTile.setImageResource(R.drawable.ic_qs_onthego);
            onthegoTile.setTextResource(R.string.quick_settings_onthego_back);
            onthegoTile.setTemporary(true);
            onthegoTile.setOnClickListener(new View.OnClickListener() {
                 @Override
                 public void onClick(View v) {
                     ContentResolver resolver = mContext.getContentResolver();
                     int mode = Settings.System.getInt(resolver,
                               Settings.System.ON_THE_GO_CAMERA, 0);
                     Settings.System.putInt(resolver,
                               Settings.System.ON_THE_GO_CAMERA, (mode != 0) ? 0 : 1);
                 }
            });
            mModel.addOnTheGoTile(onthegoTile, new QuickSettingsModel.RefreshCallback() {
                 @Override
                 public void refreshView(QuickSettingsTileView unused, State onthegoState) {
                     onthegoTile.setImageResource(onthegoState.iconId);
                     onthegoTile.setText(onthegoState.label);
                     onthegoTile.setVisibility(onthegoState.enabled ? View.VISIBLE : View.GONE);
                 }
            });
            parent.addView(onthegoTile);
        } else {
          Log.e("ONTHEGO","Could not find onthego camera");
        }

        // Remote Display
        QuickSettingsBasicTile remoteDisplayTile
                = new QuickSettingsBasicTile(mContext);
        remoteDisplayTile.setTemporary(true);
        remoteDisplayTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                collapsePanels();

                final Dialog[] dialog = new Dialog[1];
                dialog[0] = MediaRouteDialogPresenter.createDialog(mContext,
                        MediaRouter.ROUTE_TYPE_REMOTE_DISPLAY,
                        new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog[0].dismiss();
                        startSettingsActivity(
                                android.provider.Settings.ACTION_WIFI_DISPLAY_SETTINGS);
                    }
                });
                dialog[0].getWindow().setType(WindowManager.LayoutParams.TYPE_VOLUME_OVERLAY);
                dialog[0].show();
            }
        });
        mModel.addRemoteDisplayTile(remoteDisplayTile,
                new QuickSettingsModel.BasicRefreshCallback(remoteDisplayTile)
                        .setShowWhenEnabled(true));
        parent.addView(remoteDisplayTile);

        if (SHOW_IME_TILE || DEBUG_GONE_TILES) {
            // IME
            final QuickSettingsBasicTile imeTile
                    = new QuickSettingsBasicTile(mContext);
            imeTile.setTemporary(true);
            imeTile.setImageResource(R.drawable.ic_qs_ime);
            imeTile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        collapsePanels();
                        Intent intent = new Intent(Settings.ACTION_SHOW_INPUT_METHOD_PICKER);
                        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);
                        pendingIntent.send();
                    } catch (Exception e) {}
                }
            });
            mModel.addImeTile(imeTile,
                    new QuickSettingsModel.BasicRefreshCallback(imeTile)
                            .setShowWhenEnabled(true));
            parent.addView(imeTile);
        }

        // Bug reports
        final QuickSettingsBasicTile bugreportTile
                = new QuickSettingsBasicTile(mContext);
        bugreportTile.setTemporary(true);
        bugreportTile.setImageResource(com.android.internal.R.drawable.stat_sys_adb);
        bugreportTile.setTextResource(com.android.internal.R.string.bugreport_title);
        bugreportTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                collapsePanels();
                showBugreportDialog();
            }
        });
        mModel.addBugreportTile(bugreportTile, new QuickSettingsModel.RefreshCallback() {
            @Override
            public void refreshView(QuickSettingsTileView view, State state) {
                view.setVisibility(state.enabled ? View.VISIBLE : View.GONE);
            }
        });
        parent.addView(bugreportTile);
        /*
        QuickSettingsTileView mediaTile = (QuickSettingsTileView)
                inflater.inflate(R.layout.quick_settings_tile, parent, false);
        mediaTile.setContent(R.layout.quick_settings_tile_media, inflater);
        parent.addView(mediaTile);
        QuickSettingsTileView imeTile = (QuickSettingsTileView)
                inflater.inflate(R.layout.quick_settings_tile, parent, false);
        imeTile.setContent(R.layout.quick_settings_tile_ime, inflater);
        imeTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                parent.removeViewAt(0);
            }
        });
        parent.addView(imeTile);
        */

        // SSL CA Cert Warning.
        final QuickSettingsBasicTile sslCaCertWarningTile =
                new QuickSettingsBasicTile(mContext, null, R.layout.quick_settings_tile_monitoring);
        sslCaCertWarningTile.setTemporary(true);
        sslCaCertWarningTile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                collapsePanels();
                startSettingsActivity(Settings.ACTION_MONITORING_CERT_INFO);
            }
        });

        sslCaCertWarningTile.setImageResource(
                com.android.internal.R.drawable.indicator_input_error);
        sslCaCertWarningTile.setTextResource(R.string.ssl_ca_cert_warning);

        mModel.addSslCaCertWarningTile(sslCaCertWarningTile,
                new QuickSettingsModel.BasicRefreshCallback(sslCaCertWarningTile)
                        .setShowWhenEnabled(true));
        parent.addView(sslCaCertWarningTile);
    }

    List<String> enumToStringArray(Tile[] enumData) {
        List<String> array = new ArrayList<String>();
        for(Tile tile : enumData) {
            array.add(tile.toString());
        }
        return array;
    }

    void updateResources() {
        Resources r = mContext.getResources();

        // Update the model
        mModel.refreshBatteryTile();

        QuickSettingsContainerView container = ((QuickSettingsContainerView)mContainerView);

        container.updateSpan();
        container.updateResources();
        mContainerView.requestLayout();
    }

    private void selectImmersiveStyle() {
        Resources r = mContext.getResources();

        SettingConfirmationHelper helper = new SettingConfirmationHelper(mContext);
        helper.showConfirmationDialogForSetting(
                r.getString(R.string.enable_pie_control_title),
                r.getString(R.string.enable_pie_control_message),
                r.getDrawable(R.drawable.want_some_slice),
                Settings.System.PIE_STATE);
    }

    private void showBrightnessDialog() {
        Intent intent = new Intent(Intent.ACTION_SHOW_BRIGHTNESS_DIALOG);
        mContext.sendBroadcast(intent);
    }

    private void showBugreportDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setPositiveButton(com.android.internal.R.string.report, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    // Add a little delay before executing, to give the
                    // dialog a chance to go away before it takes a
                    // screenshot.
                    mHandler.postDelayed(new Runnable() {
                        @Override public void run() {
                            try {
                                ActivityManagerNative.getDefault()
                                        .requestBugReport();
                            } catch (RemoteException e) {
                            }
                        }
                    }, 500);
                }
            }
        });
        builder.setMessage(com.android.internal.R.string.bugreport_message);
        builder.setTitle(com.android.internal.R.string.bugreport_title);
        builder.setCancelable(true);
        final Dialog dialog = builder.create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        try {
            WindowManagerGlobal.getWindowManagerService().dismissKeyguard();
        } catch (RemoteException e) {
        }
        dialog.show();
    }

    private void applyBluetoothStatus() {
        mModel.onBluetoothStateChange(mBluetoothState);
    }

    private void applyLocationEnabledStatus() {
        mModel.onLocationSettingsChanged(mLocationController.isLocationEnabled());
    }

    void reloadUserInfo() {
        if (mUserInfoTask != null) {
            mUserInfoTask.cancel(false);
            mUserInfoTask = null;
        }
        if (mTilesSetUp) {
            queryForUserInformation();
            queryForSslCaCerts();
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                mBluetoothState.enabled = (state == BluetoothAdapter.STATE_ON);
                applyBluetoothStatus();
            } else if (BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                int status = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE,
                        BluetoothAdapter.STATE_DISCONNECTED);
                mBluetoothState.connected = (status == BluetoothAdapter.STATE_CONNECTED);
                applyBluetoothStatus();
            } else if (Intent.ACTION_USER_SWITCHED.equals(action)) {
                reloadUserInfo();
            } else if (Intent.ACTION_CONFIGURATION_CHANGED.equals(action)) {
                if (mUseDefaultAvatar) {
                    queryForUserInformation();
                }
            } else if (KeyChain.ACTION_STORAGE_CHANGED.equals(action)) {
                queryForSslCaCerts();
            }
        }
    };

    private final BroadcastReceiver mProfileReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (ContactsContract.Intents.ACTION_PROFILE_CHANGED.equals(action) ||
                    Intent.ACTION_USER_INFO_CHANGED.equals(action)) {
                try {
                    final int currentUser = ActivityManagerNative.getDefault().getCurrentUser().id;
                    final int changedUser =
                            intent.getIntExtra(Intent.EXTRA_USER_HANDLE, getSendingUserId());
                    if (changedUser == currentUser) {
                        reloadUserInfo();
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Couldn't get current user id for profile change", e);
                }
            }

        }
    };

    private abstract static class NetworkActivityCallback
            implements QuickSettingsModel.RefreshCallback {
        private final long mDefaultDuration = new ValueAnimator().getDuration();
        private final long mShortDuration = mDefaultDuration / 3;

        public void setActivity(View view, ActivityState state) {
            setVisibility(view.findViewById(R.id.activity_in), state.activityIn);
            setVisibility(view.findViewById(R.id.activity_out), state.activityOut);
        }

        private void setVisibility(View view, boolean visible) {
            final float newAlpha = visible ? 1 : 0;
            if (view.getAlpha() != newAlpha) {
                view.animate()
                    .setDuration(visible ? mShortDuration : mDefaultDuration)
                    .alpha(newAlpha)
                    .start();
            }
        }
    }

    final Runnable delayTileRevert = new Runnable () {
        public void run() {
            if (QuickSettingsModel.mRecordingState.recording == QR_JUST_RECORDED) {
                QuickSettingsModel.mRecordingState.recording = QR_IDLE;
                updateResources();
            }
        }
    };

    final Runnable autoStopRecord = new Runnable() {
        public void run() {
            if (QuickSettingsModel.mRecordingState.recording == QR_RECORDING) {
                stopRecording();
            }
        }
    };

    final OnCompletionListener stoppedPlaying = new OnCompletionListener(){
        public void onCompletion(MediaPlayer mp) {
            QuickSettingsModel.mRecordingState.recording = QR_IDLE;
            updateResources();
        }
    };

    private void startPlaying() {
        mPlayer = new MediaPlayer();
        try {
            mPlayer.setDataSource(mQuickAudio);
            mPlayer.prepare();
            mPlayer.start();
            QuickSettingsModel.mRecordingState.recording = QR_PLAYING;
            updateResources();
            mPlayer.setOnCompletionListener(stoppedPlaying);
        } catch (IOException e) {
            Log.e("QUICKSETTINGS", "QuickRecord prepare() failed on play: ", e);
        }
    }

    private void stopPlaying() {
        mPlayer.release();
        mPlayer = null;
        QuickSettingsModel.mRecordingState.recording = QR_IDLE;
        updateResources();
    }

    private void startRecording() {
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setOutputFile(mQuickAudio);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        try {
            mRecorder.prepare();
            mRecorder.start();
            QuickSettingsModel.mRecordingState.recording = QR_RECORDING;
            updateResources();
            mHandler.postDelayed(autoStopRecord, MAX_RECORD_TIME);
        } catch (IOException e) {
            Log.e(TAG, "QuickRecord prepare() failed on record: ", e);
        }
    }

    private void stopRecording() {
        mRecorder.stop();
        mRecorder.release();
        mRecorder = null;
        QuickSettingsModel.mRecordingState.recording = QR_JUST_RECORDED;
        updateResources();
        mHandler.postDelayed(delayTileRevert, 2000);
    }

    private Runnable mStartRunnable = new Runnable() {
        @Override
        public void run() {
            if (mCamera != null) {
                return;
            }

            Camera.getCameraInfo(CAMERA_ID, mCameraInfo);

            try {
                mCamera = Camera.open(CAMERA_ID);
            } catch (Exception e) {
                Toast.makeText(mContext, R.string.quick_settings_camera_error_connect,
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // Orientation listener to rotate the camera preview
            if (mCameraOrientationListener == null) {
                mCameraOrientationListener = new CameraOrientationListener(mContext);
            }
            mCameraOrientationListener.enable();

            mParams = mCamera.getParameters();

            // Use smallest preview size that is bigger than the tile view
            Camera.Size previewSize = mParams.getPreviewSize();
            for (Camera.Size size : mParams.getSupportedPreviewSizes()) {
                if ((size.width > cameraTile.getWidth() && size.height > cameraTile.getHeight()) &&
                        (size.width < previewSize.width && size.height < previewSize.height)) {
                    previewSize = size;
                }
            }
            mParams.setPreviewSize(previewSize.width, previewSize.height);

            // Use largest picture size
            Camera.Size pictureSize = mParams.getPictureSize();
            for (Camera.Size size : mParams.getSupportedPictureSizes()) {
                if (size.width > pictureSize.width && size.height > pictureSize.height) {
                    pictureSize = size;
                }
            }
            mCameraSize = pictureSize;
            mParams.setPictureSize(pictureSize.width, pictureSize.height);

            // Try focus with continuous modes first, then basic autofocus
            List<String> focusModes = mParams.getSupportedFocusModes();
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                mParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                mParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            } else if (mParams.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                mParams.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }

            mCamera.setParameters(mParams);
            updateOrientation();

            final PanelView panel = getContainingPanel();
            final View parent = (View) mContainerView.getParent();

            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (panel.isFullyExpanded() && parent.getScaleX() == 1) {
                        mHandler.postDelayed(this, 100);
                    } else {
                        mHandler.post(mReleaseCameraRunnable);
                    }
                }
            }, 100);

            mIconContainer.setVisibility(View.GONE);
            Log.v("QUICKSETTINGS", "mIconContainer set to GONE");
            mSurfaceView = new CameraPreview(mContext, mCamera);
            mSurfaceView.setVisibility(View.VISIBLE);
            mSurfaceLayout.addView(mSurfaceView, 0);
        }
    };

    private Runnable mTakePictureRunnable = new Runnable() {
        @Override
        public void run() {
            if (mCamera == null) {
                return;
            }

            // Repeat until the preview has started and we can
            // take picture
            if (!mCameraStarted) {
                mHandler.postDelayed(this, 200);
                return;
            }

            // To avoid crashes don't post new picture requests
            // if previous request has not returned
            if (mCameraBusy) {
                return;
            }
            mCameraBusy = true;

            // Display flash animation above the preview
            mFlashView.setVisibility(View.VISIBLE);
            mFlashView.animate().alpha(0f).withEndAction(new Runnable() {
                @Override
                public void run() {
                    mFlashView.setVisibility(View.GONE);
                    mFlashView.setAlpha(1f);
                }
            });

            // Update the JPEG rotation\
            if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mJpegRotation = (mCameraInfo.orientation - mOrientation + 360) % 360;
            } else {
                mJpegRotation = (mCameraInfo.orientation + mOrientation) % 360;
            }

            mParams.setRotation(mJpegRotation);
            mCamera.setParameters(mParams);

            // Request a picture
            try {
                mCamera.takePicture(null, null, new Camera.PictureCallback() {
                    @Override
                    public void onPictureTaken(byte[] data, Camera camera) {
                        mCameraBusy = false;

                        long time = System.currentTimeMillis();

                        int orientation = (mOrientation + mDisplayRotation) % 360;

                        mStorage.addImage(mContext.getContentResolver(),
                                mImageNameFormatter.format(new Date(time)),
                                time, orientation, data, mCameraSize.width,
                                mCameraSize.height);

                        mCamera.startPreview();
                    }
                });
            } catch (RuntimeException e) {
                // This can happen if user is pressing the
                // tile too fast, nothing we can do
            }
        }
    };

    private Runnable mReleaseCameraRunnable = new Runnable() {
        @Override
        public void run() {
            if (mCamera == null) {
                mIconContainer.setVisibility(View.VISIBLE);
                Log.v("QUICKSETTINGS", "mIconContainer set to VISIBLE");
                return;
            }

            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            mCameraStarted = false;
            mCameraOrientationListener.disable();

            mIconContainer.setVisibility(View.VISIBLE);
            Log.v("QUICKSETTINGS", "mIconContainer set to VISIBLE");
 
            mSurfaceView.setVisibility(View.GONE);
            mSurfaceLayout.removeView(mSurfaceView);
            mSurfaceView = null;
        }
    };

    private Runnable mAutoFocusRunnable = new Runnable() {
        @Override
        public void run() {
            if (mCameraStarted) {
                mCamera.autoFocus(null);
            }
        }
    };

    private PanelView getContainingPanel() {
        ViewParent parent = mContainerView;
        while (parent != null) {
            if (parent instanceof PanelView) {
                return (PanelView) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }

    private void updateOrientation() {
        final WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        int rotation = wm.getDefaultDisplay().getRotation();

        switch (rotation) {
            case Surface.ROTATION_0:
            default:
                mDisplayRotation = 0;
                break;
            case Surface.ROTATION_90:
                mDisplayRotation = 90;
                break;
            case Surface.ROTATION_180:
                mDisplayRotation = 180;
                break;
            case Surface.ROTATION_270:
                mDisplayRotation = 270;
                break;
        }

        int cameraOrientation;

        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            cameraOrientation = (mCameraInfo.orientation + mDisplayRotation) % 360;
            cameraOrientation = (360 - cameraOrientation) % 360;  // compensate the mirror
        } else {
            cameraOrientation = (mCameraInfo.orientation - mDisplayRotation + 360) % 360;
        }

        mCamera.setDisplayOrientation(cameraOrientation);
    }

    private class CameraOrientationListener extends OrientationEventListener {
        public CameraOrientationListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            if (mCamera == null || orientation == ORIENTATION_UNKNOWN) {
                return;
            }

            mOrientation = (orientation + 45) / 90 * 90;
            updateOrientation();
        }
    }

    private class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
        private SurfaceHolder mHolder;
        private Camera mCamera;

        public CameraPreview(Context context, Camera camera) {
            super(context);
            mCamera = camera;

            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed.
            mHolder = getHolder();
            mHolder.addCallback(this);
        }

        public void surfaceCreated(SurfaceHolder holder) {
            // The Surface has been created, now tell the camera where
            // to draw the preview.
            try {
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
                mCameraStarted = true;
                mCameraBusy = false;
                mHandler.postDelayed(mAutoFocusRunnable, 200);
            } catch (IOException e) {
                // Try release camera
                mCamera.release();
            }
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        }
    }

    private class Storage {
        private static final String TAG = "CameraStorage";
        private String mRoot = Environment.getExternalStorageDirectory().toString();
        private Storage() {}

        public String writeFile(String title, byte[] data) {
            String path = generateFilepath(title);
            FileOutputStream out = null;

            try {
                out = new FileOutputStream(path);
                out.write(data);
            } catch (Exception e) {
                Log.e(TAG, "Failed to write data", e);
            } finally {
                try {
                    out.close();
                } catch (Exception e) {
                    // Do nothing here
                }
            }
            return path;
        }

        // Save the image and add it to media store.
        public Uri addImage(ContentResolver resolver, String title, long date,
                int orientation, byte[] jpeg, int width, int height) {
            // Save the image.
            String path = writeFile(title, jpeg);
            return addImage(resolver, title, date, orientation, jpeg.length,
                    path, width, height);
        }

        // Add the image to media store.
        public Uri addImage(ContentResolver resolver, String title, long date,
            int orientation, int jpegLength, String path, int width, int height) {

            try {
                ExifInterface exif = new ExifInterface(path);
                switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1)) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        orientation = 90;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        orientation = 180;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        orientation = 270;
                        break;
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to read exif", e);
            }

            if ((mJpegRotation + orientation) % 180 != 0) {
                int temp = width;
                width = height;
                height = width;
            }

            // Insert into MediaStore.
            ContentValues values = new ContentValues(9);
            values.put(ImageColumns.TITLE, title);
            values.put(ImageColumns.DISPLAY_NAME, title + ".jpg");
            values.put(ImageColumns.DATE_TAKEN, date);
            values.put(ImageColumns.MIME_TYPE, "image/jpeg");

            // Clockwise rotation in degrees. 0, 90, 180, or 270.
            values.put(ImageColumns.ORIENTATION, orientation);
            values.put(ImageColumns.DATA, path);
            values.put(ImageColumns.SIZE, jpegLength);
            values.put(MediaColumns.WIDTH, width);
            values.put(MediaColumns.HEIGHT, height);

            Uri uri = null;

            try {
                uri = resolver.insert(Images.Media.EXTERNAL_CONTENT_URI, values);
            } catch (Throwable th)  {
                // This can happen when the external volume is already mounted, but
                // MediaScanner has not notify MediaProvider to add that volume.
                // The picture is still safe and MediaScanner will find it and
                // insert it into MediaProvider. The only problem is that the user
                // cannot click the thumbnail to review the picture.
                Log.e(TAG, "Failed to write MediaStore" + th);
            }
            return uri;
        }

        private String generateDCIM() {
            return new File(mRoot, Environment.DIRECTORY_DCIM).toString();
        }

        public String generateDirectory() {
            return generateDCIM() + "/Camera";
        }

        private String generateFilepath(String title) {
            return generateDirectory() + '/' + title + ".jpg";
        }

        public String generateBucketId() {
            return String.valueOf(generateDirectory().toLowerCase().hashCode());
        }

        public int generateBucketIdInt() {
            return generateDirectory().toLowerCase().hashCode();
        }
    }

    private void playbackStateUpdate(int state) {
        boolean active;
        switch (state) {
            case RemoteControlClient.PLAYSTATE_PLAYING:
                active = true;
                break;
            case RemoteControlClient.PLAYSTATE_ERROR:
            case RemoteControlClient.PLAYSTATE_PAUSED:
            default:
                active = false;
                break;
        }
        if (active != mActive) {
            mActive = active;
            mModel.updateMusicTile(mMetadata.trackTitle, mMetadata.bitmap, mActive);
        }
    }

    private void sendMediaButtonClick(int keyCode) {
        if (!mClientIdLost) {
            mRemoteController.sendMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            mRemoteController.sendMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        } else {
            long eventTime = SystemClock.uptimeMillis();
            KeyEvent key = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0);
            dispatchMediaKeyWithWakeLockToAudioService(key);
            dispatchMediaKeyWithWakeLockToAudioService(
                KeyEvent.changeAction(key, KeyEvent.ACTION_UP));
        }
    }

    private void dispatchMediaKeyWithWakeLockToAudioService(KeyEvent event) {
        mAudioService = getAudioService();
        if (mAudioService != null) {
            try {
                mAudioService.dispatchMediaKeyEventUnderWakelock(event);
            } catch (RemoteException e) {
                Log.e(TAG, "dispatchMediaKeyEvent threw exception " + e);
            }
        }
    }

    private IAudioService getAudioService() {
        if (mAudioService == null) {
            mAudioService = IAudioService.Stub.asInterface(
                    ServiceManager.checkService(Context.AUDIO_SERVICE));
            if (mAudioService == null) {
                Log.w(TAG, "Unable to find IAudioService interface.");
            }
        }
        return mAudioService;
    }

    private RemoteController.OnClientUpdateListener mRCClientUpdateListener =
            new RemoteController.OnClientUpdateListener() {

        private String mCurrentTrack = null;
        private Bitmap mCurrentBitmap = null;

        @Override
        public void onClientChange(boolean clearing) {
            if (clearing) {
                mMetadata.clear();
                mCurrentTrack = null;
                mCurrentBitmap = null;
                mActive = false;
                mClientIdLost = true;
                mModel.updateMusicTile(null, null, mActive);
            }
        }

        @Override
        public void onClientPlaybackStateUpdate(int state, long stateChangeTimeMs,
                long currentPosMs, float speed) {
            mClientIdLost = false;
            playbackStateUpdate(state);
        }

        @Override
        public void onClientPlaybackStateUpdate(int state) {
            mClientIdLost = false;
            playbackStateUpdate(state);
        }

        @Override
        public void onClientMetadataUpdate(RemoteController.MetadataEditor data) {
            mMetadata.trackTitle = data.getString(MediaMetadataRetriever.METADATA_KEY_TITLE,
                    mMetadata.trackTitle);
            mMetadata.bitmap = data.getBitmap(MediaMetadataEditor.BITMAP_KEY_ARTWORK,
                    mMetadata.bitmap);
            mClientIdLost = false;
            if ((mMetadata.trackTitle != null
                    && !mMetadata.trackTitle.equals(mCurrentTrack))
                || (mMetadata.bitmap != null && !mMetadata.bitmap.sameAs(mCurrentBitmap))) {
                mCurrentTrack = mMetadata.trackTitle;
                mCurrentBitmap = mMetadata.bitmap;
                mModel.updateMusicTile(mCurrentTrack, mCurrentBitmap, mActive);
            }
        }

        @Override
        public void onClientTransportControlUpdate(int transportControlFlags) {
        }
    };

    class Metadata {
        public String trackTitle;
        public Bitmap bitmap;

        public void clear() {
            trackTitle = null;
            bitmap = null;
        }
    }
}
