package net.kdt.pojavlaunch;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.system.Os;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.kdt.pickafile.FileListView;
import com.kdt.pickafile.FileSelectedListener;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import java.util.List;
import net.kdt.pojavlaunch.authenticator.microsoft.MicrosoftAuthTask;
import net.kdt.pojavlaunch.authenticator.mojang.InvalidateTokenTask;
import net.kdt.pojavlaunch.authenticator.mojang.LoginListener;
import net.kdt.pojavlaunch.authenticator.mojang.LoginTask;
import net.kdt.pojavlaunch.authenticator.mojang.RefreshListener;
import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.CustomControls;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.JREUtils;
import net.kdt.pojavlaunch.utils.LocaleUtils;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.Tools;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

public class PojavLoginActivity extends BaseActivity
// MineActivity
{
    private final Object mLockStoragePerm = new Object();
    private final Object mLockSelectJRE = new Object();
    
    private EditText edit2, edit3;
    private final int REQUEST_STORAGE_REQUEST_CODE = 1;
    private CheckBox sRemember, sOffline;
    private TextView startupTextView;
    private SharedPreferences firstLaunchPrefs;
    private MinecraftAccount mProfile = null;
    
    private static boolean isSkipInit = false;

    public static final String PREF_IS_INSTALLED_JAVARUNTIME = "isJavaRuntimeInstalled";
    
    public Uri treeUri;
    public boolean StorageAllowed;
    public boolean isInitCalled2;
    // move to utils??
    //public final class FileUtil {

    private static final String PRIMARY_VOLUME_NAME = "primary";

    @Nullable
    public static String getFullPathFromTreeUri(@Nullable final Uri treeUri, Context con) {
        if (treeUri == null) return null;
        String volumePath = getVolumePath(getVolumeIdFromTreeUri(treeUri),con);
        if (volumePath == null) return File.separator;
        if (volumePath.endsWith(File.separator))
            volumePath = volumePath.substring(0, volumePath.length() - 1);

        String documentPath = getDocumentPathFromTreeUri(treeUri);
        if (documentPath.endsWith(File.separator))
            documentPath = documentPath.substring(0, documentPath.length() - 1);

        if (documentPath.length() > 0) {
            if (documentPath.startsWith(File.separator))
                return volumePath + documentPath;
            else
                return volumePath + File.separator + documentPath;
        }
        else return volumePath;
    }


    private static String getVolumePath(final String volumeId, Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            return null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            return getVolumePathForAndroid11AndAbove(volumeId, context);
        else
            return getVolumePathBeforeAndroid11(volumeId, context);
    }


    private static String getVolumePathBeforeAndroid11(final String volumeId, Context context){
        try {
            StorageManager mStorageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            Class<?> storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            Method getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
            Method getUuid = storageVolumeClazz.getMethod("getUuid");
            Method getPath = storageVolumeClazz.getMethod("getPath");
            Method isPrimary = storageVolumeClazz.getMethod("isPrimary");
            Object result = getVolumeList.invoke(mStorageManager);

            final int length = Array.getLength(result);
            for (int i = 0; i < length; i++) {
                Object storageVolumeElement = Array.get(result, i);
                String uuid = (String) getUuid.invoke(storageVolumeElement);
                Boolean primary = (Boolean) isPrimary.invoke(storageVolumeElement);

                if (primary && PRIMARY_VOLUME_NAME.equals(volumeId))    // primary volume?
                    return (String) getPath.invoke(storageVolumeElement);

                if (uuid != null && uuid.equals(volumeId))    // other volumes?
                    return (String) getPath.invoke(storageVolumeElement);
            }
            // not found.
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.R)
    private static String getVolumePathForAndroid11AndAbove(final String volumeId, Context context) {
        try {
            StorageManager mStorageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            List<StorageVolume> storageVolumes = mStorageManager.getStorageVolumes();
            for (StorageVolume storageVolume : storageVolumes) {
                // primary volume?
                if (storageVolume.isPrimary() && PRIMARY_VOLUME_NAME.equals(volumeId))
                    return storageVolume.getDirectory().getPath();

                // other volumes?
                String uuid = storageVolume.getUuid();
                if (uuid != null && uuid.equals(volumeId))
                    return storageVolume.getDirectory().getPath();

            }
            // not found.
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static String getVolumeIdFromTreeUri(final Uri treeUri) {
        final String docId = DocumentsContract.getTreeDocumentId(treeUri);
        final String[] split = docId.split(":");
        if (split.length > 0) return split[0];
        else return null;
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private static String getDocumentPathFromTreeUri(final Uri treeUri) {
        final String docId = DocumentsContract.getTreeDocumentId(treeUri);
        final String[] split = docId.split(":");
        if ((split.length >= 2) && (split[1] != null)) return split[1];
        else return File.separator;
    }
    //}
    //
    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState); // false;

        Tools.updateWindowSize(this);
        
        firstLaunchPrefs = getSharedPreferences("pojav_extract", MODE_PRIVATE);
        new InitTask().execute(isSkipInit);
    }

    private class InitTask extends AsyncTask<Boolean, String, Integer>{
        private AlertDialog startAle;
        private ProgressBar progress;

        @Override
        protected void onPreExecute() {
            LinearLayout startScr = new LinearLayout(PojavLoginActivity.this);
            LayoutInflater.from(PojavLoginActivity.this).inflate(R.layout.start_screen, startScr);

            FontChanger.changeFonts(startScr);

            progress = (ProgressBar) startScr.findViewById(R.id.startscreenProgress);
            startupTextView = (TextView) startScr.findViewById(R.id.startscreen_text);


            AlertDialog.Builder startDlg = new AlertDialog.Builder(PojavLoginActivity.this, R.style.AppTheme);
            startDlg.setView(startScr);
            startDlg.setCancelable(false);

            startAle = startDlg.create();
            startAle.show();
            startAle.getWindow().setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            );
        }
        
        private int revokeCount = -1;
        
        protected Integer tryInitMain() {
            int InitCode = 0;
            try {
                initMain();
                } catch (Throwable th) {
                    Tools.showError(PojavLoginActivity.this, th, true);
                    InitCode = 1;
                }
            return InitCode;
        }

        @Override
        protected Integer doInBackground(Boolean[] params) {
            // If trigger a quick restart
            if (params[0]) return 0;
            
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {}

            publishProgress("visible");

            while (Build.VERSION.SDK_INT >= 23 && !isStorageAllowed()){
                try {
                    revokeCount++;
                    if (revokeCount >= 3) {
                        Toast.makeText(PojavLoginActivity.this, R.string.toast_permission_denied, Toast.LENGTH_LONG).show();
                        finish();
                        return 0;
                    }
                    
                    requestStoragePermission();
                    
                    synchronized (mLockStoragePerm) {
                        mLockStoragePerm.wait();
                    }
                } catch (InterruptedException e) {}
            }
            
            if (Build.VERSION.SDK_INT < 23 && !StorageAllowed) {
                requestSdCardPermission();
            }
            
            while ((isStorageAllowed() || StorageAllowed) && !isInitCalled2) {
                isInitCalled2 = true;
                tryInitMain();
                return InitCode;
            }
            return 0;
        }

        @Override
        protected void onProgressUpdate(String... obj)
        {
            if (obj[0].equals("visible")) {
                progress.setVisibility(View.VISIBLE);
            }
        }

        @Override
        protected void onPostExecute(Integer obj) {
            startAle.dismiss();
            if (obj == 0) uiInit();
        }
    }
    
    private void uiInit() {
        setContentView(R.layout.launcher_login_v3);

        Spinner spinnerChgLang = findViewById(R.id.login_spinner_language);

        String defaultLang = LocaleUtils.DEFAULT_LOCALE.getDisplayName();
        SpannableString defaultLangChar = new SpannableString(defaultLang);
        defaultLangChar.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, defaultLang.length(), 0);
        
        final ArrayAdapter<DisplayableLocale> langAdapter = new ArrayAdapter<DisplayableLocale>(this, android.R.layout.simple_spinner_item);
        langAdapter.add(new DisplayableLocale(LocaleUtils.DEFAULT_LOCALE, defaultLangChar));
        langAdapter.add(new DisplayableLocale(Locale.ENGLISH));
        
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open("language_list.txt")));
            String line;
            while ((line = reader.readLine()) != null) {
                File currFile = new File("/" + line);
                // System.out.println(currFile.getAbsolutePath());
                if (currFile.getAbsolutePath().contains("/values-") || currFile.getName().startsWith("values-")) {
                    // TODO use regex(?)
                    langAdapter.add(new DisplayableLocale(currFile.getName().replace("values-", "").replace("-r", "-")));
                }
            }
        } catch (IOException e) {
            Tools.showError(this, e);
        }
        
        langAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        
        int selectedLang = 0;
        for (int i = 0; i < langAdapter.getCount(); i++) {
            if (Locale.getDefault().getDisplayLanguage().equals(langAdapter.getItem(i).mLocale.getDisplayLanguage())) {
                selectedLang = i;
                break;
            }
        }
        
        spinnerChgLang.setAdapter(langAdapter);
        spinnerChgLang.setSelection(selectedLang);
        spinnerChgLang.setOnItemSelectedListener(new Spinner.OnItemSelectedListener(){
            private boolean isInitCalled;
            @Override
            public void onItemSelected(AdapterView<?> adapter, View view, int position, long id) {
                if (!isInitCalled) {
                    isInitCalled = true;
                    return;
                }
                
                Locale locale;
                if (position == 0) {
                    locale = LocaleUtils.DEFAULT_LOCALE;
                } else if (position == 1) {
                    locale = Locale.ENGLISH;
                } else {
                    locale = langAdapter.getItem(position).mLocale;
                }
                
                LauncherPreferences.PREF_LANGUAGE = locale.getLanguage();
                LauncherPreferences.DEFAULT_PREF.edit().putString("language", LauncherPreferences.PREF_LANGUAGE).apply();
                
                // Restart to apply language change
                finish();
                startActivity(getIntent());
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> adapter) {}
        });
            
        edit2 = (EditText) findViewById(R.id.login_edit_email);
        edit3 = (EditText) findViewById(R.id.login_edit_password);
        
        sRemember = findViewById(R.id.login_switch_remember);
        sOffline  = findViewById(R.id.login_switch_offline);
        sOffline.setOnCheckedChangeListener(new OnCheckedChangeListener(){

                @Override
                public void onCheckedChanged(CompoundButton p1, boolean p2) {
                    // May delete later
                    edit3.setEnabled(!p2);
                }
            });
            
        isSkipInit = true;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        Tools.updateWindowSize(this);
        
        // Clear current profile
        PojavProfile.setCurrentProfile(this, null);
    }

    private boolean isJavaRuntimeInstalled(AssetManager am) {
        boolean prefValue = firstLaunchPrefs.getBoolean(PREF_IS_INSTALLED_JAVARUNTIME, false);
        try {
            return prefValue && (
                am.open("components/jre/bin-" + Tools.CURRENT_ARCHITECTURE.split("/")[0] + ".tar.xz") == null ||
                Tools.read(new FileInputStream(Tools.DIR_HOME_JRE+"/version")).equals(Tools.read(am.open("components/jre/version"))));
        } catch(IOException e) {
            Log.e("JVMCtl","failed to read file",e);
            return prefValue;
        }
    }
   
    private void unpackComponent(AssetManager am, String component) throws IOException {
        File versionFile = new File(Tools.DIR_GAME_HOME + "/" + component + "/version");
        InputStream is = am.open("components/" + component + "/version");
        if(!versionFile.exists()) {
            if (versionFile.getParentFile().exists() && versionFile.getParentFile().isDirectory()) {
                FileUtils.deleteDirectory(versionFile.getParentFile());
            }
            versionFile.getParentFile().mkdir();
            
            Log.i("UnpackPrep", component + ": Pack was installed manually, or does not exist, unpacking new...");
            String[] fileList = am.list("components/" + component);
            for(String s : fileList) {
                Tools.copyAssetFile(this, "components/" + component + "/" + s, Tools.DIR_GAME_HOME + "/" + component, true);
            }
        } else {
            FileInputStream fis = new FileInputStream(versionFile);
            String release1 = Tools.read(is);
            String release2 = Tools.read(fis);
            if (!release1.equals(release2)) {
                if (versionFile.getParentFile().exists() && versionFile.getParentFile().isDirectory()) {
                    FileUtils.deleteDirectory(versionFile.getParentFile());
                }
                versionFile.getParentFile().mkdir();
                
                String[] fileList = am.list("components/" + component);
                for (String s : fileList) {
                    Tools.copyAssetFile(this, "components/" + component + "/" + s, Tools.DIR_GAME_HOME + "/" + component, true);
                }
            } else {
                Log.i("UnpackPrep", component + ": Pack is up-to-date with the launcher, continuing...");
            }
        }
    }
    public static void disableSplash(String dir) {
        mkdirs(dir + "/config");
        File forgeSplashFile = new File(dir, "config/splash.properties");
        String forgeSplashContent = "enabled=true";
        try {
            if (forgeSplashFile.exists()) {
                forgeSplashContent = Tools.read(forgeSplashFile.getAbsolutePath());
            }
            if (forgeSplashContent.contains("enabled=true")) {
                Tools.write(forgeSplashFile.getAbsolutePath(),
                        forgeSplashContent.replace("enabled=true", "enabled=false"));
            }
        } catch (IOException e) {
            Log.w(Tools.APP_NAME, "Could not disable Forge 1.12.2 and below splash screen!", e);
        }
    }
    private void initMain() throws Throwable {
        mkdirs(Tools.DIR_ACCOUNT_NEW);
        PojavMigrator.migrateAccountData(this);
        
        mkdirs(Tools.DIR_GAME_HOME);
        mkdirs(Tools.DIR_GAME_HOME + "/lwjgl3");
        mkdirs(Tools.DIR_GAME_HOME + "/config");
        if (!PojavMigrator.migrateGameDir()) {
            mkdirs(Tools.DIR_GAME_NEW);
            mkdirs(Tools.DIR_GAME_NEW + "/mods");
            mkdirs(Tools.DIR_HOME_VERSION);
            mkdirs(Tools.DIR_HOME_LIBRARY);
        }

        mkdirs(Tools.CTRLMAP_PATH);
        
        try {
            new CustomControls(this).save(Tools.CTRLDEF_FILE);

            Tools.copyAssetFile(this, "components/security/pro-grade.jar", Tools.DIR_DATA, true);
            Tools.copyAssetFile(this, "components/security/java_sandbox.policy", Tools.DIR_DATA, true);
            Tools.copyAssetFile(this, "options.txt", Tools.DIR_GAME_NEW, false);
            // TODO: Remove after implement.
            Tools.copyAssetFile(this, "launcher_profiles.json", Tools.DIR_GAME_NEW, false);

            AssetManager am = this.getAssets();
            
            unpackComponent(am, "caciocavallo");
            unpackComponent(am, "lwjgl3");
            if (!isJavaRuntimeInstalled(am)) {
                if(!installRuntimeAutomatically(am)) {
                    File jreTarFile = selectJreTarFile();
                    uncompressTarXZ(jreTarFile, new File(Tools.DIR_HOME_JRE));
                } else {
                    Tools.copyAssetFile(this, "components/jre/version", Tools.DIR_HOME_JRE + "/","version", true);
                }
                firstLaunchPrefs.edit().putBoolean(PREF_IS_INSTALLED_JAVARUNTIME, true).commit();
            }
            
            JREUtils.relocateLibPath(this);

            File ftIn = new File(Tools.DIR_HOME_JRE, Tools.DIRNAME_HOME_JRE + "/libfreetype.so.6");
            File ftOut = new File(Tools.DIR_HOME_JRE, Tools.DIRNAME_HOME_JRE + "/libfreetype.so");
            if (ftIn.exists() && (!ftOut.exists() || ftIn.length() != ftOut.length())) {
                ftIn.renameTo(ftOut);
            }
            
            // Refresh libraries
            copyDummyNativeLib("libawt_xawt.so");
            // copyDummyNativeLib("libfontconfig.so");
        }
        catch(Throwable e){
            Tools.showError(this, e);
        }
    }
    
    private boolean installRuntimeAutomatically(AssetManager am) {
        try {
            am.open("components/jre/version");
        } catch (IOException e) {
            Log.e("JREAuto", "JRE was not included on this APK.", e);
            return false;
        }
        
        File rtUniversal = new File(Tools.DIR_HOME_JRE+"/universal.tar.xz");
        File rtPlatformDependent = new File(Tools.DIR_HOME_JRE+"/cust-bin.tar.xz");
        if(!new File(Tools.DIR_HOME_JRE).exists()) new File(Tools.DIR_HOME_JRE).mkdirs(); else {
            //SANITY: remove the existing files
            for (File f : new File(Tools.DIR_HOME_JRE).listFiles()) {
                if (f.isDirectory()){
                    try {
                        FileUtils.deleteDirectory(f);
                    } catch(IOException e1) {
                        Log.e("JREAuto","da fuq is wrong wit ur device? n2",e1);
                    }
                } else{
                    f.delete();
                }
            }
        }
        InputStream is;
        FileOutputStream os;
        try {
            is = am.open("components/jre/universal.tar.xz");
            os = new FileOutputStream(rtUniversal);
            IOUtils.copy(is,os);
            is.close();
            os.close();
            uncompressTarXZ(rtUniversal, new File(Tools.DIR_HOME_JRE));
        } catch (IOException e){
            Log.e("JREAuto","Failed to unpack universal. Custom embedded-less build?",e);
            return false;
        }
        try {
            is = am.open("components/jre/bin-" + Tools.CURRENT_ARCHITECTURE.split("/")[0] + ".tar.xz");
            os = new FileOutputStream(rtPlatformDependent);
            IOUtils.copy(is, os);
            is.close();
            os.close();
            uncompressTarXZ(rtPlatformDependent, new File(Tools.DIR_HOME_JRE));
        } catch (IOException e) {
            // Something's very wrong, or user's using an unsupported arch (MIPS phone? ARMv6 phone?),
            // in both cases, redirecting to manual install, and removing the universal stuff
            for (File f : new File(Tools.DIR_HOME_JRE).listFiles()) {
                if (f.isDirectory()){
                    try {
                        FileUtils.deleteDirectory(f);
                    } catch(IOException e1) {
                        Log.e("JREAuto","da fuq is wrong wit ur device?",e1);
                    }
                } else{
                    f.delete();
                }
            }
            return false;
        }
        return true;
    }
    private void copyDummyNativeLib(String name) throws Throwable {
        File fileLib = new File(Tools.DIR_HOME_JRE, Tools.DIRNAME_HOME_JRE + "/" + name);
        fileLib.delete();
        FileInputStream is = new FileInputStream(new File(getApplicationInfo().nativeLibraryDir, name));
        FileOutputStream os = new FileOutputStream(fileLib);
        IOUtils.copy(is, os);
        is.close();
        os.close();
    }
    
    private File selectJreTarFile() throws InterruptedException {
        final StringBuilder selectedFile = new StringBuilder();
        
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(PojavLoginActivity.this);
                builder.setTitle(getString(R.string.alerttitle_install_jre, Tools.CURRENT_ARCHITECTURE));
                builder.setCancelable(false);

                final AlertDialog dialog = builder.create();
                FileListView flv = new FileListView(dialog, "tar.xz");
                flv.setFileSelectedListener(new FileSelectedListener(){

                        @Override
                        public void onFileSelected(File file, String path) {
                            selectedFile.append(path);
                            dialog.dismiss();

                            synchronized (mLockSelectJRE) {
                                mLockSelectJRE.notifyAll();
                            }

                        }
                    });
                dialog.setView(flv);
                dialog.show();
            }
        });
        
        synchronized (mLockSelectJRE) {
            mLockSelectJRE.wait();
        }
        
        return new File(selectedFile.toString());
    }

    private void uncompressTarXZ(final File tarFile, final File dest) throws IOException {

        dest.mkdirs();
        TarArchiveInputStream tarIn = null;

        tarIn = new TarArchiveInputStream(
            new XZCompressorInputStream(
                new BufferedInputStream(
                    new FileInputStream(tarFile)
                )
            )
        );

        TarArchiveEntry tarEntry = tarIn.getNextTarEntry();
        // tarIn is a TarArchiveInputStream
        while (tarEntry != null) {
            /*
             * Unpacking very small files in short time cause
             * application to ANR or out of memory, so delay
             * a little if size is below than 20kb (20480 bytes)
             */
            if (tarEntry.getSize() <= 20480) {
                try {
                    // 40 small files per second
                    Thread.sleep(25);
                } catch (InterruptedException e) {}
            }
            final String tarEntryName = tarEntry.getName();
            runOnUiThread(new Runnable(){
                @SuppressLint("StringFormatInvalid")
                @Override
                public void run() {
                    startupTextView.setText(getString(R.string.global_unpacking, tarEntryName));
                }
            });
            // publishProgress(null, "Unpacking " + tarEntry.getName());
            File destPath = new File(dest, tarEntry.getName()); 
            if (tarEntry.isSymbolicLink()) {
                destPath.getParentFile().mkdirs();
                try {
                    // android.system.Os
                    // Libcore one support all Android versions
                    Os.symlink(tarEntry.getName(), tarEntry.getLinkName());
                } catch (Throwable e) {
                    e.printStackTrace();
                }

            } else if (tarEntry.isDirectory()) {
                destPath.mkdirs();
                destPath.setExecutable(true);
            } else if (!destPath.exists() || destPath.length() != tarEntry.getSize()) {
                destPath.getParentFile().mkdirs();
                destPath.createNewFile();
                
                FileOutputStream os = new FileOutputStream(destPath);
                IOUtils.copy(tarIn, os);
                os.close();

            }
            tarEntry = tarIn.getNextTarEntry();
        }
        tarIn.close();
    }
    
    private static boolean mkdirs(String path)
    {
        File file = new File(path);
        // check necessary???
        if(file.getParentFile().exists())
             return file.mkdir();
        else return file.mkdirs();
    }

    
    public void loginMicrosoft(View view) {
        CustomTabs.openTab(this,
            "https://login.live.com/oauth20_authorize.srf" + 
            "?client_id=00000000402b5328" +
            "&response_type=code" +
            "&scope=service%3A%3Auser.auth.xboxlive.com%3A%3AMBI_SSL" +
            "&redirect_url=https%3A%2F%2Flogin.live.com%2Foauth20_desktop.srf");
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        
        Uri data = intent.getData();
        //Log.i("MicroAuth", data.toString());
        if (data != null && data.getScheme().equals("ms-xal-00000000402b5328") && data.getHost().equals("auth")) {
            String error = data.getQueryParameter("error");
            String error_description = data.getQueryParameter("error_description");
            if (error != null) {
                // "The user has denied access to the scope requested by the client application": user pressed Cancel button, skip it
                if (!error_description.startsWith("The user has denied access to the scope requested by the client application")) {
                    Toast.makeText(this, "Error: " + error + ": " + error_description, Toast.LENGTH_LONG).show();
                }
            } else {
                String code = data.getQueryParameter("code");
                new MicrosoftAuthTask(this, new RefreshListener(){
                        @Override
                        public void onFailed(Throwable e) {
                            Tools.showError(PojavLoginActivity.this, e);
                        }

                        @Override
                        public void onSuccess(MinecraftAccount b) {
                            mProfile = b;
                            playProfile(false);
                        }
                    }).execute("false", code);
                // Toast.makeText(this, "Logged in to Microsoft account, but NYI", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private View getViewFromList(int pos, ListView listView) {
        final int firstItemPos = listView.getFirstVisiblePosition();
        final int lastItemPos = firstItemPos + listView.getChildCount() - 1;

        if (pos < firstItemPos || pos > lastItemPos ) {
            return listView.getAdapter().getView(pos, null, listView);
        } else {
            final int childIndex = pos - firstItemPos;
            return listView.getChildAt(childIndex);
        }
    }
    
    public void loginSavedAcc(View view) {
        String[] accountArr = new File(Tools.DIR_ACCOUNT_NEW).list();
        if(accountArr.length == 0){
           showNoAccountDialog();
           return;
        }

        final Dialog accountDialog = new Dialog(PojavLoginActivity.this);

        accountDialog.setContentView(R.layout.simple_account_list_holder);

        LinearLayout accountListLayout = accountDialog.findViewById(R.id.accountListLayout);
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);

        for (int accountIndex = 0; accountIndex < accountArr.length; accountIndex++) {
            String s = accountArr[accountIndex];
            View child = inflater.inflate(R.layout.simple_account_list_item, null);
            TextView accountName = child.findViewById(R.id.accountitem_text_name);
            ImageButton removeButton = child.findViewById(R.id.accountitem_button_remove);
            ImageView imageView = child.findViewById(R.id.account_head);

            String accNameStr = s.substring(0, s.length() - 5);
            String skinFaceBase64 = MinecraftAccount.load(accNameStr).skinFaceBase64;
            if (skinFaceBase64 != null) {
                byte[] faceIconBytes = Base64.decode(skinFaceBase64, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(faceIconBytes, 0, faceIconBytes.length);

                imageView.setImageDrawable(new BitmapDrawable(getResources(),
                        bitmap));
            }
            accountName.setText(accNameStr);

            accountListLayout.addView(child);

            accountName.setOnClickListener(new View.OnClickListener() {
                final String selectedAccName = accountName.getText().toString();
                @Override
                public void onClick(View v) {
                    try {
                        RefreshListener authListener = new RefreshListener(){
                            @Override
                            public void onFailed(Throwable e) {
                                Tools.showError(PojavLoginActivity.this, e);
                            }

                            @Override
                            public void onSuccess(MinecraftAccount out) {
                                accountDialog.dismiss();
                                mProfile = out;
                                playProfile(true);
                            }
                        };

                        MinecraftAccount acc = MinecraftAccount.load(selectedAccName);
                        if (acc.isMicrosoft){
                            new MicrosoftAuthTask(PojavLoginActivity.this, authListener)
                                    .execute("true", acc.msaRefreshToken);
                        } else if (acc.accessToken.length() >= 5) {
                            PojavProfile.updateTokens(PojavLoginActivity.this, selectedAccName, authListener);
                        } else {
                            accountDialog.dismiss();
                            PojavProfile.launch(PojavLoginActivity.this, selectedAccName);
                        }
                    } catch (Exception e) {
                        Tools.showError(PojavLoginActivity.this, e);
                    }
                }
            });

            final int accountIndex_final = accountIndex;
            removeButton.setOnClickListener(new View.OnClickListener() {
                final String selectedAccName = accountName.getText().toString();
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder builder2 = new AlertDialog.Builder(PojavLoginActivity.this);
                    builder2.setTitle(selectedAccName);
                    builder2.setMessage(R.string.warning_remove_account);
                    builder2.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener(){

                        @Override
                        public void onClick(DialogInterface p1, int p2) {
                            new InvalidateTokenTask(PojavLoginActivity.this).execute(selectedAccName);
                            accountListLayout.removeViewsInLayout(accountIndex_final, 1);

                            if (accountListLayout.getChildCount() == 0) {
                                accountDialog.dismiss(); //No need to keep it, since there is no account
                                return;
                            }
                            //Refreshes the layout with the same settings so it take the missing child into account.
                            accountListLayout.setLayoutParams(accountListLayout.getLayoutParams());

                        }
                    });
                    builder2.setNegativeButton(android.R.string.cancel, null);
                    builder2.show();
                }
            });

        }
        accountDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        accountDialog.show();
    }
    
    private MinecraftAccount loginOffline() {
        new File(Tools.DIR_ACCOUNT_OLD).mkdir();
        
        String text = edit2.getText().toString();
        if (text.isEmpty()) {
            edit2.setError(getString(R.string.global_error_field_empty));
        } else if (text.length() <= 2) {
            edit2.setError(getString(R.string.login_error_short_username));
        } else if (new File(Tools.DIR_ACCOUNT_NEW + "/" + text + ".json").exists()) {
            edit2.setError(getString(R.string.login_error_exist_username));
        } else if (!edit3.getText().toString().isEmpty()) {
            edit3.setError(getString(R.string.login_error_offline_password));
        } else {
            MinecraftAccount builder = new MinecraftAccount();
            builder.isMicrosoft = false;
            builder.username = text;
            
            return builder;
        }
        return null;
    }
    

    public void loginMC(final View v)
    {
        
        if (sOffline.isChecked()) {
            mProfile = loginOffline();
            playProfile(false);
        } else {
            ProgressBar prb = findViewById(R.id.launcherAccProgress);
            new LoginTask().setLoginListener(new LoginListener(){


                    @Override
                    public void onBeforeLogin() {
                        v.setEnabled(false);
                        prb.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onLoginDone(String[] result) {
                        if(result[0].equals("ERROR")){
                            Tools.dialogOnUiThread(PojavLoginActivity.this,
                                getResources().getString(R.string.global_error), strArrToString(result));
                        } else{
                            MinecraftAccount builder = new MinecraftAccount();
                            builder.accessToken = result[1];
                            builder.clientToken = result[2];
                            builder.profileId = result[3];
                            builder.username = result[4];
                            builder.selectedVersion = "1.12.2";
                            builder.updateSkinFace();
                            mProfile = builder;
                        }
                        runOnUiThread(() -> {
                            v.setEnabled(true);
                            prb.setVisibility(View.GONE);
                            playProfile(false);
                        });
                    }
                }).execute(edit2.getText().toString(), edit3.getText().toString());
        }
    }
    
    private void playProfile(boolean notOnLogin) {
        if (mProfile != null) {
            try {
                String profileName = null;
                if (sRemember.isChecked() || notOnLogin) {
                    profileName = mProfile.save();
                }
                
                PojavProfile.launch(PojavLoginActivity.this, profileName == null ? mProfile : profileName);
            } catch (IOException e) {
                Tools.showError(this, e);
            }
        }
    }
    
    public static String strArrToString(String[] strArr)
    {
        String[] strArrEdit = strArr;
        strArrEdit[0] = "";
        
        String str = Arrays.toString(strArrEdit);
        str = str.substring(1, str.length() - 1).replace(",", "\n");
        
        return str;
    }
    //We are calling this method to check the permission status
    private boolean isStorageAllowed() {
        //Getting the permission status
        int result1 = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int result2 = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);


        //If permission is granted returning true
        return result1 == PackageManager.PERMISSION_GRANTED &&
            result2 == PackageManager.PERMISSION_GRANTED;
    }
    
    //Requesting permission
    private void requestStoragePermission()
    {
        ActivityCompat.requestPermissions(this, new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE_REQUEST_CODE);
    }

    // This method will be called when the user will tap on allow or deny
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_STORAGE_REQUEST_CODE){
            synchronized (mLockStoragePerm) {
                mLockStoragePerm.notifyAll();
            }
        }
    }
    
    // Request storage through SAF for Android 5.0-5.1
    private void requestSdCardPermission()
    {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, 42);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
    if (resultCode == RESULT_OK) {
        Uri treeUri = resultData.getData();
        getContentResolver().takePersistableUriPermission(treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION |
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        String GamePath = getFullPathFromTreeUri(treeUri,this); 
        // gives /storage/extSdCard/plaunch
        String treeGamePath = treeUri.getPath();
        // gives /tree/4411-1D0A:plaunch
        Tools.DIR_GAME_HOME = GamePath;
        StorageAllowed = true;
        if (Tools.ENABLE_DEV_FEATURES) {
            Toast.makeText(PojavLoginActivity.this, ("Picked absolute path: " + GamePath), Toast.LENGTH_LONG).show();
            Toast.makeText(PojavLoginActivity.this, ("Picked tree path: " + treeGamePath), Toast.LENGTH_LONG).show();
            Toast.makeText(PojavLoginActivity.this, ("DIR_GAME_HOME: " + Tools.DIR_GAME_HOME), Toast.LENGTH_LONG).show();
        }
    }
    }
    //When the user have no saved account, you can show him this dialog
    private void showNoAccountDialog(){
        AlertDialog.Builder builder = new AlertDialog.Builder(PojavLoginActivity.this);


        builder.setMessage(R.string.login_dialog_no_saved_account)
                .setTitle(R.string.login_title_no_saved_account)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    //Fucking nothing
                });


        AlertDialog dialog = builder.create();
        dialog.show();
    }

}
