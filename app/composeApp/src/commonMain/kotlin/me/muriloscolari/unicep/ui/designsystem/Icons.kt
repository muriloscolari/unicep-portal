package me.muriloscolari.unicep.ui.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object PortalIcons {
    private fun strokeIcon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 22.dp,
            defaultHeight = 22.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()
    }

    private fun ImageVector.Builder.strokePath(pathData: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.85f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathData
        )
    }

    val Home: ImageVector by lazy {
        strokeIcon("Home") {
            strokePath {
                moveTo(3f, 10.5f)
                lineTo(12f, 3f)
                lineTo(21f, 10.5f)
                moveTo(5f, 9.5f)
                verticalLineTo(20f)
                horizontalLineTo(19f)
                verticalLineTo(9.5f)
                moveTo(9.5f, 20f)
                verticalLineTo(14f)
                horizontalLineTo(14.5f)
                verticalLineTo(20f)
            }
        }
    }

    val Grades: ImageVector by lazy {
        strokeIcon("Grades") {
            strokePath {
                moveTo(4f, 19f)
                horizontalLineTo(20f)
                moveTo(7f, 16f)
                verticalLineTo(11f)
                moveTo(12f, 16f)
                verticalLineTo(6f)
                moveTo(17f, 16f)
                verticalLineTo(9f)
            }
        }
    }

    val Report: ImageVector by lazy {
        strokeIcon("Report") {
            strokePath {
                moveTo(6f, 3f)
                horizontalLineTo(15f)
                lineTo(19f, 7f)
                verticalLineTo(21f)
                horizontalLineTo(6f)
                close()
                moveTo(14.5f, 3f)
                verticalLineTo(7.5f)
                horizontalLineTo(19f)
                moveTo(9f, 12f)
                horizontalLineTo(15.5f)
                moveTo(9f, 16f)
                horizontalLineTo(13.5f)
            }
        }
    }

    val Academic: ImageVector by lazy {
        strokeIcon("Academic") {
            strokePath {
                moveTo(2f, 9f)
                lineTo(12f, 4.5f)
                lineTo(22f, 9f)
                lineTo(12f, 13.5f)
                close()
                moveTo(6f, 11f)
                verticalLineTo(16.5f)
                curveTo(6f, 18f, 8.7f, 19.5f, 12f, 19.5f)
                curveTo(15.3f, 19.5f, 18f, 18f, 18f, 16.5f)
                verticalLineTo(11f)
            }
        }
    }

    val More: ImageVector by lazy {
        strokeIcon("More") {
            strokePath {
                moveTo(4f, 4f)
                horizontalLineTo(10f)
                verticalLineTo(10f)
                horizontalLineTo(4f)
                close()
                moveTo(14f, 4f)
                horizontalLineTo(20f)
                verticalLineTo(10f)
                horizontalLineTo(14f)
                close()
                moveTo(4f, 14f)
                horizontalLineTo(10f)
                verticalLineTo(20f)
                horizontalLineTo(4f)
                close()
                moveTo(14f, 14f)
                horizontalLineTo(20f)
                verticalLineTo(20f)
                horizontalLineTo(14f)
                close()
            }
        }
    }

    val Bell: ImageVector by lazy {
        strokeIcon("Bell") {
            strokePath {
                moveTo(18f, 8.5f)
                curveTo(18f, 5.4f, 15.3f, 3f, 12f, 3f)
                curveTo(8.7f, 3f, 6f, 5.4f, 6f, 8.5f)
                curveTo(6f, 14f, 4f, 16f, 4f, 16f)
                horizontalLineTo(20f)
                curveTo(20f, 16f, 18f, 14f, 18f, 8.5f)
                moveTo(10f, 19.5f)
                curveTo(10.5f, 20.4f, 11.2f, 20.8f, 12f, 20.8f)
                curveTo(12.8f, 20.8f, 13.5f, 20.4f, 14f, 19.5f)
            }
        }
    }

    val Refresh: ImageVector by lazy {
        strokeIcon("Refresh") {
            strokePath {
                moveTo(20f, 11f)
                curveTo(19.5f, 6.8f, 16.1f, 3.8f, 12f, 3.8f)
                curveTo(7.5f, 3.8f, 4f, 7.4f, 4f, 12f)
                curveTo(4f, 16.6f, 7.5f, 20.2f, 12f, 20.2f)
                curveTo(15.5f, 20.2f, 18.4f, 18f, 19.5f, 14.9f)
                moveTo(20f, 5f)
                verticalLineTo(11f)
                horizontalLineTo(14f)
            }
        }
    }

    val ChevronRight: ImageVector by lazy {
        strokeIcon("ChevronRight") {
            strokePath {
                moveTo(9f, 6f)
                lineTo(15f, 12f)
                lineTo(9f, 18f)
            }
        }
    }
}
