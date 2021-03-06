package com.google.android.apps.nexuslauncher;

import android.Manifest;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.DownloadManager;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.preference.TwoStatePreference;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.google.android.apps.nexuslauncher.smartspace.SmartspaceController;

import java.io.File;

import dev.dworks.apps.alauncher.App;
import dev.dworks.apps.alauncher.Settings;
import dev.dworks.apps.alauncher.apps.lock.AppLockHelper;
import dev.dworks.apps.alauncher.helpers.Utils;

import static com.android.launcher3.Utilities.THEME_OVERRIDE_KEY;
import static com.android.launcher3.graphics.IconShapeOverride.KEY_PREFERENCE;
import static dev.dworks.apps.alauncher.Settings.SUPPORT;

public class SettingsActivity extends com.android.launcher3.SettingsActivity implements PreferenceFragment.OnPreferenceStartFragmentCallback {
    public final static String ICON_PACK_PREF = "pref_icon_pack";
    public final static String SHOW_PREDICTIONS_PREF = "pref_show_predictions";
    public final static String ENABLE_MINUS_ONE_PREF = "pref_enable_minus_one";
    public final static String SMARTSPACE_PREF = "pref_smartspace";
    public final static String APP_VERSION_PREF = "about_app_version";
    private final static String GOOGLE_APP = "com.google.android.googlequicksearchbox";

    private static final String SMARTSPACE_SETTINGS = "pref_smartspace_settings";

    private final static int REQUEST_EXTERNAL_STORAGE = 100;

