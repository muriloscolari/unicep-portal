package me.muriloscolari.unicep.ui.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Ícones vetoriais próprios (viewport 24x24, stroke 1.75dp arredondado)
// inspirados nos padrões geométricos do Lucide (ISC) e diretrizes de peso visual iOS.
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

    private fun ImageVector.Builder.strokePath(
        strokeWidth: Float = 1.75f,
        pathData: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit
    ) {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathData
        )
    }

    private fun ImageVector.Builder.fillPath(
        pathData: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit
    ) {
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
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
                moveTo(4f, 19.5f)
                horizontalLineTo(20f)
                moveTo(6.5f, 16f)
                verticalLineTo(11f)
                horizontalLineTo(9.5f)
                verticalLineTo(16f)
                moveTo(10.5f, 16f)
                verticalLineTo(5.5f)
                horizontalLineTo(13.5f)
                verticalLineTo(16f)
                moveTo(14.5f, 16f)
                verticalLineTo(8.5f)
                horizontalLineTo(17.5f)
                verticalLineTo(16f)
            }
        }
    }

    val GradesFill: ImageVector by lazy {
        strokeIcon("GradesFill") {
            fillPath {
                moveTo(5.5f, 10.5f)
                horizontalLineTo(9f)
                verticalLineTo(17.5f)
                horizontalLineTo(5.5f)
                close()
                moveTo(10.25f, 4.5f)
                horizontalLineTo(13.75f)
                verticalLineTo(17.5f)
                horizontalLineTo(10.25f)
                close()
                moveTo(15f, 7.5f)
                horizontalLineTo(18.5f)
                verticalLineTo(17.5f)
                horizontalLineTo(15f)
                close()
            }
            strokePath {
                moveTo(3.5f, 19.5f)
                horizontalLineTo(20.5f)
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
                moveTo(20f, 10f)
                verticalLineTo(16f)
            }
        }
    }

    val AcademicFill: ImageVector by lazy {
        strokeIcon("AcademicFill") {
            fillPath {
                moveTo(1.8f, 9f)
                lineTo(12f, 4.3f)
                lineTo(22.2f, 9f)
                lineTo(12f, 13.7f)
                close()
                moveTo(6f, 11.8f)
                lineTo(12f, 14.6f)
                lineTo(18f, 11.8f)
                verticalLineTo(16.6f)
                curveTo(18f, 18.2f, 15.3f, 19.7f, 12f, 19.7f)
                curveTo(8.7f, 19.7f, 6f, 18.2f, 6f, 16.6f)
                close()
            }
            strokePath {
                moveTo(20.5f, 9.5f)
                verticalLineTo(16f)
            }
        }
    }

    val More: ImageVector by lazy {
        strokeIcon("More") {
            strokePath {
                moveTo(4f, 5.5f)
                curveTo(4f, 4.67f, 4.67f, 4f, 5.5f, 4f)
                horizontalLineTo(9f)
                curveTo(9.83f, 4f, 10.5f, 4.67f, 10.5f, 5.5f)
                verticalLineTo(9f)
                curveTo(10.5f, 9.83f, 9.83f, 10.5f, 9f, 10.5f)
                horizontalLineTo(5.5f)
                curveTo(4.67f, 10.5f, 4f, 9.83f, 4f, 9f)
                close()

                moveTo(13.5f, 5.5f)
                curveTo(13.5f, 4.67f, 14.17f, 4f, 15f, 4f)
                horizontalLineTo(18.5f)
                curveTo(19.33f, 4f, 20f, 4.67f, 20f, 5.5f)
                verticalLineTo(9f)
                curveTo(20f, 9.83f, 19.33f, 10.5f, 18.5f, 10.5f)
                horizontalLineTo(15f)
                curveTo(14.17f, 10.5f, 13.5f, 9.83f, 13.5f, 9f)
                close()

                moveTo(4f, 15f)
                curveTo(4f, 14.17f, 4.67f, 13.5f, 5.5f, 13.5f)
                horizontalLineTo(9f)
                curveTo(9.83f, 13.5f, 10.5f, 14.17f, 10.5f, 15f)
                verticalLineTo(18.5f)
                curveTo(10.5f, 19.33f, 9.83f, 20f, 9f, 20f)
                horizontalLineTo(5.5f)
                curveTo(4.67f, 20f, 4f, 19.33f, 4f, 18.5f)
                close()

                moveTo(13.5f, 15f)
                curveTo(13.5f, 14.17f, 14.17f, 13.5f, 15f, 13.5f)
                horizontalLineTo(18.5f)
                curveTo(19.33f, 13.5f, 20f, 14.17f, 20f, 15f)
                verticalLineTo(18.5f)
                curveTo(20f, 19.33f, 19.33f, 20f, 18.5f, 20f)
                horizontalLineTo(15f)
                curveTo(14.17f, 20f, 13.5f, 19.33f, 13.5f, 18.5f)
                close()
            }
        }
    }

    val MoreFill: ImageVector by lazy {
        strokeIcon("MoreFill") {
            fillPath {
                moveTo(3.8f, 5.5f)
                curveTo(3.8f, 4.56f, 4.56f, 3.8f, 5.5f, 3.8f)
                horizontalLineTo(9.2f)
                curveTo(10.14f, 3.8f, 10.9f, 4.56f, 10.9f, 5.5f)
                verticalLineTo(9.2f)
                curveTo(10.9f, 10.14f, 10.14f, 10.9f, 9.2f, 10.9f)
                horizontalLineTo(5.5f)
                curveTo(4.56f, 10.9f, 3.8f, 10.14f, 3.8f, 9.2f)
                close()

                moveTo(13.1f, 5.5f)
                curveTo(13.1f, 4.56f, 13.86f, 3.8f, 14.8f, 3.8f)
                horizontalLineTo(18.5f)
                curveTo(19.44f, 3.8f, 20.2f, 4.56f, 20.2f, 5.5f)
                verticalLineTo(9.2f)
                curveTo(20.2f, 10.14f, 19.44f, 10.9f, 18.5f, 10.9f)
                horizontalLineTo(14.8f)
                curveTo(13.86f, 10.9f, 13.1f, 10.14f, 13.1f, 9.2f)
                close()

                moveTo(3.8f, 14.8f)
                curveTo(3.8f, 13.86f, 4.56f, 13.1f, 5.5f, 13.1f)
                horizontalLineTo(9.2f)
                curveTo(10.14f, 13.1f, 10.9f, 13.86f, 10.9f, 14.8f)
                verticalLineTo(18.5f)
                curveTo(10.9f, 19.44f, 10.14f, 20.2f, 9.2f, 20.2f)
                horizontalLineTo(5.5f)
                curveTo(4.56f, 20.2f, 3.8f, 19.44f, 3.8f, 18.5f)
                close()

                moveTo(13.1f, 14.8f)
                curveTo(13.1f, 13.86f, 13.86f, 13.1f, 14.8f, 13.1f)
                horizontalLineTo(18.5f)
                curveTo(19.44f, 13.1f, 20.2f, 13.86f, 20.2f, 14.8f)
                verticalLineTo(18.5f)
                curveTo(20.2f, 19.44f, 19.44f, 20.2f, 18.5f, 20.2f)
                horizontalLineTo(14.8f)
                curveTo(13.86f, 20.2f, 13.1f, 19.44f, 13.1f, 18.5f)
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
            strokePath(strokeWidth = 2.1f) {
                moveTo(9f, 6f)
                lineTo(15f, 12f)
                lineTo(9f, 18f)
            }
        }
    }

    val ChevronDown: ImageVector by lazy {
        strokeIcon("ChevronDown") {
            strokePath(strokeWidth = 2f) {
                moveTo(6f, 9f)
                lineTo(12f, 15f)
                lineTo(18f, 9f)
            }
        }
    }

    val Close: ImageVector by lazy {
        strokeIcon("Close") {
            strokePath(strokeWidth = 2f) {
                moveTo(6f, 6f)
                lineTo(18f, 18f)
                moveTo(18f, 6f)
                lineTo(6f, 18f)
            }
        }
    }

    val Check: ImageVector by lazy {
        strokeIcon("Check") {
            strokePath(strokeWidth = 2.1f) {
                moveTo(5f, 12.5f)
                lineTo(9.5f, 17f)
                lineTo(19f, 7f)
            }
        }
    }

    val CheckCircle: ImageVector by lazy {
        strokeIcon("CheckCircle") {
            strokePath {
                moveTo(21f, 12f)
                curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
                curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
                curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
                curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
                close()
                moveTo(8.5f, 12.3f)
                lineTo(11f, 14.8f)
                lineTo(15.8f, 9.5f)
            }
        }
    }

    val Warning: ImageVector by lazy {
        strokeIcon("Warning") {
            strokePath {
                moveTo(10.3f, 3.86f)
                lineTo(2.4f, 17.5f)
                curveTo(1.7f, 18.7f, 2.57f, 20.2f, 3.95f, 20.2f)
                horizontalLineTo(20.05f)
                curveTo(21.43f, 20.2f, 22.3f, 18.7f, 21.6f, 17.5f)
                lineTo(13.7f, 3.86f)
                curveTo(12.95f, 2.58f, 11.05f, 2.58f, 10.3f, 3.86f)
                close()
                moveTo(12f, 9.2f)
                verticalLineTo(13.5f)
                moveTo(12f, 16.8f)
                verticalLineTo(17f)
            }
        }
    }

    val Search: ImageVector by lazy {
        strokeIcon("Search") {
            strokePath {
                moveTo(16.5f, 10.5f)
                curveTo(16.5f, 13.81f, 13.81f, 16.5f, 10.5f, 16.5f)
                curveTo(7.19f, 16.5f, 4.5f, 13.81f, 4.5f, 10.5f)
                curveTo(4.5f, 7.19f, 7.19f, 4.5f, 10.5f, 4.5f)
                curveTo(13.81f, 4.5f, 16.5f, 7.19f, 16.5f, 10.5f)
                close()
                moveTo(15f, 15f)
                lineTo(19.5f, 19.5f)
            }
        }
    }

    val Person: ImageVector by lazy {
        strokeIcon("Person") {
            strokePath {
                moveTo(15.5f, 7.5f)
                curveTo(15.5f, 9.43f, 13.93f, 11f, 12f, 11f)
                curveTo(10.07f, 11f, 8.5f, 9.43f, 8.5f, 7.5f)
                curveTo(8.5f, 5.57f, 10.07f, 4f, 12f, 4f)
                curveTo(13.93f, 4f, 15.5f, 5.57f, 15.5f, 7.5f)
                close()
                moveTo(4.5f, 20f)
                curveTo(5.2f, 16.2f, 8.3f, 14f, 12f, 14f)
                curveTo(15.7f, 14f, 18.8f, 16.2f, 19.5f, 20f)
            }
        }
    }

    val CreditCard: ImageVector by lazy {
        strokeIcon("CreditCard") {
            strokePath {
                moveTo(3f, 6.5f)
                curveTo(3f, 5.67f, 3.67f, 5f, 4.5f, 5f)
                horizontalLineTo(19.5f)
                curveTo(20.33f, 5f, 21f, 5.67f, 21f, 6.5f)
                verticalLineTo(17.5f)
                curveTo(21f, 18.33f, 20.33f, 19f, 19.5f, 19f)
                horizontalLineTo(4.5f)
                curveTo(3.67f, 19f, 3f, 18.33f, 3f, 17.5f)
                close()
                moveTo(3f, 10f)
                horizontalLineTo(21f)
                moveTo(7f, 14.5f)
                horizontalLineTo(10.5f)
            }
        }
    }

    val Document: ImageVector by lazy {
        strokeIcon("Document") {
            strokePath {
                moveTo(6f, 3f)
                horizontalLineTo(14.5f)
                lineTo(18.5f, 7f)
                verticalLineTo(21f)
                horizontalLineTo(6f)
                close()
                moveTo(14f, 3f)
                verticalLineTo(7.5f)
                horizontalLineTo(18.5f)
                moveTo(9f, 12f)
                horizontalLineTo(15f)
                moveTo(9f, 15.8f)
                horizontalLineTo(13.5f)
            }
        }
    }

    val Paintbrush: ImageVector by lazy {
        strokeIcon("Paintbrush") {
            strokePath {
                moveTo(14.5f, 4f)
                lineTo(20f, 9.5f)
                lineTo(11.5f, 18f)
                lineTo(6f, 12.5f)
                close()
                moveTo(7.5f, 14f)
                lineTo(4.5f, 17f)
                curveTo(3.5f, 18f, 3.8f, 19.5f, 5f, 20f)
                curveTo(6.5f, 20.5f, 8.2f, 19.5f, 9f, 18f)
                lineTo(10f, 16.5f)
            }
        }
    }

    val Logout: ImageVector by lazy {
        strokeIcon("Logout") {
            strokePath {
                moveTo(10f, 4.5f)
                horizontalLineTo(5.5f)
                curveTo(4.67f, 4.5f, 4f, 5.17f, 4f, 6f)
                verticalLineTo(18f)
                curveTo(4f, 18.83f, 4.67f, 19.5f, 5.5f, 19.5f)
                horizontalLineTo(10f)
                moveTo(15f, 8f)
                lineTo(19.5f, 12f)
                lineTo(15f, 16f)
                moveTo(9.5f, 12f)
                horizontalLineTo(19f)
            }
        }
    }

    val Sparkles: ImageVector by lazy {
        strokeIcon("Sparkles") {
            strokePath {
                moveTo(12f, 3f)
                lineTo(13.9f, 8.6f)
                lineTo(19.5f, 10.5f)
                lineTo(13.9f, 12.4f)
                lineTo(12f, 18f)
                lineTo(10.1f, 12.4f)
                lineTo(4.5f, 10.5f)
                lineTo(10.1f, 8.6f)
                close()
                moveTo(18.5f, 15.5f)
                lineTo(19.2f, 17.3f)
                lineTo(21f, 18f)
                lineTo(19.2f, 18.7f)
                lineTo(18.5f, 20.5f)
                lineTo(17.8f, 18.7f)
                lineTo(16f, 18f)
                lineTo(17.8f, 17.3f)
                close()
            }
        }
    }

    val Calendar: ImageVector by lazy {
        strokeIcon("Calendar") {
            strokePath {
                moveTo(4f, 6.5f)
                curveTo(4f, 5.67f, 4.67f, 5f, 5.5f, 5f)
                horizontalLineTo(18.5f)
                curveTo(19.33f, 5f, 20f, 5.67f, 20f, 6.5f)
                verticalLineTo(19f)
                curveTo(20f, 19.83f, 19.33f, 20.5f, 18.5f, 20.5f)
                horizontalLineTo(5.5f)
                curveTo(4.67f, 20.5f, 4f, 19.83f, 4f, 19f)
                close()
                moveTo(8f, 3f)
                verticalLineTo(6.5f)
                moveTo(16f, 3f)
                verticalLineTo(6.5f)
                moveTo(4f, 10f)
                horizontalLineTo(20f)
            }
        }
    }

    val Clock: ImageVector by lazy {
        strokeIcon("Clock") {
            strokePath {
                moveTo(21f, 12f)
                curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
                curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
                curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
                curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
                close()
                moveTo(12f, 7f)
                verticalLineTo(12.2f)
                lineTo(15.5f, 14.2f)
            }
        }
    }

    val Eye: ImageVector by lazy {
        strokeIcon("Eye") {
            strokePath {
                moveTo(2.5f, 12f)
                curveTo(4.5f, 7.8f, 8f, 5.5f, 12f, 5.5f)
                curveTo(16f, 5.5f, 19.5f, 7.8f, 21.5f, 12f)
                curveTo(19.5f, 16.2f, 16f, 18.5f, 12f, 18.5f)
                curveTo(8f, 18.5f, 4.5f, 16.2f, 2.5f, 12f)
                close()
                moveTo(14.8f, 12f)
                curveTo(14.8f, 13.55f, 13.55f, 14.8f, 12f, 14.8f)
                curveTo(10.45f, 14.8f, 9.2f, 13.55f, 9.2f, 12f)
                curveTo(9.2f, 10.45f, 10.45f, 9.2f, 12f, 9.2f)
                curveTo(13.55f, 9.2f, 14.8f, 10.45f, 14.8f, 12f)
                close()
            }
        }
    }

    val EyeOff: ImageVector by lazy {
        strokeIcon("EyeOff") {
            strokePath {
                moveTo(4f, 4f)
                lineTo(20f, 20f)
                moveTo(10.6f, 5.7f)
                curveTo(11.05f, 5.57f, 11.52f, 5.5f, 12f, 5.5f)
                curveTo(16f, 5.5f, 19.5f, 7.8f, 21.5f, 12f)
                curveTo(20.6f, 13.9f, 19.4f, 15.4f, 18f, 16.5f)
                moveTo(6.2f, 7.5f)
                curveTo(4.6f, 8.6f, 3.4f, 10.1f, 2.5f, 12f)
                curveTo(4.5f, 16.2f, 8f, 18.5f, 12f, 18.5f)
                curveTo(13.5f, 18.5f, 14.9f, 18.2f, 16.1f, 17.6f)
            }
        }
    }

    val ArrowUpRight: ImageVector by lazy {
        strokeIcon("ArrowUpRight") {
            strokePath {
                moveTo(7f, 17f)
                lineTo(17f, 7f)
                moveTo(9f, 7f)
                horizontalLineTo(17f)
                verticalLineTo(15f)
            }
        }
    }

    val Globe: ImageVector by lazy {
        strokeIcon("Globe") {
            strokePath {
                moveTo(21f, 12f)
                curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
                curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
                curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
                curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
                close()
                moveTo(3.5f, 12f)
                horizontalLineTo(20.5f)
                moveTo(12f, 3f)
                curveTo(14.5f, 5.8f, 15.8f, 8.8f, 15.8f, 12f)
                curveTo(15.8f, 15.2f, 14.5f, 18.2f, 12f, 21f)
                curveTo(9.5f, 18.2f, 8.2f, 15.2f, 8.2f, 12f)
                curveTo(8.2f, 8.8f, 9.5f, 5.8f, 12f, 3f)
                close()
            }
        }
    }

    val Download: ImageVector by lazy {
        strokeIcon("Download") {
            strokePath {
                moveTo(12f, 4f)
                verticalLineTo(15.5f)
                moveTo(7.5f, 11.5f)
                lineTo(12f, 16f)
                lineTo(16.5f, 11.5f)
                moveTo(4.5f, 19.5f)
                horizontalLineTo(19.5f)
            }
        }
    }

    val Lock: ImageVector by lazy {
        strokeIcon("Lock") {
            strokePath {
                moveTo(5.5f, 11f)
                horizontalLineTo(18.5f)
                verticalLineTo(19.5f)
                horizontalLineTo(5.5f)
                close()
                moveTo(8f, 11f)
                verticalLineTo(8f)
                curveTo(8f, 5.8f, 9.8f, 4f, 12f, 4f)
                curveTo(14.2f, 4f, 16f, 5.8f, 16f, 8f)
                verticalLineTo(11f)
            }
        }
    }

    val Book: ImageVector by lazy {
        strokeIcon("Book") {
            strokePath {
                moveTo(4f, 19.5f)
                curveTo(4f, 18.12f, 5.12f, 17f, 6.5f, 17f)
                horizontalLineTo(20f)
                moveTo(6.5f, 3f)
                horizontalLineTo(20f)
                verticalLineTo(21f)
                horizontalLineTo(6.5f)
                curveTo(5.12f, 21f, 4f, 19.88f, 4f, 18.5f)
                verticalLineTo(5.5f)
                curveTo(4f, 4.12f, 5.12f, 3f, 6.5f, 3f)
                close()
            }
        }
    }

    val Info: ImageVector by lazy {
        strokeIcon("Info") {
            strokePath {
                moveTo(21f, 12f)
                curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
                curveTo(7.03f, 21f, 3f, 16.97f, 3f, 12f)
                curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
                curveTo(16.97f, 3f, 21f, 7.03f, 21f, 12f)
                close()
                moveTo(12f, 16f)
                verticalLineTo(11.5f)
                moveTo(12f, 8.2f)
                verticalLineTo(8.3f)
            }
        }
    }

    val Share: ImageVector by lazy {
        strokeIcon("Share") {
            strokePath {
                moveTo(12f, 3f)
                verticalLineTo(15f)
                moveTo(8f, 7f)
                lineTo(12f, 3f)
                lineTo(16f, 7f)
                moveTo(5f, 12f)
                verticalLineTo(18f)
                curveTo(5f, 19.66f, 6.34f, 21f, 8f, 21f)
                horizontalLineTo(16f)
                curveTo(17.66f, 21f, 19f, 19.66f, 19f, 18f)
                verticalLineTo(12f)
            }
        }
    }

    val ChevronLeft: ImageVector by lazy {
        strokeIcon("ChevronLeft") {
            strokePath {
                moveTo(15f, 6f)
                lineTo(9f, 12f)
                lineTo(15f, 18f)
            }
        }
    }

    val Virtual: ImageVector by lazy {
        strokeIcon("Virtual") {
            strokePath {
                moveTo(4f, 6.5f)
                curveTo(4f, 5.4f, 4.9f, 4.5f, 6f, 4.5f)
                horizontalLineTo(18f)
                curveTo(19.1f, 4.5f, 20f, 5.4f, 20f, 6.5f)
                verticalLineTo(15.5f)
                horizontalLineTo(4f)
                close()
                moveTo(2.5f, 18.5f)
                horizontalLineTo(21.5f)
            }
        }
    }

    val VirtualFill: ImageVector by lazy {
        strokeIcon("VirtualFill") {
            fillPath {
                moveTo(4f, 6.5f)
                curveTo(4f, 5.4f, 4.9f, 4.5f, 6f, 4.5f)
                horizontalLineTo(18f)
                curveTo(19.1f, 4.5f, 20f, 5.4f, 20f, 6.5f)
                verticalLineTo(15.5f)
                horizontalLineTo(4f)
                close()
            }
            strokePath {
                moveTo(2.5f, 18.5f)
                horizontalLineTo(21.5f)
            }
        }
    }

    val Folder: ImageVector by lazy {
        strokeIcon("Folder") {
            strokePath {
                moveTo(3.5f, 6.5f)
                curveTo(3.5f, 5.4f, 4.4f, 4.5f, 5.5f, 4.5f)
                horizontalLineTo(9.5f)
                lineTo(11.5f, 7f)
                horizontalLineTo(18.5f)
                curveTo(19.6f, 7f, 20.5f, 7.9f, 20.5f, 9f)
                verticalLineTo(17.5f)
                curveTo(20.5f, 18.6f, 19.6f, 19.5f, 18.5f, 19.5f)
                horizontalLineTo(5.5f)
                curveTo(4.4f, 19.5f, 3.5f, 18.6f, 3.5f, 17.5f)
                close()
            }
        }
    }
}
