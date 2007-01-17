package VASSAL.chat;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Vector;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

/**
 * Manages {@link PrivateChatter} instances
 */
public class PrivateChatManager {
  private ChatServerConnection client;

  private Vector chatters;
  private Vector banned;

  public PrivateChatManager(ChatServerConnection client) {
    chatters = new Vector();
    banned = new Vector();
    this.client = client;
  }

  public PrivateChatter getChatterFor(final Player sender) {
    if (banned.contains(sender)) {
      return null;
    }
    PrivateChatter chat = null;
    int index = chatters.indexOf(new Entry(sender, null));
    if (index >= 0) {
      chat = ((Entry) chatters.elementAt(index)).chatter;
    }
    if (chat == null) {
      chat = new PrivateChatter(sender, client);
      chatters.addElement(new Entry(sender, chat));
      JFrame f = new JFrame();
      f.addWindowListener(new WindowAdapter() {
        public void windowClosing(WindowEvent e) {
          promptToBan(sender);
        }
      });
      f.setTitle(sender.getName() + " private channel");
      f.getContentPane().add(chat);
      f.pack();
      f.setLocation(java.awt.Toolkit.getDefaultToolkit().getScreenSize().width / 2 - f.getSize().width / 2, 0);
    }
    return chat;
  }

  private void promptToBan(Player p) {
    if (JOptionPane.YES_OPTION ==
      JOptionPane.showConfirmDialog
      (null,
       "Ignore all messages from " + p.getName() + " for this session?",
       null,
       JOptionPane.YES_NO_OPTION)) {
      banned.addElement(p);
    }
  }

  private static class Entry {
    private Player player;
    private PrivateChatter chatter;

    private Entry(Player p, PrivateChatter chat) {
      if (p == null) {
        throw new NullPointerException();
      }
      player = p;
      chatter = chat;
    }

    public boolean equals(Object o) {
      if (o instanceof Entry) {
        return player.equals(((Entry) o).player);
      }
      else {
        return false;
      }
    }
  }
}