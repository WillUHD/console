// Copyright WillUHD 2025. 

import javax.imageio.ImageIO;
import javax.swing.Timer;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * The core of the console interface for Maradona.
 * Uses Swing, but is really heck fast. It re-writes the
 * text area viewport models built for performance and
 * view updates above anything else. No EDT starvation.
 * Also, you can input :)
 * <p>
 * Officially beats UNIX by 11X
 * (10M stress test, 4.7 seconds vs 52 seconds is no joke.)
 * <p>
 * Latency is on average ~0.05ms faster than the native
 * UNIX terminal. Due to hardware limitations, both are
 * about 3ms.
 * <p>
 *
 * @since a0.1
 *
 * Farewell, Maradona Console
 */
public final class Console {

    //TODO LIST
    // 1. Make this instantiatable: Console c = new Console("Maradona Console");
    // 2. Do word wrap, but not at the cost of performance
    // 3. Custom titles: Draw on title bar, or as an image

    // our custom written components that demolishes Java
    private static ConsoleStream stream;
    private static ConsoleView viewport;
    private static final ThreadLocal<StringBuilder> sb = ThreadLocal.withInitial(StringBuilder::new);
    public static final ExecutorService vt = Executors.newVirtualThreadPerTaskExecutor();
    
    // APIs: Optimizes Strings with sb, but you can really put any object inside
    public static void writeLine(Object obj) {stream.writeln(obj.toString());}
    public static void writeLine() {stream.writeln("");}
    public static void writeLine(String message) {stream.writeln(message);}

    public static void writeLine(String method, int num, String arg) {
        var builder = sb.get();
        builder.setLength(0); // do this every time just in case. 99% shouldn't happen because it's safe.
        stream.writeln(builder.append(method).append("[").append(num).append("]: ").append(arg).toString());
    }

    public static void success(String method, int num, String arg) {
        var builder = sb.get();
        builder.setLength(0);
        stream.writeln(builder.append("✅ ").append(method).append("[").append(num).append("]: ").append(arg).toString());
    }

    public static void success(Object obj) {
        var builder = sb.get(); builder.setLength(0);
        stream.writeln(builder.append("✅ ").append(obj).toString());
    }

    public static void error(Throwable t) {
        var builder = sb.get();
        builder.setLength(0);
        builder.append("❌ Error: ").append(t.getClass().getName()).append(": ").append(t.getMessage()).append("\n");
        for (StackTraceElement element : t.getStackTrace()) { builder.append("\tat ").append(element.toString()).append("\n");}
        stream.writeln(builder.toString().trim());
    }

    public static void error(Object obj) {
        var builder = sb.get();
        builder.setLength(0);
        stream.writeln(builder.append("❌ ").append(obj).toString());
    }

    public static void start() {
        var consoleFrame = makeFrame("Maradona Console", 600, 320);

        // make our viewport for the text area
        var model = new ConsoleDocument();
        viewport = new ConsoleView(model);
        var terminalPane = new JScrollPane(viewport);

        // initialize the pane components for UI
        terminalPane.setBorder(BorderFactory.createEmptyBorder());
        terminalPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS); // mainly a laf decision

        terminalPane.setWheelScrollingEnabled(false);
        terminalPane.getViewport().setBackground(new Color(56, 56, 56));

        // initialize the scrolling feature
        var verticalScrollBar = terminalPane.getVerticalScrollBar();
        verticalScrollBar.addAdjustmentListener(e -> viewport.repaint());
        terminalPane.addMouseWheelListener(new MouseAdapter() {
            public void mouseWheelMoved(MouseWheelEvent e) {
                e.consume(); // this is where the trackpad smooth scroll magic happens!!
                verticalScrollBar.setValue(verticalScrollBar.getValue() +
                        (int) (e.getPreciseWheelRotation() * 16)); // using 16 for font choices
            }
        });

        // assign the line numbers, 99% don't serve a purpose
        // but looks good + I try to make it take less cpu
        var lineNumberView = new LineNumberView(model, viewport);
        terminalPane.setRowHeaderView(lineNumberView);
        stream = new ConsoleStream(model, viewport, verticalScrollBar);

