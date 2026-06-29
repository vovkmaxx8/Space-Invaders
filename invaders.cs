// invaders.cs
using System;
using System.Collections.Generic;
using System.IO;
using System.Text.Json;
using System.Threading;
using System.Runtime.InteropServices;

class SpaceInvaders
{
    static string Colorize(string text, string color)
    {
        string col = color switch
        {
            "yellow" => "\x1b[93m",
            "red" => "\x1b[91m",
            "green" => "\x1b[92m",
            "cyan" => "\x1b[96m",
            "white" => "\x1b[97m",
            _ => "\x1b[0m"
        };
        return col + text + "\x1b[0m";
    }

    static string ConfigFile => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".invaders_record.json");

    static int LoadRecord()
    {
        if (!File.Exists(ConfigFile)) return 0;
        string json = File.ReadAllText(ConfigFile);
        var data = JsonSerializer.Deserialize<Dictionary<string,int>>(json);
        return data.GetValueOrDefault("record", 0);
    }

    static void SaveRecord(int record)
    {
        var data = new Dictionary<string,int> { { "record", record } };
        string json = JsonSerializer.Serialize(data);
        File.WriteAllText(ConfigFile, json);
    }

    static void Main(string[] args)
    {
        int speed = 50;
        for (int i=0; i<args.Length; i++)
        {
            if (args[i] == "-s" && i+1 < args.Length)
                speed = int.Parse(args[++i]);
            else if (args[i] == "-h") { Console.WriteLine("Usage: invaders [-s speed_ms]"); return; }
        }

        Console.Clear();
        int height = Console.WindowHeight;
        int width = Console.WindowWidth;
        if (height < 25 || width < 40)
        {
            Console.WriteLine("Terminal too small");
            return;
        }

        Random rand = new Random();
        int playerX = width/2;
        int playerY = height-2;
        List<(int x, int y)> bullets = new List<(int, int)>();
        List<(int x, int y)> enemies = new List<(int, int)>();
        List<(int x, int y, int type)> bonuses = new List<(int, int, int)>();
        int score = 0, lives = 3, level = 1;
        int best = LoadRecord();
        bool gameOver = false;
        int frameTime = speed;
        int enemyDir = 1, enemySpeed = 1;
        int shootCooldown = 0, maxCooldown = 10;

        void SpawnEnemies()
        {
            enemies.Clear();
            for (int r=0; r<4; r++)
                for (int c=0; c<8; c++)
                    enemies.Add((4 + c*4, 3 + r*2));
        }
        SpawnEnemies();

        Console.CursorVisible = false;

        while (true)
        {
            if (Console.KeyAvailable)
            {
                var key = Console.ReadKey(true).Key;
                if (key == ConsoleKey.Q) break;
                if (key == ConsoleKey.R && gameOver)
                {
                    playerX = width/2; bullets.Clear(); bonuses.Clear();
                    score = 0; lives = 3; level = 1; gameOver = false;
                    enemyDir = 1; enemySpeed = 1;
                    SpawnEnemies();
                    continue;
                }
                if (key == ConsoleKey.LeftArrow || key == ConsoleKey.A) playerX = Math.Max(0, playerX-2);
                else if (key == ConsoleKey.RightArrow || key == ConsoleKey.D) playerX = Math.Min(width-1, playerX+2);
                if (key == ConsoleKey.Spacebar && shootCooldown <= 0)
                {
                    bullets.Add((playerX, playerY-1));
                    shootCooldown = maxCooldown;
                    Console.Beep();
                }
            }
            if (shootCooldown > 0) shootCooldown--;

            if (gameOver)
            {
                Console.Clear();
                string msg = $"💀 Game Over! Score: {score}  Best: {best}";
                Console.SetCursorPosition((width - msg.Length) / 2, height/2 - 2);
                Console.Write(Colorize(msg, "red"));
                Console.SetCursorPosition((width - 20) / 2, height/2);
                Console.Write(Colorize("R - restart | Q - quit", "cyan"));
                continue;
            }

            // Enemy movement
            bool moveDown = false;
            for (int i=0; i<enemies.Count; i++)
            {
                var e = enemies[i];
                e.x += enemyDir * enemySpeed;
                enemies[i] = e;
                if (e.x >= width-2 || e.x <= 1) moveDown = true;
            }
            if (moveDown)
            {
                enemyDir *= -1;
                for (int i=0; i<enemies.Count; i++)
                {
                    var e = enemies[i];
                    e.y += 1;
                    enemies[i] = e;
                }
            }

            // Bullet collisions
            List<(int x, int y)> newBullets = new List<(int, int)>();
            foreach (var b in bullets)
            {
                bool hit = false;
                for (int i=enemies.Count-1; i>=0; i--)
                {
                    var e = enemies[i];
                    if (Math.Abs(b.x - e.x) < 2 && Math.Abs(b.y - e.y) < 1)
                    {
                        score += 10;
                        if (rand.Next(100) < 20)
                            bonuses.Add((e.x, e.y, rand.Next(2)));
                        enemies.RemoveAt(i);
                        hit = true;
                        Console.Beep();
                        break;
                    }
                }
                if (!hit && b.y > 0) newBullets.Add((b.x, b.y-1));
            }
            bullets = newBullets;

            // Game over
            foreach (var e in enemies)
            {
                if (e.y >= playerY-1 || (Math.Abs(e.x - playerX) < 2 && Math.Abs(e.y - playerY) < 1))
                {
                    gameOver = true;
                    if (score > best) { best = score; SaveRecord(best); }
                    break;
                }
            }
            if (gameOver) continue;

            if (enemies.Count == 0)
            {
                level++;
                enemySpeed += 1;
                SpawnEnemies();
            }

            // Bonuses
            List<(int x, int y, int type)> newBonuses = new List<(int, int, int)>();
            foreach (var b in bonuses)
            {
                if (Math.Abs(b.x - playerX) < 2 && Math.Abs(b.y - playerY) < 1)
                {
                    if (b.type == 0) lives++;
                    else if (b.type == 1) maxCooldown = Math.Max(3, maxCooldown - 2);
                    continue;
                }
                if (b.y < height) newBonuses.Add((b.x, b.y+1, b.type));
            }
            bonuses = newBonuses;

            // Draw
            Console.Clear();
            // player
            Console.SetCursorPosition(playerX, playerY);
            Console.Write(Colorize("A", "yellow"));
            // bullets
            foreach (var b in bullets) { Console.SetCursorPosition(b.x, b.y); Console.Write(Colorize("|", "green")); }
            // enemies
            foreach (var e in enemies) { Console.SetCursorPosition(e.x, e.y); Console.Write(Colorize("V", "red")); }
            // bonuses
            foreach (var b in bonuses)
            {
                char sym = b.type == 0 ? '+' : 'S';
                Console.SetCursorPosition(b.x, b.y);
                Console.Write(Colorize(sym.ToString(), "cyan"));
            }
            // score
            Console.SetCursorPosition(2, 0);
            Console.Write(Colorize($"Score: {score}", "white"));
            Console.SetCursorPosition(width/2 - 4, 0);
            Console.Write(Colorize($"Best: {best}", "white"));
            Console.SetCursorPosition(width - 20, 0);
            Console.Write(Colorize($"Lives: {lives}  Level: {level}", "white"));
            Thread.Sleep(frameTime);
        }
    }
}
