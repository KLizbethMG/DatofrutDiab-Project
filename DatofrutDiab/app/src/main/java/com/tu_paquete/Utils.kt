package com.example.datofrutdiab // Asegúrate de que este sea tu paquete correcto

fun formatearPorcion(cantidad: Double, unidad: String): String {
    val cantRedondeada = Math.round(cantidad * 4) / 4.0

    val textoCantidad = when (cantRedondeada) {
        0.25 -> "Un cuarto de"
        0.5 -> "Media"
        0.75 -> "Tres cuartos de"
        1.0 -> "Una"
        1.5 -> "Una y media"
        2.0 -> "Dos"
        else -> if (cantRedondeada % 1.0 == 0.0) cantRedondeada.toInt().toString() else String.format("%.1f", cantRedondeada)
    }

    val textoUnidad = when {
        cantRedondeada == 1.0 && unidad.endsWith("s") -> unidad.dropLast(1)
        cantRedondeada > 1.0 && !unidad.endsWith("s") -> "${unidad}s"
        cantRedondeada < 1.0 && unidad.lowercase() == "pieza" -> "pieza"
        else -> unidad
    }

    if (textoCantidad == "Media" || textoCantidad == "Un cuarto de") {
        return "$textoCantidad $textoUnidad"
    }

    return "$textoCantidad $textoUnidad"
}