package com.example.renderer

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.curriculum.CurriculumRepository
import com.example.curriculum.HighlightMode
import com.example.model.CubeFace
import com.example.model.CubeMove
import com.example.model.CubeState
import com.example.model.Cubie
import com.example.model.RotationAxis
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Estado de animação física de uma camada em rotação no espaço 3D.
 */
data class ActiveLayerAnimation(
    val move: CubeMove,
    val progress: Float // 0.0f a 1.0f
) {
    val currentAngleDegrees: Float
        get() {
            // Interpolação balanceada (35% linear + 65% cosseno suave) para movimento 3D visível desde os primeiros quadros
            val t = progress.coerceIn(0f, 1f)
            val sineEase = 0.5f * (1f - cos(t * PI.toFloat()))
            val eased = 0.35f * t + 0.65f * sineEase
            return move.glAngleDegrees * eased
        }
}

/**
 * Resultado de raycasting 3D de um toque na tela sobre o cubo 3x3.
 */
data class CubeTouchHit(
    val cubieX: Int,
    val cubieY: Int,
    val cubieZ: Int,
    val hitFace: CubeFace,
    val hitPointWorld: FloatArray
)

/**
 * Renderizador 3D OpenGL ES 2.0 nativo para o Cubo Mágico 3x3.
 * Renderiza 27 peças tridimensionais independentes com corpo de plástico ABS preto chanfrado,
 * adesivos coloridos em alto-relevo com iluminação de estúdio Blinn-Phong e sombras suaves.
 */
class CubeGLRenderer : GLSurfaceView.Renderer {

    private val sceneStateLock = Any()

    @Volatile
    var cubeState: CubeState = CubeState.createSolved()

    @Volatile
    var activeAnimation: ActiveLayerAnimation? = null

    @Volatile
    var highlightMode: HighlightMode = HighlightMode.ALL_VIVID

    @Volatile
    var highlightEnabled: Boolean = false

    @Volatile
    var isDarkBackground: Boolean = true

    @Volatile
    var autoRotateIdle: Boolean = false

    @Volatile
    var isUserTouching: Boolean = false
        private set

    @Volatile
    var resumeAutoRotateAfterUptimeMs: Long = 0L
        private set

    @Volatile
    var cameraYaw: Float = DEFAULT_YAW

    @Volatile
    var cameraPitch: Float = DEFAULT_PITCH

    @Volatile
    var cameraDistance: Float = DEFAULT_DISTANCE

    @Volatile
    var celebrationPulse: Float = 0f

    @Volatile
    var guidedMove: CubeMove? = null

    @Volatile
    var previewTouchHit: CubeTouchHit? = null

    @Volatile
    var previewTouchMove: CubeMove? = null

    @Volatile
    private var viewportWidth: Int = 1

    @Volatile
    private var viewportHeight: Int = 1

    private val recordedAnglesLock = Any()
    private val recordedAnimationAngles = mutableListOf<Float>()

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val invVpMatrix = FloatArray(16)
    private val vpLock = Any()

    fun onUserTouchStart() {
        isUserTouching = true
    }

    fun onUserTouchEnd(inactivityDelayMs: Long = 1800L) {
        isUserTouching = false
        resumeAutoRotateAfterUptimeMs = android.os.SystemClock.uptimeMillis() + inactivityDelayMs
    }

    fun clearAutoRotateCooldownForTest() {
        isUserTouching = false
        resumeAutoRotateAfterUptimeMs = 0L
    }

