package io.github.simo255.onnvolumebridge;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

public class HttpVolumeTargetTest {
 static final class Protocol implements TvProtocol {
  final List<String> events=new CopyOnWriteArrayList<>();
  final CountDownLatch entered=new CountDownLatch(1), gate=new CountDownLatch(1);
  volatile boolean blocked, failPress, nativeSupported=true;
  final java.util.concurrent.atomic.AtomicInteger probes=new java.util.concurrent.atomic.AtomicInteger();
  public void probe() { probes.incrementAndGet(); }
  public void send(VolumeKey key) { events.add("click:"+key); }
  public boolean supportsNativeHold() { return nativeSupported; }
  public void press(VolumeKey key) throws Exception {
   events.add("press:"+key);entered.countDown();
   if(blocked && !gate.await(3,TimeUnit.SECONDS))throw new TimeoutException();
   if(failPress)throw new java.io.IOException("Uncertain keydown delivery");
  }
  public void release(VolumeKey key) { events.add("release:"+key); }
 }
 static void await(BooleanSupplier condition) throws Exception {
  long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
  while(!condition.getAsBoolean() && System.nanoTime()<end)Thread.sleep(2);
  assertTrue("Timed out waiting for worker",condition.getAsBoolean());
 }
 static HttpVolumeTarget connected(Protocol p) throws Exception {
  HttpVolumeTarget target=new HttpVolumeTarget(p);target.connect();await(target::ready);return target;
 }
 @Test public void downUpAndDirectionChangesStayOrdered() throws Exception {
  Protocol p=new Protocol();HttpVolumeTarget t=connected(p);
  try {
   assertTrue(t.startHold(VolumeKey.UP));t.endHold();assertTrue(t.startHold(VolumeKey.DOWN));t.endHold();
   await(()->p.events.size()==4);
   assertEquals(Arrays.asList("press:UP","release:UP","press:DOWN","release:DOWN"),p.events);
  } finally { t.close(); }
 }
 @Test public void floodCannotConsumeReservedReleaseSlot() throws Exception {
  Protocol p=new Protocol();p.blocked=true;HttpVolumeTarget t=connected(p);
  try {
   assertTrue(t.startHold(VolumeKey.DOWN));assertTrue(p.entered.await(3,TimeUnit.SECONDS));
   for(int i=0;i<100;i++)t.key(VolumeKey.MUTE);
   t.endHold();p.gate.countDown();await(()->p.events.contains("release:DOWN"));
   assertEquals(7,p.events.size());assertEquals("release:DOWN",p.events.get(6));
  } finally { p.gate.countDown();t.close(); }
 }
 @Test public void closeReleasesAnInflightPressInsteadOfDroppingIt() throws Exception {
  Protocol p=new Protocol();p.blocked=true;HttpVolumeTarget t=connected(p);
  try {
   assertTrue(t.startHold(VolumeKey.UP));assertTrue(p.entered.await(3,TimeUnit.SECONDS));
   t.close();p.gate.countDown();await(()->p.events.size()==2);
   assertEquals(Arrays.asList("press:UP","release:UP"),p.events);assertFalse(t.startHold(VolumeKey.DOWN));
  } finally { p.gate.countDown();t.close(); }
 }
 @Test public void failedPressStillAttemptsReleaseBecauseDeliveryIsUncertain() throws Exception {
  Protocol p=new Protocol();p.failPress=true;HttpVolumeTarget t=connected(p);
  try { assertTrue(t.startHold(VolumeKey.UP));t.endHold();await(()->p.events.size()==2);assertEquals("release:UP",p.events.get(1)); }
  finally { t.close(); }
 }
 @Test public void unsupportedTargetsMuteAndDisconnectedTargetsDeclineNativeHold() throws Exception {
  Protocol p=new Protocol();p.nativeSupported=false;HttpVolumeTarget t=connected(p);
  try { assertFalse(t.startHold(VolumeKey.UP));assertTrue(p.events.isEmpty()); }
  finally { t.close(); }
  p=new Protocol();t=connected(p);
  try { assertFalse(t.startHold(VolumeKey.MUTE)); }
  finally { t.close(); }
  t=new HttpVolumeTarget(new Protocol());try { assertFalse(t.startHold(VolumeKey.UP)); }finally {t.close();}
 }
 @Test public void reconnectCannotTakeTheReleaseSlotAfterAnUncertainPress() throws Exception {
  Protocol p=new Protocol();p.failPress=true;HttpVolumeTarget t=connected(p);
  try {
   assertTrue(t.startHold(VolumeKey.DOWN));await(()->!t.ready());
   t.connect();t.endHold();await(()->p.events.contains("release:DOWN"));
   assertEquals(1,p.probes.get());
  } finally { t.close(); }
 }
}
