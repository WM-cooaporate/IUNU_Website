package com.iunu.realestate.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** The HTTP round trips the refresh-token tests share, on H2 and on PostgreSQL. */
final class RefreshFlows {

    private RefreshFlows() {
    }

    static JsonNode login(MockMvc mockMvc, ObjectMapper json, String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Credentials(email, password))))
                .andReturn();
        if (result.getResponse().getStatus() != 200) {
            throw new AssertionError("login failed: " + result.getResponse().getStatus());
        }
        return json.readTree(result.getResponse().getContentAsString());
    }

    static MvcResult refresh(MockMvc mockMvc, ObjectMapper json, String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new RefreshBody(refreshToken))))
                .andReturn();
    }

    /**
     * Two requests carrying the same refresh token, released at the same
     * instant by a latch, returning both status codes.
     */
    static List<Integer> raceTwoRefreshes(MockMvc mockMvc, ObjectMapper json, String refreshToken) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return refresh(mockMvc, json, refreshToken).getResponse().getStatus();
                }));
            }
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    record Credentials(String email, String password) {
    }

    record RefreshBody(String refreshToken) {
    }
}
