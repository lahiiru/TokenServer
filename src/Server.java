/**
 * Created by Lahiru on 1/21/2018.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class Server {
    final BlockingQueue<ClientObj> QUEUE = new ArrayBlockingQueue<>(100);
    boolean tokenIsInTheServer = true;
    int tokenHavingClientNumber = 0;
    final int port = 9898;

    static final String CLIENT_REQUEST_WANT = "WANT";
    static final String CLIENT_REQUEST_RELEASE = "RELEASE";
    static final String CLIENT_RESPONSE_WAIT = "WAIT";
    static final String CLIENT_RESPONSE_GRANTED = "GRANTED";
    static final String CLIENT_RESPONSE_REQUESTED = "ALREADY_REQUESTED";
    static final String CLIENT_RESPONSE_INVALID = "INVALID_REQUEST";

    public static void main(String[] args) throws Exception {
        Server server = new Server();
        server.run();
    }

    void run() throws IOException {
        System.out.println("The token manager server is running on port " + port);
        int clientNumber = 0;
        ServerSocket listener = new ServerSocket(port);

        try {
            while (true) {
                new RequestHandler(listener.accept(), clientNumber++, this).start();
            }
        } finally {
            listener.close();
        }
    }
}

class RequestHandler extends Thread {
    private Socket socket;
    private int clientNumber;
    PrintWriter out;
    Server server;

    RequestHandler(Socket socket, int clientNumber, Server server) {
        this.socket = socket;
        this.clientNumber = clientNumber;
        this.server = server;

        log("New connection with client# " + clientNumber + " at " + socket);
    }

    public void run() {
        try {

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Send a welcome message to the client.
            out.println("Hello, you are client #" + clientNumber + ".");
            out.println("q to quit\n");

            while (true) {
                String input = in.readLine();
                if (input == null || input.equals("q")) {
                    break;
                } else if (input.contains(Server.CLIENT_REQUEST_WANT)){
                    if (!server.tokenIsInTheServer && server.tokenHavingClientNumber == clientNumber) {
                        out.println(Server.CLIENT_RESPONSE_REQUESTED);
                    } else if (server.QUEUE.isEmpty()){
                        if (!server.tokenIsInTheServer){
                            try {
                                server.QUEUE.put(new ClientObj(this, clientNumber));
                                out.println(Server.CLIENT_RESPONSE_WAIT);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        } else {
                            server.tokenIsInTheServer = false;
                            server.tokenHavingClientNumber = clientNumber;
                            out.println(Server.CLIENT_RESPONSE_GRANTED);
                        }
                    } else {
                        try {
                            server.QUEUE.put(new ClientObj(this, clientNumber));
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } else if (input.contains(Server.CLIENT_REQUEST_RELEASE)){
                    if (server.QUEUE.isEmpty()){
                        server.tokenIsInTheServer = true;
                        log("Token is now in the server.");
                    } else {
                        try {
                            ClientObj clientObj = server.QUEUE.take();
                            server.tokenHavingClientNumber = clientObj.getClientId();
                            clientObj.getRequestHandler().send(Server.CLIENT_RESPONSE_GRANTED);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    out.println(Server.CLIENT_RESPONSE_INVALID);
                }

            }
        } catch (IOException e) {
            log("Error handling client# " + clientNumber + ": " + e);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                log("Couldn't close a socket, what's going on?");
            }
            log("Connection with client# " + clientNumber + " closed");
        }
    }

    private boolean send(String msg){
        if (socket.isClosed()) return false;
        out.println(msg);
        return true;
    }

    private void log(String message) {
        System.out.println(message);
    }
}

class ClientObj {
    private RequestHandler requestHandler;
    private int clientId;

    ClientObj (RequestHandler requestHandler, int clientId){
        this.requestHandler = requestHandler;
        this.clientId = clientId;
    }

    RequestHandler getRequestHandler() {
        return requestHandler;
    }

    int getClientId() {
        return clientId;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj.getClass() != ClientObj.class) return false;
        ClientObj clientObj = (ClientObj)obj;
        return clientObj.getClientId() == getClientId();
    }

    @Override
    public int hashCode() {
        return getClientId();
    }
}