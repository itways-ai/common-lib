package com.itways.web.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;

/**
 * A GET whose body is read as a stream and refused once it passes a size cap
 * (F02; ARC-11: moved from conversation-service). A voice note or media file is held
 * in memory for transcription, so an unbounded {@code byte[]} download lets one
 * oversized (or endless) response take the heap of the whole service.
 */
public final class BoundedDownloads {

    /** Default cap when a caller configures none: 25 MB, Whisper's own upload limit. */
    public static final long DEFAULT_MAX_BYTES = 25L * 1024 * 1024;

    private BoundedDownloads() {
    }

    /**
     * The body of a GET, at most {@code maxBytes} long.
     *
     * @throws DownloadTooLargeException when the declared or the streamed length
     *                                   passes the cap; its message carries no URL
     */
    public static byte[] get(RestTemplate restTemplate, String url, HttpHeaders headers, long maxBytes) {
        return restTemplate.execute(url, HttpMethod.GET,
                request -> {
                    if (headers != null) {
                        request.getHeaders().addAll(headers);
                    }
                },
                response -> read(response, maxBytes));
    }

    /** The body of {@code response}, at most {@code maxBytes} long; the stream is closed. */
    public static byte[] read(ClientHttpResponse response, long maxBytes) throws IOException {
        long declared = response.getHeaders().getContentLength();
        if (declared > maxBytes) {
            throw new DownloadTooLargeException(declared, maxBytes);
        }
        try (InputStream in = response.getBody()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(
                    declared > 0 ? (int) declared : 8192);
            byte[] buffer = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buffer)) != -1) {
                total += n;
                if (total > maxBytes) {
                    throw new DownloadTooLargeException(total, maxBytes);
                }
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    /** A download refused for its size. Unchecked so RestTemplate does not wrap it with the URL. */
    public static class DownloadTooLargeException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public DownloadTooLargeException(long bytes, long maxBytes) {
            super("Download refused: more than " + maxBytes + " bytes (at least " + bytes + ")");
        }
    }
}
