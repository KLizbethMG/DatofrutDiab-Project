from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.responses import FileResponse, JSONResponse
import numpy as np
import cv2
import joblib
import os
import logging
from datetime import datetime

from utils import extraer_caracteristicas_desde_array

# ─────────────────────────────────────────────────────────────────
# CONFIGURACIÓN
# ─────────────────────────────────────────────────────────────────
CONFIANZA_MINIMA = 0.35  
LOG_PREDICCIONES = True 

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)s  %(message)s"
)
log = logging.getLogger(__name__)

app = FastAPI(
    title="DatoFrutDiab – Backend de IA",
    docs_url="/docs",
    redoc_url="/redoc"
)

# ─────────────────────────────────────────────────────────────────
# CARGA DEL MODELO
# ─────────────────────────────────────────────────────────────────
backend_dir  = os.path.dirname(os.path.abspath(__file__))
modelo_path = os.path.join(backend_dir, "modelo_frutas_v2.pkl")

if not os.path.exists(modelo_path):
    log.error(f"Modelo no encontrado en: {modelo_path}")
    modelo = None
else:
    modelo = joblib.load(modelo_path)
    clases = list(modelo.classes_)
    log.info(f"Modelo cargado. Clases ({len(clases)}): {clases}")


# ─────────────────────────────────────────────────────────────────
# PREPROCESADO DE IMAGEN
# ─────────────────────────────────────────────────────────────────
def preprocesar_imagen(img: np.ndarray) -> np.ndarray:
    """
    Solo redimensiona a un tamaño manejable si la foto es muy grande.
    El CLAHE y el resize final los hace utils.py internamente.
    """
    h, w = img.shape[:2]
    if h > 1000 or w > 1000:
        img = cv2.resize(img, (1000, 1000))
    return img


# ─────────────────────────────────────────────────────────────────
# ENDPOINTS
# ─────────────────────────────────────────────────────────────────

@app.get("/")
def inicio():
    return {
        "estado": "Backend funcionando",
        "modelo_cargado": modelo is not None,
        "hora": datetime.now().strftime("%H:%M:%S")
    }


@app.get("/diagnostico")
def diagnostico():
    """Verifica el estado completo del servidor."""
    if modelo is None:
        return JSONResponse(status_code=503, content={
            "error": "Modelo no cargado",
            "ruta_esperada": modelo_path
        })
    return {
        "estado": "OK",
        "clases_total": len(clases),
        "clases": clases,
        "confianza_minima": CONFIANZA_MINIMA
    }


@app.get("/clases")
def listar_clases():
    """Devuelve la lista de frutas que el modelo puede detectar."""
    if modelo is None:
        raise HTTPException(status_code=503, detail="Modelo no disponible")
    return {"clases": clases, "total": len(clases)}


@app.post("/predecir")
async def predecir_fruta(imagen: UploadFile = File(...)):
    """
    Recibe una foto de fruta (JPEG/PNG) y devuelve:
      - fruta_detectada : nombre de la carpeta del dataset
      - confianza       : porcentaje de certeza (0–100)
      - advertencia     : mensaje si la confianza es baja (opcional)
    """
    if modelo is None:
        raise HTTPException(status_code=503, detail="Modelo no disponible. Reinicia el servidor.")

    # ── Leer imagen ───────────────────────────────────────────────
    contenido = await imagen.read()
    if len(contenido) == 0:
        raise HTTPException(status_code=400, detail="El archivo de imagen está vacío.")

    npimg = np.frombuffer(contenido, np.uint8)
    img   = cv2.imdecode(npimg, cv2.IMREAD_COLOR)

    if img is None:
        raise HTTPException(status_code=400, detail="No se pudo decodificar la imagen. Asegúrate de enviar JPEG o PNG.")

    log.info(f"Imagen recibida → tamaño original: {img.shape[1]}x{img.shape[0]} px")

    # ── Preprocesar ───────────────────────────────────────────────
    img_proc = preprocesar_imagen(img)

    # ── Extraer características ───────────────────────────────────
    features = extraer_caracteristicas_desde_array(img_proc)

    # ── Predecir ──────────────────────────────────────────────────
    fruta_detectada  = modelo.predict([features])[0]
    probabilidades   = modelo.predict_proba([features])[0]
    confianza        = float(np.max(probabilidades))
    idx_top3         = np.argsort(probabilidades)[::-1][:3]
    top3             = [
        {"fruta": modelo.classes_[i], "confianza": round(float(probabilidades[i]) * 100, 1)}
        for i in idx_top3
    ]

    if LOG_PREDICCIONES:
        log.info(f"Predicción: {fruta_detectada}  |  Confianza: {confianza:.1%}  |  Top3: {top3}")

    # ── Respuesta ─────────────────────────────────────────────────
    respuesta = {
        "fruta_detectada": fruta_detectada,
        "confianza": round(confianza * 100, 1),
        "top3": top3
    }

    if confianza < CONFIANZA_MINIMA:
        respuesta["advertencia"] = (
            f"Confianza baja ({round(confianza*100,1)}%). "
            "Intenta con mejor iluminación, acercarte más y centrar la fruta."
        )

    return respuesta


@app.get("/favicon.ico")
def favicon():
    favicon_path = os.path.join(backend_dir, "favicon.ico")
    if os.path.exists(favicon_path):
        return FileResponse(favicon_path)
    return {"message": "Sin favicon"}
