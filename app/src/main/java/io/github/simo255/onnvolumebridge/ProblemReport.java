package io.github.simo255.onnvolumebridge;

import java.net.URLEncoder;

/** Allowlisted diagnostics only. Never accepts device identities, config fields or exception text. */
final class ProblemReport {
    static final String ISSUES="https://github.com/simo255/onn-volume-bridge/issues/new";
    enum Problem {
        TV_CONFIG("OVB-001",R.string.problem_tv_config,"TV setup incomplete"),
        UNSAVED("OVB-002",R.string.problem_unsaved,"TV settings changed but not saved"),
        TV_APPROVAL("OVB-101",R.string.problem_tv_approval,"Waiting for TV approval"),
        TV_CONNECTING("OVB-102",R.string.problem_tv_connecting,"TV connection in progress"),
        TV_FAILED("OVB-103",R.string.problem_tv_failed,"TV connection or command failed"),
        TV_DISCONNECTED("OVB-104",R.string.problem_tv_disconnected,"TV disconnected"),
        DEBUG_APPROVAL("OVB-201",R.string.problem_debug_approval,"USB debugging approval needed"),
        INPUT_MISSING("OVB-202",R.string.problem_input_missing,"Remote volume input not found"),
        KEY_UNAVAILABLE("OVB-203",R.string.problem_key_unavailable,"Local debugging identity unavailable"),
        READER_CONNECTING("OVB-204",R.string.problem_reader_connecting,"Remote reader connecting"),
        READER_FAILED("OVB-205",R.string.problem_reader_failed,"Remote reader disconnected"),
        ACCESSIBILITY("OVB-206",R.string.problem_accessibility,"Accessibility permission needed"),
        HDMI_SETTING("OVB-301",R.string.problem_hdmi_setting,"Fixed HDMI volume not confirmed"),
        OTHER_AUDIO("OVB-302",R.string.problem_other_audio,"TV forwarding suspended for another audio output"),
        PAUSED("OVB-000",R.string.problem_paused,"Volume forwarding paused"),
        READY("OVB-OK",R.string.problem_ready,"Connection checks passed; physical button behavior still needs checking");
        final String code,summary;final int message;
        Problem(String code,int message,String summary){this.code=code;this.message=message;this.summary=summary;}
    }
    static final class State {
        final TargetConfig.Provider provider;
        final boolean configured,saved,enabled,setup,tv,service,reader,cec;
        final Boolean hdmi;
        final int step;
        final Problem tvProblem,readerProblem;
        State(TargetConfig.Provider provider,boolean configured,boolean saved,boolean enabled,boolean setup,
              boolean tv,boolean service,boolean reader,boolean cec,Boolean hdmi,int step,
              String tvStatus,String readerStatus) {
            this.provider=provider;this.configured=configured;this.saved=saved;this.enabled=enabled;
            this.setup=setup;this.tv=tv;this.service=service;this.reader=reader;this.cec=cec;
            this.hdmi=hdmi;this.step=Math.max(0,Math.min(3,step));
            // Raw status strings are classified, never retained or exported.
            tvProblem=tvProblem(tvStatus);readerProblem=readerProblem(readerStatus);
        }
        Problem problem() {
            if(!configured)return Problem.TV_CONFIG;
            if(step==0 && !saved)return Problem.UNSAVED;
            if(!tv && (enabled || setup))return tvProblem;
            if(!enabled)return Problem.PAUSED;
            if(!service)return Problem.ACCESSIBILITY;
            if(!reader)return readerProblem;
            if(Boolean.FALSE.equals(hdmi))return Problem.OTHER_AUDIO;
            if(!cec)return Problem.HDMI_SETTING;
            return Problem.READY;
        }
    }
    static Problem tvProblem(String status) {
        if("Waiting for TV approval".equals(status) || "TV approval required".equals(status))return Problem.TV_APPROVAL;
        if("Connecting".equals(status) || "Checking TV API".equals(status))return Problem.TV_CONNECTING;
        if("Connection failed; check TV address, certificate and network".equals(status)
            || "TV request failed. Check network control, pairing and protocol compatibility.".equals(status))return Problem.TV_FAILED;
        return Problem.TV_DISCONNECTED;
    }
    static Problem readerProblem(String status) {
        if("Approve USB debugging for onn Volume on this box".equals(status))return Problem.DEBUG_APPROVAL;
        if("Onn keyboard volume input not found".equals(status))return Problem.INPUT_MISSING;
        if(status!=null && status.startsWith("Local ADB key unavailable ("))return Problem.KEY_UNAVAILABLE;
        if("Connecting local key monitor".equals(status) || "Local key monitor reconnecting".equals(status))return Problem.READER_CONNECTING;
        return Problem.READER_FAILED;
    }
    static String tvFamily(TargetConfig.Provider provider) {
        if(provider==null)return "Not selected";
        switch(provider){case SAMSUNG:return "Samsung/TizenOS";case ROKU:return "Roku TV";
            case SONY:return "Sony BRAVIA";default:return "UPnP TV";}
    }
    static String create(State state,String version) {
        return create(state,version,0,new SupportDiagnostics(()->0));
    }
    static String create(State state,String version,int androidApi,SupportDiagnostics diagnostics) {
        // Version comes from package metadata, but still fail closed on unexpected text.
        String safeVersion=version!=null && version.matches("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}")?version:"unknown";
        return "## Problem\nWhat happened? Please describe here.\n\nExpected behavior:\n\n"
            +"## App report\nApp: onn Volume Bridge "+safeVersion+"\nDevice: onn streaming device\n"
            +"TV system (selected): "+tvFamily(state.provider)+"\nCode: "+state.problem().code
            +"\nStatus: "+state.problem().summary
            +"\nAndroid API: "+(androidApi>=26 && androidApi<=100?Integer.toString(androidApi):"unknown")
            +"\nScreen: "+new String[]{"TV setup","Remote setup","Button test","Ready"}[state.step]
            +"\nRelay enabled: "+yes(state.enabled)+"; TV connected: "+yes(state.tv)
            +"\nAccessibility: "+yes(state.service)+"; Remote reader: "+yes(state.reader)
            +"\nHDMI setting confirmed: "+yes(state.cec)
            +"\nAudio route: "+(state.hdmi==null?"not checked":state.hdmi?"HDMI":"other output")
            +"\nTV check: "+(state.tv?"connected":state.tvProblem.code)+"; Reader check: "+(state.reader?"connected":state.readerProblem.code)
            +"\n\n## Technical events (this session only)\n"+diagnostics.snapshot()
            +"\n\nNo exact model IDs, addresses, credentials or raw device logs included."
            +"\nYou may add exact device/TV models yourself if you want. Do not add private details.\n";
    }
    static String issueUrl(State state,String report) {
        return ISSUES+"?title="+encode("Volume problem "+state.problem().code)+"&body="+encode(report);
    }
    private static String encode(String text){
        try{return URLEncoder.encode(text,"UTF-8");}catch(java.io.UnsupportedEncodingException impossible){throw new AssertionError(impossible);}
    }
    private static String yes(boolean value){return value?"yes":"no";}
}
