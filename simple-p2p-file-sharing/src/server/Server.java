package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;

public class Server extends Thread {

    private static int peerid = 0;
    private ArrayList<Peer> peerList = new ArrayList<>();
    private Socket socket;
    private static final HashMap<String, Integer> addressPortToPeerId = new HashMap<>();

    // private int getUniqueId() {
    //     synchronized (this) {
    //         return ++id;
    //     }
    // }

    public void newPeerList() {
        peerList = new ArrayList<Peer>();
    }

    public void addPeer(Peer peer) {
        peerList.add(peer);
    }

    public Boolean addAllPeer(ArrayList<Peer> peers) {
        peerList.addAll(peers);
        return true;
    }

    public Server(Socket socket) {
        this.socket = socket;
    }

    public Boolean search(String fileName) {
        newPeerList();
        return (CentralIndexingServer.getIndex().containsKey(fileName))
                ? addAllPeer(CentralIndexingServer.getIndex().get(fileName))
                : false;
    }
    // public Peer findPeerById(int peerId) {
    //     for (Peer peer : peerList) { // assuming 'peers' is your list of Peer objects
    //         if (Integer.valueOf(peer.getPeerId()).equals(peerId)) {
    //             return peer;
    //         }
    //     }
    //     return null;
    // }

    public void run() {
        try {
            DataInputStream dIn = new DataInputStream(socket.getInputStream());
            DataOutputStream dOut = new DataOutputStream(socket.getOutputStream());

            byte option = dIn.readByte();
			// System.out.println("Received request code: " + option);
            switch (option) {
                case 0:
                    // int peerId = getUniqueId();

                    

                    Boolean end = false;
                    ArrayList<String> fileNames = new ArrayList<String>();
                    int numFiles = 0, port = 0;
                    String directory = null, address = null;

                    while (!end) {
                        byte messageType = dIn.readByte();

                        switch (messageType) {
                            case 1:
                                numFiles = dIn.readInt();
                                // System.out.println("\nPeer " + peerId + " registering with " + numFiles + " files:");
                                // System.out.println(socket.getInetAddress().getHostAddress());
                                break;
                            case 2:
                                System.out.println("\nNew peer registering with " + numFiles + " files:");
                                for (int i = 0; i < numFiles; i++) {
                                    fileNames.add(dIn.readUTF());
                                    System.out.println(fileNames.get(i));
                                }
                                break;
                            case 3:
                                directory = dIn.readUTF();
                                break;
                            case 4:
                                address = dIn.readUTF();
                                break;
                            case 5:
                                port = dIn.readInt();
                                break;
                            default:
                                end = true;
                        }
                    }

                    // Check if the port and address are already in the lists
					boolean check = false;
					for (int i = 0; i < CentralIndexingServer.saveAddress.size(); i++){
						if (CentralIndexingServer.saveAddress.get(i).equals(address) && CentralIndexingServer.savePort.get(i).equals(port)){
							peerid = i + 1;
							check = true;

                            // clear the file names from the list
                            // CentralIndexingServer.saveFileNames.get(i).clear();
                            // CentralIndexingServer.saveFileNames.get(i).addAll(fileNames);
                            
							break;
						}
					}

					if (check == false){
						CentralIndexingServer.savePort.add(port);
						CentralIndexingServer.saveAddress.add(address);
						peerid = CentralIndexingServer.saveAddress.size();
					} 

					System.out.println("\nPeer ID: " + peerid + ", Address: " + address + ", Port: " + port);
					System.out.println("----------------------------------");

                    synchronized (this) {
                        CentralIndexingServer.registry(peerid, numFiles, fileNames, directory, address, port);
                    }

                    dOut.writeInt(peerid);
                    dOut.flush();
                    socket.close();
                    break;

                case 1:
                    String fileName = dIn.readUTF();
                    Set<Peer> allPeers = CentralIndexingServer.getAllPeers();
                    // for (Peer p : allPeers) {
                    //     p.refreshFileList();
                    // }
                    Boolean b = search(fileName);

                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    if (b) {
                        dOut.writeByte(1);
                        dOut.writeInt(peerList.size());
                        dOut.flush();

                        for (Peer p : peerList) {
                            dOut.writeUTF(p.getAddress() + ":" + p.getPort() + ":" + p.getPeerId());
                            dOut.flush();
                        }
                    } else {
                        dOut.writeByte(0);
                        dOut.flush();
                    }
                    socket.close();
                    break;
                
                // case 2: // Handle "LIST_FILES" request
                //     Set<Peer> allPeers2 = CentralIndexingServer.getAllPeers();
                //     Set<String> allFileNames = new HashSet<>(); // Use a HashSet to avoid duplicates

                //     for (Peer p : allPeers2) {
                //         // Refresh the file list from the peer
                //         // p.refreshFileList();
                //         allFileNames.addAll(p.getFileNames()); // AddAll still works fine with Sets
                //     }

                //     dOut.writeInt(allFileNames.size()); // Send the number of unique files
                //     dOut.flush();
                //     for (String file : allFileNames) {
                //         dOut.writeUTF(file); // Send each unique file name
                //         dOut.flush();
                //     }
                //     socket.close();
                //     break;

                case 2: // Handle "LIST_FILES" request
                    Set<Peer> allPeers2 = CentralIndexingServer.getAllPeers();
                    Set<String> allFileNames = new HashSet<>(); // Use a HashSet to avoid duplicates

                    for (Peer p : allPeers2) {
                        //p.refreshFileList();
                        allFileNames.addAll(p.getFileNames());
                    }

                    try {
                        dOut.writeInt(allFileNames.size()); // Send the number of unique files
                        dOut.flush();
                        for (String file : allFileNames) {
                            dOut.writeUTF(file); // Send each unique file name
                            dOut.flush();
                        }
                    } catch (IOException e) {
                        System.err.println("An I/O error occurred while sending file list: " + e.getMessage());
                    }
                    socket.close();
                    break;

                case 3:
                    String message = dIn.readUTF();

                    if (message.startsWith("DISCONNECT ")) {
                        String peerId = message.substring(11); // Get the peerId from the message
                        System.out.println("Peer " + peerId + " has disconnected");

                    }
                    break;
                case 4:
                    int peerId = dIn.readInt(); // Read the peerId from the client
                    Peer peer = null; // Initialize the peer variable
                    int size = dIn.readInt(); // Read the number of files to delete
                    // Iterate over all peers in the index
                    for (ArrayList<Peer> peers : CentralIndexingServer.getIndex().values()) {
                        for (Peer p : peers) {
                            if (p.getPeerId() == peerId) {
                                peer = p; // If the peerId matches, assign the peer and break the loop
                                break;
                            }
                        }
                        if (peer != null) {
                            break;
                        }
                    }
                    CentralIndexingServer.updateIndex(peer);
                    break;
                default:
                    System.out.println("Not an option");

                // close stream
                dIn.close();
                dOut.close();
                socket.close();

            }

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}