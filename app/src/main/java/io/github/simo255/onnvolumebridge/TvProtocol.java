package io.github.simo255.onnvolumebridge;
/** Synchronous protocol contract, executed only by the target's bounded worker. */
interface TvProtocol {
    void probe() throws Exception;
    void send(VolumeKey key) throws Exception;
    default boolean supportsNativeHold() { return false; }
    default void press(VolumeKey key) throws Exception { throw new UnsupportedOperationException(); }
    default void release(VolumeKey key) throws Exception { throw new UnsupportedOperationException(); }
}
