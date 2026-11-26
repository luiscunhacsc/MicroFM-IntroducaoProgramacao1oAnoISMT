package microfm.match;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import javax.swing.JComponent;

/**
 * Câmara do estádio desenhada apenas com Java2D. O cenário mais dispendioso é
 * guardado numa imagem e só volta a ser criado quando a janela muda de tamanho.
 * paintComponent não altera a simulação nem espera por temporizadores.
 */
public final class MatchCanvas extends JComponent {
    private static final Color INK = new Color(10,18,29);
    private static final Color WHITE = new Color(234,247,242);
    private final MatchEngine engine;
    private BufferedImage stadium;
    private int cachedWidth, cachedHeight, seenGoal;
    private double time, goalAge=100;
    private final ArrayDeque<TrailPoint> trail = new ArrayDeque<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<MatchEngine.Footballer> depthOrder = new ArrayList<>();
    private final Random effects = new Random(181);
    private double pitchX,pitchY,pitchW,pitchH;
    private final Color homeColor,awayColor;
    private boolean showNames=true;
    private record TrailPoint(double x,double y,double z,double age) { }
    private static final class Particle {
        double x,y,vx,vy,life;
        Color color;
        Particle(double x,double y,double vx,double vy,Color color) {
            this.x=x;this.y=y;this.vx=vx;this.vy=vy;this.life=1;this.color=color;
        }
    }

    public MatchCanvas(MatchEngine engine) {
        this.engine=engine;
        this.homeColor=new Color(engine.homeClub().rgb());
        Color away=new Color(engine.awayClub().rgb());
        double difference=Math.abs(homeColor.getRed()-away.getRed())+Math.abs(homeColor.getGreen()-away.getGreen())
            +Math.abs(homeColor.getBlue()-away.getBlue());
        this.awayColor=difference<150?new Color(242,232,204):away;
        setOpaque(true);
        setBackground(INK);
        setFocusable(false);
        setToolTipText("Relvado em direto · jogadores e bola simulados em tempo real");
        setPreferredSize(new java.awt.Dimension(940,540));
        setMinimumSize(new java.awt.Dimension(380,240));
        depthOrder.addAll(engine.footballers());
    }

    /** dt é tempo de animação; a simulação avança separadamente no controlador. */
    public void updateVisuals(double dtSeconds) {
        if (!Double.isFinite(dtSeconds) || dtSeconds<=0) return;
        double dt=Math.min(dtSeconds,.1);
        time+=dt;goalAge+=dt;
        if (seenGoal!=engine.goalSerial()) {
            seenGoal=engine.goalSerial();goalAge=0;
            Color color=engine.lastGoalHome()?homeColor:awayColor;
            for(int i=0;i<90;i++) particles.add(new Particle(.5,.43,
                (effects.nextDouble()-.5)*1.1,-.15-effects.nextDouble()*.65,
                i%3==0?new Color(255,214,122):i%3==1?WHITE:color));
        }
        for(Particle p:particles) {
            p.x+=p.vx*dt;p.y+=p.vy*dt;p.vy+=.5*dt;p.life-=dt*.34;
        }
        particles.removeIf(p->p.life<=0 || p.y>1.3);
        ArrayDeque<TrailPoint> aged=new ArrayDeque<>();
        for(TrailPoint p:trail) if(p.age+dt<.32) aged.add(new TrailPoint(p.x,p.y,p.z,p.age+dt));
        trail.clear();trail.addAll(aged);
        MatchEngine.Ball b=engine.ball();
        if(!engine.halftime() && !engine.finished()) trail.add(new TrailPoint(b.x(),b.y(),b.height(),0));
        while(trail.size()>18) trail.removeFirst();
        repaint();
    }

