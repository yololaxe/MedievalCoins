from pathlib import Path
from zipfile import ZipFile
from PIL import Image
import json
p=Path(__file__).resolve().parents[2];r=p/'src/main/resources';a=r/'assets/medieval_coins'
houses=['feran','goodbrook','linster','rok','rollingford','roxton','wyne','banefort','chalk']
suffixes=['sigil','portable_standard','iron_helmet','iron_chestplate','iron_leggings','iron_boots','horse_armor','banner','flag','long_banner','grand_banner','tower_flag']
expected={h+'_'+s for h in houses for s in suffixes}|{'renblood_royal_seal','renblood_royal_pedestal','pippin_music_disc'}
expected_full={'medieval_coins:'+x for x in expected}
recipes=[json.loads(f.read_text(encoding='utf-8-sig')) for f in (r/'data/medieval_coins/recipes').glob('*.json')]
results={d['result']['item'] for d in recipes if isinstance(d.get('result'),dict) and 'item' in d['result']}
assert expected_full<=results,expected_full-results
v=ZipFile(r'C:\Users\alban\.gradle\caches\forge_gradle\minecraft_repo\versions\1.20.1\client.jar')
vanilla={n.removeprefix('assets/minecraft/models/item/').removesuffix('.json') for n in v.namelist() if n.startswith('assets/minecraft/models/item/') and n.endswith('.json')}
def check_id(value):
 namespace,ident=value.split(':')
 if namespace=='minecraft':assert ident in vanilla,value
 if namespace=='medieval_coins':assert (a/'models/item'/(ident+'.json')).exists(),value
def item_refs(d):
 if isinstance(d,dict):
  if 'item' in d:check_id(d['item'])
  for value in d.values():item_refs(value)
 elif isinstance(d,list):
  for value in d:item_refs(value)
shapes={}
for d in recipes:
 item_refs(d)
 if d['type']=='minecraft:crafting_shaped':
  rows=['|'.join(d['key'][k].get('item',d['key'][k].get('tag','')) if k!=' ' else '' for k in row) for row in d['pattern']]
  key='\n'.join(rows)
  result=d['result']['item']
  if key in shapes:assert shapes[key]==result,(shapes[key],result)
  shapes[key]=result
for ident in expected:
 f=a/'textures/item'/(ident+'.png');assert f.exists(),f
 im=Image.open(f);assert im.mode=='RGBA' and im.size==(64,64),(f,im.size,im.mode)
 d=json.loads((a/'models/item'/(ident+'.json')).read_text(encoding='utf-8'))
 assert d.get('loader')=='forge:separate_transforms' or d['textures']['layer0']=='medieval_coins:item/'+ident,ident
for house in houses:
 for layer in [1,2]:assert Image.open(a/'textures/models/armor'/f'{house}_layer_{layer}.png').size==(256,128)
 assert Image.open(a/'textures/entity/horse/armor'/f'{house}.png').size==(256,256)
 for kind in ['helmet','chestplate','leggings','boots','horse_armor']:
  ident=house+('_horse_armor' if kind=='horse_armor' else '_iron_'+kind)
  d=json.loads((r/'data/medieval_coins/recipes'/(ident+'.json')).read_text(encoding='utf-8'))
  assert d['type']=='minecraft:smithing_transform'
  assert d['template']['item']=='medieval_coins:'+house+'_sigil'
  assert d['base']['item']=='minecraft:iron_'+kind
 d=json.loads((a/'models/item'/f'{house}_portable_standard.json').read_text(encoding='utf-8'))
 assert 'thirdperson_lefthand' in d['base']['display']
 assert all(-16<=x<=32 for e in d['base']['elements'] for key in ['from','to'] for x in e[key])
for f in r.rglob('*.json'):json.loads(f.read_text(encoding='utf-8-sig'))
print(f'PASS: {len(expected)} heraldic items have icons and recipes; all recipe item references valid; 45 smithing recipes preserve base data; 9 left-hand models and 27 armor UV atlases verified.')
