package io.github.simo255.onnvolumebridge;
import java.net.*;
import java.security.*;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;
import java.util.Locale;
/** First enrollment only. The UI requires explicit approval of this local certificate before saving. */
final class EnrollSamsung {
    @android.annotation.SuppressLint({"CustomX509TrustManager","TrustAllX509TrustManager"})
    static String fingerprint(String address) throws Exception {
        String host=LocalAddress.require(address);
        SSLContext tls=SSLContext.getInstance("TLS");
        tls.init(null,new TrustManager[]{new X509TrustManager(){
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[] chain,String authType) throws java.security.cert.CertificateException{throw new java.security.cert.CertificateException();}
            public void checkServerTrusted(X509Certificate[] chain,String authType) throws java.security.cert.CertificateException{if(chain==null || chain.length==0)throw new java.security.cert.CertificateException();}
        }},new SecureRandom());
        try(java.net.Socket raw=new java.net.Socket()) {
            raw.connect(new InetSocketAddress(host,8002),4000);raw.setSoTimeout(5000);
            try(SSLSocket socket=(SSLSocket)tls.getSocketFactory().createSocket(raw,host,8002,true)) {
                socket.setSoTimeout(5000);socket.startHandshake();
                byte[] hash=MessageDigest.getInstance("SHA-256").digest(socket.getSession().getPeerCertificates()[0].getEncoded());
                StringBuilder out=new StringBuilder();for(byte b:hash)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
            }
        }
    }
}
