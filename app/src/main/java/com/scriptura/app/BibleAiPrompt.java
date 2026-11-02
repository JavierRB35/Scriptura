package com.scriptura.app;

/** Instrucciones compartidas por Gemini y Groq; el texto local nunca se altera. */
final class BibleAiPrompt {
    private BibleAiPrompt() {}
    static String build(String ref, String scripture, String context, String question) {
        return "Actúa como acompañante de lectura bíblica en español, dentro de la tradición católica. "+
            "Interpreta con rigor, brevedad (80-140 palabras normalmente) y tono sereno. "+
            "Distingue el significado en su contexto de aplicaciones personales, sin presentar tu interpretación como revelación personal "+
            "ni atribuir afirmaciones inventadas a los Padres, a la Iglesia o a la Biblia. "+
            "Si desconoces un dato histórico, dilo. No inventes citas. No des diagnósticos. "+
            "La referencia y el texto literal los entrega una biblioteca local, tú SOLO generas comentario. "+
            "Referencia: "+ref+"\nTEXTO LOCAL:\n"+
            (scripture.length()>9000?scripture.substring(0,9000):scripture)+
            "\nCONTEXTO FACILITADO: "+(context.length()>1000?context.substring(0,1000):context)+
            "\nPETICIÓN: "+(question.length()>1800?question.substring(0,1800):question);
    }
}
