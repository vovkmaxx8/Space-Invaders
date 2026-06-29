// invaders.js
#!/usr/bin/env node
'use strict';

const blessed = require('blessed');
const fs = require('fs');
const path = require('path');
const os = require('os');

const RECORD_FILE = path.join(os.homedir(), '.invaders_record.json');

function loadRecord() {
    try { return JSON.parse(fs.readFileSync(RECORD_FILE)).record || 0; } catch { return 0; }
}
function saveRecord(record) {
    fs.writeFileSync(RECORD_FILE, JSON.stringify({ record }));
}

let speed = 50;
if (process.argv.includes('-s') && process.argv.length > process.argv.indexOf('-s')+1) {
    speed = parseInt(process.argv[process.argv.indexOf('-s')+1]) || 50;
}

const screen = blessed.screen({
    smartCSR: true,
    title: 'Space Invaders',
    fullUnicode: true,
});

const height = screen.height;
const width = screen.width;
if (height < 25 || width < 40) {
    console.log('Terminal too small (min 25x40)');
    process.exit(1);
}

let playerX = Math.floor(width/2);
const playerY = height - 2;
let bullets = [];
let enemies = [];
let bonuses = [];
let score = 0, lives = 3, level = 1;
let best = loadRecord();
let gameOver = false;
let frameTime = speed;
let enemyDir = 1, enemySpeed = 1;
let shootCooldown = 0, maxCooldown = 10;

function spawnEnemies() {
    enemies = [];
    for (let r=0; r<4; r++) {
        for (let c=0; c<8; c++) {
            enemies.push({x: 4 + c*4, y: 3 + r*2});
        }
    }
}
spawnEnemies();

function draw() {
    screen.clear();
    // Player
    screen.fillRegion('A', playerX, playerY, playerX+1, playerY+1, blessed.colors.yellow, blessed.colors.black);
    // Bullets
    for (const b of bullets) {
        screen.fillRegion('|', b.x, b.y, b.x+1, b.y+1, blessed.colors.green, blessed.colors.black);
    }
    // Enemies
    for (const e of enemies) {
        screen.fillRegion('V', e.x, e.y, e.x+1, e.y+1, blessed.colors.red, blessed.colors.black);
    }
    // Bonuses
    for (const b of bonuses) {
        const sym = b.type === 0 ? '+' : 'S';
        screen.fillRegion(sym, b.x, b.y, b.x+1, b.y+1, blessed.colors.cyan, blessed.colors.black);
    }
    // Score
    screen.setContent(0, 2, `Score: ${score}`, blessed.colors.white);
    screen.setContent(0, Math.floor(width/2)-4, `Best: ${best}`, blessed.colors.white);
    screen.setContent(0, width-20, `Lives: ${lives}  Level: ${level}`, blessed.colors.white);
    if (gameOver) {
        screen.setContent(Math.floor(height/2)-2, Math.floor(width/2)-15, `💀 Game Over! Score: ${score}  Best: ${best}`, blessed.colors.red);
        screen.setContent(Math.floor(height/2), Math.floor(width/2)-10, 'R - restart | Q - quit', blessed.colors.cyan);
    }
    screen.render();
}

function update() {
    if (gameOver) {
        draw();
        return;
    }

    // Enemy movement
    let moveDown = false;
    for (const e of enemies) {
        e.x += enemyDir * enemySpeed;
        if (e.x >= width-2 || e.x <= 1) moveDown = true;
    }
    if (moveDown) {
        enemyDir *= -1;
        for (const e of enemies) e.y += 1;
    }

    // Bullet collisions
    const newBullets = [];
    for (const b of bullets) {
        let hit = false;
        for (let i=0; i<enemies.length; i++) {
            const e = enemies[i];
            if (Math.abs(b.x - e.x) < 2 && Math.abs(b.y - e.y) < 1) {
                score += 10;
                if (Math.random() < 0.2) {
                    bonuses.push({x: e.x, y: e.y, type: Math.random() < 0.5 ? 0 : 1});
                }
                enemies.splice(i, 1);
                hit = true;
                process.stdout.write('\x07');
                break;
            }
        }
        if (!hit && b.y > 0) {
            newBullets.push({x: b.x, y: b.y - 1});
        }
    }
    bullets = newBullets;

    // Game over
    for (const e of enemies) {
        if (e.y >= playerY-1 || (Math.abs(e.x - playerX) < 2 && Math.abs(e.y - playerY) < 1)) {
            gameOver = true;
            if (score > best) { best = score; saveRecord(best); }
            break;
        }
    }
    if (gameOver) { draw(); return; }

    if (enemies.length === 0) {
        level++;
        enemySpeed += 0.5;
        spawnEnemies();
    }

    // Bonuses
    const newBonuses = [];
    for (const b of bonuses) {
        if (Math.abs(b.x - playerX) < 2 && Math.abs(b.y - playerY) < 1) {
            if (b.type === 0) lives++;
            else if (b.type === 1) maxCooldown = Math.max(3, maxCooldown - 2);
            continue;
        }
        if (b.y < height) newBonuses.push({x: b.x, y: b.y + 1, type: b.type});
    }
    bonuses = newBonuses;
    draw();
    setTimeout(update, frameTime);
}

// Controls
screen.key(['left', 'a', 'A'], function() { playerX = Math.max(0, playerX - 2); });
screen.key(['right', 'd', 'D'], function() { playerX = Math.min(width - 1, playerX + 2); });
screen.key(['space'], function() {
    if (shootCooldown <= 0 && !gameOver) {
        bullets.push({x: playerX, y: playerY - 1});
        shootCooldown = maxCooldown;
        process.stdout.write('\x07');
    }
});
screen.key(['r', 'R'], function() {
    if (gameOver) {
        playerX = Math.floor(width/2);
        bullets = [];
        bonuses = [];
        score = 0; lives = 3; level = 1;
        gameOver = false;
        enemyDir = 1; enemySpeed = 1;
        spawnEnemies();
        draw();
    }
});
screen.key(['q', 'Q'], function() { process.exit(0); });

// Cooldown timer
setInterval(() => { if (shootCooldown > 0) shootCooldown--; }, 50);

draw();
update();

screen.on('resize', function() {});
