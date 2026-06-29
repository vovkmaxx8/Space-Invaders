# invaders.py
#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import sys
import os
import random
import json
import time
import argparse
import curses
from pathlib import Path

# Конфигурация
RECORD_FILE = Path.home() / '.invaders_record.json'

def load_record():
    try:
        with open(RECORD_FILE, 'r') as f:
            return json.load(f).get('record', 0)
    except:
        return 0

def save_record(record):
    with open(RECORD_FILE, 'w') as f:
        json.dump({'record': record}, f)

def main(stdscr, speed):
    curses.curs_set(0)
    stdscr.nodelay(1)
    stdscr.timeout(0)
    curses.start_color()
    curses.use_default_colors()
    curses.init_pair(1, curses.COLOR_YELLOW, -1)   # корабль
    curses.init_pair(2, curses.COLOR_RED, -1)     # враги
    curses.init_pair(3, curses.COLOR_GREEN, -1)   # пули
    curses.init_pair(4, curses.COLOR_CYAN, -1)    # бонусы
    curses.init_pair(5, curses.COLOR_WHITE, -1)   # счёт

    height, width = stdscr.getmaxyx()
    if height < 25 or width < 40:
        print("Терминал слишком мал. Нужно минимум 25x40.")
        return

    # Игровые параметры
    player_x = width // 2
    player_y = height - 2
    bullets = []  # (x, y)
    enemies = []
    bonuses = []  # (x, y, type) type: 0=life, 1=speed
    score = 0
    lives = 3
    level = 1
    best = load_record()
    game_over = False
    frame_time = speed / 1000.0

    # Генерация врагов
    def spawn_enemies():
        enemies.clear()
        rows = 4
        cols = 8
        for r in range(rows):
            for c in range(cols):
                x = 4 + c * 4
                y = 3 + r * 2
                enemies.append([x, y, 0, 1])  # x, y, направление (0=вправо, 1=влево)

    spawn_enemies()
    enemy_dir = 1  # 1=вправо, -1=влево
    enemy_speed = 1
    shoot_cooldown = 0
    max_cooldown = 10

    # Основной цикл
    while True:
        key = stdscr.getch()
        if key == ord('q') or key == ord('Q'):
            break
        if key == ord('r') or key == ord('R'):
            if game_over:
                player_x = width // 2
                bullets.clear()
                bonuses.clear()
                score = 0
                lives = 3
                level = 1
                game_over = False
                spawn_enemies()
                enemy_dir = 1
                enemy_speed = 1
                continue

        if game_over:
            stdscr.clear()
            msg = f"💀 Игра окончена! Счёт: {score}  Рекорд: {best}"
            stdscr.addstr(height//2 - 2, (width - len(msg))//2, msg, curses.color_pair(5))
            stdscr.addstr(height//2, (width - 20)//2, "R - рестарт | Q - выход", curses.color_pair(5))
            stdscr.refresh()
            continue

        # Управление
        if key == curses.KEY_LEFT or key == ord('a') or key == ord('A'):
            player_x = max(0, player_x - 2)
        elif key == curses.KEY_RIGHT or key == ord('d') or key == ord('D'):
            player_x = min(width - 1, player_x + 2)

        if key == ord(' ') and shoot_cooldown <= 0:
            bullets.append([player_x, player_y - 1])
            shoot_cooldown = max_cooldown
            stdscr.addstr(0, 0, '\a')
        if shoot_cooldown > 0:
            shoot_cooldown -= 1

        # Движение врагов
        move_down = False
        for enemy in enemies:
            if enemy_dir == 1:
                enemy[0] += enemy_speed
                if enemy[0] >= width - 2:
                    move_down = True
            else:
                enemy[0] -= enemy_speed
                if enemy[0] <= 1:
                    move_down = True

        if move_down:
            enemy_dir *= -1
            for enemy in enemies:
                enemy[1] += 1

        # Столкновения пуль с врагами
        new_bullets = []
        for bx, by in bullets:
            hit = False
            for enemy in enemies:
                ex, ey, _, _ = enemy
                if abs(bx - ex) < 2 and abs(by - ey) < 1:
                    enemies.remove(enemy)
                    score += 10
                    # бонус
                    if random.random() < 0.2:
                        bonuses.append([ex, ey, random.choice([0, 1])])
                    hit = True
                    stdscr.addstr(0, 0, '\a')
                    break
            if not hit and by > 0:
                new_bullets.append([bx, by - 1])
        bullets = new_bullets

        # Проверка поражения
        for enemy in enemies:
            if enemy[1] >= player_y - 1:
                game_over = True
                if score > best:
                    best = score
                    save_record(best)
                break
            if abs(enemy[0] - player_x) < 2 and abs(enemy[1] - player_y) < 1:
                game_over = True
                break

        # Проверка уровня
        if not enemies:
            level += 1
            enemy_speed += 0.5
            spawn_enemies()

        # Бонусы (просто подбираем, если касаются корабля)
        new_bonuses = []
        for bx, by, btype in bonuses:
            if abs(bx - player_x) < 2 and abs(by - player_y) < 1:
                if btype == 0:
                    lives += 1
                elif btype == 1:
                    max_cooldown = max(3, max_cooldown - 2)
                continue
            if by < height:
                new_bonuses.append([bx, by + 1, btype])
            else:
                continue
        bonuses = new_bonuses

        # Отрисовка
        stdscr.clear()
        # Корабль
        stdscr.addch(player_y, player_x, 'A', curses.color_pair(1) | curses.A_BOLD)
        # Пули
        for bx, by in bullets:
            stdscr.addch(by, bx, '|', curses.color_pair(3))
        # Враги
        for ex, ey, _, _ in enemies:
            stdscr.addch(ey, ex, 'V', curses.color_pair(2) | curses.A_BOLD)
        # Бонусы
        for bx, by, btype in bonuses:
            sym = '+' if btype == 0 else 'S'
            stdscr.addch(by, bx, sym, curses.color_pair(4))
        # Счёт, жизни, уровень
        stdscr.addstr(0, 2, f"Счёт: {score}", curses.color_pair(5))
        stdscr.addstr(0, width//2 - 4, f"Рекорд: {best}", curses.color_pair(5))
        stdscr.addstr(0, width - 20, f"Жизни: {lives}  Уровень: {level}", curses.color_pair(5))
        stdscr.refresh()

        time.sleep(frame_time)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('-s', '--speed', type=int, default=50, help='Скорость (мс)')
    args = parser.parse_args()
    try:
        curses.wrapper(main, args.speed)
    except KeyboardInterrupt:
        print("\nИгра завершена.")
