package com.wxy;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class ServerChat {
    public static void main(String[] args) {
        ServerFrame serverFrame = new ServerFrame();
        serverFrame.initialize();
    }
}

class ServerFrame extends JFrame {
    private JTextArea textArea = new JTextArea();
    private JButton startButton = new JButton("Start");
    private JButton stopButton = new JButton("Stop");
    private static final int PORT = 8888;
    private ServerSocket serverSocket;
    private ArrayList<ClientConnection> clientConnections = new ArrayList<>();
    private boolean isRunning = false;
    private int lastMessageId = 0;

    public ServerFrame() {
        super("Server Window");
    }

    public void initialize() {
        setLayout(new BorderLayout());
        add(textArea, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        add(buttonPanel, BorderLayout.SOUTH);

        setBounds(0, 0, 500, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);

        startButton.addActionListener(this::startServer);
        stopButton.addActionListener(this::stopServer);
    }

    private void startServer(ActionEvent event) {
        if (!isRunning) {
            try {
                serverSocket = new ServerSocket(PORT);
                isRunning = true;
                textArea.append("Server started!\n");
                new Thread(this::acceptClients).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void stopServer(ActionEvent event) {
        try {
            if (serverSocket != null) {
                isRunning = false;
                serverSocket.close();
                textArea.append("Server stopped.\n");
                System.exit(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void acceptClients() {
        while (isRunning) {
            try {
                Socket socket = serverSocket.accept();
                ClientConnection connection = new ClientConnection(socket);
                clientConnections.add(connection);
                textArea.append("Client connected: " + socket.getInetAddress() + "/" + socket.getPort() + "\n");
            } catch (IOException e) {
                if (isRunning) {
                    textArea.append("Error accepting client connection.\n");
                    e.printStackTrace();
                } else {
                    textArea.append("Server has stopped accepting new connections.\n");
                }
            }
        }
    }

    private synchronized int getNextMessageId() {
        return ++lastMessageId; // Increment and return the next message ID
    }

    public void broadcastMessage(String message) {
        int messageId = getNextMessageId(); // Generate a unique message ID for each new message
        for (ClientConnection client : clientConnections) {
            client.sendMessage(messageId, message); // Append message ID to the message
        }
    }

    void broadcastImage(byte[] imageData) {
        clientConnections.forEach(conn -> conn.sendImage(imageData));
    }

    void broadcastAudio(byte[] audioData) {
        clientConnections.forEach(conn -> conn.sendAudio(audioData));
    }

    class ClientConnection implements Runnable {
        private Socket socket;
        private DataInputStream inputStream;
        private DataOutputStream outputStream;

        public ClientConnection(Socket socket) {
            this.socket = socket;
            try {
                inputStream = new DataInputStream(socket.getInputStream());
                outputStream = new DataOutputStream(socket.getOutputStream());
                new Thread(this).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                while (isRunning) {
                    int type = inputStream.readInt();
                    System.out.println("Received message type: " + type);

                    switch (type) {
                        case 0:  // Text message
                            handleTextMessage();
                            break;
                        case 1:  // Image data
                            handleImageData();
                            break;
                        case 2:  // Audio data
                            handleAudioData();
                            break;
                        default:
                            System.out.println("Invalid message type received: " + type);
                            break;
                    }
                }
            } catch (IOException e) {
                System.out.println("Error with client connection: " + e.getMessage());
                textArea.append("Client disconnected: " + socket.getInetAddress() + "/" + socket.getPort() + "\n");
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void handleTextMessage() throws IOException {
            String receivedMessage = inputStream.readUTF();
            System.out.println("Received text message: " + receivedMessage);
            broadcastMessage(receivedMessage);
        }
        
        private void handleImageData() throws IOException {
            int length = inputStream.readInt();
            byte[] imageData = new byte[length];
            inputStream.readFully(imageData);
            broadcastImage(imageData);
            System.out.println("Received and broadcasting image data.");
        }
        
        private void handleAudioData() throws IOException {
            int length = inputStream.readInt();
            byte[] audioData = new byte[length];
            inputStream.readFully(audioData);
            broadcastAudio(audioData);
            System.out.println("Received and broadcasting audio data.");
        }

        public void sendMessage(int messageId, String message) {
            try {
                outputStream.writeInt(0); // Indicate this is a text message
                outputStream.writeUTF(messageId + ";" + message);
                outputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void sendImage(byte[] imageData) {
            try {
                outputStream.writeInt(1); // Indicate this is an image
                outputStream.writeInt(imageData.length);
                outputStream.write(imageData);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void sendAudio(byte[] audioData) {
            try {
                outputStream.writeInt(2); // Indicate this is an audio
                outputStream.writeInt(audioData.length);
                outputStream.write(audioData, 0, audioData.length);
                outputStream.flush(); // Ensure all data is sent immediately
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}
