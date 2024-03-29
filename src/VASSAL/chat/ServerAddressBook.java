/*
 * $Id$
 *
 * Copyright (c) 2009-2012 by Brent Easton
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */
package VASSAL.chat;

import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import javax.swing.AbstractAction;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import net.miginfocom.swing.MigLayout;
import VASSAL.build.GameModule;
import VASSAL.chat.jabber.JabberClient;
import VASSAL.chat.jabber.JabberClientFactory;
import VASSAL.chat.node.NodeClientFactory;
import VASSAL.chat.peer2peer.P2PClientFactory;
import VASSAL.configure.StringConfigurer;
import VASSAL.i18n.Resources;
import VASSAL.preferences.Prefs;
import VASSAL.tools.PropertiesEncoder;
import VASSAL.tools.SequenceEncoder;
import VASSAL.tools.icon.IconFactory;
import VASSAL.tools.icon.IconFamily;
import VASSAL.tools.swing.Dialogs;

public class ServerAddressBook {
  public static final String CURRENT_SERVER = "currentServer"; //$NON-NLS-1$
  protected static final String ADDRESS_PREF = "ServerAddressBook"; //$NON-NLS-1$
  protected static final String LEGACY_TYPE = NodeClientFactory.NODE_TYPE;
  protected static final String DYNAMIC_TYPE = DynamicClientFactory.DYNAMIC_TYPE;
  protected static final String JABBER_TYPE = JabberClientFactory.JABBER_SERVER_TYPE;
  protected static final String P2P_TYPE = P2PClientFactory.P2P_TYPE;
  protected static final String P2P_MODE_KEY = P2PClientFactory.P2P_MODE_KEY;
  protected static final String P2P_SERVER_MODE = P2PClientFactory.P2P_SERVER_MODE;
  protected static final String P2P_CLIENT_MODE = P2PClientFactory.P2P_CLIENT_MODE;
  protected static final String TYPE_KEY = ChatServerFactory.TYPE_KEY;
  protected static final String DESCRIPTION_KEY = "description"; //$NON-NLS-1$
  protected final int LEAF_ICON_SIZE = IconFamily.SMALL;
  protected final int CONTROLS_ICON_SIZE = IconFamily.XSMALL;

