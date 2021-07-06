package core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

public class BotpressAPI {

    private final OkHttpClient client;
    private final String domain;

    public BotpressAPI(String domain) {
        this.client = new OkHttpClient();
        this.domain = domain;
    }

    public CompletableFuture<List<String>> request(String botId, String userId, String text) {
        String url = String.format("http://%s/api/v1/bots/%s/converse/%s", domain, botId, userId);

        JSONObject requestJSON = new JSONObject();
        requestJSON.put("type", "text");
        requestJSON.put("text", text);

        RequestBody body = RequestBody.create(requestJSON.toString(), MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        CompletableFuture<List<String>> future = new CompletableFuture<>();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                try {
                    ArrayList<String> responseTexts = new ArrayList<>();
                    JSONObject responseRootJson = new JSONObject(response.body().string());
                    JSONArray responseArrayJson = responseRootJson.getJSONArray("responses");
                    for (int i = 0; i < responseArrayJson.length(); i++) {
                        JSONObject responseJson = responseArrayJson.getJSONObject(i);
                        if (responseJson.getString("type").equals("text")) {
                            responseTexts.add(responseJson.getString("text"));
                        }
                    }
                    future.complete(responseTexts);
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                }
            }
        });

        return future;
    }

}
