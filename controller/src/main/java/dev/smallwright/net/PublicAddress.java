package dev.smallwright.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

/**
 * Which addresses count as the public internet, and a TLS socket factory that refuses every other
 * address it finds a connection made to, before a byte is sent.
 */
public final class PublicAddress {
    public static final String NOT_PUBLIC =
            "That address is not on the public internet, so the builder does not open it.";

    /** IPv4 blocks that are not public internet addresses, as address and prefix length. */
    private static final int[][] NOT_PUBLIC_IPV4 = {
        {0x00000000, 8},
        {0x0A000000, 8},
        {0x64400000, 10},
        {0x7F000000, 8},
        {0xA9FE0000, 16},
        {0xAC100000, 12},
        {0xC0000000, 24},
        {0xC0000200, 24},
        {0xC0A80000, 16},
        {0xC6120000, 15},
        {0xC6336400, 24},
        {0xCB007100, 24},
        {0xE0000000, 3},
    };

    /** Opens TLS only over sockets connected to a public address. */
    public static final SSLSocketFactory SOCKETS =
            new Sockets(HttpsURLConnection.getDefaultSSLSocketFactory());

    private PublicAddress() {}

    public static boolean isPublic(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) return isPublicIpv4(bytes, 0);
        int first = (bytes[0] & 0xFF) << 8 | (bytes[1] & 0xFF);
        int second = (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
        if (first == 0x0064 && second == 0xFF9B && zero(bytes, 4, 12))
            return isPublicIpv4(bytes, 12);
        if (first == 0x2002) return isPublicIpv4(bytes, 2);
        if (first == 0x2001 && (second == 0x0DB8 || second == 0)) return false;
        return (first & 0xE000) == 0x2000;
    }

    private static boolean isPublicIpv4(byte[] bytes, int at) {
        int value =
                (bytes[at] & 0xFF) << 24
                        | (bytes[at + 1] & 0xFF) << 16
                        | (bytes[at + 2] & 0xFF) << 8
                        | (bytes[at + 3] & 0xFF);
        for (int[] block : NOT_PUBLIC_IPV4) {
            int mask = -1 << (32 - block[1]);
            if ((value & mask) == block[0]) return false;
        }
        return true;
    }

    private static boolean zero(byte[] bytes, int from, int to) {
        for (int index = from; index < to; index++) if (bytes[index] != 0) return false;
        return true;
    }

    /**
     * Every address {@code host} has, refused unless all of them are public. An address written as
     * the host is checked as it is, without a lookup.
     */
    public static InetAddress[] resolvePublic(String host, Resolver resolver) throws IOException {
        String bare =
                host.startsWith("[") && host.endsWith("]")
                        ? host.substring(1, host.length() - 1)
                        : host;
        InetAddress[] addresses;
        try {
            addresses =
                    bare.indexOf(':') >= 0 || bare.matches("[0-9.]+")
                            ? new InetAddress[] {InetAddress.getByName(bare)}
                            : resolver.resolve(bare);
        } catch (UnknownHostException unknown) {
            throw new IOException("There is no website at " + host + ".");
        }
        if (addresses.length == 0) throw new IOException("There is no website at " + host + ".");
        for (InetAddress address : addresses)
            if (!isPublic(address)) throw new IOException(NOT_PUBLIC);
        return addresses;
    }

    /** Looks up a host's addresses. */
    public interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private static final class Sockets extends SSLSocketFactory {
        private final SSLSocketFactory tls;

        Sockets(SSLSocketFactory tls) {
            this.tls = tls;
        }

        private static Socket checked(Socket socket) throws IOException {
            InetAddress peer = socket.getInetAddress();
            if (peer == null || !isPublic(peer)) {
                socket.close();
                throw new IOException(NOT_PUBLIC);
            }
            return socket;
        }

        @Override
        public Socket createSocket(Socket connected, String host, int port, boolean autoClose)
                throws IOException {
            checked(connected);
            return tls.createSocket(connected, host, port, autoClose);
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return checked(tls.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress local, int localPort)
                throws IOException {
            return checked(tls.createSocket(host, port, local, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return checked(tls.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress local, int localPort)
                throws IOException {
            return checked(tls.createSocket(address, port, local, localPort));
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return tls.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return tls.getSupportedCipherSuites();
        }
    }
}
