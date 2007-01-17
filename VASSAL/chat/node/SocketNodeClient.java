package VASSAL.chat.node;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import VASSAL.chat.CgiServerStatus;
import VASSAL.chat.WelcomeMessageServer;
import VASSAL.chat.messageboard.MessageBoard;
import VASSAL.command.CommandEncoder;

public class SocketNodeClient extends NodeClient implements SocketWatcher {
  private SocketHandler sender;
  protected NodeServerInfo serverInfo;

  public SocketNodeClient(String moduleName, String playerId, CommandEncoder encoder, NodeServerInfo serverInfo, MessageBoard msgSvr, WelcomeMessageServer welcomer) {
    super(moduleName, playerId, encoder, msgSvr, welcomer);
    this.serverInfo = serverInfo;
    serverStatus = new CgiServerStatus();
  }

  public SocketNodeClient(String moduleName, String playerId, CommandEncoder encoder, final String host, final int port, MessageBoard msgSvr, WelcomeMessageServer welcomer) {
    this(moduleName, playerId, encoder, new NodeServerInfo() {

      public String getHostName() {
        return host;
      }

      public int getPort() {
        return port;
      }

    }, msgSvr, welcomer);

  }

  public void send(String command) {
    sender.writeLine(command);
  }

  protected void initializeConnection() throws UnknownHostException, IOException {
    Socket s = new Socket(serverInfo.getHostName(), serverInfo.getPort());
    sender = new BufferedSocketHandler(s, this);
    sender.start();

  }

  protected void closeConnection() {
    SocketHandler s = sender;
    sender = null;
    s.close();
  }

  public boolean isConnected() {
    return sender != null;
  }

  public void socketClosed(SocketHandler handler) {
    if (sender != null) {
      propSupport.firePropertyChange(STATUS, null, "Lost connection to server");
      propSupport.firePropertyChange(CONNECTED, null, Boolean.FALSE);
      sender = null;
    }
  }

  public void handleMessage(String msg) {
    handleMessageFromServer(msg);
  }
}