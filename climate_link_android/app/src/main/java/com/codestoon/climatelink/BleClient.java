package com.codestoon.climatelink;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.os.*;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** One in-flight RPC, acknowledged write fragments, then a long GATT read. */
@SuppressLint("MissingPermission")
public final class BleClient implements AutoCloseable {
    public static final UUID SERVICE=UUID.fromString("a2c10001-7d4b-4cb8-9b31-57f53cb1a001");
    private static final UUID RPC=UUID.fromString("a2c10002-7d4b-4cb8-9b31-57f53cb1a001");
    public interface Listener {
        void status(String value);
        default void scanState(boolean active) {}
        void found(BluetoothDevice device, int rssi);
        void ready();
        void disconnected();
        void error(String message);
    }
    public interface Reply { void receive(JSONObject value); }
    private static final class Request {
        final byte[] bytes; final Reply reply;
        Request(JSONObject json, Reply callback) {
            bytes=(json.toString()+"\n").getBytes(StandardCharsets.UTF_8); reply=callback;
        }
    }
    private final Context context;
    private final Listener listener;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final BluetoothAdapter adapter;
    private final ArrayDeque<Request> queue=new ArrayDeque<>();
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic characteristic;
    private Request current;
    private int offset, fragment=20;
    private boolean ready, scanning;
    private final Runnable timeout=()->fail("مهلت ارتباط تمام شد؛ دستگاه را دوباره بیدار و متصل کنید.");
    private final Runnable scanTimeout=this::stopScan;

