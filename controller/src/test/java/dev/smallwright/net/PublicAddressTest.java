package dev.smallwright.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import org.junit.Test;

public final class PublicAddressTest {
    private static boolean isPublic(String literal) throws Exception {
        return PublicAddress.isPublic(InetAddress.getByName(literal));
    }

    @Test
    public void acceptsPublicAddresses() throws Exception {
        for (String address :
                List.of(
                        "8.8.8.8",
                        "142.250.180.14",
                        "1.1.1.1",
                        "100.63.255.255",
                        "100.128.0.1",
                        "172.15.255.255",
                        "172.32.0.1",
                        "2a00:1450:4001:82a::200e",
                        "2606:4700::1111")) assertTrue(address, isPublic(address));
    }

    @Test
    public void refusesLoopbackPrivateLinkLocalMulticastAndOtherNonPublicAddresses()
            throws Exception {
        for (String address :
                List.of(
                        "127.0.0.1",
                        "127.255.0.9",
                        "0.0.0.0",
                        "0.1.2.3",
                        "10.0.0.1",
                        "172.16.0.1",
                        "172.31.255.255",
                        "192.168.1.1",
                        "169.254.169.254",
                        "100.64.0.1",
                        "100.127.255.255",
                        "192.0.0.8",
                        "192.0.2.1",
                        "198.18.0.1",
                        "198.51.100.7",
                        "203.0.113.9",
                        "224.0.0.1",
                        "239.255.255.250",
                        "240.0.0.1",
                        "255.255.255.255",
                        "::1",
                        "::",
                        "::ffff:127.0.0.1",
                        "::ffff:10.0.0.1",
                        "::127.0.0.1",
                        "fe80::1",
                        "fec0::1",
                        "fc00::1",
                        "fd12:3456::1",
                        "ff02::1",
                        "64:ff9b::7f00:1",
                        "64:ff9b::a00:1",
                        "2002:7f00:1::1",
                        "2002:c0a8:101::1",
                        "2001:db8::1",
                        "100::1")) assertFalse(address, isPublic(address));
    }

    @Test
    public void addressesTranslatedFromPublicIpv4AreAcceptedLikeTheirIpv4Address()
            throws Exception {
        assertTrue(isPublic("64:ff9b::808:808"));
        assertTrue(isPublic("2002:808:808::1"));
    }

    @Test
    public void theTlsLayerRefusesASocketConnectedToALoopbackAddressBeforeSendingAnything()
            throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                Socket raw = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort());
                Socket accepted = server.accept()) {
            IOException refused =
                    assertThrows(
                            IOException.class,
                            () ->
                                    PublicAddress.SOCKETS.createSocket(
                                            raw, "docs.example.com", server.getLocalPort(), true));
            assertEquals(PublicAddress.NOT_PUBLIC, refused.getMessage());
            assertTrue(raw.isClosed());
            accepted.setSoTimeout(5_000);
            try (InputStream received = accepted.getInputStream()) {
                assertEquals(-1, received.read());
            }
        }
    }
}
