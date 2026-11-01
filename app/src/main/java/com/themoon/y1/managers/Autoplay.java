package com.themoon.y1.managers;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Autoplay: when the queue runs out, keep playing songs that go with the last one.
 *
 * InniSync (the desktop companion) works out which songs on the Y1 sound alike and writes them to
 * /storage/sdcard0/.innisync_autoplay.json on every sync, each path listed once to keep it small:
 *   {"version": 2, "paths": ["<song path>", ...], "next": [[<indexes into paths, closest first>], ...]}
 * This class only reads that file (re-read whenever it changes) and picks the closest song that
 * isn't already in the current queue, so a long autoplay run doesn't loop back on itself.
 */
public class Autoplay {
    public static final File MAP_FILE = new File("/storage/sdcard0/.innisync_autoplay.json");
    private static Map<String, List<String>> next = new HashMap<>();
    private static long loadedModified = -1;

    private static synchronized void load() {
        long modified = MAP_FILE.exists() ? MAP_FILE.lastModified() : 0;
        if (modified == loadedModified) return;
        loadedModified = modified;
        next = new HashMap<>();
        if (modified == 0) return;
        try {
            FileInputStream in = new FileInputStream(MAP_FILE);
            byte[] data = new byte[(int) MAP_FILE.length()];
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n < 0) break;
                off += n;
            }
            in.close();
            JSONObject root = new JSONObject(new String(data, 0, off, "UTF-8"));
            JSONArray paths = root.getJSONArray("paths");
            JSONArray lists = root.getJSONArray("next");
            for (int i = 0; i < paths.length() && i < lists.length(); i++) {
                JSONArray idx = lists.getJSONArray(i);
                List<String> list = new ArrayList<>(idx.length());
                for (int j = 0; j < idx.length(); j++) list.add(paths.getString(idx.getInt(j)));
                next.put(paths.getString(i), list);
            }
        } catch (Exception e) {
            next = new HashMap<>();   // a half-written or old-format file just means no autoplay
        }
    }

    /** The song to play after {@code finished}, or null when there's nothing new to suggest. */
    public static File pick(File finished, List<File> queue) {
        if (finished == null) return null;
        load();
        List<String> candidates = next.get(finished.getAbsolutePath());
        if (candidates == null) return null;
        Set<String> queued = new HashSet<>();
        for (File f : queue) queued.add(f.getAbsolutePath());
        for (String path : candidates) {
            if (queued.contains(path)) continue;
            File f = new File(path);
            if (f.exists()) return f;
        }
        return null;
    }
}
