# Révision des armures et du sceau royal

Les 9 ensembles utilisent des matières acier brossé, tissu aux couleurs de maison, broderies laiton et champs contrastés pour les emblèmes. Les flancs des chevaux sont divisés en bandes avec un grand champ central ; cou, chanfrein et protections de jambes reprennent les mêmes matières. Les 45 icônes d’équipement sont assorties. Le logo royal original est conservé sans modification ; seuls les matériaux du sceau mural et de son piédestal sont retravaillés.

Les textures restent aux dimensions natives multipliées par quatre : armures 256×128, chevaux 256×256, icônes 64×64. Pas de changement des identifiants, recettes, statistiques ou modèles animés. Le logo original de l’utilisateur n’est pas écrasé.

## Sources et régénération

`artwork/heraldry/couture/materials.png` est la source des matières produite par image_gen. `royal-original.png` est une copie exacte du logo fourni par l’utilisateur. `royal.png` est une proposition abandonnée, non utilisée par le mod. `prompts.json` conserve les prompts exacts. `tools/heraldry/couture.py` assemble les matières aux coordonnées UV de Minecraft 1.20.1 et adapte les couleurs. `generate_assets.py` appelle désormais cette étape en dernier. Python/Pillow et le client Minecraft 1.20.1 en cache Forge sont nécessaires.

`docs/heraldry-couture-preview.png` montre les panneaux UV de face et de flanc ainsi que les icônes : il ne s’agit pas d’une capture du jeu. Les 9 maisons doivent être contrôlées en jeu sur joueur debout/en mouvement et cheval sellé/non sellé, en lumière du jour puis à l’ombre. Le modèle cheval vanilla reste cubique ; cette révision travaille ses textures et non sa géométrie.
