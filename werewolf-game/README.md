# 🌕 Loup-Garou Multijoueur

Jeu du Loup-Garou multijoueur, **client JavaFX** + **API REST PHP** + **MySQL**.
Communication par HTTP / JSON, polling toutes les 2 secondes.

## Architecture

```
werewolf-game/
├── backend/                    # API REST PHP
│   ├── api/
│   │   ├── index.php           # routeur
│   │   ├── config.php          # connexion BDD, helpers, auth
│   │   ├── config.local.example.php  # template config locale (clé Gemini…)
│   │   ├── auth.php            # /login, /register
│   │   ├── games.php           # lobby, jeu, chat, historique, classement
│   │   ├── game_logic.php      # rôles, machine à états, chasseur, ELO
│   │   ├── social.php          # amis, invitations, profil
│   │   ├── bots.php            # bots du mode entraînement (votes/actions)
│   │   └── ai_bots.php         # dialogues IA des bots (Google Gemini)
│   └── db/
│       ├── schema.sql          # création BDD complète (installation fraîche)
│       └── migration_v2..v5.sql  # mises à jour incrémentales d'une BDD existante
└── frontend/                   # Client JavaFX (Maven)
    ├── pom.xml
    └── src/main/
        ├── java/com/werewolf/
        │   ├── Main.java
        │   ├── Router.java
        │   ├── model/          # POJO (Player, GameSession, Role…)
        │   ├── service/        # ApiClient, AuthService, GameService, SocialService…
        │   └── controller/     # Login, Lobby, WaitingRoom, Game, Profile, History
        └── resources/
            ├── fxml/           # Vues (login, lobby, waiting_room, game, profile, history)
            ├── css/dark-theme.css
            ├── fonts/ images/ sounds/
```

## Pré-requis

| Outil       | Version                |
|-------------|------------------------|
| Java JDK    | **17+** (testé 17/21)  |
| Maven       | 3.8+                   |
| PHP         | **8.1+**               |
| MySQL       | 5.7+ ou 8.0+           |

> Sous Windows, **XAMPP** ou **WampServer** sont parfaits (Apache + MySQL + PHP).

## 1. Installation du backend

### a) Créer la base

**Installation fraîche** (recommandé) — `schema.sql` contient le schéma complet à jour :
```bash
mysql -u root -p < backend/db/schema.sql
```

**Mise à jour d'une BDD existante** — appliquer les migrations dans l'ordre, uniquement celles pas encore passées :
```bash
mysql -u root -p werewolf < backend/db/migration_v2.sql   # ELO, chat, chasseur
mysql -u root -p werewolf < backend/db/migration_v3.sql   # amis, invitations
mysql -u root -p werewolf < backend/db/migration_v4.sql   # bots d'entraînement
mysql -u root -p werewolf < backend/db/migration_v5.sql   # throttle chat IA
mysql -u root -p werewolf < backend/db/migration_v6.sql   # Petite Fille, Capitaine, fix sorcière
```
> Les migrations ne sont **pas** nécessaires après un `schema.sql` frais.

### b) Adapter les identifiants
Éditer `backend/api/config.php` :
```php
define('DB_HOST', '127.0.0.1');
define('DB_NAME', 'werewolf');
define('DB_USER', 'root');
define('DB_PASS', '');
```

### c) Lancer le serveur PHP
Le plus simple (sans Apache) :
```bash
cd backend
php -S 127.0.0.1:8000 -t api
```
> ⚠️ Bien utiliser `127.0.0.1` et non `localhost` : selon la machine, PHP peut se lier
> uniquement en IPv6 (`::1`) alors que le client Java appelle en IPv4 → `Connection refused`.
Vérification :
```
http://localhost:8000/api/games
→ {"games":[]}
```

> ⚠️ Avec le serveur intégré, les URLs `/api/...` sont mappées car on sert directement le dossier `api/` à la racine. Si vous passez par Apache + `.htaccess`, déposez le dossier `backend/api` dans `htdocs/` et appelez `http://localhost/api/...`.

### Endpoints

**Auth & profil**

| Méthode | Route                | Auth | Description                                  |
|---------|----------------------|------|----------------------------------------------|
| POST    | `/api/register`      | non  | Inscription → `{ token, player_id, pseudo }` |
| POST    | `/api/login`         | non  | Connexion                                    |
| GET     | `/api/me`            | oui  | Profil courant (ELO, stats, avatar)          |
| POST    | `/api/me/avatar`     | oui  | Changer d'avatar                             |
| POST    | `/api/me/email`      | oui  | Changer d'email                              |
| POST    | `/api/me/password`   | oui  | Changer de mot de passe                      |

**Social**

