package io.github.simo255.onnvolumebridge;
public final class TargetConfig {
    public enum Provider { SAMSUNG, ROKU, SONY, UPNP }
    public final Provider provider;
    public final String host, pin, psk, path, inputName;
    public final int port;
    public TargetConfig(Provider provider,String host,String pin,String psk,int port,String path,String inputName) {
        if(provider==null)throw new IllegalArgumentException("Choose a TV protocol");
        this.provider=provider;this.host=LocalAddress.require(host);
        this.pin=provider!=Provider.SAMSUNG || pin==null?"":pin.trim().toLowerCase(java.util.Locale.ROOT);
        this.psk=provider!=Provider.SONY || psk==null?"":psk;
        if(this.psk.length()>128 || this.psk.contains("\r") || this.psk.contains("\n"))throw new IllegalArgumentException("Invalid pre-shared key");
        if(provider==Provider.SAMSUNG && !this.pin.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Approve the Samsung certificate first");
        this.port=port==0?(provider==Provider.ROKU?8060:provider==Provider.SAMSUNG?8002:provider==Provider.SONY?80:9197):port;
        if(provider==Provider.SAMSUNG && this.port!=8002)throw new IllegalArgumentException("Samsung requires port 8002");
        if(this.port<1 || this.port>65535)throw new IllegalArgumentException("Invalid TV port");
        this.path=path==null || path.isEmpty()?"/upnp/control/RenderingControl1":path;
        if(!this.path.matches("/[A-Za-z0-9/_.,-]+") || this.path.contains(".."))throw new IllegalArgumentException("Invalid local control path");
        this.inputName=inputName==null?"":inputName.trim();
        if(this.inputName.length()>120 || this.inputName.contains("\n"))throw new IllegalArgumentException("Invalid input-device name");
    }
}
