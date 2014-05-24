import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;

public class Handler extends Thread {
	private String identity;
	private Socket socket;
	private BufferedReader in;
	private PrintWriter out;
	private int number;
	private boolean nameAccepted;
	private String roomName;
	
	public Handler(Socket socket, int number, String roomName) {
		this.roomName = roomName;
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
			out.println(ChatServer.roomName);
			String tempIdentity = null;
			while(true) {
				nameAccepted = true;
				out.println("What is your name?");
				tempIdentity = in.readLine();
				if(tempIdentity == null) {
					return;
				}
				if(tempIdentity.length() <= 18) {
					synchronized(ChatServer.handlers) {
						for(Handler handler : ChatServer.handlers) {
							if(handler.getIdentity() != null) {
								if(handler.getIdentity().equals(
										tempIdentity))
									nameAccepted = false;
							}
						}
						if(nameAccepted) {
							identity = tempIdentity;
							break;
						}
					}
					out.println("Name is already taken.");
				} else
					out.println("Name must be 18 characters or less.");
			}
			log("Client #" + number + " has identified as: \"" + identity
					+ "\"");
			// Now that a successful name has been chosen, add the
			// socket's print writer to the set of all writers so
			// this client can receive broadcast messages.
			out.println("Welcome to " + roomName + ", " + identity + "!");
			println(identity + " has entered the room.");
			// Accept messages from this client and broadcast them.
			// Ignore other clients that cannot be broadcasted to.
			while(true) {
				String input = in.readLine();
				if(input == null) {
					println(identity + " has disconnected.");
					return;
				}
				if(input.equals("!list")) {
					out.println("\nCurrently in the chat:");
					synchronized(ChatServer.handlers) {
						for(Handler handler : ChatServer.handlers) {
							out.println(handler.getIdentity());
						}
					}
					out.println();
				} else {
					println(getIdentity() + "> " + input);
				}
			}
		}
		catch(SocketException e) {}
		catch(IOException e) {
			log("Client #" + number + " (" + identity + ") "
					+ e.toString());
		}
		finally {
			if(!socket.isClosed())
				close();
		}
	}
	
	private static void log(String string) {
		ChatServer.log(string);
	}
	
	public void println(String string) {
		synchronized(ChatServer.handlers) {
			for(Handler handler : ChatServer.handlers) {
				handler.getWriter().println(string);
			}
		}
	}
	
	public PrintWriter getWriter() {
		return out;
	}
	
	public String getIdentity() {
		return identity;
	}
	
	public void close() {
		// This client is going down!  Remove its name and its print
		// writer from the sets, and close its socket.
		try {
			socket.close();
			log("Connection with client #" + number + " (" + identity
					+ ")" + " has closed.");
		}
		catch(IOException e) {
			log("Client #" + number + "'s (" + identity + "'s)"
					+ " socket didn't close." + e);
		}
	}
}