| Méthode | Route                       | Auth | Description                     |
|---------|-----------------------------|------|---------------------------------|
| GET     | `/api/friends`              | oui  | Liste d'amis + demandes         |
| POST    | `/api/friends/request`      | oui  | Envoyer une demande d'ami       |
| POST    | `/api/friends/accept`       | oui  | Accepter                        |
| POST    | `/api/friends/decline`      | oui  | Refuser                         |
| POST    | `/api/friends/remove`       | oui  | Supprimer un ami                |
| GET     | `/api/invitations`          | oui  | Invitations en partie reçues    |
| POST    | `/api/invitations/send`     | oui  | Inviter un ami dans sa partie   |
| POST    | `/api/invitations/accept`   | oui  | Accepter (rejoint la partie)    |
| POST    | `/api/invitations/decline`  | oui  | Refuser                         |

**Lobby & jeu**

| Méthode | Route                          | Auth | Description                                     |
|---------|--------------------------------|------|-------------------------------------------------|
| GET     | `/api/games`                   | non  | Parties en `WAITING`                            |
| POST    | `/api/games`                   | oui  | Créer une partie → `{ session_id }`             |
| POST    | `/api/games/practice`          | oui  | Créer une partie d'entraînement avec bots IA    |
| POST    | `/api/games/{id}/join`         | oui  | Rejoindre                                       |
| GET     | `/api/games/{id}/players`      | non  | Joueurs (rôles masqués sauf morts/fin)          |
| GET     | `/api/games/{id}/state`        | oui  | État complet + logs + chat + my_role + timer    |
| POST    | `/api/games/{id}/start`        | oui  | (host) attribue les rôles, démarre la nuit      |
| POST    | `/api/games/{id}/vote`         | oui  | `{ target_id }` — phase NIGHT_WEREWOLF / DAY_VOTE |
| GET     | `/api/games/{id}/votes`        | oui  | Décompte du round courant                       |
| POST    | `/api/games/{id}/action`       | oui  | `{ action_type, target_id }` — SEER_REVEAL, WITCH_HEAL, WITCH_KILL, WITCH_PASS, HUNTER_SHOT |
| POST    | `/api/games/{id}/chat`         | oui  | Envoyer un message (`scope`: ALL ou WEREWOLVES) |

**Historique & classement**

| Méthode | Route               | Auth | Description                          |
|---------|---------------------|------|--------------------------------------|
| GET     | `/api/history`      | oui  | Parties jouées + évolution ELO       |
| GET     | `/api/leaderboard`  | non  | Classement ELO des joueurs           |

L'authentification est un **token Bearer** stocké en BDD :
```
Authorization: Bearer <token>
```

## 2. Installation du frontend (JavaFX)

**Lancer en développement :**
```bash
cd frontend
mvn javafx:run
```

**Construire le JAR exécutable (livrable démo) :**
```bash
cd frontend
mvn package
java -jar target/werewolf-frontend-1.0.0.jar
```
Le fat jar embarque JavaFX et Jackson : il se lance sur n'importe quelle machine avec un JDK 17+, sans Maven.

(Maven téléchargera automatiquement JavaFX 21 et Jackson.)

Si l'URL de l'API n'est pas `http://127.0.0.1:8000/api`, éditer :
```java
// frontend/src/main/java/com/werewolf/service/ApiClient.java
public static String BASE_URL = "http://127.0.0.1:8000/api";
```

## 3. Tester rapidement

1. Lancer le backend (`php -S 127.0.0.1:8000 -t api`)
2. Lancer **4 instances** du client : `mvn javafx:run` (4 terminaux)
3. Chaque instance :
   - S'inscrit avec pseudo + email + mot de passe différents
   - Le premier crée une partie → `WaitingRoom`
   - Les 3 autres rejoignent via le Lobby
   - L'hôte clique **Démarrer**
4. La partie suit le cycle :
   ```
   NIGHT_WEREWOLF → NIGHT_SEER → NIGHT_WITCH → résolution nuit
                ↓
        DAY_VOTE → résolution jour → vérif victoire → NIGHT…
   ```

## Rôles et composition

| Joueurs | Loups | Voyante | Sorcière | Petite Fille | Chasseur | Villageois |
|---------|-------|---------|----------|--------------|----------|------------|
| 4       | 1     | ✅      |          |              |          | 2          |
| 5       | 1     | ✅      | ✅       |              |          | 2          |
| 6       | 2     | ✅      | ✅       | ✅           |          | 1          |
| 7       | 2     | ✅      | ✅       | ✅           | ✅       | 1          |
| 10+     | 3     | ✅      | ✅       | ✅           | ✅       | reste      |

