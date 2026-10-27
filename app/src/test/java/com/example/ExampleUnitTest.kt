package com.example

import com.example.curriculum.CurriculumRepository
import com.example.model.CubeColor
import com.example.model.CubeFace
import com.example.model.CubeMove
import com.example.model.CubeState
import com.example.model.CubieType
import com.example.renderer.CubeGLRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExampleUnitTest {

    @Test
    fun `quatro rotacoes de 90 graus retornam ao estado original`() {
        val solved = CubeState.createSolved()
        val ninetyDegreeMoves = listOf(
            CubeMove.F, CubeMove.F_PRIME,
            CubeMove.R, CubeMove.R_PRIME,
            CubeMove.U, CubeMove.U_PRIME,
            CubeMove.L, CubeMove.L_PRIME,
            CubeMove.D, CubeMove.D_PRIME,
            CubeMove.B, CubeMove.B_PRIME,
            CubeMove.M, CubeMove.M_PRIME,
            CubeMove.E, CubeMove.E_PRIME,
            CubeMove.S, CubeMove.S_PRIME
        )

        for (move in ninetyDegreeMoves) {
            var state = solved
            repeat(4) {
                state = state.applyMove(move)
            }
            assertEquals("Falha ao aplicar 4x o movimento ${move.notation}", solved, state)
            assertTrue(state.isSolved())
        }
    }

    @Test
    fun `movimentos de cada camada afetam exclusivamente as 9 pecas daquela camada`() {
        val solved = CubeState.createSolved()
        for (move in CubeMove.entries) {
            val after = solved.applyMove(move)
            val changedCubies = solved.cubies.filter { beforeCubie ->
                val afterCubie = after.cubies.first { it.id == beforeCubie.id }
                beforeCubie != afterCubie
            }
            // Em uma camada 3x3, todas as peças da fatia (exceto o centro/núcleo que gira no próprio eixo sem trocar de adesivo na própria face)
            // pertencem exclusivamente à coordenada daquela camada.
            assertTrue(
                "Movimento ${move.notation} não deve alterar peças fora da sua camada",
                changedCubies.all { move.affectsCubie(it) }
            )
            val unaffectedCubies = solved.cubies.filter { !move.affectsCubie(it) }
            assertEquals(18, unaffectedCubies.size)
            for (unaffected in unaffectedCubies) {
                val afterUnaffected = after.cubies.first { it.id == unaffected.id }
                assertEquals(unaffected, afterUnaffected)
            }
        }
    }

    @Test
    fun `movimento seguido do seu inverso restaura exatamente o estado anterior`() {
        val baseState = CubeState.createSolved().applySequence("R U F' L2 D B' M E S'")
        for (move in CubeMove.entries) {
            val afterMoveAndInverse = baseState.applyMove(move).applyMove(move.inverse())
            assertEquals(
                "Movimento ${move.notation} seguido de ${move.inverse().notation} nao restaurou o estado",
                baseState,
                afterMoveAndInverse
            )
        }
    }

    @Test
    fun `dois movimentos de 90 graus correspondem a uma rotacao de 180 graus`() {
        val baseState = CubeState.createSolved().applySequence("F R U' E S")
        val families = listOf(
            Triple(CubeMove.F, CubeMove.F_PRIME, CubeMove.F2),
            Triple(CubeMove.R, CubeMove.R_PRIME, CubeMove.R2),
            Triple(CubeMove.U, CubeMove.U_PRIME, CubeMove.U2),
            Triple(CubeMove.L, CubeMove.L_PRIME, CubeMove.L2),
            Triple(CubeMove.D, CubeMove.D_PRIME, CubeMove.D2),
            Triple(CubeMove.B, CubeMove.B_PRIME, CubeMove.B2),
            Triple(CubeMove.M, CubeMove.M_PRIME, CubeMove.M2),
            Triple(CubeMove.E, CubeMove.E_PRIME, CubeMove.E2),
            Triple(CubeMove.S, CubeMove.S_PRIME, CubeMove.S2)
        )

        for ((cw, ccw, halfTurn) in families) {
            val twoCw = baseState.applyMove(cw).applyMove(cw)
            val twoCcw = baseState.applyMove(ccw).applyMove(ccw)
            val one180 = baseState.applyMove(halfTurn)

            assertEquals("${cw.notation} + ${cw.notation} deve ser igual a ${halfTurn.notation}", one180, twoCw)
            assertEquals("${ccw.notation} + ${ccw.notation} deve ser igual a ${halfTurn.notation}", one180, twoCcw)
        }
    }

    @Test
    fun `embaralhamentos preservam estados fisicamente validos e a sequencia inversa recupera o cubo resolvido`() {
        val solved = CubeState.createSolved()
        val random = Random(42)

        repeat(10) {
            val scramble = CubeState.generateScramble(length = 25, random = random)
            val scrambled = solved.applyMoves(scramble)

            assertFalse("Cubo embaralhado nao deve estar resolvido", scrambled.isSolved())

            // Verifica integridade física das 27 peças e 54 adesivos (9 de cada cor)
            assertEquals(27, scrambled.cubies.size)
            val coords = scrambled.cubies.map { Triple(it.x, it.y, it.z) }.toSet()
            assertEquals(27, coords.size)

            assertEquals(1, scrambled.cubies.count { it.type == CubieType.CORE })
            assertEquals(6, scrambled.cubies.count { it.type == CubieType.CENTER })
            assertEquals(12, scrambled.cubies.count { it.type == CubieType.EDGE })
            assertEquals(8, scrambled.cubies.count { it.type == CubieType.CORNER })

            for (color in CubeColor.entries) {
                val stickerCount = scrambled.cubies.sumOf { cubie ->
                    cubie.stickers.values.count { it == color }
                }
                assertEquals("Cor $color deve ter exatamente 9 adesivos", 9, stickerCount)
            }

            // Aplica a sequência inversa e valida que retorna exatamente ao estado resolvido
            val inverseMoves = CubeMove.invertSequence(scramble)
            val recovered = scrambled.applyMoves(inverseMoves)
            assertEquals(solved, recovered)
            assertTrue(recovered.isSolved())
        }
    }

    @Test
    fun `todas as 7 etapas pedagogicas e seus casos preservam camadas anteriores e atingem o objetivo sem resolver etapas futuras`() {
        assertEquals(7, CurriculumRepository.steps.size)

        for (step in CurriculumRepository.steps) {
            assertTrue("Etapa ${step.number} deve ter casos", step.cases.isNotEmpty())
            for (lessonCase in step.cases) {
                val initialState = lessonCase.createInitialState()

                // Antes de aplicar a solução do caso, o objetivo da etapa atual ainda não foi atingido
                assertFalse(
                    "Estado inicial do caso ${lessonCase.id} não deve já cumprir a etapa ${step.number}",
                    initialState.isStepGoalAchieved(step.number)
                )

                // Verifica se o estado inicial já preserva as etapas anteriores concluídas
                if (step.number >= 3) {
                    assertTrue(
                        "Caso ${lessonCase.id} da etapa ${step.number} deve iniciar com a 1ª camada pronta",
                        initialState.isFirstLayerSolved()
                    )
                }
                if (step.number >= 4) {
                    assertTrue(
                        "Caso ${lessonCase.id} da etapa ${step.number} deve iniciar com a 2ª camada pronta",
                        initialState.isSecondLayerSolved()
                    )
                }
                if (step.number >= 5) {
                    assertTrue(
                        "Caso ${lessonCase.id} da etapa ${step.number} deve iniciar com a cruz amarela pronta",
                        initialState.isYellowCrossOriented()
                    )
                }
                if (step.number >= 6) {
                    assertTrue(
                        "Caso ${lessonCase.id} da etapa ${step.number} deve iniciar com a face amarela orientada",
                        initialState.isYellowFaceOriented()
                    )
                }
                if (step.number == 7) {
                    assertTrue(
                        "Caso ${lessonCase.id} da etapa 7 deve iniciar com os cantos do topo permutados",
                        initialState.isYellowCornersPermuted()
                    )
                }

                // Aplica o algoritmo completo de demonstração do caso
                val finalState = initialState.applyMoves(lessonCase.fullMoveList)

                // Verifica que o objetivo da etapa foi atingido preservando todas as camadas anteriores
                assertTrue(
                    "Algoritmo '${lessonCase.fullResolutionAlgorithm}' falhou ao atingir objetivo do caso ${lessonCase.id} (Etapa ${step.number})",
                    finalState.isStepGoalAchieved(step.number)
                )

                // Para etapas 1 a 6, concluir a etapa NÃO deve resolver o cubo inteiro prematuramente
                if (step.number < 7) {
                    assertFalse(
                        "Concluir a etapa ${step.number} (${lessonCase.id}) não deve deixar o cubo 100% resolvido antes da Etapa 7",
                        finalState.isSolved()
                    )
                } else {
                    assertTrue(
                        "Concluir a etapa 7 (${lessonCase.id}) deve deixar o cubo 100% resolvido",
                        finalState.isSolved()
                    )
                }
            }
        }
    }

    @Test
    fun `verifica fidelidade exata dos casos das Etapas 4 e 5 com as figuras de camadas_pdf`() {
        // Etapa 4: Caso 1 (L em UL e UB), Caso 2 (Linha em UL e UR), Caso 3 (Ponto: 0 meios amarelos)
        val step4 = CurriculumRepository.getStep(4)
        val s4c1 = step4.cases[0].createInitialState()
        assertEquals(CubeColor.YELLOW, s4c1.cubieAt(-1, 1, 0).colorOn(CubeFace.UP)) // UL
        assertEquals(CubeColor.YELLOW, s4c1.cubieAt(0, 1, -1).colorOn(CubeFace.UP)) // UB
        assertFalse(s4c1.cubieAt(0, 1, 1).colorOn(CubeFace.UP) == CubeColor.YELLOW)
        assertFalse(s4c1.cubieAt(1, 1, 0).colorOn(CubeFace.UP) == CubeColor.YELLOW)

        val s4c2 = step4.cases[1].createInitialState()
        assertEquals(CubeColor.YELLOW, s4c2.cubieAt(-1, 1, 0).colorOn(CubeFace.UP)) // UL
        assertEquals(CubeColor.YELLOW, s4c2.cubieAt(1, 1, 0).colorOn(CubeFace.UP))  // UR
        assertFalse(s4c2.cubieAt(0, 1, 1).colorOn(CubeFace.UP) == CubeColor.YELLOW)
        assertFalse(s4c2.cubieAt(0, 1, -1).colorOn(CubeFace.UP) == CubeColor.YELLOW)

        val s4c3 = step4.cases[2].createInitialState()
        val topEdgesS4C3 = listOf(
            s4c3.cubieAt(0, 1, 1),
            s4c3.cubieAt(0, 1, -1),
            s4c3.cubieAt(1, 1, 0),
            s4c3.cubieAt(-1, 1, 0)
        )
        assertEquals(0, topEdgesS4C3.count { it.colorOn(CubeFace.UP) == CubeColor.YELLOW })

        // Etapa 5: Caso 1A (1 canto em UFL), Caso 1B (1 canto em UFL), Caso 2 (2 cantos, UFL com amarelo na FRENTE), Caso 3 (0 cantos, UFL com amarelo na ESQUERDA)
        val step5 = CurriculumRepository.getStep(5)
        fun countYellowCorners(state: CubeState): Int = listOf(
            state.cubieAt(1, 1, 1),
            state.cubieAt(-1, 1, 1),
            state.cubieAt(1, 1, -1),
            state.cubieAt(-1, 1, -1)
        ).count { it.colorOn(CubeFace.UP) == CubeColor.YELLOW }

        val s5c1a = step5.cases[0].createInitialState()
        assertEquals(1, countYellowCorners(s5c1a))
        assertEquals(CubeColor.YELLOW, s5c1a.cubieAt(-1, 1, 1).colorOn(CubeFace.UP))

        val s5c1b = step5.cases[1].createInitialState()
        assertEquals(1, countYellowCorners(s5c1b))
        assertEquals(CubeColor.YELLOW, s5c1b.cubieAt(-1, 1, 1).colorOn(CubeFace.UP))

        val s5c2 = step5.cases[2].createInitialState()
        assertEquals(2, countYellowCorners(s5c2))
        assertEquals(CubeColor.YELLOW, s5c2.cubieAt(-1, 1, 1).colorOn(CubeFace.FRONT))

        val s5c3 = step5.cases[3].createInitialState()
        assertEquals(0, countYellowCorners(s5c3))
        assertEquals(CubeColor.YELLOW, s5c3.cubieAt(-1, 1, 1).colorOn(CubeFace.LEFT))
    }

    @Test
    fun `algoritmo Minerva da etapa 7 cicla corretamente os 3 meios mantendo cantos e 2 camadas intactos`() {
        val solved = CubeState.createSolved()
        // Caso 1 do Minerva: F2 U M' U2 M U F2
        val afterMinerva1 = solved.applySequence("F2 U M' U2 M U F2")
        assertTrue(afterMinerva1.isSecondLayerSolved())
        assertTrue(afterMinerva1.isYellowFaceOriented())
        assertTrue(afterMinerva1.isYellowCornersPermuted())
        // Três meios do topo foram permutados, mas o meio de trás (UB) permaneceu resolvido
        assertEquals(CubeColor.GREEN, afterMinerva1.cubieAt(0, 1, -1).colorOn(CubeFace.BACK))
        assertFalse(afterMinerva1.isSolved())

        // Aplicando o Caso 2 do Minerva (F2 U' M' U2 M U' F2), que é o inverso, o cubo volta a ficar 100% resolvido
        val reSolved = afterMinerva1.applySequence("F2 U' M' U2 M U' F2")
        assertTrue(reSolved.isSolved())
    }
}
