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
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class LogExporter {
	private static final Logger LOG = Logging.LOG();
    private static final Gson gson = new Gson();
    private static final int SLEEP_TIME_MILLISECONDS = 10_000;

    private String exportEndpoint;

    private final CloseableHttpClient httpClient = HttpClients.createDefault();


	public LogExporter(String exportEndpoint) throws Exception {
        this.exportEndpoint = exportEndpoint;
    }

    public Map<String, Object> pollNewLogs() {
        System.out.println("Polling new logs...");

        // TODO(@millerm) - parse from actual log file
        Map<String, Object> logRecord = new HashMap<>();

        logRecord.put("timeUnixNano", System.nanoTime());
        logRecord.put("severityText", "INFO");
        logRecord.put("body", "TEST");
        logRecord.put("attributes", new HashMap<String, String>() {{
            put("service.name", "your-service-name");
        }});

        return logRecord;
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
		
    public void start() throws Exception {
        System.out.println("Log collector is running...");

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
            logExporter.start();
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "SHUTDOWN", e);
		}
	}	
}
