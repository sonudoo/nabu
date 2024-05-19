package org.peergos;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import org.peergos.util.Logging;

import java.util.logging.Logger;
import java.util.logging.Level;

public class ClientWithLogExporter {
    private static final Logger LOG = Logging.LOG();
    private static final int SLEEP_TIME_MILLISECONDS = 10_000;

    public Optional<Long> startClientProcess() {
        try {
            // TODO(@millerm) - handle multiple clients
            ProcessBuilder clientProcessBuilder = new ProcessBuilder("java", "-cp",
                    "target/nabu-v0.7.7-jar-with-dependencies.jar", "org.peergos.Client", "-id", "0");

            // Make standard input/output available
            clientProcessBuilder.inheritIO();

            Process clientProcess = clientProcessBuilder.start();
            return Optional.of(clientProcess.pid());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    public Optional<Long> startAgentProcess() {
        // File log = new File("LogExporter.log");

        try {
            ProcessBuilder agentProcessBuilder = new ProcessBuilder("java", "-cp",
                    "target/nabu-v0.7.7-jar-with-dependencies.jar", "org.peergos.LogExporter");
            agentProcessBuilder.inheritIO();

            // agentProcessBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(log));

            Process agentProcess = agentProcessBuilder.start();
            return Optional.of(agentProcess.pid());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    public void run() throws Exception {
        Optional<Long> maybeClientProcessId = startClientProcess();
        Optional<ProcessHandle> clientProcessHandle = Optional.empty();

        Optional<Long> maybeAgentProcessId = startAgentProcess();
        Optional<ProcessHandle> agentProcessHandle = Optional.empty();

        if (maybeClientProcessId.isPresent()) {
            clientProcessHandle = ProcessHandle.of(maybeClientProcessId.get());
        }

        if (maybeAgentProcessId.isPresent()) {
            agentProcessHandle = ProcessHandle.of(maybeAgentProcessId.get());
        }

        while (true) {
            if (clientProcessHandle.isPresent()) {
                if (clientProcessHandle.get().isAlive()) {
                    System.out.println("Client processId = " + maybeClientProcessId.get());
                } else {
                    System.out.println("Client process is not alive.");
                }
            }

            if (agentProcessHandle.isPresent()) {
                if (agentProcessHandle.get().isAlive()) {
                    System.out.println("Agent processId = " + maybeAgentProcessId.get());
                } else {
                    System.out.println("Agent process is not alive.");
                }
            }

            Thread.sleep(SLEEP_TIME_MILLISECONDS);
        }

    }

    public ClientWithLogExporter(Args args) throws Exception {
        run();
    }

    public static void main(String[] args) {
        try {
            new ClientWithLogExporter(Args.parse(args, /* isClient= */ false));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "SHUTDOWN", e);
        }
    }
}
