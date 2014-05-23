import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.text.DefaultCaret;

public class ChatServer {
	private static final int PORT = 9898;
	private static HashSet<String> names = new HashSet<String>();
	private static HashSet<PrintWriter> writers = new HashSet<PrintWriter>();
	private static HashSet<Socket> sockets = new HashSet<Socket>();
	private static String roomName;
	private static JTextArea text;
	private static int number = 0;
	private static boolean closed;
	private static JTextField field;
	private static JButton button;
	private static JFrame frame;
	
	public static void main(String[] args) throws Exception {
		javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager
				.getSystemLookAndFeelClassName());
		//get roomName
		if((roomName = JOptionPane.showInputDialog(null,
				"What is the name of the chatroom?", "Name",
				JOptionPane.QUESTION_MESSAGE)) == null)
			System.exit(0);
		roomName = "Chatroom: " + roomName;
		//initialize socket
		final ServerSocket listener = new ServerSocket(PORT);
		//initialize gui
		frame = new JFrame("Log of " + roomName);
		text = new JTextArea();
		frame.add(new JScrollPane(text), BorderLayout.CENTER);
		frame.setMinimumSize(new Dimension(384, 288));
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		button = new JButton("Close");
		frame.add(button, BorderLayout.EAST);
		field = new JTextField();
		frame.add(field, BorderLayout.SOUTH);
		field.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String input = field.getText();
				field.setText("");
				if(input.length() < 4) {
					text.append("The command \"" + input
							+ "\" is not recognized.\n");
					return;
				}
				if(input.substring(0, 4).equalsIgnoreCase(
						new String("list"))) {
					text.append("\nCurrently in the chat:\n");
					for(String name : names) {
						text.append(name + "\n");
					}
					text.append("\n");
					return;
				}
				if(input.length() < 10) {
					text.append("The command \"" + input
							+ "\" is not recognized.\n");
					return;
				}
				if(input.substring(0, 10).equalsIgnoreCase("broadcast ")) {
					for(PrintWriter writer : writers) {
						writer.println("SERVER>" + input.substring(9));
					}
					return;
				}
				text.append("The command \"" + input
						+ "\" is not recognized.\n");
			}
		});
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if(!closed)
					attemptClose(listener);
			}
		});
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		text.setBackground(null);
		text.setBorder(null);
		text.setEditable(false);
		DefaultCaret caret = (DefaultCaret) text.getCaret();
		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if(!closed)
					attemptClose(listener);
				System.exit(0);
			}
		});
		URL myIP = new URL("http://www.trackip.net/ip");
		String ipAddress = null;
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(
					myIP.openStream()));
			ipAddress = in.readLine();
		}
		catch(Exception e) {}
		if(InetAddress.getLocalHost().getHostAddress().trim()
				.equals("127.0.0.1"))
			text.append(roomName + " is running at "
					+ InetAddress.getLocalHost().getHostAddress()
					+ " locally!\n");
		else if(ipAddress != null)
			text.append(roomName + " is running at "
					+ InetAddress.getLocalHost().getHostAddress()
					+ " internally and " + ipAddress + " externally!\n");
		else
			text.append(roomName + " is running at "
					+ InetAddress.getLocalHost().getHostAddress()
					+ " internally!\n");
		frame.setVisible(true);
		try {
			while(true) {
				new Handler(listener.accept(), number++).start();
			}
		}
		catch(SocketException e) {}
		finally {
			listener.close();
		}
	}
	
	static void attemptClose(ServerSocket listener) {
		try {
			if(JOptionPane.showConfirmDialog(frame,
					"Are you sure you want to close the connection?",
					"Connection Closing",
					JOptionPane.YES_NO_CANCEL_OPTION,
					JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION)
				return;
			listener.close();
			closed = true;
			button.setEnabled(false);
			field.setEnabled(false);
			// This client is going down!  Remove its name and its print
			// writer from the sets, and close its socket.
			for(PrintWriter writer : writers) {
				writer.println("SERVER> Shutting down.");
			}
			try {
				synchronized(sockets) {
					for(Socket socket : sockets) {
						socket.close();
					}
				}
			}
			catch(IOException e) {
				log("A socket didn't close." + e);
			}
			synchronized(names) {
				for(String name : names) {
					if(name != null) {
						names.remove(name);
					}
				}
			}
			synchronized(writers) {
				for(PrintWriter out : writers) {
					if(out != null) {
						writers.remove(out);
					}
				}
			}
		}
		catch(IOException e) {
			log("listener didn't close" + e);
		}
		catch(ConcurrentModificationException e) {}
		catch(Exception e) {
			log(e.toString());
		}
		finally {
			log("Connection Closed");
		}
	}
	
	private static class Handler extends Thread {
		private String name;
		private Socket socket;
		private BufferedReader in;
		private PrintWriter out;
		private int number;
		
		public Handler(Socket socket, int number) {
			this.socket = socket;
			this.number = number;
			log("New connection at " + socket);
			log("Connected to client #" + number + ".");
		}
		
		public void run() {
			try {
				sockets.add(socket);
				// Create character streams for the socket.
				in = new BufferedReader(new InputStreamReader(
						socket.getInputStream()));
				out = new PrintWriter(socket.getOutputStream(), true);
				// Request a name from this client.  Keep requesting until
				// a name is submitted that is not already used.  Note that
				// checking for the existence of a name and adding the name
				// must be done while locking the set of names.
				out.println(roomName);
				while(true) {
					out.println("What is your name?");
					name = in.readLine();
					if(name == null) {
						return;
					}
					if(name.length() <= 18) {
						synchronized(names) {
							if(!names.contains(name)) {
								names.add(name);
								break;
							}
						}
						out.println("Name is already taken.");
					} else
						out.println("Name must be 18 characters or less.");
				}
				log("Client #" + number + " has identified as: \"" + name
						+ "\"");
				// Now that a successful name has been chosen, add the
				// socket's print writer to the set of all writers so
				// this client can receive broadcast messages.
				out.println("Welcome to " + roomName + ", " + name + "!");
				for(PrintWriter writer : writers) {
					writer.println(name + " has entered the room.");
				}
				writers.add(out);
				// Accept messages from this client and broadcast them.
				// Ignore other clients that cannot be broadcasted to.
				while(true) {
					String input = in.readLine();
					if(input == null) {
						for(PrintWriter writer : writers) {
							writer.println(name + " has disconnected.");
						}
						return;
					}
					if(input.equals("!list")) {
						out.println("\nCurrently in the chat:");
						for(String name : names) {
							out.println(name);
						}
						out.println();
					} else {
						for(PrintWriter writer : writers) {
							writer.println(name + "> " + input);
						}
					}
				}
			}
			catch(SocketException e) {}
			catch(IOException e) {
				log("Client #" + number + " (" + name + ") "
						+ e.toString());
			}
			finally {
				// This client is going down!  Remove its name and its print
				// writer from the sets, and close its socket.
				if(name != null)
					names.remove(name);
				if(out != null)
					writers.remove(out);
				try {
					if(!socket.isClosed())
						socket.close();
					log("Connection with client #" + number + " (" + name
							+ ")" + " has closed.");
				}
				catch(IOException e) {
					log("Client #" + number + "'s (" + name + "'s)"
							+ " socket didn't close." + e);
				}
			}
		}
	}
	
	private static void log(String string) {
		text.append(string + "\n");
	}
}