    public void setShowNames(boolean value) { showNames=value;repaint(); }
    public void dispose() { stadium=null;trail.clear();particles.clear(); }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if(getWidth()<20 || getHeight()<20)return;
        Graphics2D g=(Graphics2D)graphics.create();
        hints(g);
        configurePitch();
        if(stadium==null || cachedWidth!=getWidth() || cachedHeight!=getHeight()) rebuildStadium();
        // A celebração usa uma vibração curta, sem alterar as coordenadas do jogo.
        if(goalAge<.45)g.translate(Math.sin(goalAge*75)*(1-goalAge/.45)*2,0);
        g.drawImage(stadium,0,0,null);
        paintLiveBoards(g);
        paintBallTrail(g);
        depthOrder.sort(Comparator.comparingDouble(MatchEngine.Footballer::y));
        for(MatchEngine.Footballer f:depthOrder)paintPlayer(g,f);
        paintBall(g);
        paintOwnerLabel(g);
        paintCameraCaption(g);
        paintParticles(g);
        if(engine.celebrationRemaining()>0)paintGoalBanner(g);
        if(engine.halftime() || engine.finished())paintBreakBanner(g);
        g.dispose();
    }

    private void configurePitch() {
        pitchX=getWidth()*.079;pitchW=getWidth()*.842;
        pitchY=getHeight()*.142;pitchH=getHeight()*.70;
    }

    private Point2D.Double point(double x,double y) {
        double perspective=.925+.075*y;
        return new Point2D.Double(pitchX+pitchW*.5+(x-.5)*pitchW*perspective,pitchY+pitchH*y);
    }
    private Shape fieldRect(double x,double y,double w,double h) {
        Path2D.Double p=new Path2D.Double();Point2D.Double a=point(x,y);
        p.moveTo(a.x,a.y);a=point(x+w,y);p.lineTo(a.x,a.y);a=point(x+w,y+h);p.lineTo(a.x,a.y);
        a=point(x,y+h);p.lineTo(a.x,a.y);p.closePath();return p;
    }
    private void fieldLine(Graphics2D g,double x1,double y1,double x2,double y2) {
        g.draw(new Line2D.Double(point(x1,y1),point(x2,y2)));
    }
    private void fieldEllipse(Graphics2D g,double cx,double cy,double rx,double ry) {
        Path2D.Double path=new Path2D.Double();
        for(int i=0;i<=80;i++) {
            double angle=i*Math.PI*2/80;Point2D.Double p=point(cx+Math.cos(angle)*rx,cy+Math.sin(angle)*ry);
            if(i==0)path.moveTo(p.x,p.y);else path.lineTo(p.x,p.y);
        }
        path.closePath();g.draw(path);
    }

    private void rebuildStadium() {
        cachedWidth=getWidth();cachedHeight=getHeight();
        stadium=new BufferedImage(cachedWidth,cachedHeight,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=stadium.createGraphics();hints(g);
        int w=cachedWidth,h=cachedHeight;
        g.setPaint(new GradientPaint(0,0,new Color(14,25,39),0,h,new Color(6,14,22)));
        g.fillRect(0,0,w,h);
        // Bancadas: padrões determinísticos e iluminação radial, calculados só no resize.
        Random crowd=new Random(2048);
        for(int i=0;i<3300;i++) {
            int x=crowd.nextInt(w),y=crowd.nextInt(h);
            boolean stand=y<pitchY-12 || y>pitchY+pitchH+18 || x<pitchX-14 || x>pitchX+pitchW+14;
            if(!stand)continue;
            int brightness=32+crowd.nextInt(43);
            Color fan=i%7==0?homeColor:i%11==0?awayColor:new Color(brightness,brightness+7,brightness+11);
            g.setColor(new Color(fan.getRed(),fan.getGreen(),fan.getBlue(),100+crowd.nextInt(100)));
            g.fillRoundRect(x,y,2+crowd.nextInt(3),2+crowd.nextInt(3),2,2);
        }
        g.setStroke(new BasicStroke(2));g.setColor(new Color(47,62,76,160));
        for(int i=0;i<5;i++) {
            int yy=(int)(pitchY-14-i*10);g.drawLine(0,yy,w,yy);
            yy=(int)(pitchY+pitchH+24+i*10);g.drawLine(0,yy,w,yy);
        }
        // Halo lumineux derrière les bords du terrain.
        g.setColor(new Color(19,45,40));g.fill(fieldRect(-.024,-.055,1.048,1.11));
        g.setColor(new Color(12,25,27));g.setStroke(new BasicStroke(5));g.draw(fieldRect(-.026,-.058,1.052,1.116));
        for(int i=0;i<18;i++) {
            g.setColor(i%2==0?new Color(29,103,68):new Color(32,113,73));
            g.fill(fieldRect(i/18.0,0,1/18.0+.0005,1));
        }
        Shape field=fieldRect(0,0,1,1);Shape originalClip=g.getClip();g.clip(field);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(w*.51,h*.38),(float)(w*.65),
            new float[]{0,1},new Color[]{new Color(117,176,113,25),new Color(0,15,26,105)}));
        g.fillRect(0,0,w,h);
        // Textura finíssima, estática, para não fazer cintilar o relvado.
        for(int i=0;i<6500;i++) {
            Point2D.Double p=point(crowd.nextDouble(),crowd.nextDouble());
            g.setColor(i%2==0?new Color(180,233,169,11):new Color(2,24,16,19));
            g.drawLine((int)p.x,(int)p.y,(int)p.x+1,(int)p.y);
        }
        g.setClip(originalClip);
        g.setColor(new Color(220,242,224,175));g.setStroke(new BasicStroke((float)Math.max(1.1,w/950.0)));
        g.draw(field);fieldLine(g,.5,0,.5,1);fieldEllipse(g,.5,.5,.087,.134);
        g.draw(fieldRect(0,.20,.158,.60));g.draw(fieldRect(.842,.20,.158,.60));
        g.draw(fieldRect(0,.36,.052,.28));g.draw(fieldRect(.948,.36,.052,.28));
        fieldEllipse(g,.105,.5,.002,.003);fieldEllipse(g,.895,.5,.002,.003);
        fieldEllipse(g,.5,.5,.0025,.0036);
        paintPenaltyArc(g,false);paintPenaltyArc(g,true);
        paintGoal(g,false);paintGoal(g,true);
        paintCornerFlag(g,0,0);paintCornerFlag(g,1,0);paintCornerFlag(g,0,1);paintCornerFlag(g,1,1);
        paintBoards(g);
        // Quatro focos iluminam o relvado sem imagens nem recursos externos.
        for(int side=0;side<2;side++)for(int end=0;end<2;end++) {
            float fx=(float)(w*(end==0?.055:.945)),fy=(float)(h*(side==0?.055:.94));
            g.setPaint(new RadialGradientPaint(fx,fy,(float)(w*.17),
                new float[]{0,.12f,1},new Color[]{new Color(216,245,255,75),new Color(210,240,255,18),new Color(200,240,255,0)}));
            g.fill(new Ellipse2D.Double(fx-w*.17,fy-w*.17,w*.34,w*.34));
            g.setColor(new Color(230,247,255,170));g.fillRoundRect((int)fx-12,(int)fy-2,24,4,3,3);
        }
        g.dispose();
    }

    private void paintPenaltyArc(Graphics2D g,boolean right) {
        Path2D.Double path=new Path2D.Double();
        for(int i=0;i<=32;i++) {
            double a=-Math.PI/2+i*Math.PI/32;
            double x=(right?.895:.105)+(right?-1:1)*Math.cos(a)*.087;
            double y=.5+Math.sin(a)*.134;
            if((!right&&x<.158)||(right&&x>.842))continue;
            Point2D.Double p=point(x,y);
            if(path.getCurrentPoint()==null)path.moveTo(p.x,p.y);else path.lineTo(p.x,p.y);
        }
        g.draw(path);
    }

    private void paintGoal(Graphics2D g,boolean right) {
        double line=right?1:0,back=right?1.028:-.028;
        Point2D.Double a=point(line,.40),b=point(line,.60),c=point(back,.60),d=point(back,.40);
        Path2D.Double net=new Path2D.Double();net.moveTo(a.x,a.y);net.lineTo(d.x,d.y-4);
        net.lineTo(c.x,c.y-4);net.lineTo(b.x,b.y);net.closePath();
        g.setColor(new Color(177,207,211,29));g.fill(net);
        g.setStroke(new BasicStroke(.65f));g.setColor(new Color(180,214,217,75));
        for(int i=0;i<=8;i++) {
            double y=.40+.20*i/8;Point2D.Double front=point(line,y),rear=point(back,y);
            g.draw(new Line2D.Double(front.x,front.y,rear.x,rear.y-4));
        }
        for(int i=0;i<=4;i++) {
            double x=line+(back-line)*i/4;Point2D.Double top=point(x,.40),bottom=point(x,.60);
            g.draw(new Line2D.Double(top.x,top.y-i,bottom.x,bottom.y-i));
        }
        g.setStroke(new BasicStroke(2.6f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(new Color(218,234,236));
        g.draw(new Line2D.Double(a.x,a.y-7,b.x,b.y-7));
        g.draw(new Line2D.Double(a.x,a.y-7,a.x,a.y));g.draw(new Line2D.Double(b.x,b.y-7,b.x,b.y));
    }

    private void paintCornerFlag(Graphics2D g,double x,double y) {
        Point2D.Double p=point(x,y);g.setStroke(new BasicStroke(1));g.setColor(new Color(213,224,211));
        g.draw(new Line2D.Double(p.x,p.y,p.x,p.y-10));
        Path2D.Double flag=new Path2D.Double();flag.moveTo(p.x,p.y-10);flag.lineTo(p.x+7,p.y-8);flag.lineTo(p.x,p.y-5);
        g.setColor(new Color(255,208,102));g.fill(flag);
    }

    private void paintBoards(Graphics2D g) {
        for(int side=0;side<2;side++) {
            double y=side==0?-.055:1.035;
            for(int i=0;i<8;i++) {
                Point2D.Double a=point(i/8.0+.003,y),b=point((i+1)/8.0-.003,y);
                int height=Math.max(7,getHeight()/57);
                g.setColor(i%2==0?new Color(19,49,54):new Color(20,32,50));
                g.fillRoundRect((int)a.x,(int)a.y,(int)(b.x-a.x),height,2,2);
                g.setColor(i%2==0?new Color(111,214,180,170):new Color(150,177,205,160));
                g.setFont(new Font("SansSerif",Font.BOLD,Math.max(5,height-3)));
                String text=i%3==0?"O TEU CLUBE. A TUA HISTÓRIA.":i%3==1?"MICROFM":"LIGA MONDEGO";
                FontMetrics fm=g.getFontMetrics();
                if(fm.stringWidth(text)<b.x-a.x-4)g.drawString(text,(float)(a.x+(b.x-a.x-fm.stringWidth(text))/2),(float)(a.y+height-2));
            }
        }
    }

    private void paintLiveBoards(Graphics2D g) {
        Point2D.Double a=point(0,-.055),b=point(1,-.055);
        double scan=(time*.055)%1;
        g.setColor(new Color(109,239,189,80));
        g.fillRoundRect((int)(a.x+(b.x-a.x)*scan),(int)a.y,Math.max(2,getWidth()/90),2,2,2);
    }

    private void paintPlayer(Graphics2D g,MatchEngine.Footballer f) {
        Point2D.Double p=point(f.x(),f.y());
        double radius=Math.max(4.4,Math.min(9.6,getWidth()/109.0))*(.91+.09*f.y());
        double gait=Math.sin(time*17+f.slot()*2)*Math.min(1,Math.hypot(f.vx(),f.vy())*18);
        Color shirt=f.goalkeeper()?(f.home()?new Color(248,193,81):new Color(182,139,235)):(f.home()?homeColor:awayColor);
        g.setColor(new Color(0,8,13,82));
        g.fill(new Ellipse2D.Double(p.x-radius*.75+3,p.y+radius*.5,radius*1.6,radius*.65));
        if(f==engine.owner()) {
            g.setStroke(new BasicStroke(1.25f));g.setColor(new Color(198,255,204,135));
            g.draw(new Ellipse2D.Double(p.x-radius*1.38,p.y-radius*.05,radius*2.76,radius*1.1));
        }
        g.setColor(new Color(13,24,31));
        g.setStroke(new BasicStroke((float)(radius*.35),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(p.x-radius*.26,p.y+radius*.22,p.x-radius*.4-gait*radius*.28,p.y+radius*(.95+gait*.18)));
        g.draw(new Line2D.Double(p.x+radius*.26,p.y+radius*.22,p.x+radius*.4+gait*radius*.28,p.y+radius*(.95-gait*.18)));
        g.setColor(shirt.darker());g.fill(new Ellipse2D.Double(p.x-radius*.75,p.y-radius*.75,radius*1.5,radius*1.48));
        g.setPaint(new GradientPaint((float)(p.x-radius),(float)p.y,shirt.brighter(),(float)(p.x+radius),(float)(p.y+radius),shirt.darker()));
        g.fill(new RoundRectangle2D.Double(p.x-radius*.65,p.y-radius*.85,radius*1.3,radius*1.45,radius*.65,radius*.65));
        g.setColor(new Color(0,0,0,35));g.setStroke(new BasicStroke(.7f));
        g.draw(new RoundRectangle2D.Double(p.x-radius*.65,p.y-radius*.85,radius*1.3,radius*1.45,radius*.65,radius*.65));
        // Braços e cabeça dão volume aos pequenos jogadores sem assets externos.
        g.setColor(shirt.brighter());g.setStroke(new BasicStroke((float)(radius*.4),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(p.x-radius*.63,p.y-radius*.45,p.x-radius*.94,p.y+gait*radius*.25));
        g.draw(new Line2D.Double(p.x+radius*.63,p.y-radius*.45,p.x+radius*.94,p.y-gait*radius*.25));
        g.setColor(new Color(206,160,121));
        g.fill(new Ellipse2D.Double(p.x-radius*.34,p.y-radius*1.2,radius*.68,radius*.68));
        g.setColor(new Color(42,35,32));g.fill(new Ellipse2D.Double(p.x-radius*.35,p.y-radius*1.24,radius*.70,radius*.40));
        g.setFont(new Font("SansSerif",Font.BOLD,(int)Math.max(6,radius*.9)));
        int luminance=shirt.getRed()*3+shirt.getGreen()*6+shirt.getBlue();
        g.setColor(luminance>1700?new Color(17,34,42):WHITE);
        String number=Integer.toString(f.number());FontMetrics fm=g.getFontMetrics();
        g.drawString(number,(float)(p.x-fm.stringWidth(number)/2.0),(float)(p.y+radius*.32));
    }

    private void paintBallTrail(Graphics2D g) {
        if(engine.ball().height()<.009)return;
        Point2D.Double previous=null;
        for(TrailPoint t:trail) {
            Point2D.Double p=point(t.x,t.y);p.y-=t.z*pitchH*.78;
            if(previous!=null) {
                g.setColor(new Color(231,255,224,(int)Math.max(0,50*(1-t.age/.32))));
                g.setStroke(new BasicStroke((float)Math.max(1,getWidth()/440.0),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(previous,p));
            }
            previous=p;
        }
    }

    private void paintBall(Graphics2D g) {
        MatchEngine.Ball b=engine.ball();Point2D.Double ground=point(b.x(),b.y());
        double r=Math.max(3.1,Math.min(5.3,getWidth()/210.0));
        g.setColor(new Color(1,12,14,(int)(110/(1+b.height()*5))));
        g.fill(new Ellipse2D.Double(ground.x-r+2,ground.y-r*.25+3,r*2,r*.75));
        double x=ground.x,y=ground.y-b.height()*pitchH*.78;
        r*=1+b.height()*1.2;
        g.setColor(new Color(2,13,18,150));g.fill(new Ellipse2D.Double(x-r-.6,y-r-.6,r*2+1.2,r*2+1.2));
        g.setPaint(new GradientPaint((float)(x-r),(float)(y-r),Color.WHITE,(float)(x+r),(float)(y+r),new Color(174,195,185)));
        g.fill(new Ellipse2D.Double(x-r,y-r,r*2,r*2));
        Graphics2D spin=(Graphics2D)g.create();spin.rotate(b.spin(),x,y);
        spin.setColor(new Color(37,57,58));
        spin.fill(new Ellipse2D.Double(x-r*.34,y-r*.34,r*.68,r*.68));
        spin.fill(new Ellipse2D.Double(x+r*.4,y-r*.72,r*.4,r*.45));
        spin.fill(new Ellipse2D.Double(x-r*.8,y+r*.14,r*.42,r*.45));
        spin.dispose();
    }

    private void paintOwnerLabel(Graphics2D g) {
        MatchEngine.Footballer f=engine.owner();
        if(!showNames || f==null || engine.celebrationRemaining()>0)return;
        Point2D.Double p=point(f.x(),f.y());
        String name=f.name();int space=name.lastIndexOf(' ');name=name.substring(space+1).toUpperCase();
        g.setFont(new Font("SansSerif",Font.BOLD,Math.max(9,Math.min(11,getWidth()/94))));
        FontMetrics fm=g.getFontMetrics();int labelW=fm.stringWidth(name)+14;
        double y=p.y+17;
        g.setColor(new Color(7,19,28,190));g.fillRoundRect((int)p.x-labelW/2,(int)y,labelW,19,7,7);
        g.setColor(WHITE);g.drawString(name,(float)(p.x-fm.stringWidth(name)/2.0),(float)(y+13));
    }

    private void paintCameraCaption(Graphics2D g) {
        int x=18,y=23;
        g.setFont(new Font("SansSerif",Font.BOLD,10));g.setColor(new Color(121,230,181));
        double pulse=.65+Math.sin(time*2)*.20;
        g.setComposite(AlphaComposite.SrcOver.derive((float)pulse));g.fillOval(x,y-7,5,5);g.setComposite(AlphaComposite.SrcOver);
        g.drawString("DIRETO",x+12,y-1);
        g.setFont(new Font("SansSerif",Font.PLAIN,10));g.setColor(new Color(142,164,179));
        g.drawString("CÂMARA DO ESTÁDIO",x+69,y-1);
        String stadiumName="ESTÁDIO "+engine.homeClub().city().toUpperCase();
        FontMetrics fm=g.getFontMetrics();
        if(getWidth()>650)g.drawString(stadiumName,getWidth()-fm.stringWidth(stadiumName)-18,y-1);
        int bottom=getHeight()-15;
        teamCaption(g,homeColor,engine.homeClub().shortName(),18,bottom);
        String away=engine.awayClub().shortName();fm=g.getFontMetrics();
        teamCaption(g,awayColor,away,getWidth()-fm.stringWidth(away)-36,bottom);
        g.setFont(new Font("SansSerif",Font.PLAIN,9));g.setColor(new Color(122,149,153));
        String text=engine.phaseLabel()+"   /   MICROFM";fm=g.getFontMetrics();
        g.drawString(text,(getWidth()-fm.stringWidth(text))/2,bottom);
    }
    private void teamCaption(Graphics2D g,Color color,String name,int x,int y) {
        g.setColor(color);g.fillRoundRect(x,y-8,6,9,2,2);
        g.setColor(new Color(193,211,213));g.setFont(new Font("SansSerif",Font.BOLD,10));g.drawString(name,x+13,y);
    }

    private void paintParticles(Graphics2D g) {
        Path2D.Double shape=new Path2D.Double();
        for(Particle p:particles) {
            g.setColor(new Color(p.color.getRed(),p.color.getGreen(),p.color.getBlue(),(int)(p.life*210)));
            double x=p.x*getWidth(),y=p.y*getHeight();
            double angle=time*4+p.vx*10,cos=Math.cos(angle),sin=Math.sin(angle);
            // Um único caminho é reutilizado para os confetes; não se criam
            // noventa contextos Graphics2D em cada fotograma da celebração.
            shape.reset();shape.moveTo(x-1.5*cos+3.5*sin,y-1.5*sin-3.5*cos);
            shape.lineTo(x+1.5*cos+3.5*sin,y+1.5*sin-3.5*cos);
            shape.lineTo(x+1.5*cos-3.5*sin,y+1.5*sin+3.5*cos);
            shape.lineTo(x-1.5*cos-3.5*sin,y-1.5*sin+3.5*cos);shape.closePath();
            g.fill(shape);
        }
    }

    private void paintGoalBanner(Graphics2D g) {
        double remaining=engine.celebrationRemaining();
        float alpha=(float)Math.min(1,Math.min(goalAge*5,remaining*3));
        if(alpha<=0)return;
        Graphics2D banner=(Graphics2D)g.create();banner.setComposite(AlphaComposite.SrcOver.derive(alpha));
        int w=Math.min(430,getWidth()-50),h=116,x=(getWidth()-w)/2,y=(getHeight()-h)/2;
        banner.setColor(new Color(7,19,29,225));banner.fillRoundRect(x,y,w,h,20,20);
        Color team=engine.lastGoalHome()?homeColor:awayColor;
        banner.setColor(team);banner.fillRoundRect(x,y,5,h,5,5);
        banner.setFont(new Font("SansSerif",Font.BOLD,40));banner.setColor(new Color(232,251,236));
        centered(banner,"GOOOLO!",getWidth()/2,y+48);
        banner.setFont(new Font("SansSerif",Font.BOLD,19));
        centered(banner,engine.homeGoals()+"  —  "+engine.awayGoals(),getWidth()/2,y+78);
        banner.setFont(new Font("SansSerif",Font.PLAIN,11));banner.setColor(new Color(163,191,187));
        centered(banner,(engine.lastGoalHome()?engine.homeClub():engine.awayClub()).name(),getWidth()/2,y+100);
        banner.dispose();
    }

    private void paintBreakBanner(Graphics2D g) {
        int w=Math.min(340,getWidth()-40),h=78,x=(getWidth()-w)/2,y=(getHeight()-h)/2;
        g.setColor(new Color(7,19,29,225));g.fillRoundRect(x,y,w,h,16,16);
        g.setColor(new Color(140,231,194));g.setFont(new Font("SansSerif",Font.BOLD,20));
        centered(g,engine.phaseLabel(),getWidth()/2,y+31);
        g.setColor(new Color(219,234,230));g.setFont(new Font("SansSerif",Font.BOLD,18));
        centered(g,engine.homeGoals()+"  —  "+engine.awayGoals(),getWidth()/2,y+58);
    }
    private static void centered(Graphics2D g,String text,int x,int y) { g.drawString(text,x-g.getFontMetrics().stringWidth(text)/2,y); }
    private static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,RenderingHints.VALUE_STROKE_PURE);
    }
}
