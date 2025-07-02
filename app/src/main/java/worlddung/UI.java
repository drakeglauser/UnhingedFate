package worlddung;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import com.google.gson.Gson;



/**
 * UI popup for game management: Stats, Inventory, Skills/Spells
 */
public class UI extends JDialog {
    public static class Stats {
        public int health;
        public int strength;
        public int dexterity;
        public int intelligence;
        public int wisdom;
        public int charisma;
        public int movementSpeed;
    }

    public static Stats stats = new Stats();

    public static class InventoryItem {
        public String name;
        public Map<String, Object> properties;
        @Override
        public String toString() { return name; }
    }

    public static List<InventoryItem> inventory = new ArrayList<>();

    public static class Spell {
        public String name;
        public int level;
        public int radiusOfEffect;
        public int damage;
        public int range;
        public String effects;
        public String buffs;
        public double castTime;
        public int cost;
        @Override
        public String toString() { return name; }
    }

    public static List<Spell> spells = new ArrayList<>();


    public static class Achievemnt {
        public String name;
        public String description;
        public int points;

        @Override
        public String toString() {
            return name + " (" + points + " pts)";
        }
    }
    public static List<Achievemnt> achievements = new ArrayList<>();


    private static final Gson gson = new Gson();

    static {
        loadJSON("data/stats.json", "stats");
        loadJSON("data/inventory.json", "inventory");
        loadJSON("data/spells.json", "spells");
        loadJSON("data/achievements.json", "achievements");
    }

