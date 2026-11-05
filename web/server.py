#!/usr/bin/env python3
"""Scriptura Web: biblioteca y lector local para PC. Solo biblioteca estándar de Python."""
from __future__ import annotations

import argparse
import html
from html.parser import HTMLParser
import io
import json
import mimetypes
import os
from pathlib import Path
import posixpath
import re
import shutil
import sqlite3
import sys
import tempfile
import uuid
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, unquote, urlsplit
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parent
DATA = Path(os.environ.get("SCRIPTURA_DATA_DIR", ROOT / "data")).resolve()
UPLOADS = DATA / "uploads"
DB = DATA / "scriptura-web.db"
MAX_FILE = 80 * 1024 * 1024
EXTS = {".epub", ".pdf", ".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg",
        ".txt", ".md", ".docx", ".xlsx", ".kml", ".gpx", ".zip"}
IMAGES = {".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg"}
BOOKS = [
("Gn","Génesis"),("Ex","Éxodo"),("Lv","Levítico"),("Nm","Números"),("Dt","Deuteronomio"),
("Jos","Josué"),("Jc","Jueces"),("Rt","Rut"),("1S","1 Samuel"),("2S","2 Samuel"),
("1R","1 Reyes"),("2R","2 Reyes"),("1Cro","1 Crónicas"),("2Cro","2 Crónicas"),
("Esd","Esdras"),("Ne","Nehemías"),("Tb","Tobías"),("Jdt","Judit"),("Est","Ester"),
("1M","1 Macabeos"),("2M","2 Macabeos"),("Jb","Job"),("Sal","Salmos"),("Pr","Proverbios"),
("Qo","Eclesiastés"),("Ct","Cantar de los Cantares"),("Sb","Sabiduría"),("Si","Eclesiástico"),
("Is","Isaías"),("Jr","Jeremías"),("Lm","Lamentaciones"),("Ba","Baruc"),("Ez","Ezequiel"),
("Dn","Daniel"),("Os","Oseas"),("Jl","Joel"),("Am","Amós"),("Ab","Abdías"),
("Jon","Jonás"),("Mi","Miqueas"),("Na","Nahúm"),("Ha","Habacuc"),("So","Sofonías"),
("Ag","Ageo"),("Za","Zacarías"),("Ml","Malaquías"),("Mt","Mateo"),("Mc","Marcos"),
("Lc","Lucas"),("Jn","Juan"),("Hch","Hechos"),("Rm","Romanos"),("1Co","1 Corintios"),
("2Co","2 Corintios"),("Ga","Gálatas"),("Ef","Efesios"),("Flp","Filipenses"),
("Col","Colosenses"),("1Ts","1 Tesalonicenses"),("2Ts","2 Tesalonicenses"),
("1Tm","1 Timoteo"),("2Tm","2 Timoteo"),("Tt","Tito"),("Flm","Filemón"),("Hb","Hebreos"),
("St","Santiago"),("1P","1 Pedro"),("2P","2 Pedro"),("1Jn","1 Juan"),
("2Jn","2 Juan"),("3Jn","3 Juan"),("Jds","Judas"),("Ap","Apocalipsis")
]
BOOK_NAMES = dict(BOOKS)

def conn():
    c = sqlite3.connect(DB, timeout=30)
    c.row_factory = sqlite3.Row
    c.execute("PRAGMA foreign_keys=ON")
    return c

def init_db():
    UPLOADS.mkdir(parents=True, exist_ok=True)
    with conn() as c:
        c.executescript("""
            CREATE TABLE IF NOT EXISTS documents (
                id INTEGER PRIMARY KEY, name TEXT NOT NULL, title TEXT NOT NULL,
                extension TEXT NOT NULL, category TEXT NOT NULL, tags TEXT NOT NULL DEFAULT '',
                stored TEXT NOT NULL UNIQUE, size INTEGER NOT NULL,
                created TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
            CREATE TABLE IF NOT EXISTS verses (
                book TEXT NOT NULL, chapter INTEGER NOT NULL, verse INTEGER NOT NULL,
                text TEXT NOT NULL, PRIMARY KEY(book,chapter,verse)
            );
            CREATE INDEX IF NOT EXISTS idx_verses_lookup ON verses(book,chapter);
            CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS favorites (
                ref TEXT PRIMARY KEY, created TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
            CREATE TABLE IF NOT EXISTS history (
                ref TEXT PRIMARY KEY, seen TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
            CREATE TABLE IF NOT EXISTS links (
                id INTEGER PRIMARY KEY,
                kind TEXT NOT NULL CHECK(kind IN ('bible','epub','pdf')),
                target TEXT NOT NULL,
                start_offset INTEGER, end_offset INTEGER,
                quote TEXT NOT NULL, note TEXT NOT NULL DEFAULT '',
                image_id INTEGER NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
                created TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
            CREATE INDEX IF NOT EXISTS idx_links_target ON links(kind,target);
            CREATE TABLE IF NOT EXISTS references_extra (
                id INTEGER PRIMARY KEY,
                kind TEXT NOT NULL CHECK(kind IN ('bible','epub','pdf')),
                target TEXT NOT NULL,
                start_offset INTEGER, end_offset INTEGER,
                quote TEXT NOT NULL, note TEXT NOT NULL DEFAULT '',
                resource_kind TEXT NOT NULL CHECK(resource_kind IN ('url','document')),
                document_id INTEGER REFERENCES documents(id) ON DELETE CASCADE,
                url TEXT, label TEXT NOT NULL DEFAULT '',
                created TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CHECK ((resource_kind='url' AND url IS NOT NULL AND document_id IS NULL)
                    OR (resource_kind='document' AND document_id IS NOT NULL AND url IS NULL))
            );
            CREATE INDEX IF NOT EXISTS idx_refs_extra_target ON references_extra(kind,target);
            CREATE TABLE IF NOT EXISTS notes (
                id INTEGER PRIMARY KEY,
                kind TEXT NOT NULL CHECK(kind IN ('bible','epub','pdf')),
                target TEXT NOT NULL,
                start_offset INTEGER, end_offset INTEGER,
                quote TEXT NOT NULL DEFAULT '',
                body TEXT NOT NULL,
                created TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
            CREATE INDEX IF NOT EXISTS idx_notes_target ON notes(kind,target);
        """)

