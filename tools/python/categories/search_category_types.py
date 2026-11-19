#!/usr/bin/env python3
"""Generates the place types listed in "All categories" on the Android search screen.

Writes android/app/src/main/assets/search_category_types.json: one entry per searchable place type with
  t: type (e.g. "amenity-dentist"); the app shows its translated name (sdk types_strings.xml)
  i: map icon in the symbols atlas, empty if none
  g: section key (see GROUPS; names are app strings search_group_<key>)
  q: English term to search for it (search always matches English, so it works in every UI language)
  s: more English terms, used only to filter the list

Run from the repo root after generating drawing rules (data/drules_proto_default_light.txt):
  tools/python/categories/search_category_types.py
"""
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
SYNONYMS = os.path.join(ROOT, 'data/translations/categories-strings/en.json/localize.json')
TYPE_NAMES = os.path.join(ROOT, 'android/sdk/src/main/res/values/types_strings.xml')
DRULES = os.path.join(ROOT, 'data/drules_proto_default_light.txt')
SYMBOLS = os.path.join(ROOT, 'data/styles/default/light/symbols')
OUT = os.path.join(ROOT, 'android/app/src/main/assets/search_category_types.json')

# Section order in the app.
GROUPS = ['food', 'shops', 'health', 'services', 'transport', 'stay', 'sport', 'culture', 'nature', 'public',
          'everyday']

# Not useful to browse, or only reachable by other means.
SKIP = re.compile(r'brothel|stripclub|love_hotel|erotic|condoms|DoNotMatch|swimming_pool-private|guardhouse|'
                  r'hunting_stand|petroleum_well|flare|chimney|crane|survey_point|-mast$|^club|^craft-(metal|sawmill|'
                  r'grinding)|^office$|^building|^place-|^highway-(path|ford|elevator)|waste_transfer|money_lender|'
                  r'mobile_money|^natural-(water|bay|strait|cape)|^waterway-(canal|lock_gate)|^landuse|-private$')

# Where the first English term finds other types too, search with a more specific one.
QUERY_OVERRIDES = {
    'shop-bicycle': 'Cycling shop',
    'leisure-dog_park': 'Dog park',
}

# Cuisines have no map icon of their own: show the restaurant icon.
CUISINE_ICON_TYPE = 'amenity-restaurant'


def group(t):
    a, _, rest = t.partition('-')
    b = rest.split('-')[0] if rest else ''
    if a == 'cuisine' or t in ('amenity-restaurant', 'amenity-cafe', 'amenity-fast_food', 'amenity-bar', 'amenity-pub',
                               'amenity-biergarten', 'amenity-ice_cream', 'amenity-food_court', 'amenity-nightclub'):
        return 'food'
    if a == 'shop':
        return 'shops'
    if a == 'healthcare' or t.startswith(('amenity-hospital', 'amenity-clinic', 'amenity-doctors', 'amenity-dentist',
                                          'amenity-pharmacy', 'amenity-veterinary', 'emergency-defibrillator',
                                          'amenity-nursing_home')):
        return 'health'
    if a == 'tourism' and b in ('hotel', 'hostel', 'motel', 'guest_house', 'camp_site', 'caravan_site', 'alpine_hut',
                                'wilderness_hut', 'apartment', 'chalet', 'resort'):
        return 'stay'
    if (a in ('highway', 'railway', 'aeroway', 'aerialway', 'barrier') or (a == 'waterway' and b != 'waterfall')
            or t.startswith(('amenity-bus', 'amenity-fuel', 'amenity-charging', 'amenity-parking', 'amenity-bicycle',
                             'amenity-car_', 'amenity-motorcycle', 'amenity-taxi', 'amenity-ferry', 'amenity-boat',
                             'amenity-compressed_air', 'amenity-vehicle'))):
        return 'transport'
    if a in ('leisure', 'sport'):
        return 'sport'
    if (a in ('historic', 'attraction', 'tourism') or t.startswith(('amenity-theatre', 'amenity-cinema',
                                                                    'amenity-arts_centre', 'amenity-planetarium',
                                                                    'amenity-casino', 'amenity-stage',
                                                                    'amenity-events_venue'))
            or t in ('man_made-lighthouse', 'man_made-windmill', 'man_made-observatory')):
        return 'culture'
    if a in ('natural', 'boundary', 'mountain_pass') or t == 'waterway-waterfall':
        return 'nature'
    if t.startswith(('amenity-school', 'amenity-university', 'amenity-college', 'amenity-kindergarten',
                     'amenity-library', 'amenity-townhall', 'amenity-courthouse', 'amenity-police',
                     'amenity-fire_station', 'amenity-place_of_worship', 'amenity-community_centre', 'amenity-prison',
                     'amenity-childcare', 'amenity-music_school', 'amenity-language_school', 'amenity-driving_school',
                     'amenity-research', 'amenity-social', 'amenity-prep_school', 'amenity-flight_school',
                     'amenity-sailing_school', 'social_facility', 'office-diplomatic', 'office-government')):
        return 'public'
    if t.startswith(('amenity-bank', 'amenity-atm', 'amenity-bureau', 'amenity-post', 'amenity-parcel',
                     'amenity-money', 'amenity-payment')) or a in ('craft', 'office'):
        return 'services'
    return 'everyday'


