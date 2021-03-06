/**
 ** com/charliemouse/cambozola/Viewer.java
 **  Copyright (C) Andy Wilcock, 2001.
 **  Available from http://www.charliemouse.com
 **
 **  Cambozola is free software; you can redistribute it and/or modify
 **  it under the terms of the GNU General Public License as published by
 **  the Free Software Foundation; either version 2 of the License, or
 **  (at your option) any later version.
 **
 **  Cambozola is distributed in the hope that it will be useful,
 **  but WITHOUT ANY WARRANTY; without even the implied warranty of
 **  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 **  GNU General Public License for more details.
 **
 **  You should have received a copy of the GNU General Public License
 **  along with Cambozola; if not, write to the Free Software
 **  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 **/
package com.charliemouse.cambozola;

import com.charliemouse.cambozola.profiles.ICameraProfile;
import com.charliemouse.cambozola.profiles.Profile_LocalPTZ;
import com.charliemouse.cambozola.shared.AppID;
import com.charliemouse.cambozola.shared.CamStream;
import com.charliemouse.cambozola.shared.ExceptionReporter;
import com.charliemouse.cambozola.shared.ImageChangeEvent;
import com.charliemouse.cambozola.shared.ImageChangeListener;
import com.charliemouse.cambozola.watermark.Watermark;
import com.charliemouse.cambozola.watermark.WatermarkCollection;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;

public class Viewer extends java.applet.Applet implements MouseListener, MouseMotionListener, KeyListener, ImageChangeListener, ExceptionReporter, ViewerAttributeInterface
{
    private static final int DEFAULT_WIDTH = 352;
    private static final int DEFAULT_HEIGHT = 300;

    private static final String PAR_FAILUREIMAGE = "failureimage";
    private static final String PAR_DELAY = "delay";
    private static final String PAR_RETRIES = "retries";
    private static final String PAR_URL = "url";
    private static final String PAR_ACCESSORIES = "accessories";
    private static final String PAR_WATERMARK = "watermark";
    private static final String PAR_ACCESSORYSTYLE = "accessorystyle";
    private static final String PAR_DEBUG = "debug";
    private static final String PAR_SHOW_COPYRIGHT = "showCopyright";
    private static final String PAR_BACKGROUND = "backgroundColor";
    private static final String PAR_TEXTCOLOR = "textColor";
    private static final String PAR_USERAGENT = "userAgent";
    private static final String PAR_PROFILE = "profile";

    private static final int IMG_TYPE = BufferedImage.TYPE_INT_RGB;

    private static final int VAL_STYLE_INDENT = 0;
    private static final int VAL_STYLE_OVERLAY = 1;
    private static final int VAL_STYLE_ALWAYSON = 2;

    private static boolean ms_standalone = false;
    private Properties m_parameters = null;
    private URL m_documentBase = null;
    private URL m_codeBase = null;
    private URL m_mainURL = null;
    private Vector m_alternateURLs = null;
    private CamStream m_imgStream = null;
    private String m_msg = null;
    private AppID m_props = null;
    private boolean m_displayAccessories = false;
    private int m_accessoryStyle = VAL_STYLE_INDENT;
    private PercentArea m_area = new PercentArea();
    private Vector m_accessories = new Vector();
    private Image m_offscreenAccBar = null;
    private Image m_backingStore = null;
    private boolean m_readingStream = false;
    private int m_retryCount = 1;
    private int m_retryDelay = 1000;
    private Image m_failureImage = null;
    private boolean m_loadFailure = false;
    private Watermark m_wmHit = null;
    private WatermarkCollection m_wmCollection = null;
    private boolean m_debug = false;
    private int m_imgWidth = 0;
    private int m_imgHeight = 0;
    private boolean m_showCopyright = true;
    private Color m_backgroundColor = Color.white;
    private Color m_textColor = Color.black;
    private String m_userAgent = null;
    private ICameraProfile m_profile = null;

    public Viewer()
    {
        m_props = AppID.getAppID();
        m_alternateURLs = new Vector();
        m_parameters = new Properties();

        m_parameters.put(PAR_ACCESSORYSTYLE, "indent");

        m_wmCollection = new WatermarkCollection();
    }

