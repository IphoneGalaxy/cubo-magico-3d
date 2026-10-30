package com.example.model

import kotlin.math.abs
import kotlin.random.Random

/**
 * As seis faces ortogonais do cubo mágico 3x3 em coordenadas 3D (mão direita):
 * +X = RIGHT (R), -X = LEFT (L), +Y = UP (U), -Y = DOWN (D), +Z = FRONT (F), -Z = BACK (B)
 */
enum class CubeFace(val nx: Int, val ny: Int, val nz: Int, val labelPt: String) {
    UP(0, 1, 0, "Cima (U)"),
    DOWN(0, -1, 0, "Baixo (D)"),
    FRONT(0, 0, 1, "Frente (F)"),
    BACK(0, 0, -1, "Trás (B)"),
    RIGHT(1, 0, 0, "Direita (R)"),
    LEFT(-1, 0, 0, "Esquerda (L)");

    companion object {
        fun fromNormal(nx: Int, ny: Int, nz: Int): CubeFace? =
            entries.firstOrNull { it.nx == nx && it.ny == ny && it.nz == nz }
    }
}

/**
 * As seis cores tradicionais do cubo mágico 3x3.
 */
enum class CubeColor(val labelPt: String, val rgbFloat: FloatArray) {
    WHITE("Branco", floatArrayOf(0.97f, 0.98f, 0.99f)),
    YELLOW("Amarelo", floatArrayOf(1.00f, 0.84f, 0.00f)),
    BLUE("Azul", floatArrayOf(0.00f, 0.42f, 1.00f)),
    GREEN("Verde", floatArrayOf(0.00f, 0.78f, 0.33f)),
    RED("Vermelho", floatArrayOf(1.00f, 0.12f, 0.27f)),
    ORANGE("Laranja", floatArrayOf(1.00f, 0.43f, 0.00f))
}

/**
 * Eixo 3D de rotação de uma camada.
 */
enum class RotationAxis { X, Y, Z }

/**
 * Notação internacional completa de movimentos do cubo mágico 3x3, incluindo camada média M.
 */
