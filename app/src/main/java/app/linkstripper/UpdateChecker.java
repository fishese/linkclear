package app.linkstripper;

import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;

/** Explicit, user-triggered version check. No device or link data is sent. */
public final class UpdateChecker {
    private static final String RELEASE_API = "https://api.github.com/repos/fishese/linkclear/releases/latest";

    public static String latestVersion() throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(RELEASE_API).openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(7000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "LinkClear update check");
        try {
            if (connection.getResponseCode() != 200) throw new Exception();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (var stream = connection.getInputStream()) {
                byte[] chunk = new byte[4096];
                int count;
                while ((count = stream.read(chunk)) != -1) {
                    if (buffer.size() + count > 65536) throw new Exception();
                    buffer.write(chunk, 0, count);
                }
            }
            String json = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            Matcher tag = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"v?([0-9]+(?:\\.[0-9]+){1,3})\\\"").matcher(json);
            if (!tag.find()) throw new Exception();
            return tag.group(1);
        } finally {
            connection.disconnect();
        }
    }

    static boolean isNewer(String available, String installed) {
        int[] a = parts(available), b = parts(installed);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int left = i < a.length ? a[i] : 0;
            int right = i < b.length ? b[i] : 0;
            if (left != right) return left > right;
        }
        return false;
    }

    private static int[] parts(String version) {
        String[] raw = version.replaceFirst("^[vV]", "").split("\\.");
        int[] values = new int[raw.length];
        for (int i = 0; i < raw.length; i++) values[i] = Integer.parseInt(raw[i]);
        return values;
    }
}