    private static void loadJSON(String resourcePath, String type) {
        try (InputStream in = UI.class
                .getClassLoader()
                .getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Resource not found on classpath: " + resourcePath);
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);

            switch (type) {
                case "stats":
                    stats = gson.fromJson(json, Stats.class);
                    break;

                case "inventory": {
                    // note: fully-qualified java.lang.reflect.Type
                    java.lang.reflect.Type invListType =
                    new com.google.gson.reflect.TypeToken<java.util.List<InventoryItem>>(){}.getType();
                    inventory = gson.fromJson(json, invListType);
                    break;
                }

                case "spells": {
                    java.lang.reflect.Type spellListType =
                    new com.google.gson.reflect.TypeToken<java.util.List<Spell>>(){}.getType();
                    spells = gson.fromJson(json, spellListType);
                    break;
                }

                case "achievements": {
                    java.lang.reflect.Type achievementListType =
                    new com.google.gson.reflect.TypeToken<java.util.List<Achievemnt>>(){}.getType();
                    achievements = gson.fromJson(json, achievementListType);
                    break;
                }

                default:
                    throw new IllegalArgumentException("Unknown JSON type: " + type);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    public UI(JFrame owner) {
        super(owner, "Game UI", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(600, 400);
        setLocationRelativeTo(owner);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Stats", createStatsPanel());
        tabbedPane.addTab("Inventory", createInventoryPanel());
        tabbedPane.addTab("Skills/Spells", createSpellsPanel());
        tabbedPane.addTab("Achievements", createAchievementsPanel());

        setContentPane(tabbedPane);
    }

    private JPanel createStatsPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(new JLabel("Constitution:")); panel.add(new JLabel(String.valueOf(stats.health)));
        panel.add(new JLabel("Strength:")); panel.add(new JLabel(String.valueOf(stats.strength)));
        panel.add(new JLabel("Dexterity:")); panel.add(new JLabel(String.valueOf(stats.dexterity)));
        panel.add(new JLabel("Intelligence:")); panel.add(new JLabel(String.valueOf(stats.intelligence)));
        panel.add(new JLabel("Wisdom:")); panel.add(new JLabel(String.valueOf(stats.wisdom)));
        panel.add(new JLabel("Charisma:")); panel.add(new JLabel(String.valueOf(stats.charisma)));
        panel.add(new JLabel("Movement Speed:")); panel.add(new JLabel(String.valueOf(stats.movementSpeed)));
        return panel;
    }

    private JPanel createInventoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        DefaultListModel<InventoryItem> listModel = new DefaultListModel<>();
        inventory.forEach(listModel::addElement);
        JList<InventoryItem> itemList = new JList<>(listModel);
        JScrollPane listScroll = new JScrollPane(itemList);
        listScroll.setPreferredSize(new Dimension(150, 0));
        panel.add(listScroll, BorderLayout.WEST);

        JPanel detailPanel = new JPanel(new GridLayout(0, 2, 5, 5));
        detailPanel.setBorder(BorderFactory.createTitledBorder("Item Details"));
        panel.add(detailPanel, BorderLayout.CENTER);

        itemList.addListSelectionListener(e -> {
            detailPanel.removeAll();
            InventoryItem sel = itemList.getSelectedValue();
            if (sel != null && sel.properties != null) {
                sel.properties.forEach((k, v) -> {
                    detailPanel.add(new JLabel(k + ":"));
                    detailPanel.add(new JLabel(v.toString()));
                });
            }
            detailPanel.revalidate(); detailPanel.repaint();
        });
        return panel;
    }

    private JPanel createSpellsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        DefaultListModel<Spell> listModel = new DefaultListModel<>();
        spells.forEach(listModel::addElement);
        JList<Spell> spellList = new JList<>(listModel);
        JScrollPane listScroll = new JScrollPane(spellList);
        listScroll.setPreferredSize(new Dimension(150, 0));
        panel.add(listScroll, BorderLayout.WEST);

        JPanel detailPanel = new JPanel(new GridLayout(0, 2, 5, 5));
        detailPanel.setBorder(BorderFactory.createTitledBorder("Spell Details"));
        panel.add(detailPanel, BorderLayout.CENTER);

        spellList.addListSelectionListener(e -> {
            detailPanel.removeAll();
            Spell sel = spellList.getSelectedValue();
            if (sel != null) {
                detailPanel.add(new JLabel("Name:")); detailPanel.add(new JLabel(sel.name));
                detailPanel.add(new JLabel("Level:")); detailPanel.add(new JLabel(String.valueOf(sel.level)));
                detailPanel.add(new JLabel("Radius:")); detailPanel.add(new JLabel(String.valueOf(sel.radiusOfEffect)));
                detailPanel.add(new JLabel("Damage:")); detailPanel.add(new JLabel(String.valueOf(sel.damage)));
                detailPanel.add(new JLabel("Range:")); detailPanel.add(new JLabel(String.valueOf(sel.range)));
                detailPanel.add(new JLabel("Effects:")); detailPanel.add(new JLabel(sel.effects));
                detailPanel.add(new JLabel("Buffs:")); detailPanel.add(new JLabel(sel.buffs));
                detailPanel.add(new JLabel("Cast Time:")); detailPanel.add(new JLabel(String.valueOf(sel.castTime)));
                detailPanel.add(new JLabel("Cost:")); detailPanel.add(new JLabel(String.valueOf(sel.cost)));
            }
            detailPanel.revalidate(); detailPanel.repaint();
        });
        return panel;
    }

    private JPanel createAchievementsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        DefaultListModel<Achievemnt> listModel = new DefaultListModel<>();
        achievements.forEach(listModel::addElement);
        JList<Achievemnt> achievementList = new JList<>(listModel);
        JScrollPane listScroll = new JScrollPane(achievementList);
        listScroll.setPreferredSize(new Dimension(150, 0));
        panel.add(listScroll, BorderLayout.WEST);

        JPanel detailPanel = new JPanel(new GridLayout(0, 2, 5, 5));
        detailPanel.setBorder(BorderFactory.createTitledBorder("Achievement Details"));
        panel.add(detailPanel, BorderLayout.CENTER);

        achievementList.addListSelectionListener(e -> {
            detailPanel.removeAll();
            Achievemnt sel = achievementList.getSelectedValue();
            if (sel != null) {
                detailPanel.add(new JLabel("Name:")); detailPanel.add(new JLabel(sel.name));
                detailPanel.add(new JLabel("Description:")); detailPanel.add(new JLabel(sel.description));
                detailPanel.add(new JLabel("Points:")); detailPanel.add(new JLabel(String.valueOf(sel.points)));
            }
            detailPanel.revalidate(); detailPanel.repaint();
        });
        return panel;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            UI ui = new UI(null);
            ui.setVisible(true);
        });
    }
}