enum class CubeMove(
    val notation: String,
    val axis: RotationAxis,
    val layerCoord: Int, // -1, 0 ou +1 no eixo correspondente
    val glAngleDegrees: Float, // Ângulo de rotação física no OpenGL (regra da mão direita)
    val baseFamily: String,
    val titlePt: String,
    val explanationPt: String
) {
    F("F", RotationAxis.Z, 1, -90f, "F", "Frente Horário", "Gira a face da FRENTE 90° no sentido horário."),
    F_PRIME("F'", RotationAxis.Z, 1, 90f, "F", "Frente Anti-horário", "Gira a face da FRENTE 90° no sentido anti-horário (inverso)."),
    F2("F2", RotationAxis.Z, 1, -180f, "F", "Frente Meia-volta", "Gira a face da FRENTE 180° (meia-volta)."),

    R("R", RotationAxis.X, 1, -90f, "R", "Direita Subindo", "Gira a face da DIREITA 90° no sentido horário (subindo pela frente)."),
    R_PRIME("R'", RotationAxis.X, 1, 90f, "R", "Direita Descendo", "Gira a face da DIREITA 90° no sentido anti-horário (descendo pela frente)."),
    R2("R2", RotationAxis.X, 1, -180f, "R", "Direita Meia-volta", "Gira a face da DIREITA 180° (meia-volta)."),

    U("U", RotationAxis.Y, 1, -90f, "U", "Cima Horário", "Gira a camada de CIMA 90° no sentido horário (da direita para a esquerda na frente)."),
    U_PRIME("U'", RotationAxis.Y, 1, 90f, "U", "Cima Anti-horário", "Gira a camada de CIMA 90° no sentido anti-horário (da esquerda para a direita na frente)."),
    U2("U2", RotationAxis.Y, 1, -180f, "U", "Cima Meia-volta", "Gira a camada de CIMA 180° (meia-volta)."),

    L("L", RotationAxis.X, -1, 90f, "L", "Esquerda Descendo", "Gira a face da ESQUERDA 90° no sentido horário (descendo pela frente)."),
    L_PRIME("L'", RotationAxis.X, -1, -90f, "L", "Esquerda Subindo", "Gira a face da ESQUERDA 90° no sentido anti-horário (subindo pela frente)."),
    L2("L2", RotationAxis.X, -1, 180f, "L", "Esquerda Meia-volta", "Gira a face da ESQUERDA 180° (meia-volta)."),

    D("D", RotationAxis.Y, -1, 90f, "D", "Baixo Horário", "Gira a camada de BAIXO 90° no sentido horário (olhando por baixo)."),
    D_PRIME("D'", RotationAxis.Y, -1, -90f, "D", "Baixo Anti-horário", "Gira a camada de BAIXO 90° no sentido anti-horário (olhando por baixo)."),
    D2("D2", RotationAxis.Y, -1, 180f, "D", "Baixo Meia-volta", "Gira a camada de BAIXO 180° (meia-volta)."),

    B("B", RotationAxis.Z, -1, 90f, "B", "Trás Horário", "Gira a face de TRÁS 90° no sentido horário (olhando por trás)."),
    B_PRIME("B'", RotationAxis.Z, -1, -90f, "B", "Trás Anti-horário", "Gira a face de TRÁS 90° no sentido anti-horário (olhando por trás)."),
    B2("B2", RotationAxis.Z, -1, 180f, "B", "Trás Meia-volta", "Gira a face de TRÁS 180° (meia-volta)."),

    M("M", RotationAxis.X, 0, 90f, "M", "Meio Descendo", "Gira a camada vertical do MEIO para baixo na frente (mesmo sentido de L)."),
    M_PRIME("M'", RotationAxis.X, 0, -90f, "M", "Meio Subindo", "Gira a camada vertical do MEIO para cima na frente (mesmo sentido de R)."),
    M2("M2", RotationAxis.X, 0, 180f, "M", "Meio Meia-volta", "Gira a camada vertical do MEIO 180° (meia-volta)."),

    E("E", RotationAxis.Y, 0, 90f, "E", "Meio Horiz. Direita", "Gira a camada horizontal do MEIO para a direita na frente (mesmo sentido de D)."),
    E_PRIME("E'", RotationAxis.Y, 0, -90f, "E", "Meio Horiz. Esquerda", "Gira a camada horizontal do MEIO para a esquerda na frente (mesmo sentido de U)."),
    E2("E2", RotationAxis.Y, 0, 180f, "E", "Meio Horiz. Meia-volta", "Gira a camada horizontal do MEIO 180° (meia-volta)."),

    S("S", RotationAxis.Z, 0, -90f, "S", "Meio Lateral Horário", "Gira a fatia central entre Frente e Trás 90° no sentido horário (mesmo sentido de F)."),
    S_PRIME("S'", RotationAxis.Z, 0, 90f, "S", "Meio Lateral Anti-horário", "Gira a fatia central entre Frente e Trás 90° no sentido anti-horário (mesmo sentido de F')."),
    S2("S2", RotationAxis.Z, 0, -180f, "S", "Meio Lateral Meia-volta", "Gira a fatia central entre Frente e Trás 180° (meia-volta).");

    fun inverse(): CubeMove = when (this) {
        F -> F_PRIME
        F_PRIME -> F
        F2 -> F2
        R -> R_PRIME
        R_PRIME -> R
        R2 -> R2
        U -> U_PRIME
        U_PRIME -> U
        U2 -> U2
        L -> L_PRIME
        L_PRIME -> L
        L2 -> L2
        D -> D_PRIME
        D_PRIME -> D
        D2 -> D2
        B -> B_PRIME
        B_PRIME -> B
        B2 -> B2
        M -> M_PRIME
        M_PRIME -> M
        M2 -> M2
        E -> E_PRIME
        E_PRIME -> E
        E2 -> E2
        S -> S_PRIME
        S_PRIME -> S
        S2 -> S2
    }

    fun affectsCubie(cubie: Cubie): Boolean = when (axis) {
        RotationAxis.X -> cubie.x == layerCoord
        RotationAxis.Y -> cubie.y == layerCoord
        RotationAxis.Z -> cubie.z == layerCoord
    }

    companion object {
        private val byNotation = entries.associateBy { it.notation }

        fun fromNotation(token: String): CubeMove? {
            val normalized = token.trim()
                .replace("’", "'")
                .replace("‘", "'")
                .replace("′", "'")
            return byNotation[normalized]
        }

        /**
         * Converte uma string de notação de movimentos (ex.: "F U R U' R' F'") em uma lista ordenada de [CubeMove].
         * Valida explicitamente todos os tokens separados por espaços, tabulações ou quebras de linha.
         * Lança [InvalidCubeNotationException] caso qualquer token não seja reconhecido, impedindo
         * descarte silencioso ou execução parcial de sequências inválidas.
         */
        fun parseSequence(sequence: String): List<CubeMove> {
            if (sequence.isBlank()) return emptyList()
            val rawTokens = sequence.trim().split(Regex("\\s+"))
            val parsedMoves = ArrayList<CubeMove>(rawTokens.size)
            for ((index, token) in rawTokens.withIndex()) {
                val move = fromNotation(token)
                    ?: throw InvalidCubeNotationException(
                        invalidToken = token,
                        tokenIndex = index,
                        rawSequence = sequence
                    )
                parsedMoves.add(move)
            }
            return parsedMoves
        }

        /**
         * Variante segura para entradas externas em tempo de execução que retorna [Result]
         * contendo a lista validada ou a [InvalidCubeNotationException] correspondente.
         */
        fun parseSequenceResult(sequence: String): Result<List<CubeMove>> =
            runCatching { parseSequence(sequence) }

        fun invertSequence(moves: List<CubeMove>): List<CubeMove> =
            moves.asReversed().map { it.inverse() }

        fun formatSequence(moves: List<CubeMove>): String =
            moves.joinToString(" ") { it.notation }
    }
}

