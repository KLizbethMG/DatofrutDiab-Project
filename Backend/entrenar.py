import os
import cv2
import numpy as np
import joblib
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import threading       
import time              

from tqdm import tqdm
from skimage.feature import hog
from sklearn.svm import SVC
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.model_selection import train_test_split
from sklearn.metrics import (
    accuracy_score,
    classification_report,
    recall_score,
    confusion_matrix,
    ConfusionMatrixDisplay
)
from collections import Counter


# ============================================================
# CONFIGURACIÓN
# ============================================================

SCRIPT_DIR   = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, os.pardir, os.pardir))
DATASET_PATH = os.path.join(PROJECT_ROOT, "dataset_frutas")

MODEL_NAME  = "modelo_frutas_v2.pkl"
MATRIX_NAME = "confusion_matrix.png"

# ── Tamaño de imagen ──────────────────────────────────────
IMG_SIZE = (96, 96)

MIN_IMAGENES = 20

VALID_EXTENSIONS = {
    ".jpg", ".jpeg", ".png",
    ".bmp", ".tiff", ".tif", ".webp"
}

# ── Umbral de confianza ───────────────────────────────────
UMBRAL_CONFIANZA = 0.55

# ── Timeout por imagen ────────────────────────────────────
TIMEOUT_SEGUNDOS = 5


# ============================================================
# VALIDAR IMAGEN
# ============================================================

def es_imagen_valida(nombre):
    _, ext = os.path.splitext(nombre)
    return ext.lower() in VALID_EXTENSIONS


# ============================================================
# MEJORAR ILUMINACIÓN  (sin cambios, ya funcionaba bien)
# ============================================================

def mejorar_iluminacion(img):
    lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB)
    l, a, b = cv2.split(lab)
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    l = clahe.apply(l)
    lab = cv2.merge((l, a, b))
    return cv2.cvtColor(lab, cv2.COLOR_LAB2BGR)


# ============================================================
# AUGMENTACIÓN DE DATOS
# ============================================================

