from pathlib import Path
from zipfile import ZipFile
from PIL import Image, ImageDraw, ImageOps
from io import BytesIO
import json, shutil
p=Path(__file__).resolve().parents[2];r=p/'src/main/resources';a=r/'assets/medieval_coins'
sources={h: str(p/'artwork/heraldry'/(h+'.png')) for h in ['feran','goodbrook','linster','rok','rollingford','roxton','wyne','banefort','chalk']}
vanilla=ZipFile(r'C:\Users\alban\.gradle\caches\forge_gradle\minecraft_repo\versions\1.20.1\client.jar')
houses={'feran':('#6da7bd','light_blue'),'goodbrook':('#deded8','white'),'linster':('#ae2026','red'),'rok':('#246f40','green'),'rollingford':('#8b4e20','brown'),'roxton':('#ba842d','orange'),'wyne':('#b8272c','red'),'banefort':('#9b9fa0','light_gray'),'chalk':('#dedecb','white')}
S=4
def put(f,t):f.parent.mkdir(parents=True,exist_ok=True);f.write_text(t,encoding='utf-8')
def out(f,d):put(f,json.dumps(d,ensure_ascii=False,indent=2)+'\n')
def save(im,f):f.parent.mkdir(parents=True,exist_ok=True);im.save(f)
def base(path,scale=4):return Image.open(BytesIO(vanilla.read('assets/minecraft/textures/'+path+'.png'))).convert('RGBA').resize((Image.open(BytesIO(vanilla.read('assets/minecraft/textures/'+path+'.png'))).width*scale,Image.open(BytesIO(vanilla.read('assets/minecraft/textures/'+path+'.png'))).height*scale),Image.Resampling.NEAREST)
def fit(im,w,h):return ImageOps.contain(im,(w,h),Image.Resampling.LANCZOS)
def stamp(canvas,emblem,x,y,w,h):
 im=fit(emblem,round(w),round(h));canvas.alpha_composite(im,(round(x+(w-im.width)/2),round(y+(h-im.height)/2)))
def rect(im,coords,color):ImageDraw.Draw(im).rectangle(tuple(round(v*S) for v in coords),fill=color)
def panel(im,coords,color):
 x0,y0,x1,y1=coords;rect(im,(x0,y0,x1-.25,y1-.25),'#d1ab52');rect(im,(x0+.3,y0+.4,x1-.55,y1-.55),color)
def generated(name):return {'parent':'minecraft:item/generated','textures':{'layer0':'medieval_coins:item/'+name}}
def separate(name,main):return {'loader':'forge:separate_transforms','gui_light':'front','textures':{'particle':'medieval_coins:item/'+name},'base':main,'perspectives':{context:generated(name) for context in ['gui','ground','fixed']}}
def cube(lo,hi,tex):return {'from':lo,'to':hi,'faces':{side:{'texture':'#'+tex,'uv':[0,0,16,16]} for side in ['north','south','east','west','up','down']}}
def emblem_plane(lo,hi):return {'from':lo,'to':hi,'shade':False,'faces':{side:{'texture':'#crest','uv':[0,0,16,16]} for side in ['north','south']}}
def icon_banner(emblem,color,kind):
 im=Image.new('RGBA',(64,64));d=ImageDraw.Draw(im)
 if kind in ['flag','tower_flag','portable_standard']:
  d.rectangle((8,4,11,60),fill='#292b32');d.rectangle((7,3,12,6),fill='#e6bd57')
  d.polygon([(12,9),(57,9),(54,24),(58,41),(12,41)],fill=color,outline='#d4ac4c',width=2)
  stamp(im,emblem,21,12,28,26)
  if kind!='portable_standard':d.rectangle((2,58,20,62),fill='#555861')
  if kind=='tower_flag':d.rectangle((10,42,12,57),fill='#d4ac4c')
 else:
  x0,x1=(20,48) if kind=='long_banner' else ((11,55) if kind=='grand_banner' else (15,51))
  d.rectangle((x0,10,x1,57),fill=color,outline='#d5ac4e',width=2);d.rectangle((x0-4,7,x1+4,11),fill='#d5ac4e');stamp(im,emblem,x0+3,18,x1-x0-6,30)
 return im

