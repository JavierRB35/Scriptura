package com.scriptura.app;

import org.junit.Test;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public final class AiRouterTest {
    private final AiRouter.Request request = new AiRouter.Request("Jn 3,16", "texto", "contexto", "pregunta");

    @Test public void primarySuccessNeverCallsBackup() throws Exception {
        AtomicInteger backup = new AtomicInteger();
        AiRouter router = new AiRouter(r -> "respuesta Gemini",
            r -> { backup.incrementAndGet(); return "respuesta Groq"; });
        AiRouter.Result result = router.explain(request, true, "gemini-model", "groq-model");
        assertEquals("Gemini", result.provider);
        assertEquals(0, backup.get());
    }

    @Test public void quotaFailureCallsBackupOnlyOnceWithSameRequest() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AiRouter router = new AiRouter(r -> {throw new AiRecoverableException("HTTP 429");},
            r -> {assertSame(request, r); calls.incrementAndGet(); return "comentario";});
        AiRouter.Result result = router.explain(request, true, "gemini-model", "groq-model");
        assertEquals("Groq (respaldo)", result.provider);
        assertTrue(result.displayText().contains("comentario"));
        assertEquals(1, calls.get());
    }

    @Test public void withoutConsentNeverCallsBackup() {
        AtomicInteger calls = new AtomicInteger();
        AiRouter router = new AiRouter(r -> {throw new AiRecoverableException("HTTP 503");},
            r -> {calls.incrementAndGet(); return "NO";});
        try {
            router.explain(request, false, "gemini-model", "groq-model");
            fail("Debe informar del fallo de Gemini");
        } catch (AiRecoverableException expected) {
            assertEquals(0, calls.get());
        } catch (Exception unexpected) {
            fail(unexpected.toString());
        }
    }

    @Test public void safetyOrConfigurationErrorsDoNotTriggerBackup() {
        AtomicInteger calls = new AtomicInteger();
        AiRouter router = new AiRouter(r -> {throw new IOException("respuesta bloqueada");},
            r -> {calls.incrementAndGet(); return "NO";});
        try {
            router.explain(request, true, "gemini-model", "groq-model");
            fail("Debe conservar el error");
        } catch (IOException expected) {
            assertEquals(0, calls.get());
        } catch (Exception unexpected) {
            fail(unexpected.toString());
        }
    }

    @Test public void twoFailuresAreBoundedAndReported() {
        AtomicInteger calls = new AtomicInteger();
        AiRouter router = new AiRouter(r -> {throw new AiRecoverableException("HTTP 503");},
            r -> {calls.incrementAndGet(); throw new IOException("sin cuota");});
        try {
            router.explain(request, true, "gemini-model", "groq-model");
            fail("Ambos proveedores fallan");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Groq"));
            assertEquals(1, calls.get());
        } catch (Exception unexpected) {
            fail(unexpected.toString());
        }
    }
}
