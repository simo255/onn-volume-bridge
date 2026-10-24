package io.github.simo255.onnvolumebridge;
import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;

/** TV-friendly opt-in setup. No launcher integration, accounts or automatic TV commands. */
public final class MainActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Spinner provider;private EditText host,port,psk,path,inputName;private TextView status;
    private Button save;private Switch enabled;private String pin="";private String pinHost="";
    private final Runnable refresh=new Runnable(){public void run(){
        if(!ConfigStore.prefs(MainActivity.this).getBoolean("enabled",false))status.setText(R.string.paused);
        else status.setText(VolumeRelay.get(MainActivity.this).ready() && LocalAdbVolumeKeys.get(MainActivity.this).ready()?R.string.connected:R.string.waiting);
        handler.postDelayed(this,1500);
    }};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(50,28,50,30);content.setBackgroundColor(Color.rgb(14,23,40));scroll.addView(content);
        label(content,R.string.title,26);label(content,R.string.summary,17);
        status=label(content,R.string.paused,18);status.setTextColor(Color.rgb(130,183,255));
        label(content,R.string.provider,16);provider=new Spinner(this);
        provider.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Samsung Tizen","Roku TV (experimental)","Sony BRAVIA (experimental)","UPnP RenderingControl (experimental)"}));content.addView(provider);
        host=edit(content,R.string.host,InputType.TYPE_CLASS_PHONE);
        port=edit(content,R.string.port,InputType.TYPE_CLASS_NUMBER);
        psk=edit(content,R.string.psk,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        path=edit(content,R.string.control_path,InputType.TYPE_CLASS_TEXT);
        inputName=edit(content,R.string.input_name,InputType.TYPE_CLASS_TEXT);
        android.content.SharedPreferences prefs=ConfigStore.prefs(this);
        host.setText(prefs.getString("host",""));port.setText(prefs.getInt("port",8002)+"");
        psk.setText(SecretStore.read(prefs,"sony_psk"));path.setText(prefs.getString("control_path","/upnp/control/RenderingControl1"));inputName.setText(prefs.getString("input_name",""));
        pin=prefs.getString("cert_pin","");pinHost=prefs.getString("host","");
        int selected;try{selected=TargetConfig.Provider.valueOf(prefs.getString("provider","SAMSUNG")).ordinal();}catch(RuntimeException e){selected=0;}
        provider.setSelection(selected);
        final int[] previous={selected};
        provider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> parent){}
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                if(position!=previous[0]){port.setText(position==0?"8002":position==1?"8060":position==2?"80":"9197");previous[0]=position;}
                psk.setVisibility(position==2?View.VISIBLE:View.GONE);path.setVisibility(position==3?View.VISIBLE:View.GONE);
            }
        });
        save=button(content,R.string.save,v->save());
        enabled=new Switch(this);enabled.setText(R.string.enable);enabled.setChecked(prefs.getBoolean("enabled",false));content.addView(enabled);
        enabled.setOnCheckedChangeListener((b,on)->{
            if(on && !VolumeRelay.get(this).configured()){enabled.setChecked(false);toast(R.string.bad_config);return;}
            prefs.edit().putBoolean("enabled",on).apply();
            if(on)VolumeRelay.get(this).connect();else{VolumeRelay.get(this).disconnect();LocalAdbVolumeKeys.get(this).stop();}
        });
        LinearLayout tests=new LinearLayout(this);content.addView(tests);
        button(tests,R.string.down,v->test(VolumeKey.DOWN));button(tests,R.string.up,v->test(VolumeKey.UP));button(tests,R.string.mute,v->test(VolumeKey.MUTE));
        button(content,R.string.reconnect,v->{VolumeRelay.get(this).connect();if(enabled.isChecked())LocalAdbVolumeKeys.get(this).start();});
        button(content,R.string.accessibility,v->open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        button(content,R.string.remote_settings,v->open(new Intent("android.settings.SLICE_SETTINGS").putExtra("slice_uri","content://com.google.android.tv.axel.sliceprovider/main")));
        label(content,R.string.setup_steps,16);label(content,R.string.experimental,15);
        setContentView(scroll);if(prefs.getBoolean("enabled",false))VolumeRelay.get(this).connect();
    }
    private void save(){
        TargetConfig.Provider type=TargetConfig.Provider.values()[provider.getSelectedItemPosition()];
        final String address;try{address=LocalAddress.require(host.getText().toString());}catch(RuntimeException e){toast(R.string.bad_config);return;}
        if(type==TargetConfig.Provider.SAMSUNG && (!address.equals(pinHost) || pin.isEmpty())){
            save.setEnabled(false);
            worker.execute(()->{try{
                String candidate=EnrollSamsung.fingerprint(address);
                handler.post(()->{if(isFinishing() || isDestroyed())return;save.setEnabled(true);
                    new AlertDialog.Builder(this).setTitle(R.string.trust_title).setMessage(candidate)
                        .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.approve,(dialog,which)->{pin=candidate;pinHost=address;persist(type,address);}).show();});
            }catch(Exception e){handler.post(()->{if(!isFinishing()){save.setEnabled(true);toast(R.string.cert_failed);}});}});
        } else persist(type,address);
    }
    private void persist(TargetConfig.Provider type,String address){
        try{
            TargetConfig c=new TargetConfig(type,address,pin,psk.getText().toString(),Integer.parseInt(port.getText().toString()),path.getText().toString(),inputName.getText().toString());
            ConfigStore.save(this,c);VolumeRelay.get(this).disconnect();VolumeRelay.get(this).connect();toast(R.string.saved);
        }catch(RuntimeException e){toast(R.string.bad_config);}
    }
    private void test(VolumeKey key){if(!enabled.isChecked()){toast(R.string.enable_first);return;}VolumeRelay.get(this).key(key);}
    private void open(Intent intent){try{startActivity(intent);}catch(RuntimeException e){toast(R.string.open_settings_failed);}}
    private void toast(int text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private TextView label(LinearLayout parent,int text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(Color.WHITE);v.setPadding(0,8,0,8);parent.addView(v);return v;}
    private EditText edit(LinearLayout parent,int hint,int input){EditText v=new EditText(this);v.setHint(hint);v.setInputType(input);v.setSingleLine(true);v.setTextColor(Color.WHITE);v.setHintTextColor(Color.LTGRAY);parent.addView(v);return v;}
    private Button button(LinearLayout parent,int text,View.OnClickListener listener){Button b=new Button(this);b.setText(text);b.setOnClickListener(listener);parent.addView(b);return b;}
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy(){worker.shutdownNow();handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