/**
 * Exceção identificável lançada quando [CubeMove.parseSequence] encontra um token inválido.
 * Informa o token rejeitado ([invalidToken]), seu índice 0-based ([tokenIndex]) e a sequência original ([rawSequence]).
 */
class InvalidCubeNotationException(
    val invalidToken: String,
    val tokenIndex: Int,
    val rawSequence: String
) : IllegalArgumentException(
    "Token de movimento inválido '$invalidToken' na posição $tokenIndex (1-based: ${tokenIndex + 1}) da sequência \"$rawSequence\"."
)

/**
 * Tipo estrutural de peça do cubo 3x3.
 */
enum class CubieType { CORE, CENTER, EDGE, CORNER }

/**
 * Representa uma das 27 peças tridimensionais independentes do cubo 3x3.
 */
data class Cubie(
    val id: Int,
    val homeX: Int,
    val homeY: Int,
    val homeZ: Int,
    val x: Int,
    val y: Int,
    val z: Int,
    val stickers: Map<CubeFace, CubeColor>
) {
    val type: CubieType
        get() = when (abs(x) + abs(y) + abs(z)) {
            0 -> CubieType.CORE
            1 -> CubieType.CENTER
            2 -> CubieType.EDGE
            else -> CubieType.CORNER
        }

    fun colorOn(face: CubeFace): CubeColor? = stickers[face]

    fun hasColor(color: CubeColor): Boolean = stickers.containsValue(color)

    fun colorsSet(): Set<CubeColor> = stickers.values.toSet()
}

/**
 * Estado imutável completo do Cubo Mágico 3x3.
 * Mantém sincronia matemática exata entre posições 3D e orientações dos adesivos.
 */
data class CubeState(val cubies: List<Cubie>) {

    fun cubieAt(x: Int, y: Int, z: Int): Cubie =
        cubies.first { it.x == x && it.y == y && it.z == z }

    fun stickerAt(face: CubeFace, x: Int, y: Int, z: Int): CubeColor? =
        cubieAt(x, y, z).colorOn(face)

    fun centerColor(face: CubeFace): CubeColor =
        cubieAt(face.nx, face.ny, face.nz).colorOn(face)
            ?: error("Centro sem cor na face $face")

    /**
     * Aplica um movimento da notação internacional ao estado lógico do cubo.
     */
    fun applyMove(move: CubeMove): CubeState = when (move) {
        CubeMove.R -> rotateLayerX(layerX = 1, cwFromRight = true)
        CubeMove.R_PRIME -> rotateLayerX(layerX = 1, cwFromRight = false)
        CubeMove.R2 -> rotateLayerX(layerX = 1, cwFromRight = true).rotateLayerX(layerX = 1, cwFromRight = true)

        CubeMove.L -> rotateLayerX(layerX = -1, cwFromRight = false)
        CubeMove.L_PRIME -> rotateLayerX(layerX = -1, cwFromRight = true)
        CubeMove.L2 -> rotateLayerX(layerX = -1, cwFromRight = false).rotateLayerX(layerX = -1, cwFromRight = false)

        CubeMove.M -> rotateLayerX(layerX = 0, cwFromRight = false)
        CubeMove.M_PRIME -> rotateLayerX(layerX = 0, cwFromRight = true)
        CubeMove.M2 -> rotateLayerX(layerX = 0, cwFromRight = false).rotateLayerX(layerX = 0, cwFromRight = false)

        CubeMove.U -> rotateLayerY(layerY = 1, cwFromTop = true)
        CubeMove.U_PRIME -> rotateLayerY(layerY = 1, cwFromTop = false)
        CubeMove.U2 -> rotateLayerY(layerY = 1, cwFromTop = true).rotateLayerY(layerY = 1, cwFromTop = true)

        CubeMove.D -> rotateLayerY(layerY = -1, cwFromTop = false)
        CubeMove.D_PRIME -> rotateLayerY(layerY = -1, cwFromTop = true)
        CubeMove.D2 -> rotateLayerY(layerY = -1, cwFromTop = false).rotateLayerY(layerY = -1, cwFromTop = false)

        CubeMove.E -> rotateLayerY(layerY = 0, cwFromTop = false)
        CubeMove.E_PRIME -> rotateLayerY(layerY = 0, cwFromTop = true)
        CubeMove.E2 -> rotateLayerY(layerY = 0, cwFromTop = false).rotateLayerY(layerY = 0, cwFromTop = false)

        CubeMove.F -> rotateLayerZ(layerZ = 1, cwFromFront = true)
        CubeMove.F_PRIME -> rotateLayerZ(layerZ = 1, cwFromFront = false)
        CubeMove.F2 -> rotateLayerZ(layerZ = 1, cwFromFront = true).rotateLayerZ(layerZ = 1, cwFromFront = true)

        CubeMove.B -> rotateLayerZ(layerZ = -1, cwFromFront = false)
        CubeMove.B_PRIME -> rotateLayerZ(layerZ = -1, cwFromFront = true)
        CubeMove.B2 -> rotateLayerZ(layerZ = -1, cwFromFront = false).rotateLayerZ(layerZ = -1, cwFromFront = false)

        CubeMove.S -> rotateLayerZ(layerZ = 0, cwFromFront = true)
        CubeMove.S_PRIME -> rotateLayerZ(layerZ = 0, cwFromFront = false)
        CubeMove.S2 -> rotateLayerZ(layerZ = 0, cwFromFront = true).rotateLayerZ(layerZ = 0, cwFromFront = true)
    }

