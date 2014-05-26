import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.text.DefaultCaret;

public class ChatClient {
	private static final int PORT = 9898;
	private PrintWriter out;
	private static BufferedReader in;
	private JFrame frame = new JFrame("Chat");
	private JTextField dataField = new JTextField(40);
	private JTextArea messageArea = new JTextArea(8, 30);
	private Socket socket;
	private String identity;
	private static boolean closed;
	
	public ChatClient() {
		messageArea.setEditable(false);
		frame.add(dataField, BorderLayout.SOUTH);
		frame.add(new JScrollPane(messageArea), BorderLayout.CENTER);
		frame.setMinimumSize(new Dimension(384, 288));
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		messageArea.setLineWrap(true);
		messageArea.setWrapStyleWord(true);
		messageArea.setBackground(null);
		messageArea.setBorder(null);
		DefaultCaret caret = (DefaultCaret) messageArea.getCaret();
		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
		// Add Listeners
		dataField.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				out.println(dataField.getText());
				dataField.setText("");
			}
		});
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				try {
					if(!closed) {
						if(JOptionPane.showConfirmDialog(frame,
								"Are you sure you want to exit?", "Exit",
								JOptionPane.YES_NO_CANCEL_OPTION,
								JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION)
							return;
					}
					socket.close();
				}
				catch(IOException e1) {}
				System.exit(0);
			}
		});
		try {
			connectToServer();
			new Inbound(socket, messageArea, frame, identity).start();
		}
		catch(Exception e1) {
			JOptionPane.showMessageDialog(frame, "No connection." + e1,
					"Connection Issue", JOptionPane.ERROR_MESSAGE);
			System.exit(0);
		}
		frame.setVisible(true);
	}
	
	public void connectToServer() throws Exception {
		// Get the server address from a dialog box.
		String serverAddress;
		if((serverAddress = JOptionPane.showInputDialog(frame,
				"Enter IP Address of the Server:", "Connect",
				JOptionPane.QUESTION_MESSAGE)) == null)
			System.exit(0);
		// Make connection and initialize streams
		socket = new Socket(serverAddress, PORT);
		in = new BufferedReader(new InputStreamReader(
				socket.getInputStream(), "UTF-16"));
		out = new PrintWriter(new OutputStreamWriter(
				socket.getOutputStream(), "UTF-16"), true);
		identity = in.readLine();
		out.println(getIdentity());
	}
	
	private String getIdentity() {
		String identity;
		try {
			if((identity = JOptionPane.showInputDialog(null,
					"What is your name?", "Identity",
					JOptionPane.QUESTION_MESSAGE)) != null) {
				return identity;
			}
		}
		catch(HeadlessException e) {
			e.printStackTrace();
		}
		System.exit(0);
		return null;
	}
	
	private static class Inbound extends Thread {
		private Socket socket;
		private JTextArea textArea;
		private String roomName;
		private JFrame frame;
		
		public Inbound(Socket socket, JTextArea textArea, JFrame frame,
				String identity) {
			this.socket = socket;
			this.textArea = textArea;
			this.frame = frame;
			this.roomName = identity;
		}
		
		public void run() {
			try {
				frame.setTitle(roomName);
				in.readLine();
				while(true) {
					String input = in.readLine();
					if(input == null) {
						break;
					}
					textArea.append(input + "\n");
				}
			}
			catch(IOException e) {}
			finally {
				try {
					socket.close();
					textArea.append("Connection with server closed.");
					closed = true;
				}
				catch(IOException e) {}
			}
		}
	}
	
	public static void main(String[] args) throws Exception {
		UIManager.setLookAndFeel(javax.swing.UIManager
				.getSystemLookAndFeelClassName());
		new ChatClient();
	}
}