    public void init()
    {
        if (!ms_standalone) {
            m_documentBase = getDocumentBase();
            m_codeBase = getCodeBase();
        }
        //
        // Init!
        //
        m_props = AppID.getAppID();
        m_alternateURLs = new Vector();
        //
        String wmarks = getParameterValue(PAR_WATERMARK);
        if (wmarks != null) {
            m_wmCollection.populate(wmarks, m_documentBase);
        }
        String mm = getParameterValue(PAR_SHOW_COPYRIGHT);
        if ("false".equalsIgnoreCase(mm)) {
            m_showCopyright = false;
        }
        //
        // Set up the initial message.
        //
        String appMsg = m_props.getAppNameVersion() + " " + m_props.getCopyright();
        if (m_showCopyright) {
            //
            // Possibly set the message.
            //
            setMessage(appMsg);
        }
        System.err.println("// " + appMsg);
        System.err.println("// Build date: " + m_props.getBuildDate());
        System.err.println("// Available from " + m_props.getLocURL());
        //
        // Load the failure Image.
        //
        String fistr = getParameterValue(PAR_FAILUREIMAGE);
        if (fistr != null && !fistr.equals("")) {
            try {
                URL fiurl = new URL(m_documentBase, fistr);
                setFailureImageURL(fiurl);
            } catch (MalformedURLException mfe) {
                System.err.println("Unable to access URL for failure image -" + fistr);
            }
        }
        //
        String delay = getParameterValue(PAR_DELAY);
        if (delay != null && !delay.equals("")) {
            try {
                int di = Integer.parseInt(delay);
                setRetryDelay(di);
            } catch (Exception e) {
                System.err.println("Unable to set retry delay");
            }
        }
        String debug = getParameterValue(PAR_DEBUG);
        m_debug = (debug != null && debug.equalsIgnoreCase("true"));

        String retries = getParameterValue(PAR_RETRIES);
        if (retries != null && !retries.equals("")) {
            try {
                int ri = Integer.parseInt(retries);
                setRetryCount(ri);
            } catch (Exception e) {
                System.err.println("Unable to set retry count");
            }
        }
        //
        String appurl = getParameterValue(PAR_URL);
        if (appurl == null && !appurl.equals("")) {
            throw new IllegalArgumentException("Missing URL");
        }
        m_mainURL = null;
        //
        // Break apart the URLs - separator is |
        //
        StringTokenizer st = new StringTokenizer(appurl, "|");
        while (st.hasMoreTokens()) {
            try {
                URL alt = new URL(m_codeBase, st.nextToken());
                m_alternateURLs.addElement(alt);
                if (m_mainURL == null) {
                    m_mainURL = alt;
                }
            } catch (MalformedURLException mfe) {
                reportError(mfe);
            }
        }
        //
        // Set up the identifying User-Agent
        //
        String userAgent = getParameterValue(PAR_USERAGENT);
        if (userAgent != null && !userAgent.equals("") && !userAgent.equalsIgnoreCase("default")) {
            m_userAgent = userAgent;
        } else {
            m_userAgent = m_props.getAppNameVersion() + "/Java " + System.getProperty("java.version") + " " + System.getProperty("java.vendor");
        }
        //
        // Pick up the camera profile (or default to all local if not)
        //
        String profile = getParameterValue(PAR_PROFILE);
        if (profile != null && !profile.equals("")) {
            Class profClazz;
            try {
                profClazz = Class.forName("com.charliemouse.cambozola.profiles.Profile_" + profile);
                Constructor constr = profClazz.getConstructor(new Class[]{ViewerAttributeInterface.class});
                m_profile = (ICameraProfile) constr.newInstance(new Object[]{this});
            } catch (Exception e) {
                System.err.println("Failed to set camera profile - " + profile);
                e.printStackTrace();
            }
        }
        if (m_profile == null) {
            //
            // Fallback.
            //
            m_profile = new Profile_LocalPTZ(this);
        }
        System.err.println("// Using Camera profile: " + m_profile.getDescription());
        //
        setCurrentURL(m_mainURL);
        setAlternateURLs(m_alternateURLs);
        Color bg = parseColor(getParameterValue(PAR_BACKGROUND));
        if (bg != null) {
            setBackgroundColor(bg);
        }
        Color textCol = parseColor(getParameterValue(PAR_TEXTCOLOR));
        if (textCol != null) {
            setTextColor(textCol);
        }
        //
        configureAccessories(getParameterValue(PAR_ACCESSORIES));
        //
        setBackground(Color.white);
        addMouseListener(this);
        addMouseMotionListener(this);
        addKeyListener(this);
    }