    fun applyMoves(moves: Iterable<CubeMove>): CubeState =
        moves.fold(this) { acc, move -> acc.applyMove(move) }

    fun applySequence(notation: String): CubeState =
        applyMoves(CubeMove.parseSequence(notation))

    /**
     * Rotação de 90° em torno do eixo X.
     * Quando [cwFromRight] = true (sentido de R / M' / L'):
     * FRONT (+Z) -> UP (+Y) -> BACK (-Z) -> DOWN (-Y) -> FRONT (+Z)
     * (y', z') = (z, -y)
     */
    private fun rotateLayerX(layerX: Int, cwFromRight: Boolean): CubeState {
        val updated = cubies.map { cubie ->
            if (cubie.x != layerX) {
                cubie
            } else {
                val newY = if (cwFromRight) cubie.z else -cubie.z
                val newZ = if (cwFromRight) -cubie.y else cubie.y
                val newStickers = cubie.stickers.mapKeys { (face, _) ->
                    rotateFaceX(face, cwFromRight)
                }
                cubie.copy(y = newY, z = newZ, stickers = newStickers)
            }
        }
        return CubeState(updated)
    }

    private fun rotateFaceX(face: CubeFace, cwFromRight: Boolean): CubeFace =
        if (cwFromRight) {
            when (face) {
                CubeFace.FRONT -> CubeFace.UP
                CubeFace.UP -> CubeFace.BACK
                CubeFace.BACK -> CubeFace.DOWN
                CubeFace.DOWN -> CubeFace.FRONT
                CubeFace.RIGHT -> CubeFace.RIGHT
                CubeFace.LEFT -> CubeFace.LEFT
            }
        } else {
            when (face) {
                CubeFace.UP -> CubeFace.FRONT
                CubeFace.FRONT -> CubeFace.DOWN
                CubeFace.DOWN -> CubeFace.BACK
                CubeFace.BACK -> CubeFace.UP
                CubeFace.RIGHT -> CubeFace.RIGHT
                CubeFace.LEFT -> CubeFace.LEFT
            }
        }

    /**
     * Rotação de 90° em torno do eixo Y.
     * Quando [cwFromTop] = true (sentido de U / D'):
     * FRONT (+Z) -> LEFT (-X) -> BACK (-Z) -> RIGHT (+X) -> FRONT (+Z)
     * (x', z') = (-z, x)
     */
    private fun rotateLayerY(layerY: Int, cwFromTop: Boolean): CubeState {
        val updated = cubies.map { cubie ->
            if (cubie.y != layerY) {
                cubie
            } else {
                val newX = if (cwFromTop) -cubie.z else cubie.z
                val newZ = if (cwFromTop) cubie.x else -cubie.x
                val newStickers = cubie.stickers.mapKeys { (face, _) ->
                    rotateFaceY(face, cwFromTop)
                }
                cubie.copy(x = newX, z = newZ, stickers = newStickers)
            }
        }
        return CubeState(updated)
    }