**La Petite Fille** espionne le chat des loups pendant la nuit (lecture seule) — à elle d'utiliser
ce qu'elle entend sans se griller. **Le Capitaine** (désigné au hasard au démarrage, annoncé
publiquement) a une voix qui compte double au vote du village ; à sa mort, il transmet son
écharpe à un survivant.

## Modèle MySQL

- `players`           : compte joueur + token Bearer, ELO, stats, avatar, flag bot
- `game_sessions`     : status, phase, round, timer, chasseur en attente, équipe gagnante
- `session_players`   : rôle attribué, vivant, hôte, potions sorcière, ELO avant/après
- `votes`             : un vote par (joueur, phase, round)
- `night_actions`     : actions Voyante / Sorcière / Chasseur
- `game_logs`         : journal d'événements (visibilité ALL / WEREWOLVES / SELF)
- `chat_messages`     : chat en partie (scope ALL / WEREWOLVES)
- `friendships`       : demandes et liens d'amitié
- `game_invitations`  : invitations à rejoindre une partie

## Polling

Le client JavaFX appelle `GET /state` **toutes les 2 secondes** via une `Timeline` (jamais sur le thread UI). Quand `phase` ou `round` change, l'UI se met à jour avec une animation. Le timer est décrémenté localement chaque seconde mais re-synchronisé à chaque réponse serveur.

## Composants UI mis en œuvre

- `TreeView` : Vivants / Éliminés
- `ListView` + `ComboBox` : sélection de cibles selon le rôle
- `ProgressBar` animée : timer de phase, dégradé multicolore
- `Alert` : révélation de rôle (Voyante), fin de partie
- `Timeline` + `KeyFrame` : polling 2 s + countdown 1 s
- `FadeTransition` : animation sur changement de phase / élimination
- **CSS sombre** : dégradés violets, ambiance mystère, hover/focus stylisés

## 🤖 Activer l'IA Gemini pour les bots (optionnel)

Par défaut, les bots du mode entraînement utilisent des phrases pré-écrites (30 messages génériques). Tu peux activer des dialogues **générés par IA Google Gemini** (gratuit) pour des réactions contextuelles aux événements du jeu.

### Obtenir une clé Gemini (2 min, gratuit)

1. Va sur https://aistudio.google.com/apikey
2. Connecte-toi avec un compte Google
3. **Create API key** → choisis ou crée un projet
4. Copie la clé (format `AIzaSy...`)

> Tier gratuit : 60 requêtes/min, 1500/jour. Sans carte bancaire.

### Configuration

```bash
cd backend/api/
cp config.local.example.php config.local.php
# édite config.local.php et colle ta clé dans GEMINI_API_KEY
```

Le fichier `config.local.php` est ignoré par git, ta clé reste privée.

### Comportement

Les bots réagissent automatiquement à :
- **Changement de phase** (nuit qui tombe, jour qui se lève, vote)
- **Mort d'un joueur** (commentaire selon le rôle révélé)
- **Vote du jour** (réaction aux soupçons)

Throttling : max 1 message IA toutes les 3s par partie pour éviter la surcharge. Les bots restent dans leur rôle (les loups bluffent, les villageois doutent).

## Fonctionnalités notables

- **Chasseur interactif** : à sa mort, une fenêtre de tir de 30 s s'ouvre (la partie est mise en pause), il choisit sa cible via l'UI
- **Petite Fille** : espionne le chat des loups la nuit (dès 6 joueurs)
- **Capitaine** : voix double au vote du village, écharpe transmise à sa mort
- **Chat intégré** : scope `ALL` le jour, canal privé `WEREWOLVES` pour les loups la nuit (visible aussi des morts et de la Petite Fille la nuit)
- **Votes en direct** : décompte des voix affiché pendant le vote du village (et la nuit pour les loups)
- **Effets sonores** : nuit, cloche du jour, hurlement de loup, mort, victoire (synthétisés par `tools/generate_sounds.py`, remplaçables par des `.wav` libres de droits)
- **Classement ELO** (K=32, par équipe) pour les parties classées + leaderboard
- **Système social** : amis, invitations en partie, profil avec avatar et stats
- **Mode entraînement** : partie solo contre des bots qui votent/agissent, avec dialogues générés par IA (Gemini) — chaque bot a une personnalité stable (agressif, peureux, silencieux…)

## Limites (proof of concept)

- Authentification simpliste (token statique en BDD, pas de TTL)
- Pas de WebSocket : le polling 2 s suffit pour 4–10 joueurs
- Capitaine désigné au hasard (pas de phase d'élection) ; la Petite Fille ne risque pas d'être repérée
- Pas de Cupidon, ni de voleur, etc.

## Crédits

Projet pédagogique Master 1 — École 89.
