#!/usr/bin/env python3
"""Build an attributed, reproducible subset of OpenBible's CC-BY-4.0 geography.

Usage: python tools/build_geography_catalog.py /path/to/Bible-Geocoding-Data
No Bible translation, images or OSM polygon geometries are redistributed.
English source names remain available when no Spanish editorial alias exists.
"""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BOOKS = dict(zip(
    'Gen Exod Lev Num Deut Josh Judg Ruth 1Sam 2Sam 1Kgs 2Kgs 1Chr 2Chr Ezra Neh Esth Job Ps Prov Eccl Song Isa Jer Lam Ezek Dan Hos Joel Amos Obad Jonah Mic Nah Hab Zeph Hag Zech Mal Matt Mark Luke John Acts Rom 1Cor 2Cor Gal Eph Phil Col 1Thess 2Thess 1Tim 2Tim Titus Phlm Heb Jas 1Pet 2Pet 1John 2John 3John Jude Rev'.split(),
    'Gn Ex Lv Nm Dt Jos Jc Rt 1S 2S 1R 2R 1Cro 2Cro Esd Ne Est Jb Sal Pr Qo Ct Is Jr Lm Ez Dn Os Jl Am Ab Jon Mi Na Ha So Ag Za Ml Mt Mc Lc Jn Hch Rm 1Co 2Co Ga Ef Flp Col 1Ts 2Ts 1Tm 2Tm Tt Flm Hb St 1P 2P 1Jn 2Jn 3Jn Jds Ap'.split()))
# Explicit identities, never a fuzzy name or coordinate merge.
LEGACY = dict(zip(
    ['Ur 1','Haran','Shechem','Bethel 1','Hebron','Beersheba 1','Egypt','Mount Sinai','Mount Nebo','Jordan','Jericho 1','Jerusalem','Bethlehem 1','Nazareth','Capernaum','Sea of Galilee','Cana','Nain','Magdala','Bethany 1','Tyre','Sidon','Caesarea Philippi','Damascus','Antioch 1','Caesarea','Tarsus','Salamis','Paphos','Philippi','Thessalonica','Athens','Corinth','Ephesus','Malta','Rome'],
    ['ur','haran','shechem','bethel','hebron','beersheba','egypt','sinai','nebo','jordan','jericho','jerusalem','bethlehem','nazareth','capernaum','galilee','cana','nain','magdala','bethany','tyre','sidon','banias','damascus','antioch','caesarea','tarsus','salamis','paphos','philippi','thessalonica','athens','corinth','ephesus','malta','rome']))
SPANISH = {
    'Derbe':'Derbe','Lystra':'Listra','Iconium':'Iconio','Phrygia':'Frigia',
    'Galatia':'Galacia','Asia':'Asia','Mysia':'Misia','Bithynia':'Bitinia',
    'Troas':'Tróade','Macedonia':'Macedonia','Samothrace':'Samotracia',
    'Neapolis':'Neápolis','Thyatira':'Tiatira',
    'Babylon 1':'Babilonia','Zion':'Sión','Assyria':'Asiria','Samaria 1':'Samaria',
    'Moab 1':'Moab','Edom':'Edom','Ammon':'Amón','Canaan':'Canaán','Syria 1':'Siria',
    'Gilead 1':'Galaad','Chaldea':'Caldea','Galilee 1':'Galilea','Lebanon':'Líbano',
    'Bashan':'Basán','Judea 1':'Judea','Midian':'Madián','Sodom':'Sodoma',
    'City of David':'Ciudad de David','Gibeah 1':'Guibeá','Nile':'Nilo',
    'Euphrates':'Éufrates','Negeb':'Négueb','Gibeon':'Gabaón','Gath 1':'Gat',
    'Heshbon':'Jesbón','Shiloh':'Siló','Great Sea':'Mar Mediterráneo',
    'Mount Seir 1':'Monte Seír','Gilgal 1':'Guilgal','Ai 1':'Ay','Mizpah 3':'Mispá',
    'Nineveh':'Nínive','Jezreel 2':'Yezreel','Amalek':'Amalec','Persia':'Persia',
    'Arabah':'Arabá','Dan':'Dan','Mount Zion':'Monte Sión','Gomorrah':'Gomorra',
    'Kadesh-barnea':'Cadés Barnea','Gaza':'Gaza','Lachish':'Laquis',
    'Philistia':'Filistea','Ashdod':'Asdod','Hamath 1':'Jamat','Red Sea 1':'Mar Rojo',
    'Salt Sea':'Mar Muerto','Ekron':'Ecrón','Jabesh-gilead':'Yabés de Galaad',
    'Ramoth-gilead':'Ramot de Galaad','Susa':'Susa','Cush 1':'Cus',
    'Kiriath-jearim':'Quiriat Yearín','Shephelah':'Sefelá','Anathoth':'Anatot',
    'Mount Carmel':'Monte Carmelo','Mount of Olives':'Monte de los Olivos',
    'Mount Horeb':'Monte Horeb','Elam':'Elam','Mahanaim':'Majanáin',
    'Mount Hermon':'Monte Hermón','Tarshish 1':'Tarsis','Babylonia':'Babilonia (región)',
    'Keilah':'Queilá','Geba 1':'Gueba','Gezer':'Guézer','Joppa':'Jope','Kidron':'Cedrón',
    'Patmos':'Patmos','Pergamum':'Pérgamo','Laodicea':'Laodicea','Smyrna':'Esmirna',
    'Sardis':'Sardes','Philadelphia':'Filadelfia','Berea':'Berea','Cyprus':'Chipre',
    'Crete':'Creta','Antioch 2':'Antioquía de Pisidia','Attalia':'Atalía','Perga':'Perge',
    'Rhegium':'Regio','Syracuse':'Siracusa','Puteoli':'Pozzuoli','Ecbatana':'Ecbátana',
}
TYPES = {'settlement':'asentamiento','region':'región','mountain':'montaña',
    'river':'río','valley':'valle','campsite':'campamento','gate':'puerta',
    'island':'isla','hill':'colina','spring':'manantial','body of water':'masa de agua',
    'mountain range':'cordillera','wadi':'cauce estacional','structure':'edificio',
    'natural area':'paraje natural','road':'camino','well':'pozo','garden':'jardín',
    'pool':'estanque','forest':'bosque','altar':'altar'}
