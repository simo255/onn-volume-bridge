package io.github.simo255.onnvolumebridge;
interface VolumeTarget {
    void connect();
    boolean ready();
    String status();
    void key(VolumeKey key);
    default boolean startHold(VolumeKey key) { return false; }
    default void endHold() { }
    void close();
}
