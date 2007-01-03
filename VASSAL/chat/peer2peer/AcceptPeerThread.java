package VASSAL.chat.peer2peer;

import org.litesoft.p2pchat.PendingPeerManager;

import java.net.ServerSocket;
import java.io.IOException;

/**
 * Date: Mar 11, 2003
 */
public class AcceptPeerThread extends Thread {
  private boolean running = true;
  private ServerSocket socket;
  private PendingPeerManager ppm;
  private int port;
  private static final int MAX_ATTEMPTS = 10;

  public AcceptPeerThread(int initialPort, PendingPeerManager ppm) throws IOException {
    this.ppm = ppm;
    for (int i=0;i<MAX_ATTEMPTS;++i) {
      port = initialPort+i;
      try {
        socket = new ServerSocket(port);
        break;
      }
      catch (Exception ex) {
        if (i == MAX_ATTEMPTS -1) {
          throw new IOException(ex.getMessage());
        }
      }
    }
  }

  public int getPort() {
    return port;
  }

  public AcceptPeerThread(ServerSocket socket, PendingPeerManager ppm) {
    this.socket = socket;
    this.ppm = ppm;
  }

  public synchronized void start() {
    running = true;
    super.start();
  }

  public void run() {
    while (running) {
      try {
        ppm.addNewPeer(socket.accept());
      }
      catch (Exception ex) {
        ex.printStackTrace();
        halt();
      }
    }
  }

  public void halt() {
    interrupt();
    running = false;
    try {
      socket.close();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }
}