        // draw the hud
        var hud = new JPanel() {

            // use virtual threads for the image reading
            private BufferedImage title, dfVersion, visor;

            // needs the braces for some reason
            {
                vt.submit(() -> title = readImg("ConsoleTitle.png"));
                vt.submit(() -> visor = readImg("visor.png"));
            }

            // cut the HUD to the first 52 because lazy
            public Dimension getPreferredSize() {return new Dimension(super.getPreferredSize().width, 52);}

            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                var width = getWidth();
                var g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                g2d.setColor(new Color(40,40,40)); // make this the same color as the LNV for consistency
                g2d.fillRect(0, 0, width, 52);

                g2d.drawImage(visor, 0, 0, 60, 20, null); // macOS magic nums
//                g2d.drawImage(dfVersion, width - 60, 0, 50, 30, null);
                g2d.drawImage(title, (width - 360) / 2, -5, 360, 60, null); // centers the title
                g2d.dispose();
            }
        };

        hud.setBackground(new Color(56, 56, 56));
        consoleFrame.setLayout(new BorderLayout());
        consoleFrame.add(hud, BorderLayout.NORTH);
        consoleFrame.add(terminalPane, BorderLayout.CENTER);
        consoleFrame.setVisible(true);
    }

    /**
     * A basic unlock function for the console that takes in the
     * user input for one line. A document-level implementation.
     *
     * @return the user entered content as a String
     */
    public static String unlock() {

        final var userInput = new AtomicReference<>(""); // stores String as atomic for EDT safety
        final var enterLatch = new CountDownLatch(1); // using CDLs for thread waiting

        // step 1 we begin the user input
        try {SwingUtilities.invokeAndWait(() -> viewport.beginUserInput(enterLatch, userInput));}
        catch (InterruptedException | InvocationTargetException e) {
            Thread.currentThread().interrupt();
            return "";
        } // Java boilerplate, legit never ever experienced any of these exceptions

        // step 2 wait for input to happen
        try {enterLatch.await();}
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            userInput.set("");
        } finally {SwingUtilities.invokeLater(viewport::endUserInput);}

        return userInput.get();
    }

    /**
     * Default document is flawed in terms of memory. So we build one from the ground up.
     * The original one is simply sh!t in terms of RAM usage.
     * Allows for input down to the document level which is easier to implement.
     * Sounds design patternsy to use MVC but works.
     */
    private static class ConsoleDocument {

        // ArrayList is basically more memory efficient than traditional docs
        // Somehow traditional docs need to store some more data?? What data they storing
        private final List<String> lines = new ArrayList<>(100000);
        private volatile boolean inputActive = false;
        private StringBuilder activeInputLine;

        public synchronized void appendLines(List<String> newLines) {

            // start adding at the correct index
            if (inputActive && !lines.isEmpty()) lines.addAll(lines.size() - 1, newLines);
            else lines.addAll(newLines); // if it's empty (initialize)
        }

        public synchronized void startInput() {
            activeInputLine = new StringBuilder(">>> "); // input hint
//            lines.add(null);
            inputActive = true;
        }

        public synchronized String endInput() {
            if (!inputActive) return ""; // heuristic: don't return anything if somehow ended
            var input = activeInputLine.substring(4); // length of input hint
            lines.set(lines.size() - 1, activeInputLine.toString());  // add to our list
            activeInputLine = null;
            inputActive = false; // de-initialize everything
            return input; // finally return what was inputted
        }

        public synchronized void deleteChar(int pos) {

            // ONLY used in input mode. We delete the 4th one, accounting for input hint.
            if (inputActive && pos >= 0) activeInputLine.deleteCharAt(pos + 4);
        }

        public synchronized String getLine(int index) {

            // small thing for current line which is mostly what's needed
            if (inputActive && index == lines.size() - 1) return activeInputLine.toString();
            if (index >= 0 && index < lines.size()) return lines.get(index); // before current line
            return null;  // properly allow a NPE to throw, usually doesn't happen(?)
        }

        // the other things required in a document
        public synchronized void insertChar(int pos, char c) {if (inputActive) activeInputLine.insert(pos + 4, c);}
        public synchronized int getActiveInputLength() {return inputActive ? activeInputLine.length() - 4 : 0;}
        public boolean isInputActive() {return inputActive;}
        public synchronized int getLineCount() {return lines.size();}
    }

    /**
     * Java's viewport is the combined effort of de-optimization
     * enabled by secret Java hating spies in a Java hate agency.
     * What were they smoking when they made it??
     * <p>
     * Our custom viewport is built form {@code Scrollable} and
     * used as a {@code JComponent} (similar to Java's own ones).
     * The real console lies in-memory, and the view is responsible
     * for taking continuous snapshots of that console.
     * <p>
     * It's used in conjunction with our custom document model
     * and is built for render efficiency along with performance,
     * while maintaining most compatibility with Java's functions
     * and also smooth scrolling yay!
     */
    private static class ConsoleView extends JComponent implements Scrollable {

        // initializes the private hell of objects
        // NOTE: only initialize when necessary/have many usages!
        private final ConsoleDocument model;
        private final int lineHeight;
        private final FontMetrics fontMetrics;

        // we implement our custom selection because this thing
        // does not share properties with text areas
        private Point selectionStart = null, selectionEnd = null;

        // this whole thing is for handling inputs..
        private boolean inputActive = false;
        private int caretPosition = 0;
        private final Timer caretTimer;
        private KeyListener inputKeyListener;
        private CountDownLatch inputLatch;
        private AtomicReference<String> inputResult;

        // initialize the class
        public ConsoleView(ConsoleDocument model) {
            this.model = model;
            var consoleFont = new Font("SF Mono", Font.PLAIN, 14);
            setFont(consoleFont); fontMetrics = getFontMetrics(consoleFont);
            lineHeight = fontMetrics.getHeight();
            setFocusable(true);
            setOpaque(true); // we paint our own background, but this helps swing optimize
            caretTimer = new Timer(500, e -> {if (inputActive) repaintCaret();}); // make sure caret is there upon input
            caretTimer.setRepeats(true);

            // add our listeners so we can see the selection
            addMouseListener(new MouseAdapter(){
                public void mousePressed(MouseEvent e){
                    requestFocusInWindow();
                    if(inputActive) return;
                    selectionStart = viewToModelCoords(e.getPoint());
                    selectionEnd = selectionStart;
                    repaint();
                }

                // important to make the selection actually finalize
                public void mouseReleased(MouseEvent e){if(!inputActive) repaint();}
            });

            addMouseMotionListener(new MouseMotionAdapter(){
                public void mouseDragged(MouseEvent e){
                    if(selectionStart == null||inputActive) return;
                    selectionEnd = viewToModelCoords(e.getPoint());
                    scrollRectToVisible(new Rectangle(e.getPoint()));
                    repaint();
                }
            });

            var inputMap = getInputMap(WHEN_FOCUSED);
            var actionMap = getActionMap();

            inputMap.put(KeyStroke.getKeyStroke(
                    KeyEvent.VK_C,Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "copy");

            actionMap.put("copy", new AbstractAction(){public void actionPerformed(ActionEvent e){
                if(selectionStart == null || selectionEnd == null) return; // handle null cases just in case
                Point start = getOrderedStart(),
                      end = getOrderedEnd();
                var sb = new StringBuilder();

                for(var line = start.y; line <= end.y; line++){
                    String lineText = model.getLine(line);
                    if(lineText == null) continue;
                    int lineStartCol = (line==start.y) ? start.x:0;
                    int lineEndCol = (line == end.y) ? end.x:lineText.length();
                    if(lineEndCol > lineText.length()) lineEndCol = lineText.length();
                    if(lineStartCol < lineEndCol) sb.append(lineText, lineStartCol, lineEndCol);
                    if(line < end.y) sb.append('\n');
                }

                // set the clipboard
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()),null);
            }});
        }

        public int getLineHeight() {return this.lineHeight;}
        public void repaintForcibly() {revalidate(); repaint();} // we can't use the repaint name, that's patented

        // annotation overload because complex logic
        protected void paintComponent(Graphics g) {

            // typical rendering for Retina screens
            super.paintComponent(g);
            var g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY); // minimal GPU effect

            // ONLY paint the visible stuff, the rest is blit
            Rectangle visibleRect = getVisibleRect(); // what we see on panel
            g2d.setColor(new Color(56, 56, 56));
            g2d.fillRect(visibleRect.x, visibleRect.y, visibleRect.width, visibleRect.height); // paint only our rect

            // NOTE: When calculating, include visibleRect.y because of indents and stuff
            // so the real visible lines may be partially outside our viewable area but still THERE.
            var firstLine = visibleRect.y / lineHeight; // the boundary for our first visible line
            var lastLine = Math.min(model.getLineCount() - 1, // either the last line in doc model,
                                   (visibleRect.y + visibleRect.height) / lineHeight); // or our last viewing line.
            if (lastLine < 0) {g2d.dispose(); return;} // The thing is either broken or not initialized yet

            // selection handling logic
            Point selStart = getOrderedStart(), selEnd = getOrderedEnd();

            // loop to paint each line in selection, CPU preloading
            for (var i = firstLine; i <= lastLine; i++) {
                var lineY = i * lineHeight; // current position pixel for line
                var lineText = model.getLine(i);

                // NOTE: needed as we sometimes assign lines to null when there's an error,
                // so to avoid NPE we must do continue. Could fix in future by eliminating null
                // line assigning completely and only doing stuff like return "" instead.
                if (lineText == null) continue;

                // continuously highlight and color until stops
                // NOTE: selStart and selEnd are unavoidably @Nullable so we must check.
                if (selStart != null && selEnd != null && i >= selStart.y && i <= selEnd.y) {

                    // font is usually 16px in height so we use 8 for good looks
                    int startX = 8, endX = 8 + fontMetrics.stringWidth(lineText);

                    // Handling edge cases to include all chars of selection (regularly won't paint)
                    if (i == selStart.y) // if we are at the current start of selection
                        startX += fontMetrics.stringWidth(lineText.substring(0, selStart.x)); // "add" this to paint
                    if (i == selEnd.y) // if we are at the current end of selection
                        endX = 8 + fontMetrics.stringWidth(lineText.substring(0, selEnd.x)); // still paint it.
                    g2d.setColor(new Color(90, 90, 90)); // high contrast enough?
                    g2d.fillRect(startX, lineY, endX - startX, lineHeight); // paint everything
                }

                g2d.setColor(Color.WHITE);
                g2d.drawString(lineText, 8, lineY + fontMetrics.getAscent());
            }

            // painting our final selection (GPU)
            if(inputActive && caretTimer.isRunning()){
                var i = model.getLineCount() - 1;
                var t = model.getLine(i);
                if(t != null){
                    int x = 8 + fontMetrics.stringWidth(t.substring(0, caretPosition + 4)),
                        y = i * lineHeight;
                    g.setColor(Color.WHITE);
                    g.fillRect(x, y, 1, lineHeight); // draw custom rectangle for selection
                }
            }

            g2d.dispose();
        }

        private Point viewToModelCoords(Point p){
            var line = Math.max(0, p.y / lineHeight);

            if(line >= model.getLineCount()) {
                line = model.getLineCount() - 1; // force the correct numbering
                if (line < 0) return new Point(0,0); // handle bad case
            }

            var t = model.getLine(line);
            if(t == null) return new Point(0, line);

            var w = 8; // laf width
            for(var c = 0; c < t.length(); c++){ // dream come true in actual code lol c++
                var charWidth = fontMetrics.charWidth(t.charAt(c));
                if(w + (charWidth / 2) > p.x) return new Point(c, line);
                w += charWidth; // character with respect to the width
            }

            return new Point(t.length(), line); // now our Point is 100% safe
        }

        // Why don't we use getDot and getMark?? whatever this works
        private Point getOrderedStart(){
            if(selectionStart == null) return null; // using NPE to handle NPE...
            return (selectionStart.y < selectionEnd.y ||
                   (selectionStart.y == selectionEnd.y && selectionStart.x < selectionEnd.x))
                   ? selectionStart : selectionEnd; // gets the real start of selection
        }

        private Point getOrderedEnd(){
            if(selectionStart == null) return null;
            return (selectionStart.y > selectionEnd.y ||
                   (selectionStart.y == selectionEnd.y && selectionStart.x > selectionEnd.x))
                   ? selectionStart:selectionEnd; // gets the real end of selection
        }

        private void repaintCaret(){
            if(!inputActive) return; // could remove because initialization errors are already handled
            int i = model.getLineCount() - 1,
                y = i * lineHeight;
            repaint(0, y, getWidth(),lineHeight + 1); // this is pretty expensive esp. called freq.
        }

        // NOTE: don't inline, should be in ConsoleView
        public void beginUserInput(CountDownLatch l, AtomicReference<String> r){
            this.inputLatch = l;
            this.inputResult = r;
            model.startInput();
            inputActive = true;
            caretPosition = model.getActiveInputLength();

            // IMPORTANT: Cancel the selections
            selectionStart = null;
            selectionEnd = null;
            repaintForcibly(); // force a repaint right after we cancel our selections

            // sometimes the user is idiot
            SwingUtilities.invokeLater(() -> scrollRectToVisible(new Rectangle(0,getPreferredSize().height,1,1)));

            // handle keypresses
            if(inputKeyListener == null) inputKeyListener = new KeyAdapter(){
                public void keyPressed(KeyEvent e){

                    int c = e.getKeyCode(), l = model.getActiveInputLength();

                    switch(c) {
                        case KeyEvent.VK_ENTER: // return the user input
                            inputResult.set(model.endInput());
                            inputLatch.countDown();
                            break;
                        case KeyEvent.VK_BACK_SPACE:
                            if (caretPosition > 0) model.deleteChar(--caretPosition);
                            break;
                        case KeyEvent.VK_DELETE: // opposite of backspace
                            if (caretPosition < l) model.deleteChar(caretPosition);
                            break;
                        case KeyEvent.VK_LEFT:
                            if (caretPosition > 0) caretPosition--;
                            break;
                        case KeyEvent.VK_RIGHT:
                            if (caretPosition < l) caretPosition++;
                            break;

                        // Less conventional keys, but exists in Java's
                        // own API so we include for compatibility
                        case KeyEvent.VK_HOME:
                            caretPosition = 0;
                            break;
                        case KeyEvent.VK_END:
                            caretPosition = l;
                            break;
                        default:
                            if (e.isActionKey() || e.isControlDown() || e.isMetaDown()) return; // don't allow modifier keys
                            char k = e.getKeyChar(); // add the correct input keys safely
                            if (k != KeyEvent.CHAR_UNDEFINED && k >= ' ') model.insertChar(caretPosition++, k);
                    }

                    caretTimer.restart();
                    repaintCaret();
                }
            };

            addKeyListener(inputKeyListener);
            requestFocusInWindow(); // focus can be drawn away by stuff like scrollbars
            caretTimer.start();
        }

        // remove anything added in beginUserInput
        public void endUserInput(){
            removeKeyListener(inputKeyListener);
            caretTimer.stop();
            inputActive = false;
            repaintCaret();
        }

        // more boilerplate required in java
        // we could make the class abstract and not do these things, but then we can't implement in console
        public Dimension getPreferredSize(){return new Dimension(1,model.getLineCount() * lineHeight);}
        public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
        public int getScrollableUnitIncrement(Rectangle r,int o,int d){return lineHeight;}
        public int getScrollableBlockIncrement(Rectangle r,int o,int d){return r.height;}
        public boolean getScrollableTracksViewportWidth(){return true;}
        public boolean getScrollableTracksViewportHeight(){return false;}
    }

    /**
     * A lightweight viewer for displaying the current line number.
     * In 90% conditions, this doesn't add any CPU overhead.
     * <p>
     * idk why need this in a console but looks cool. and doesn't
     * chew up performance.
     */
    private static class LineNumberView extends JComponent {
        private final ConsoleDocument model;
        private final ConsoleView view;
        private final Font font = new Font("Monospaced",Font.PLAIN,12); // smaller font
        private final FontMetrics fontMetrics = getFontMetrics(font);

        // Class initialization. We NEED to pass in the doc and view (even though
        // they're already here) because of proper setup and object stuff.
        public LineNumberView(ConsoleDocument m, ConsoleView v){
            this.model = m;
            this.view = v;
            setFont(font);
            setForeground(Color.LIGHT_GRAY); // someday create a color palette for console
            setBackground(new Color(40,40,40));
            setOpaque(true);

            // don't repaint, only revalidate upon resize.
            view.addComponentListener(new ComponentAdapter(){
                public void componentResized(ComponentEvent e){revalidate();}
            });
        }

        public Dimension getPreferredSize(){
            int lines = model.getLineCount(); // total current lines. Should match with our view
            int width = fontMetrics.stringWidth(String.valueOf(lines == 0 ? 1 : lines)); // handle the 0 condition
            return new Dimension(width + 10, view.getPreferredSize().height); // 10px margin
        }

        protected void paintComponent(Graphics g){
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Rectangle clip = g2d.getClipBounds();
            g2d.setColor(getBackground());
            g2d.fillRect(clip.x, clip.y, clip.width, clip.height);

            // same logic in our main view component
            int firstLine = clip.y / view.getLineHeight(),
                lastLine = Math.min(model.getLineCount() - 1,
                                   (clip.y + clip.height) / view.getLineHeight());

            if(lastLine < 0){g2d.dispose(); return;}
            g2d.setColor(getForeground());

            for(int i = firstLine; i <= lastLine; i++){
                String ln = String.valueOf(i + 1);
                int x = getWidth() - fontMetrics.stringWidth(ln) - 5,
                        y = (i * view.getLineHeight()) + this.fontMetrics.getAscent();
                g2d.drawString(ln, x, y);
            }

            g2d.dispose();
        }
    }

    /**
     * Our custom high performance datastream. Built to be a
     * "Maradona quality replacement" for OutputStream (which
     * is objectively also crack).
     * <p>
     * Unlike ConsoleView, this is for handling and ingesting
     * raw input data. Kind of the "backend/controller" of an MVC.
     */
    private static class ConsoleStream extends OutputStream {
        private final ConsoleDocument model;
        private final JScrollBar scrollBar;
        private final ConsoleView view;
        private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

        public ConsoleStream(ConsoleDocument m, ConsoleView v, JScrollBar s){
            this.model = m;
            this.view = v;
            this.scrollBar = s;
            ScheduledExecutorService flusher = Executors.newSingleThreadScheduledExecutor(
                    r -> new Thread(r, "Maradona Console Flusher"));

            // 20fps flushing feels just right for a fast console
            flusher.scheduleAtFixedRate(() -> {
                if(queue.isEmpty()) return;
                List<String> batch = new ArrayList<>(queue.size());
                String line;
                while((line = queue.poll()) != null) batch.add(line);
                SwingUtilities.invokeLater(() -> { // do on EDT
                    boolean isAtBottom = (scrollBar.getValue() + scrollBar.getVisibleAmount()) >=
                                         (scrollBar.getMaximum() - 5); // 5px threshold

                    // --=== START DEBUG ===--
                    System.out.println("THIS: " + (scrollBar.getValue() + scrollBar.getVisibleAmount()) + "\n" +
                                       "THRESH: " + (scrollBar.getMaximum() - 5) + "\n\n");
                    //   -=== END DEBUG ===-

                    model.appendLines(batch);
                    view.repaintForcibly();
                    if (isAtBottom && !model.isInputActive()) // autoscroll when already at bottom
                        scrollBar.setValue(scrollBar.getMaximum());
                });
            },100, 50, TimeUnit.MILLISECONDS);
        }

        public void writeln(String line){Collections.addAll(queue,line.split("\n",-1));} // primary API
        public void write(byte[] b,int off,int len){writeln(new String(b,off,len,StandardCharsets.UTF_8));}
        public void write(int b){} // forced to do this by java's design. unless we're asm writers, we won't need this at ALL.
    }

    public static BufferedImage readImg(String macchiatoDir){
        BufferedImage image = null;
        try {image = ImageIO.read(Objects.requireNonNull(Console.class.getClassLoader().getResourceAsStream("images/" + macchiatoDir)));
        } catch (IOException e) {
            Console.error(e);}
        return image;
    }

    public static JFrame makeFrame(String title, int width, int height){
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        frame.getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        frame.getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        frame.getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        frame.setTitle(title);
        frame.setLocationRelativeTo(null);
        frame.setBackground(new Color(56, 56, 56));
        frame.setMinimumSize(new Dimension(width, height));
        return frame;
    }
}
