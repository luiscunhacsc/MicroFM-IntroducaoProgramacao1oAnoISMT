package microfm.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.util.Locale;

/** Sistema visual partilhado. Todos os elementos são Swing/Java2D. */
public final class Theme {
    public static final Color BG = new Color(0x0B111B), SIDE = new Color(0x0E1622),
        CARD = new Color(0x141F2D), RAISED = new Color(0x1B2939), LINE = new Color(0x28374A),
        TEXT = new Color(0xF2F5F8), MUTED = new Color(0x94A5B9), GREEN = new Color(0xB5F280),
        TEAL = new Color(0x51D8B2), ORANGE = new Color(0xFFBE7A), RED = new Color(0xFF838C);
    private Theme() { }
    public static Font font(float size) { return new Font("Segoe UI", Font.PLAIN, (int) size); }
    public static Font bold(float size) { return font(size).deriveFont(Font.BOLD); }
    public static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }
    public static void install() {
        UIManager.put("Panel.background", BG); UIManager.put("Label.foreground", TEXT);
        UIManager.put("Label.font", font(14)); UIManager.put("Button.font", bold(13));
        UIManager.put("Button.background", RAISED); UIManager.put("Button.foreground", TEXT);
        UIManager.put("OptionPane.background", CARD); UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("TextField.background", RAISED); UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", GREEN); UIManager.put("TextField.font", font(15));
        UIManager.put("ComboBox.background", RAISED); UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", LINE); UIManager.put("ComboBox.selectionForeground", GREEN);
        UIManager.put("ComboBox.font", font(13)); UIManager.put("List.background", RAISED);
        UIManager.put("List.foreground", TEXT); UIManager.put("ScrollBar.thumb", LINE);
        UIManager.put("ScrollBar.track", BG); UIManager.put("ToolTip.background", RAISED);
        UIManager.put("ToolTip.foreground", TEXT); UIManager.put("ToolTip.font", font(12));
    }
    public static JPanel panel(LayoutManager layout) { JPanel p = new JPanel(layout); p.setOpaque(false); p.setAlignmentX(Component.LEFT_ALIGNMENT); return p; }
    public static JPanel column(int gap) { JPanel p = panel(null); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); return p; }
    public static JLabel label(String text, int size, Color color) {
        JLabel l = new JLabel(text); l.setFont(displayFont(text,size,false)); l.setForeground(color); l.setAlignmentX(Component.LEFT_ALIGNMENT); return l;
    }
    private static Font displayFont(String text,int size,boolean bold) {
        Font chosen=bold?bold(size):font(size);
        return chosen.canDisplayUpTo(text)==-1?chosen:new Font("Dialog",bold?Font.BOLD:Font.PLAIN,size);
    }
    public static JLabel heading(String text, int size) { JLabel l = label(text, size, TEXT); l.setFont(displayFont(text,size,true)); return l; }
    public static Card card(LayoutManager layout) { return new Card(layout, CARD); }
    public static JPanel padded(JPanel p, int amount) { p.setBorder(new EmptyBorder(amount,amount,amount,amount)); return p; }
    public static JButton button(String text, Runnable action) { return button(text, false, action); }
    public static JButton button(String text, boolean primary, Runnable action) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create(); quality(g);
                Color base = primary ? GREEN : RAISED;
                if (!isEnabled()) base = LINE;
                else if (getModel().isPressed()) base = primary ? TEAL : LINE;
                else if (getModel().isRollover()) base = primary ? new Color(0xC8FFA1) : new Color(0x2C4055);
                g.setColor(base); g.fillRoundRect(0,0,getWidth(),getHeight(),12,12);
                if (isFocusOwner()) { g.setColor(TEAL); g.setStroke(new BasicStroke(2)); g.drawRoundRect(2,2,getWidth()-5,getHeight()-5,10,10); }
                g.dispose(); super.paintComponent(graphics);
            }
        };
        b.setFont(displayFont(text,13,true)); b.setForeground(primary ? BG : TEXT); b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setOpaque(false); b.setContentAreaFilled(false); b.setBorderPainted(false);
        b.setFocusPainted(false); b.setBorder(new EmptyBorder(11,18,11,18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> action.run()); return b;
    }
    public static JComboBox<String> combo(String[] options) {
        JComboBox<String> c = new JComboBox<>(options); c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setUI(new BasicComboBoxUI(){
            @Override protected JButton createArrowButton(){
                JButton arrow=new JButton(){@Override protected void paintComponent(Graphics graphics){
                    Graphics2D g=(Graphics2D)graphics.create();quality(g);g.setColor(RAISED);g.fillRect(0,0,getWidth(),getHeight());
                    g.setColor(MUTED);int x=getWidth()/2,y=getHeight()/2;g.setStroke(new BasicStroke(1.5f));g.drawLine(x-3,y-2,x,y+1);g.drawLine(x,y+1,x+3,y-2);g.dispose();
                }};
                arrow.setPreferredSize(new Dimension(26,24));arrow.setBorder(null);arrow.setFocusable(false);return arrow;
            }
        });
        c.setRenderer(new DefaultListCellRenderer(){@Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
            super.getListCellRendererComponent(list,value,index,selected,focus);setFont(font(13));setBorder(new EmptyBorder(5,9,5,9));
            setBackground(selected?LINE:RAISED);setForeground(selected?GREEN:TEXT);return this;
        }});
        c.setBorder(new LineBorder(LINE));
        c.setMaximumSize(new Dimension(400,38)); return c;
    }
    public static JScrollPane scroll(Component content) {
        JScrollPane s = new JScrollPane(content); s.setBorder(null); s.setOpaque(false);
        s.getViewport().setOpaque(false); s.getVerticalScrollBar().setUnitIncrement(28);
        if(content instanceof JTable table){s.setColumnHeaderView(table.getTableHeader());s.getColumnHeader().setBackground(RAISED);}
        s.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        s.getVerticalScrollBar().setPreferredSize(new Dimension(9,0));
        s.getVerticalScrollBar().setUI(new BasicScrollBarUI(){
            @Override protected JButton createDecreaseButton(int orientation){return emptyArrow();}
            @Override protected JButton createIncreaseButton(int orientation){return emptyArrow();}
            private JButton emptyArrow(){JButton b=new JButton();b.setPreferredSize(new Dimension(0,0));b.setMinimumSize(new Dimension(0,0));b.setMaximumSize(new Dimension(0,0));return b;}
            @Override protected void paintTrack(Graphics g,JComponent c,Rectangle bounds){g.setColor(CARD);g.fillRect(bounds.x,bounds.y,bounds.width,bounds.height);}
            @Override protected void paintThumb(Graphics graphics,JComponent c,Rectangle bounds){Graphics2D g=(Graphics2D)graphics.create();quality(g);g.setColor(isThumbRollover()?MUTED:LINE);g.fillRoundRect(bounds.x+2,bounds.y+2,Math.max(3,bounds.width-4),Math.max(4,bounds.height-4),5,5);g.dispose();}
        });return s;
    }
    public static JTable table(String[] columns, Object[][] rows) {
        DefaultTableModel model = new DefaultTableModel(rows, columns) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
            @Override public Class<?> getColumnClass(int column){return rows.length>0&&rows[0][column]!=null?rows[0][column].getClass():Object.class;}
        };
        JTable t = new JTable(model); t.setFont(font(13)); t.setRowHeight(43);
        t.setBackground(CARD); t.setForeground(TEXT); t.setSelectionBackground(new Color(0x314B47));
        t.setSelectionForeground(GREEN); t.setShowGrid(false); t.setIntercellSpacing(new Dimension(0,0));
        t.setFillsViewportHeight(true); t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.getTableHeader().setFont(bold(11)); t.getTableHeader().setBackground(RAISED);
        t.getTableHeader().setForeground(MUTED); t.getTableHeader().setReorderingAllowed(false);
        t.getTableHeader().setPreferredSize(new Dimension(0,36));
        t.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer(){
            @Override public Component getTableCellRendererComponent(JTable table,Object value,boolean selected,boolean focus,int row,int col){
                super.getTableCellRendererComponent(table,value,selected,focus,row,col);setBackground(RAISED);setForeground(MUTED);setFont(bold(10));
                setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,1,0,LINE),new EmptyBorder(0,12,0,8)));return this;
            }
        });
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table,Object value,boolean selected,boolean focus,int row,int col) {
                super.getTableCellRendererComponent(table,value,selected,focus,row,col);
                setBorder(new EmptyBorder(0,12,0,10));
                if (!selected) { setBackground(row%2==0 ? CARD : new Color(0x172332)); setForeground(TEXT); }
                return this;
            }
        };
        t.setDefaultRenderer(Object.class,renderer);t.setDefaultRenderer(Number.class,renderer);
        t.setAutoCreateRowSorter(true); return t;
    }
    public static String money(int amount) { return String.format(Locale.forLanguageTag("pt-PT"),"%,d €",amount); }
    public static JComponent bar(double fraction, Color color) {
        return new JComponent() {
            { setPreferredSize(new Dimension(100,5)); }
            @Override protected void paintComponent(Graphics gr) {
                Graphics2D g=(Graphics2D)gr.create(); quality(g); g.setColor(LINE);
                g.fillRoundRect(0,0,getWidth(),getHeight(),5,5); g.setColor(color);
                g.fillRoundRect(0,0,(int)(getWidth()*Math.max(0,Math.min(1,fraction))),getHeight(),5,5); g.dispose();
            }
        };
    }
    public static final class Card extends JPanel {
        private final Color color;
        public Card(LayoutManager layout, Color color) { super(layout); this.color=color; setOpaque(false); }
        @Override protected void paintComponent(Graphics gr) {
            Graphics2D g=(Graphics2D)gr.create(); quality(g); g.setColor(color);
            g.fillRoundRect(0,0,getWidth()-1,getHeight()-1,20,20);
            g.setColor(LINE); g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,20,20); g.dispose();
            super.paintComponent(gr);
        }
    }
}
