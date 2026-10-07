# Fiche colon style vanilla

## 1. Analyse de l'interface MineColonies actuelle

La fiche colon de MineColonies est un ensemble de fenêtres BlockUI indépendantes. Chacune est une classe Java qui partage une barre d'onglets (`AbstractWindowCitizen`) :

| Page (onglet) | Classe MineColonies | Informations | Actions |
|---|---|---|---|
| Info (liste) | `MainWindowCitizen` | nom, sceau homme/femme, statut visible (icône + infobulle), 10 cœurs (rouges → dorés → verts → bleus au-delà de 20 PV), 10 cuisses (saturation 0-60), 10 smileys (bonheur 0-10), niveau des 11 compétences | en créatif : +/- sur chaque compétence (`AdjustSkillCitizenMessage`) |
| Requêtes (enveloppe) | `RequestWindowCitizen` | arbre des requêtes ouvertes du colon et de son bâtiment : objet, demandeur, sous-requêtes | Détails (`WindowRequestDetail`), Compléter avec son inventaire (`onFulfill` : transfert + requête « OVERRULED »), Annuler (`UpdateRequestStateMessage`) |
| Inventaire (coffre) | `WindowCitizenInventory` (menu serveur `ContainerCitizenInventory`) | inventaire du colon, 4 emplacements d'armure, inventaire du joueur, modèle 3D | glisser-déposer d'objets, armure limitée au niveau du bâtiment |
| Bonheur (smiley) | `HappinessWindowCitizen` | modificateurs de bonheur dont le facteur ≠ 1, avec icône et description en infobulle | aucune |
| Famille (arbre) | `FamilyWindowCitizen` | parents, partenaire, frères et sœurs, enfants | aucune |
| Emploi (coffre +) | `JobWindowCitizen` | métier, compétence principale (100 % XP) et secondaire (50 %), leurs compétences complémentaires (+10/+5 %) et opposées (-10/-5 %) | aucune ; visible seulement si le lieu de travail n'est pas la bibliothèque |
| Debug (comparateur) | `DebugWindowCitizen` | identifiants | historique IA, suivi du pathfinding ; visible seulement en mode debug |

Défauts relevés : cinq fenêtres qui se remplacent sans cohérence visuelle, texte tronqué (« Concentratio »), l'inventaire n'a pas d'onglets pour revenir, les compétences utiles au métier sont sur une autre page que leur niveau, pas de portrait ni d'équipement hors inventaire.

## 2. Nouvelle organisation

Une seule fiche, un seul cadre, des onglets en haut comme l'inventaire créatif :

```
 [Aperçu][Compétences][Inventaire][Requêtes][Bonheur][Famille]        [Debug]
+-----------------------------------------------------------------------------+
| Ryker J. Gill ♂                                                             |
| Livreur - Entrepôt (niv. 2)                                                 |
| +------------- contenu de l'onglet (162 px) --------+ +portrait+ [casque]  |
| |                                                   | |  3D    | [plastron]  |
| |                                                   | |        | [jambières] [main]
| |                                                   | +--------+ [bottes]  [2e main]
| |                                                   |  ♥♥♥♥♥♥♥♥♥♥          |
| |                                                   |  🍗🍗🍗🍗🍗🍗🍗🍗🍗🍗          |
| |                                                   |  ☺☺☺☺☺☺☺☺☺☺          |
| |                                                   |   (icône) Au travail |
+-----------------------------------------------------------------------------+
```

Sous le nom, une ligne d'identité donne le métier et le lieu de travail avec son niveau (celui du bâtiment : un colon n'a pas de niveau). Elle remplace les titres de section, puisque l'onglet sélectionné indique déjà la page. Sur l'onglet Inventaire, les cases commencent juste sous le nom : le métier suit alors le nom sur la même ligne.

La colonne de droite (portrait, équipement, vie, faim, bonheur, activité en cours) est identique sur **toutes** les pages. L'onglet Requêtes affiche le nombre de requêtes ouvertes, comme la taille d'une pile d'objets. Sur l'onglet Inventaire, les 4 cases d'armure sont les vraies cases du menu MineColonies (leur position est imposée par le serveur, c'est elle qui fixe la géométrie de toute la fiche).

| Onglet | Contenu |
|---|---|
| Aperçu | métier, lieu de travail (niveau), logement (niveau), activité actuelle, état (malade / enfant / en pause), nombre de requêtes (lien vers l'onglet), compétences principale et secondaire du métier |
| Compétences | les 11 compétences avec leur niveau (nombre et barre d'XP de villageois, sur 99), et à droite la part d'XP du métier (100 %, 50 %, 10 %, -10 %…) : fusion de l'ancienne page Info et de la page Emploi. Une compétence qui reçoit deux parts affiche leur somme (MineColonies les ajoute et les retire séparément), le détail est en infobulle. En créatif, les boutons +/- remplacent la barre |
| Inventaire | le menu de MineColonies, inchangé, dans le même cadre |
| Requêtes | arbre des requêtes, case d'objet, titre et demandeur sur toute la largeur ; un clic sur la ligne ouvre les détails, les boutons ✔ / ✖ apparaissent au survol (mêmes actions et mêmes conditions que MineColonies) ; l'infobulle donne l'état de la requête et les informations de MineColonies |
| Bonheur | bonheur général (barre d'XP de villageois), tous les modificateurs : d'abord ceux qui agissent (du plus négatif au plus positif), puis les neutres grisés |
| Famille | parents, partenaire, fratrie, enfants ; un clic sur un membre présent dans la colonie ouvre sa fiche |
| Debug | ouvre la fenêtre de debug de MineColonies (seulement en mode debug) |

Toutes les textures viennent du jeu à l'exécution : onglets et barre de défilement de l'inventaire créatif, cases d'inventaire, boutons, cœurs et cuisses du HUD, barre d'XP des villageois, icônes vides d'armure. Seuls les icônes propres à MineColonies (compétences, smileys, cœurs verts/bleus, humeurs, statut) sont lues depuis son jar. Aucun fichier Mojang ou MineColonies n'est copié dans le mod.

## 3. Intégration

- MineColonies continue d'ouvrir ses propres fenêtres (clic droit, dialogues, boutons d'onglets). Au moment où l'une d'elles va s'afficher (`ScreenEvent.Opening`), elle est remplacée par la page correspondante de la fiche. Les messages envoyés à la création des fenêtres (succès « ouvrir la fenêtre ») sont donc conservés, et les clics envoient le même `ClickGuiButtonTriggerMessage` (le succès « Compléter une requête » continue de fonctionner).
- Les actions des requêtes passent par le module de requêtes de la fenêtre MineColonies remplacée : même logique, et la fenêtre Détails revient sur la fiche à sa fermeture.
- Les données sont lues à chaque image depuis les vues client de MineColonies : santé, équipement, compétences, métier, requêtes se mettent à jour d'elles-mêmes.
- En cas d'incompatibilité (future version de MineColonies), l'erreur est journalisée et les fenêtres d'origine reprennent la main pour la session.
- Option de configuration `vanillaCitizenScreen` (Mods > MineColonies Vanilla Tweaks > Config) pour revenir aux fenêtres MineColonies.

## 4. Vérifier la mise en page sans colonie

`./gradlew runPreview` lance le jeu de développement, ouvre chaque page avec un colon fictif, enregistre `run/screenshots/citizen_*.png` puis quitte. Ce code est dans le source set `preview` et n'est jamais inclus dans le jar.