  private boolean frozen;
  private JComponent controls;
  private StringConfigurer addressConfig;
  private JList myList;
  private DefaultListModel addressBook;
  private AddressBookEntry currentEntry;
  private boolean enabled = true;
  private PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);
  private static ServerAddressBook instance;
  private static String localIPAddress;
  private static String externalIPAddress;

  private JButton addButton;
  private JButton removeButton;
  private JButton editButton;
  private JButton setButton;

  public static ServerAddressBook getInstance() {
    return instance;
  }

  public static void editCurrentServer(boolean connected) {
    instance.editCurrent(connected);
  }

  public static void changeServerPopup(JComponent source) {
    instance.showPopup(source);
  }

  public static String getLocalAddress() {
    if (localIPAddress == null) {
      try {
        localIPAddress = InetAddress.getLocalHost().getHostAddress();
      }
      catch (UnknownHostException e) {
        localIPAddress = "?"; //$NON-NLS-1$
      }
    }
    return localIPAddress;
  }

  public static String getExternalAddress() {
    return getExternalAddress("?"); //$NON-NLS-1$
  }

  public static String getExternalAddress(String dflt) {
    if (externalIPAddress == null) {
      externalIPAddress = dflt;
      try {
        externalIPAddress = discoverMyIpAddressFromRemote();
      }
      catch (IOException e) {
        externalIPAddress = "?"; //$NON-NLS-1$
      }
    }
    return externalIPAddress;
  }

  private static String discoverMyIpAddressFromRemote() throws IOException {
    String theIp = null;
    HttpRequestWrapper r = new HttpRequestWrapper("http://www.vassalengine.org/util/getMyAddress"); //$NON-NLS-1$
    List<String> l = r.doGet(null);
    if (!l.isEmpty()) {
      theIp = l.get(0);
    }
    else {
      throw new IOException(Resources.getString("Server.empty_response")); //$NON-NLS-1$
    }
    return theIp;
  }

  public ServerAddressBook() {
    instance = this;
  }

  public JComponent getControls() {
    if (controls == null) {

      controls = new JPanel(new MigLayout());
      addressConfig = new StringConfigurer(ADDRESS_PREF, null, ""); //$NON-NLS-1$
      Prefs.getGlobalPrefs().addOption(null, addressConfig);
      addressBook = new DefaultListModel();
      loadAddressBook();
      myList = new JList(addressBook);
      myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      myList.setCellRenderer(new MyRenderer());
      myList.addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent evt) {
          updateButtonVisibility();
        }
      });
      myList.addMouseListener(new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
            if (editButton.isEnabled() && e.getClickCount() == 2) {
                int index = myList.locationToIndex(e.getPoint());
                editServer(index);
             }
        }
      });


      final JScrollPane scroll = new JScrollPane(myList);
      myList.repaint();
      controls.add(scroll, "grow, push, w 500, h 400, wrap, span 4"); //$NON-NLS-1$

      setButton = new JButton(Resources.getString("ServerAddressBook.set_current")); //$NON-NLS-1$
      setButton.setToolTipText(Resources.getString("ServerAddressBook.set_selected_server")); //$NON-NLS-1$
      setButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          setCurrentServer(myList.getSelectedIndex());
        }
      });

      addButton = new JButton(Resources.getString(Resources.ADD));
      addButton.setToolTipText(Resources.getString("ServerAddressBook.add_jabber_server")); //$NON-NLS-1$
      addButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          addServer();
        }
      });

      removeButton = new JButton(Resources.getString(Resources.REMOVE));
      removeButton.setToolTipText(Resources.getString("ServerAddressBook.remove_selected_server")); //$NON-NLS-1$
      removeButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          removeServer(myList.getSelectedIndex());
        }
      });

      editButton = new JButton(Resources.getString(Resources.EDIT));
      editButton.setToolTipText(Resources.getString("ServerAddressBook.edit_server")); //$NON-NLS-1$
      editButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          editServer(myList.getSelectedIndex());
        }
      });

      controls.add(addButton, "grow, push"); //$NON-NLS-1$
      controls.add(editButton, "grow, push"); //$NON-NLS-1$
      controls.add(removeButton, "grow, push"); //$NON-NLS-1$
      controls.add(setButton, "grow, push"); //$NON-NLS-1$

      updateButtonVisibility();
    }
    return controls;
  }

  public void setEnabled(boolean b) {
    enabled = b;
    updateButtonVisibility();
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setFrozen(boolean b) {
    frozen = b;
  }

  private void updateButtonVisibility() {
    final int index = myList.getSelectedIndex();
    if (index >= 0) {
      final AddressBookEntry e = (AddressBookEntry) addressBook.get(index);
      editButton.setEnabled(e.isEditable() && (isEnabled() || !e.isCurrent()));
      removeButton.setEnabled(e.isRemovable() && !e.isCurrent());
      setButton.setEnabled(isEnabled() && !e.isCurrent());
    }
    else {
      editButton.setEnabled(false);
      removeButton.setEnabled(false);
      setButton.setEnabled(false);
    }
  }

  public void setCurrentServer(Properties p) {

    // Check for Basic Types, regardless of other properties
    int index = 0;
    final String type = p.getProperty(TYPE_KEY);
    final String dtype = p.getProperty(DYNAMIC_TYPE);
    final String ctype = p.getProperty(P2P_MODE_KEY);
    for (Enumeration<?> e = addressBook.elements(); e.hasMoreElements();) {
      final AddressBookEntry entry = (AddressBookEntry) e.nextElement();
      final Properties ep = entry.getProperties();

      if (ep.equals(p)) {
        setCurrentServer(index);
        return;
      }

      else if (DYNAMIC_TYPE.equals(type) && DYNAMIC_TYPE.equals(ep.getProperty(TYPE_KEY))
          && ep.getProperty(DYNAMIC_TYPE).equals(dtype)) {
        setCurrentServer(index);
        return;
      }
      else if (P2P_TYPE.equals(type) && P2P_TYPE.equals(ep.getProperty(TYPE_KEY))
          && ep.getProperty(P2P_MODE_KEY).equals(ctype)) {
        setCurrentServer(index);
      }

      index++;
    }

    // Some Server we don't know about, add a server entry
    final AddressBookEntry newEntry = buildEntry(p);
    if (newEntry != null) {
      addressBook.addElement(newEntry);
      setCurrentServer(addressBook.indexOf(newEntry));
    }
    saveAddressBook();

  }

  private void setCurrentServer(AddressBookEntry e) {
    setCurrentServer(addressBook.indexOf(e));
  }

  private void setCurrentServer(int index) {
    final AddressBookEntry e = (AddressBookEntry) addressBook.get(index);
    if (currentEntry != null) {
      currentEntry.setCurrent(false);
    }
    final Properties oldProps = currentEntry == null ? null : currentEntry.getProperties();
    currentEntry = e;
    currentEntry.setCurrent(true);
    if (!frozen) {
      changeSupport.firePropertyChange(CURRENT_SERVER, oldProps, e.getProperties());
    }
    updateButtonVisibility();
    myList.repaint();
  }


  public void showPopup(JComponent source) {
    final JPopupMenu popup = new JPopupMenu();

    for (Enumeration<?> e = addressBook.elements(); e.hasMoreElements();) {
      final AddressBookEntry entry = (AddressBookEntry) e.nextElement();
      final JMenuItem item = new JMenuItem(entry.toString());
      final AbstractAction action = new MenuAction(entry);
      item.setAction(action);
      item.setIcon(entry.getIcon(IconFamily.SMALL));
      popup.add(item);
    }
    popup.show(source, 0, 0);
  }

  private class MenuAction extends AbstractAction {
    private static final long serialVersionUID = 1L;
    private AddressBookEntry entry;

    public MenuAction (AddressBookEntry e) {
      super(e.toString());
      entry = e;
    }

    public void actionPerformed(ActionEvent e) {
      ServerAddressBook.getInstance().setCurrentServer(entry);
    }

  }
  public void addPropertyChangeListener(PropertyChangeListener l) {
    changeSupport.addPropertyChangeListener(l);
  }

  public void removePropertyChangeListener(PropertyChangeListener l) {
    changeSupport.removePropertyChangeListener(l);
  }

  public Icon getCurrentIcon() {
    return currentEntry.getIcon(CONTROLS_ICON_SIZE);
  }

  public String getCurrentDescription() {
    return currentEntry.toString();
  }

  private void editCurrent(boolean connected) {
    if (currentEntry != null) {
      editServer(addressBook.indexOf(currentEntry), connected);
    }
  }

  private void editServer(int index) {
    editServer(index, true);
  }

  private void editServer(int index, boolean enabled) {
    final AddressBookEntry e = (AddressBookEntry) addressBook.get(index);
    final boolean current = e.equals(currentEntry);
    final Properties oldProps = e.getProperties();
    if (e.edit(enabled) && current) {
      changeSupport.firePropertyChange(CURRENT_SERVER, oldProps, e.getProperties());
    }
  }

  private void removeServer(int index) {
    final AddressBookEntry e = (AddressBookEntry) addressBook.get(index);
    int i = JOptionPane.showConfirmDialog(GameModule.getGameModule().getFrame(), Resources
        .getString("ServerAddressBook.remove_server", e.getDescription())); //$NON-NLS-1$
    if (i == 0) {
      addressBook.remove(index);
      myList.setSelectedIndex(-1);
      myList.repaint();
      updateButtonVisibility();
      saveAddressBook();
    }
  }

  private void addServer() {
    final AddressBookEntry e = new JabberEntry();
    if (e.edit()) {
      addressBook.addElement(e);
      saveAddressBook();
    }
  }

  /**
   * Set up the default server
   * @return
   */
  public Properties getDefaultServerProperties() {
    // return (new VassalJabberEntry()).getProperties();
    return (new LegacyEntry()).getProperties();
  }

  private void loadAddressBook() {
    decodeAddressBook(addressConfig.getValueString());
    // Ensure that the Address Book has the basic
    // servers in it.
    boolean legacy = false;
    boolean jabber = false;
    boolean peerServer = false;
    boolean peerClient = false;
    boolean updated = false;

    for (Enumeration<?> e = addressBook.elements(); e.hasMoreElements();) {
      final AddressBookEntry entry = (AddressBookEntry) e.nextElement();
      if (entry instanceof LegacyEntry) {
        legacy = true;
      }
      else if (entry instanceof VassalJabberEntry) {
        jabber = true;
      }
      else if (entry instanceof PeerServerEntry) {
        peerServer = true;
      }
      else if (entry instanceof PeerClientEntry) {
        peerClient = true;
      }
    }
    if (!jabber) {
      final AddressBookEntry entry = new VassalJabberEntry();
      entry.setCurrent(true);
      currentEntry = entry;
      addressBook.addElement(entry);
      updated = true;
    }
    if (!legacy) {
      addressBook.addElement(new LegacyEntry());
      updated = true;
    }
    if (!peerServer) {
      addressBook.addElement(new PeerServerEntry());
      updated = true;
    }
    if (!peerClient) {
      addressBook.addElement(new PeerClientEntry());
      updated = true;
    }
    if (updated) {
      saveAddressBook();
    }

  }

  private void saveAddressBook() {
    addressConfig.setValue(encodeAddressBook());
    if (myList != null) {
      myList.repaint();
    }
  }

  private String encodeAddressBook() {
    SequenceEncoder se = new SequenceEncoder(',');
    for (Enumeration<?> e = addressBook.elements(); e.hasMoreElements();) {
      final AddressBookEntry entry = (AddressBookEntry) e.nextElement();
      if (entry != null) {
        se.append(entry.encode());
      }
    }
    return se.getValue();
  }

  private void decodeAddressBook(String s) {
    addressBook.clear();
    for (SequenceEncoder.Decoder sd = new SequenceEncoder.Decoder(s, ','); sd.hasMoreTokens();) {
      final String token = sd.nextToken(""); //$NON-NLS-1$
      if (token.length() > 0) {
        final AddressBookEntry entry = buildEntry(token);
        if (entry != null) {
          addressBook.addElement(buildEntry(token));
        }
      }
    }
  }

  /**
   * Return an appropriately typed Entry, depending on the Server Properties
   * passed
   *
   * @param s
   *          Encoded Server Properties
   * @return Entry
   */
  private AddressBookEntry buildEntry(String s) {
    Properties newProperties = new Properties();
    try {
      newProperties = new PropertiesEncoder(s).getProperties();
    }
    catch (IOException e) {
      // FIXME: Error Message?
    }
    return buildEntry(newProperties);
  }

  private AddressBookEntry buildEntry(Properties newProperties) {
    final String type = newProperties.getProperty(TYPE_KEY);
    if (JABBER_TYPE.equals(type)) {
      return new JabberEntry(newProperties);
    }
    else if (DYNAMIC_TYPE.equals(type)) {
      final String dtype = newProperties.getProperty(DYNAMIC_TYPE);
      if (JABBER_TYPE.equals(dtype)) {
        return new VassalJabberEntry(newProperties);
      }
      else if (LEGACY_TYPE.equals(dtype)) {
        return new LegacyEntry(newProperties);
      }
    }
    else if (P2P_TYPE.equals(type)) {
      final String ctype = newProperties.getProperty(P2P_MODE_KEY);
      if (P2P_SERVER_MODE.equals(ctype)) {
        return new PeerServerEntry(newProperties);
      }
      else if (P2P_CLIENT_MODE.equals(ctype)) {
        return new PeerClientEntry(newProperties);
      }
    }
    return null;
  }

  /**
   * Base class for an Address Book Entry
   *
   */
  private abstract class AddressBookEntry implements Comparable<AddressBookEntry> {
    protected Properties properties = new Properties();
    protected boolean current;

    protected AddressBookEntry() {
      this(new Properties());
    }

    protected AddressBookEntry(Properties props) {
      properties = props;
    }

    protected String getDescription() {
      return getProperty(DESCRIPTION_KEY);
    }

    protected void setDescription(String desc) {
      properties.setProperty(DESCRIPTION_KEY, desc);
    }

    public String getProperty(String key) {
      return properties.getProperty(key);
    }

    public void setProperty(String key, String value) {
      properties.setProperty(key, value);
    }

    protected boolean isRemovable() {
      return true;
    }

    protected boolean isEditable() {
      return true;
    }

    protected abstract String getIconName();

    protected Icon getIcon(int size) {
      return IconFactory.getIcon(getIconName(), size);
    }

    public String getType() {
      return properties.getProperty(TYPE_KEY);
    }

    public void setType(String t) {
      properties.setProperty(TYPE_KEY, t);
    }

    public Properties getProperties() {
      return properties;
    }

    public void setProperties(Properties p) {
      properties = p;
    }

    public String encode() {
      return new PropertiesEncoder(properties).getStringValue();
    }

    public int compareTo(AddressBookEntry target) {
      if (getType().equals(target.getType())) {
        return getDescription().compareTo(target.getDescription());
      }
      return getType().compareTo(target.getType());
    }

    public boolean isCurrent() {
      return current;
    }

    public void setCurrent(boolean b) {
      current = b;
    }

    protected boolean isDescriptionEditable() {
      return true;
    }

    public boolean edit() {
      return edit(true);
    }

    public boolean edit(boolean enabled) {
      if (isEditable()) {
        final ServerConfig config = getEditor(getProperties(), enabled);
        final Integer result = (Integer) Dialogs.showDialog(null,
            Resources.getString("ServerAddressBook.edit_server_configuration"), //$NON-NLS-1$
            config.getControls(), JOptionPane.PLAIN_MESSAGE, null, JOptionPane.OK_CANCEL_OPTION,
            null, null, null, null);
        if (result != null && result.intValue() == 0) {
          if (enabled) {
            setProperties(config.getProperties());
            saveAddressBook();
          }
          return true;
        }
      }
      return false;
    }

    protected abstract void setAdditionalProperties(Properties props);

    protected abstract void getAdditionalProperties(Properties props);

    protected abstract void addAdditionalControls(JComponent c, boolean enabled);

    public ServerConfig getEditor(Properties p, boolean enabled) {
      return new ServerConfig(p, this, enabled);
    }

    class ServerConfig {
      protected JComponent configControls;
      protected JTextField description = new JTextField();
      protected AddressBookEntry entry;
      boolean enabled;

      public ServerConfig() {
      }

      public ServerConfig(Properties props, AddressBookEntry entry, boolean enabled) {
        this();
        this.entry = entry;
        this.enabled = enabled;
        description.setText(props.getProperty(DESCRIPTION_KEY));
        setAdditionalProperties(props);
      }

      protected boolean isEnabled() {
        return enabled;
      }

      public JComponent getControls() {
        if (configControls == null) {
          configControls = new JPanel();
          configControls.setLayout(new MigLayout("", "[align right]rel[]", "")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
          configControls.add(
              new JLabel(IconFactory.getIcon(entry.getIconName(), IconFamily.LARGE)),
              "span 2, align center, wrap"); //$NON-NLS-1$
          configControls.add(new JLabel(Resources.getString("Editor.description_label"))); //$NON-NLS-1$
          configControls.add(description, "wrap, grow, push"); //$NON-NLS-1$
          entry.addAdditionalControls(configControls, enabled);
          description.setEditable(isDescriptionEditable() && isEnabled());
        }
        return configControls;
      }

      public Properties getProperties() {
        final Properties props = new Properties();
        props.setProperty(DESCRIPTION_KEY, description.getText());
        getAdditionalProperties(props);
        return props;
      }
    }
  }

  /**
   * Address Book entry for a user defined Jabber Server
   *
   */
  private class JabberEntry extends AddressBookEntry {

    private JTextField jabberHost = new JTextField();
    private JTextField jabberPort = new JTextField();
    private JTextField jabberUser = new JTextField();
    private JTextField jabberPw = new JTextField();
    private JButton testButton;

    public JabberEntry() {
      this(new Properties());
      setType(JABBER_TYPE);
      setDescription(""); //$NON-NLS-1$
      setProperty(JabberClientFactory.JABBER_PORT, "5222"); //$NON-NLS-1$
    }

    public JabberEntry(Properties props) {
      super(props);
    }

    public String toString() {
      return Resources.getString("ServerAddressBook.jabber_server") + " " + getDescription() + " [" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
          + getProperty(JabberClientFactory.JABBER_HOST) + ":" //$NON-NLS-1$
          + getProperty(JabberClientFactory.JABBER_PORT) + " " //$NON-NLS-1$
          + getProperty(JabberClientFactory.JABBER_LOGIN) + "/" //$NON-NLS-1$
          + getProperty(JabberClientFactory.JABBER_PWD) + "]"; //$NON-NLS-1$
    }

    protected String getIconName() {
      return "jabber"; //$NON-NLS-1$
    }

    protected boolean isDescriptionEditable() {
      return true;
    }

    protected void setAdditionalProperties(Properties props) {
      jabberHost.setText(props.getProperty(JabberClientFactory.JABBER_HOST));
      jabberPort.setText(props.getProperty(JabberClientFactory.JABBER_PORT));
      jabberUser.setText(props.getProperty(JabberClientFactory.JABBER_LOGIN));
      jabberPw.setText(props.getProperty(JabberClientFactory.JABBER_PWD));
    }

    protected void getAdditionalProperties(Properties props) {
      props.setProperty(JabberClientFactory.JABBER_HOST, jabberHost.getText());
      props.setProperty(JabberClientFactory.JABBER_PORT, jabberPort.getText());
      props.setProperty(JabberClientFactory.JABBER_LOGIN, jabberUser.getText());
      props.setProperty(JabberClientFactory.JABBER_PWD, jabberPw.getText());
      props.setProperty(TYPE_KEY, JabberClientFactory.JABBER_SERVER_TYPE);
    }

    protected void addAdditionalControls(JComponent c, boolean enabled) {
      jabberHost.setEditable(enabled);
      jabberPort.setEditable(enabled);
      jabberUser.setEditable(enabled);
      jabberPw.setEditable(enabled);
      c.add(new JLabel(Resources.getString("ServerAddressBook.jabber_host"))); //$NON-NLS-1$
      c.add(jabberHost, "wrap, grow, push"); //$NON-NLS-1$
      c.add(new JLabel(Resources.getString("ServerAddressBook.port"))); //$NON-NLS-1$
      c.add(jabberPort, "wrap, grow, push"); //$NON-NLS-1$
      c.add(new JLabel(Resources.getString("ServerAddressBook.user_name"))); //$NON-NLS-1$
      c.add(jabberUser, "wrap, grow, push"); //$NON-NLS-1$
      c.add(new JLabel(Resources.getString("ServerAddressBook.password"))); //$NON-NLS-1$
      c.add(jabberPw, "wrap, grow, push"); //$NON-NLS-1$

      testButton = new JButton(Resources.getString("ServerAddressBook.test_connection")); //$NON-NLS-1$
      testButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          test();
        }
      });
      c.add(testButton, "span 2, align center, wrap"); //$NON-NLS-1$
    }

    protected void test() {
      final JTextArea result = new JTextArea(10, 30);
      result.setText(JabberClient.testConnection(jabberHost.getText(), jabberPort.getText(),
          jabberUser.getText(), jabberPw.getText()));
      try {
      Dialogs.showDialog(null,
          Resources.getString("ServerAddressBook.connection_test"), //$NON-NLS-1$
          result, JOptionPane.INFORMATION_MESSAGE, null, JOptionPane.OK_CANCEL_OPTION, null, null,
          null, null);
      }
      catch (IllegalStateException ex) {
        ex.printStackTrace();
      }
    }
  }

  /**
   * Address Book entry for the VASSAL Jabber server
   *
   */
  private class VassalJabberEntry extends AddressBookEntry {

    protected JTextField jabberUser = new JTextField();
    protected JTextField jabberPw = new JTextField();

    public VassalJabberEntry() {
      this(new Properties());
      setDescription("VASSAL" + Resources.getString("ServerAddressBook.jabber_server")); //$NON-NLS-1$ //$NON-NLS-2$
      setType(DYNAMIC_TYPE);
      setProperty(DYNAMIC_TYPE, JabberClientFactory.JABBER_SERVER_TYPE);
      setProperty(JabberClientFactory.JABBER_LOGIN, ""); //$NON-NLS-1$
      setProperty(JabberClientFactory.JABBER_PWD, ""); //$NON-NLS-1$
      setProperty(DynamicClientFactory.URL, DynamicClient.JABBER_URL);
    }

    public VassalJabberEntry(Properties props) {
      super(props);
    }

    public String toString() {
      String details;
      final String login = getProperty(JabberClientFactory.JABBER_LOGIN);
      final String pw = getProperty(JabberClientFactory.JABBER_PWD);
      if (login == null || login.length() == 0 || pw == null || pw.length() == 0) {
        details = Resources.getString("ServerAddressBook.login_details_required"); //$NON-NLS-1$
      }
      else {
        details = getProperty(JabberClientFactory.JABBER_LOGIN) + "/" //$NON-NLS-1$
            + getProperty(JabberClientFactory.JABBER_PWD);
      }
      return getDescription() + " [" + details + "]"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    protected boolean isRemovable() {
      return false;
    }

    protected boolean isDescriptionEditable() {
      return false;
    }

    protected String getIconName() {
      return "VASSAL-jabber"; //$NON-NLS-1$
    }

    protected void setAdditionalProperties(Properties props) {
      jabberUser.setText(props.getProperty(JabberClientFactory.JABBER_LOGIN));
      jabberPw.setText(props.getProperty(JabberClientFactory.JABBER_PWD));
      setType(DYNAMIC_TYPE);
      setProperty(DYNAMIC_TYPE, JABBER_TYPE);
    }

    protected void getAdditionalProperties(Properties props) {
      props.setProperty(JabberClientFactory.JABBER_LOGIN, jabberUser.getText());
      props.setProperty(JabberClientFactory.JABBER_PWD, jabberPw.getText());
      props.setProperty(TYPE_KEY, DYNAMIC_TYPE);
      props.setProperty(DYNAMIC_TYPE, JABBER_TYPE);
    }

    protected void addAdditionalControls(JComponent c, boolean enabled) {
      jabberUser.setEditable(enabled);
      jabberPw.setEditable(enabled);
      c.add(new JLabel(Resources.getString("ServerAddressBook.user_name"))); //$NON-NLS-1$
      c.add(jabberUser, "wrap, grow, push"); //$NON-NLS-1$
      c.add(new JLabel(Resources.getString("ServerAddressBook.password"))); //$NON-NLS-1$
      c.add(jabberPw, "wrap, grow, push"); //$NON-NLS-1$
    }

  }

  /**
   * Address Book entry for the VASSAL legacy server
   *
   */
  private class LegacyEntry extends AddressBookEntry {

    public LegacyEntry() {
      this(new Properties());
      setDescription(Resources.getString("ServerAddressBook.legacy_server")); //$NON-NLS-1$
      setType(DYNAMIC_TYPE);
      setProperty(DynamicClientFactory.DYNAMIC_TYPE, NodeClientFactory.NODE_TYPE);
      setProperty(DynamicClientFactory.URL, DynamicClient.LEGACY_URL);
    }

    public LegacyEntry(Properties props) {
      super(props);
    }

    public String toString() {
      return getDescription();
    }

    protected String getIconName() {
      return "VASSAL"; //$NON-NLS-1$
    }

    protected boolean isRemovable() {
      return false;
    }

    protected boolean isEditable() {
      return false;
    }

    protected boolean isDescriptionEditable() {
      return false;
    }

    protected void addAdditionalControls(JComponent c, boolean enabled) {

    }

    protected void getAdditionalProperties(Properties props) {

    }

    protected void setAdditionalProperties(Properties props) {

    }

  }

  /**
   * Address Book Entry for a Peer to Peer connection in Server Mode
   *
   */
  private class PeerServerEntry extends AddressBookEntry {

    public PeerServerEntry() {
      super();
      setDescription(Resources.getString("ServerAddressBook.peer_server")); //$NON-NLS-1$
      setType(P2P_TYPE);
      setProperty(P2P_MODE_KEY, P2P_SERVER_MODE);
    }

    public PeerServerEntry(Properties props) {
      super(props);
    }

    public String toString() {
      return getDescription();
    }

    public boolean isRemovable() {
      return false;
    }

    protected boolean isDescriptionEditable() {
      return false;
    }

    protected String getIconName() {
      return "network-server"; //$NON-NLS-1$
    }

    protected void setAdditionalProperties(Properties p) {
      setType(P2P_TYPE);
      setProperty(P2P_MODE_KEY, P2P_SERVER_MODE);
    }

    protected void getAdditionalProperties(Properties props) {
      props.setProperty(TYPE_KEY, P2P_TYPE);
      props.setProperty(P2P_MODE_KEY, P2P_SERVER_MODE);
    }

    protected void addAdditionalControls(JComponent c, boolean enabled) {

    }

  }

  /**
   * Address Book Entry for a Peer to Peer connection in Client Mode
   *
   */
  private class PeerClientEntry extends AddressBookEntry {

    private JTextField listenPort = new JTextField();

    public PeerClientEntry() {
      super();
      setDescription(Resources.getString("ServerAddressBook.peer_client")); //$NON-NLS-1$
      setType(P2P_TYPE);
      setProperty(P2P_MODE_KEY, P2P_CLIENT_MODE);
      setProperty(P2PClientFactory.P2P_LISTEN_PORT, "5050"); //$NON-NLS-1$
    }

    public PeerClientEntry(Properties props) {
      super(props);
    }

    public String toString() {
      return getDescription();
    }

    public boolean isRemovable() {
      return false;
    }

    protected boolean isDescriptionEditable() {
      return false;
    }

    protected String getIconName() {
      return "network-idle"; //$NON-NLS-1$
    }

    protected void setAdditionalProperties(Properties p) {
      setType(P2P_TYPE);
      setProperty(P2P_MODE_KEY, P2P_CLIENT_MODE);
      listenPort.setText(p.getProperty(P2PClientFactory.P2P_LISTEN_PORT));
    }

    protected void getAdditionalProperties(Properties props) {
      props.setProperty(TYPE_KEY, P2P_TYPE);
      props.setProperty(P2P_MODE_KEY, P2P_CLIENT_MODE);
      props.setProperty(P2PClientFactory.P2P_LISTEN_PORT, listenPort.getText());
    }

    protected void addAdditionalControls(JComponent c, boolean enabled) {
      listenPort.setEditable(enabled);
      c.add(new JLabel(Resources.getString("ServerAddressBook.listen_port"))); //$NON-NLS-1$
      c.add(listenPort, "wrap, growx, push"); //$NON-NLS-1$

      c.add(new JLabel(Resources.getString("Peer2Peer.internet_address"))); //$NON-NLS-1$
      final JTextField externalIP = new JTextField(getExternalAddress());
      externalIP.setEditable(false);
      c.add(externalIP, "wrap, growx, push"); //$NON-NLS-1$

      if (!getLocalAddress().equals(getExternalAddress())) {
        c.add(new JLabel(Resources.getString("Peer2Peer.local_address"))); //$NON-NLS-1$
        final JTextField localIP = new JTextField(getLocalAddress());
        localIP.setEditable(false);
        c.add(localIP, "wrap, growx, push"); //$NON-NLS-1$
      }
    }

  }

  /**
   * Customised List Cell Renderer for the JList display: - Display the Icon
   * appropriate to the Server Entry - Highlight the currently selected server
   *
   */
  private class MyRenderer extends DefaultListCellRenderer {
    private static final long serialVersionUID = 1L;
    private Font standardFont;
    private Font highlightFont;

    public Component getListCellRendererComponent(JList list, Object value, int index,
        boolean isSelected, boolean cellHasFocus) {

      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

      if (standardFont == null) {
        standardFont = getFont();
        highlightFont = new Font(standardFont.getFamily(), Font.BOLD + Font.ITALIC, standardFont
            .getSize());
      }

      if (value instanceof AddressBookEntry) {
        final AddressBookEntry e = (AddressBookEntry) value;
        setIcon(e.getIcon(LEAF_ICON_SIZE));
        if (e.isCurrent()) {
          setFont(highlightFont);
          setText(e.toString() + Resources.getString("ServerAddressBook.current")); //$NON-NLS-1$
        }
        else {
          setFont(standardFont);
        }

      }
      return this;
    }
  }
}