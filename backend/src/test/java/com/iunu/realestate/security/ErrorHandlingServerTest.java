package com.iunu.realestate.security;

import com.iunu.realestate.entity.Role;
import com.iunu.realestate.entity.User;
import com.iunu.realestate.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static com.iunu.realestate.security.ErrorHandlingIntegrationTest.assertClean;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The malformed requests MockMvc cannot send: it skips Tomcat, so it never
 * parses a real multipart body, never enforces the upload size limits, never
 * sees a URL Tomcat itself refuses, and never forwards to {@code /error}.
 *
 * <p>Requests are written byte for byte over a socket, because the whole point
 * is that they are not well formed - an HTTP client library would refuse to
 * send most of them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Error handling through a real server")
class ErrorHandlingServerTest {

    @LocalServerPort
    private int port;

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private record Response(int status, String body) {
    }

    private Response send(String head, byte[] body) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(20_000);
            OutputStream out = socket.getOutputStream();
            String headers = head + "Host: localhost\r\nConnection: close\r\n"
                    + (body.length > 0 ? "Content-Length: " + body.length + "\r\n" : "") + "\r\n";
            out.write(headers.getBytes(StandardCharsets.ISO_8859_1));
            try {
                out.write(body);
                out.flush();
            } catch (IOException ignored) {
                // The server may answer and close before reading the whole
                // body (an oversized upload) - the response is still there.
            }
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try {
                in.transferTo(buffer);
            } catch (IOException ignored) {
                // Connection reset after the response was written.
            }
            String raw = buffer.toString(StandardCharsets.UTF_8);
            assertThat(raw).as("no response at all for:\n%s", head).startsWith("HTTP/1.1 ");
            int status = Integer.parseInt(raw.substring(9, 12));
            int split = raw.indexOf("\r\n\r\n");
            return new Response(status, split < 0 ? "" : raw.substring(split + 4));
        }
    }

    private Response send(String head) throws IOException {
        return send(head, new byte[0]);
    }

    private String adminBearer() {
        User admin = userRepository.save(User.builder()
                .fullName("Server Admin")
                .email("server-admin-" + System.nanoTime() + "@iunu.test")
                .phone("+20 100 000 0000")
                .password(passwordEncoder.encode("Password1"))
                .role(Role.ADMIN)
                .build());
        return "Bearer " + jwtService.generateAccessToken(admin.getId(), admin.getEmail(), admin.getRole().name());
    }

    private static byte[] multipartBody(String boundary, String field, String filename, byte[] content) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        String partHead = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + field + "\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
        body.writeBytes(partHead.getBytes(StandardCharsets.ISO_8859_1));
        body.writeBytes(content);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.ISO_8859_1));
        return body.toByteArray();
    }

    private static void expect(Response response, int status) {
        assertThat(response.status()).as("body: %s", response.body()).isEqualTo(status);
        assertClean(response.body());
    }

    @Test
    @DisplayName("multipart with no boundary is a 400")
    void multipartWithoutBoundary() throws IOException {
        expect(send("POST /api/careers HTTP/1.1\r\nContent-Type: multipart/form-data\r\n",
                "fullName=A".getBytes(StandardCharsets.UTF_8)), 400);
    }

    @Test
    @DisplayName("a truncated multipart body is a 400")
    void truncatedMultipart() throws IOException {
        byte[] body = ("--XYZ\r\nContent-Disposition: form-data; name=\"fullName\"\r\n\r\nA")
                .getBytes(StandardCharsets.UTF_8);
        expect(send("POST /api/careers HTTP/1.1\r\nContent-Type: multipart/form-data; boundary=XYZ\r\n", body), 400);
    }

    @Test
    @DisplayName("a public upload over the size limit is a 413")
    void oversizedPublicUpload() throws IOException {
        byte[] body = multipartBody("XYZ", "resume", "cv.pdf", new byte[6 * 1024 * 1024]);
        expect(send("POST /api/careers HTTP/1.1\r\nContent-Type: multipart/form-data; boundary=XYZ\r\n", body), 413);
    }

    @Test
    @DisplayName("an admin upload over the size limit is a 413")
    void oversizedAdminUpload() throws IOException {
        byte[] body = multipartBody("XYZ", "files", "a.png", new byte[6 * 1024 * 1024]);
        expect(send("POST /api/properties/images HTTP/1.1\r\nAuthorization: " + adminBearer()
                + "\r\nContent-Type: multipart/form-data; boundary=XYZ\r\n", body), 413);
    }

    @Test
    @DisplayName("multipart/mixed to a multipart/form-data endpoint is a 415")
    void wrongMultipartSubtype() throws IOException {
        byte[] body = multipartBody("XYZ", "resume", "cv.pdf", "%PDF-1".getBytes(StandardCharsets.UTF_8));
        expect(send("POST /api/careers HTTP/1.1\r\nContent-Type: multipart/mixed; boundary=XYZ\r\n", body), 415);
    }

    @Test
    @DisplayName("a multipart body sent to a JSON endpoint is a 415")
    void multipartToJsonEndpoint() throws IOException {
        byte[] body = multipartBody("XYZ", "file", "a.txt", "hello".getBytes(StandardCharsets.UTF_8));
        expect(send("POST /api/contact HTTP/1.1\r\nContent-Type: multipart/form-data; boundary=XYZ\r\n", body), 415);
    }

    @Test
    @DisplayName("invalid UTF-8 in a JSON body is a 400")
    void invalidUtf8() throws IOException {
        byte[] body = {'{', '"', 'e', 'm', 'a', 'i', 'l', '"', ':', '"', (byte) 0xC3, (byte) 0x28, '"', '}'};
        expect(send("POST /api/newsletter HTTP/1.1\r\nContent-Type: application/json\r\n", body), 400);
    }

    @Test
    @DisplayName("an unknown route is a 401 anonymously and a 404 for an admin, through /error")
    void unknownRoutes() throws IOException {
        expect(send("GET /api/does-not-exist HTTP/1.1\r\n"), 401);
        expect(send("GET /api/does-not-exist HTTP/1.1\r\nAuthorization: " + adminBearer() + "\r\n"), 404);
        expect(send("GET /api/auth/does-not-exist HTTP/1.1\r\n"), 404);
    }

    @Test
    @DisplayName("a wrong method on a public route is a 405")
    void wrongMethod() throws IOException {
        expect(send("PUT /api/auth/login HTTP/1.1\r\nContent-Type: application/json\r\n",
                "{}".getBytes(StandardCharsets.UTF_8)), 405);
        // Spring Security's firewall refuses TRACE outright, before routing.
        expect(send("TRACE /api/properties HTTP/1.1\r\n"), 400);
    }

    @Test
    @DisplayName("URLs the firewall or Tomcat refuse are a 400")
    void rejectedUrls() throws IOException {
        expect(send("GET /api/properties/..;/admin HTTP/1.1\r\n"), 400);
        expect(send("GET /api/properties/%2e%2e/admin HTTP/1.1\r\n"), 400);
        expect(send("GET /api/properties/%zz HTTP/1.1\r\n"), 400);
        expect(send("GET /api/properties/a%00b HTTP/1.1\r\n"), 400);
    }

    @Test
    @DisplayName("asking for a representation the API does not produce is a 406, not a 500")
    void notAcceptable() throws IOException {
        expect(send("GET /api/properties HTTP/1.1\r\nAccept: text/html\r\n"), 406);
        expect(send("POST /api/contact HTTP/1.1\r\nAccept: application/xml\r\nContent-Type: application/json\r\n",
                "{}".getBytes(StandardCharsets.UTF_8)), 400);
    }

    @Test
    @DisplayName("a garbage bearer token on a public read does not break it")
    void garbageTokenOnPublicRead() throws IOException {
        Response response = send("GET /api/properties HTTP/1.1\r\nAuthorization: Bearer not.a.jwt\r\n");
        assertThat(response.status()).isIn(200, 401);
        assertClean(response.body());
    }
}
