// invaders.java
import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;

public class invaders {
    private static String configFile = System.getProperty("user.home") + "/.invaders_record.json";

    private static int loadRecord() throws IOException {
        Path path = Paths.get(configFile);
        if (!Files.exists(path)) return 0;
        String json = new String(Files.readAllBytes(path));
        Gson gson = new Gson();
        JsonObject obj = gson.fromJson(json, JsonObject.class);
        return obj.get("record").getAsInt();
    }

    private static void saveRecord(int record) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject obj = new JsonObject();
        obj.addProperty("record", record);
        Files.write(Paths.get(configFile), gson.toJson(obj).getBytes());
    }

    public static void main(String[] args) throws Exception {
        int speed = 50;
        for (int i=0; i<args.length; i++) {
            if (args[i].equals("-s") && i+1 < args.length) {
                speed = Integer.parseInt(args[++i]);
            } else if (args[i].equals("-h")) {
                System.out.println("Usage: invaders [-s speed_ms]");
                return;
            }
        }

        Terminal terminal = new DefaultTerminalFactory().createTerminal();
        terminal.enterPrivateMode();
        terminal.setCursorVisible(false);
        TerminalSize size = terminal.getTerminalSize();
        int height = size.getRows();
        int width = size.getColumns();
        if (height < 25 || width < 40) {
            System.out.println("Terminal too small");
            System.exit(1);
        }

        Random rand = new Random();
        int playerX = width/2, playerY = height-2;
        List<int[]> bullets = new ArrayList<>();
        List<int[]> enemies = new ArrayList<>();
        List<int[]> bonuses = new ArrayList<>();
        int score = 0, lives = 3, level = 1;
        int best = loadRecord();
        boolean gameOver = false;
        int frameTime = speed;
        int enemyDir = 1, enemySpeed = 1;
        int shootCooldown = 0, maxCooldown = 10;

        Runnable spawnEnemies = () -> {
            enemies.clear();
            for (int r=0; r<4; r++)
                for (int c=0; c<8; c++)
                    enemies.add(new int[]{4 + c*4, 3 + r*2});
        };
        spawnEnemies.run();

        TextGraphics tg = terminal.newTextGraphics();

        while (true) {
            KeyStroke key = terminal.pollInput();
            if (key != null) {
                char ch = key.getCharacter() != null ? key.getCharacter() : 0;
                if (ch == 'q' || ch == 'Q') break;
                if (ch == 'r' || ch == 'R') {
                    if (gameOver) {
                        playerX = width/2; bullets.clear(); bonuses.clear();
                        score = 0; lives = 3; level = 1; gameOver = false;
                        enemyDir = 1; enemySpeed = 1;
                        spawnEnemies.run();
                        continue;
                    }
                }
                if (key.getKeyType() == KeyStroke.KeyType.ArrowLeft || ch == 'a')
                    playerX = Math.max(0, playerX-2);
                else if (key.getKeyType() == KeyStroke.KeyType.ArrowRight || ch == 'd')
                    playerX = Math.min(width-1, playerX+2);
                if (ch == ' ' && shootCooldown <= 0) {
                    bullets.add(new int[]{playerX, playerY-1});
                    shootCooldown = maxCooldown;
                    System.out.print("\007");
                }
            }
            if (shootCooldown > 0) shootCooldown--;

            if (gameOver) {
                tg.clear();
                String msg = "💀 Game Over! Score: " + score + "  Best: " + best;
                tg.putString((width - msg.length())/2, height/2-2, msg, TextColor.ANSI.RED);
                tg.putString((width - 20)/2, height/2, "R - restart | Q - quit", TextColor.ANSI.CYAN);
                terminal.flush();
                continue;
            }

            // Enemy movement
            boolean moveDown = false;
            for (int[] e : enemies) {
                e[0] += enemyDir * enemySpeed;
                if (e[0] >= width-2 || e[0] <= 1) moveDown = true;
            }
            if (moveDown) {
                enemyDir *= -1;
                for (int[] e : enemies) e[1] += 1;
            }

            // Bullet collisions
            List<int[]> newBullets = new ArrayList<>();
            for (int[] b : bullets) {
                boolean hit = false;
                for (Iterator<int[]> it = enemies.iterator(); it.hasNext(); ) {
                    int[] e = it.next();
                    if (Math.abs(b[0] - e[0]) < 2 && Math.abs(b[1] - e[1]) < 1) {
                        score += 10;
                        if (rand.nextInt(100) < 20) {
                            bonuses.add(new int[]{e[0], e[1], rand.nextInt(2)});
                        }
                        it.remove();
                        hit = true;
                        System.out.print("\007");
                        break;
                    }
                }
                if (!hit && b[1] > 0) newBullets.add(new int[]{b[0], b[1]-1});
            }
            bullets = newBullets;

            // Game over
            for (int[] e : enemies) {
                if (e[1] >= playerY-1 || (Math.abs(e[0]-playerX) < 2 && Math.abs(e[1]-playerY) < 1)) {
                    gameOver = true;
                    if (score > best) { best = score; saveRecord(best); }
                    break;
                }
            }
            if (gameOver) continue;

            if (enemies.isEmpty()) {
                level++;
                enemySpeed += 1;
                spawnEnemies.run();
            }

            // Bonuses
            List<int[]> newBonuses = new ArrayList<>();
            for (int[] b : bonuses) {
                if (Math.abs(b[0]-playerX) < 2 && Math.abs(b[1]-playerY) < 1) {
                    if (b[2] == 0) lives++;
                    else if (b[2] == 1) maxCooldown = Math.max(3, maxCooldown-2);
                    continue;
                }
                if (b[1] < height) newBonuses.add(new int[]{b[0], b[1]+1, b[2]});
            }
            bonuses = newBonuses;

            // Draw
            tg.clear();
            // player
            tg.putString(playerX, playerY, "A", TextColor.ANSI.YELLOW, TextColor.ANSI.DEFAULT);
            // bullets
            for (int[] b : bullets) tg.putString(b[0], b[1], "|", TextColor.ANSI.GREEN);
            // enemies
            for (int[] e : enemies) tg.putString(e[0], e[1], "V", TextColor.ANSI.RED);
            // bonuses
            for (int[] b : bonuses) {
                char sym = b[2] == 0 ? '+' : 'S';
                tg.putString(b[0], b[1], String.valueOf(sym), TextColor.ANSI.CYAN);
            }
            // score
            tg.putString(2, 0, "Score: " + score, TextColor.ANSI.WHITE);
            tg.putString(width/2-4, 0, "Best: " + best, TextColor.ANSI.WHITE);
            tg.putString(width-20, 0, "Lives: " + lives + "  Level: " + level, TextColor.ANSI.WHITE);
            terminal.flush();
            Thread.sleep(frameTime);
        }
        terminal.exitPrivateMode();
        terminal.close();
    }
}
