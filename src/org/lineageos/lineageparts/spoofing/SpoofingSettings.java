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

    private void fetchDevicesForChannel(PifChannel channel) {
        Context context = requireContext();
        ProgressDialog progress = new ProgressDialog(context);
        progress.setMessage(getString(R.string.pif_fetching));
        progress.setCancelable(false);
        progress.show();

        mExecutor.execute(() -> {
            try {
                List<PifDevice> devices = scanGoogleBetaDevices(channel);
                mMainHandler.post(() -> {
                    if (progress.isShowing()) progress.dismiss();
                    if (devices.isEmpty()) {
                        Toast.makeText(context, getString(R.string.pif_failed, "No devices found"), Toast.LENGTH_LONG).show();
                    } else {
                        showDeviceSelectionDialog(devices, channel);
                    }
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

    private List<PifDevice> scanGoogleBetaDevices(PifChannel channel) {
        List<PifDevice> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        String googleUrl = "https://developer.android.com";
        String versionsHtml = fetchHttp(googleUrl + "/about/versions");
        if (versionsHtml == null || versionsHtml.isEmpty()) return result;

        Matcher verMatcher = Pattern.compile("https://developer\\.android\\.com/about/versions/(\\d+)").matcher(versionsHtml);
        List<Integer> versions = new ArrayList<>();
        while (verMatcher.find()) {
            int v = Integer.parseInt(verMatcher.group(1));
            if (!versions.contains(v)) versions.add(v);
        }
        Collections.sort(versions, Collections.reverseOrder());

        for (int version : versions) {
            try {
                String downloadUrl = googleUrl + "/about/versions/" + version + "/download-ota";
                String otaHtml = fetchHttp(downloadUrl);
                if (otaHtml == null) continue;

                Matcher matchOta = Pattern.compile("href=\"(https://dl\\.google\\.com/[^\"]*ota/([^/\"]+_beta)[^\"]*?)\"").matcher(otaHtml);
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

    private void showDeviceSelectionDialog(List<PifDevice> devices, PifChannel channel) {
        Context context = requireContext();
        String[] modelNames = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            modelNames[i] = devices.get(i).model;
        }

        new AlertDialog.Builder(context)
                .setTitle(R.string.pif_select_device)
                .setItems(modelNames, (dialog, which) -> {
                    generateAndSavePif(devices.get(which), channel);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void generateAndSavePif(PifDevice device, PifChannel channel) {
        Context context = requireContext();
        ProgressDialog progress = new ProgressDialog(context);
        progress.setMessage(getString(R.string.pif_generating, device.model));
        progress.setCancelable(false);
        progress.show();

        mExecutor.execute(() -> {
            try {
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

                String jsonString = pifJson.toString(2);

                mMainHandler.post(() -> {
                    if (progress.isShowing()) progress.dismiss();
                    applyPifJson(device.model, jsonString);
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
            String keyboxXml = new String(rawBytes, StandardCharsets.UTF_8);

            if (!keyboxXml.contains("<Keybox") && !keyboxXml.contains("<CertificateChain")
                    && !keyboxXml.contains("<PrivateKey")) {
                Toast.makeText(context, R.string.trickystore_error, Toast.LENGTH_LONG).show();
                return;
            }

            // crDroid 16.0 standard: Saves to Settings.Secure for AxSpoofManager
            Settings.Secure.putString(getContentResolver(), SPOOF_TRICKYSTORE_KEYBOX, keyboxXml);

            killGms();
            updateKeyboxStatus();
            Toast.makeText(context, R.string.trickystore_keybox_imported, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error importing keybox", e);
            Toast.makeText(context, R.string.trickystore_error, Toast.LENGTH_SHORT).show();
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

            // crDroid 16.0 standard: Saves to Settings.Secure for AxSpoofManager
            Settings.Secure.putString(getContentResolver(), SPOOF_PIF_CONFIG, pifContent);
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
                am.killBackgroundProcesses("com.google.android.gms");
                am.killBackgroundProcesses("com.android.vending");
                am.killBackgroundProcesses("com.google.android.gms.unstable");
            }
        } catch (Exception ignored) {}
    }
}