    @Override
    protected void onCreate(final Bundle bundle) {
        setTheme(R.style.SettingsTheme);
        super.onCreate(bundle);
        if (bundle == null) {
            getFragmentManager().beginTransaction().replace(android.R.id.content, new MySettingsFragment()).commit();
        }

        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(R.drawable.ic_logo);
        }
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragment preferenceFragment, Preference preference) {
        Fragment instantiate = Fragment.instantiate(this, preference.getFragment(), preference.getExtras());
        if (instantiate instanceof DialogFragment) {
            ((DialogFragment) instantiate).show(getFragmentManager(), preference.getKey());
        } else {
            getFragmentManager().beginTransaction().replace(android.R.id.content, instantiate).addToBackStack(preference.getKey()).commit();
        }
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (Settings.isSettingsDirty(this) && !isChangingConfigurations()) {
            Settings.clearSettingsDirty(this);
            Utils.restart(this);
        }
    }

    protected void promptBridge() {
        if (Utilities.ATLEAST_MARSHMALLOW &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, REQUEST_EXTERNAL_STORAGE);
        } else {
            installBridge();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_EXTERNAL_STORAGE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                installBridge();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void installBridge() {
        // Use application context to ensure it does not expire after the download.
        final Context context = getApplicationContext();

        final String fileName = "/" + getString(R.string.bridge_download_file);

        final String src = getString(R.string.bridge_download_url);

        final String dest = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS) + fileName;

        final File file = new File(dest);
        if (file.exists() && !file.delete()) {
            return;
        }

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(src))
                .setVisibleInDownloadsUi(false)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

        final DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        final long downloadId = manager.enqueue(request);

        context.registerReceiver(new BroadcastReceiver() {
            public void onReceive(Context ignored, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
                if (id == downloadId) {
                    Utils.installCompanionApp(ignored, file);
                    context.unregisterReceiver(this);
                }
            }
        }, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    public static class MySettingsFragment extends LauncherSettingsFragment
            implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
        private CustomIconPreference mIconPackPref;
        private Context mContext;

        @Override
        public void onCreate(Bundle bundle) {
            super.onCreate(bundle);

            mContext = getActivity();

            findPreference(SHOW_PREDICTIONS_PREF).setOnPreferenceChangeListener(this);
            findPreference(ENABLE_MINUS_ONE_PREF).setOnPreferenceChangeListener(this);
            findPreference(ENABLE_MINUS_ONE_PREF).setTitle(getDisplayGoogleTitle());

            PackageManager packageManager = mContext.getPackageManager();
            try {
                PackageInfo packageInfo = packageManager.getPackageInfo(mContext.getPackageName(), 0);
                findPreference(APP_VERSION_PREF).setSummary(packageInfo.versionName);
            } catch (PackageManager.NameNotFoundException ex) {
                Log.e("SettingsActivity", "Unable to load my own package info", ex);
            }

            try {
                ApplicationInfo applicationInfo = mContext.getPackageManager().getApplicationInfo(GOOGLE_APP, 0);
                if (!applicationInfo.enabled) {
                    throw new PackageManager.NameNotFoundException();
                }
            } catch (PackageManager.NameNotFoundException ignored) {
                getPreferenceScreen().removePreference(findPreference(SettingsActivity.ENABLE_MINUS_ONE_PREF));
            }

            mIconPackPref = (CustomIconPreference) findPreference(ICON_PACK_PREF);
            mIconPackPref.setOnPreferenceChangeListener(this);

            findPreference(SHOW_PREDICTIONS_PREF).setOnPreferenceChangeListener(this);

            customPreferences();
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
            if (preference instanceof PreferenceScreen) {
                initializeActionBar((PreferenceScreen) preference);
            }
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }

        public void initializeActionBar(PreferenceScreen preferenceScreen) {
            final Dialog dialog = preferenceScreen.getDialog();

            if (dialog != null) {
                dialog.getActionBar().setDisplayHomeAsUpEnabled(true);
                dialog.getActionBar().setHomeButtonEnabled(true);
                int titleId = getActivity().getResources().getIdentifier("action_bar", "id", "android");
                ViewGroup root = dialog.findViewById(titleId);
                View homeBtn = null;
                for (int i = 0 ; i < root.getChildCount(); i++) {
                    View child = root.getChildAt(i);
                    if(child instanceof ImageView){
                        homeBtn = child;
                        break;
                    }
                }

                if (homeBtn != null) {
                    homeBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            dialog.dismiss();
                        }
                    });
                }
            }
        }

        private void customPreferences() {
            //findPreference(THEME_OVERRIDE_KEY).setOnPreferenceChangeListener(this);
            findPreference(Settings.BOTTOM_SEARCH_BAR_KEY).setOnPreferenceChangeListener(this);
            findPreference(Settings.TOP_SEARCH_BAR_KEY).setOnPreferenceChangeListener(this);
            findPreference(Settings.PHYSICAL_ANIMATION_KEY).setOnPreferenceChangeListener(this);
            findPreference(Settings.TRANSPARENT_NAVIGATION_BAR).setOnPreferenceChangeListener(this);
            findPreference(Settings.EXTRA_BOTTOM_PADDING).setOnPreferenceChangeListener(this);
            findPreference(Settings.GRID_COLUMNS).setOnPreferenceChangeListener(this);
            findPreference(Settings.GRID_ROWS).setOnPreferenceChangeListener(this);
            findPreference(Settings.HOTSEAT_ICONS).setOnPreferenceChangeListener(this);
            findPreference(Settings.FORCE_COLORED_G_ICON).setOnPreferenceChangeListener(this);
            findPreference(Settings.ICON_SIZE).setOnPreferenceChangeListener(this);
            findPreference(Settings.ICON_TEXT_SIZE).setOnPreferenceChangeListener(this);
            findPreference(Settings.HOTSEAT_BACKGROUND).setOnPreferenceChangeListener(this);
            findPreference(Settings.DARK_BOTTOM_SEARCH_BAR).setOnPreferenceChangeListener(this);
            findPreference(Settings.DARK_TOP_SEARCH_BAR).setOnPreferenceChangeListener(this);
            findPreference(Settings.LABEL_HIDDEN_ON_DESKTOP).setOnPreferenceChangeListener(this);
            findPreference(Settings.LABEL_HIDDEN_ON_ALL_APPS).setOnPreferenceChangeListener(this);
            findPreference(Settings.QSB_VOICE_ICON).setOnPreferenceChangeListener(this);
            findPreference(Settings.QSB_ASSISTANT_ICON).setOnPreferenceChangeListener(this);
            findPreference(Settings.BLACK_COLORS).setOnPreferenceChangeListener(this);
            findPreference(Settings.SHOW_CARET).setOnPreferenceChangeListener(this);
            findPreference(Settings.GENERATE_ADAPTIVE_ICONS).setOnPreferenceChangeListener(this);
            findPreference(Settings.GENERATED_ADAPTIVE_BACKGROUND).setOnPreferenceChangeListener(this);
            findPreference(Settings.ALLOW_TWO_LINE_LABELS).setOnPreferenceChangeListener(this);
            findPreference(Settings.DATE_FORMAT).setOnPreferenceChangeListener(this);
            findPreference(Settings.SHORTCUT_UNLOCKED_WIDGETS).setOnPreferenceChangeListener(this);
            findPreference(Settings.SHORTCUT_UNLOCKED_UNINSTALL).setOnPreferenceChangeListener(this);
            findPreference(Settings.SHORTCUT_UNLOCKED_EDIT).setOnPreferenceChangeListener(this);
            findPreference(Settings.SHORTCUT_LOCKED_UNINSTALL).setOnPreferenceChangeListener(this);
            findPreference(Settings.SHORTCUT_LOCKED_EDIT).setOnPreferenceChangeListener(this);

            findPreference(Settings.RESET_APP_NAMES).setOnPreferenceClickListener(this);
            findPreference(Settings.RESET_APP_VISIBILITY).setOnPreferenceClickListener(this);
            findPreference(Settings.RESET_APP_LOCK).setOnPreferenceClickListener(this);
            findPreference(Settings.RESET_APP_ICONS).setOnPreferenceClickListener(this);
            findPreference(Settings.RESTART_PREFERENCE).setOnPreferenceClickListener(this);
            findPreference(Settings.CHANGE_DEFAULT_PREFERENCE).setOnPreferenceClickListener(this);
            findPreference(ENABLE_MINUS_ONE_PREF).setOnPreferenceClickListener(this);

            findPreference(Settings.ONE_FINGER_DOWN).setOnPreferenceChangeListener(this);
            findPreference(Settings.TWO_FINGER_DOWN).setOnPreferenceChangeListener(this);
            findPreference(Settings.DOUBLE_TAP_TO_LOCK).setOnPreferenceChangeListener(this);
            findPreference(Settings.DOUBLE_TAP_TO_LOCK_IS_SECURE).setOnPreferenceChangeListener(this);
            findPreference(Settings.CARET_LONG_PRESS).setOnPreferenceChangeListener(this);
            findPreference(Settings.LOCK_DESKTOP_KEY).setOnPreferenceChangeListener(this);
            findPreference(Settings.TOP_SEARCH_APPS_KEY).setOnPreferenceChangeListener(this);

            //PRO
            findPreference(Settings.BOTTOM_SEARCH_BAR_KEY).setOnPreferenceClickListener(this);
            findPreference(Settings.EXTRA_BOTTOM_PADDING).setOnPreferenceClickListener(this);
            findPreference(Settings.TOP_SEARCH_BAR_KEY).setOnPreferenceClickListener(this);
            findPreference(Settings.PHYSICAL_ANIMATION_KEY).setOnPreferenceClickListener(this);
            findPreference(Settings.TRANSPARENT_NAVIGATION_BAR).setOnPreferenceClickListener(this);
            findPreference(Settings.FORCE_COLORED_G_ICON).setOnPreferenceClickListener(this);
            findPreference(Settings.DARK_BOTTOM_SEARCH_BAR).setOnPreferenceClickListener(this);
            findPreference(Settings.DARK_TOP_SEARCH_BAR).setOnPreferenceClickListener(this);
            findPreference(Settings.LABEL_HIDDEN_ON_DESKTOP).setOnPreferenceClickListener(this);
            findPreference(Settings.LABEL_HIDDEN_ON_ALL_APPS).setOnPreferenceClickListener(this);
            findPreference(Settings.QSB_VOICE_ICON).setOnPreferenceClickListener(this);
            findPreference(Settings.QSB_ASSISTANT_ICON).setOnPreferenceClickListener(this);
            findPreference(Settings.BLACK_COLORS).setOnPreferenceClickListener(this);
            findPreference(Settings.ALLOW_TWO_LINE_LABELS).setOnPreferenceClickListener(this);
            findPreference(Settings.GENERATE_ADAPTIVE_ICONS).setOnPreferenceClickListener(this);
            findPreference(Settings.GENERATED_ADAPTIVE_BACKGROUND).setOnPreferenceClickListener(this);
            findPreference(Settings.GRID_COLUMNS).setOnPreferenceClickListener(this);
            findPreference(Settings.GRID_ROWS).setOnPreferenceClickListener(this);
            findPreference(Settings.HOTSEAT_ICONS).setOnPreferenceClickListener(this);
            findPreference(Settings.ICON_SIZE).setOnPreferenceClickListener(this);
            findPreference(Settings.ICON_TEXT_SIZE).setOnPreferenceClickListener(this);
            findPreference(Settings.HOTSEAT_BACKGROUND).setOnPreferenceClickListener(this);
            findPreference(Settings.DATE_FORMAT).setOnPreferenceClickListener(this);
            findPreference(Settings.SEARCH_PROVIDER).setOnPreferenceClickListener(this);
            findPreference(THEME_OVERRIDE_KEY).setOnPreferenceClickListener(this);
            findPreference(KEY_PREFERENCE).setOnPreferenceClickListener(this);
            findPreference(Settings.ONE_FINGER_DOWN).setOnPreferenceClickListener(this);
            findPreference(Settings.TWO_FINGER_DOWN).setOnPreferenceClickListener(this);
            findPreference(Settings.DOUBLE_TAP_TO_LOCK).setOnPreferenceClickListener(this);
            findPreference(Settings.DOUBLE_TAP_TO_LOCK_IS_SECURE).setOnPreferenceClickListener(this);
            findPreference(Settings.HOME_ACTION).setOnPreferenceClickListener(this);
            findPreference(Settings.CARET_LONG_PRESS).setOnPreferenceClickListener(this);
            findPreference(Settings.LOCK_DESKTOP_KEY).setOnPreferenceClickListener(this);
            findPreference(Settings.SHORTCUT_UNLOCKED_WIDGETS).setOnPreferenceClickListener(this);
            findPreference(Settings.SHORTCUT_UNLOCKED_UNINSTALL).setOnPreferenceClickListener(this);
            findPreference(Settings.SHORTCUT_UNLOCKED_EDIT).setOnPreferenceClickListener(this);
            findPreference(Settings.SHORTCUT_LOCKED_UNINSTALL).setOnPreferenceClickListener(this);
            findPreference(Settings.SHORTCUT_LOCKED_EDIT).setOnPreferenceClickListener(this);
            findPreference(Settings.TOP_SEARCH_APPS_KEY).setOnPreferenceClickListener(this);

            if (!Utilities.ATLEAST_OREO) {
                ((PreferenceCategory) ((PreferenceScreen) getPreferenceScreen()
                        .findPreference("pref_edit_apps_screen"))
                        .findPreference("pref_icons_category"))
                        .removePreference(findPreference(Settings.GENERATE_ADAPTIVE_ICONS));
                ((PreferenceCategory) ((PreferenceScreen) getPreferenceScreen()
                        .findPreference("pref_edit_apps_screen"))
                        .findPreference("pref_icons_category"))
                        .removePreference(findPreference(Settings.GENERATED_ADAPTIVE_BACKGROUND));
            }

            if (SmartspaceController.get(mContext).cY() && !Utils.isAmazonBuild()) {
                findPreference(SMARTSPACE_SETTINGS).setOnPreferenceClickListener(this);
            } else {
                ((PreferenceScreen) getPreferenceScreen()
                        .findPreference("pref_smartspace_screen"))
                        .removePreference(findPreference(SMARTSPACE_SETTINGS));
            }

            if(Utils.isAmazonBuild()){
                getPreferenceScreen().removePreference(findPreference("pref_basic_category"));
            }
        }

        private String getDisplayGoogleTitle() {
            CharSequence charSequence = null;
            try {
                Resources resourcesForApplication = mContext.getPackageManager().getResourcesForApplication(GOOGLE_APP);
                int identifier = resourcesForApplication.getIdentifier("title_google_home_screen", "string", GOOGLE_APP);
                if (identifier != 0) {
                    charSequence = resourcesForApplication.getString(identifier);
                }
            } catch (PackageManager.NameNotFoundException ex) { }
            if (TextUtils.isEmpty(charSequence)) {
                charSequence = mContext.getString(R.string.title_google_app);
            }
            return mContext.getString(R.string.title_show_google_app, charSequence);
        }

        @Override
        public void onResume() {
            super.onResume();
            mIconPackPref.reloadIconPacks();

            if(App.isPurchased()) {
                Preference preference = findPreference(SUPPORT);
                if(null != preference) {
                    getPreferenceScreen().removePreference(preference);
                }
            }

            SwitchPreference minusOne = (SwitchPreference) findPreference(ENABLE_MINUS_ONE_PREF);
            if (minusOne != null && !Utils.isBridgeInstalled(getActivity())) {
                minusOne.setChecked(false);
            }
        }

        @Override
        public boolean onPreferenceChange(Preference preference, final Object newValue) {
            switch (preference.getKey()) {
                case Settings.GRID_COLUMNS:
                case Settings.GRID_ROWS:
                case Settings.HOTSEAT_ICONS:
                case Settings.ICON_SIZE:
                case Settings.ICON_TEXT_SIZE:
                    if(!App.isPurchased()) {
                        break;
                    }
                    if (preference instanceof ListPreference) {
                        ((ListPreference) preference).setValue((String) newValue);
                    }
                    Settings.setSettingsDirty(mContext);
                    break;

                case THEME_OVERRIDE_KEY:
                case Settings.HOTSEAT_BACKGROUND:
                case Settings.DATE_FORMAT:
                    if(!App.isPurchased()) {
                        break;
                    }
                    if (preference instanceof ListPreference) {
                        ((ListPreference) preference).setValue((String) newValue);
                    }
                    Utils.reloadTheme(mContext);
                    break;

                case Settings.BOTTOM_SEARCH_BAR_KEY:
                case Settings.EXTRA_BOTTOM_PADDING:
                case Settings.TOP_SEARCH_BAR_KEY:
                case Settings.PHYSICAL_ANIMATION_KEY:
                case Settings.TRANSPARENT_NAVIGATION_BAR:
                case Settings.FORCE_COLORED_G_ICON:
                case Settings.DARK_BOTTOM_SEARCH_BAR:
                case Settings.DARK_TOP_SEARCH_BAR:
                case Settings.LABEL_HIDDEN_ON_DESKTOP:
                case Settings.LABEL_HIDDEN_ON_ALL_APPS:
                case Settings.QSB_VOICE_ICON:
                case Settings.QSB_ASSISTANT_ICON:
                case Settings.BLACK_COLORS:
                case Settings.SHOW_CARET:
                case Settings.ALLOW_TWO_LINE_LABELS:
                    if(!App.isPurchased()) {
                        break;
                    }
                    if (preference instanceof TwoStatePreference) {
                        ((TwoStatePreference) preference).setChecked((boolean) newValue);
                    }
                    if(!Utils.isAmazonBuild()) {
                        Utils.reloadTheme(mContext);
                    }
                    break;

                case Settings.GENERATE_ADAPTIVE_ICONS:
                case Settings.GENERATED_ADAPTIVE_BACKGROUND:
                    if(!App.isPurchased()) {
                        break;
                    }
                    if (preference instanceof TwoStatePreference) {
                        ((TwoStatePreference) preference).setChecked((boolean) newValue);
                    }
                    CustomIconUtils.applyIconPackAsync(mContext);

                    final ProgressDialog adaptiveIconDialog = ProgressDialog.show(mContext,
                            null /* title */,
                            mContext.getString(R.string.state_loading),
                            true /* indeterminate */,
                            false /* cancelable */);

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            adaptiveIconDialog.cancel();
                        }
                    }, 1000);
                    break;

                case ENABLE_MINUS_ONE_PREF:
                    if (Utils.isBridgeInstalled(getActivity())) {
                        return true;
                    }
                    break;
                case ICON_PACK_PREF:
                    if (!CustomIconUtils.getCurrentPack(mContext).equals(newValue)) {
                        final ProgressDialog applyingDialog = ProgressDialog.show(mContext,
                                null /* title */,
                                mContext.getString(R.string.state_loading),
                                true /* indeterminate */,
                                false /* cancelable */);

                        CustomIconUtils.setCurrentPack(getActivity(), (String) newValue);
                        CustomIconUtils.applyIconPackAsync(mContext);

                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                applyingDialog.cancel();
                            }
                        }, 1000);
                    }
                    return true;
                case SHOW_PREDICTIONS_PREF:
                    if ((boolean) newValue) {
                        return true;
                    }
                    SuggestionConfirmationFragment confirmationFragment = new SuggestionConfirmationFragment();
                    confirmationFragment.setTargetFragment(this, 0);
                    confirmationFragment.show(getFragmentManager(), preference.getKey());
                    break;

                case Settings.ONE_FINGER_DOWN:
                case Settings.TWO_FINGER_DOWN:
                case Settings.CARET_LONG_PRESS:
                case Settings.LOCK_DESKTOP_KEY:
                case Settings.SHORTCUT_UNLOCKED_WIDGETS:
                case Settings.SHORTCUT_UNLOCKED_EDIT:
                case Settings.SHORTCUT_UNLOCKED_UNINSTALL:
                case Settings.SHORTCUT_LOCKED_EDIT:
                case Settings.SHORTCUT_LOCKED_UNINSTALL:
                case Settings.TOP_SEARCH_APPS_KEY:
                    if(!App.isPurchased()) {
                        break;
                    }
                    if (preference instanceof TwoStatePreference) {
                        ((TwoStatePreference) preference).setChecked((boolean) newValue);
                    }
                    if(!Utils.isAmazonBuild()) {
                        Utils.reloadTheme(mContext);
                    }
                    break;
                case Settings.DOUBLE_TAP_TO_LOCK:
                    if(!App.isPurchased() || !Utils.isTimeoutLockEnabled(mContext)) {
                        break;
                    }
                    if (preference instanceof TwoStatePreference) {
                        ((TwoStatePreference) preference).setChecked((boolean) newValue);
                    }
                    break;
                case Settings.DOUBLE_TAP_TO_LOCK_IS_SECURE:
                    if(!App.isPurchased() || !Utils.isSecureLockEnabled(mContext)) {
                        break;
                    }
                    if (preference instanceof TwoStatePreference) {
                        ((TwoStatePreference) preference).setChecked((boolean) newValue);
                    }
                    break;
            }
            return false;
        }

        @Override
        public boolean onPreferenceClick(Preference preference) {
            switch (preference.getKey()) {
                case SMARTSPACE_SETTINGS:
                    SmartspaceController.get(mContext).cZ();
                    return true;
                case Settings.RESET_APP_NAMES:
                    if(!App.isPurchased()){
                        App.openPurchaseActivity(getActivity());
                    } else {
                        new ResetAppNamesDialog().show(getFragmentManager(), preference.getKey());
                    }
                    return true;
                case Settings.RESET_APP_VISIBILITY:
                    if(!App.isPurchased()){
                        App.openPurchaseActivity(getActivity());
                    } else {
                        new ResetAppVisibilityDialog().show(getFragmentManager(), preference.getKey());
                    }
                    return true;
                case Settings.RESET_APP_LOCK:
                    if(!App.isPurchased()){
                        App.openPurchaseActivity(getActivity());
                    } else {
                        new ResetAppSecurityDialog().show(getFragmentManager(), preference.getKey());
                    }
                    return true;
                case Settings.RESET_APP_ICONS:
                    if(!App.isPurchased()){
                        App.openPurchaseActivity(getActivity());
                    } else {
                        new ResetAppIconsDialog().show(getFragmentManager(), preference.getKey());
                    }
                    return true;
                case Settings.RESTART_PREFERENCE:
                    Utils.restart(mContext);
                    return true;
                case Settings.CHANGE_DEFAULT_PREFERENCE:
                    Utils.setDefaultLauncher(getActivity());
                    return true;
                case ENABLE_MINUS_ONE_PREF:
                    if (Utils.isBridgeInstalled(getActivity())) {
                        return true;
                    }
                    Utils.checkBridge(getActivity());
                    return true;
                case Settings.BOTTOM_SEARCH_BAR_KEY:
                case Settings.EXTRA_BOTTOM_PADDING:
                case Settings.TOP_SEARCH_BAR_KEY:
                case Settings.PHYSICAL_ANIMATION_KEY:
                case Settings.TRANSPARENT_NAVIGATION_BAR:
                case Settings.FORCE_COLORED_G_ICON:
                case Settings.DARK_BOTTOM_SEARCH_BAR:
                case Settings.DARK_TOP_SEARCH_BAR:
                case Settings.LABEL_HIDDEN_ON_DESKTOP:
                case Settings.LABEL_HIDDEN_ON_ALL_APPS:
                case Settings.QSB_VOICE_ICON:
                case Settings.QSB_ASSISTANT_ICON:
                case Settings.BLACK_COLORS:
                case Settings.SHOW_CARET:
                case Settings.ALLOW_TWO_LINE_LABELS:
                case Settings.GENERATE_ADAPTIVE_ICONS:
                case Settings.GENERATED_ADAPTIVE_BACKGROUND:
                case Settings.GRID_COLUMNS:
                case Settings.GRID_ROWS:
                case Settings.HOTSEAT_ICONS:
                case Settings.ICON_SIZE:
                case Settings.ICON_TEXT_SIZE:
                case Settings.HOTSEAT_BACKGROUND:
                case Settings.DATE_FORMAT:
                case Settings.SEARCH_PROVIDER:
                case THEME_OVERRIDE_KEY:
                case KEY_PREFERENCE:
                case Settings.ONE_FINGER_DOWN:
                case Settings.TWO_FINGER_DOWN:
                case Settings.HOME_ACTION:
                case Settings.CARET_LONG_PRESS:
                case Settings.LOCK_DESKTOP_KEY:
                case Settings.SHORTCUT_UNLOCKED_WIDGETS:
                case Settings.SHORTCUT_UNLOCKED_EDIT:
                case Settings.SHORTCUT_UNLOCKED_UNINSTALL:
                case Settings.SHORTCUT_LOCKED_EDIT:
                case Settings.SHORTCUT_LOCKED_UNINSTALL:
                case Settings.TOP_SEARCH_APPS_KEY:
                    if(!App.isPurchased()){
                        App.openPurchaseActivity(getActivity());
                    }
                    return true;
                case Settings.DOUBLE_TAP_TO_LOCK:
                    if(!App.isPurchased()){
                        App.openPurchaseActivity(getActivity());
                    }
                    Utils.enableTimeoutLock(mContext);
                    return true;
                case Settings.DOUBLE_TAP_TO_LOCK_IS_SECURE:
                    if(!App.isPurchased()){
                        App.openPurchaseActivity(getActivity());
                    }
                    Utils.enableSecureLock(mContext);
                    return true;
            }
            return false;
        }
    }

    public static class OpenSourceLicensesFragment extends DialogFragment {
        public Dialog onCreateDialog(Bundle bundle) {
            WebView view = new WebView(getActivity());
            view.setWebViewClient(new WebViewClient());
            view.getSettings().setBuiltInZoomControls(true);
            view.loadUrl("file:///android_asset/license.html");
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.pref_open_source_licenses_title)
                    .setView(view)
                    .create();
        }
    }

    public static class SuggestionConfirmationFragment extends DialogFragment implements DialogInterface.OnClickListener {
        public void onClick(final DialogInterface dialogInterface, final int n) {
            if (getTargetFragment() instanceof PreferenceFragment) {
                Preference preference = ((PreferenceFragment) getTargetFragment()).findPreference(SHOW_PREDICTIONS_PREF);
                if (preference instanceof TwoStatePreference) {
                    ((TwoStatePreference) preference).setChecked(false);
                }
            }
        }

        public Dialog onCreateDialog(final Bundle bundle) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.title_disable_suggestions_prompt)
                    .setMessage(R.string.msg_disable_suggestions_prompt)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.label_turn_off_suggestions, this).create();
        }
    }

    public static class ResetAppNamesDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.reset_app_names_title)
                    .setMessage(R.string.reset_app_names_description)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Utilities.getCustomAppNamePrefs(getActivity()).edit().clear().apply();
                            Utils.reload(getActivity());
                        }
                    })
                    .create();
        }
    }

    public static class ResetAppVisibilityDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.reset_app_visibility_title)
                    .setMessage(R.string.reset_app_visibility_description)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            CustomAppFilter.resetAppFilter(getActivity());
                            Utils.reload(getActivity());
                        }
                    })
                    .create();
        }
    }

    public static class ResetAppIconsDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.reset_app_icons_title)
                    .setMessage(R.string.reset_app_icons_description)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            final ProgressDialog applyingDialog = ProgressDialog.show(getActivity(),
                                    null /* title */,
                                    getActivity().getString(R.string.state_loading),
                                    true /* indeterminate */,
                                    false /* cancelable */);

                            Settings.clearCustomIcons(getActivity());
                            CustomIconUtils.setCurrentPack(getActivity(), "");
                            CustomIconUtils.applyIconPackAsync(getActivity());

                            new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    applyingDialog.cancel();
                                }
                            }, 1000);
                        }
                    })
                    .create();
        }
    }

    public static class ResetAppSecurityDialog extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.reset_app_security_title)
                    .setMessage(R.string.reset_app_security_description)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            AppLockHelper.resetAppLock(getActivity());
                            Utils.reload(getActivity());
                        }
                    })
                    .create();
        }
    }
}