manifest={}
for house,(color,wool) in houses.items():
 source=Path(sources.get(house) or rf'D:\Projets\Projet\Image\blason\{house.title()}.png')
 original=Image.open(source).convert('RGBA');bbox=original.getchannel('A').getbbox();symbol=original.crop(bbox) if bbox else original
 emblem=Image.new('RGBA',(128,128));stamp(emblem,symbol,8,8,112,112)
 # Native game textures: pack the prepared symbol into the precise vanilla UV slots.
 save(emblem,a/'textures/block/houses'/f'{house}.png')
 art=p/'artwork/heraldry'/f'{house}.png';art.parent.mkdir(parents=True,exist_ok=True);
 if source.resolve()!=art.resolve():shutil.copyfile(source,art)
 manifest[house]={'source':str(source),'prepared_with':'image_gen' if house != 'linster' else 'original coat of arms retained','asset':str(art)}
 for layer in [1,2]:
  im=base(f'models/armor/iron_layer_{layer}')
  if layer==1:
   for x in [20,32]:
    panel(im,(x,20,x+8,32),color);stamp(im,emblem,(x+.8)*S,22*S,6.4*S,7.4*S)
   rect(im,(8,8,16,9),color);stamp(im,emblem,10*S,9*S,4*S,3*S)
   for x in [4,44]:rect(im,(x,28,x+4,29),color);rect(im,(x,29,x+4,29.5),'#d1ab52')
   panel(im,(44,20,48,23),color)
  else:
   panel(im,(20,20,28,24),color)
   for x in [4,12]:panel(im,(x,20,x+2,30),color)
  save(im,a/'textures/models/armor'/f'{house}_layer_{layer}.png')
 horse=base('entity/horse/armor/horse_armor_iron')
 for x in [0,32]:
  panel(horse,(x+.5,54.3,x+21.5,64),color);stamp(horse,emblem,(x+7)*S,55*S,8*S,8*S)
 save(horse,a/'textures/entity/horse/armor'/f'{house}.png')
 for kind in ['helmet','chestplate','leggings','boots','horse_armor']:
  name=house+('_horse_armor' if kind=='horse_armor' else '_iron_'+kind)
  im=base('item/iron_'+kind)
  d=ImageDraw.Draw(im);d.ellipse((36,35,63,62),fill=color,outline='#d2ad57',width=2);stamp(im,emblem,39,38,21,21)
  save(im,a/'textures/item'/f'{name}.png');out(a/'models/item'/f'{name}.json',generated(name))
 name=house+'_sigil';im=Image.new('RGBA',(64,64));d=ImageDraw.Draw(im)
 d.polygon([(17,2),(47,2),(62,17),(62,47),(47,62),(17,62),(2,47),(2,17)],fill='#d8b45b')
 d.ellipse((7,7,57,57),fill=color,outline='#30313b',width=3);stamp(im,emblem,13,13,38,38)
 save(im,a/'textures/item'/f'{name}.png');out(a/'models/item'/f'{name}.json',generated(name))
 for kind in ['banner','flag','long_banner','grand_banner','tower_flag','portable_standard']:
  name=house+'_'+kind;im=icon_banner(emblem,color,kind);save(im,a/'textures/item'/f'{name}.png')
  if kind=='portable_standard':
   main={'render_type':'minecraft:cutout','textures':{'pole':'minecraft:block/dark_oak_log','gold':'minecraft:block/gold_block','cloth':'minecraft:block/'+wool+'_wool','crest':'medieval_coins:block/houses/'+house,'particle':'minecraft:block/'+wool+'_wool'},'elements':[cube([7,3,7],[8,32,8],'pole'),cube([6.5,30.5,6.5],[8.5,32,8.5],'gold'),cube([8,17,7],[30,30,7.6],'cloth'),cube([8,29.5,6.9],[30,30.3,7.7],'gold'),cube([29.3,17,6.9],[30,30,7.7],'gold'),emblem_plane([12.5,17.3,6.97],[25.5,29.7,7.63])],'display':{'thirdperson_lefthand':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]},'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[1,1,1]},'firstperson_lefthand':{'rotation':[0,-25,0],'translation':[-3,-1,0],'scale':[.6,.6,.6]},'firstperson_righthand':{'rotation':[0,25,0],'translation':[-3,-1,0],'scale':[.6,.6,.6]}}}
  else:
   path=a/'models/item'/f'{name}.json';old=json.loads(path.read_text(encoding='utf-8'));main=old.get('base',old)
  out(a/'models/item'/f'{name}.json',separate(name,main))

