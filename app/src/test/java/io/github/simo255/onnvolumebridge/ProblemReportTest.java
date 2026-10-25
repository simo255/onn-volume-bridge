package io.github.simo255.onnvolumebridge;

import org.junit.Test;
import static org.junit.Assert.*;
import java.net.URI;
import java.net.URLDecoder;

public class ProblemReportTest {
    private ProblemReport.State state(boolean enabled,boolean setup,boolean tv,boolean service,boolean reader,
                                      boolean cec,Boolean hdmi,String tvStatus,String readerStatus) {
        return new ProblemReport.State(TargetConfig.Provider.SAMSUNG,true,true,enabled,setup,tv,service,reader,cec,hdmi,1,tvStatus,readerStatus);
    }
    @Test public void pausedPairingStillExplainsTvApprovalWithoutEnablingForwarding() {
        ProblemReport.State waiting=state(false,true,false,true,false,true,true,"Waiting for TV approval","");
        assertEquals(ProblemReport.Problem.TV_APPROVAL,waiting.problem());
        assertTrue(ProblemReport.create(waiting,"0.2.1").contains("Relay enabled: no; TV connected: no"));
        assertEquals(ProblemReport.Problem.PAUSED,state(false,false,false,true,false,true,true,"Disconnected","").problem());
    }
    @Test public void readinessExplainsPermissionReaderAudioAndHdmiBlockers() {
        assertEquals(ProblemReport.Problem.ACCESSIBILITY,state(true,false,true,false,false,false,true,"Connected","").problem());
        assertEquals(ProblemReport.Problem.DEBUG_APPROVAL,state(true,false,true,true,false,true,true,"Connected","Approve USB debugging for onn Volume on this box").problem());
        assertEquals(ProblemReport.Problem.INPUT_MISSING,state(true,false,true,true,false,true,true,"Connected","Onn keyboard volume input not found").problem());
        assertEquals(ProblemReport.Problem.OTHER_AUDIO,state(true,false,true,true,true,true,false,"Connected","").problem());
        assertEquals(ProblemReport.Problem.HDMI_SETTING,state(true,false,true,true,true,false,true,"Connected","").problem());
        assertEquals(ProblemReport.Problem.READY,state(true,false,true,true,true,true,true,"Connected","").problem());
    }
    @Test public void unsavedFormIsDistinguishedFromAPairingFailure() {
        ProblemReport.State changed=new ProblemReport.State(TargetConfig.Provider.SAMSUNG,true,false,true,false,true,true,true,true,true,0,"Connected","");
        assertEquals(ProblemReport.Problem.UNSAVED,changed.problem());
    }
    @Test public void rawStatusAndVersionCannotLeakAddressesIdsOrSecrets() {
        String poison="192.168.1.25 device-id SECRET token=secret /dev/input/event7 https://private.example/";
        ProblemReport.State s=state(true,false,false,true,false,true,null,poison,poison);
        String report=ProblemReport.create(s,poison);
        for(String forbidden:new String[]{"192.168","device-id","SECRET","token=","/dev/","private.example"})assertFalse(report.contains(forbidden));
        assertTrue(report.contains("Device: onn streaming device"));
        assertTrue(report.contains("TV system (selected): Samsung/TizenOS"));
        assertTrue(report.contains("App: onn Volume Bridge unknown"));
        assertEquals(ProblemReport.Problem.KEY_UNAVAILABLE,ProblemReport.readerProblem("Local ADB key unavailable ("+poison+")"));
        assertFalse(ProblemReport.create(state(true,false,true,true,false,true,true,"Connected","Local ADB key unavailable ("+poison+")"),"0.2.1").contains(poison));
    }
    @Test public void issueLinkPrefillsOnlyTheReviewedReportAtTheFixedRepository() throws Exception {
        ProblemReport.State s=state(true,false,true,true,true,true,true,"Connected","");
        String report=ProblemReport.create(s,"0.2.1"),link=ProblemReport.issueUrl(s,report);
        URI uri=new URI(link);
        assertEquals("https",uri.getScheme());assertEquals("github.com",uri.getHost());
        assertEquals("/simo255/onn-volume-bridge/issues/new",uri.getPath());
        String body=uri.getRawQuery().split("&body=",2)[1];
        assertEquals(report,URLDecoder.decode(body,"UTF-8"));
        assertTrue(link.length()<2400);
        assertFalse(link.contains("api.github.com")); // Browser draft only; no issue-creation API.
    }
}