    private fun rotateFaceY(face: CubeFace, cwFromTop: Boolean): CubeFace =
        if (cwFromTop) {
            when (face) {
                CubeFace.FRONT -> CubeFace.LEFT
                CubeFace.LEFT -> CubeFace.BACK
                CubeFace.BACK -> CubeFace.RIGHT
                CubeFace.RIGHT -> CubeFace.FRONT
                CubeFace.UP -> CubeFace.UP
                CubeFace.DOWN -> CubeFace.DOWN
            }
        } else {
            when (face) {
                CubeFace.FRONT -> CubeFace.RIGHT
                CubeFace.RIGHT -> CubeFace.BACK
                CubeFace.BACK -> CubeFace.LEFT
                CubeFace.LEFT -> CubeFace.FRONT
                CubeFace.UP -> CubeFace.UP
                CubeFace.DOWN -> CubeFace.DOWN
            }
        }

    /**
     * Rotação de 90° em torno do eixo Z.
     * Quando [cwFromFront] = true (sentido de F / B'):
     * UP (+Y) -> RIGHT (+X) -> DOWN (-Y) -> LEFT (-X) -> UP (+Y)
     * (x', y') = (y, -x)
     */
    private fun rotateLayerZ(layerZ: Int, cwFromFront: Boolean): CubeState {
        val updated = cubies.map { cubie ->
            if (cubie.z != layerZ) {
                cubie
            } else {
                val newX = if (cwFromFront) cubie.y else -cubie.y
                val newY = if (cwFromFront) -cubie.x else cubie.x
                val newStickers = cubie.stickers.mapKeys { (face, _) ->
                    rotateFaceZ(face, cwFromFront)
                }
                cubie.copy(x = newX, y = newY, stickers = newStickers)
            }
        }
        return CubeState(updated)
    }

    private fun rotateFaceZ(face: CubeFace, cwFromFront: Boolean): CubeFace =
        if (cwFromFront) {
            when (face) {
                CubeFace.UP -> CubeFace.RIGHT
                CubeFace.RIGHT -> CubeFace.DOWN
                CubeFace.DOWN -> CubeFace.LEFT
                CubeFace.LEFT -> CubeFace.UP
                CubeFace.FRONT -> CubeFace.FRONT
                CubeFace.BACK -> CubeFace.BACK
            }
        } else {
            when (face) {
                CubeFace.UP -> CubeFace.LEFT
                CubeFace.LEFT -> CubeFace.DOWN
                CubeFace.DOWN -> CubeFace.RIGHT
                CubeFace.RIGHT -> CubeFace.UP
                CubeFace.FRONT -> CubeFace.FRONT
                CubeFace.BACK -> CubeFace.BACK
            }
        }

    // =========================================================================
    // VALIDAÇÕES PEDAGÓGICAS DAS 7 ETAPAS (MÉTODO DE CAMADAS - camadas.pdf)
    // =========================================================================

    /**
     * Verifica se o cubo está 100% resolvido (Etapa 7 concluída).
     * Cada uma das 6 faces deve ter todos os 9 adesivos iguais ao seu centro.
     */
    fun isSolved(): Boolean {
        for (face in CubeFace.entries) {
            val targetColor = centerColor(face)
            for (cubie in cubies) {
                if (cubie.x * face.nx + cubie.y * face.ny + cubie.z * face.nz == 1) {
                    if (cubie.colorOn(face) != targetColor) return false
                }
            }
        }
        return true
    }

    /**
     * Etapa 1: Cruz Branca formada e alinhada com os 4 centros laterais.
     * Suporta tanto o centro branco em CIMA (como ilustrado no Passo 1 do PDF)
     * quanto o centro branco em BAIXO (como usado nos Passos 2 a 7).
     */
    fun isWhiteCrossSolved(): Boolean {
        return isCrossSolvedOnFace(CubeFace.UP, CubeColor.WHITE) ||
            isCrossSolvedOnFace(CubeFace.DOWN, CubeColor.WHITE)
    }

    fun isCrossSolvedOnFace(crossFace: CubeFace, crossColor: CubeColor = CubeColor.WHITE): Boolean {
        if (centerColor(crossFace) != crossColor) return false
        val yCoord = crossFace.ny
        val sideFaces = listOf(CubeFace.FRONT, CubeFace.BACK, CubeFace.LEFT, CubeFace.RIGHT)
        for (side in sideFaces) {
            val edge = cubieAt(side.nx, yCoord, side.nz)
            if (edge.colorOn(crossFace) != crossColor) return false
            if (edge.colorOn(side) != centerColor(side)) return false
        }
        return true
    }

