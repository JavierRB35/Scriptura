package com.scriptura.app;

import java.io.IOException;

/** Una consulta a Gemini y, únicamente si procede, un intento de respaldo con Groq. */
final class AiRouter {
    interface Generator { String generate(Request request) throws Exception; }

    static final class Request {
        final String reference, scripture, context, question;
        Request(String reference, String scripture, String context, String question) {
            this.reference = reference;
            this.scripture = scripture;
            this.context = context;
            this.question = question;
        }
    }

    static final class Result {
        final String text, provider, model;
        Result(String text, String provider, String model) {
            this.text = text; this.provider = provider; this.model = model;
        }
        String displayText() { return provider + " · " + model + "\n\n" + text; }
    }

    private final Generator primary, backup;
    AiRouter(Generator primary, Generator backup) {
        this.primary = primary; this.backup = backup;
    }

    Result explain(Request request, boolean fallbackAllowed,
                   String geminiModel, String groqModel) throws Exception {
        try {
            return new Result(primary.generate(request), "Gemini", geminiModel);
        } catch (AiRecoverableException original) {
            if (!fallbackAllowed) throw original;
            try {
                return new Result(backup.generate(request), "Groq (respaldo)", groqModel);
            } catch (Exception failedBackup) {
                throw new IOException("Gemini no está disponible y Groq tampoco ha podido responder. "
                    + "Comprueba la conexión, las cuotas y ambas claves en Ajustes.", failedBackup);
            }
        }
    }
}
