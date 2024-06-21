package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import util.PeerQueue;

public class CentralIndexingServer {

    private static Hashtable<String, ArrayList<Peer>> index;
    private static int port = 3434;
    private static int numThreads = 4;
    private static PeerQueue<Socket> peerQueue;

    protected static ArrayList<Integer> savePort = new ArrayList<>();
	protected static ArrayList<String> saveAddress = new ArrayList<>();
    protected static ArrayList<ArrayList<String>> saveFileNames = new ArrayList<>();
    public static Hashtable<String, ArrayList<Peer>> getIndex() {
        return index;
    }

    // New method to get all unique peers
    public static Set<Peer> getAllPeers() {
        Set<Peer> allPeers = new HashSet<>();
        for (ArrayList<Peer> peers : index.values()) {
            allPeers.addAll(peers);
        }
        return allPeers;
    }

    private static void server() throws IOException {

        @SuppressWarnings("resource")
        ServerSocket serverSocket = new ServerSocket(port);

        while (true) {
            System.out.println("\nWaiting for peer...");
            Socket socket = serverSocket.accept();
            synchronized (peerQueue) {
                peerQueue.add(socket);
            }
        }

    }
    public static void addFile(Peer peer, String fileName) {
        if (index.containsKey(fileName)) {
            index.get(fileName).add(peer);
        } else {
            ArrayList<Peer> newPeerList = new ArrayList<>();
            newPeerList.add(peer);
            index.put(fileName, newPeerList);
        }
    }
    public static void removeFile(Peer peer, String fileName) {
        if (index.containsKey(fileName)) {
            index.get(fileName).remove(peer);
            if (index.get(fileName).isEmpty()) {
                index.remove(fileName);
            }
        }
    }

    public static synchronized void updateIndex(Peer modifiedPeer) {
        // Remove all files of the modified peer from the index
        for (ArrayList<Peer> peers : index.values()) {
            peers.removeIf(peer -> peer.equals(modifiedPeer));
        }

        // Re-add all files of the modified peer to the index
        for (String fileName : modifiedPeer.getFileNames()) {
            addFile(modifiedPeer, fileName);
        }
    }

    private static void income() throws IOException {

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        while (true) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (peerQueue.peek() == null)
                continue;
            synchronized (peerQueue) {
                Socket socket = peerQueue.poll();
                Server s = new Server(socket);
                executor.execute(s);
            }
        }

    }

    public static synchronized void registry(int peerId, int numFiles, ArrayList<String> fileNames, String directory, String address, int port) {
        Peer newPeer = new Peer(peerId, numFiles, fileNames, directory, address, port);
        for (String fileName : fileNames) {
            if (index.containsKey(fileName)) {
                // Check if peerId already exists
                boolean peerExists = index.get(fileName).stream().anyMatch(peer -> peer.getPeerId() == peerId);
                if (!peerExists) {
                    index.get(fileName).add(newPeer);
                }
            } else {
                index.put(fileName, new ArrayList<Peer>());
                index.get(fileName).add(newPeer);
            }
        }
    }

    public static void main(String[] args) throws IOException {

        index = new Hashtable<String, ArrayList<Peer>>();
        peerQueue = new PeerQueue<Socket>();
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (Exception e) {
                System.out.println("Put a valid port number");
            }
        }

        new Thread() {
            public void run() {
                try {
                    server();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();

        new Thread() {
            public void run() {
                try {
                    income();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }
}