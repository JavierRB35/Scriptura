#!/usr/bin/env python3
"""E2E del mismo DefinitionUi que usa MainActivity, sin EPUB protegido."""
import json,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
report={"checks":{}}
def adb(*args,check=True,timeout=90):
    p=subprocess.run(["adb",*args],capture_output=True,text=True,timeout=timeout)
    if check and p.returncode:raise RuntimeError("adb failed: "+p.stderr[-500:])
    return p.stdout
def dump():
    for _ in range(5):
        try:
            adb("shell","uiautomator","dump","/sdcard/qa-definir.xml",timeout=60)
            content=adb("shell","cat","/sdcard/qa-definir.xml")
            at=content.find("<?xml")
            if at>=0:return ET.fromstring(content[at:])
        except Exception:pass
        time.sleep(2)
    raise RuntimeError("No se pudo obtener XML de Android")
def find(term,strict=False):
    for node in dump().iter("node"):
        attrs=node.attrib
        texts=[attrs.get("text",""),attrs.get("content-desc","")]
        if not any(x==term if strict else term.lower() in x.lower() for x in texts):continue
        m=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",attrs.get("bounds",""))
        if m:
            x1,y1,x2,y2=map(int,m.groups())
            return ((x1+x2)//2,(y1+y2)//2)
    return None
def hit(term,scroll=False):
    for i in range(5 if scroll else 1):
        v=find(term)
        if v:
            adb("shell","input","tap",str(v[0]),str(v[1]))
            time.sleep(1)
            return
        if scroll:
            adb("shell","input","swipe","420","1050","420","470","420")
            time.sleep(.7)
    raise RuntimeError("No se encontró para pulsar: "+term)
def check(term,key):
    if not find(term):raise RuntimeError("No se encontró: "+term)
    report["checks"][key]="PASS"
def long_press():
    point=find("Fariseos",strict=True)
    if not point:raise RuntimeError("No hay TextView de prueba")
    adb("shell","input","swipe",str(point[0]),str(point[1]),str(point[0]),str(point[1]),"1200")
    time.sleep(1.4)
def define():
    long_press()
    if not find("Definir"):
        for term in ("More options","Más opciones","More","Más"):
            if find(term):
                hit(term)
                break
    check("Definir","action_define_visible")
    if find("Copiar") or find("Copy"):
        report["checks"]["native_copy_preserved"]="PASS"
    else:
        # Algunas versiones envían Copiar al menú flotante de desbordamiento.
        report["checks"]["native_copy_preserved"]="NOT_VISIBLE"
    hit("Definir")
    check("Definición y fuentes","definition_dialog_visible")
def run():
    apk="app/build/outputs/apk/debug/app-debug.apk"
    print(adb("install","-r",apk,timeout=120))
    print(adb("shell","am","start","-n","com.scriptura.app/.DefinitionQaActivity"))
    time.sleep(3)
    for label,part in [
        ("Enciclopedia Católica","https://ec.aciprensa.com/wiki/Fariseos"),
        ("Wikipedia","https://es.wikipedia.org/wiki/Especial:Buscar?search=Fariseos"),
        ("Wikcionario","https://es.wiktionary.org/wiki/Especial:Buscar?search=Fariseos")]:
        define()
        hit(label,scroll=True)
        check("DESTINO: "+part,"opened_"+label.replace(" ","_").lower())
    snap=subprocess.run(["adb","exec-out","screencap","-p"],capture_output=True,check=True)
    Path("qa/definitions_screenshot.png").write_bytes(snap.stdout)
    if report["checks"].get("native_copy_preserved")!="PASS":
        raise RuntimeError("Copiar no se mostró en la barra durante la prueba; requiere revisión")
try:
    run()
    report["outcome"]="PASS"
except Exception as exc:
    report["outcome"]="FAIL"
    report["error"]=str(exc)
    raise
finally:
    Path("qa/definitions_report.json").write_text(json.dumps(report,ensure_ascii=False,indent=2))
    print(json.dumps(report,ensure_ascii=False,indent=2))
