import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Toolkit;
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
import java.util.Vector;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultCaret;

public class ChatServer {
	public static Vector<Handler> handlers = new Vector<Handler>();
	public static String roomName;
	private static final int PORT = 9898;
	private static JTextArea text;
	private static int number = 0;
	private static boolean closed;
	private static JButton closeButton;
	private static JButton kickButton;
	private static JComboBox<Handler> comboBox;
	private static JFrame frame;
	private static ServerSocket listener;
	private static JPanel contentPane;
	private static JButton listButton;
	private static JButton castButton;
	private static Handler temp;
	
	public ChatServer() {
		frame = new JFrame("Log of " + roomName);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if(closed || attemptClose(listener))
					System.exit(0);
			}
		});
		frame.setMinimumSize(new Dimension(384, 288));
		frame.setPreferredSize(new Dimension(768, 576));
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		
		contentPane = new JPanel();
		contentPane.setLayout(new BorderLayout(0, 0));
		frame.setContentPane(contentPane);
		
		text = new JTextArea();
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		text.setBackground(null);
		text.setBorder(null);
		text.setEditable(false);
		contentPane.add(new JScrollPane(text), BorderLayout.CENTER);
		
		DefaultCaret caret = (DefaultCaret) text.getCaret();
		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
		
		JPanel eastPan = new JPanel();
		eastPan.setLayout(new BorderLayout(0, 0));
		eastPan.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPane.add(eastPan, BorderLayout.EAST);
		
		JPanel commandPan = new JPanel();
		commandPan.setLayout(new BoxLayout(commandPan, BoxLayout.Y_AXIS));
		eastPan.add(commandPan, BorderLayout.SOUTH);
		
		JLabel lblCommands = new JLabel("Commands:");
		lblCommands.setAlignmentX(Component.CENTER_ALIGNMENT);
		commandPan.add(lblCommands);
		
		JPanel userPan = new JPanel();
		userPan.setLayout(new BoxLayout(userPan, BoxLayout.Y_AXIS));
		eastPan.add(userPan, BorderLayout.NORTH);
		
		JLabel lblPerson = new JLabel("Name:");
		lblPerson.setAlignmentX(Component.CENTER_ALIGNMENT);
		userPan.add(lblPerson);
		
		comboBox = new JComboBox<Handler>(handlers);
		comboBox.setAlignmentX(Component.CENTER_ALIGNMENT);
		comboBox.setPreferredSize(new Dimension(89, 20));
		userPan.add(comboBox);
		
		closeButton = new JButton("Close");
		closeButton.setAlignmentX(Component.CENTER_ALIGNMENT);
		closeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if(attemptClose(listener))
					log("Connection Closed.\n");
			}
		});
		commandPan.add(closeButton);
		
		listButton = new JButton("List");
		listButton.setAlignmentX(Component.CENTER_ALIGNMENT);
		listButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				showList();
			}
		});
		commandPan.add(listButton);
		
		castButton = new JButton("Broadcast");
		castButton.setAlignmentX(Component.CENTER_ALIGNMENT);
		castButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				broadcast();
			}
		});
		commandPan.add(castButton);
		
		kickButton = new JButton("Kick");
		kickButton.setAlignmentX(Component.CENTER_ALIGNMENT);
		kickButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if(comboBox.getSelectedItem() == null) {
					Toolkit.getDefaultToolkit().beep();
					return;
				}
				kick((Handler) comboBox.getSelectedItem());
				comboBox.setSelectedIndex(-1);
			}
		});
		userPan.add(kickButton);
		
		frame.setVisible(true);
	}
	
	private void broadcast() {
		String input = JOptionPane.showInputDialog(frame,
				"What would you like to broadcast to " + roomName + "?",
				"Broadcast", JOptionPane.PLAIN_MESSAGE);
		if(input == null)
			return;
		if((input.equals("")))
			return;
		synchronized(handlers) {
			for(Handler handler : handlers) {
				handler.getWriter().println("SERVER> " + input);
			}
		}
		return;
	}
	
	private void showList() {
		if(handlers.isEmpty()) {
			log("\nThe chat is currently empty.");
			return;
		}
		log("\nCurrently in the chat:");
		synchronized(handlers) {
			for(Handler handler : handlers) {
				log("\"" + handler.getIdentity() + "\"");
			}
		}
		log();
		return;
	}
	
	private static void kick(Handler handler) {
		if(JOptionPane.showConfirmDialog(frame,
				"Are you sure you want to kick \"" + handler + "\"?",
				"Kick " + handler + "?", JOptionPane.YES_NO_OPTION) == JOptionPane.NO_OPTION)
			return;
		handler.getWriter().println("SERVER> You have been kicked.");
		handler.close();
		destroy(handler);
		for(Handler handler1 : handlers) {
			handler1.getWriter().println(
					"SERVER> " + handler + " has been kicked.");
		}
		log(handler + " has been kicked.\n");
	}
	
	public static void destroy(final Handler handler) {
		handlers.remove(handler);
		comboBox.removeItem(handler);
	}
	
	private static boolean attemptClose(ServerSocket listener) {
		try {
			if(JOptionPane.showConfirmDialog(frame,
					"Are you sure you want to close the connection?",
					"Connection Closing",
					JOptionPane.YES_NO_CANCEL_OPTION,
					JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION)
				return false;
			listener.close();
			closed = true;
			closeButton.setEnabled(false);
			kickButton.setEnabled(false);
			listButton.setEnabled(false);
			castButton.setEnabled(false);
			
			comboBox.setEnabled(false);
			// This client is going down!  Remove its name and its print
			// writer from the sets, and close its socket.
			synchronized(handlers) {
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
			comboBox.removeAllItems();
			comboBox.setSelectedIndex(-1);
		}
		catch(IOException e) {
			log("listener didn't close" + e);
			return false;
		}
		catch(Exception e) {
			log(e.toString());
			return false;
		}
		return true;
	}
	
	public static void log(String string) {
		text.append(string + "\n");
	}
	
	public static void log() {
		text.append("\n");
	}
	
	public static void main(String[] args) throws Exception {
		UIManager.setLookAndFeel(javax.swing.UIManager
				.getSystemLookAndFeelClassName());
		//get roomName
		if((roomName = JOptionPane.showInputDialog(null,
				"What is the name of the chatroom?", "Name",
				JOptionPane.QUESTION_MESSAGE)) == null)
			System.exit(0);
		roomName = "Chatroom: " + roomName;
		//initialize socket
		listener = new ServerSocket(PORT);
		//initialize gui
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					new ChatServer();
				}
				catch(Exception e) {
					e.printStackTrace();
				}
			}
		});
		URL myIP = new URL("http://www.trackip.net/ip");
		String ipAddress = null;
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(
					myIP.openStream()));
			ipAddress = in.readLine();
			in.close();
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		if(InetAddress.getLocalHost().getHostAddress().trim()
				.equals("127.0.0.1"))
			log(roomName + " is running internally at:\n"
					+ InetAddress.getLocalHost().getHostAddress().trim());
		else if(ipAddress != null)
			log(roomName + " is running locally at:\n"
					+ InetAddress.getLocalHost().getHostAddress().trim()
					+ "\nand on the internet at:\n" + ipAddress
					+ "\n\nNote: To recieve connections from the internet"
					+ " IP, you must forward Port 9898 to redirect"
					+ " connections from the internet here.\n");
		else
			log(roomName + " is running locally at\n"
					+ InetAddress.getLocalHost().getHostAddress());
		frame.setVisible(true);
		try {
			while(true) {
				temp = null;
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
}