package io.github.simo255.onnvolumebridge;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
/** All API operations run on one bounded worker. Old queued key presses are dropped. */
final class HttpVolumeTarget implements VolumeTarget {
    private final TvProtocol protocol;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(6),r->{Thread t=new Thread(r,"OnnVolumeBridge-TV");t.setDaemon(true);return t;});
    private final AtomicBoolean probing=new AtomicBoolean();
    private volatile boolean ready,closed;
    private Hold requestedHold; // Guarded by this; reserve a queue slot for its release.
    private Hold activeHold; // Worker thread only, including uncertain/failed presses.
    private static final class Hold {
        final VolumeKey key; final long created=System.nanoTime();
        Hold(VolumeKey key){this.key=key;}
    }
    private volatile String status="Not connected";
    HttpVolumeTarget(TvProtocol protocol){this.protocol=protocol;}
    @Override public synchronized void connect() {
        if(closed || ready || requestedHold!=null || !probing.compareAndSet(false,true))return;
        if(!enqueue(()->{try {if(!closed){status="Checking TV API";SupportDiagnostics.LIVE.event(SupportDiagnostics.Source.TV,SupportDiagnostics.Kind.CONNECTING);protocol.probe();ready=!closed;status="TV API reachable; verify the volume buttons";
            if(ready)SupportDiagnostics.LIVE.event(SupportDiagnostics.Source.TV,SupportDiagnostics.Kind.CONNECTED);}}
            catch(Exception e){fail(e,SupportDiagnostics.Operation.PROBE);}finally{probing.set(false);}}))probing.set(false);
    }
    @Override public boolean ready(){return ready && !closed;}
    @Override public String status(){return status;}
    @Override public synchronized void key(VolumeKey key) {
        if(closed || (requestedHold!=null && worker.getQueue().remainingCapacity()<=1))return;long created=System.nanoTime();
        enqueue(()->{
            if(closed || System.nanoTime()-created>TimeUnit.SECONDS.toNanos(1))return;
            try {
                if(!ready){protocol.probe();ready=!closed;}
                if(closed || System.nanoTime()-created>TimeUnit.SECONDS.toNanos(1))return;
                protocol.send(key);status="Volume command accepted by TV API";
            }catch(Exception e){fail(e,SupportDiagnostics.Operation.COMMAND);}
        });
    }
    @Override public synchronized boolean startHold(VolumeKey key) {
        if(closed || !ready || !protocol.supportsNativeHold() || key==VolumeKey.MUTE)return false;
        if(requestedHold!=null)return requestedHold.key==key;
        if(worker.getQueue().remainingCapacity()<2)return false;
        Hold hold=new Hold(key);
        if(!enqueue(()->{
            if(closed || System.nanoTime()-hold.created>TimeUnit.SECONDS.toNanos(1))return;
            activeHold=hold; // A timeout can occur after the TV has accepted keydown.
            try{protocol.press(key);}catch(Exception e){fail(e,SupportDiagnostics.Operation.PRESS);}
        }))return false;
        requestedHold=hold;return true;
    }
    @Override public synchronized void endHold() {
        Hold hold=requestedHold;
        if(hold==null)return;
        // Ordinary clicks cannot consume the reserved release slot. Releases never expire.
        enqueue(()->release(hold));
        requestedHold=null;
    }
    private void release(Hold hold) {
        if(activeHold!=hold)return;
        try{protocol.release(hold.key);}catch(Exception e){fail(e,SupportDiagnostics.Operation.RELEASE);}
        finally{activeHold=null;}
    }
    private void fail(Exception error,SupportDiagnostics.Operation operation){ready=false;status="TV request failed. Check network control, pairing and protocol compatibility.";SupportDiagnostics.LIVE.failure(SupportDiagnostics.Source.TV,operation,error);}
    private boolean enqueue(Runnable task){try{worker.execute(task);return true;}catch(RejectedExecutionException e){status="Busy; old volume presses were dropped";SupportDiagnostics.LIVE.failure(SupportDiagnostics.Source.TV,new SupportDiagnostics.Failure(SupportDiagnostics.Kind.BUSY));return false;}}
    @Override public synchronized void close(){
        if(closed)return;
        closed=true;ready=false;requestedHold=null;
        worker.getQueue().clear();
        // Serialize cleanup after any in-flight keydown instead of interrupting it.
        enqueue(()->{if(activeHold!=null)release(activeHold);status="Disconnected";});
        worker.shutdown();status="Disconnected";
    }
}