def load_icons():
    """type -> map icon drawn at the most detailed zoom that has one."""
    icons = {}
    cur, scale = None, 0
    with open(DRULES, encoding='utf-8') as f:
        for line in f:
            if line.startswith('  name: "'):
                cur = line.split('"')[1]
                continue
            m = re.match(r'\s*scale: (\d+)', line)
            if m:
                scale = int(m.group(1))
                continue
            m = re.match(r'\s+name: "([^"]+)"', line)
            if m and cur:
                best = icons.get(cur)
                if not best or scale >= best[0]:
                    icons[cur] = (scale, m.group(1))
    return {t: name for t, (_, name) in icons.items() if os.path.isfile(os.path.join(SYMBOLS, name + '.svg'))}


def main():
    if not os.path.isfile(DRULES):
        sys.exit(f'{DRULES} not found: run tools/unix/generate_drules.sh first')
    names = {m.group(1).replace('.', '-') for m in
             re.finditer(r'<string name="type\.([^"]+)">', open(TYPE_NAMES, encoding='utf-8').read())}
    icons = load_icons()
    synonyms = json.load(open(SYNONYMS, encoding='utf-8'))

    def words_of(value):
        words = [re.sub(r'^\d', '', w).strip() for w in value.split('|')]
        return [w for w in words if w and not w.upper().startswith('U+')]

    groups = {k: words_of(v['defaultMessage']) for k, v in synonyms.items() if k.startswith('@')}
    terms = {}
    for key, value in synonyms.items():
        parts = key.split('|')
        # A type's own terms first, then those it borrows from @groups in the same key.
        words = words_of(value['defaultMessage']) + [w for g in parts if g.startswith('@') for w in groups.get(g, [])]
        for t in parts:
            if not t.startswith('@') and words:
                terms.setdefault(t, words)

    entries = []
    for t in sorted(terms):
        if t not in names or SKIP.search(t):
            continue
        icon = icons.get(t) or (icons.get(CUISINE_ICON_TYPE, '') if t.startswith('cuisine-') else '')
        words = list(dict.fromkeys(terms[t]))
        entries.append({'t': t, 'i': icon, 'g': group(t), 'q': QUERY_OVERRIDES.get(t, words[0]), 's': words[1:8]})

    with open(OUT, 'w', encoding='utf-8') as f:
        json.dump(entries, f, ensure_ascii=False, separators=(',', ':'))
        f.write('\n')
    counts = {g: sum(1 for e in entries if e['g'] == g) for g in GROUPS}
    print(f'{len(entries)} types written to {os.path.relpath(OUT, ROOT)}: {counts}')


if __name__ == '__main__':
    main()
