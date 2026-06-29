#!/usr/bin/env ruby
# invaders.rb
# encoding: UTF-8

require 'curses'
require 'json'
require 'fileutils'

RECORD_FILE = File.join(Dir.home, '.invaders_record.json')

def load_record
  return 0 unless File.exist?(RECORD_FILE)
  JSON.parse(File.read(RECORD_FILE))['record'] || 0
rescue
  0
end

def save_record(record)
  File.write(RECORD_FILE, JSON.pretty_generate(record: record))
end

Curses.init_screen
Curses.start_color
Curses.use_default_colors
Curses.init_pair(1, Curses::COLOR_YELLOW, -1)
Curses.init_pair(2, Curses::COLOR_RED, -1)
Curses.init_pair(3, Curses::COLOR_GREEN, -1)
Curses.init_pair(4, Curses::COLOR_CYAN, -1)
Curses.init_pair(5, Curses::COLOR_WHITE, -1)

height = Curses.lines
width = Curses.cols
if height < 25 || width < 40
  puts "Terminal too small"
  exit 1
end

speed = 50
if ARGV.include?('-s') && ARGV.index('-s') + 1 < ARGV.size
  speed = ARGV[ARGV.index('-s') + 1].to_i
end

player_x = width / 2
player_y = height - 2
bullets = []
enemies = []
bonuses = []
score = 0
lives = 3
level = 1
best = load_record
game_over = false
frame_time = speed / 1000.0
enemy_dir = 1
enemy_speed = 1
shoot_cooldown = 0
max_cooldown = 10

def spawn_enemies(enemies, width)
  enemies.clear
  4.times do |r|
    8.times do |c|
      enemies << [4 + c*4, 3 + r*2]
    end
  end
end
spawn_enemies(enemies, width)

Curses.curs_set(0)
Curses.noecho
Curses.timeout = 0

loop do
  ch = Curses.getch
  if ch == 'q' || ch == 'Q'
    break
  elsif ch == 'r' || ch == 'R'
    if game_over
      player_x = width / 2
      bullets.clear
      bonuses.clear
      score = 0
      lives = 3
      level = 1
      game_over = false
      enemy_dir = 1
      enemy_speed = 1
      spawn_enemies(enemies, width)
      next
    end
  end

  if game_over
    Curses.clear
    msg = "💀 Game Over! Score: #{score}  Best: #{best}"
    Curses.setpos(height/2-2, (width - msg.length)/2)
    Curses.attron(Curses.color_pair(5)) { Curses.addstr(msg) }
    Curses.setpos(height/2, (width - 20)/2)
    Curses.attron(Curses.color_pair(5)) { Curses.addstr("R - restart | Q - quit") }
    Curses.refresh
    next
  end

  # Controls
  if ch == Curses::KEY_LEFT || ch == 'a'
    player_x = [0, player_x - 2].max
  elsif ch == Curses::KEY_RIGHT || ch == 'd'
    player_x = [width - 1, player_x + 2].min
  end
  if ch == ' ' && shoot_cooldown <= 0
    bullets << [player_x, player_y - 1]
    shoot_cooldown = max_cooldown
    print "\a"
  end
  shoot_cooldown -= 1 if shoot_cooldown > 0

  # Enemy movement
  move_down = false
  enemies.each do |e|
    e[0] += enemy_dir * enemy_speed
    move_down = true if e[0] >= width - 2 || e[0] <= 1
  end
  if move_down
    enemy_dir *= -1
    enemies.each { |e| e[1] += 1 }
  end

  # Bullet collisions
  new_bullets = []
  bullets.each do |b|
    hit = false
    enemies.each_with_index do |e, i|
      if (b[0] - e[0]).abs < 2 && (b[1] - e[1]).abs < 1
        score += 10
        if rand(100) < 20
          bonuses << [e[0], e[1], rand(2)]
        end
        enemies.delete_at(i)
        hit = true
        print "\a"
        break
      end
    end
    new_bullets << [b[0], b[1] - 1] if !hit && b[1] > 0
  end
  bullets = new_bullets

  # Game over
  enemies.each do |e|
    if e[1] >= player_y - 1 || ((e[0] - player_x).abs < 2 && (e[1] - player_y).abs < 1)
      game_over = true
      if score > best
        best = score
        save_record(best)
      end
      break
    end
  end
  next if game_over

  if enemies.empty?
    level += 1
    enemy_speed += 1
    spawn_enemies(enemies, width)
  end

  # Bonuses
  new_bonuses = []
  bonuses.each do |b|
    if (b[0] - player_x).abs < 2 && (b[1] - player_y).abs < 1
      if b[2] == 0
        lives += 1
      elsif b[2] == 1
        max_cooldown = [3, max_cooldown - 2].max
      end
      next
    end
    new_bonuses << [b[0], b[1] + 1, b[2]] if b[1] < height
  end
  bonuses = new_bonuses

  # Draw
  Curses.clear
  Curses.attron(Curses.color_pair(1) | Curses::A_BOLD) { Curses.setpos(player_y, player_x); Curses.addstr('A') }
  Curses.attron(Curses.color_pair(3)) { bullets.each { |b| Curses.setpos(b[1], b[0]); Curses.addstr('|') } }
  Curses.attron(Curses.color_pair(2) | Curses::A_BOLD) { enemies.each { |e| Curses.setpos(e[1], e[0]); Curses.addstr('V') } }
  Curses.attron(Curses.color_pair(4)) do
    bonuses.each do |b|
      sym = b[2] == 0 ? '+' : 'S'
      Curses.setpos(b[1], b[0]); Curses.addstr(sym)
    end
  end
  Curses.attron(Curses.color_pair(5)) do
    Curses.setpos(0, 2); Curses.addstr("Score: #{score}")
    Curses.setpos(0, width/2-4); Curses.addstr("Best: #{best}")
    Curses.setpos(0, width-20); Curses.addstr("Lives: #{lives}  Level: #{level}")
  end
  Curses.refresh
  sleep(frame_time)
end

Curses.close_screen
