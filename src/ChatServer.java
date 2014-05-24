import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
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
	protected static HashSet<Handler> handlers = new HashSet<Handler>();
	protected static String roomName;
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
				if(input.substring(0, 4).equalsIgnoreCase("list")) {
					text.append("\nCurrently in the chat:\n");
					synchronized(ChatServer.handlers) {
						for(Handler handler : handlers) {
							text.append("\"" + handler.getIdentity()
									+ "\"\n");
						}
					}
					text.append("\n");
					return;
				}
				if(input.length() < 5) {
					text.append("The command \"" + input
							+ "\" is not recognized.\n");
					return;
				}
				if(input.substring(0, 5).equalsIgnoreCase("kick ")) {
					String tempIdentity = input.substring(5);
					synchronized(ChatServer.handlers) {
						for(Handler handler : handlers) {
							if(handler.getIdentity().equals(tempIdentity)) {
								handler.getWriter().println(
										"SERVER> You have been kicked.");
								handler.close();
								handlers.remove(handler);
								for(Handler handler1 : handlers) {
									handler1.getWriter().println(
											"SERVER> " + tempIdentity
													+ " has been kicked.");
								}
								log(tempIdentity + " has been kicked.\n");
								return;
							}
						}
					}
					text.append("The name \"" + tempIdentity
							+ "\" is not in the chatroom.\n");
					return;
				}
				if(input.length() < 10) {
					text.append("The command \"" + input
							+ "\" is not recognized.\n");
					return;
				}
				if(input.substring(0, 10).equalsIgnoreCase("broadcast ")) {
					synchronized(ChatServer.handlers) {
						for(Handler handler : handlers) {
							handler.getWriter().println(
									"SERVER>" + input.substring(9));
						}
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
				if(closed || attemptClose(listener))
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
			Handler temp = null;
			while(true) {
				temp = new Handler(listener.accept(), number++, roomName);
				handlers.add(temp);
				temp.start();
			}
		}
		catch(SocketException e) {}
		finally {
			listener.close();
		}
	}
	
	static boolean attemptClose(ServerSocket listener) {
		try {
			if(JOptionPane.showConfirmDialog(frame,
					"Are you sure you want to close the connection?",
					"Connection Closing",
					JOptionPane.YES_NO_CANCEL_OPTION,
					JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION)
				return false;
			listener.close();
			closed = true;
			button.setEnabled(false);
			field.setEnabled(false);
			// This client is going down!  Remove its name and its print
			// writer from the sets, and close its socket.
			synchronized(ChatServer.handlers) {
				for(Handler handler : handlers) {
					handler.getWriter().println("SERVER> Shutting down.");
				}
			}
			synchronized(handlers) {
				for(Handler handler : handlers) {
					handler.close();
				}
			}
			handlers.clear();
		}
		catch(IOException e) {
			log("listener didn't close" + e);
		}
		catch(ConcurrentModificationException e) {}
		catch(Exception e) {
			log(e.toString());
		}
		log("Connection Closed\n");
		return true;
	}
	
	public static void log(String string) {
		text.append(string + "\n");
	}
}