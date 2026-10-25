package io.github.simo255.onnvolumebridge;

import com.google.zxing.*;
import com.google.zxing.common.*;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReportQrTest {
    @Test public void phoneCanDecodePlainRedditTextAndTheExactGitHubDraft() throws Exception {
        ProblemReport.State s=new ProblemReport.State(TargetConfig.Provider.SAMSUNG,true,true,true,false,true,true,true,true,true,2,"Connected","");
        String report=ProblemReport.create(s,"0.2.1");
        for(String payload:new String[]{report,ProblemReport.issueUrl(s,report)}) {
            BitMatrix qr=ReportQr.encode(payload);
            int size=qr.getWidth()*4;int[] pixels=new int[size*size];
            for(int y=0;y<size;y++)for(int x=0;x<size;x++)pixels[y*size+x]=qr.get(x/4,y/4)?0xff000000:0xffffffff;
            BinaryBitmap image=new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(size,size,pixels)));
            assertEquals(payload,new QRCodeReader().decode(image).getText());
        }
    }
    @Test public void unboundedPayloadsAndArbitraryDestinationsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->ReportQr.encode(null));
        assertThrows(IllegalArgumentException.class,()->ReportQr.encode("https://private.example/"));
        assertThrows(IllegalArgumentException.class,()->ReportQr.encode(ProblemReport.ISSUES+"?"+new String(new char[2400]).replace('\0','x')));
    }
}
