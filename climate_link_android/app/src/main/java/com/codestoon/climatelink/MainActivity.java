package com.codestoon.climatelink;

import android.Manifest;
import android.graphics.Rect;
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
import com.google.android.material.textfield.TextInputLayout;
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
    private String selectedAddress="",savedFingerprint="";
    private final ActivityResultLauncher<String[]> permissions=registerForActivityResult(
        new ActivityResultContracts.RequestMultiplePermissions(),result->{
            boolean granted=true; for(String permission:requiredPermissions())
                granted &= ContextCompat.checkSelfPermission(this,permission)==PackageManager.PERMISSION_GRANTED;
            if(granted) startScan(); else error(getString(R.string.permission_error));
        });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); EdgeToEdge.enable(this); setContentView(R.layout.activity_main);
        View root=findViewById(R.id.main);
        WindowInsetsControllerCompat barsController=new WindowInsetsControllerCompat(getWindow(),root);
        barsController.setAppearanceLightStatusBars(true);barsController.setAppearanceLightNavigationBars(true);
        boolean landscape=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if(landscape){findViewById(R.id.overview_intro).setVisibility(View.GONE);findViewById(R.id.brand_tagline).setVisibility(View.GONE);}
        if(getResources().getConfiguration().fontScale>1.3f){
            View badge=findViewById(R.id.connection_badge);((ViewGroup)badge.getParent()).removeView(badge);
            LinearLayout.LayoutParams position=new LinearLayout.LayoutParams(-2,-2);
            int gutter=getResources().getDimensionPixelSize(R.dimen.page_gutter);
            position.setMarginStart(gutter);position.setMarginEnd(gutter);position.bottomMargin=dp(8);
            ((LinearLayout)root).addView(badge,1,position);findViewById(R.id.brand_tagline).setVisibility(View.GONE);
        }
        if(getResources().getConfiguration().fontScale>1.3f){
            LinearLayout grid=findViewById(R.id.measurement_grid);grid.setOrientation(LinearLayout.VERTICAL);
            for(int i=0;i<grid.getChildCount();i++){
                View cell=grid.getChildAt(i);
                LinearLayout.LayoutParams position=new LinearLayout.LayoutParams(-1,cell instanceof LinearLayout?-2:dp(1));
                if(!(cell instanceof LinearLayout)){position.topMargin=dp(14);position.bottomMargin=dp(14);}
                cell.setLayoutParams(position);
            }
        }
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
        navigation.setItemActiveIndicatorEnabled(false);
        navigation.setItemPaddingTop(10);
        navigation.setItemPaddingBottom(10);
        navigation.setOnItemSelectedListener(item->{
            int page=item.getItemId()==R.id.nav_devices?1:item.getItemId()==R.id.nav_settings?2:0;
            if(pages.getDisplayedChild()!=page) {
                View oldPage=pages.getCurrentView();oldPage.animate().cancel();oldPage.setAlpha(1f);
                pages.setDisplayedChild(page);
                View child=pages.getCurrentView();child.animate().cancel();child.setAlpha(1f);
            }
            return true;
        });
        goDevices.setOnClickListener(v->navigation.setSelectedItemId(connected?R.id.nav_settings:R.id.nav_devices));
        scanButton.setOnClickListener(v->{if(scanActive)ble.stopScan();else ensureScan();});
        disconnectButton.setOnClickListener(v->ble.disconnect());
        refresh.setOnClickListener(v->{
            if(isDirty())new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(R.string.refresh_title)
                .setMessage(R.string.refresh_discard).setPositiveButton(R.string.refresh,(d,w)->loadDevice())
                .setNegativeButton(R.string.back,null).show();
            else loadDevice();
        });save.setOnClickListener(v->saveConfig());
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
        setupFormFeedback();
        ViewCompat.setAccessibilityLiveRegion(connectionBadge,ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);
        ViewCompat.setAccessibilityLiveRegion(deviceCount,ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.reading_state),true);
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
        settingsNotice.setText(!connected?R.string.settings_locked:isDirty()?R.string.unsaved_changes:
            value(ssid).isEmpty()||value(url).trim().isEmpty()?R.string.complete_settings:R.string.settings_ready);
        goDevices.setText(connected?R.string.settings:R.string.connect_device);
        findViewById(R.id.connected_details).setVisibility(connected||hasSample?View.VISIBLE:View.GONE);
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
            card.setRadius(dp(20));card.setCardElevation(0);card.setStrokeWidth(0);
            card.setStrokeColor(ContextCompat.getColor(MainActivity.this,R.color.cl_outline));
            card.setCardBackgroundColor(ContextCompat.getColor(MainActivity.this,R.color.cl_surface));
            LinearLayout.LayoutParams margins=new LinearLayout.LayoutParams(-1,-2);margins.bottomMargin=dp(14);devices.addView(card,margins);
            LinearLayout content=new LinearLayout(MainActivity.this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(18),dp(16),dp(18),dp(16));card.addView(content);
            TextView title=new TextView(MainActivity.this);title.setText(name);title.setTextSize(17);title.setTextColor(ContextCompat.getColor(MainActivity.this,R.color.cl_ink));content.addView(title);
            signal=new TextView(MainActivity.this);signal.setTextSize(12);signal.setTextColor(ContextCompat.getColor(MainActivity.this,R.color.cl_muted));content.addView(signal);
            TextView address=new TextView(MainActivity.this);address.setText(device.getAddress());address.setTextSize(12);address.setTypeface(android.graphics.Typeface.MONOSPACE);address.setTextColor(ContextCompat.getColor(MainActivity.this,R.color.cl_muted));address.setTextDirection(View.TEXT_DIRECTION_LTR);address.setGravity(Gravity.START);content.addView(address);
            button=new MaterialButton(MainActivity.this,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(R.string.connect);button.setIconResource(R.drawable.ic_bluetooth);button.setCornerRadius(dp(999));button.setMinHeight(dp(52));content.addView(button,new LinearLayout.LayoutParams(-1,-2));
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
        refresh.setEnabled(connected&&!loading);
        save.setEnabled(connected&&configured&&!loading&&isDirty());
        run.setEnabled(connected&&configured&&!loading&&!isDirty()&&!value(ssid).isEmpty()&&!value(url).trim().isEmpty());
        updateConnectionUi();
    }
    @Override public void status(String s) { connection.setText(s);connection.setContentDescription(s); }
    @Override public void error(String s) { loading=false; setConnected(connected); status(s); Snackbar.make(findViewById(R.id.main),s,Snackbar.LENGTH_LONG).setAnchorView(navigation).setTextMaxLines(4).show(); }
    @Override public void disconnected() { configured=false; loading=false; connecting=false; setConnected(false); status(getString(R.string.offline_message)); }
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
            boolean hasTemperature=renderMeasurement(temperature,r.optDouble("temperature_c"),true);
            boolean hasHumidity=renderMeasurement(humidity,r.optDouble("humidity_pct"),false);
            hasSample=hasTemperature||hasHumidity;
            deviceIdentity.setText(r.optString("device_id")+"\n"+getString(R.string.firmware_label,r.optString("firmware")));
            heroCaption.setText(r.optString("device_id"));
            ((TextView)findViewById(R.id.queued_value)).setText(number(r.optInt("queued")));
            ((TextView)findViewById(R.id.failures_value)).setText(number(r.optInt("failures")));
            ((TextView)findViewById(R.id.dropped_value)).setText(number(r.optInt("dropped")));
            findViewById(R.id.telemetry_card).setVisibility(View.VISIBLE);
            boolean simulated=r.optBoolean("simulated",true);
            findViewById(R.id.simulation_badge).setVisibility(simulated?View.VISIBLE:View.GONE);
            String receivedAt=android.text.format.DateFormat.getTimeFormat(this).format(new Date());
            sampleCaption.setText(hasSample?getString(simulated?R.string.sample_simulated_at:R.string.sample_read_at,receivedAt):getString(R.string.no_sample));
        });
        ble.request(command("get_config"),r->{
            ssid.setText(r.optString("ssid")); url.setText(r.optString("server_url"));
            sample.setText(number(r.optInt("sample_s",900))); every.setText(number(r.optInt("upload_every",1)));
            http.setChecked(r.optBoolean("allow_http")); password.setText(""); token.setText("");
            if(http.isChecked()){
                findViewById(R.id.server_advanced_content).setVisibility(View.VISIBLE);
                ViewCompat.setStateDescription(findViewById(R.id.server_advanced_toggle),getString(R.string.expanded));
            }
            clearPassword.setChecked(false); clearToken.setChecked(false);
            loading=false; configured=true; markSaved(); clearFormErrors(); setConnected(true); status(getString(R.string.config_loaded));
        });
    }
    private boolean renderMeasurement(TextView view,double reading,boolean lead){
        boolean available=!Double.isNaN(reading)&&!Double.isInfinite(reading);
        view.setText(available?String.format(Locale.forLanguageTag("fa"),"%.1f",reading):getString(R.string.empty_value));
        view.setTextSize(available?(lead?40:30):22);
        view.setTextColor(ContextCompat.getColor(this,available?(lead?R.color.cl_accent:R.color.cl_ink):R.color.cl_muted));
        view.setContentDescription(available?getString(R.string.measurement_accessibility,
            getString(lead?R.string.temperature:R.string.humidity),view.getText(),getString(lead?R.string.celsius:R.string.percent)):
            getString(lead?R.string.temperature_unknown:R.string.humidity_unknown));
        return available;
    }
    private static String value(EditText e) { return e.getText().toString(); }
    private void saveConfig(){
        ConfigValidator.Input input=formInput();
        LinkedHashMap<String,Integer> errors=ConfigValidator.validate(input);
        if(!errors.isEmpty()){showFormErrors(errors);return;}
        clearFormErrors();
        try{
            JSONObject request=command("set_config");request.put("ssid",input.ssid);request.put("server_url",input.url);
            request.put("sample_s",Integer.parseInt(input.sample));request.put("upload_every",Integer.parseInt(input.every));
            request.put("allow_http",input.allowHttp);
            if(input.clearPassword)request.put("password","");else if(!input.password.isEmpty())request.put("password",input.password);
            if(input.clearToken)request.put("token","");else if(!input.token.isEmpty())request.put("token",input.token);
            loading=true;setConnected(connected);
            ble.request(request,reply->{
                password.setText("");token.setText("");clearPassword.setChecked(false);clearToken.setChecked(false);
                loading=false;markSaved();setConnected(connected);status(getString(R.string.config_saved));
                Snackbar.make(findViewById(R.id.main),R.string.config_saved,Snackbar.LENGTH_LONG)
                    .setAnchorView(navigation).setTextMaxLines(3).show();
            });
        }catch(Exception e){error(getString(R.string.invalid_form));}
    }
    private ConfigValidator.Input formInput(){
        return new ConfigValidator.Input(value(ssid),value(password),value(url).trim(),value(token),value(sample),value(every),
            clearPassword.isChecked(),clearToken.isChecked(),http.isChecked());
    }
    private String formFingerprint(){
        return Arrays.asList(value(ssid),value(password),value(url),value(token),value(sample),value(every),
            String.valueOf(clearPassword.isChecked()),String.valueOf(clearToken.isChecked()),String.valueOf(http.isChecked())).toString();
    }
    private void markSaved(){savedFingerprint=formFingerprint();}
    private boolean isDirty(){return configured&&!savedFingerprint.equals(formFingerprint());}

    private void disclosure(int toggleId,int contentId){
        MaterialButton toggle=findViewById(toggleId);View content=findViewById(contentId);
        ViewCompat.setStateDescription(toggle,getString(R.string.collapsed));
        toggle.setOnClickListener(v->{
            boolean expanded=content.getVisibility()!=View.VISIBLE;content.setVisibility(expanded?View.VISIBLE:View.GONE);
            ViewCompat.setStateDescription(toggle,getString(expanded?R.string.expanded:R.string.collapsed));
        });
    }
    private TextInputEditText input(String key){
        switch(key){case "ssid":return ssid;case "password":return password;case "url":return url;
            case "token":return token;case "sample":return sample;default:return every;}
    }
    private TextInputLayout inputLayout(String key){return (TextInputLayout)input(key).getParent().getParent();}
    private void setupFormFeedback(){
        disclosure(R.id.wifi_advanced_toggle,R.id.wifi_advanced_content);disclosure(R.id.server_advanced_toggle,R.id.server_advanced_content);
        for(String key:new String[]{"ssid","password","url","token","sample","every"}){
            TextInputEditText edit=input(key);
            edit.addTextChangedListener(new TextWatcher(){
                public void beforeTextChanged(CharSequence text,int start,int count,int after){}
                public void onTextChanged(CharSequence text,int start,int before,int count){
                    if(loading)return;
                    inputLayout(key).setError(null);findViewById(R.id.form_errors).setVisibility(View.GONE);setConnected(connected);
                }
                public void afterTextChanged(Editable text){}
            });
            edit.setOnFocusChangeListener((v,focus)->{
                if(!focus&&!loading){Integer message=ConfigValidator.validate(formInput()).get(key);
                    inputLayout(key).setError(message==null?null:getString(message));}
            });
        }
        for(CompoundButton toggle:new CompoundButton[]{clearPassword,clearToken,http})
            toggle.setOnCheckedChangeListener((v,checked)->{if(!loading)setConnected(connected);});
    }
    private void clearFormErrors(){
        for(String key:new String[]{"ssid","password","url","token","sample","every"})inputLayout(key).setError(null);
        findViewById(R.id.form_errors).setVisibility(View.GONE);
    }
    private void focusInput(String key){
        TextInputEditText edit=input(key);
        edit.requestFocus();edit.post(()->{
            Rect bounds=new Rect(0,0,edit.getWidth(),edit.getHeight());edit.requestRectangleOnScreen(bounds,true);
        });
    }
    private void showFormErrors(LinkedHashMap<String,Integer> errors){
        clearFormErrors();
        for(Map.Entry<String,Integer> entry:errors.entrySet())inputLayout(entry.getKey()).setError(getString(entry.getValue()));
        if(errors.size()==1){focusInput(errors.keySet().iterator().next());return;}
        LinearLayout summary=findViewById(R.id.form_errors);summary.removeAllViews();summary.setVisibility(View.VISIBLE);
        TextView heading=new TextView(this);heading.setText(R.string.error_summary_title);heading.setTextSize(16);
        heading.setTextColor(ContextCompat.getColor(this,R.color.cl_error));heading.setFocusableInTouchMode(true);
        ViewCompat.setAccessibilityHeading(heading,true);summary.addView(heading);
        for(Map.Entry<String,Integer> entry:errors.entrySet()){
            MaterialButton link=new MaterialButton(this,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
            link.setText(entry.getValue());link.setTextColor(ContextCompat.getColor(this,R.color.cl_error));
            link.setOnClickListener(v->focusInput(entry.getKey()));summary.addView(link,new LinearLayout.LayoutParams(-1,-2));
        }
        heading.post(()->{heading.requestFocus();((ScrollView)findViewById(R.id.settings_page)).smoothScrollTo(0,0);});
    }
    @Override protected void onDestroy() { if(ble!=null) ble.close(); super.onDestroy(); }
}
