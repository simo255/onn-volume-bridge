package io.github.simo255.onnvolumebridge;
/** Literal private IPv4 endpoints only; never resolves a hostname or sends commands to the Internet. */
public final class LocalAddress {
    private LocalAddress() { }
    public static String require(String input) {
        String host = input == null ? "" : input.trim();
        if (!host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) throw new IllegalArgumentException("Enter a local IPv4 address");
        String[] parts = host.split("\\."); int[] n = new int[4];
        for (int i=0;i<4;i++) {
            if (parts[i].length()>1 && parts[i].startsWith("0")) throw new IllegalArgumentException("Ambiguous address");
            n[i]=Integer.parseInt(parts[i]); if(n[i]>255)throw new IllegalArgumentException("Invalid address");
        }
        if (!(n[0]==10 || (n[0]==172 && n[1]>=16 && n[1]<=31) || (n[0]==192 && n[1]==168)))
            throw new IllegalArgumentException("Use a private local-network IPv4 address");
        return host;
    }
}