    fun isCurrentlyAutoRotating(): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        synchronized(sceneStateLock) {
            return autoRotateIdle && activeAnimation == null && !isUserTouching && now >= resumeAutoRotateAfterUptimeMs
        }
    }

    /**
     * Atualiza atomicamente o estado lógico do cubo e a transformação de animação ativa,
     * impedindo qualquer quadro intermediário dessincronizado entre a UI Thread e a GLThread.
     */
    fun syncSceneState(
        newCubeState: CubeState,
        newAnimation: ActiveLayerAnimation?,
        newGuidedMove: CubeMove?,
        newHighlightMode: HighlightMode,
        newHighlightEnabled: Boolean,
        newIsDarkBackground: Boolean,
        newAutoRotateIdle: Boolean,
        newCelebrationPulse: Float
    ) {
        synchronized(sceneStateLock) {
            cubeState = newCubeState
            activeAnimation = newAnimation
            guidedMove = newGuidedMove
            highlightMode = newHighlightMode
            highlightEnabled = newHighlightEnabled
            isDarkBackground = newIsDarkBackground
            autoRotateIdle = newAutoRotateIdle
            celebrationPulse = newCelebrationPulse
            if (newAnimation != null && newAutoRotateIdle) {
                val targetResume = android.os.SystemClock.uptimeMillis() + 1500L
                if (targetResume > resumeAutoRotateAfterUptimeMs) {
                    resumeAutoRotateAfterUptimeMs = targetResume
                }
            }
        }
        if (newAnimation != null) {
            synchronized(recordedAnglesLock) {
                recordedAnimationAngles.add(newAnimation.currentAngleDegrees)
            }
        }
    }

    fun getRecordedAnimationAnglesForAudit(): List<Float> = synchronized(recordedAnglesLock) {
        recordedAnimationAngles.toList()
    }

    fun clearRecordedAnimationAnglesForAudit() = synchronized(recordedAnglesLock) {
        recordedAnimationAngles.clear()
    }

    private val modelMatrix = FloatArray(16)
    private val layerRotMatrix = FloatArray(16)
    private val translateMatrix = FloatArray(16)
    private val scaleMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val eyePosBuffer = FloatArray(3)
    private val bodyColorBuffer = FloatArray(4)
    private val stickerColorBuffer = FloatArray(4)

    private var shaderProgram = 0
    private var aPositionLoc = 0
    private var aNormalLoc = 0
    private var uMvpMatrixLoc = 0
    private var uModelMatrixLoc = 0
    private var uColorLoc = 0
    private var uLightDirLoc = 0
    private var uViewPosLoc = 0
    private var uSpecularStrengthLoc = 0
    private var uShininessLoc = 0

    // Buffers de geometria 3D
    private lateinit var cubieBodyBuffer: FloatBuffer
    private var cubieBodyVertexCount = 0

    private val stickerBuffers = mutableMapOf<CubeFace, FloatBuffer>()
    private var stickerVertexCount = 0

    init {
        configureViewport(360, 360)
    }

    fun configureViewport(width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        val ratio = viewportWidth.toFloat() / viewportHeight.toFloat()
        // Garante que o campo de visão (FOV) mínimo (horizontal e vertical) seja sempre >= 40°,
        // mantendo o cubo 3D inteiramente visível tanto em telas retrato estreitas quanto em paisagem/tablets.
        val baseHalfFovRad = Math.toRadians(20.0)
        val effectiveFovY = if (ratio < 1.0f) {
            Math.toDegrees(2.0 * kotlin.math.atan(kotlin.math.tan(baseHalfFovRad) / ratio.toDouble()))
                .toFloat()
                .coerceAtMost(72f)
        } else {
            40f
        }
        synchronized(vpLock) {
            Matrix.perspectiveM(projMatrix, 0, effectiveFovY, ratio, 1.5f, 35f)
            updateViewProjectionLocked()
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        shaderProgram = createProgram(VERTEX_SHADER_CODE, FRAGMENT_SHADER_CODE)
        aPositionLoc = GLES20.glGetAttribLocation(shaderProgram, "aPosition")
        aNormalLoc = GLES20.glGetAttribLocation(shaderProgram, "aNormal")
        uMvpMatrixLoc = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix")
        uModelMatrixLoc = GLES20.glGetUniformLocation(shaderProgram, "uModelMatrix")
        uColorLoc = GLES20.glGetUniformLocation(shaderProgram, "uColor")
        uLightDirLoc = GLES20.glGetUniformLocation(shaderProgram, "uLightDir")
        uViewPosLoc = GLES20.glGetUniformLocation(shaderProgram, "uViewPos")
        uSpecularStrengthLoc = GLES20.glGetUniformLocation(shaderProgram, "uSpecularStrength")
        uShininessLoc = GLES20.glGetUniformLocation(shaderProgram, "uShininess")

        buildGeometryBuffers()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
        configureViewport(width, height)
    }

    private fun updateViewProjectionLocked() {
        val yawRad = (cameraYaw * PI / 180.0).toFloat()
        val pitchRad = (cameraPitch.coerceIn(-78f, 78f) * PI / 180.0).toFloat()
        val dist = cameraDistance.coerceIn(MIN_DISTANCE, MAX_DISTANCE)

        val eyeX = dist * cos(pitchRad) * sin(yawRad)
        val eyeY = dist * sin(pitchRad)
        val eyeZ = dist * cos(pitchRad) * cos(yawRad)

        Matrix.setLookAtM(
            viewMatrix, 0,
            eyeX, eyeY, eyeZ,
            0f, 0f, 0f,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)
        Matrix.invertM(invVpMatrix, 0, vpMatrix, 0)
    }

    /**
     * Calcula a matriz de transformação 4x4 (Model Matrix) de uma peça [cubie] no espaço 3D,
     * levando em conta qualquer animação de rotação de camada ativa [animSnapshot].
     */
    fun computeCubieModelMatrix(
        cubie: Cubie,
        animSnapshot: ActiveLayerAnimation?,
        spacing: Float = 0.98f,
        outModelMatrix: FloatArray
    ) {
        val localRot = FloatArray(16)
        val localTrans = FloatArray(16)
        Matrix.setIdentityM(localRot, 0)
        if (animSnapshot != null && animSnapshot.move.affectsCubie(cubie)) {
            val angle = animSnapshot.currentAngleDegrees
            when (animSnapshot.move.axis) {
                RotationAxis.X -> Matrix.rotateM(localRot, 0, angle, 1f, 0f, 0f)
                RotationAxis.Y -> Matrix.rotateM(localRot, 0, angle, 0f, 1f, 0f)
                RotationAxis.Z -> Matrix.rotateM(localRot, 0, angle, 0f, 0f, 1f)
            }
        }
        Matrix.setIdentityM(localTrans, 0)
        Matrix.translateM(
            localTrans, 0,
            cubie.x * spacing,
            cubie.y * spacing,
            cubie.z * spacing
        )
        Matrix.multiplyMM(outModelMatrix, 0, localRot, 0, localTrans, 0)
    }

    /**
     * Calcula a posição central 3D no mundo de uma peça [cubie] durante ou fora de uma animação.
     */
    fun computeCubieWorldCenter(
        cubie: Cubie,
        animSnapshot: ActiveLayerAnimation?,
        spacing: Float = 0.98f
    ): FloatArray {
        val m = FloatArray(16)
        computeCubieModelMatrix(cubie, animSnapshot, spacing, m)
        val out = FloatArray(4)
        Matrix.multiplyMV(out, 0, m, 0, floatArrayOf(0f, 0f, 0f, 1f), 0)
        return floatArrayOf(out[0], out[1], out[2])
    }

    /**
     * Calcula a posição central 3D e o vetor normal 3D de um adesivo na face [face] da peça [cubie].
     */
    fun computeStickerWorldCenterAndNormal(
        cubie: Cubie,
        face: CubeFace,
        animSnapshot: ActiveLayerAnimation?,
        spacing: Float = 0.98f
    ): Pair<FloatArray, FloatArray> {
        val m = FloatArray(16)
        computeCubieModelMatrix(cubie, animSnapshot, spacing, m)
        val localCenter = map2DToFace3D(face, 0f, 0f, 0.473f)
        val worldCenter4 = FloatArray(4)
        val worldNormal4 = FloatArray(4)
        Matrix.multiplyMV(
            worldCenter4, 0, m, 0,
            floatArrayOf(localCenter[0], localCenter[1], localCenter[2], 1f), 0
        )
        Matrix.multiplyMV(
            worldNormal4, 0, m, 0,
            floatArrayOf(face.nx.toFloat(), face.ny.toFloat(), face.nz.toFloat(), 0f), 0
        )
        return floatArrayOf(worldCenter4[0], worldCenter4[1], worldCenter4[2]) to
            normalize3(worldNormal4[0], worldNormal4[1], worldNormal4[2])
    }

    /**
     * Avança um quadro de auto-rotação ociosa (caso elegível), permitindo auditoria determinística
     * da pausa durante toque/animação e retomada após inatividade.
     */
    fun advanceIdleAutoRotationIfEligible(): Boolean {
        val nowUptime = android.os.SystemClock.uptimeMillis()
        val canRotate: Boolean
        synchronized(sceneStateLock) {
            canRotate = autoRotateIdle &&
                activeAnimation == null &&
                !isUserTouching &&
                nowUptime >= resumeAutoRotateAfterUptimeMs
        }
        if (canRotate) {
            cameraYaw = (cameraYaw + 0.35f) % 360f
        }
        return canRotate
    }

    override fun onDrawFrame(gl: GL10?) {
        val stateSnapshot: CubeState
        val animSnapshot: ActiveLayerAnimation?
        val guidedSnapshot: CubeMove?
        val modeSnapshot: HighlightMode
        val darkBgSnapshot: Boolean
        val autoRotateSnapshot: Boolean
        val pulse: Float

        synchronized(sceneStateLock) {
            stateSnapshot = cubeState
            animSnapshot = activeAnimation
            guidedSnapshot = animSnapshot?.move ?: previewTouchMove ?: guidedMove
            modeSnapshot = if (highlightEnabled) highlightMode else HighlightMode.ALL_VIVID
            darkBgSnapshot = isDarkBackground
            autoRotateSnapshot = autoRotateIdle
            pulse = celebrationPulse
        }

        if (animSnapshot != null) {
            synchronized(recordedAnglesLock) {
                recordedAnimationAngles.add(animSnapshot.currentAngleDegrees)
            }
        }

        // Rotação automática permitida EXCLUSIVAMENTE quando autoRotateIdle == true (Home),
        // sem animação de camada ativa, sem dedo na tela e após a janela de inatividade pós-interação
        if (autoRotateSnapshot && animSnapshot == null && !isUserTouching &&
            android.os.SystemClock.uptimeMillis() >= resumeAutoRotateAfterUptimeMs
        ) {
            cameraYaw = (cameraYaw + 0.35f) % 360f
        }

        if (darkBgSnapshot) {
            GLES20.glClearColor(0.027f, 0.043f, 0.078f, 1.0f)
        } else {
            GLES20.glClearColor(0.957f, 0.969f, 0.984f, 1.0f)
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        synchronized(vpLock) {
            updateViewProjectionLocked()
            val yawRad = (cameraYaw * PI / 180.0).toFloat()
            val pitchRad = (cameraPitch.coerceIn(-78f, 78f) * PI / 180.0).toFloat()
            val dist = cameraDistance.coerceIn(MIN_DISTANCE, MAX_DISTANCE)
            eyePosBuffer[0] = dist * cos(pitchRad) * sin(yawRad)
            eyePosBuffer[1] = dist * sin(pitchRad)
            eyePosBuffer[2] = dist * cos(pitchRad) * cos(yawRad)
        }

        GLES20.glUseProgram(shaderProgram)

        // Luz principal de estúdio posicionada acima e à frente da câmera
        val lightX = eyePosBuffer[0] * 0.8f + 3.0f
        val lightY = eyePosBuffer[1] * 0.9f + 5.5f
        val lightZ = eyePosBuffer[2] * 0.8f + 4.0f
        val len = sqrt(lightX * lightX + lightY * lightY + lightZ * lightZ).coerceAtLeast(0.001f)
        GLES20.glUniform3f(uLightDirLoc, lightX / len, lightY / len, lightZ / len)
        GLES20.glUniform3f(uViewPosLoc, eyePosBuffer[0], eyePosBuffer[1], eyePosBuffer[2])

        val baseSpacing = 0.98f + pulse * 0.06f
        val touchedHit = previewTouchHit

        for (cubie in stateSnapshot.cubies) {
            val isRotatingNow = animSnapshot != null && animSnapshot.move.affectsCubie(cubie)
            Matrix.setIdentityM(layerRotMatrix, 0)
            if (isRotatingNow && animSnapshot != null) {
                val angle = animSnapshot.currentAngleDegrees
                when (animSnapshot.move.axis) {
                    RotationAxis.X -> Matrix.rotateM(layerRotMatrix, 0, angle, 1f, 0f, 0f)
                    RotationAxis.Y -> Matrix.rotateM(layerRotMatrix, 0, angle, 0f, 1f, 0f)
                    RotationAxis.Z -> Matrix.rotateM(layerRotMatrix, 0, angle, 0f, 0f, 1f)
                }
            }

            Matrix.setIdentityM(translateMatrix, 0)
            Matrix.translateM(
                translateMatrix, 0,
                cubie.x * baseSpacing,
                cubie.y * baseSpacing,
                cubie.z * baseSpacing
            )

            Matrix.multiplyMM(modelMatrix, 0, layerRotMatrix, 0, translateMatrix, 0)
            synchronized(vpLock) {
                Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
            }

            val isHighlighted = CurriculumRepository.isCubieHighlighted(cubie, modeSnapshot)
            val isInGuidedLayer = guidedSnapshot?.affectsCubie(cubie) == true
            val isTouchedCubie = touchedHit != null &&
                cubie.x == touchedHit.cubieX &&
                cubie.y == touchedHit.cubieY &&
                cubie.z == touchedHit.cubieZ

            // 1. Renderiza o corpo de plástico chanfrado ABS sem alocações por quadro
            when {
                isRotatingNow -> {
                    bodyColorBuffer[0] = 0.14f; bodyColorBuffer[1] = 0.25f; bodyColorBuffer[2] = 0.31f; bodyColorBuffer[3] = 1.0f
                }
                isTouchedCubie || isInGuidedLayer -> {
                    bodyColorBuffer[0] = 0.11f; bodyColorBuffer[1] = 0.20f; bodyColorBuffer[2] = 0.25f; bodyColorBuffer[3] = 1.0f
                }
                isHighlighted -> {
                    bodyColorBuffer[0] = 0.10f; bodyColorBuffer[1] = 0.12f; bodyColorBuffer[2] = 0.16f; bodyColorBuffer[3] = 1.0f
                }
                else -> {
                    bodyColorBuffer[0] = 0.07f; bodyColorBuffer[1] = 0.08f; bodyColorBuffer[2] = 0.11f; bodyColorBuffer[3] = 1.0f
                }
            }
            GLES20.glUniformMatrix4fv(uMvpMatrixLoc, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(uModelMatrixLoc, 1, false, modelMatrix, 0)
            GLES20.glUniform4fv(uColorLoc, 1, bodyColorBuffer, 0)
            GLES20.glUniform1f(
                uSpecularStrengthLoc,
                when {
                    isRotatingNow -> 0.62f
                    isInGuidedLayer || isTouchedCubie -> 0.52f
                    else -> 0.40f
                }
            )
            GLES20.glUniform1f(uShininessLoc, 28.0f)
            drawMesh(cubieBodyBuffer, cubieBodyVertexCount)

            // 2. Renderiza os adesivos desta peça nas faces correspondentes sem alocações por quadro
            for ((face, color) in cubie.stickers) {
                val stickerBuf = stickerBuffers[face] ?: continue
                val rgb = color.rgbFloat
                if (isHighlighted || isRotatingNow) {
                    stickerColorBuffer[0] = rgb[0]
                    stickerColorBuffer[1] = rgb[1]
                    stickerColorBuffer[2] = rgb[2]
                    stickerColorBuffer[3] = 1.0f
                } else {
                    stickerColorBuffer[0] = rgb[0] * 0.28f + 0.12f
                    stickerColorBuffer[1] = rgb[1] * 0.28f + 0.14f
                    stickerColorBuffer[2] = rgb[2] * 0.28f + 0.18f
                    stickerColorBuffer[3] = 1.0f
                }
                GLES20.glUniform4fv(uColorLoc, 1, stickerColorBuffer, 0)
                GLES20.glUniform1f(uSpecularStrengthLoc, if (isHighlighted || isRotatingNow) 0.58f else 0.18f)
                GLES20.glUniform1f(uShininessLoc, 48.0f)
                drawMesh(stickerBuf, stickerVertexCount)
            }
        }
    }

    private fun drawMesh(buffer: FloatBuffer, vertexCount: Int) {
        val stride = 6 * 4 // 3 pos + 3 normal floats
        buffer.position(0)
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 3, GLES20.GL_FLOAT, false, stride, buffer)

        buffer.position(3)
        GLES20.glEnableVertexAttribArray(aNormalLoc)
        GLES20.glVertexAttribPointer(aNormalLoc, 3, GLES20.GL_FLOAT, false, stride, buffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)

        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aNormalLoc)
    }

    /**
     * Constrói a malha 3D chanfrada fechada (26 facetas: 6 faces principais + 12 chanfros de aresta + 8 chanfros de canto)
     * do corpo de cada peça (Cubie) e as placas octogonais em relevo dos 6 adesivos.
     */
    fun buildGeometryBuffers() {
        val bodyVertices = mutableListOf<Float>()
        val half = 0.465f
        val bevel = 0.425f

        // 6 faces principais + chanfros nos cantos/arestas para aparência realista de plástico moldado
        addQuad(
            bodyVertices,
            floatArrayOf(-bevel, -bevel, half),
            floatArrayOf(bevel, -bevel, half),
            floatArrayOf(bevel, bevel, half),
            floatArrayOf(-bevel, bevel, half),
            floatArrayOf(0f, 0f, 1f)
        )
        addQuad(
            bodyVertices,
            floatArrayOf(bevel, -bevel, -half),
            floatArrayOf(-bevel, -bevel, -half),
            floatArrayOf(-bevel, bevel, -half),
            floatArrayOf(bevel, bevel, -half),
            floatArrayOf(0f, 0f, -1f)
        )
        addQuad(
            bodyVertices,
            floatArrayOf(half, -bevel, bevel),
            floatArrayOf(half, -bevel, -bevel),
            floatArrayOf(half, bevel, -bevel),
            floatArrayOf(half, bevel, bevel),
            floatArrayOf(1f, 0f, 0f)
        )
        addQuad(
            bodyVertices,
            floatArrayOf(-half, -bevel, -bevel),
            floatArrayOf(-half, -bevel, bevel),
            floatArrayOf(-half, bevel, bevel),
            floatArrayOf(-half, bevel, -bevel),
            floatArrayOf(-1f, 0f, 0f)
        )
        addQuad(
            bodyVertices,
            floatArrayOf(-bevel, half, bevel),
            floatArrayOf(bevel, half, bevel),
            floatArrayOf(bevel, half, -bevel),
            floatArrayOf(-bevel, half, -bevel),
            floatArrayOf(0f, 1f, 0f)
        )
        addQuad(
            bodyVertices,
            floatArrayOf(-bevel, -half, -bevel),
            floatArrayOf(bevel, -half, -bevel),
            floatArrayOf(bevel, -half, bevel),
            floatArrayOf(-bevel, -half, bevel),
            floatArrayOf(0f, -1f, 0f)
        )

        // 12 chanfros de aresta conectando as 6 faces para cantos arredondados com reflexo especular
        // Frente-Cima, Frente-Baixo, Trás-Cima, Trás-Baixo
        addQuad(
            bodyVertices,
            floatArrayOf(-bevel, bevel, half),
            floatArrayOf(bevel, bevel, half),
            floatArrayOf(bevel, half, bevel),
            floatArrayOf(-bevel, half, bevel),
            normalize3(0f, 1f, 1f)
        )
        addQuad(
            bodyVertices,
            floatArrayOf(-bevel, -half, bevel),
            floatArrayOf(bevel, -half, bevel),
            floatArrayOf(bevel, -bevel, half),
            floatArrayOf(-bevel, -bevel, half),
            normalize3(0f, -1f, 1f)
        )
        addQuad(
            bodyVertices,
            floatArrayOf(bevel, bevel, -half),
            floatArrayOf(-bevel, bevel, -half),
            floatArrayOf(-bevel, half, -bevel),
            floatArrayOf(bevel, half, -bevel),
            normalize3(0f, 1f, -1f)
        )
        addQuad(
            bodyVertices,
            floatArrayOf(bevel, -half, -bevel),
            floatArrayOf(-bevel, -half, -bevel),
            floatArrayOf(-bevel, -bevel, -half),
            floatArrayOf(bevel, -bevel, -half),
            normalize3(0f, -1f, -1f)
        )
        // Direita-Cima, Direita-Baixo, Esquerda-Cima, Esquerda-Baixo
        addQuad(
            bodyVertices,
            floatArrayOf(half, bevel, bevel),
            floatArrayOf(half, bevel, -bevel),
            floatArrayOf(bevel, half, -bevel),
            floatArrayOf(bevel, half, bevel),
            normalize3(1f, 1f, 0f)
        )
        addQuad(
            bodyVertices,
            floatArrayOf(bevel, -half, bevel),
            floatArrayOf(bevel, -half, -bevel),
            floatArrayOf(half, -bevel, -bevel),
            floatArrayOf(half, -bevel, bevel),
            normalize3(1f, -1f, 0f)
        )
        addQuad(
            bodyVertices,
            floatArrayOf(-half, bevel, -bevel),
            floatArrayOf(-half, bevel, bevel),
            floatArrayOf(-bevel, half, bevel),
            floatArrayOf(-bevel, half, -bevel),
            normalize3(-1f, 1f, 0f)
        )
        addQuad(
            bodyVertices,
            floatArrayOf(-bevel, -half, -bevel),
            floatArrayOf(-bevel, -half, bevel),
            floatArrayOf(-half, -bevel, bevel),
            floatArrayOf(-half, -bevel, -bevel),
            normalize3(-1f, -1f, 0f)
        )
        // 4 chanfros verticais (Frente-Direita, Frente-Esquerda, Trás-Direita, Trás-Esquerda)
        addQuad(
            bodyVertices,
            floatArrayOf(bevel, -bevel, half),
            floatArrayOf(half, -bevel, bevel),
            floatArrayOf(half, bevel, bevel),
            floatArrayOf(bevel, bevel, half),
            normalize3(1f, 0f, 1f)
        )
        addQuad(
            bodyVertices,
            floatArrayOf(-half, -bevel, bevel),
            floatArrayOf(-bevel, -bevel, half),
            floatArrayOf(-bevel, bevel, half),
            floatArrayOf(-half, bevel, bevel),
            normalize3(-1f, 0f, 1f)
        )
        addQuad(
            bodyVertices,
            floatArrayOf(half, -bevel, -bevel),
            floatArrayOf(bevel, -bevel, -half),
            floatArrayOf(bevel, bevel, -half),
            floatArrayOf(half, bevel, -bevel),
            normalize3(1f, 0f, -1f)
        )
        addQuad(
            bodyVertices,
            floatArrayOf(-bevel, -bevel, -half),
            floatArrayOf(-half, -bevel, -bevel),
            floatArrayOf(-half, bevel, -bevel),
            floatArrayOf(-bevel, bevel, -half),
            normalize3(-1f, 0f, -1f)
        )

        // 8 chanfros triangulares nos cantos fechando completamente o poliedro de 26 faces
        for (sx in intArrayOf(-1, 1)) {
            for (sy in intArrayOf(-1, 1)) {
                for (sz in intArrayOf(-1, 1)) {
                    val fx = sx.toFloat()
                    val fy = sy.toFloat()
                    val fz = sz.toFloat()
                    val pX = floatArrayOf(fx * half, fy * bevel, fz * bevel)
                    val pY = floatArrayOf(fx * bevel, fy * half, fz * bevel)
                    val pZ = floatArrayOf(fx * bevel, fy * bevel, fz * half)
                    val cornerNormal = normalize3(fx, fy, fz)
                    if (sx * sy * sz > 0) {
                        addTriangle(bodyVertices, pZ, pX, pY, cornerNormal)
                    } else {
                        addTriangle(bodyVertices, pZ, pY, pX, cornerNormal)
                    }
                }
            }
        }

        cubieBodyVertexCount = bodyVertices.size / 6
        cubieBodyBuffer = createFloatBuffer(bodyVertices)

        // Adesivos com bordas suavizadas (octógono arredondado) sobre cada uma das 6 faces
        val sHalf = 0.395f
        val sCorner = 0.335f
        val sZ = 0.473f

        for (face in CubeFace.entries) {
            val sVerts = mutableListOf<Float>()
            addRoundedStickerFace(sVerts, face, sHalf, sCorner, sZ)
            stickerVertexCount = sVerts.size / 6
            stickerBuffers[face] = createFloatBuffer(sVerts)
        }
    }

    fun getBodyVertexCountForAudit(): Int = cubieBodyVertexCount
    fun getStickerVertexCountForAudit(): Int = stickerVertexCount
    fun getStickerFaceCountForAudit(): Int = stickerBuffers.size

    private fun addRoundedStickerFace(
        out: MutableList<Float>,
        face: CubeFace,
        s: Float,
        c: Float,
        depth: Float
    ) {
        // Polígono octogonal convexo (8 vértices no plano 2D local da face) para cantos arredondados
        val pts2D = arrayOf(
            floatArrayOf(-c, -s),
            floatArrayOf(c, -s),
            floatArrayOf(s, -c),
            floatArrayOf(s, c),
            floatArrayOf(c, s),
            floatArrayOf(-c, s),
            floatArrayOf(-s, c),
            floatArrayOf(-s, -c)
        )
        val normal = floatArrayOf(face.nx.toFloat(), face.ny.toFloat(), face.nz.toFloat())
        val center3D = map2DToFace3D(face, 0f, 0f, depth)

        for (i in pts2D.indices) {
            val p1 = pts2D[i]
            val p2 = pts2D[(i + 1) % pts2D.size]
            val v1 = map2DToFace3D(face, p1[0], p1[1], depth)
            val v2 = map2DToFace3D(face, p2[0], p2[1], depth)
            addTriangle(out, center3D, v1, v2, normal)
        }
    }

    private fun map2DToFace3D(face: CubeFace, u: Float, v: Float, d: Float): FloatArray = when (face) {
        CubeFace.FRONT -> floatArrayOf(u, v, d)
        CubeFace.BACK -> floatArrayOf(-u, v, -d)
        CubeFace.RIGHT -> floatArrayOf(d, v, -u)
        CubeFace.LEFT -> floatArrayOf(-d, v, u)
        CubeFace.UP -> floatArrayOf(u, d, -v)
        CubeFace.DOWN -> floatArrayOf(u, -d, v)
    }

    private fun addQuad(
        out: MutableList<Float>,
        v0: FloatArray,
        v1: FloatArray,
        v2: FloatArray,
        v3: FloatArray,
        normal: FloatArray
    ) {
        addTriangle(out, v0, v1, v2, normal)
        addTriangle(out, v0, v2, v3, normal)
    }

    private fun addTriangle(
        out: MutableList<Float>,
        v0: FloatArray,
        v1: FloatArray,
        v2: FloatArray,
        normal: FloatArray
    ) {
        for (v in arrayOf(v0, v1, v2)) {
            out.add(v[0])
            out.add(v[1])
            out.add(v[2])
            out.add(normal[0])
            out.add(normal[1])
            out.add(normal[2])
        }
    }

    private fun normalize3(x: Float, y: Float, z: Float): FloatArray {
        val len = sqrt(x * x + y * y + z * z).coerceAtLeast(0.0001f)
        return floatArrayOf(x / len, y / len, z / len)
    }

    private fun createFloatBuffer(list: List<Float>): FloatBuffer {
        val bb = ByteBuffer.allocateDirect(list.size * 4).order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        for (f in list) fb.put(f)
        fb.position(0)
        return fb
    }

    // =========================================================================
    // RAYCASTING 3D E DETECÇÃO DE MOVIMENTOS POR ARRASTE TÁTIL NAS FACES
    // =========================================================================

    /**
     * Realiza raycasting 3D da posição do toque na tela contra o volume do cubo 3x3.
     * Suporta todas as 9 camadas (-1, 0, +1 nos eixos X, Y e Z) sob qualquer orientação de câmera,
     * incluindo tolerância de borda ([allowEdgePadding]) para gestos iniciados nas extremidades do cubo.
     */
    fun pickCubieAtScreen(
        screenX: Float,
        screenY: Float,
        allowEdgePadding: Boolean = false
    ): CubeTouchHit? {
        val w = viewportWidth.toFloat().coerceAtLeast(1f)
        val h = viewportHeight.toFloat().coerceAtLeast(1f)
        val ndcX = (2f * screenX / w) - 1f
        val ndcY = 1f - (2f * screenY / h)

        val nearPoint = FloatArray(4)
        val farPoint = FloatArray(4)
        val eyeDir = FloatArray(3)
        synchronized(vpLock) {
            updateViewProjectionLocked()
            Matrix.multiplyMV(nearPoint, 0, invVpMatrix, 0, floatArrayOf(ndcX, ndcY, -1f, 1f), 0)
            Matrix.multiplyMV(farPoint, 0, invVpMatrix, 0, floatArrayOf(ndcX, ndcY, 1f, 1f), 0)
            val yawRad = (cameraYaw * PI / 180.0).toFloat()
            val pitchRad = (cameraPitch.coerceIn(-78f, 78f) * PI / 180.0).toFloat()
            eyeDir[0] = cos(pitchRad) * sin(yawRad)
            eyeDir[1] = sin(pitchRad)
            eyeDir[2] = cos(pitchRad) * cos(yawRad)
        }
        if (abs(nearPoint[3]) < 1e-6f || abs(farPoint[3]) < 1e-6f) return null

        val ox = nearPoint[0] / nearPoint[3]
        val oy = nearPoint[1] / nearPoint[3]
        val oz = nearPoint[2] / nearPoint[3]

        val fx = farPoint[0] / farPoint[3]
        val fy = farPoint[1] / farPoint[3]
        val fz = farPoint[2] / farPoint[3]

        var dx = fx - ox
        var dy = fy - oy
        var dz = fz - oz
        val len = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-6f)
        dx /= len
        dy /= len
        dz /= len

        val rayOrigin = floatArrayOf(ox, oy, oz)
        val rayDir = floatArrayOf(dx, dy, dz)

        val coreBound = 1.45f
        var hitPoint = intersectRayBox(rayOrigin, rayDir, coreBound)
        if (hitPoint == null && allowEdgePadding) {
            val paddedHit = intersectRayBox(rayOrigin, rayDir, 1.58f)
            if (paddedHit != null) {
                hitPoint = floatArrayOf(
                    paddedHit[0].coerceIn(-coreBound, coreBound),
                    paddedHit[1].coerceIn(-coreBound, coreBound),
                    paddedHit[2].coerceIn(-coreBound, coreBound)
                )
            }
        }
        val hp = hitPoint ?: return null
        val hx = hp[0]
        val hy = hp[1]
        val hz = hp[2]

        // Determina a face atingida, tratando chanfros de borda entre duas faces visíveis
        // para priorizar a face mais alinhada à visão da câmera (evita que toques na coluna direita R da face frontal
        // sejam desviados para a face lateral direita).
        val hitFace = resolveHitFaceWithBevelPriority(hx, hy, hz, eyeDir)

        val cx = if (hitFace.nx != 0) hitFace.nx else quantizeLayerCoord(hx)
        val cy = if (hitFace.ny != 0) hitFace.ny else quantizeLayerCoord(hy)
        val cz = if (hitFace.nz != 0) hitFace.nz else quantizeLayerCoord(hz)

        return CubeTouchHit(
            cubieX = cx,
            cubieY = cy,
            cubieZ = cz,
            hitFace = hitFace,
            hitPointWorld = floatArrayOf(hx, hy, hz)
        )
    }

    private fun intersectRayBox(
        rayOrigin: FloatArray,
        rayDir: FloatArray,
        bound: Float
    ): FloatArray? {
        var tMin = -Float.MAX_VALUE
        var tMax = Float.MAX_VALUE
        for (i in 0..2) {
            val o = rayOrigin[i]
            val d = rayDir[i]
            if (abs(d) < 1e-6f) {
                if (o < -bound || o > bound) return null
            } else {
                var t1 = (-bound - o) / d
                var t2 = (bound - o) / d
                if (t1 > t2) {
                    val tmp = t1
                    t1 = t2
                    t2 = tmp
                }
                if (t1 > tMin) tMin = t1
                if (t2 < tMax) tMax = t2
                if (tMin > tMax) return null
            }
        }
        if (tMax < 0f || tMin < 0f) return null
        return floatArrayOf(
            rayOrigin[0] + tMin * rayDir[0],
            rayOrigin[1] + tMin * rayDir[1],
            rayOrigin[2] + tMin * rayDir[2]
        )
    }

    private fun resolveHitFaceWithBevelPriority(
        hx: Float,
        hy: Float,
        hz: Float,
        eyeDir: FloatArray
    ): CubeFace {
        val ax = abs(hx)
        val ay = abs(hy)
        val az = abs(hz)

        val faceX = if (hx >= 0f) CubeFace.RIGHT else CubeFace.LEFT
        val faceY = if (hy >= 0f) CubeFace.UP else CubeFace.DOWN
        val faceZ = if (hz >= 0f) CubeFace.FRONT else CubeFace.BACK

        val primaryFace = when {
            ax >= ay && ax >= az -> faceX
            ay >= ax && ay >= az -> faceY
            else -> faceZ
        }

        // Se o toque caiu no chanfro entre duas faces (ambas coordenadas >= 1.15f),
        // escolhe a face mais voltada para o observador.
        val bevelThreshold = 1.15f
        val candidates = mutableListOf<CubeFace>()
        if (ax >= bevelThreshold) candidates.add(faceX)
        if (ay >= bevelThreshold) candidates.add(faceY)
        if (az >= bevelThreshold) candidates.add(faceZ)

        if (candidates.size >= 2 && candidates.contains(primaryFace)) {
            val bestCameraFace = candidates.maxByOrNull { f ->
                f.nx * eyeDir[0] + f.ny * eyeDir[1] + f.nz * eyeDir[2]
            } ?: primaryFace
            val primaryExposure = primaryFace.nx * eyeDir[0] + primaryFace.ny * eyeDir[1] + primaryFace.nz * eyeDir[2]
            val bestExposure = bestCameraFace.nx * eyeDir[0] + bestCameraFace.ny * eyeDir[1] + bestCameraFace.nz * eyeDir[2]
            if (bestExposure > primaryExposure + 0.05f) {
                return bestCameraFace
            }
        }
        return primaryFace
    }

    /**
     * Quantiza a coordenada no plano da face em -1, 0 (camada central) ou +1.
     * O limiar 0.50f garante que a faixa central [-0.50f, +0.50f] tenha ~1.0 unidade de largura
     * (mais de 1/3 da face), tornando a seleção de M, E e S natural e confiável.
     */
    private fun quantizeLayerCoord(v: Float): Int = when {
        v > 0.50f -> 1
        v < -0.50f -> -1
        else -> 0
    }

    /**
     * Converte o vetor de arraste 2D na tela (dragDx, dragDy) a partir de um [CubeTouchHit]
     * no movimento 3D exato da camada correspondente (incluindo F, R, U, L, D, B e as 3 fatias centrais M, E, S),
     * independentemente do ângulo da câmera.
     * Utiliza o vetor tangente puro no plano da face tocada (v = omega x faceNormal) projetado pela vpMatrix atual.
     */
    fun resolveMoveFromSwipe(hit: CubeTouchHit, dragDx: Float, dragDy: Float): CubeMove? {
        val dragLen = sqrt(dragDx * dragDx + dragDy * dragDy)
        if (dragLen < 8f) return null
        val normDragX = dragDx / dragLen
        val normDragY = dragDy / dragLen

        return findBestCandidateMove(
            cx = hit.cubieX,
            cy = hit.cubieY,
            cz = hit.cubieZ,
            hitFace = hit.hitFace,
            hitPointWorld = hit.hitPointWorld,
            normDragX = normDragX,
            normDragY = normDragY,
            minDot = 0.50f
        )
    }

    private fun findBestCandidateMove(
        cx: Int,
        cy: Int,
        cz: Int,
        hitFace: CubeFace,
        hitPointWorld: FloatArray,
        normDragX: Float,
        normDragY: Float,
        minDot: Float
    ): CubeMove? {
        val candidateMoves = CubeMove.entries.filter { move ->
            abs(move.glAngleDegrees) == 90f &&
                moveMatchesCubie(move, cx, cy, cz) &&
                !isAxisParallelToNormal(move.axis, hitFace)
        }

        val startScreen = projectWorldToScreen(hitPointWorld) ?: return null
        val nx = hitFace.nx.toFloat()
        val ny = hitFace.ny.toFloat()
        val nz = hitFace.nz.toFloat()

        var bestMove: CubeMove? = null
        var bestDot = minDot
        val axisBestDot = mutableMapOf<RotationAxis, Float>()

        for (move in candidateMoves) {
            val omegaSign = if (move.glAngleDegrees > 0f) 1f else -1f
            val wx = if (move.axis == RotationAxis.X) omegaSign else 0f
            val wy = if (move.axis == RotationAxis.Y) omegaSign else 0f
            val wz = if (move.axis == RotationAxis.Z) omegaSign else 0f

            // Tangente pura no plano da face tocada: v = omega x normalDaFace
            val vx = wy * nz - wz * ny
            val vy = wz * nx - wx * nz
            val vz = wx * ny - wy * nx

            val displacedWorld = floatArrayOf(
                hitPointWorld[0] + vx * 0.40f,
                hitPointWorld[1] + vy * 0.40f,
                hitPointWorld[2] + vz * 0.40f
            )
            val endScreen = projectWorldToScreen(displacedWorld) ?: continue

            val sx = endScreen[0] - startScreen[0]
            val sy = endScreen[1] - startScreen[1]
            val sLen = sqrt(sx * sx + sy * sy)
            if (sLen < 1e-3f) continue

            val dot = (sx / sLen) * normDragX + (sy / sLen) * normDragY
            val currentAxisMax = axisBestDot[move.axis] ?: 0f
            if (abs(dot) > currentAxisMax) {
                axisBestDot[move.axis] = abs(dot)
            }

            if (dot > bestDot) {
                bestDot = dot
                bestMove = move
            }
        }

        val chosen = bestMove ?: return null
        // Evita disparar movimentos diagonais ambíguos quando o arrasto está exatamente na bissetriz entre os 2 eixos
        val otherAxisMax = axisBestDot.entries
            .filter { it.key != chosen.axis }
            .maxOfOrNull { it.value } ?: 0f
        if (bestDot - otherAxisMax < 0.08f) {
            return null
        }
        return chosen
    }

    private fun moveMatchesCubie(move: CubeMove, cx: Int, cy: Int, cz: Int): Boolean = when (move.axis) {
        RotationAxis.X -> cx == move.layerCoord
        RotationAxis.Y -> cy == move.layerCoord
        RotationAxis.Z -> cz == move.layerCoord
    }

    private fun isAxisParallelToNormal(axis: RotationAxis, face: CubeFace): Boolean = when (axis) {
        RotationAxis.X -> abs(face.nx) == 1
        RotationAxis.Y -> abs(face.ny) == 1
        RotationAxis.Z -> abs(face.nz) == 1
    }

    /**
     * Projeta um ponto 3D do mundo para coordenadas 2D de pixel do componente.
     */
    fun projectWorldToScreen(worldPt: FloatArray): FloatArray? {
        val clip = FloatArray(4)
        synchronized(vpLock) {
            updateViewProjectionLocked()
            Matrix.multiplyMV(
                clip, 0, vpMatrix, 0,
                floatArrayOf(worldPt[0], worldPt[1], worldPt[2], 1f), 0
            )
        }
        if (clip[3] <= 0.001f) return null
        val ndcX = clip[0] / clip[3]
        val ndcY = clip[1] / clip[3]
        val sx = (ndcX + 1f) * 0.5f * viewportWidth
        val sy = (1f - ndcY) * 0.5f * viewportHeight
        return floatArrayOf(sx, sy)
    }

    fun resetCamera() {
        cameraYaw = DEFAULT_YAW
        cameraPitch = DEFAULT_PITCH
        cameraDistance = DEFAULT_DISTANCE
        synchronized(vpLock) {
            updateViewProjectionLocked()
        }
    }

    companion object {
        const val DEFAULT_YAW = 38f
        const val DEFAULT_PITCH = 26f
        const val DEFAULT_DISTANCE = 7.6f
        const val MIN_DISTANCE = 5.4f
        const val MAX_DISTANCE = 10.5f

        private const val VERTEX_SHADER_CODE = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uModelMatrix;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            varying vec3 vWorldPos;
            varying vec3 vNormal;
            void main() {
                vec4 worldPos = uModelMatrix * vec4(aPosition, 1.0);
                vWorldPos = worldPos.xyz;
                vNormal = normalize((uModelMatrix * vec4(aNormal, 0.0)).xyz);
                gl_Position = uMVPMatrix * vec4(aPosition, 1.0);
            }
        """

        private const val FRAGMENT_SHADER_CODE = """
            precision mediump float;
            uniform vec4 uColor;
            uniform vec3 uLightDir;
            uniform vec3 uViewPos;
            uniform float uSpecularStrength;
            uniform float uShininess;
            varying vec3 vWorldPos;
            varying vec3 vNormal;
            void main() {
                vec3 norm = normalize(vNormal);
                vec3 lightDir = normalize(uLightDir);
                vec3 viewDir = normalize(uViewPos - vWorldPos);
                vec3 halfDir = normalize(lightDir + viewDir);

                // Iluminação ambiente + difusa principal + luz de preenchimento (rim light)
                float ambient = 0.42;
                float diff = max(dot(norm, lightDir), 0.0) * 0.58;
                float fillLight = max(dot(norm, normalize(vec3(-0.6, -0.4, -0.5))), 0.0) * 0.16;

                // Reflexo especular Blinn-Phong de estúdio
                float spec = pow(max(dot(norm, halfDir), 0.0), uShininess) * uSpecularStrength;

                vec3 litColor = uColor.rgb * (ambient + diff + fillLight) + vec3(spec);
                gl_FragColor = vec4(min(litColor, vec3(1.0)), uColor.a);
            }
        """

        private fun createProgram(vertexSource: String, fragmentSource: String): Int {
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            val program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
            return program
        }

        private fun loadShader(type: Int, shaderCode: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            return shader
        }
    }
}