    /**
     * Etapa 2: Primeira camada completa (Cruz Branca embaixo + 4 cantos brancos posicionados e orientados).
     */
    fun isFirstLayerSolved(): Boolean {
        if (!isCrossSolvedOnFace(CubeFace.DOWN, CubeColor.WHITE)) return false
        val sidePairs = listOf(
            CubeFace.FRONT to CubeFace.RIGHT,
            CubeFace.FRONT to CubeFace.LEFT,
            CubeFace.BACK to CubeFace.RIGHT,
            CubeFace.BACK to CubeFace.LEFT
        )
        for ((faceA, faceB) in sidePairs) {
            val corner = cubieAt(faceA.nx + faceB.nx, -1, faceA.nz + faceB.nz)
            if (corner.colorOn(CubeFace.DOWN) != CubeColor.WHITE) return false
            if (corner.colorOn(faceA) != centerColor(faceA)) return false
            if (corner.colorOn(faceB) != centerColor(faceB)) return false
        }
        return true
    }

    /**
     * Etapa 3: Segunda camada completa (1ª camada pronta + 4 meios da camada intermediária y=0).
     */
    fun isSecondLayerSolved(): Boolean {
        if (!isFirstLayerSolved()) return false
        val sidePairs = listOf(
            CubeFace.FRONT to CubeFace.RIGHT,
            CubeFace.FRONT to CubeFace.LEFT,
            CubeFace.BACK to CubeFace.RIGHT,
            CubeFace.BACK to CubeFace.LEFT
        )
        for ((faceA, faceB) in sidePairs) {
            val edge = cubieAt(faceA.nx + faceB.nx, 0, faceA.nz + faceB.nz)
            if (edge.colorOn(faceA) != centerColor(faceA)) return false
            if (edge.colorOn(faceB) != centerColor(faceB)) return false
        }
        return true
    }

    /**
     * Etapa 4: Cruz Amarela orientada no topo (2 primeiras camadas prontas + 4 meios do topo com amarelo para cima).
     */
    fun isYellowCrossOriented(): Boolean {
        if (!isSecondLayerSolved()) return false
        if (centerColor(CubeFace.UP) != CubeColor.YELLOW) return false
        val topEdges = listOf(
            cubieAt(0, 1, 1),
            cubieAt(0, 1, -1),
            cubieAt(1, 1, 0),
            cubieAt(-1, 1, 0)
        )
        return topEdges.all { it.colorOn(CubeFace.UP) == CubeColor.YELLOW }
    }

    /**
     * Etapa 4 (Caso 2 - Estado Inicial): Linha Horizontal Amarela no topo (UL em x=-1,z=0 e UR em x=1,z=0 com amarelo para cima;
     * UF e UB sem amarelo para cima; 2 primeiras camadas resolvidas).
     */
    fun isYellowHorizontalLineOnTop(): Boolean {
        if (!isSecondLayerSolved()) return false
        if (centerColor(CubeFace.UP) != CubeColor.YELLOW) return false
        val ulYellow = cubieAt(-1, 1, 0).colorOn(CubeFace.UP) == CubeColor.YELLOW
        val urYellow = cubieAt(1, 1, 0).colorOn(CubeFace.UP) == CubeColor.YELLOW
        val ufYellow = cubieAt(0, 1, 1).colorOn(CubeFace.UP) == CubeColor.YELLOW
        val ubYellow = cubieAt(0, 1, -1).colorOn(CubeFace.UP) == CubeColor.YELLOW
        return ulYellow && urYellow && !ufYellow && !ubYellow
    }

    /**
     * Etapa 4 (Caso 1 / Estado Intermediário após a Fase 1 do Caso 2): Formato de L Amarelo no topo
     * (UB em x=0,z=-1 e UL em x=-1,z=0 com amarelo para cima; UF e UR sem amarelo para cima; 2 primeiras camadas resolvidas).
     */
    fun isYellowLShapeOnTop(): Boolean {
        if (!isSecondLayerSolved()) return false
        if (centerColor(CubeFace.UP) != CubeColor.YELLOW) return false
        val ubYellow = cubieAt(0, 1, -1).colorOn(CubeFace.UP) == CubeColor.YELLOW
        val ulYellow = cubieAt(-1, 1, 0).colorOn(CubeFace.UP) == CubeColor.YELLOW
        val ufYellow = cubieAt(0, 1, 1).colorOn(CubeFace.UP) == CubeColor.YELLOW
        val urYellow = cubieAt(1, 1, 0).colorOn(CubeFace.UP) == CubeColor.YELLOW
        return ubYellow && ulYellow && !ufYellow && !urYellow
    }

