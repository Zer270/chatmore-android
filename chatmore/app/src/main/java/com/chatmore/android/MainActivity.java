package com.chatmore.android;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String SUPABASE_URL = "https://zyrjbwtcepnvkqdeyjvl.supabase.co";
    private static final String ANON = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inp5cmpid3RjZXBudmtxZGV5anZsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTg3OTgzODEsImV4cCI6MjA3NDM3NDM4MX0.N5I6J4GBTqVpbDtrm49oixckxO2xfe7_M2kOQl_PNOc";
    private static final String PU = SUPABASE_URL + "/functions/v1/unifically-proxy";
    private static final String PG = SUPABASE_URL + "/functions/v1/grok-proxy";

    private static final String[] MODEL_NAMES    = {"GPT-5.6 Instant", "Claude Opus 4.6", "Grok 4.5", "DeepSeek Chat"};
    private static final String[] MODEL_IDS      = {"gpt-5.6-instant", "claude-opus-4-6", "grok-4.5", "deepseek-chat"};
    private static final String[] MODEL_ENDPOINT = {PU, PU, PG, PU};

    private int mi = 0;
    private RecyclerView rv;
    private EditText et;
    private Button bs;
    private ProgressBar pb;
    private TextView tm;
    private final List<Msg> msgs = new ArrayList<Msg>();
    private ChatAdapter adp;
    private OkHttpClient http;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rv = (RecyclerView) findViewById(R.id.rv_chat);
        et = (EditText) findViewById(R.id.et_input);
        bs = (Button) findViewById(R.id.btn_send);
        pb = (ProgressBar) findViewById(R.id.pb_loading);
        tm = (TextView) findViewById(R.id.tv_model);

        adp = new ChatAdapter(msgs);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adp);

        http = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build();

        tm.setText(MODEL_NAMES[mi]);

        tm.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                mi = (mi + 1) % MODEL_NAMES.length;
                tm.setText(MODEL_NAMES[mi]);
                Toast.makeText(MainActivity.this, "→ " + MODEL_NAMES[mi], Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        bs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send();
            }
        });
    }

    private void send() {
        String t = et.getText().toString().trim();
        if (TextUtils.isEmpty(t)) return;
        et.setText("");
        addMsg("user", t);
        pb.setVisibility(View.VISIBLE);
        bs.setEnabled(false);

        final String mod = MODEL_IDS[mi];
        final String ep  = MODEL_ENDPOINT[mi];

        ExecutorService ex = Executors.newSingleThreadExecutor();
        ex.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final String r = call(mod, ep);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            addMsg("assistant", r);
                            pb.setVisibility(View.GONE);
                            bs.setEnabled(true);
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            addMsg("error", e.getMessage());
                            pb.setVisibility(View.GONE);
                            bs.setEnabled(true);
                        }
                    });
                }
            }
        });
        ex.shutdown();
    }

    private String call(String model, String ep) throws IOException {
        JsonArray ms = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", "You are ChatMore, a friendly AI assistant.");
        ms.add(sys);

        for (int i = 0; i < msgs.size(); i++) {
            Msg m = msgs.get(i);
            if ("error".equals(m.role)) continue;
            JsonObject o = new JsonObject();
            o.addProperty("role", m.role);
            o.addProperty("content", m.content);
            ms.add(o);
        }

        JsonObject b = new JsonObject();
        b.addProperty("model", model);
        b.add("messages", ms);
        b.addProperty("max_tokens", 2048);

        Request req = new Request.Builder()
            .url(ep)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + ANON)
            .header("apikey", ANON)
            .header("X-ChatMore-Device-Time", String.valueOf(System.currentTimeMillis() / 1000L))
            .post(RequestBody.create(b.toString(), MediaType.get("application/json")))
            .build();

        Response resp = http.newCall(req).execute();
        String j = resp.body().string();
        if (!resp.isSuccessful()) throw new IOException(resp.code() + ": " + j);

        return new JsonParser().parse(j).getAsJsonObject()
            .getAsJsonArray("choices").get(0).getAsJsonObject()
            .getAsJsonObject("message").get("content").getAsString();
    }

    private void addMsg(String role, String content) {
        msgs.add(new Msg(role, content));
        adp.notifyItemInserted(msgs.size() - 1);
        rv.scrollToPosition(msgs.size() - 1);
    }

    static class Msg {
        String role, content;
        Msg(String r, String c) { role = r; content = c; }
    }

    static class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {
        private final List<Msg> data;
        ChatAdapter(List<Msg> d) { data = d; }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setPadding(32, 24, 32, 24);
            tv.setTextSize(15);
            tv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            return new VH(tv);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            Msg m = data.get(pos);
            h.tv.setText(m.content);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
            if ("user".equals(m.role)) {
                h.tv.setBackgroundColor(0xFF1565C0);
                h.tv.setTextColor(Color.WHITE);
                lp.gravity = Gravity.END;
                lp.setMargins(80, 8, 16, 8);
            } else if ("error".equals(m.role)) {
                h.tv.setBackgroundColor(0xFFB71C1C);
                h.tv.setTextColor(Color.WHITE);
                lp.gravity = Gravity.CENTER;
                lp.setMargins(16, 8, 16, 8);
            } else {
                h.tv.setBackgroundColor(0xFFF5F5F5);
                h.tv.setTextColor(0xFF212121);
                lp.gravity = Gravity.START;
                lp.setMargins(16, 8, 80, 8);
            }
            h.tv.setLayoutParams(lp);
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tv;
            VH(TextView t) { super(t); tv = t; }
        }
    }
}
