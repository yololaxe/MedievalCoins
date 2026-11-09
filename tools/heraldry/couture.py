"""Pack generated textile/metal art into Minecraft's native armor UVs.

Run after generate_assets.py. All coordinates below are vanilla UV pixels;
textures are sampled at 4x. No model or registry changes are required.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageOps, ImageColor, ImageStat
from zipfile import ZipFile
from io import BytesIO
import json

P = Path(__file__).resolve().parents[2]
A = P / 'src/main/resources/assets/medieval_coins'
ART = P / 'artwork/heraldry/couture'
S = 4
V = ZipFile(Path.home()/'.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client.jar')
COLORS = {'feran':'#527d95','goodbrook':'#bfc4bc','linster':'#8d2634','rok':'#31634b','rollingford':'#805235','roxton':'#ac7537','wyne':'#9a2935','banefort':'#909a9b','chalk':'#d4cdb8'}
sheet = Image.open(ART/'materials.png').convert('RGBA')
w,h = sheet.size
cloth = sheet.crop((0,0,w//2,h//2))
steel = sheet.crop((w//2,0,w,h//2))
bordered = sheet.crop((0,h//2,w//2,h))
cuirass = sheet.crop((w//2,h//2,w,h))

def save(im,path):
    path.parent.mkdir(parents=True,exist_ok=True)
    im.save(path)

def native(name):
    return Image.open(BytesIO(V.read('assets/minecraft/textures/'+name+'.png'))).convert('RGBA')

def tint(material,color):
    """Color neutral textile texels; preserve the generated brass embroidery."""
    rgb=ImageColor.getrgb(color)
    im=material.copy()
    mw,mh=im.size
    mid=im.crop((mw//4,mh//4,mw*3//4,mh*3//4)).convert('L')
    neutral=max(1,ImageStat.Stat(mid).mean[0])
    px=[]
    for r,g,b,a in im.getdata():
        if r>b*1.18 and r>g*1.06:
            px.append((r,g,b,a))
        else:
            light=(r+g+b)/3/neutral*.85
            px.append(tuple(min(255,round(c*light)) for c in rgb)+(a,))
    im.putdata(px)
    return im

def paste_uv(im,texture,box):
    x,y,x1,y1=[round(v*S) for v in box]
    im.alpha_composite(texture.resize((x1-x,y1-y),Image.Resampling.LANCZOS),(x,y))

def line(im,xy,fill,width=.25):
    ImageDraw.Draw(im).line([(round(x*S),round(y*S)) for x,y in xy], fill=fill,width=max(1,round(width*S)))

def symbol(house):
    im=Image.open(P/'artwork/heraldry'/f'{house}.png').convert('RGBA')
    return im.crop(im.getchannel('A').getbbox())

def emblem(im,sym,box):
    x,y,x1,y1=[round(v*S) for v in box]
    fitted=ImageOps.contain(sym,(x1-x,y1-y),Image.Resampling.LANCZOS)
    im.alpha_composite(fitted,(x+(x1-x-fitted.width)//2,y+(y1-y-fitted.height)//2))

def metal_base(name):
    original=native(name)
    size=(original.width*S,original.height*S)
    result=steel.resize(size,Image.Resampling.LANCZOS)
    result.putalpha(original.getchannel('A').resize(size,Image.Resampling.NEAREST))
    return result

def belt(im,x,y,width):
    paste_uv(im,cloth,(x,y,x+width,y+1))
    line(im,[(x,y),(x+width-.25,y)],'#514538')
    d=ImageDraw.Draw(im)
    d.rectangle((round((x+width/2-.6)*S),round(y*S),round((x+width/2+.6)*S),round((y+.9)*S)),outline='#c0a16a',width=1)

previews=[]
for house,color in COLORS.items():
    sym=symbol(house)
    textile=tint(bordered,color)
    wool=tint(cloth,color)
    # Contrasting pale heraldic field makes black house emblems readable.
    field=tint(bordered,'#d2c5a0')
    layer1=metal_base('models/armor/iron_layer_1')
    for x in [20,32]:
        paste_uv(layer1,cuirass,(x,20,x+8,32))
        paste_uv(layer1,textile,(x+1.2,20.5,x+6.8,30.2))
        paste_uv(layer1,field,(x+1.7,21.3,x+6.3,27.5))
        emblem(layer1,sym,(x+2,21.7,x+6,27.1))
        belt(layer1,x,29.5,8)
    # Helmet forehead, temple seams, shoulder plates and articulated vambraces.
    paste_uv(layer1,textile,(8,8,16,10))
    line(layer1,[(8,10),(16,10)],'#bfa06b')
    for x in [0,16,24]:
        line(layer1,[(x,8.5),(x+7.75,8.5)],'#bfa06b')
    for x in [40,44,48,52]:
        paste_uv(layer1,textile,(x,20,x+4,23.5))
        for y in [25.5,27.5,29.5]:
            line(layer1,[(x,y),(x+3.75,y)],'#454c50')
            line(layer1,[(x,y+.25),(x+3.75,y+.25)],'#adb3ad')
    for x in [0,4,8,12]:
        for y in [26,28.5]:
            line(layer1,[(x,y),(x+3.75,y)],'#b69a65')
        paste_uv(layer1,wool,(x,30,x+4,31))
    save(layer1,A/'textures/models/armor'/f'{house}_layer_1.png')
    layer2=metal_base('models/armor/iron_layer_2')
    for x in [16,20,28,32]:
        width=8 if x in [20,32] else 4
        paste_uv(layer2,wool,(x,20,x+width,25))
        belt(layer2,x,20,width)
    for x in [0,4,8,12]:
        paste_uv(layer2,textile,(x,20,x+4,25))
        for y in [27,29]:
            line(layer2,[(x,y),(x+3.75,y)],'#525b61')
    save(layer2,A/'textures/models/armor'/f'{house}_layer_2.png')
    horse=metal_base('entity/horse/armor/horse_armor_iron')
    # Horse body UV: two 22x10 sides. Keep the crest square in world space.
    for x in [0,32]:
        paste_uv(horse,textile,(x,54,x+22,64))
        # Color blocks and two broad bands interrupt the previous plain rectangle.
        for stripe in [2,18.5]:
            paste_uv(horse,cuirass,(x+stripe,54,x+stripe+1.5,64))
        paste_uv(horse,field,(x+6,54,x+16,64))
        emblem(horse,sym,(x+6.7,54.6,x+15.3,63.4))
    # Neck sides 7x12, breast 4x12; the rest retains the steel alpha mask.
    for x,width in [(0,7),(7,4),(11,7),(18,4)]:
        paste_uv(horse,textile,(x,42,x+width,54))
    # Head front + shin guards, respecting native UV islands.
    paste_uv(horse,cuirass,(7,20,13,25))
    for x in [48,52,56,60]:
        paste_uv(horse,cuirass,(x,25,x+4,33))
    save(horse,A/'textures/entity/horse/armor'/f'{house}.png')
    # Icons use the same metal and fabric palette within vanilla item silhouettes.
    for kind in ['helmet','chestplate','leggings','boots','horse_armor']:
        src=native('item/iron_'+kind).resize((64,64),Image.Resampling.NEAREST)
        icon=steel.resize((64,64),Image.Resampling.LANCZOS)
        icon.putalpha(src.getchannel('A'))
        decoration=Image.new('RGBA',(64,64))
        if kind=='chestplate':
            paste_uv(decoration,textile,(5,5,11,13));emblem(decoration,sym,(5.6,6,10.4,11.5))
        elif kind=='horse_armor':
            paste_uv(decoration,textile,(3,5,13,11));paste_uv(decoration,field,(6,5,11,11));emblem(decoration,sym,(6.3,5.4,10.7,10.6))
        elif kind=='helmet':paste_uv(decoration,textile,(2,5,14,7))
        else:
            for x in [3,9]:paste_uv(decoration,textile,(x,6,x+3,9))
        icon.alpha_composite(decoration)
        icon.putalpha(src.getchannel('A'))
        name=house+('_horse_armor' if kind=='horse_armor' else '_iron_'+kind)
        save(icon,A/'textures/item'/f'{name}.png')
    previews.append((house,layer1.crop((80,80,112,128)),horse.crop((0,216,88,256))))

# Preserve the user's original kingdom logo byte-for-byte; only the mount is redesigned.
import shutil
shutil.copyfile(ART/'royal-original.png', A/'textures/block/renblood_royal_emblem.png')
canvas=Image.open(ART/'royal-original.png').convert('RGBA')
for kind in ['seal','pedestal']:
    icon=Image.new('RGBA',(64,64))
    if kind=='pedestal':
        d=ImageDraw.Draw(icon);d.rectangle((10,55,54,62),fill='#444e58');d.rectangle((25,42,39,56),fill='#aa8952')
    emblem(icon,canvas,(1,0,15,14 if kind=='pedestal' else 16))
    save(icon,A/'textures/item'/f'renblood_royal_{kind}.png')
# Royal mount materials use generated metal instead of bright vanilla gold blocks.
save(tint(steel,'#b89b61').resize((32,32)),A/'textures/block/royal_brass.png')
save(tint(steel,'#46515d').resize((32,32)),A/'textures/block/royal_steel.png')
for f in (A/'models/block').glob('renblood_royal*.json'):
    data=json.loads(f.read_text(encoding='utf-8'))
    if 'gold' in data.get('textures',{}):data['textures']['gold']='medieval_coins:block/royal_brass'
    if 'metal' in data.get('textures',{}):data['textures']['metal']='medieval_coins:block/royal_steel'
    f.write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')

preview=Image.new('RGB',(960,1050),'#161b22');d=ImageDraw.Draw(preview)
d.text((24,16),'RENBLOOD / ARMOR TEXTURE STUDY - native UV panels, not in-game rendering',fill='#d7c29b')
for i,(name,chest,flank) in enumerate(previews):
    y=50+i*108
    d.text((24,y+42),name.upper(),fill='#e3d6bd')
    preview.paste(chest.resize((64,96),Image.Resampling.NEAREST),(155,y),chest.resize((64,96),Image.Resampling.NEAREST))
    flank=flank.resize((220,100),Image.Resampling.NEAREST);preview.paste(flank,(250,y),flank)
    for j,kind in enumerate(['helmet','chestplate','leggings','boots','horse_armor']):
        name2=name+('_horse_armor' if kind=='horse_armor' else '_iron_'+kind)
        ico=Image.open(A/'textures/item'/f'{name2}.png');preview.paste(ico,(500+j*86,y+20),ico)
save(preview,P/'docs/heraldry-couture-preview.png')
print('Couture: 27 equipment atlases, 47 icons, royal seal and mount materials packed.')
