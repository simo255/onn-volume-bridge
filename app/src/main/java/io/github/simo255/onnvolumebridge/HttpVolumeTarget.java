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
    private volatile String status="Not connected";
    HttpVolumeTarget(TvProtocol protocol){this.protocol=protocol;}
    @Override public void connect() {
        if(closed || ready || !probing.compareAndSet(false,true))return;
        if(!enqueue(()->{try {if(!closed){status="Checking TV API";protocol.probe();ready=!closed;status="TV API reachable; verify the volume buttons";}}
            catch(Exception e){fail();}finally{probing.set(false);}}))probing.set(false);
    }
    @Override public boolean ready(){return ready && !closed;}
    @Override public String status(){return status;}
    @Override public void key(VolumeKey key) {
        if(closed)return;long created=System.nanoTime();
        enqueue(()->{
            if(closed || System.nanoTime()-created>TimeUnit.SECONDS.toNanos(1))return;
            try {
                if(!ready){protocol.probe();ready=!closed;}
                if(closed || System.nanoTime()-created>TimeUnit.SECONDS.toNanos(1))return;
                protocol.send(key);status="Volume command accepted by TV API";
            }catch(Exception e){fail();}
        });
    }
    private void fail(){ready=false;status="TV request failed. Check network control, pairing and protocol compatibility.";}
    private boolean enqueue(Runnable task){try{worker.execute(task);return true;}catch(RejectedExecutionException e){status="Busy; old volume presses were dropped";return false;}}
    @Override public void close(){closed=true;ready=false;worker.getQueue().clear();worker.shutdownNow();status="Disconnected";}
}
