package edu.uw.rgm.quote;

import java.awt.Container;
import java.awt.Event;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLConnection;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;


/**
 * Application for obtaining stock quotes from NASDAQ.
 *
 * @author Russ Moul
 */
public final class StockQuoter {
    /** Platform specific EOL */
    private static final String EOL = System.getProperty("line.separator");

    /** The Xerces parser class */
    private static final String DEFAULT_SAX_DRIVER =
                                "org.apache.xerces.parsers.SAXParser";
    /** The SAX driver property */
    private static final String DRIVER_PROPERTY = "org.xml.sax.driver";

    /** The quote service url */
    private static final String QUOTE_URL =
            "http://finance.yahoo.com/d/quotes.csv?f=sl1d1t1c1ohgv&e=.csv&s=";
            // "http://quotes.nasdaq.com/quote.dll?page=xml&mode=stock&symbol=";
    /** The list of pertinent stock symbols */
    private static final String STOCK_LIST_FILE = "config/stocklist.txt";

    /** Inset */
    private static final int INSET_SIZE = 4;

    /** Index of the stock symbol element. */
    private static final int SYMBOL_NDX = 0;
    /** Index of the most recent asking price element. */
    private static final int ASK_NDX = 1;
    /** Index of the stock symbol element. */
    private static final int DATE_NDX = 2;
    /** Index of the date element. */
    private static final int TIME_NDX = 3;
    /** Index of the price change element. */
    private static final int CHANGE_NDX = 4;
    /** Index of the open price element. */
    private static final int OPEN_NDX = 5;
    /** Index of the day high element. */
    private static final int DAYHIGH_NDX = 6;
    /** Index of the day low element. */
    private static final int DAYLOW_NDX = 7;
    /** Index of the volume element. */
    private static final int VOLUME_NDX = 8;

    /**
     * Constructor,
     */
    public StockQuoter() {
        constructLayout();
    }

