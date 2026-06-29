// invaders.cpp
#include <curses.h>
#include <stdlib.h>
#include <time.h>
#include <unistd.h>
#include <fstream>
#include <string>
#include <vector>
#include <json/json.h>

using namespace std;

int loadRecord() {
    ifstream f(getenv("HOME") + string("/.invaders_record.json"));
    Json::Value root;
    if (f >> root) return root["record"].asInt();
    return 0;
}

void saveRecord(int record) {
    Json::Value root;
    root["record"] = record;
    ofstream f(getenv("HOME") + string("/.invaders_record.json"));
    f << root.toStyledString();
}

int main(int argc, char* argv[]) {
    int speed = 50;
    for (int i=1; i<argc; ++i) {
        if (string(argv[i]) == "-s" && i+1 < argc) speed = atoi(argv[++i]);
        else if (string(argv[i]) == "-h") { cout << "Usage: invaders [-s speed_ms]\n"; return 0; }
    }

    initscr();
    cbreak();
    noecho();
    curs_set(0);
    nodelay(stdscr, TRUE);
    keypad(stdscr, TRUE);
    start_color();
    init_pair(1, COLOR_YELLOW, COLOR_BLACK);
    init_pair(2, COLOR_RED, COLOR_BLACK);
    init_pair(3, COLOR_GREEN, COLOR_BLACK);
    init_pair(4, COLOR_CYAN, COLOR_BLACK);
    init_pair(5, COLOR_WHITE, COLOR_BLACK);

    int height, width;
    getmaxyx(stdscr, height, width);
    if (height < 25 || width < 40) {
        endwin();
        cout << "Terminal too small.\n";
        return 1;
    }

    int player_x = width/2, player_y = height-2;
    vector<pair<int,int>> bullets; // x,y
    struct Enemy { int x, y, dir; };
    vector<Enemy> enemies;
    struct Bonus { int x, y, type; }; // 0=life, 1=speed
    vector<Bonus> bonuses;
    int score = 0, lives = 3, level = 1;
    int best = loadRecord();
    bool gameOver = false;
    int frameTime = speed * 1000;
    int enemyDir = 1, enemySpeed = 1;
    int shootCooldown = 0, maxCooldown = 10;

    auto spawnEnemies = [&]() {
        enemies.clear();
        for (int r=0; r<4; ++r)
            for (int c=0; c<8; ++c)
                enemies.push_back({4+c*4, 3+r*2, 0});
    };
    spawnEnemies();

    while (true) {
        int ch = getch();
        if (ch == 'q' || ch == 'Q') break;
        if (ch == 'r' || ch == 'R') {
            if (gameOver) {
                player_x = width/2; bullets.clear(); bonuses.clear();
                score = 0; lives = 3; level = 1; gameOver = false;
                spawnEnemies(); enemyDir = 1; enemySpeed = 1;
                continue;
            }
        }

        if (gameOver) {
            clear();
            string msg = "💀 Game Over! Score: " + to_string(score) + "  Best: " + to_string(best);
            mvprintw(height/2-2, (width-msg.length())/2, "%s", msg.c_str());
            mvprintw(height/2, (width-20)/2, "R - restart | Q - quit");
            refresh();
            continue;
        }

        // Controls
        if (ch == KEY_LEFT || ch == 'a' || ch == 'A') player_x = max(0, player_x-2);
        else if (ch == KEY_RIGHT || ch == 'd' || ch == 'D') player_x = min(width-1, player_x+2);
        if (ch == ' ' && shootCooldown <= 0) {
            bullets.push_back({player_x, player_y-1});
            shootCooldown = maxCooldown;
            putchar('\a');
        }
        if (shootCooldown > 0) --shootCooldown;

        // Enemy movement
        bool moveDown = false;
        for (auto &e : enemies) {
            e.x += enemyDir * enemySpeed;
            if (e.x >= width-2 || e.x <= 1) moveDown = true;
        }
        if (moveDown) {
            enemyDir *= -1;
            for (auto &e : enemies) e.y += 1;
        }

        // Bullet collisions
        vector<pair<int,int>> newBullets;
        for (auto &b : bullets) {
            bool hit = false;
            for (auto it=enemies.begin(); it!=enemies.end(); ++it) {
                if (abs(b.first - it->x) < 2 && abs(b.second - it->y) < 1) {
                    score += 10;
                    if (rand() % 100 < 20) {
                        bonuses.push_back({it->x, it->y, rand()%2});
                    }
                    enemies.erase(it);
                    hit = true;
                    putchar('\a');
                    break;
                }
            }
            if (!hit && b.second > 0) newBullets.push_back({b.first, b.second-1});
        }
        bullets = newBullets;

        // Game over conditions
        for (auto &e : enemies) {
            if (e.y >= player_y-1 || (abs(e.x-player_x)<2 && abs(e.y-player_y)<1)) {
                gameOver = true;
                if (score > best) { best = score; saveRecord(best); }
                break;
            }
        }

        // Level complete
        if (enemies.empty()) {
            level++;
            enemySpeed += 0.5;
            spawnEnemies();
        }

        // Bonuses
        vector<Bonus> newBonuses;
        for (auto &b : bonuses) {
            if (abs(b.x-player_x)<2 && abs(b.y-player_y)<1) {
                if (b.type == 0) lives++;
                else if (b.type == 1) maxCooldown = max(3, maxCooldown-2);
                continue;
            }
            if (b.y < height) newBonuses.push_back({b.x, b.y+1, b.type});
        }
        bonuses = newBonuses;

        // Draw
        clear();
        attron(COLOR_PAIR(1) | A_BOLD);
        mvaddch(player_y, player_x, 'A');
        attroff(COLOR_PAIR(1) | A_BOLD);
        attron(COLOR_PAIR(3));
        for (auto &b : bullets) mvaddch(b.second, b.first, '|');
        attroff(COLOR_PAIR(3));
        attron(COLOR_PAIR(2) | A_BOLD);
        for (auto &e : enemies) mvaddch(e.y, e.x, 'V');
        attroff(COLOR_PAIR(2) | A_BOLD);
        attron(COLOR_PAIR(4));
        for (auto &b : bonuses) {
            char sym = (b.type == 0) ? '+' : 'S';
            mvaddch(b.y, b.x, sym);
        }
        attroff(COLOR_PAIR(4));
        attron(COLOR_PAIR(5));
        mvprintw(0, 2, "Score: %d", score);
        mvprintw(0, width/2-4, "Best: %d", best);
        mvprintw(0, width-20, "Lives: %d  Level: %d", lives, level);
        attroff(COLOR_PAIR(5));
        refresh();
        usleep(frameTime);
    }
    endwin();
    return 0;
}