    /**
     * Etapa 5: Face Amarela completamente orientada (Cruz Amarela + 4 cantos do topo com amarelo para cima).
     */
    fun isYellowFaceOriented(): Boolean {
        if (!isYellowCrossOriented()) return false
        val topCorners = listOf(
            cubieAt(1, 1, 1),
            cubieAt(-1, 1, 1),
            cubieAt(1, 1, -1),
            cubieAt(-1, 1, -1)
        )
        return topCorners.all { it.colorOn(CubeFace.UP) == CubeColor.YELLOW }
    }

    /**
     * Etapa 6: Cantos da última camada permutados (todos os 4 cantos de cima têm pares de cores iguais em todas as 4 laterais
     * e alinhados com os centros ou prontos após ajuste U).
     */
    fun isYellowCornersPermuted(requireAlignedWithCenters: Boolean = true): Boolean {
        if (!isYellowFaceOriented()) return false
        val sides = listOf(CubeFace.FRONT, CubeFace.RIGHT, CubeFace.BACK, CubeFace.LEFT)
        for (side in sides) {
            val cornersOnSide = topCornersOnSide(side)
            if (cornersOnSide.size != 2) return false
            val c1 = cornersOnSide[0].colorOn(side) ?: return false
            val c2 = cornersOnSide[1].colorOn(side) ?: return false
            if (c1 != c2) return false
            if (requireAlignedWithCenters && c1 != centerColor(side)) return false
        }
        return true
    }

    /**
     * Retorna os 2 cantos da camada superior (y = 1) pertencentes à face lateral [side] (FRONT, RIGHT, BACK ou LEFT).
     */
    fun topCornersOnSide(side: CubeFace): List<Cubie> {
        require(side.ny == 0) { "A face deve ser lateral (FRONT, RIGHT, BACK ou LEFT), mas foi $side" }
        return cubies.filter {
            it.y == 1 && abs(it.x) == 1 && abs(it.z) == 1 &&
                (it.x * side.nx + it.z * side.nz == 1)
        }
    }

    /**
     * Retorna a cor comum dos 2 cantos superiores na face lateral [side] caso formem um par ("faróis"),
     * ou `null` se os dois cantos tiverem cores diferentes nessa face lateral.
     */
    fun cornerPairColorOnSide(side: CubeFace): CubeColor? {
        if (side.ny != 0) return null
        val corners = topCornersOnSide(side)
        if (corners.size != 2) return null
        val c1 = corners[0].colorOn(side) ?: return null
        val c2 = corners[1].colorOn(side) ?: return null
        return if (c1 == c2) c1 else null
    }

    /**
     * Verifica se a face lateral [side] possui um par de cantos superiores com a mesma cor ("faróis").
     */
    fun hasCornerPairOnSide(side: CubeFace): Boolean =
        cornerPairColorOnSide(side) != null

    /**
     * Lista todas as faces laterais (na ordem FRONT, RIGHT, BACK, LEFT) que possuem um par de cantos superiores
     * com adesivos da mesma cor ("faróis").
     */
    fun findSidesWithCornerPairs(): List<CubeFace> =
        listOf(CubeFace.FRONT, CubeFace.RIGHT, CubeFace.BACK, CubeFace.LEFT)
            .filter { hasCornerPairOnSide(it) }

    /**
     * Quando existe exatamente 1 par de cantos iguais ("faróis") nas faces laterais (situação do Caso 1 da Etapa 6
     * ou após a Fase 1 do Caso 2), determina qual giro da camada superior (U, U' ou U2) posiciona esse par
     * na face traseira (BACK). Retorna `null` se o par já estiver em BACK ou se não houver exatamente 1 par.
     */
    fun findTopMoveToPlaceCornerPairOnBack(): CubeMove? {
        val pairSides = findSidesWithCornerPairs()
        if (pairSides.size != 1) return null
        if (pairSides.first() == CubeFace.BACK) return null
        return listOf(CubeMove.U, CubeMove.U_PRIME, CubeMove.U2).firstOrNull { candidate ->
            applyMove(candidate).hasCornerPairOnSide(CubeFace.BACK)
        }
    }

    /**
     * Quando todas as 4 faces laterais já possuem pares de cantos iguais (`isYellowCornersPermuted(false) == true`),
     * determina qual giro da camada superior (U, U' ou U2) alinha os 4 pares aos seus respectivos centros laterais.
     * Retorna `null` se já estiverem alinhados aos centros ou se os 4 pares ainda não estiverem formados.
     */
    fun findTopMoveToAlignPermutedCorners(): CubeMove? {
        if (!isYellowCornersPermuted(requireAlignedWithCenters = false)) return null
        if (isYellowCornersPermuted(requireAlignedWithCenters = true)) return null
        return listOf(CubeMove.U, CubeMove.U_PRIME, CubeMove.U2).firstOrNull { candidate ->
            applyMove(candidate).isYellowCornersPermuted(requireAlignedWithCenters = true)
        }
    }

