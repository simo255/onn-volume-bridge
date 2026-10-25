package io.github.simo255.onnvolumebridge;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Guided, remote-friendly opt-in setup. Connections never imply permission to send volume. */
public final class MainActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Spinner provider;
    private EditText host,port,psk,path,inputName;
    private TextView status,progress,providerHelp,tvCheck,serviceCheck,readerCheck;
    private Button save,discover,next,back,advancedToggle,manualTvToggle;
    private Switch enabled;
    private CheckBox cec;
    private LinearLayout advanced,sonyFields,upnpFields,footer,manualTv;
    private final LinearLayout[] pages=new LinearLayout[4];
    private ScrollView scroll;
    private int step;
    private boolean advancedOpen,automaticPending,automaticBusy,advanceTvWhenReady,advanceRemoteWhenReady;
    private Button automatic;
    private LinearLayout manualRemote;
    private String pin="",pinHost="";
    private final Runnable refresh=new Runnable(){public void run(){refreshState();handler.postDelayed(this,1000);}};

    @Override protected void attachBaseContext(Context base) {
        android.content.res.Configuration config=new android.content.res.Configuration(base.getResources().getConfiguration());
        String language=base.getSharedPreferences("bridge",MODE_PRIVATE).getString("ui_language","en");
        config.setLocale(Locale.forLanguageTag(language));
        super.attachBaseContext(base.createConfigurationContext(config));
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        automaticPending=state!=null && state.getBoolean("automatic_pending",false);
        advancedOpen=state!=null && state.getBoolean("advanced_open",false);
        android.content.SharedPreferences prefs=ConfigStore.prefs(this);
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);outer.setBackgroundColor(Color.rgb(14,23,40));
        scroll=new ScrollView(this);scroll.setFillViewport(true);outer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(42),dp(20),dp(42),dp(28));content.setBackgroundColor(Color.rgb(14,23,40));scroll.addView(content);
        Button language=button(content,R.string.language,v->new AlertDialog.Builder(this)
            .setTitle(R.string.language)
            .setSingleChoiceItems(new String[]{"English","Français"},"fr".equals(prefs.getString("ui_language","en"))?1:0,(dialog,position)->{
                dialog.dismiss();String chosen=position==1?"fr":"en";
                if(!chosen.equals(prefs.getString("ui_language","en"))){prefs.edit().putString("ui_language",chosen).apply();recreate();}
            }).setNegativeButton(R.string.cancel,null).show());
        language.setText(getString(R.string.language_selector,"fr".equals(prefs.getString("ui_language","en"))?"Français":"English"));
        label(content,R.string.title,25);label(content,R.string.summary,16);
        status=label(content,R.string.paused,18);status.setTextColor(Color.rgb(141,198,255));
        progress=label(content,R.string.step_tv,15);
        for(int i=0;i<pages.length;i++){pages[i]=column(content);pages[i].setPadding(0,dp(8),0,dp(8));}
        buildTv(pages[0],prefs);
        if(state!=null)showManualTv(state.getBoolean("manual_tv",false));
        buildRemote(pages[1],prefs);
        buildTest(pages[2]);
        buildDone(pages[3]);
        footer=new LinearLayout(this);footer.setPadding(dp(42),dp(8),dp(42),dp(12));outer.addView(footer);
        back=button(footer,R.string.back,v->showStep(step-1));
        next=button(footer,R.string.next,v->{if(step==2){prefs.edit().putBoolean("setup_complete",true).apply();showStep(3);}else showStep(step+1);});
        back.setLayoutParams(new LinearLayout.LayoutParams(0,dp(52),1));next.setLayoutParams(new LinearLayout.LayoutParams(0,dp(52),1));
        setContentView(outer);
        step=state!=null?state.getInt("setup_step",0):prefs.getBoolean("setup_complete",false)?3:0;
        showStep(step);
        if(state!=null && state.getBoolean("setup_connection",false))VolumeRelay.get(this).connectForSetup();
        else if(prefs.getBoolean("enabled",false))VolumeRelay.get(this).connect();
    }

    private void buildTv(LinearLayout parent,android.content.SharedPreferences prefs) {
        label(parent,R.string.connect_title,22);label(parent,R.string.connect_intro,17);
        discover=button(parent,R.string.find_tv,v->discover());
        tvCheck=label(parent,R.string.tv_not_connected,16);
        manualTvToggle=button(parent,R.string.manual_tv,v->showManualTv(manualTv.getVisibility()!=View.VISIBLE));
        manualTv=column(parent);manualTv.setVisibility(View.GONE);parent=manualTv;
        label(parent,R.string.provider_simple,16);provider=new Spinner(this);
        provider.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,getResources().getStringArray(R.array.providers)));
        parent.addView(provider);
        providerHelp=label(parent,R.string.help_samsung,16);
        label(parent,R.string.address_label,16);host=edit(parent,R.string.host,InputType.TYPE_CLASS_PHONE);
        host.setId(R.id.tv_address);host.setText(prefs.getString("host",""));
        label(parent,R.string.address_help,15);
        sonyFields=column(parent);label(sonyFields,R.string.psk,16);
        psk=edit(sonyFields,R.string.psk,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        psk.setSaveEnabled(false);psk.setText(SecretStore.read(prefs,"sony_psk"));
        advancedToggle=button(parent,R.string.show_advanced,v->{advancedOpen=!advancedOpen;updateProvider(false);});
        advanced=column(parent);
        label(advanced,R.string.port,16);port=edit(advanced,R.string.port,InputType.TYPE_CLASS_NUMBER);port.setId(R.id.tv_port);port.setText(Integer.toString(prefs.getInt("port",8002)));
        upnpFields=column(advanced);label(upnpFields,R.string.control_path,16);
        path=edit(upnpFields,R.string.control_path,InputType.TYPE_CLASS_TEXT);path.setId(R.id.control_path_field);path.setText(prefs.getString("control_path","/upnp/control/RenderingControl1"));
        label(advanced,R.string.input_name,16);inputName=edit(advanced,R.string.input_name,InputType.TYPE_CLASS_TEXT);inputName.setId(R.id.input_name_field);inputName.setText(prefs.getString("input_name",""));
        pin=prefs.getString("cert_pin","");pinHost=prefs.getString("host","");
        int selected;try{selected=TargetConfig.Provider.valueOf(prefs.getString("provider","SAMSUNG")).ordinal();}catch(RuntimeException ignored){selected=0;}
        provider.setSelection(selected);final int[] previous={selected};
        provider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> parent) { }
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id) {
                updateProvider(position!=previous[0]);previous[0]=position;
            }
        });
        updateProvider(false);
        save=button(parent,R.string.save,v->save());
    }

    private void showManualTv(boolean show) {
        manualTv.setVisibility(show?View.VISIBLE:View.GONE);
        manualTvToggle.setText(show?R.string.hide_manual_tv:R.string.manual_tv);
    }

    private void updateProvider(boolean changed) {
        int type=provider.getSelectedItemPosition();
        if(changed){port.setText(type==0?"8002":type==1?"8060":type==2?"80":"9197");}
        providerHelp.setText(new int[]{R.string.help_samsung,R.string.help_roku,R.string.help_sony,R.string.help_upnp}[type]);
        sonyFields.setVisibility(type==2?View.VISIBLE:View.GONE);
        upnpFields.setVisibility(type==3?View.VISIBLE:View.GONE);
        advanced.setVisibility(advancedOpen || type==3?View.VISIBLE:View.GONE);
        advancedToggle.setVisibility(type==3?View.GONE:View.VISIBLE);
        advancedToggle.setText(advancedOpen?R.string.hide_advanced:R.string.show_advanced);
    }

    private void buildRemote(LinearLayout parent,android.content.SharedPreferences prefs) {
        label(parent,R.string.remote_title,22);label(parent,R.string.auto_intro,18);
        automatic=button(parent,R.string.auto_setup,v->automaticSetup());
        serviceCheck=label(parent,R.string.accessibility_needed,16);
        readerCheck=label(parent,R.string.reader_needed,16);
        button(parent,R.string.manual_setup,v->manualRemote.setVisibility(manualRemote.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        manualRemote=column(parent);manualRemote.setVisibility(View.GONE);
        parent=manualRemote;
        enabled=new Switch(this);enabled.setText(R.string.enable);enabled.setTextColor(Color.WHITE);enabled.setTextSize(18);enabled.setMinHeight(dp(52));enabled.setChecked(prefs.getBoolean("enabled",false));parent.addView(enabled);
        enabled.setOnCheckedChangeListener((button,on)->{
            if(on && !VolumeRelay.get(this).configured()){enabled.setChecked(false);toast(R.string.bad_config);return;}
            prefs.edit().putBoolean("enabled",on).apply();
            if(on){VolumeRelay.get(this).connect();if(accessibilityEnabled())LocalAdbVolumeKeys.get(this).start();}
            else{LocalAdbVolumeKeys.get(this).stop();VolumeRelay.get(this).disconnect();}
            refreshState();
        });
        label(parent,R.string.debug_help,17);
        button(parent,R.string.debug_settings,v->open(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)));
        label(parent,R.string.accessibility_help,17);
        button(parent,R.string.accessibility,v->open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        label(parent,R.string.authorize_help,17);
        button(parent,R.string.retry_remote,v->{if(enabled.isChecked()){VolumeRelay.get(this).connect();LocalAdbVolumeKeys.get(this).start();}});
        label(parent,R.string.cec_help,17);
        button(parent,R.string.remote_settings,v->openRemoteSettings());
        cec=new CheckBox(this);cec.setText(R.string.cec_confirm);cec.setTextColor(Color.WHITE);cec.setTextSize(17);cec.setChecked(prefs.getBoolean("cec_confirmed",false));parent.addView(cec);
        cec.setOnCheckedChangeListener((button,on)->{prefs.edit().putBoolean("cec_confirmed",on).apply();refreshState();});
    }

    private void automaticSetup() {
        if(automaticBusy)return;
        if(!VolumeRelay.get(this).configured()){toast(R.string.bad_config);return;}
        automaticPending=true;
        if(Settings.Global.getInt(getContentResolver(),Settings.Global.ADB_ENABLED,0)==0) {
            new AlertDialog.Builder(this).setTitle(R.string.enable_debug_title).setMessage(R.string.enable_debug_intro)
                .setNegativeButton(R.string.cancel,(d,w)->automaticPending=false)
                .setPositiveButton(R.string.debug_settings,(d,w)->open(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))).show();
            return;
        }
        enabled.setChecked(true);automaticBusy=true;automatic.setEnabled(false);automatic.setText(R.string.auto_working);
        worker.execute(()->{
            try {
                boolean fixed=LocalAdbVolumeKeys.get(this).configureRemote();
                handler.post(()->{
                    if(isFinishing() || isDestroyed())return;
                    automaticBusy=false;automaticPending=false;automatic.setEnabled(true);automatic.setText(R.string.auto_setup);
                    advanceRemoteWhenReady=fixed;cec.setChecked(fixed);LocalAdbVolumeKeys.get(this).start();refreshState();
                    if(!fixed)new AlertDialog.Builder(this).setTitle(R.string.volume_mode_title).setMessage(R.string.volume_mode_help)
                        .setPositiveButton(R.string.remote_settings,(d,w)->{automaticPending=true;openRemoteSettings();})
                        .setNegativeButton(R.string.cancel,null).show();
                });
            }catch(Exception ignored){handler.post(()->{
                if(isFinishing() || isDestroyed())return;
                automaticBusy=false;automaticPending=false;automatic.setEnabled(true);automatic.setText(R.string.auto_setup);
                new AlertDialog.Builder(this).setTitle(R.string.auto_retry_title).setMessage(R.string.auto_retry_help)
                    .setPositiveButton(R.string.auto_setup,(d,w)->automaticSetup()).setNegativeButton(R.string.manual_setup,(d,w)->manualRemote.setVisibility(View.VISIBLE)).show();
            });}
        });
    }

    private void buildTest(LinearLayout parent) {
        label(parent,R.string.test_title,22);label(parent,R.string.test_intro,17);
        LinearLayout row=new LinearLayout(this);parent.addView(row);
        button(row,R.string.down,v->test(VolumeKey.DOWN));button(row,R.string.up,v->test(VolumeKey.UP));button(row,R.string.mute,v->test(VolumeKey.MUTE));
        label(parent,R.string.test_remote_help,17);
        button(parent,R.string.troubleshoot,v->new AlertDialog.Builder(this).setTitle(R.string.troubleshoot).setMessage(R.string.troubleshoot_help).setPositiveButton(android.R.string.ok,null).show());
        label(parent,R.string.experimental,15);
    }

    private void buildDone(LinearLayout parent) {
        label(parent,R.string.done_title,24);label(parent,R.string.done_help,18);
        button(parent,R.string.pause_resume,v->enabled.setChecked(!enabled.isChecked()));
        button(parent,R.string.reconnect,v->{VolumeRelay.get(this).connect();if(enabled.isChecked())LocalAdbVolumeKeys.get(this).start();});
        button(parent,R.string.change_setup,v->{showStep(0);});
        button(parent,R.string.forget_pairing,v->new AlertDialog.Builder(this).setTitle(R.string.forget_pairing).setMessage(R.string.forget_message).setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.approve,(d,w)->{
            ConfigStore.prefs(this).edit().remove("samsung_token").remove("sony_psk").remove("cert_pin").putBoolean("enabled",false).putBoolean("setup_complete",false).putLong("config_revision",System.currentTimeMillis()).apply();
            pin="";pinHost="";psk.setText("");enabled.setChecked(false);LocalAdbVolumeKeys.get(this).stop();VolumeRelay.get(this).disconnect();showStep(0);
        }).show());
    }

    private void showStep(int requested) {
        step=Math.max(0,Math.min(3,requested));
        for(int i=0;i<pages.length;i++)pages[i].setVisibility(i==step?View.VISIBLE:View.GONE);
        progress.setText(new int[]{R.string.step_tv,R.string.step_remote,R.string.step_test,R.string.step_done}[step]);
        footer.setVisibility(step==3?View.GONE:View.VISIBLE);back.setVisibility(step==0?View.GONE:View.VISIBLE);
        next.setText(step==2?R.string.finish:R.string.next);
        refreshState();scroll.post(()->{scroll.fullScroll(View.FOCUS_UP);pages[step].requestFocus();});
    }

    private boolean accessibilityEnabled() {
        String services=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        ComponentName ours=new ComponentName(this,VolumeService.class);
        if(services!=null)for(String service:services.split(":"))if(ours.equals(ComponentName.unflattenFromString(service)))return true;
        return false;
    }

    private void refreshState() {
        if(status==null || next==null)return;
        boolean active=ConfigStore.prefs(this).getBoolean("enabled",false);
        boolean tv=VolumeRelay.get(this).ready(), service=accessibilityEnabled();
        boolean reader=LocalAdbVolumeKeys.get(this).ready();
        status.setText(!active?R.string.paused:tv && service && reader?R.string.connected:!tv?R.string.tv_waiting:!service?R.string.accessibility_needed:R.string.reader_needed);
        tvCheck.setText(tv && savedForm()?R.string.tv_connected:R.string.tv_not_connected);
        serviceCheck.setText(service?R.string.accessibility_ready:R.string.accessibility_needed);
        readerCheck.setText(reader?R.string.reader_ready:R.string.reader_needed);
        boolean ready=active && tv && service && reader && cec.isChecked();
        if(step==0 && advanceTvWhenReady && tv && savedForm()){advanceTvWhenReady=false;showStep(1);return;}
        if(step==1 && advanceRemoteWhenReady && ready){advanceRemoteWhenReady=false;showStep(2);return;}
        next.setEnabled(step==0?VolumeRelay.get(this).configured() && tv && savedForm():step==1?ready:ready);
    }

    private boolean savedForm() {
        try {
            TargetConfig c=ConfigStore.load(this);
            return c.provider.ordinal()==provider.getSelectedItemPosition() && c.host.equals(host.getText().toString().trim())
                && c.port==Integer.parseInt(port.getText().toString()) && (c.provider!=TargetConfig.Provider.SONY || c.psk.equals(psk.getText().toString()))
                && c.path.equals(path.getText().toString()) && c.inputName.equals(inputName.getText().toString().trim());
        }catch(RuntimeException ignored){return false;}
    }

    private void discover() {
        discover.setEnabled(false);discover.setText(R.string.searching);
        worker.execute(()->{
            List<TvDiscovery.Candidate> results;
            try{results=TvDiscovery.search();}catch(Exception ignored){results=Collections.emptyList();}
            final List<TvDiscovery.Candidate> found=results;
            handler.post(()->{
                if(isFinishing() || isDestroyed())return;
                discover.setEnabled(true);discover.setText(R.string.find_tv);
                if(found.isEmpty()){new AlertDialog.Builder(this).setTitle(R.string.find_tv).setMessage(R.string.discovery_empty)
                    .setPositiveButton(R.string.manual_tv,(dialog,which)->showManualTv(true)).setNegativeButton(R.string.cancel,null).show();return;}
                String[] names=new String[found.size()];String[] brands=getResources().getStringArray(R.array.providers);
                for(int i=0;i<found.size();i++){TvDiscovery.Candidate c=found.get(i);names[i]=(c.provider==null?getString(R.string.network_tv):brands[c.provider.ordinal()])+" — "+c.host;}
                new AlertDialog.Builder(this).setTitle(R.string.choose_tv).setItems(names,(dialog,index)->{
                    TvDiscovery.Candidate c=found.get(index);
                    if(c.provider!=null)selectDiscoveredTv(c,c.provider);
                    else new AlertDialog.Builder(this).setTitle(R.string.unknown_tv_title)
                        .setItems(R.array.providers,(d,choice)->selectDiscoveredTv(c,TargetConfig.Provider.values()[choice]))
                        .setNegativeButton(R.string.cancel,null).show();
                }).setNegativeButton(R.string.cancel,null).show();
            });
        });
    }

    private void selectDiscoveredTv(TvDiscovery.Candidate candidate,TargetConfig.Provider type) {
        provider.setSelection(type.ordinal());host.setText(candidate.host);
        // Wait for the spinner's provider-change callback before applying the default port.
        handler.post(()->{
            port.setText(type==TargetConfig.Provider.SAMSUNG?"8002":type==TargetConfig.Provider.ROKU?"8060":type==TargetConfig.Provider.SONY?"80":"9197");
            if(type==TargetConfig.Provider.SONY || type==TargetConfig.Provider.UPNP)showManualTv(true);
            else save();
        });
    }

    private void save() {
        TargetConfig.Provider type=TargetConfig.Provider.values()[provider.getSelectedItemPosition()];
        final String address;
        try{address=LocalAddress.require(host.getText().toString());host.setError(null);}catch(RuntimeException e){host.setError(getString(R.string.address_error));host.requestFocus();return;}
        if(type==TargetConfig.Provider.SAMSUNG && (!address.equals(pinHost) || pin.isEmpty())) {
            save.setEnabled(false);save.setText(R.string.connecting);
            worker.execute(()->{try{
                String candidate=EnrollSamsung.fingerprint(address);
                handler.post(()->{if(isFinishing() || isDestroyed())return;save.setEnabled(true);save.setText(R.string.save);
                    new AlertDialog.Builder(this).setTitle(R.string.trust_title).setMessage(getString(R.string.trust_help)+"\n\n"+candidate)
                        .setNegativeButton(R.string.cancel,null).setPositiveButton(R.string.approve,(dialog,which)->{pin=candidate;pinHost=address;persist(type,address);}).show();});
            }catch(Exception e){handler.post(()->{if(!isFinishing() && !isDestroyed()){save.setEnabled(true);save.setText(R.string.save);toast(R.string.cert_failed);}});}});
        }else persist(type,address);
    }

    private void persist(TargetConfig.Provider type,String address) {
        try {
            TargetConfig config=new TargetConfig(type,address,pin,psk.getText().toString(),Integer.parseInt(port.getText().toString()),path.getText().toString(),inputName.getText().toString());
            ConfigStore.save(this,config);ConfigStore.prefs(this).edit().putBoolean("setup_complete",false).apply();
            VolumeRelay.get(this).disconnect();VolumeRelay.get(this).connectForSetup();advanceTvWhenReady=step==0;toast(R.string.saved);refreshState();
        }catch(RuntimeException e){toast(R.string.bad_config);}
    }
    private void test(VolumeKey key) {
        if(!enabled.isChecked()){toast(R.string.enable_first);return;}
        if(!VolumeRelay.get(this).ready()){toast(R.string.tv_waiting);return;}
        VolumeRelay.get(this).key(key);
    }
    private void openRemoteSettings() {
        try{startActivity(new Intent("android.settings.SLICE_SETTINGS").putExtra("slice_uri","content://com.google.android.tv.axel.sliceprovider/main"));}
        catch(RuntimeException e){open(new Intent(Settings.ACTION_SETTINGS));}
    }
    private void open(Intent intent){try{startActivity(intent);}catch(RuntimeException e){toast(R.string.open_settings_failed);}}
    private void toast(int text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private int dp(int size){return Math.round(size*getResources().getDisplayMetrics().density);}
    private LinearLayout column(LinearLayout parent){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);parent.addView(v);return v;}
    private TextView label(LinearLayout parent,int text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(Color.WHITE);v.setPadding(0,dp(5),0,dp(5));parent.addView(v);return v;}
    private EditText edit(LinearLayout parent,int hint,int type){EditText v=new EditText(this);v.setHint(hint);v.setInputType(type);v.setSingleLine(true);v.setTextColor(Color.WHITE);v.setHintTextColor(Color.LTGRAY);v.setTextSize(18);v.setMinHeight(dp(48));parent.addView(v);return v;}
    private Button button(LinearLayout parent,int text,View.OnClickListener click){Button v=new Button(this);v.setText(text);v.setTextSize(16);v.setMinHeight(dp(48));v.setBackgroundTintList(getColorStateList(R.color.button_background));v.setTextColor(getColorStateList(R.color.button_text));v.setOnClickListener(click);parent.addView(v);return v;}
    @Override public void onBackPressed(){if(step>0 && step<3)showStep(step-1);else super.onBackPressed();}
    @Override protected void onSaveInstanceState(Bundle out){out.putBoolean("automatic_pending",automaticPending);out.putInt("setup_step",step);out.putBoolean("setup_connection",VolumeRelay.get(this).setupConnectionRequested());out.putBoolean("manual_tv",manualTv.getVisibility()==View.VISIBLE);out.putBoolean("advanced_open",advancedOpen);super.onSaveInstanceState(out);}
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);handler.post(refresh);if(automaticPending && !automaticBusy && Settings.Global.getInt(getContentResolver(),Settings.Global.ADB_ENABLED,0)!=0)automaticSetup();}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy(){LocalAdbVolumeKeys.get(this).cancelSetup();VolumeRelay.get(this).finishSetupConnection();worker.shutdownNow();handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