royal=Image.open(a/'textures/block/renblood_royal_emblem.png').convert('RGBA')
for kind in ['seal','pedestal']:
 name='renblood_royal_'+kind;im=Image.new('RGBA',(64,64));d=ImageDraw.Draw(im)
 if kind=='pedestal':d.rectangle((10,55,54,62),fill='#3a3c44');d.rectangle((25,42,39,56),fill='#d6ad49');stamp(im,royal,7,1,50,50)
 else:d.ellipse((3,3,61,61),fill='#222633',outline='#d9b850',width=3);stamp(im,royal,8,8,48,48)
 save(im,a/'textures/item'/f'{name}.png');path=a/'models/item'/f'{name}.json';old=json.loads(path.read_text(encoding='utf-8'));out(path,separate(name,old.get('base',old)))
im=Image.new('RGBA',(64,64));d=ImageDraw.Draw(im);d.ellipse((2,2,62,62),fill='#252832',outline='#b89740',width=2);d.ellipse((20,20,44,44),fill='#e7bd57');d.ellipse((29,29,35,35),fill='#262831');d.rectangle((36,9,39,20),fill='#e7bd57');d.ellipse((31,17,39,23),fill='#e7bd57');save(im,a/'textures/item/pippin_music_disc.png');out(a/'models/item/pippin_music_disc.json',generated('pippin_music_disc'))
out(p/'artwork/heraldry/manifest.json',manifest)
put(p/'docs/HERALDRY.md','''# Héraldique de Renblood

Les neuf maisons disposent chacune d’un insigne, d’un étendard portable, de quatre pièces d’armure en fer et d’une armure pour cheval. Les statistiques restent celles du fer (5 points pour le cheval).

## Fabrication et équipement

- L’insigne se fabrique avec papier, pépite d’or et teintures propres à la maison. La recette produit quatre insignes.
- La petite bannière utilise l’insigne, la laine et un bâton. Le livre de recettes présente les conversions vers drapeau sur pied, bannière 1×2, grande bannière 2×4 et étendard de tour.
- L’étendard portable utilise le drapeau sur pied, du cuir et deux bâtons. Clic droit depuis la main principale : transfert vers une seconde main vide. La touche F reste disponible. Il s’agit d’un objet cérémoniel, sans protection de bouclier.
- À la table de forge : insigne dans l’emplacement du modèle, pièce en fer au centre, laine de la maison dans l’emplacement d’ajout. Cette transformation conserve les enchantements, le nom et l’usure.
- Même personnalisation pour l’armure de cheval en fer. Une recette est ajoutée pour fabriquer l’armure de cheval de base.
- Le disque Pippin se fabrique avec le disque 13, du papier et des pépites d’or. Les objets royaux ont également leurs recettes.

Les icônes d’inventaire sont distinctes des modèles portés. Les textures d’équipement utilisent les UV natifs de Minecraft et les emblèmes préparés dans `artwork/heraldry`. Les sources graphiques d’origine restent conservées hors du mod. Le manifeste indique les emblèmes préparés avec image_gen et ceux conservés tels quels.
''')
print('Armor UV atlases, horse armor textures, 111 heraldic item icons and native hand models generated.')

# Apply the couture materials last so regeneration preserves the visual revision.
import runpy
runpy.run_path(str(p / "tools/heraldry/couture.py"), run_name="__main__")
