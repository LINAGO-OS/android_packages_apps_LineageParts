/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.spoofing;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import org.json.JSONObject;
import org.lineageos.lineageparts.R;
import org.lineageos.lineageparts.SettingsPreferenceFragment;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpoofingSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

    private static final String TAG = "SpoofingSettings";

    // 16.0 Settings Keys
    private static final String KEY_PLAY_INTEGRITY_SPOOF = "play_integrity_spoof";
    private static final String KEY_PHOTOS_SPOOF = "play_integrity_photos_spoof";
    private static final String KEY_FETCH_PIF_BETA = "fetch_pif_beta";
    private static final String KEY_IMPORT_PIF = "import_pif_json";
    private static final String KEY_RESET_PIF = "reset_pif_config";

    private static final String KEY_KEYBOX_STATUS = "trickystore_keybox_status";
    private static final String KEY_IMPORT_KEYBOX = "trickystore_import_keybox";
    private static final String KEY_TARGET_APPS = "trickystore_target_apps";
    private static final String KEY_IMPORT_TARGETS = "trickystore_import_targets";
    private static final String KEY_SECURITY_PATCH = "trickystore_security_patch";
    private static final String KEY_TRICKYSTORE_RESET = "trickystore_reset";

    // Secure Settings Strings (crDroid 16.0 standard)
    private static final String SPOOF_PIF_CONFIG = "spoof_pif_config";
    private static final String SPOOF_PIF_PHOTOS = "spoof_pif_photos";
    private static final String SPOOF_TRICKYSTORE_KEYBOX = "spoof_trickystore_keybox";
    private static final String SPOOF_TRICKYSTORE_TARGET = "spoof_trickystore_target";
    private static final String SPOOF_TRICKYSTORE_PATCH = "spoof_trickystore_patch";

    private static final Pattern PATCH_REGEX = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private static final Map<String, String> DEVICE_MODEL_MAP = new HashMap<>();
    static {
        DEVICE_MODEL_MAP.put("oriole", "Pixel 6");
        DEVICE_MODEL_MAP.put("raven", "Pixel 6 Pro");
        DEVICE_MODEL_MAP.put("bluejay", "Pixel 6a");
        DEVICE_MODEL_MAP.put("panther", "Pixel 7");
        DEVICE_MODEL_MAP.put("cheetah", "Pixel 7 Pro");
        DEVICE_MODEL_MAP.put("lynx", "Pixel 7a");
        DEVICE_MODEL_MAP.put("shiba", "Pixel 8");
        DEVICE_MODEL_MAP.put("tangorpro", "Pixel Tablet");
        DEVICE_MODEL_MAP.put("felix", "Pixel Fold");
        DEVICE_MODEL_MAP.put("husky", "Pixel 8 Pro");
        DEVICE_MODEL_MAP.put("akita", "Pixel 8a");
        DEVICE_MODEL_MAP.put("tokay", "Pixel 9");
        DEVICE_MODEL_MAP.put("caiman", "Pixel 9 Pro");
        DEVICE_MODEL_MAP.put("komodo", "Pixel 9 Pro XL");
        DEVICE_MODEL_MAP.put("comet", "Pixel 9 Pro Fold");
        DEVICE_MODEL_MAP.put("tegu", "Pixel 9a");
        DEVICE_MODEL_MAP.put("frankel", "Pixel 10");
        DEVICE_MODEL_MAP.put("blazer", "Pixel 10 Pro");
        DEVICE_MODEL_MAP.put("mustang", "Pixel 10 Pro XL");
        DEVICE_MODEL_MAP.put("rango", "Pixel 10 Pro Fold");
        DEVICE_MODEL_MAP.put("stallion", "Pixel 10a");
    }

    private static class PifDevice {
        String product;
        String device;
        String model;
        String otaUrl;

        PifDevice(String product, String device, String model, String otaUrl) {
            this.product = product;
            this.device = device;
            this.model = model;
            this.otaUrl = otaUrl;
        }
    }

    private enum PifChannel {
        LATEST_RELEASE,
        CANARY
    }

    private SwitchPreferenceCompat mPlayIntegrityPref;
    private SwitchPreferenceCompat mPhotosSpoofPref;
    private Preference mFetchPifBetaPref;
    private Preference mImportPifPref;
    private Preference mResetPifPref;

    private Preference mKeyboxStatusPref;
    private Preference mImportKeyboxPref;
    private Preference mTargetAppsPref;
    private Preference mImportTargetsPref;
    private Preference mSecurityPatchPref;
    private Preference mResetTrickyStorePref;

    private ActivityResultLauncher<String> mKeyboxPickerLauncher;
    private ActivityResultLauncher<String> mPifPickerLauncher;
    private ActivityResultLauncher<String> mTargetsPickerLauncher;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.spoofing_settings);

        mPlayIntegrityPref = findPreference(KEY_PLAY_INTEGRITY_SPOOF);
        mPhotosSpoofPref = findPreference(KEY_PHOTOS_SPOOF);
        mFetchPifBetaPref = findPreference(KEY_FETCH_PIF_BETA);
        mImportPifPref = findPreference(KEY_IMPORT_PIF);
        mResetPifPref = findPreference(KEY_RESET_PIF);

        mKeyboxStatusPref = findPreference(KEY_KEYBOX_STATUS);
        mImportKeyboxPref = findPreference(KEY_IMPORT_KEYBOX);
        mTargetAppsPref = findPreference(KEY_TARGET_APPS);
        mImportTargetsPref = findPreference(KEY_IMPORT_TARGETS);
        mSecurityPatchPref = findPreference(KEY_SECURITY_PATCH);
        mResetTrickyStorePref = findPreference(KEY_TRICKYSTORE_RESET);

        // Play Integrity Spoof Switch
        String pifConfig = Settings.Secure.getString(getContentResolver(), SPOOF_PIF_CONFIG);
        mPlayIntegrityPref.setChecked(!TextUtils.isEmpty(pifConfig));
        mPlayIntegrityPref.setOnPreferenceChangeListener(this);

        // Photos Spoof Switch
        boolean photosEnabled = Settings.Secure.getInt(getContentResolver(),
                SPOOF_PIF_PHOTOS, 1) == 1;
        mPhotosSpoofPref.setChecked(photosEnabled);
        mPhotosSpoofPref.setOnPreferenceChangeListener(this);

        if (mFetchPifBetaPref != null) {
            mFetchPifBetaPref.setOnPreferenceClickListener(this);
        }
        mImportPifPref.setOnPreferenceClickListener(this);
        mResetPifPref.setOnPreferenceClickListener(this);

        mImportKeyboxPref.setOnPreferenceClickListener(this);
        mTargetAppsPref.setOnPreferenceClickListener(this);
        if (mImportTargetsPref != null) {
            mImportTargetsPref.setOnPreferenceClickListener(this);
        }
        mSecurityPatchPref.setOnPreferenceClickListener(this);
        mResetTrickyStorePref.setOnPreferenceClickListener(this);

        // Setup File Pickers
        mKeyboxPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) importKeyboxFile(uri);
                }
        );

        mPifPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) importPifFile(uri);
                }
        );

        mTargetsPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) importTargetsFile(uri);
                }
        );
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAllSummaries();
    }

    private void refreshAllSummaries() {
        updateKeyboxStatus();
        updateTargetAppsSummary();
        updateSecurityPatchSummary();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mPlayIntegrityPref) {
            boolean enabled = (Boolean) newValue;
            if (!enabled) {
                Settings.Secure.putString(getContentResolver(), SPOOF_PIF_CONFIG, null);
                killGms();
                Toast.makeText(requireContext(), R.string.pif_reset_done, Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (preference == mPhotosSpoofPref) {
            boolean enabled = (Boolean) newValue;
            Settings.Secure.putInt(getContentResolver(), SPOOF_PIF_PHOTOS, enabled ? 1 : 0);
            killGms();
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference == mFetchPifBetaPref) {
            showChannelSelectionDialog();
            return true;
        } else if (preference == mImportKeyboxPref) {
            mKeyboxPickerLauncher.launch("*/*");
            return true;
        } else if (preference == mImportPifPref) {
            mPifPickerLauncher.launch("*/*");
            return true;
        } else if (preference == mImportTargetsPref) {
            mTargetsPickerLauncher.launch("*/*");
            return true;
        } else if (preference == mTargetAppsPref) {
            showTargetAppsDialog();
            return true;
        } else if (preference == mSecurityPatchPref) {
            showPatchDateDialog();
            return true;
        } else if (preference == mResetPifPref) {
            resetPifConfig();
            return true;
        } else if (preference == mResetTrickyStorePref) {
            resetTrickyStore();
            return true;
        }
        return false;
    }

    private void showChannelSelectionDialog() {
        Context context = requireContext();
        String[] channels = {
            getString(R.string.pif_channel_latest),
            getString(R.string.pif_channel_canary)
        };

        new AlertDialog.Builder(context)
                .setTitle(R.string.pif_select_channel)
                .setItems(channels, (dialog, which) -> {
                    PifChannel channel = which == 0 ? PifChannel.LATEST_RELEASE : PifChannel.CANARY;
                    fetchDevicesForChannel(channel);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private List<PifDevice> fetchAvailableDevices() {
        List<PifDevice> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String googleUrl = "https://developer.android.com";
        String versionsHtml = fetchHttp(googleUrl + "/about/versions");
        if (versionsHtml == null || versionsHtml.isEmpty()) return result;

        Matcher verMatcher = Pattern.compile("https://developer\.android\.com/about/versions/(\d+)").matcher(versionsHtml);
        List<Integer> versions = new ArrayList<>();
        while (verMatcher.find()) {
            int v = Integer.parseInt(verMatcher.group(1));
            if (!versions.contains(v)) versions.add(v);
        }
        Collections.sort(versions, Collections.reverseOrder());
        if (versions.isEmpty()) return result;
        versions.add(0, versions.get(0) + 1);

        for (int version : versions) {
            try {
                String downloadUrl = googleUrl + "/about/versions/" + version + "/download-ota";
                String otaHtml = fetchHttp(downloadUrl);
                if (otaHtml == null) continue;

                Matcher matchOta = Pattern.compile("href=\"(https://dl\.google\.com/[^\"]*ota/([^/\"]+_beta)[^\"]*?)\"").matcher(otaHtml);
                while (matchOta.find()) {
                    String otaUrl = matchOta.group(1);
                    String product = matchOta.group(2);
                    String device = product.replace("_beta", "");
                    if (seen.add(device)) {
                        String model = DEVICE_MODEL_MAP.getOrDefault(device, device);
                        result.add(new PifDevice(product, device, model, otaUrl));
                    }
                }
                if (!result.isEmpty()) return result;
            } catch (Exception ignored) {}
        }
        return result;
    }

    private static class CanaryResult {
        List<PifDevice> devices;
        String apiKey;
        CanaryResult(List<PifDevice> d, String a) { devices = d; apiKey = a; }
    }

    private CanaryResult fetchAvailableCanaryDevices() {
        List<PifDevice> result = new ArrayList<>();
        String googleUrl = "https://developer.android.com";
        try {
            String versionsHtml = fetchHttp(googleUrl + "/about/versions");
            if (versionsHtml == null || versionsHtml.isEmpty()) return new CanaryResult(result, null);
            Matcher verMatcher = Pattern.compile("https://developer\.android\.com/about/versions/(\d+)").matcher(versionsHtml);
            List<Integer> versions = new ArrayList<>();
            while (verMatcher.find()) {
                int v = Integer.parseInt(verMatcher.group(1));
                if (!versions.contains(v)) versions.add(v);
            }
            Collections.sort(versions, Collections.reverseOrder());
            if (versions.isEmpty()) return new CanaryResult(result, null);
            versions.add(0, versions.get(0) + 1);

            Pattern rowPattern = Pattern.compile("<tr id=\"([^\"]+)\">(.*?)<td[^>]*>([^<]+)</td>", Pattern.DOTALL);

            for (int version : versions) {
                try {
                    String latestHtml = fetchHttp(googleUrl + "/about/versions/" + version);
                    if (latestHtml == null) continue;
                    Matcher qprMatcher = Pattern.compile("href=\"(/about/versions/" + version + "/qpr(\d+)/download-ota)\"").matcher(latestHtml);
                    int maxQpr = -1;
                    String qprPath = null;
                    while (qprMatcher.find()) {
                        int qpr = Integer.parseInt(qprMatcher.group(2));
                        if (qpr > maxQpr) {
                            maxQpr = qpr;
                            qprPath = qprMatcher.group(1);
                        }
                    }
                    if (qprPath == null) continue;

                    String fiHtml = fetchHttp(googleUrl + qprPath);
                    if (fiHtml == null) continue;
                    Set<String> seen = new HashSet<>();
                    Matcher rowMatch = rowPattern.matcher(fiHtml);
                    while (rowMatch.find()) {
                        String device = rowMatch.group(1);
                        if (!seen.add(device)) continue;
                        String model = rowMatch.group(3).trim();
                        if (model.isEmpty()) model = DEVICE_MODEL_MAP.getOrDefault(device, device);
                        result.add(new PifDevice(device + "_beta", device, model, ""));
                    }

                    if (result.isEmpty()) continue;

                    String flashHtml = fetchHttp("https://flash.android.com");
                    String apiKey = null;
                    if (flashHtml != null) {
                        Matcher apiMatch = Pattern.compile("AIza[0-9A-Za-z_-]{35}").matcher(flashHtml);
                        if (apiMatch.find()) {
                            apiKey = apiMatch.group();
                        }
                    }
                    return new CanaryResult(result, apiKey);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            Log.e(TAG, "Canary fetch failed", e);
        }
        return new CanaryResult(result, null);
    }

    private void fetchDevicesForChannel(PifChannel channel) {
        Context context = requireContext();
        ProgressDialog progress = new ProgressDialog(context);
        progress.setMessage(getString(R.string.pif_fetching));
        progress.setCancelable(false);
        progress.show();

        mExecutor.execute(() -> {
            try {
                List<PifDevice> devices;
                String apiKey = null;
                if (channel == PifChannel.LATEST_RELEASE) {
                    devices = fetchAvailableDevices();
                } else {
                    CanaryResult cr = fetchAvailableCanaryDevices();
                    devices = cr.devices;
                    apiKey = cr.apiKey;
                }
                
                final String finalApiKey = apiKey;
                mMainHandler.post(() -> {
                    if (progress.isShowing()) progress.dismiss();
                    if (devices.isEmpty()) {
                        Toast.makeText(context, getString(R.string.pif_failed, "No devices found"), Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (channel == PifChannel.CANARY && (finalApiKey == null || finalApiKey.isEmpty())) {
                        Toast.makeText(context, getString(R.string.pif_failed, "Failed to extract Flash Tool API key"), Toast.LENGTH_LONG).show();
                        return;
                    }
                    showDeviceSelectionDialog(devices, channel, finalApiKey);
                });
            } catch (Exception e) {
                Log.e(TAG, "Error fetching online devices", e);
                mMainHandler.post(() -> {
                    if (progress.isShowing()) progress.dismiss();
                    Toast.makeText(context, getString(R.string.pif_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                });
            }
        });
    }



    private void showDeviceSelectionDialog(List<PifDevice> devices, PifChannel channel, String apiKey) {
        Context context = requireContext();
        String[] modelNames = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            modelNames[i] = devices.get(i).model;
        }

        new AlertDialog.Builder(context)
                .setTitle(R.string.pif_select_device)
                .setItems(modelNames, (dialog, which) -> {
                    generateAndSavePif(devices.get(which), channel, apiKey);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void generateAndSavePif(PifDevice device, PifChannel channel, String apiKey) {
        Context context = requireContext();
        ProgressDialog progress = new ProgressDialog(context);
        progress.setMessage(getString(R.string.pif_generating, device.model));
        progress.setCancelable(false);
        progress.show();

        mExecutor.execute(() -> {
            try {
                String jsonString;
                if (channel == PifChannel.LATEST_RELEASE) {
                    byte[] headerBytes = fetchPartialBytes(device.otaUrl, 4096);
                    if (headerBytes == null || headerBytes.length == 0) {
                        throw new Exception("Could not download OTA metadata");
                    }
                    String partial = new String(headerBytes, StandardCharsets.ISO_8859_1);

                    String fingerprint = extractKey(partial, "post-build=");
                    String securityPatch = extractKey(partial, "security-patch-level=");

                    if (TextUtils.isEmpty(fingerprint) || TextUtils.isEmpty(securityPatch)) {
                        throw new Exception("Could not extract fingerprint from OTA");
                    }

                    String[] fpParts = fingerprint.split("/");
                    String release = "";
                    if (fpParts.length > 2) {
                        int colonIdx = fpParts[2].indexOf(':');
                        if (colonIdx != -1) {
                            release = fpParts[2].substring(colonIdx + 1);
                        }
                    }
                    String buildId = fpParts.length > 3 ? fpParts[3] : "";

                    JSONObject pifJson = new JSONObject();
                    pifJson.put("TYPE", "user");
                    pifJson.put("TAGS", "release-keys");
                    pifJson.put("ID", buildId);
                    pifJson.put("BRAND", "google");
                    pifJson.put("DEVICE", device.device);
                    pifJson.put("FINGERPRINT", fingerprint);
                    pifJson.put("MANUFACTURER", "Google");
                    pifJson.put("MODEL", device.model);
                    pifJson.put("PRODUCT", device.product);
                    pifJson.put("RELEASE", release);
                    pifJson.put("SECURITY_PATCH", securityPatch);
                    pifJson.put("DEVICE_INITIAL_SDK_INT", "21");
                    pifJson.put("DEBUG", false);
                    pifJson.put("SDK_INT", "32");
                    jsonString = pifJson.toString(2);
                } else {
                    String buildsUrl = "https://content-flashstation-pa.googleapis.com/v1/builds?product=" + device.product + "&key=" + apiKey;
                    HttpURLConnection conn = (HttpURLConnection) new URL(buildsUrl).openConnection();
                    conn.setRequestProperty("Referer", "https://flash.android.com");
                    conn.setRequestProperty("X-Goog-Api-Key", apiKey);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    
                    InputStream in = conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line).append("
");
                    String buildsJson = sb.toString();

                    JSONObject root = new JSONObject(buildsJson);
                    org.json.JSONArray buildsArray = root.optJSONArray("flashstationBuild");
                    if (buildsArray == null) throw new Exception("No flashstationBuild array in Flash Tool response");

                    String id = null, incremental = null, canaryId = null;
                    for (int i = buildsArray.length() - 1; i >= 0; i--) {
                        JSONObject b = buildsArray.optJSONObject(i);
                        if (b == null) continue;
                        JSONObject meta = b.optJSONObject("previewMetadata");
                        if (meta == null || !meta.optBoolean("canary")) continue;

                        String rc = b.optString("releaseCandidateName");
                        String bid = b.optString("buildId");
                        if (rc.isEmpty() || bid.isEmpty()) continue;

                        id = rc;
                        incremental = bid;
                        String metaId = meta.optString("id");
                        if (metaId.contains("canary-")) {
                            canaryId = metaId;
                        }
                        break;
                    }

                    if (id == null || incremental == null) {
                        throw new Exception("No canary build found for " + device.product);
                    }

                    String fingerprint = "google/" + device.product + "/" + device.device + ":CANARY/" + id + "/" + incremental + ":user/release-keys";
                    String canaryMonth = null;
                    if (canaryId != null) {
                        Matcher m = Pattern.compile("canary-(\d{4})(\d{2})").matcher(canaryId);
                        if (m.find()) {
                            canaryMonth = m.group(1) + "-" + m.group(2);
                        }
                    }
                    if (canaryMonth == null) throw new Exception("Failed to derive canary month id");

                    String securityPatch = canaryMonth + "-05";
                    try {
                        String bulletinHtml = fetchHttp("https://source.android.com/docs/security/bulletin/pixel");
                        if (bulletinHtml != null) {
                            Matcher m = Pattern.compile("<td>(" + canaryMonth + "-\d{2})</td>").matcher(bulletinHtml);
                            if (m.find()) {
                                securityPatch = m.group(1);
                            }
                        }
                    } catch (Exception e) {
                        Log.d(TAG, "Bulletin fetch failed, using estimated patch: " + e.getMessage());
                    }

                    JSONObject pifJson = new JSONObject();
                    pifJson.put("TYPE", "user");
                    pifJson.put("TAGS", "release-keys");
                    pifJson.put("ID", id);
                    pifJson.put("BRAND", "google");
                    pifJson.put("DEVICE", device.device);
                    pifJson.put("FINGERPRINT", fingerprint);
                    pifJson.put("MANUFACTURER", "Google");
                    pifJson.put("MODEL", device.model);
                    pifJson.put("PRODUCT", device.product);
                    pifJson.put("RELEASE", "CANARY");
                    pifJson.put("SECURITY_PATCH", securityPatch);
                    pifJson.put("DEVICE_INITIAL_SDK_INT", "32");
                    pifJson.put("DEBUG", false);
                    pifJson.put("SDK_INT", "32");
                    jsonString = pifJson.toString(2);
                }

                final String finalJsonString = jsonString;
                mMainHandler.post(() -> {
                    if (progress.isShowing()) progress.dismiss();
                    applyPifJson(device.model, finalJsonString);
                });

            } catch (Exception e) {
                Log.e(TAG, "Failed downloading OTA metadata", e);
                mMainHandler.post(() -> {
                    if (progress.isShowing()) progress.dismiss();
                    Toast.makeText(context, getString(R.string.pif_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private String extractKey(String content, String prefix) {
        int idx = content.indexOf(prefix);
        if (idx == -1) return null;
        int end = content.indexOf('\n', idx);
        if (end == -1) end = content.indexOf('\r', idx);
        if (end == -1) end = content.length();
        return content.substring(idx + prefix.length(), end).trim();
    }

    private String fetchHttp(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            if (conn.getResponseCode() != 200) return null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private byte[] fetchPartialBytes(String urlStr, int maxBytes) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Range", "bytes=0-" + (maxBytes - 1));
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                return out.toByteArray();
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void applyPifJson(String model, String jsonContent) {
        try {
            // crDroid 16.0: Saves directly into Settings.Secure for AxSpoofManager cache
            Settings.Secure.putString(getContentResolver(), SPOOF_PIF_CONFIG, jsonContent);
            mPlayIntegrityPref.setChecked(true);

            killGms();
            Toast.makeText(requireContext(), getString(R.string.pif_fetched_model, model), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Error applying PIF json", e);
            Toast.makeText(requireContext(), getString(R.string.pif_failed, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void updateKeyboxStatus() {
        String keyboxXml = Settings.Secure.getString(getContentResolver(), SPOOF_TRICKYSTORE_KEYBOX);

        if (!TextUtils.isEmpty(keyboxXml) && keyboxXml.trim().length() > 0) {
            mKeyboxStatusPref.setSummary(getString(R.string.trickystore_keybox_status_summary_loaded,
                    keyboxXml.length()));
        } else {
            mKeyboxStatusPref.setSummary(R.string.trickystore_keybox_status_summary_none);
        }
    }

    private void updateTargetAppsSummary() {
        String targetData = Settings.Secure.getString(getContentResolver(), SPOOF_TRICKYSTORE_TARGET);
        if (TextUtils.isEmpty(targetData)) {
            mTargetAppsPref.setSummary(R.string.trickystore_target_apps_summary_default);
            return;
        }

        int auto = 0, cert = 0, leaf = 0;
        for (String line : targetData.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            if (trimmed.endsWith("!")) cert++;
            else if (trimmed.endsWith("?")) leaf++;
            else auto++;
        }

        int total = auto + cert + leaf;
        if (total == 0) {
            mTargetAppsPref.setSummary(R.string.trickystore_target_apps_summary_default);
        } else {
            StringBuilder sb = new StringBuilder();
            if (auto > 0) sb.append(auto).append(" auto");
            if (cert > 0) {
                if (sb.length() > 0) sb.append(" • ");
                sb.append(cert).append(" cert");
            }
            if (leaf > 0) {
                if (sb.length() > 0) sb.append(" • ");
                sb.append(leaf).append(" leaf");
            }
            mTargetAppsPref.setSummary(sb.toString());
        }
    }

    private void updateSecurityPatchSummary() {
        String patch = Settings.Secure.getString(getContentResolver(), SPOOF_TRICKYSTORE_PATCH);
        if (!TextUtils.isEmpty(patch)) {
            mSecurityPatchPref.setSummary(patch);
        } else {
            mSecurityPatchPref.setSummary(R.string.trickystore_security_patch_summary);
        }
    }

    private void showPatchDateDialog() {
        Context context = requireContext();
        String current = Settings.Secure.getString(getContentResolver(), SPOOF_TRICKYSTORE_PATCH);
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
        input.setHint(R.string.trickystore_patch_hint);
        if (!TextUtils.isEmpty(current)) {
            input.setText(current);
        }

        new AlertDialog.Builder(context)
                .setTitle(R.string.trickystore_security_patch_title)
                .setView(input)
                .setPositiveButton(R.string.dlg_ok, (dialog, which) -> {
                    String date = input.getText().toString().trim();
                    if (TextUtils.isEmpty(date)) {
                        Settings.Secure.putString(getContentResolver(), SPOOF_TRICKYSTORE_PATCH, "");
                        updateSecurityPatchSummary();
                        killGms();
                    } else if (PATCH_REGEX.matcher(date).matches()) {
                        Settings.Secure.putString(getContentResolver(), SPOOF_TRICKYSTORE_PATCH, date);
                        updateSecurityPatchSummary();
                        killGms();
                        Toast.makeText(context, R.string.trickystore_saved, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(context, R.string.trickystore_invalid_patch, Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void importKeyboxFile(Uri uri) {
        Context context = requireContext();
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            byte[] rawBytes = out.toByteArray();
            String encoded = Base64.encodeToString(rawBytes, Base64.NO_WRAP);

            // crDroid 16.0 standard: Saves to Settings.Secure for AxSpoofManager as Base64 NO_WRAP
            Settings.Secure.putString(getContentResolver(), SPOOF_TRICKYSTORE_KEYBOX, encoded);

            killGms();
            updateKeyboxStatus();
            Toast.makeText(context, R.string.trickystore_keybox_imported, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error importing keybox", e);
            Toast.makeText(context, R.string.trickystore_error, Toast.LENGTH_SHORT).show();
        }
    }

    private String normalizePifPayload(String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return "{}";
        if (trimmed.startsWith("{")) return trimmed;
        try {
            JSONObject json = new JSONObject();
            for (String line : trimmed.split("
")) {
                String stripped = line.trim();
                if (stripped.isEmpty() || stripped.startsWith("#") || stripped.startsWith("//")) continue;
                int eq = stripped.indexOf('=');
                if (eq > 0) {
                    String key = stripped.substring(0, eq).trim();
                    String valueStr = stripped.substring(eq + 1).trim();
                    int hashIdx = valueStr.indexOf('#');
                    if (hashIdx >= 0) {
                        valueStr = valueStr.substring(0, hashIdx).trim();
                    }
                    if (!key.isEmpty()) {
                        json.put(key, valueStr);
                    }
                }
            }
            return json.toString(2);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void importPifFile(Uri uri) {
        Context context = requireContext();
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            byte[] rawBytes = out.toByteArray();
            String pifContent = new String(rawBytes, StandardCharsets.UTF_8);
            String normalized = normalizePifPayload(pifContent);

            // crDroid 16.0 standard: Saves to Settings.Secure for AxSpoofManager
            Settings.Secure.putString(getContentResolver(), SPOOF_PIF_CONFIG, normalized);
            mPlayIntegrityPref.setChecked(true);

            killGms();
            Toast.makeText(context, R.string.pif_imported, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error importing pif.json", e);
            Toast.makeText(context, R.string.trickystore_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void importTargetsFile(Uri uri) {
        Context context = requireContext();
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            String content = new String(out.toByteArray(), StandardCharsets.UTF_8);

            Settings.Secure.putString(getContentResolver(), SPOOF_TRICKYSTORE_TARGET, content);
            killGms();
            updateTargetAppsSummary();
            Toast.makeText(context, R.string.trickystore_target_imported, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error importing target list", e);
            Toast.makeText(context, R.string.trickystore_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void resetPifConfig() {
        Settings.Secure.putString(getContentResolver(), SPOOF_PIF_CONFIG, null);
        mPlayIntegrityPref.setChecked(false);

        killGms();
        Toast.makeText(requireContext(), R.string.pif_reset_done, Toast.LENGTH_SHORT).show();
    }

    private void resetTrickyStore() {
        Settings.Secure.putString(getContentResolver(), SPOOF_TRICKYSTORE_KEYBOX, null);
        Settings.Secure.putString(getContentResolver(), SPOOF_TRICKYSTORE_TARGET, null);
        Settings.Secure.putString(getContentResolver(), SPOOF_TRICKYSTORE_PATCH, null);

        killGms();
        refreshAllSummaries();
        Toast.makeText(requireContext(), R.string.trickystore_reset_done, Toast.LENGTH_SHORT).show();
    }

    private Map<String, String> readTargetAppsMap() {
        Map<String, String> map = new HashMap<>();
        String targetData = Settings.Secure.getString(getContentResolver(), SPOOF_TRICKYSTORE_TARGET);
        if (TextUtils.isEmpty(targetData)) {
            map.put("com.google.android.gms", "AUTO");
            map.put("com.android.vending", "AUTO");
            return map;
        }

        for (String line : targetData.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            if (trimmed.endsWith("!")) {
                map.put(trimmed.substring(0, trimmed.length() - 1).trim(), "GENERATE");
            } else if (trimmed.endsWith("?")) {
                map.put(trimmed.substring(0, trimmed.length() - 1).trim(), "LEAF");
            } else {
                map.put(trimmed, "AUTO");
            }
        }
        return map;
    }

    private void showTargetAppsDialog() {
        PackageManager pm = getPackageManager();
        if (pm == null) return;

        List<PackageInfo> installedPackages = pm.getInstalledPackages(PackageManager.GET_META_DATA);
        List<String> appLabels = new ArrayList<>();
        List<String> packageNames = new ArrayList<>();
        Map<String, String> currentTargets = readTargetAppsMap();

        for (PackageInfo pi : installedPackages) {
            if (pi.applicationInfo == null) continue;
            boolean isSystem = (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (!isSystem || pi.packageName.equals("com.google.android.gms")
                    || pi.packageName.equals("com.android.vending")) {
                CharSequence label = pi.applicationInfo.loadLabel(pm);
                appLabels.add(label.toString());
                packageNames.add(pi.packageName);
            }
        }

        showTargetAppListDialog(appLabels, packageNames, currentTargets);
    }

    private void showTargetAppListDialog(List<String> appLabels, List<String> packageNames, Map<String, String> currentTargets) {
        Context context = requireContext();
        String[] displayItems = new String[packageNames.size()];
        for (int i = 0; i < packageNames.size(); i++) {
            String pkg = packageNames.get(i);
            String mode = currentTargets.get(pkg);
            String modeBadge = mode == null ? " [" + getString(R.string.trickystore_mode_disabled) + "]" : " [" + mode + "]";
            displayItems[i] = appLabels.get(i) + " (" + pkg + ")" + modeBadge;
        }

        new AlertDialog.Builder(context)
                .setTitle(R.string.trickystore_target_apps_title)
                .setItems(displayItems, (dialog, which) -> {
                    String selectedPkg = packageNames.get(which);
                    String selectedLabel = appLabels.get(which);
                    showModeSelectionDialog(selectedLabel, selectedPkg, currentTargets, () -> {
                        showTargetAppListDialog(appLabels, packageNames, currentTargets);
                    });
                })
                .setPositiveButton(R.string.dlg_ok, (dialog, which) -> {
                    saveTargetApps(currentTargets);
                })
                .setNeutralButton(R.string.trickystore_import_targets_title, (dialog, which) -> {
                    mTargetsPickerLauncher.launch("*/*");
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showModeSelectionDialog(String appLabel, String pkg, Map<String, String> currentTargets, Runnable onDismiss) {
        Context context = requireContext();
        String[] modes = {
            getString(R.string.trickystore_mode_auto),
            getString(R.string.trickystore_mode_cert),
            getString(R.string.trickystore_mode_leaf),
            getString(R.string.trickystore_mode_disabled)
        };

        new AlertDialog.Builder(context)
                .setTitle(appLabel + "\n" + getString(R.string.trickystore_select_mode_title))
                .setItems(modes, (dialog, which) -> {
                    if (which == 0) {
                        currentTargets.put(pkg, "AUTO");
                    } else if (which == 1) {
                        currentTargets.put(pkg, "GENERATE");
                    } else if (which == 2) {
                        currentTargets.put(pkg, "LEAF");
                    } else {
                        currentTargets.remove(pkg);
                    }
                    saveTargetApps(currentTargets);
                    onDismiss.run();
                })
                .setNegativeButton(R.string.cancel, (d, w) -> onDismiss.run())
                .show();
    }

    private void saveTargetApps(Map<String, String> targets) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : targets.entrySet()) {
            String pkg = entry.getKey();
            String mode = entry.getValue();
            if ("GENERATE".equals(mode)) sb.append(pkg).append("!\n");
            else if ("LEAF".equals(mode)) sb.append(pkg).append("?\n");
            else sb.append(pkg).append("\n");
        }
        String saved = sb.toString();
        Settings.Secure.putString(getContentResolver(), SPOOF_TRICKYSTORE_TARGET, saved);
        killGms();
        updateTargetAppsSummary();
    }

    private void killGms() {
        try {
            ActivityManager am = (ActivityManager) requireContext().getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.forceStopPackage("com.android.vending");
                am.forceStopPackage("com.google.android.gms.unstable");
                am.forceStopPackage("com.google.android.gms");
                am.forceStopPackage("com.google.android.rkpdapp");
                requireContext().getPackageManager().clearApplicationUserData("com.android.vending", null);
            }
        } catch (Exception ignored) {}
    }
}
