package com.wxy;

import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.Socket;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.util.LinkedHashMap;
import java.util.Map;

public class ClientChat {
    public static void main(String[] args) {
        ClientFrame clientFrame = new ClientFrame();
        clientFrame.initialize();
    }
}

class ClientFrame extends JFrame {
    private JTextPane chatDisplayPane = new JTextPane();
    private JTextField chatInputField = new JTextField(20);
    private JButton sendImageButton = new JButton("Send Image");
    private JButton recordAudioButton = new JButton("Record Audio");
    private JButton stopRecordButton = new JButton("Stop & Send");
    private JScrollPane scrollPane;

    private String username;
    private int lastMessageId = 0;
    private boolean isReplyMode = false;
    private int replyToMessageId = -1;

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 8888;
    private Socket socket;
    private DataOutputStream outputStream;
    private boolean isConnected = false;

    private TargetDataLine audioLine;
    private AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 16000.0f, 16, 1, 2, 16000.0f, false);
    private ByteArrayOutputStream audioOutputStream;

    private static final int MAX_CACHE_SIZE = 100;
    private LinkedHashMap<Integer, String> messageCache = new LinkedHashMap<Integer, String>() {
        protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
            return size() > MAX_CACHE_SIZE; // Limit cache to 100 entries
        }
    };

    public ClientFrame() {
        super("Client Chat Window");
        chatDisplayPane.setContentType("text/html");  // Ensure JTextPane interprets HTML
    }

    public void initialize() {
        setLayout(new BorderLayout());
        chatDisplayPane.setEditable(false);
        scrollPane = new JScrollPane(chatDisplayPane);
        add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(chatInputField, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(sendImageButton);
        buttonPanel.add(recordAudioButton);
        buttonPanel.add(stopRecordButton);
        stopRecordButton.setEnabled(false); // Initially disable the stop button

        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        setBounds(300, 300, 500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        chatInputField.requestFocus();

        setupConnection();
        setupInputFieldListener();
        setupSendImageButtonListener();
        setupAudioButtons();
        startReceivingMessages();

        setVisible(true);
    }

    public void setUsername(String username) {
        this.username = username;
    }

    private synchronized int generateMessageId() {
        return ++lastMessageId;
    }

    private void setupConnection() {
        try {
            socket = new Socket(HOST, PORT);
            outputStream = new DataOutputStream(socket.getOutputStream());
            isConnected = true;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to connect to server.", "Connection Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setupInputFieldListener() {
        chatInputField.addActionListener(e -> {
            String message = chatInputField.getText().trim();
            if (!message.isEmpty()) {
                sendMessage(message);
            }
        });
    }

    private void setupSendImageButtonListener() {
        sendImageButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new FileNameExtensionFilter("Image files", "jpg", "jpeg", "png", "gif"));
            int result = fileChooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                sendImage(selectedFile);
            }
        });
    }

    private void setupAudioButtons() {
        recordAudioButton.addActionListener(e -> {
            startRecording();
            recordAudioButton.setEnabled(false);
            stopRecordButton.setEnabled(true);
        });

        stopRecordButton.addActionListener(e -> {
            stopRecording();
            recordAudioButton.setEnabled(true);
            stopRecordButton.setEnabled(false);
            sendAudio();
        });
    }

    private void startRecording() {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            audioLine = (TargetDataLine) AudioSystem.getLine(info);
            audioLine.open(format);
            audioLine.start();
            audioOutputStream = new ByteArrayOutputStream();
            Thread thread = new Thread(this::captureAudio);
            thread.start();
        } catch (LineUnavailableException e) {
            JOptionPane.showMessageDialog(this, "Audio line unavailable.", "Recording Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void captureAudio() {
        byte[] buffer = new byte[1024];
        int bytesRead;
        while (audioLine.isOpen()) {
            bytesRead = audioLine.read(buffer, 0, buffer.length);
            if (bytesRead > 0) {
                audioOutputStream.write(buffer, 0, bytesRead);
            }
        }
    }

    private void stopRecording() {
        try {
            audioLine.stop();
            audioLine.close();
            
//            // Save the recorded audio to a file
//            File outputFile = new File("recorded_audio.wav");
//            byte[] audioBytes = audioOutputStream.toByteArray();
//            ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes);
//            AudioInputStream audioInputStream = new AudioInputStream(bais, format, audioBytes.length / format.getFrameSize());
//            
//            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, outputFile);
//            JOptionPane.showMessageDialog(null, "Audio recorded successfully and saved to " + outputFile.getAbsolutePath());
        } finally {
            if (audioOutputStream != null) {
                try {
                    audioOutputStream.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }


    private void sendAudio() {
        try {
            outputStream.writeInt(2); // 2 means audio message
            byte[] audioBytes = audioOutputStream.toByteArray();
            outputStream.writeInt(audioBytes.length);
            outputStream.write(audioBytes);
            resetInputState();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Failed to send audio.", "Sending Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sendMessage(String message) {
        int messageId = generateMessageId(); // Generate ID for each new message
        String formattedMessage = messageId + ";" + username + ": " + message; // Include username in the message
        try {
            outputStream.writeInt(0); // Message type 0 for text
            outputStream.writeUTF(formattedMessage);
            outputStream.flush();
            chatInputField.setText(""); // Reset text field
        } catch (IOException e) {
            e.printStackTrace();
        }
    }        

    private String formatReplyMessage(String message) throws Exception {
        int colonIndex = message.indexOf(":");
        if (colonIndex == -1) {
            throw new Exception("Invalid reply format");
        }
        int replyToId = Integer.parseInt(message.substring(12, colonIndex).trim());
        String actualMessage = message.substring(colonIndex + 1).trim();
        int messageId = generateMessageId();
        return messageId + ";" + replyToId + ";" + actualMessage;
    }
    
    private void sendImage(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[fis.available()];
            fis.read(buffer);
            outputStream.writeInt(1); // 1 means image
            outputStream.writeInt(buffer.length);
            outputStream.write(buffer);
            resetInputState();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to send image.", "Sending Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleTags(String message) {
        // Basic tag detection
        Pattern pattern = Pattern.compile("@\\w+");
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            String taggedUser = matcher.group().substring(1); // Remove '@'
            // Process tagged user, e.g., send a notification, highlight, etc.
            System.out.println("Tagged user: " + taggedUser);
            // Implementation depends on your application's requirements
        }
    }

    // This method should handle displaying all messages.
    private void displayMessage(String message, int messageId) {
        try {
            // Assuming message format is "messageId;username: message"
            final int finalMessageId = messageId;  
            final String finalMessage = message;  
    
            JButton replyButton = new JButton("Reply");
            replyButton.addActionListener(e -> prepareReply(finalMessageId, finalMessage));
    
            message = formatTags(finalMessage);
            HTMLDocument doc = (HTMLDocument) chatDisplayPane.getDocument();
            HTMLEditorKit editorKit = (HTMLEditorKit) chatDisplayPane.getEditorKit();
    
            // Insert the message followed by the button as a component
            editorKit.insertHTML(doc, doc.getLength(), "<b>" + message + "</b> ", 0, 0, null);
            chatDisplayPane.setCaretPosition(doc.getLength());
            chatDisplayPane.insertComponent(replyButton);
            editorKit.insertHTML(doc, doc.getLength(), "<br>", 0, 0, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }    
    
    private void prepareReply(int messageId, String messageText) {
        isReplyMode = true;
        replyToMessageId = messageId;
        String preview = messageCache.getOrDefault(messageId, "No content available");
        System.out.println("Retrieving message ID " + messageId + " with preview: " + preview);
    
        chatInputField.setText("Replying to [" + preview + "]: ");
        chatInputField.requestFocus();
    }    

    private void resetInputState() {
        chatInputField.setText("");
        isReplyMode = false;
        replyToMessageId = -1;
        chatInputField.requestFocus();
    }    
    
    private String formatTags(String message) {
        // This regex will find words that start with @ and are followed by one or more word characters
        return message.replaceAll("@(\\w+)", "<b style='color:blue;'>@$1</b>");
    }

    private void startReceivingMessages() {
        new Thread(new MessageReceiver()).start();
    }

    class MessageReceiver implements Runnable {
        @Override
        public void run() {
            try {
                DataInputStream inputStream = new DataInputStream(socket.getInputStream());
                while (isConnected) {
                    int type = inputStream.readInt();
                    if (type == 0) {
                        handleTextMessage(inputStream);
                    } else if (type == 1) {
                        handleImageMessage(inputStream);
                    } else if (type == 2) {
                        handleAudioMessage(inputStream);
                    }
                }
            } catch (IOException e) {
                System.err.println("Connection error: " + e.getMessage());
                isConnected = false;
            }
        }

        private void handleTextMessage(DataInputStream inputStream) throws IOException {
            String messageReceived = inputStream.readUTF();
            try {
                String[] parts = messageReceived.split(";", 3);
                if (parts.length == 3) {
                    int messageId = Integer.parseInt(parts[0]);
                    String actualMessage = parts[2];
        
                    messageCache.put(messageId, actualMessage);
                    displayMessage(actualMessage, messageId);
                } else {
                    displayMessage("Error: Incorrect message format received.", -1);
                }
            } catch (NumberFormatException e) {
                displayMessage("Error parsing message ID.", -1);
            }
        }
        
        private void handleImageMessage(DataInputStream inputStream) throws IOException {
            int length = inputStream.readInt();
            byte[] messageBytes = new byte[length];
            inputStream.readFully(messageBytes);
            ImageIcon originalIcon = new ImageIcon(messageBytes);
            SwingUtilities.invokeLater(() -> {
                Image image = originalIcon.getImage();
                Image resizedImage = image.getScaledInstance(image.getWidth(null) / 20, image.getHeight(null) / 20, Image.SCALE_SMOOTH);
                ImageIcon resizedIcon = new ImageIcon(resizedImage);
                chatDisplayPane.insertComponent(new JLabel(resizedIcon));
                try {
                    Document doc = chatDisplayPane.getDocument();
                    doc.insertString(doc.getLength(), "\n", null);
                } catch (BadLocationException e) {
                    e.printStackTrace();
                }
            });
        }

        private void displayReplyMessage(String message, int messageId, int replyToId) {
            String originalMessagePreview = getOriginalMessagePreview(replyToId);
            String displayMessage = "Reply to [" + originalMessagePreview + "]: " + message;
        
            SwingUtilities.invokeLater(() -> {
                try {
                    HTMLDocument doc = (HTMLDocument) chatDisplayPane.getDocument();
                    HTMLEditorKit editorKit = (HTMLEditorKit) chatDisplayPane.getEditorKit();
                    editorKit.insertHTML(doc, doc.getLength(), "<div style='margin-left: 20px;'>" + displayMessage + "</div>", 0, 0, null);
                    editorKit.insertHTML(doc, doc.getLength(), "<br>", 0, 0, null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }        
        
        private String getOriginalMessagePreview(int messageId) {
            String fullMessage = messageCache.getOrDefault(messageId, "Original message not found.");
            // Return either the full message or just a snippet
            return fullMessage.length() > 50 ? fullMessage.substring(0, 50) + "..." : fullMessage;
        }
        

        private void handleAudioMessage(DataInputStream inputStream) throws IOException {
        	int length = inputStream.readInt();
            byte[] audioBytes = new byte[length];
            int bytesRead = 0;
            while (bytesRead < length) {
                bytesRead += inputStream.read(audioBytes, bytesRead, length - bytesRead);
            }
            SwingUtilities.invokeLater(() -> {
                JButton playButton = new JButton("Play Audio");
                playButton.addActionListener(e -> playAudio(audioBytes));
                try {
                    Document doc = chatDisplayPane.getDocument();
                    Style style = chatDisplayPane.addStyle("StyleName", null);
                    StyleConstants.setComponent(style, playButton);
                    doc.insertString(doc.getLength(), "Incoming audio: ", null);
                    doc.insertString(doc.getLength(), "\n", style);
                } catch (BadLocationException ex) {
                    ex.printStackTrace();
                }
            });
        }

        private void playAudio(byte[] audioBytes) {
            try {
                InputStream input = new ByteArrayInputStream(audioBytes);
                AudioInputStream audioStream = new AudioInputStream(input, format, audioBytes.length / format.getFrameSize());
//            	File audioFile = new File("/Users/panxuanen/Documents/Projects/CS9053 Java/Final_Project/2.wav");
//                AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
                DataLine.Info info = new DataLine.Info(Clip.class, format);
                Clip clip = (Clip) AudioSystem.getLine(info);
                clip.open(audioStream);
                clip.start();
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        clip.close();
                    }
                });
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Error playing audio: " + e.getMessage(), "Playback Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
