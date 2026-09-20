package com.webappypie.optilens.core.camera.document

/**
 * 2D normalized point in [0.0, 1.0] viewfinder/image coordinates.
 */
data class DocumentPoint(
    val x: Float,
    val y: Float,
)

/**
 * Quadrilateral boundary defining the 4 detected corners of a document page.
 */
data class DocumentQuad(
    val topLeft: DocumentPoint,
    val topRight: DocumentPoint,
    val bottomRight: DocumentPoint,
    val bottomLeft: DocumentPoint,
) {
    val isValid: Boolean
        get() = topLeft.x < topRight.x &&
                bottomLeft.x < bottomRight.x &&
                topLeft.y < bottomLeft.y &&
                topRight.y < bottomRight.y

    companion object {
        /**
         * Default fallback quadrilateral with 5% margins from image boundaries.
         */
        val DEFAULT = DocumentQuad(
            topLeft = DocumentPoint(0.05f, 0.05f),
            topRight = DocumentPoint(0.95f, 0.05f),
            bottomRight = DocumentPoint(0.95f, 0.95f),
            bottomLeft = DocumentPoint(0.05f, 0.95f),
        )
    }
}

/**
 * Output visual mode for scanned document documents.
 */
enum class DocumentColorMode(
    val label: String,
    val description: String,
) {
    COLOR(
        label = "Color",
        description = "Original natural colors with shadow and illumination normalization",
    ),
    GRAYSCALE(
        label = "Grayscale",
        description = "Clean neutral monochrome with dynamic contrast stretch",
    ),
    BLACK_AND_WHITE(
        label = "B&W",
        description = "High-contrast adaptive binarization for crisp text documents",
    );

    companion object {
        val DEFAULT = COLOR
    }
}

/**
 * Scanned and rectified document output bundle.
 */
data class DocumentScanResult(
    val uri: String? = null,
    val quad: DocumentQuad = DocumentQuad.DEFAULT,
    val colorMode: DocumentColorMode = DocumentColorMode.COLOR,
    val width: Int = 0,
    val height: Int = 0,
    val timestampMs: Long = System.currentTimeMillis(),
)
