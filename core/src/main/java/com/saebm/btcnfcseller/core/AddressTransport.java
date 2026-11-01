package com.saebm.btcnfcseller.core;

public final class AddressTransport {
    public static final class Response {
        public final int status; public final String body; public final String retryAfter;
        public Response(int status, String body, String retryAfter) { this.status = status; this.body = body; this.retryAfter = retryAfter; }
    }
    private final String host;
    private final int port, timeout;
    private final boolean tls;
    private volatile java.net.Socket active;
    private volatile boolean cancelled;
    public AddressTransport() { this("btc4.fun", 443, true, 10000); }
    AddressTransport(String host, int port, boolean tls, int timeout) {
        this.host = host; this.port = port; this.tls = tls; this.timeout = timeout;
    }
    public void cancel() {
        cancelled = true;
        java.net.Socket socket = active;
        if (socket != null) try { socket.close(); } catch (java.io.IOException ignored) { }
    }
    public Response lookup(String address) throws Exception {
        java.util.concurrent.FutureTask<Response> task = new java.util.concurrent.FutureTask<>(() -> exchange(address));
        Thread worker = new Thread(task, "address-lookup"); worker.setDaemon(true); worker.start();
        try { return task.get(timeout, java.util.concurrent.TimeUnit.MILLISECONDS); }
        catch (java.util.concurrent.TimeoutException e) { cancel(); task.cancel(true); throw new java.io.IOException("Deadline", e); }
        catch (java.util.concurrent.ExecutionException e) { throw new java.io.IOException("Lookup failed", e.getCause()); }
        catch (InterruptedException e) { cancel(); task.cancel(true); Thread.currentThread().interrupt(); throw new java.io.IOException("Cancelled", e); }
    }
    private Response exchange(String address) throws Exception {
        if (!BitcoinAddress.isValid(address) || cancelled) throw new java.io.IOException("Invalid or cancelled");
        java.net.Socket socket = new java.net.Socket(java.net.Proxy.NO_PROXY);
        active = socket;
        try {
            socket.connect(new java.net.InetSocketAddress(host, port), timeout);
            socket.setSoTimeout(timeout);
            if (tls) {
                javax.net.ssl.SSLSocket secure = (javax.net.ssl.SSLSocket) ((javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault()).createSocket(socket, host, port, true);
                active = socket = secure;
                javax.net.ssl.SSLParameters parameters = secure.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                secure.setSSLParameters(parameters);
                secure.startHandshake();
            }
            if (cancelled) throw new java.io.IOException("Cancelled");
            socket.getOutputStream().write(("GET /api/addresses/" + address + " HTTP/1.1\r\nHost: " + host
                + "\r\nAccept: application/json\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            java.io.InputStream input = socket.getInputStream();
            String statusLine = line(input);
            if (!statusLine.matches("HTTP/1\\.[01] [0-9]{3} .{0,200}")) throw new java.io.IOException("Invalid HTTP");
            int status = Integer.parseInt(statusLine.substring(9, 12));
            java.util.Map<String, String> headers = new java.util.HashMap<>();
            int headerBytes = statusLine.length();
            for (String value; !(value = line(input)).isEmpty();) {
                headerBytes += value.length();
                if (headerBytes > 8192 || !value.contains(":")) throw new java.io.IOException("Invalid headers");
                int colon = value.indexOf(':');
                String key = value.substring(0, colon).toLowerCase(java.util.Locale.ROOT);
                if (headers.put(key, value.substring(colon + 1).trim()) != null) throw new java.io.IOException("Duplicate headers");
            }
            if (headers.containsKey("transfer-encoding")) {
                if (!"chunked".equalsIgnoreCase(headers.get("transfer-encoding")) || headers.containsKey("content-length"))
                    throw new java.io.IOException("Ambiguous framing");
                java.io.ByteArrayOutputStream chunks = new java.io.ByteArrayOutputStream();
                while (true) {
                    String chunk = line(input);
                    if (!chunk.matches("[0-9a-fA-F]{1,8}")) throw new java.io.IOException("Invalid chunk");
                    long size = Long.parseLong(chunk, 16);
                    if (size > 32768 - chunks.size()) throw new java.io.IOException("Oversize response");
                    if (size == 0) {
                        if (!line(input).isEmpty()) throw new java.io.IOException("Unexpected trailers");
                        return new Response(status, chunks.toString("UTF-8"), headers.get("retry-after"));
                    }
                    for (long i = 0; i < size; i++) {
                        int value = input.read(); if (value < 0) throw new java.io.IOException("Truncated chunk"); chunks.write(value);
                    }
                    if (!line(input).isEmpty()) throw new java.io.IOException("Invalid chunk end");
                }
            }
            int length = headers.containsKey("content-length") ? Integer.parseInt(headers.get("content-length")) : -1;
            if (length > 32768 || length < -1) throw new java.io.IOException("Oversize response");
            java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
            while (length < 0 || body.size() < length) {
                int value = input.read();
                if (value < 0) { if (length >= 0) throw new java.io.IOException("Truncated body"); break; }
                if (body.size() == 32768) throw new java.io.IOException("Oversize response");
                body.write(value);
            }
            return new Response(status, body.toString("UTF-8"), headers.get("retry-after"));
        } finally { socket.close(); active = null; }
    }
    private static String line(java.io.InputStream input) throws java.io.IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        while (bytes.size() <= 8192) {
            int value = input.read();
            if (value < 0) throw new java.io.IOException("Truncated headers");
            if (value == '\n') {
                byte[] line = bytes.toByteArray();
                if (line.length == 0 || line[line.length - 1] != '\r') throw new java.io.IOException("Invalid line");
                return new String(line, 0, line.length - 1, java.nio.charset.StandardCharsets.US_ASCII);
            }
            bytes.write(value);
        }
        throw new java.io.IOException("Oversize headers");
    }
}
