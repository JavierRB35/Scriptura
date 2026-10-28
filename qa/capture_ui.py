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

def start(candidate=False):
    adb("shell", "am", "force-stop", "com.scriptura.app")
    # Ambas versiones 0.7.5 y 0.7.6 usan SplashActivity como launcher.
    # MainActivity no se exporta: adb no puede abrirla directamente.
    activity="com.scriptura.app/.SplashActivity"
    adb("shell", "am", "start", "-n",activity)
    adb("shell","wm","dismiss-keyguard",check=False)
    if candidate:
        # Esperar a que Android dibuje el splash para evitar capturar
        # la ventana de lanzamiento en blanco antes del primer frame.
        wait(.65)
        screen("01_splash_real")
    wait(5)
    # El título «Scriptura» ya aparece en el splash; esperar al control
    # accesible de Ajustes de MainActivity antes de seguir con el ensayo.
    for attempt in range(12):
        if list(candidates(dump(),"Abrir ajustes")):
            return
        wait(1)
    screen("arranque_diagnostico")
    raise RuntimeError("Inicio no aparece tras SplashActivity")

def screen(name):
    path = SCREENSHOTS / (name + ".png")
    image=sh("adb","exec-out","screencap","-p").stdout
    if not image.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError("El emulador no devolvió una captura PNG válida")
    path.write_bytes(image)
    REPORT["screenshots"].append(str(path))
    print("SCREENSHOT",path,len(image))

def dump():
    # UIAutomator puede tardar varios segundos tras el arranque del emulador.
    # adb shell cat es más fiable que exec-out en algunas imágenes Android 34.
    detail=""
    for attempt in range(9):
        try:
            output=adb("shell","uiautomator","dump","/sdcard/scriptura_ui.xml",timeout=65)
            xml=adb("shell","cat","/sdcard/scriptura_ui.xml",timeout=20)
            start=xml.find("<?xml")
            if start>=0:
                root=ET.fromstring(xml[start:])
                visible=[n.attrib.get("text","") for n in root.iter("node")]
                if any("isn't responding" in v or "no responde" in v.lower()
                       for v in visible):
                    # Solo en el emulador de CI: no pulsar nunca "Close app".
                    # Un aviso ANR del proceso SYSTEM puede superponerse a la
                    # interfaz correcta de Scriptura después del arranque.
                    waits=[n for n in root.iter("node")
                           if n.attrib.get("text","") in ("Wait","Esperar")]
                    if waits:
                        m=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",
                                   waits[0].attrib.get("bounds",""))
                        if m:
                            x1,y1,x2,y2=map(int,m.groups())
                            adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2),check=False)
                            print("QA: descartado aviso ANR del sistema pulsando Esperar")
                            wait(2.5)
                            continue
                return root
            detail="dump="+output[:140].strip()+"; bytes="+str(len(xml))
        except (subprocess.SubprocessError,ET.ParseError) as error:
            detail=type(error).__name__
        if attempt==0:
            adb("shell","input","keyevent","82",check=False)
        wait(3)
    raise RuntimeError("UIAutomator no devolvió XML; "+detail)

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
    # Deslizar por el margen evita desplazar el mapa anidado en el ScrollView.
    for attempt in range(5 if scroll else 1):
        hits=list(candidates(dump(),term,strict))
        if hits:
            x1,y1,x2,y2,_=hits[0]
            adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2))
            wait()
            return
        if scroll:
            adb("shell","input","swipe","22","1430","22","515","500")
            wait(.6)
    raise RuntimeError("No se encontró el control real: "+term)

def assert_text(term, scroll=False):
    # Los mapas consumen gestos verticales; usar el margen lateral del contenedor.
    for attempt in range(5 if scroll else 1):
        if list(candidates(dump(),term)):
            print("VISIBLE",term)
            return
        if scroll:
            adb("shell","input","swipe","22","1430","22","515","500")
            wait(.6)
    raise RuntimeError("La pantalla no contiene el texto esperado: "+term)

def app_install(path):
    process=sh("adb","install","-r",path,check=False,timeout=120)
    stdout=process.stdout.decode("utf-8","replace")
    stderr=process.stderr.decode("utf-8","replace")
    result=(stdout+" "+stderr).strip()
    if process.returncode!=0 or "Success" not in stdout:
        raise RuntimeError("Fallo adb install -r: "+result[-700:])
    print("INSTALLED",path)

def make_font_setting():
    hit("Abrir ajustes")
    # Ajustes se presenta en un AlertDialog centrado. Los gestos en el margen
    # x=22 sirven para el atlas, pero quedan FUERA del diálogo de Ajustes.
    for attempt in range(8):
        if list(candidates(dump(),"Aumentar tamaño de letra")):
            hit("Aumentar tamaño de letra")
            REPORT["checks"]["baseline_font_increased"]="PASS"
            break
        adb("shell","input","swipe","500","1230","500","475","420")
        wait(.6)
    else:
        screen("00_ajustes_diagnostico")
        raise RuntimeError("No se encontró Aumentar tamaño de letra dentro de Ajustes")
    # La pantalla principal se ha regenerado; cerrar la ficha de ajustes si aún está encima.
    adb("shell","input","keyevent","4")
    wait(1)

def main():
    base=os.environ["SCRIPTURA_PREVIOUS_APK"]
    candidate=os.environ["SCRIPTURA_CANDIDATE_APK"]
    try:
        print("CHECK: release firmado anterior")
        app_install(base)
        start()
        screen("00_version_anterior")
        assert_text("Scriptura")
        make_font_setting()
        screen("00_ajuste_previo")
        print("CHECK: actualización sobre release anterior con adb install -r")
        app_install(candidate)
        start(candidate=True)
        screen("01_inicio")
        hit("Abrir ajustes")
        # Verificar dentro del diálogo modal, no en el margen externo.
        for attempt in range(8):
            if list(candidates(dump(),"Tamaño de letra actual: 20 sp")):
                REPORT["checks"]["font_setting_persist_after_upgrade"]="PASS"
                break
            adb("shell","input","swipe","500","1230","500","475","420")
            wait(.6)
        else:
            screen("01_ajustes_diagnostico")
            raise RuntimeError("El tamaño de letra anterior no se conserva o no es visible")
        screen("01_ajustes")
        adb("shell","input","keyevent","4")
        wait(1)
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
        hit("Recorridos",scroll=True)
        hit("Ver recorrido",scroll=True)
        assert_text("Lugares del recorrido",scroll=True)
        adb("shell","input","swipe","420","1320","420","570","450")
        wait(16)
        screen("08_mapa_real_abraham")
        assert_text("teselas de fondo cargadas",scroll=True)
        REPORT["checks"]["real_osm_tile_loaded"]="PASS"
        assert_text("Etapas y acontecimientos",scroll=True)
        assert_text("La familia de Abram sale de Ur",scroll=True)
        assert_text("Leer Gn 11:27-31",scroll=True)
        REPORT["checks"]["journey_stage_narration_and_reading_link"]="PASS"
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
