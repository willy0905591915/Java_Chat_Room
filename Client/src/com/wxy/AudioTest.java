package com.wxy;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

public class AudioTest {
    public static void main(String[] args) {
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        for (Mixer.Info info : mixerInfos) {
            Mixer mixer = AudioSystem.getMixer(info);
            System.out.println("Mixer: " + info.getName() + " [" + info.getDescription() + "]");

            // Test each mixer with a clip
            if (testMixer(mixer)) {
                System.out.println(info.getName() + " supports clip playback\n");
            } else {
                System.out.println(info.getName() + " does NOT support clip playback\n");
            }
        }
    }

    private static boolean testMixer(Mixer mixer) {
        DataLine.Info info = new DataLine.Info(Clip.class, null); // Format not specified
        boolean supports = mixer.isLineSupported(info);
        if (supports) {
            try {
                Clip clip = (Clip) mixer.getLine(info);
                // Ensure you have a .wav file to test
                File audioFile = new File("/Users/panxuanen/Documents/Projects/CS9053 Java/Final_Project/2.wav");
                AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
                clip.open(audioStream);
                clip.start();
                // Allow the clip to play for 5 seconds
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Playback interrupted");
                }
                clip.close();
            } catch (LineUnavailableException | UnsupportedAudioFileException | IOException e) {
                System.err.println("Failed to play audio on mixer " + mixer.getMixerInfo().getName() + ": " + e.getMessage());
                return false;
            }
        }
        return supports;
    }
}