    public void destroy()
    {
        stop();
    }

    public static void main(String args[])
    {
        ms_standalone = true;
        Frame f = new Frame(AppID.getAppID().getAppName());
        f.addWindowListener(new WindowAdapter()
        {
            public void windowClosing(WindowEvent we)
            {
                System.exit(0);
            }
        });

        f.setLayout(new BorderLayout());
        Viewer cv = new Viewer();
        //
        int width = DEFAULT_WIDTH;
        int height = DEFAULT_HEIGHT;
        //
        StringBuffer concatURL = new StringBuffer();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("-")) {
                int eqidx = arg.indexOf("=") + 1;
                if (arg.equals("-noaccessories")) {
                    cv.m_parameters.put(PAR_ACCESSORIES, "none");
                } else if (arg.startsWith("-accessories=")) {
                    cv.m_parameters.put(PAR_ACCESSORIES, arg.substring(eqidx));
                } else if (arg.startsWith("-retries=")) {
                    cv.m_parameters.put(PAR_RETRIES, arg.substring(eqidx));
                } else if (arg.startsWith("-delay=")) {
                    cv.m_parameters.put(PAR_DELAY, arg.substring(eqidx));
                } else if (arg.startsWith("-failureimage=")) {
                    cv.m_parameters.put(PAR_FAILUREIMAGE, arg.substring(eqidx));
                } else if (arg.startsWith("-watermark=") || arg.startsWith("-watermarks=")) {
                    cv.m_parameters.put(PAR_WATERMARK, arg.substring(eqidx));
                } else if (arg.startsWith("-accessorystyle=")) {
                    cv.m_parameters.put(PAR_ACCESSORYSTYLE, arg.substring(eqidx));
                } else if (arg.startsWith("-width=")) {
                    width = Integer.parseInt(arg.substring(eqidx));
                } else if (arg.startsWith("-height=")) {
                    height = Integer.parseInt(arg.substring(eqidx));
                } else if (arg.startsWith("-debug")) {
                    cv.m_parameters.put(PAR_DEBUG, "true");
                } else if (arg.startsWith("-showCopyright=")) {
                    cv.m_parameters.put(PAR_SHOW_COPYRIGHT, arg.substring(eqidx));
                } else if (arg.startsWith("-backgroundColor=")) {
                    cv.m_parameters.put(PAR_BACKGROUND, arg.substring(eqidx));
                } else if (arg.startsWith("-textColor=")) {
                    cv.m_parameters.put(PAR_TEXTCOLOR, arg.substring(eqidx));
                } else if (arg.startsWith("-userAgent=")) {
                    cv.m_parameters.put(PAR_USERAGENT, arg.substring(eqidx));
                } else if (arg.startsWith("-profile=")) {
                    cv.m_parameters.put(PAR_PROFILE, arg.substring(eqidx));
                } else {
                    usage();
                    System.exit(0);
                }
            } else {
                if (concatURL.length() != 0) {
                    concatURL.append("|");
                }
                concatURL.append(arg.trim());
            }
        }
        f.setSize(width, height);
        //
        // No Stream -> error...
        //
        if (concatURL.length() == 0) {
            usage();
            System.exit(0);
        }
        cv.m_parameters.put(PAR_URL, concatURL.toString());
        f.add(BorderLayout.CENTER, cv);
        cv.init();
        //
        f.setVisible(true);
        cv.start();
    }

    private String getHTMLParameterValue(String key)
    {
        String s = getParameter(key);
        if (s == null) {
            return "";
        }
        return s;
    }

    public String getParameterValue(String key)
    {
        if (!ms_standalone) {
            return getHTMLParameterValue(key);
        }
        return m_parameters.getProperty(key, null);
    }

    void setFailureImageURL(URL fistr)
    {
        try {
            m_failureImage = createImage((ImageProducer) fistr.getContent());
            m_failureImage.getWidth(this);
        } catch (IOException ie) {
            System.err.println("Unable to access failure image contents - " + ie);
        }
    }

    void setRetryCount(int rc)
    {
        if (rc < 1) {
            return;
        }
        m_retryCount = rc;
    }

    void setRetryDelay(int delay)
    {
        if (delay < 0) {
            return;
        }
        m_retryDelay = delay;
    }

    private void configureAccessories(String acclist)
    {
        //
        // Set up the accessory style.
        //
        String as = getParameterValue(PAR_ACCESSORYSTYLE);
        if (as != null) {
            if (as.equalsIgnoreCase("indent")) {
                m_accessoryStyle = VAL_STYLE_INDENT;
            } else if (as.equalsIgnoreCase("overlay")) {
                m_accessoryStyle = VAL_STYLE_OVERLAY;
            } else if (as.equalsIgnoreCase("always")) {
                m_accessoryStyle = VAL_STYLE_ALWAYSON;
            }
        }
        //
        // Set up the accessories (the things on the LHS).
        //
        if (acclist == null || acclist.equals("") || acclist.equalsIgnoreCase("default")) {
            //
            // Default list.
            //
            acclist = "Home,ZoomOut,ZoomIn,Pan,ChangeStream,Info,WWWHelp";
        } else if (acclist.equalsIgnoreCase("none")) {
            //
            // Explicitly none...
            //
            acclist = "";
        }
        StringTokenizer st = new StringTokenizer(acclist, ", ");
        while (st.hasMoreTokens()) {
            String tok = st.nextToken();
            try {
                Class accClazz = Class.forName("com.charliemouse.cambozola.accessories." + tok + "Accessory");
                Accessory acc = (Accessory) accClazz.newInstance();
                //
                // MS JVM in IE is really picky about this code, and used to crash if
                // it used a 'continue'...
                //
                if (acc.isEnabled(m_profile, this)) {
                    acc.getIconImage();
                    m_accessories.addElement(acc);
                }
            } catch (Exception exc) {
                System.err.println("Unable to load accessory - " + tok);
                exc.printStackTrace();
            }
        }
    }

    public synchronized void reportError(Throwable t)
    {
        reportNote(t.getMessage());
        m_loadFailure = true;
        stop();
    }

    public synchronized void reportFailure(String s)
    {
        m_loadFailure = true;
        reportNote(s);
    }

    public synchronized void reportNote(String s)
    {
        System.err.println(s);
        setMessage(s);
        m_readingStream = false;
        repaint();
    }

    private synchronized void setMessage(String s)
    {
        m_msg = s;
    }

    public void start()
    {
    }


    public void stop()
    {
        if (m_imgStream != null) {
            m_imgStream.unhook();
            m_imgStream = null;
        }
        m_readingStream = false;
        for (Enumeration e = m_accessories.elements(); e.hasMoreElements();) {
            ((Accessory) e.nextElement()).terminate();
        }
    }

    public void setCurrentURL(URL loc)
    {
        m_loadFailure = false;
        //
        m_mainURL = loc;
        if (m_imgStream != null) {
            m_msg = "";
            m_imgStream.removeImageChangeListener(this);
            m_imgStream.unhook();
        }
        m_imgStream = new CamStream(m_mainURL, m_userAgent, m_documentBase, m_retryCount, m_retryDelay, this, m_debug);
        m_imgStream.addImageChangeListener(this);
        m_imgStream.start();
    }

    public void displayURL(URL url, String target)
    {
        if (ms_standalone) return;

        if (target == null) {
            getAppletContext().showDocument(url);
        } else {
            getAppletContext().showDocument(url, target);
        }
    }

    public Vector getAlternateURLs()
    {
        return m_alternateURLs;
    }

    public void setAlternateURLs(Vector v)
    {
        m_alternateURLs = v;
    }

    public void imageChanged(ImageChangeEvent ce)
    {
        update(getGraphics());
        getToolkit().sync();
    }


    public void paint(Graphics g)
    {
        update(g);
    }


    public void update(Graphics g)
    {
        if (g == null) return;
        Dimension d = getSize();
        if (m_backingStore == null || m_backingStore.getWidth(this) != d.width || m_backingStore.getHeight(this) != d.height) {
            m_backingStore = new BufferedImage(d.width, d.height, IMG_TYPE);
            //
            // Size has changed, recalculate the hit areas
            //
            m_wmCollection.recalculateLocations(d);
        }
        Graphics gg2 = m_backingStore.getGraphics();
        if (m_loadFailure && m_failureImage != null) {
            //
            // Draw the failure image.
            //
            paintFrame(gg2, m_failureImage, d, null);
        } else if (!m_readingStream) {
            gg2.setPaintMode();
            gg2.setColor(m_backgroundColor);
            if (isDisplayingAccessories() && m_accessoryStyle == VAL_STYLE_INDENT) {
                gg2.fillRect(Accessory.BUTTON_SIZE, 0, d.width, d.height);
            } else {
                gg2.fillRect(0, 0, d.width, d.height);
            }
            //
            FontMetrics fm = gg2.getFontMetrics();
            if (m_msg != null) {
                int width = fm.stringWidth(m_msg);
                gg2.setColor(m_textColor);
                gg2.drawString(m_msg, (d.width - width) / 2, d.height / 2);
                gg2.setColor(m_backgroundColor);
            }
            //
            // Draw the accessories...
            //
            paintAccessories(gg2);
        }
        if (m_imgStream != null) {
            Image img = m_imgStream.getCurrent();
            if (img != null) {
                m_loadFailure = false;
                m_readingStream = true;
                paintFrame(gg2, img, d, m_wmCollection);
            }
        }
        g.drawImage(m_backingStore, 0, 0, null);
        gg2.dispose();
    }


    public void paintFrame(Graphics g, Image img, Dimension d, WatermarkCollection wmc)
    {
        //
        // Draw the main image...
        //
        int indent = 0;
        if (isDisplayingAccessories() && m_accessoryStyle == VAL_STYLE_INDENT) {
            indent = Accessory.BUTTON_SIZE;
        }
        m_imgWidth = img.getWidth(this);
        m_imgHeight = img.getHeight(this);
        if (m_imgWidth == -1 || m_imgHeight == -1) return; // No size for the image, no zoom.
        //
        //
        // Work out the area to zoom into.
        //
        Rectangle imgarea = m_area.getArea(m_imgWidth, m_imgHeight);

        g.drawImage(img, indent, 0, d.width, d.height, imgarea.x, imgarea.y, imgarea.x + imgarea.width, imgarea.y + imgarea.height, this);
        //
        // Draw the watermark
        //
        if (wmc != null) {
            wmc.paint(g);
        }
        //
        // Draw the accessories...
        //
        paintAccessories(g);
    }

    /**
     * @param g The graphics to paint to
     * @noinspection PointlessBooleanExpression
     */
    private void paintAccessories(Graphics g)
    {
        Dimension d = getSize();
        int asize = m_accessories.size();
        if (isDisplayingAccessories() && Accessory.BUTTON_SIZE > 0 && asize > 0) {
            //
            // First time - build up, store in image, and reuse...
            //
            if (m_offscreenAccBar == null) {
                m_offscreenAccBar = createImage(Accessory.BUTTON_SIZE, m_accessories.size() * Accessory.BUTTON_SIZE);
                Graphics accessoryBar = m_offscreenAccBar.getGraphics();
                //
                int idx = 0;
                for (Enumeration accEnum = m_accessories.elements(); accEnum.hasMoreElements();) {
                    accessoryBar.setColor(Color.lightGray);
                    Accessory acc = (Accessory) accEnum.nextElement();
                    int yoffset = idx * Accessory.BUTTON_SIZE;
                    accessoryBar.fill3DRect(0, yoffset, Accessory.BUTTON_SIZE, Accessory.BUTTON_SIZE, true);
                    accessoryBar.drawImage(acc.getIconImage(), Accessory.ICON_INDENT, yoffset + Accessory.ICON_INDENT,
                            new ImageObserver()
                            {
                                public boolean imageUpdate(Image img, int infoflags,
                                                           int x, int y, int width, int height)
                                {
                                    return true;
                                }
                            });
                    idx++;
                }
                accessoryBar.dispose();
            }
            //
            g.drawImage(m_offscreenAccBar, 0, 0, null);
            //
            // Draw the white box only if we are indenting...
            //
            if (m_accessoryStyle == VAL_STYLE_INDENT) {
                int fluff = (m_accessories.size() * Accessory.BUTTON_SIZE);
                g.setColor(m_backgroundColor);
                g.fillRect(0, fluff, Accessory.BUTTON_SIZE, d.height);
            }
        }
    }


    public void keyPressed(KeyEvent ke)
    {
        if (!m_readingStream) {
            return;
        }
        if (ke.getKeyCode() == KeyEvent.VK_HOME) {
            m_profile.homeView();
        } else if (ke.getKeyCode() == KeyEvent.VK_PAGE_UP && m_profile.supportsZoom()) {
            m_profile.zoomTele();
        } else if (ke.getKeyCode() == KeyEvent.VK_PAGE_DOWN && m_profile.supportsZoom()) {
            m_profile.zoomWide();
        } else if (ke.getKeyCode() == KeyEvent.VK_LEFT && m_profile.supportsPan()) {
            m_profile.panLeft();
        } else if (ke.getKeyCode() == KeyEvent.VK_RIGHT && m_profile.supportsPan()) {
            m_profile.panRight();
        } else if (ke.getKeyCode() == KeyEvent.VK_UP && m_profile.supportsTilt()) {
            m_profile.tiltUp();
        } else if (ke.getKeyCode() == KeyEvent.VK_DOWN && m_profile.supportsTilt()) {
            m_profile.tiltDown();
        }
    }

    public void keyTyped(KeyEvent e)
    {
    }

    public void keyReleased(KeyEvent e)
    {
    }

    public void mouseEntered(MouseEvent me)
    {
    }


    public void mouseExited(MouseEvent me)
    {
        if (isDisplayingAccessories()) {
            setDisplayingAccessories(false);
            repaint();
        }
    }

    public boolean isDisplayingAccessories()
    {
        return m_displayAccessories || m_accessoryStyle == VAL_STYLE_ALWAYSON;
    }

    public void setDisplayingAccessories(boolean b)
    {
        m_displayAccessories = b;
    }

    public void mouseClicked(final MouseEvent me)
    {
        if (!ms_standalone && !isDisplayingAccessories() && m_wmHit != null) {
            //
            // Go to the url.
            //
            displayURL(m_wmHit.getURL(), null);
            return;
        }
        if (me.getX() >= Accessory.BUTTON_SIZE) {
            // Mouse Click [Calculate [point on image]
            if (m_imgWidth > 0 && m_imgHeight > 0) {
                double px = ((double) me.getX() / (double) getWidth());
                double py = ((double) me.getY() / (double) getHeight());
                // % of view
                Rectangle imgarea = m_area.getArea(m_imgWidth, m_imgHeight);

                final int imgx = (int) (imgarea.getX() + (imgarea.getWidth() * px));
                final int imgy = (int) (imgarea.getY() + (imgarea.getHeight() * py));

                Runnable tr = new Runnable()
                {
                    public void run()
                    {
                        getProfile().mouseClicked(m_imgWidth, m_imgHeight, imgx, imgy, (me.getClickCount() > 1));
                    }
                };
                new Thread(tr).start();
            }
            return;
        }
        int idx = (me.getY() / Accessory.BUTTON_SIZE);
        if (idx >= m_accessories.size()) {
            // System.err.println("Out of range for accessories");
        } else {
            //
            // Get the local location...
            //
            Point p = new Point(me.getX(), me.getY() - (idx * Accessory.BUTTON_SIZE));
            ((Accessory) (m_accessories.elementAt(idx))).actionPerformed(p, this);
        }
    }


    public void mousePressed(MouseEvent me)
    {
    }


    public void mouseReleased(MouseEvent me)
    {
    }

    public void mouseDragged(MouseEvent me)
    {
    }

    public void mouseMoved(MouseEvent me)
    {
        boolean needRepaint = false;
        Point p = me.getPoint();
        //
        boolean previously = isDisplayingAccessories();
        //
        if (p.x < Accessory.BUTTON_SIZE) {
            if (m_accessories.size() > 0) {
                setDisplayingAccessories(true);
                int idx = (me.getY() / Accessory.BUTTON_SIZE);
                //
                String statusFeedback = "";
                if (idx < m_accessories.size()) {
                    String desc = ((Accessory) (m_accessories.elementAt(idx))).getDescription();
                    if (desc != null) {
                        statusFeedback = desc;
                    }
                }
                showStatus(statusFeedback);
            }
        } else {
            setDisplayingAccessories(false);
        }
        //
        // Only clickable in a web page.
        //
        //noinspection PointlessBooleanExpression
        if (m_displayAccessories == false && !ms_standalone) {
            //
            // Are we over a Clickable hit point?
            //
            Watermark pwnew = m_wmCollection.isOverClickableWatermark(p);
            if (pwnew != m_wmHit) {
                m_wmHit = pwnew;
                needRepaint = true;
                setCursor((m_wmHit != null) ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            }
        }
        if (isDisplayingAccessories() != previously) {
            needRepaint = true;
        }
        if (needRepaint) {
            repaint();
        }
    }

    public void showStatus(String s)
    {
        if (!ms_standalone) super.showStatus(s);
    }

    public PercentArea getViewArea()
    {
        return m_area;
    }

    public ICameraProfile getProfile()
    {
        return m_profile;
    }

    public CamStream getStream()
    {
        return m_imgStream;
    }

    public boolean isStandalone()
    {
        return ms_standalone;
    }

    public Vector getAccessories()
    {
        return m_accessories;
    }

    /**
     * Read a color definition of the format '#RRGGBB' where RR, GG  and BB
     * are 3 hex values.
     *
     * @param s The string to check
     * @return null if there is an issue, or the color if not.
     */
    private static Color parseColor(String s)
    {
        if (s == null || !(s.startsWith("#") || s.length() == 7)) {
            return null;
        }
        int r = Integer.parseInt(s.substring(1, 3), 16);
        int g = Integer.parseInt(s.substring(3, 5), 16);
        int b = Integer.parseInt(s.substring(5, 7), 16);
        //
        return new Color(r, g, b);
    }

    public void setBackgroundColor(Color col)
    {
        m_backgroundColor = col;
    }

    public void setTextColor(Color col)
    {
        m_textColor = col;
    }

    public static void usage()
    {
        System.err.println("Usage: WebCamURL [otherURLs] [-accessories=comma separated accessory list]");
        System.err.println("Current set of accessories are:");
        System.err.println(" o ZoomIn       - Zooms in to the image");
        System.err.println(" o ZoomOut      - Zooms out of the image");
        System.err.println(" o Home         - Shows all the image");
        System.err.println(" o Pan          - Pan around a zoomed-in image");
        System.err.println(" o ChangeStream - Swap to a different stream (if > 1 listed)");
        System.err.println(" o Info         - Displays information about the stream");
        System.err.println(" o WWWHelp      - Displays a web page showing help");
        System.err.println("");
        System.err.println(" -debug                      Write debug information");
        System.err.println(" -width={width}              Sets the width of the application");
        System.err.println(" -height={height}            Sets the height of the application");
        System.err.println(" -noaccessories              Will not display any accessories");
        System.err.println(" -accessories=none           Will not display any accessories");
        System.err.println(" -accessories=default        Will display the default set of accessories");
        System.err.println(" -accessorystyle={see below} Defines how the accessories will appear on top-left");
        System.err.println("   indent                      Will squeeze the image [default]");
        System.err.println("   overlay                     Will overlay the accessories onto the image");
        System.err.println("   always                      Always display the accessories (overlaid)");
        System.err.println(" -retries={num}              The number of retries (default = 1)");
        System.err.println(" -delay={num}                The number of milliseconds between retries");
        System.err.println(" -failureimage={url}         Image to display if failure to connect");
        System.err.println(" -backgroundColor=#RRGGBB    Background Color in hex - e.g. #FF0000 for red");
        System.err.println(" -textColor=#RRGGBB          Text Color in hex - e.g. #FFFFFF for white");
        System.err.println(" -userAgent={useragent}      Sets the user-agent string, which will be used in the HTTP-request (f.e. 'Mozilla/5.0'");
        System.err.println(" -profile={Camera Profile}   Choose profile for camera");
        System.err.println(" -watermark={see below}      List of watermarks, separated by '|'");
        System.err.println("   imageURL|corner|linkURL     Watermark information, separated by '|'");
    }
}
