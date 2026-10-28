/* Scriptura: marks from stable text offsets. Never inject user-supplied markup. */
(function (root) {
  "use strict";
  function spans(text, notes, links) {
    const all=[];
    for (const n of notes||[]) {
      if (Number.isInteger(n.start_offset) && Number.isInteger(n.end_offset) &&
          n.start_offset>=0 && n.end_offset<=text.length && n.end_offset>n.start_offset &&
          text.slice(n.start_offset,n.end_offset).trim()===(n.quote||"").trim()) {
        all.push({start:n.start_offset,end:n.end_offset,type:"note",id:Number(n.id)});
      }
    }
    for (const r of links||[]) {
      if (Number.isInteger(r.start_offset) && Number.isInteger(r.end_offset) &&
          r.start_offset>=0 && r.end_offset<=text.length && r.end_offset>r.start_offset &&
          text.slice(r.start_offset,r.end_offset).trim()===(r.quote||"").trim()) {
        all.push({start:r.start_offset,end:r.end_offset,type:"link",id:String(r.source)+"-"+r.id});
      }
    }
    const points=Array.from(new Set([0,text.length,...all.flatMap(x=>[x.start,x.end])])).sort((a,b)=>a-b);
    const segments=[];
    for(let i=0;i+1<points.length;i++){
      const start=points[i],end=points[i+1];
      if(start===end)continue;
      const active=all.filter(x=>x.start<end&&x.end>start);
      segments.push({start,end,notes:active.filter(x=>x.type==="note").map(x=>x.id),
        links:active.filter(x=>x.type==="link").map(x=>x.id)});
    }
    return segments;
  }
  function render(element,text,notes,links) {
    if(!element)return;
    const fragment=document.createDocumentFragment();
    for(const item of spans(text,notes,links)){
      const piece=text.slice(item.start,item.end);
      if(!item.notes.length&&!item.links.length){
        fragment.append(document.createTextNode(piece));continue;
      }
      const mark=document.createElement("mark");
      mark.className="study-mark"+(item.notes.length?" has-note":"")+(item.links.length?" has-link":"");
      mark.textContent=piece;mark.tabIndex=0;mark.setAttribute("role","button");
      mark.setAttribute("aria-label","Ver anotaciones o recursos vinculados a este fragmento");
      mark.dataset.start=String(item.start);
      mark.dataset.end=String(item.end);
      mark.dataset.notes=item.notes.join(",");
      mark.dataset.links=item.links.join(",");
      mark.title=(item.notes.length?item.notes.length+" nota(s) ":"")+
        (item.links.length?item.links.length+" recurso(s)":"");
      fragment.append(mark);
    }
    element.replaceChildren(fragment);
  }
  function rangeFromOffsets(element,start,end) {
    if(!element||!Number.isInteger(start)||!Number.isInteger(end)||start>=end)return null;
    const walker=document.createTreeWalker(element,NodeFilter.SHOW_TEXT);
    let node,offset=0,begin=null,finish=null;
    while((node=walker.nextNode())){
      const length=node.textContent.length;
      if(!begin&&start>=offset&&start<offset+length)begin={node,offset:start-offset};
      if(end>offset&&end<=offset+length){finish={node,offset:end-offset};break;}
      offset+=length;
    }
    if(!begin||!finish)return null;
    const range=document.createRange();
    range.setStart(begin.node,begin.offset);range.setEnd(finish.node,finish.offset);
    return range;
  }
  function locate(element,start,end) {
    const range=rangeFromOffsets(element,start,end);
    if(!range)return false;
    const selected=window.getSelection();
    if(selected){selected.removeAllRanges();selected.addRange(range);}
    (range.startContainer.parentElement||element).scrollIntoView({block:"center",behavior:"smooth"});
    return true;
  }
  const api={spans,render,locate,rangeFromOffsets};
  if(typeof module!=="undefined"&&module.exports)module.exports=api;
  else root.ScripturaMarks=api;
})(typeof window!=="undefined"?window:{});
