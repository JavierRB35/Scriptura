"use strict";
const assert=require("node:assert/strict");
const {spans}=require("./annotations.js");
const original="ABCDEFGHIJ";
const note={id:1,start_offset:0,end_offset:5,quote:"ABCDE"};
const link={id:2,source:"extra",start_offset:3,end_offset:8,quote:"DEFGH"};
const result=spans(original,[note],[link]);
assert.deepEqual(result.map(x=>[x.start,x.end,x.notes.length,x.links.length]),[
  [0,3,1,0],[3,5,1,1],[5,8,0,1],[8,10,0,0]
]);
assert.deepEqual(result[1].links,["extra-2"]);
assert.deepEqual(result[1].notes,[1]);
const invalid={...note,quote:"Otro libro"};
assert.equal(spans(original,[invalid],[]).some(x=>x.notes.length),false);
const outOfBounds={...note,start_offset:-1};
assert.equal(spans(original,[outOfBounds],[]).some(x=>x.notes.length),false);
assert.equal(spans("",[],[]).length,0);
assert.equal(spans("ABC",[],[]).map(x=>x.end).join(","),"3");
console.log("OK: segmentos, solapamientos y citas desactualizadas");
