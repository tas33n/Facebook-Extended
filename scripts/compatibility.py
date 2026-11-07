"""Version mapping policy and generated Kotlin source; no optimistic fallback."""
import json
from tools import ROOT

def select_profile(version):
    profiles=json.loads((ROOT/'compatibility.json').read_text())
    if version not in profiles:
        raise RuntimeError('No reviewed mapping for Facebook '+version+'. Run scripts/discover.py on its merged APK; add/review a profile before building. Existing builds remain untouched.')
    return profiles[version]

def kotlin_profile(version,p):
    keys={'VERSION':version,'PLAYER_CLASS':p['playerClass'],'HEADER_CLASS':p['headerClass'],
          'HEADER_MODEL_FIELD':p['headerModelField'],'LOW_RES_FIELD':p['lowResField'],
          'MODEL_GETTER':p['modelGetter'],'IMAGE_HELPER_CLASS':p['imageHelperClass'],'IMAGE_HELPER_METHOD':p['imageHelperMethod']}
    lines=['package tn.loukious.facebookappadsremover.core','','/** Generated from validated compatibility.json. */','object HostCompatibility {']
    lines += ['    const val '+k+' = '+json.dumps(v) for k,v in keys.items()]
    lines += ['    val COVER_PATH = arrayOf('+', '.join(json.dumps(v) for v in p['coverPath'])+')','}']
    return '\n'.join(lines)+'\n'
