package client;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import util.PeerQueue;
import util.Util;

public class Peer {
	
	private int peerId;
	private int numFiles;
	private ArrayList<String> fileNames;
	private String directory;
	private String address;
	private int port;
	private PeerQueue<Connection> peerQueue;
	private int numThreads = 4;
	public ServerSocket serverSocket;
	
	public Peer(String directory, ArrayList<String> fileNames, int numFiles, String address, int port) throws IOException{
		this.directory = directory;
		this.fileNames = fileNames;
		this.numFiles = numFiles;
		this.address = address;
		this.port = port;
		
		peerQueue = new PeerQueue<Connection>();
	}
	
	//getters
		public int getPeerId(){
			return peerId;
		}
		
		public int getNumFiles(){
			return numFiles;
		}
		
		public ArrayList<String> getFileNames(){
			return fileNames;
		}
		
		public String getDirectory(){
			return directory;
		}
		
		public String getAddress(){
			return address;
		}
		
		public int getPort(){
			return port;
		}
		
		//setters
		public void setPeerId(int peerId){
			this.peerId = peerId;
		}
		
		public void setNumFiles(int numFiles){
			this.numFiles = numFiles;
		}
		
		public void setFileNames(ArrayList<String> fileNames){
			this.fileNames.addAll(fileNames);
		}
		
		public void addFileName(String fileName){
			this.fileNames.add(fileName);
		}
		
		public void setDirectory(String directory){
			this.directory = directory;
		}
		
		public void setAddress(String address){
			this.address = address;
		}
		
		public void setPort(int port){
			this.port = port;
		}
    
    public void register(Socket socket) throws IOException {

    	//System.out.println("Connecting to the server...");
		long start = System.currentTimeMillis();
		DataOutputStream dOut = new DataOutputStream(socket.getOutputStream());

    	//Option to register in the server (new peer)
		dOut.writeByte(0);
		dOut.flush();

		//Number of files
		dOut.writeByte(1);
		dOut.writeInt(numFiles);
		dOut.flush(); 
		//Files names
		dOut.writeByte(2);
		for(String str : fileNames)
			dOut.writeUTF(str);
		dOut.flush();
		dOut.writeByte(3);
		dOut.writeUTF(directory);
		dOut.flush();
		dOut.writeByte(4);
		dOut.writeUTF(address);
		dOut.flush();
		dOut.writeByte(5);
		dOut.writeInt(port);
		dOut.flush();
		dOut.writeByte(-1);
		dOut.flush();


    	//Reading the Unique Id from the Server
		DataInputStream dIn = new DataInputStream(socket.getInputStream());
		this.peerId = dIn.readInt();

		dOut.close();
		dIn.close();
		socket.close();

		System.out.println("Running as Peer " + peerId + "! " + "It Took " + (System.currentTimeMillis() - start) + "ms for register.");
		
    	//System.out.println("Took " + (System.currentTimeMillis() - start) + " ms to register in the server.");
    	//System.out.println((System.currentTimeMillis() - start) + " ms");
    	// if(BenchRegistry.times != null) BenchRegistry.times.add((System.currentTimeMillis() - start));
	}

    public String[] lookup(String fileName, Socket socket, int count) throws IOException{
		DataOutputStream dOut = new DataOutputStream(socket.getOutputStream());
		
		//Option to look for a file
		dOut.writeByte(1);
		
		String [] peerAddress = new String[0];
		
		//File name
		dOut.writeUTF(fileName);
		dOut.flush();
		//System.out.println("Reading from the server...");
		
		System.out.println("Peer " + peerId + " - looking for file. (" + count + ")");
		
		//Reading the peer Address that has the file
		DataInputStream dIn = new DataInputStream(socket.getInputStream());
		byte found = dIn.readByte();
		
		if(found == 1){
			int qt = dIn.readInt();
			
			peerAddress = new String[qt];
			
			for(int i = 0; i < qt; i++){
				try{
					peerAddress[i] = dIn.readUTF();
				}catch (EOFException e){
					i--;
				}
				// String paddress[] = peerAddress[i].split(":");
				// System.out.println("Peer " + paddress[2] + " - " + paddress[0] +":" + paddress[1] + " has the file " + fileName + "! - Looked by Peer " + peerId);
			}
		} else if(found == 0){
			System.out.println("File not found in the system");
			peerAddress = new String[0];
		}
		
		dOut.close();
		dIn.close();
		socket.close();
		return peerAddress;
    }
    