ACTS = {
    'Derbe':('recorrido','Pablo llega a Derbe antes de dirigirse a Listra.'),
    'Lystra':('recorrido','En Listra aparece Timoteo, a quien Pablo incorpora al viaje.'),
    'Iconium':('mención','Los hermanos de Iconio y Listra dan buen testimonio de Timoteo; no se narra aquí una visita a Iconio.'),
    'Jerusalem':('mención','Se alude a las decisiones de los apóstoles y presbíteros de Jerusalén, transmitidas a las comunidades.'),
    'Phrygia':('región','Frigia figura entre los territorios atravesados por los viajeros.'),
    'Galatia':('región','La región de Galacia aparece en el desplazamiento misionero. Su delimitación histórica exige cautela.'),
    'Asia':('predicación impedida','Se impide anunciar la palabra en la provincia romana de Asia. Esto no equivale a afirmar que nunca la atravesaran.'),
    'Mysia':('región','El relato sitúa a los viajeros cerca de Misia antes de descender a Tróade; las traducciones matizan de distinta manera ese paso.'),
    'Bithynia':('destino no alcanzado','Intentan entrar en Bitinia, pero el relato dice que no se les permite.'),
    'Troas':('recorrido','En Tróade Pablo tiene la visión del macedonio; desde allí parte la navegación.'),
    'Macedonia':('región','La visión orienta la misión hacia Macedonia, donde se sitúa Filipos.'),
    'Samothrace':('escala','Samotracia es una escala de la navegación desde Tróade hacia Neápolis.'),
    'Neapolis':('recorrido','Llegan a Neápolis por mar y continúan hacia Filipos.'),
    'Philippi':('escenario principal','Filipos es escenario del encuentro con Lidia, el encarcelamiento de Pablo y Silas y el episodio del carcelero.'),
    'Thyatira':('procedencia','Tiatira es la ciudad de procedencia de Lidia. El capítulo no describe un viaje de Pablo a esa ciudad.'),
}

