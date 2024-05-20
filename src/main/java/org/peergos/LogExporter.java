package org.peergos;

import com.google.gson.Gson;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.peergos.util.Logging;

import java.util.logging.Logger;
import java.io.IOException;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class LogExporter {
    private static final Logger LOG = Logging.LOG();

    private static final Gson gson = new Gson();

    private static final int SLEEP_TIME_MILLISECONDS = 10_000;

    private static final int MAX_BUFFER_SIZE = 1024;

    private static final String LOG_FILE_PATH = Nabu.DEFAULT_IPFS_DIR_PATH.toAbsolutePath().toString() + "/trace.log";

    private static final String STATE_PATH = Nabu.DEFAULT_IPFS_DIR_PATH.toAbsolutePath().toString()
            + "/log-exporter-state.log";

    private ZonedDateTime lastWritten;
    private DateTimeFormatter timestampFormatter;

    private final CloseableHttpClient httpClient = HttpClients.createDefault();

    public LogExporter(String exportEndpoint) throws Exception {
        this.lastWritten = ZonedDateTime.ofInstant(java.time.Instant.EPOCH, ZoneOffset.UTC);
        this.timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'");
    }

    public void writeState() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(STATE_PATH))) {
            writer.write(this.lastWritten.format(this.timestampFormatter));
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Wrote state.");
    }

    private String _readLogs() {
        try (FileChannel channel = new FileInputStream(LOG_FILE_PATH).getChannel()) {
            try (FileLock lock = channel.lock(0, Long.MAX_VALUE, true)) {
                java.nio.ByteBuffer buff = java.nio.ByteBuffer.allocate(MAX_BUFFER_SIZE);
                StringBuilder logContent = new StringBuilder();

                while (channel.read(buff) > 0) {
                    // See:
                    // https://docs.oracle.com/javase%2F9%2Fdocs%2Fapi%2F%2F/java/nio/ByteBuffer.html#flip--
                    buff.flip();

                    while (buff.hasRemaining()) {
                        logContent.append((char) buff.get());
                    }

                    buff.clear();
                }

                return logContent.toString();
            } catch (Exception e) {
                System.err.println("Error acquiring lock: " + e.getMessage());
                e.printStackTrace();
                return "";
            }
        } catch (Exception e) {
            System.err.println("Error opening file: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }

    public String readLogs() {
        System.out.println("Reading logs...");

        return _readLogs();
    }

    private Map<String, Object> _formatLogRecord(String logRecord) {
        Map<String, Object> formattedLogRecord = new HashMap<>();

        formattedLogRecord.put("traceId", "123");
        formattedLogRecord.put("threadId", "456");
        formattedLogRecord.put("timestamp", "789");
        formattedLogRecord.put("traceType", "test");
        formattedLogRecord.put("details", "this");

        return formattedLogRecord;
    }

    public Map<String, Object> formatLogRecord(String logRecord) {
        return _formatLogRecord(logRecord);
    }

    private void _exportLogs(Map<String, Object> logRecord) {
        String exportEndpoint = System.getenv("LOG_EXPORT_ENDPOINT");
        String jsonLog = gson.toJson(logRecord);

        HttpPost httpPost = new HttpPost(exportEndpoint);

        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setEntity(new StringEntity(jsonLog, "UTF-8"));

        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            String responseBody = EntityUtils.toString(response.getEntity());

            System.out.println("Response from collector: " + responseBody);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void exportLogs(Map<String, Object> logRecord) {
        System.out.println("Exporting logs...");

        _exportLogs(logRecord);
    }

    public void run() throws Exception {
        System.out.println("Log collector is running...");

        while (true) {
            String logRecord = readLogs();
            Map<String, Object> formattedLogRecord = formatLogRecord(logRecord);
            exportLogs(formattedLogRecord);
            writeState();

            Thread.sleep(SLEEP_TIME_MILLISECONDS);
        }
    }

    public static void main(String[] args) {
        String exportEndpoint = System.getenv("LOG_EXPORT_ENDPOINT");

        try {
            LogExporter logExporter = new LogExporter(exportEndpoint);
            logExporter.run();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "SHUTDOWN", e);
        }
    }
}
