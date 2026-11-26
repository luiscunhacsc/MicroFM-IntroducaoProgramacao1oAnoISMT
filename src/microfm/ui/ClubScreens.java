package microfm.ui;

import microfm.model.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;
import static microfm.ui.Theme.*;

/** Ecrãs de gestão: apresentam o modelo e delegam as regras em Career. */
final class ClubScreens {
    private ClubScreens() { }
    private static JPanel frame(String title,String subtitle){
        JPanel p=panel(new BorderLayout(0,20));JPanel head=column(0);
        head.add(heading(title,28));head.add(Box.createVerticalStrut(7));head.add(label(subtitle,13,MUTED));p.add(head,BorderLayout.NORTH);return p;
    }
    private static JPanel tableCard(JTable table){
        Card p=card(new BorderLayout());p.setBorder(new EmptyBorder(12,12,12,12));p.add(scroll(table));return p;
    }
    static JComponent squad(GameWindow app,Career c){
        JPanel page=frame("Um grupo. Uma ambição.","Seleciona os teus titulares e acompanha a evolução de cada jogador.");
        Set<Integer> starters=new HashSet<>();for(Player p:c.lineup())starters.add(p.id());
        Object[][] rows=new Object[c.squad().size()][8];int r=0;
        for(Player p:c.squad())rows[r++]=new Object[]{starters.contains(p.id())?"●  ONZE":"BANCO",p.name(),p.position().label(),p.age(),p.quality(),p.condition()+"%",p.goals(),p.injuredRounds()>0?"Lesionado / "+p.injuredRounds()+" j.":"Disponível"};
        JTable t=table(new String[]{"ESTADO","JOGADOR","POS.","IDADE","QUAL.","CONDIÇÃO","GOLOS","DISPONIBILIDADE"},rows);
        t.getColumnModel().getColumn(1).setPreferredWidth(210);t.getColumnModel().getColumn(7).setPreferredWidth(155);
        page.add(tableCard(t));JPanel foot=panel(new BorderLayout());foot.add(label(c.squad().size()+" jogadores  ·  "+c.formation().label()+"  ·  Qualidade de 1 a 99",12,MUTED),BorderLayout.WEST);
        JPanel actions=panel(new FlowLayout(FlowLayout.RIGHT,10,0));
        actions.add(button("Escolher melhor onze",()->app.act(c::autoSelect,"Onze escolhido por posição, qualidade e condição.")));
        actions.add(button("Trocar titular  ⇄",true,()->swapDialog(app,c)));foot.add(actions,BorderLayout.EAST);page.add(foot,BorderLayout.SOUTH);return page;
    }
    private static void swapDialog(GameWindow app,Career c){
        List<Player> reserves=c.squad().stream().filter(p->c.lineup().stream().noneMatch(s->s.id()==p.id())&&p.available()).toList();
        if(reserves.isEmpty()){app.toast("Não há suplentes disponíveis.");return;}
        JComboBox<String> out=combo(c.lineup().stream().map(p->p.position().label()+" / "+p.name()).toArray(String[]::new));
        JComboBox<String> in=combo(reserves.stream().map(p->p.position().label()+" / "+p.name()+" / "+p.quality()).toArray(String[]::new));
        JPanel form=panel(new GridLayout(4,1,0,10));form.add(label("TITULAR A RETIRAR",11,MUTED));form.add(out);form.add(label("JOGADOR A ENTRAR",11,MUTED));form.add(in);
        if(JOptionPane.showConfirmDialog(app,form,"Escolher o onze",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION)
            app.act(()->c.swapStarter(out.getSelectedIndex(),reserves.get(in.getSelectedIndex()).id()),"O onze foi atualizado.");
    }
    static JComponent tactics(GameWindow app,Career c){
        JPanel page=frame("Dá uma identidade à equipa.","A formação distribui os jogadores; a mentalidade define o risco que assumes.");
        JPanel grid=panel(new GridLayout(1,2,22,0));Card pitch=card(new BorderLayout());pitch.setBorder(new EmptyBorder(14,14,14,14));
        pitch.add(label("ONZE INICIAL  /  "+c.formation().label(),11,GREEN),BorderLayout.NORTH);pitch.add(new PitchArt(c,true));grid.add(pitch);
        JPanel controls=column(0);Card form=card(new BorderLayout(0,16));form.setBorder(new EmptyBorder(24,24,24,24));
        JPanel fields=column(0);fields.add(heading("O desenho do jogo",21));fields.add(Box.createVerticalStrut(20));fields.add(label("FORMAÇÃO",11,MUTED));fields.add(Box.createVerticalStrut(8));
        JComboBox<String> formation=combo(Arrays.stream(Formation.values()).map(Formation::label).toArray(String[]::new));formation.setSelectedIndex(c.formation().ordinal());fields.add(formation);
        fields.add(Box.createVerticalStrut(21));fields.add(label("MENTALIDADE",11,MUTED));fields.add(Box.createVerticalStrut(8));
        JComboBox<String> mentality=combo(Arrays.stream(Mentality.values()).map(Mentality::label).toArray(String[]::new));mentality.setSelectedIndex(c.mentality().ordinal());fields.add(mentality);
        fields.add(Box.createVerticalStrut(22));fields.add(label("Defensiva: contenção e segurança.",13,MUTED));fields.add(Box.createVerticalStrut(7));fields.add(label("Equilibrada: controlo sem perder profundidade.",13,MUTED));fields.add(Box.createVerticalStrut(7));fields.add(label("Ofensiva: mais pressão, mais exposição.",13,MUTED));
        form.add(fields);form.add(button("Aplicar plano de jogo  →",true,()->app.act(()->{c.setFormation(Formation.values()[formation.getSelectedIndex()]);c.setMentality(Mentality.values()[mentality.getSelectedIndex()]);},"Plano aplicado. O melhor onze foi ajustado à formação.")),BorderLayout.SOUTH);
        GameWindow.block(controls,form,390);controls.add(Box.createVerticalStrut(18));
        Card hint=card(new BorderLayout(0,12));hint.setBorder(new EmptyBorder(22,24,22,24));hint.add(heading("Lê o jogo. Muda o jogo.",17),BorderLayout.NORTH);
        hint.add(label("<html>Durante o encontro podes mudar de sistema,<br>alterar a mentalidade e utilizar até cinco suplentes.</html>",13,MUTED));GameWindow.block(controls,hint,130);grid.add(controls);page.add(grid);return page;
    }
    static JComponent training(GameWindow app,Career c){
        JPanel page=frame("O trabalho invisível ganha jogos.","Uma sessão por jornada. Escolhe o equilíbrio entre qualidade, condição e moral.");
        JPanel content=column(0);Card state=card(new BorderLayout(18,0));state.setBorder(new EmptyBorder(21,24,21,24));
        JLabel stateLabel=heading(c.trained()?"✓  A sessão desta semana está concluída.":"●  O relvado de treinos está à tua espera.",20);stateLabel.setForeground(c.trained()?TEAL:GREEN);state.add(stateLabel);
        int rounds = c.league().roundCount();
        state.add(label("JORNADA "+Math.min(rounds,c.round()+1)+" / "+rounds,11,MUTED),BorderLayout.EAST);GameWindow.block(content,state,78);content.add(Box.createVerticalStrut(22));
        JPanel sessions=panel(new GridLayout(1,3,18,0));String[] names={"Recarregar energias","Afinar o toque","Subir a intensidade"};
        String[] descriptions={"<html>Recupera a condição física<br>e reforça a moral do grupo.<br><br>Ideal depois de um jogo exigente.</html>","<html>Trabalha a qualidade técnica<br>com um esforço moderado.<br><br>O detalhe faz a diferença.</html>","<html>Melhora mais a qualidade,<br>com maior desgaste físico.<br><br>Exige uma equipa em boa condição.</html>"};
        String[] symbols={"↺","◇","↗"};Color[] colors={TEAL,GREEN,ORANGE};
        for(int i=0;i<3;i++){final Training training=Training.values()[i];Card card=card(new BorderLayout(0,22));card.setBorder(new EmptyBorder(25,24,24,24));
            JPanel text=column(0);text.add(label(symbols[i],42,colors[i]));text.add(Box.createVerticalStrut(20));text.add(heading(names[i],20));text.add(Box.createVerticalStrut(16));text.add(label(descriptions[i],14,MUTED));card.add(text);
            JButton train=button(c.trained()?"Sessão concluída":"Escolher sessão  →",i==1,()->app.act(()->c.train(training),"Treino concluído. A equipa está a evoluir."));train.setEnabled(!c.trained()&&c.round()<rounds);card.add(train,BorderLayout.SOUTH);sessions.add(card);}
        GameWindow.block(content,sessions,340);content.add(Box.createVerticalStrut(24));
        content.add(label("A condição e a qualidade influenciam a seleção do onze e a força da equipa no jogo.",13,MUTED));page.add(scroll(content));return page;
    }
    static JComponent market(GameWindow app,Career c){
        JPanel page=frame("O próximo talento está aqui.","Investe na equipa. Cada contratação sai do orçamento disponível do clube.");
        Object[][] rows=new Object[c.market().size()][7];int i=0;
        for(MarketOffer offer:c.market()){Player p=offer.player();rows[i++]=new Object[]{p.name(),p.position().label(),p.age(),p.quality(),p.condition()+"%",money(offer.price()),offer.signed()?"Contratado":"Disponível"};}
        JTable table=table(new String[]{"JOGADOR","POS.","IDADE","QUALIDADE","CONDIÇÃO","PREÇO","ESTADO"},rows);table.getColumnModel().getColumn(0).setPreferredWidth(210);table.getColumnModel().getColumn(5).setPreferredWidth(150);
        page.add(tableCard(table));JPanel foot=panel(new BorderLayout());JLabel budget=heading("Disponível  /  "+money(c.balance()),19);budget.setForeground(GREEN);foot.add(budget);
        foot.add(button("Contratar jogador selecionado  +",true,()->{int row=table.getSelectedRow();if(row<0){app.toast("Seleciona primeiro um jogador na lista.");return;}app.act(()->c.sign(c.market().get(table.convertRowIndexToModel(row)).player().id()),"Reforço contratado. Já está disponível no plantel.");}),BorderLayout.EAST);page.add(foot,BorderLayout.SOUTH);return page;
    }
    static JComponent league(Career c){
        JPanel page=frame(c.league().label(),c.clubs().size()+" clubes. Desempate por diferença de golos, depois golos marcados.");
        Object[][] rows=new Object[c.clubs().size()][10];int i=0;
        for(Standing s:c.standings()){Club club=c.clubs().get(s.clubId());rows[i]=new Object[]{(i+1)+".º",(club.id()==c.club().id()?"●  ":"")+club.name(),s.played(),s.wins(),s.draws(),s.losses(),s.goalsFor(),s.goalsAgainst(),String.format("%+d",s.goalsFor()-s.goalsAgainst()),s.points()};i++;}
        JTable table=table(new String[]{"POS.","CLUBE","J","V","E","D","GM","GS","DG","PTS"},rows);table.setRowHeight(57);table.getColumnModel().getColumn(1).setPreferredWidth(300);page.add(tableCard(table));
        page.add(label("Vitória 3 pontos  ·  Empate 1 ponto  ·  O símbolo ● identifica o teu clube.",12,MUTED),BorderLayout.SOUTH);return page;
    }
    static JComponent calendar(Career c){
        JPanel page=frame("Cada jornada, uma nova oportunidade.","O calendário completo da liga, com todos os resultados e os próximos desafios.");
        Object[][] rows=new Object[c.fixtures().size()][5];int i=0;
        for(Fixture f:c.fixtures()){Club home=c.clubs().get(f.homeId()),away=c.clubs().get(f.awayId());rows[i++]=new Object[]{String.format("%02d",f.round()+1),home.name(),f.played()?f.homeGoals()+"  –  "+f.awayGoals():"×",away.name(),home.id()==c.club().id()||away.id()==c.club().id()?"●  O TEU JOGO":f.played()?"Terminado":"Por disputar"};}
        JTable table=table(new String[]{"JORNADA","CASA","RESULTADO","FORA","ESTADO"},rows);table.getColumnModel().getColumn(1).setPreferredWidth(245);table.getColumnModel().getColumn(3).setPreferredWidth(245);table.getColumnModel().getColumn(4).setPreferredWidth(145);page.add(tableCard(table));return page;
    }
}
