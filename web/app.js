"use strict";
/* Scriptura Web: cliente sin frameworks; los originales permanecen en la biblioteca privada local. */
const $ = (selector) => document.querySelector(selector);
const esc = (value) => String(value == null ? "" : value).replace(/[&<>"']/g, c => ({
  "&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"
})[c]);
const qp = (object) => new URLSearchParams(object).toString();
const categories = ["Libros","Mapas","Imágenes","Documentos","Otros"];
const state = {page:"home",books:[],docs:[],status:null,reader:null,selection:null,libraryQuery:"",libraryCategory:""};
let toastTimer = null;
async function api(path, method="GET", payload) {
  const options = {method, headers:{}};
  if (payload !== undefined) {
    options.body = JSON.stringify(payload);
    options.headers["Content-Type"] = "application/json";
  }
  const response = await fetch(path,options);
  let body;
  try { body = await response.json(); } catch (_) { body = {}; }
  if (!response.ok) throw new Error(body.error || "No se ha podido completar la operación.");
  return body;
}
function toast(message, error=false) {
  const el=$("#toast"); el.textContent=message;
  el.style.background=error?"#942f34":"#0d2338";
  el.classList.add("visible");clearTimeout(toastTimer);
  toastTimer=setTimeout(()=>el.classList.remove("visible"),5500);
}
function busy(promise) {Promise.resolve(promise).catch(e=>{console.error(e);toast(e.message||String(e),true);});}
function title(value) {$("#page-title").textContent=value;}
function content(html) {$("#content").innerHTML=html;}
function optionList(items,selected) {
  return items.map(v=>'<option value="'+esc(v)+'"'+(v===selected?' selected':'')+'>'+esc(v)+'</option>').join("");
}
function action(name, label, attributes="", className="ghost-button") {
  return '<button type="button" class="'+className+'" data-action="'+name+'" '+attributes+'>'+label+'</button>';
}
function empty(label) {return '<div class="empty">'+esc(label)+'</div>';}
function short(value,length=140) {
  const s=String(value||"");return s.length>length?s.slice(0,length)+"…":s;
}
function fileUrl(id) {return "/api/documents/"+encodeURIComponent(id)+"/file";}
function categoryFor(name) {
  const ext=(name.split(".").pop()||"").toLowerCase();
  if(ext==="epub") return "Libros";
  if(["png","jpg","jpeg","gif","webp"].includes(ext)) return "Imágenes";
  if(["gpx","kml"].includes(ext)) return "Mapas";
  if(["pdf","docx","txt","md","xlsx"].includes(ext)) return "Documentos";
  return "Otros";
}
async function refresh() {
  const [status,books,docs]=await Promise.all([
    api("/api/status"),api("/api/books"),api("/api/documents")
  ]);
  state.status=status;state.books=books;state.docs=docs;
}
function navigate(page) {
  state.page=page;
  document.querySelectorAll(".nav-button").forEach(b=>b.classList.toggle("active",b.dataset.page===page));
  const names={home:"Inicio",bible:"Biblia",library:"Biblioteca",links:"Vínculos",saved:"Guardados",help:"Ayuda"};
  title(names[page]||"Scriptura");
  busy(renderPage());
}
async function renderPage() {
  if(state.page==="home")return renderHome();
  if(state.page==="bible")return renderBibleIndex();
  if(state.page==="library")return renderLibrary();
  if(state.page==="links")return renderLinksPage();
  if(state.page==="saved")return renderSaved();
  if(state.page==="help")return renderHelp();
}
function renderHome() {
  const stats=state.status||{verses:0,documents:0,links:0};
  content('<div class="card" style="background:#0d2338;color:#fff">'
    +'<div class="eyebrow" style="color:#daa85c">LEE · EXPLORA · RELACIONA</div>'
    +'<h2 style="color:#fff;margin-top:12px">Tu espacio de lectura e investigación</h2>'
    +'<p>Consulta la Biblia de tu ejemplar, lee libros EPUB y PDF, clasifica mapas e imágenes y conecta tus fuentes con fragmentos concretos de texto.</p>'
    +'<div class="actions">'+action("open-bible","Abrir Biblia","","primary-button")
    +action("open-library","Explorar biblioteca")+'</div></div>'
    +'<div class="grid">'
    +'<button class="tile" data-action="open-bible"><strong>Versículos de la Biblia</strong><span class="number">'+Number(stats.verses||0).toLocaleString("es")+'</span><span class="small">Solo desde tu EPUB privado de Navarra</span></button>'
    +'<button class="tile" data-action="open-library"><strong>Documentos guardados</strong><span class="number">'+Number(stats.documents||0)+'</span><span class="small">EPUB, PDF, mapas, imágenes y otros</span></button>'
    +'<button class="tile" data-action="open-links"><strong>Imágenes vinculadas</strong><span class="number">'+Number(stats.links||0)+'</span><span class="small">Vínculos tradicionales; las nuevas referencias aparecen en Vínculos</span></button>'
    +'</div><div class="card section"><h2>Cómo empezar</h2>'
    +'<div class="step"><span>1</span><p>Importa tu propio EPUB o PDF desde <strong>Biblioteca</strong>. También puedes guardar mapas, fotografías y documentos de apoyo.</p></div>'
    +'<div class="step"><span>2</span><p>Abre un libro o capítulo, <strong>selecciona un fragmento de texto</strong> y elige Vincular recurso.</p></div>'
    +'<div class="step"><span>3</span><p>Asocia una imagen, un documento o una URL; después podrás volver directamente al pasaje asociado.</p></div>'
    +'</div>');
}
function renderBibleIndex() {
  const available=state.books.some(b=>b.chapters>0);
  const html='<div class="card"><h2>Los 73 libros de la Biblia</h2>'
    +'<p class="muted">Se importan únicamente los versículos de tu propio EPUB de Navarra. La edición no se distribuye con la aplicación.</p>'
    +(!available?'<p class="pill">No hay texto bíblico importado. Añade el EPUB desde Biblioteca y selecciona «Importar como Biblia de Navarra».</p>':'')
    +'<div class="form-row"><div class="field grow"><label>Buscar por palabra</label><input id="bible-term" type="search" placeholder="Ej.: esperanza"></div>'
    +action("search-bible","Buscar","","primary-button")+'</div><div id="search-results"></div></div>'
    +'<div class="card"><h2>Índice de libros</h2><div class="grid">'
    +state.books.map(b=>'<button class="tile" data-action="read-bible" data-book="'+esc(b.code)+'" data-chapter="1">'
      +'<strong>'+esc(b.name)+'</strong><span class="small">'+(b.chapters?b.chapters+" capítulos":"Sin texto importado")+'</span></button>').join("")
    +'</div></div>';
  content(html);
}
async function searchBible() {
  const query=($("#bible-term")?.value||"").trim();
  if(query.length<2)throw Error("Escribe al menos dos caracteres.");
  const results=await api("/api/search?"+qp({q:query}));
  $("#search-results").innerHTML='<h3 style="margin-top:18px">Resultados ('+results.length+')</h3>'
    +(results.length?results.map(row=>'<button class="search-result" data-action="read-bible" data-book="'+esc(row.book)+'" data-chapter="'+Number(row.chapter)+'">'
      +'<strong>'+esc((state.books.find(b=>b.code===row.book)||{}).name||row.book)+' '+row.chapter+':'+row.verse+'</strong> '+esc(short(row.text,160))+'</button>').join("")
      :empty("No se encontraron versículos con ese texto."));
}
async function openBible(book,chapter=1) {
  const data=await api("/api/verses?"+qp({book,chapter}));
  if(!data.verses.length) {toast("No hay versículos importados en ese capítulo.",true);return;}
  state.reader={kind:"bible",target:book+":"+chapter,book,chapter,
    name:data.name+" · capítulo "+chapter,text:data.verses.map(v=>v.verse+"  "+v.text).join("\n\n")};
  await api("/api/history","POST",{ref:data.name+" "+chapter});
  renderReader();
}
async function openEpub(doc,index=0) {
  const data=await api("/api/documents/"+doc.id+"/chapter/"+index);
  state.reader={kind:"epub",target:doc.id+":"+index,doc,index,total:data.total,
    name:doc.title+" · sección "+(index+1)+" de "+data.total,text:data.text};
  renderReader();
}
async function openPdf(doc,page=1) {
  state.reader={kind:"pdf",target:doc.id+":"+page,doc,page,
    name:doc.title+" · página "+page};
  renderReader();
}
async function openDocument(id) {
  const doc=state.docs.find(d=>d.id===Number(id));
  if(!doc)throw Error("Documento no encontrado.");
  if(doc.extension===".epub")return openEpub(doc,0);
  if(doc.extension===".pdf")return openPdf(doc,1);
  title(doc.title);
  state.reader=null;state.selection=null;
  const img=[".png",".jpg",".jpeg",".gif",".webp"].includes(doc.extension);
  content('<div class="reader-head"><h2>'+esc(doc.title)+'</h2>'+action("open-library","Volver a Biblioteca")+'</div>'
    +'<div class="card">'+(img?'<img class="picture" src="'+fileUrl(doc.id)+'" alt="'+esc(doc.title)+'">':empty("Vista previa no disponible para este formato."))
    +'<div class="toolbar"><a class="primary-button" href="'+fileUrl(doc.id)+'" download>Descargar archivo</a>'
    +action("edit-doc","Editar clasificación",'data-id="'+doc.id+'"')
    +'</div></div>');
}
async function renderReader() {
  const r=state.reader;
  if(!r)return;
  title(r.name);
  state.selection=null;
  const isPdf=r.kind==="pdf";
  const nav=r.kind==="bible"
    ?action("reader-prev","‹ Anterior",'data-kind="bible"')
      +action("reader-next","Siguiente ›",'data-kind="bible"')
    :r.kind==="epub"
    ?action("reader-prev","‹ Sección anterior",r.index===0?'disabled':'')
      +action("reader-next","Siguiente sección ›",r.index+1>=r.total?'disabled':'')
    :action("reader-prev","‹ Página anterior",r.page===1?'disabled':'')
      +action("reader-next","Página siguiente ›");
  const body=isPdf
    ?'<iframe title="Documento PDF" class="pdf-reader" src="'+fileUrl(r.doc.id)+'#page='+r.page+'"></iframe>'
    :'<div class="reader-text" id="reader-text" tabindex="0"></div>';
  content('<div class="reader-head"><div><div class="eyebrow">'+esc(r.kind.toUpperCase())+'</div>'
    +'<h2>'+esc(r.name)+'</h2></div>'+action("open-library","Biblioteca")
    +'</div><div class="toolbar">'+nav
    +action("link-current","Vincular recurso","","primary-button")
    +(r.kind==="bible"?action("favorite","☆ Favorito"):"")
    +'</div><div class="reader-layout"><div class="reading-surface"><p class="muted" style="font-size:12px">'
    +(isPdf?"Para PDF, indica la página y transcribe el fragmento en el diálogo de vinculación."
      :"Selecciona con el ratón un fragmento y pulsa «Vincular recurso». Se guarda su posición en esta sección.")
    +'</p>'+body+'</div><aside class="aside-card"><h3>Referencias de este pasaje</h3><div id="passage-links">'
    +empty("Cargando vínculos…")+'</div></aside></div>');
  if(!isPdf) {
    $("#reader-text").textContent=r.text||"";
    $("#reader-text").addEventListener("mouseup",captureSelection);
    $("#reader-text").addEventListener("keyup",captureSelection);
  }
  await showLinksForReader();
}
function captureSelection() {
  const element=$("#reader-text"), r=state.reader, selection=window.getSelection();
  if(!r||!element||!selection||selection.isCollapsed||!selection.rangeCount)return;
  const range=selection.getRangeAt(0);
  if(!element.contains(range.startContainer)||!element.contains(range.endContainer))return;
  const before=range.cloneRange();
  before.selectNodeContents(element); before.setEnd(range.startContainer,range.startOffset);
  const start=before.toString().length;
  const quote=selection.toString().trim();
  if(!quote)return;
  const text=element.textContent||"";
  const rangeLen=range.toString().length;
  const end=start+rangeLen;
  if(quote.length>1600){toast("Selecciona un fragmento de menos de 1.600 caracteres.",true);return;}
  if(end>text.length||end<=start)return;
  state.selection={kind:r.kind,target:r.target,start,end,quote:range.toString()};
}
async function moveReader(delta) {
  const r=state.reader;if(!r)return;
  if(r.kind==="epub")return openEpub(r.doc,Math.max(0,Math.min(r.total-1,r.index+delta)));
  if(r.kind==="pdf")return openPdf(r.doc,Math.max(1,r.page+delta));
  const bookIndex=state.books.findIndex(b=>b.code===r.book);
  const current=state.books[bookIndex], max=current?.chapters||1;
  if(r.chapter+delta>=1&&r.chapter+delta<=max)return openBible(r.book,r.chapter+delta);
  const other=state.books[bookIndex+(delta>0?1:-1)];
  if(other&&other.chapters)return openBible(other.code,delta>0?1:other.chapters);
}
async function showLinksForReader() {
  const r=state.reader;if(!r)return;
  const [extra,images]=await Promise.all([
    api("/api/references?"+qp({kind:r.kind,target:r.target})),
    api("/api/links?"+qp({kind:r.kind,target:r.target}))
  ]);
  if(!state.reader||state.reader.target!==r.target)return;
  const holder=$("#passage-links"); if(!holder)return;
  const rows=[...extra.map(x=>({...x,source:"extra"})),...images.map(x=>({...x,source:"legacy"}))];
  holder.innerHTML=rows.length?rows.map(linkRow).join(""):empty("Aún no hay recursos vinculados.");
}
function safeUrl(url) {
  try {const parsed=new URL(url);return ["http:","https:"].includes(parsed.protocol)?parsed.href:"";}catch(_){return "";}
}
function linkRow(row) {
  let resource="";
  if(row.source==="legacy"){
    resource='<a href="'+fileUrl(row.image_id)+'" target="_blank" rel="noopener"><img alt="'+esc(row.image_title)+'" src="'+fileUrl(row.image_id)+'"></a>';
  } else if(row.resource_kind==="url") {
    const url=safeUrl(row.url);
    resource=url?'<a href="'+esc(url)+'" target="_blank" rel="noopener noreferrer">'+esc(row.label||url)+'</a>':'Enlace inválido';
  } else {
    resource='<a href="'+fileUrl(row.document_id)+'" target="_blank" rel="noopener">'+esc(row.document_title||"Documento")+'</a>';
  }
  return '<div class="link-entry"><div class="quote">“'+esc(short(row.quote,220))+'”</div>'
    +'<div style="margin:8px 0">'+resource+'</div>'
    +(row.note?'<p class="note">'+esc(row.note)+'</p>':'')
    +'<div class="toolbar">'+action("go-link","Ir al pasaje",'data-kind="'+esc(row.kind)+'" data-target="'+esc(row.target)+'" data-start="'+(row.start_offset??"")+'" data-end="'+(row.end_offset??"")+'"',"ghost-button small-btn")
    +action("delete-link","Eliminar",'data-source="'+row.source+'" data-id="'+row.id+'"',"danger-button small-btn")
    +'</div></div>';
}
async function beginLink() {
  const r=state.reader;if(!r)throw Error("Abre antes un capítulo o documento.");
  let sel=state.selection;
  if(r.kind==="pdf") {
    const page=window.prompt("Página del PDF:",String(r.page));
    if(page===null)return;
    if(!/^[1-9]\d*$/.test(page))throw Error("Indica una página válida.");
    const quote=window.prompt("Copia el fragmento concreto de esa página (máximo 1.600 caracteres):","");
    if(quote===null)return;
    if(!quote.trim()||quote.length>1600)throw Error("Es obligatorio incluir una cita breve.");
    sel={kind:"pdf",target:r.doc.id+":"+page,quote:quote.trim(),start:null,end:null};
  } else if(!sel||sel.kind!==r.kind||sel.target!==r.target) {
    throw Error("Selecciona primero un fragmento del texto con el ratón.");
  }
  state.selection=sel;
  $("#selected-quote").textContent=short(sel.quote,360);
  const docs=state.docs;
  $("#link-image").innerHTML=docs.map(d=>'<option value="'+d.id+'">'+esc(d.title+" · "+d.category)+'</option>').join("");
  $("#link-note").value="";$("#link-url").value="";$("#link-label").value="";
  $("#resource-type").value=docs.length?"document":"url";
  switchResource();
  $("#link-dialog").showModal();
}
function switchResource() {
  const isUrl=$("#resource-type").value==="url";
  $("#document-input").hidden=isUrl;
  $("#url-input").hidden=!isUrl;
  $("#link-image").required=!isUrl;
  $("#link-url").required=isUrl;
}
async function saveLink(event) {
  event.preventDefault();
  const sel=state.selection;if(!sel)throw Error("No se ha seleccionado ningún fragmento.");
  const resource_kind=$("#resource-type").value;
  const payload={kind:sel.kind,target:sel.target,start:sel.start,end:sel.end,
    quote:sel.quote,note:$("#link-note").value,resource_kind};
  if(resource_kind==="url"){
    payload.url=$("#link-url").value.trim();payload.label=$("#link-label").value.trim();
    if(!safeUrl(payload.url))throw Error("La URL debe comenzar por https:// o http://.");
  }else{
    payload.document_id=Number($("#link-image").value);
    if(!payload.document_id)throw Error("Importa primero un recurso a la biblioteca.");
  }
  await api("/api/references","POST",payload);
  $("#link-dialog").close();
  toast("Referencia vinculada al fragmento.");
  await showLinksForReader();
}
async function renderLibrary() {
  const query=state.libraryQuery||"", category=state.libraryCategory||"";
  const docs=await api("/api/documents?"+qp({q:query,category}));
  const upload='<div class="card"><h2>Importar documentos</h2>'
    +'<p class="muted">Archivos de hasta 80 MB. Solo se almacenan en la carpeta local de Scriptura.</p>'
    +'<form id="upload-form"><div class="form-row"><div class="field grow"><label for="upload-file">Seleccionar archivo</label>'
    +'<input id="upload-file" type="file" required accept=".epub,.pdf,.png,.jpg,.jpeg,.webp,.gif,.txt,.md,.docx,.xlsx,.kml,.gpx,.zip"></div>'
    +'<div class="field"><label for="upload-category">Clasificación</label>'
    +'<select id="upload-category">'+optionList(categories,"Documentos")+'</select></div>'
    +'<button class="primary-button" type="submit">Importar</button></div></form></div>';
  content(upload+'<div class="card"><div class="reader-head"><h2>Biblioteca ('+docs.length+')</h2></div>'
    +'<div class="form-row"><div class="field grow"><label>Buscar archivos o etiquetas</label><input id="library-query" value="'+esc(query)+'" type="search" placeholder="Título, palabra clave…"></div>'
    +'<div class="field"><label>Tipo</label><select id="library-category">'
    +'<option value="">Todos</option>'+optionList(categories,category)+'</select></div>'
    +action("filter-docs","Filtrar","","primary-button")+'</div></div>'
    +(docs.length?'<div class="doc-grid">'+docs.map(docCard).join("")+'</div>':empty("Todavía no hay documentos en esta categoría.")));
  $("#upload-file").addEventListener("change",()=>{
    const file=$("#upload-file").files[0];if(file)$("#upload-category").value=categoryFor(file.name);
  });
}
function docCard(doc) {
  const image=[".png",".jpg",".jpeg",".webp",".gif"].includes(doc.extension);
  return '<div class="doc-card">'
    +(image?'<img class="doc-image" src="'+fileUrl(doc.id)+'" alt="'+esc(doc.title)+'">'
      :'<div class="doc-icon">'+(doc.extension===".epub"?"📖":doc.extension===".pdf"?"▣":"▤")+'</div>')
    +'<h3>'+esc(doc.title)+'</h3><div><span class="tag">'+esc(doc.category)+'</span><span class="tag">'+esc(doc.extension)+'</span></div>'
    +(doc.tags?'<p class="muted">'+esc(doc.tags)+'</p>':'')
    +'<p class="muted" style="margin:0;font-size:12px">'+(doc.size/1024/1024).toFixed(2)+' MB</p>'
    +'<div class="doc-actions">'+action("read-doc","Abrir",'data-id="'+doc.id+'"',"primary-button small-btn")
    +action("edit-doc","Editar",'data-id="'+doc.id+'"',"ghost-button small-btn")
    +(doc.extension===".epub"?action("import-bible","Importar como Biblia",'data-id="'+doc.id+'"',"ghost-button small-btn"):"")
    +action("delete-doc","Eliminar",'data-id="'+doc.id+'"',"danger-button small-btn")
    +'</div></div>';
}
async function upload(event) {
  event.preventDefault();
  const file=$("#upload-file").files[0];
  if(!file)throw Error("Selecciona un archivo.");
  if(file.size>80*1024*1024)throw Error("El archivo supera los 80 MB.");
  const category=$("#upload-category").value;
  const res=await fetch("/api/upload?"+qp({filename:file.name,category}),{
    method:"POST",headers:{"Content-Type":"application/octet-stream"},body:file});
  let result={};try{result=await res.json();}catch(_){}
  if(!res.ok)throw Error(result.error||"Falló la importación.");
  toast("Documento importado correctamente.");
  await refresh();await renderLibrary();
}
async function editDoc(id) {
  const doc=state.docs.find(d=>d.id===Number(id));if(!doc)return;
  const name=prompt("Título del documento:",doc.title);if(name===null)return;
  if(!name.trim())throw Error("El título no puede estar vacío.");
  const category=prompt("Categoría: "+categories.join(", "),doc.category);
  if(category===null)return;
  if(!categories.includes(category))throw Error("Categoría no reconocida.");
  const tags=prompt("Etiquetas separadas por comas:",doc.tags||"");
  if(tags===null)return;
  await api("/api/documents/"+doc.id+"/update","POST",{title:name,category,tags});
  await refresh();navigate("library");toast("Clasificación guardada.");
}
async function deleteDoc(id) {
  if(!confirm("¿Eliminar este documento y sus vínculos? Esta acción no se puede deshacer."))return;
  await api("/api/documents/"+id,"DELETE");
  await refresh();navigate("library");toast("Documento eliminado.");
}
async function importBible(id) {
  if(!confirm("Se sustituirá el índice bíblico previo por este EPUB privado de Navarra. ¿Continuar?"))return;
  toast("Procesando los versículos del EPUB. No cierres esta pestaña.");
  const result=await api("/api/import-bible","POST",{document_id:id});
  await refresh();toast("Biblia importada: "+result.imported+" versículos.");navigate("bible");
}
async function renderLinksPage() {
  const [extra,images]=await Promise.all([api("/api/references"),api("/api/links")]);
  const rows=[...extra.map(x=>({...x,source:"extra"})),...images.map(x=>({...x,source:"legacy"}))];
  content('<div class="card"><h2>Referencias cruzadas ('+rows.length+')</h2>'
    +'<p class="muted">Cada recurso conserva el fragmento y el capítulo o sección donde se vinculó. Las páginas PDF se guardan por número de página y cita escrita.</p>'
    +(rows.length?rows.map(linkRow).join(""):empty("Selecciona un fragmento de un libro y vincula un recurso para empezar."))
    +'</div>');
}
async function renderSaved() {
  const [favorites,history]=await Promise.all([api("/api/favorites"),api("/api/history")]);
  const list=(rows)=>rows.length?rows.map(item=>'<div class="list-entry">'
    +action("open-reference",esc(item.ref),'data-ref="'+esc(item.ref)+'"',"ghost-button")+'</div>').join("")
    :empty("Todavía no hay registros.");
  content('<div class="card"><h2>Favoritos</h2>'+list(favorites)+'</div>'
    +'<div class="card"><h2>Lecturas recientes</h2>'+list(history)+'</div>');
}
function renderHelp() {
  content('<div class="card"><h2>Scriptura Web · versión preliminar</h2>'
    +'<p>Esta web funciona <strong>solo en tu ordenador</strong> con un servidor local Python, sin nube y sin crear cuentas. Los archivos y la base de datos quedan en la carpeta <code>web/data</code>.</p>'
    +'<p>EPUB: se ofrece lectura por secciones en texto plano; imágenes interiores, maquetación y notas editoriales no se reproducen íntegramente en esta primera fase.</p>'
    +'<p>PDF: se visualiza mediante el lector integrado del navegador. Los vínculos permiten indicar página y citar el fragmento, pero todavía no se identifican coordenadas exactas dentro de cada página.</p>'
    +'<p>Para conservar tu biblioteca, utiliza <strong>Copia de seguridad</strong>. El ZIP contiene tus documentos privados: guárdalo con protección adecuada. No se cifra automáticamente.</p>'
    +'<p>El índice de los 73 libros proviene del proyecto Android, pero el EPUB de Navarra es propiedad del usuario y debe importarse por separado. No se incluye en GitHub.</p>'
    +'<p>Esta versión aún no incorpora todos los módulos Android: atlas interactivo, arte, liturgia, Gemini y contexto histórico.</p></div>');
}
async function goLink(btn) {
  const kind=btn.dataset.kind,target=btn.dataset.target;
  const start=Number(btn.dataset.start),end=Number(btn.dataset.end);
  if(kind==="bible") {
    const [book,chapter]=target.split(":");await openBible(book,Number(chapter));
  } else {
    const [id,number]=target.split(":");
    const doc=state.docs.find(d=>d.id===Number(id));
    if(!doc)throw Error("El documento del vínculo ya no existe.");
    if(kind==="pdf")await openPdf(doc,Number(number));
    else await openEpub(doc,Number(number));
  }
  if(kind!=="pdf"&&btn.dataset.start!==""&&end>start) {
    const el=$("#reader-text");const text=el?.firstChild;
    if(text?.nodeType===Node.TEXT_NODE&&end<=text.textContent.length) {
      const range=document.createRange();range.setStart(text,start);range.setEnd(text,end);
      const selection=window.getSelection();selection.removeAllRanges();selection.addRange(range);
      const before=range.cloneRange(); const marker=document.createElement("span");
      try{range.surroundContents(marker);marker.scrollIntoView({block:"center"});marker.replaceWith(...marker.childNodes);}catch(_){el.scrollIntoView();}
    }
  }
}
async function openReference(ref) {
  const match=ref.match(/^(.+)\s+(\d+)$/);
  if(!match)throw Error("No se puede resolver esta referencia.");
  const book=state.books.find(b=>b.name===match[1]);
  if(!book)throw Error("Libro no reconocido.");
  await openBible(book.code,Number(match[2]));
}
document.querySelectorAll(".nav-button").forEach(el=>el.addEventListener("click",()=>navigate(el.dataset.page)));
$("#backup-button").addEventListener("click",()=>{window.location.assign("/api/backup");});
$("#close-dialog").addEventListener("click",()=>$("#link-dialog").close());
$("#cancel-dialog").addEventListener("click",()=>$("#link-dialog").close());
$("#resource-type").addEventListener("change",switchResource);
$("#link-form").addEventListener("submit",event=>busy(saveLink(event)));
$("#content").addEventListener("submit",event=>{if(event.target.id==="upload-form")busy(upload(event));});
$("#content").addEventListener("click",event=>{
  const btn=event.target.closest("[data-action]");if(!btn)return;
  const job=async()=>{
    switch(btn.dataset.action){
      case "open-bible":navigate("bible");break;
      case "open-library":navigate("library");break;
      case "open-links":navigate("links");break;
      case "read-bible":await openBible(btn.dataset.book,Number(btn.dataset.chapter||1));break;
      case "read-doc":await openDocument(btn.dataset.id);break;
      case "search-bible":await searchBible();break;
      case "filter-docs":state.libraryQuery=$("#library-query").value;
        state.libraryCategory=$("#library-category").value;await renderLibrary();break;
      case "edit-doc":await editDoc(btn.dataset.id);break;
      case "delete-doc":await deleteDoc(btn.dataset.id);break;
      case "import-bible":await importBible(btn.dataset.id);break;
      case "reader-prev":await moveReader(-1);break;
      case "reader-next":await moveReader(1);break;
      case "link-current":await beginLink();break;
      case "favorite":
        const saved=await api("/api/favorites","POST",{ref:state.reader.name.replace(" · capítulo "," ")});
        toast(saved.favorite?"Añadido a favoritos.":"Quitado de favoritos.");break;
      case "delete-link":
        if(!confirm("¿Quitar este vínculo?"))return;
        await api("/api/"+(btn.dataset.source==="legacy"?"links":"references")+"/"+btn.dataset.id,"DELETE");
        toast("Vínculo eliminado.");if(state.reader&&$("#passage-links"))await showLinksForReader();
        else await renderLinksPage();break;
      case "go-link":await goLink(btn);break;
      case "open-reference":await openReference(btn.dataset.ref);break;
    }
  };busy(job());
});
(async()=>{try{await refresh();navigate("home");}catch(e){
  title("No se puede iniciar Scriptura");content('<div class="card"><h2>Servidor no disponible</h2><p>'
    +esc(e.message)+'</p><p>Ejecuta <code>python server.py</code> en la carpeta <code>web</code> del repositorio.</p></div>');
}})();
