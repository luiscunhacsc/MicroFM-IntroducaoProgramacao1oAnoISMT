package microfm.ui;

import microfm.model.*;
import microfm.match.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;
import static microfm.ui.Theme.*;

/** Controla um encontro sem ciclos bloqueantes: o Timer pertence à janela. */
final class MatchPanel extends JPanel {
    private final GameWindow app;
    private final Career career;
    private final MatchEngine engine;
    private final MatchCanvas canvas;
    private final boolean demo;
    private final JLabel score=heading("0  :  0",38),clock=label("00:00",15,GREEN),phase=label("1.ª PARTE",10,MUTED);
    private final JLabel possession=label("50%    POSSE DE BOLA    50%",11,MUTED),shots=label("0    REMATES    0",11,MUTED);
    private final JLabel latest=label("O árbitro apita. Começa a tua história.",12,TEXT),subCount=label("0 / 5 substituições",11,MUTED);
    private final JButton pause,advance;
    private final JComboBox<String> speed,formation,mentality;
    private final JPanel commentary=column(0);
    private final Set<Integer> participants=new LinkedHashSet<>(),departed=new HashSet<>();
    private final SoundFx sound=new SoundFx();
    private boolean paused,wasHalf,wasFull;
    private int seenEvents=-1,seenGoals;
    private double halfWait,refresh;
    private final JComponent possessionBar;

