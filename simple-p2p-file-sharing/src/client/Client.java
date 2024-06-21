package client;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


import util.Util;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class Client {
    public JFrame frame;
	public JPanel panel;
    public JButton lookupButton;
    public JButton downloadButton;
	public JButton listFilesButton;
    public JButton exitButton;
    public Peer peer;
    public String[] peerAddress = new String[0];
	public JButton runClientButton;
	public JLabel backgroundLabel;

	public JTextField createCustomTextField(String text) {
		JTextField field = new JTextField();
		field.addFocusListener(new FocusListener() {
			@Override
			public void focusGained(FocusEvent e){
				if (field.getText().equals(text)) {
					field.setForeground(Color.BLACK);
					field.setText("");
				}
			}
			
			@Override
			public void focusLost(FocusEvent e) {
				if (field.getText().isEmpty()) {
					field.setForeground(Color.GRAY);
					field.setText(text);
				}
			}
		});
		return field;
	}

	public JTextField folderDirectoryField = createCustomTextField("Enter the folder directory here");
    public JTextField clientPortField = createCustomTextField("Enter the client port here");
    public JTextField serverAddressField = createCustomTextField("Enter the server address here");
    public JTextField serverPortField = createCustomTextField("Enter the server port here");
    public JTextField fileNameField;

    public Client() {
        frame = new JFrame("Peer UI");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(600, 300);
		frame.setLocationRelativeTo(null);


		ImageIcon background = new ImageIcon("simple-p2p-file-sharing copy/background.jpg"); // Replace with your image path
		backgroundLabel = new JLabel();
		backgroundLabel.setIcon(background);
		backgroundLabel.setHorizontalAlignment(JLabel.CENTER);
		frame.add(backgroundLabel, BorderLayout.NORTH);


        panel = new JPanel();
        panel.setLayout(new GridLayout(6, 2));

        panel.add(new JLabel("Folder Directory:"));
		folderDirectoryField.setForeground(Color.GRAY);
		folderDirectoryField.setText("Enter the folder directory here");
        panel.add(folderDirectoryField);

        panel.add(new JLabel("Client Port:"));
		clientPortField.setForeground(Color.GRAY);
		clientPortField.setText("Enter the client port here");
        panel.add(clientPortField);

        panel.add(new JLabel("Server Address:"));
		serverAddressField.setForeground(Color.GRAY);
		serverAddressField.setText("Enter the server address here");
        panel.add(serverAddressField);

        panel.add(new JLabel("Server Port:"));
		serverPortField.setForeground(Color.GRAY);
		serverPortField.setText("Enter the server port here");
        panel.add(serverPortField);

        runClientButton = new JButton("Run Client");
		runClientButton.addActionListener(new runClientButtonListener());
		panel.add(runClientButton);

		// panel.setBackground(Color.WHITE);

		// bgpanel.add(panel, BorderLayout.CENTER);

		// Add panel to frame
		frame.add(panel);
		frame.setVisible(true);


        //
    }
	public class runClientButtonListener implements ActionListener {

		public void actionPerformed(ActionEvent e) {
			String folderDirectory = folderDirectoryField.getText();
			int clientPort = Integer.parseInt(clientPortField.getText());
			String serverAddress = serverAddressField.getText();
			int serverPort = Integer.parseInt(serverPortField.getText());
	
			try {
				runClient(folderDirectory, clientPort, serverAddress, serverPort);
				// startTimer(); // Start the timer after running the client
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	

	}
	

	int count = 0;
	private List<String> deletedFiles = new ArrayList<>();
    public void runClient(String folderDirectory, int clientPort, String serverAddress, int serverPort) throws IOException {
        String dir = folderDirectory;
        File folder = new File(dir);
        String address = InetAddress.getLocalHost().getHostAddress();

		//get all file names in folder
        ArrayList<String> fileNames = Util.listFilesForFolder(folder);


        final Peer peer = new Peer(dir, fileNames, fileNames.size(), address, clientPort);

        Socket socket = new Socket(serverAddress, serverPort);
        peer.register(socket);

		int peerId = peer.getPeerId();

		if (count == 0){
			JOptionPane.showMessageDialog(null, "Peer " + peerId + " is registered");
		}
		count++;

		new Thread(() -> {
            try {
                WatchService watcher = FileSystems.getDefault().newWatchService();
                Path dir1 = Paths.get(dir);
                dir1.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);

                while (true) {
                    WatchKey key;
                    try {
                        key = watcher.take();
                    } catch (InterruptedException ex) {
                        return;
                    }

                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            continue;
                        }

                        // The filename is the context of the event.
                        WatchEvent<Path> ev = (WatchEvent<Path>) event;
                        Path fileName = ev.context();

                        System.out.println(kind.name() + ": " + fileName);

						// if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        //     deletedFiles.add(fileName.toString()); // Add this line to store the deleted file name

                        // }

                        if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_DELETE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                            try {
								runClient(folderDirectory, clientPort, serverAddress, serverPort);
								Socket newSocket = new Socket(serverAddress, serverPort);
								peer.notifyFileDeletion(deletedFiles, newSocket);
							} catch (IOException e) {
								System.err.println("Failed to register peer: " + e);
							}
                        }
                    }

					// run nofityFileDeletion with new socket
					// try {
					// 	Socket newSocket = new Socket(serverAddress, serverPort);
					// 	peer.notifyFileDeletion(deletedFiles, newSocket);
					// } catch (IOException e) {
					// 	System.err.println("Failed to notify server of file deletion: " + e);
					// }

                    boolean valid = key.reset();
                    if (!valid) {
                        break;
                    }
                }
            } catch (IOException ex) {
                System.err.println(ex);
            }
        }).start();

        new Thread() {
            public void run() {
                try {
                    peer.server();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();

        new Thread() {
            public void run() {
                try {
                    peer.income();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    	// String [] peerAddress = new String[0];

		// panel.removeAll();
		// panel.setLayout(new GridLayout(1,3)); // 1 row, 3 columns
		
		// Add buttons to list all file of the peer registered in the server

		JPanel buttonPanel = new JPanel(new GridLayout(1, 4));
		// buttonPanel.add(new JLabel("Options:"));
		listFilesButton = new JButton("List Files");
		lookupButton = new JButton("Lookup File");
		downloadButton = new JButton("Download File");
		exitButton = new JButton("Exit Server");
		
		buttonPanel.add(listFilesButton);
		buttonPanel.add(lookupButton);
		buttonPanel.add(downloadButton);
		buttonPanel.add(exitButton);

		frame.add(buttonPanel, BorderLayout.SOUTH);


		lookupButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				panel.removeAll();
				panel.setLayout(new GridLayout(1,3));
				panel.add(new JLabel("File Name:"));
				fileNameField = new JTextField();
				panel.add(fileNameField);
				JButton lookupButton2 = new JButton("Lookup");
				lookupButton2.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						String fileName = fileNameField.getText();
						try {
							peerAddress = peer.lookup(fileName, new Socket(serverAddressField.getText(), Integer.parseInt(serverPortField.getText())), 1);
							// Display the lookup result
							StringBuilder message = new StringBuilder();

							// for (String peerAdd : peerAddress){
							// 	String paddress[] = peerAdd.split(":");
							// 	message.append("Peer " + paddress[2] + " - " + paddress[0] +":" + paddress[1] + " has the file " + fileName + "! - Looked by Peer " + peerId + "\n");
							// }
							if(peerAddress.length == 0) {
								message.append("File not found on any peer.");
							} else {
								// message.append("File found on peer(s): ");
								// for (String addr : peerAddress) {
								// 	String paddress[] = addr.split(":");
								// 	message.append(paddress[2] + ", ");
								// }
								// message.delete(message.length() - 2, message.length());
								message.append("File found!!!");
							}
							JOptionPane.showMessageDialog(null, message.toString());
							// System.out.println("Lookup result: " + Arrays.toString(peerAddress));
						} catch (IOException ex) {
							ex.printStackTrace();
						}
					}
				});
				panel.add(lookupButton2);
				panel.revalidate();
				panel.repaint();
			}
		});

		downloadButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String fileName = fileNameField.getText();
				String message = "";
				String inputDirectory = "";
				if (peerAddress.length == 0) {
					System.out.println("Lookup for the peer first.");
				} else if (peerAddress.length == 1 && Integer.parseInt(peerAddress[0].split(":")[2]) == peer.getPeerId()) {
					JOptionPane.showMessageDialog(null, "This peer has the file already, not downloading then.");
				} else if (peerAddress.length == 1) {
					String[] addrport = peerAddress[0].split(":");
					JOptionPane.showMessageDialog(null, "Downloading from peer " + addrport[2] + ": " + addrport[0] + ":" + addrport[1]);
					try {
						inputDirectory = JOptionPane.showInputDialog("Enter the directory to saved the file to:");
						if (inputDirectory == null || inputDirectory.trim().isEmpty()) {
							JOptionPane.showMessageDialog(null, "No directory entered. Download cancelled.");
							return;
						}
						message = peer.download(addrport[0], Integer.parseInt(addrport[1]), fileName, -1, inputDirectory);
						JOptionPane.showMessageDialog(null, message);
					} catch (IOException ex) {
						ex.printStackTrace();
					}
				} else {
					// Display a dialog to select the peer to download from
					String[] options = new String[peerAddress.length];
					for (int i = 0; i < peerAddress.length; i++) {
						String[] addrport = peerAddress[i].split(":");
						options[i] = addrport[0] + ":" + addrport[1];
					}
					String selectedPeer = (String) JOptionPane.showInputDialog(frame, "Select the peer to download from:", "Download File",
							JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
					if (selectedPeer != null) {
						String[] addrport = selectedPeer.split(":");
						try {
							inputDirectory = JOptionPane.showInputDialog("Enter the directory to saved the file to:");
							if (inputDirectory == null || inputDirectory.trim().isEmpty()) {
								JOptionPane.showMessageDialog(null, "No directory entered. Download cancelled.");
								return;
							}
							message = peer.download(addrport[0], Integer.parseInt(addrport[1]), fileName, -1, inputDirectory);
							JOptionPane.showMessageDialog(null, message);
						} catch (IOException ex) {
							ex.printStackTrace();
						}
					}
				}

			}
		});

		// list file of other peer in the server using the listServerFiles from the peer.java

		listFilesButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				new Thread(new Runnable() {
					public void run() {
						try {
							System.out.println("Attempting to list files from server...");
							List<String> serverFiles = peer.listServerFiles(new Socket(serverAddressField.getText(), Integer.parseInt(serverPortField.getText())));
							if (serverFiles.isEmpty()) {
								System.out.println("No files found on the server.");
							} else {
								System.out.println("Files received from server: " + serverFiles);
							}
							SwingUtilities.invokeLater(new Runnable() {
								public void run() {
									JList<String> fileList = new JList<>(serverFiles.toArray(new String[0]));
									JOptionPane.showMessageDialog(frame, new JScrollPane(fileList), "Files on Server", JOptionPane.INFORMATION_MESSAGE);
								}
							});
						} catch (IOException ex) {
							ex.printStackTrace();
							System.out.println("Failed to list files from server: " + ex.getMessage());
						}
					}
				}).start();
			}
		});


		exitButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				System.out.println("Peer disconnected!");
				try {
					peer.disconnect(new Socket(serverAddressField.getText(), Integer.parseInt(serverPortField.getText())));
					System.exit(0);
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		});

		// Refresh the panel
		// panel.revalidate();
		// panel.repaint();

		frame.setSize(600, 300);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

	}
		

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new Client();
            }
        });
    }
}