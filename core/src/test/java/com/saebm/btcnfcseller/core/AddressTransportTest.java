package com.saebm.btcnfcseller.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class AddressTransportTest {
    private static final String ADDRESS = "bc1qqqqsyqcyq5rqwzqfpg9scrgwpugpzysn4v0345";
    @Test public void sendsOneGetWithNoCookiesAndReturnsBoundedBody() throws Exception {
        try (java.net.ServerSocket server = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            java.util.concurrent.FutureTask<String> request = new java.util.concurrent.FutureTask<>(() -> {
                try (java.net.Socket socket = server.accept()) {
                    java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream()));
                    StringBuilder headers = new StringBuilder(); String line;
                    while ((line = in.readLine()) != null && !line.isEmpty()) headers.append(line).append("\n");
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\n{}".getBytes());
                    return headers.toString();
                }
            });
            Thread peer = new Thread(request); peer.setDaemon(true); peer.start();
            AddressTransport transport;
            try { transport = AddressTransport.class.getDeclaredConstructor(String.class, int.class, boolean.class, int.class)
                .newInstance("127.0.0.1", server.getLocalPort(), false, 1000); }
            catch (NoSuchMethodException e) { throw new AssertionError("Missing isolated socket transport seam", e); }
            AddressTransport.Response response = transport.lookup(ADDRESS);
            assertEquals(200, response.status); assertEquals("{}", response.body);
            String wire = request.get(2, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(wire.startsWith("GET /api/addresses/" + ADDRESS + " HTTP/1.1\n"));
            assertFalse(wire.contains("Cookie")); assertFalse(wire.contains("Authorization"));
        }
    }
    @Test public void wholeResponseDeadlineStopsContinuousDrip() throws Exception {
        try (java.net.ServerSocket server = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            Thread peer = new Thread(() -> {
                try (java.net.Socket s = server.accept()) {
                    s.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\n".getBytes());
                    for (int i = 0; i < 100; i++) { s.getOutputStream().write('x'); s.getOutputStream().flush(); Thread.sleep(20); }
                } catch (Exception expected) { }
            }); peer.setDaemon(true); peer.start();
            AddressTransport transport = new AddressTransport("127.0.0.1", server.getLocalPort(), false, 150);
            long started = System.nanoTime();
            assertThrows(java.io.IOException.class, () -> transport.lookup(ADDRESS));
            assertTrue(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 900);
            peer.join(1000); assertFalse(peer.isAlive());
        }
    }
    @Test public void chunkedJsonIsDecodedWithoutRedirectOrRetry() throws Exception {
        try (java.net.ServerSocket server = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger();
            Thread peer = new Thread(() -> {
                try (java.net.Socket s = server.accept()) {
                    count.incrementAndGet();
                    s.getOutputStream().write("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n2\r\n{}\r\n0\r\n\r\n".getBytes());
                } catch (Exception e) { throw new AssertionError(e); }
            }); peer.setDaemon(true); peer.start();
            AddressTransport.Response response = new AddressTransport("127.0.0.1", server.getLocalPort(), false, 1000).lookup(ADDRESS);
            assertEquals("{}", response.body); peer.join(); assertEquals(1, count.get());
        }
    }
    @Test public void statusResponsesNeverRedirectOrRetryEvenWithRetryAfterZero() throws Exception {
        for (int status : new int[]{302, 400, 404, 429, 503}) {
            try (java.net.ServerSocket server = new java.net.ServerSocket(0, 2, java.net.InetAddress.getLoopbackAddress())) {
                server.setSoTimeout(250);
                java.util.concurrent.FutureTask<Integer> peer = new java.util.concurrent.FutureTask<>(() -> {
                    try (java.net.Socket s = server.accept()) {
                        java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(s.getInputStream()));
                        while (!in.readLine().isEmpty()) { }
                        s.getOutputStream().write(("HTTP/1.1 " + status + " Status\r\nContent-Length: 2\r\nRetry-After: 0\r\nLocation: http://127.0.0.1:" + server.getLocalPort() + "/other\r\nSet-Cookie: a=b\r\n\r\n{}").getBytes());
                    }
                    try (java.net.Socket unexpected = server.accept()) { return 2; }
                    catch (java.net.SocketTimeoutException expected) { return 1; }
                });
                Thread thread = new Thread(peer); thread.setDaemon(true); thread.start();
                AddressTransport.Response response = new AddressTransport("127.0.0.1", server.getLocalPort(), false, 1000).lookup(ADDRESS);
                assertEquals(status, response.status); assertEquals("0", response.retryAfter);
                assertEquals(Integer.valueOf(1), peer.get(2, java.util.concurrent.TimeUnit.SECONDS));
            }
        }
    }
    @Test public void oversizedTruncatedOrAmbiguousBodyFailsClosed() throws Exception {
        for (String wire : new String[]{"Content-Length: 32769\r\n\r\n", "Content-Length: 4\r\n\r\n{}",
                "Content-Length: 2\r\nTransfer-Encoding: chunked\r\n\r\n", "Transfer-Encoding: chunked\r\n\r\n8001\r\n"}) {
            try (java.net.ServerSocket server = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
                Thread peer = new Thread(() -> { try (java.net.Socket s = server.accept()) {
                    s.getOutputStream().write(("HTTP/1.1 200 OK\r\n" + wire).getBytes());
                } catch (Exception ignored) { } }); peer.setDaemon(true); peer.start();
                assertThrows(java.io.IOException.class, () -> new AddressTransport("127.0.0.1", server.getLocalPort(), false, 1000).lookup(ADDRESS));
                peer.join(1000); assertFalse(peer.isAlive());
            }
        }
    }
    @Test public void cancelledTransportNeverConnects() throws Exception {
        try (java.net.ServerSocket server = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(100);
            AddressTransport transport = new AddressTransport("127.0.0.1", server.getLocalPort(), false, 1000); transport.cancel();
            assertThrows(java.io.IOException.class, () -> transport.lookup(ADDRESS));
            assertThrows(java.net.SocketTimeoutException.class, server::accept);
        }
    }
    @Test public void transportExistsForExplicitLookupOnly() throws Exception {
        try { Class.forName("com.saebm.btcnfcseller.core.AddressTransport"); }
        catch (ClassNotFoundException e) { throw new AssertionError("Missing bounded one-shot transport", e); }
    }
}
