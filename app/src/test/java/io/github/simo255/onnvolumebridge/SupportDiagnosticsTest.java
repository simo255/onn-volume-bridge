package io.github.simo255.onnvolumebridge;

import org.junit.Test;
import static org.junit.Assert.*;
import java.net.*;
import java.io.*;
import java.security.cert.CertificateException;
import javax.net.ssl.SSLHandshakeException;

public class SupportDiagnosticsTest {
    @Test public void classificationNeverReadsPrivateExceptionMessages() {
        String privateText="192.168.1.25 token=SECRET model-id serial=PRIVATE";
        SupportDiagnostics journal=new SupportDiagnostics(()->0);
        journal.failure(SupportDiagnostics.Source.TV,new SocketTimeoutException(privateText));
        journal.failure(SupportDiagnostics.Source.READER,new ConnectException(privateText));
        assertTrue(journal.snapshot().contains("TV/TIMEOUT"));
        assertTrue(journal.snapshot().contains("READER/CONNECTION_REFUSED"));
        assertFalse(journal.snapshot().contains(privateText));
        IOException bomb=new IOException(){@Override public String getMessage(){throw new AssertionError("Raw error message read");}};
        journal.failure(SupportDiagnostics.Source.SETUP,bomb);
        assertTrue(journal.snapshot().contains("SETUP/NETWORK_IO"));
    }
    @Test public void certificateCauseTakesPriorityOverGenericTlsFailure() {
        SSLHandshakeException error=new SSLHandshakeException("private certificate detail");
        error.initCause(new CertificateException("private fingerprint"));
        assertEquals(SupportDiagnostics.Kind.CERTIFICATE,SupportDiagnostics.classify(error));
    }
    @Test public void eventsAreBoundedDeduplicatedAndOnlyHaveRelativeTiming() {
        long[] now={1_000_000};SupportDiagnostics journal=new SupportDiagnostics(()->now[0]);
        journal.failure(SupportDiagnostics.Source.TV,new SupportDiagnostics.Failure(SupportDiagnostics.Kind.HTTP_DENIED,403));
        for(int i=0;i<20;i++){
            now[0]+=1000;
            journal.event(SupportDiagnostics.Source.READER,i%2==0?SupportDiagnostics.Kind.CONNECTING:SupportDiagnostics.Kind.CONNECTED);
        }
        journal.event(SupportDiagnostics.Source.READER,SupportDiagnostics.Kind.CONNECTED);
        String snapshot=journal.snapshot();
        assertEquals(7,snapshot.split("\n").length); // One retained failure and the six most recent transitions.
        assertTrue(snapshot.contains("Last failure: TV/HTTP_DENIED/403"));
        assertFalse(snapshot.contains("1000000"));assertTrue(snapshot.contains("-0s READER/CONNECTED"));
    }
    @Test public void fullTechnicalReportStillFitsAReadableQr() throws Exception {
        SupportDiagnostics journal=new SupportDiagnostics(()->0);
        for(SupportDiagnostics.Source source:SupportDiagnostics.Source.values())journal.failure(source,SupportDiagnostics.Operation.DISCOVER,new SupportDiagnostics.Failure(SupportDiagnostics.Kind.CONNECTION_REFUSED,599));
        journal.event(SupportDiagnostics.Source.READER,SupportDiagnostics.Kind.APPROVAL_NEEDED);
        journal.event(SupportDiagnostics.Source.TV,SupportDiagnostics.Kind.APPROVAL_NEEDED);
        ProblemReport.State state=new ProblemReport.State(TargetConfig.Provider.SAMSUNG,true,true,true,false,true,true,true,true,true,2,"Connected","");
        String report=ProblemReport.create(state,"123.123.123",36,journal);
        assertTrue(report.contains("Android API: 36"));assertTrue(report.contains("TV check: connected"));
        String url=ProblemReport.issueUrl(state,report);
        assertTrue("URL length: "+url.length(),url.length()<2400);
        assertNotNull(ReportQr.encode(url));assertNotNull(ReportQr.encode(report));
        assertTrue(new SupportDiagnostics(()->0).snapshot().contains("No technical events"));
    }
}
