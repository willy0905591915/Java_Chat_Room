# Chat Application

This is a Java-based chatroom application that supports text, image, and audio messaging. The application utilizes sockets for network communication and `javax.sound.sampled` for audio handling.

## Features

- **Text Messaging**: Send and receive real-time text messages.
- **Image Messaging**: Share images within the chat. Supports formats such as JPG.
- **Audio Messaging**: Record and play back audio messages. Note that audio recording is done through the system's default microphone and play back is done through default speaker.

## Running the Application

1. **Clone the Repository**: Clone or download the repository to your local machine.
2. **Run the Server**: Navigate to the `ServerChat.java` file, run it, and press start to start the server.
3. **Run the Client(s)**: Open one or more instances of `ClientChat.java` and run them to start the client interfaces.
4. **Connect and Chat**: Use the client interfaces to connect to the server and start chatting.

## Important Notes

- **IDE Compatibility**: I have successfully tested on Visual Studio Code. Due to issues with Eclipse not supporting audio input from MacBook microphones, it is recommended to use an alternative IDE if you are working on macOS and need audio recording capabilities.
- **Audio Configuration**: The application is configured to use the system's default audio device for both input and output. Ensure that your microphone and speakers are correctly configured before running the application.
- **Network Configuration**: By default, the application connects to `localhost` on port `8888`. Ensure that no other services are running on this port before starting the server.
