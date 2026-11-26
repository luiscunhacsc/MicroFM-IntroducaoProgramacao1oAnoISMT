package microfm.ui;

import java.awt.*;
import java.awt.geom.*;
import javax.swing.*;
import microfm.model.*;

/** Ilustração vetorial: redimensiona sem imagens ou recursos externos. */
public final class PitchArt extends JComponent {
    private final Career career;
    private final boolean tactical;
    public PitchArt(Career career, boolean tactical) {
        this.career=career; this.tactical=tactical;
        setPreferredSize(new Dimension(tactical?440:540,tactical?450:290));
    }
    public static Icon crest(Club club, int size) {
        return new Icon() {
            public int getIconWidth() { return size; }
            public int getIconHeight() { return size; }
            public void paintIcon(Component c,Graphics graphics,int x,int y) {
                Graphics2D g=(Graphics2D)graphics.create(); Theme.quality(g); g.translate(x,y);
                double s=size/64.0; g.scale(s,s); Path2D shield=new Path2D.Double();
                shield.moveTo(7,6); shield.lineTo(57,6); shield.lineTo(55,37);
                shield.quadTo(49,53,32,61); shield.quadTo(15,53,9,37); shield.closePath();
                g.setColor(new Color(club.rgb())); g.fill(shield);
                g.setColor(new Color(255,255,255,80)); g.setStroke(new BasicStroke(2)); g.draw(shield);
                g.setColor(new Color(255,255,255,35)); g.fillRect(28,9,8,36);
                g.setColor(Color.WHITE); g.setFont(Theme.bold(17));
                String text=club.shortName().substring(0,Math.min(3,club.shortName().length()));
                FontMetrics fm=g.getFontMetrics(); g.drawString(text,32-fm.stringWidth(text)/2,34);
                g.dispose();
            }
        };
    }
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g=(Graphics2D)graphics.create(); Theme.quality(g);
        int w=getWidth(),h=getHeight();
        if (tactical) {
            double x=28,y=22,pw=w-56,ph=h-44;
            g.setColor(new Color(0x123C36)); g.fillRoundRect((int)x,(int)y,(int)pw,(int)ph,10,10);
            for(int i=0;i<10;i++) if(i%2==0) { g.setColor(new Color(255,255,255,7)); g.fill(new Rectangle2D.Double(x,y+i*ph/10,pw,ph/10)); }
            g.setColor(new Color(0x5A8C77)); g.setStroke(new BasicStroke(1.3f));
            g.draw(new Rectangle2D.Double(x+12,y+12,pw-24,ph-24));
            g.draw(new Line2D.Double(x+12,h/2.0,x+pw-12,h/2.0));
            g.draw(new Ellipse2D.Double(w/2.0-36,h/2.0-36,72,72));
            g.draw(new Rectangle2D.Double(w/2.0-pw*.23,y+12,pw*.46,ph*.15));
            g.draw(new Rectangle2D.Double(w/2.0-pw*.23,y+ph-12-ph*.15,pw*.46,ph*.15));
            if(career!=null) {
                double[][] positions=career.formation().positions();
                for(int i=0;i<11;i++) {
                    double px=x+26+positions[i][1]*(pw-52),py=y+ph-20-positions[i][0]*(ph-40);
                    g.setColor(new Color(0,0,0,65)); g.fill(new Ellipse2D.Double(px-17,py-12,36,36));
                    g.setColor(i==0?Theme.ORANGE:Theme.GREEN); g.fill(new Ellipse2D.Double(px-16,py-17,32,32));
                    g.setColor(Theme.BG); g.setFont(Theme.bold(12));
                    String number=""+(i+1); g.drawString(number,(float)(px-g.getFontMetrics().stringWidth(number)/2.0),(float)(py+3));
                    Player p=career.lineup().get(i); String[] names=p.name().split(" "); String last=names[names.length-1];
                    g.setColor(Theme.TEXT); g.setFont(Theme.bold(11));
                    g.drawString(last,(float)(px-g.getFontMetrics().stringWidth(last)/2.0),(float)(py+30));
                }
            }
        } else {
            g.setPaint(new RadialGradientPaint(new Point2D.Double(w*.52,h*.55),(float)Math.max(1,w*.65),
                new float[]{0,1},new Color[]{new Color(0x235B43),new Color(0x142A2B)}));
            g.fillRoundRect(0,0,w,h,22,22);
            g.translate(w*.06,h*.12); g.shear(-.18,0); g.scale(.9,.78);
            double pw=w*.95,ph=h*.9;
            g.setColor(new Color(0x315E45)); g.fill(new RoundRectangle2D.Double(0,0,pw,ph,8,8));
            for(int i=0;i<10;i++){g.setColor(new Color(255,255,255,i%2==0?8:1));g.fill(new Rectangle2D.Double(i*pw/10,0,pw/10,ph));}
            g.setColor(new Color(0x6EAD79)); g.setStroke(new BasicStroke(1.5f));
            g.draw(new Rectangle2D.Double(14,14,pw-28,ph-28)); g.draw(new Line2D.Double(pw/2,14,pw/2,ph-14));
            g.draw(new Ellipse2D.Double(pw/2-32,ph/2-32,64,64));
            g.draw(new Rectangle2D.Double(14,ph*.28,pw*.14,ph*.44));
            g.draw(new Rectangle2D.Double(pw*.86-14,ph*.28,pw*.14,ph*.44));
            double[][] nodes={{.1,.5},{.28,.2},{.28,.4},{.28,.6},{.28,.8},{.48,.3},{.48,.5},{.48,.7},{.72,.2},{.78,.5},{.72,.8}};
            for(int i=0;i<nodes.length;i++) {
                double px=nodes[i][0]*pw,py=nodes[i][1]*ph;
                g.setColor(new Color(0,0,0,60)); g.fill(new Ellipse2D.Double(px-9,py-4,20,13));
                g.setColor(i==0?Theme.ORANGE:Theme.GREEN);g.fill(new Ellipse2D.Double(px-7,py-12,14,14));
                g.setColor(new Color(181,242,128,35));g.draw(new Ellipse2D.Double(px-14,py-19,28,28));
            }
            g.setColor(Color.WHITE);g.fill(new Ellipse2D.Double(pw*.58,ph*.46,7,7));
        }
        g.dispose();
    }
}
