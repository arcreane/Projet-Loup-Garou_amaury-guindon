<?php
// =====================================================
// Fichier de configuration LOCAL (à copier en config.local.php)
// Ce fichier est ignoré par git, parfait pour les clés secrètes.
// =====================================================
//
// 1. Copie ce fichier en "config.local.php" (dans le même dossier)
// 2. Renseigne ta clé Gemini ci-dessous
//
// Comment obtenir une clé Gemini gratuite :
// 1. Va sur https://aistudio.google.com/apikey
// 2. Connecte-toi avec un compte Google
// 3. Clique "Create API key" (gratuit, sans carte bancaire)
// 4. Copie la clé (commence par "AIza..." ou "AQ....")
//
// Tier gratuit : 60 req/min, 1500 req/jour. Largement suffisant.

define('GEMINI_API_KEY', 'AIza...COLLE_TA_CLE_ICI');
// define('GEMINI_MODEL', 'gemini-2.5-flash-lite');  // optionnel

// Tu peux aussi surcharger les params DB si différents :
// define('DB_HOST', '127.0.0.1:3306');
// define('DB_PASS', 'mon_mot_de_passe_root');
