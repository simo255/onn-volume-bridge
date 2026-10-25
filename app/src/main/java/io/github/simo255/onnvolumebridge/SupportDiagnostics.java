package io.github.simo255.onnvolumebridge;

import java.io.*;
import java.net.*;
import java.security.cert.CertificateException;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.function.LongSupplier;
import javax.net.ssl.SSLException;

/** Bounded in-memory technical events, not device logs. No free-form text is accepted. */
final class SupportDiagnostics {
    enum Source { TV, READER, SETUP, DISCOVERY }
    enum Operation { STATUS, CONNECT, PROBE, COMMAND, PRESS, RELEASE, READ, SETUP, DISCOVER, ENROLL }
    enum Kind {
        CONNECTING,CONNECTED,APPROVAL_NEEDED,DISCONNECTED,INPUT_MISSING,STARTED,FINISHED,NO_RESULTS,
        TIMEOUT,CONNECTION_REFUSED,CERTIFICATE,TLS,CONNECTION_CLOSED,NETWORK_IO,
        HTTP_DENIED,HTTP_NOT_FOUND,HTTP_SERVER,HTTP_REDIRECT,HTTP_OTHER,
        RESPONSE_TOO_LARGE,API_REJECTED,INCOMPATIBLE,INVALID_RESPONSE,KEY_UNAVAILABLE,BUSY,OTHER_FAILURE
    }
    static final SupportDiagnostics LIVE=new SupportDiagnostics(()->System.nanoTime()/1_000_000);
    static final class Failure extends IOException {
        final Kind kind;final int http;
        Failure(Kind kind){this(kind,0);}
        Failure(Kind kind,int http){super(kind.name());this.kind=kind;this.http=http>=100 && http<=599?http:0;}
    }
    private static final class Event {
        final Source source;final Operation operation;final Kind kind;final int http;final long at;
        Event(Source source,Operation operation,Kind kind,int http,long at){this.source=source;this.operation=operation;this.kind=kind;this.http=http;this.at=at;}
        String code(){return source+"/"+(operation==Operation.STATUS?"":operation+"/")+kind+(http==0?"":"/"+http);}
    }
    private final ArrayDeque<Event> events=new ArrayDeque<>();
    private final EnumMap<Source,Event> failures=new EnumMap<>(Source.class);
    private final LongSupplier clock;
    SupportDiagnostics(LongSupplier clock){this.clock=clock;}
    synchronized void event(Source source,Kind kind){record(source,Operation.STATUS,kind,0,false);}
    synchronized void failure(Source source,Throwable error){failure(source,Operation.STATUS,error);}
    synchronized void failure(Source source,Operation operation,Throwable error){
        Kind kind=classify(error);int http=0;
        for(int i=0;error!=null && i<8;i++,error=error.getCause())if(error instanceof Failure){http=((Failure)error).http;break;}
        record(source,operation,kind,http,true);
    }
    private void record(Source source,Operation operation,Kind kind,int http,boolean failure) {
        Event entry=new Event(source,operation,kind,http,clock.getAsLong());
        if(failure)failures.put(source,entry);
        Event last=events.peekLast();
        if(last!=null && last.source==source && last.operation==operation && last.kind==kind && last.http==http)return;
        if(events.size()==6)events.removeFirst();events.addLast(entry);
    }
    synchronized String snapshot() {
        long now=clock.getAsLong();StringBuilder out=new StringBuilder();
        for(Event failure:failures.values())out.append("Last failure: ").append(failure.code()).append('\n');
        for(Event event:events)out.append("-").append(Math.min(9999,Math.max(0,(now-event.at)/1000)))
            .append("s ").append(event.code()).append('\n');
        return out.length()==0?"No technical events recorded in this session.\n":out.toString();
    }
    synchronized Kind lastFailure(Source source){Event event=failures.get(source);return event==null?null:event.kind;}
    static Kind classify(Throwable error) {
        Kind fallback=Kind.OTHER_FAILURE;
        for(int i=0;error!=null && i<8;i++,error=error.getCause()) {
            if(error instanceof Failure)return ((Failure)error).kind;
            if(error instanceof CertificateException)return Kind.CERTIFICATE;
            if(error instanceof SocketTimeoutException)return Kind.TIMEOUT;
            if(error instanceof ConnectException)return Kind.CONNECTION_REFUSED;
            if(error instanceof EOFException)return Kind.CONNECTION_CLOSED;
            if(error instanceof org.json.JSONException || error instanceof org.xml.sax.SAXException || error instanceof NumberFormatException)return Kind.INVALID_RESPONSE;
            if(error instanceof SSLException)fallback=Kind.TLS;
            else if(error instanceof IOException && fallback==Kind.OTHER_FAILURE)fallback=Kind.NETWORK_IO;
        }
        return fallback;
    }
}
