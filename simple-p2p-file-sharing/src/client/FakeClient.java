package client;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import util.Util;

public class FakeClient {
    private String folderDirectory;
    private int clientPort;
    private String serverAddress;
    private int serverPort;
    private Peer peer;

    public FakeClient(String folderDirectory, int clientPort, String serverAddress, int serverPort) {
        this.folderDirectory = folderDirectory;
        this.clientPort = clientPort;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
    }

    public void run() throws IOException {
        String dir = folderDirectory;
        File folder = new File(dir);
        String address = InetAddress.getLocalHost().getHostAddress();

        // Get all file names in folder
        ArrayList<String> fileNames = Util.listFilesForFolder(folder);

        peer = new Peer(dir, fileNames, fileNames.size(), address, clientPort);

        Socket socket = new Socket(serverAddress, serverPort);
        peer.register(socket);

        new Thread(() -> {
            try {
                peer.server();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        new Thread(() -> {
            try {
                peer.income();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        // Simulate some operations like listing files and looking up a file
        simulateOperations(socket);
    }

    private void simulateOperations(Socket socket) {
        try {
            // List files on the server
            List<String> serverFiles = peer.listServerFiles(socket);
            System.out.println("Server files: " + serverFiles);

            // Lookup a file (assuming we're looking for a file named "test.txt")
            String[] peerAddresses = peer.lookup("test.txt", socket, 1);
            System.out.println("Lookup result for 'test.txt': " + String.join(", ", peerAddresses));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Number of fake clients to simulate
        int numberOfClients = 500;

        // Parameters for the fake clients
        String serverAddress = "172.16.132.99";
        int serverPort = 3434;

        Random random = new Random();
        List<Thread> clientThreads = new ArrayList<>();

        for (int i = 0; i < numberOfClients; i++) {
            // Generate a unique directory and client port for each fake client
            String folderDirectory = "client_" + i + "_dir";
            int clientPort = 5000 + i; // Assign a random port for each fake client

            // Create the directory
            try {
                Path path = Paths.get(folderDirectory);
                if (!Files.exists(path)) {
                    Files.createDirectory(path);
                }
                // Optionally, create some dummy files in the directory
                for (int j = 0; j < 5; j++) {
                    Files.createFile(Paths.get(folderDirectory, "file" + j + ".txt"));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            FakeClient fakeClient = new FakeClient(folderDirectory, clientPort, serverAddress, serverPort);

            Thread clientThread = new Thread(() -> {
                try {
                    fakeClient.run();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            clientThreads.add(clientThread);
            clientThread.start();
        }

        // Wait for all client threads to finish
        for (Thread clientThread : clientThreads) {
            try {
                clientThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}