    public void server() throws IOException{
		
		try {
			serverSocket = new ServerSocket(port);
		} catch(Exception e){
			return;
		}
		
		while(true){
			Socket socket = serverSocket.accept();
			synchronized(peerQueue){
				peerQueue.add(new Connection(socket,directory));
			}
			// try {
			// 	Thread.sleep(2);
			// } catch (InterruptedException e) {
			// 	e.printStackTrace();
			// }
		}
		
	}
    
    public void income() throws IOException{
		
		ExecutorService executor = Executors.newFixedThreadPool(numThreads);

		while(true){
			if(peerQueue.peek() == null){
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				continue;
			}
			synchronized(peerQueue){
				//System.out.println("Added to executor");
				Connection c = peerQueue.poll();
				Server s = new Server(c.getSocket(), c.getDirectory());
				executor.execute(s);
			}
		}
		
	}
    
    public String download(String peerAddress, int port, String fileName, int i, String inputdirectory) throws IOException {
		String message = "";
		Socket socket = new Socket(peerAddress, port);
		DataOutputStream dOut = new DataOutputStream(socket.getOutputStream());
		dOut.writeUTF(fileName);
		InputStream in = socket.getInputStream();
	
		String peerDirectory = inputdirectory;
		File folder = new File(peerDirectory);
		Boolean created = false;
		if (!folder.exists()) {
			try {
				created = folder.mkdir();
			} catch (Exception e) {
				// System.out.println("Couldn't create the folder, the file will be saved in the current directory!");
				message = "Couldn't create the folder, the file will be saved in the current directory!";
			}
		} else {
			created = true;
		}
	
		if (i != -1) {
			fileName = fileName + i;
		}
	
		OutputStream out = (created) ? new FileOutputStream(peerDirectory + "/" + fileName) : new FileOutputStream(fileName);

		// message = Util.copy(in, out);
		// // System.out.println("File " + fileName + " received from peer " + peerAddress + ":" + port);
		// // message += "File " + fileName + " received from peer " + peerAddress + ":" + port;
		// // Check if Util.copy() returned an error message
		// if (message.contains("Can't continue download file")) {
		// 	// System.out.println("Peer " + peerId + " disconnected while downloading file " + fileName);
		// 	message += "Peer " + peerId + " disconnected while downloading file " + fileName;
		// }
		// else {
		// 	// System.out.println("File " + fileName + " received from peer " + peerAddress + ":" + port);
		// 	message += "File " + fileName + " received from peer " + peerAddress + ":" + port;
		// }
		try {
			message = Util.copy(in, out);
			System.out.println(message);
		} catch (IOException e) {
			// System.err.println("Error during file transfer: " + e.getMessage());
			message = "Error during file transfer: " + e.getMessage();
			// Handle the error (e.g., by retrying the download, notifying the user, etc.)
		}
		dOut.close();
		out.close();
		in.close();
		socket.close();
		return message;
	}

	public List<String> listServerFiles(Socket socket) throws IOException {
		DataOutputStream dOut = new DataOutputStream(socket.getOutputStream());
		DataInputStream dIn = new DataInputStream(socket.getInputStream());
	
		// Send request to list files (option 2)
		dOut.writeByte(2);
		dOut.flush();
	
		// Read the number of files from the server
		int numFiles = dIn.readInt();
		List<String> fileNames = new ArrayList<>();
	
		// Read each file name
		for (int i = 0; i < numFiles; i++) {
			fileNames.add(dIn.readUTF());
		}
		
		dOut.close();
		dIn.close();
		socket.close();
	
		return fileNames; // Return the list of file names
	}

    public void disconnect(Socket socket) throws IOException {
		DataOutputStream dOut = new DataOutputStream(socket.getOutputStream());
		// DataInputStream dIn = new DataInputStream(socket.getInputStream());
        try {
            dOut.writeByte(3);
            dOut.flush();

			dOut.writeUTF("DISCONNECT " + this.peerId);
            dOut.flush();
        } catch (IOException e) {
            System.out.println("Error while disconnecting: " + e.getMessage());
        }
		dOut.close();
    }
	public void notifyFileDeletion(List<String> fileNames, Socket socket) throws IOException {
		DataOutputStream dOut = new DataOutputStream(socket.getOutputStream());
		try {
			dOut.writeByte(4); // 4 is the code for file deletion
			dOut.flush();
			dOut.writeInt(this.peerId); // send the peerId
			dOut.flush();
			// send file names length
			dOut.writeInt(fileNames.size());
			dOut.flush();
			for (String fileName : fileNames) {
				dOut.writeUTF(fileName); // the name of the deleted file
				dOut.flush();

			}
		} catch (IOException e) {
			System.out.println("Error while notifying file deletion: " + e.getMessage());
		} finally {
			dOut.close();
		}
	}

}

	
