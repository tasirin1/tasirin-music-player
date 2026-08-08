package com.tasirin.musicplayer;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Remote web: halaman kontrol + API JSON (tanpa dependensi eksternal). */
public final class RemoteServer {

    private volatile boolean running;
    private ServerSocket server;
    private Thread acceptThread;

    public synchronized void start(int port) throws Exception {
        if (running) {
            return;
        }
        server = new ServerSocket(port);
        running = true;
        acceptThread = new Thread(this::acceptLoop, "mp-remote");
        acceptThread.start();
    }

    public synchronized void stop() {
        running = false;
        try {
            if (server != null) {
                server.close();
            }
        } catch (Exception ignored) {
        }
        server = null;
    }

    public boolean isRunning() {
        return running;
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket s = server.accept();
                new Thread(() -> handle(s), "mp-req").start();
            } catch (Exception e) {
                if (!running) {
                    break;
                }
            }
        }
    }

    private void handle(Socket sock) {
        try (Socket s = sock;
             BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
            s.setSoTimeout(5000);
            String line = r.readLine();
            if (line == null) {
                return;
            }
            String[] parts = line.split(" ");
            if (parts.length < 2 || !"GET".equals(parts[0])) {
                respond(s, 405, "text/plain", "Method not allowed");
                return;
            }
            String raw = parts[1];
            String path = raw.contains("?") ? raw.substring(0, raw.indexOf('?')) : raw;
            Map<String, String> q = parseQuery(raw);
            if (path.equals("/") || path.equals("/index.html")) {
                respond(s, 200, "text/html; charset=utf-8", PAGE);
            } else if (path.equals("/api/list")) {
                respond(s, 200, "application/json; charset=utf-8", jsonList());
            } else if (path.equals("/api/status")) {
                respond(s, 200, "application/json; charset=utf-8", jsonStatus());
            } else if (path.equals("/api/action")) {
                handleAction(q);
                respond(s, 200, "application/json", "{\"ok\":true}");
            } else if (path.equals("/favicon.ico")) {
                respond(s, 204, "text/plain", "");
            } else {
                respond(s, 404, "text/plain", "Not found");
            }
        } catch (Exception ignored) {
        }
    }

    private void handleAction(Map<String, String> q) {
        String cmd = q.get("cmd");
        Context ctx = App.ctx;
        if (cmd == null || ctx == null) {
            return;
        }
        switch (cmd) {
            case "play": {
                String idx = q.get("i");
                if (idx != null) {
                    try {
                        MusicService.playIndex(ctx, Integer.parseInt(idx));
                    } catch (NumberFormatException ignored) {
                    }
                } else {
                    MusicService.toggle(ctx);
                }
                break;
            }
            case "pause":
                MusicService.pause(ctx);
                break;
            case "next":
                MusicService.next(ctx);
                break;
            case "prev":
                MusicService.prev(ctx);
                break;
            case "seek":
                try {
                    MusicService.seekTo(ctx, Long.parseLong(q.get("pos")));
                } catch (Exception ignored) {
                }
                break;
            case "shuffle":
                MusicService.toggleShuffle(ctx);
                break;
            case "repeat":
                MusicService.cycleRepeat(ctx);
                break;
            default:
                break;
        }
    }

    private String jsonList() {
        List<Track> q = MusicService.queue;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < q.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            Track t = q.get(i);
            sb.append("{\"i\":").append(i)
                    .append(",\"n\":\"").append(jsonEsc(t.name)).append('"')
                    .append(",\"f\":\"").append(jsonEsc(t.folder)).append("\"}");
        }
        return sb.append(']').toString();
    }

    private String jsonStatus() {
        return "{\"playing\":" + MusicService.playing
                + ",\"index\":" + MusicService.playIndex
                + ",\"name\":\"" + jsonEsc(MusicService.trackName) + "\""
                + ",\"pos\":" + MusicService.positionMs
                + ",\"dur\":" + MusicService.durationMs
                + ",\"shuffle\":" + MusicService.shuffle
                + ",\"repeat\":" + MusicService.repeat
                + ",\"count\":" + MusicService.queue.size() + "}";
    }

    private static String jsonEsc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> out = new HashMap<>();
        int q = raw.indexOf('?');
        if (q < 0) {
            return out;
        }
        for (String pair : raw.substring(q + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                out.put(dec(pair.substring(0, eq)), dec(pair.substring(eq + 1)));
            }
        }
        return out;
    }

    private static String dec(String s) {
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private void respond(Socket s, int code, String type, String body) throws Exception {
        byte[] data = body.getBytes("UTF-8");
        OutputStream o = s.getOutputStream();
        String reason = code == 200 ? "OK" : code == 204 ? "No Content"
                : code == 404 ? "Not Found" : "Method Not Allowed";
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n");
        head.append("Content-Type: ").append(type).append("\r\n");
        head.append("Content-Length: ").append(data.length).append("\r\n");
        head.append("Cache-Control: no-store\r\n");
        head.append("Connection: close\r\n\r\n");
        o.write(head.toString().getBytes("US-ASCII"));
        o.write(data);
        o.flush();
    }

    private static final String PAGE = """
            <!DOCTYPE html>
            <html lang="id">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Tasirin Musik</title>
            <style>
            :root{--bg:#0b0f14;--card:#151b23;--line:#232b36;--txt:#e6edf3;--dim:#8b98a5;--acc:#2dd4bf}
            *{box-sizing:border-box;margin:0;padding:0}
            body{background:var(--bg);color:var(--txt);font-family:system-ui,"Segoe UI",Roboto,sans-serif;min-height:100vh}
            .wrap{max-width:720px;margin:0 auto;padding:16px}
            h1{font-size:18px;margin-bottom:4px}
            .sub{color:var(--dim);font-size:12px;margin-bottom:14px}
            .box{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:12px;margin-bottom:12px}
            .row{display:flex;gap:8px;flex-wrap:wrap;align-items:center}
            .btn{background:#212a36;border:1px solid var(--line);color:var(--txt);border-radius:8px;padding:10px 14px;font-size:15px;cursor:pointer}
            .btn:hover{background:#2b3644}
            .btn.acc{background:var(--acc);color:#04211d;border:none;font-weight:700}
            input[type=range]{flex:1 1 100%;accent-color:var(--acc)}
            .time{font-size:12px;color:var(--dim);display:flex;justify-content:space-between;width:100%;margin-top:4px}
            ul{list-style:none}
            li{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:10px 12px;margin-bottom:8px;cursor:pointer}
            li.on{border-color:var(--acc)}
            li .t{font-size:14px}
            li .f{font-size:11px;color:var(--dim);margin-top:2px}
            </style>
            </head>
            <body>
            <div class="wrap">
            <h1>Tasirin Musik Player</h1>
            <div class="sub" id="sub">Menghubungkan...</div>
            <div class="box">
            <div class="row">
            <button class="btn" onclick="go('prev')">&#9664; Sebelum</button>
            <button class="btn acc" id="pp" onclick="go('play')">&#9654; Putar</button>
            <button class="btn" onclick="go('next')">Berikutnya &#9654;</button>
            <button class="btn" id="sf" onclick="go('shuffle')">Acak: Mati</button>
            <button class="btn" id="rp" onclick="go('repeat')">Ulang: Mati</button>
            </div>
            <input type="range" id="seek" min="0" max="0" value="0" oninput="scrub()">
            <div class="time"><span id="pos">0:00</span><span id="dur">0:00</span></div>
            </div>
            <ul id="list"></ul>
            </div>
            <script>
            var items=[],dragging=false;
            function esc(s){return (s==null?'':String(s)).replace(/[&<>"']/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];});}
            function fmt(ms){ms=Math.max(0,ms||0);var m=Math.floor(ms/60000),s=Math.floor(ms%60000/1000);return m+':'+(s<10?'0':'')+s;}
            function go(cmd){fetch('/api/action?cmd='+cmd).catch(function(){});}
            function onList(d){items=d;var h='';for(var i=0;i<d.length;i++){h+='<li data-i="'+i+'"><div class="t">'+esc(d[i].n)+'</div><div class="f">'+esc(d[i].f)+'</div></li>';}document.getElementById('list').innerHTML=h;var lis=document.querySelectorAll('#list li');for(var j=0;j<lis.length;j++){lis[j].onclick=(function(k){return function(){fetch('/api/action?cmd=play&i='+k).catch(function(){});};})(j);}}
            function onStatus(s){
            document.getElementById('pp').textContent=s.playing?'|| Jeda':'▶ Putar';
            document.getElementById('sub').textContent=(s.name?esc(s.name):'Tidak ada lagu')+' · '+s.count+' lagu';
            document.getElementById('sf').textContent='Acak: '+(s.shuffle?'Nyala':'Mati');
            document.getElementById('rp').textContent='Ulang: '+['Mati','Semua','Satu'][s.repeat];
            var seek=document.getElementById('seek');seek.max=Math.max(1,s.dur);if(!dragging)seek.value=s.pos;
            document.getElementById('pos').textContent=fmt(s.pos);document.getElementById('dur').textContent=fmt(s.dur);
            var lis=document.querySelectorAll('#list li');for(var i=0;i<lis.length;i++){if(lis[i].getAttribute('data-i')==s.index){lis[i].className='on';}else{lis[i].className='';}}
            }
            function scrub(){dragging=true;document.getElementById('pos').textContent=fmt(document.getElementById('seek').value);}
            document.getElementById('seek').onchange=function(){dragging=false;fetch('/api/action?cmd=seek&pos='+this.value).catch(function(){});};
            function refresh(){fetch('/api/status').then(function(r){return r.json();}).then(function(s){onStatus(s);if(s.count!==items.length){return fetch('/api/list').then(function(r){return r.json();}).then(onList);}}).catch(function(){});}
            refresh();setInterval(refresh,1000);
            </script>
            </body>
            </html>
            """;
}