    public BleClient(Context context, Listener listener) {
        this.context=context.getApplicationContext(); this.listener=listener;
        BluetoothManager manager=(BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);
        adapter=manager==null?null:manager.getAdapter();
        IntentFilter filter=new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        if(Build.VERSION.SDK_INT>=33) this.context.registerReceiver(bondReceiver,filter,Context.RECEIVER_EXPORTED);
        else this.context.registerReceiver(bondReceiver,filter);
    }
    public boolean enabled() { return adapter!=null && adapter.isEnabled(); }
    public void scan() {
        disconnect(); stopScan();
        if(!enabled()) { listener.error("بلوتوث گوشی را روشن کنید."); return; }
        scanner=adapter.getBluetoothLeScanner();
        if(scanner==null) { listener.error("اسکن BLE در دسترس نیست."); return; }
        scanning=true; listener.scanState(true); listener.status("در حال جست‌وجو، ۱۵ ثانیه…");
        try {
            scanner.startScan(Collections.singletonList(new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(SERVICE)).build()),
                new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scanCallback);
            handler.postDelayed(scanTimeout,15000);
        } catch(RuntimeException e) { stopScan(); listener.error("اسکن آغاز نشد؛ مجوزها و بلوتوث را بررسی کنید."); }
    }
    public void stopScan() {
        handler.removeCallbacks(scanTimeout);
        if(scanning && scanner!=null) {
            try { scanner.stopScan(scanCallback); } catch(RuntimeException ignored) { }
            listener.status("جست‌وجو پایان یافت. دستگاه موردنظر را انتخاب کنید.");
        }
        scanning=false; listener.scanState(false);
    }
    private final ScanCallback scanCallback=new ScanCallback() {
        @Override public void onScanResult(int type, ScanResult result) {
            handler.post(()->{ if(scanning) listener.found(result.getDevice(),result.getRssi()); });
        }
        @Override public void onScanFailed(int code) {
            handler.post(()->{ stopScan(); listener.error("خطای اسکن: "+code); });
        }
    };
    public void connect(BluetoothDevice device) {
        stopScan(); disconnect(); fragment=20;
        listener.status("در حال اتصال؛ PIN چاپ‌شده در Serial Monitor را در پنجره اندروید وارد کنید.");
        arm(60000);
        try { gatt=device.connectGatt(context,false,callback,BluetoothDevice.TRANSPORT_LE); }
        catch(RuntimeException e) { fail("اتصال BLE آغاز نشد."); }
    }
    private void arm(long ms) { handler.removeCallbacks(timeout); handler.postDelayed(timeout,ms); }
    private void makeReady() {
        if(ready) return;
        handler.removeCallbacks(timeout); ready=true; listener.ready();
    }
    private final BroadcastReceiver bondReceiver=new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            BluetoothDevice device=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if(gatt==null || device==null || !device.equals(gatt.getDevice())) return;
            // Also inspect the real bond state; do not trust a broadcast's extras alone.
            int state=device.getBondState();
            if(state==BluetoothDevice.BOND_BONDED && characteristic!=null) makeReady();
            else if(state==BluetoothDevice.BOND_NONE &&
                intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,0)==BluetoothDevice.BOND_BONDING)
                fail("جفت‌سازی لغو شد یا PIN نادرست بود.");
        }
    };
    public void request(JSONObject json, Reply reply) {
        if(!ready || gatt==null) { listener.error("ابتدا به دستگاه وصل شوید."); return; }
        Request r=new Request(json,reply);
        if(r.bytes.length>=1536) { listener.error("حجم تنظیمات بیش از حد مجاز است."); return; }
        queue.add(r); pump();
    }
    private void pump() {
        if(current!=null || queue.isEmpty()) return;
        current=queue.remove(); offset=0; arm(20000); writeNext();
    }
    private void writeNext() {
        if(current==null || gatt==null) return;
        if(offset==current.bytes.length) {
            if(!gatt.readCharacteristic(characteristic)) fail("خواندن پاسخ آغاز نشد.");
            return;
        }
        int end=Math.min(current.bytes.length,offset+fragment);
        byte[] part=Arrays.copyOfRange(current.bytes,offset,end);
        boolean started;
        if(Build.VERSION.SDK_INT>=33) started=gatt.writeCharacteristic(characteristic,part,
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)==BluetoothStatusCodes.SUCCESS;
        else {
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            characteristic.setValue(part); started=gatt.writeCharacteristic(characteristic);
        }
        if(started) offset=end; else fail("ارسال تنظیمات آغاز نشد.");
    }
    private void readResult(BluetoothGatt source, int status, byte[] value) {
        handler.post(()->{
            if(source!=gatt || current==null) return;
            if(status!=BluetoothGatt.GATT_SUCCESS) { fail("خطای خواندن BLE: "+status); return; }
            Request completed=current; current=null; handler.removeCallbacks(timeout);
            try {
                JSONObject result=new JSONObject(new String(value,StandardCharsets.UTF_8));
                if(!result.optBoolean("ok")) { queue.clear(); listener.error("دستگاه تنظیمات را نپذیرفت: "+result.optString("error")); }
                else completed.reply.receive(result);
            } catch(Exception e) { queue.clear(); listener.error("پاسخ دستگاه معتبر نیست."); }
            pump();
        });
    }
    private final BluetoothGattCallback callback=new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt source,int status,int state) {
            handler.post(()->{
                if(source!=gatt) return;
                if(status!=BluetoothGatt.GATT_SUCCESS || state==BluetoothProfile.STATE_DISCONNECTED) {
                    disconnect(); listener.status("ارتباط قطع شد؛ ممکن است دستگاه وارد خواب شده باشد."); return;
                }
                if(state==BluetoothProfile.STATE_CONNECTED && !source.requestMtu(247)) source.discoverServices();
            });
        }
        @Override public void onMtuChanged(BluetoothGatt source,int mtu,int status) {
            handler.post(()->{
                if(source!=gatt) return;
                fragment=status==BluetoothGatt.GATT_SUCCESS?Math.max(20,Math.min(180,mtu-3)):20;
                if(!source.discoverServices()) fail("کشف سرویس‌ها آغاز نشد.");
            });
        }
        @Override public void onServicesDiscovered(BluetoothGatt source,int status) {
            handler.post(()->{
                if(source!=gatt) return;
                BluetoothGattService service=source.getService(SERVICE);
                characteristic=service==null?null:service.getCharacteristic(RPC);
                if(status!=BluetoothGatt.GATT_SUCCESS || characteristic==null) { fail("سرویس ClimateLink پیدا نشد."); return; }
                BluetoothDevice device=source.getDevice();
                if(device.getBondState()==BluetoothDevice.BOND_BONDED) makeReady();
                else if(device.getBondState()!=BluetoothDevice.BOND_BONDING && !device.createBond()) fail("جفت‌سازی آغاز نشد.");
            });
        }
        @Override public void onCharacteristicWrite(BluetoothGatt source,BluetoothGattCharacteristic c,int status) {
            handler.post(()->{ if(source!=gatt || current==null) return;
                if(status!=BluetoothGatt.GATT_SUCCESS) fail("خطای نوشتن BLE: "+status); else writeNext(); });
        }
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,int status) {
            readResult(g,status,c.getValue());
        }
        @Override public void onCharacteristicRead(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value,int status) {
            readResult(g,status,value);
        }
    };
    private void fail(String message) { disconnect(); listener.error(message); }
    public void disconnect() {
        handler.removeCallbacks(timeout); ready=false; current=null; queue.clear(); characteristic=null;
        BluetoothGatt previous=gatt; gatt=null;
        if(previous!=null) { try { previous.disconnect(); previous.close(); } catch(RuntimeException ignored) { } }
        listener.disconnected();
    }
    @Override public void close() { stopScan(); disconnect(); context.unregisterReceiver(bondReceiver); }
}
