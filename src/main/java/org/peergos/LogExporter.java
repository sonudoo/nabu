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
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private static final String LOG_FILE_PATH = Nabu.DEFAULT_IPFS_DIR_PATH.toAbsolutePath().toString() + "/trace.log";
    private static final String STATE_PATH = Nabu.DEFAULT_IPFS_DIR_PATH.toAbsolutePath().toString()
            + "/log-exporter-state.log";

    private String exportEndpoint;
    private ZonedDateTime lastWritten;
    private DateTimeFormatter timestampFormatter;

    private FileInputStream inFile = null;
    private BufferedInputStream buffer = null;

    private final CloseableHttpClient httpClient = HttpClients.createDefault();

    public LogExporter(String exportEndpoint) throws Exception {
        this.exportEndpoint = exportEndpoint;
        // TODO(@millerm) - read from state upon initialization
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

    public Map<String, Object> pollNewLogs() {
        System.out.println("Polling new logs...");
        Map<String, Object> logRecord = new HashMap<>();

        try {
            inFile = new FileInputStream(LOG_FILE_PATH);
            buffer = new BufferedInputStream(inFile);

            int rawLogData;

            // Handle possible resource contention b/t writer & reader
            while ((rawLogData = buffer.read()) != -1) {
                System.out.print((char) rawLogData);
            }

            this.lastWritten = ZonedDateTime.now(ZoneOffset.UTC);

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (buffer != null) {
                try {
                    buffer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (inFile != null) {
                try {
                    inFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return logRecord;

        // // TODO(@millerm) - put data from raw log into JSON data structure
        // Map<String, Object> logRecord = new HashMap<>();

        // logRecord.put("timeUnixNano", System.nanoTime());
        // logRecord.put("severityText", "INFO");
        // logRecord.put("body", "TEST");
        // logRecord.put("attributes", new HashMap<String, String>() {
        // {
        // put("service.name", "your-service-name");
        // }
        // });

        // return logRecord;
    }

    public void exportLogs(Map<String, Object> logRecord) {
        System.out.println("Exporting logs...");

        String jsonLog = gson.toJson(logRecord);

        HttpPost httpPost = new HttpPost(this.exportEndpoint);

        httpPost.setHeader("Content-Type", "application/json");
        httpPost.setEntity(new StringEntity(jsonLog, "UTF-8"));

        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            String responseBody = EntityUtils.toString(response.getEntity());

            System.out.println("Response from collector: " + responseBody);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void garbageCollectLogs() {
        System.out.println("Cleaning up old logs...");

        // TODO(@millerm)
    }

    public void run() throws Exception {
        System.out.println("Log collector is running...");

        // TODO(@millerm) - create thread to persist state (i.e. context about the most
        // recent logs exported)

        while (true) {
            Map<String, Object> logRecord = pollNewLogs();

            exportLogs(logRecord);

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