    /**
     * Atalho semântico para verificar se os 4 cantos superiores já estão permutados e alinhados aos centros laterais (Etapa 6).
     */
    fun areTopCornersPermuted(requireAlignedWithCenters: Boolean = true): Boolean =
        isYellowCornersPermuted(requireAlignedWithCenters = requireAlignedWithCenters)

    /**
     * Atalho semântico para verificar se o cubo 3x3 está 100% resolvido (Etapa 7).
     */
    fun isFullySolved(): Boolean = isSolved()

    /**
     * Verifica se o objetivo pedagógico da etapa [stepNumber] (1..7) foi atingido.
     */
    fun isStepGoalAchieved(stepNumber: Int): Boolean = when (stepNumber) {
        1 -> isWhiteCrossSolved()
        2 -> isFirstLayerSolved()
        3 -> isSecondLayerSolved()
        4 -> isYellowCrossOriented()
        5 -> isYellowFaceOriented()
        6 -> isYellowCornersPermuted(requireAlignedWithCenters = true)
        7 -> isSolved()
        else -> isSolved()
    }

    companion object {
        /**
         * Cria um cubo 3x3 resolvido padrão (Amarelo em cima, Branco embaixo, Azul na frente, Laranja na direita, Verde atrás, Vermelho na esquerda),
         * exatamente como nas ilustrações dos Passos 2 a 7 de camadas.pdf.
         */
        fun createSolved(whiteOnTop: Boolean = false): CubeState {
            val faceColors = if (whiteOnTop) {
                // Passo 1 do PDF mostra a cruz branca sendo montada no topo (U = Branco, D = Amarelo, F = Azul, R = Vermelho, B = Verde, L = Laranja)
                mapOf(
                    CubeFace.UP to CubeColor.WHITE,
                    CubeFace.DOWN to CubeColor.YELLOW,
                    CubeFace.FRONT to CubeColor.BLUE,
                    CubeFace.BACK to CubeColor.GREEN,
                    CubeFace.RIGHT to CubeColor.RED,
                    CubeFace.LEFT to CubeColor.ORANGE
                )
            } else {
                // Passos 2 a 7 do PDF: Cruz Branca embaixo (D = Branco, U = Amarelo, F = Azul, R = Laranja, B = Verde, L = Vermelho)
                mapOf(
                    CubeFace.UP to CubeColor.YELLOW,
                    CubeFace.DOWN to CubeColor.WHITE,
                    CubeFace.FRONT to CubeColor.BLUE,
                    CubeFace.BACK to CubeColor.GREEN,
                    CubeFace.RIGHT to CubeColor.ORANGE,
                    CubeFace.LEFT to CubeColor.RED
                )
            }

            val list = mutableListOf<Cubie>()
            var nextId = 0
            for (x in -1..1) {
                for (y in -1..1) {
                    for (z in -1..1) {
                        val stickers = mutableMapOf<CubeFace, CubeColor>()
                        if (y == 1) stickers[CubeFace.UP] = faceColors.getValue(CubeFace.UP)
                        if (y == -1) stickers[CubeFace.DOWN] = faceColors.getValue(CubeFace.DOWN)
                        if (z == 1) stickers[CubeFace.FRONT] = faceColors.getValue(CubeFace.FRONT)
                        if (z == -1) stickers[CubeFace.BACK] = faceColors.getValue(CubeFace.BACK)
                        if (x == 1) stickers[CubeFace.RIGHT] = faceColors.getValue(CubeFace.RIGHT)
                        if (x == -1) stickers[CubeFace.LEFT] = faceColors.getValue(CubeFace.LEFT)

                        list.add(
                            Cubie(
                                id = nextId++,
                                homeX = x,
                                homeY = y,
                                homeZ = z,
                                x = x,
                                y = y,
                                z = z,
                                stickers = stickers
                            )
                        )
                    }
                }
            }
            return CubeState(list)
        }

        /**
         * Gera uma sequência válida de embaralhamento sem repetir o mesmo eixo em movimentos consecutivos.
         */
        fun generateScramble(length: Int = 20, random: Random = Random.Default): List<CubeMove> {
            val standardMoves = CubeMove.entries.filter { it.layerCoord != 0 }
            val result = mutableListOf<CubeMove>()
            var lastFamily: String? = null
            repeat(length) {
                val candidates = standardMoves.filter { it.baseFamily != lastFamily }
                val chosen = candidates[random.nextInt(candidates.size)]
                result.add(chosen)
                lastFamily = chosen.baseFamily
            }
            return result
        }
    }
}
