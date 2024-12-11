// File: src/main/java/org/example/ChatServer.java

package org.example;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class Main {

    private static final String DATA_FILE = "data.json"; // Path to the JSON file for user data
    private static final Map<String, UserSession> activeSessions = new HashMap<>(); // Active user sessions
    private static final ChatRoom chatRoom = new ChatRoom(); // Shared chat room for all users

    public static void main(String[] args) throws IOException {
        initializeDataFile();

        // Set up the server on port 8080
        HttpServer server = HttpServer.create(new InetSocketAddress(25565), 0);
        server.createContext("/login", new LoginHandler());
        server.createContext("/signup", new SignupHandler());
        server.createContext("/sendMessage", new SendMessageHandler());
        server.createContext("/receiveMessages", new ReceiveMessagesHandler());

        System.out.println("Chat server started on port 25565");

        server.setExecutor(null); // Use default executor
        server.start();
    }

    // Initializes the data.json file if it does not already exist
    private static void initializeDataFile() throws IOException {
        File file = new File(DATA_FILE);
        if (!file.exists()) {
            Files.write(Paths.get(DATA_FILE), "[]".getBytes(StandardCharsets.UTF_8));
        }
    }

    // Reads all users from the JSON file
    private static JSONArray readUsersFromFile() throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(DATA_FILE)), StandardCharsets.UTF_8);
        return new JSONArray(content);
    }

    // Writes the updated user data to the JSON file
    private static void writeUsersToFile(JSONArray users) throws IOException {
        Files.write(Paths.get(DATA_FILE), users.toString(2).getBytes(StandardCharsets.UTF_8));
    }

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

                JSONObject responseJson = new JSONObject();
                boolean success = false;

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
                    responseJson.put("status", "success");
                    responseJson.put("message", "Login successful!");
                } else {
                    responseJson.put("status", "failure");
                    responseJson.put("message", "Invalid username or password.");
                }

                sendJsonResponse(exchange, responseJson);
            } else {
                exchange.sendResponseHeaders(405, -1); // Method not allowed
            }
        }
    }

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

                JSONObject responseJson = new JSONObject();

                JSONArray users = readUsersFromFile();
                boolean userExists = false;
                for (int i = 0; i < users.length(); i++) {
                    if (users.getJSONObject(i).getString("username").equals(username)) {
                        userExists = true;
                        break;
                    }
                }

                if (userExists) {
                    responseJson.put("status", "failure");
                    responseJson.put("message", "Username already exists.");
                } else if (username.isEmpty() || password.isEmpty()) {
                    responseJson.put("status", "failure");
                    responseJson.put("message", "Username or password cannot be empty.");
                } else {
                    JSONObject newUser = new JSONObject();
                    newUser.put("username", username);
                    newUser.put("password", password);
                    users.put(newUser);
                    writeUsersToFile(users);

                    responseJson.put("status", "success");
                    responseJson.put("message", "Signup successful!");
                }

                sendJsonResponse(exchange, responseJson);
            } else {
                exchange.sendResponseHeaders(405, -1); // Method not allowed
            }
        }
    }

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

                JSONObject responseJson = new JSONObject();

                if (activeSessions.containsKey(username)) {
                    chatRoom.addMessage(username, message);
                    responseJson.put("status", "success");
                    responseJson.put("message", "Message sent!");
                } else {
                    responseJson.put("status", "failure");
                    responseJson.put("message", "User not logged in.");
                }

                sendJsonResponse(exchange, responseJson);
            } else {
                exchange.sendResponseHeaders(405, -1); // Method not allowed
            }
        }
    }

    static class ReceiveMessagesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                handleCors(exchange);
                return;
            }
            if ("GET".equals(exchange.getRequestMethod())) {
                JSONObject responseJson = new JSONObject();
                responseJson.put("messages", chatRoom.getMessages());
                sendJsonResponse(exchange, responseJson);
            } else {
                exchange.sendResponseHeaders(405, -1); // Method not allowed
            }
        }
    }

    private static class UserSession {
        private final String username;

        public UserSession(String username) {
            this.username = username;
        }

        public String getUsername() {
            return username;
        }
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

    private static void handleCors(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(204, -1); // No content for OPTIONS request
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