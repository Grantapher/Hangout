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
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashSet;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.text.DefaultCaret;

public class ChatServer {
	private static final int PORT = 9898;
	private static HashSet<String> names = new HashSet<String>();
	private static HashSet<PrintWriter> writers = new HashSet<PrintWriter>();
	private static String roomName;
	private static JTextArea text;
	private static int number = 0;
	private static boolean closed;
	
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
		final JFrame frame = new JFrame("Log of " + roomName);
		text = new JTextArea();
		frame.add(new JScrollPane(text), BorderLayout.CENTER);
		frame.setMinimumSize(new Dimension(384, 288));
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		final JButton button = new JButton("Close");
		frame.add(button, BorderLayout.SOUTH);
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if(!closed)
					attemptClose(frame, listener, button);
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
					attemptClose(frame, listener, button);
				System.exit(0);
			}
		});
		text.append(roomName + " is running!\n");
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
	
	static void attemptClose(JFrame frame, ServerSocket listener,
			JButton button) {
		try {
			if(JOptionPane.showConfirmDialog(frame,
					"Are you sure you want to close the connection?",
					"Connection Closing",
					JOptionPane.YES_NO_CANCEL_OPTION,
					JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION)
				return;
			listener.close();
			log("Connection Closed");
			closed = true;
			button.setEnabled(false);
		}
		catch(IOException e) {
			log("listener didn't close" + e);
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
					out.println("Identify yourself!");
					name = in.readLine();
					if(name == null) {
						return;
					}
					synchronized(names) {
						if(!names.contains(name)) {
							names.add(name);
							break;
						}
					}
					out.println("Name is already taken.");
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
			catch(IOException e) {
				log("Client #" + number + " (" + name + ") "
						+ e.toString());
			}
			finally {
				// This client is going down!  Remove its name and its print
				// writer from the sets, and close its socket.
				if(name != null) {
					names.remove(name);
				}
				if(out != null) {
					writers.remove(out);
				}
				try {
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