def safe_name(name):
    return Path(str(name).replace("\\", "/")).name[:180].strip()

def get_doc(c, ident):
    row = c.execute("SELECT * FROM documents WHERE id=?", (ident,)).fetchone()
    if row is None:
        raise ValueError("Documento no encontrado")
    return row

def doc_path(row):
    return UPLOADS / row["stored"]

def plain_html_fragment(s):
    """Conversión conservadora de XHTML a texto: nunca ejecuta scripts del EPUB."""
    p = TextOnly()
    p.feed(s)
    p.close()
    return p.text()

class TextOnly(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.output = []
        self.skip = 0
    def handle_starttag(self, tag, attrs):
        if tag in ("script", "style", "svg"):
            self.skip += 1
        elif not self.skip and tag in ("p", "div", "h1", "h2", "h3", "h4", "li", "blockquote", "br"):
            self.output.append("\n")
    def handle_endtag(self, tag):
        if tag in ("script", "style", "svg") and self.skip:
            self.skip -= 1
        elif not self.skip and tag in ("p", "div", "h1", "h2", "h3", "h4", "li", "blockquote"):
            self.output.append("\n")
    def handle_data(self, data):
        if not self.skip:
            self.output.append(data)
    def text(self):
        s = "".join(self.output).replace("\u00a0", " ").replace("\ufeff", "")
        s = re.sub(r"[ \t]+", " ", s)
        s = re.sub(r"\n[ \t]*\n+", "\n\n", s)
        return s.strip()

def epub_spine(path):
    """Obtiene los capítulos en orden de lectura a partir de EPUB container/OPF/spine."""
    with zipfile.ZipFile(path) as z:
        if "META-INF/container.xml" not in z.namelist():
            raise ValueError("EPUB sin META-INF/container.xml")
        if len(z.infolist()) > 12000 or sum(max(0, i.file_size) for i in z.infolist()) > 350_000_000:
            raise ValueError("EPUB excesivamente grande o comprimido")
        container = ET.fromstring(z.read("META-INF/container.xml")[:128000])
        rootfile = next((x.attrib.get("full-path") for x in container.iter() if x.tag.endswith("rootfile")), None)
        if not rootfile:
            raise ValueError("EPUB sin índice de contenidos")
        if rootfile not in z.namelist():
            raise ValueError("EPUB incompleto")
        opf = ET.fromstring(z.read(rootfile)[:2_000_000])
        items = {}
        spine = []
        for x in opf.iter():
            if x.tag.endswith("item"):
                items[x.attrib.get("id")] = (x.attrib.get("href",""), x.attrib.get("media-type",""))
            if x.tag.endswith("itemref"):
                spine.append(x.attrib.get("idref"))
        base = posixpath.dirname(rootfile)
        chapters = []
        for item_id in spine[:1500]:
            href, media = items.get(item_id, ("",""))
            candidate = posixpath.normpath(posixpath.join(base, unquote(href.split("#")[0])))
            if candidate not in z.namelist() or not candidate.lower().endswith((".xhtml",".html",".htm")):
                continue
            info = z.getinfo(candidate)
            if info.file_size > 2_000_000:
                continue
            chapters.append(candidate)
        if not chapters:
            raise ValueError("No se encontraron capítulos legibles en el EPUB")
        return chapters

def epub_chapter(path, index):
    chapters = epub_spine(path)
    if index < 0 or index >= len(chapters):
        raise ValueError("Capítulo fuera del índice")
    with zipfile.ZipFile(path) as z:
        raw = z.read(chapters[index])
    raw_text = raw.decode("utf-8-sig", errors="replace")
    return {"index": index, "total": len(chapters), "title": f"Sección {index+1}", "text": plain_html_fragment(raw_text)}

PARA = re.compile(r"<p\b([^>]*)>(.*?)</p\s*>", re.I | re.S)
CLASS = re.compile(r"""class=["']([^"']+)""", re.I)
MARK = re.compile(r"""<(?:a|b|span)\b[^>]*\bid=["']tx-([A-Za-z0-9]+)-(\d+)-(\d+)(?:m)?["'][^>]*>""",re.I|re.S)
PART = re.compile(r".*/Text/part(\d+)(?:_split_\d+)?\.html$",re.I)

def normalized_fragment(value, drop_num=False):
    s = html.unescape(re.sub(r"<[^>]+>", "", value))
    s = re.sub(r"\s+", " ", s.replace("\ufeff","").replace("\u00a0"," ")).strip()
    return re.sub(r"^\d+\s*", "", s) if drop_num else s

def parse_navarra_epub(path):
    """Replica la detección de versículos tx- del importador Android, no cualquier EPUB."""
    verses = {}
    current = None
    fragments = []
    seen_files = 0
    def flush():
        if current and fragments:
            value = re.sub(r" +", " ", " ".join(fragments)).strip()
            if value:
                verses[current] = value
    def append_piece(piece, drop_num=False):
        s = normalized_fragment(piece, drop_num)
        if s and current:
            fragments.append(s)
    with zipfile.ZipFile(path) as z:
        if "META-INF/container.xml" not in z.namelist():
            raise ValueError("No es un EPUB válido")
        total = 0
        for item in z.infolist():
            match = PART.fullmatch(item.filename)
            if not match or not 6 <= int(match.group(1)) <= 235:
                continue
            total += item.file_size
            seen_files += 1
            if item.file_size > 3_000_000 or total > 180_000_000 or seen_files > 1500:
                raise ValueError("EPUB supera los límites de seguridad")
            raw = z.read(item).decode("utf-8", errors="replace")
            for attrs, content in PARA.findall(raw):
                cm = CLASS.search(attrs)
                if not cm or not re.fullmatch(r"(tx[a-z0-9]*|h6|h7|h9|h13|h21|h22|s)",cm.group(1).split()[0],re.I):
                    continue
                markers = list(MARK.finditer(content))
                seen = False
                pos = 0
                for marker in markers:
                    if current and not seen and marker.start() > 0:
                        prefix = content[:marker.start()]
                        if "tx-Cap-" not in prefix:
                            append_piece(prefix)
                    if current and seen:
                        append_piece(content[pos:marker.start()], True)
                    book, chap, verse = marker.group(1), int(marker.group(2)), int(marker.group(3))
                    if book not in BOOK_NAMES or not 1 <= chap <= 200 or not 1 <= verse <= 200:
                        pos = marker.end()
                        continue
                    flush()
                    current = (book, chap, verse)
                    fragments = []
                    seen = True
                    pos = marker.end()
                if seen:
                    append_piece(content[pos:], True)
                elif current:
                    append_piece(content)
        flush()
    if len(verses) < 25000 or {key[0] for key in verses} != set(BOOK_NAMES):
        raise ValueError(f"Formato Navarra no reconocido o incompleto: {len(verses)} versículos, {len({x[0] for x in verses})} libros. No se modifica la Biblia anterior.")
    return verses


SVG_NS = "http://www.w3.org/2000/svg"
SVG_TAGS = {
    "svg", "g", "defs", "symbol", "use", "path", "circle", "ellipse",
    "rect", "line", "polyline", "polygon", "text", "tspan", "textPath",
    "linearGradient", "radialGradient", "stop", "clipPath", "mask", "pattern",
    "title", "desc", "marker"
}
SVG_ATTRS = {
    "xmlns", "version", "width", "height", "viewBox", "preserveAspectRatio",
    "x", "y", "x1", "x2", "y1", "y2", "cx", "cy", "r", "rx", "ry",
    "dx", "dy", "d", "points", "transform", "fill", "fill-opacity",
    "stroke", "stroke-width", "stroke-opacity", "stroke-linecap",
    "stroke-linejoin", "stroke-dasharray", "stroke-dashoffset",
    "opacity", "id", "class", "clip-path", "mask", "fill-rule",
    "clip-rule", "font-family", "font-size", "font-weight", "font-style",
    "text-anchor", "dominant-baseline", "letter-spacing", "word-spacing",
    "gradientUnits", "gradientTransform", "spreadMethod",
    "offset", "stop-color", "stop-opacity", "patternUnits",
    "patternContentUnits", "patternTransform", "marker-start",
    "marker-mid", "marker-end", "vector-effect", "paint-order",
    "display", "visibility", "orient", "refX", "refY",
    "markerWidth", "markerHeight", "markerUnits", "aria-label", "role"
}
SVG_STYLE_KEYS = {
    "fill", "fill-opacity", "stroke", "stroke-width", "stroke-opacity",
    "stroke-linecap", "stroke-linejoin", "stroke-dasharray",
    "opacity", "font-family", "font-size", "font-weight", "font-style",
    "text-anchor", "stop-color", "stop-opacity", "display",
    "visibility", "fill-rule", "clip-rule", "paint-order"
}
def clean_svg_style(raw):
    pairs = []
    for entry in raw.split(";"):
        if not entry.strip():
            continue
        if ":" not in entry:
            raise ValueError("Estilo SVG no reconocido")
        k, value = (p.strip() for p in entry.split(":", 1))
        if k.lower() not in SVG_STYLE_KEYS:
            continue
        if not safe_svg_value(value):
            raise ValueError("SVG contiene recursos externos o estilos no permitidos")
        pairs.append(k.lower()+":"+value)
    return ";".join(pairs)

def safe_svg_value(value):
    if len(value)>5000 or any(ord(c)<32 for c in value):
        return False
    candidate = re.sub(r"url\(\s*['\"]?#[A-Za-z0-9_.:-]+['\"]?\s*\)",
                       "LOCAL_REFERENCE",value,flags=re.I)
    return not re.search(r"url\s*\(|@import|expression\s*\(|javascript:|data:|https?:|<|>|\\",
                         candidate,re.I)

def sanitize_svg(path):
    """Keep only inert SVG drawing primitives; never retain scripts, events or external files."""
    if path.stat().st_size > 5_000_000:
        raise ValueError("SVG demasiado grande (máximo 5 MB)")
    source = path.read_bytes()
    if re.search(rb"<!DOCTYPE|<!ENTITY|<\?xml-stylesheet",source,re.I):
        raise ValueError("El SVG contiene definiciones externas prohibidas")
    try:
        tree = ET.fromstring(source)
    except ET.ParseError as exc:
        raise ValueError("El SVG no contiene XML válido") from exc
    if tree.tag != "{"+SVG_NS+"}svg":
        raise ValueError("La imagen no contiene una raíz SVG válida")
    count = 0
    def clean(node):
        nonlocal count
        count+=1
        if count>30000:
            raise ValueError("SVG demasiado complejo")
        if not isinstance(node.tag,str):
            raise ValueError("SVG contiene nodos no admitidos")
        tag=node.tag.split("}",1)[-1]
        # Inkscape/Sodipodi y algunos editores añaden metadatos no visuales.
        if tag in ("metadata","namedview"):
            return None
        if not node.tag.startswith("{"+SVG_NS+"}"):
            raise ValueError("SVG contiene elementos externos no admitidos")
        if tag not in SVG_TAGS:
            raise ValueError("SVG contiene elementos activos o no admitidos: "+tag)
        out=ET.Element(node.tag)
        for attr,value in node.attrib.items():
            local=attr.split("}",1)[-1]
            if local.lower().startswith("on"):
                raise ValueError("SVG contiene eventos ejecutables")
            if local in ("href","xlink:href"):
                if tag!="use" or not re.fullmatch(r"#[A-Za-z0-9_.:-]{1,120}",value):
                    raise ValueError("SVG contiene vínculos externos")
                out.set("href",value)
            elif local=="style":
                cleaned=clean_svg_style(value)
                if cleaned: out.set("style",cleaned)
            elif local in SVG_ATTRS:
                if not safe_svg_value(value):
                    raise ValueError("SVG contiene atributos no seguros")
                out.set(local,value)
        if tag in ("title","desc","text","tspan","textPath"):
            out.text=node.text or ""
        for child in node:
            sanitized_child=clean(child)
            if sanitized_child is None:
                continue
            out.append(sanitized_child)
            if child.tail:
                out[-1].tail=child.tail if tag in ("text","tspan","textPath") else None
        return out
    sanitized=clean(tree)
    ET.register_namespace("",SVG_NS)
    path.write_bytes(ET.tostring(sanitized,encoding="utf-8",xml_declaration=True))

def validate_file(path, ext):
    with path.open("rb") as f:
        signature = f.read(16)
    if ext == ".svg":
        sanitize_svg(path)
    if ext == ".pdf" and not signature.startswith(b"%PDF-"):
        raise ValueError("PDF no válido")
    if ext == ".epub":
        epub_spine(path)
    if ext == ".png" and not signature.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError("PNG no válido")
    if ext in (".jpg", ".jpeg") and not signature.startswith(b"\xff\xd8"):
        raise ValueError("JPEG no válido")
    if ext == ".gif" and not signature.startswith((b"GIF87a", b"GIF89a")):
        raise ValueError("GIF no válido")
    if ext == ".webp" and not (signature.startswith(b"RIFF") and signature[8:12] == b"WEBP"):
        raise ValueError("WEBP no válido")

class Handler(BaseHTTPRequestHandler):
    server_version = "ScripturaWeb/0.1.2"
    def log_message(self, fmt, *args):
        sys.stderr.write("[%s] %s\n" % (self.log_date_time_string(), fmt % args))

    def headers_common(self):
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("X-Frame-Options", "SAMEORIGIN")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Security-Policy",
            "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: blob:; "
            "frame-src 'self' blob:; object-src 'self' blob:; connect-src 'self'; "
            "base-uri 'none'; form-action 'self'; frame-ancestors 'none'")
    def respond(self, status, body, mime="application/json; charset=utf-8", disposition=None):
        if not isinstance(body, bytes):
            body = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.headers_common()
        self.send_header("Content-Type",mime)
        self.send_header("Content-Length",str(len(body)))
        if disposition:
            self.send_header("Content-Disposition",disposition)
        self.end_headers()
        self.wfile.write(body)
    def fail(self, exc):
        message = str(exc)
        code = 400 if isinstance(exc,(ValueError,zipfile.BadZipFile,ET.ParseError)) else 500
        self.respond(code, {"error": message if code==400 else "Error interno del servidor"})
        if code == 500:
            self.log_message("Error: %r",exc)
    def path_params(self):
        u = urlsplit(self.path)
        return u.path, {k:v[0] for k,v in parse_qs(u.query).items()}
    def json_body(self):
        length = int(self.headers.get("Content-Length","0"))
        if length > 100_000 or length < 1:
            raise ValueError("JSON vacío o demasiado grande")
        return json.loads(self.rfile.read(length))
    def check_host(self):
        from urllib.parse import urlsplit
        host = self.headers.get("Host","")
        valid = {"127.0.0.1:"+str(self.server.server_port),
                 "localhost:"+str(self.server.server_port)}
        if host not in valid:
            raise ValueError("Host no permitido: accede mediante http://127.0.0.1")
    def check_origin(self):
        origin = self.headers.get("Origin")
        if origin and origin != "http://" + self.headers.get("Host",""):
            raise ValueError("Origen no permitido")
    def file_upload(self, q):
        length = int(self.headers.get("Content-Length","0"))
        if not 0 < length <= MAX_FILE:
            raise ValueError("Archivo vacío o de más de 80 MB")
        name = safe_name(q.get("filename",""))
        ext = Path(name).suffix.lower()
        if not name or ext not in EXTS:
            raise ValueError("Tipo de archivo no admitido")
        stored = uuid.uuid4().hex + ext
        dest = UPLOADS / stored
        try:
            left = length
            with dest.open("xb") as stream:
                while left:
                    chunk = self.rfile.read(min(65536,left))
                    if not chunk:
                        raise ValueError("Carga incompleta")
                    stream.write(chunk)
                    left -= len(chunk)
            validate_file(dest,ext)
            return name, ext, stored, dest.stat().st_size
        except Exception:
            dest.unlink(missing_ok=True)
            raise
    def do_GET(self):
        try:
            self.check_host()
            p, q = self.path_params()
            if p in ("/","/index.html","/app.js","/annotations.js","/style.css"):
                file = ROOT / (p.lstrip("/") or "index.html")
                mime = "text/html; charset=utf-8" if p in ("/","/index.html") else ("text/javascript; charset=utf-8" if p in ("/app.js","/annotations.js") else "text/css; charset=utf-8")
                return self.respond(200,file.read_bytes(),mime)
            if p == "/api/health":
                return self.respond(200,{"ok":True,"version":"0.1.2"})
            with conn() as c:
                if p == "/api/status":
                    return self.respond(200,{
                        "verses":c.execute("SELECT COUNT(*) FROM verses").fetchone()[0],
                        "documents":c.execute("SELECT COUNT(*) FROM documents").fetchone()[0],
                        "links":(c.execute("SELECT COUNT(*) FROM links").fetchone()[0] +
                                 c.execute("SELECT COUNT(*) FROM references_extra").fetchone()[0]),
                        "edition":(c.execute("SELECT value FROM settings WHERE key='edition'").fetchone() or [""])[0]
                    })
                if p == "/api/books":
                    rows = c.execute("SELECT book,MAX(chapter) chapters FROM verses GROUP BY book").fetchall()
                    chapters = {r["book"]:r["chapters"] for r in rows}
                    return self.respond(200,[{"code":code,"name":name,"chapters":chapters.get(code,0)} for code,name in BOOKS])
                if p == "/api/verses":
                    book = q.get("book","")
                    chapter = int(q.get("chapter","1"))
                    if book not in BOOK_NAMES or not 1 <= chapter <= 200: raise ValueError("Referencia inválida")
                    rows = c.execute("SELECT verse,text FROM verses WHERE book=? AND chapter=? ORDER BY verse",(book,chapter)).fetchall()
                    return self.respond(200,{"book":book,"name":BOOK_NAMES[book],"chapter":chapter,"verses":[dict(r) for r in rows]})
                if p == "/api/search":
                    term = q.get("q","").strip()[:120]
                    if len(term)<2: return self.respond(200,[])
                    rows = c.execute("SELECT book,chapter,verse,text FROM verses WHERE text LIKE ? LIMIT 80",("%"+term+"%",)).fetchall()
                    return self.respond(200,[dict(r) for r in rows])
                if p == "/api/documents":
                    term = q.get("q","")[:120]
                    category = q.get("category","")
                    sql = "SELECT id,name,title,extension,category,tags,size,created FROM documents WHERE (title LIKE ? OR tags LIKE ?)"
                    args = ["%"+term+"%","%"+term+"%"]
                    if category:
                        sql += " AND category=?"
                        args.append(category)
                    sql += " ORDER BY created DESC, id DESC"
                    return self.respond(200,[dict(r) for r in c.execute(sql,args).fetchall()])
                if p == "/api/favorites":
                    return self.respond(200,[dict(r) for r in c.execute("SELECT ref,created FROM favorites ORDER BY created DESC").fetchall()])
                if p == "/api/history":
                    return self.respond(200,[dict(r) for r in c.execute("SELECT ref,seen FROM history ORDER BY seen DESC LIMIT 60").fetchall()])
                if p == "/api/notes":
                    kind, target = q.get("kind",""), q.get("target","")
                    sql="SELECT * FROM notes WHERE 1=1"
                    args=[]
                    if kind: sql+=" AND kind=?"; args.append(kind)
                    if target: sql+=" AND target=?"; args.append(target)
                    sql+=" ORDER BY updated DESC,id DESC"
                    return self.respond(200,[dict(row) for row in c.execute(sql,args)])
                if p == "/api/references":
                    kind, target = q.get("kind",""), q.get("target","")
                    sql = ("SELECT r.*,d.title document_title,d.extension document_extension "
                           "FROM references_extra r LEFT JOIN documents d ON d.id=r.document_id WHERE 1=1")
                    args = []
                    if kind: sql += " AND r.kind=?"; args.append(kind)
                    if target: sql += " AND r.target=?"; args.append(target)
                    sql += " ORDER BY r.created DESC,r.id DESC"
                    return self.respond(200,[dict(r) for r in c.execute(sql,args).fetchall()])
                if p == "/api/links":
                    kind, target = q.get("kind",""), q.get("target","")
                    sql = "SELECT l.*,d.title image_title FROM links l JOIN documents d ON d.id=l.image_id WHERE 1=1"
                    args = []
                    if kind: sql += " AND l.kind=?"; args.append(kind)
                    if target: sql += " AND l.target=?"; args.append(target)
                    sql += " ORDER BY l.created DESC,l.id DESC"
                    return self.respond(200,[dict(r) for r in c.execute(sql,args).fetchall()])
                m = re.fullmatch(r"/api/documents/(\d+)/chapters",p)
                if m:
                    row = get_doc(c,int(m.group(1)))
                    if row["extension"]!=".epub": raise ValueError("No es un EPUB")
                    return self.respond(200, [{"index":i,"title":f"Sección {i+1}"} for i,_ in enumerate(epub_spine(doc_path(row)))])
                m = re.fullmatch(r"/api/documents/(\d+)/chapter/(\d+)",p)
                if m:
                    row = get_doc(c,int(m.group(1)))
                    if row["extension"]!=".epub": raise ValueError("No es un EPUB")
                    return self.respond(200,epub_chapter(doc_path(row),int(m.group(2))))
                m = re.fullmatch(r"/api/documents/(\d+)/file",p)
                if m:
                    row = get_doc(c,int(m.group(1)))
                    ext = row["extension"]
                    mime = {".pdf":"application/pdf",".png":"image/png",".jpg":"image/jpeg",
                            ".jpeg":"image/jpeg",".webp":"image/webp",".gif":"image/gif",
                            ".svg":"image/svg+xml"}.get(ext,"application/octet-stream")
                    disposition = ("inline" if ext in IMAGES or ext==".pdf" else "attachment") + '; filename="scriptura-file'+ext+'"'
                    return self.respond(200,doc_path(row).read_bytes(),mime,disposition)
            if p == "/api/backup":
                return self.backup()
            return self.respond(404,{"error":"Ruta no encontrada"})
        except Exception as e:
            self.fail(e)

    def do_POST(self):
        try:
            self.check_host()
            self.check_origin()
            p,q = self.path_params()
            if p == "/api/upload":
                name,ext,stored,size = self.file_upload(q)
                category = q.get("category","Otros")[:60]
                if category not in ("Libros","Mapas","Imágenes","Documentos","Otros"):category="Otros"
                try:
                    with conn() as c:
                        cur = c.execute("INSERT INTO documents(name,title,extension,category,stored,size) VALUES(?,?,?,?,?,?)",
                                        (name,Path(name).stem,ext,category,stored,size))
                        ident = cur.lastrowid
                except Exception:
                    (UPLOADS / stored).unlink(missing_ok=True)
                    raise
                return self.respond(201,{"id":ident,"name":name})
            body = self.json_body()
            with conn() as c:
                if p == "/api/import-bible":
                    ident = int(body.get("document_id",0))
                    row = get_doc(c,ident)
                    if row["extension"]!=".epub":raise ValueError("Selecciona un EPUB de Navarra")
                    verses = parse_navarra_epub(doc_path(row))
                    c.execute("DELETE FROM verses")
                    c.executemany("INSERT INTO verses(book,chapter,verse,text) VALUES(?,?,?,?)",
                                  [(b,ch,v,txt) for (b,ch,v),txt in verses.items()])
                    c.execute("INSERT OR REPLACE INTO settings(key,value) VALUES('edition',?)",("Sagrada Biblia · Universidad de Navarra (ejemplar privado)",))
                    c.commit()
                    return self.respond(200,{"imported":len(verses)})
                if p == "/api/favorites":
                    ref = str(body.get("ref",""))[:120]
                    if not ref:raise ValueError("Referencia vacía")
                    if c.execute("SELECT 1 FROM favorites WHERE ref=?",(ref,)).fetchone():
                        c.execute("DELETE FROM favorites WHERE ref=?",(ref,))
                        c.commit()
                        return self.respond(200,{"favorite":False})
                    c.execute("INSERT INTO favorites(ref) VALUES(?)",(ref,))
                    c.commit()
                    return self.respond(200,{"favorite":True})
                if p == "/api/history":
                    ref = str(body.get("ref",""))[:120]
                    if not ref:raise ValueError("Referencia vacía")
                    c.execute("INSERT OR REPLACE INTO history(ref,seen) VALUES(?,CURRENT_TIMESTAMP)",(ref,))
                    c.commit()
                    return self.respond(200,{"ok":True})
                if p == "/api/notes":
                    kind = str(body.get("kind",""))
                    target = str(body.get("target",""))[:120]
                    text = str(body.get("body","")).strip()
                    quote = str(body.get("quote","")).strip()
                    start, end = body.get("start"),body.get("end")
                    if not text or len(text)>10000:
                        raise ValueError("La anotación debe contener entre 1 y 10.000 caracteres")
                    if len(quote)>1600:
                        raise ValueError("La cita es demasiado larga")
                    if kind=="bible":
                        match=re.fullmatch(r"([A-Za-z0-9]+):([1-9]\d*)",target)
                        if not match or match.group(1) not in BOOK_NAMES or int(match.group(2))>200:
                            raise ValueError("Referencia bíblica inválida")
                    elif kind in ("epub","pdf"):
                        match=re.fullmatch(r"([1-9]\d*):(\d+)",target)
                        if not match or (kind=="pdf" and int(match.group(2))<1):
                            raise ValueError("Referencia de documento inválida")
                        document=get_doc(c,int(match.group(1)))
                        if document["extension"]!=("."+kind):
                            raise ValueError("El documento no corresponde a su tipo")
                    else:
                        raise ValueError("Tipo de nota inválido")
                    if quote:
                        if kind=="pdf":
                            start=end=None
                        elif (not isinstance(start,int) or not isinstance(end,int)
                              or isinstance(start,bool) or isinstance(end,bool)
                              or not 0<=start<end<=2_000_000):
                            raise ValueError("La selección no es válida")
                    else:
                        start=end=None
                    cur=c.execute(
                        "INSERT INTO notes(kind,target,start_offset,end_offset,quote,body) VALUES(?,?,?,?,?,?)",
                        (kind,target,start,end,quote,text))
                    c.commit()
                    return self.respond(201,{"id":cur.lastrowid})
                m=re.fullmatch(r"/api/notes/([1-9]\d*)/update",p)
                if m:
                    text=str(body.get("body","")).strip()
                    if not text or len(text)>10000:
                        raise ValueError("La anotación debe contener entre 1 y 10.000 caracteres")
                    cur=c.execute("UPDATE notes SET body=?,updated=CURRENT_TIMESTAMP WHERE id=?",
                                  (text,int(m.group(1))))
                    if not cur.rowcount: raise ValueError("Anotación inexistente")
                    c.commit()
                    return self.respond(200,{"ok":True})
                if p == "/api/references":
                    kind = str(body.get("kind",""))
                    target = str(body.get("target",""))[:120]
                    quote = str(body.get("quote","")).strip()[:1600]
                    note = str(body.get("note",""))[:2000]
                    start, end = body.get("start"),body.get("end")
                    resource_kind = str(body.get("resource_kind",""))
                    url = None
                    document_id = None
                    if kind not in ("bible","epub","pdf") or not target or not quote:
                        raise ValueError("Selecciona un fragmento de texto")
                    if kind in ("bible","epub") and (
                        not isinstance(start,int) or not isinstance(end,int)
                        or isinstance(start,bool) or isinstance(end,bool)
                        or not 0<=start<end<=2_000_000):
                        raise ValueError("La selección de texto no es válida")
                    if kind == "pdf":
                        if not re.fullmatch(r"\d+:[1-9]\d*",target):
                            raise ValueError("Página PDF no válida")
                        if get_doc(c,int(target.split(":")[0]))["extension"]!=".pdf":
                            raise ValueError("No es un documento PDF")
                        start=end=None
                    if kind == "epub":
                        if not re.fullmatch(r"\d+:\d+",target) or get_doc(c,int(target.split(":")[0]))["extension"]!=".epub":
                            raise ValueError("No es un documento EPUB")
                    if kind == "bible" and not re.fullmatch(r"[A-Za-z0-9]+:[1-9]\d*",target):
                        raise ValueError("Referencia bíblica no válida")
                    if resource_kind == "url":
                        from urllib.parse import urlparse
                        url = str(body.get("url","")).strip()[:2000]
                        parsed = urlparse(url)
                        if (parsed.scheme not in ("https","http") or not parsed.netloc
                            or parsed.username is not None or parsed.password is not None
                            or any(ord(ch)<32 for ch in url)):
                            raise ValueError("Indica una URL http/https válida")
                    elif resource_kind == "document":
                        document_id = int(body.get("document_id",0))
                        get_doc(c,document_id)
                    else: raise ValueError("Tipo de referencia desconocido")
                    label = str(body.get("label","")).strip()[:180]
                    cur = c.execute(
                        "INSERT INTO references_extra(kind,target,start_offset,end_offset,quote,note,resource_kind,document_id,url,label) "
                        "VALUES(?,?,?,?,?,?,?,?,?,?)",
                        (kind,target,start,end,quote,note,resource_kind,document_id,url,label))
                    c.commit()
                    return self.respond(201,{"id":cur.lastrowid})
                if p == "/api/links":
                    kind = str(body.get("kind",""))
                    target = str(body.get("target",""))[:120]
                    start = body.get("start")
                    end = body.get("end")
                    quote = str(body.get("quote",""))[:1600].strip()
                    note = str(body.get("note",""))[:2000]
                    image_id = int(body.get("image_id",0))
                    row = get_doc(c,image_id)
                    if row["extension"] not in IMAGES:raise ValueError("El vínculo requiere una imagen")
                    if kind not in ("bible","epub","pdf") or not target or not quote:
                        raise ValueError("Vínculo sin destino o cita")
                    if kind in ("bible","epub") and (not isinstance(start,int) or not isinstance(end,int) or not 0 <= start < end <= 2_000_000):
                        raise ValueError("Selecciona un fragmento concreto del texto")
                    if kind == "pdf":
                        if not re.fullmatch(r"\d+:[1-9]\d*",target):raise ValueError("Página PDF inválida")
                        d_id = int(target.split(":")[0])
                        if get_doc(c,d_id)["extension"]!=".pdf":raise ValueError("El destino no es PDF")
                        start = end = None
                    if kind == "epub":
                        if not re.fullmatch(r"\d+:\d+",target) or get_doc(c,int(target.split(":")[0]))["extension"]!=".epub":
                            raise ValueError("Destino EPUB inválido")
                    if kind == "bible" and not re.fullmatch(r"[A-Za-z0-9]+:[1-9]\d*",target):
                        raise ValueError("Referencia bíblica inválida")
                    cur = c.execute("INSERT INTO links(kind,target,start_offset,end_offset,quote,note,image_id) VALUES(?,?,?,?,?,?,?)",
                                    (kind,target,start,end,quote,note,image_id))
                    c.commit()
                    return self.respond(201,{"id":cur.lastrowid})
                m = re.fullmatch(r"/api/documents/(\d+)/update",p)
                if m:
                    ident = int(m.group(1))
                    get_doc(c,ident)
                    title = str(body.get("title","")).strip()[:180]
                    category = str(body.get("category","Otros"))[:60]
                    tags = str(body.get("tags",""))[:500]
                    if not title or category not in ("Libros","Mapas","Imágenes","Documentos","Otros"):
                        raise ValueError("Título o clasificación inválida")
                    c.execute("UPDATE documents SET title=?,category=?,tags=? WHERE id=?",(title,category,tags,ident))
                    c.commit()
                    return self.respond(200,{"ok":True})
            return self.respond(404,{"error":"Ruta no encontrada"})
        except Exception as e:
            self.fail(e)
    def do_DELETE(self):
        try:
            self.check_host()
            self.check_origin()
            p,_ = self.path_params()
            with conn() as c:
                m = re.fullmatch(r"/api/notes/([1-9]\d*)",p)
                if m:
                    c.execute("DELETE FROM notes WHERE id=?",(int(m.group(1)),))
                    c.commit()
                    return self.respond(200,{"ok":True})
                m = re.fullmatch(r"/api/references/(\d+)",p)
                if m:
                    c.execute("DELETE FROM references_extra WHERE id=?",(int(m.group(1)),))
                    c.commit()
                    return self.respond(200,{"ok":True})
                m = re.fullmatch(r"/api/links/(\d+)",p)
                if m:
                    c.execute("DELETE FROM links WHERE id=?",(int(m.group(1)),))
                    c.commit()
                    return self.respond(200,{"ok":True})
                m = re.fullmatch(r"/api/documents/(\d+)",p)
                if m:
                    row = get_doc(c,int(m.group(1)))
                    for table in ("links","references_extra","notes"):
                        c.execute("DELETE FROM "+table+" WHERE kind IN ('pdf','epub') AND target LIKE ?",
                                  (str(row["id"])+":%",))
                    c.execute("DELETE FROM documents WHERE id=?",(row["id"],))
                    (UPLOADS / row["stored"]).unlink(missing_ok=True)
                    c.commit()
                    return self.respond(200,{"ok":True})
            return self.respond(404,{"error":"Ruta no encontrada"})
        except Exception as e:
            self.fail(e)
    def backup(self):
        """Backup consistente, sin compartir automáticamente la colección privada."""
        with tempfile.TemporaryDirectory() as tmp:
            snapshot = Path(tmp) / "scriptura-web.db"
            with conn() as source, sqlite3.connect(snapshot) as destination:
                source.backup(destination)
            out = io.BytesIO()
            with zipfile.ZipFile(out,"w",compression=zipfile.ZIP_DEFLATED,compresslevel=3) as z:
                z.write(snapshot,"scriptura-web.db")
                for path in UPLOADS.iterdir():
                    if path.is_file(): z.write(path,"uploads/"+path.name)
            self.respond(200,out.getvalue(),"application/zip",
                         'attachment; filename="scriptura-copia-seguridad.zip"')

def main():
    parser = argparse.ArgumentParser(description="Scriptura Web: biblioteca privada para PC")
    parser.add_argument("--port",type=int,default=8765)
    args=parser.parse_args()
    init_db()
    server=ThreadingHTTPServer(("127.0.0.1",args.port),Handler)
    print(f"Scriptura Web: http://127.0.0.1:{args.port}  |  Datos: {DATA}",flush=True)
    try:server.serve_forever()
    except KeyboardInterrupt:pass
    finally:server.server_close()

if __name__=="__main__":
    main()
