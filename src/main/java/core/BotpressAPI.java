package core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import okhttp3.*;
import org.checkerframework.checker.units.qual.C;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BotpressAPI {

    private final static Logger LOGGER = LoggerFactory.getLogger(BotpressAPI.class);

    private final OkHttpClient client;
    private final String domain;
    private final String username;
    private final String password;
    private String token = "none";

    public BotpressAPI(String domain, String username, String password) {
        this.client = new OkHttpClient();
        this.domain = domain;
        this.username = username;
        this.password = password;
    }

    public CompletableFuture<List<String>> request(String botId, String userId, String text) {
        return request(botId, userId, text, true);
    }

    public CompletableFuture<List<String>> request(String botId, String userId, String text, boolean allowLogin) {
        String url = String.format("http://%s/api/v1/bots/%s/converse/%s/secured?include=nlu", domain, botId, userId);
        CompletableFuture<List<String>> future = new CompletableFuture<>();

        JSONObject requestJSON = new JSONObject();
        requestJSON.put("type", "text");
        requestJSON.put("text", text);

        RequestBody body = RequestBody.create(requestJSON.toString(), MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .post(body)
                .build();

        BotpressAPI self = this;
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                try {
                    JSONObject responseRootJson = new JSONObject(response.body().string());
                    if (responseRootJson.has("errorCode") && responseRootJson.getString("errorCode").equals("BP_0041")) {
                        if (allowLogin) {
                            LOGGER.info("Refreshing invalid token");
                            login().thenAccept(token -> {
                                self.token = token;
                                LOGGER.info("Login successful");
                                request(botId, userId, text, false)
                                        .thenAccept(future::complete)
                                        .exceptionally(e -> {
                                            future.completeExceptionally(e);
                                            return null;
                                        });
                            }).exceptionally(e -> {
                                future.completeExceptionally(e);
                                return null;
                            });
                        } else {
                            future.completeExceptionally(new Exception("Received invalid token: " + self.token));
                        }
                    } else {
                        boolean ambiguous = responseRootJson.getJSONObject("nlu").getBoolean("ambiguous");
                        if (ambiguous) {
                            LOGGER.warn("Skipping ambiguous request");
                            future.complete(Collections.emptyList());
                        } else {
                            ArrayList<String> responseTexts = new ArrayList<>();
                            JSONArray responseArrayJson = responseRootJson.getJSONArray("responses");
                            for (int i = 0; i < responseArrayJson.length(); i++) {
                                JSONObject responseJson = responseArrayJson.getJSONObject(i);
                                if (responseJson.getString("type").equals("text")) {
                                    responseTexts.add(responseJson.getString("text"));
                                }
                            }
                            future.complete(responseTexts);
                        }
                    }
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                }
            }

            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    public CompletableFuture<String> login() {
        String url = String.format("http://%s/api/v1/auth/login/basic/default", domain);
        CompletableFuture<String> future = new CompletableFuture<>();

        JSONObject requestJSON = new JSONObject();
        requestJSON.put("email", username);
        requestJSON.put("password", password);

        RequestBody body = RequestBody.create(requestJSON.toString(), MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                try {
                    JSONObject responseRootJson = new JSONObject(response.body().string());
                    String token = responseRootJson.getJSONObject("payload").getString("jwt");
                    future.complete(token);
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                }
            }

            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                future.completeExceptionally(e);
            }
        });

        return future;
    }

}
