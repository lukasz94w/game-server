INSERT INTO player (name)
SELECT 'user1' WHERE NOT EXISTS (SELECT 1 FROM player WHERE name = 'user1');
INSERT INTO player (name)
SELECT 'user2' WHERE NOT EXISTS (SELECT 1 FROM player WHERE name = 'user2');
INSERT INTO player (name)
SELECT 'user3' WHERE NOT EXISTS (SELECT 1 FROM player WHERE name = 'user3');
INSERT INTO player (name)
SELECT 'user4' WHERE NOT EXISTS (SELECT 1 FROM player WHERE name = 'user4');
