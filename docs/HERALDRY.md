# Héraldique de Renblood

Les neuf maisons disposent chacune d’un insigne, d’un étendard portable, de quatre pièces d’armure en fer et d’une armure pour cheval. Les statistiques restent celles du fer (5 points pour le cheval).

## Fabrication et équipement

- L’insigne se fabrique avec papier, pépite d’or et teintures propres à la maison. La recette produit quatre insignes.
- La petite bannière utilise l’insigne, la laine et un bâton. Le livre de recettes présente les conversions vers drapeau sur pied, bannière 1×2, grande bannière 2×4 et étendard de tour.
- L’étendard portable utilise le drapeau sur pied, du cuir et deux bâtons. Clic droit depuis la main principale : transfert vers une seconde main vide. La touche F reste disponible. Il s’agit d’un objet cérémoniel, sans protection de bouclier.
- À la table de forge : insigne dans l’emplacement du modèle, pièce en fer au centre, laine de la maison dans l’emplacement d’ajout. Cette transformation conserve les enchantements, le nom et l’usure.
- Même personnalisation pour l’armure de cheval en fer. Une recette est ajoutée pour fabriquer l’armure de cheval de base.
- Le disque Pippin se fabrique avec le disque 13, du papier et des pépites d’or. Les objets royaux ont également leurs recettes.

Les icônes d’inventaire sont distinctes des modèles portés. Les textures d’équipement utilisent les UV natifs de Minecraft et les emblèmes préparés dans `artwork/heraldry`. Les sources graphiques d’origine restent conservées hors du mod. Le manifeste indique les emblèmes préparés avec image_gen et ceux conservés tels quels.

## Préparation graphique

Huit emblèmes ont été préparés avec l’outil image_gen intégré. Le motif Linster conserve l’écu d’origine, la génération de sa retouche ayant été refusée par l’outil. Les consignes sont conservées dans `artwork/heraldry/prompts.json`. Le script `tools/heraldry/generate_assets.py` assemble les textures UV natives et les icônes à partir des sources locales. `tools/heraldry/validate_assets.py` contrôle leur couverture et les références des recettes.