def augmentar(img):
    """
    Devuelve una lista con la imagen original + 4 variaciones.
    Total: 5 imágenes por imagen original.
    """
    variaciones = [img]  # 1. original

    # 2. Espejo horizontal (la fruta al revés sigue siendo la misma)
    variaciones.append(cv2.flip(img, 1))

    # 3. Brillo +20% (simula buena iluminación)
    bright = cv2.convertScaleAbs(img, alpha=1.2, beta=10)
    variaciones.append(bright)

    # 4. Brillo -20% (simula sombra)
    dark = cv2.convertScaleAbs(img, alpha=0.8, beta=-10)
    variaciones.append(dark)

    # 5. Rotación leve ±15°
    h, w = img.shape[:2]
    centro = (w // 2, h // 2)
    M = cv2.getRotationMatrix2D(centro, 15, 1.0)
    rotada = cv2.warpAffine(img, M, (w, h))
    variaciones.append(rotada)

    return variaciones


# ============================================================
# EXTRAER CARACTERÍSTICAS
# ============================================================

def extraer_caracteristicas(img):

    # ── 1. Resize ─────────────────────────────────────────
    img = cv2.resize(img, IMG_SIZE)

    # ── 2. Mejorar iluminación ────────────────────────────
    img = mejorar_iluminacion(img)

    # ── 3. Escala de grises ───────────────────────────────
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

    hog_features = hog(
        gray,
        orientations=9,          
        pixels_per_cell=(8, 8),  
        cells_per_block=(2, 2),  
        visualize=False,
        block_norm='L2-Hys'
    )

    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    hist_hsv = cv2.calcHist(
        [hsv],
        [0, 1, 2],
        None,
        [8, 4, 4],              
        [0, 180, 0, 256, 0, 256]
    )
    cv2.normalize(hist_hsv, hist_hsv)
    color_hsv = hist_hsv.flatten()

    sobelx = cv2.Sobel(gray, cv2.CV_64F, 1, 0, ksize=3)
    sobely = cv2.Sobel(gray, cv2.CV_64F, 0, 1, ksize=3)
    magnitud = np.sqrt(sobelx**2 + sobely**2)
    hist_textura, _ = np.histogram(
        magnitud.flatten(),
        bins=16,
        range=(0, 255)
    )
    hist_textura = hist_textura.astype(float)
    hist_textura /= (hist_textura.sum() + 1e-7)  # normalizar

    # ── 7. Combinar features ──────────────────────────────
    features = np.hstack([
        hog_features,    
        color_hsv,       
        hist_textura     
    ])

    return features


# ============================================================
# PROCESAR IMAGEN CON TIMEOUT
# ============================================================

def procesar_imagen_con_timeout(ruta, timeout=TIMEOUT_SEGUNDOS):
    """
    Intenta cargar y extraer features de una imagen.
    Devuelve (lista_de_features, None) si todo va bien.
    Devuelve (None, motivo) si hay error o timeout.
    """
    resultado = {"features": None, "error": None}

    def _worker():
        try:
            t0  = time.time()
            img = cv2.imread(ruta)

            if img is None:
                resultado["error"] = "No se pudo leer"
                return

            h, w = img.shape[:2]
            if h > 4000 or w > 4000:
                img = cv2.resize(img, (1000, 1000))

            variaciones = augmentar(img)
            feats = []
            for img_aug in variaciones:
                feats.append(extraer_caracteristicas(img_aug))

            resultado["features"] = feats
            resultado["tiempo"]   = time.time() - t0

        except Exception as e:
            resultado["error"] = str(e)

    hilo = threading.Thread(target=_worker, daemon=True)
    hilo.start()
    hilo.join(timeout=timeout)   # ← espera máximo TIMEOUT_SEGUNDOS

    if hilo.is_alive():
        # El hilo sigue corriendo → timeout
        return None, f"Timeout (>{timeout}s)"

    if resultado["error"]:
        return None, resultado["error"]

    return resultado["features"], None


# ============================================================
# INICIO
# ============================================================

print("\n" + "=" * 60)
print(" ENTRENAMIENTO MODELO DE FRUTAS  v2")
print("=" * 60)
print(f"\nDataset:\n{DATASET_PATH}\n")

if not os.path.isdir(DATASET_PATH):
    raise FileNotFoundError(f"Dataset no encontrado:\n{DATASET_PATH}")


# ============================================================
# CARGA DATASET  (con augmentación)
# ============================================================

X = []
y = []
conteo = {}

carpetas = sorted(os.listdir(DATASET_PATH))

for carpeta in carpetas:

    ruta_carpeta = os.path.join(DATASET_PATH, carpeta)

    if not os.path.isdir(ruta_carpeta):
        continue

    archivos = [
        a for a in os.listdir(ruta_carpeta)
        if es_imagen_valida(a)
    ]

    conteo[carpeta] = len(archivos)
    print(f"Cargando {carpeta} ({len(archivos)} imágenes × 5 aug = {len(archivos)*5})")

    saltadas = 0
    timeouts = 0
    lentas   = []   #

    for archivo in tqdm(archivos):

        ruta     = os.path.join(ruta_carpeta, archivo)
        t_inicio = time.time()

        feats_lista, error = procesar_imagen_con_timeout(ruta)
        t_total = time.time() - t_inicio

        if error:
            saltadas += 1
            if "Timeout" in error:
                timeouts += 1
                tqdm.write(f"  ⏱ Timeout saltado: {archivo}")
            else:
                tqdm.write(f"  ⚠ Error saltado:   {archivo} → {error}")
            continue

        if t_total > 2.0:
            lentas.append((archivo, t_total))

        for features in feats_lista:
            X.append(features)
            y.append(carpeta)

    # ── Reporte por carpeta ───────────────────────────────
    if saltadas:
        print(f"  → {saltadas} imagen(es) saltada(s) "
              f"({timeouts} por timeout)")
    if lentas:
        print(f"  → {len(lentas)} imagen(es) lenta(s) (>2s):")
        for nombre, t in lentas[:5]:
            print(f"     {nombre}  ({t:.1f}s)")


# ============================================================
# FILTRAR CLASES PEQUEÑAS
# ============================================================

clases_excluidas = [
    fruta for fruta, cantidad in conteo.items()
    if cantidad < MIN_IMAGENES
]

if clases_excluidas:
    print(f"\nClases excluidas (< {MIN_IMAGENES} imágenes): {clases_excluidas}")
    X = [f for f, l in zip(X, y) if l not in clases_excluidas]
    y = [l for l in y if l not in clases_excluidas]


# ============================================================
# CONVERTIR A NUMPY
# ============================================================

X = np.array(X)
y = np.array(y)

print(f"\nTotal imágenes (con aug): {len(X)}")
print(f"Total clases             : {len(set(y))}")

if len(X) == 0:
    print("\nERROR: No se cargaron imágenes")
    exit()


# ============================================================
# TRAIN / TEST SPLIT
# ============================================================

X_train, X_test, y_train, y_test = train_test_split(
    X, y,
    test_size=0.2,
    random_state=42,
    stratify=y
)

print(f"\nEntrenando con {len(X_train)} imágenes...")
print("Espera (SVM tarda más con más datos)...\n")


# ============================================================
# MODELO SVM MEJORADO
# ============================================================


pipeline = Pipeline([
    ('scaler', StandardScaler()),
    ('svm', SVC(
        kernel='linear',
        C=10,               
        probability=True,     
        class_weight='balanced',
        random_state=42
    ))
])


# ============================================================
# ENTRENAMIENTO
# ============================================================

pipeline.fit(X_train, y_train)
print("\nEntrenamiento terminado.")


# ============================================================
# PREDICCIONES
# ============================================================

y_pred      = pipeline.predict(X_test)
y_proba     = pipeline.predict_proba(X_test)
confianzas  = y_proba.max(axis=1)
precision   = accuracy_score(y_test, y_pred)


# ============================================================
# PREDICCIONES CON UMBRAL DE CONFIANZA
# ============================================================

clases = pipeline.classes_

def predecir_con_umbral(img_features):
    """
    Predice la fruta. Si la confianza es menor al umbral,
    devuelve 'desconocido' en lugar de adivinar.
    """
    proba  = pipeline.predict_proba([img_features])[0]
    idx    = np.argmax(proba)
    conf   = proba[idx]
    fruta  = clases[idx]

    if conf < UMBRAL_CONFIANZA:
        return "desconocido", conf

    return fruta, conf

y_pred_umbral = []
for i, feat in enumerate(X_test):
    fruta, _ = predecir_con_umbral(feat)
    y_pred_umbral.append(fruta)

rechazados = sum(1 for p in y_pred_umbral if p == "desconocido")

print("\n" + "=" * 60)
print(" RESULTADOS  v2")
print("=" * 60)

print(f"\nPrecisión general (sin umbral) : {precision * 100:.2f}%")
print(f"Imágenes rechazadas (< {UMBRAL_CONFIANZA:.0%} conf): {rechazados}/{len(X_test)}")

confianza_media = confianzas.mean()
print(f"Confianza media en prueba      : {confianza_media * 100:.1f}%\n")

print(classification_report(y_test, y_pred))

print("\nFrutas problemáticas (recall < 70%)")
labels_unicas = sorted(set(y_test))
recalls = recall_score(y_test, y_pred, labels=labels_unicas, average=None)

problemas = False
for label, rec in zip(labels_unicas, recalls):
    if rec < 0.70:
        print(
            f"  ⚠ {label:<20} "
            f"recall={rec:.0%} "
            f"(train={Counter(y_train)[label]})"
        )
        problemas = True

if not problemas:
    print("  Ninguna — ¡buen resultado!")


print(f"\nGenerando matriz de confusión → {MATRIX_NAME}")

cm   = confusion_matrix(y_test, y_pred, labels=labels_unicas)
fig, ax = plt.subplots(figsize=(14, 12))

disp = ConfusionMatrixDisplay(confusion_matrix=cm, display_labels=labels_unicas)
disp.plot(
    ax=ax,
    colorbar=True,
    xticks_rotation=45,
    cmap='Blues'
)

ax.set_title("Matriz de confusión — modelo frutas v2", fontsize=14, pad=16)
plt.tight_layout()
plt.savefig(MATRIX_NAME, dpi=150)
plt.close()

print(f"  Guardada: {MATRIX_NAME}")
print("  Cómo leerla: cada fila = clase real, cada columna = clase predicha.")
print("  Celdas fuera de la diagonal = errores del modelo.")

joblib.dump(pipeline, MODEL_NAME)

print(f"\nModelo guardado: {MODEL_NAME}")
print(f"Umbral de confianza configurado: {UMBRAL_CONFIANZA:.0%}")
print("\nSiguientes pasos:")
print("  1. Copia modelo_frutas_v2.pkl al backend FastAPI")
print("  2. Actualiza el nombre del .pkl en el backend")
print("  3. Reinicia FastAPI")
print("  4. Revisa confusion_matrix.png para ver qué frutas aún se confunden")
