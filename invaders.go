// invaders.go
package main

import (
	"encoding/json"
	"fmt"
	"math/rand"
	"os"
	"time"
	"github.com/nsf/termbox-go"
)

const recordFile = ".invaders_record.json"

type Record struct {
	Best int `json:"record"`
}

func loadRecord() int {
	f, err := os.Open(recordFile)
	if err != nil {
		return 0
	}
	defer f.Close()
	var r Record
	json.NewDecoder(f).Decode(&r)
	return r.Best
}

func saveRecord(best int) {
	r := Record{Best: best}
	f, _ := os.Create(recordFile)
	defer f.Close()
	json.NewEncoder(f).Encode(r)
}

func main() {
	speed := 50
	if len(os.Args) > 2 && os.Args[1] == "-s" {
		if s, err := strconv.Atoi(os.Args[2]); err == nil && s > 0 {
			speed = s
		}
	}
	err := termbox.Init()
	if err != nil {
		fmt.Println("termbox init failed:", err)
		return
	}
	defer termbox.Close()
	termbox.SetInputMode(termbox.InputEsc)
	w, h := termbox.Size()
	if h < 25 || w < 40 {
		fmt.Println("Terminal too small")
		return
	}
	rand.Seed(time.Now().UnixNano())

	playerX := w / 2
	playerY := h - 2
	type Bullet struct{ x, y int }
	type Enemy struct{ x, y int }
	type Bonus struct{ x, y, typ int }
	bullets := []Bullet{}
	enemies := []Enemy{}
	bonuses := []Bonus{}
	score, lives, level := 0, 3, 1
	best := loadRecord()
	gameOver := false
	frame := time.Duration(speed) * time.Millisecond
	enemyDir, enemySpeed := 1, 1
	shootCooldown, maxCooldown := 0, 10

	spawnEnemies := func() {
		enemies = []Enemy{}
		for r := 0; r < 4; r++ {
			for c := 0; c < 8; c++ {
				enemies = append(enemies, Enemy{4 + c*4, 3 + r*2})
			}
		}
	}
	spawnEnemies()

	for {
		ev := termbox.PollEvent()
		if ev.Type == termbox.EventKey {
			if ev.Key == termbox.KeyEsc || ev.Ch == 'q' {
				return
			}
			if ev.Ch == 'r' && gameOver {
				playerX = w / 2
				bullets = []Bullet{}
				bonuses = []Bonus{}
				score, lives, level = 0, 3, 1
				gameOver = false
				enemyDir, enemySpeed = 1, 1
				spawnEnemies()
				continue
			}
			if ev.Key == termbox.KeyArrowLeft || ev.Ch == 'a' {
				playerX = max(0, playerX-2)
			} else if ev.Key == termbox.KeyArrowRight || ev.Ch == 'd' {
				playerX = min(w-1, playerX+2)
			}
			if ev.Ch == ' ' && shootCooldown <= 0 {
				bullets = append(bullets, Bullet{playerX, playerY - 1})
				shootCooldown = maxCooldown
				fmt.Print("\a")
			}
			if shootCooldown > 0 {
				shootCooldown--
			}
		}

		if gameOver {
			termbox.Clear(termbox.ColorDefault, termbox.ColorDefault)
			msg := fmt.Sprintf("💀 Game Over! Score: %d  Best: %d", score, best)
			tbprint(w/2-len(msg)/2, h/2-2, termbox.ColorWhite, termbox.ColorDefault, msg)
			tbprint(w/2-10, h/2, termbox.ColorCyan, termbox.ColorDefault, "R - restart | Q - quit")
			termbox.Flush()
			continue
		}

		// Enemy movement
		moveDown := false
		for i := range enemies {
			enemies[i].x += enemyDir * enemySpeed
			if enemies[i].x >= w-2 || enemies[i].x <= 1 {
				moveDown = true
			}
		}
		if moveDown {
			enemyDir *= -1
			for i := range enemies {
				enemies[i].y += 1
			}
		}

		// Bullet collisions
		newBullets := []Bullet{}
		for _, b := range bullets {
			hit := false
			for i := 0; i < len(enemies); i++ {
				if abs(b.x-enemies[i].x) < 2 && abs(b.y-enemies[i].y) < 1 {
					score += 10
					if rand.Intn(100) < 20 {
						bonuses = append(bonuses, Bonus{enemies[i].x, enemies[i].y, rand.Intn(2)})
					}
					enemies = append(enemies[:i], enemies[i+1:]...)
					hit = true
					fmt.Print("\a")
					break
				}
			}
			if !hit && b.y > 0 {
				newBullets = append(newBullets, Bullet{b.x, b.y - 1})
			}
		}
		bullets = newBullets

		// Game over
		for _, e := range enemies {
			if e.y >= playerY-1 || (abs(e.x-playerX) < 2 && abs(e.y-playerY) < 1) {
				gameOver = true
				if score > best {
					best = score
					saveRecord(best)
				}
				break
			}
		}
		if gameOver {
			continue
		}

		if len(enemies) == 0 {
			level++
			enemySpeed++
			spawnEnemies()
		}

		// Bonuses
		newBonuses := []Bonus{}
		for _, b := range bonuses {
			if abs(b.x-playerX) < 2 && abs(b.y-playerY) < 1 {
				if b.typ == 0 {
					lives++
				} else if b.typ == 1 {
					maxCooldown = max(3, maxCooldown-2)
				}
				continue
			}
			if b.y < h {
				newBonuses = append(newBonuses, Bonus{b.x, b.y + 1, b.typ})
			}
		}
		bonuses = newBonuses

		// Draw
		termbox.Clear(termbox.ColorDefault, termbox.ColorDefault)
		// Player
		termbox.SetCell(playerX, playerY, 'A', termbox.ColorYellow|termbox.AttrBold, termbox.ColorDefault)
		// Bullets
		for _, b := range bullets {
			termbox.SetCell(b.x, b.y, '|', termbox.ColorGreen, termbox.ColorDefault)
		}
		// Enemies
		for _, e := range enemies {
			termbox.SetCell(e.x, e.y, 'V', termbox.ColorRed|termbox.AttrBold, termbox.ColorDefault)
		}
		// Bonuses
		for _, b := range bonuses {
			sym := '+'
			if b.typ == 1 {
				sym = 'S'
			}
			termbox.SetCell(b.x, b.y, sym, termbox.ColorCyan, termbox.ColorDefault)
		}
		// Score
		tbprint(2, 0, termbox.ColorWhite, termbox.ColorDefault, fmt.Sprintf("Score: %d", score))
		tbprint(w/2-4, 0, termbox.ColorWhite, termbox.ColorDefault, fmt.Sprintf("Best: %d", best))
		tbprint(w-20, 0, termbox.ColorWhite, termbox.ColorDefault, fmt.Sprintf("Lives: %d  Level: %d", lives, level))
		termbox.Flush()
		time.Sleep(frame)
	}
}

func tbprint(x, y int, fg, bg termbox.Attribute, msg string) {
	for _, ch := range msg {
		termbox.SetCell(x, y, ch, fg, bg)
		x++
	}
}
func max(a, b int) int { if a > b { return a }; return b }
func min(a, b int) int { if a < b { return a }; return b }
func abs(a int) int { if a < 0 { return -a }; return a }