def build(source):
    raw = (source/'data/ancient.jsonl').read_bytes()
    data = [json.loads(line) for line in raw.decode().splitlines()]
    labels_file=ROOT/'tools/geography_es_labels.json'
    labels=json.loads(labels_file.read_text())['labels'] if labels_file.exists() else {}
    # Existing descriptions and coordinates remain the editorial layer.
    java = (ROOT/'app/src/main/java/com/scriptura/app/BibleGeography.java').read_text()
    legacy = {}
    pattern = r'add\("([^"]+)","([^"]+)","([^"]+)",([\d.-]+),([\d.-]+),(\w+),\s*"([^"]+)","([^"]+)","([^"]+)"\)'
    for m in re.finditer(pattern, java):
        id_,name,modern,lat,lon,certainty,description,refs,books = m.groups()
        legacy[id_] = dict(name=name,modern=modern,lat=float(lat),lon=float(lon),
            certainty=certainty,description=description)
    entries=[]
    ids={p['friendly_id']:LEGACY.get(p['friendly_id'],'ob-'+p['id']) for p in data}
    for p in data:
        friendly=p['friendly_id']; id_=ids[friendly]
        # The best-supported identification in the pinned source, never invented.
        ident=sorted(p.get('identifications',[]),key=lambda i:i.get('score',{}).get('time_total',-10000),reverse=True)
        best=ident[0] if ident else {}
        resolutions=best.get('resolutions',[])
        point=next((r for r in resolutions if r.get('lonlat')), {})
        score=best.get('score',{}).get('time_total',0)
        modern=re.sub('<[^>]+>','',best.get('description','')).strip()
        if not modern: modern='Sin identificación moderna documentada'
        types=[t for t in p.get('types',[]) if t!='special']
        kind=TYPES.get(types[0],'lugar') if types else 'lugar'
        certainty='REGION' if point.get('lonlat_type')=='representative point' else ('APPROX' if score>=500 else 'DEBATED')
        wikidata_id=p.get('linked_data',{}).get('s7cc8b2',{}).get('id','')
        name=SPANISH.get(friendly,labels.get(wikidata_id,re.sub(r' \d+$','',friendly)))
        entry=dict(id=id_,name=name,
            aliases=list(dict.fromkeys([name,re.sub(r' \d+$','',friendly),*p.get('translation_name_counts',{}).keys()])),
            modern=modern,kind=kind,certainty=certainty,
            description=f'Ficha geográfica de tipo {kind}. Identificación propuesta en el catálogo: {modern}. Consulta la fuente para las alternativas y su valoración.',
            source='https://www.openbible.info/geo/ancient/'+p['id'],
            source_name='OpenBible.info · CC BY 4.0',original_name=friendly,
            source_score=score,editorial=False,occurrences=[])
        entry['name_language']='es' if friendly in SPANISH or wikidata_id in labels else 'source'
        entry['aliases']=[x for x in entry['aliases'] if x]
        if point:
            lon,lat=map(float,point['lonlat'].split(','))
            entry.update(lat=lat,lon=lon)
        if id_ in legacy:
            entry.update(legacy[id_]);entry['editorial']=True
            entry['aliases']+=entry['name'].split(' / ')
        entry['aliases']=list(dict.fromkeys(entry['aliases']+[entry['name']]))
        for verse in p.get('verses',[]):
            if verse.get('instance_types',{}).get('name',0)<=0:continue
            book,ch,v=verse['osis'].split('.')
            if book in BOOKS:entry['occurrences'].append([BOOKS[book],int(ch),int(v)])
        entries.append(entry)
    by_id={p['id']:p for p in entries}
    chapter=[]
    for friendly,(role,description) in ACTS.items():
        entry=by_id[ids[friendly]]
        chapter.append(dict(place_id=entry['id'],name=SPANISH.get(friendly,entry['name']),
            verses=sorted({v for b,ch,v in entry['occurrences'] if b=='Hch' and ch==16}),
            role=role,description=description))
    chapter.sort(key=lambda m:m['verses'][0])
    revision=subprocess.check_output(['git','-C',str(source),'rev-parse','HEAD'],text=True).strip()
    return dict(version='2026-11-03.1',source_commit=revision,source_sha256=hashlib.sha256(raw).hexdigest(),
        source_name='OpenBible.info · Bible Geocoding Data',license='CC BY 4.0',
        source_url='https://github.com/openbibleinfo/Bible-Geocoding-Data',
        license_url='https://creativecommons.org/licenses/by/4.0/',
        scope='Referencias de 66 libros; no acredita cobertura completa del canon católico ni de cada traducción. Las fichas básicas no son revisiones históricas individuales.',
        entries=entries,chapters=[dict(book='Hch',chapter=16,reviewed=True,
            source='https://bible.usccb.org/es/bible/acts/16',mentions=chapter)])

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('source',type=Path)
    args=parser.parse_args();catalog=build(args.source)
    target=ROOT/'app/src/main/assets/geografia_catalogo.json'
    target.write_text(json.dumps(catalog,ensure_ascii=False,separators=(',',':'))+'\n')
    print(f"{len(catalog['entries'])} fichas; {len(catalog['chapters'][0]['mentions'])} lugares en Hechos 16")
