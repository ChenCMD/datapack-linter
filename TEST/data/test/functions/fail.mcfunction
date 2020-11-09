say @a[gamemode=advanture]
kick @s
scoreboard players get @s miss
data get entity @s miss
tellraw @s [{"text": "miss"}
time set 1m
loot give @s loot minecraft:entities/miss