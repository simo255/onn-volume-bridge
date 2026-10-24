package io.github.simo255.onnvolumebridge;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** User-initiated SSDP discovery only. No subnet scan, HTTP fetch, pairing or volume commands. */
final class TvDiscovery {
    static final class Candidate {
        final String host;
        final TargetConfig.Provider provider;
        Candidate(String host, TargetConfig.Provider provider) { this.host=host;this.provider=provider; }
    }
    static List<Candidate> search() throws Exception {
        Map<String,Candidate> found=new LinkedHashMap<>();
        try(DatagramSocket socket=new DatagramSocket()) {
            socket.setSoTimeout(350);
            String query="M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n";
            byte[] bytes=query.getBytes(StandardCharsets.US_ASCII);
            socket.send(new DatagramPacket(bytes,bytes.length,InetAddress.getByName("239.255.255.250"),1900));
            long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
            byte[] buffer=new byte[8192];
            int received=0;
            while(System.nanoTime()<end && received<128 && !Thread.currentThread().isInterrupted()) {
                DatagramPacket response=new DatagramPacket(buffer,buffer.length);
                try{socket.receive(response);}catch(SocketTimeoutException ignored){continue;}
                received++;
                Candidate candidate=parse(new String(response.getData(),response.getOffset(),response.getLength(),StandardCharsets.US_ASCII),response.getAddress().getHostAddress());
                if(candidate!=null && found.size()<16) {
                    Candidate old=found.get(candidate.host);
                    if(old==null || old.provider==null)found.put(candidate.host,candidate);
                }
            }
        }
        return new ArrayList<>(found.values());
    }
    static Candidate parse(String packet,String sender) {
        try {
            String host=LocalAddress.require(sender);
            String[] lines=packet.split("\r?\n");
            if(lines.length==0 || !lines[0].startsWith("HTTP/1.1 200"))return null;
            Map<String,String> headers=new HashMap<>();
            for(int i=1;i<lines.length;i++){int colon=lines[i].indexOf(':');if(colon>0)headers.put(lines[i].substring(0,colon).trim().toLowerCase(Locale.ROOT),lines[i].substring(colon+1).trim());}
            String st=headers.getOrDefault("st","").toLowerCase(Locale.ROOT);
            String server=headers.getOrDefault("server","").toLowerCase(Locale.ROOT);
            if(!st.contains("mediarenderer") && !st.equals("roku:ecp") && !server.contains("samsung") && !server.contains("sony"))return null;
            // Trust neither arbitrary LOCATION URLs nor friendly names from network packets.
            TargetConfig.Provider type=st.equals("roku:ecp")?TargetConfig.Provider.ROKU:
                server.contains("samsung")?TargetConfig.Provider.SAMSUNG:server.contains("sony")?TargetConfig.Provider.SONY:null;
            return new Candidate(host,type);
        }catch(RuntimeException ignored){return null;}
    }
}
