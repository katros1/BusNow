package ba.backend.vehicledata.tcp;

import ba.backend.vehicledata.model.VehiclePayload;
import ba.backend.vehicledata.service.VehicleDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

@Component
public class TcpServer {

    private static final Logger log     = Logger.getLogger(TcpServer.class.getName());
    private static final int    PORT    = 9000;
    private static final int    TIMEOUT_MS = 60_000;

    private final VehicleDataService service;
    private final ObjectMapper       mapper;
    private final ExecutorService    clientPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "tcp-client");
        t.setDaemon(true);
        return t;
    });

    private ServerSocket serverSocket;
    private Thread       listenerThread;

    public TcpServer(VehicleDataService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper  = mapper;
    }

    @PostConstruct
    public void start() throws Exception {
        serverSocket   = new ServerSocket(PORT);
        listenerThread = new Thread(this::listen, "tcp-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
        log.info("[TCP] Listening on port " + PORT);
    }

    @PreDestroy
    public void stop() throws Exception {
        clientPool.shutdownNow();
        if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
    }

    private void listen() {
        while (serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                try {
                    clientPool.submit(() -> handle(client));
                } catch (Exception e) {
                    client.close();
                    throw e;
                }
            } catch (Exception e) {
                if (serverSocket == null || serverSocket.isClosed()) break;
                log.warning("[TCP] Accept error: " + e.getMessage());
            }
        }
    }

    private void handle(Socket client) {
        try (client) {
            client.setSoTimeout(TIMEOUT_MS);
            String raw = new BufferedReader(
                new InputStreamReader(client.getInputStream())
            ).readLine();

            if (raw == null || raw.isBlank()) return;

            log.info("[TCP] Received: " + raw);
            VehiclePayload payload = mapper.readValue(raw, VehiclePayload.class);
            service.save(payload);

        } catch (Exception e) {
            log.warning("[TCP] Client error: " + e.getMessage());
        }
    }
}
