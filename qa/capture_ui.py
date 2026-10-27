#!/usr/bin/env python3
"""Capturas REALES de Android y comprobación de persistencia tras adb install -r.

El archivo EPUB personal nunca se incorpora al CI. Se capturan estados vacíos reales.
"""
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

SCREENSHOTS = Path("qa/screenshots")
SCREENSHOTS.mkdir(parents=True, exist_ok=True)
REPORT = {"source": "Android Emulator + adb screencap", "screenshots": [],
          "checks": {}, "limitations": [
              "No se importó el EPUB protegido del usuario en CI; no se capturó el lector real.",
              "No se enviaron consultas a Gemini ni se validó el texto litúrgico externo en esta prueba.",
              "No es una prueba en el teléfono físico del usuario."
          ]}

def sh(*args, check=True, capture=True, timeout=75):
    return subprocess.run(args, check=check, stdout=subprocess.PIPE if capture else None,
                          stderr=subprocess.PIPE if capture else None, timeout=timeout)

def adb(*args, check=True, timeout=75):
    return sh("adb", *args, check=check, timeout=timeout).stdout.decode("utf-8", "replace")

def wait(seconds=1.1):
    time.sleep(seconds)

def start():
    adb("shell", "am", "force-stop", "com.scriptura.app")
    adb("shell", "am", "start", "-n", "com.scriptura.app/.MainActivity")
    wait(2.4)

def screen(name):
    path = SCREENSHOTS / (name + ".png")
    image=sh("adb","exec-out","screencap","-p").stdout
    if not image.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError("El emulador no devolvió una captura PNG válida")
    path.write_bytes(image)
    REPORT["screenshots"].append(str(path))
    print("SCREENSHOT",path,len(image))

def dump():
    adb("shell","uiautomator","dump","/sdcard/scriptura_ui.xml",timeout=120)
    xml=adb("exec-out","cat","/sdcard/scriptura_ui.xml")
    start=xml.find("<?xml")
    if start<0:raise RuntimeError("No se pudo obtener XML de UI Automator")
    return ET.fromstring(xml[start:])

def candidates(root,term,strict=False):
    for node in root.iter("node"):
        fields=[node.attrib.get("text",""),node.attrib.get("content-desc","")]
        if any((value==term if strict else term.lower() in value.lower()) for value in fields):
            b=node.attrib.get("bounds","")
            m=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",b)
            if m:
                x1,y1,x2,y2=map(int,m.groups())
                yield x1,y1,x2,y2,node.attrib

def hit(term, strict=False, scroll=False):
    for attempt in range(5 if scroll else 1):
        hits=list(candidates(dump(),term,strict))
        if hits:
            x1,y1,x2,y2,_=hits[0]
            adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2))
            wait()
            return
        if scroll:
            adb("shell","input","swipe","450","1470","450","570","420")
            wait(.6)
    raise RuntimeError("No se encontró el control real: "+term)

def assert_text(term, scroll=False):
    for attempt in range(5 if scroll else 1):
        if list(candidates(dump(),term)):
            print("VISIBLE",term)
            return
        if scroll:
            adb("shell","input","swipe","450","1470","450","570","420")
            wait(.6)
    raise RuntimeError("La pantalla no contiene el texto esperado: "+term)

def app_install(path):
    output=adb("install","-r",path,timeout=120)
    if "Success" not in output:raise RuntimeError("Falló adb install -r: "+output)
    print("INSTALLED",path)

def make_model_setting():
    hit("⚙")
    hit("Cambiar modelo",scroll=True)
    # El cuadro contiene el modelo actual: se sustituye por otro modelo válido
    root=dump()
    entries=[n for n in root.iter("node") if n.attrib.get("class")=="android.widget.EditText"]
    if len(entries)!=1:raise RuntimeError("Esperábamos un solo campo de modelo")
    rect=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",entries[0].attrib["bounds"])
    x1,y1,x2,y2=map(int,rect.groups())
    adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2))
    adb("shell","input","keyevent","123")
    adb("shell","input","keyevent",*["67"]*45)
    adb("shell","input","text","gemini-2.5-flash-lite")
    hit("Guardar",strict=True)
    wait(.5)
    assert_text("Ajustes de Scriptura")
    hit("Cerrar",strict=True)
    hit("⚙")
    assert_text("Cambiar modelo (gemini-2.5-flash-lite)",scroll=True)
    REPORT["checks"]["baseline_model_changed"]="PASS"
    hit("Cerrar",strict=True)

def main():
    base=os.environ["SCRIPTURA_PREVIOUS_APK"]
    candidate=os.environ["SCRIPTURA_CANDIDATE_APK"]
    try:
        print("CHECK: release firmado anterior")
        app_install(base)
        start()
        assert_text("Scriptura")
        make_model_setting()
        screen("00_ajuste_previo")
        print("CHECK: actualización sobre release anterior con adb install -r")
        app_install(candidate)
        start()
        hit("⚙")
        assert_text("Cambiar modelo (gemini-2.5-flash-lite)",scroll=True)
        REPORT["checks"]["settings_persist_after_upgrade"]="PASS"
        screen("01_ajustes")
        hit("Cerrar",strict=True)
        assert_text("Una palabra para hoy")
        screen("02_inicio")
        hit("Buscar")
        assert_text("Buscar en la Biblia")
        screen("03_buscar")
        hit("Misa")
        assert_text("Lecturas de la misa")
        screen("04_misa")
        hit("Para mí")
        assert_text("Una lectura para ti")
        screen("05_para_mi")
        hit("Guardados")
        assert_text("Tus lecturas")
        screen("06_guardados")
        hit("Inicio")
        hit("Explorar atlas bíblico",scroll=True)
        assert_text("Lugares bíblicos")
        screen("07_atlas")
        REPORT["checks"]["five_tabs_navigable"]="PASS"
        REPORT["checks"]["atlas_internal_page"]="PASS"
    except Exception as error:
        REPORT["error"]=str(error)
        REPORT["checks"]["emulator_end_to_end"]="FAIL"
        raise
    else:
        REPORT["checks"]["emulator_end_to_end"]="PASS"
    finally:
        Path("qa").mkdir(exist_ok=True)
        Path("qa/visual-qa-report.json").write_text(json.dumps(REPORT,ensure_ascii=False,indent=2),encoding="utf-8")
        print(json.dumps(REPORT,ensure_ascii=False,indent=2))

if __name__=="__main__":
    main()
