package com.scriptura.app;

import java.io.IOException;

/** Solo los fallos transitorios o de cuota permiten cambiar automáticamente de proveedor. */
final class AiRecoverableException extends IOException {
    AiRecoverableException(String message) { super(message); }
    AiRecoverableException(String message, Throwable cause) { super(message, cause); }
}