    MatchPanel(GameWindow app,Career career,boolean demo){
        super(new BorderLayout(0,14));setOpaque(false);this.app=app;this.career=career;this.demo=demo;
        engine=new MatchEngine(career,System.nanoTime());canvas=new MatchCanvas(engine);
        for(Player p:career.lineup())participants.add(p.id());
        Card scoreboard=card(new BorderLayout(15,0));scoreboard.setBorder(new EmptyBorder(15,26,15,26));
        scoreboard.add(team(engine.homeClub(),true),BorderLayout.WEST);scoreboard.add(team(engine.awayClub(),false),BorderLayout.EAST);
        JPanel center=column(0);score.setAlignmentX(CENTER_ALIGNMENT);clock.setAlignmentX(CENTER_ALIGNMENT);phase.setAlignmentX(CENTER_ALIGNMENT);
        center.add(score);center.add(clock);center.add(Box.createVerticalStrut(3));center.add(phase);scoreboard.add(center);add(scoreboard,BorderLayout.NORTH);
        JPanel middle=panel(new BorderLayout(14,0));Card stadium=card(new BorderLayout());stadium.setBorder(new EmptyBorder(1,1,1,1));stadium.add(canvas);middle.add(stadium);
        JPanel bench=column(0);bench.setPreferredSize(new Dimension(242,0));
        Card tactical=card(new BorderLayout(0,14));tactical.setBorder(new EmptyBorder(19,19,19,19));JPanel fields=column(0);
        fields.add(heading(demo?"Da bancada":"Do teu banco",18));fields.add(Box.createVerticalStrut(16));fields.add(label("MENTALIDADE",10,MUTED));fields.add(Box.createVerticalStrut(6));
        mentality=combo(Arrays.stream(Mentality.values()).map(Mentality::label).toArray(String[]::new));mentality.setSelectedIndex(career.mentality().ordinal());fields.add(mentality);
        mentality.addActionListener(e->{if(!engine.finished())engine.setMentality(mentality());});
        fields.add(Box.createVerticalStrut(14));fields.add(label("FORMAÇÃO",10,MUTED));fields.add(Box.createVerticalStrut(6));
        formation=combo(Arrays.stream(Formation.values()).map(Formation::label).toArray(String[]::new));formation.setSelectedIndex(career.formation().ordinal());fields.add(formation);
        formation.addActionListener(e->{if(!engine.finished())engine.setFormation(formation());});
        fields.add(Box.createVerticalStrut(17));fields.add(subCount);fields.add(Box.createVerticalStrut(9));
        JButton sub=button("Substituição  ⇄",()->substitute());sub.setAlignmentX(LEFT_ALIGNMENT);fields.add(sub);tactical.add(fields);
        if(demo){mentality.setEnabled(false);formation.setEnabled(false);sub.setEnabled(false);}
        GameWindow.block(bench,tactical,289);bench.add(Box.createVerticalStrut(13));
        Card stats=card(new BorderLayout(0,9));stats.setBorder(new EmptyBorder(17,18,17,18));JPanel statsText=column(0);statsText.add(possession);statsText.add(Box.createVerticalStrut(10));
        possessionBar=new JComponent(){
            {setPreferredSize(new Dimension(170,5));setMaximumSize(new Dimension(Integer.MAX_VALUE,5));}
            @Override protected void paintComponent(Graphics gr){Graphics2D g=(Graphics2D)gr.create();quality(g);g.setColor(new Color(engine.awayClub().rgb()));g.fillRoundRect(0,0,getWidth(),5,5,5);g.setColor(new Color(engine.homeClub().rgb()));g.fillRoundRect(0,0,(int)(getWidth()*engine.homePossession()),5,5,5);g.dispose();}
        };
        statsText.add(possessionBar);statsText.add(Box.createVerticalStrut(12));statsText.add(shots);stats.add(statsText);GameWindow.block(bench,stats,100);bench.add(Box.createVerticalStrut(13));
        Card feed=card(new BorderLayout(0,10));feed.setBorder(new EmptyBorder(17,18,14,18));feed.add(label("A HISTÓRIA DO JOGO",10,GREEN),BorderLayout.NORTH);feed.add(scroll(commentary));
        feed.setAlignmentX(LEFT_ALIGNMENT);feed.setMaximumSize(new Dimension(Integer.MAX_VALUE,Integer.MAX_VALUE));bench.add(feed);middle.add(bench,BorderLayout.EAST);add(middle);
        Card controls=card(new BorderLayout(12,12));controls.setBorder(new EmptyBorder(13,18,13,18));
        JPanel buttons=panel(new FlowLayout(FlowLayout.LEFT,8,0));
        pause=button("Ⅱ  Pausar",()->togglePause());buttons.add(pause);
        speed=combo(new String[]{"1×  Normal","2×  Rápido","4×  Muito rápido","8×  Expresso"});speed.setToolTipText("Velocidade da simulação; a animação mantém os quadros por segundo.");buttons.add(speed);
        JButton names=button("Nomes: sim",()->{});
        names.addActionListener(e->{boolean hide=!Boolean.TRUE.equals(canvas.getClientProperty("hideNames"));canvas.putClientProperty("hideNames",hide);canvas.setShowNames(!hide);names.setText(hide?"Nomes: não":"Nomes: sim");});buttons.add(names);
        JButton audio=button("Som: não",()->{});audio.addActionListener(e->{sound.setEnabled(!sound.enabled());audio.setText(sound.enabled()?"Som: sim":"Som: não");});buttons.add(audio);
        advance=button("Encontro em curso",true,()->{if(engine.finished())app.finishMatch();else if(engine.halftime()){engine.resumeHalf();paused=false;pause.setText("Ⅱ  Pausar");sound.whistle();}});advance.setEnabled(false);
        controls.add(buttons,BorderLayout.WEST);controls.add(advance,BorderLayout.EAST);controls.add(latest,BorderLayout.SOUTH);add(controls,BorderLayout.SOUTH);
        updateReadout();
    }
    private JComponent team(Club club,boolean home){
        JPanel p=panel(new BorderLayout(12,0));JPanel info=column(0);JLabel name=heading(club.shortName(),23);JLabel descriptor=label(home?"CASA  /  "+club.city():"FORA  /  "+club.city(),10,MUTED);
        name.setAlignmentX(home?LEFT_ALIGNMENT:RIGHT_ALIGNMENT);descriptor.setAlignmentX(home?LEFT_ALIGNMENT:RIGHT_ALIGNMENT);info.add(name);info.add(Box.createVerticalStrut(5));info.add(descriptor);
        JLabel crest=new JLabel(PitchArt.crest(club,56));p.add(crest,home?BorderLayout.WEST:BorderLayout.EAST);p.add(info);p.setPreferredSize(new Dimension(215,70));return p;
    }
    MatchEngine engine(){return engine;}
    Set<Integer> participants(){return Set.copyOf(participants);}
    Formation formation(){return Formation.values()[formation.getSelectedIndex()];}
    Mentality mentality(){return Mentality.values()[mentality.getSelectedIndex()];}
    void dispose(){sound.close();canvas.dispose();}
    boolean pauseForDialog(){boolean previous=paused;paused=true;return previous;}
    void restorePause(boolean previous){paused=previous;pause.setText(paused?"▶  Retomar":"Ⅱ  Pausar");}
    void togglePause(){
        if(engine.finished()||engine.halftime())return;paused=!paused;pause.setText(paused?"▶  Retomar":"Ⅱ  Pausar");phase.setText(paused?"EM PAUSA":engine.phaseLabel());
    }
    void tick(double dt){
        if(!paused&&!engine.finished()&&!engine.halftime())engine.update(dt*(1<<speed.getSelectedIndex()));
        canvas.updateVisuals(paused?0:dt);refresh+=dt;
        if(seenGoals!=engine.goalSerial()){seenGoals=engine.goalSerial();sound.goal();}
        if(engine.halftime()&&!wasHalf){wasHalf=true;advance.setEnabled(true);advance.setText("Jogar a 2.ª parte  →");pause.setEnabled(false);sound.whistle();}
        if(wasHalf&&!engine.halftime()&&!engine.finished()){advance.setEnabled(false);advance.setText("Encontro em curso");pause.setEnabled(true);}
        if(demo&&engine.halftime()){halfWait+=dt;if(halfWait>3)engine.resumeHalf();}
        if(engine.finished()&&!wasFull){wasFull=true;advance.setText(demo?"Voltar ao início  →":"Concluir jornada  →");advance.setEnabled(true);pause.setEnabled(false);formation.setEnabled(false);mentality.setEnabled(false);sound.whistle();}
        if(refresh>.1||dt==0){refresh=0;updateReadout();}
    }
    private void updateReadout(){
        score.setText(engine.homeGoals()+"  :  "+engine.awayGoals());int m=(int)engine.minute(),s=(int)((engine.minute()-m)*60);
        clock.setText(String.format("%02d:%02d",m,s));phase.setText(paused?"EM PAUSA":engine.phaseLabel());
        int possessionHome=(int)Math.round(engine.homePossession()*100);possession.setText(possessionHome+"%    POSSE DE BOLA    "+(100-possessionHome)+"%");
        shots.setText(engine.homeShots()+"    REMATES    "+engine.awayShots());possessionBar.repaint();subCount.setText(engine.substitutions()+" / 5 substituições");
        List<MatchEvent> events=engine.events();if(events.size()!=seenEvents){seenEvents=events.size();commentary.removeAll();int shown=0;
            for(int i=events.size()-1;i>=0&&shown<6;i--){MatchEvent event=events.get(i);if(event.kind().equals("pass"))continue;
                Color color=event.kind().equals("goal")?GREEN:event.kind().equals("shot")?ORANGE:MUTED;
                JLabel l=label("<html><b>"+(int)event.minute()+"′</b>  "+event.text()+"</html>",12,color);l.setMaximumSize(new Dimension(190,60));commentary.add(l);commentary.add(Box.createVerticalStrut(12));shown++;
            }
            commentary.revalidate();commentary.repaint();if(!events.isEmpty()){MatchEvent e=events.get(events.size()-1);latest.setText((int)e.minute()+"′   "+e.text());}
        }
    }
    private void substitute(){
        if(engine.finished()||demo)return;
        Set<Integer> onPitch=new HashSet<>();List<String> starters=new ArrayList<>();
        for(int i=0;i<11;i++){Player p=engine.playerAtSlot(i);onPitch.add(p.id());starters.add((i+1)+" / "+p.position().label()+" / "+p.name());}
        List<Player> bench=career.squad().stream().filter(p->p.available()&&!onPitch.contains(p.id())&&!departed.contains(p.id())).toList();
        if(bench.isEmpty()){app.toast("Não tens suplentes disponíveis.");return;}
        boolean before=paused;paused=true;
        JComboBox<String> out=combo(starters.toArray(String[]::new)),in=combo(bench.stream().map(p->p.position().label()+" / "+p.name()+" / "+p.condition()+"%").toArray(String[]::new));
        JPanel form=panel(new GridLayout(4,1,0,10));form.add(label("SAI",11,MUTED));form.add(out);form.add(label("ENTRA",11,MUTED));form.add(in);
        if(JOptionPane.showConfirmDialog(app,form,"Substituição / "+engine.substitutions()+" de 5 utilizadas",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION){
            try{int slot=out.getSelectedIndex();Player leaving=engine.playerAtSlot(slot),entering=bench.get(in.getSelectedIndex());engine.substitute(slot,entering);departed.add(leaving.id());participants.add(entering.id());}
            catch(RuntimeException ex){app.error(ex);}
        }
        paused=before;updateReadout();
    }
}
