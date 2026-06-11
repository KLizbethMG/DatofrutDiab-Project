import cv2
import numpy as np
from skimage.feature import hog

IMG_SIZE = (96, 96)   # igual que entrenar.py v2

def extraer_caracteristicas_desde_array(img: np.ndarray) -> np.ndarray:

    # 1. Resize
    img = cv2.resize(img, IMG_SIZE)

    # 2. Mejorar iluminación (CLAHE en Lab)
    lab   = cv2.cvtColor(img, cv2.COLOR_BGR2LAB)
    l, a, b = cv2.split(lab)
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    l     = clahe.apply(l)
    img   = cv2.cvtColor(cv2.merge((l, a, b)), cv2.COLOR_LAB2BGR)

    # 3. HOG (forma y bordes)
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    hog_features = hog(
        gray,
        orientations=9,
        pixels_per_cell=(8, 8),
        cells_per_block=(2, 2),
        visualize=False,
        block_norm='L2-Hys'
    )

    # 4. Histograma HSV — igual que entrenamiento [8, 4, 4]
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    hist_hsv = cv2.calcHist(
        [hsv], [0, 1, 2], None,
        [8, 4, 4],
        [0, 180, 0, 256, 0, 256]
    )
    cv2.normalize(hist_hsv, hist_hsv)
    color_hsv = hist_hsv.flatten()

    # 5. Histograma de textura Sobel — igual que entrenamiento (16 bins)
    sobelx = cv2.Sobel(gray, cv2.CV_64F, 1, 0, ksize=3)
    sobely = cv2.Sobel(gray, cv2.CV_64F, 0, 1, ksize=3)
    magnitud = np.sqrt(sobelx**2 + sobely**2)
    hist_textura, _ = np.histogram(magnitud.flatten(), bins=16, range=(0, 255))
    hist_textura = hist_textura.astype(float)
    hist_textura /= (hist_textura.sum() + 1e-7)

    # 6. Combinar todo
    return np.hstack([hog_features, color_hsv, hist_textura])
