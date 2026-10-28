"""Pruebas de integración del servidor local. Ejecutar: python -m unittest -v test_server.py"""
from __future__ import annotations
import base64
import io
import json
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
import zipfile
from pathlib import Path
import server as app

ONE_PIXEL_PNG=base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9QkwAAAABJRU5ErkJggg==")

def mini_epub():
    """EPUB libre, generado solo para la prueba (no Biblia privada)."""
    zbuf=io.BytesIO()
    with zipfile.ZipFile(zbuf,"w") as z:
        z.writestr("mimetype","application/epub+zip")
        z.writestr("META-INF/container.xml",'''<?xml version="1.0"?>
        <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
          <rootfiles><rootfile full-path="OEBPS/content.opf"
          media-type="application/oebps-package+xml"/></rootfiles>
        </container>''')
        z.writestr("OEBPS/content.opf",'''<?xml version="1.0"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
        <manifest><item id="text" href="chapter.xhtml"
        media-type="application/xhtml+xml"/></manifest>
        <spine><itemref idref="text"/></spine></package>''')
        z.writestr("OEBPS/chapter.xhtml",
            "<html><body><h1>Prueba</h1><p>El texto seleccionado</p>"
            "<script>alert('no ejecutar')</script></body></html>")
    return zbuf.getvalue()

class WebTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp=tempfile.TemporaryDirectory()
        app.DATA=Path(cls.tmp.name)
        app.UPLOADS=app.DATA/"uploads"
        app.DB=app.DATA/"scriptura-web.db"
        app.init_db()
        cls.http=app.ThreadingHTTPServer(("127.0.0.1",0),app.Handler)
        cls.thread=threading.Thread(target=cls.http.serve_forever,daemon=True)
        cls.thread.start()
        cls.url="http://127.0.0.1:"+str(cls.http.server_port)
    @classmethod
    def tearDownClass(cls):
        cls.http.shutdown()
        cls.http.server_close()
        cls.thread.join(timeout=3)
        cls.tmp.cleanup()
    def request(self,path,method="GET",body=None,headers=None):
        h=dict(headers or {})
        if isinstance(body,dict):
            body=json.dumps(body).encode("utf-8")
            h["Content-Type"]="application/json"
        req=urllib.request.Request(self.url+path,data=body,headers=h,method=method)
        try:
            with urllib.request.urlopen(req,timeout=8) as response:
                raw=response.read()
                return response.status,raw
        except urllib.error.HTTPError as e:
            return e.code,e.read()
    def get_json(self,path):
        code,body=self.request(path)
        self.assertEqual(code,200,body)
        return json.loads(body)
    def upload(self,name,data,category="Documentos"):
        from urllib.parse import urlencode
        code,body=self.request("/api/upload?"+urlencode({"filename":name,"category":category}),
            method="POST",body=data,headers={"Content-Type":"application/octet-stream"})
        self.assertEqual(code,201,body)
        return json.loads(body)["id"]
    def test_01_health_books_and_local_host(self):
        self.assertTrue(self.get_json("/api/health")["ok"])
        self.assertEqual(len(self.get_json("/api/books")),73)
        code,_=self.request("/api/status",headers={"Host":"sitio-ajeno.example"})
        self.assertEqual(code,400)
    def test_02_epub_plain_text_and_upload(self):
        ident=self.upload("ejemplo.epub",mini_epub(),"Libros")
        self.__class__.epub_id=ident
        section=self.get_json("/api/documents/%d/chapter/0"%ident)
        self.assertIn("texto seleccionado",section["text"])
        self.assertNotIn("alert",section["text"])
        self.assertEqual(section["total"],1)
    def test_03_images_and_multiple_resources(self):
        image=self.upload("mapa.png",ONE_PIXEL_PNG,"Mapas")
        self.__class__.image_id=image
        self.assertEqual(self.get_json("/api/documents?category=Mapas")[0]["id"],image)
        target=str(self.epub_id)+":0"
        new={
          "kind":"epub","target":target,"quote":"texto seleccionado",
          "start":10,"end":27,"resource_kind":"document",
          "document_id":image,"note":"Mapa asociado"}
        code,body=self.request("/api/references","POST",new)
        self.assertEqual(code,201,body)
        new["resource_kind"]="url"
        new.pop("document_id")
        new["url"]="https://example.org/fuente"
        new["label"]="Fuente"
        code,body=self.request("/api/references","POST",new)
        self.assertEqual(code,201,body)
        refs=self.get_json("/api/references?kind=epub&target="+target)
        self.assertEqual(len(refs),2)
        self.assertEqual({x["resource_kind"] for x in refs},{"url","document"})
        bad=dict(new,url="javascript:alert(1)")
        code,_=self.request("/api/references","POST",bad)
        self.assertEqual(code,400)
    def test_03b_svg_import_and_safe_rendering(self):
        image=(b'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 150 80">'
            b'<rect width="150" height="80" fill="#dbeafe"/>'
            b'<text x="5" y="22" font-size="16">Doce tribus</text></svg>')
        ident=self.upload("12_tribus_de_Israel.svg",image,"Mapas")
        self.__class__.svg_id=ident
        code,data=self.request("/api/documents/"+str(ident)+"/file")
        self.assertEqual(code,200)
        self.assertIn(b"Doce tribus",data)
        self.assertTrue(data.startswith(b"<?xml"))
        target=str(self.epub_id)+":0"
        code,raw=self.request("/api/references","POST",{
            "kind":"epub","target":target,"start":10,"end":27,
            "quote":"texto seleccionado","resource_kind":"document","document_id":ident})
        self.assertEqual(code,201,raw)
    def test_03c_svg_rejects_active_content(self):
        cases=[
            b'<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>',
            b'<svg xmlns="http://www.w3.org/2000/svg"><rect width="40" onload="alert(1)"/></svg>',
            b'<svg xmlns="http://www.w3.org/2000/svg"><use href="https://evil.example/img.svg"/></svg>',
            b'<!DOCTYPE svg [<!ENTITY ex SYSTEM "file:///tmp/secret">]><svg xmlns="http://www.w3.org/2000/svg"/>',
            b'<svg xmlns="http://www.w3.org/2000/svg"><foreignObject/></svg>'
        ]
        from urllib.parse import urlencode
        for i,svg in enumerate(cases):
            with self.subTest(i=i):
                status,raw=self.request("/api/upload?"+urlencode({
                    "filename":"unsafe%d.svg"%i,"category":"Mapas"}),
                    "POST",svg,{"Content-Type":"application/octet-stream"})
                self.assertEqual(status,400,raw)
        self.assertFalse(any(x["name"].startswith("unsafe") for x in self.get_json("/api/documents")))
    def test_03d_highlight_js_is_available(self):
        status,body=self.request("/annotations.js")
        self.assertEqual(status,200)
        self.assertIn(b"rangeFromOffsets",body)
    def test_04_pdf_and_backup(self):
        ident=self.upload("ejemplo.pdf",b"%PDF-1.4\n%%EOF\n")
        code,body=self.request("/api/references","POST",{
            "kind":"pdf","target":str(ident)+":2","quote":"página de prueba",
            "resource_kind":"document","document_id":self.image_id})
        self.assertEqual(code,201,body)
        self.assertGreater(len(self.get_json("/api/references")),2)
        code,body=self.request("/api/backup")
        self.assertEqual(code,200)
        with zipfile.ZipFile(io.BytesIO(body)) as z:
            names=z.namelist()
            self.assertIn("scriptura-web.db",names)
            self.assertGreaterEqual(len([n for n in names if n.startswith("uploads/")]),3)
    def test_04b_notes_create_edit_and_reject_bad_data(self):
        target = str(self.epub_id)+":0"
        body = {"kind":"epub","target":target,
                "quote":"texto seleccionado","start":10,"end":27,
                "body":"Primer comentario del libro"}
        code,raw = self.request("/api/notes","POST",body)
        self.assertEqual(code,201,raw)
        ident = json.loads(raw)["id"]
        self.__class__.epub_note_id = ident
        rows=self.get_json("/api/notes?kind=epub&target="+target)
        self.assertEqual(len(rows),1)
        self.assertEqual(rows[0]["body"],"Primer comentario del libro")
        code,_=self.request("/api/notes/"+str(ident)+"/update",
                            "POST",{"body":"Una segunda versión de la nota"})
        self.assertEqual(code,200)
        updated=self.get_json("/api/notes?kind=epub&target="+target)
        self.assertEqual(updated[0]["body"],"Una segunda versión de la nota")
        bad=dict(body,body="")
        self.assertEqual(self.request("/api/notes","POST",bad)[0],400)
        bad=dict(body,start=-1)
        self.assertEqual(self.request("/api/notes","POST",bad)[0],400)
        self.assertEqual(self.request("/api/notes/"+str(ident)+"/update",
                "POST",{"body":""})[0],400)
        self.assertEqual(self.request("/api/notes/999999/update",
                "POST",{"body":"No debería actualizar"})[0],400)
    def test_04c_pdf_page_note(self):
        pdf_id=self.upload("otro-pdf.pdf",b"%PDF-1.4\n%%EOF\n")
        self.__class__.pdf_note_document=pdf_id
        status,raw=self.request("/api/notes","POST",{
            "kind":"pdf","target":str(pdf_id)+":3",
            "quote":"Referencia manuscrita","body":"Ver el mapa asociado"})
        self.assertEqual(status,201,raw)
        note=self.get_json("/api/notes?kind=pdf&target="+str(pdf_id)+":3")[0]
        self.assertIsNone(note["start_offset"])
        self.assertIsNone(note["end_offset"])
    def test_04d_notes_are_in_backup(self):
        code,raw=self.request("/api/backup")
        self.assertEqual(code,200)
        with zipfile.ZipFile(io.BytesIO(raw)) as z:
            import sqlite3
            with tempfile.TemporaryDirectory() as tmp:
                db=Path(tmp)/"backup.db"
                db.write_bytes(z.read("scriptura-web.db"))
                connection=sqlite3.connect(db)
                try:
                    number=connection.execute("SELECT COUNT(*) FROM notes").fetchone()[0]
                    self.assertGreaterEqual(number,2)
                finally:
                    connection.close()
    def test_05_cascade_on_delete(self):
        code,_=self.request("/api/documents/"+str(self.image_id),"DELETE")
        self.assertEqual(code,200)
        self.assertFalse(any(r["document_id"]==self.image_id
                             for r in self.get_json("/api/references")))
        code,_=self.request("/api/documents/"+str(self.epub_id),"DELETE")
        self.assertEqual(code,200)
        self.assertFalse(any(r["kind"]=="epub" for r in self.get_json("/api/references")))
        self.assertFalse(any(r["kind"]=="epub" for r in self.get_json("/api/notes")))
        code,_=self.request("/api/documents/"+str(self.pdf_note_document),"DELETE")
        self.assertEqual(code,200)
        self.assertFalse(any(r["kind"]=="pdf" for r in self.get_json("/api/notes")))
    def test_06_bible_notes_without_editorial_content(self):
        code,raw=self.request("/api/notes","POST",{
            "kind":"bible","target":"Jn:1","body":"Nota personal del capítulo"})
        self.assertEqual(code,201,raw)
        ident=json.loads(raw)["id"]
        self.assertEqual(self.get_json("/api/notes?kind=bible&target=Jn:1")[0]["body"],
                         "Nota personal del capítulo")
        code,_=self.request("/api/notes/"+str(ident),"DELETE")
        self.assertEqual(code,200)
        self.assertEqual(self.get_json("/api/notes?kind=bible&target=Jn:1"),[])


if __name__=="__main__":
    unittest.main()
