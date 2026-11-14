package com.scriptura.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Registro local de consultas generales. Reutiliza la tabla reflections y sus copias. */
final class BibleQuestionHistory {
    static final String KEY_PREFIX="bibleqa:";

    private BibleQuestionHistory(){}

    static final class Entry {
        final String question,answer;
        final List<BibleQuestionReferences.Ref> references;
        final long created;

        Entry(String question,String answer,List<BibleQuestionReferences.Ref> references,long created){
            this.question=question;
            this.answer=answer;
            this.references=references;
            this.created=created;
        }
    }

    static String encode(String question,String answer,List<BibleQuestionReferences.Ref> references)
            throws JSONException {
        if(question==null||question.trim().isEmpty()||answer==null||answer.trim().isEmpty()
                ||references==null||references.isEmpty())
            throw new IllegalArgumentException("Una consulta guardada requiere pregunta, respuesta y citas.");
        JSONArray citations=new JSONArray();
        for(BibleQuestionReferences.Ref ref:references)citations.put(ref.query);
        return new JSONObject()
            .put("version",1)
            .put("question",question.trim())
            .put("answer",answer.trim())
            .put("citations",citations)
            .toString();
    }

    /** Una entrada dañada no impide consultar las demás ni abrir Guardados. */
    static Entry decode(String value,long created){
        if(value==null||value.isEmpty())return null;
        try{
            JSONObject json=new JSONObject(value);
            if(json.optInt("version")!=1)return null;
            String question=json.optString("question","").trim();
            String answer=json.optString("answer","").trim();
            JSONArray raw=json.optJSONArray("citations");
            if(question.isEmpty()||answer.isEmpty()||raw==null)return null;
            ArrayList<String> citations=new ArrayList<>();
            for(int i=0;i<raw.length();i++)citations.add(raw.optString(i,""));
            List<BibleQuestionReferences.Ref> references=BibleQuestionReferences.parse(citations);
            if(references.isEmpty())return null;
            return new Entry(question,answer,references,created);
        }catch(JSONException error){
            return null;
        }
    }
}
