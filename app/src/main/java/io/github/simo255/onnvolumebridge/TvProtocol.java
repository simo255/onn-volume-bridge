package io.github.simo255.onnvolumebridge;
/** Synchronous protocol contract, executed only by the target's bounded worker. */
interface TvProtocol {
    void probe() throws Exception;
    void send(VolumeKey key) throws Exception;
}
