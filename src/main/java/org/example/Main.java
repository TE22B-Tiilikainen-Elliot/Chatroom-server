package org.example;

// Importerar nödvändiga bibliotek
import com.sun.net.httpserver.*;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;

public class Main {
    // Konstanter och delade resurser
    private static final String DATA_FILE = "data.json"; // Fil för användardata
    private static final Map<String, UserSession> activeSessions = new HashMap<>(); // Aktiva användarsessioner
    private static final ChatRoom chatRoom = new ChatRoom(); // Globalt chattrum

    public static void main(String[] args) throws IOException {
        // Initiera datafilen om den inte finns
        initializeDataFile();

        // Starta HTTP-server på port 25565
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 25565), 0);

        // Skapa API-endpoints
        server.createContext("/login", new LoginHandler());
        server.createContext("/signup", new SignupHandler());
        server.createContext("/sendMessage", new SendMessageHandler());
        server.createContext("/receiveMessages", new ReceiveMessagesHandler());

        System.out.println("Chattserver startad på port 25565");
        server.setExecutor(null);
        server.start();
    }

    // Skapar datafilen om den inte finns
    private static void initializeDataFile() throws IOException {
        Path path = Paths.get(DATA_FILE);
        if (!Files.exists(path)) {
            // Försök kopiera från resurser eller skapa tom fil
            InputStream resource = Main.class.getClassLoader().getResourceAsStream(DATA_FILE);
            if (resource != null) {
                Files.copy(resource, path);
                resource.close();
                System.out.println("data.json kopierad från resurser");
            } else {
                Files.write(path, "[]".getBytes(StandardCharsets.UTF_8));
                System.out.println("Tom data.json skapad");
            }
        }
    }

    // Läser användare från JSON-fil
    private static JSONArray readUsersFromFile() throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(DATA_FILE)), StandardCharsets.UTF_8);
        return new JSONArray(content);
    }

    // Sparar användare till JSON-fil
    private static void writeUsersToFile(JSONArray users) throws IOException {
        Files.write(Paths.get(DATA_FILE), users.toString(2).getBytes(StandardCharsets.UTF_8));
    }

    // Hanterare för inloggning
    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                handleCors(exchange);
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                JSONObject requestBody = parseRequestBody(exchange);
                String username = requestBody.optString("username", "");
                String password = requestBody.optString("password", "");

                JSONObject response = new JSONObject();
                boolean success = false;

                // Kolla användare mot fil
                JSONArray users = readUsersFromFile();
                for (int i = 0; i < users.length(); i++) {
                    JSONObject user = users.getJSONObject(i);
                    if (user.getString("username").equals(username) &&
                            user.getString("password").equals(password)) {
                        success = true;
                        activeSessions.put(username, new UserSession(username));
                        break;
                    }
                }

                if (success) {
                    response.put("status", "success");
                    response.put("message", "Inloggning lyckades!");
                } else {
                    response.put("status", "failure");
                    response.put("message", "Fel användarnamn eller lösenord");
                }

                sendJsonResponse(exchange, response);
            } else {
                exchange.sendResponseHeaders(405, -1); // Metod inte tillåten
            }
        }
    }

    // Hanterare för registrering
    static class SignupHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                handleCors(exchange);
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                JSONObject requestBody = parseRequestBody(exchange);
                String username = requestBody.optString("username", "");
                String password = requestBody.optString("password", "");

                JSONObject response = new JSONObject();
                JSONArray users = readUsersFromFile();
                boolean userExists = false;

                // Kolla om användare redan finns
                for (int i = 0; i < users.length(); i++) {
                    if (users.getJSONObject(i).getString("username").equals(username)) {
                        userExists = true;
                        break;
                    }
                }

                if (userExists) {
                    response.put("status", "failure");
                    response.put("message", "Användarnamn upptaget");
                } else if (username.isEmpty() || password.isEmpty()) {
                    response.put("status", "failure");
                    response.put("message", "Användarnamn/lösenord får inte vara tomma");
                } else {
                    // Skapa ny användare
                    JSONObject newUser = new JSONObject();
                    newUser.put("username", username);
                    newUser.put("password", password);
                    users.put(newUser);
                    writeUsersToFile(users);

                    response.put("status", "success");
                    response.put("message", "Konto skapat!");
                }

                sendJsonResponse(exchange, response);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // Hanterare för att skicka meddelanden
    static class SendMessageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                handleCors(exchange);
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                JSONObject requestBody = parseRequestBody(exchange);
                String username = requestBody.optString("username", "");
                String message = requestBody.optString("message", "");

                JSONObject response = new JSONObject();

                if (activeSessions.containsKey(username)) {
                    chatRoom.addMessage(username, message);
                    response.put("status", "success");
                    response.put("message", "Meddelande skickat");
                } else {
                    response.put("status", "failure");
                    response.put("message", "Ej inloggad");
                }

                sendJsonResponse(exchange, response);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // Hanterare för att hämta meddelanden
    static class ReceiveMessagesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                handleCors(exchange);
                return;
            }

            if ("GET".equals(exchange.getRequestMethod())) {
                JSONObject response = new JSONObject();
                response.put("messages", chatRoom.getMessages());
                sendJsonResponse(exchange, response);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // Hjälpklasser
        private record UserSession(String username) {
    }

    private static class ChatRoom {
        private final List<ChatMessage> messages = new CopyOnWriteArrayList<>();

        public void addMessage(String username, String message) {
            messages.add(new ChatMessage(username, message));
        }

        public JSONArray getMessages() {
            JSONArray jsonMessages = new JSONArray();
            for (ChatMessage msg : messages) {
                jsonMessages.put(new JSONObject()
                        .put("username", msg.getUsername())
                        .put("message", msg.getMessage()));
            }
            return jsonMessages;
        }
    }

    private static class ChatMessage {
        private final String username;
        private final String message;

        public ChatMessage(String username, String message) {
            this.username = username;
            this.message = message;
        }

        public String getUsername() {
            return username;
        }

        public String getMessage() {
            return message;
        }
    }

    // Hjälpmetoder för HTTP-hantering
    private static void handleCors(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(204, -1);
    }

    private static JSONObject parseRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        StringBuilder textBuilder = new StringBuilder();
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            int c;
            while ((c = reader.read()) != -1) {
                textBuilder.append((char) c);
            }
        }
        return new JSONObject(textBuilder.toString());
    }

    private static void sendJsonResponse(HttpExchange exchange, JSONObject jsonResponse) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        byte[] response = jsonResponse.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
}