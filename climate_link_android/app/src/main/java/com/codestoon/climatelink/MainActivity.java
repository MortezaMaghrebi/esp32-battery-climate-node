package com.codestoon.climatelink;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.*;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.*;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import org.json.JSONObject;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends AppCompatActivity implements BleClient.Listener {
    private BleClient ble;
    private LinearLayout devices;
    private TextView connection,temperature,humidity,deviceIdentity,deviceName,connectionBadge;
    private TextView heroTitle,heroCaption,sampleCaption,settingsNotice,deviceCount;
    private TextInputEditText ssid,password,url,token,sample,every;
    private CompoundButton clearPassword,clearToken,http;
    private MaterialButton save,run,refresh,scanButton,disconnectButton,goDevices;
    private ViewFlipper pages;
    private BottomNavigationView navigation;
    private final Map<String,DeviceRow> discovered=new LinkedHashMap<>();
    private boolean loading,configured,connected,scanActive,connecting,hasSample,updatingPreset;
    private String selectedAddress="";
    private final ActivityResultLauncher<String[]> permissions=registerForActivityResult(
        new ActivityResultContracts.RequestMultiplePermissions(),result->{
            boolean granted=true; for(String permission:requiredPermissions())
                granted &= ContextCompat.checkSelfPermission(this,permission)==PackageManager.PERMISSION_GRANTED;
            if(granted) startScan(); else error(getString(R.string.permission_error));
        });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); EdgeToEdge.enable(this); setContentView(R.layout.activity_main);
        View root=findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root,(v,insets)->{
            Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime=insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        devices=findViewById(R.id.device_list); connection=findViewById(R.id.connection);
        temperature=findViewById(R.id.temperature_value); humidity=findViewById(R.id.humidity_value);
        deviceIdentity=findViewById(R.id.device_identity); deviceName=findViewById(R.id.device_name);
        heroTitle=findViewById(R.id.hero_title);heroCaption=findViewById(R.id.hero_caption);
        sampleCaption=findViewById(R.id.sample_caption);settingsNotice=findViewById(R.id.settings_notice);
        deviceCount=findViewById(R.id.device_count);connectionBadge=findViewById(R.id.connection_badge);
        ssid=findViewById(R.id.ssid); password=findViewById(R.id.password);url=findViewById(R.id.url);token=findViewById(R.id.token);
        sample=findViewById(R.id.sample);every=findViewById(R.id.every);
        clearPassword=findViewById(R.id.clear_password);clearToken=findViewById(R.id.clear_token);http=findViewById(R.id.allow_http);
        save=findViewById(R.id.save_button);run=findViewById(R.id.run_button);refresh=findViewById(R.id.refresh_button);
        scanButton=findViewById(R.id.scan_button);disconnectButton=findViewById(R.id.disconnect_button);goDevices=findViewById(R.id.go_devices);
        pages=findViewById(R.id.pages);navigation=findViewById(R.id.bottom_nav);
        navigation.setOnItemSelectedListener(item->{
            int page=item.getItemId()==R.id.nav_devices?1:item.getItemId()==R.id.nav_settings?2:0;
            if(pages.getDisplayedChild()!=page) {
                pages.setDisplayedChild(page);
                View child=pages.getCurrentView();child.setAlpha(.35f);child.animate().alpha(1f).setDuration(160).start();
            }
            return true;
        });
        goDevices.setOnClickListener(v->navigation.setSelectedItemId(R.id.nav_devices));
        scanButton.setOnClickListener(v->{if(scanActive)ble.stopScan();else ensureScan();});
        disconnectButton.setOnClickListener(v->ble.disconnect());
        refresh.setOnClickListener(v->loadDevice());save.setOnClickListener(v->saveConfig());
        run.setOnClickListener(v->{
            if(loading)return;
            new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(R.string.run_title)
                .setMessage(R.string.run_message).setPositiveButton(R.string.start,(d,w)->{
                    loading=true;setConnected(connected);
                    ble.request(command("run"),reply->{loading=false;setConnected(connected);status(getString(R.string.cycle_started));});
                }).setNegativeButton(R.string.back,null).show();
        });
        for(TextInputEditText edit:new TextInputEditText[]{ssid,password,url,token,sample,every}) {
            edit.setSaveEnabled(false);
            if(Build.VERSION.SDK_INT>=26)edit.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }
        sample.setText(state==null?number(900):state.getString("sample",number(900)));
        every.setText(state==null?number(1):state.getString("every",number(1)));
        if(state!=null){ssid.setText(state.getString("ssid",""));url.setText(state.getString("url",""));http.setChecked(state.getBoolean("http"));}
        ChipGroup presets=findViewById(R.id.schedule_presets);
        presets.setOnCheckedStateChangeListener((group,ids)->{
            if(updatingPreset||ids.isEmpty())return;
            int id=ids.get(0);sample.setText(number(id==R.id.preset_60?60:id==R.id.preset_300?300:id==R.id.preset_900?900:3600));
        });
        TextWatcher timingWatcher=new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){updateSchedule();}
            public void afterTextChanged(Editable s){}
        };
        sample.addTextChangedListener(timingWatcher);every.addTextChangedListener(timingWatcher);updateSchedule();
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){
            @Override public void handleOnBackPressed(){if(pages.getDisplayedChild()!=0)navigation.setSelectedItemId(R.id.nav_overview);else finish();}
        });
        setConnected(false);ble=new BleClient(this,this);
        if(state!=null)navigation.setSelectedItemId(state.getInt("tab",R.id.nav_overview));
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private String number(int n){return String.format(Locale.forLanguageTag("fa"),"%d",n);}
    private void updateSchedule(){
        if(updatingPreset)return;
        updatingPreset=true;
        ChipGroup presets=findViewById(R.id.schedule_presets);
        try{
            int seconds=Integer.parseInt(value(sample)),count=Integer.parseInt(value(every));
            int preset=seconds==60?R.id.preset_60:seconds==300?R.id.preset_300:seconds==900?R.id.preset_900:seconds==3600?R.id.preset_3600:View.NO_ID;
            if(preset==View.NO_ID)presets.clearCheck();else presets.check(preset);
            ((TextView)findViewById(R.id.schedule_summary)).setText(getString(R.string.schedule_summary,number(seconds),number(count)));
        }catch(NumberFormatException e){presets.clearCheck();((TextView)findViewById(R.id.schedule_summary)).setText(R.string.schedule_empty);}
        updatingPreset=false;
    }
    private void updateConnectionUi(){
        int label=connected?R.string.online:connecting?R.string.connecting:scanActive?R.string.scanning:R.string.offline;
        connectionBadge.setText(label);
        connectionBadge.setBackgroundResource(connected?R.drawable.bg_chip:R.drawable.bg_offline);
        connectionBadge.setTextColor(ContextCompat.getColor(this,connected?R.color.cl_primary:R.color.cl_muted));
        disconnectButton.setVisibility(connected?View.VISIBLE:View.GONE);
        settingsNotice.setText(connected?R.string.settings_ready:R.string.settings_locked);
        goDevices.setText(connected?R.string.devices:R.string.connect_device);
        heroTitle.setText(connected?R.string.hero_connected:R.string.hero_title);
        if(!connected)heroCaption.setText(R.string.hero_caption);
        if(hasSample&&!connected)sampleCaption.setText(R.string.stale_sample);
        findViewById(R.id.operation_progress).setVisibility(loading?View.VISIBLE:View.INVISIBLE);
        for(Map.Entry<String,DeviceRow> entry:discovered.entrySet()) {
            DeviceRow row=entry.getValue();
            row.button.setEnabled(!connecting&&!loading);
            row.button.setText(connected&&entry.getKey().equals(selectedAddress)?R.string.settings:R.string.connect);
        }
    }
    @Override public void scanState(boolean active){
        scanActive=active;scanButton.setText(active?R.string.stop_scan:R.string.scan);
        scanButton.setIconResource(active?R.drawable.ic_bluetooth:R.drawable.ic_search);
        findViewById(R.id.scan_progress).setVisibility(active?View.VISIBLE:View.GONE);updateConnectionUi();
    }
    private final class DeviceRow {
        final MaterialButton button;final TextView signal;
        DeviceRow(BluetoothDevice device,String name){
            MaterialCardView card=new MaterialCardView(MainActivity.this);
            card.setRadius(dp(20));card.setCardElevation(0);card.setStrokeWidth(dp(1));
            card.setStrokeColor(ContextCompat.getColor(MainActivity.this,R.color.cl_outline));
            card.setCardBackgroundColor(ContextCompat.getColor(MainActivity.this,R.color.cl_surface));
            LinearLayout.LayoutParams margins=new LinearLayout.LayoutParams(-1,-2);margins.bottomMargin=dp(12);devices.addView(card,margins);
            LinearLayout content=new LinearLayout(MainActivity.this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(16),dp(18),dp(16));card.addView(content);
            TextView title=new TextView(MainActivity.this);title.setText(name);title.setTextSize(17);title.setTextColor(ContextCompat.getColor(MainActivity.this,R.color.cl_ink));content.addView(title);
            signal=new TextView(MainActivity.this);signal.setTextSize(12);signal.setTextColor(ContextCompat.getColor(MainActivity.this,R.color.cl_muted));content.addView(signal);
            TextView address=new TextView(MainActivity.this);address.setText(device.getAddress());address.setTextSize(11);address.setTextDirection(View.TEXT_DIRECTION_LTR);address.setGravity(Gravity.START);content.addView(address);
            button=new MaterialButton(MainActivity.this,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(R.string.connect);button.setIconResource(R.drawable.ic_bluetooth);button.setCornerRadius(dp(14));content.addView(button,new LinearLayout.LayoutParams(-1,dp(52)));
            button.setOnClickListener(v->{
                if(connected&&device.getAddress().equals(selectedAddress)){navigation.setSelectedItemId(R.id.nav_settings);return;}
                selectedAddress=device.getAddress();deviceName.setText(name);ble.connect(device);connecting=true;updateConnectionUi();
            });
        }
    }
    @Override protected void onSaveInstanceState(Bundle out){
        out.putInt("tab",navigation.getSelectedItemId());out.putString("ssid",value(ssid));out.putString("url",value(url));
        out.putString("sample",value(sample));out.putString("every",value(every));out.putBoolean("http",http.isChecked());
        // Password and token never enter saved instance state.
        super.onSaveInstanceState(out);
    }
    @Override protected void onRestoreInstanceState(Bundle state){
        super.onRestoreInstanceState(state);
        updateSchedule();
    }
    private String[] requiredPermissions() {
        return Build.VERSION.SDK_INT>=31?new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT}:
            new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }
    private void ensureScan() {
        for(String p:requiredPermissions()) if(ContextCompat.checkSelfPermission(this,p)!=PackageManager.PERMISSION_GRANTED) {
            permissions.launch(requiredPermissions()); return;
        }
        startScan();
    }
    private void startScan() {
        if(Build.VERSION.SDK_INT<31) {
            LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);
            if(!lm.isProviderEnabled(LocationManager.GPS_PROVIDER) && !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                error(getString(R.string.location_error)); return;
            }
        }
        devices.removeAllViews(); discovered.clear(); deviceCount.setText(R.string.empty_value); findViewById(R.id.empty_devices).setVisibility(View.VISIBLE); ble.scan();
    }
    private void setConnected(boolean connected) {
        this.connected=connected;
        refresh.setEnabled(connected && !loading); save.setEnabled(connected && configured && !loading); run.setEnabled(connected && configured && !loading); updateConnectionUi();
    }
    @Override public void status(String s) { connection.setText(s);connection.setContentDescription(s); }
    @Override public void error(String s) { loading=false; setConnected(connected); status(s); Snackbar.make(findViewById(R.id.main),s,Snackbar.LENGTH_LONG).setAnchorView(navigation).setTextMaxLines(4).show(); }
    @Override public void disconnected() { configured=false; loading=false; connecting=false; setConnected(false); }
    @SuppressLint("MissingPermission")
    @Override public void found(BluetoothDevice device,int rssi){
        String name=device.getName()==null?getString(R.string.app_name):device.getName();
        DeviceRow row=discovered.get(device.getAddress());
        if(row==null){row=new DeviceRow(device,name);discovered.put(device.getAddress(),row);}
        row.signal.setText(getString(R.string.device_rssi,number(rssi)));
        deviceCount.setText(getString(R.string.device_count,number(discovered.size())));
        findViewById(R.id.empty_devices).setVisibility(View.GONE);
    }
    @Override public void ready(){connecting=false;status(getString(R.string.ready_message));setConnected(true);loadDevice();}
    private JSONObject command(String name) {
        JSONObject o=new JSONObject(); try{o.put("cmd",name);}catch(Exception ignored){} return o;
    }
    private void loadDevice() {
        if(loading) return; loading=true; setConnected(true);
        ble.request(command("get_status"),r->{
            hasSample=true;
            temperature.setText(String.format(Locale.forLanguageTag("fa"),"%.1f",r.optDouble("temperature_c")));
            humidity.setText(String.format(Locale.forLanguageTag("fa"),"%.1f",r.optDouble("humidity_pct")));
            deviceIdentity.setText(r.optString("device_id")+"\n"+getString(R.string.firmware_label,r.optString("firmware")));
            heroCaption.setText(r.optString("device_id"));
            ((TextView)findViewById(R.id.queued_value)).setText(number(r.optInt("queued")));
            ((TextView)findViewById(R.id.failures_value)).setText(number(r.optInt("failures")));
            ((TextView)findViewById(R.id.dropped_value)).setText(number(r.optInt("dropped")));
            findViewById(R.id.telemetry_card).setVisibility(View.VISIBLE);
            boolean simulated=r.optBoolean("simulated",true);
            findViewById(R.id.simulation_badge).setVisibility(simulated?View.VISIBLE:View.GONE);
            sampleCaption.setText(simulated?R.string.prototype_note:R.string.last_sample);
        });
        ble.request(command("get_config"),r->{
            ssid.setText(r.optString("ssid")); url.setText(r.optString("server_url"));
            sample.setText(number(r.optInt("sample_s",900))); every.setText(number(r.optInt("upload_every",1)));
            http.setChecked(r.optBoolean("allow_http")); password.setText(""); token.setText("");
            clearPassword.setChecked(false); clearToken.setChecked(false);
            loading=false; configured=true; setConnected(true); status(getString(R.string.config_loaded));
        });
    }
    private static String value(EditText e) { return e.getText().toString(); }
    private void saveConfig() {
        try {
            int seconds=Integer.parseInt(value(sample)), count=Integer.parseInt(value(every));
            if(seconds<60 || seconds>86400 || count<1 || count>32) throw new IllegalArgumentException(getString(R.string.schedule_error));
            String network=value(ssid), endpoint=value(url).trim(), pass=value(password), secret=value(token);
            if(network.isEmpty() || network.getBytes(StandardCharsets.UTF_8).length>32) throw new IllegalArgumentException(getString(R.string.ssid_error));
            if(!pass.isEmpty() && (pass.getBytes(StandardCharsets.UTF_8).length<8 || pass.getBytes(StandardCharsets.UTF_8).length>63))
                throw new IllegalArgumentException(getString(R.string.password_error));
            URI uri=URI.create(endpoint);
            if(uri.getHost()==null || uri.getUserInfo()!=null || uri.getFragment()!=null ||
                !("https".equals(uri.getScheme()) || (http.isChecked() && "http".equals(uri.getScheme()))))
                throw new IllegalArgumentException(getString(R.string.url_error));
            if(endpoint.getBytes(StandardCharsets.UTF_8).length>192 || secret.getBytes(StandardCharsets.UTF_8).length>128 || secret.contains("\r") || secret.contains("\n"))
                throw new IllegalArgumentException(getString(R.string.length_error));
            if(clearPassword.isChecked() && !pass.isEmpty() || clearToken.isChecked() && !secret.isEmpty())
                throw new IllegalArgumentException(getString(R.string.clear_error));
            JSONObject request=command("set_config"); request.put("ssid",network); request.put("server_url",endpoint);
            request.put("sample_s",seconds); request.put("upload_every",count); request.put("allow_http",http.isChecked());
            if(clearPassword.isChecked()) request.put("password",""); else if(!pass.isEmpty()) request.put("password",pass);
            if(clearToken.isChecked()) request.put("token",""); else if(!secret.isEmpty()) request.put("token",secret);
            loading=true; setConnected(true);
            ble.request(request,r->{loading=false; password.setText(""); token.setText(""); clearPassword.setChecked(false); clearToken.setChecked(false);
                setConnected(true); status(getString(R.string.config_saved)); Snackbar.make(findViewById(R.id.main),R.string.config_saved,Snackbar.LENGTH_LONG).setAnchorView(navigation).setTextMaxLines(3).show();});
        } catch(Exception e) { error(e.getMessage()==null?getString(R.string.invalid_form):e.getMessage()); }
    }
    @Override protected void onDestroy() { if(ble!=null) ble.close(); super.onDestroy(); }
}
