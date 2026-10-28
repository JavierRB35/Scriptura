"""Abre Scriptura en el navegador, manteniendo el servidor local activo."""
import threading
import webbrowser
from server import main
if __name__ == "__main__":
    threading.Timer(1.2, lambda: webbrowser.open("http://127.0.0.1:8765")).start()
    main()
