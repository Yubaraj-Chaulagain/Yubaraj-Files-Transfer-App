package com.yubaraj.share;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private WebView webView;
    private static final int PICK_SEND = 4101;
    private static final int PICK_RECEIVE = 4102;
    private static final int PICK_ONLINE_SEND = 4103;
    private String pendingServer = "";
    private boolean onlineSendMode = false;
    private String pendingIp = "";
    private ServerSocket serverSocket;
    private ExecutorService executor = Executors.newCachedThreadPool();

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void js(String function, String value) {
        String safe = value == null ? "" : value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", " ");
        runOnUiThread(() -> webView.evaluateJavascript("window."+function+"('"+safe+"')", null));
    }
    private void toast(String message) { runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show()); }

    private String localIp() {
        try {
            for (java.util.Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (java.util.Enumeration<InetAddress> addrs = intf.getInetAddresses(); addrs.hasMoreElements();) {
                    InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (ip != null && !ip.startsWith("169.254.")) return ip;
                    }
                }
            }
        } catch (Exception ignored) {}
        return "Check Wi-Fi settings";
    }

    private String fileName(Uri uri) {
        String name = "shared_file";
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) name = c.getString(i);
            }
        } catch (Exception ignored) {}
        return name.replace("\r","_").replace("\n","_");
    }

    private void startReceiver() {
        executor.execute(() -> {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    js("nativeStatus", "Receiver is already running on port 8988. IP: " + localIp());
                    return;
                }
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress(8988));
                js("nativeStatus", "RECEIVE READY — connect both phones to the same Wi-Fi/hotspot. This phone IP: " + localIp() + " — Port: 8988");
                while (!serverSocket.isClosed()) {
                    Socket client = serverSocket.accept();
                    executor.execute(() -> handleUpload(client));
                }
            } catch (Exception e) {
                js("nativeStatus", "Receiver error: " + e.getMessage());
            }
        });
    }

    private void handleUpload(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(30000);
            InputStream in = new BufferedInputStream(s.getInputStream());
            ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
            int state = 0, b;
            while ((b = in.read()) != -1) {
                headerBuf.write(b);
                if ((state==0 && b=='\r') || (state==2 && b=='\r')) state++;
                else if ((state==1 && b=='\n') || (state==3 && b=='\n')) state++;
                else state = (b=='\r') ? 1 : 0;
                if (state==4) break;
                if (headerBuf.size()>16384) throw new IOException("Headers too large");
            }
            String headers = headerBuf.toString("ISO-8859-1");
            int contentLength = -1;
            String name = "received_file";
            for (String line : headers.split("\r\n")) {
                String low=line.toLowerCase(Locale.ROOT);
                if (low.startsWith("content-length:")) contentLength=Integer.parseInt(line.substring(line.indexOf(':')+1).trim());
                if (low.startsWith("x-file-name:")) name=URLDecoder.decode(line.substring(line.indexOf(':')+1).trim(), "UTF-8");
            }
            if (contentLength < 0) throw new IOException("Missing Content-Length");
            name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
            if (name.trim().isEmpty()) name = "received_file";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, name);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
            values.put(MediaStore.Downloads.RELATIVE_PATH, "Download/Yubaraj Share");
            Uri outUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (outUri == null) throw new IOException("Could not create destination file");
            long remaining=contentLength;
            try (OutputStream out = getContentResolver().openOutputStream(outUri)) {
                if (out == null) throw new IOException("Could not open destination");
                byte[] buf=new byte[65536];
                while (remaining>0) {
                    int n=in.read(buf,0,(int)Math.min(buf.length,remaining));
                    if (n<0) throw new EOFException("Transfer interrupted");
                    out.write(buf,0,n); remaining-=n;
                }
                out.flush();
            }
            byte[] response="HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK".getBytes(StandardCharsets.US_ASCII);
            s.getOutputStream().write(response); s.getOutputStream().flush();
            js("nativeStatus", "Received: " + name + " — saved in Downloads/Yubaraj Share");
            js("nativeHistory", "Received " + name);
        } catch (Exception e) {
            js("nativeStatus", "Receive failed: " + e.getMessage());
            try { socket.getOutputStream().write("HTTP/1.1 500 Error\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII)); } catch (Exception ignored) {}
        }
    }

    private void sendSelected(Uri uri, String ip) {
        executor.execute(() -> {
            String name=fileName(uri);
            try {
                long size=-1;
                try (Cursor c=getContentResolver().query(uri,null,null,null,null)) {
                    if(c!=null && c.moveToFirst()){int idx=c.getColumnIndex(OpenableColumns.SIZE);if(idx>=0)size=c.getLong(idx);}
                }
                if(size<0) throw new IOException("File size unavailable for this provider");
                URL url=new URL("http://"+ip+":8988/upload");
                HttpURLConnection conn=(HttpURLConnection)url.openConnection();
                conn.setConnectTimeout(10000); conn.setReadTimeout(120000); conn.setRequestMethod("POST");
                conn.setDoOutput(true); conn.setFixedLengthStreamingMode(size);
                conn.setRequestProperty("Content-Type","application/octet-stream");
                conn.setRequestProperty("Content-Length",Long.toString(size));
                conn.setRequestProperty("X-File-Name",URLEncoder.encode(name,"UTF-8"));
                js("nativeStatus","Sending "+name+" to "+ip+"…");
                try(InputStream in=getContentResolver().openInputStream(uri); OutputStream out=conn.getOutputStream()){
                    if(in==null)throw new IOException("Cannot read selected file");
                    byte[] buf=new byte[65536]; int n;
                    while((n=in.read(buf))!=-1)out.write(buf,0,n);
                }
                int code=conn.getResponseCode();
                if(code==200){js("nativeStatus","Sent successfully: "+name);js("nativeHistory","Sent "+name+" to "+ip);}
                else throw new IOException("Receiver returned HTTP "+code);
                conn.disconnect();
            } catch(Exception e){js("nativeStatus","Send failed: "+e.getMessage()+". Check same Wi-Fi/hotspot and receiver IP.");}
        });
    }


    private void uploadOnline(Uri uri, String server) {
        executor.execute(() -> {
            String boundary = "----YubarajShare" + System.currentTimeMillis();
            String name = fileName(uri);
            try {
                URL url = new URL(server.replaceAll("/+$", "") + "/api/upload");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000); conn.setReadTimeout(180000);
                conn.setRequestMethod("POST"); conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                js("nativeStatus", "Uploading " + name + " to temporary online relay…");
                try (OutputStream out = conn.getOutputStream(); InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new IOException("Cannot read selected file");
                    out.write(("--"+boundary+"\\r\\nContent-Disposition: form-data; name=\\"file\\"; filename=\\""+name.replace("\\"","_")+"\\"\\r\\nContent-Type: application/octet-stream\\r\\n\\r\\n").getBytes(StandardCharsets.UTF_8));
                    byte[] buf = new byte[65536]; int n;
                    while ((n=in.read(buf))!=-1) out.write(buf,0,n);
                    out.write(("\\r\\n--"+boundary+"--\\r\\n").getBytes(StandardCharsets.UTF_8));
                }
                int code=conn.getResponseCode();
                InputStream response=(code>=200&&code<300)?conn.getInputStream():conn.getErrorStream();
                ByteArrayOutputStream bout=new ByteArrayOutputStream();
                if(response!=null){byte[] b=new byte[4096];int n;while((n=response.read(b))!=-1)bout.write(b,0,n);}
                String body=bout.toString("UTF-8");
                if(code<200||code>=300) throw new IOException("Relay HTTP "+code+": "+body);
                org.json.JSONObject obj=new org.json.JSONObject(body);
                js("onlineCodeReady", obj.optString("code"));
                js("nativeStatus", "Uploaded. Share code " + obj.optString("code") + " with recipient. Code expires in " + obj.optInt("ttlMinutes",60) + " minutes.");
                conn.disconnect();
            } catch(Exception e){js("nativeStatus","Online upload failed: "+e.getMessage());}
        });
    }

    private void downloadOnline(String server, String code) {
        executor.execute(() -> {
            try {
                URL url=new URL(server.replaceAll("/+$","")+"/api/file/"+code.trim());
                HttpURLConnection conn=(HttpURLConnection)url.openConnection();
                conn.setConnectTimeout(15000); conn.setReadTimeout(180000);
                int response=conn.getResponseCode();
                if(response!=200) {
                    InputStream err=conn.getErrorStream(); ByteArrayOutputStream b=new ByteArrayOutputStream();
                    if(err!=null){byte[] x=new byte[2048];int n;while((n=err.read(x))!=-1)b.write(x,0,n);}
                    throw new IOException("Code unavailable/expired (HTTP "+response+")");
                }
                String disposition=conn.getHeaderField("Content-Disposition");
                String name="YubarajShare-file";
                if(disposition!=null && disposition.contains("filename*=")){
                    String enc=disposition.substring(disposition.indexOf("filename*=")+10);
                    int q=enc.indexOf("''"); if(q>=0)enc=enc.substring(q+2);
                    try{name=URLDecoder.decode(enc,"UTF-8");}catch(Exception ignored){}
                }
                name=name.replaceAll("[\\\\/:*?\\"<>|]","_");
                ContentValues values=new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME,name);
                values.put(MediaStore.Downloads.MIME_TYPE,"application/octet-stream");
                values.put(MediaStore.Downloads.RELATIVE_PATH,"Download/Yubaraj Share");
                Uri outUri=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
                if(outUri==null)throw new IOException("Cannot create download file");
                try(InputStream in=conn.getInputStream();OutputStream out=getContentResolver().openOutputStream(outUri)){
                    if(out==null)throw new IOException("Cannot open output file");
                    byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);out.flush();
                }
                conn.disconnect();
                js("nativeStatus","Online download complete: "+name+" — saved in Downloads/Yubaraj Share");
                js("nativeHistory","Online received "+name);
            }catch(Exception e){js("nativeStatus","Online download failed: "+e.getMessage());}
        });
    }

    private class Bridge {
        @JavascriptInterface public void startReceiver(){ MainActivity.this.startReceiver(); }
        @JavascriptInterface public void onlineSend(String server){
            pendingServer = server == null ? "" : server.trim();
            if(pendingServer.isEmpty()){ js("nativeStatus","Enter the online relay HTTPS URL first."); return; }
            runOnUiThread(() -> {
                onlineSendMode = true;
                Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("*/*");
                startActivityForResult(i,PICK_ONLINE_SEND);
            });
        }
        @JavascriptInterface public void onlineReceive(String server, String code){
            String u=server==null?"":server.trim(), c=code==null?"":code.trim();
            if(u.isEmpty() || c.length()!=6){ js("nativeStatus","Enter relay URL and the six-digit transfer code."); return; }
            downloadOnline(u,c);
        }
        @JavascriptInterface public void sendTo(String ip){ pendingIp=ip==null?"":ip.trim(); runOnUiThread(()->{
            if(pendingIp.isEmpty()){js("nativeStatus","Enter the receiver IP address first.");return;}
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,PICK_SEND);
        });}
        @JavascriptInterface public void pickFile(){ runOnUiThread(()->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,PICK_RECEIVE);
        });}
        @JavascriptInterface public void showMessage(String message){toast(message);}
        @JavascriptInterface public void shareText(String text){runOnUiThread(()->{Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");send.putExtra(Intent.EXTRA_TEXT,text);startActivity(Intent.createChooser(send,"Share with"));});}
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK||data==null)return;
        java.util.ArrayList<Uri> uris=new java.util.ArrayList<>();
        if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());}
        else if(data.getData()!=null)uris.add(data.getData());
        if(requestCode==PICK_ONLINE_SEND){
            for(Uri uri:uris)uploadOnline(uri,pendingServer);
        }else if(requestCode==PICK_SEND){
            for(Uri uri:uris)sendSelected(uri,pendingIp);
        }else if(requestCode==PICK_RECEIVE){
            toast("To receive files, tap Receive and keep this app open. Share the receiver IP with the sender.");
        }
    }
    @Override protected void onDestroy(){try{if(serverSocket!=null)serverSocket.close();}catch(Exception ignored){}super.onDestroy();}
}
