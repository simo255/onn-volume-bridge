package io.github.simo255.onnvolumebridge;
interface VolumeTarget {
    void connect();
    boolean ready();
    String status();
    void key(VolumeKey key);
    void close();
}
