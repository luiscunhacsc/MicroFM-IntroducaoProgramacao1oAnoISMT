package microfm.ui;

import microfm.model.*;
import microfm.match.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import static microfm.ui.Theme.*;

/** Controlador da navegação. Toda a interação com o modelo ocorre no EDT. */
public final class GameWindow extends JPanel {
    private static final Path SAVE = Path.of("carreira-swing.microfm");
    private static final Path PRIMEIRA_LIGA_SAVE = Path.of("carreira-primeira-liga-2026-27.microfm");
    private Career career;
    private JPanel screen, body;
    private JLabel pageTitle, status;
    private final Map<String,JButton> navigation = new LinkedHashMap<>();
    private MatchPanel match;
    private String page="Início";
    private boolean demo, saving, loading;
    private long previousNanos;
    private final javax.swing.Timer timer;

    public GameWindow() {
        super(new BorderLayout()); setBackground(BG);
        timer=new javax.swing.Timer(16,e -> tick()); timer.setCoalesce(true);
        showWelcome();
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("SPACE"),"pauseMatch");
        getActionMap().put("pauseMatch",new AbstractAction(){public void actionPerformed(ActionEvent e){if(match!=null&&page.equals("Jogo ao vivo")) match.togglePause();}});
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("control S"),"save");
        getActionMap().put("save",new AbstractAction(){public void actionPerformed(ActionEvent e){saveCareer();}});
    }
    @Override public void addNotify(){super.addNotify();previousNanos=System.nanoTime();timer.start();}
    @Override public void removeNotify(){timer.stop();super.removeNotify();}
    private void tick(){
        long now=System.nanoTime(); double dt=Math.min(.08,(now-previousNanos)/1_000_000_000.0); previousNanos=now;
        if(match!=null&&page.equals("Jogo ao vivo")) match.tick(dt);
    }
    public static void launch(String[] args) throws Exception {
        if(args.length>0&&args[0].equals("--render")){
            Path target=Path.of(args.length>1?args[1]:"build/previews");
            SwingUtilities.invokeAndWait(()->{try{renderPreviews(target);}catch(Exception e){throw new RuntimeException(e);}}); return;
        }
        if(GraphicsEnvironment.isHeadless()) throw new IllegalStateException("A edição Swing precisa de um ambiente gráfico. Para testes: --render build/previews");
        SwingUtilities.invokeLater(()->{
            install(); GameWindow app=new GameWindow();
            JFrame frame=new JFrame("MicroFM / Touchline — O teu clube. As tuas decisões.");
            BufferedImage icon=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);Graphics2D ig=icon.createGraphics();quality(ig);
            ig.setColor(SIDE);ig.fillRoundRect(0,0,64,64,16,16);ig.setColor(GREEN);ig.setFont(bold(27));ig.drawString("MF",10,42);ig.dispose();frame.setIconImage(icon);
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.setContentPane(app); frame.setMinimumSize(new Dimension(1100,740));
            Rectangle usable=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            frame.setSize(Math.min(1440,usable.width),Math.min(920,usable.height));frame.setLocationRelativeTo(null);
            frame.addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){app.close(frame);}});
            frame.setVisible(true);
            if(Arrays.asList(args).contains("--demo")) app.startDemo();
        });
    }
    private void close(JFrame frame){
        if(saving){toast("A guardar a carreira… aguarda um instante.");return;}
        if(match!=null&&!demo&&!match.engine().finished()){
            boolean previousPause=match.pauseForDialog();
            int answer=JOptionPane.showConfirmDialog(this,"O encontro ainda não terminou. Sair e guardar a carreira antes desta jornada?","Sair do MicroFM",JOptionPane.YES_NO_OPTION);
            if(answer!=JOptionPane.YES_OPTION){match.restorePause(previousPause);return;}
        }
        if(match!=null&&!demo&&match.engine().finished())try{applyMatchResult();}catch(RuntimeException ex){error(ex);return;}
        if(career!=null&&!demo) try{CareerStore.save(career,savePath(career.league()));}catch(Exception ex){error(ex);return;}
        if(match!=null)clearMatch();timer.stop();frame.dispose();
    }
    private void showWelcome(){
        removeAll();career=null;demo=false;page="Início";navigation.clear();
        screen=null;body=null;pageTitle=null;status=null;
        JPanel outer=panel(new BorderLayout(0,22));outer.setBorder(new EmptyBorder(32,46,30,46));
        JPanel top=panel(new BorderLayout());top.add(wordmark(),BorderLayout.WEST);
        top.add(label("TOUCHLINE EDITION     /     JAVA + SWING",11,MUTED),BorderLayout.EAST);outer.add(top,BorderLayout.NORTH);
        JPanel center=panel(new BorderLayout(0,24));
        Card hero=card(new GridLayout(1,2,24,0));hero.setBorder(new EmptyBorder(30,32,28,24));
        JPanel intro=column(0);intro.add(label("O TEU BANCO. A TUA HISTÓRIA.",11,GREEN));intro.add(Box.createVerticalStrut(14));
        intro.add(heading("Noventa minutos.",36));intro.add(heading("Mil decisões.",36));intro.add(Box.createVerticalStrut(15));
        intro.add(label("Constrói uma equipa. Encontra a tua identidade.",14,MUTED));
        intro.add(label("E faz cada jornada contar.",14,MUTED));intro.add(Box.createVerticalStrut(23));
        JPanel actions=panel(new FlowLayout(FlowLayout.LEFT,0,0));
        actions.add(button("Ver demonstração  ↗",true,()->startDemo()));intro.add(actions);
        hero.add(intro);hero.add(new PitchArt(null,false));hero.setPreferredSize(new Dimension(1100,295));center.add(hero,BorderLayout.NORTH);
        Card selection=card(new BorderLayout(0,16));selection.setBorder(new EmptyBorder(22,26,22,26));
        JPanel caption = panel(new BorderLayout());
        JPanel captionText = panel(new BorderLayout(0,3));
        captionText.add(heading("Nova carreira",22),BorderLayout.NORTH);
        JLabel leagueDetails = label("",11,MUTED);
        captionText.add(leagueDetails,BorderLayout.SOUTH);
        caption.add(captionText,BorderLayout.WEST);
        League[] leagues = League.values();
        JComboBox<String> leagueChoice = combo(Arrays.stream(leagues).map(League::label).toArray(String[]::new));
        leagueChoice.setSelectedItem(League.FICTIONAL.label());
        JLabel leagueLabel = label("LIGA",11,MUTED);
        leagueLabel.setLabelFor(leagueChoice);
        leagueChoice.getAccessibleContext().setAccessibleName("Liga da nova carreira");
        JPanel leaguePicker = panel(new FlowLayout(FlowLayout.RIGHT,10,0));
        leaguePicker.add(leagueLabel);
        leaguePicker.add(leagueChoice);
        caption.add(leaguePicker,BorderLayout.EAST);
        selection.add(caption,BorderLayout.NORTH);

        CardLayout leagueCards = new CardLayout();
        JPanel clubs = panel(leagueCards);
        Map<League,ButtonGroup> clubChoices = new EnumMap<>(League.class);
        for (League league : leagues) {
            ButtonGroup choices = new ButtonGroup();
            JPanel grid = panel(new GridLayout(0,4,12,10));
            for (Club club : league.clubs()) {
                JToggleButton choice = clubChoice(club);
                choices.add(choice);
                grid.add(choice);
            }
            choices.getElements().nextElement().setSelected(true);
            clubChoices.put(league,choices);
            JScrollPane clubList = scroll(grid);
            clubList.getAccessibleContext().setAccessibleName("Clubes da " + league.label());
            clubs.add(clubList,league.name());
        }
        JButton load = button("Continuar carreira",() -> loadCareer(leagues[leagueChoice.getSelectedIndex()]));
        Runnable showSelectedLeague = () -> {
            League league = leagues[leagueChoice.getSelectedIndex()];
            leagueCards.show(clubs,league.name());
            leagueDetails.setText(league.clubs().size()+" clubes  ·  "+league.roundCount()+" jornadas");
            load.setEnabled(Files.isRegularFile(savePath(league)));
        };
        leagueChoice.addActionListener(e -> showSelectedLeague.run());
        showSelectedLeague.run();
        selection.add(clubs,BorderLayout.CENTER);
        JPanel bottom=panel(new BorderLayout(16,0));JTextField name=new JTextField("Mister",18);
        name.setBorder(BorderFactory.createCompoundBorder(new LineBorder(LINE),new EmptyBorder(10,12,10,12)));
        name.getAccessibleContext().setAccessibleName("Nome do treinador");
        JPanel nameGroup=panel(new BorderLayout(10,0));nameGroup.add(label("TREINADOR",11,MUTED),BorderLayout.WEST);nameGroup.add(name);
        bottom.add(nameGroup,BorderLayout.CENTER);
        JPanel starts=panel(new FlowLayout(FlowLayout.RIGHT,10,0));starts.add(load);
        starts.add(button("Começar carreira  →",true,() -> {
            if (loading) return;
            try {
                League league = leagues[leagueChoice.getSelectedIndex()];
                int clubId = Integer.parseInt(clubChoices.get(league).getSelection().getActionCommand());
                career = Career.create(name.getText(),clubId,System.nanoTime(),league);
                buildShell();
                navigate("Início");
            } catch (RuntimeException ex) {
                error(ex);
            }
        }));
        bottom.add(starts,BorderLayout.EAST);selection.add(bottom,BorderLayout.SOUTH);center.add(selection,BorderLayout.CENTER);
        outer.add(center,BorderLayout.CENTER);
        add(outer);revalidate();repaint();
    }
    private JToggleButton clubChoice(Club club) {
        JToggleButton choice = new JToggleButton(club.name(),PitchArt.crest(club,34)) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                quality(g);
                g.setColor(isSelected() ? new Color(0x254239) : RAISED);
                g.fillRoundRect(0,0,getWidth()-1,getHeight()-1,12,12);
                g.setColor(isSelected() || isFocusOwner() ? GREEN : LINE);
                g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,12,12);
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        choice.setFont(bold(13));
        choice.setForeground(TEXT);
        choice.setHorizontalAlignment(SwingConstants.LEFT);
        choice.setIconTextGap(10);
        choice.setActionCommand(Integer.toString(club.id()));
        choice.setToolTipText(club.name()+" · "+club.city());
        choice.setOpaque(false);
        choice.setContentAreaFilled(false);
        choice.setFocusPainted(false);
        choice.setBorder(new EmptyBorder(10,12,10,8));
        choice.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) {
                choice.scrollRectToVisible(new Rectangle(choice.getSize()));
            }
        });
        return choice;
    }
    private JComponent wordmark(){JPanel p=panel(new FlowLayout(FlowLayout.LEFT,0,0));p.add(heading("MICRO",23));p.add(label(" / ",24,GREEN));p.add(heading("FM",23));p.setMaximumSize(new Dimension(300,32));return p;}
    private void buildShell(){
        removeAll();navigation.clear();
        JPanel sidebar=new JPanel(new BorderLayout());sidebar.setBackground(SIDE);sidebar.setBorder(new EmptyBorder(28,20,20,20));sidebar.setPreferredSize(new Dimension(204,0));
        JPanel sideTop=column(0);sideTop.add(wordmark());sideTop.add(Box.createVerticalStrut(7));sideTop.add(label("T O U C H L I N E",10,MUTED));sideTop.add(Box.createVerticalStrut(32));
        JLabel crest=new JLabel(PitchArt.crest(career.club(),62));crest.setAlignmentX(LEFT_ALIGNMENT);sideTop.add(crest);sideTop.add(Box.createVerticalStrut(12));
        sideTop.add(heading(career.club().shortName(),18));sideTop.add(label(career.club().city()+"  /  Portugal",11,MUTED));sideTop.add(Box.createVerticalStrut(30));
        sideTop.add(label("O TEU CLUBE",10,MUTED));sideTop.add(Box.createVerticalStrut(12));
        String[] titles={"Início","Plantel","Tática","Treino","Mercado","Liga","Calendário","Jogo ao vivo"};
        String[] icons={"◈","≡","◇","↗","⇄","≣","▦","▶"};
        for(int i=0;i<titles.length;i++){
            String name=titles[i];JButton b=button(icons[i]+"     "+name,()->navigate(name));
            b.setHorizontalAlignment(SwingConstants.LEFT);b.setMaximumSize(new Dimension(180,43));b.setAlignmentX(LEFT_ALIGNMENT);
            b.setBorder(new EmptyBorder(11,12,11,8));navigation.put(name,b);sideTop.add(b);sideTop.add(Box.createVerticalStrut(5));
        }
        sidebar.add(scroll(sideTop),BorderLayout.CENTER);JPanel sideBottom=column(0);
        sideBottom.add(button("Guardar carreira",()->saveCareer()));sideBottom.add(Box.createVerticalStrut(10));
        sideBottom.add(label("Mister "+career.manager(),11,MUTED));sideBottom.add(label("Época "+career.season()+"  ·  "+career.league().label(),10,MUTED));sidebar.add(sideBottom,BorderLayout.SOUTH);add(sidebar,BorderLayout.WEST);
        screen=panel(new BorderLayout());JPanel header=panel(new BorderLayout());header.setBorder(new EmptyBorder(22,30,18,30));
        pageTitle=heading("Centro do treinador",20);header.add(pageTitle,BorderLayout.WEST);
        status=label("●  Tudo pronto para a próxima jornada",12,GREEN);header.add(status,BorderLayout.EAST);screen.add(header,BorderLayout.NORTH);
        body=panel(new BorderLayout());body.setBorder(new EmptyBorder(0,28,24,28));screen.add(body,BorderLayout.CENTER);add(screen);revalidate();repaint();
    }
    void navigate(String destination){
        if(saving)return;
        if(match!=null&&!destination.equals("Jogo ao vivo")){toast("Conclui ou interrompe o encontro para voltares à gestão do clube.");return;}
        if(destination.equals("Jogo ao vivo")&&match==null){startMatch();return;}
        page=destination;for(var entry:navigation.entrySet())entry.getValue().setForeground(entry.getKey().equals(page)?GREEN:MUTED);
        pageTitle.setText(switch(page){case "Início"->"Centro do treinador";case "Jogo ao vivo"->demo?"Demonstração / Matchday":"Matchday / Jornada "+(career.round()+1);default->page;});
        body.removeAll();JComponent view=switch(page){
            case "Início"->dashboard();case "Plantel"->ClubScreens.squad(this,career);
            case "Tática"->ClubScreens.tactics(this,career);case "Treino"->ClubScreens.training(this,career);
            case "Mercado"->ClubScreens.market(this,career);case "Liga"->ClubScreens.league(career);
            case "Calendário"->ClubScreens.calendar(career);case "Jogo ao vivo"->match;default->dashboard();};
        body.add(view);body.revalidate();body.repaint();
    }
    private JComponent dashboard(){
        JPanel content=column(0);
        JPanel hello=panel(new BorderLayout());JPanel greetings=column(0);
        greetings.add(heading("O jogo começa nas tuas decisões.",28));greetings.add(Box.createVerticalStrut(6));
        greetings.add(label("Bem-vindo ao banco, "+career.manager()+". Vamos preparar a próxima jornada.",13,MUTED));hello.add(greetings);
        int rounds = career.league().roundCount();
        hello.add(label("ÉPOCA "+String.format("%02d",career.season())+"    /    JORNADA "+Math.min(rounds,career.round()+1)+" DE "+rounds,11,GREEN),BorderLayout.EAST);
        block(content,hello,68);content.add(Box.createVerticalStrut(18));
        JPanel metrics=panel(new GridLayout(1,4,14,0));
        int position=1;for(Standing s:career.standings()){if(s.clubId()==career.club().id())break;position++;}
        Standing own=career.standings().stream().filter(s->s.clubId()==career.club().id()).findFirst().orElseThrow();
        metrics.add(metric("POSIÇÃO NA LIGA",position+".º",own.points()+" pontos conquistados",GREEN));
        metrics.add(metric("ORÇAMENTO",money(career.balance()),"Disponível para reforços",TEAL));
        metrics.add(metric("MORAL DA EQUIPA",career.morale()+" / 100",career.morale()>=70?"Um balneário confiante":"Cada decisão faz a diferença",ORANGE));
        int condition=(int)career.lineup().stream().mapToInt(Player::condition).average().orElse(0);
        metrics.add(metric("CONDIÇÃO DO ONZE",condition+"%",career.trained()?"Treino da semana concluído":"Treino da semana disponível",TEAL));block(content,metrics,112);
        content.add(Box.createVerticalStrut(20));
        Card fixture=card(new BorderLayout(24,0));fixture.setBorder(new EmptyBorder(24,26,24,24));
        Fixture next=career.nextFixture();
        String fixtureHeading = next == null ? "APITO FINAL  /  ÉPOCA CONCLUÍDA"
            : "PRÓXIMO ENCONTRO  /  " + career.league().label().toUpperCase(Locale.ROOT);
        JPanel fixtureText=column(0);fixtureText.add(label(fixtureHeading,11,GREEN));fixtureText.add(Box.createVerticalStrut(20));
        if(next!=null){Club home=career.clubs().get(next.homeId()),away=career.clubs().get(next.awayId());
            JPanel duel=panel(new FlowLayout(FlowLayout.LEFT,0,0));duel.add(new JLabel(PitchArt.crest(home,48)));duel.add(heading("  "+home.shortName()+"   ×   "+away.shortName()+"  ",23));duel.add(new JLabel(PitchArt.crest(away,48)));fixtureText.add(duel);
            fixtureText.add(Box.createVerticalStrut(13));fixtureText.add(label("Jornada "+(career.round()+1)+"  ·  "+home.city()+"  ·  "+(next.homeId()==career.club().id()?"Jogas em casa":"Deslocação"),13,MUTED));
        }else{fixtureText.add(heading(position==1?"O título é teu, mister.":"Uma época. Muitas histórias.",26));fixtureText.add(Box.createVerticalStrut(12));fixtureText.add(label("Terminaste em "+position+".º lugar. Prepara o próximo capítulo.",13,MUTED));}
        fixtureText.add(Box.createVerticalStrut(23));JPanel action=panel(new FlowLayout(FlowLayout.LEFT,0,0));
        action.add(button(next==null?"Iniciar nova época  →":"Entrar em campo  →",true,()->{if(next==null)act(()->career.nextSeason(),"Começa uma nova época.");else startMatch();}));
        fixtureText.add(action);fixture.add(fixtureText,BorderLayout.CENTER);PitchArt art=new PitchArt(career,false);art.setPreferredSize(new Dimension(340,225));fixture.add(art,BorderLayout.EAST);block(content,fixture,260);
        content.add(Box.createVerticalStrut(20));JPanel lower=panel(new GridLayout(1,2,18,0));
        Card news=card(new BorderLayout(0,14));news.setBorder(new EmptyBorder(22,24,20,24));news.add(heading("Dentro do balneário",18),BorderLayout.NORTH);
        JPanel newsLines=column(0);List<String> items=career.news();int shown=0;
        for(String text:items){if(shown++>=3)break;newsLines.add(label("●  "+text,12,shown==1?TEAL:MUTED));newsLines.add(Box.createVerticalStrut(13));}
        news.add(newsLines);lower.add(news);
        Card plan=card(new BorderLayout(0,15));plan.setBorder(new EmptyBorder(22,24,20,24));plan.add(heading("A tua identidade",18),BorderLayout.NORTH);
        JPanel identity=column(0);identity.add(heading(career.formation().label()+"  /  "+career.mentality().label(),21));identity.add(Box.createVerticalStrut(12));identity.add(label("Onze jogadores. Uma ideia de jogo.",13,MUTED));
        JPanel adjust=panel(new FlowLayout(FlowLayout.LEFT,0,0));adjust.add(button("Preparar a tática  ↗",()->navigate("Tática")));identity.add(Box.createVerticalStrut(15));identity.add(adjust);plan.add(identity);lower.add(plan);block(content,lower,210);
        return scroll(content);
    }
    private Card metric(String name,String value,String note,Color color){Card c=card(new BorderLayout(0,8));c.setBorder(new EmptyBorder(17,19,15,16));c.add(label(name,10,MUTED),BorderLayout.NORTH);JLabel number=heading(value,value.length()>10?22:27);number.setForeground(color);c.add(number);c.add(label(note,11,MUTED),BorderLayout.SOUTH);return c;}
    static void block(JPanel column,JComponent component,int height){component.setAlignmentX(LEFT_ALIGNMENT);component.setMaximumSize(new Dimension(Integer.MAX_VALUE,height));component.setPreferredSize(new Dimension(800,height));column.add(component);}
    void act(Runnable action,String success){try{action.run();navigate(page);toast(success);}catch(RuntimeException ex){error(ex);}}
    void error(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage()==null?ex.toString():ex.getMessage(),"Não foi possível concluir",JOptionPane.WARNING_MESSAGE);}
    void toast(String text){if(status!=null){status.setText("●  "+text);status.setToolTipText(text);}}
    private void startDemo(){if(loading)return;career=Career.create("Visitante",0,20261126L);demo=true;buildShell();startMatch();}
    private void startMatch(){
        if(career.nextFixture()==null){navigate("Início");toast("A época terminou. Inicia uma nova época.");return;}
        match=new MatchPanel(this,career,demo);navigate("Jogo ao vivo");toast(demo?"Demonstração automática":"Espaço para pausar  ·  Decide a partir do banco");
    }
    void interruptMatch() {
        if (match == null) return;
        boolean previousPause = match.pauseForDialog();
        String message = demo ? "Queres interromper a demonstração?"
            : "Queres descartar o resultado e as alterações feitas durante o encontro?";
        String[] options = {"Continuar jogo", "Interromper jogo"};
        int answer = JOptionPane.showOptionDialog(this, message, "Interromper jogo", JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (answer != 1) {
            match.restorePause(previousPause);
            return;
        }

        clearMatch();
        if (demo) {
            showWelcome();
            return;
        }
        navigate("Início");
        toast("Encontro interrompido. Podes voltar a jogá-lo desde o início.");
    }
    void finishMatch(){
        if(match==null)return;
        MatchEngine engine=match.engine();
        if(!engine.finished())return;
        if(demo){clearMatch();showWelcome();return;}
        try{applyMatchResult();navigate("Início");saveCareer();}
        catch(RuntimeException ex){error(ex);}
    }
    private void applyMatchResult(){
        MatchEngine engine=match.engine();
        career.finishMatch(engine.homeGoals(),engine.awayGoals(),engine.scorerGoals(),match.participants());
        career.setFormation(match.formation());career.setMentality(match.mentality());clearMatch();
    }
    private void clearMatch() {
        match.dispose();
        match = null;
    }
    private void saveCareer(){
        if(career==null||demo||saving)return;
        if(match!=null){toast("A carreira será guardada depois do encontro.");return;}
        saving=true;setTreeEnabled(screen,false);toast("A guardar a carreira…");
        new SwingWorker<Void,Void>(){
            protected Void doInBackground() throws Exception {CareerStore.save(career,savePath(career.league()));return null;}
            protected void done(){saving=false;setTreeEnabled(screen,true);navigate(page);try{get();toast("Carreira guardada. Podes regressar quando quiseres.");}catch(Exception ex){error(new Exception("Não foi possível guardar: "+ex.getCause()));}}
        }.execute();
    }
    private void loadCareer(League league) {
        if (loading) return;
        loading = true;
        setTreeEnabled(this,false);
        new SwingWorker<Career,Void>() {
            protected Career doInBackground() throws Exception {
                Career loaded = CareerStore.load(savePath(league));
                if (loaded.league() != league) throw new IllegalArgumentException("A gravação pertence a outra liga.");
                return loaded;
            }
            protected void done() {
                loading = false;
                setTreeEnabled(GameWindow.this,true);
                try {
                    career = get();
                    buildShell();
                    navigate("Início");
                    toast("Bem-vindo de volta, mister.");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    error(new Exception("Não foi possível abrir a carreira: "+cause.getMessage()));
                }
            }
        }.execute();
    }
    private static Path savePath(League league) {
        return switch (league) {
            case FICTIONAL -> SAVE;
            case PRIMEIRA_LIGA_2026_27 -> PRIMEIRA_LIGA_SAVE;
        };
    }
    private static void setTreeEnabled(Component c,boolean enabled){c.setEnabled(enabled);if(c instanceof Container p)for(Component child:p.getComponents())setTreeEnabled(child,enabled);}
    public static void renderPreviews(Path directory) throws Exception {
        install();Files.createDirectories(directory);GameWindow app=new GameWindow();app.setSize(1440,920);
        snapshot(app,directory.resolve("01-boas-vindas.png"));app.career=Career.create("Inês Costa",0,20261126L);app.buildShell();
        String[] pages={"Início","Plantel","Tática","Treino","Mercado","Liga","Calendário"};int i=2;
        for(String p:pages){app.navigate(p);snapshot(app,directory.resolve(String.format("%02d-%s.png",i++,p)));}
        app.startMatch();for(int t=0;t<2200;t++)app.match.engine().update(.02);
        app.match.tick(0);snapshot(app,directory.resolve("09-jogo.png"));app.timer.stop();
    }
    private static void snapshot(JComponent component,Path path) throws Exception {
        layoutTree(component);BufferedImage image=new BufferedImage(component.getWidth(),component.getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();component.printAll(g);g.dispose();ImageIO.write(image,"png",path.toFile());
    }
    private static void layoutTree(Component c){if(c instanceof Container p){p.doLayout();for(Component child:p.getComponents())layoutTree(child);}}
}