    /**
     * Creates the window and lays out its components.
     */
    private void constructLayout() {
        Exception initException = null;

        final JFrame frame = new JFrame("Stock Quoter");
        frame.addWindowListener(new WindowAdapter() {
                public void windowClosing(final WindowEvent e) {
                    super.windowClosing(e);
                    e.getWindow().dispose();
                }
            });

        final JMenu fileMenu = new JMenu("File");

        final JMenuItem quitMenu = new JMenuItem("Quit", 'Q');
        final KeyStroke quitKey = KeyStroke.getKeyStroke('Q', Event.ALT_MASK);
        quitMenu.setAccelerator(quitKey);
        quitMenu.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    frame.dispose();
                }
            });

        fileMenu.add(quitMenu);

        final JMenuBar menuBar = new JMenuBar();
        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);

        final GridBagLayout gridbag = new GridBagLayout();
        final GridBagConstraints gbc = new GridBagConstraints();
        final Container content = frame.getContentPane();
        content.setLayout(gridbag);

        gbc.insets = new Insets(INSET_SIZE, INSET_SIZE, INSET_SIZE, INSET_SIZE);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.gridheight = 1;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        content.add(new JLabel("Stock Symbol:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.gridheight = 1;
        gbc.gridwidth = 1;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE;

        JComboBox<String> cbx;

        try {
            final String[] stocks = parseStockList();
            cbx = new JComboBox<String>(stocks);
        } catch (final IOException ex) {
            cbx = new JComboBox<String>();
            initException = ex;
        }

        final JComboBox<String> lst = cbx;
        lst.setEditable(true);
        content.add(lst, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.gridheight = 1;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.NONE;

        final JButton btn = new JButton("Get Quote");
        content.add(btn, gbc);

        btn.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    final String symbol = lst.getSelectedItem().toString();

                    try {
                        final double quote = getQuote(symbol);
                        final String msg = "Last sell price for " + symbol
                                   + " was " + quote;
                        JOptionPane.showMessageDialog(frame, msg,
                            "Price Quote", JOptionPane.INFORMATION_MESSAGE);
                    } catch (final Exception nex) {
                        JOptionPane.showMessageDialog(frame, nex.getMessage(),
                            "Exception", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });

        frame.pack();
        frame.setResizable(false);

        GraphicsEnvironment gEnv;
        gEnv = GraphicsEnvironment.getLocalGraphicsEnvironment();

        final Point center = gEnv.getCenterPoint();
        frame.setLocation(center.x - (frame.getWidth() / 2),
            center.y - (frame.getHeight() / 2));
        frame.setVisible(true);

        // deal with initialization exceptions
        if (initException != null) {
            postException(frame, initException);
        }
    }

    /**
     * Posts a dialog with exception stack trace.
     *
     * @param f the paen frame for the dialog
     * @param ex the exception
     */
    private void postException(final JFrame f, final Exception ex) {
        final StringWriter w = new StringWriter();
        ex.printStackTrace(new PrintWriter(w));
        JOptionPane.showMessageDialog(f, w.toString(), "Exception",
            JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Obtains the last sales price of the stock.
     *
     * @param symbol the symbol of the stock the quote is wanted for
     *
     * @return the last sale price for the stock
     *
     * @throws IOException if an an IO error occurs
     * @throws SAXException if the is a parse error or an invalid quote is received
     */
    public double getQuote(final String symbol) throws IOException, SAXException {
        final String xml = getQuoteDocument(symbol);

        final StringReader rdr = new StringReader(xml);

        final String driverClassName = System.getProperty(DRIVER_PROPERTY,
                                                          DEFAULT_SAX_DRIVER);
        final XMLReader parser = XMLReaderFactory.createXMLReader(driverClassName);
        final XmlQuoteHandler handler = new XmlQuoteHandler();
        parser.setContentHandler(handler);

        final InputSource source = new InputSource(rdr);
        parser.parse(source);

        final double price = handler.getPriceQuote();

        if (price < 0.0) {
            throw new SAXException(handler.getErrorMessage());
        }

        return price;
    }


    /**
     * Requests the stock quote from the server and provides the result in an
     * XML document.
     *
     * @param symbol the symbol of the stock the quote is wanted for
     *
     * @return the XML document containing the quote
     *
     * @throws IOException if the unable to read a valid quote
     */
    private String getQuoteDocument(final String symbol) throws IOException  {
        final URL url = new URL(QUOTE_URL + symbol);
        final URLConnection conn = url.openConnection();
        final InputStream in = conn.getInputStream();
        final InputStreamReader rdr = new InputStreamReader(in);
        final BufferedReader br = new BufferedReader(rdr);
        final String csv = br.readLine();
        br.close();

        if (null == csv) {
            throw new IOException("Received an empty quote.");
        }

        final String[] elements = csv.split(",");
        elements[SYMBOL_NDX] =
            elements[SYMBOL_NDX].substring(1,
                                           elements[SYMBOL_NDX].length() - 1);
        elements[DATE_NDX] =
            elements[DATE_NDX].substring(1, elements[DATE_NDX].length() - 1);
        elements[TIME_NDX] =
            elements[TIME_NDX].substring(1, elements[TIME_NDX].length() - 1);
        final StringBuffer xmlBuf = new StringBuffer("<stock_quote>" + EOL);
        xmlBuf.append(" <symbol>" + elements[SYMBOL_NDX] + "</symbol>" + EOL);
        xmlBuf.append(" <when>" + EOL);
        xmlBuf.append("   <date>" + elements[DATE_NDX] + "</date>" + EOL);
        xmlBuf.append("   <time>" + elements[TIME_NDX] + "</time>" + EOL);
        xmlBuf.append(" </when>" + EOL);
        xmlBuf.append(" <price type=\"ask\" value=\""
                      + elements[ASK_NDX] + "\"/>" + EOL);
        xmlBuf.append(" <price type=\"open\" value=\""
                      + elements[OPEN_NDX] + "\"/>" + EOL);
        xmlBuf.append(" <price type=\"dayhigh\" value=\""
                      + elements[DAYHIGH_NDX] + "\"/>" + EOL);
        xmlBuf.append(" <price type=\"daylow\" value=\""
                      + elements[DAYLOW_NDX] + "\"/>" + EOL);
        xmlBuf.append(" <change>" + elements[CHANGE_NDX] + "</change>" + EOL);
        xmlBuf.append(" <volume>" + elements[VOLUME_NDX] + "</volume>" + EOL);
        xmlBuf.append("</stock_quote>" + EOL);

        return xmlBuf.toString();
    }


    /**
     * Obtains the list of stock symbols from stock list resource.
     *
     * @return the list of stock symbols
     *
     * @throws IOException if an error occurs while processing the stock list
     *                     file
     */
    private String[] parseStockList() throws IOException {
        final ClassLoader loader = this.getClass().getClassLoader();
        final InputStream in = loader.getResourceAsStream(STOCK_LIST_FILE);

        if (in == null) {
            throw new IOException("Unable to locate resource: "
                                + STOCK_LIST_FILE);
        }

        final InputStreamReader rdr = new InputStreamReader(in);
        final BufferedReader br = new BufferedReader(rdr);
        String list = br.readLine();
        br.close();
        if (null == list) {
            list = "";
        }
        return list.split(":");
    }

    /**
     * Entry point.
     *
     * @param args (not used)
     * @throws InvocationTargetException if the construction of the UI throws an exception
     * @throws InterruptedException if the AWT thread is interrupted while creating the UI
     */
    public static void main(final String[] args) throws InterruptedException, InvocationTargetException {
        /** enables a proxy
        java.util.Properties sysProp = System.getProperties();
        sysProp.put( "proxySet", "true" );
        sysProp.put( "proxyHost", "www-my-proxy.com" );
        sysProp.put( "proxyPort", "31060" );
        */

        // Construct everything on the AWT thread
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                new StockQuoter();
            }
        });
    }

    /**
     * Parses the xml document obtained from NASDAQ to obtain the last sale
     * price or alternatively the error description.
     */
    private static final class XmlQuoteHandler extends DefaultHandler {
        /** Initial content buffer size */
        private static final int BUFFER_SIZE = 256;

        /** The error message - if there was an error */
        private String errorMsg = "No specified error message.";

        /** The stock price quote */
        private double priceQuote = -1;

        /** The content of the tag being parser */
        private StringBuffer contentBuf = new StringBuffer(BUFFER_SIZE);

        /**
         * Returns the parsed error message.
         *
         * @return the error message
         */
        public String getErrorMessage() {
            return errorMsg;
        }

        /**
         * Returns the parsed price.
         *
         * @return the price
         */
        public double getPriceQuote() {
            return priceQuote;
        }

        /**
         * Indicates the start of the tag, resets the content accumulation
         * buffer.
         *
         * @param uri (not used)
         * @param localName the tag/element name
         * @param qName (not used)
         * @param attributes (not used)
        */
        public void startElement(final String uri, final String localName,
                                 final String qName,
                                 final Attributes attributes) {
                                 //throws SAXException {
            if ("price".equals(localName)) {
                if ("ask".equals(attributes.getValue("type"))) {
                    final String priceStr = attributes.getValue("value");
                    priceQuote = Double.parseDouble(priceStr);

                }
            }
        }

        /**
         * Accumulate the contents of the tags.
         *
         * @param ch the caracters
         * @param start starting position
         * @param length number of characters
         */
        public void characters(final char[] ch, final int start,
                               final int length) {
            contentBuf.append(ch, start, length);
        }
    }

    /**
     * A distinct exception to indicate the symbol was not listed.
     */
    public static final class StockQuoteException extends Exception {
        /** Version id. */
        private static final long serialVersionUID = -4861504990765406058L;

        /**
         * Constructor.
         *
         * @param msg message
         */
        public StockQuoteException(final String msg) {
            super(msg);
        }
    }
}

