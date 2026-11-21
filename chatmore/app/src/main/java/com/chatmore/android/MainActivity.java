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
    private static final String SUPABASE_URL="https://zyrjbwtcepnvkqdeyjvl.supabase.co";
    private static final String ANON_KEY="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inp5cmpid3RjZXBudmtxZGV5anZsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTg3OTgzODEsImV4cCI6MjA3NDM3NDM4MX0.N5I6J4GBTqVpbDtrm49oixckxO2xfe7_M2kOQl_PNOc";
    private static final String PU=SUPABASE_URL+"/functions/v1/unifically-proxy";
    private static final String PG=SUPABASE_URL+"/functions/v1/grok-proxy";
    private static final String[] MN={"GPT-5.6 Instant","Claude Opus 4.6","Grok 4.5","DeepSeek"};
    private static final String[] MI={"gpt-5.6-instant","claude-opus-4-6","grok-4.5","deepseek-chat"};
    private static final String[] ME={PU,PU,PG,PU};
    private int mi=0;
    private String jwt=null;
    private RecyclerView rv; private EditText et; private Button bs;
    private ProgressBar pb; private TextView tm;
    private List<Msg> msgs=new ArrayList<Msg>();
    private ChatAdapter adp;
    private OkHttpClient http;
    private Handler ui=new Handler(Looper.getMainLooper());
    @Override protected void onCreate(Bundle s){
        super.onCreate(s); setContentView(R.layout.activity_main);
        rv=(RecyclerView)findViewById(R.id.rv_chat);
        et=(EditText)findViewById(R.id.et_input);
        bs=(Button)findViewById(R.id.btn_send);
        pb=(ProgressBar)findViewById(R.id.pb_loading);
        tm=(TextView)findViewById(R.id.tv_model);
        adp=new ChatAdapter(msgs);
        rv.setLayoutManager(new LinearLayoutManager(this)); rv.setAdapter(adp);
        http=new OkHttpClient.Builder().connectTimeout(30,TimeUnit.SECONDS).readTimeout(90,TimeUnit.SECONDS).build();
        tm.setText(MN[mi]); bs.setEnabled(false); pb.setVisibility(View.VISIBLE);
        tm.setOnLongClickListener(new View.OnLongClickListener(){
            @Override public boolean onLongClick(View v){
                mi=(mi+1)%MN.length; tm.setText(MN[mi]);
                Toast.makeText(MainActivity.this,"→ "+MN[mi],Toast.LENGTH_SHORT).show();
                return true;}});
        bs.setOnClickListener(new View.OnClickListener(){
            @Override public void onClick(View v){send();}});
        signIn();
    }
    private void signIn(){
        ExecutorService ex=Executors.newSingleThreadExecutor();
        ex.execute(new Runnable(){@Override public void run(){
            try{
                Request req=new Request.Builder()
                    .url(SUPABASE_URL+"/auth/v1/signup")
                    .header("Content-Type","application/json")
                    .header("apikey",ANON_KEY)
                    .header("Authorization","Bearer "+ANON_KEY)
                    .post(RequestBody.create("{}",MediaType.get("application/json")))
                    .build();
                Response resp=http.newCall(req).execute();
                String j=resp.body().string();
                if(resp.isSuccessful()){
                    JsonObject p=new JsonParser().parse(j).getAsJsonObject();
                    if(p.has("access_token")) jwt=p.get("access_token").getAsString();
                    else if(p.has("session")&&!p.get("session").isJsonNull())
                        jwt=p.getAsJsonObject("session").get("access_token").getAsString();
                }
            }catch(Exception e){}
            if(jwt==null) jwt=ANON_KEY;
            ui.post(new Runnable(){@Override public void run(){
                pb.setVisibility(View.GONE); bs.setEnabled(true);}});
        }});
        ex.shutdown();
    }
    private void send(){
        String t=et.getText().toString().trim(); if(TextUtils.isEmpty(t))return;
        et.setText(""); addMsg("user",t);
        pb.setVisibility(View.VISIBLE); bs.setEnabled(false);
        final String mod=MI[mi]; final String ep=ME[mi];
        ExecutorService ex=Executors.newSingleThreadExecutor();
        ex.execute(new Runnable(){@Override public void run(){
            try{final String r=call(mod,ep);
                ui.post(new Runnable(){@Override public void run(){
                    addMsg("assistant",r); pb.setVisibility(View.GONE); bs.setEnabled(true);}});
            }catch(final Exception e){
                ui.post(new Runnable(){@Override public void run(){
                    addMsg("error",e.getMessage()); pb.setVisibility(View.GONE); bs.setEnabled(true);}});
            }}});
        ex.shutdown();
    }
    private String call(String model,String ep) throws IOException{
        JsonArray ms=new JsonArray();
        JsonObject sys=new JsonObject();
        sys.addProperty("role","system"); sys.addProperty("content","You are ChatMore, a friendly AI assistant."); ms.add(sys);
        for(int i=0;i<msgs.size();i++){Msg m=msgs.get(i); if("error".equals(m.role))continue;
            JsonObject o=new JsonObject(); o.addProperty("role",m.role); o.addProperty("content",m.content); ms.add(o);}
        JsonObject b=new JsonObject(); b.addProperty("model",model); b.add("messages",ms); b.addProperty("max_tokens",2048);
        Request req=new Request.Builder().url(ep)
            .header("Content-Type","application/json")
            .header("Authorization","Bearer "+(jwt!=null?jwt:ANON_KEY))
            .header("apikey",ANON_KEY)
            .header("X-ChatMore-Device-Time",String.valueOf(System.currentTimeMillis()/1000L))
            .post(RequestBody.create(b.toString(),MediaType.get("application/json"))).build();
        Response resp=http.newCall(req).execute();
        String j=resp.body().string();
        if(!resp.isSuccessful()) throw new IOException(resp.code()+": "+j);
        return new JsonParser().parse(j).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
    }
    private void addMsg(String r,String c){msgs.add(new Msg(r,c)); adp.notifyItemInserted(msgs.size()-1); rv.scrollToPosition(msgs.size()-1);}
    static class Msg{String role,content; Msg(String r,String c){role=r;content=c;}}
    static class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH>{
        List<Msg> d; ChatAdapter(List<Msg> d){this.d=d;}
        @Override public VH onCreateViewHolder(ViewGroup p,int t){
            TextView tv=new TextView(p.getContext()); tv.setPadding(32,24,32,24); tv.setTextSize(15);
            tv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));
            return new VH(tv);}
        @Override public void onBindViewHolder(VH h,int pos){
            Msg m=d.get(pos); h.tv.setText(m.content);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            if("user".equals(m.role)){h.tv.setBackgroundColor(0xFF1565C0);h.tv.setTextColor(Color.WHITE);lp.gravity=Gravity.END;lp.setMargins(80,8,16,8);}
            else if("error".equals(m.role)){h.tv.setBackgroundColor(0xFFB71C1C);h.tv.setTextColor(Color.WHITE);lp.gravity=Gravity.CENTER;lp.setMargins(16,8,16,8);}
            else{h.tv.setBackgroundColor(0xFFF5F5F5);h.tv.setTextColor(0xFF212121);lp.gravity=Gravity.START;lp.setMargins(16,8,80,8);}
            h.tv.setLayoutParams(lp);}
        @Override public int getItemCount(){return d.size();}
        static class VH extends RecyclerView.ViewHolder{TextView tv; VH(TextView t){super(t);tv=t;}}
    }
}
