/*
 * Copyright (c) Juspay Technologies.
 *
 * This source code is licensed under the AGPL 3.0 license found in the
 * LICENSE file in the root directory of this source tree.
 */

package in.juspay.hypersdkreact;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactRootView;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import in.juspay.hypercheckoutlite.HyperCheckoutLite;
import in.juspay.hypersdk.core.MerchantViewType;
import in.juspay.hypersdk.core.SdkTracker;
import in.juspay.hypersdk.data.JuspayResponseHandler;
import in.juspay.hypersdk.ui.HyperPaymentsCallbackAdapter;
import in.juspay.services.HyperServices;


/**
 * Module that exposes Hyper SDK to React Native's JavaScript code. Merchants only need to deal with
 * the one static method {@link #onRequestPermissionsResult(int, String[], int[])} by calling it
 * when the React Activity gets a permissions result.
 */
@ReactModule(name = HyperSdkReactModule.NAME)
public class HyperSdkReactModule extends ReactContextBaseJavaModule implements ActivityEventListener {
    static final String NAME = "HyperSdkReact";
    private static final String HYPER_EVENT = "HyperEvent";

    @Nullable
    private ReactInstanceManager reactInstanceManager;

    /**
     * All the React methods in here should be synchronized on this specific object because there
     * was no guarantee that all React methods will be called on the same thread, and can cause
     * concurrency issues.
     */
    private static final Object lock = new Object();

    private static final RequestPermissionsResultDelegate requestPermissionsResultDelegate = new RequestPermissionsResultDelegate();
    private static final ActivityResultDelegate activityResultDelegate = new ActivityResultDelegate();

    HashMap<String, HyperServices> servicesHashMap = new HashMap<>();

    private static WeakReference<HashMap<String, HyperServices>> servicesHashMapReference = new WeakReference<>(null);;

    private final ReactApplicationContext context;

    private boolean wasProcessWithActivity = false;

    private Set<String> registeredComponents = new HashSet<>();

    @NonNull
    private WeakReference<Activity> processActivityRef = new WeakReference<>(null);

    HyperSdkReactModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.context = reactContext;
        reactContext.addActivityEventListener(this);
    }

    @Override
    @NonNull
    public String getName() {
        return NAME;
    }

    @ReactMethod
    public void addListener(String eventName) {
        // Set up any upstream listeners or background tasks as necessary
    }

    @ReactMethod
    public void removeListeners(Integer count) {
        // Remove upstream listeners, stop unnecessary background tasks
    }


    /**
     * Notifies HyperSDK that a response for permissions is here. Merchants are required to call
     * this method from their activity as by default {@link com.facebook.react.ReactActivity} will
     * not forward any results to the fragments running inside it.
     *
     * @param requestCode  The requestCode that was received in your activity's
     *                     {@code onRequestPermissionsResult} method.
     * @param permissions  The set of permissions received in your activity's
     *                     {@code onRequestPermissionsResult} method.
     * @param grantResults The results of each permission received in your activity's
     *                     {@code onRequestPermissionsResult} method.
     */
    public static void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        synchronized (lock) {
            requestPermissionsResultDelegate.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * Used to forward the intent result to HyperSDK,
     * for the cases when it is not forwarded by React Native implicitly.
     *
     * @param requestCode The requestCode that was received in your activity's
     *                    {@code onActivityResult} method.
     * @param resultCode  The requestCode that was received in your activity's
     *                    {@code onActivityResult} method.
     * @param data        The intent data that was received in your activity's
     *                    {@code onActivityResult} method.
     */
    @Keep
    public static void onActivityResult(int requestCode, int resultCode, Intent data) {
        synchronized (lock) {
            activityResultDelegate.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * Used to get the list of requestCodes for which intent was started by HyperSDK.
     *
     * @return {@link Set set} of {@link Integer} requestCodes.
     */
    @Keep
    public static Set<Integer> getIntentRequestCodes() {
        return ReactLaunchDelegate.getIntentRequestCodes();
    }

    /**
     * Used to get the list of requestCodes for which permission was requested by HyperSDK.
     *
     * @return {@link Set set} of {@link Integer} requestCodes.
     */
    public static Set<Integer> getPermissionRequestCodes() {
        return ReactRequestDelegate.getPermissionRequestCodes();
    }

    @Nullable
    @Override
    public Map<String, Object> getConstants() {
        return new HashMap<String, Object>() {{
            put(HYPER_EVENT, HYPER_EVENT);
            put(MerchantViewConstants.JUSPAY_HEADER, MerchantViewConstants.JUSPAY_HEADER);
            put(MerchantViewConstants.JUSPAY_HEADER_ATTACHED, MerchantViewConstants.JUSPAY_HEADER_ATTACHED);
            put(MerchantViewConstants.JUSPAY_FOOTER, MerchantViewConstants.JUSPAY_FOOTER);
            put(MerchantViewConstants.JUSPAY_FOOTER_ATTACHED, MerchantViewConstants.JUSPAY_FOOTER_ATTACHED);
        }};
    }

    @Nullable
    public static HyperServices getHyperServices(String key) {
        if (key == null) key = "default";
        HashMap<String, HyperServices> map = servicesHashMapReference.get();
        return map != null ? map.get(key) : null;
    }

    @ReactMethod
    public void preFetch(String data) {
        try {
            JSONObject payload = new JSONObject(data);
            HyperServices.preFetch(getReactApplicationContext(), payload);
        } catch (JSONException e) {
            SdkTracker.trackAndLogBootException(
                    NAME,
                    LogConstants.CATEGORY_LIFECYCLE,
                    LogConstants.SUBCATEGORY_HYPER_SDK,
                    LogConstants.SDK_TRACKER_LABEL,
                    "Exception in prefetch",
                    e
            );
        }
    }

    @Nullable
    private HyperServices getHyperServiceForKey(String key) {
        if (key == null) {
            key = "default";
        }
        return servicesHashMap.get(key);
    }

    @ReactMethod
    public void createHyperServices(@Nullable String key) {
        if (key == null) {
            key = "default";
        }
        synchronized (lock) {
            FragmentActivity activity = (FragmentActivity) getCurrentActivity();

            if (activity == null) {
                SdkTracker.trackBootLifecycle(
                        LogConstants.SUBCATEGORY_HYPER_SDK,
                        LogConstants.LEVEL_ERROR,
                        LogConstants.SDK_TRACKER_LABEL,
                        "createHyperServices",
                        "activity is null");
                return;
            }
            Application app = activity.getApplication();
            if (app instanceof ReactApplication) {
                reactInstanceManager = ((ReactApplication) app).getReactNativeHost().getReactInstanceManager();
            }
            if (getHyperServiceForKey(key) != null) {
                SdkTracker.trackBootLifecycle(
                        LogConstants.SUBCATEGORY_HYPER_SDK,
                        LogConstants.LEVEL_WARN,
                        LogConstants.SDK_TRACKER_LABEL,
                        "createHyperServices",
                        "hyperServices instance already exists");
                return;
            }
            HyperServices hyperServices;
            if (key.equals("default")) hyperServices = new MobilityServices(activity);
            else hyperServices = new HyperServices(activity);
            servicesHashMap.put(key,hyperServices);
            servicesHashMapReference = new WeakReference<>(servicesHashMap);

            requestPermissionsResultDelegate.set(servicesHashMap);
            activityResultDelegate.set(servicesHashMap);
        }
    }

    @ReactMethod(isBlockingSynchronousMethod = true)
    public boolean onBackPressed(String key) {
        synchronized (lock) {
            if (key == null) key = "default";
            HyperServices hs = servicesHashMap.get(key);
            return hs != null && hs.onBackPressed();
        }
    }

    @ReactMethod
    public void initiate(String data, @Nullable String key) {
        synchronized (lock) {
            try {
                if (key == null) key = "default";
                JSONObject payload = new JSONObject(data);
                FragmentActivity activity = (FragmentActivity) getCurrentActivity();
                HyperServices hyperServices = getHyperServices(key);

                if (activity == null) {
                    SdkTracker.trackBootLifecycle(
                            LogConstants.SUBCATEGORY_HYPER_SDK,
                            LogConstants.LEVEL_ERROR,
                            LogConstants.SDK_TRACKER_LABEL,
                            "initiate",
                            "activity is null");
                    return;
                }

                if (hyperServices == null) {
                    SdkTracker.trackBootLifecycle(
                            LogConstants.SUBCATEGORY_HYPER_SDK,
                            LogConstants.LEVEL_ERROR,
                            LogConstants.SDK_TRACKER_LABEL,
                            "initiate",
                            "hyperServices is null");
                    return;
                }

                String finalKey = key;
                hyperServices.initiate(activity, payload, new HyperPaymentsCallbackAdapter() {
                    @Override
                    public void onEvent(JSONObject data, JuspayResponseHandler handler) {
                        // Send out the event to the merchant on JS side
                        if (data.optString("event").equals("process_result") && wasProcessWithActivity) {
                            Activity processActivity = processActivityRef.get();
                            if (processActivity != null) {
                                processActivity.finish();
                                processActivity.overridePendingTransition(0, android.R.anim.fade_out);
                            }
                            ProcessActivity.setActivityCallback(null);
                            wasProcessWithActivity = false;
                            processActivityRef = new WeakReference<>(null);
                        }
                        sendEventToJS(finalKey,data);
                    }

                    @Nullable
                    @Override
                    public View getMerchantView(ViewGroup viewGroup, MerchantViewType merchantViewType) {
                        Activity activity = (Activity) getCurrentActivity();
                        if (reactInstanceManager == null || activity == null) {
                            return super.getMerchantView(viewGroup, merchantViewType);
                        } else {
                            ReactRootView reactRootView = new ReactRootView(activity);
                            switch (merchantViewType) {
                                case HEADER:
                                    if (isViewRegistered(MerchantViewConstants.JUSPAY_HEADER))
                                        reactRootView.startReactApplication(reactInstanceManager, MerchantViewConstants.JUSPAY_HEADER);
                                    break;
                                case FOOTER:
                                    if (isViewRegistered(MerchantViewConstants.JUSPAY_FOOTER))
                                        reactRootView.startReactApplication(reactInstanceManager, MerchantViewConstants.JUSPAY_FOOTER);
                                    break;
                                case FOOTER_ATTACHED:
                                    if (isViewRegistered(MerchantViewConstants.JUSPAY_FOOTER_ATTACHED))
                                        reactRootView.startReactApplication(reactInstanceManager, MerchantViewConstants.JUSPAY_FOOTER_ATTACHED);
                                    break;
                                case HEADER_ATTACHED:
                                    if (isViewRegistered(MerchantViewConstants.JUSPAY_HEADER_ATTACHED))
                                        reactRootView.startReactApplication(reactInstanceManager, MerchantViewConstants.JUSPAY_HEADER_ATTACHED);
                                    break;
                            }
                            return reactRootView;
                        }
                    }
                });
            } catch (Exception e) {
                SdkTracker.trackAndLogBootException(
                        NAME,
                        LogConstants.CATEGORY_LIFECYCLE,
                        LogConstants.SUBCATEGORY_HYPER_SDK,
                        LogConstants.SDK_TRACKER_LABEL,
                        "Exception in initiate",
                        e
                );
            }
        }
    }

    private boolean isViewRegistered(String tag) {
        return registeredComponents.contains(tag);
    }

    private void sendEventToJS(String key,JSONObject data) {
        DeviceEventManagerModule.RCTDeviceEventEmitter jsModule = getJSModule();
        if (jsModule == null) {
            Handler handler = new Handler();
            handler.postDelayed(() -> sendEventToJS(key,data), 200);
            return;
        }

        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("key", key);
            jsonObject.put("data", data);
            jsModule.emit(HYPER_EVENT, jsonObject.toString());
        } catch (Exception e) {

        }
    }

    private DeviceEventManagerModule.RCTDeviceEventEmitter getJSModule() {
        return getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class);
    }

    @ReactMethod
    public void process(String data, String key) {
        synchronized (lock) {
            if (key == null) key = "default";
            try {
                JSONObject payload = new JSONObject(data);
                FragmentActivity activity = (FragmentActivity) getCurrentActivity();

                if (activity == null) {
                    SdkTracker.trackBootLifecycle(
                            LogConstants.SUBCATEGORY_HYPER_SDK,
                            LogConstants.LEVEL_ERROR,
                            LogConstants.SDK_TRACKER_LABEL,
                            "initiate",
                            "activity is null");
                    return;
                }

                HyperServices hyperServices = getHyperServiceForKey(key);

                if (hyperServices == null) {
                    SdkTracker.trackBootLifecycle(
                            LogConstants.SUBCATEGORY_HYPER_SDK,
                            LogConstants.LEVEL_ERROR,
                            LogConstants.SDK_TRACKER_LABEL,
                            "initiate",
                            "hyperServices is null");
                    return;
                }
                hyperServices.setActivityLaunchDelegate(new ReactLaunchDelegate(context));
                hyperServices.setRequestPermissionDelegate(new ReactRequestDelegate(activity));

                hyperServices.process(activity, payload);
            } catch (JSONException e) {
                SdkTracker.trackAndLogBootException(
                        NAME,
                        LogConstants.CATEGORY_LIFECYCLE,
                        LogConstants.SUBCATEGORY_HYPER_SDK,
                        LogConstants.SDK_TRACKER_LABEL,
                        "Exception in process",
                        e
                );
            }
        }
    }


    @ReactMethod
    public void processWithActivity(String data, String key) {
        synchronized (lock) {
            try {
                if (key == null) key = "default";
                JSONObject payload = new JSONObject(data);
                FragmentActivity activity = (FragmentActivity) getCurrentActivity();

                if (activity == null) {
                    SdkTracker.trackBootLifecycle(
                            LogConstants.SUBCATEGORY_HYPER_SDK,
                            LogConstants.LEVEL_ERROR,
                            LogConstants.SDK_TRACKER_LABEL,
                            "initiate",
                            "activity is null");
                    return;
                }

                HyperServices hyperServices = getHyperServices(key);

                Intent i = new Intent(activity, ProcessActivity.class);
                String finalKey = key;
                ProcessActivity.setActivityCallback(new ActivityCallback() {
                    @Override
                    public void onCreated(@NonNull FragmentActivity fragmentActivity) {
                        if (hyperServices == null) {
                            SdkTracker.trackBootLifecycle(
                                    LogConstants.SUBCATEGORY_HYPER_SDK,
                                    LogConstants.LEVEL_ERROR,
                                    LogConstants.SDK_TRACKER_LABEL,
                                    "initiate",
                                    "hyperServices is null");
                            return;
                        }

                        wasProcessWithActivity = true;
                        processActivityRef = new WeakReference<>(fragmentActivity);
                        hyperServices.process(fragmentActivity, payload);
                    }

                    @Override
                    public boolean onBackPressed() {
                        return HyperSdkReactModule.this.onBackPressed(finalKey);
                    }

                    @Override
                    public void resetActivity(@NonNull FragmentActivity activity) {
                        if (hyperServices != null) {
                            hyperServices.resetActivity(activity);
                        }
                    }
                });
                activity.startActivity(i);
            } catch (JSONException e) {
                SdkTracker.trackAndLogBootException(
                        NAME,
                        LogConstants.CATEGORY_LIFECYCLE,
                        LogConstants.SUBCATEGORY_HYPER_SDK,
                        LogConstants.SDK_TRACKER_LABEL,
                        "Exception in processWithActivity",
                        e
                );
            }
        }

    }

    @ReactMethod
    public void openPaymentPage(String data, String key) {
        synchronized (lock) {
            if (key == null) key ="default";
            try {
                JSONObject sdkPayload = new JSONObject(data);
                FragmentActivity activity = (FragmentActivity) getCurrentActivity();

                if (activity == null) {
                    SdkTracker.trackBootLifecycle(
                            LogConstants.SUBCATEGORY_HYPER_SDK,
                            LogConstants.LEVEL_ERROR,
                            LogConstants.SDK_TRACKER_LABEL,
                            "initiate",
                            "activity is null");
                    return;
                }

                Intent i = new Intent(activity, ProcessActivity.class);
                String finalKey = key;
                ProcessActivity.setActivityCallback(new ActivityCallback() {
                    @Override
                    public void onCreated(@NonNull FragmentActivity processActivity) {
                        HyperCheckoutLite.openPaymentPage(processActivity, sdkPayload, new HyperPaymentsCallbackAdapter() {
                            @Override
                            public void onEvent(JSONObject data, JuspayResponseHandler handler) {
                                if (data.optString("event").equals("process_result")) {
                                    processActivity.finish();
                                    processActivity.overridePendingTransition(0, android.R.anim.fade_out);
                                    ProcessActivity.setActivityCallback(null);
                                }
                                sendEventToJS(finalKey,data);
                            }
                        });
                    }

                    @Override
                    public boolean onBackPressed() {
                        return HyperCheckoutLite.onBackPressed();
                    }

                    @Override
                    public void resetActivity(@NonNull FragmentActivity activity) {
                        // Ignored
                    }
                });
                activity.startActivity(i);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    @ReactMethod
    public void terminate(String key) {
        synchronized (lock) {
            if (key == null) key = "default";
            HyperServices hyperServices = getHyperServiceForKey(key);
            if (hyperServices != null) {
                hyperServices.terminate();
            }
            servicesHashMap.remove(key);
        }
    }

    @ReactMethod
    public void terminateAll() {
        synchronized (lock) {
            Set<String> keys = servicesHashMap.keySet();
            for (Object object : keys.toArray()) {
                try {
                    String key = (String) object;
                    terminate(key);
                }catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }


    @ReactMethod
    public void notifyAboutRegisterComponent(String tag) {
        registeredComponents.add(tag);
    }

    @ReactMethod(isBlockingSynchronousMethod = true)
    public boolean isNull(String key) {
        if (key == null) key = "default";
        HyperServices hyperServices = getHyperServiceForKey(key);
        return hyperServices == null;
    }

    @ReactMethod
    public void isInitialised(String key, Promise promise) {
        boolean isInitialized = false;
        if (key == null) key = "default";
        HyperServices hyperServices = getHyperServiceForKey(key);

        synchronized (lock) {
            if (hyperServices != null) {
                try {
                    isInitialized = hyperServices.isInitialised();
                } catch (Exception e) {
                    SdkTracker.trackAndLogBootException(
                            NAME,
                            LogConstants.CATEGORY_LIFECYCLE,
                            LogConstants.SUBCATEGORY_HYPER_SDK,
                            LogConstants.SDK_TRACKER_LABEL,
                            "Exception in isInitialised",
                            e
                    );
                }
            }
        }

        promise.resolve(isInitialized);
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        synchronized (lock) {

            if (servicesHashMap == null || servicesHashMap.isEmpty()) {
                SdkTracker.trackBootLifecycle(
                        LogConstants.SUBCATEGORY_HYPER_SDK,
                        LogConstants.LEVEL_ERROR,
                        LogConstants.SDK_TRACKER_LABEL,
                        "onActivityResult",
                        "hyperServices is null");
                return;
            }

            servicesHashMap.forEach((key,val) ->val.onActivityResult(requestCode, resultCode, data));
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
    }

    /**
     * A holder class that allows us to maintain HyperServices instance statically without causing a
     * memory leak. This was required because HyperServices class maintains a reference to the
     * activity internally.
     */
    private static class RequestPermissionsResultDelegate {
        @NonNull
        private WeakReference<HashMap<String,HyperServices>> hyperServicesHolder = new WeakReference<>(null);

        synchronized void set(@NonNull HashMap<String, HyperServices> hyperServices) {
            this.hyperServicesHolder = new WeakReference<>(hyperServices);
        }

        void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
            HashMap<String,HyperServices> hyperServices = hyperServicesHolder.get();

            if (hyperServices == null) {
                SdkTracker.trackBootLifecycle(
                        LogConstants.SUBCATEGORY_HYPER_SDK,
                        LogConstants.LEVEL_ERROR,
                        LogConstants.SDK_TRACKER_LABEL,
                        "onRequestPermissionsResult",
                        "hyperServices is null");
                return;
            }

            SdkTracker.trackBootLifecycle(
                    LogConstants.SUBCATEGORY_HYPER_SDK,
                    LogConstants.LEVEL_INFO,
                    LogConstants.SDK_TRACKER_LABEL,
                    "onRequestPermissionsResult",
                    "onRequestPermissionsResult() called with: requestCode = [" + requestCode + "], permissions = [" + Arrays.toString(permissions) + "], grantResults = [" + Arrays.toString(grantResults) + "]");

            hyperServices.forEach((ele, val) -> val.onRequestPermissionsResult(requestCode, permissions, grantResults));
        }
    }

    private static class ActivityResultDelegate {
        @NonNull
        private WeakReference<HashMap<String,HyperServices>> hyperServicesHolder = new WeakReference<>(null);

        synchronized void set(@NonNull HashMap<String, HyperServices> hyperServices) {
            this.hyperServicesHolder = new WeakReference<>(hyperServices);
        }


        void onActivityResult(int requestCode, int resultCode, Intent data) {
            HashMap<String,HyperServices> hyperServices = hyperServicesHolder.get();

            if (hyperServices == null) {
                SdkTracker.trackBootLifecycle(
                        LogConstants.SUBCATEGORY_HYPER_SDK,
                        LogConstants.LEVEL_ERROR,
                        LogConstants.SDK_TRACKER_LABEL,
                        "onActivityResult",
                        "hyperServices is null");
                return;
            }

            SdkTracker.trackBootLifecycle(
                    LogConstants.SUBCATEGORY_HYPER_SDK,
                    LogConstants.LEVEL_INFO,
                    LogConstants.SDK_TRACKER_LABEL,
                    "onActivityResult",
                    "onActivityResult() called with: requestCode = [" + requestCode + "], resultCode = [" + resultCode + "], data = [" + data + "]"
            );
            hyperServices.forEach((ele, val) -> val.onActivityResult(requestCode, resultCode, data));
        }
    }
}
