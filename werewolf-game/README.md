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
│   │   ├── auth.php            # /login, /register
│   │   ├── games.php           # lobby + endpoints de jeu
│   │   └── game_logic.php      # attribution rôles + machine à états
│   └── db/schema.sql           # création BDD MySQL
└── frontend/                   # Client JavaFX (Maven)
    ├── pom.xml
    └── src/main/
        ├── java/com/werewolf/
        │   ├── Main.java
        │   ├── Router.java
        │   ├── model/          # POJO (Player, GameSession, Role…)
        │   ├── service/        # ApiClient, AuthService, GameService, Session
        │   └── controller/     # Login, Lobby, WaitingRoom, Game
        └── resources/
            ├── fxml/           # Vues JavaFX (login, lobby, waiting_room, game)
            └── css/dark-theme.css
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
```bash
mysql -u root -p < backend/db/schema.sql
```

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
php -S localhost:8000 -t api
```
Vérification :
```
http://localhost:8000/api/games
→ {"games":[]}
```

> ⚠️ Avec le serveur intégré, les URLs `/api/...` sont mappées car on sert directement le dossier `api/` à la racine. Si vous passez par Apache + `.htaccess`, déposez le dossier `backend/api` dans `htdocs/` et appelez `http://localhost/api/...`.

### Endpoints

| Méthode | Route                          | Auth | Description                                     |
|---------|--------------------------------|------|-------------------------------------------------|
| POST    | `/api/register`                | non  | Inscription → `{ token, player_id, pseudo }`    |
| POST    | `/api/login`                   | non  | Connexion                                       |
| GET     | `/api/games`                   | non  | Parties en `WAITING`                            |
| POST    | `/api/games`                   | oui  | Créer une partie → `{ session_id }`             |
| POST    | `/api/games/{id}/join`         | oui  | Rejoindre                                       |
| GET     | `/api/games/{id}/players`      | non  | Joueurs (rôles masqués sauf morts/fin)          |
| GET     | `/api/games/{id}/state`        | oui  | État complet + logs + my_role + timer           |
| POST    | `/api/games/{id}/start`        | oui  | (host) attribue les rôles, démarre la nuit      |
| POST    | `/api/games/{id}/vote`         | oui  | `{ target_id }` — phase NIGHT_WEREWOLF / DAY_VOTE |
| GET     | `/api/games/{id}/votes`        | oui  | Décompte du round courant                       |
| POST    | `/api/games/{id}/action`       | oui  | `{ action_type, target_id }` — Voyante / Sorcière |

L'authentification est un **token Bearer** stocké en BDD :
```
Authorization: Bearer <token>
```

## 2. Installation du frontend (JavaFX)

```bash
cd frontend
mvn javafx:run
```

(Maven téléchargera automatiquement JavaFX 21 et Jackson.)

Si l'URL de l'API n'est pas `http://localhost:8000/api`, éditer :
```java
// frontend/src/main/java/com/werewolf/service/ApiClient.java
public static String BASE_URL = "http://localhost:8000/api";
```

## 3. Tester rapidement

1. Lancer le backend (`php -S localhost:8000 -t api`)
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

| Joueurs | Loups | Voyante | Sorcière | Chasseur | Villageois |
|---------|-------|---------|----------|----------|------------|
| 4       | 1     | ✅      |          |          | 2          |
| 5       | 1     | ✅      | ✅       |          | 2          |
| 6       | 2     | ✅      | ✅       |          | 2          |
| 7       | 2     | ✅      | ✅       | ✅       | 2          |
| 10+     | 3     | ✅      | ✅       | ✅       | reste      |

## Modèle MySQL

- `players`           : compte joueur + token Bearer
- `game_sessions`     : status, phase, round, timer
- `session_players`   : rôle attribué, vivant, hôte, potions sorcière
- `votes`             : un vote par (joueur, phase, round)
- `night_actions`     : actions Voyante / Sorcière / Chasseur
- `game_logs`         : journal d'événements (visibilité ALL / WEREWOLVES / SELF)

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

## Limites (proof of concept)

- Authentification simpliste (token statique en BDD, pas de TTL)
- Pas de WebSocket : le polling 2 s suffit pour 4–10 joueurs
- Le Chasseur tire **automatiquement** sur une cible aléatoire (non-interactif)
- Pas de Cupidon, petite fille, capitaine, etc.
- Pas de chat texte intégré

## Crédits

Projet pédagogique Master 1 — École 89.
